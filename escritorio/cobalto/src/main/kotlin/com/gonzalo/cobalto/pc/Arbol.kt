@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.gonzalo.cobalto.pc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed as ctrlPuntero
import androidx.compose.ui.input.pointer.isMetaPressed as metaPuntero
import androidx.compose.ui.input.pointer.isShiftPressed as mayusPuntero
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.ArbolEscribir
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.Escritos
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * El árbol de relaciones: las fichas en óvalos sobre un lienzo libre, unidas por líneas con la palabra que tú quieras en medio
 * (enemigos, amantes, hogar…). Con el ratón: se arrastran las fichas (su sitio viaja con la cuenta: ax, ay) y el fondo, la
 * rueda acerca y aleja hacia el puntero (dos dedos en el trackpad mueven el lienzo), doble clic abre la ficha, el botón derecho
 * tiene lo demás, y arrastrar con Mayús de una ficha a otra las une. «Ordenar» las reparte solas. Es el árbol del móvil
 * (EscribirArbol.kt) y el de Atrio del PC (escribir-arbol.js): las mismas medidas, el mismo orden y lo mismo guardado.
 */

// Las medidas y la colocación del árbol (ArbolEscribir) son las del móvil: EscribirArbol.kt, el mismo archivo, copiado al compilar.

/** El desplazamiento y el zoom del lienzo de cada obra, mientras la app está abierta. */
private class VistaArbol(var x: Float = 0f, var y: Float = 0f, var k: Float = 1f, var puesta: Boolean = false)
private val vistas = HashMap<String, VistaArbol>()

/** Lo que el gesto del árbol necesita «de ahora» (se pone al día en cada composición). */
private class VivoArbol(var pos: MutableMap<String, Offset>, var nodoEn: (Offset) -> Doc?, var etqEn: (Offset) -> Doc?)

@Composable
fun ArbolObra(obra: Doc, nav: Nav, abre: AbreDoc, foco: String?, abrirEscena: (String) -> Unit) {
    val e = Cuaderno.escritos
    val ver = e.version
    val pid = obra.id
    val fs = remember(ver, pid) { e.fichas(pid) }
    val rels = remember(ver, pid) { e.relaciones(pid).filter { e.get(it.s("a")) != null && e.get(it.s("b")) != null } }
    val cogida = remember(pid) { arrayOf<String?>(null) }
    val ultimo = remember(pid) { arrayOfNulls<Map<String, Offset>>(1) }
    val pos = remember(ver, pid) {
        mutableStateMapOf<String, Offset>().also { m ->
            m.putAll(ArbolEscribir.posiciones(fs))
            cogida[0]?.let { id -> ultimo[0]?.get(id)?.let { m[id] = it } }
            ultimo[0] = m
        }
    }
    val v = remember(pid) { vistas.getOrPut(pid) { VistaArbol() } }
    var tick by remember { mutableIntStateOf(0) }
    var lienzo by remember { mutableStateOf(IntSize.Zero) }
    var uniendo by remember { mutableStateOf(false) }
    var primera by remember { mutableStateOf<String?>(null) }
    var elegida by remember(pid) { mutableStateOf(foco) }
    var encima by remember { mutableStateOf<String?>(null) }
    var ordenar by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Pair<Doc, Offset>?>(null) }
    var cuerda by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    val dens = LocalDensity.current.density
    val medidor = rememberTextMeasurer()
    val tinta = Charcoal; val papel = Paper; val acento = Acento
    val porId = fs.associateBy { it.id }
    val scope = rememberCoroutineScope()
    val fLienzo = remember { FocusRequester() }

    fun aMundo(p: Offset) = Offset((p.x - v.x) / (dens * v.k), (p.y - v.y) / (dens * v.k))
    fun centra(soloId: String? = null) {
        if (lienzo.width == 0) return
        val ps = if (soloId != null && pos[soloId] != null) listOf(pos[soloId]!!) else pos.values.toList()
        if (ps.isEmpty()) { v.x = lienzo.width / 2f; v.y = lienzo.height / 2f; tick++; return }
        val minX = ps.minOf { it.x } - 120; val maxX = ps.maxOf { it.x } + 120
        val minY = ps.minOf { it.y } - 80; val maxY = ps.maxOf { it.y } + 80
        v.k = if (soloId != null) 1.2f else min(1.3f, max(0.3f, min(lienzo.width / dens / (maxX - minX), lienzo.height / dens / (maxY - minY))))
        v.x = lienzo.width / 2f - (minX + maxX) / 2 * dens * v.k; v.y = lienzo.height / 2f - (minY + maxY) / 2 * dens * v.k
        tick++
    }
    fun zoom(f: Float, c: Offset = Offset(lienzo.width / 2f, lienzo.height / 2f)) {
        val k = (v.k * f).coerceIn(0.25f, 3f)
        v.x = c.x - (c.x - v.x) * k / v.k; v.y = c.y - (c.y - v.y) * k / v.k; v.k = k; tick++
    }
    var focoUsado by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lienzo, foco) {
        if (lienzo.width == 0) return@LaunchedEffect
        if (foco != null && foco != focoUsado) { focoUsado = foco; v.puesta = true; elegida = foco; centra(foco) }
        else if (!v.puesta) { v.puesta = true; centra() }
    }

    val estiloEtq = TextStyle(fontFamily = Body, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
    val ALTO = 22f
    val etqs = remember(rels, tinta) { rels.associate { r -> val c = colorEscrito(r.s("color"), tinta); r.id to medidor.measure(r.s("titulo").ifBlank { "unión" }.uppercase(), estiloEtq.copy(color = c)) } }
    fun anchoEtq(r: Doc) = max(34f, (etqs[r.id]?.size?.width ?: 0) / dens + 22f)
    val textosNodo = remember(fs, tinta) {
        fs.associate { f ->
            val t = ArbolEscribir.tam(f); val fz = ArbolEscribir.TAM.getValue(t).third; val base = colorEscrito(f.s("color"))
            val ls = ArbolEscribir.lineasNombre(f.s("nombre"), ArbolEscribir.letras(t))
            val estilo = TextStyle(fontSize = fz.sp, fontWeight = FontWeight.Bold, color = tinta, fontFamily = Grotesca, lineHeight = (fz * 1.08f).sp, textAlign = TextAlign.Center)
            f.id to (medidor.measure(ls.joinToString("\n"), estilo) to medidor.measure(singularFicha(f.s("tipo")).uppercase(), TextStyle(fontFamily = Body, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = lerp(base, tinta, 0.35f))))
        }
    }
    class Linea(val r: Doc, val a: Offset, val b: Offset, val ctrl: Offset, val etq: Offset, val ancho: Float)
    fun lineas(): List<Linea> {
        val pares = rels.groupBy { listOf(it.s("a"), it.s("b")).sorted().joinToString("|") }
        return rels.mapNotNull { r ->
            val A = pos[r.s("a")] ?: return@mapNotNull null; val B = pos[r.s("b")] ?: return@mapNotNull null
            val fa = porId[r.s("a")] ?: return@mapNotNull null; val fb = porId[r.s("b")] ?: return@mapNotNull null
            val g = pares.getValue(listOf(r.s("a"), r.s("b")).sorted().joinToString("|")); val n = g.size; val i = g.indexOf(r)
            val lo = listOf(r.s("a"), r.s("b")).sorted()[0]
            val (P0, Q0) = if (lo == r.s("a")) A to B else B to A
            val dx = Q0.x - P0.x; val dy = Q0.y - P0.y; val len = max(1f, hypot(dx, dy))
            val nx = -dy / len; val ny = dx / len
            val sep = g.maxOf { abs(nx) * anchoEtq(it) / 2 + abs(ny) * ALTO / 2 } * 2 + 10
            val off = if (n > 1) (i - (n - 1) / 2f) * sep * 2 else 0f
            val m = Offset((A.x + B.x) / 2 + nx * off, (A.y + B.y) / 2 + ny * off)
            fun borde(f: Doc, d: Offset, extra: Float): Offset { val (rx, ry, _) = ArbolEscribir.TAM.getValue(ArbolEscribir.tam(f)); val l = hypot(d.x / (rx + extra), d.y / (ry + extra)).takeIf { it > 0 } ?: 1f; return Offset(d.x / l, d.y / l) }
            Linea(r, A + borde(fa, m - A, 3f), B + borde(fb, m - B, if (r.i("flecha") == 1) 6f else 3f), m, Offset((A.x + B.x) / 4 + m.x / 2, (A.y + B.y) / 4 + m.y / 2), anchoEtq(r))
        }
    }
    fun rectEtq(l: Linea) = Rect(l.etq.x - l.ancho / 2, l.etq.y - ALTO / 2, l.etq.x + l.ancho / 2, l.etq.y + ALTO / 2)
    fun nodoEn(p: Offset): Doc? { val m = aMundo(p); return fs.lastOrNull { f -> val c = pos[f.id] ?: return@lastOrNull false; ArbolEscribir.dentro(m, c, ArbolEscribir.tam(f)) } }
    fun etqEn(p: Offset): Doc? { val m = aMundo(p); return lineas().lastOrNull { rectEtq(it).inflate(4f).contains(m) }?.r }
    fun huecoLibre(c: Offset, t: Int): Offset {
        val (rx, ry, _) = ArbolEscribir.TAM.getValue(t)
        val ls = lineas()
        fun libre(p: Offset): Boolean =
            fs.none { f -> val q = pos[f.id] ?: return@none false; val (ax, ay, _) = ArbolEscribir.TAM.getValue(ArbolEscribir.tam(f)); abs(p.x - q.x) < rx + ax + 10 && abs(p.y - q.y) < ry + ay + 10 } &&
                ls.none { l -> rectEtq(l).inflate(8f).let { r -> p.x + rx > r.left && p.x - rx < r.right && p.y + ry > r.top && p.y - ry < r.bottom } }
        if (libre(c)) return c
        for (anillo in 1..14) { val r = anillo * 50f; for (i in 0 until 12) { val ang = i * (2 * PI / 12); val p = Offset(c.x + (r * cos(ang)).toFloat(), c.y + (r * sin(ang)).toFloat()); if (libre(p)) return p } }
        return c
    }
    /** Une dos fichas: la unión nueva se abre para ponerle su palabra. */
    fun une(a: String, b: String) {
        if (a == b) return
        val r = e.nuevo(Escritos.T_REL, pid) { put("a", a); put("b", b); put("titulo", ""); put("texto", ""); put("flecha", 0) }
        abre.editar(r.id, esNuevo = true)
    }
    fun pulsa(f: Doc) {
        if (uniendo) {
            val x = primera
            when { x == null -> primera = f.id; x == f.id -> primera = null; else -> { primera = null; uniendo = false; une(x, f.id) } }
        } else elegida = f.id
    }
    fun ordenarAhora() {
        val destino = ArbolEscribir.ordena(fs.map { it.id }, rels.map { it.s("a") to it.s("b") }, pos)
        val desde = pos.toMap()
        scope.launch {
            val t0 = withFrameNanos { it }
            while (true) {
                val t = withFrameNanos { it }
                val u = ((t - t0) / 650_000_000f).coerceIn(0f, 1f); val q = 1 - (1 - u) * (1 - u) * (1 - u)
                fs.forEach { f -> val a0 = desde[f.id] ?: return@forEach; val b0 = destino[f.id] ?: return@forEach; pos[f.id] = a0 + (b0 - a0) * q }
                if (u >= 1f) break
            }
            centra()
            fs.forEach { f -> destino[f.id]?.let { q -> e.edita(f.id) { put("ax", q.x.roundToInt()); put("ay", q.y.roundToInt()) } } }
        }
    }
    fun nuevaFicha() {
        val f = Nuevos.ficha(e, pid)
        val m = huecoLibre(aMundo(Offset(lienzo.width / 2f, lienzo.height / 2f)), ArbolEscribir.tam(f))
        e.edita(f.id) { put("ax", m.x.roundToInt()); put("ay", m.y.roundToInt()) }
        elegida = f.id
        abre.editar(f.id, esNuevo = true)
    }
    val vivo = remember { VivoArbol(pos, ::nodoEn, ::etqEn) }
    SideEffect { vivo.pos = pos; vivo.nodoEn = ::nodoEn; vivo.etqEn = ::etqEn }
    val pulsaAhora by rememberUpdatedState<(Doc) -> Unit> { f -> pulsa(f) }
    val dobleAhora by rememberUpdatedState<(Doc) -> Unit> { f -> abre.ver(f.id) }
    val etqAhora by rememberUpdatedState<(Doc) -> Unit> { r -> abre.ver(r.id) }
    val uneAhora by rememberUpdatedState<(String, String) -> Unit> { a, b -> une(a, b) }
    val menuAhora by rememberUpdatedState<(Doc, Offset) -> Unit> { f, p -> menu = f to p }
    fun vecinosDe(id: String?): Set<String>? = id?.let { x -> (rels.filter { it.s("a") == x || it.s("b") == x }.flatMap { listOf(it.s("a"), it.s("b")) } + x).toSet() }

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            EnlaceRevista("+ Nueva ficha", acento = true) { nuevaFicha() }
            if (fs.size >= 2) EnlaceRevista(if (uniendo) "Dejar de unir" else "Unir dos fichas", acento = uniendo) { uniendo = !uniendo; primera = null }
            if (fs.size >= 2) EnlaceRevista("Ordenar") { ordenar = true }
            Spacer(Modifier.weight(1f))
            if (fs.isNotEmpty()) {
                Tecla("−", "Alejar (−)", tam = 16.sp) { zoom(1 / 1.2f) }
                Text("${(v.k * 100).roundToInt().also { tick }} %", color = Muted, fontFamily = Body, fontSize = 11.sp)
                Tecla("+", "Acercar (+)", tam = 16.sp) { zoom(1.2f) }
                Tecla("Ver todo", "Que se vea todo el árbol (0)") { centra() }
            }
        }
        if (fs.isNotEmpty()) Text(
            when {
                uniendo && primera == null -> "Elige la primera ficha."
                uniendo -> "Ahora la ficha con la que unir a ${porId[primera]?.s("nombre").orEmpty()}."
                else -> "Arrastra las fichas para colocarlas y el fondo para moverte; la rueda acerca y aleja. Doble clic abre una ficha; con Mayús, arrastra de una a otra para unirlas. La palabra de una línea abre su unión."
            }, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 8.dp)
        )
        if (fs.isEmpty()) {
            Column(Modifier.padding(vertical = 30.dp)) {
                Text("El árbol de tu historia", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                Spacer(Modifier.height(10.dp))
                Text("Crea fichas (personajes, lugares, objetos…) y únelas: «enemigos», «amantes», «hogar», «pertenencia»… Con todo lo que quieras contar de cada unión.",
                    color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 27.sp)
            }
            return@Column
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 16.dp).clipToBounds().background(Crudo).border(1.dp, Line).onSizeChanged { lienzo = it }
            .focusRequester(fLienzo).focusable()
            .onPreviewKeyEvent { k ->
                if (k.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (k.key) {
                    Key.Plus, Key.Equals, Key.NumPadAdd -> { zoom(1.2f); true }
                    Key.Minus, Key.NumPadSubtract -> { zoom(1 / 1.2f); true }
                    Key.Zero, Key.NumPad0 -> { centra(); true }
                    Key.Escape -> { if (uniendo || elegida != null) { uniendo = false; primera = null; elegida = null; true } else false }
                    Key.Enter -> { elegida?.let { abre.ver(it) }; elegida != null }
                    else -> false
                }
            }
            .pointerHoverIcon(if (encima != null) PointerIcon.Hand else PointerIcon.Default)
            .onPointerEvent(PointerEventType.Move) { ev -> val p = ev.changes.first().position; val n = vivo.nodoEn(p)?.id; val t = if (n == null) vivo.etqEn(p)?.id else null; encima = n ?: t?.let { "etq:$it" } }
            .onPointerEvent(PointerEventType.Exit) { encima = null }
            .onPointerEvent(PointerEventType.Scroll) { ev ->
                val c = ev.changes.first(); val d = c.scrollDelta
                if (ArbolEscribir.ruedaAcerca(d.x, d.y, ev.keyboardModifiers.ctrlPuntero || ev.keyboardModifiers.metaPuntero))
                    zoom(if (ev.keyboardModifiers.ctrlPuntero || ev.keyboardModifiers.metaPuntero) exp(-d.y * 0.1f) else if (d.y < 0) 1.12f else 1 / 1.12f, c.position)
                else { v.x -= d.x * 24f; v.y -= d.y * 24f; tick++ }
                c.consume()
            }
            .pointerInput(uniendo) {
                var ultimoClic = 0L; var ultimoNodo: String? = null
                awaitEachGesture {
                    try {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        runCatching { fLienzo.requestFocus() }
                        val botones = currentEvent.buttons
                        if (botones.isSecondaryPressed) {
                            vivo.nodoEn(down.position)?.let { menuAhora(it, down.position) }
                            return@awaitEachGesture
                        }
                        if (!botones.isPrimaryPressed && currentEvent.changes.size <= 1 && botones.isSecondaryPressed) return@awaitEachGesture
                        val conMayus = currentEvent.keyboardModifiers.mayusPuntero
                        val etq = if (!uniendo) vivo.etqEn(down.position) else null
                        val nodo = if (etq == null) vivo.nodoEn(down.position) else null
                        var inicio = Offset.Zero; var arrastre = Offset.Zero; var sujeta = false; var movido = false; var recorrido = 0f
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                            if (!c.pressed) break
                            val d = c.positionChange()
                            recorrido += d.getDistance()
                            if (!movido && recorrido > 5f) movido = true
                            if (movido) {
                                if (nodo != null && conMayus) cuerda = nodo.id to aMundo(c.position)
                                else if (nodo != null) {
                                    if (!sujeta) { sujeta = true; cogida[0] = nodo.id; inicio = vivo.pos[nodo.id] ?: Offset.Zero; arrastre = Offset.Zero } else arrastre += d
                                    vivo.pos[nodo.id] = inicio + arrastre / (dens * v.k)
                                } else if (etq == null) { v.x += d.x; v.y += d.y; tick++ }
                            }
                            c.consume()
                        }
                        val fin = currentEvent.changes.first().position
                        when {
                            nodo != null && conMayus && movido -> { cuerda = null; vivo.nodoEn(fin)?.let { otra -> if (otra.id != nodo.id) uneAhora(nodo.id, otra.id) } }
                            !movido && etq != null -> etqAhora(etq)
                            !movido && nodo != null -> {
                                val ahora = System.currentTimeMillis()
                                if (ahora - ultimoClic < 450 && ultimoNodo == nodo.id && !uniendo) dobleAhora(nodo) else pulsaAhora(nodo)
                                ultimoClic = ahora; ultimoNodo = nodo.id
                            }
                            movido && nodo != null && !conMayus -> vivo.pos[nodo.id]?.let { p -> e.edita(nodo.id) { put("ax", p.x.roundToInt()); put("ay", p.y.roundToInt()) } }
                            !movido && nodo == null && etq == null -> { elegida = null; ultimoNodo = null }
                        }
                        cogida[0] = null
                    } catch (x: kotlinx.coroutines.CancellationException) { cogida[0] = null; cuerda = null; throw x } catch (x: Exception) { cogida[0] = null; cuerda = null }
                }
            }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                tick
                val k = dens * v.k
                fun P(p: Offset) = Offset(v.x + p.x * k, v.y + p.y * k)
                val esc = v.k.coerceAtLeast(0.6f)
                // La rejilla de puntos (como papel cuadriculado), que se mueve y crece con el lienzo
                val paso = 24f * k
                if (paso >= 8f) {
                    val puntos = ArrayList<Offset>()
                    var x = ((v.x % paso) + paso) % paso
                    while (x < size.width) { var y = ((v.y % paso) + paso) % paso; while (y < size.height) { puntos += Offset(x, y); y += paso }; x += paso }
                    drawPoints(puntos, PointMode.Points, tinta.copy(alpha = 0.16f), strokeWidth = 2.2f * dens * 0.5f, cap = StrokeCap.Round)
                }
                val sobreNodo = encima?.takeIf { !it.startsWith("etq:") }
                val resaltada = primera ?: sobreNodo ?: elegida
                val vecinos = vecinosDe(resaltada)
                fun apagado(id: String) = vecinos != null && id !in vecinos
                val ls = lineas()
                ls.forEach { l ->
                    val conColor = l.r.s("color").isNotBlank()
                    val tenue = vecinos != null && (apagado(l.r.s("a")) || apagado(l.r.s("b")))
                    val fuerte = vecinos != null && !tenue
                    val c = colorEscrito(l.r.s("color"), tinta).copy(alpha = when { tenue -> 0.12f; fuerte -> 1f; conColor -> 0.85f; else -> 0.62f })
                    val path = Path().apply { moveTo(P(l.a).x, P(l.a).y); quadraticTo(P(l.ctrl).x, P(l.ctrl).y, P(l.b).x, P(l.b).y) }
                    drawPath(path, c, style = Stroke((if (fuerte) 2.4f else 1.7f) * dens * esc, cap = StrokeCap.Round))
                    if (l.r.i("flecha") == 1) {
                        val ang = atan2(l.b.y - l.ctrl.y, l.b.x - l.ctrl.x)
                        val t = P(l.b); val s = 9f * dens * esc
                        drawPath(Path().apply {
                            moveTo(t.x, t.y); lineTo(t.x - s * cos(ang - 0.42f), t.y - s * sin(ang - 0.42f))
                            lineTo(t.x - s * 0.72f * cos(ang), t.y - s * 0.72f * sin(ang)); lineTo(t.x - s * cos(ang + 0.42f), t.y - s * sin(ang + 0.42f)); close()
                        }, c)
                    }
                }
                // La cuerda de unir con Mayús: de la ficha al puntero
                cuerda?.let { (id, m) -> pos[id]?.let { a -> drawLine(acento, P(a), P(m), 2f * dens, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * dens, 5f * dens)), cap = StrokeCap.Round) } }
                fs.forEach { f ->
                    val c = pos[f.id] ?: return@forEach
                    val t = ArbolEscribir.tam(f)
                    val (rx, ry, _) = ArbolEscribir.TAM.getValue(t)
                    val alfa = if (apagado(f.id)) 0.28f else 1f
                    val base = colorEscrito(f.s("color"))
                    val centro = P(c)
                    val tl = Offset(centro.x - rx * k, centro.y - ry * k); val sz = Size(rx * 2 * k, ry * 2 * k)
                    for ((ext, a0) in listOf(3.5f to 0.05f, 2f to 0.07f, 0.8f to 0.08f)) {
                        val e2 = ext * k
                        drawOval(Color.Black.copy(alpha = a0 * alfa), Offset(tl.x - e2, tl.y + 3f * k - e2 * 0.6f), Size(sz.width + 2 * e2, sz.height + 2 * e2 * 0.8f))
                    }
                    drawOval(lerp(base, papel, 0.84f).copy(alpha = alfa), tl, sz)
                    val on = f.id == resaltada || f.id == elegida
                    drawOval((if (on) acento else base).copy(alpha = alfa), tl, sz, style = Stroke((if (on) 4f else 2.2f) * dens * esc))
                    if (t == 3) {
                        val d = 6f * k
                        drawOval(base.copy(alpha = 0.45f * alfa), Offset(tl.x + d, tl.y + d), Size(sz.width - 2 * d, sz.height - 2 * d),
                            style = Stroke(1f * dens * esc, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * dens, 3f * dens))))
                    }
                    val (nombre, tipo) = textosNodo[f.id] ?: return@forEach
                    val alto = nombre.size.height + tipo.size.height
                    scale(v.k, v.k, pivot = centro) {
                        drawText(nombre, topLeft = Offset(centro.x - nombre.size.width / 2f, centro.y - alto / 2f), alpha = alfa)
                        drawText(tipo, topLeft = Offset(centro.x - tipo.size.width / 2f, centro.y - alto / 2f + nombre.size.height), alpha = alfa)
                    }
                }
                ls.forEach { l ->
                    val tenue = vecinos != null && (apagado(l.r.s("a")) || apagado(l.r.s("b")))
                    val alfa = if (tenue) 0.28f else 1f
                    val c = colorEscrito(l.r.s("color"), tinta)
                    val r = rectEtq(l)
                    val tl = P(Offset(r.left, r.top)); val sz = Size(r.width * k, r.height * k); val rad = CornerRadius(sz.height / 2)
                    val sobre = encima == "etq:${l.r.id}"
                    drawRoundRect((if (sobre) lerp(papel, c, 0.12f) else papel).copy(alpha = alfa), tl, sz, rad)
                    drawRoundRect(c.copy(alpha = (if (sobre) 1f else 0.55f) * alfa), tl, sz, rad, style = Stroke(1f * dens))
                    val res = etqs[l.r.id] ?: return@forEach
                    val centro = P(l.etq)
                    scale(v.k, v.k, pivot = centro) { drawText(res, topLeft = Offset(centro.x - res.size.width / 2f, centro.y - res.size.height / 2f), alpha = alfa) }
                }
            }
            // La ficha elegida: su tarjeta abajo a la izquierda
            elegida?.let { porId[it] }?.let { f ->
                val n = rels.count { it.s("a") == f.id || it.s("b") == f.id }
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp).widthIn(max = 360.dp).clip(esquina(6.dp)).background(Paper).border(1.dp, Line, esquina(6.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Punto(colorEscrito(f.s("color")))
                        Spacer(Modifier.width(8.dp))
                        Text(singularFicha(f.s("tipo")).uppercase() + " · " + (if (n == 0) "SIN UNIONES" else "$n ${if (n == 1) "UNIÓN" else "UNIONES"}"), color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                    }
                    Text(f.s("nombre").ifBlank { "Sin nombre" }, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                    if (f.s("resumen").isNotBlank()) Text(f.s("resumen"), color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AccionPeque("Abrir", acento = true) { abre.ver(f.id) }
                        AccionPeque("Editar") { abre.editar(f.id) }
                        if (fs.size >= 2) AccionPeque("Unir con…") { uniendo = true; primera = f.id }
                    }
                }
            }
            // El menú del botón derecho sobre una ficha
            menu?.let { (f, p) ->
                Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(p.x.roundToInt(), p.y.roundToInt()) }.size(1.dp)) {
                    DropdownMenu(true, { menu = null }, containerColor = Paper, offset = DpOffset(0.dp, 0.dp)) {
                        DropdownMenuItem({ Text("Abrir «${f.s("nombre").ifBlank { "Sin nombre" }}»", fontFamily = Body) }, { menu = null; abre.ver(f.id) })
                        DropdownMenuItem({ Text("Editar", fontFamily = Body) }, { menu = null; abre.editar(f.id) })
                        if (fs.size >= 2) DropdownMenuItem({ Text("Unir con otra ficha…", fontFamily = Body) }, { menu = null; uniendo = true; primera = f.id })
                        DropdownMenuItem({ Text("Centrar aquí", fontFamily = Body) }, { menu = null; elegida = f.id; centra(f.id) })
                        listOf(1 to "Pequeña", 2 to "Mediana", 3 to "Grande").forEach { (t, n) ->
                            DropdownMenuItem({ Text((if (ArbolEscribir.tam(f) == t) "✓ " else "    ") + n, fontFamily = Body) }, { menu = null; e.edita(f.id) { put("tam", t) } })
                        }
                    }
                }
            }
        }
    }
    if (ordenar) Dialogo("¿Ordenar el árbol?", { ordenar = false }, botones = {
        BotonSuave("Cancelar") { ordenar = false }
        BotonAcento("Ordenar", alto = 44.dp, modifier = Modifier.width(130.dp)) { ordenar = false; ordenarAhora() }
    }) { Cuerpo("Thot colocará todas las fichas solas (lo unido, cerca). Después puedes moverlas a mano.", tam = 14) }
}
