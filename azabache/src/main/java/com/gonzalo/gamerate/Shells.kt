@file:OptIn(ExperimentalFoundationApi::class)

package com.gonzalo.gamerate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/*
 * La portada de la interfaz Revista: papel, tipografía enorme, las secciones arriba como el menú de una web,
 * «En portada» (la única imagen de la app, que va pasando por lo que llevas entre manos) y listas numeradas.
 */

/** Un título de la colección, venga de «en curso / en lista» (Play) o de «valorado» (Game). */
/** Algo que llevas entre manos (jugando, leyendo, viendo), con su categoría. */
class Ongoing(val kind: Kind, val play: Play)

class ShellEntry(val kind: Kind, val play: Play?, val game: Game?) {
    val id: Long get() = play?.id ?: game!!.id
    val name: String get() = play?.name ?: game!!.name
    val cover: String get() = play?.cover ?: game!!.cover
    val hero: String get() = (play?.hero ?: game?.hero).orEmpty()
    val creator: String get() = (play?.creator ?: game?.creator).orEmpty()
    val minutes: Int get() = play?.minutes ?: game?.minutes ?: 0
    val score: Int? get() = game?.general
    val activity: Long get() = play?.activity ?: game!!.activity
    val status: String get() = play?.let { kind.statuses[it.status.coerceIn(0, 2)] } ?: kind.doneWord.removeSuffix("s").let { if (kind.isGame) "Pasado" else it }
}

/** Todo lo que una interfaz necesita saber y puede hacer. */
class ShellModel(
    val name: String,
    val readOnly: Boolean,
    val kinds: List<Kind>,
    val entries: (Kind) -> List<ShellEntry>,
    val ongoing: List<Ongoing>,
    val paired: Boolean,
    val partnerName: String?,
    val open: (ShellEntry) -> Unit,
    val primary: (Ongoing) -> Unit,
    val primaryLabel: (Ongoing) -> Pair<String, ImageVector>,
    val library: (Kind) -> Unit,
    val diary: () -> Unit,
    /** Escribir (obras, capítulos, fichas…). */
    val write: (() -> Unit)? = null,
    val discover: ((Int) -> Unit)?,
    val profile: (() -> Unit)?,
    val add: ((Kind) -> Unit)?,
    val partner: (() -> Unit)?,
    val stats: (Kind) -> Unit,
    /** Elegir la imagen de «En portada» (SteamGridDB, TMDB, TVmaze u Open Library, según lo que sea). */
    val pickImage: ((Ongoing) -> Unit)? = null,
    /** La página del PC (en directo, biblioteca, capturas y equipo). */
    val pc: (() -> Unit)? = null,
    /** Lo que te recomienda tu pareja (añadir a tu lista o descartar) y lo último que ha hecho. */
    val recs: List<Rec> = emptyList(),
    val onRecAdd: (Rec) -> Unit = {},
    val onRecDismiss: (Rec) -> Unit = {},
    val acts: List<Activity> = emptyList(),
    val onAct: (Activity) -> Unit = {}
)

/** El orden de siempre: lo que llevas entre manos, lo apuntado y lo terminado (lo último primero). */
fun ShellModel.feed(k: Kind): List<ShellEntry> = entries(k).sortedWith(
    compareBy<ShellEntry> { when (it.play?.status) { 1 -> 0; 0 -> 1; null -> 2; else -> 3 } }.thenByDescending { it.activity }
)

@Composable
fun ShellHome(m: ShellModel) {
    RevistaHome(m)
}

private fun kindShort(k: Kind) = when { k.isMovie -> "Pelis"; k.isTv -> "Series"; k.isGame -> "Juegos"; k.isBook -> "Libros"; else -> k.title }

// ================================================================ 2 · REVISTA

private val Paper2: Color get() = Paper
private val Ink2: Color get() = Charcoal
private val Red2: Color get() = ColGeneral
private val Grey2: Color get() = Muted

/** La entrada de la portada se anima solo la primera vez que se abre la app (no al volver de una ficha). */
private object RevistaIntro { var done = false }

@Composable
private fun RevistaHome(m: ShellModel) {
    val today = LocalDate.now()
    val all = m.kinds.flatMap { m.entries(it) }
    val best = all.filter { it.score != null }.sortedByDescending { it.score }.take(5)
    // Al abrir la app: las piezas de la portada llegan una tras otra, en muy poco tiempo, en lugar de todo de golpe
    val intro = remember { !RevistaIntro.done }
    LaunchedEffect(Unit) { RevistaIntro.done = true }
    fun Modifier.llega(n: Int) = appear(n * 110, intro)
    val desplazado = rememberScrollState()
    val ongoing = m.ongoing
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Paper2).verticalScroll(desplazado).navigationBarsPadding()) {
        // ---- Jonsu 1.8: la portada del día, los retos de todas y la semana (lo de las hermanas, juntas)
        Box(Modifier.llega(0)) {
            Column {
                PortadaFamilia(all, m.kinds.filter { it.isBook }.flatMap { k -> m.entries(k).mapNotNull { it.game } },
                    m.kinds.filter { it.isGame }.flatMap { k -> m.entries(k).mapNotNull { it.game } },
                    m.kinds.filter { it.isMovie || it.isTv }.flatMap { k -> m.entries(k).mapNotNull { it.game } }) { m.open(it) }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // Cifras (lo que antes estaba en el menú de la portada): debajo de la semana
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
            Row(Modifier.press(haptic = false) { m.stats(GAMES) }.padding(vertical = 10.dp)) {
                Text("CIFRAS", color = Acento, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1)
                Text(" →", color = Acento, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
        // Ahora mismo: todo lo que llevas entre manos, en lista numerada (fija: no cambia con la portada)
        if (ongoing.isNotEmpty()) Column(Modifier.llega(4)) {
            Spacer(Modifier.height(26.dp))
            Text("Ahora mismo", color = Ink2, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, modifier = Modifier.padding(bottom = 4.dp))
            Rule(2.dp)
            ongoing.take(8).forEachIndexed { i, o ->
                val e = ShellEntry(o.kind, o.play, null)
                Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 14.dp), verticalAlignment = if (Skin.listCovers) Alignment.CenterVertically else Alignment.Top) {
                    if (Skin.listCovers) { ListCover(e.cover, e.name, 84.dp, 126.dp, if (m.readOnly) null else coverKey(e.kind.id, e.id)); Spacer(Modifier.width(16.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text("%02d".format(i + 1), color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.sp)
                        FitTitle(e.name, 22.sp, 25.sp, color = Ink2, family = Grotesca, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp, conEdicion = false)
                        // (los libros con su archivo: por dónde vas)
                        val pct = if (e.kind.isBook && BookFiles.get(ctx, e.id) != null) (BookFiles.progress(ctx, e.id) * 100).toInt() else 0
                        Text(listOf(kindShort(e.kind), e.creator, if (pct in 1..99) "$pct %" else "").filter { it.isNotBlank() }.joinToString(" · "), color = Grey2, fontSize = 13.sp)
                        // Lo que se hace con él (apuntar, valorar…), como el botón de la portada de antes
                        if (!m.readOnly) Text("${m.primaryLabel(o).first.uppercase()}\u00A0→", color = Acento, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(top = 6.dp).press(haptic = false) { m.primary(o) }.padding(vertical = 4.dp))
                    }
                }
                Rule(2.dp)
            }
        }

        // Escritos: lo último que diste por terminado en Cobalto
        if (!m.readOnly && m.write != null) RevistaEscritos(m.write)

        // Tu pareja: lo que te recomienda y lo último que ha hecho
        RevistaPareja(m)

        // Próximos episodios de las series que ves o tienes en lista (los de las dos próximas semanas)
        Box(Modifier.llega(4)) { RevistaProximos(m) }

        // Las notas más altas: números gigantes
        if (best.isNotEmpty()) Column(Modifier.llega(5)) {
            Spacer(Modifier.height(38.dp))
            RevSection("Las notas más altas")
            // La columna de los números, tan ancha como la nota más ancha (un 100 no cabía y se partía en «10» y «0»)
            val numStyle = androidx.compose.ui.text.TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 58.sp)
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val density = androidx.compose.ui.platform.LocalDensity.current
            val numW = with(density) { best.maxOf { measurer.measure("${it.score}", numStyle).size.width }.toDp() } + 16.dp
            best.forEach { e ->
                Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.score}", color = ColorCifras, style = numStyle, maxLines = 1, softWrap = false, modifier = Modifier.width(maxOf(numW, 100.dp)))
                    if (Skin.listCovers) { ListCover(e.cover, e.name, 52.dp, 74.dp); Spacer(Modifier.width(14.dp)) }
                    Column(Modifier.weight(1f)) {
                        FitTitle(e.name, 20.sp, 23.sp, color = Ink2, fontWeight = FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(kindShort(e.kind).uppercase(), color = Red2, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    }
                }
            }
        }

        // La cita (un subrayado tuyo, distinto cada día) y lo que terminaste tal día como hoy otros años
        RevistaCita(all.filter { it.kind.isBook })
        RevistaEfemerides(all) { m.open(it) }

        Spacer(Modifier.height(40.dp))
        Text(Ediciones.actual.pie, color = Grey2, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        // La versión instalada, debajo (para no tener que entrar en Ajustes a mirarla)
        val ctxV = androidx.compose.ui.platform.LocalContext.current
        val version = remember { runCatching { ctxV.packageManager.getPackageInfo(ctxV.packageName, 0).versionName }.getOrNull().orEmpty() }
        if (version.isNotBlank()) Text("versión $version", color = Grey2.copy(alpha = 0.7f), fontSize = 10.sp, letterSpacing = 0.6.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
        Spacer(Modifier.height(24.dp))
        }
    }
    }
}

/** «X te recomienda» (a tu lista o descartar) y «Lo último de X»: lo de tu pareja, en la portada. */
@Composable
private fun RevistaPareja(m: ShellModel) {
    val nombre = m.partnerName ?: return
    if (m.recs.isNotEmpty() && !m.readOnly) {
        Spacer(Modifier.height(38.dp))
        RevSection("$nombre te recomienda")
        m.recs.take(6).forEach { r ->
            val k = kindOf(r.kind)
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (Skin.listCovers) { ListCover(r.cover, r.name, 44.dp, 62.dp); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        FitTitle(r.name, 22.sp, 25.sp, color = Ink2, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOf(kindShort(k), r.creator).filter { it.isNotBlank() }.joinToString(" · "), color = Grey2, fontSize = 13.sp)
                    }
                }
                if (r.note.isNotBlank()) Text("«${r.note}»", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 21.sp, color = Ink2, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    EnlaceRevista("A mi lista →", acento = true) { m.onRecAdd(r) }
                    EnlaceRevista("Descartar") { m.onRecDismiss(r) }
                }
            }
            Rule(1.dp, Line)
        }
    }
    if (m.acts.isNotEmpty()) {
        Spacer(Modifier.height(38.dp))
        RevSection(if (m.readOnly) "Lo último" else "Lo último de $nombre")
        m.acts.take(6).forEach { a ->
            Row(Modifier.fillMaxWidth().press(haptic = false) { m.onAct(a) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (Skin.listCovers) { ListCover(a.cover, a.name, 40.dp, 56.dp); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                    FitTitle(a.name, 19.sp, 22.sp, color = Ink2, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(kindShort(kindOf(a.kind)), a.text).joinToString(" · ").uppercase(), color = Red2, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
            }
            Rule(1.dp, Line)
        }
    }
}

/**
 * Las secciones, como el menú del PC y en el mismo orden: cada categoría (con su nombre), Diario, Escribir, Descubrir,
 * Cifras, Tu PC y Ajustes. En versalitas y en las líneas que hagan falta (todo a la vista, sin deslizar).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RevistaMenu(m: ShellModel) {
    val items = m.kinds.map { k -> k.title to { m.library(k) } } +
        listOfNotNull("Diario" to m.diary, m.write?.let { "Escritos" to it }, "Cifras" to { m.stats(GAMES) })
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Rule(1.dp)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items.forEach { (t, f) ->
                Text(t.uppercase(), color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1,
                    modifier = Modifier.press(haptic = false, onClick = f).padding(vertical = 9.dp))
            }
        }
        Rule(1.dp)
    }
}

@Composable
private fun Rule(h: androidx.compose.ui.unit.Dp, c: Color = Charcoal) = Filete(h, c)

@Composable
private fun RevSection(t: String) {
    Antetitulo(t, Modifier.padding(bottom = 10.dp), color = Ink2)
}

@Composable
private fun IconoRevista(icono: androidx.compose.ui.graphics.vector.ImageVector, nombre: String, f: () -> Unit) =
    androidx.compose.material3.Icon(icono, nombre, tint = Ink2, modifier = Modifier.padding(start = 6.dp).press(haptic = false, onClick = f).padding(8.dp).size(22.dp))

@Composable
private fun RevLink(t: String, f: () -> Unit) = EnlaceRevista(t, Modifier.padding(start = 14.dp), onClick = f)

/**
 * Buscar en todo lo tuyo desde la portada: juegos, libros, pelis, series y tus categorías a la vez (sin fijarse en
 * tildes), y también en lo que has subrayado en tus libros (tocar una cita abre el libro justo ahí).
 */
@Composable
internal fun BuscadorGeneral(m: ShellModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val todo = remember(m.kinds) { m.kinds.flatMap { k -> m.entries(k) } }
    val libros = remember(todo) { todo.filter { it.kind.isBook }.distinctBy { it.id } }
    // Las citas de tus libros (con el lector), para buscar también en ellas
    val citas = remember(libros, Hermanas.version) { libros.flatMap { e -> bookNotes(ctx, e.id).map { e to it } } }
    val res = remember(q, todo) { if (q.isBlank()) emptyList() else todo.filter { libMatches(q, it.name, it.creator) }.sortedByDescending { it.activity }.take(80) }
    val resCitas = remember(q, citas) { if (q.trim().length < 3) emptyList() else citas.filter { (_, n) -> libMatches(q, n.text, n.note) }.take(40) }
    // Y en lo que has escrito en tu diario
    val resNotas = remember(q, DiarioHub.notas) { if (q.trim().length < 3 || m.readOnly) emptyList() else DiarioHub.notas.filter { libMatches(q, it.texto, "") }.sortedByDescending { it.dia }.take(30) }
    val recientes = remember(todo) { todo.sortedByDescending { it.activity }.distinctBy { it.kind.id to it.id }.take(8) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val foco = remember { androidx.compose.ui.focus.FocusRequester() }
        Column(Modifier.fillMaxSize().background(Paper2).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Buscar en todo", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Ink2, modifier = Modifier.weight(1f))
                Text("CERRAR", color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press { onClose() }.padding(8.dp))
            }
            Rule(3.dp)
            androidx.compose.foundation.text.BasicTextField(
                q, { q = it }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Ink2, fontSize = 22.sp, fontFamily = Display),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Red2),
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).focusRequester(foco),
                decorationBox = { inner -> Box { if (q.isEmpty()) Text("Título, autor, una frase que subrayaste o escribiste", color = Grey2, fontSize = 17.sp, fontFamily = Display, fontStyle = FontStyle.Italic); inner() } }
            )
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(150); runCatching { foco.requestFocus() } }
            Rule(1.dp)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (q.isBlank()) {
                    item { RevSectionPad("Lo último") }
                    items(recientes.size) { i -> ResultadoFila(recientes[i]) { onClose(); m.open(recientes[i]) } }
                } else {
                    if (res.isEmpty() && resCitas.isEmpty() && resNotas.isEmpty()) item {
                        Text("Nada con «${q.trim()}».", color = Grey2, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(vertical = 24.dp))
                    }
                    // Por categorías, en el orden de la portada
                    m.kinds.forEach { k ->
                        val deK = res.filter { it.kind.id == k.id }
                        if (deK.isNotEmpty()) {
                            item(key = "k${k.id}") { RevSectionPad("${k.title} · ${deK.size}") }
                            items(deK.size, key = { i -> "e${k.id}-${deK[i].id}-${deK[i].play != null}" }) { i -> ResultadoFila(deK[i]) { onClose(); m.open(deK[i]) } }
                        }
                    }
                    if (resNotas.isNotEmpty()) {
                        item(key = "notas") { RevSectionPad("En tu diario · ${resNotas.size}") }
                        items(resNotas.size) { i ->
                            val n = resNotas[i]
                            Column(Modifier.fillMaxWidth().press(haptic = false) { onClose(); DiarioHub.editar?.invoke(n) }.padding(vertical = 10.dp)) {
                                Text(fechaDiario(n.dia).uppercase(), color = Grey2, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(extracto(n.texto, 220), fontFamily = Display, fontSize = 17.sp, lineHeight = 22.sp, color = Ink2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                            Rule(1.dp, Line)
                        }
                    }
                    if (resCitas.isNotEmpty()) {
                        item(key = "citas") { RevSectionPad("En tus subrayados · ${resCitas.size}") }
                        items(resCitas.size) { i ->
                            val (e, n) = resCitas[i]
                            Column(Modifier.fillMaxWidth().press(haptic = false) { onClose(); ReaderActivity.open(ctx, e.id, e.name, n.value) }.padding(vertical = 10.dp)) {
                                Text("«${n.text}»", fontFamily = Display, fontSize = 17.sp, lineHeight = 22.sp, color = Ink2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                if (n.note.isNotBlank()) Text(n.note, color = Red2, fontStyle = FontStyle.Italic, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOf(e.name, n.label).filter { it.isNotBlank() }.joinToString(" · ").uppercase(), color = Grey2, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                            Rule(1.dp, Line)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun RevSectionPad(t: String) { Box(Modifier.padding(top = 18.dp, bottom = 6.dp)) { RevSection(t) } }

@Composable
private fun ResultadoFila(e: ShellEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().press(haptic = false, onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        ListCover(e.cover, e.name, 40.dp, 58.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 22.sp, color = Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(kindShort(e.kind), e.status, e.creator).filter { it.isNotBlank() }.joinToString(" · "), color = Grey2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        e.score?.let { Text("$it", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Ink2, modifier = Modifier.padding(start = 8.dp)) }
    }
    Rule(1.dp, Line)
}

/** En la portada: los próximos episodios (de aquí a dos semanas) de tus series en curso o en lista, según TVmaze. */
@Composable
private fun RevistaProximos(m: ShellModel) {
    val series = remember(m.kinds) {
        m.kinds.filter { it.isTv }.flatMap { k -> m.entries(k) }.filter { e -> e.play != null && e.play.status in 0..1 && isWebId(e.id) }
            .sortedBy { it.play?.status?.let { s -> if (s == 1) 0 else 1 } ?: 2 }.distinctBy { it.id }.take(15)
    }
    if (series.isEmpty()) return
    val hoy = LocalDate.now()
    val proximos by produceState<List<Pair<ShellEntry, Proximo>>>(emptyList(), series.map { it.id }) {
        value = series.mapNotNull { e -> Proximos.de(e.id)?.takeIf { !it.dia.isBefore(hoy) && it.dia.isBefore(hoy.plusDays(15)) }?.let { e to it } }.sortedBy { it.second.dia }
    }
    if (proximos.isEmpty()) return
    Column {
        Spacer(Modifier.height(38.dp))
        RevSection("Próximos episodios")
        proximos.take(6).forEach { (e, p) ->
            Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(64.dp)) {
                    Text(when (p.dia) { hoy -> "HOY"; hoy.plusDays(1) -> "MAÑANA"; else -> p.dia.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("es", "ES")).uppercase().trimEnd('.') },
                        color = Red2, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text("${p.dia.dayOfMonth}", color = Ink2, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp)
                }
                Column(Modifier.weight(1f)) {
                    FitTitle(e.name, 20.sp, 23.sp, color = Ink2, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(p.num, p.titulo).filter { it.isNotBlank() }.joinToString(" · "), color = Grey2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Rule(1.dp, Line)
        }
    }
}
