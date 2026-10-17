package bo.alertasicoes

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Guarda entidades vigiladas, convocatorias y ajustes en el teléfono. */
class Almacen(ctx: Context) {
    val prefs: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("alerta_sicoes", Context.MODE_PRIVATE)

    // ---------- Entidades vigiladas ----------

    fun vigilancias(): List<Vigilancia> {
        val arr = JSONArray(prefs.getString(K_VIGILANCIAS, null) ?: "[]")
        return List(arr.length()) { Vigilancia.deJson(arr.getJSONObject(it)) }
    }

    private fun guardarVigilancias(lista: List<Vigilancia>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.aJson()) }
        prefs.edit().putString(K_VIGILANCIAS, arr.toString()).apply()
    }

    fun agregarVigilancia(v: Vigilancia) = synchronized(LOCK) {
        guardarVigilancias(vigilancias() + v)
    }

    fun eliminarVigilancia(id: String) = synchronized(LOCK) {
        guardarVigilancias(vigilancias().filter { it.id != id })
        guardarConvocatorias(convocatorias().filter { it.vigilanciaId != id })
        prefs.edit().remove("init_$id").apply()
    }

    // ---------- Convocatorias ----------

    fun convocatorias(): List<Convocatoria> {
        val arr = JSONArray(prefs.getString(K_CONVOCATORIAS, null) ?: "[]")
        return List(arr.length()) { Convocatoria.deJson(arr.getJSONObject(it)) }
    }

    private fun guardarConvocatorias(lista: List<Convocatoria>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.aJson()) }
        prefs.edit().putString(K_CONVOCATORIAS, arr.toString()).apply()
    }

    /** Clave "idVigilancia|CUCE" → momento en que se detectó por primera vez. */
    fun vistos(): Map<String, Long> {
        val o = JSONObject(prefs.getString(K_VISTOS, null) ?: "{}")
        val m = HashMap<String, Long>()
        o.keys().forEach { k -> m[k] = o.optLong(k) }
        return m
    }

    fun inicializada(idVigilancia: String) = prefs.getBoolean("init_$idVigilancia", false)

    /** Guarda el resultado de una revisión completa. */
    fun guardarResultado(
        convocatorias: List<Convocatoria>,
        vistos: Map<String, Long>,
        inicializadas: Set<String>,
        momento: Long,
        error: String?,
    ) = synchronized(LOCK) {
        // Por si se borró una entidad mientras se revisaba
        val vigentes = vigilancias().map { it.id }.toSet()
        val arr = JSONArray()
        convocatorias.filter { it.vigilanciaId in vigentes }.forEach { arr.put(it.aJson()) }
        val vis = JSONObject()
        vistos.forEach { (k, v) -> vis.put(k, v) }
        val ed = prefs.edit()
            .putString(K_CONVOCATORIAS, arr.toString())
            .putString(K_VISTOS, vis.toString())
            .putLong(K_ULTIMO, momento)
        if (error == null) ed.remove(K_ERROR) else ed.putString(K_ERROR, error)
        inicializadas.forEach { ed.putBoolean("init_$it", true) }
        ed.apply()
    }

    // ---------- Estado y ajustes ----------

    fun ultimaRevision(): Long = prefs.getLong(K_ULTIMO, 0L)
    fun ultimoError(): String? = prefs.getString(K_ERROR, null)

    fun intervaloMin(): Int = prefs.getInt(K_INTERVALO, 30)
    fun setIntervaloMin(m: Int) = prefs.edit().putInt(K_INTERVALO, m).apply()

    companion object {
        private val LOCK = Any()
        private const val K_VIGILANCIAS = "vigilancias"
        private const val K_CONVOCATORIAS = "convocatorias"
        private const val K_VISTOS = "vistos"
        private const val K_ULTIMO = "ultima_revision"
        private const val K_ERROR = "ultimo_error"
        private const val K_INTERVALO = "intervalo_min"
    }
}
