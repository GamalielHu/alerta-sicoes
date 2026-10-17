package bo.alertasicoes

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Abre la página real del SICOES dentro de la app para:
 *  - "dbc":    descargar y abrir el Documento Base de Contratación,
 *  - "form100": ver el Formulario 100 (y guardar el precio referencial),
 *  - "ficha":  descargar la ficha del proceso.
 *
 * El SICOES protege estas descargas con la verificación de Cloudflare.
 * Esa verificación corre aquí igual que en el navegador del celular; la app no la
 * salta: solo pulsa por ti el mismo enlace de la página y recibe el archivo
 * que el SICOES entrega. Si Cloudflare pide marcar una casilla, aparece en pantalla.
 */
class FichaActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var estado: TextView
    private lateinit var progreso: ProgressBar
    private var cuce = ""
    private var modo = MODO_DBC
    private var paso = 0 // 0: inicio, 1: yendo a convocatorias / ficha, 2: acción lanzada

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cuce = (intent.getStringExtra(EXTRA_CUCE) ?: "").replace(Regex("[^0-9A-Za-z-]"), "")
        modo = intent.getStringExtra(EXTRA_MODO) ?: MODO_DBC

        estado = TextView(this).apply {
            setPadding(32, 20, 32, 20)
            setBackgroundColor(0xFF0B4F6C.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
        }
        progreso = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }
        web = WebView(this)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(estado, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(progreso, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        estado.gravity = Gravity.START
        setContentView(raiz)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Puente(), "AlertaApp")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) = alTerminar(url ?: "")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        mostrar(
            when (modo) {
                MODO_FORM100 -> "Abriendo el Formulario 100 de $cuce…"
                MODO_FICHA -> "Abriendo la ficha de $cuce…"
                else -> "Buscando el DBC de $cuce…"
            },
            cargando = true,
        )
        web.loadUrl("${SicoesCliente.BASE}/portal/index.php")
    }

    private fun mostrar(texto: String, cargando: Boolean) = runOnUiThread {
        estado.text = texto
        progreso.visibility = if (cargando) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun alTerminar(url: String) {
        // Siempre: interceptar descargas para que el archivo llegue a la app
        web.evaluateJavascript(JS_GANCHO, null)

        when {
            paso == 0 && url.contains("index.php") -> {
                paso = 1
                val destino = if (modo == MODO_FICHA) {
                    "/portal/contrataciones/ficha/fichaProceso.php?cp=$cuce&token="
                } else {
                    "/portal/contrataciones/busqueda/convocatorias.php?tipo=convNacional&token="
                }
                web.evaluateJavascript(
                    "(function(){var t=document.getElementById('token');location.replace('$destino'+(t?t.value:''));})();",
                    null,
                )
            }
            paso == 1 && url.contains("fichaProceso.php") -> {
                paso = 2
                mostrar("Toca \"Descargar Ficha\" (si aparece la casilla de verificación, márcala primero).", cargando = false)
            }
            paso == 1 && url.contains("convocatorias.php") -> {
                paso = 2
                web.evaluateJavascript(jsAccion(), null)
            }
        }
    }

    private fun jsAccion(): String = """
        (function(cuce, modo){
          try {
            var f = document.getElementById('formSimple');
            var p = cuce.split('-');
            for (var i = 0; i < 6; i++) { var el = f.querySelector('[name=cuce' + (i + 1) + ']'); if (el) el.value = p[i] || ''; }
            ['entidad', 'objetoContrato'].forEach(function(n){ var el = f.querySelector('[name=' + n + ']'); if (el) el.value = ''; });
            var todos = f.querySelector('input[name=r1][value=""]'); if (todos) todos.checked = true;
            ['bienes','obras','servicios','consultoria'].forEach(function(n){ var c = f.querySelector('[name=' + n + ']'); if (c) c.checked = true; });
            buscarConvocatorias('Simple', 'tablaSimple', 'formSimple', 1, 'convNacional');
            var cols = JSON.parse(document.getElementById('colsConvocatoriasSimple').value);
            var datos = window.tablaData || [];
            var fila = null;
            for (var j = 0; j < datos.length; j++) { if (datos[j][cols[0]] == cuce) { fila = datos[j]; break; } }
            if (!fila) { AlertaApp.error('No encontré el proceso ' + cuce + ' en la búsqueda del SICOES.'); return; }
            function dec(v){ v = v || ''; try { if (v.indexOf('%') === 0) v = decodeURIComponent(v); } catch(e) {} return v; }
            function enlaces(html, funcion){
              var d = document.createElement('div'); d.innerHTML = html; var r = [];
              d.querySelectorAll('a').forEach(function(a){
                var oc = a.getAttribute('onclick') || a.getAttribute('onClick') || '';
                var m = oc.match(new RegExp(funcion + "\\('([^']+)'"));
                if (m) r.push({ texto: a.textContent.trim(), link: m[1] });
              });
              return r;
            }
            var docs = enlaces(dec(fila[cols[9]]), 'descargarArchivo');
            AlertaApp.documentos(JSON.stringify(docs));
            if (modo == 'form100') {
              var fs = enlaces(dec(fila[cols[10]]), 'verFormulario');
              var f100 = fs.filter(function(x){ return /100/.test(x.texto); })[0] || fs[0];
              if (!f100) { AlertaApp.error('Este proceso no tiene Formulario 100 publicado.'); return; }
              AlertaApp.info('Verificando con el SICOES y abriendo el Formulario 100…');
              verFormulario(f100.link);
            } else {
              var dbc = docs.filter(function(x){ return /documento\s+base/i.test(x.texto); })[0];
              if (!dbc) { AlertaApp.error('Este proceso no tiene DBC publicado. Abajo puedes tocar otro documento.'); return; }
              AlertaApp.info('Verificando con el SICOES y descargando el DBC…');
              descargarArchivo(dbc.link);
            }
          } catch (e) { AlertaApp.error('No se pudo completar: ' + e); }
        })(${JSONObject.quote(cuce)}, ${JSONObject.quote(modo)});
    """.trimIndent()

    /** Recibe lo que la página manda a la app. */
    inner class Puente {
        private var archivo: File? = null
        private var salida: FileOutputStream? = null
        private var tipo = ""

        @JavascriptInterface
        fun info(texto: String) = mostrar(texto, cargando = true)

        @JavascriptInterface
        fun error(texto: String) = mostrar(texto, cargando = false)

        @JavascriptInterface
        fun documentos(json: String) { /* reservado: lista de documentos del proceso */ }

        @JavascriptInterface
        fun formulario(texto: String) {
            val monto = extraerMonto(texto)
            if (monto != null) {
                Almacen(this@FichaActivity).guardarMonto(cuce, monto)
                mostrar("Formulario 100 · Precio referencial: $monto (guardado en la tarjeta)", cargando = false)
            } else {
                mostrar("Formulario 100 abierto. No encontré el precio referencial en el texto.", cargando = false)
            }
        }

        @JavascriptInterface
        fun inicio(nombre: String, mime: String) {
            val limpio = nombre.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "documento" }
            val dir = File(cacheDir, "docs").apply { mkdirs() }
            archivo = File(dir, limpio)
            salida = FileOutputStream(archivo!!)
            tipo = mime
            mostrar("Recibiendo $limpio…", cargando = true)
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
            runOnUiThread { guardarYAbrir(f, tipo) }
        }
    }

    private fun guardarYAbrir(f: File, mimeRecibido: String) {
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: mimeRecibido.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: "application/pdf"

        // Copia en Descargas/AlertaSICOES para tenerlo a mano
        var enDescargas = false
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val valores = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "${cuce}_${f.name}")
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/AlertaSICOES")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                    enDescargas = true
                }
            } catch (_: Exception) {
            }
        }
        mostrar(
            "Listo: ${f.name}" + if (enDescargas) " · guardado en Descargas/AlertaSICOES" else "",
            cargando = false,
        )

        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.archivos", f)
        val ver = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(ver, "Abrir ${f.name}"))
        } catch (_: Exception) {
            Toast.makeText(this, "No hay una app para abrir este archivo ($mime)", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_CUCE = "cuce"
        const val EXTRA_MODO = "modo"
        const val MODO_DBC = "dbc"
        const val MODO_FORM100 = "form100"
        const val MODO_FICHA = "ficha"

        /** Busca "Precio referencial" (o "Monto") en el texto del Formulario 100. */
        fun extraerMonto(texto: String): String? {
            val t = texto.replace(' ', ' ')
            val patrones = listOf(
                Regex("precio\\s+referencial[^0-9]{0,80}?(\\d{1,3}(?:[.,\\s]\\d{3})*(?:[.,]\\d{1,2})?)", RegexOption.IGNORE_CASE),
                Regex("monto[^0-9]{0,60}?(\\d{1,3}(?:[.,\\s]\\d{3})*(?:[.,]\\d{1,2})?)", RegexOption.IGNORE_CASE),
            )
            for (p in patrones) {
                val m = p.find(t) ?: continue
                val num = m.groupValues[1].trim()
                if (num.replace(Regex("[^0-9]"), "").length >= 3) return "Bs $num"
            }
            return null
        }

        /**
         * Se inyecta en cada página del SICOES abierta en la app. La descarga sigue
         * siendo la del SICOES (con su verificación); solo cambia quién recibe el archivo.
         */
        private val JS_GANCHO = """
            (function(){
              if (window.__alertaGancho) return; window.__alertaGancho = true;
              function enviar(form){
                AlertaApp.info('Descargando…');
                fetch(form.getAttribute('action') || location.href, { method: 'POST', body: new FormData(form), credentials: 'include' })
                  .then(function(r){
                    var cd = r.headers.get('content-disposition') || '';
                    var ct = (r.headers.get('content-type') || '').split(';')[0].trim();
                    return r.blob().then(function(b){ return { cd: cd, ct: ct, b: b }; });
                  })
                  .then(function(x){
                    if (x.ct.indexOf('text/html') === 0 || x.ct.indexOf('application/json') === 0) {
                      return x.b.text().then(function(t){
                        AlertaApp.error('El SICOES no entregó el archivo: ' + t.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 250));
                      });
                    }
                    var m = x.cd.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
                    var nombre = 'documento.pdf';
                    if (m) { try { nombre = decodeURIComponent(m[1]); } catch(e) { nombre = m[1]; } }
                    var rd = new FileReader();
                    rd.onload = function(){
                      var s = rd.result; s = s.substring(s.indexOf(',') + 1);
                      AlertaApp.inicio(nombre, x.ct);
                      for (var i = 0; i < s.length; i += 524288) AlertaApp.parte(s.substring(i, i + 524288));
                      AlertaApp.fin();
                    };
                    rd.readAsDataURL(x.b);
                  })
                  .catch(function(e){ AlertaApp.error('Error al descargar: ' + e); });
              }
              // Archivos de la búsqueda de convocatorias (DBC, planos, etc.)
              if (window.jQuery) {
                jQuery('#formDescargaArchivoPortal').on('submit', function(e){ e.preventDefault(); enviar(this); return false; });
              }
              // Ficha del proceso
              var ficha = document.getElementById('formulario');
              if (ficha && /descargaFicha/.test(ficha.getAttribute('action') || '')) {
                ficha.submit = function(){ enviar(ficha); };
              }
              // Formulario 100: avisar a la app cuando se muestre
              document.querySelectorAll('.contenidoFormulario').forEach(function(c){
                var espera = null, ultimo = '';
                new MutationObserver(function(){
                  clearTimeout(espera);
                  espera = setTimeout(function(){
                    var t = c.innerText || '';
                    if (t.trim().length > 20 && t !== ultimo) { ultimo = t; AlertaApp.formulario(t); }
                  }, 700);
                }).observe(c, { childList: true, subtree: true });
              });
            })();
        """.trimIndent()
    }
}
