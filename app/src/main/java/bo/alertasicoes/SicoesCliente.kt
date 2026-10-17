package bo.alertasicoes

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Consulta la búsqueda de convocatorias del SICOES igual que lo hace la página:
 *  1. Abre portal/index.php (crea la sesión y entrega un token).
 *  2. Abre la página de convocatorias con ese token (entrega un token nuevo y
 *     el orden de las columnas de la tabla).
 *  3. Pide los resultados por POST a portal/contrataciones/operacion.php,
 *     10 por página (el número de página va en "draw").
 * Si el SICOES invalida la sesión, se vuelve a abrir automáticamente.
 */
class SicoesCliente {

    private val jar = object : CookieJar {
        private val lista = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (c in cookies) {
                lista.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                lista.add(c)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val ahora = System.currentTimeMillis()
            lista.removeAll { it.expiresAt < ahora }
            return lista.filter { it.matches(url) }
        }
    }

    private val http = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var token: String? = null
    private var columnas: List<String> = emptyList()
    private var urlConvocatorias: String = "$BASE/portal/contrataciones/busqueda/convocatorias.php?tipo=convNacional"

    private fun get(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "es-BO,es;q=0.9")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("El SICOES respondió con error ${r.code}")
            return r.body?.string() ?: ""
        }
    }

    private fun abrirSesion() {
        val inicio = Jsoup.parse(get("$BASE/portal/index.php"))
        val t1 = inicio.selectFirst("input#token")?.attr("value").orEmpty()
        if (t1.isBlank()) throw IOException("No se pudo iniciar sesión en el SICOES (sin token)")

        urlConvocatorias = "$BASE/portal/contrataciones/busqueda/convocatorias.php?tipo=convNacional&token=$t1"
        val pag = Jsoup.parse(get(urlConvocatorias))
        val t2 = (pag.selectFirst("form#formSimple input[name=token]")?.attr("value")
            ?: pag.selectFirst("input#token")?.attr("value")).orEmpty()
        if (t2.isBlank()) throw IOException("No se pudo abrir la búsqueda de convocatorias del SICOES")

        val cols = pag.selectFirst("input#colsConvocatoriasSimple")?.attr("value").orEmpty()
        if (cols.isBlank()) throw IOException("El SICOES cambió su página de convocatorias (sin columnas)")
        val arr = JSONArray(cols)
        columnas = List(arr.length()) { arr.getString(it) }
        token = t2
    }

    private class Pagina(val total: Int, val filas: List<FilaSicoes>)

    /** Devuelve null si el SICOES no aceptó la sesión (hay que reabrirla). */
    private fun pedirPagina(entidad: String, tipos: Set<String>, pagina: Int): Pagina? {
        val tk = token ?: return null
        val form = FormBody.Builder()
            .add("token", tk)
            .add("entidad", entidad)
            .add("objetoContrato", "")
            .add("publicacionDesde", "")
            .add("publicacionHasta", "")
            .add("presentacionPropuestasDesde", "")
            .add("presentacionPropuestasHasta", "")
        for (i in 1..6) form.add("cuce$i", "")
        form.add("r1", "11") // 11 = Vigente
            .add("subasta", "")
        if ("B" in tipos) form.add("bienes", "B")
        if ("O" in tipos) form.add("obras", "O")
        if ("S" in tipos) form.add("servicios", "S")
        if ("C" in tipos) form.add("consultoria", "C")
        form.add("tipo", "Simple")
            .add("operacion", "convNacional")
            .add("nroRegistros", "10")
            .add("draw", pagina.toString())
            .add("start", "0")
            .add("length", "10")
            .add("captcha", "")

        val req = Request.Builder()
            .url("$BASE/portal/contrataciones/operacion.php")
            .post(form.build())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", BASE)
            .header("Referer", urlConvocatorias)
            .build()

        val cuerpo = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            r.body?.string().orEmpty().trim()
        }
        if (!cuerpo.startsWith("{")) return null // nos devolvió al inicio: sesión vencida

        val json = JSONObject(cuerpo)
        val total = json.optInt("recordsTotal", 0)
        val data = json.optJSONArray("data") ?: JSONArray()
        val filas = ArrayList<FilaSicoes>()
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            fun col(n: Int): String {
                val clave = columnas.getOrNull(n) ?: return ""
                if (!o.has(clave) || o.isNull(clave)) return ""
                return limpiar(o.optString(clave))
            }
            val cuce = col(0)
            if (cuce.isBlank()) continue
            filas += FilaSicoes(
                cuce = cuce,
                entidad = col(1),
                tipo = col(2),
                modalidad = col(3),
                objeto = col(4),
                fechaPublicacion = col(6),
                fechaPresentacion = col(7),
                estado = col(8),
            )
        }
        return Pagina(total, filas)
    }

    /**
     * Todas las convocatorias VIGENTES cuya entidad contiene [entidad],
     * de los tipos indicados (O, B, S, C).
     */
    fun buscar(entidad: String, tipos: Set<String>, maxPaginas: Int = 30): List<FilaSicoes> {
        val texto = quitarTildes(entidad.trim())
        if (token == null) abrirSesion()
        val res = ArrayList<FilaSicoes>()
        var pagina = 1
        while (true) {
            var p = pedirPagina(texto, tipos, pagina)
            if (p == null) {
                abrirSesion()
                p = pedirPagina(texto, tipos, pagina)
                    ?: throw IOException("El SICOES rechazó la consulta; se intentará en la próxima revisión")
            }
            res += p.filas
            if (p.filas.isEmpty() || res.size >= p.total || pagina >= maxPaginas) break
            pagina++
            Thread.sleep(400) // no saturar al servidor
        }
        return res.distinctBy { it.cuce }
    }

    /** Nombres de entidades con convocatorias vigentes que contienen el texto. */
    fun nombresEntidad(texto: String): List<String> =
        buscar(texto, setOf("O", "B", "S", "C"), maxPaginas = 5)
            .map { it.entidad }
            .filter { it.isNotBlank() }
            .distinctBy { normalizar(it) }
            .sorted()

    private fun limpiar(s: String): String =
        Parser.unescapeEntities(s.replace(Regex("<[^>]*>"), " "), false)
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun quitarTildes(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    companion object {
        const val BASE = "https://www.sicoes.gob.bo"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"
    }
}
