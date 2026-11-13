package com.gonzalo.marfil

import com.gonzalo.familia.pulsable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * La portada de Seshat, la primera página (la cabecera y las pestañas son de [PantPrincipal]): las portadas mandan.
 *  - El cartel: lo que lees ahora (el último que has abierto), a lo grande sobre su color, con su avance; debajo, «SEGUIR LEYENDO →».
 *  - «También lees · N →»: los demás que tienes a medias, en portadas grandes que se deslizan.
 *  - «Pendientes · N →»: lo que toca, en portadas medianas (de cada saga, el siguiente que te queda).
 *  - Debajo del botón: el reto del año (cómo vas) y la semana en barras que se tocan (lo leído cada día).
 *  - Con pocos libros, «También lees» y «Pendientes» lado a lado.
 *  - «Tu año · N leídos →»: el mosaico de lo leído este año.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PaginaPortada(alAbrir: (Libro) -> Unit, alLeer: (Libro) -> Unit, alAnadir: () -> Unit, alIr: (Destino) -> Unit, alAjustes: () -> Unit = {}) {
    val libros = Biblioteca.propios()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val leyendo = libros.filter { it.estado == Estado.LEYENDO }.sortedWith(compareByDescending<Libro> { it.ultima }.thenByDescending { it.anadido })
    val ahora = leyendo.firstOrNull()
    // Pendientes, en el orden que tengas elegido en su página; de cada saga, el siguiente que te queda
    val pendientes = remember(libros, EspejoCuenta.version) {
        Sagas.agrupar(ordenar(OrdenGuardado.de(ctx, Estado.PENDIENTE), libros.filter { it.estado == Estado.PENDIENTE })).map { f ->
            when (f) { is FilaLista.Suelto -> f.libro to null; is FilaLista.DeSaga -> f.miembros.first() to f }
        }
    }
    val nPendientes = libros.count { it.estado == Estado.PENDIENTE }
    val lectura = remember(libros.map { it.id to it.msPropios to it.ultima }) { datosLectura(ctx, libros) }
    val metaAnual = remember(libros) { Ajustes.metaAnual(ctx) }
    val ano = remember { anoDe(System.currentTimeMillis()) }
    val leidosAno = libros.filter { l -> l.terminados.any { anoDe(it) == ano } }.sortedByDescending { l -> l.terminados.filter { anoDe(it) == ano }.maxOrNull() ?: 0L }
    val terminadosAno = terminadosEn(libros, ano)
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        if (libros.isEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text("Tu biblioteca está vacía. Añade tu primer libro (EPUB, PDF, MOBI, AZW3, FB2, CBZ o TXT) o trae los de Atrio desde tu cuenta.", color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(12.dp))
            BotonC("Añadir un libro", onClick = alAnadir)
        }

        // ---- El cartel: lo que lees ahora, de protagonista; debajo, «Seguir leyendo» a todo el ancho
        if (libros.isNotEmpty()) {
            if (ahora != null) {
                Cartel(ahora, alAbrir)
                if (ahora.enOtroDispositivo) BotonC("${ahora.titulo}: está en otro dispositivo", fondo = Charcoal, letra = Paper) { alAbrir(ahora) }
                else BotonC("Seguir leyendo", fondo = Acento, letra = sobreAcento()) { alLeer(ahora) }
            } else CartelVacio(alAnadir)
        }

        // ---- El reto del año y la semana, justo debajo del botón
        if (libros.isNotEmpty()) {
            BloqueReto(metaAnual, terminadosAno, ano, alIr = { alIr(Destino.Leidos) }, alPoner = alAjustes)
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp), verticalAlignment = Alignment.Bottom) {
                Text("Esta semana", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text("${minutosCorto(lectura.semana.sumOf { it.second })} · HOY ${minutosCorto(lectura.hoyMin)}", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1)
            }
            SemanaTocable(lectura, Modifier.padding(horizontal = 20.dp))
            if (lectura.racha > 0) Text("${lectura.racha} ${if (lectura.racha == 1) "DÍA SEGUIDO" else "DÍAS SEGUIDOS"} LEYENDO", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black,
                fontSize = 10.5.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
        }

        val otros = leyendo.drop(1)
        // Con pocos libros (tres portadas entre los dos estantes, como mucho), «También lees» y «Pendientes» van lado a lado, con las
        // portadas tan grandes como quepan; si no, cada uno en su fila
        val juntos = libros.isNotEmpty() && otros.isNotEmpty() && pendientes.isNotEmpty() && otros.size + pendientes.size <= 3
        if (juntos) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                val n = otros.size + pendientes.size
                val ancho = ((maxWidth.value - 18f - 10f * (n - 2)) / n).coerceIn(72f, 120f).toInt()
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column {
                        EncabezadoCorto("También lees", "${otros.size} →") { alIr(Destino.Leyendo) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { otros.forEach { l -> TapaConAvance(l, ancho, alAbrir) } }
                    }
                    Column {
                        EncabezadoCorto("Pendientes", "$nPendientes →") { alIr(Destino.Pendientes) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { pendientes.forEach { (l, saga) -> TapaPendiente(l, saga, ancho, alAbrir) } }
                    }
                }
            }
        } else {
            // ---- También lees: los demás que tienes a medias, en portadas grandes con su avance
            if (otros.isNotEmpty()) {
                TituloEstante("También lees", "${otros.size} →") { alIr(Destino.Leyendo) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    otros.forEach { l -> TapaConAvance(l, 120, alAbrir) }
                }
            }

            // ---- Pendientes: portadas medianas, en tu orden
            if (libros.isNotEmpty()) {
                TituloEstante("Pendientes", if (nPendientes == 0) null else "$nPendientes →") { alIr(Destino.Pendientes) }
                if (pendientes.isEmpty()) VacioEstante("No tienes nada pendiente. Lo que añadas sin empezar saldrá aquí.") { BotonC("Añadir un libro", Modifier.padding(top = 8.dp), onClick = alAnadir) }
                else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pendientes.forEach { (l, saga) -> TapaPendiente(l, saga, 104, alAbrir) }
                }
            }
        }

        // ---- Tu año: el mosaico de lo leído (el reto, con su barra, ya va arriba)
        if (libros.isNotEmpty()) {
            TituloEstante("Tu $ano · $terminadosAno ${if (terminadosAno == 1) "leído" else "leídos"}", "Leídos →") { alIr(Destino.Leidos) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                if (leidosAno.isEmpty()) Text("Aún no has terminado ninguno este año. Cuando acabes uno, su portada saldrá aquí.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
                else MosaicoLeidos(leidosAno, alAbrir) { alIr(Destino.Leidos) }
            }
        }

        Text("Seshat · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 22.dp))
        // La versión instalada, debajo (para no tener que entrar en Ajustes a mirarla)
        Text("versión $VERSION", color = Muted.copy(alpha = 0.7f), fontFamily = Body, fontSize = 10.sp, letterSpacing = 0.6.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
        Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }
}

/** El título de un estante de la portada: el nombre en grotesca y, a la derecha y en el acento, «N →» (lleva a su página). */
@Composable
private fun TituloEstante(titulo: String, enlace: String?, alIr: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enlace != null, onClick = alIr).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 24.sp, maxLines = 2, modifier = Modifier.weight(1f))
        if (enlace != null) Text(enlace.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp, bottom = 3.dp))
    }
}

@Composable
private fun VacioEstante(texto: String, extra: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(texto, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
        extra()
    }
}

/** El mosaico de lo leído este año: 4 por fila, como mucho 3 filas; si hay más, la última casilla dice «+N» y lleva a Leídos. */
@Composable
private fun MosaicoLeidos(libros: List<Libro>, alAbrir: (Libro) -> Unit, alVerTodos: () -> Unit) {
    val col = 4
    val max = col * 3
    val sobran = (libros.size - max).coerceAtLeast(0)
    val vistos = if (sobran > 0) libros.take(max - 1) else libros
    val casillas: List<Libro?> = vistos + if (sobran > 0) listOf(null) else emptyList()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        casillas.chunked(col).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                fila.forEach { l ->
                    Box(Modifier.weight(1f).aspectRatio(0.67f)) {
                        if (l != null) PortadaLibro(l, Modifier.fillMaxSize().clickable { alAbrir(l) }, radio = 0, conTitulo = false)
                        else Box(Modifier.fillMaxSize().background(Charcoal).clickable(onClick = alVerTodos), contentAlignment = Alignment.Center) {
                            Text("+${sobran + 1}", color = Paper, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1)
                        }
                    }
                }
                // (huecos vacíos al final de la última fila: la rejilla no se descuadra)
                repeat(col - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Una barra fina de lo leído. */
@Composable
fun Barra(f: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Line)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
}

/** El color del texto sobre el acento (claro u oscuro, según lo claro que sea el acento). */
internal fun sobreAcento(): Color = if (Acento.red * 0.299f + Acento.green * 0.587f + Acento.blue * 0.114f > 0.55f) Color(0xFF050404) else Color.White

/** «5 h 20 min», «51 min». */
private fun tiempo(ms: Long): String { val m = (ms / 60_000L).toInt(); return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }

/**
 * El cartel de la portada: el libro que lees ahora, como un cartel. Su portada muy grande y centrada sobre su color (el medio de la imagen o el de
 * su tela), oscurecido abajo; encima, abajo, «AHORA LEES · N %», el título en grotesca, la barra gruesa y autor · páginas · tiempo. Si la portada
 * es de las generadas, sin el autor pintado (queda bajo el texto del cartel).
 */
@Composable
private fun Cartel(l: Libro, alAbrir: (Libro) -> Unit) {
    val base = remember(l.portada, l.titulo) { colorDePortada(l.portada) ?: telaDe(l.titulo) }
    val claro = lerpColor(base, Color.White, 0.12f)
    val oscuro = lerpColor(base, Color.Black, 0.72f)
    val acentoClaro = lerpColor(Acento, Color.White, 0.35f)
    // La portada (con su paspartú) va entera en el hueco de arriba y acaba antes del texto: el oscurecido solo queda detrás del texto, nunca
    // encima de la portada (antes el degradado de abajo se comía el pie del marco y parecía cortado). El hueco se ajusta al tamaño de letra.
    Box(Modifier.fillMaxWidth().height(ALTO_CARTEL).background(Brush.verticalGradient(listOf(claro, oscuro))).pulsable(hundido = 0.985f) { alAbrir(l) }) {
        val conFondo = l.fondo.isNotBlank()
        if (conFondo) {
            // Con fondo elegido: el fondo ocupa el cartel (recortado) y la portada va encima, grande, arriba a la izquierda
            ImagenEncuadrada(l.fondo, l.encFondo, Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))))
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp).background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Color(0xCC050404), 1f to Color(0xF2050404))))
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = if (conFondo) 20.dp else 16.dp, bottom = 14.dp, start = 20.dp, end = 20.dp),
            contentAlignment = if (conFondo) Alignment.TopStart else Alignment.TopCenter) {
            val tope = if (conFondo) 225.dp else 330.dp
            PortadaSobreFondo(l, Modifier.heightIn(max = tope).fillMaxHeight().aspectRatio(2f / 3f, matchHeightConstraintsFirst = true), sombra = 22.dp)
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
            Text("AHORA LEES · ${porcentaje(l.fraccion)}", color = acentoClaro, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp, maxLines = 1)
            Text(l.titulo.uppercase(), color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 33.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp))
            val fr = com.gonzalo.familia.llenado("cartel:${l.id}", l.fraccion.coerceIn(0f, 1f), 260)
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.22f))) { Box(Modifier.fillMaxWidth(fr).fillMaxHeight().background(Acento)) }
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.autor.uppercase(), color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val derecha = listOf(Paginas.texto(l), if (l.msLeidos > 0) tiempo(l.msLeidos) else "").filter { it.isNotBlank() }.joinToString(" · ")
                if (derecha.isNotBlank()) Text(derecha.uppercase(), color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
            }
        }
        }
    }
}

/** El cartel cuando no lees nada: un hueco claro y la manera de llenarlo. */
@Composable
private fun CartelVacio(alAnadir: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Charcoal).padding(horizontal = 20.dp, vertical = 28.dp)) {
        Text("AHORA LEES", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp)
        Text("Nada, de momento.", color = Paper, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 33.sp, modifier = Modifier.padding(top = 4.dp))
        Text("Empieza uno de tus pendientes o añade un libro, y su portada saldrá aquí en grande.", color = Paper.copy(alpha = 0.75f), fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        BotonC("Añadir un libro", fondo = Acento, letra = sobreAcento(), onClick = alAnadir)
    }
}

/** El reto del año: «Reto 2026 · 13 / 30», su barra y cómo vas. Sin reto, una invitación a ponértelo (en Ajustes). */
@Composable
private fun BloqueReto(meta: Int, hechos: Int, ano: Int, alIr: () -> Unit, alPoner: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = if (meta > 0) alIr else alPoner).padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Reto $ano", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (meta > 0) {
                Text("$hechos", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(" / $meta", color = Muted, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        if (meta > 0) {
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(9.dp).background(Line)) {
                Box(Modifier.fillMaxWidth(com.gonzalo.familia.llenado("reto:$ano", (hechos.toFloat() / meta).coerceIn(0f, 1f), 320)).fillMaxHeight().background(Acento))
            }
            Text(ritmoReto(meta, hechos).texto(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.sp, lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp))
        } else {
            Text("$hechos ${if (hechos == 1) "terminado" else "terminados"} este año. Ponte un reto y verás cómo vas.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic,
                fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
            Text("PONERTE UN RETO →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** El título pequeño de un estante cuando van dos lado a lado. */
@Composable
private fun EncabezadoCorto(titulo: String, enlace: String, alIr: () -> Unit) {
    Row(Modifier.clickable(onClick = alIr).padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
        Text("  $enlace", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/** Una tapa de «También lees», con su barra y su porcentaje. */
@Composable
private fun TapaConAvance(l: Libro, ancho: Int, alAbrir: (Libro) -> Unit) {
    Column(Modifier.width(ancho.dp).pulsable { alAbrir(l) }) {
        PortadaLibro(l, Modifier.width(ancho.dp).height((ancho * 1.5f).dp), radio = 0)
        Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(4.dp).background(Line)) { Box(Modifier.fillMaxWidth(l.fraccion.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
        Text("${(l.fraccion * 100).toInt().coerceIn(0, 100)} %", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.6.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Una tapa de «Pendientes» (de una saga, cuántos más quedan). */
@Composable
private fun TapaPendiente(l: Libro, saga: FilaLista.DeSaga?, ancho: Int, alAbrir: (Libro) -> Unit) {
    Column(Modifier.width(ancho.dp).pulsable { alAbrir(l) }) {
        PortadaLibro(l, Modifier.width(ancho.dp).height((ancho * 1.5f).dp), radio = 0)
        if (saga != null) Text("+${saga.miembros.size - 1} DE LA SAGA", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 0.4.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}
