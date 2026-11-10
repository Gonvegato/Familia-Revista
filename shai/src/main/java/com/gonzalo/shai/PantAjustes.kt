package com.gonzalo.shai

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

const val VERSION = "0.13.1"

/** «Ajustes»: Aspecto · IA · Actualizar. Todo se guarda en este móvil. */
@Composable
fun PantAjustes(alVolver: () -> Unit, inicial: Int = 0) {
    var pestana by rememberSaveable { mutableIntStateOf(inicial.coerceIn(0, 2)) }
    val mg = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.padding(horizontal = mg)) {
            RevistaTopBar(alVolver, "Volver", titulo = "Ajustes")
        }
        PestanasRevista(listOf("Aspecto", "IA", "Actualizar"), pestana) { pestana = it }
        Column(Modifier.padding(horizontal = mg).widthIn(max = 760.dp)) {
            Spacer(Modifier.height(14.dp))
            when (pestana) {
                0 -> SeccionAspecto()
                1 -> SeccionIA()
                else -> SeccionActualizaciones()
            }
            Spacer(Modifier.height(26.dp))
            Filete(1.dp)
            Spacer(Modifier.height(10.dp))
            Etiqueta("FcFeed $VERSION")
            Text("Tus carreras de EA Sports FC, como una red social: tú juegas el campo y lo de fuera lo escribe Claude (de Anthropic) con tu clave de la API. Todo se guarda solo en este móvil. EA Sports FC es de Electronic Arts; FcFeed no tiene nada que ver con EA.",
                color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** «Aspecto»: claro u oscuro y el tamaño del texto. */
@Composable
private fun SeccionAspecto() {
    val ctx = LocalContext.current
    Antetitulo("Claro u oscuro", Modifier.padding(bottom = 6.dp))
    MagChoice(listOf("Como el sistema", "Claro", "Oscuro"), Modo.eleccion) { Modo.eleccion = it; Skin.guardar(ctx) }
    Spacer(Modifier.height(16.dp))
    Antetitulo("Tamaño del texto", Modifier.padding(bottom = 6.dp))
    val tams = Skin.ESCALAS_NOMBRES.keys.toList()
    MagChoice(Skin.ESCALAS_NOMBRES.values.toList(), tams.indexOf(Skin.escala).coerceAtLeast(0)) { Skin.escala = tams[it]; Skin.guardar(ctx) }
    Text("Se suma al tamaño de letra del móvil.", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
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

/** «Actualizaciones»: FcFeed se actualiza sola desde la carpeta «fcfeed» de tu kDrive, con tu token de kDrive (guardado cifrado en este móvil). */
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
    Fila("Actualizar sola", "Mira la carpeta «fcfeed» de tu kDrive (FcFeed-<versión>.apk) y se instala sin preguntar cuando no la usas (nunca con algo en marcha ni con la pantalla apagada).") {
        Interruptor(auto) { auto = it; Actualizador.ponActivo(ctx, it) }
    }
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        var avisos by remember { mutableStateOf(ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
        val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }
        if (!avisos) MagLink("Avisos de actualización: dar permiso", "Para avisarte de una versión nueva que Android no deja instalar sola, y para volver a abrir FcFeed al terminar") { pedirAvisos.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
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
        MagLink(if (sobre) "Tras actualizar, FcFeed se vuelve a abrir sola" else "Permitir que se reabra sola tras actualizar", if (sobre) null else "Hace falta el permiso «Mostrar sobre otras apps»; si no, te avisa para abrirla") {
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
        MagLink("Quitar el token", "FcFeed deja de mirar tu kDrive") { KDrive.forget(ctx); hay = false }
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
        title = { Text("FcFeed tiene una versión nueva", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (Actualizador.faltaPermiso) "Hay una versión nueva en tu kDrive. Para instalarla, Android tiene que dejar a FcFeed instalar apps: te llevo a ese ajuste." else "Hay una versión nueva en tu kDrive, ya descargada. Android te pedirá confirmar la instalación.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            if (Actualizador.faltaPermiso) { Actualizador.faltaPermiso = false; ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))) }
            else runCatching { Actualizador.instala(ctx, n) }.onFailure { android.widget.Toast.makeText(ctx, "No se pudo instalar: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }) { Text(if (Actualizador.faltaPermiso) "Permitir" else "Actualizar ahora", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ luego = true }) { Text("Luego", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold) } }
    )
}

/** «IA»: tu clave de la API de Claude, el tope de gasto del mes y lo de entre partidos. */
@Composable
private fun SeccionIA() {
    val ambito = rememberCoroutineScope()
    Carreras.version
    var clave by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf<String?>(null) }
    var tope by remember { mutableStateOf(Formato.cifra(Math.round(Gasto.tope()))) }
    var solos by remember { mutableStateOf(Gasto.eventosSolos()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    SiNo("Modo prueba (sin IA)", "Para ver todas las funciones sin gastar: lo de fuera del campo lo escriben unas plantillas de ejemplo, no Claude. Sus carreras van aparte y no tocan las de verdad; el gasto es siempre 0.", Prueba.activo) { Prueba.pon(ctx, it) }
    if (Prueba.activo) MagLink("Borrar las carreras de prueba", "Empezar de cero en el modo prueba") { Prueba.vacia(); aviso = "Borradas." }
    Spacer(Modifier.height(10.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Tu clave de la API", Modifier.padding(bottom = 4.dp))
    Text("La de tu organización de Claude Console (platform.claude.com), donde reclamas los créditos de la API de tu plan desde claude.ai → Configuración → Facturación. " +
        "Se guarda cifrada en este móvil y nunca va a un registro.", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
    if (ClaveApi.hayDeVerdad) Text("Puesta: ${ClaveApi.tapada()}", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
    CampoClave(if (ClaveApi.hayDeVerdad) "Cambiarla" else "Clave", clave) { clave = it }
    if (clave.isNotBlank()) MagLink("Guardar la clave", accent = true) {
        aviso = try { ClaveApi.guarda(clave); clave = ""; "Guardada." }
        catch (e: Exception) { e.message ?: "No se pudo guardar." }
    }
    if (ClaveApi.hayDeVerdad) MagLink("Quitar la clave", "FcFeed deja de pedir nada") { aviso = try { ClaveApi.guarda(""); "Quitada." } catch (e: Exception) { e.message } }
    aviso?.let { Nota(it, Modifier.padding(top = 4.dp), color = Acento) }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Lo gastado", Modifier.padding(bottom = 4.dp))
    BarraGasto()
    MagFact("Haiku 5.5", Formato.dolares(Gasto.mes(modelo = "h")))
    MagFact("Sonnet 5.5", Formato.dolares(Gasto.mes(modelo = "s")))
    MagFact("El mes pasado", Formato.dolares(Gasto.mes(Gasto.mesActual().minusMonths(1))))
    Text("Haiku escribe la red y los mensajes y lee las fotos; Sonnet, la prensa, las ofertas, las decisiones y lo de entre partidos. Se cuenta con lo que dice la API de cada respuesta (los tokens) y sus precios: Haiku 5.5, 0,10\u00A0$ y 0,50\u00A0$ por millón de tokens de entrada y de salida; Sonnet 5.5, 2\u00A0$ y 10\u00A0$; " +
        "lo que se lee de la caché, la décima parte; lo de entre partidos va por lotes, a mitad de precio. Es una cuenta de FcFeed: la de verdad está en Claude Console. " +
        "Por jornada, unos 0,13 $ (estimación, no medida: la red ~0,003, la prensa ~0,04, los dos eventos por lotes ~0,035, contestar decisiones ~0,045, la memoria ~0,006); cerrar una temporada, unos 0,05 $.",
        color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
    Campo("Tope al mes (dólares)", tope, "100", teclado = KeyboardType.Number) { tope = it.filter { c -> c.isDigit() }.take(5) }
    if (numero(tope).toDouble() != Gasto.tope() && numero(tope) > 0) MagLink("Guardar el tope", "Al llegar a él, FcFeed no pide nada más hasta el mes siguiente", accent = true) { Gasto.ponTope(numero(tope).toDouble()); Carreras.avisa() }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(6.dp))
    SiNo("Lo de entre partidos, solo", "Tras cada partido, dos cosas que pasan mientras tanto: llegan por lotes (a mitad de precio) y la segunda sale unas horas después.", solos) { solos = it; Gasto.ponEventosSolos(it); Carreras.avisa() }
}
