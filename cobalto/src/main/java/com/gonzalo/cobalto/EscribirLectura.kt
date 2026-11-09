@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.cobalto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Lo que se abre al pulsar una ficha, una nota, un suceso de la línea o una unión del árbol: primero para leerlo, como una
 * página de revista (el nombre a lo grande, la frase en cursiva, la letra capitular, los datos en su recuadro), y al tocar
 * lo escrito, a editarlo. Al guardar (o cerrar) lo editado se vuelve a leerlo. Solo al crearlas se abre directamente el
 * formulario. Cada pantalla tiene su [AbreEscribir] y pinta [VentanasEscribir].
 */
class AbreEscribir {
    /** Lo que se está leyendo (su id). */
    var ver by mutableStateOf<String?>(null)
    /** Lo que se está editando (su id). */
    var editar by mutableStateOf<String?>(null)
    /** Lo editado venía de leerlo: al cerrar, se vuelve a leer. */
    var volver: String? = null
}

@Composable
fun VentanasEscribir(e: Escritos, pid: String, a: AbreEscribir, abrirEscena: (String) -> Unit, verArbol: ((String) -> Unit)? = null) {
    a.ver?.let { id ->
        val d = e.get(id)
        val cerrar = { a.ver = null }
        val editar = { a.ver = null; a.editar = id; a.volver = id }
        when (d?.t) {
            Escritos.T_FICHA -> VerFicha(e, pid, d, a, cerrar, editar, abrirEscena, verArbol)
            Escritos.T_NOTA -> VerNota(e, d, cerrar, editar)
            Escritos.T_HITO -> VerHito(e, d, cerrar, editar, abrirEscena)
            Escritos.T_REL -> VerRelacion(e, d, a, cerrar, editar)
            else -> LaunchedEffect(id) { a.ver = null }
        }
    }
    a.editar?.let { id ->
        val d = e.get(id)
        // (si venía de leerlo y sigue ahí —no se ha borrado—, se vuelve a leerlo)
        val cerrar = { a.editar = null; if (a.volver == id && e.get(id) != null) a.ver = id; a.volver = null }
        when (d?.t) {
            Escritos.T_FICHA -> FichaDialogo(e, pid, d, onCerrar = cerrar, abrirEscena = { abrirEscena(it.id) })
            Escritos.T_NOTA -> NotaDialogo(e, pid, d, onCerrar = cerrar)
            Escritos.T_HITO -> HitoDialogo(e, pid, d, onCerrar = cerrar)
            Escritos.T_REL -> RelacionDialogo(e, pid, d, onCerrar = cerrar)
            Escritos.T_RAMA -> RamaDialogo(e, pid, d, onCerrar = cerrar)
            Escritos.T_PARTE -> ParteDialogo(e, pid, d, onCerrar = cerrar)
            Escritos.T_CAP -> CapituloDialogo(e, pid, d, onCerrar = cerrar)
            else -> LaunchedEffect(id) { a.editar = null }
        }
    }
}

/** Un texto largo en párrafos, con **negrita** y *cursiva* como en el editor. */
@Composable
fun ParrafosEscribir(texto: String, tam: Int = 16) {
    texto.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { l ->
        when {
            l == "***" -> Text("* * *", color = Muted, fontSize = tam.sp, modifier = Modifier.fillMaxWidth().wrapContentWidth())
            Regex("^#{1,3} ").containsMatchIn(l) -> Text(l.replace(Regex("^#+ "), ""), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (tam + 4).sp, color = Charcoal)
            l.startsWith("> ") -> Text(conFormato(l.removePrefix("> ")), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = tam.sp, lineHeight = (tam * 1.5).sp, color = Muted, modifier = Modifier.padding(start = 14.dp))
            else -> Text(conFormato(l), fontFamily = Display, fontSize = tam.sp, lineHeight = (tam * 1.5).sp, color = Charcoal)
        }
    }
}

fun conFormato(t: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    for (m in Regex("\\*\\*[^*]+\\*\\*|\\*[^*]+\\*").findAll(t)) {
        append(t.substring(i, m.range.first))
        val v = m.value
        if (v.startsWith("**")) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(v.substring(2, v.length - 2)) }
        else withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(v.substring(1, v.length - 1)) }
        i = m.range.last + 1
    }
    append(t.substring(i))
}

/**
 * Un párrafo con letra capitular, como en las revistas: la primera letra, grande y en [color], ocupa tres líneas y el texto
 * la rodea; lo que no cabe a su lado sigue debajo, a todo lo ancho.
 */
@Composable
fun ParrafoCapitular(texto: AnnotatedString, color: Color, tam: Int = 17, lineas: Int = 3) {
    val alto = tam * 1.55f
    val estilo = TextStyle(fontFamily = Display, fontSize = tam.sp, lineHeight = alto.sp, color = Charcoal)
    if (texto.length < 40) { Text(texto, style = estilo); return }
    val letra = texto.text.first().toString()
    val resto = texto.subSequence(1, texto.length)
    val medidor = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val dens = androidx.compose.ui.platform.LocalDensity.current
        val tamLetra = (alto * lineas * 0.92f).sp
        val cap = remember(letra, tamLetra) { medidor.measure(letra, TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = tamLetra, lineHeight = tamLetra)) }
        val hueco = with(dens) { 8.dp.roundToPx() }
        val anchoTexto = (constraints.maxWidth - cap.size.width - hueco).coerceAtLeast(1)
        val medida = remember(resto, anchoTexto) { medidor.measure(resto, estilo, constraints = Constraints(maxWidth = anchoTexto)) }
        val corte = if (medida.lineCount <= lineas) resto.length else medida.getLineEnd(lineas - 1, visibleEnd = false)
        Column {
            Row {
                Text(letra, color = color, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = tamLetra, lineHeight = tamLetra,
                    modifier = Modifier.padding(end = 8.dp).height((alto * lineas).dp).offset(y = (-(tam * 0.18f)).dp))
                Text(resto.subSequence(0, corte), style = estilo)
            }
            if (corte < resto.length) Text(resto.subSequence(corte, resto.length).let { r -> if (r.text.startsWith(" ")) r.subSequence(1, r.length) else r }, style = estilo)
        }
    }
}

/** El texto largo de una ficha o una nota para leerlo: el primer párrafo con capitular, los demás como en el libro. */
@Composable
fun TextoLectura(texto: String, color: Color, tam: Int = 17) {
    val ps = texto.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    val primero = ps.indexOfFirst { it != "***" && !it.startsWith("#") && !it.startsWith("> ") }
    ps.forEachIndexed { i, l ->
        when {
            i == primero -> ParrafoCapitular(conFormato(l), color, tam)
            l == "***" -> Text("* * *", color = Muted, fontSize = tam.sp, modifier = Modifier.fillMaxWidth().wrapContentWidth().padding(vertical = 4.dp))
            Regex("^#{1,3} ").containsMatchIn(l) -> Text(l.replace(Regex("^#+ "), ""), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (tam + 5).sp, color = Charcoal, modifier = Modifier.padding(top = 6.dp))
            l.startsWith("> ") -> Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(color.copy(alpha = 0.6f)))
                Text(conFormato(l.removePrefix("> ")), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = tam.sp, lineHeight = (tam * 1.55f).sp, color = Muted, modifier = Modifier.padding(start = 14.dp))
            }
            else -> Text(conFormato(l), fontFamily = Display, fontSize = tam.sp, lineHeight = (tam * 1.55f).sp, color = Charcoal)
        }
    }
}

/**
 * La página de lectura (de revista) de una ficha, una nota, un suceso o una unión: volver y «Editar» arriba, la franja de su
 * color, el antetítulo, el nombre enorme y el filete; debajo, lo principal y, en una pantalla ancha, al lado una columna
 * con los datos. Tocar lo escrito lo abre para editarlo.
 */
@Composable
fun HojaLectura(
    color: Color, ante: String, titulo: String, onCerrar: () -> Unit, onEditar: () -> Unit,
    sub: String = "", lateral: (@Composable ColumnScope.() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Paper)) {
            val ancha = maxWidth >= 760.dp
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                // Dirección C: la barra de arriba, un bloque de tinta (por detrás de la barra de estado) con «← VOLVER» y «EDITAR»
                Box(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding()) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalSobreTinta provides true) {
                        Row(Modifier.fillMaxWidth().widthIn(max = 1080.dp).align(Alignment.Center).padding(horizontal = 22.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            EnlaceRevista("← Volver", Modifier.semantics { contentDescription = "Cerrar" }, onClick = onCerrar)
                            Spacer(Modifier.weight(1f))
                            EnlaceRevista("Editar", Modifier.semantics { contentDescription = "Editar" }, acento = true, onClick = onEditar)
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(8.dp).background(color))
                    Column(Modifier.fillMaxWidth().widthIn(max = 1080.dp).align(Alignment.CenterHorizontally).padding(horizontal = 22.dp).padding(top = 18.dp, bottom = 40.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(color)); Spacer(Modifier.width(8.dp))
                            Text(ante.uppercase(), color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        FitTitle(titulo.uppercase(), if (ancha) 56.sp else 40.sp, if (ancha) 58.sp else 42.sp, Modifier.padding(top = 6.dp), color = Charcoal, family = Grotesca, fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp, maxLines = 3, overflow = TextOverflow.Ellipsis, minSize = 20f, conEdicion = false)
                        if (sub.isNotBlank()) Text(sub.uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
                        Filete(3.dp, modifier = Modifier.padding(top = 12.dp, bottom = 18.dp))
                        if (ancha && lateral != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                            Column(Modifier.weight(1.65f), verticalArrangement = Arrangement.spacedBy(14.dp), content = contenido)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = lateral)
                        } else Column(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            contenido()
                            lateral?.invoke(this)
                        }
                        Text("Toca lo escrito para editarlo.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().wrapContentWidth().padding(top = 30.dp))
                    }
                }
            }
        }
    }
}

/** La entradilla: la frase de la ficha, grande y en cursiva. */
@Composable
private fun Entradilla(t: String, onEditar: () -> Unit) =
    Text(t, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 23.sp, lineHeight = 31.sp, color = Charcoal, modifier = Modifier.press(haptic = false, onClick = onEditar))

/** Un recuadro de la columna de los datos: filete de color arriba, su nombre en versalitas y lo de dentro. */
@Composable
private fun Recuadro(titulo: String, color: Color, onClick: (() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Crudo).then(if (onClick != null) Modifier.press(haptic = false, onClick = onClick) else Modifier)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(color))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
            contenido()
        }
    }
}

/** Los enlaces a las partes y capítulos a los que está vinculada. */
@Composable
private fun VinculadaA(e: Escritos, d: Doc) {
    val destinos = d.vin().mapNotNull { e.get(it) }
    if (destinos.isEmpty()) return
    Recuadro("Vinculada a", Charcoal) {
        destinos.forEach { x -> Text((if (x.t == Escritos.T_PARTE) "Parte · " else "Capítulo ${numeroCapitulo(e, x)} · ") + x.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontSize = 16.sp, color = Charcoal) }
    }
}

@Composable
fun VerFicha(e: Escritos, pid: String, f: Doc, a: AbreEscribir, onCerrar: () -> Unit, onEditar: () -> Unit, abrirEscena: (String) -> Unit, verArbol: ((String) -> Unit)?) {
    val ver = e.version
    val sale = remember(ver, f.id) { e.escenasDe(pid).filter { s -> e.fichasEn(pid, s.s("texto")).any { it.id == f.id } } }
    val rels = remember(ver, f.id) { e.relaciones(pid).filter { it.s("a") == f.id || it.s("b") == f.id }.mapNotNull { r -> e.get(if (r.s("a") == f.id) r.s("b") else r.s("a"))?.let { r to it } } }
    val datos = remember(ver, f.id) { f.o.optJSONArray("campos")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { it.optString("k") to it.optString("v") }.filter { it.first.isNotBlank() || it.second.isNotBlank() } }.orEmpty() }
    val color = colorEscrito(f.s("color"))
    val sub = listOfNotNull(
        f.s("alias").takeIf { it.isNotBlank() }?.let { "También «$it»" },
        if (sale.isEmpty()) "Aún no sale en ninguna escena" else "Sale en ${sale.size} ${if (sale.size == 1) "escena" else "escenas"}",
        if (rels.isNotEmpty()) "${rels.size} ${if (rels.size == 1) "unión" else "uniones"}" else null
    ).joinToString(" · ")
    HojaLectura(color, singularFicha(f.s("tipo")) + (e.get(pid)?.s("titulo")?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), f.s("nombre").ifBlank { "Sin nombre" }, onCerrar, onEditar, sub = sub,
        lateral = {
            Recuadro("Ficha", color, onClick = onEditar) {
                if (datos.isEmpty()) Text("Sin datos todavía. Toca para añadir la edad, el aspecto, lo que quiere…", color = Muted, fontSize = 14.sp, fontStyle = FontStyle.Italic)
                datos.forEachIndexed { i, (k, v) ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    Column {
                        Text(k.ifBlank { "—" }.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                        Text(v.ifBlank { "—" }, fontFamily = Display, color = Charcoal, fontSize = 17.sp, lineHeight = 22.sp)
                    }
                }
            }
            Recuadro(if (rels.isEmpty()) "Uniones" else "Uniones · ${rels.size}", Charcoal) {
                if (rels.isEmpty()) Text("Todavía no está unida a nada. Únela en la pestaña Árbol.", color = Muted, fontSize = 13.sp)
                rels.forEach { (r, otra) ->
                    val flecha = r.i("flecha") == 1
                    Column {
                        Text((if (flecha && r.s("b") == f.id) "← " else "") + r.s("titulo").ifBlank { "Unión" }.uppercase() + (if (flecha && r.s("a") == f.id) " →" else ""),
                            color = colorEscrito(r.s("color"), Acento), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.press(haptic = false) { a.ver = r.id }.padding(vertical = 2.dp))
                        Text(otra.s("nombre").ifBlank { "Sin nombre" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Charcoal, modifier = Modifier.press(haptic = false) { a.ver = otra.id })
                        if (r.s("texto").isNotBlank()) Text(r.s("texto").lineSequence().first().take(140), color = Muted, fontStyle = FontStyle.Italic, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (verArbol != null) EnlaceRevista("Ver en el árbol →", acento = true) { onCerrar(); verArbol(f.id) }
            }
            if (sale.isNotEmpty()) Recuadro("Sale en", Charcoal) {
                sale.take(30).forEach { s ->
                    val c = e.get(s.s("cid"))
                    Row(Modifier.fillMaxWidth().press(haptic = false) { onCerrar(); abrirEscena(s.id) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c?.let { "${numeroCapitulo(e, it)}" }.orEmpty(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                        Text(s.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontSize = 16.sp, color = Charcoal, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("→", color = Acento, fontSize = 14.sp)
                    }
                }
                if (sale.size > 30) Text("y ${sale.size - 30} más", color = Muted, fontSize = 12.sp)
            }
            VinculadaA(e, f)
        }) {
        if (f.s("resumen").isNotBlank()) Entradilla(f.s("resumen"), onEditar)
        if (f.s("texto").isNotBlank()) Column(Modifier.press(haptic = false, onClick = onEditar), verticalArrangement = Arrangement.spacedBy(12.dp)) { TextoLectura(f.s("texto"), color) }
        if (f.s("resumen").isBlank() && f.s("texto").isBlank())
            Text("Aún no has escrito nada de ${f.s("nombre").ifBlank { "esta ficha" }}. Toca aquí para contar quién es, de dónde viene, qué quiere…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 25.sp,
                modifier = Modifier.press(haptic = false, onClick = onEditar))
    }
}

@Composable
fun VerNota(e: Escritos, n: Doc, onCerrar: () -> Unit, onEditar: () -> Unit) {
    val tipo = singularNota(n.s("tipo"))
    val color = colorEscrito(n.s("color"))
    val palabras = remember(n.u) { Escritos.palabras(n.s("texto")) }
    val sub = listOfNotNull(if (n.b("fija")) "Fijada arriba" else null, if (palabras > 0) "${cifraE(palabras)} palabras" else null, "Editada ${haceE(n.u)}").joinToString(" · ")
    val lateral: (@Composable ColumnScope.() -> Unit)? = if (n.vin().any { e.get(it) != null }) { { VinculadaA(e, n) } } else null
    HojaLectura(color, tipo + (e.get(n.pid)?.s("titulo")?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), n.s("titulo").ifBlank { tipo }, onCerrar, onEditar, sub = sub, lateral = lateral) {
        if (n.s("texto").isNotBlank()) Column(Modifier.press(haptic = false, onClick = onEditar), verticalArrangement = Arrangement.spacedBy(12.dp)) { TextoLectura(n.s("texto"), color) }
        else Text("Sin texto todavía. Toca aquí para escribirlo.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.press(haptic = false, onClick = onEditar))
    }
}

@Composable
fun VerHito(e: Escritos, x: Doc, onCerrar: () -> Unit, onEditar: () -> Unit, abrirEscena: (String) -> Unit) {
    val r = e.get(x.s("rama"))
    val s = e.get(x.s("eid"))
    val color = colorEscrito(x.s("color").ifBlank { r?.s("color").orEmpty() }, Charcoal)
    val lateral: (@Composable ColumnScope.() -> Unit)? = if (s == null) null else { {
        Recuadro("Se cuenta en", color) {
            Row(Modifier.fillMaxWidth().press(haptic = false) { onCerrar(); abrirEscena(s.id) }, verticalAlignment = Alignment.CenterVertically) {
                Text(s.s("titulo").ifBlank { "la escena" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Charcoal, modifier = Modifier.weight(1f))
                Text("ESCRIBIR →", color = Acento, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            }
        }
    } }
    HojaLectura(color, if (r != null) "Rama · ${r.s("titulo")}" else "Línea principal", x.s("titulo").ifBlank { "Suceso" }, onCerrar, onEditar, sub = x.s("cuando"), lateral = lateral) {
        if (x.s("nota").isNotBlank()) Column(Modifier.press(haptic = false, onClick = onEditar), verticalArrangement = Arrangement.spacedBy(12.dp)) { TextoLectura(x.s("nota"), color) }
        else Text("Sin más detalles. Toca aquí para contar qué pasa.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.press(haptic = false, onClick = onEditar))
    }
}

@Composable
fun VerRelacion(e: Escritos, r: Doc, a: AbreEscribir, onCerrar: () -> Unit, onEditar: () -> Unit) {
    val fa = e.get(r.s("a")); val fb = e.get(r.s("b"))
    if (fa == null || fb == null) { LaunchedEffect(r.id) { onCerrar() }; return }
    val color = colorEscrito(r.s("color"), Charcoal)
    HojaLectura(color, "Unión", r.s("titulo").ifBlank { "Unión" }, onCerrar, onEditar) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FitTitle(fa.s("nombre").ifBlank { "Sin nombre" }, 24.sp, 27.sp, Modifier.press(haptic = false) { a.ver = fa.id }, color = Charcoal, conEdicion = false)
            Text(if (r.i("flecha") == 1) "→" else "—", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            FitTitle(fb.s("nombre").ifBlank { "Sin nombre" }, 24.sp, 27.sp, Modifier.press(haptic = false) { a.ver = fb.id }, color = Charcoal, conEdicion = false)
        }
        if (r.s("texto").isNotBlank()) Column(Modifier.press(haptic = false, onClick = onEditar), verticalArrangement = Arrangement.spacedBy(12.dp)) { TextoLectura(r.s("texto"), color) }
        else Text("Solo la palabra; no has escrito nada más de esta unión. Toca aquí para contarlo.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.press(haptic = false, onClick = onEditar))
    }
}
