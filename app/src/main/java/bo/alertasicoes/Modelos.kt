package bo.alertasicoes

import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale

/** Tipos de contratación que maneja el SICOES (código del formulario → nombre). */
val TIPOS: List<Pair<String, String>> = listOf(
    "O" to "Obras",
    "B" to "Bienes",
    "S" to "Servicios Generales",
    "C" to "Servicios de Consultoría",
)

fun nombreTipo(codigo: String): String = TIPOS.firstOrNull { it.first == codigo }?.second ?: codigo

/** Convierte el texto de tipo que devuelve el SICOES ("Obras", "Bienes"...) a su código. */
fun codigoTipo(textoTipo: String): String {
    val t = normalizar(textoTipo)
    return when {
        t.contains("consult") -> "C"
        t.startsWith("obra") -> "O"
        t.startsWith("bien") -> "B"
        else -> "S"
    }
}

/** Minúsculas, sin tildes y sin espacios repetidos: para comparar nombres de entidades. */
fun normalizar(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()

/** Fecha "dd/MM/yyyy" del SICOES → milisegundos (o null si no se puede leer). */
fun fechaMs(texto: String): Long? {
    if (texto.length < 10) return null
    return try {
        val f = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        f.isLenient = false
        f.parse(texto.substring(0, 10))?.time
    } catch (e: Exception) {
        null
    }
}

private val DIAS = listOf("domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado")

/** Día de la semana de una fecha "dd/MM/yyyy" del SICOES ("lunes", "martes"…), o "" si no se puede leer. */
fun diaSemana(texto: String): String {
    val ms = fechaMs(texto) ?: return ""
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return DIAS[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
}

/** Una entidad que el usuario quiere vigilar. */
data class Vigilancia(
    val id: String,
    val entidad: String,
    val exacta: Boolean,
    val tipos: Set<String>,
) {
    fun aJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("entidad", entidad)
        .put("exacta", exacta)
        .put("tipos", tipos.joinToString(""))

    companion object {
        fun deJson(o: JSONObject) = Vigilancia(
            id = o.getString("id"),
            entidad = o.getString("entidad"),
            exacta = o.optBoolean("exacta", false),
            tipos = o.optString("tipos", "O").map { it.toString() }.toSet(),
        )
    }
}

/** Fila tal como viene del SICOES. */
data class FilaSicoes(
    val cuce: String,
    val entidad: String,
    val tipo: String,
    val modalidad: String,
    val objeto: String,
    val fechaPublicacion: String,
    val fechaPresentacion: String,
    val estado: String,
)

/** Convocatoria guardada en la app, ligada a la entidad vigilada que la encontró. */
data class Convocatoria(
    val cuce: String,
    val entidad: String,
    val tipo: String,
    val modalidad: String,
    val objeto: String,
    val fechaPublicacion: String,
    val fechaPresentacion: String,
    val estado: String,
    val vigilanciaId: String,
    /** Cuándo la detectó la app por primera vez. */
    val detectadoEn: Long,
    /** true si apareció en la primera carga de esa entidad (no se avisa como "nueva"). */
    val inicial: Boolean,
) {
    fun aJson(): JSONObject = JSONObject()
        .put("cuce", cuce)
        .put("entidad", entidad)
        .put("tipo", tipo)
        .put("modalidad", modalidad)
        .put("objeto", objeto)
        .put("fechaPublicacion", fechaPublicacion)
        .put("fechaPresentacion", fechaPresentacion)
        .put("estado", estado)
        .put("vigilanciaId", vigilanciaId)
        .put("detectadoEn", detectadoEn)
        .put("inicial", inicial)

    companion object {
        fun deJson(o: JSONObject) = Convocatoria(
            cuce = o.optString("cuce"),
            entidad = o.optString("entidad"),
            tipo = o.optString("tipo"),
            modalidad = o.optString("modalidad"),
            objeto = o.optString("objeto"),
            fechaPublicacion = o.optString("fechaPublicacion"),
            fechaPresentacion = o.optString("fechaPresentacion"),
            estado = o.optString("estado"),
            vigilanciaId = o.optString("vigilanciaId"),
            detectadoEn = o.optLong("detectadoEn"),
            inicial = o.optBoolean("inicial", false),
        )

        fun desdeFila(f: FilaSicoes, vigilanciaId: String, detectadoEn: Long, inicial: Boolean) = Convocatoria(
            cuce = f.cuce,
            entidad = f.entidad,
            tipo = f.tipo,
            modalidad = f.modalidad,
            objeto = f.objeto,
            fechaPublicacion = f.fechaPublicacion,
            fechaPresentacion = f.fechaPresentacion,
            estado = f.estado,
            vigilanciaId = vigilanciaId,
            detectadoEn = detectadoEn,
            inicial = inicial,
        )
    }
}
