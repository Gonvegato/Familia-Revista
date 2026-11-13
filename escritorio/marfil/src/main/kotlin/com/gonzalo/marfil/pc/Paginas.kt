package com.gonzalo.marfil.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.mov.entrada
import com.gonzalo.familia.pc.mov.filaDe
import com.gonzalo.familia.pc.revista.*
import java.time.LocalDate
import java.time.ZoneId

/** Una página con su barra de desplazamiento (la del Mac aparece al moverse) y su contenido centrado hasta [ancho]. */
@Composable
fun PaginaC(ancho: androidx.compose.ui.unit.Dp = 1440.dp, contenido: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = ancho).fillMaxWidth().padding(horizontal = 48.dp).padding(top = 30.dp, bottom = 48.dp), content = contenido)
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** La portada de un libro con su paspartú del papel (la de «Ahora lees» y la de la ficha). */
@Composable
fun TapaConPaspartu(l: Libro, ancho: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(modifier.shadow(18.dp).background(Paper).padding(ancho * 0.055f)) {
        PortadaCosa(l.portada, l.portadaUrl, l.titulo, Modifier.width(ancho), pie = l.autor)
    }
}

/** Los minutos leídos un día (de todos tus dispositivos). */
fun minutosDia(d: LocalDate): Long {
    val k = "d$d"
    return Libros.tiempo().vivos().sumOf { it.entero(k) } / 60_000L
}

/**
 * La portada a dos lados (en el móvil va en una columna): a la izquierda, lo que lees ahora en grande (con «Seguir leyendo») y lo demás que
 * lees; a la derecha, el reto del año, esta semana y lo último que subrayaste. Debajo, lo leído este año.
 */
@Composable
fun PaginaPortada(nav: Nav) {
    LaFamilia.cambios
    val libros = Libros.todos()
    val leyendo = libros.filter { it.estado == Estado.LEYENDO }.sortedByDescending { it.ultima }
    val ano = LocalDate.now().year
    val inicio = LocalDate.of(ano, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val delAno = libros.filter { l -> l.estado == Estado.LEIDO && l.terminados.any { it >= inicio } }
    val (minDia, objAno, deAno) = Libros.objetivo()
    val sigue = leyendo.firstOrNull()
    PaginaC {
        if (libros.isEmpty()) { Vacio("Tu biblioteca está vacía", "Añade un libro (EPUB, PDF o cómic) o ábrelo en Seshat del móvil: los de la cuenta salen aquí."); return@PaginaC }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
            Column(Modifier.weight(1.45f)) {
                if (sigue != null) {
                    // ---- Ahora lees: la portada con su paspartú sobre la tinta, el título enorme y por dónde vas
                    Row(Modifier.fillMaxWidth().entrada("portada:" + sigue.id).filaDe(sigue.id).levanta(alto = 2.dp, hundido = 0.99f) { nav.ir(Pantalla.Ficha(sigue.id, Pantalla.Portada)) }
                        .background(Brush.linearGradient(listOf(Color(0xFF3A2418), Color(0xFF0C0A09)))).padding(30.dp), verticalAlignment = Alignment.Bottom) {
                        TapaConPaspartu(sigue, 150.dp)
                        Column(Modifier.weight(1f).padding(start = 30.dp)) {
                            Text("Ahora lees · ${(sigue.fraccion * 100).toInt()} %".uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
                            Text(sigue.titulo.uppercase(), color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 44.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(6.dp).background(Color(0xFF3C3A37))) { Box(Modifier.fillMaxWidth(sigue.fraccion).fillMaxHeight().background(Acento)) }
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                Text(sigue.autor.uppercase(), color = Color.White.copy(alpha = 0.9f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                val pags = if (sigue.paginas > 0) "${(sigue.fraccion * sigue.paginas).toInt()}/${sigue.paginas} · " else ""
                                Text((pags + tiempoDe(sigue.minutos)).uppercase(), color = Color.White.copy(alpha = 0.9f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp, maxLines = 1)
                            }
                            Libros.posicion(sigue.id)?.etiqueta?.takeIf { it.isNotBlank() }?.let {
                                Text("Ibas por «$it»", color = Color.White.copy(alpha = 0.75f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    BotonC("Seguir leyendo") { nav.ir(Pantalla.Leer(sigue.id, Pantalla.Portada)) }
                } else Vacio("No estás leyendo nada", "Empieza uno de tus pendientes y saldrá aquí.")
                if (leyendo.size > 1) {
                    Spacer(Modifier.height(30.dp))
                    TituloC("También lees", derecha = "${leyendo.size - 1} →", alDerecha = { nav.ir(Pantalla.Lista(Estado.LEYENDO)) })
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        leyendo.drop(1).take(5).forEachIndexed { i, l -> TapaPequena(l, nav, i) }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                // ---- El reto del año
                if (objAno > 0 && (deAno == 0 || deAno == ano)) {
                    val hechos = delAno.size
                    val dia = LocalDate.now().dayOfYear; val dias = LocalDate.now().lengthOfYear()
                    val tocaria = (objAno.toLong() * dia / dias).toInt()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text("Reto $ano", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, modifier = Modifier.weight(1f))
                        Text("$hechos", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                        Text(" / $objAno", color = Muted, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(10.dp).background(Line)) { Box(Modifier.fillMaxWidth((hechos.toFloat() / objAno).coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
                    val quedan = (objAno - hechos).coerceAtLeast(0)
                    val diasQuedan = dias - dia
                    Text(listOfNotNull(if (hechos < tocaria) "Vas ${tocaria - hechos} por detrás" else if (hechos > tocaria) "Vas ${hechos - tocaria} por delante" else "Vas al día",
                        if (quedan > 0) "te quedan $quedan en $diasQuedan días" else "¡reto cumplido!").joinToString(" · ").uppercase(),
                        color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.3.sp, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(32.dp))
                }
                // ---- Esta semana: los minutos de cada día (hoy, en el acento)
                val hoy = LocalDate.now()
                val semana = (6 downTo 0).map { hoy.minusDays(it.toLong()) }.map { it to minutosDia(it) }
                val total = semana.sumOf { it.second }
                TituloC("Esta semana", derecha = "${tiempoDe(total)} · hoy ${tiempoDe(semana.last().second)}" + if (minDia > 0) " de $minDia" else "")
                val max = maxOf(semana.maxOf { it.second }, minDia.toLong(), 1L)
                Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    semana.forEachIndexed { i, (d, m) ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(0.82f * (m.toFloat() / max)).heightIn(min = 3.dp).background(if (i == 6) Acento else if (m > 0) Charcoal else Line))
                            Text(listOf("L", "M", "X", "J", "V", "S", "D")[d.dayOfWeek.value - 1], color = if (i == 6) Charcoal else Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                // ---- Lo último que subrayaste (en cualquier dispositivo)
                sigue?.let { s -> Marcas.de(s.id, Marcas.SUBRAYADOS).filter { !it.delTextoDeUnPdf }.maxByOrNull { it.creada }?.let { m ->
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).levanta(alto = 1.dp) { nav.ir(Pantalla.Leer(s.id, Pantalla.Portada, ir = m.valor)) }) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(Acento))
                        Column(Modifier.padding(start = 18.dp)) {
                            Text("Lo último que subrayaste".uppercase(), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp)
                            Text("«${m.texto}»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 21.sp, lineHeight = 28.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            if (m.nota.isNotBlank()) Text(m.nota, color = Acento, fontFamily = Display, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            Text(listOfNotNull(s.titulo, m.etiqueta.ifBlank { null }, if (m.creada > 0) fechaDe(m.creada) else null).joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                } }
            }
        }
        // ---- Lo leído este año
        if (delAno.isNotEmpty()) {
            Spacer(Modifier.height(40.dp))
            TituloC("Leídos en $ano", derecha = "${delAno.size} →", alDerecha = { nav.ir(Pantalla.Lista(Estado.LEIDO)) })
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                delAno.sortedByDescending { it.terminados.lastOrNull() ?: 0L }.take(8).forEachIndexed { i, l -> TapaPequena(l, nav, i) }
            }
        }
    }
}

/** Un libro pequeño de la portada: su portada, lo que llevas (o cuándo lo terminaste) y su título. */
@Composable
private fun TapaPequena(l: Libro, nav: Nav, orden: Int) {
    Column(Modifier.width(124.dp).entrada("tapa:" + l.id, orden).filaDe(l.id).levanta { nav.ir(Pantalla.Ficha(l.id, Pantalla.Portada)) }) {
        PortadaCosa(l.portada, l.portadaUrl, l.titulo, Modifier.fillMaxWidth(), pie = l.autor)
        if (l.estado != Estado.LEIDO) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).background(Line)) { Box(Modifier.fillMaxWidth(l.fraccion).fillMaxHeight().background(Acento)) }
        Text(l.titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text((if (l.estado == Estado.LEIDO) fechaDe(l.terminados.lastOrNull() ?: 0L) else "${(l.fraccion * 100).toInt()} %").uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

/** Cómo se ordena una página de libros. */
private enum class Orden(val nombre: String) { RECIENTE("Último añadido"), TITULO("Título A-Z"), AUTOR("Autor A-Z"), PAGINAS("Más páginas") }

/** Una página de libros (Leyendo, Pendientes, Leídos, Abandonados): sus cifras, cómo se ordenan y los libros a dos columnas. */
@Composable
fun PaginaLista(e: Estado, nav: Nav) {
    LaFamilia.cambios
    var orden by remember(e) { mutableStateOf(if (e == Estado.LEIDO) Orden.RECIENTE else Orden.RECIENTE) }
    val todos = Libros.todos().filter { it.estado == e }
    val l = when (orden) {
        Orden.RECIENTE -> if (e == Estado.LEIDO) todos.sortedByDescending { it.terminados.lastOrNull() ?: 0L } else todos
        Orden.TITULO -> todos.sortedBy { Libros.norm(it.titulo) }
        Orden.AUTOR -> todos.sortedBy { Libros.norm(it.autor) }
        Orden.PAGINAS -> todos.sortedByDescending { it.paginas }
    }
    val nombre = if (e == Estado.LEIDO) "Leídos" else if (e == Estado.LEYENDO) "Leyendo" else e.nombre + "s"
    PaginaC {
        Text(nombre.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp)
        BloquesCifras(listOf(BloqueCifra("${l.size}", if (l.size == 1) "libro" else "libros"), BloqueCifra(tiempoDe(l.sumOf { it.minutos }).replace(" min", "′"), "leídas"),
            BloqueCifra(l.sumOf { it.paginas }.let { if (it > 0) "%,d".format(it).replace(',', '.') else "—" }, "páginas")), Modifier.padding(top = 12.dp).widthIn(max = 780.dp))
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Orden.entries.forEach { o -> FiltroC(o.nombre, o == orden) { orden = o } }
        }
        Spacer(Modifier.height(20.dp)); Filete(2.dp)
        if (l.isEmpty()) Vacio("Aquí no hay ningún libro")
        l.chunked(2).forEachIndexed { fila, par ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                par.forEachIndexed { j, x -> FilaLibro(x, fila * 2 + j + 1, nav, Modifier.weight(1f)) }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Un libro en una página: su portada, su número, título, autor y lo que llevas (o sus páginas, o cuándo lo terminaste). */
@Composable
private fun FilaLibro(l: Libro, n: Int, nav: Nav, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().weight(1f).entrada("fila:" + l.id, (n - 1).coerceAtMost(9)).filaDe(l.id).levanta { nav.ir(Pantalla.Ficha(l.id, Pantalla.Lista(l.estado))) }.padding(vertical = 16.dp)) {
            PortadaCosa(l.portada, l.portadaUrl, l.titulo, Modifier.width(84.dp), pie = l.autor)
            Column(Modifier.weight(1f).padding(start = 20.dp)) {
                Text("%02d".format(n), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text(l.titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 25.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Text(l.autor.uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.5.sp, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.weight(1f).heightIn(min = 10.dp))
                val pie = when (l.estado) {
                    Estado.LEIDO -> "Terminado el " + fechaDe(l.terminados.lastOrNull() ?: 0L)
                    Estado.PENDIENTE -> if (l.paginas > 0) "%,d págs".format(l.paginas).replace(',', '.') else "Por empezar"
                    else -> "${(l.fraccion * 100).toInt()} % · " + tiempoDe(l.minutos)
                }
                Text(pie.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1)
                if (l.estado == Estado.LEYENDO) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).background(Line)) { Box(Modifier.fillMaxWidth(l.fraccion).fillMaxHeight().background(Acento)) }
            }
        }
        Filete(2.dp)
    }
}

