package bo.alertasicoes

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Revisa en el SICOES todas las entidades vigiladas y avisa las convocatorias nuevas. */
object Monitor {
    private val mutex = Mutex()

    class Resultado(val nuevas: Int, val errores: List<String>)

    suspend fun revisar(ctx: Context, automatica: Boolean = false): Resultado = mutex.withLock {
        withContext(Dispatchers.IO) { revisarAhora(ctx.applicationContext, automatica) }
    }

    private fun revisarAhora(ctx: Context, automatica: Boolean): Resultado {
        val almacen = Almacen(ctx)
        val vigilancias = almacen.vigilancias()
        val previas = almacen.convocatorias()
        val vistos = almacen.vistos().toMutableMap()
        val ahora = System.currentTimeMillis()

        val resultado = ArrayList<Convocatoria>()
        val nuevas = ArrayList<Convocatoria>()
        val errores = ArrayList<String>()
        val inicializadas = HashSet<String>()
        val cliente = SicoesCliente()

        for (v in vigilancias) {
            try {
                val buscada = normalizar(v.entidad)
                val filas = cliente.buscar(v.entidad, v.tipos)
                    .filter { normalizar(it.estado).contains("vigente") || it.estado.isBlank() }
                    .filter { !v.exacta || normalizar(it.entidad) == buscada }

                val primeraVez = !almacen.inicializada(v.id)
                for (f in filas) {
                    val clave = v.id + "|" + f.cuce
                    val yaVista = vistos.containsKey(clave)
                    val detectado = vistos.getOrPut(clave) { ahora }
                    val anterior = previas.firstOrNull { it.vigilanciaId == v.id && it.cuce == f.cuce }
                    val inicial = anterior?.inicial ?: (primeraVez && !yaVista)
                    val c = Convocatoria.desdeFila(f, v.id, detectado, inicial)
                    resultado += c
                    if (!yaVista && !primeraVez) nuevas += c
                }
                inicializadas += v.id
            } catch (e: Exception) {
                errores += "${v.entidad}: ${e.message ?: e.javaClass.simpleName}"
                // Conservamos lo que ya sabíamos de esa entidad
                resultado += previas.filter { it.vigilanciaId == v.id }
            }
        }

        // Limpieza: olvidar entidades borradas y registros de más de un año
        val ids = vigilancias.map { it.id }.toSet()
        val limite = ahora - 365L * 24 * 3600 * 1000
        vistos.entries.removeAll { (k, t) -> k.substringBefore('|') !in ids || t < limite }

        almacen.guardarResultado(
            convocatorias = resultado,
            vistos = vistos,
            revisadas = vigilancias,
            inicializadas = inicializadas,
            momento = ahora,
            error = errores.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            automatica = automatica,
        )

        if (nuevas.isNotEmpty()) Notificador.avisarNuevas(ctx, nuevas)
        return Resultado(nuevas.size, errores)
    }
}
