package com.gonzalo.cobre

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

const val VERSION = "0.9.0"

/** La pestaña «Fútbol» de Ajustes (para llegar a ella desde un partido o desde el widget). */
const val PESTANA_FUTBOL = 1

/** «Ajustes» en pestañas, como las hermanas: Apariencia · Fútbol · Reproductor · Guía · Cuenta · Avanzado. Todo lo que eliges aquí viaja con tu cuenta. */
@Composable
fun PantAjustes(alVolver: () -> Unit, alListas: () -> Unit = {}, inicial: Int = 0) {
    var pestana by rememberSaveable { mutableIntStateOf(inicial) }
    EspejoCuenta.version
    val mg = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = mg)) {
        RevistaTopBar(alVolver, "Nut")
        Antetitulo("Sección", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("Ajustes", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
        Filete(3.dp)
        PestanasRevista(listOf("Apariencia", "Fútbol", "Reproductor", "Guía", "Cuenta", "Avanzado"), pestana) { pestana = it }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.widthIn(max = 760.dp)) {
            when (pestana) {
                0 -> SeccionApariencia()
                PESTANA_FUTBOL -> SeccionFutbol()
                2 -> SeccionReproductor()
                3 -> SeccionGuia(alListas)
                4 -> SeccionCuenta()
                else -> SeccionAvanzado()
            }
        }
        Spacer(Modifier.height(26.dp))
        Filete(3.dp)
        Spacer(Modifier.height(10.dp))
        Etiqueta("Nut $VERSION")
        Text("Nut es hermana de Jonsu, Hathor, Bastet, Seshat y Thot. Es un reproductor: no trae canales ni listas, los pones tú. Reproduce con AndroidX Media3 (Apache 2.0) y, para el sonido que tu aparato no sepa, con FFmpeg (el decodificador de Jellyfin, GPL-3.0).",
            color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SeccionReproductor() {
    val ctx = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    v; EspejoCuenta.version
    fun cambia(f: () -> Unit) { f(); v++ }
    Antetitulo("La imagen", Modifier.padding(bottom = 4.dp))
    val asp = AjustesReproductor.Aspecto.entries
    MagChoice(asp.map { it.nombre }, asp.indexOf(AjustesReproductor.aspecto(ctx)), "De serie (se cambia también mientras ves)") { i -> cambia { AjustesReproductor.ponAspecto(ctx, asp[i]) } }
    Fila("Ahorrar datos del móvil", "Con datos (o una wifi con límite), la calidad más baja que dé el canal (en los que dan varias).") { Interruptor(AjustesReproductor.ahorro(ctx)) { x -> cambia { AjustesReproductor.ponAhorro(ctx, x) } } }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Audio y subtítulos", Modifier.padding(bottom = 4.dp))
    val audios = listOf("spa" to "Español", "eng" to "Inglés", "" to "El que venga")
    MagChoice(audios.map { it.second }, audios.indexOfFirst { it.first == AjustesReproductor.audio(ctx) }.coerceAtLeast(0), "Audio, si hay varios") { i -> cambia { AjustesReproductor.ponAudio(ctx, audios[i].first) } }
    val subs = listOf("" to "Sin subtítulos", "spa" to "Español", "eng" to "Inglés")
    MagChoice(subs.map { it.second }, subs.indexOfFirst { it.first == AjustesReproductor.subs(ctx) }.coerceAtLeast(0), "Subtítulos") { i -> cambia { AjustesReproductor.ponSubs(ctx, subs[i].first) } }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("En directo", Modifier.padding(bottom = 4.dp))
    Fila("Volver a conectar solo", "Si un canal se corta, se vuelve a conectar (hasta seis veces, cada vez esperando un poco más).") { Interruptor(AjustesReproductor.reconectar(ctx)) { x -> cambia { AjustesReproductor.ponReconectar(ctx, x) } } }
    Fila("Arriba, el canal anterior", "Como en muchas teles. Si no, arriba en el mando es el siguiente.") { Interruptor(AjustesReproductor.invertirCanales(ctx)) { x -> cambia { AjustesReproductor.ponInvertirCanales(ctx, x) } } }
    if (!esTele(ctx)) Fila("Seguir sonando al salir", "Al salir del reproductor (inicio, otra app, la pantalla apagada) se sigue oyendo, con la notificación para pausar o parar. También se enciende desde el reproductor.") {
        Interruptor(AjustesReproductor.segundoPlano(ctx)) { x -> cambia { AjustesReproductor.ponSegundoPlano(ctx, x) } }
    }
    Fila("Al abrir, el último canal", "Nut se abre con lo último que viste, como una tele.") { Interruptor(AjustesDirecto.alAbrir(ctx)) { x -> cambia { AjustesDirecto.ponAlAbrir(ctx, x) } } }
    val ordenes = AjustesDirecto.Orden.entries
    MagChoice(ordenes.map { it.nombre }, ordenes.indexOf(AjustesDirecto.orden(ctx)), "El orden de los canales") { i -> cambia { AjustesDirecto.ponOrden(ctx, ordenes[i]) } }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Películas y series", Modifier.padding(bottom = 4.dp))
    Fila("Pelis y series del proveedor", "Las de tu servidor de Xtream, en el menú, en la portada y en «Buscar». Apagado, Nut es la tele y el fútbol.") {
        Interruptor(AjustesDirecto.videoclub(ctx)) { x -> cambia { AjustesDirecto.ponVideoclub(ctx, x) } }
    }
    Fila("El episodio siguiente, solo", "Al terminar uno, el siguiente empieza en 8 segundos.") { Interruptor(AjustesReproductor.siguiente(ctx)) { x -> cambia { AjustesReproductor.ponSiguiente(ctx, x) } } }
    val saltos = listOf(5, 10, 15, 30)
    MagChoice(saltos.map { "$it s" }, saltos.indexOf(AjustesReproductor.salto(ctx)).coerceAtLeast(1), "Saltar con el mando") { i -> cambia { AjustesReproductor.ponSalto(ctx, saltos[i]) } }
    val otros = remember { Reproducir.otros(ctx) }
    val ops = listOf("" to "Preguntar") + otros
    MagChoice(ops.map { it.second }, ops.indexOfFirst { it.first == AjustesReproductor.externo(ctx) }.coerceAtLeast(0), "Abrir con otro reproductor") { i -> cambia { AjustesReproductor.ponExterno(ctx, ops[i].first) } }
    Text("«Abrir con otro reproductor» le pasa a esa app la dirección de lo que ves, que lleva tu usuario y tu contraseña de la lista: Nut te lo pregunta cada vez.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
private fun SeccionGuia(alListas: () -> Unit) {
    val ctx = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    v; EspejoCuenta.version
    fun cambia(f: () -> Unit) { f(); v++ }
    Text("La guía (XMLTV) sale de tus listas: la que diga la lista M3U, la del servidor de Xtream o la que pongas tú en cada lista. Se guarda en este dispositivo (solo la de tus canales) y se vuelve a bajar sola.",
        color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
    val horas = listOf(6, 12, 24, 48)
    MagChoice(horas.map { "Cada $it h" }, horas.indexOf(AjustesGuia.horas(ctx)).coerceAtLeast(1), "Bajarla otra vez") { i -> cambia { AjustesGuia.ponHoras(ctx, horas[i]) } }
    val dias = listOf(1, 2, 4, 7)
    MagChoice(dias.map { if (it == 1) "1 día" else "$it días" }, dias.indexOf(AjustesGuia.dias(ctx)).coerceAtLeast(2), "Cuántos días por delante") { i -> cambia { AjustesGuia.ponDias(ctx, dias[i]) } }
    Fila("Solo con wifi", "Una guía puede pesar más de 100 MB: con datos del móvil se baja solo si la pides tú.") { Interruptor(AjustesGuia.soloWifi(ctx)) { x -> cambia { AjustesGuia.ponSoloWifi(ctx, x) } } }
    Spacer(Modifier.height(8.dp))
    MagLink("Tus listas", "Para poner la dirección de una guía o bajarla ya", accent = true) { alListas() }
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
        Text("La misma de Jonsu y las demás: si ya has entrado en una, Nut entra sola. Tus listas, tus favoritos, lo que ves y todo lo que eliges viajan con ella.",
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        CampoRevista("Usuario", usuario, "tu usuario", teclado = KeyboardType.Text) { usuario = it }
        CampoRevista("Contraseña", clave, "••••••••", secreto = true) { clave = it }
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
        MagLink("Salir de la cuenta", "Solo en Nut y solo en este dispositivo") { Cuenta.salir() }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Las hermanas", Modifier.padding(bottom = 4.dp))
    Text("Lo que ves en Nut se queda en Nut (en tu cuenta): las películas y las series de tus listas no pasan a la biblioteca de Hathor.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    Hermanas.TODAS.forEach { (n, p) -> if (Hermanas.instalada(ctx, p)) MagLink("Abrir $n") { Hermanas.abre(ctx, p) } }
}

@Composable
private fun SeccionAvanzado() {
    val ctx = LocalContext.current
    com.gonzalo.familia.UltimoFallo("Nut", Charcoal, Muted, Body) { Antetitulo("Último fallo", Modifier.padding(bottom = 4.dp)) }
    val ambito = rememberCoroutineScope()
    Antetitulo("kDrive y actualizaciones", Modifier.padding(bottom = 4.dp))
    SeccionActualizaciones()
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Este dispositivo", Modifier.padding(bottom = 4.dp))
    var aviso by remember { mutableStateOf<String?>(null) }
    val ocupa by produceState(0L, aviso) { value = withContext(Dispatchers.IO) { listOf("cobre_listas", "cobre_futbol").sumOf { d -> java.io.File(ctx.filesDir, d).walkTopDown().filter { it.isFile }.sumOf { it.length() } } } }
    Text("Lo bajado de tus listas (canales, videoclub y guía) y del fútbol ocupa aquí ${Formato.cifra(ocupa / 1_000_000)} MB. Es caché: se vuelve a bajar.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    MagLink("Borrar lo bajado", "Tus listas siguen en tu cuenta; las de archivo de este dispositivo hay que volver a elegirlas") {
        ambito.launch {
            withContext(Dispatchers.IO) { Listas.lista().forEach { l -> Tele.olvida(l) }; Futbol.limpia(todo = true) }
            aviso = "Hecho. Se vuelve a bajar todo al entrar en cada lista."
        }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp) }
}

/** «Actualizaciones»: Cobre se actualiza sola desde la carpeta «versiones» de tu kDrive, con el token de kDrive de tu cuenta. */
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
    Fila("Actualizar sola", "Mira la carpeta «versiones» de tu kDrive (Nut-<versión>.apk) y se instala sin preguntar cuando no la usas (nunca con algo en marcha ni con la pantalla apagada).") {
        Interruptor(auto) { auto = it; Actualizador.ponActivo(ctx, it) }
    }
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        var avisos by remember { mutableStateOf(ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
        val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }
        if (!avisos) MagLink("Avisos de actualización: dar permiso", "Para avisarte de una versión nueva que Android no deja instalar sola, y para volver a abrir Nut al terminar") { pedirAvisos.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
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
        MagLink(if (sobre) "Tras actualizar, Nut se vuelve a abrir sola" else "Permitir que se reabra sola tras actualizar", if (sobre) null else "Hace falta el permiso «Mostrar sobre otras apps»; si no, te avisa para abrirla") {
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
        title = { Text("Nut tiene una versión nueva", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (Actualizador.faltaPermiso) "Hay una versión nueva en tu kDrive. Para instalarla, Android tiene que dejar a Nut instalar apps: te llevo a ese ajuste." else "Hay una versión nueva en tu kDrive, ya descargada. Android te pedirá confirmar la instalación.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            if (Actualizador.faltaPermiso) { Actualizador.faltaPermiso = false; ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))) }
            else runCatching { Actualizador.instala(ctx, n) }.onFailure { android.widget.Toast.makeText(ctx, "No se pudo instalar: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }) { Text(if (Actualizador.faltaPermiso) "PERMITIR" else "ACTUALIZAR AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ luego = true }) { Text("LUEGO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}
