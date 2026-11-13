@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.cobalto.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.Formato
import com.gonzalo.familia.pc.revista.*

/*
 * El modo lectura de las fichas, las notas, los sucesos de la línea y las uniones del árbol: una página de revista (el nombre en
 * grande, el tipo en versalitas, la franja de su color, la entradilla, los datos, el texto en párrafos con el Markdown ligero y
 * lo relacionado). Un clic en la página (o «Editar») la abre para cambiarla. Como el modo lectura de Atrio y del móvil.
 */

/** Un trozo de un texto para leer: títulos, citas, saltos de escena, puntos de una lista y párrafos. */
sealed class Bloque {
    data class Titulo(val nivel: Int, val texto: String) : Bloque()
    data class Cita(val texto: String) : Bloque()
    data class Punto(val texto: String) : Bloque()
    data class Parrafo(val texto: String) : Bloque()
    data object Salto : Bloque()
}

/** El texto en bloques (cada línea con algo es un bloque; las vacías solo separan). */
fun bloquesDe(texto: String): List<Bloque> = texto.replace("\r\n", "\n").split('\n').map { it.trim() }.filter { it.isNotEmpty() }.map { l ->
    when {
        l == "***" || l == "* * *" -> Bloque.Salto
        l.startsWith("### ") -> Bloque.Titulo(3, l.removePrefix("### ").trim())
        l.startsWith("## ") -> Bloque.Titulo(2, l.removePrefix("## ").trim())
        l.startsWith("# ") -> Bloque.Titulo(1, l.removePrefix("# ").trim())
        l.startsWith("> ") -> Bloque.Cita(l.removePrefix("> ").trim())
        l.startsWith("- ") || l.startsWith("• ") -> Bloque.Punto(l.drop(2).trim())
        else -> Bloque.Parrafo(l)
    }
}

/** **negrita** y *cursiva* con su formato (los asteriscos no se ven). */
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
 * Un texto largo para leer: los párrafos en la letra de los títulos, aireados; los títulos, las citas (en cursiva, con su raya
 * a la izquierda), los saltos de escena y las listas. [libro]: como en un libro (justificado y con sangría desde el segundo).
 */
@Composable
fun TextoLectura(texto: String, tam: Int = 18, libro: Boolean = false, color: Color = Charcoal, modifier: Modifier = Modifier) {
    val bs = remember(texto) { bloquesDe(texto) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (libro) 2.dp else 10.dp)) {
        var anterior: Bloque? = null
        bs.forEach { b ->
            when (b) {
                Bloque.Salto -> Text("* * *", Modifier.fillMaxWidth().padding(vertical = 10.dp), color = Muted, fontFamily = Display, fontSize = tam.sp, textAlign = TextAlign.Center, letterSpacing = 6.sp)
                is Bloque.Titulo -> Text(conFormato(b.texto), Modifier.padding(top = if (anterior == null) 0.dp else 12.dp), color = color, fontFamily = Display, fontWeight = FontWeight.Bold,
                    fontStyle = if (b.nivel == 3) FontStyle.Italic else FontStyle.Normal, fontSize = (tam * when (b.nivel) { 1 -> 1.6f; 2 -> 1.3f; else -> 1.12f }).sp, lineHeight = (tam * 1.6f * 1.15f).sp)
                is Bloque.Cita -> Row(Modifier.padding(vertical = 4.dp).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Acento.copy(alpha = 0.55f)))
                    Spacer(Modifier.width(14.dp))
                    Text(conFormato(b.texto), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = tam.sp, lineHeight = (tam * 1.55f).sp)
                }
                is Bloque.Punto -> Row {
                    Text("•", Modifier.width(18.dp), color = Acento, fontFamily = Display, fontSize = tam.sp, lineHeight = (tam * 1.55f).sp)
                    Text(conFormato(b.texto), color = color, fontFamily = Display, fontSize = tam.sp, lineHeight = (tam * 1.55f).sp)
                }
                is Bloque.Parrafo -> {
                    val sangria = libro && anterior is Bloque.Parrafo
                    Text(conFormato(b.texto), color = color, fontFamily = Display, fontSize = tam.sp, lineHeight = (tam * if (libro) 1.62f else 1.55f).sp,
                        style = androidx.compose.ui.text.TextStyle(textIndent = if (sangria) TextIndent(firstLine = 1.6.em) else TextIndent.None, textAlign = if (libro) TextAlign.Justify else TextAlign.Start))
                }
            }
            anterior = b
        }
    }
}

/** Las acciones de una página de lectura: editarla y abrir lo relacionado. */
class AccionesLectura(
    val editar: () -> Unit,
    val verDoc: (String) -> Unit = {},
    val abrirEscena: (String) -> Unit = {},
    val verArbol: ((String) -> Unit)? = null,
)

/** El antetítulo de una página de lectura (en acento, versalitas espaciadas). */
@Composable
fun AnteLectura(t: String) = Text(t.uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

/** La franja del color del escrito, bajo el titular. */
@Composable
fun FranjaLectura(c: Color) = Box(Modifier.padding(top = 6.dp).width(72.dp).height(6.dp).background(c))

/** Un dato de una ficha: la etiqueta en versalitas y el valor (con puntos guía si es corto, como el índice de una revista). */
@Composable
fun DatoLectura(k: String, v: String) {
    if (v.length <= 26 && k.length <= 22 && !v.contains('\n')) { MagFact(k.ifBlank { "—" }, v.ifBlank { "—" }); return }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(k.ifBlank { "—" }.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Text(v.ifBlank { "—" }, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Un enlace en la letra de los títulos («Marta →»). */
@Composable
fun EnlaceSerif(t: String, modifier: Modifier = Modifier, tam: Int = 18, color: Color = Charcoal, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val encima by hover.collectIsHoveredAsState()
    Text(t, modifier.hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(vertical = 3.dp), color = if (encima) Acento else color,
        fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = tam.sp, lineHeight = (tam * 1.2f).sp)
}

/** Una sección de la columna de al lado (Datos, Uniones…): su antetítulo con el filete encima. */
@Composable
fun SeccionLectura(t: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Filete(2.dp)
        Spacer(Modifier.height(8.dp))
        Antetitulo(t, tam = 11.sp)
        Spacer(Modifier.height(6.dp))
        contenido()
    }
}

/**
 * La página: un clic en cualquier sitio que no sea un enlace la abre para editar (con la mano al pasar por encima y, abajo, el
 * aviso). Lo de dentro se compone en dos columnas si hay sitio (el texto y, al lado, lo demás).
 */
@Composable
private fun PaginaLectura(acc: AccionesLectura, cabecera: @Composable ColumnScope.() -> Unit, principal: @Composable ColumnScope.() -> Unit, lado: (@Composable ColumnScope.() -> Unit)?) {
    val hover = remember { MutableInteractionSource() }
    val encima by hover.collectIsHoveredAsState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ancha = maxWidth >= 700.dp && lado != null
        Column(Modifier.fillMaxWidth().hoverable(hover).pointerHoverIcon(PointerIcon.Text).clickable(interactionSource = hover, indication = null) { acc.editar() }) {
            cabecera()
            Spacer(Modifier.height(22.dp))
            if (ancha) Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1.7f), content = principal)
                Spacer(Modifier.width(40.dp))
                Column(Modifier.weight(1f), content = lado!!)
            } else {
                Column(Modifier.fillMaxWidth(), content = principal)
                if (lado != null) { Spacer(Modifier.height(22.dp)); Column(Modifier.fillMaxWidth(), content = lado) }
            }
            Spacer(Modifier.height(26.dp))
            Text("Un clic en la página para editarla", color = if (encima) Muted else Muted.copy(alpha = 0.45f), fontFamily = Body, fontSize = 11.sp, letterSpacing = 0.6.sp)
        }
    }
}

/** El titular de una página de lectura: el antetítulo, el nombre enorme, «Editar» a la derecha y la franja de color. */
@Composable
private fun CabeceraLectura(ante: String, titulo: String, color: Color, acc: AccionesLectura, extra: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            AnteLectura(ante)
            val (fs, lh) = when { titulo.length <= 14 -> 66.sp to 68.sp; titulo.length <= 28 -> 52.sp to 56.sp; else -> 40.sp to 44.sp }
            FitTitle(titulo, fs, lh, letterSpacing = (-1.2).sp, color = Charcoal, family = Grotesca)
        }
        Spacer(Modifier.width(16.dp))
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            extra()
            EnlaceRevista("Editar", acento = true, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) { acc.editar() }
        }
    }
    FranjaLectura(color)
}

/** Las partes y capítulos a los que está vinculada. */
@Composable
private fun VinculadaA(e: Escritos, d: Doc) {
    val destinos = d.vin().mapNotNull { e.get(it) }
    if (destinos.isEmpty()) return
    SeccionLectura("Vinculada a") {
        destinos.forEach { x ->
            val n = if (x.t == Escritos.T_PARTE) "Parte · " + x.s("titulo").ifBlank { "Sin título" } else "Cap. ${e.capitulos(x.pid).indexOfFirst { it.id == x.id } + 1} · " + x.s("titulo").ifBlank { "Sin título" }
            Text(n, color = Charcoal, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

/** La página de una ficha, para leerla. */
@Composable
fun LecturaFicha(e: Escritos, f: Doc, acc: AccionesLectura) {
    val ver = e.version
    val pid = f.pid
    val sale = remember(ver, f.id) { e.escenasDe(pid).filter { s -> e.fichasEn(pid, s.s("texto")).any { it.id == f.id } } }
    val rels = remember(ver, f.id) { e.relaciones(pid).filter { it.s("a") == f.id || it.s("b") == f.id }.mapNotNull { r -> e.get(if (r.s("a") == f.id) r.s("b") else r.s("a"))?.let { r to it } } }
    val datos = remember(ver, f.id) { camposDe(f) }
    val color = colorEscrito(f.s("color"))
    val ante = singularFicha(f.s("tipo")) + (if (f.s("alias").isNotBlank()) " · también «${f.s("alias").split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("», «")}»" else "")
    val lado: @Composable ColumnScope.() -> Unit = {
        if (datos.isNotEmpty()) SeccionLectura("Datos") { datos.forEach { (k, v) -> DatoLectura(k, v) } }
        SeccionLectura(if (rels.isEmpty()) "Uniones" else "Uniones · ${rels.size}") {
            if (rels.isEmpty()) Text("Todavía no está unida a nada. Únela en el árbol.", color = Muted, fontFamily = Body, fontSize = 13.sp)
            rels.forEach { (r, otra) ->
                val flecha = r.i("flecha") == 1
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text((if (flecha && r.s("b") == f.id) "← " else "") + r.s("titulo").ifBlank { "Unión" }.uppercase() + (if (flecha && r.s("a") == f.id) " →" else ""),
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { acc.verDoc(r.id) }, color = colorEscrito(r.s("color"), Charcoal),
                        fontFamily = Body, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                    EnlaceSerif(otra.s("nombre").ifBlank { "Sin nombre" }, tam = 19) { acc.verDoc(otra.id) }
                    if (r.s("texto").isNotBlank()) Text(r.s("texto").lineSequence().first().take(140), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (sale.isNotEmpty()) SeccionLectura("Sale en ${sale.size} ${if (sale.size == 1) "escena" else "escenas"}") {
            sale.take(40).forEach { s ->
                val cap = e.get(s.s("cid"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EnlaceSerif(s.s("titulo").ifBlank { "Escena" }, tam = 16, modifier = Modifier.weight(1f, fill = false)) { acc.abrirEscena(s.id) }
                    if (cap != null) Text("  " + cap.s("titulo"), color = Muted, fontFamily = Body, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        VinculadaA(e, f)
    }
    PaginaLectura(acc, cabecera = {
        CabeceraLectura(ante, f.s("nombre").ifBlank { "Sin nombre" }, color, acc) {
            if (acc.verArbol != null) EnlaceRevista("Ver en el árbol", modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) { acc.verArbol?.invoke(f.id) }
        }
        if (f.s("resumen").isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(f.s("resumen"), color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 23.sp, lineHeight = 31.sp)
        }
    }, principal = {
        if (f.s("texto").isNotBlank()) TextoLectura(f.s("texto"))
        else Text("Aún no has escrito nada de ${f.s("nombre").ifBlank { "esta ficha" }}: su historia, cómo es, qué quiere…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
    }, lado = lado)
}

/** La página de una nota, para leerla. */
@Composable
fun LecturaNota(e: Escritos, n: Doc, acc: AccionesLectura) {
    val tipo = singularNota(n.s("tipo"))
    PaginaLectura(acc, cabecera = {
        CabeceraLectura(tipo + (if (n.b("fija")) " · fijada" else "") + " · " + hace(n.u), n.s("titulo").ifBlank { tipo }, colorEscrito(n.s("color")), acc)
    }, principal = {
        if (n.s("texto").isNotBlank()) TextoLectura(n.s("texto")) else Text("Sin texto.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
    }, lado = if (n.vin().any { e.get(it) != null }) ({ VinculadaA(e, n) }) else null)
}

/** La página de un suceso de la línea temporal. */
@Composable
fun LecturaHito(e: Escritos, x: Doc, acc: AccionesLectura) {
    val r = e.get(x.s("rama"))
    val s = e.get(x.s("eid"))
    PaginaLectura(acc, cabecera = {
        CabeceraLectura(listOfNotNull(if (r != null) "Rama · ${r.s("titulo")}" else "Línea principal", x.s("cuando").ifBlank { null }).joinToString(" · "),
            x.s("titulo").ifBlank { "Suceso" }, colorEscrito(x.s("color").ifBlank { r?.s("color").orEmpty() }, Charcoal), acc)
    }, principal = {
        if (x.s("nota").isNotBlank()) TextoLectura(x.s("nota")) else Text("Sin más detalles.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
    }, lado = if (s != null) ({
        SeccionLectura("Se cuenta en") { EnlaceSerif(s.s("titulo").ifBlank { "la escena" } + " →") { acc.abrirEscena(s.id) } }
    }) else null)
}

/** La página de una unión del árbol. */
@Composable
fun LecturaRelacion(e: Escritos, r: Doc, acc: AccionesLectura) {
    val fa = e.get(r.s("a")); val fb = e.get(r.s("b"))
    PaginaLectura(acc, cabecera = {
        CabeceraLectura("Unión", r.s("titulo").ifBlank { "Unión" }.uppercase(), colorEscrito(r.s("color"), Charcoal), acc)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EnlaceSerif(fa?.s("nombre")?.ifBlank { "Sin nombre" } ?: "…", tam = 26) { fa?.let { acc.verDoc(it.id) } }
            Text(if (r.i("flecha") == 1) "→" else "—", color = Muted, fontFamily = Display, fontSize = 24.sp)
            EnlaceSerif(fb?.s("nombre")?.ifBlank { "Sin nombre" } ?: "…", tam = 26) { fb?.let { acc.verDoc(it.id) } }
        }
    }, principal = {
        if (r.s("texto").isNotBlank()) TextoLectura(r.s("texto")) else Text("Solo la palabra; no has escrito nada más de esta unión.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
    }, lado = null)
}

/** «12.345 palabras». */
fun palabrasTexto(n: Int) = Formato.cifra(n) + if (n == 1) " palabra" else " palabras"
