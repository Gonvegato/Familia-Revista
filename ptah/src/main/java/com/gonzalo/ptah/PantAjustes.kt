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

const val VERSION = "0.3.0"

/** «Ajustes» en pestañas, como las hermanas: Apariencia · Cuenta · Avanzado. Todo lo que eliges aquí viaja con tu cuenta. */
@Composable
fun PantAjustes(alVolver: () -> Unit, inicial: Int = 0) {
    var pestana by rememberSaveable { mutableIntStateOf(inicial) }
    EspejoCuenta.version
    val mg = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = mg)) {
        RevistaTopBar(alVolver, "Ptah")
        Antetitulo("Sección", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("Ajustes", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
        Filete(3.dp)
        PestanasRevista(listOf("Apariencia", "Cuenta", "Avanzado"), pestana) { pestana = it }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.widthIn(max = 760.dp)) {
            when (pestana) {
                0 -> SeccionApariencia()
                1 -> SeccionCuenta()
                else -> SeccionAvanzado()
            }
        }
        Spacer(Modifier.height(26.dp))
        Filete(3.dp)
        Spacer(Modifier.height(10.dp))
        Etiqueta("Ptah $VERSION")
        Text("Ptah es hermana de Jonsu, Hathor, Nut, Bastet, Seshat y Thot: tus widgets de la pantalla de inicio, con la revista de la familia. ${OpenMeteo.LICENCIA}",
            color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(30.dp))
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
private fun SeccionAvanzado() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    Antetitulo("kDrive y actualizaciones", Modifier.padding(bottom = 4.dp))
    SeccionActualizaciones()
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    SeccionRelojes()
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Tus widgets", Modifier.padding(bottom = 4.dp))
    var aviso by remember { mutableStateOf<String?>(null) }
    val n = remember { Widgets.ids(ctx).size }
    Text(if (n == 0) "No tienes ningún widget de Ptah en la pantalla de inicio de este móvil." else "Tienes $n widget${if (n > 1) "s" else ""} de Ptah en la pantalla de inicio de este móvil. Se vuelven a pintar solos; si alguno se ha quedado atrás:",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    if (n > 0) MagLink("Volver a pintarlos ahora", "Y baja el tiempo, si toca") {
        ambito.launch { withContext(Dispatchers.IO) { Refresco.tiempo(ctx, forzar = true) }; Widgets.pedir(ctx); aviso = "Hecho." }
    }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Este móvil", Modifier.padding(bottom = 4.dp))
    val ocupa by produceState(0L, aviso) { value = withContext(Dispatchers.IO) { java.io.File(ctx.filesDir, "fotos").walkTopDown().filter { it.isFile }.sumOf { it.length() } } }
    Text("Las fotos de tus widgets de foto están solo aquí (no van a tu cuenta) y ocupan ${Formato.cifra(ocupa / 1_000_000)} MB. El tiempo bajado es caché: se vuelve a pedir solo.",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    MagLink("Borrar el tiempo guardado", "Se vuelve a bajar en la próxima vuelta") {
        ambito.launch { withContext(Dispatchers.IO) { java.io.File(ctx.filesDir, "tiempo").deleteRecursively() }; aviso = "Hecho. Se vuelve a pedir solo." }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp) }
}

/** La cuenta de la familia: entrar (una vez para todas), cómo va la sincronización, salir; y las hermanas. */
@Composable
private fun SeccionCuenta() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    Antetitulo("Tu cuenta", Modifier.padding(bottom = 4.dp))
    if (!Cuenta.vinculada) {
        Text("La misma de Jonsu y las demás: si ya has entrado en una, Ptah entra sola. Tus diseños, tu apariencia y lo que eliges viajan con ella (las fotos no: se quedan en el móvil).",
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        Campo("Usuario", usuario, "tu usuario") { usuario = it }
        CampoClave("Contraseña", clave) { clave = it }
        MagLink(if (ocupado) "Entrando…" else "Entrar", "Se guarda un pase de acceso, no tu contraseña") {
            if (!ocupado) {
                ocupado = true; aviso = null
                ambito.launch {
                    aviso = try { Cuenta.entrar(usuario, clave); clave = ""; SincroP.sincronizar(ctx); null }
                    catch (e: Cuenta.Fail) { e.message } catch (e: Exception) { "No se pudo entrar (¿sin conexión?)." }
                    ocupado = false
                }
            }
        }
    } else {
        Etiqueta("Dentro como", Modifier.padding(top = 8.dp))
        Text(Cuenta.usuario, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 22.sp)
        val cuando = SincroP.ultima.takeIf { it > 0 }?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it)) }
        Text(listOfNotNull(SincroP.estado.ifBlank { "Sin sincronizar todavía" }, cuando?.let { "última vez $it" }).joinToString(" · "), color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(6.dp))
        MagLink(if (ocupado) "Sincronizando…" else "Sincronizar ahora", "Baja lo de tus otros dispositivos y sube lo de este") {
            if (!ocupado) { ocupado = true; ambito.launch { SincroP.sincronizar(ctx); ocupado = false } }
        }
        Filete(1.dp, Line)
        MagLink("Salir de la cuenta", "Solo en Ptah y solo en este dispositivo") { Cuenta.salir() }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Las hermanas", Modifier.padding(bottom = 4.dp))
    Text("Ptah es de la familia y entra con tu cuenta, pero va a su aire: Jonsu no enseña nada de Ptah, y Ptah no toca lo de las demás.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    Hermanas.TODAS.forEach { (n, p) -> if (Hermanas.instalada(ctx, p)) MagLink("Abrir $n") { Hermanas.abre(ctx, p) } }
}

/** «Actualizaciones»: Ptah se actualiza sola desde la carpeta «versiones» de tu kDrive, con el token de kDrive de tu cuenta. */
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
    Fila("Actualizar sola", "Mira la carpeta «versiones» de tu kDrive (Ptah-<versión>.apk) y se instala sin preguntar cuando no la usas (nunca con algo en marcha ni con la pantalla apagada).") {
        Interruptor(auto) { auto = it; Actualizador.ponActivo(ctx, it) }
    }
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        var avisos by remember { mutableStateOf(ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
        val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }
        if (!avisos) MagLink("Avisos de actualización: dar permiso", "Para avisarte de una versión nueva que Android no deja instalar sola, y para volver a abrir Ptah al terminar") { pedirAvisos.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
    if (!KDrive.on(ctx)) {
        Text("El token de kDrive es de tu cuenta: se pone una vez en Jonsu (Ajustes → Avanzado → Tu nube) y lo tienen todas las apps.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
    } else {
        val vers = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
        Text("Versión instalada: $vers" + if (estado.isNotBlank()) " · última comprobación: $estado" else "", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        MagLink(if (ocupado) "Actualizando…" else "Actualizar ahora", "Busca la última versión en tu kDrive y la instala ahora", accent = true) {
            if (!ocupado) { ocupado = true; aviso = null; ambito.launch { aviso = withContext(Dispatchers.IO) { Actualizador.actualizarAhora(ctx) }; ocupado = false } }
        }
        Filete(1.dp, Line)
        MagLink("Solo comprobar") { if (!ocupado) { ocupado = true; ambito.launch { withContext(Dispatchers.IO) { Actualizador.comprueba(ctx, manual = true) }; ocupado = false } } }
        Filete(1.dp, Line)
        MagLink(if (sobre) "Tras actualizar, Ptah se vuelve a abrir sola" else "Permitir que se reabra sola tras actualizar", if (sobre) null else "Hace falta el permiso «Mostrar sobre otras apps»; si no, te avisa para abrirla") {
            if (!sobre) runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))) }
        }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
}

/** Si hay una versión nueva ya bajada que Android no deja instalar en silencio, se pregunta (o se lleva al ajuste de «instalar apps»). */
@Composable
fun AvisoActualizacion() {
    val ctx = LocalContext.current
    val n = Actualizador.nueva?.takeIf { !it.silenciosa } ?: return
    var luego by remember(n.nombre) { mutableStateOf(false) }
    if (luego) return
    AlertDialog(
        onDismissRequest = { luego = true }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Ptah tiene una versión nueva", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (Actualizador.faltaPermiso) "Hay una versión nueva en tu kDrive. Para instalarla, Android tiene que dejar a Ptah instalar apps: te llevo a ese ajuste." else "Hay una versión nueva en tu kDrive, ya descargada. Android te pedirá confirmar la instalación.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            if (Actualizador.faltaPermiso) { Actualizador.faltaPermiso = false; ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))) }
            else runCatching { Actualizador.instala(ctx, n) }.onFailure { android.widget.Toast.makeText(ctx, "No se pudo instalar: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }) { Text(if (Actualizador.faltaPermiso) "PERMITIR" else "ACTUALIZAR AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ luego = true }) { Text("LUEGO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}

/** La hora de los relojes de la pantalla de inicio: dibujada con tu letra (cada minuto) o con la del móvil. */
@Composable
fun SeccionRelojes() {
    val ctx = LocalContext.current
    var delMovil by remember { mutableStateOf(Minutero.letraDelMovil(ctx)) }
    val exactas = remember { Minutero.puedeExactas(ctx) }
    Antetitulo("La hora de tus relojes", Modifier.padding(bottom = 4.dp))
    Text("Para que la hora salga con la letra de tu diseño, Ptah la dibuja y vuelve a pintar tus relojes al empezar cada minuto, solo con la pantalla encendida (apagada no gasta nada). Si lo prefieres, con la letra del móvil: se pone en hora sola y no gasta, pero con la letra del sistema.",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    MagChoice(listOf("Con la letra del diseño", "Con la letra del móvil"), if (delMovil) 1 else 0, "La hora y la fecha") { i -> delMovil = i == 1; Minutero.ponLetraDelMovil(ctx, delMovil) }
    if (!exactas) {
        Text("Android no deja ahora a Ptah despertarse cada minuto: tus relojes van con la letra del móvil hasta que lo permitas.", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp)
        if (android.os.Build.VERSION.SDK_INT >= 31) MagLink("Permitir las alarmas exactas", "En Ajustes del móvil: «Alarmas y recordatorios»", accent = true) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${ctx.packageName}"))) }
        }
    }
    if (!delMovil && exactas) {
        val u = Minutero.ultimo(ctx)
        if (u > 0) Text("Última vez que se pusieron en hora: " + java.text.SimpleDateFormat("HH:mm", java.util.Locale("es", "ES")).format(java.util.Date(u)), color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Text("Si un reloj se queda atrás (en algunos móviles, como los OPPO, Realme o Xiaomi, el ahorro de batería no deja a las apps despertarse), permite a Ptah «actividad en segundo plano» o «inicio automático» y quítale la optimización de batería.",
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        MagLink("Abrir los ajustes de Ptah en el móvil", "Batería → permitir actividad en segundo plano") {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}"))) }
        }
    }
}
