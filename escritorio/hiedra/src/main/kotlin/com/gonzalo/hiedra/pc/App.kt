package com.gonzalo.hiedra.pc

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.AjustesCuenta
import com.gonzalo.familia.pc.AjustesVersion
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Portadas
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class Pantalla {
    data object Portada : Pantalla()
    data class Lista(val estado: Estado) : Pantalla()
    data object EsteOrdenador : Pantalla()
    data class Ficha(val id: String, val volver: Pantalla) : Pantalla()
    data object Ajustes : Pantalla()
}

class Nav(val ir: (Pantalla) -> Unit, val aviso: (String) -> Unit)
private val ES = Locale.forLanguageTag("es-ES")
fun horas(ms: Long): String { val m = ms / 60_000L; return if (m < 60) "$m min" else "${m / 60} h" + if (m % 60 > 0) " ${m % 60} min" else "" }
fun fecha(ms: Long): String = if (ms <= 0) "" else java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", ES))

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AppHiedra(inicial: Pantalla = Pantalla.Portada, sala: Boolean = false) {
    LaFamilia.cambios
    val w = androidx.compose.ui.awt.LocalAwtWindow.current as? androidx.compose.ui.awt.ComposeWindow
    SideEffect { if (w != null) VentanaHiedra.ventana = w }
    var enSala by remember { mutableStateOf(sala) }
    val estSala = remember { EstadoSala() }
    LaunchedEffect(enSala, w) { if (w != null) VentanaHiedra.pantallaCompleta(enSala) }
    // Si se quita la pantalla completa por otro lado (⌃⌘F en el Mac), se sale de la sala
    val enSalaAhora by rememberUpdatedState(enSala)
    LaunchedEffect(Unit) {
        var antes = com.gonzalo.familia.pc.VentanaFamilia.pantallaCompleta
        snapshotFlow { com.gonzalo.familia.pc.VentanaFamilia.pantallaCompleta }.collect { pc ->
            if (antes && !pc && enSalaAhora && !VentanaHiedra.apartada) enSala = false
            antes = pc
        }
    }
    // Al cerrar un juego abierto desde Hiedra, Hiedra vuelve delante (a la sala, si estaba en ella)
    DisposableEffect(Unit) {
        val f: (Pc.Partida) -> Unit = { VentanaHiedra.vuelve(enSalaAhora) }
        Pc.alTerminar += f
        onDispose { Pc.alTerminar -= f }
    }
    if (enSala) { PantallaSala(estSala) { enSala = false }; return }
    var pantalla by remember { mutableStateOf(inicial) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nav = remember { Nav({ pantalla = it }) { m -> scope.launch { snack.showSnackbar(m, withDismissAction = true) } } }
    val juegos = Juegos.todos()
    val estados = Estado.entries
    val entradas = buildList {
        add(Entrada("Portada", grupo = "Hiedra"))
        estados.forEach { e -> add(Entrada(if (e == Estado.JUGANDO) "Jugando" else e.nombre + "s", juegos.count { it.estado == e }, grupo = "Tus juegos")) }
        add(Entrada("En este ordenador", Pc.yaMirado()?.size, grupo = "Más")); add(Entrada("Ajustes", grupo = "Más")); add(Entrada("Modo sala", grupo = "Para la tele"))
    }
    val sel = when (val p = pantalla) { Pantalla.Portada -> 0; is Pantalla.Lista -> 1 + estados.indexOf(p.estado); Pantalla.EsteOrdenador -> 5; Pantalla.Ajustes -> 6; is Pantalla.Ficha -> (p.volver as? Pantalla.Lista)?.let { 1 + estados.indexOf(it.estado) } ?: 0 }
    fun ve(i: Int) { if (i == 7) enSala = true else pantalla = when (i) { 0 -> Pantalla.Portada; in 1..4 -> Pantalla.Lista(estados[i - 1]); 5 -> Pantalla.EsteOrdenador; else -> Pantalla.Ajustes } }
    // El mando también en la ventana: la cruceta mueve el foco, A pulsa, B vuelve, LB/RB cambian de sección y ⧉ o ☰ abren la sala
    // (F11 es la pantalla completa de las cuatro apps, sin la sala)
    val fm = androidx.compose.ui.platform.LocalFocusManager.current
    DisposableEffect(Unit) {
        Entrada.manejador = { a ->
            when (a) {
                Accion.ARRIBA, Accion.ABAJO, Accion.IZQUIERDA, Accion.DERECHA -> {
                    val d = when (a) { Accion.ARRIBA -> androidx.compose.ui.focus.FocusDirection.Up; Accion.ABAJO -> androidx.compose.ui.focus.FocusDirection.Down
                        Accion.IZQUIERDA -> androidx.compose.ui.focus.FocusDirection.Left; else -> androidx.compose.ui.focus.FocusDirection.Right }
                    if (!fm.moveFocus(d)) fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Next)
                }
                Accion.A -> pulsaIntro()
                Accion.B -> (pantalla as? Pantalla.Ficha)?.let { pantalla = it.volver }
                Accion.LB, Accion.RB -> ve(((if (sel < 0) 0 else sel) + (if (a == Accion.LB) -1 else 1) + 7) % 7)
                Accion.MENU, Accion.VISTA -> enSala = true
                else -> {}
            }
            true
        }
        onDispose { Entrada.manejador = null }
    }
    Row(Modifier.fillMaxSize()) {
        Lateral("Hiedra", entradas, sel) { i -> ve(i) }
        Filete(1.dp, Line, Modifier.fillMaxHeight().width(1.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Crossfade(pantalla, label = "pantalla") { p ->
                when (p) {
                    Pantalla.Portada -> PantPortada(nav) { enSala = true }
                    is Pantalla.Lista -> PantLista(p.estado, nav)
                    Pantalla.EsteOrdenador -> PantOrdenador(nav)
                    is Pantalla.Ficha -> PantFicha(p.id, p.volver, nav)
                    Pantalla.Ajustes -> PantAjustes(nav) { enSala = true }
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

/** A del mando en la ventana: un Intro para lo que tiene el foco (los botones de la revista lo entienden). */
private fun pulsaIntro() {
    val o = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return
    val t = System.currentTimeMillis()
    o.dispatchEvent(java.awt.event.KeyEvent(o, java.awt.event.KeyEvent.KEY_PRESSED, t, 0, java.awt.event.KeyEvent.VK_ENTER, '\n'))
    o.dispatchEvent(java.awt.event.KeyEvent(o, java.awt.event.KeyEvent.KEY_RELEASED, t + 1, 0, java.awt.event.KeyEvent.VK_ENTER, '\n'))
}

@Composable
fun Tarjeta(j: Juego, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        PortadaCosa(j.portada, j.portadaUrl, j.titulo, Modifier.fillMaxWidth(), pie = j.donde)
        Spacer(Modifier.height(8.dp))
        Text(j.titulo, color = Charcoal, fontFamily = Display, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOf(j.donde, if (j.ms > 0) horas(j.ms) else "").filter { it.isNotBlank() }.joinToString(" · ").ifBlank { j.estado.nombre }, color = Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Fila(l: List<Juego>, nav: Nav, volver: Pantalla) {
    l.chunked(6).forEach { f ->
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            f.forEach { j -> Tarjeta(j, Modifier.weight(1f)) { nav.ir(Pantalla.Ficha(j.id, volver)) } }
            repeat(6 - f.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun PantPortada(nav: Nav, alSala: () -> Unit = {}) {
    LaFamilia.cambios
    val juegos = Juegos.todos()
    val jugando = juegos.filter { it.estado == Estado.JUGANDO }.sortedByDescending { it.ultima }
    val hoy = LocalDate.now().toString()
    val msHoy = juegos.sumOf { it.dias()[hoy] ?: 0L }
    val semana = (0..6).sumOf { d -> val k = LocalDate.now().minusDays(d.toLong()).toString(); juegos.sumOf { it.dias()[k] ?: 0L } }
    val p = Pc.ahora
    Pagina {
        Text("Número de ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES))}".uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        Cabecera(96.sp, texto = "HIEDRA")
        FileteCabecera()
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                Cifra(horas(msHoy), "jugado hoy", acento = true)
                Cifra(horas(semana), "esta semana")
                Cifra(jugando.size.toString(), "jugando")
                Cifra(juegos.size.toString(), "en tu biblioteca")
            }
            Column(horizontalAlignment = Alignment.End) {
                BotonAcento("▶  Modo sala", alto = 52.dp) { alSala() }
                Text("PARA LA TELE Y EL MANDO", color = Muted, fontFamily = Body, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (p != null) {
            Spacer(Modifier.height(22.dp)); Filete(2.dp)
            Antetitulo("Ahora en este ordenador", Modifier.padding(top = 10.dp))
            Text(p.nombre, color = Charcoal, fontFamily = Display, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text("Desde las " + java.time.Instant.ofEpochMilli(p.desde).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5), color = Muted, fontFamily = Body, fontSize = 13.sp)
        }
        Spacer(Modifier.height(22.dp)); Filete(2.dp)
        Antetitulo("Jugando", Modifier.padding(vertical = 10.dp))
        if (jugando.isEmpty()) Vacio("Nada entre manos", "Lo que empieces aquí, en Hiedra del móvil o en Steam sale en esta portada.") else Fila(jugando.take(12), nav, Pantalla.Portada)
        val pasados = juegos.filter { it.estado == Estado.PASADO }.sortedByDescending { it.fin }.take(6)
        if (pasados.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Filete(2.dp); Antetitulo("Lo último que has pasado", Modifier.padding(vertical = 10.dp)); Fila(pasados, nav, Pantalla.Portada) }
    }
}

@Composable
fun PantLista(e: Estado, nav: Nav) {
    LaFamilia.cambios
    var menu by remember(e) { mutableStateOf(-1) }
    var buscar by remember(e) { mutableStateOf("") }
    val q = Juegos.norm(buscar)
    val l = Juegos.todos().filter { it.estado == e && (menu < 0 || it.menu == Menu.entries[menu]) && (q.isEmpty() || Juegos.norm(it.titulo).contains(q)) }
    val estado = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize(), estado, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Cabecera(76.sp, texto = if (e == Estado.JUGANDO) "JUGANDO" else e.nombre.uppercase() + "S")
                    FileteCabecera()
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(buscar, { buscar = it }, Modifier.width(340.dp), placeholder = { Text("Buscar") }, singleLine = true)
                        Spacer(Modifier.width(24.dp))
                        FiltrosRevista(listOf("Todos") + Menu.entries.map { it.nombre }, menu + 1) { menu = it - 1 }
                    }
                    if (l.isEmpty()) Vacio("Aquí no hay ningún juego")
                }
            }
            items(l, key = { it.id }) { j -> Tarjeta(j) { nav.ir(Pantalla.Ficha(j.id, Pantalla.Lista(e))) } }
        }
        VerticalScrollbar(rememberScrollbarAdapter(estado), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** Lo que hay para jugar en este ordenador: Steam, Heroic (Epic, GOG, Amazon), Lutris, tus ROMs y tus programas. */
@Composable
fun PantOrdenador(nav: Nav) {
    LaFamilia.cambios
    Pc.version
    val scope = rememberCoroutineScope()
    val lista = Pc.yaMirado()
    var nuevo by remember { mutableStateOf(false) }
    var filtro by remember { mutableStateOf(0) }
    var buscar by remember { mutableStateOf("") }
    val dev = Juegos.dispositivo()
    val juegos = remember(LaFamilia.cambios) { Juegos.todos() }
    val enHiedra = remember(juegos, lista) { lista.orEmpty().associate { l -> l.clave to juegos.firstOrNull { j -> j.aqui(dev) { k -> if (k == l.claveCuenta) l else null } != null } } }
    val origenes = remember(lista) { listOf("Todo") + lista.orEmpty().map { origenDe(it) }.distinct() }
    val q = Juegos.norm(buscar)
    val l = lista.orEmpty().filter { (filtro == 0 || origenDe(it) == origenes.getOrNull(filtro)) && (q.isEmpty() || Juegos.norm(it.nombre).contains(q)) }.sortedBy { it.nombre.lowercase() }
    Pagina {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { Cabecera(72.sp, texto = "EN ESTE ORDENADOR") }
            BotonSuave(if (Pc.mirando) "Mirando…" else "Volver a mirar", modifier = Modifier.padding(end = 12.dp)) { Pc.refresca(escanear = true) { nav.aviso("Mirado de nuevo: ${Pc.yaMirado().orEmpty().size} juegos") } }
            BotonAcento("+ Programa", alto = 44.dp, modifier = Modifier.width(160.dp)) { nuevo = true }
        }
        FileteCabecera()
        Spacer(Modifier.height(8.dp))
        Text("Lo que hay para jugar aquí: Steam, Heroic (Epic, GOG y Amazon), Lutris, las ROMs de tus carpetas (Ajustes → Emulación) y tus programas. Los de Steam se abren con Steam y sus horas las cuenta Steam; lo demás lo cuenta Hiedra mientras juegas, en la cuenta. Nada entra en tu biblioteca hasta que lo juegas o lo añades. Tu Hiedra del móvil lo ve en «Tu PC» y puede abrirlo desde allí.",
            color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 24.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(buscar, { buscar = it }, Modifier.width(300.dp), placeholder = { Text("Buscar") }, singleLine = true)
            Spacer(Modifier.width(24.dp))
            FiltrosRevista(origenes.mapIndexed { i, o -> if (i == 0) "$o ${lista.orEmpty().size}" else "$o ${lista.orEmpty().count { origenDe(it) == o }}" }, filtro) { filtro = it }
        }
        Spacer(Modifier.height(10.dp))
        if (lista == null) Vacio("Mirando…", "Steam, Heroic, Lutris, tus ROMs y tus programas.")
        else if (l.isEmpty()) Vacio(if (lista.isEmpty()) "No he encontrado nada" else "Nada con eso", if (lista.isEmpty()) "No veo Steam, Heroic ni Lutris en este ordenador. Añade tus ROMs en Ajustes → Emulación y tus juegos con «+ Programa»." else "")
        else l.chunked(6).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                f.forEach { j ->
                    val h = enHiedra[j.clave]
                    Column(Modifier.weight(1f)) {
                        PortadaPc(j, h, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(j.nombre, color = Charcoal, fontFamily = Display, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(j.etiqueta.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EnlaceRevista("Jugar", acento = true) { jugar(Ficha(h, j), enSala = false) { nav.aviso(it) } }
                            if (h != null) EnlaceRevista("Ficha") { nav.ir(Pantalla.Ficha(h.id, Pantalla.EsteOrdenador)) }
                            else EnlaceRevista("A Hiedra") { scope.launch { val id = withContext(Dispatchers.IO) { Juegos.mete(j).also { id -> portadaDe(j, id) } }; nav.aviso("En tu biblioteca"); nav.ir(Pantalla.Ficha(id, Pantalla.EsteOrdenador)) } }
                        }
                    }
                }
                repeat(6 - f.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (nuevo) DialogoPrograma(nav, { Pc.refresca() }) { nuevo = false }
}

/** De dónde es, para los filtros: la tienda, «Emulación» o «Programas». */
fun origenDe(j: JuegoPc) = when (j.fuente) { Fuente.EMULACION -> "Emulación"; Fuente.PROGRAMA -> "Programas"; else -> j.etiqueta }

/** La portada de algo de este ordenador en la ventana (la de tu biblioteca si ya está; si no, la suya, también las de Lutris, que son archivos). */
@Composable
fun PortadaPc(j: JuegoPc, h: Juego?, modifier: Modifier = Modifier) {
    val img = rememberImagen(h?.portada.orEmpty(), h?.portadaUrl.orEmpty(), j.portada)
    val forma = esquina(6.dp)
    Box(modifier.aspectRatio(2f / 3f).clip(forma).background(Sand).border(1.dp, Line, forma)) {
        if (img != null) androidx.compose.foundation.Image(img, j.nombre, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.width(22.dp).height(3.dp).background(Acento))
            Text(j.nombre, color = Charcoal, fontFamily = Display, fontSize = 17.sp, lineHeight = 19.sp, fontStyle = FontStyle.Italic, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Text(j.etiqueta.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, letterSpacing = 1.5.sp, maxLines = 1)
        }
    }
}

/** La portada de un juego de este ordenador (la de Steam, la de su tienda, la de libretro), a la cuenta: la misma imagen en todas las apps. */
fun portadaDe(j: JuegoPc, id: String) {
    if (!j.portada.startsWith("http") || Juegos.por(id)?.portada?.isNotBlank() == true) return
    runCatching {
        val b = java.net.URI(j.portada).toURL().openStream().use { it.readBytes() }
        Portadas.guardar(b)?.let { h -> Juegos.cambia(id, mapOf("portada" to h, "pu" to j.portada)) }
    }
}

@Composable
private fun DialogoPrograma(nav: Nav, alAnadir: () -> Unit, onCerrar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var ruta by remember { mutableStateOf<java.io.File?>(null) }
    Dialogo("Añadir un programa", onCerrar, botones = {
        BotonSuave("Cancelar") { onCerrar() }
        BotonAcento("Añadir", modifier = Modifier.width(120.dp), alto = 44.dp) {
            val r = ruta ?: return@BotonAcento
            val j = Pc.anadePrograma(nombre.ifBlank { r.nameWithoutExtension }, r)
            Juegos.mete(j)
            onCerrar(); alAnadir(); nav.aviso("Añadido: ${j.nombre}")
        }
    }) {
        Campo("Nombre del juego", nombre) { nombre = it }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ruta?.path ?: "Ningún archivo elegido", Modifier.weight(1f), color = Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 2)
            BotonSuave("Elegir…") { com.gonzalo.familia.pc.revista.eligeArchivo("Elige el juego (el ejecutable, el .AppImage, la app…)")?.let { ruta = it; if (nombre.isBlank()) nombre = it.nameWithoutExtension.removeSuffix(".app") } }
        }
        Cuerpo("Al jugar desde aquí, Hiedra cuenta el tiempo hasta que lo cierras.", tam = 12)
    }
}

@Composable
fun Campo(etiqueta: String, v: String, onCambia: (String) -> Unit) = OutlinedTextField(v, onCambia, Modifier.fillMaxWidth(), label = { Text(etiqueta) }, singleLine = true)

/** La ficha de un juego: su portada, cómo va, sus horas (por fuente y por día) y jugar aquí. */
@Composable
fun PantFicha(id: String, volver: Pantalla, nav: Nav) {
    LaFamilia.cambios
    val j = Juegos.por(id) ?: run { Pagina { VolverRevista({ nav.ir(volver) }); Vacio("Este juego ya no está") }; return }
    val scope = rememberCoroutineScope()
    val dev = Juegos.dispositivo()
    val aqui = j.aqui(dev)
    Pagina {
        VolverRevista({ nav.ir(volver) })
        Spacer(Modifier.height(18.dp))
        Row {
            Column(Modifier.width(280.dp)) {
                PortadaCosa(j.portada, j.portadaUrl, j.titulo, Modifier.fillMaxWidth(), pie = j.plataforma)
                Spacer(Modifier.height(14.dp))
                if (aqui != null) {
                    BotonAcento("Jugar", modifier = Modifier.fillMaxWidth()) { scope.launch { nav.aviso(withContext(Dispatchers.IO) { Pc.juega(aqui, j.id) }) } }
                    Text("CON " + comoSeAbre(aqui).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                }
                else Cuerpo(if (j.lanzadores.isEmpty()) "Sin lanzador: es una ficha (se juega fuera de Hiedra)." else "Se juega en " + j.lanzadores.joinToString(", ") { when (it.tipo) { "ROM" -> "emulación (${Emulacion.sistema(it.sistema)?.nombre ?: it.sistema}; la ROM no está en tus carpetas de aquí)"; "APP" -> "Android"; "WEB" -> "la web"; else -> "otro PC" } } + ".", tam = 13)
            }
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                RevistaHeadline(j.titulo, listOf(j.plataforma, j.menu.nombre).filter { it.isNotBlank() }.distinct().joinToString(" · "))
                Spacer(Modifier.height(16.dp))
                MagChoice(Estado.entries.map { it.nombre }, Estado.entries.indexOf(j.estado), "Cómo va") { Juegos.ponEstado(j, Estado.entries[it]) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Favorito", Modifier.weight(1f), color = Charcoal, fontFamily = Display, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Interruptor(j.fav) { Juegos.cambia(id, mapOf("fav" to it)) }
                }
                Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
                Antetitulo("Tus horas")
                Spacer(Modifier.height(6.dp))
                MagFact("En total", horas(j.ms))
                if (j.veces > 0) MagFact("Veces", j.veces.toString())
                j.horas.sortedByDescending { it.entero("total") }.forEach { h ->
                    val f = h.texto("fuente")
                    val nombre = when { f == "steam" -> "Steam"; f.startsWith("pc:") -> "En el PC"; f == dev -> "En este ordenador"; f.startsWith("carmesi:") -> "Carmesí"; f.startsWith("hiedra-linux-") || f.startsWith("hiedra-mac-") -> "En otro ordenador"; else -> "En un móvil" }
                    MagFact(nombre, horas(h.entero("total")))
                }
                val dias = j.dias().entries.sortedByDescending { it.key }.take(7)
                if (dias.isNotEmpty()) { Spacer(Modifier.height(8.dp)); dias.forEach { (d, ms) -> MagFact(runCatching { LocalDate.parse(d).format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).replaceFirstChar { it.uppercase() } }.getOrDefault(d), horas(ms)) } }
                if (j.anadido > 0) MagFact("Añadido", fecha(j.anadido))
                if (j.fin > 0) MagFact("Pasado", fecha(j.fin))
                Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
                Antetitulo("En Azabache")
                Text(j.nota?.toString() ?: "Sin valorar", color = if (j.nota != null) Acento else Muted, fontFamily = Display, fontSize = if (j.nota != null) 44.sp else 26.sp, fontStyle = if (j.nota == null) FontStyle.Italic else FontStyle.Normal)
            }
        }
    }
}

@Composable
fun PantAjustes(nav: Nav, alSala: () -> Unit = {}) {
    var pestana by remember { mutableStateOf(0) }
    Pagina(ancho = 980.dp) {
        Cabecera(84.sp, texto = "AJUSTES")
        FileteCabecera()
        Spacer(Modifier.height(14.dp))
        PestanasRevista(listOf("Cuenta", "Este PC", "Emulación", "Sala y mando", "Apariencia", "Versión"), pestana) { pestana = it }
        Spacer(Modifier.height(22.dp))
        when (pestana) {
            0 -> AjustesCuenta("hiedra")
            1 -> {
                Antetitulo("Este ordenador para tu Hiedra del móvil")
                Spacer(Modifier.height(6.dp))
                Cuerpo("Mientras Hiedra está abierta aquí, tu Hiedra del móvil ve este ordenador en «Tu PC»: su biblioteca (Steam, Heroic, Lutris, tus ROMs y tus programas), a qué juegas, y puede abrir un juego desde el móvil.", tam = 14)
                MagFact("Nombre", Sitio.nombreEquipo)
                MagFact("Dispositivo", Juegos.dispositivo())
                MagFact("Steam", Pc.carpetasSteam().joinToString { it.path }.ifBlank { "no lo veo en este ordenador" })
                MagFact("Heroic", Heroic.raices().joinToString { it.path }.ifBlank { "no lo veo en este ordenador" })
                val lista = Pc.yaMirado().orEmpty()
                MagFact("Lutris", lista.count { it.fuente == Fuente.LUTRIS }.let { if (it == 0) "nada (o no está instalado)" else "$it juegos" })
                Spacer(Modifier.height(8.dp))
                BotonSuave("Volver a publicar la biblioteca") { Thread { TuPc.publicaBiblioteca() }.start(); nav.aviso("Publicada") }
            }
            2 -> AjustesEmulacion(nav)
            3 -> AjustesSalaMando(alSala)
            4 -> {
                Antetitulo("Apariencia")
                Spacer(Modifier.height(6.dp))
                Cuerpo("Hiedra del ordenador tiene la apariencia de tu Hiedra del móvil (viaja con la cuenta). Aquí puedes elegir claro u oscuro.", tam = 14)
                Spacer(Modifier.height(10.dp))
                MagChoice(listOf("Como el sistema", "Claro", "Oscuro"), Modo.eleccion) { Modo.eleccion = it; Apariencia.guarda() }
            }
            else -> AjustesVersion("hiedra", "Hiedra")
        }
    }
}
