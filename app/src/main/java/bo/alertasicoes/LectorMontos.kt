package bo.alertasicoes

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity

/**
 * Mientras la app está abierta, abre el Formulario 100 de las convocatorias sin monto en un
 * WebView que no se ve, y guarda el precio referencial total. Es lo mismo que tocar "Form 100",
 * una convocatoria a la vez. Si el SICOES pide marcar la casilla de verificación, no se puede
 * hacer sin el usuario: esa convocatoria se salta y queda el botón "Form 100".
 */
@SuppressLint("SetJavaScriptEnabled")
class LectorMontos(private val actividad: ComponentActivity) {

    private val handler = Handler(Looper.getMainLooper())
    private val cola = ArrayDeque<String>()
    private val intentados = HashSet<String>()
    private var actual: String? = null
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

    /** Agrega a la cola los CUCE que todavía no tienen monto. Cada uno se intenta una vez por sesión. */
    fun pedir(cuces: List<String>) {
        cuces.filter { it !in intentados && it !in cola && it != actual }.forEach { cola.addLast(it) }
        if (actual == null && !pausado) siguiente()
    }

    /** Al salir de la pantalla: se detiene para no gastar batería ni chocar con "Ver DBC"/"Form 100". */
    fun pausar() {
        pausado = true
        handler.removeCallbacks(vencido)
        actual?.let { intentados.remove(it); cola.addFirst(it) }
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

    private fun siguiente() {
        handler.removeCallbacks(vencido)
        if (pausado) return
        val cuce = cola.removeFirstOrNull()
        actual = cuce
        if (cuce == null) {
            web.loadUrl("about:blank")
            return
        }
        intentados += cuce
        paso = 0
        handler.postDelayed(vencido, 60_000) // si en un minuto no aparece, pasa a la siguiente
        web.loadUrl("${SicoesCliente.BASE}/portal/index.php")
    }

    private fun alTerminar(url: String) {
        val cuce = actual ?: return
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
                web.evaluateJavascript(FichaActivity.jsAccion(cuce, FichaActivity.MODO_FORM100), null)
            }
        }
    }

    /** Recibe lo que manda la página. Solo interesa el texto del Formulario 100. */
    private inner class Puente {
        @JavascriptInterface
        fun formulario(texto: String) {
            val monto = FichaActivity.extraerMonto(texto) ?: return // quizá aún está cargando
            handler.post {
                val cuce = actual ?: return@post
                Almacen(actividad).guardarMonto(cuce, monto)
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
        fun inicio(nombre: String, mime: String) {}

        @JavascriptInterface
        fun parte(b64: String) {}

        @JavascriptInterface
        fun fin() {}
    }
}
