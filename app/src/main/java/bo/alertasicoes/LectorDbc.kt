package bo.alertasicoes

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Lee el texto del DBC (PDF) y busca el personal que pide la entidad. */
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

    fun texto(ctx: Context, pdf: File): String {
        PDFBoxResourceLoader.init(ctx)
        PDDocument.load(pdf).use { doc ->
            val lector = PDFTextStripper()
            lector.endPage = minOf(doc.numberOfPages, 150)
            return lector.getText(doc)
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
                    lineas[j].length in 4..160 &&
                        CARGOS.any { n.contains(it) } &&
                        !n.contains("personal") &&
                        DESCARTAR.none { n.startsWith(it) || n.contains(" $it ") }
                }
                .map { lineas[j] }
            if (encontrados.size > mejor.size) mejor = encontrados
        }
        if (mejor.isEmpty()) return null
        val cargos = mejor
            .map { it.trimStart('-', '•', '*', '·', ' ').replace(Regex("^\\d{1,2}[.)-]?\\s+"), "") }
            .distinctBy { normalizar(it) }
            .take(20)
        return "Personal requerido según el DBC (${cargos.size}):\n" + cargos.joinToString("\n") { "• $it" }
    }
}
