package bo.alertasicoes

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * Muestra la ficha del proceso en el SICOES. La ficha exige un token de sesión,
 * así que primero se abre la página de inicio y luego se salta a la ficha.
 */
class FichaActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cuce = intent.getStringExtra(EXTRA_CUCE).orEmpty()
        title = "Ficha $cuce"

        val web = WebView(this)
        CookieManager.getInstance().setAcceptCookie(true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        var redirigido = false
        val cuceSeguro = cuce.replace(Regex("[^0-9A-Za-z-]"), "")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (!redirigido && url?.contains("index.php") == true && cuceSeguro.isNotEmpty()) {
                    redirigido = true
                    view.evaluateJavascript(
                        "(function(){var t=document.getElementById('token');" +
                            "location.replace('/portal/contrataciones/ficha/fichaProceso.php?cp=$cuceSeguro&token='+(t?t.value:''));})();",
                        null,
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack() && web.url?.contains("fichaProceso") != true) web.goBack() else finish()
            }
        })

        setContentView(web)
        web.loadUrl("${SicoesCliente.BASE}/portal/index.php")
    }

    companion object {
        const val EXTRA_CUCE = "cuce"
    }
}
