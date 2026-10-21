package bo.alertasicoes

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Lee el texto del DBC (PDF, Word o .zip) y busca el personal que pide la entidad. */
object LectorDbc {

    /** Palabras con las que suelen empezar los cargos del personal (sin tildes). */
    private val CARGOS = listOf(
        "ingenier", "arquitect", "tecnico", "licenciad", "especialista", "topograf", "residente",
        "superintendente", "director", "geolog", "economista", "abogad", "contador", "auxiliar",
        "maestro", "capataz", "operador", "electricista", "sanitari", "ambiental", "seguridad industrial",
        "profesional", "biolog", "agronom", "medico", "enfermer", "laboratorista", "dibujante",
        "jefe de", "encargado", "supervisor", "fiscal", "sociolog", "trabajador social", "geotecn",
    )

    /** Frases de párrafo (no de tabla) que se descartan aunque nombren un cargo. */
    private val DESCARTAR = listOf("experiencia", "debera", "el proponente", "se requiere", "presentar", "conforme", "segun")

    /**
     * Lee el DBC y devuelve el resumen del personal, o una frase que explica por qué no se pudo.
     * Acepta PDF, Word (.docx y el .doc antiguo) y .zip que los contengan.
     */
    fun analizar(ctx: Context, archivo: File): String {
        val texto = try {
            texto(ctx, archivo)
        } catch (e: Exception) {
            null
        }
        return when {
            texto.isNullOrBlank() -> "Personal: no se pudo leer el DBC (formato no reconocido o escaneado)."
            else -> personal(texto) ?: "Personal: no encontré la sección de personal en el DBC."
        }
    }

    private fun texto(ctx: Context, archivo: File): String? {
        val cabecera = ByteArray(4)
        archivo.inputStream().use { it.read(cabecera) }
        return when {
            cabecera.startsWith("%PDF") -> textoPdf(ctx, archivo)
            cabecera.startsWith("PK") -> archivo.inputStream().use { textoZip(ctx, it) }
            cabecera[0] == 0xD0.toByte() && cabecera[1] == 0xCF.toByte() -> textoDoc(archivo.readBytes())
            else -> null
        }
    }

    private fun ByteArray.startsWith(s: String) = s.indices.all { it < size && this[it] == s[it].code.toByte() }

    private fun textoPdf(ctx: Context, pdf: File): String {
        PDFBoxResourceLoader.init(ctx)
        PDDocument.load(pdf).use { doc ->
            val lector = PDFTextStripper()
            lector.endPage = minOf(doc.numberOfPages, 150)
            return lector.getText(doc)
        }
    }

    /** Un .docx es un zip con word/document.xml; un .zip normal puede traer el DBC adentro. */
    private fun textoZip(ctx: Context, entrada: InputStream): String? {
        val partes = ArrayList<String>()
        ZipInputStream(entrada).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val nombre = e.name.lowercase()
                when {
                    nombre == "word/document.xml" -> return textoDocx(zip.readBytes().toString(Charsets.UTF_8))
                    !e.isDirectory && (nombre.endsWith(".pdf") || nombre.endsWith(".doc") || nombre.endsWith(".docx")) -> {
                        val tmp = File(ctx.cacheDir, "dbc_zip_" + nombre.substringAfterLast('/').replace(Regex("[^a-z0-9.]"), "_"))
                        FileOutputStream(tmp).use { zip.copyTo(it) }
                        try {
                            texto(ctx, tmp)?.let { partes += it }
                        } finally {
                            tmp.delete()
                        }
                    }
                }
            }
        }
        return partes.joinToString("\n").ifBlank { null }
    }

    /** Texto de un .docx; cada fila de tabla queda en una línea con sus celdas separadas por " | ". */
    private fun textoDocx(xml: String): String {
        fun sinEtiquetas(s: String) = Parser.unescapeEntities(s.replace(Regex("<[^>]+>"), ""), false)
        val conFilas = Regex("<w:tr[ >].*?</w:tr>", RegexOption.DOT_MATCHES_ALL).replace(xml) { fila ->
            val celdas = fila.value.split("</w:tc>").map { sinEtiquetas(it.replace("</w:p>", " ")).trim() }
            "\n" + celdas.filter { it.isNotEmpty() }.joinToString(" | ") + "\n"
        }
        return sinEtiquetas(conFilas.replace("</w:p>", "\n"))
    }

    /**
     * Word antiguo (.doc): el texto va guardado tal cual, en 8 bits o en UTF-16. Se sacan los tramos
     * legibles de ambas formas; \u0007 marca el fin de una celda de tabla y \r el fin de párrafo.
     */
    private fun textoDoc(b: ByteArray): String {
        val legible = Regex("[^A-Za-z0-9áéíóúÁÉÍÓÚñÑüÜ °º%.,;:()/|\\n-]")
        return listOf(String(b, Charsets.UTF_16LE), String(b, charset("windows-1252"))).joinToString("\n") { s ->
            legible.replace(s.replace('\r', '\n').replace("\u0007\u0007", "\n").replace("\u0007", " | "), "\n")
                .lines()
                .filter { l -> l.count { it.isLetter() } >= 4 }
                .joinToString("\n")
        }
    }

    /**
     * Busca la sección de personal ("Personal clave", "Personal técnico requerido"…) y devuelve
     * los cargos que aparecen en ella, uno por línea. Elige, entre todas las veces que el DBC
     * nombra esa sección, la que tiene más cargos (el índice del documento casi no tiene).
     */
    fun personal(texto: String): String? {
        val lineas = texto.lines().map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        val norm = lineas.map { normalizar(it) }
        val inicios = norm.indices.filter { i ->
            norm[i].contains("personal") &&
                listOf("clave", "tecnico", "requerido", "minimo", "profesional").any { norm[i].contains(it) }
        }
        var mejor: List<String> = emptyList()
        for (i in inicios) {
            val encontrados = (i + 1 until minOf(norm.size, i + 70))
                .filter { j ->
                    val n = norm[j]
                    lineas[j].length in 4..300 &&
                        CARGOS.any { n.contains(it) } &&
                        !n.contains("personal") &&
                        DESCARTAR.none { n.startsWith(it) }
                }
                .map { lineas[it] }
            if (encontrados.size > mejor.size) mejor = encontrados
        }
        if (mejor.isEmpty()) return null
        val cargos = mejor
            .map { it.trimStart('-', '•', '*', '·', ' ').replace(Regex("^\\d{1,2}[.)-]?\\s+"), "").trimStart('|', ' ').take(140) }
            .distinctBy { normalizar(it) }
            .take(20)
        return "Personal requerido según el DBC (${cargos.size}):\n" + cargos.joinToString("\n") { "• $it" }
    }
}
