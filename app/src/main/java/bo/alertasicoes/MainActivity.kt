package bo.alertasicoes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val pedirPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Cambia cada vez que el usuario vuelve a la app: dispara la revisión al entrar. */
    private val entradas = mutableIntStateOf(0)
    private var lector: LectorAutomatico? = null

    override fun onResume() {
        super.onResume()
        entradas.intValue++
        lector?.reanudar()
    }

    override fun onPause() {
        super.onPause()
        lector?.pausar()
    }

    override fun onDestroy() {
        lector?.destruir()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val oscuro = isSystemInDarkTheme()
            val colores = if (oscuro) {
                darkColorScheme(primary = Color(0xFF7CC4E4), secondary = Color(0xFFF4B740), tertiary = Color(0xFF81C784))
            } else {
                lightColorScheme(
                    primary = Color(0xFF0B4F6C),
                    secondary = Color(0xFFB87800),
                    tertiary = Color(0xFF1B7A3A),
                    surfaceVariant = Color(0xFFE8EEF1),
                )
            }
            MaterialTheme(colorScheme = colores) {
                PantallaPrincipal(
                    entradas = entradas.intValue,
                    onFaltanDatos = { sinMonto, sinPersonal -> lector?.pedir(sinMonto, sinPersonal) },
                )
            }
        }
        lector = LectorAutomatico(this)
    }
}

private val fmtFechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT)
private fun fechaHora(ms: Long): String = fmtFechaHora.format(Date(ms))
private fun hora(h: Int): String = String.format(Locale.ROOT, "%02d:00", h)

private fun abrirSicoes(ctx: Context, cuce: String, modo: String) {
    ctx.startActivity(
        Intent(ctx, FichaActivity::class.java)
            .putExtra(FichaActivity.EXTRA_CUCE, cuce)
            .putExtra(FichaActivity.EXTRA_MODO, modo)
    )
}

private fun compartir(ctx: Context, c: Convocatoria, monto: String?) {
    val texto = buildString {
        append(c.objeto).append("\n")
        append(c.entidad).append("\n")
        append(c.tipo).append(" · ").append(c.modalidad).append("\n")
        if (monto != null) append("Precio referencial: ").append(monto).append("\n")
        append("CUCE: ").append(c.cuce).append("\n")
        append("Publicación: ").append(c.fechaPublicacion).append("\n")
        append("Presentación: ").append(c.fechaPresentacion)
    }
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
    ctx.startActivity(Intent.createChooser(i, "Compartir convocatoria"))
}

/** "(faltan 5 días)", "(es hoy)", "(plazo vencido)" según la fecha de presentación. */
private fun plazo(fechaPresentacion: String): String {
    val f = fechaMs(fechaPresentacion) ?: return ""
    val hoy = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dias = Math.round((f - hoy) / 86_400_000.0).toInt()
    return when {
        dias < 0 -> "  (plazo vencido)"
        dias == 0 -> "  (es hoy)"
        dias == 1 -> "  (falta 1 día)"
        else -> "  (faltan $dias días)"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipal(entradas: Int, onFaltanDatos: (List<String>, List<String>) -> Unit) {
    val ctx = LocalContext.current
    val almacen = remember { Almacen(ctx) }
    var version by remember { mutableIntStateOf(0) }

    // Se actualiza sola cuando la revisión en segundo plano guarda datos nuevos
    DisposableEffect(Unit) {
        val oyente = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        almacen.prefs.registerOnSharedPreferenceChangeListener(oyente)
        onDispose { almacen.prefs.unregisterOnSharedPreferenceChangeListener(oyente) }
    }

    val vigilancias = remember(version) { almacen.vigilancias() }
    val convocatorias = remember(version) { almacen.convocatorias() }
    val montos = remember(version) { almacen.montos() }
    val personal = remember(version) { almacen.personal() }
    val ultima = remember(version) { almacen.ultimaRevision() }
    val ultimaAuto = remember(version) { almacen.ultimaAutomatica() }
    val error = remember(version) { almacen.ultimoError() }
    val intervalo = remember(version) { almacen.intervaloMin() }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var cargando by remember { mutableStateOf(false) }
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var dialogo by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Vigilancia?>(null) }
    var plantilla by remember { mutableStateOf<String?>(null) }
    val busqueda = remember { EstadoBusqueda() }

    // Al entrar no se avisa "sin novedades", solo si hay nuevas o un problema
    val revisar: (Boolean) -> Unit = { silencioso ->
        if (!cargando) {
            cargando = true
            scope.launch {
                val r = Monitor.revisar(ctx)
                cargando = false
                val msg = when {
                    r.errores.isNotEmpty() -> "No se pudo revisar: ${r.errores.first()}"
                    r.nuevas > 0 -> "${r.nuevas} convocatoria(s) nueva(s)"
                    silencioso -> null
                    else -> "Revisado. Sin convocatorias nuevas."
                }
                if (msg != null) snackbar.showSnackbar(msg)
            }
        }
    }
    val revisarAhora: () -> Unit = { revisar(false) }

    // Cada vez que se entra a la app se actualiza (salvo que se haya revisado hace menos de 1 minuto)
    LaunchedEffect(entradas) {
        if (vigilancias.isNotEmpty() && System.currentTimeMillis() - almacen.ultimaRevision() > 60_000) revisar(true)
    }

    // Busca solo el monto y el personal de las convocatorias que aún no los tienen (las más recientes primero)
    LaunchedEffect(convocatorias, montos, personal) {
        val recientes = convocatorias.sortedByDescending { it.detectadoEn }.map { it.cuce }.distinct()
        val sinMonto = recientes.filter { it !in montos }.take(15)
        val sinPersonal = recientes.filter { it !in personal }.take(8)
        if (sinMonto.isNotEmpty() || sinPersonal.isNotEmpty()) onFaltanDatos(sinMonto, sinPersonal)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Alerta SICOES", fontWeight = FontWeight.Bold)
                        Text(
                            if (ultima > 0) "Última revisión: ${fechaHora(ultima)} · cada $intervalo min"
                            else "Aún no se ha revisado",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            if (ultimaAuto > 0) "En segundo plano: ${fechaHora(ultimaAuto)}"
                            else "En segundo plano: aún no corrió",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    if (cargando) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = revisarAhora) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Revisar ahora")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B4F6C),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        floatingActionButton = {
            if (pestana == 2) {
                FloatingActionButton(onClick = { editando = null; plantilla = null; dialogo = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar entidad")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            TabRow(selectedTabIndex = pestana) {
                Tab(
                    selected = pestana == 0,
                    onClick = { pestana = 0 },
                    text = { Text("Alertas (${convocatorias.distinctBy { it.cuce }.size})") },
                )
                Tab(
                    selected = pestana == 1,
                    onClick = { pestana = 1 },
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    text = { Text("Buscar") },
                )
                Tab(
                    selected = pestana == 2,
                    onClick = { pestana = 2 },
                    text = { Text("Entidades (${vigilancias.size})") },
                )
            }
            if (error != null && pestana != 1) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Problema en la última revisión:\n$error",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            when (pestana) {
                0 -> ListaConvocatorias(convocatorias, vigilancias, montos, personal, onIrAEntidades = { pestana = 2 })
                1 -> PantallaBusqueda(
                    estado = busqueda,
                    montos = montos,
                    personal = personal,
                    onVigilar = { entidad ->
                        editando = null
                        plantilla = entidad
                        dialogo = true
                    },
                )
                else -> PantallaEntidades(
                    almacen = almacen,
                    version = version,
                    vigilancias = vigilancias,
                    convocatorias = convocatorias,
                    onEditar = { v -> editando = v; plantilla = null; dialogo = true },
                    onEliminar = { almacen.eliminarVigilancia(it) },
                )
            }
        }
    }

    if (dialogo) {
        DialogoEntidad(
            inicial = editando,
            textoInicial = plantilla,
            onCerrar = { dialogo = false },
            onGuardar = { v ->
                if (editando != null) almacen.actualizarVigilancia(v) else almacen.agregarVigilancia(v)
                dialogo = false
                editando = null
                pestana = 0
                revisarAhora()
            },
        )
    }
}

// ------------------------------------------------------------------
// Alertas (lo que encuentra la revisión automática)
// ------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaConvocatorias(
    lista: List<Convocatoria>,
    vigilancias: List<Vigilancia>,
    montos: Map<String, String>,
    personal: Map<String, String>,
    onIrAEntidades: () -> Unit,
) {
    var filtroTipo by rememberSaveable { mutableStateOf("") }
    var filtroEntidad by rememberSaveable { mutableStateOf("") }

    val visibles = lista
        .filter { filtroTipo.isEmpty() || codigoTipo(it.tipo) == filtroTipo }
        .filter { filtroEntidad.isEmpty() || it.vigilanciaId == filtroEntidad }
        .distinctBy { it.cuce }
        .sortedWith(
            compareByDescending<Convocatoria> { it.detectadoEn }
                .thenByDescending { fechaMs(it.fechaPublicacion) ?: 0L }
        )

    Column(Modifier.fillMaxSize()) {
        ChipsTipo(filtroTipo, onCambio = { filtroTipo = it })
        if (vigilancias.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = filtroEntidad.isEmpty(), onClick = { filtroEntidad = "" }, label = { Text("Todas las entidades") })
                vigilancias.forEach { v ->
                    FilterChip(
                        selected = filtroEntidad == v.id,
                        onClick = { filtroEntidad = if (filtroEntidad == v.id) "" else v.id },
                        label = { Text(v.entidad, maxLines = 1) },
                    )
                }
            }
        }

        when {
            vigilancias.isEmpty() -> Mensaje(
                "Todavía no vigilas ninguna entidad.\nAgrega la entidad que te interesa y la app revisará el SICOES automáticamente.",
                boton = "Agregar entidad",
                onBoton = onIrAEntidades,
            )
            visibles.isEmpty() -> Mensaje("No hay convocatorias vigentes para mostrar.\nToca ⟳ arriba para revisar ahora.")
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibles, key = { it.vigilanciaId + "|" + it.cuce }) { TarjetaConvocatoria(it, montos[it.cuce], personal[it.cuce]) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipsTipo(seleccion: String, onCambio: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = seleccion.isEmpty(), onClick = { onCambio("") }, label = { Text("Todos") })
        TIPOS.forEach { (cod, nombre) ->
            FilterChip(
                selected = seleccion == cod,
                onClick = { onCambio(if (seleccion == cod) "" else cod) },
                label = { Text(nombre) },
            )
        }
    }
}

// ------------------------------------------------------------------
// Búsqueda libre (no se guarda ni se vigila)
// ------------------------------------------------------------------

class EstadoBusqueda {
    var entidad by mutableStateOf("")
    var objeto by mutableStateOf("")
    var tipos by mutableStateOf(setOf("O", "B", "S", "C"))
    var soloVigentes by mutableStateOf(true)
    var buscando by mutableStateOf(false)
    var resultados by mutableStateOf<List<FilaSicoes>?>(null)
    var error by mutableStateOf<String?>(null)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PantallaBusqueda(
    estado: EstadoBusqueda,
    montos: Map<String, String>,
    personal: Map<String, String>,
    onVigilar: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val foco = LocalFocusManager.current

    val buscar: () -> Unit = {
        if (!estado.buscando && (estado.entidad.isNotBlank() || estado.objeto.isNotBlank()) && estado.tipos.isNotEmpty()) {
            foco.clearFocus()
            estado.buscando = true
            estado.error = null
            scope.launch {
                try {
                    estado.resultados = withContext(Dispatchers.IO) {
                        SicoesCliente().buscar(
                            entidad = estado.entidad,
                            tipos = estado.tipos,
                            objeto = estado.objeto,
                            soloVigentes = estado.soloVigentes,
                            maxPaginas = 10,
                        )
                    }
                } catch (e: Exception) {
                    estado.error = "No se pudo consultar el SICOES: ${e.message}"
                }
                estado.buscando = false
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                OutlinedTextField(
                    value = estado.entidad,
                    onValueChange = { estado.entidad = it },
                    label = { Text("Entidad") },
                    placeholder = { Text("Ej.: Municipal de Sucre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { buscar() }),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = estado.objeto,
                    onValueChange = { estado.objeto = it },
                    label = { Text("Objeto de contratación (opcional)") },
                    placeholder = { Text("Ej.: pavimento, puente, unidad educativa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { buscar() }),
                )
                FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TIPOS.forEach { (cod, nombre) ->
                        FilterChip(
                            selected = cod in estado.tipos,
                            onClick = { estado.tipos = if (cod in estado.tipos) estado.tipos - cod else estado.tipos + cod },
                            label = { Text(nombre) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = estado.soloVigentes, onCheckedChange = { estado.soloVigentes = it })
                    Text("Solo vigentes", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = buscar,
                        enabled = !estado.buscando && (estado.entidad.isNotBlank() || estado.objeto.isNotBlank()) && estado.tipos.isNotEmpty(),
                    ) {
                        if (estado.buscando) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Buscar")
                    }
                }
                Text(
                    "Esta búsqueda es solo para consultar: no se guarda ni se revisa sola.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                estado.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                estado.resultados?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (it.isEmpty()) "Sin resultados." else "${it.size} resultado(s)" + if (it.size >= 100) " (se muestran los primeros 100)" else "",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        items(estado.resultados ?: emptyList(), key = { "b|" + it.cuce }) { f ->
            val c = Convocatoria.desdeFila(f, vigilanciaId = "", detectadoEn = 0L, inicial = true)
            TarjetaConvocatoria(c, montos[c.cuce], personal[c.cuce], onVigilar = { onVigilar(f.entidad) })
        }
    }
}

// ------------------------------------------------------------------
// Tarjeta de una convocatoria
// ------------------------------------------------------------------

@Composable
private fun Mensaje(texto: String, boton: String? = null, onBoton: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (boton != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBoton) { Text(boton) }
        }
    }
}

@Composable
private fun Etiqueta(texto: String, fondo: Color, letra: Color) {
    Surface(color = fondo, contentColor = letra, shape = RoundedCornerShape(50)) {
        Text(
            texto,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, destacado: Boolean = false) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(
            "$etiqueta: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            valor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (destacado) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaConvocatoria(c: Convocatoria, monto: String?, personal: String?, onVigilar: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var verPersonal by remember { mutableStateOf(false) }
    if (verPersonal) {
        AlertDialog(
            onDismissRequest = { verPersonal = false },
            title = { Text("Profesionales que pide el DBC") },
            text = {
                Text(
                    personal ?: "Todavía no se leyó el DBC de esta convocatoria. La app lo lee sola mientras está abierta; " +
                        "también puedes tocar \"Ver DBC\" y el resultado aparecerá aquí.",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { verPersonal = false }) { Text("Cerrar") } },
        )
    }
    val nueva = !c.inicial && c.detectadoEn > 0 && System.currentTimeMillis() - c.detectadoEn < 48L * 3600 * 1000
    val colorTipo = when (codigoTipo(c.tipo)) {
        "O" -> Color(0xFF0B4F6C)
        "B" -> Color(0xFF2E7D32)
        "S" -> Color(0xFF6A1B9A)
        else -> Color(0xFF8D5A00)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Etiqueta(c.tipo.ifBlank { "—" }, colorTipo, Color.White)
                if (c.modalidad.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Etiqueta(c.modalidad, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)
                }
                if (nueva) {
                    Spacer(Modifier.width(6.dp))
                    Etiqueta("NUEVA", Color(0xFFC62828), Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Nombre del proyecto: lo más visible de la tarjeta
            Text(
                c.objeto,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(c.entidad, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            // Monto (precio referencial del Formulario 100)
            if (monto != null) {
                Text(
                    monto,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            } else {
                Text(
                    "Monto: buscándolo en el Form 100… (o tócalo aquí)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clickable { abrirSicoes(ctx, c.cuce, FichaActivity.MODO_FORM100) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Dato("Fecha de publicación", c.fechaPublicacion)
            val dia = diaSemana(c.fechaPresentacion)
            Dato(
                "Fecha de presentación",
                c.fechaPresentacion + (if (dia.isNotEmpty()) " ($dia)" else "") + plazo(c.fechaPresentacion),
                destacado = true,
            )
            if (c.detectadoEn > 0) Dato("Detectada por la app", fechaHora(c.detectadoEn))
            Dato("CUCE", c.cuce)
            Dato("Estado", c.estado)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { abrirSicoes(ctx, c.cuce, FichaActivity.MODO_DBC) }) { Text("Ver DBC") }
                OutlinedButton(onClick = { abrirSicoes(ctx, c.cuce, FichaActivity.MODO_FORM100) }) { Text("Form 100") }
                TextButton(onClick = { abrirSicoes(ctx, c.cuce, FichaActivity.MODO_ITEMS) }) { Text("Ítems") }
                TextButton(onClick = { verPersonal = true }) { Text("Profesionales") }
                TextButton(onClick = { compartir(ctx, c, monto) }) { Text("Compartir") }
                if (onVigilar != null) TextButton(onClick = onVigilar) { Text("Vigilar entidad") }
            }
        }
    }
}

// ------------------------------------------------------------------
// Entidades vigiladas y ahorro de batería
// ------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PantallaEntidades(
    almacen: Almacen,
    version: Int,
    vigilancias: List<Vigilancia>,
    convocatorias: List<Convocatoria>,
    onEditar: (Vigilancia) -> Unit,
    onEliminar: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var aBorrar by remember { mutableStateOf<Vigilancia?>(null) }

    val intervalo = remember(version) { almacen.intervaloMin() }
    val horarioActivo = remember(version) { almacen.horarioActivo() }
    val dias = remember(version) { almacen.dias() }
    val desde = remember(version) { almacen.horaDesde() }
    val hasta = remember(version) { almacen.horaHasta() }
    val bateria = remember(version) { almacen.pausarBateriaBaja() }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (vigilancias.isEmpty()) {
            item { Mensaje("Toca + para agregar la entidad que quieres vigilar (por ejemplo, un gobierno municipal).") }
        }
        items(vigilancias, key = { it.id }) { v ->
            val n = convocatorias.count { it.vigilanciaId == v.id }
            Card(Modifier.fillMaxWidth().clickable { onEditar(v) }) {
                Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.entidad, fontWeight = FontWeight.Bold)
                        Text(
                            (if (v.exacta) "Nombre exacto" else "Que contenga el texto") +
                                " · " + TIPOS.filter { it.first in v.tipos }.joinToString(", ") { it.second },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("$n vigente(s) ahora", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onEditar(v) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { aBorrar = v }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Dejar de vigilar")
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Revisar el SICOES cada", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(15, 30, 60, 120, 240).forEach { m ->
                    FilterChip(
                        selected = intervalo == m,
                        onClick = {
                            almacen.setIntervaloMin(m)
                            Programador.programar(ctx, reemplazar = true)
                        },
                        label = { Text(if (m < 60) "$m min" else "${m / 60} h") },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Ahorro de batería", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Revisar solo en horario", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = horarioActivo, onCheckedChange = { almacen.setHorarioActivo(it) })
            }
            if (horarioActivo) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("L", "M", "M", "J", "V", "S", "D").forEachIndexed { i, letra ->
                        val dia = i + 1
                        FilterChip(
                            selected = dia in dias,
                            onClick = { almacen.setDias(if (dia in dias) dias - dia else dias + dia) },
                            label = { Text(letra) },
                        )
                    }
                }
                SelectorHora("Desde", desde) { h -> if (h < hasta) almacen.setHoras(h, hasta) }
                SelectorHora("Hasta", hasta) { h -> if (h > desde) almacen.setHoras(desde, h) }
                Text(
                    "Fuera de ese horario la app no se conecta. Lo publicado de noche te llega en la primera revisión de la mañana.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pausar con batería baja", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = bateria, onCheckedChange = {
                    almacen.setPausarBateriaBaja(it)
                    Programador.programar(ctx, reemplazar = true)
                })
            }
            Spacer(Modifier.height(8.dp))
            val sinRestriccion =
                ctx.getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
            Text(
                "La revisión sigue funcionando con la app cerrada: es una consulta pequeña cada " +
                    "$intervalo min, de bajo consumo. Para que Android no la frene, la app debe estar sin restricciones de batería.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (sinRestriccion) {
                Text("✓ Sin restricciones de batería", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            } else {
                Button(onClick = {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${ctx.packageName}"))
                        )
                    } catch (e: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }) { Text("Permitir revisar con la app cerrada") }
            }
            Text(
                "En Xiaomi, Redmi, Huawei u Oppo activa también \"Inicio automático\" para Alerta SICOES en los ajustes de la app. " +
                    "No la cierres con \"Forzar detención\": eso sí detiene los avisos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    aBorrar?.let { v ->
        AlertDialog(
            onDismissRequest = { aBorrar = null },
            title = { Text("Dejar de vigilar") },
            text = { Text("¿Quitar \"${v.entidad}\"? Se borrarán sus convocatorias de la app.") },
            confirmButton = { TextButton(onClick = { onEliminar(v.id); aBorrar = null }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { aBorrar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SelectorHora(etiqueta: String, valor: Int, onCambio: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { if (valor > 0) onCambio(valor - 1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(hora(valor), fontWeight = FontWeight.Bold)
        TextButton(onClick = { if (valor < 24) onCambio(valor + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

// ------------------------------------------------------------------
// Agregar / editar entidad
// ------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoEntidad(
    inicial: Vigilancia?,
    textoInicial: String?,
    onCerrar: () -> Unit,
    onGuardar: (Vigilancia) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var texto by remember { mutableStateOf(inicial?.entidad ?: textoInicial ?: "") }
    var exacta by remember { mutableStateOf(inicial?.exacta ?: (textoInicial != null)) }
    var tipos by remember { mutableStateOf(inicial?.tipos ?: setOf("O")) }
    var sugerencias by remember { mutableStateOf<List<String>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (inicial != null) "Editar entidad" else "Vigilar una entidad") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it; exacta = false },
                    label = { Text("Nombre de la entidad") },
                    placeholder = { Text("Ej.: Gobierno Autonomo Municipal de El Alto") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    enabled = texto.isNotBlank() && !buscando,
                    onClick = {
                        buscando = true
                        aviso = null
                        scope.launch {
                            try {
                                sugerencias = withContext(Dispatchers.IO) { SicoesCliente().nombresEntidad(texto) }
                                if (sugerencias.isEmpty()) {
                                    aviso = "Ahora no hay convocatorias vigentes con ese nombre. Igual puedes guardarla y te avisará cuando publique."
                                }
                            } catch (e: Exception) {
                                aviso = "No se pudo consultar el SICOES: ${e.message}"
                            }
                            buscando = false
                        }
                    },
                ) {
                    if (buscando) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (buscando) "Buscando en el SICOES…" else "Buscar el nombre exacto en el SICOES")
                }
                if (sugerencias.isNotEmpty()) {
                    Text("Toca el nombre correcto:", style = MaterialTheme.typography.labelMedium)
                    sugerencias.forEach { s ->
                        Box(
                            Modifier.fillMaxWidth().clickable {
                                texto = s
                                exacta = true
                                sugerencias = emptyList()
                            }.padding(vertical = 8.dp)
                        ) {
                            Text(s, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = exacta, onCheckedChange = { exacta = it })
                    Text("Solo ese nombre exacto", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text("Tipo de contratación", style = MaterialTheme.typography.labelLarge)
                TIPOS.forEach { (cod, nombre) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = cod in tipos,
                            onCheckedChange = { marcado -> tipos = if (marcado) tipos + cod else tipos - cod },
                        )
                        Text(nombre, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Solo se muestran convocatorias en estado Vigente." +
                        if (inicial != null) " Al guardar los cambios se recarga la lista de esta entidad sin enviar avisos." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = texto.isNotBlank() && tipos.isNotEmpty(),
                onClick = {
                    onGuardar(Vigilancia(inicial?.id ?: UUID.randomUUID().toString(), texto.trim(), exacta, tipos))
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}
