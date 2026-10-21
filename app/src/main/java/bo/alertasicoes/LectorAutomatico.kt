package bo.alertasicoes

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import java.io.File
import java.io.FileOutputStream

/**
 * Mientras la app está abierta, hace sola lo mismo que los botones "Form 100" y "Ver DBC", en un
 * WebView que no se ve y de a una convocatoria por vez:
 *  - del Formulario 100 guarda el precio referencial total,
 *  - del DBC (PDF, Word o .zip) guarda el personal requerido.
 * Si el SICOES pide marcar la casilla de verificación, no se puede hacer sin el usuario: esa
 * convocatoria se salta y quedan los botones de la tarjeta.
 */
@SuppressLint("SetJavaScriptEnabled")
class LectorAutomatico(private val actividad: ComponentActivity) {

    private class Tarea(val cuce: String, val modo: String)

    private val handler = Handler(Looper.getMainLooper())
    private val cola = ArrayDeque<Tarea>()
    private val intentados = HashSet<String>()
    private var actual: Tarea? = null
    private var paso = 0
    private var pausado = false

    private val web = WebView(actividad).apply {
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        addJavascriptInterface(Puente(), "AlertaApp")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) = alTerminar(url ?: "")
        }
        // Tamaño normal (la verificación lo necesita), pero fuera de la pantalla y sin recibir toques
        translationX = -10_000f
        isFocusable = false
    }

    private val vencido = Runnable { siguiente() }

    init {
        actividad.addContentView(web, FrameLayout.LayoutParams(720, 1280))
    }

    /** Agrega a la cola lo que falta: primero los montos (rápidos), luego los DBC. Cada uno se intenta una vez por sesión. */
    fun pedir(sinMonto: List<String>, sinPersonal: List<String>) {
        val nuevas = sinMonto.map { Tarea(it, FichaActivity.MODO_FORM100) } + sinPersonal.map { Tarea(it, FichaActivity.MODO_DBC) }
        nuevas.filter { t -> clave(t) !in intentados && cola.none { clave(it) == clave(t) } && actual?.let(::clave) != clave(t) }
            .forEach { cola.addLast(it) }
        if (actual == null && !pausado) siguiente()
    }

    /** Al salir de la pantalla: se detiene para no gastar batería ni chocar con los botones de la tarjeta. */
    fun pausar() {
        pausado = true
        handler.removeCallbacks(vencido)
        actual?.let { intentados.remove(clave(it)); cola.addFirst(it) }
        actual = null
        web.stopLoading()
        web.loadUrl("about:blank")
    }

    fun reanudar() {
        pausado = false
        if (actual == null) siguiente()
    }

    fun destruir() {
        handler.removeCallbacks(vencido)
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
    }

    private fun clave(t: Tarea) = t.modo + "|" + t.cuce

    private fun siguiente() {
        handler.removeCallbacks(vencido)
        if (pausado) return
        val t = cola.removeFirstOrNull()
        actual = t
        if (t == null) {
            web.loadUrl("about:blank")
            return
        }
        intentados += clave(t)
        paso = 0
        // Si no termina a tiempo, pasa a la siguiente (el DBC puede pesar varios MB)
        handler.postDelayed(vencido, if (t.modo == FichaActivity.MODO_DBC) 150_000L else 60_000L)
        web.loadUrl("${SicoesCliente.BASE}/portal/index.php")
    }

    private fun alTerminar(url: String) {
        val t = actual ?: return
        web.evaluateJavascript(FichaActivity.JS_GANCHO, null)
        when {
            paso == 0 && url.contains("index.php") -> {
                paso = 1
                web.evaluateJavascript(
                    "(function(){var t=document.getElementById('token');" +
                        "location.replace('/portal/contrataciones/busqueda/convocatorias.php?tipo=convNacional&token='+(t?t.value:''));})();",
                    null,
                )
            }
            paso == 1 && url.contains("convocatorias.php") -> {
                paso = 2
                web.evaluateJavascript(FichaActivity.jsAccion(t.cuce, t.modo), null)
            }
        }
    }

    /** Recibe lo que manda la página: el texto del Formulario 100 o el archivo del DBC. */
    private inner class Puente {
        private var archivo: File? = null
        private var salida: FileOutputStream? = null

        @JavascriptInterface
        fun formulario(texto: String) {
            val monto = FichaActivity.extraerMonto(texto) ?: return // quizá aún está cargando
            handler.post {
                val t = actual ?: return@post
                if (t.modo != FichaActivity.MODO_FORM100) return@post
                Almacen(actividad).guardarMonto(t.cuce, monto)
                siguiente()
            }
        }

        @JavascriptInterface
        fun error(texto: String) {
            handler.post { siguiente() }
        }

        @JavascriptInterface
        fun info(texto: String) {}

        @JavascriptInterface
        fun documentos(json: String) {}

        @JavascriptInterface
        fun inicio(nombre: String, mime: String) {
            val dir = File(actividad.cacheDir, "auto").apply { mkdirs() }
            archivo = File(dir, "dbc").also { salida = FileOutputStream(it) }
        }

        @JavascriptInterface
        fun parte(b64: String) {
            salida?.write(Base64.decode(b64, Base64.DEFAULT))
        }

        @JavascriptInterface
        fun fin() {
            salida?.close()
            salida = null
            val f = archivo ?: return
            val cuce = actual?.cuce ?: return
            // Se analiza aquí mismo (hilo del puente, no el de la pantalla) y se borra el archivo
            val resumen = LectorDbc.analizar(actividad.applicationContext, f)
            f.delete()
            Almacen(actividad).guardarPersonal(cuce, resumen)
            handler.post { siguiente() }
        }
    }
}
