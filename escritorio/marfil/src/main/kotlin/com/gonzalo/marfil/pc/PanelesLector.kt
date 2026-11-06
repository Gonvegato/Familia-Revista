package com.gonzalo.marfil.pc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ---------------------------------------------------------------- piezas con los colores del lector

/** Versalitas espaciadas (los antetítulos y los botones de la revista), del color que se diga. */
@Composable
private fun Versal(t: String, color: Color, tam: TextUnit = 11.sp, modifier: Modifier = Modifier, peso: FontWeight = FontWeight.Black) =
    Text(t.uppercase(), modifier, color = color, fontFamily = Body, fontSize = tam, fontWeight = peso, letterSpacing = (tam.value * 0.13f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** El antetítulo de la revista: el cuadradito del acento y las versalitas. */
@Composable
private fun AntetituloL(t: String, c: Colores, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(c.acento)); Spacer(Modifier.width(8.dp)); Versal(t, c.tinta, 11.sp)
    }
}

/** Un enlace en versalitas. */
@Composable
private fun EnlaceL(t: String, c: Colores, acento: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Versal(t, if (acento) c.acento else c.tinta, 11.sp, modifier.mano.clickable(onClick = onClick).padding(vertical = 8.dp))

/** Un botón de la barra de arriba: su icono y su nombre debajo; encendido, del acento. */
@Composable
private fun BotonBarra(icono: ImageVector, nombre: String, c: Colores, on: Boolean = false, onClick: () -> Unit) {
    val h = remember { MutableInteractionSource() }
    val encima by h.collectIsHoveredAsState()
    Column(Modifier.hoverable(h).mano.clickable(onClick = onClick).background(if (encima) c.linea.copy(alpha = 0.07f) else Color.Transparent).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icono, nombre, tint = if (on) c.acento else c.tinta, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(2.dp))
        Versal(nombre, if (on) c.acento else c.suave, 8.5.sp, peso = FontWeight.ExtraBold)
    }
}

/** Una opción de una fila (los ajustes): la elegida, del acento y subrayada. */
@Composable
private fun Opcion(t: String, on: Boolean, c: Colores, onClick: () -> Unit) {
    Text(t, Modifier.mano.clickable(onClick = onClick).drawBehind { if (on) drawRect(c.acento, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx())) }.padding(top = 7.dp, bottom = 7.dp),
        color = if (on) c.tinta else c.suave, fontFamily = Body, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, maxLines = 1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Opciones(lista: List<Pair<Any, String>>, actual: Any, c: Colores, onSel: (Any) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) { lista.forEach { (v, t) -> Opcion(t, v == actual, c) { onSel(v) } } }
}

/** Un pequeño botón con borde, del lector. */
@Composable
fun PildoraLector(t: String, c: Colores, relleno: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.mano.clickable(onClick = onClick).background(if (relleno) c.tinta else c.superficie).border(1.dp, c.tinta).padding(horizontal = 14.dp, vertical = 9.dp)) {
        Versal(t, if (relleno) c.papel else c.tinta, 11.sp)
    }
}

@Composable
private fun Seccion(t: String, c: Colores) { Spacer(Modifier.height(18.dp)); AntetituloL(t, c); Spacer(Modifier.height(6.dp)) }

// ---------------------------------------------------------------- barras

/** La barra de arriba: volver, el libro y su capítulo, y lo que se puede abrir. */
@Composable
fun BarraArriba(st: EstadoLector, c: Colores, volver: Pantalla, nav: Nav, marcada: Boolean, alternaMarcador: () -> Unit, inmersivo: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(c.papel)) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            EnlaceL("← Biblioteca", c) { Lectura.guardaYa(); nav.ir(volver) }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(st.l.titulo, color = c.tinta, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val cap = st.libro.etiqueta(st.sitio)
                if (cap.isNotBlank()) Versal(cap, c.acento, 9.5.sp)
            }
            Spacer(Modifier.width(16.dp))
            BotonBarra(Icons.AutoMirrored.Outlined.List, "Índice", c, st.panel == Panel.INDICE) { st.panel = if (st.panel == Panel.INDICE) null else Panel.INDICE }
            BotonBarra(Icons.Outlined.EditNote, "Notas", c, st.panel == Panel.NOTAS || st.panel == Panel.MARCADORES) { st.panel = if (st.panel == Panel.NOTAS) null else Panel.NOTAS }
            BotonBarra(Icons.Outlined.Search, "Buscar", c, st.panel == Panel.BUSCAR) { st.panel = if (st.panel == Panel.BUSCAR) null else Panel.BUSCAR }
            BotonBarra(if (marcada) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (marcada) "Marcada" else "Marcar", c, marcada) { alternaMarcador() }
            BotonBarra(Icons.Outlined.TextFields, "Letra", c, st.panel == Panel.LETRA) { st.panel = if (st.panel == Panel.LETRA) null else Panel.LETRA }
            BotonBarra(Icons.Outlined.Fullscreen, "Completa", c) { inmersivo() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea))
    }
}

/** La barra de abajo: lo leído (con las marcas de cada capítulo), la página y lo que te queda. */
@Composable
fun BarraAbajo(st: EstadoLector, c: Colores) {
    val p = st.paginados[st.sitio.seccion]
    val enPaginas = st.ajustes.flujo == "paginated" && p != null && st.pagina >= 0
    val (minCap, minLibro) = st.libro.quedan(st.sitio.seccion, st.enSeccion(), st.ajustes.velocidad)
    val fr = st.fraccion
    Column(Modifier.fillMaxWidth().background(c.papel)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea))
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 6.dp, bottom = 9.dp)) {
            BarraProgreso(st, c)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pag = if (enPaginas) {
                    val n = p!!.total; val a = st.pagina + 1; val b = minOf(st.pagina + st.porVista, n)
                    if (b > a) "Páginas $a–$b de $n del capítulo" else "Página $a de $n del capítulo"
                } else ""
                Text(pag, color = c.suave, fontFamily = Body, fontSize = 11.5.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
                val quedan = buildString {
                    if (minCap >= 0.5) append("Te quedan ${LibroAbierto.duracion(minCap)} en el capítulo") else append("Última página del capítulo")
                    if (minLibro >= 1) append(" · ${LibroAbierto.duracion(minLibro)} en el libro")
                    if (st.ajustes.velocidad > 0) append(" (a tu ritmo)")
                }
                Text(quedan, color = c.suave, fontFamily = Body, fontSize = 11.5.sp, maxLines = 1)
                Spacer(Modifier.width(14.dp))
                Text("${(fr * 100).toInt()} %", color = c.tinta, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** La barra de lo leído: con una marca donde empieza cada capítulo; al pasar el ratón dice cuál, y un clic lleva allí (con «volver»). */
@Composable
private fun BarraProgreso(st: EstadoLector, c: Colores) {
    var encima by remember { mutableStateOf<Float?>(null) }
    var ancho by remember { mutableStateOf(1f) }
    val marcas = st.libro.marcasCapitulos
    val fr = st.fraccionInicio
    val dens = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(24.dp).mano.pointerInput(st) {
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull() ?: continue
                ancho = size.width.toFloat().coerceAtLeast(1f)
                when (ev.type) {
                    PointerEventType.Move, PointerEventType.Enter -> encima = (ch.position.x / ancho).coerceIn(0f, 1f)
                    PointerEventType.Exit -> encima = null
                    PointerEventType.Release -> {
                        val x = (ch.position.x / ancho).coerceIn(0f, 1f).toDouble()
                        val (s, f) = st.libro.deFraccion(x)
                        st.saltaA(st.libro.sitioEnSeccion(s, f))
                    }
                }
            }
        }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2
            drawRect(c.linea, Offset(0f, y - 1.5f), Size(size.width, 3f))
            drawRect(c.acento, Offset(0f, y - 1.5f), Size(size.width * fr.toFloat(), 3f))
            for ((m, _) in marcas) { val x = (m * size.width).toFloat(); drawRect(if (m <= fr) c.acento else c.suave.copy(alpha = 0.65f), Offset(x - 0.75f, y - 5f * dens.density), Size(1.5f, 10f * dens.density)) }
            drawCircle(c.acento, 4.5f * dens.density, Offset((size.width * fr).toFloat(), y))
            encima?.let { e -> drawRect(c.tinta.copy(alpha = 0.5f), Offset(e * size.width - 0.5f, y - 7f * dens.density), Size(1f, 14f * dens.density)) }
        }
        encima?.let { e ->
            val nombre = marcas.lastOrNull { it.first <= e + 1e-9 }?.second.orEmpty()
            val t = "${(e * 100).roundToInt()} %" + if (nombre.isNotBlank()) " · $nombre" else ""
            Box(Modifier.fillMaxSize().layout { m, k ->
                val pl = m.measure(Constraints(maxWidth = (k.maxWidth * 0.6f).toInt()))
                layout(k.maxWidth, k.maxHeight) { pl.place((e * k.maxWidth - pl.width / 2).roundToInt().coerceIn(0, (k.maxWidth - pl.width).coerceAtLeast(0)), -pl.height - 4) }
            }) {
                Box(Modifier.background(c.tinta).padding(horizontal = 10.dp, vertical = 5.dp)) { Text(t, color = c.papel, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Un aviso abajo (con su botón: «Deshacer», «Voy por aquí»…). */
@Composable
fun AvisoVista(av: AvisoLector, c: Colores, cerrar: () -> Unit) {
    Row(Modifier.background(c.tinta).padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(av.texto, color = c.papel, fontFamily = Body, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        if (av.accion != null) {
            Spacer(Modifier.width(18.dp))
            Column(Modifier.mano.clickable { cerrar(); av.accion.invoke() }) {
                Versal(av.boton, c.papel, 11.sp); Box(Modifier.padding(top = 2.dp).height(2.dp).fillMaxWidth().background(c.acento))
            }
        }
    }
}

// ---------------------------------------------------------------- menús sobre el texto

/** Coloca algo en la ventana junto a un punto (encima si cabe; si no, debajo), sin salirse. */
private fun Modifier.junto(x: Float, y: Float, hueco: Float): Modifier = this.layout { m, k ->
    val pl = m.measure(Constraints(maxWidth = k.maxWidth, maxHeight = k.maxHeight))
    layout(k.maxWidth, k.maxHeight) {
        val px = (x - pl.width / 2).roundToInt().coerceIn(12, (k.maxWidth - pl.width - 12).coerceAtLeast(12))
        val arriba = y - hueco - pl.height
        val py = (if (arriba > 70) arriba else y + hueco).roundToInt().coerceIn(8, (k.maxHeight - pl.height - 8).coerceAtLeast(8))
        pl.place(px, py)
    }
}

/** El menú de lo seleccionado (subrayar, nota, copiar, buscar) o de un subrayado (su nota, copiarlo, quitarlo). */
@Composable
fun MenuDeTexto(st: EstadoLector, menu: MenuTexto, c: Colores, onSubrayar: (Seleccion, Boolean) -> Unit, onCopiar: (String) -> Unit, onQuitar: (Marca) -> Unit, textoDe: (Seleccion) -> String) {
    val dens = LocalDensity.current
    Box(Modifier.fillMaxSize().junto(menu.x + st.origenArea.x, menu.y + st.origenArea.y, with(dens) { 18.dp.toPx() })) {
        Column(Modifier.widthIn(max = 520.dp).background(c.superficie).border(1.dp, c.linea).drawTop(c.tinta).padding(horizontal = 16.dp, vertical = 10.dp)) {
            val m = menu.marca
            if (m != null) {
                Text(m.texto.let { if (it.length > 160) it.take(160) + "…" else it }, color = c.tinta, fontFamily = Display, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (m.nota.isNotBlank()) Text(m.nota, color = c.acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 6, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    EnlaceL(if (m.nota.isNotBlank()) "Editar nota" else "Añadir nota", c) { st.menu = null; st.editando = m }
                    EnlaceL("Copiar", c) { st.menu = null; onCopiar(m.texto) }
                    EnlaceL("Buscar", c) { st.menu = null; st.busqueda = m.texto.take(80); st.hallazgos = null; st.panel = Panel.BUSCAR }
                    EnlaceL("Quitar", c, acento = true) { onQuitar(m) }
                }
            } else menu.seleccion?.let { s ->
                val t = textoDe(s)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    EnlaceL("Subrayar", c) { onSubrayar(s, false) }
                    EnlaceL("Nota", c) { onSubrayar(s, true) }
                    EnlaceL("Copiar", c) { st.menu = null; onCopiar(t) }
                    EnlaceL("Buscar", c) { st.menu = null; st.seleccion = null; st.busqueda = t.take(80); st.hallazgos = null; st.panel = Panel.BUSCAR }
                    if (!t.contains(' ') && t.trim { !it.isLetterOrDigit() }.length in 2..40) EnlaceL("Definir", c) {
                        st.menu = null; st.seleccion = null
                        st.definicion = Definiendo(t.trim { !it.isLetterOrDigit() }, menu.x, menu.y)
                    }
                }
            }
        }
    }
}

/** La raya de tinta de arriba de las hojas (como las del móvil). */
private fun Modifier.drawTop(c: Color): Modifier = this.drawBehind { drawRect(c, Offset.Zero, Size(size.width, 3.dp.toPx())) }

/** Una nota al pie, en una tarjeta junto a su llamada (como la hoja de notas del móvil). */
@Composable
fun NotaAlPieVista(st: EstadoLector, n: NotaAbierta, c: Colores) {
    val dens = LocalDensity.current
    val a = st.ajustes
    Box(Modifier.fillMaxSize().junto(n.x + st.origenArea.x, n.y + st.origenArea.y, with(dens) { 16.dp.toPx() })) {
        Column(Modifier.width(500.dp).heightIn(max = 380.dp).background(c.superficie).border(1.dp, c.linea).drawTop(c.tinta).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Versal(n.nombre, c.acento, 11.sp, Modifier.weight(1f))
                EnlaceL("Cerrar", c) { st.nota = null }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                n.textos.forEach { t ->
                    Text(t, color = c.tinta, fontFamily = Letras.de(a.letra, a.gruesa), fontSize = (LETRA_BASE * a.tamano / 100f * 0.86f).sp, lineHeight = (LETRA_BASE * a.tamano / 100f * 0.86f * 1.45f).sp, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
            EnlaceL("Ir a la nota →", c, acento = true) { st.nota = null; st.saltaA(st.libro.destino(n.enlace)) }
        }
    }
}

/** Lo que significa una palabra (como el del móvil): sus acepciones del Wikcionario y, para seguir, la RAE y el Wikcionario. */
@Composable
fun DefinicionVista(st: EstadoLector, d: Definiendo, c: Colores) {
    val dens = LocalDensity.current
    fun abre(u: String) { Thread { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(u)) } }.start() }
    val w = java.net.URLEncoder.encode(d.palabra.lowercase(), "UTF-8").replace("+", "%20")
    Box(Modifier.fillMaxSize().junto(d.x + st.origenArea.x, d.y + st.origenArea.y, with(dens) { 16.dp.toPx() })) {
        Column(Modifier.width(480.dp).heightIn(max = 400.dp).background(c.superficie).border(1.dp, c.linea).drawTop(c.tinta).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(d.resultado?.palabra ?: d.palabra, Modifier.weight(1f), color = c.tinta, fontFamily = Display, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Versal(Diccionario.lengua(st.epub.idioma), c.acento, 10.sp)
            }
            Spacer(Modifier.height(6.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                val r = d.resultado
                when {
                    d.fallo.isNotBlank() -> Text(d.fallo, color = c.suave, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                    r == null -> Text("Buscando el significado…", color = c.suave, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                    r.defs.isEmpty() -> Text("El Wikcionario no tiene esta palabra. Prueba con la RAE.", color = c.suave, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                    else -> {
                        if (r.nota.isNotBlank()) Text(r.nota.trimEnd('.') + ":", color = c.suave, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                        else if (!r.palabra.equals(d.palabra, true)) Text("De «${r.palabra}»:", color = c.suave, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                        r.defs.forEachIndexed { i, x -> Row(Modifier.padding(vertical = 3.dp)) { Text("${i + 1}.", Modifier.width(22.dp), color = c.acento, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(x, color = c.tinta, fontFamily = Body, fontSize = 14.5.sp, lineHeight = 20.sp) } }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                EnlaceL("RAE", c, acento = true) { abre("https://dle.rae.es/$w") }
                EnlaceL("Wikcionario", c, acento = true) { abre("https://es.wiktionary.org/wiki/$w") }
                Spacer(Modifier.weight(1f))
                EnlaceL("Cerrar", c) { st.definicion = null }
            }
        }
    }
}

/** Escribir la nota de un subrayado. */
@Composable
fun EditorNota(st: EstadoLector, m: Marca, c: Colores) {
    var texto by remember(m) { mutableStateOf(m.nota) }
    val foco = remember { FocusRequester() }
    fun guarda() {
        val nuevo = texto.trim()
        st.editando = null
        if (nuevo != m.nota) { Marcas.ponNota(m, nuevo); st.avisa(if (nuevo.isBlank()) "Nota borrada" else "Nota guardada") }
    }
    Box(Modifier.fillMaxSize().background(c.velo).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { st.editando = null }, contentAlignment = Alignment.Center) {
        Column(Modifier.width(600.dp).background(c.superficie).drawTop(c.tinta).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}.padding(28.dp)) {
            Text("Tu nota", color = c.tinta, fontFamily = Display, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp)); Box(Modifier.fillMaxWidth().height(2.dp).background(c.tinta)); Spacer(Modifier.height(14.dp))
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(c.tinta)); Spacer(Modifier.width(12.dp))
                Text(m.texto, color = c.tinta, fontFamily = Display, fontSize = 16.sp, lineHeight = 23.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
            BasicTextField(texto, { texto = it }, Modifier.fillMaxWidth().heightIn(min = 130.dp).border(1.dp, c.tinta).padding(12.dp).focusRequester(foco)
                .onPreviewKeyEvent { k -> if (k.type == KeyEventType.KeyDown && k.key == Key.Escape) { st.editando = null; true } else false },
                textStyle = TextStyle(color = c.tinta, fontFamily = Display, fontSize = 17.sp, lineHeight = 25.sp), cursorBrush = SolidColor(c.acento),
                decorationBox = { inner -> if (texto.isEmpty()) Text("Escribe lo que te sugiere…", color = c.suave, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp); inner() })
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text("Se guarda en tu cuenta: la verás en el móvil y en la tablet.", Modifier.weight(1f), color = c.suave, fontFamily = Body, fontSize = 12.sp)
                PildoraLector("Cancelar", c) { st.editando = null }
                PildoraLector("Guardar", c, relleno = true) { guarda() }
            }
        }
    }
    LaunchedEffect(m) { delay(60); runCatching { foco.requestFocus() } }
}

// ---------------------------------------------------------------- el panel de la derecha

@Composable
fun PanelesLector(st: EstadoLector, c: Colores, onQuitar: (Marca) -> Unit) {
    val abierto = st.panel != null
    AnimatedVisibility(abierto, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Box(Modifier.fillMaxSize().background(c.velo).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { st.panel = null })
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(abierto, enter = slideInHorizontally(tween(260)) { it } + fadeIn(tween(200)), exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(160))) {
            // (al cerrarse, se sigue viendo el que estaba mientras se va)
            val recordado = remember { arrayOf(Panel.INDICE) }
            st.panel?.let { recordado[0] = it }
            val ultimo = recordado[0]
            Column(Modifier.width(420.dp).fillMaxHeight().background(c.superficie).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 26.dp).padding(top = 22.dp)) {
                if (ultimo == Panel.LETRA) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Letra y página", Modifier.weight(1f), color = c.tinta, fontFamily = Display, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                        EnlaceL("Cerrar", c) { st.panel = null }
                    }
                    Spacer(Modifier.height(6.dp)); Box(Modifier.fillMaxWidth().height(2.dp).background(c.tinta))
                    PanelLetra(st, c)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pes = listOf(Panel.INDICE to "Índice", Panel.NOTAS to "Notas", Panel.MARCADORES to "Marcadores", Panel.BUSCAR to "Buscar")
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) { pes.forEach { (p, n) -> Opcion(n, ultimo == p, c) { st.panel = p } } }
                        EnlaceL("Cerrar", c) { st.panel = null }
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(c.tinta))
                    when (ultimo) {
                        Panel.INDICE -> PanelIndice(st, c)
                        Panel.NOTAS -> PanelNotas(st, c, onQuitar)
                        Panel.MARCADORES -> PanelMarcadores(st, c, onQuitar)
                        Panel.BUSCAR -> PanelBuscar(st, c)
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun Cifras(pares: List<Pair<String, String>>, c: Colores) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        pares.chunked(2).forEach { fila ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                fila.forEachIndexed { i, (v, e) ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.linea))
                    Column(Modifier.weight(1f).padding(start = if (i > 0) 14.dp else 0.dp, top = 9.dp, bottom = 9.dp)) {
                        Text(v, color = c.tinta, fontFamily = Display, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        Versal(e, c.suave, 9.5.sp)
                    }
                }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.tinta))
    }
}

@Composable
private fun PanelIndice(st: EstadoLector, c: Colores) {
    val lib = st.libro
    val indice = lib.epub.indice
    val actual = lib.capitulo(st.sitio)
    val lista = rememberLazyListState()
    // Lo que llevas: tu tiempo con este libro (en todos tus dispositivos), hoy, lo que te queda y los días seguidos
    val (hoy, total, racha) = remember(st.sitio.seccion) { Libros.tiempoDe(st.l.id) }
    val queda = lib.quedan(st.sitio.seccion, st.enSeccion(), st.ajustes.velocidad).second
    val cifras = buildList {
        if (total > 0) add(LibroAbierto.duracion(total / 60_000.0).ifBlank { "0 min" } to "leído en total")
        add((if (hoy > 0) LibroAbierto.duracion(hoy / 60_000.0) else "0 min") to "hoy")
        add(LibroAbierto.duracion(queda) to if (st.ajustes.velocidad > 0) "para acabarlo a tu ritmo" else "para acabarlo")
        if (racha > 1) add("$racha" to "días seguidos")
    }
    LaunchedEffect(Unit) { indice.indexOf(actual).takeIf { it > 3 }?.let { lista.scrollToItem(it - 3) } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), lista, contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Cifras(cifras, c) }
            item {
                var ir by remember { mutableStateOf("") }
                Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Versal("Ir al", c.tinta, 11.sp); Spacer(Modifier.width(10.dp))
                    BasicTextField(ir, { ir = it.filter { ch -> ch.isDigit() }.take(3) }, Modifier.width(70.dp).border(1.dp, c.linea).padding(horizontal = 8.dp, vertical = 6.dp)
                        .onPreviewKeyEvent { k -> if (k.type == KeyEventType.KeyDown && k.key == Key.Enter) { ir.toIntOrNull()?.let { n -> val (s, f) = lib.deFraccion(n.coerceIn(0, 100) / 100.0); st.panel = null; st.saltaA(lib.sitioEnSeccion(s, f)) }; true } else false },
                        textStyle = TextStyle(color = c.tinta, fontFamily = Body, fontSize = 15.sp), singleLine = true, cursorBrush = SolidColor(c.acento))
                    Spacer(Modifier.width(8.dp)); Versal("%", c.suave, 11.sp)
                    Spacer(Modifier.width(14.dp))
                    EnlaceL("Ir →", c, acento = true) { ir.toIntOrNull()?.let { n -> val (s, f) = lib.deFraccion(n.coerceIn(0, 100) / 100.0); st.panel = null; st.saltaA(lib.sitioEnSeccion(s, f)) } }
                }
            }
            if (indice.isEmpty()) item { Text("Este libro no trae índice.", color = c.suave, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(vertical = 24.dp)) }
            itemsIndexed(indice) { _, en ->
                val es = en === actual
                val d = lib.destino(en)
                val fr = lib.fraccion(d.seccion, if (d.bloque == 0) 0.0 else lib.fraccionEnSeccion(d))
                Column(Modifier.fillMaxWidth().mano.clickable { st.panel = null; st.saltaA(d) }) {
                    Row(Modifier.fillMaxWidth().padding(start = (minOf(en.nivel, 3) * 16).dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(en.titulo.ifBlank { "Sin título" }, Modifier.weight(1f), color = if (es) c.acento else c.tinta, fontFamily = Display, fontSize = if (en.nivel == 0) 17.sp else 15.sp,
                            fontWeight = if (es) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(10.dp))
                        Versal(if (es) "Estás aquí" else "${(fr * 100).toInt()} %", if (es) c.acento else c.suave, 9.5.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea))
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(lista), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** Ir a una marca (un subrayado, un marcador) y destacarla un momento. */
private fun irAMarca(st: EstadoLector, m: Marca) {
    val u = st.epub.ubica(m.valor)
    st.panel = null
    if (u != null) {
        val d = u.desde?.let { Sitio(u.seccion, it.first, it.second) } ?: Sitio(u.seccion, 0)
        st.saltaA(d)
        if (m.esSubrayado) u.hasta?.let { st.destello = d to Sitio(u.seccion, it.first, it.second) }
    } else m.fraccion?.let { fr -> val (s, f) = st.libro.deFraccion(fr); st.saltaA(st.libro.sitioEnSeccion(s, f)) }
}

private fun fecha(ms: Long): String = if (ms <= 0) "" else fechaDe(ms)

@Composable
private fun PanelNotas(st: EstadoLector, c: Colores, onQuitar: (Marca) -> Unit) {
    com.gonzalo.familia.pc.LaFamilia.cambios
    var soloConNota by remember { mutableStateOf(false) }
    val todas = Marcas.de(st.l.id, Marcas.SUBRAYADOS).filter { !it.delTextoDeUnPdf }
        .sortedWith { a, b -> val x = EpubCfi.lee(a.valor); val y = EpubCfi.lee(b.valor); (if (x != null && y != null) EpubCfi.compara(x, y) else 0).takeIf { it != 0 } ?: a.creada.compareTo(b.creada) }
    val conNota = todas.count { it.nota.isNotBlank() }
    val lista = if (soloConNota) todas.filter { it.nota.isNotBlank() } else todas
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Versal("${todas.size} subrayado${if (todas.size == 1) "" else "s"}" + if (conNota > 0) " · $conNota con nota" else "", c.suave, 10.sp, Modifier.weight(1f))
            if (conNota in 1 until todas.size) { Opcion("Todos", !soloConNota, c) { soloConNota = false }; Spacer(Modifier.width(14.dp)); Opcion("Con nota", soloConNota, c) { soloConNota = true } }
        }
        val ls = rememberLazyListState()
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), ls) {
                if (lista.isEmpty()) item { Text(if (todas.isEmpty()) "Selecciona un trozo del texto con el ratón y elige Subrayar o Nota. Lo que subrayes en el móvil sale aquí." else "Ningún subrayado tiene nota todavía.",
                    color = c.suave, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 24.dp)) }
                items(lista, key = { it.ref }) { m ->
                    Column(Modifier.fillMaxWidth().mano.clickable { irAMarca(st, m) }.padding(vertical = 12.dp)) {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            Box(Modifier.width(3.dp).fillMaxHeight().background(c.tinta)); Spacer(Modifier.width(10.dp))
                            Text(m.texto, color = c.tinta, fontFamily = Display, fontSize = 15.5.sp, lineHeight = 22.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
                        }
                        if (m.nota.isNotBlank()) Text("✎ " + m.nota, color = c.acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
                        Versal(listOfNotNull(m.etiqueta.ifBlank { null }, m.fraccion?.let { "${(it * 100).toInt()} %" }, fecha(m.creada).ifBlank { null }).joinToString(" · "), c.suave, 9.5.sp, Modifier.padding(top = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            EnlaceL(if (m.nota.isNotBlank()) "Editar nota" else "Añadir nota", c) { st.editando = m }
                            EnlaceL("Quitar", c, acento = true) { onQuitar(m) }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea))
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(ls), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        if (todas.isNotEmpty()) Row(Modifier.padding(vertical = 12.dp)) {
            PildoraLector("Copiar todas", c) {
                val t = "${st.l.titulo}\n\n" + todas.joinToString("\n\n") { m -> "«${m.texto}»" + (if (m.nota.isNotBlank()) "\n   ✎ ${m.nota}" else "") + (if (m.etiqueta.isNotBlank()) "\n   — ${m.etiqueta}" else "") }
                runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(t), null) }
                st.avisa("Copiadas ${todas.size}")
            }
        }
    }
}

@Composable
private fun PanelMarcadores(st: EstadoLector, c: Colores, onQuitar: (Marca) -> Unit) {
    com.gonzalo.familia.pc.LaFamilia.cambios
    val lista = Marcas.de(st.l.id, Marcas.MARCADORES).filter { !it.delTextoDeUnPdf }.sortedBy { it.fraccion ?: 0.0 }
    LazyColumn(Modifier.fillMaxSize()) {
        if (lista.isEmpty()) item { Text("Marca una página con «Marcar» de arriba (o la tecla B). Los marcadores del móvil salen aquí.", color = c.suave, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 24.dp)) }
        items(lista, key = { it.ref }) { m ->
            Row(Modifier.fillMaxWidth().mano.clickable { irAMarca(st, m) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.etiqueta.ifBlank { "Marcador" }, color = c.tinta, fontFamily = Display, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Versal(listOfNotNull(m.fraccion?.let { "${(it * 100).toInt()} %" }, fecha(m.creada).ifBlank { null }).joinToString(" · "), c.suave, 9.5.sp, Modifier.padding(top = 3.dp))
                }
                EnlaceL("Quitar", c, acento = true) { onQuitar(m) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea))
        }
    }
}

@Composable
private fun PanelBuscar(st: EstadoLector, c: Colores) {
    val scope = rememberCoroutineScope()
    val foco = remember { FocusRequester() }
    var trabajo by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun busca() {
        val q = st.busqueda.trim()
        if (q.isEmpty()) return
        trabajo?.cancel()
        st.buscando = true; st.hallazgos = null
        trabajo = scope.launch { val r = withContext(Dispatchers.Default) { st.libro.busca(q) }; st.hallazgos = r; st.buscando = false }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { foco.requestFocus() }; if (st.busqueda.isNotBlank() && st.hallazgos == null) busca() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(st.busqueda, { st.busqueda = it }, Modifier.weight(1f).focusRequester(foco)
                .onPreviewKeyEvent { k -> if (k.type == KeyEventType.KeyDown && k.key == Key.Enter) { busca(); true } else if (k.type == KeyEventType.KeyDown && k.key == Key.Escape) { st.panel = null; true } else false },
                textStyle = TextStyle(color = c.tinta, fontFamily = Display, fontSize = 19.sp), singleLine = true, cursorBrush = SolidColor(c.acento),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { busca() }),
                decorationBox = { inner -> Column { Box { if (st.busqueda.isEmpty()) Text("Palabra o frase", color = c.suave, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp); inner() }; Spacer(Modifier.height(6.dp)); Box(Modifier.fillMaxWidth().height(2.dp).background(c.tinta)) } })
            Spacer(Modifier.width(14.dp))
            PildoraLector("Buscar", c, relleno = true) { busca() }
        }
        val r = st.hallazgos
        Versal(when { st.buscando -> "Buscando…"; r == null -> "Sin mirar mayúsculas ni tildes"; r.isEmpty() -> "Nada encontrado"; else -> "${r.size}${if (r.size >= 300) "+" else ""} resultado${if (r.size == 1) "" else "s"}" }, c.suave, 10.sp, Modifier.padding(top = 10.dp, bottom = 4.dp))
        val ls = rememberLazyListState()
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), ls) {
                items(r.orEmpty()) { h ->
                    Column(Modifier.fillMaxWidth().mano.clickable { st.panel = null; st.saltaA(h.desde); st.destello = h.desde to h.hasta }.padding(vertical = 10.dp)) {
                        if (h.capitulo.isNotBlank()) Versal(h.capitulo, c.suave, 9.5.sp)
                        Text(buildAnnotatedString {
                            append("…" + h.antes); withStyle(SpanStyle(color = c.acento, fontWeight = FontWeight.Bold)) { append(h.frase) }; append(h.despues + "…")
                        }, color = c.tinta, fontFamily = Display, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea))
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(ls), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

/** Los ajustes del lector, los mismos que en el móvil (y viajan con el libro). */
@Composable
private fun PanelLetra(st: EstadoLector, c: Colores) {
    val a = st.ajustes
    fun pon(k: String, v: Any?) { st.ajustes = st.ajustes.con(k, v) }
    Box(Modifier.fillMaxSize()) {
        val sc = rememberScrollState()
        Column(Modifier.fillMaxSize().verticalScroll(sc).padding(bottom = 26.dp)) {
            Seccion("Tema", c)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AjustesLectura.TEMAS.forEach { t ->
                    val (fondo, tinta) = if (t.id == "app") Paper to Charcoal else Color(t.fondo) to Color(t.tinta)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(50.dp).background(fondo).border(if (a.tema == t.id) 3.dp else 1.dp, if (a.tema == t.id) c.acento else c.linea).mano.clickable { pon("theme", t.id) }, contentAlignment = Alignment.Center) {
                            Text("Aa", color = tinta, fontFamily = Display, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(t.nombre, color = if (a.tema == t.id) c.tinta else c.suave, fontFamily = Body, fontSize = 9.5.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Opciones(listOf(false to "Siempre este", true to "Noche de 21:00 a 7:00"), a.nocheAuto, c) { pon("nightAuto", it) }
            Seccion("Luz cálida", c)
            Deslizador(a.calida.toFloat(), c) { pon("warm", Math.round(it * 20) / 20.0) }
            Seccion("Tamaño de letra", c)
            Paso("${a.tamano} %", c, { pon("size", (a.tamano - 10).coerceAtLeast(60).toLong()) }, { pon("size", (a.tamano + 10).coerceAtMost(240).toLong()) })
            Seccion("Tipo de letra", c)
            Opciones(AjustesLectura.LETRAS.map { it.first to it.second }, a.letra, c) { pon("font", it) }
            if (a.letra in setOf("original", "literata", "garamond")) Opciones(listOf(0L to "Grosor normal", 1L to "Un poco más gruesa"), if (a.gruesa) 1L else 0L, c) { pon("weight", it) }
            Seccion("Interlineado", c)
            Paso(String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", a.interlineado), c,
                { pon("spacing", Math.round((a.interlineado - 0.1).coerceAtLeast(1.0) * 10) / 10.0) }, { pon("spacing", Math.round((a.interlineado + 0.1).coerceAtMost(2.4) * 10) / 10.0) })
            Seccion("Márgenes", c)
            Opciones(listOf("s" to "Estrechos", "m" to "Normales", "l" to "Anchos"), a.margen, c) { pon("margin", it) }
            Seccion("Texto", c)
            Opciones(listOf(true to "Justificado", false to "Alineado a la izquierda"), a.justificado, c) { pon("justify", it) }
            Seccion("Párrafos", c)
            Opciones(listOf(0L to "Como el libro", 1L to "Con un poco de aire", 2L to "Separados, sin sangría"), a.parrafos.toLong(), c) { pon("paraSpace", it) }
            Seccion("Pasar página", c)
            Opciones(listOf("paginated" to "Páginas", "scrolled" to "Desplazamiento continuo"), a.flujo, c) { pon("flow", it); st.pagina = -1; st.paginados.clear() }
            if (a.flujo == "paginated") Opciones(listOf(true to "Con animación", false to "Sin animación"), a.animado, c) { pon("animated", it) }
            if (a.flujo == "paginated") { Seccion("Columnas", c); Opciones(listOf(false to "Una", true to "Dos, como un libro abierto"), a.columnas, c) { pon("columns", it) } }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea))
            Text("Estos ajustes son los del lector del móvil: se guardan con el libro (y de partida para los demás) y los verás igual en el móvil y en la tablet.",
                color = c.suave, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
            Text("Teclas: ← → (o la rueda) pasan página · Inicio y Fin, principio y final del capítulo · B marca la página · T el índice · N tus notas · F solo el libro, a pantalla completa · Ctrl+F buscar · Ctrl + y − el tamaño · Esc cierra.",
                color = c.suave, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
        }
        VerticalScrollbar(rememberScrollbarAdapter(sc), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** Un deslizador fino, como la barra de lo leído (0–1). */
@Composable
private fun Deslizador(valor: Float, c: Colores, onCambio: (Float) -> Unit) {
    val dens = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(30.dp).mano.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull() ?: continue
                if (ev.type == PointerEventType.Press || (ev.type == PointerEventType.Move && ch.pressed)) { onCambio((ch.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)); ch.consume() }
            }
        }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2; val r = 7f * dens.density
            val x = r + (size.width - 2 * r) * valor.coerceIn(0f, 1f)
            drawRect(c.linea, Offset(r, y - 1.5f), Size(size.width - 2 * r, 3f))
            drawRect(c.acento, Offset(r, y - 1.5f), Size(x - r, 3f))
            drawCircle(c.acento, r, Offset(x, y))
        }
    }
}

@Composable
private fun Paso(valor: String, c: Colores, menos: () -> Unit, mas: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).border(1.dp, c.tinta).mano.clickable(onClick = menos), contentAlignment = Alignment.Center) { Text("−", color = c.tinta, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        Text(valor, Modifier.width(86.dp), color = c.tinta, fontFamily = Display, fontSize = 22.sp, textAlign = TextAlign.Center)
        Box(Modifier.size(44.dp).border(1.dp, c.tinta).mano.clickable(onClick = mas), contentAlignment = Alignment.Center) { Text("+", color = c.tinta, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}
