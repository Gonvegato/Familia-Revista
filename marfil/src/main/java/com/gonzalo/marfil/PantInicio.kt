package com.gonzalo.marfil

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
 * La portada de Seshat, la primera página (la cabecera y las pestañas son de [PantPrincipal]), en «estantes de portadas»: las portadas mandan.
 *  - «Leyendo · N →»: todo lo que lees, en portadas grandes que se deslizan, con su avance; y el botón «SEGUIR CON …».
 *  - «Pendientes · N →»: lo que toca, en portadas medianas (de cada saga, el siguiente que te queda).
 *  - «Tu año · X de Y →»: el reto con su barra y el mosaico de lo leído este año.
 *  - Debajo, en pequeño: hoy, la racha y la semana.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PaginaPortada(alAbrir: (Libro) -> Unit, alLeer: (Libro) -> Unit, alAnadir: () -> Unit, alIr: (Destino) -> Unit) {
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

        // ---- Leyendo: las portadas grandes de todo lo que lees, con su avance
        if (libros.isNotEmpty()) {
            TituloEstante("Leyendo", if (leyendo.isEmpty()) null else "${leyendo.size} →") { alIr(Destino.Leyendo) }
            if (leyendo.isEmpty()) VacioEstante("No tienes nada a medias. Empieza uno de tus pendientes y saldrá aquí.")
            else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                leyendo.forEach { l ->
                    Column(Modifier.width(96.dp).clickable { alAbrir(l) }) {
                        PortadaLibro(l, Modifier.width(96.dp).height(144.dp), radio = 0)
                        Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(4.dp).background(Line)) { Box(Modifier.fillMaxWidth(l.fraccion.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
                        Text("${(l.fraccion * 100).toInt().coerceIn(0, 100)} %", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.6.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (ahora != null) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.padding(horizontal = 20.dp)) {
                    if (ahora.enOtroDispositivo) BotonC("${ahora.titulo}: está en otro dispositivo", fondo = Charcoal, letra = Paper) { alAbrir(ahora) }
                    else BotonC("Seguir con ${ahora.titulo}") { alLeer(ahora) }
                }
            }
        }

        // ---- Pendientes: portadas medianas, en tu orden
        if (libros.isNotEmpty()) {
            TituloEstante("Pendientes", if (nPendientes == 0) null else "$nPendientes →") { alIr(Destino.Pendientes) }
            if (pendientes.isEmpty()) VacioEstante("No tienes nada pendiente. Lo que añadas sin empezar saldrá aquí.") { BotonC("Añadir un libro", Modifier.padding(top = 8.dp), onClick = alAnadir) }
            else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pendientes.forEach { (l, saga) ->
                    Column(Modifier.width(72.dp).clickable { alAbrir(l) }) {
                        PortadaLibro(l, Modifier.width(72.dp).height(108.dp), radio = 0)
                        if (saga != null) Text("+${saga.miembros.size - 1} DE LA SAGA", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 0.4.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        // ---- Tu año: el reto y el mosaico de lo leído
        if (libros.isNotEmpty()) {
            val titulo = if (metaAnual > 0) "Tu $ano · $terminadosAno de $metaAnual" else "Tu $ano · $terminadosAno ${if (terminadosAno == 1) "leído" else "leídos"}"
            TituloEstante(titulo, "Leídos →") { alIr(Destino.Leidos) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                if (metaAnual > 0) Box(Modifier.padding(top = 2.dp, bottom = 10.dp).fillMaxWidth().height(8.dp).background(Line)) {
                    Box(Modifier.fillMaxWidth((terminadosAno.toFloat() / metaAnual).coerceIn(0f, 1f)).fillMaxHeight().background(Acento))
                }
                if (leidosAno.isEmpty()) Text("Aún no has terminado ninguno este año. Cuando acabes uno, su portada saldrá aquí.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
                else MosaicoLeidos(leidosAno, alAbrir) { alIr(Destino.Leidos) }
            }
        }

        // ---- En pequeño: hoy, la racha y la semana
        if (libros.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Spacer(Modifier.height(22.dp))
                Filete(2.dp)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("HOY ", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text(if (lectura.hoyMin >= 60) "${lectura.hoyMin / 60} h ${lectura.hoyMin % 60} min" else "${lectura.hoyMin} min", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${lectura.racha} ", color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text(if (lectura.racha == 1) "DÍA SEGUIDO" else "DÍAS SEGUIDOS", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
                }
                if (lectura.semana.any { it.second >= 1 }) BarrasSemanaC(lectura)
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

/** El mosaico de lo leído este año: 6 por fila, como mucho 3 filas; si hay más, la última casilla dice «+N» y lleva a Leídos. */
@Composable
private fun MosaicoLeidos(libros: List<Libro>, alAbrir: (Libro) -> Unit, alVerTodos: () -> Unit) {
    val col = 6
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
