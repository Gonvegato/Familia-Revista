package com.gonzalo.mostaza

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val VERSION = "0.7.6"

/** «Ajustes» en pestañas, como las hermanas: Apariencia · Catálogo · Reproductor · Fuentes · Cuenta · Avanzado. Todo lo que eliges aquí viaja con tu cuenta. */
@Composable
fun PantEstilo(alVolver: () -> Unit, alAddons: () -> Unit = {}, pestanaInicial: Int = 0) {
    var pestana by rememberSaveable { mutableIntStateOf(pestanaInicial) }
    EspejoCuenta.version
    val mg = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        // Dirección C (como Seshat y Bastet): la cabecera en un bloque de tinta con «AJUSTES.» en grotesca
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = mg).padding(bottom = 14.dp)) {
            EnlaceRevista("← Hathor", Modifier.padding(top = 6.dp), color = Paper.copy(alpha = 0.75f)) { alVolver() }
            NombreConPunto("Ajustes", 52.sp, Paper, Modifier.padding(top = 2.dp))
        }
        PestanasRevista(listOf("Apariencia", "Catálogo", "Reproductor", "Fuentes", "Cuenta", "Avanzado"), pestana) { pestana = it }
        Column(Modifier.fillMaxWidth().padding(horizontal = mg)) {
        Spacer(Modifier.height(14.dp))
        Column(Modifier.widthIn(max = 760.dp)) {
            when (pestana) {
                0 -> SeccionApariencia()
                1 -> SeccionCatalogo()
                2 -> SeccionReproductor()
                3 -> SeccionFuentes(alAddons)
                4 -> SeccionCuenta()
                else -> SeccionAvanzado()
            }
        }
        Spacer(Modifier.height(26.dp))
        Filete(3.dp)
        Spacer(Modifier.height(10.dp))
        Etiqueta("Hathor $VERSION")
        Text("Hathor es hermana de Jonsu, Bastet, Seshat y Thot. Habla el protocolo abierto de los addons de Stremio; reproduce con AndroidX Media3 (Apache 2.0). " + Tmdb.AVISO_TMDB,
            color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(30.dp))
        }
    }
}

/** Una casilla de texto con el estilo de la revista: etiqueta en versalitas, texto en cursiva sobre un filete. */
@Composable
fun CampoRevista(etiqueta: String, valor: String, pista: String, secreto: Boolean = false, alCambiar: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(etiqueta.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.width(96.dp).padding(bottom = 3.dp))
            Box(Modifier.weight(1f)) {
                if (valor.isEmpty()) Text(pista, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
                BasicTextField(valor, alCambiar, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp),
                    cursorBrush = SolidColor(Acento), visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (secreto) KeyboardType.Password else KeyboardType.Uri, autoCorrect = false), modifier = Modifier.fillMaxWidth())
            }
        }
        Filete(2.dp)
    }
}

@Composable
private fun SeccionReproductor() {
    val ctx = LocalContext.current
    EspejoCuenta.version
    var v by remember { mutableIntStateOf(0) }
    v
    fun cambia(f: () -> Unit) { f(); v++ }
    Antetitulo("Subtítulos", Modifier.padding(bottom = 4.dp))
    val subs = listOf("spa" to "Español", "eng" to "Inglés", "" to "Ninguno")
    MagChoice(subs.map { it.second }, subs.indexOfFirst { it.first == AjustesReproductor.subs(ctx) }.coerceAtLeast(0), "Idioma") { i -> cambia { AjustesReproductor.ponSubs(ctx, subs[i].first) } }
    Fila("Siempre", "Aunque el audio ya sea en tu idioma (si no, solo cuando no lo es).") { Interruptor(AjustesReproductor.subsSiempre(ctx)) { x -> cambia { AjustesReproductor.ponSubsSiempre(ctx, x) } } }
    val tams = listOf(75, 100, 125, 150, 200)
    MagChoice(tams.map { "$it %" }, tams.indexOf(AjustesReproductor.subTam(ctx)).coerceAtLeast(1), "Tamaño") { i -> cambia { AjustesReproductor.ponSubTam(ctx, tams[i]) } }
    Fila("Sobre una banda oscura", "Si no, letra blanca con borde.") { Interruptor(AjustesReproductor.subFondo(ctx)) { x -> cambia { AjustesReproductor.ponSubFondo(ctx, x) } } }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Audio", Modifier.padding(bottom = 4.dp))
    val audios = listOf("spa" to "Español", "eng" to "Inglés", "" to "El original")
    MagChoice(audios.map { it.second }, audios.indexOfFirst { it.first == AjustesReproductor.audio(ctx) }.coerceAtLeast(0), "Si la fuente trae varios") { i -> cambia { AjustesReproductor.ponAudio(ctx, audios[i].first) } }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Mientras ves", Modifier.padding(bottom = 4.dp))
    Fila("El episodio siguiente, solo", "Al terminar uno, el siguiente empieza en 8 segundos (con la fuente de la misma clase).") { Interruptor(AjustesReproductor.siguiente(ctx)) { x -> cambia { AjustesReproductor.ponSiguiente(ctx, x) } } }
    val saltos = listOf(5, 10, 15, 30)
    MagChoice(saltos.map { "$it s" }, saltos.indexOf(AjustesReproductor.salto(ctx)).coerceAtLeast(1), "Saltar con el mando") { i -> cambia { AjustesReproductor.ponSalto(ctx, saltos[i]) } }
    val otros = remember { Reproducir.otros(ctx) }
    val ops = listOf("" to "Preguntar") + otros
    MagChoice(ops.map { it.second }, ops.indexOfFirst { it.first == AjustesReproductor.externo(ctx) }.coerceAtLeast(0), "Abrir con otro reproductor") { i -> cambia { AjustesReproductor.ponExterno(ctx, ops[i].first) } }
    if (otros.isEmpty()) Text("No hay otro reproductor de vídeo en este dispositivo. Para lo que este no sepa decodificar, instala VLC o Just Player.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
private fun SeccionFuentes(alAddons: () -> Unit) {
    val ctx = LocalContext.current
    EspejoCuenta.version
    var p by remember { mutableStateOf(AjustesElector.prefs(ctx)) }
    LaunchedEffect(EspejoCuenta.version) { p = AjustesElector.prefs(ctx) }
    fun pon(n: PrefsElector) { p = n; AjustesElector.pon(ctx, n) }
    Text("Al pulsar «Reproducir», Hathor mira las fuentes de todos tus addons y pone la mejor para ti: primero tu idioma (en el orden de abajo), luego la mejor calidad que cabe en tu conexión y que este dispositivo sabe reproducir, luego las directas o de tu debrid antes que los torrents, y por último el orden de tus addons. La lista entera está a un toque («Cambiar»).",
        color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
    var auto by remember { mutableStateOf(AjustesElector.auto(ctx)) }
    Fila("Elegir sola", "Si no, «Reproducir» te enseña la lista.") { Interruptor(auto) { auto = it; AjustesElector.ponAuto(ctx, it) } }
    Spacer(Modifier.height(8.dp)); Antetitulo("Idiomas, por orden", Modifier.padding(bottom = 4.dp))
    val todos = PrefsElector.IDIOMAS.map { it.first }
    val orden = p.idiomas + todos.filter { it !in p.idiomas }
    orden.forEachIndexed { i, id ->
        val on = id in p.idiomas
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (on) "${p.idiomas.indexOf(id) + 1}" else "·", color = if (on) Acento else Muted, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.width(32.dp))
            Text(PrefsElector.nombre(id), color = if (on) Charcoal else Muted, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (on && p.idiomas.indexOf(id) > 0) EnlaceRevista("↑") { val l = p.idiomas.toMutableList(); val k = l.indexOf(id); l.removeAt(k); l.add(k - 1, id); pon(p.copy(idiomas = l)) }
            Spacer(Modifier.width(10.dp))
            EnlaceRevista(if (on) "Quitar" else "Poner", acento = !on) { pon(p.copy(idiomas = if (on) (p.idiomas - id).ifEmpty { listOf("castellano") } else p.idiomas + id)) }
        }
        if (i < orden.size - 1) Filete(1.dp, Line)
    }
    Fila("Vale con subtítulos", "Una fuente sin audio en español, con subtítulos (de la fuente o de tus addons de subtítulos).") { Interruptor(p.subtitulados) { pon(p.copy(subtitulados = it)) } }
    val maximas = listOf(2160 to "4K", 1080 to "1080p", 720 to "720p", 480 to "SD")
    MagChoice(maximas.map { it.second }, maximas.indexOfFirst { it.first == p.maxima }.coerceAtLeast(0), "Calidad máxima") { i -> pon(p.copy(maxima = maximas[i].first)) }
    Fila("Directas antes que torrents", "Las de tu debrid (o un enlace directo) se ponen al momento.") { Interruptor(p.directasPrimero) { pon(p.copy(directasPrimero = it)) } }
    Fila("Ahorrar datos del móvil", "Con datos (no con wifi): como mucho 720p, y lo que menos pese.") { Interruptor(p.ahorroDatos) { pon(p.copy(ahorroDatos = it)) } }
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Tu conexión, aquí", Modifier.padding(bottom = 4.dp))
    val red = remember { Conexion.red(ctx) }
    val cap = remember { Conexion.capacidades(ctx) }
    MagFact("Red", when (Conexion.tipo(ctx)) { Conexion.Tipo.WIFI -> "Wifi"; Conexion.Tipo.MOVIL -> "Datos del móvil"; Conexion.Tipo.CABLE -> "Cable"; Conexion.Tipo.OTRA -> "Otra"; Conexion.Tipo.NINGUNA -> "Sin conexión" })
    MagFact(if (red.medida) "Medido al ver" else "Lo que estima Android", if (red.kbps > 0) String.format(java.util.Locale("es", "ES"), "%.1f Mbit/s", red.kbps / 1000.0) else "aún no se sabe")
    MagFact("Este dispositivo", listOfNotNull("hasta ${Analisis.etiquetaResolucion(cap.maxima)}", if (cap.hevc) "HEVC" else null, if (cap.av1) "AV1" else null, if (cap.hdr) "HDR" else null, if (cap.dv) "Dolby Vision" else null).joinToString(" · "))
    Text("Lo de la conexión es de este dispositivo (no viaja): se mide al ver, por wifi y por datos aparte, y baja en cuanto algo se corta.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    Spacer(Modifier.height(12.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Torrents", Modifier.padding(bottom = 4.dp))
    Text(if (Torrents.disponible) "Con el motor de torrents de la familia." else "El motor de torrents no se ha podido poner en marcha en este dispositivo: se ven las fuentes directas o de tu debrid. Lo que elijas aquí viaja con tu cuenta.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    var t by remember { mutableStateOf(AjustesTorrents.ajustes(ctx)) }
    fun ponT(n: AjustesTorrent) { t = n; AjustesTorrents.pon(ctx, n) }
    Fila("Solo con wifi", "Con datos del móvil, los torrents no se bajan.") { Interruptor(t.soloWifi) { ponT(t.copy(soloWifi = it)) } }
    Fila("Compartir mientras ves", "Lo que bajas se comparte con otros mientras lo ves (es como van los torrents).") { Interruptor(t.compartir) { ponT(t.copy(compartir = it)) } }
    val subidas = listOf(0 to "Sin límite", 100 to "100 KB/s", 500 to "500 KB/s", 1000 to "1 MB/s")
    MagChoice(subidas.map { it.second }, subidas.indexOfFirst { it.first == t.subidaKBs }.coerceAtLeast(0), "Subida") { i -> ponT(t.copy(subidaKBs = subidas[i].first)) }
    val bajadas = listOf(0 to "Sin límite", 2000 to "2 MB/s", 5000 to "5 MB/s", 10000 to "10 MB/s")
    MagChoice(bajadas.map { it.second }, bajadas.indexOfFirst { it.first == t.bajadaKBs }.coerceAtLeast(0), "Bajada") { i -> ponT(t.copy(bajadaKBs = bajadas[i].first)) }
    val caches = listOf(1024 to "1 GB", 4096 to "4 GB", 8192 to "8 GB", 16384 to "16 GB")
    MagChoice(caches.map { it.second }, caches.indexOfFirst { it.first == t.cacheMB }.coerceAtLeast(1), "Lo que puede ocupar aquí (de este dispositivo)") { i -> ponT(t.copy(cacheMB = caches[i].first)) }
    Spacer(Modifier.height(12.dp)); Filete(2.dp)
    MagLink("Tus addons", "Instalar, ordenar, configurar o quitar", accent = true) { alAddons() }
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
        Text("La misma de Jonsu y las demás: si ya has entrado en una, Hathor entra sola. Tu biblioteca, lo que ves, tus addons y todo lo que eliges viajan con ella.",
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        CampoRevista("Usuario", usuario, "tu usuario") { usuario = it }
        CampoRevista("Contraseña", clave, "••••••••", secreto = true) { clave = it }
        MagLink(if (ocupado) "Entrando…" else "Entrar", "Se guarda un pase de acceso, no tu contraseña") {
            if (!ocupado) {
                ocupado = true; aviso = null
                ambito.launch {
                    aviso = try { Cuenta.entrar(usuario, clave); clave = ""; SincroP.sincronizar(ctx); null }
                    catch (e: Cuenta.Fail) { e.message } catch (e: Exception) { "No se pudo entrar: ${e.message ?: "sin conexión"}" }
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
        MagLink("Salir de la cuenta", "Solo en Hathor y solo en este dispositivo") { Cuenta.salir() }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Las hermanas", Modifier.padding(bottom = 4.dp))
    Text("Jonsu valora lo que ves aquí (la nota y la reseña van en la misma ficha de tu cuenta). Lo que tenía Jonsu de películas y series ya es de Hathor.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    Hermanas.TODAS.forEach { (n, p) -> if (Hermanas.instalada(ctx, p)) MagLink("Abrir $n") { Hermanas.abre(ctx, p) } }
}

@Composable
private fun SeccionAvanzado() {
    val ctx = LocalContext.current
    Antetitulo("kDrive y actualizaciones", Modifier.padding(bottom = 4.dp))
    SeccionActualizaciones()
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("Reto del año", Modifier.padding(bottom = 4.dp))
    var reto by remember { mutableIntStateOf(RetoCine.meta(ctx)) }
    val retos = remember { (listOf(0, 12, 24, 36, 50, 75, 100, 150) + RetoCine.meta(ctx)).distinct().sorted() }
    MagChoice(retos.map { if (it == 0) "Sin reto" else "$it" }, retos.indexOf(reto).coerceAtLeast(0), "Películas y series terminadas al año") { i -> reto = retos[i]; RetoCine.poner(ctx, reto) }
    Text("Una película vista cuenta 1; una serie, 1 cuando la terminas (todos sus capítulos estrenados vistos). Va con tu cuenta: sale igual en tus otros Hathor.",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo("La portada", Modifier.padding(bottom = 4.dp))
    var cuantas by remember { mutableIntStateOf(AjustesPortada.cuantas(ctx)) }
    val ops = listOf(0, 2, 4, 6, 8, 12)
    MagChoice(ops.map { if (it == 0) "Ninguna" else "$it" }, ops.indexOf(cuantas).coerceAtLeast(2), "Filas de catálogos") { i -> cuantas = ops[i]; AjustesPortada.ponCuantas(ctx, cuantas) }
    Text("Las primeras de tus addons, en su orden.", color = Muted, fontFamily = Body, fontSize = 12.sp)
    // La sala (la cara de la tele) también en una tablet: en una tele sale siempre, así que allí no se ofrece
    if (!Sala.esTele(ctx)) {
        Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
        Antetitulo("La sala", Modifier.padding(bottom = 4.dp))
        Fila("Modo sala", "Hathor como en la tele: a pantalla completa, con portadas grandes y todo con la cruceta de un mando. Para una tablet con un mando o conectada a la tele. Solo en este dispositivo; se sale desde su menú.") {
            Interruptor(Sala.enTablet) { Sala.ponEnTablet(ctx, it) }
        }
    }
}

/** «Actualizaciones»: Mostaza se actualiza sola desde la carpeta «versiones» de tu kDrive, con el token de kDrive de tu cuenta. */
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
    Fila("Actualizar sola", "Mira la carpeta «versiones» de tu kDrive (Hathor-<versión>.apk) y se instala sin preguntar cuando no la usas (nunca con algo en marcha ni con la pantalla apagada).") {
        Interruptor(auto) { auto = it; Actualizador.ponActivo(ctx, it) }
    }
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        var avisos by remember { mutableStateOf(ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
        val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }
        if (!avisos) MagLink("Avisos de actualización: dar permiso", "Para avisarte de una versión nueva que Android no deja instalar sola, y para volver a abrir Hathor al terminar") { pedirAvisos.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
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
        MagLink(if (sobre) "Tras actualizar, Hathor se vuelve a abrir sola" else "Permitir que se reabra sola tras actualizar", if (sobre) null else "Hace falta el permiso «Mostrar sobre otras apps»; si no, te avisa para abrirla") {
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
        title = { Text("Hathor tiene una versión nueva", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (Actualizador.faltaPermiso) "Hay una versión nueva en tu kDrive. Para instalarla, Android tiene que dejar a Hathor instalar apps: te llevo a ese ajuste." else "Hay una versión nueva en tu kDrive, ya descargada. Android te pedirá confirmar la instalación.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            if (Actualizador.faltaPermiso) { Actualizador.faltaPermiso = false; ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))) }
            else runCatching { Actualizador.instala(ctx, n) }.onFailure { android.widget.Toast.makeText(ctx, "No se pudo instalar: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }) { Text(if (Actualizador.faltaPermiso) "PERMITIR" else "ACTUALIZAR AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ luego = true }) { Text("LUEGO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}
