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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontWeight
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
                darkColorScheme(primary = Color(0xFF7CC4E4), secondary = Color(0xFFF4B740), tertiary = Color(0xFFFF8A80))
            } else {
                lightColorScheme(
                    primary = Color(0xFF0B4F6C),
                    secondary = Color(0xFFB87800),
                    tertiary = Color(0xFFC62828),
                    surfaceVariant = Color(0xFFE8EEF1),
                )
            }
            MaterialTheme(colorScheme = colores) { PantallaPrincipal() }
        }
    }
}

private val fmtFechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT)
private fun fechaHora(ms: Long): String = fmtFechaHora.format(Date(ms))

private fun abrirFicha(ctx: Context, cuce: String) {
    ctx.startActivity(Intent(ctx, FichaActivity::class.java).putExtra(FichaActivity.EXTRA_CUCE, cuce))
}

private fun compartir(ctx: Context, c: Convocatoria) {
    val texto = buildString {
        append(c.entidad).append("\n")
        append(c.tipo).append(" · ").append(c.modalidad).append("\n")
        append(c.objeto).append("\n")
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
fun PantallaPrincipal() {
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
    val ultima = remember(version) { almacen.ultimaRevision() }
    val error = remember(version) { almacen.ultimoError() }
    val intervalo = remember(version) { almacen.intervaloMin() }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var cargando by remember { mutableStateOf(false) }
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var dialogoNueva by remember { mutableStateOf(false) }

    val revisarAhora: () -> Unit = {
        if (!cargando) {
            cargando = true
            scope.launch {
                val r = Monitor.revisar(ctx)
                cargando = false
                val msg = when {
                    r.errores.isNotEmpty() -> "No se pudo revisar: ${r.errores.first()}"
                    r.nuevas > 0 -> "${r.nuevas} convocatoria(s) nueva(s)"
                    else -> "Revisado. Sin convocatorias nuevas."
                }
                snackbar.showSnackbar(msg)
            }
        }
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
            if (pestana == 1) {
                FloatingActionButton(onClick = { dialogoNueva = true }) {
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
                    text = { Text("Convocatorias (${convocatorias.distinctBy { it.cuce }.size})") },
                )
                Tab(
                    selected = pestana == 1,
                    onClick = { pestana = 1 },
                    text = { Text("Entidades (${vigilancias.size})") },
                )
            }
            if (error != null) {
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
                0 -> ListaConvocatorias(convocatorias, vigilancias, onIrAEntidades = { pestana = 1 })
                else -> PantallaEntidades(
                    vigilancias = vigilancias,
                    convocatorias = convocatorias,
                    intervalo = intervalo,
                    onEliminar = { almacen.eliminarVigilancia(it) },
                    onIntervalo = { m ->
                        almacen.setIntervaloMin(m)
                        Programador.programar(ctx, m, reemplazar = true)
                    },
                )
            }
        }
    }

    if (dialogoNueva) {
        DialogoEntidad(
            onCerrar = { dialogoNueva = false },
            onGuardar = { v ->
                almacen.agregarVigilancia(v)
                dialogoNueva = false
                pestana = 0
                revisarAhora()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaConvocatorias(
    lista: List<Convocatoria>,
    vigilancias: List<Vigilancia>,
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
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = filtroTipo.isEmpty(), onClick = { filtroTipo = "" }, label = { Text("Todos") })
            TIPOS.forEach { (cod, nombre) ->
                FilterChip(
                    selected = filtroTipo == cod,
                    onClick = { filtroTipo = if (filtroTipo == cod) "" else cod },
                    label = { Text(nombre) },
                )
            }
        }
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
                items(visibles, key = { it.vigilanciaId + "|" + it.cuce }) { TarjetaConvocatoria(it) }
            }
        }
    }
}

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

@Composable
fun TarjetaConvocatoria(c: Convocatoria) {
    val ctx = LocalContext.current
    val nueva = !c.inicial && System.currentTimeMillis() - c.detectadoEn < 48L * 3600 * 1000
    val colorTipo = when (codigoTipo(c.tipo)) {
        "O" -> Color(0xFF0B4F6C)
        "B" -> Color(0xFF2E7D32)
        "S" -> Color(0xFF6A1B9A)
        else -> Color(0xFF8D5A00)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { abrirFicha(ctx, c.cuce) },
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
            Text(c.entidad, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(c.objeto, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Dato("Fecha de publicación", c.fechaPublicacion)
            Dato("Fecha de presentación", c.fechaPresentacion + plazo(c.fechaPresentacion), destacado = true)
            Dato("Detectada por la app", fechaHora(c.detectadoEn))
            Dato("CUCE", c.cuce)
            Dato("Estado", c.estado)
            Row {
                TextButton(onClick = { abrirFicha(ctx, c.cuce) }) { Text("Ver ficha en SICOES") }
                TextButton(onClick = { compartir(ctx, c) }) { Text("Compartir") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEntidades(
    vigilancias: List<Vigilancia>,
    convocatorias: List<Convocatoria>,
    intervalo: Int,
    onEliminar: (String) -> Unit,
    onIntervalo: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    var aBorrar by remember { mutableStateOf<Vigilancia?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (vigilancias.isEmpty()) {
            item { Mensaje("Toca + para agregar la entidad que quieres vigilar (por ejemplo, un gobierno municipal).") }
        }
        items(vigilancias, key = { it.id }) { v ->
            val n = convocatorias.count { it.vigilanciaId == v.id }
            Card(Modifier.fillMaxWidth()) {
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
                        onClick = { onIntervalo(m) },
                        label = { Text(if (m < 60) "$m min" else "${m / 60} h") },
                    )
                }
            }
            Text(
                "Android decide el momento exacto para ahorrar batería, así que la revisión puede retrasarse unos minutos. " +
                    "Para que no la detenga, permite que la app funcione sin restricciones de batería:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }) { Text("Ajustes de batería") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoEntidad(onCerrar: () -> Unit, onGuardar: (Vigilancia) -> Unit) {
    val scope = rememberCoroutineScope()
    var texto by remember { mutableStateOf("") }
    var exacta by remember { mutableStateOf(false) }
    var tipos by remember { mutableStateOf(setOf("O")) }
    var sugerencias by remember { mutableStateOf<List<String>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Vigilar una entidad") },
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
                    "Solo se muestran convocatorias en estado Vigente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = texto.isNotBlank() && tipos.isNotEmpty(),
                onClick = { onGuardar(Vigilancia(UUID.randomUUID().toString(), texto.trim(), exacta, tipos)) },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}
