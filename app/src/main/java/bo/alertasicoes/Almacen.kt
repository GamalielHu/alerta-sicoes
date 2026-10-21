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

    /** Cambia las opciones de una entidad ya guardada; la próxima revisión la recarga sin avisar. */
    fun actualizarVigilancia(v: Vigilancia) = synchronized(LOCK) {
        guardarVigilancias(vigilancias().map { if (it.id == v.id) v else it })
        guardarConvocatorias(convocatorias().filter { it.vigilanciaId != v.id })
        prefs.edit().remove("init_${v.id}").apply()
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
        revisadas: List<Vigilancia>,
        inicializadas: Set<String>,
        momento: Long,
        error: String?,
        automatica: Boolean,
    ) = synchronized(LOCK) {
        // Si una entidad se borró o se editó mientras se revisaba, lo revisado ya no vale para ella:
        // se descarta y la próxima revisión la vuelve a cargar sin avisar.
        val actuales = vigilancias().associateBy { it.id }
        val validas = revisadas.filter { actuales[it.id] == it }.map { it.id }.toSet()
        val arr = JSONArray()
        convocatorias.filter { it.vigilanciaId in validas }.forEach { arr.put(it.aJson()) }
        // Las editadas durante la revisión conservan lo que tenían guardado
        convocatorias().filter { it.vigilanciaId in actuales && it.vigilanciaId !in validas }.forEach { arr.put(it.aJson()) }
        val vis = JSONObject()
        vistos.forEach { (k, v) -> vis.put(k, v) }
        val ed = prefs.edit()
            .putString(K_CONVOCATORIAS, arr.toString())
            .putString(K_VISTOS, vis.toString())
            .putLong(K_ULTIMO, momento)
        if (error == null) ed.remove(K_ERROR) else ed.putString(K_ERROR, error)
        if (automatica) ed.putLong(K_ULTIMO_AUTO, momento)
        inicializadas.filter { it in validas }.forEach { ed.putBoolean("init_$it", true) }
        ed.apply()
    }

    // ---------- Montos (sacados del Formulario 100) ----------

    fun montos(): Map<String, String> {
        val o = JSONObject(prefs.getString(K_MONTOS, null) ?: "{}")
        val m = HashMap<String, String>()
        o.keys().forEach { k -> m[k] = o.optString(k) }
        return m
    }

    fun guardarMonto(cuce: String, monto: String) = synchronized(LOCK) {
        val o = JSONObject(prefs.getString(K_MONTOS, null) ?: "{}")
        o.put(cuce, monto)
        prefs.edit().putString(K_MONTOS, o.toString()).apply()
    }

    // ---------- Personal requerido (sacado del DBC) ----------

    fun personal(): Map<String, String> {
        val o = JSONObject(prefs.getString(K_PERSONAL, null) ?: "{}")
        val m = HashMap<String, String>()
        o.keys().forEach { k -> m[k] = o.optString(k) }
        return m
    }

    fun guardarPersonal(cuce: String, resumen: String) = synchronized(LOCK) {
        val o = JSONObject(prefs.getString(K_PERSONAL, null) ?: "{}")
        o.put(cuce, resumen)
        prefs.edit().putString(K_PERSONAL, o.toString()).apply()
    }

    // ---------- Estado y ajustes ----------

    fun ultimaRevision(): Long = prefs.getLong(K_ULTIMO, 0L)
    fun ultimoError(): String? = prefs.getString(K_ERROR, null)

    /** Última revisión hecha en segundo plano (sirve para saber si Android la está dejando correr). */
    fun ultimaAutomatica(): Long = prefs.getLong(K_ULTIMO_AUTO, 0L)

    /** Última vez que Android despertó la tarea de fondo (aunque estuviera fuera de horario). */
    fun ultimoDespertar(): Long = prefs.getLong(K_DESPERTAR, 0L)
    fun marcarDespertar() = prefs.edit().putLong(K_DESPERTAR, System.currentTimeMillis()).apply()

    fun intervaloMin(): Int = prefs.getInt(K_INTERVALO, 30)
    fun setIntervaloMin(m: Int) = prefs.edit().putInt(K_INTERVALO, m).apply()

    /** Revisar solo en ciertos días y horas (ahorro de batería). */
    fun horarioActivo(): Boolean = prefs.getBoolean(K_HORARIO, true)
    fun setHorarioActivo(b: Boolean) = prefs.edit().putBoolean(K_HORARIO, b).apply()

    /** Días habilitados: 1 = lunes … 7 = domingo. */
    fun dias(): Set<Int> = (prefs.getString(K_DIAS, null) ?: "12345").mapNotNull { it.digitToIntOrNull() }.toSet()
    fun setDias(d: Set<Int>) = prefs.edit().putString(K_DIAS, d.sorted().joinToString("")).apply()

    fun horaDesde(): Int = prefs.getInt(K_DESDE, 7)
    fun horaHasta(): Int = prefs.getInt(K_HASTA, 20)
    fun setHoras(desde: Int, hasta: Int) = prefs.edit().putInt(K_DESDE, desde).putInt(K_HASTA, hasta).apply()

    fun pausarBateriaBaja(): Boolean = prefs.getBoolean(K_BATERIA, true)
    fun setPausarBateriaBaja(b: Boolean) = prefs.edit().putBoolean(K_BATERIA, b).apply()

    /** ¿Toca revisar ahora según el horario? */
    fun dentroDeHorario(ahora: java.util.Calendar = java.util.Calendar.getInstance()): Boolean {
        if (!horarioActivo()) return true
        val dia = ((ahora.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7) + 1 // lunes = 1
        val hora = ahora.get(java.util.Calendar.HOUR_OF_DAY)
        return dia in dias() && hora >= horaDesde() && hora < horaHasta()
    }

    companion object {
        private val LOCK = Any()
        private const val K_VIGILANCIAS = "vigilancias"
        private const val K_CONVOCATORIAS = "convocatorias"
        private const val K_VISTOS = "vistos"
        private const val K_ULTIMO = "ultima_revision"
        private const val K_ERROR = "ultimo_error"
        private const val K_ULTIMO_AUTO = "ultima_revision_auto"
        private const val K_PERSONAL = "personal"
        private const val K_DESPERTAR = "ultimo_despertar"
        private const val K_INTERVALO = "intervalo_min"
        private const val K_MONTOS = "montos_total" // "montos" guardaba a veces el precio de un ítem
        private const val K_HORARIO = "horario_activo"
        private const val K_DIAS = "horario_dias"
        private const val K_DESDE = "horario_desde"
        private const val K_HASTA = "horario_hasta"
        private const val K_BATERIA = "pausar_bateria_baja"
    }
}
