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

const val VERSION = "0.4.0"

/** «Ajustes» en pestañas, como las hermanas: Apariencia · Cuenta · Avanzado. Todo lo que eliges aquí viaja con tu cuenta. */
@Composable
fun PantAjustes(alVolver: () -> Unit, inicial: Int = 0) {
    var pestana by rememberSaveable { mutableIntStateOf(inicial) }
    EspejoCuenta.version
    val mg = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = mg)) {
        RevistaTopBar(alVolver, "Shai")
        Antetitulo("Sección", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("Ajustes", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
        Filete(3.dp)
        PestanasRevista(listOf("Apariencia", "Cuenta", "IA", "Avanzado"), pestana) { pestana = it }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.widthIn(max = 760.dp)) {
            when (pestana) {
                0 -> SeccionApariencia()
                1 -> SeccionCuenta()
                2 -> SeccionIA()
                else -> SeccionAvanzado()
            }
        }
        Spacer(Modifier.height(26.dp))
        Filete(3.dp)
        Spacer(Modifier.height(10.dp))
        Etiqueta("Shai $VERSION")
        Text("Shai es hermana de Jonsu, Hathor, Nut, Bastet, Seshat, Thot y Ptah: tus carreras de EA Sports FC, con la revista de la familia. Lo de fuera del campo lo escribe Claude (de Anthropic) con tu clave de la API. EA Sports FC es de Electronic Arts; Shai no tiene nada que ver con EA.",
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
    Antetitulo("kDrive y actualizaciones", Modifier.padding(bottom = 4.dp))
    SeccionActualizaciones()
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
        Text("La misma de Jonsu y las demás. Si ya has entrado en una, Shai entra sola en cuanto esa tenga una versión que ya conozca a Shai; mientras, entra aquí con tu usuario. Tus carreras, tu apariencia y lo que eliges viajan con ella (la foto de tu personaje no: se queda en el móvil).",
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
        MagLink("Salir de la cuenta", "Solo en Shai y solo en este dispositivo") { Cuenta.salir() }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Las hermanas", Modifier.padding(bottom = 4.dp))
    Text("Shai es de la familia y entra con tu cuenta, pero va a su aire: Jonsu no enseña nada de Shai, y Shai no toca lo de las demás.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    Hermanas.TODAS.forEach { (n, p) -> if (Hermanas.instalada(ctx, p)) MagLink("Abrir $n") { Hermanas.abre(ctx, p) } }
}

/** «Actualizaciones»: Shai se actualiza sola desde la carpeta «versiones» de tu kDrive, con el token de kDrive de tu cuenta. */
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
    Fila("Actualizar sola", "Mira la carpeta «versiones» de tu kDrive (Shai-<versión>.apk) y se instala sin preguntar cuando no la usas (nunca con algo en marcha ni con la pantalla apagada).") {
        Interruptor(auto) { auto = it; Actualizador.ponActivo(ctx, it) }
    }
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        var avisos by remember { mutableStateOf(ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
        val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }
        if (!avisos) MagLink("Avisos de actualización: dar permiso", "Para avisarte de una versión nueva que Android no deja instalar sola, y para volver a abrir Shai al terminar") { pedirAvisos.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
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
        MagLink(if (sobre) "Tras actualizar, Shai se vuelve a abrir sola" else "Permitir que se reabra sola tras actualizar", if (sobre) null else "Hace falta el permiso «Mostrar sobre otras apps»; si no, te avisa para abrirla") {
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
        title = { Text("Shai tiene una versión nueva", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (Actualizador.faltaPermiso) "Hay una versión nueva en tu kDrive. Para instalarla, Android tiene que dejar a Shai instalar apps: te llevo a ese ajuste." else "Hay una versión nueva en tu kDrive, ya descargada. Android te pedirá confirmar la instalación.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            if (Actualizador.faltaPermiso) { Actualizador.faltaPermiso = false; ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))) }
            else runCatching { Actualizador.instala(ctx, n) }.onFailure { android.widget.Toast.makeText(ctx, "No se pudo instalar: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }) { Text(if (Actualizador.faltaPermiso) "PERMITIR" else "ACTUALIZAR AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ luego = true }) { Text("LUEGO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
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
    SiNo("Modo prueba (sin IA)", "Para ver todas las funciones sin gastar: lo de fuera del campo lo escriben unas plantillas de ejemplo, no Claude. Sus carreras van aparte (solo en este móvil) y no tocan las de tu cuenta; el gasto es siempre 0.", Prueba.activo) { Prueba.pon(ctx, it) }
    if (Prueba.activo) MagLink("Borrar las carreras de prueba", "Empezar de cero en el modo prueba") { Prueba.vacia(); aviso = "Borradas." }
    Spacer(Modifier.height(10.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Tu clave de la API", Modifier.padding(bottom = 4.dp))
    Text("La de tu organización de Claude Console (platform.claude.com), donde reclamas los créditos de la API de tu plan desde claude.ai → Configuración → Facturación. " +
        "Va solo a tu cuenta, a un sitio que no ve nadie más (ni tu pareja), y nunca a un registro.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    if (ClaveApi.hayDeVerdad) Text("Puesta: ${ClaveApi.tapada()}", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
    CampoClave(if (ClaveApi.hayDeVerdad) "Cambiarla" else "Clave", clave) { clave = it }
    if (clave.isNotBlank()) MagLink("Guardar la clave", accent = true) {
        aviso = try { ClaveApi.guarda(clave); clave = ""; ambito.launch { withContext(Dispatchers.IO) { runCatching { com.gonzalo.familia.LaFamilia.sincronizaYa() } } }; "Guardada." }
        catch (e: Exception) { e.message ?: "No se pudo guardar." }
    }
    if (ClaveApi.hayDeVerdad) MagLink("Quitar la clave", "Shai deja de pedir nada") { aviso = try { ClaveApi.guarda(""); "Quitada." } catch (e: Exception) { e.message } }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Lo gastado", Modifier.padding(bottom = 4.dp))
    BarraGasto()
    MagFact("Haiku 5.5", Formato.dolares(Gasto.mes(modelo = "h")))
    MagFact("Sonnet 5.5", Formato.dolares(Gasto.mes(modelo = "s")))
    MagFact("El mes pasado", Formato.dolares(Gasto.mes(Gasto.mesActual().minusMonths(1))))
    Text("Haiku escribe la red y los mensajes y lee las fotos; Sonnet, la prensa, las ofertas, las decisiones y lo de entre partidos. Se cuenta con lo que dice la API de cada respuesta (los tokens) y sus precios: Haiku 5.5, 0,10\u00A0$ y 0,50\u00A0$ por millón de tokens de entrada y de salida; Sonnet 5.5, 2\u00A0$ y 10\u00A0$; " +
        "lo que se lee de la caché, la décima parte; lo de entre partidos va por lotes, a mitad de precio. Es una cuenta de Shai: la de verdad está en Claude Console. " +
        "Por jornada, unos 0,13 $ (estimación, no medida: la red ~0,003, la prensa ~0,04, los dos eventos por lotes ~0,035, contestar decisiones ~0,045, la memoria ~0,006); cerrar una temporada, unos 0,05 $.",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
    Campo("Tope al mes (dólares)", tope, "100", teclado = KeyboardType.Number) { tope = it.filter { c -> c.isDigit() }.take(5) }
    if (numero(tope).toDouble() != Gasto.tope() && numero(tope) > 0) MagLink("Guardar el tope", "Al llegar a él, Shai no pide nada más hasta el mes siguiente", accent = true) { Gasto.ponTope(numero(tope).toDouble()); Carreras.avisa() }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(6.dp))
    SiNo("Lo de entre partidos, solo", "Tras cada partido, dos cosas que pasan mientras tanto: llegan por lotes (a mitad de precio) y la segunda sale unas horas después.", solos) { solos = it; Gasto.ponEventosSolos(it); Carreras.avisa() }
}
