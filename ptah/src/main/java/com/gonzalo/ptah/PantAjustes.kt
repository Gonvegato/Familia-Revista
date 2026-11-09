package com.gonzalo.ptah

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val VERSION = "0.6.0"

/** «Ajustes»: Estilo (el de serie de tus widgets) · General · Actualizar. Todo se guarda en este móvil. */
@Composable
fun PantAjustes(alVolver: () -> Unit, inicial: Int = 0, alDiagnostico: () -> Unit = {}) {
    var pestana by rememberSaveable { mutableIntStateOf(inicial.coerceIn(0, 2)) }
    EspejoCuenta.version
    val mg = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.padding(horizontal = mg)) { RevistaTopBar(alVolver, "Volver", titulo = "Ajustes") }
        PestanasRevista(listOf("Estilo", "General", "Actualizar"), pestana) { pestana = it }
        Column(Modifier.padding(horizontal = mg).widthIn(max = 760.dp)) {
            Spacer(Modifier.height(16.dp))
            when (pestana) {
                0 -> SeccionApariencia()
                1 -> SeccionGeneral(alDiagnostico)
                else -> SeccionActualizaciones()
            }
            Spacer(Modifier.height(26.dp))
            Filete(1.dp)
            Spacer(Modifier.height(10.dp))
            Etiqueta("Widgets $VERSION")
            Text("Tus widgets de la pantalla de inicio, del tamaño que quieras. Todo se guarda solo en este móvil. ${OpenMeteo.LICENCIA}",
                color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** La contraseña: tapada, con «Ver» para comprobar lo escrito. */
@Composable
fun CampoClave(etiqueta: String, valor: String, alCambiar: (String) -> Unit) {
    var ver by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Etiqueta(etiqueta)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                if (valor.isEmpty()) Text("••••••••", color = Muted, fontFamily = Display, fontSize = 18.sp)
                androidx.compose.foundation.text.BasicTextField(valor, alCambiar, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Charcoal, fontFamily = Display, fontSize = 18.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Acento),
                    visualTransformation = if (ver) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            }
            if (valor.isNotEmpty()) EnlaceRevista(if (ver) "Tapar" else "Ver", Modifier.padding(start = 8.dp)) { ver = !ver }
        }
        Filete(1.5.dp)
    }
}

@Composable
private fun SeccionGeneral(alDiagnostico: () -> Unit) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    Antetitulo("Tamaño del texto de la app", Modifier.padding(bottom = 6.dp))
    val tams = listOf(90 to "Pequeño", 100 to "Normal", 112 to "Grande", 125 to "Muy grande")
    MagChoice(tams.map { it.second }, tams.indexOfFirst { it.first == Skin.escala }.coerceAtLeast(0)) { Skin.escala = tams[it].first; Skin.guardar(ctx) }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Si algo no sale bien", Modifier.padding(bottom = 4.dp))
    MagLink("Qué ve tu móvil", "Los tamaños de tus widgets, la hora, lo guardado: para copiarlo y mandarlo", accent = true) { alDiagnostico() }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    SeccionRelojes()
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Tus widgets", Modifier.padding(bottom = 4.dp))
    var aviso by remember { mutableStateOf<String?>(null) }
    val n = remember { Widgets.ids(ctx).size }
    Text(if (n == 0) "No tienes ningún widget en la pantalla de inicio de este móvil." else "Tienes $n widget${if (n > 1) "s" else ""} en la pantalla de inicio de este móvil. Se vuelven a pintar solos; si alguno se ha quedado atrás:",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    if (n > 0) MagLink("Volver a pintarlos ahora", "Y baja el tiempo, si toca") {
        ambito.launch { withContext(Dispatchers.IO) { Refresco.tiempo(ctx, forzar = true) }; Widgets.pedir(ctx); aviso = "Hecho." }
    }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Este móvil", Modifier.padding(bottom = 4.dp))
    val ocupa by produceState(0L, aviso) { value = withContext(Dispatchers.IO) { java.io.File(ctx.filesDir, "fotos").walkTopDown().filter { it.isFile }.sumOf { it.length() } } }
    Text("Las fotos de tus widgets de foto están solo aquí y ocupan ${Formato.cifra(ocupa / 1_000_000)} MB. El tiempo bajado es caché: se vuelve a pedir solo.",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    MagLink("Borrar el tiempo guardado", "Se vuelve a bajar en la próxima vuelta") {
        ambito.launch { withContext(Dispatchers.IO) { java.io.File(ctx.filesDir, "tiempo").deleteRecursively() }; aviso = "Hecho. Se vuelve a pedir solo." }
    }
    aviso?.let { Nota(it, color = Acento) }
}

/** «Actualizaciones»: Widgets se actualiza sola desde la carpeta «widgets» de tu kDrive, con tu token de kDrive (guardado cifrado en este móvil). */
@Composable
private fun SeccionActualizaciones() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(Actualizador.activo(ctx)) }
    var sobre by remember { mutableStateOf(Actualizador.puedeReabrirSola(ctx)) }
    val estado = Actualizador.estado.ifBlank { Actualizador.estadoGuardado(ctx) }
    DisposableEffect(Unit) {
        val owner = androidx.lifecycle.ProcessLifecycleOwner.get()
        val ob = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) sobre = Actualizador.puedeReabrirSola(ctx) }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }
    Antetitulo("Tu kDrive", Modifier.padding(bottom = 4.dp))
    TokenKDrive()
    Spacer(Modifier.height(14.dp)); Filete(1.dp); Spacer(Modifier.height(6.dp))
    Fila("Actualizar sola", "Mira la carpeta «widgets» de tu kDrive (Widgets-<versión>.apk) y se instala sin preguntar cuando no la usas (nunca con algo en marcha ni con la pantalla apagada).") {
        Interruptor(auto) { auto = it; Actualizador.ponActivo(ctx, it) }
    }
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        var avisos by remember { mutableStateOf(ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
        val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }
        if (!avisos) MagLink("Avisos de actualización: dar permiso", "Para avisarte de una versión nueva que Android no deja instalar sola, y para volver a abrir Widgets al terminar") { pedirAvisos.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
    if (KDrive.on(ctx)) {
        val vers = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
        Text("Versión instalada: $vers" + if (estado.isNotBlank()) " · última comprobación: $estado" else "", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        MagLink(if (ocupado) "Actualizando…" else "Actualizar ahora", "Busca la última versión en tu kDrive y la instala ahora", accent = true) {
            if (!ocupado) { ocupado = true; aviso = null; ambito.launch { aviso = withContext(Dispatchers.IO) { Actualizador.actualizarAhora(ctx) }; ocupado = false } }
        }
        Filete(1.dp, Line)
        MagLink("Solo comprobar") { if (!ocupado) { ocupado = true; ambito.launch { withContext(Dispatchers.IO) { Actualizador.comprueba(ctx, manual = true) }; ocupado = false } } }
        Filete(1.dp, Line)
        MagLink(if (sobre) "Tras actualizar, Widgets se vuelve a abrir sola" else "Permitir que se reabra sola tras actualizar", if (sobre) null else "Hace falta el permiso «Mostrar sobre otras apps»; si no, te avisa para abrirla") {
            if (!sobre) runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))) }
        }
    }
    aviso?.let { Nota(it, Modifier.padding(top = 4.dp), color = Acento) }
}

/** El token de kDrive (para las actualizaciones): pegarlo, comprobarlo y quitarlo. Se guarda cifrado en este móvil. */
@Composable
private fun TokenKDrive() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var drive by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var hay by remember { mutableStateOf(KDrive.on(ctx)) }
    if (hay) {
        Text("Puesto: kDrive ${KDrive.drive(ctx)} (el token, cifrado en este móvil).", color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
        MagLink("Quitar el token", "Widgets deja de mirar tu kDrive") { KDrive.forget(ctx); hay = false }
    } else {
        Text("Un token de la API de Infomaniak (con permiso de kDrive) y el número de tu kDrive (o su dirección: «…/app/drive/123456/…»). Se guarda cifrado en este móvil.",
            color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        CampoClave("Token", token) { token = it }
        Campo("Tu kDrive", drive, "123456") { drive = it }
        if (token.isNotBlank() && drive.isNotBlank()) MagLink(if (ocupado) "Comprobando…" else "Guardar y comprobar", accent = true) {
            if (!ocupado) {
                ocupado = true; aviso = null
                KDrive.save(ctx, token, drive)
                ambito.launch {
                    val r = runCatching { KDrive.test(ctx) }
                    if (r.isSuccess) { KDrive.confirma(ctx); token = ""; drive = ""; hay = true; aviso = r.getOrNull() } else { KDrive.descarta(ctx); aviso = r.exceptionOrNull()?.message ?: "No vale." }
                    ocupado = false
                }
            }
        }
    }
    aviso?.let { Nota(it, Modifier.padding(top = 4.dp), color = Acento) }
}

/** Si hay una versión nueva ya bajada que Android no deja instalar en silencio, se pregunta (o se lleva al ajuste de «instalar apps»). */
@Composable
fun AvisoActualizacion() {
    val ctx = LocalContext.current
    val n = Actualizador.nueva?.takeIf { !it.silenciosa } ?: return
    var luego by remember(n.nombre) { mutableStateOf(false) }
    if (luego) return
    AlertDialog(
        onDismissRequest = { luego = true }, containerColor = Paper, shape = esquina(16.dp),
        title = { Text("Widgets tiene una versión nueva", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (Actualizador.faltaPermiso) "Hay una versión nueva en tu kDrive. Para instalarla, Android tiene que dejar a Widgets instalar apps: te llevo a ese ajuste." else "Hay una versión nueva en tu kDrive, ya descargada. Android te pedirá confirmar la instalación.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            if (Actualizador.faltaPermiso) { Actualizador.faltaPermiso = false; ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))) }
            else runCatching { Actualizador.instala(ctx, n) }.onFailure { android.widget.Toast.makeText(ctx, "No se pudo instalar: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }) { Text(if (Actualizador.faltaPermiso) "Permitir" else "Actualizar ahora", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ luego = true }) { Text("Luego", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold) } }
    )
}


/** La hora de los relojes de la pantalla de inicio: dibujada con tu letra (cada minuto) o con la del móvil. */
@Composable
fun SeccionRelojes() {
    val ctx = LocalContext.current
    var delMovil by remember { mutableStateOf(Minutero.letraDelMovil(ctx)) }
    val exactas = remember { Minutero.puedeExactas(ctx) }
    Antetitulo("La hora de tus relojes", Modifier.padding(bottom = 4.dp))
    Text("Para que la hora salga con la letra de tu diseño, Widgets la dibuja y vuelve a pintar tus relojes al empezar cada minuto, solo con la pantalla encendida (apagada no gasta nada). Si lo prefieres, con la letra del móvil: se pone en hora sola y no gasta, pero con la letra del sistema.",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    MagChoice(listOf("Con la letra del diseño", "Con la letra del móvil"), if (delMovil) 1 else 0, "La hora y la fecha") { i -> delMovil = i == 1; Minutero.ponLetraDelMovil(ctx, delMovil) }
    Minutero.motivo(ctx)?.let { Text("Ahora tus relojes van con la letra del móvil, porque $it.", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp)) }
    if (!exactas && android.os.Build.VERSION.SDK_INT >= 31) MagLink("Permitir las alarmas exactas", "En Ajustes del móvil: «Alarmas y recordatorios»", accent = true) { Minutero.abreAlarmas(ctx) }
    if (!delMovil && exactas) {
        val u = Minutero.ultimo(ctx)
        if (u > 0) Text("Última vez que se pusieron en hora: " + java.text.SimpleDateFormat("HH:mm", java.util.Locale("es", "ES")).format(java.util.Date(u)), color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Text("Si un reloj se queda atrás (en algunos móviles, como los OPPO, Realme o Xiaomi, el ahorro de batería no deja a las apps despertarse), permite a Widgets «actividad en segundo plano» o «inicio automático» y quítale la optimización de batería.",
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        MagLink("Que la batería no frene a Widgets", "El cuadro de Android para quitarle la optimización") { Minutero.abreBateria(ctx) }
        MagLink("Inicio automático y segundo plano", "En OPPO, Realme y OnePlus (ColorOS); si no, los ajustes de Widgets") { Minutero.abreInicioAutomatico(ctx) }
    }
}
