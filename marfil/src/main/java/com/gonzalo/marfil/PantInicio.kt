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
 * La portada de Marfil. La cabecera y el menú, con las mismas medidas que los de Atrio (y que los de Pórtico); lo de debajo, propio:
 * «Ahora» (el libro que estás leyendo, enmarcado, con lo que llevas y un botón para seguir) y «En la mesilla» (los pendientes, con la portada a la izquierda).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PantInicio(alAbrir: (Libro) -> Unit, alLeer: (Libro) -> Unit, alAnadir: () -> Unit, alIr: (Destino) -> Unit, alBuscar: () -> Unit, alAbrirSaga: (Saga) -> Unit = {}) {
    val hoy = remember { Calendar.getInstance() }
    val libros = Biblioteca.propios()
    val ahora = libros.filter { it.estado == Estado.LEYENDO }.maxByOrNull { it.ultima } ?: libros.firstOrNull { it.estado == Estado.LEYENDO }
    // En la mesilla: lo que estás leyendo, menos lo último que abriste (eso es «Ahora»). En la estantería: lo pendiente.
    val mesilla = libros.filter { it.estado == Estado.LEYENDO && it.id != ahora?.id }.sortedByDescending { it.ultima }.take(6)
    val estanteria = libros.filter { it.estado == Estado.PENDIENTE }.sortedByDescending { it.anadido }.take(6)
    val secciones = listOf<Pair<String, () -> Unit>>(
        "Leyendo" to { alIr(Destino.Leyendo) }, "Pendientes" to { alIr(Destino.Pendientes) }, "Leídos" to { alIr(Destino.Leidos) },
        "Abandonados" to { alIr(Destino.Abandonados) }, "Autores" to { alIr(Destino.Autores) }, "Sagas" to { alIr(Destino.Sagas) }, "Escritos" to { alIr(Destino.Escritos) }
    )

    // La cabecera y el menú, exactamente como en la portada de Atrio (RevistaHome): mismos márgenes, mismo tamaño de cabecera, de filetes y de letra
    val desplazado = rememberScrollState()
    val umbral = with(androidx.compose.ui.platform.LocalDensity.current) { 300.dp.toPx() }
    // (solo cambia al cruzar el umbral: leer el desplazamiento aquí recomponía la portada entera en cada píxel que bajabas)
    val plegada by remember(umbral) { derivedStateOf { desplazado.value > umbral } }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lectura = remember(libros.map { it.id to it.msPropios to it.ultima }) { datosLectura(ctx, libros) }
    val metaAnual = remember(libros) { Ajustes.metaAnual(ctx) }
    val ano = remember { anoDe(System.currentTimeMillis()) }
    val terminadosAno = terminadosEn(libros, ano)
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(desplazado)) {
        // ---- La cabecera de la dirección C: un bloque de tinta con la fila pequeña (Buscar · + Añadir · fecha · ⚙) y «SESHAT.» en grotesca enorme
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                EnlaceRevista("Buscar", color = Paper.copy(alpha = 0.75f)) { alBuscar() }
                EnlaceRevista("+ Añadir", Modifier.padding(start = 14.dp), color = Acento) { alAnadir() }
                Spacer(Modifier.weight(1f))
                Text(SimpleDateFormat("dd.MM.yy", Locale("es", "ES")).format(hoy.time), color = Paper.copy(alpha = 0.75f), fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Icon(Icons.Filled.Settings, "Ajustes", tint = Paper.copy(alpha = 0.75f), modifier = Modifier.padding(start = 6.dp).combinedClickable(onClick = { alIr(Destino.Ajustes) }).padding(8.dp).size(20.dp))
            }
            val ancho = minOf(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp, 1080)
            NombreConPunto("Seshat", if (ancho >= 700) 120.sp else 72.sp, Paper, Modifier.padding(top = 4.dp))
        }
        // ---- Las secciones, en una fila de bloques que se desliza
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            secciones.forEachIndexed { i, (t, f) ->
                Text(t.uppercase(), color = if (i == 0) SobreAcento else Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1,
                    modifier = Modifier.clickable(onClick = f).background(if (i == 0) Acento else Color.Transparent).padding(horizontal = 12.dp, vertical = 12.dp))
            }
        }
        Filete(2.dp)

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            if (libros.isEmpty()) {
                Text("Tu biblioteca está vacía. Añade tu primer libro (EPUB, PDF, MOBI, AZW3, FB2, CBZ o TXT) o trae los de Atrio desde tu cuenta.", color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(6.dp))
                MagLink("Añadir un libro", "Del móvil: se copia a la app y se lee su portada", accent = true) { alAnadir() }
            }

            // ---- Ahora: la portada, el porcentaje gigante y el botón negro
            if (ahora != null) {
                Row(Modifier.fillMaxWidth().clickable { alAbrir(ahora) }) {
                    PortadaLibro(ahora, Modifier.width(120.dp).aspectRatio(0.67f), radio = 0)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("${(ahora.fraccion * 100).toInt().coerceIn(0, 100)}", color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 104.sp, lineHeight = 92.sp, letterSpacing = (-5).sp, maxLines = 1, softWrap = false)
                        Text(listOfNotNull("%", Paginas.texto(ahora).takeIf { it.isNotBlank() }?.let { "$it PÁGS" }).joinToString(" · ").uppercase(), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 4.dp))
                        Text(ahora.titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 24.sp, letterSpacing = (-0.4).sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                        val sub = listOfNotNull(ahora.autor.ifBlank { null }, if (ahora.msLeidos >= 60_000) tiempoDe(ahora.msLeidos) else null).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, color = Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (ahora.enOtroDispositivo) BotonC("Está en otro dispositivo", fondo = Charcoal, letra = Paper, enabled = true) { alAbrir(ahora) }
                else BotonC("Seguir leyendo") { alLeer(ahora) }
            }

            // ---- La rejilla de cifras: hoy (con la racha), el reto y los días seguidos
            if (libros.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                val bloques = buildList {
                    add(BloqueCifra(if (lectura.hoyMin >= 60) "${lectura.hoyMin / 60}H${"%02d".format(lectura.hoyMin % 60)}" else "${lectura.hoyMin}′", if (lectura.meta > 0) "hoy · objetivo ${lectura.meta}′" else "hoy", Acento, SobreAcento))
                    if (metaAnual > 0) add(BloqueCifra("$terminadosAno/$metaAnual", "reto $ano", Paper, Charcoal))
                    add(BloqueCifra("${lectura.racha}", if (lectura.racha == 1) "día seguido" else "días seguidos", Bloque2, Color.White))
                }
                RejillaCifras(bloques)
                if (lectura.semana.any { it.second >= 1 }) { Spacer(Modifier.height(18.dp)); BarrasSemanaC(lectura) }
            }

            // ---- En la mesilla y en la estantería: listas numeradas con filetes de 2
            if (mesilla.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Antetitulo("En la mesilla", Modifier.padding(bottom = 6.dp))
                Filete(2.dp)
                mesilla.forEachIndexed { i, l -> FilaLibro(l, alAbrir, portada = 48.dp, orden = i + 1); Filete(2.dp) }
                Text("VER LOS QUE LEES →", color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                    modifier = Modifier.clickable { alIr(Destino.Leyendo) }.padding(vertical = 12.dp))
            }
            if (estanteria.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Antetitulo("En la estantería", Modifier.padding(bottom = 6.dp))
                Filete(2.dp)
                estanteria.forEachIndexed { i, l -> FilaLibro(l, alAbrir, portada = 48.dp, orden = i + 1); Filete(2.dp) }
                Text("VER LOS PENDIENTES →", color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                    modifier = Modifier.clickable { alIr(Destino.Pendientes) }.padding(vertical = 12.dp))
            }

            Text("Seshat · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp))
            // La versión instalada, debajo (para no tener que entrar en Ajustes a mirarla)
            Text("versión $VERSION", color = Muted.copy(alpha = 0.7f), fontFamily = Body, fontSize = 10.sp, letterSpacing = 0.6.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
            Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
    // Al bajar, la cabecera se pliega: arriba solo quedan «SESHAT.» y lo de siempre (Buscar · + Añadir)
    BarraPlegada("Seshat", plegada) { EnlaceRevista("Buscar", color = Paper.copy(alpha = 0.8f)) { alBuscar() }; EnlaceRevista("+ Añadir", Modifier.padding(start = 14.dp), color = Acento) { alAnadir() } }
    }
}

/** Una barra fina de lo leído. */
@Composable
fun Barra(f: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Line)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
}
