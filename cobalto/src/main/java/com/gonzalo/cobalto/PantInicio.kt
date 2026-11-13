package com.gonzalo.cobalto

import com.gonzalo.familia.abre
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId

/** El año de una fecha (ms), con la hora de este móvil. */
fun anoDe(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).year

/** Los capítulos terminados en [ano] (por la última vez que se tocaron), por obra: obra → cuántos. */
fun Escritos.capitulosDelAno(ano: Int): Map<String, Int> = obras().associate { o ->
    o.id to capitulos(o.id).count { c -> Compartir.hecho(this, c) && anoDe(maxOf(c.u, escenas(c.id).maxOfOrNull { it.u } ?: 0L)) == ano }
}.filterValues { it > 0 }

/**
 * La portada de Thot, la primera página (la cabecera y las pestañas son de [PantPrincipal]):
 *  - El cartel: la obra en la que escribes (la última que has tocado), con su portada grande y su paspartú sobre su fondo o su color,
 *    «AHORA ESCRIBES · CAP. N · NOMBRE», las palabras de hoy y las de la obra, y la barra de su meta; debajo, «SEGUIR ESCRIBIENDO →», que
 *    abre el manuscrito donde lo dejaste. Sin obras, una invitación a crear la primera.
 *  - Debajo del botón: la meta de palabras del día (cómo vas hoy y cuántos días la cumpliste) y la semana en barras que se tocan.
 *  - «También escribes» y «En pausa»: lado a lado si son pocas, cada una en su fila si no.
 *  - «Tu 2026»: el mosaico de las obras con capítulos terminados este año.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaginaPortada(alAbrir: (String) -> Unit, alSeguir: (String, String?) -> Unit, alNueva: () -> Unit, alIr: (Destino) -> Unit, alPareja: () -> Unit, alMeta: () -> Unit,
                  alSprint: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    val ver = e.version
    val mv = MetaDiaria.version
    val ahora = remember(ver) { e.obraActual() }
    val todas = remember(ver) { e.obras() }
    val otras = remember(ver) { e.obrasDe(EstadoObra.ESCRIBIENDO).filter { it.id != ahora?.id } }
    val pausa = remember(ver) { e.obrasDe(EstadoObra.PAUSA) }
    val meta = remember(ver, mv) { MetaDiaria.de(ctx) }
    val datos = remember(ver, meta) { datosEscritura(e, meta) }
    val ano = remember { anoDe(System.currentTimeMillis()) }
    val delAno = remember(ver) { e.capitulosDelAno(ano) }
    val obrasAno = remember(ver) { delAno.keys.mapNotNull { e.get(it) }.sortedByDescending { e.ultimaEdicion(it.id) } }
    androidx.compose.runtime.LaunchedEffect(ver) { ImagenesObra.bajarQueFalten(ctx, e) }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        // ---- El cartel: la obra en la que escribes; debajo, «Seguir escribiendo» a todo el ancho
        if (ahora != null) {
            val escena = remember(ver, ahora.id) { e.ultimaEscenaDe(ahora.id) }
            Cartel(e, ahora) { alAbrir(ahora.id) }
            BotonC("Seguir escribiendo", fondo = Acento, letra = SobreAcento) { alSeguir(ahora.id, escena?.id) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                Text("EMPEZAR UN SPRINT →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, maxLines = 1,
                    modifier = Modifier.clickable { alSprint(ahora.id) }.padding(vertical = 10.dp))
            }
        } else CartelVacio(todas.isNotEmpty(), alNueva) { alIr(Destino.EnPausa) }

        // ---- La meta del día y la semana, justo debajo del botón (sin obras, solo la invitación)
        if (todas.isNotEmpty()) {
        BloqueReto(datos, alMeta)
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp), verticalAlignment = Alignment.Bottom) {
            Text("Esta semana", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text("${palabrasCorto(datos.semanaTotal)} · HOY ${palabrasCorto(datos.hoy)}", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1)
        }
        SemanaTocable(datos, Modifier.padding(horizontal = 20.dp))
        if (datos.racha > 0) Text("${datos.racha} ${if (datos.racha == 1) "DÍA SEGUIDO" else "DÍAS SEGUIDOS"} ESCRIBIENDO", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black,
            fontSize = 10.5.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
        }

        // ---- Lo que te comparte tu pareja (si te comparte algo)
        Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { TeComparte(alPareja) }

        // ---- También escribes y En pausa: con pocas (tres portadas entre las dos, como mucho), lado a lado
        val juntos = otras.isNotEmpty() && pausa.isNotEmpty() && otras.size + pausa.size <= 3
        if (juntos) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                val n = otras.size + pausa.size
                val ancho = ((maxWidth.value - 18f - 10f * (n - 2)) / n).coerceIn(72f, 120f).toInt()
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column {
                        EncabezadoCorto("También escribes", "${otras.size} →", (ancho * otras.size + 10 * (otras.size - 1)).dp) { alIr(Destino.Escribiendo) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { otras.forEach { o -> TapaConAvance(e, o, ancho, alAbrir) } }
                    }
                    Column {
                        EncabezadoCorto("En pausa", "${pausa.size} →", (ancho * pausa.size + 10 * (pausa.size - 1)).dp) { alIr(Destino.EnPausa) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { pausa.forEach { o -> TapaConAvance(e, o, ancho, alAbrir) } }
                    }
                }
            }
        } else {
            if (otras.isNotEmpty()) {
                TituloEstante("También escribes", "${otras.size} →") { alIr(Destino.Escribiendo) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    otras.forEach { o -> TapaConAvance(e, o, 120, alAbrir) }
                }
            }
            if (pausa.isNotEmpty()) {
                TituloEstante("En pausa", "${pausa.size} →") { alIr(Destino.EnPausa) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pausa.forEach { o -> TapaConAvance(e, o, 104, alAbrir) }
                }
            }
        }

        // ---- Tu año: las obras con capítulos terminados este año
        if (todas.isNotEmpty()) {
            val caps = delAno.values.sum()
            TituloEstante("Tu $ano · $caps ${if (caps == 1) "capítulo" else "capítulos"}", "Terminadas →") { alIr(Destino.Terminadas) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                if (obrasAno.isEmpty()) Text("Aún no has terminado ningún capítulo este año. Márcalo como terminado en el manuscrito y su obra saldrá aquí.", color = Muted,
                    fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
                else MosaicoAno(obrasAno, delAno, alAbrir)
            }
        }

        Text("Thot · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 22.dp))
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

/** El título pequeño de un estante cuando van dos lado a lado. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EncabezadoCorto(titulo: String, enlace: String, ancho: androidx.compose.ui.unit.Dp, alIr: () -> Unit) {
    // (si «En pausa 1 →» no cabe encima de sus portadas —letra grande—, el enlace baja a la línea de abajo en vez de cortarse)
    FlowRow(Modifier.widthIn(max = ancho).clickable(onClick = alIr).padding(bottom = 8.dp), verticalArrangement = Arrangement.Bottom) {
        Text(titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(end = 8.dp))
        Text(enlace, color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.align(Alignment.Bottom).padding(bottom = 2.dp))
    }
}

/** Una tapa de «También escribes» o «En pausa», con su barra hacia la meta (o sus palabras). */
@Composable
private fun TapaConAvance(e: Escritos, o: Doc, ancho: Int, alAbrir: (String) -> Unit) {
    val ver = e.version
    val palabras = remember(ver, o.id) { e.palabrasObra(o.id) }
    val meta = o.i("meta")
    Column(Modifier.width(ancho.dp).abre(o.id) { alAbrir(o.id) }) {
        PortadaObra(o, Modifier.width(ancho.dp).height((ancho * 1.5f).dp))
        if (meta > 0) Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(4.dp).background(Line)) { Box(Modifier.fillMaxWidth(e.avance(o)).fillMaxHeight().background(Acento)) }
        Text(if (meta > 0) "${(palabras * 100L / meta).coerceAtMost(999)} %" else "${palabrasCorto(palabras)} PAL.", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp,
            letterSpacing = 0.6.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/** El mosaico del año: 4 por fila, como mucho 3 filas; cada portada con sus capítulos del año. Si hay más, la última casilla dice «+N». */
@Composable
private fun MosaicoAno(obras: List<Doc>, caps: Map<String, Int>, alAbrir: (String) -> Unit) {
    val col = 4; val max = col * 3
    val sobran = (obras.size - max).coerceAtLeast(0)
    val vistas = if (sobran > 0) obras.take(max - 1) else obras
    val casillas: List<Doc?> = vistas + if (sobran > 0) listOf(null) else emptyList()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        casillas.chunked(col).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                fila.forEach { o ->
                    Box(Modifier.weight(1f).aspectRatio(0.67f)) {
                        if (o != null) {
                            PortadaObra(o, Modifier.fillMaxSize().abre(o.id) { alAbrir(o.id) })
                            Text("${caps[o.id] ?: 0} CAP.", color = Paper, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 0.6.sp, maxLines = 1,
                                modifier = Modifier.align(Alignment.BottomStart).background(Charcoal).padding(horizontal = 5.dp, vertical = 2.dp))
                        } else Box(Modifier.fillMaxSize().background(Charcoal), contentAlignment = Alignment.Center) {
                            Text("+${sobran + 1}", color = Paper, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1)
                        }
                    }
                }
                repeat(col - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * El cartel de la portada: la obra en la que escribes. Su portada grande (con paspartú) sobre su fondo o su color, oscurecido abajo;
 * encima, abajo, «AHORA ESCRIBES · CAP. N · NOMBRE», el título en grotesca, la barra de su meta (si tiene) y las palabras de hoy y totales.
 */
@Composable
private fun Cartel(e: Escritos, o: Doc, alAbrir: () -> Unit) {
    val ctx = LocalContext.current
    val ver = e.version
    val v = ImagenesObra.version
    val base = baseDeObra(o)
    val claro = lerpColor(base, Color.White, 0.12f)
    val oscuro = lerpColor(base, Color.Black, 0.72f)
    // Sobre el oscurecido (casi negro), el acento elegido aclarado lo justo para leerse
    val acentoClaro = acentoLegible(AcentoBloque, Color(0xFF050404), Color.White, 4.5f)
    val palabras = remember(ver, o.id) { e.palabrasObra(o.id) }
    val hoy = remember(ver, o.id) { e.escritas(Escritos.hoy(), o.id) }
    val cap = remember(ver, o.id) {
        val caps = e.capitulos(o.id); val cid = e.ultimaEscenaDe(o.id)?.s("cid"); val i = caps.indexOfFirst { it.id == cid }
        if (i >= 0) "CAP. ${i + 1}" + caps[i].s("titulo").takeIf { it.isNotBlank() }?.let { " · ${it.uppercase()}" }.orEmpty() else null
    }
    val meta = o.i("meta")
    val fondo = remember(o.s(ImagenesObra.FONDO), v) { ImagenesObra.fondo(ctx, o) }
    Box(Modifier.fillMaxWidth().height(ALTO_CARTEL).background(Brush.verticalGradient(listOf(claro, oscuro))).clickable(onClick = alAbrir)) {
        val conFondo = fondo.isNotBlank()
        if (conFondo) {
            ImagenEncuadrada(fondo, remember(fondo, v) { ImagenesObra.encFondo(ctx, o) }, Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))))
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp).background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Color(0xCC050404), 1f to Color(0xF2050404))))
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = if (conFondo) 20.dp else 16.dp, bottom = 14.dp, start = 20.dp, end = 20.dp),
                contentAlignment = if (conFondo) Alignment.TopStart else Alignment.TopCenter) {
                val tope = if (conFondo) 225.dp else 330.dp
                PortadaSobreFondo(o, Modifier.heightIn(max = tope).fillMaxHeight().aspectRatio(2f / 3f, matchHeightConstraintsFirst = true), sombra = 22.dp)
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                Text(listOfNotNull("AHORA ESCRIBES", cap).joinToString(" · "), color = acentoClaro, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp,
                    lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(o.s("titulo").ifBlank { "Sin título" }.uppercase(), color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 33.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                if (meta > 0) Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.22f))) {
                    Box(Modifier.fillMaxWidth(e.avance(o)).fillMaxHeight().background(acentoClaro))
                }
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("HOY ${cifraE(hoy)}", color = if (hoy > 0) acentoClaro else Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp,
                        maxLines = 1, modifier = Modifier.weight(1f))
                    Text((if (meta > 0) "${cifraE(palabras)} DE ${cifraE(meta)} PALABRAS" else "${cifraE(palabras)} PALABRAS"), color = Color.White.copy(alpha = 0.85f), fontFamily = Body,
                        fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

/** El cartel cuando no escribes nada: la manera de llenarlo (crear una obra, o retomar una en pausa). */
@Composable
private fun CartelVacio(hayObras: Boolean, alNueva: () -> Unit, alPausa: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Charcoal).padding(horizontal = 20.dp, vertical = 28.dp)) {
        Text("AHORA ESCRIBES", color = acentoSobreTinta(), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp)
        Text("Nada, de momento.", color = Paper, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 33.sp, modifier = Modifier.padding(top = 4.dp))
        Text(if (hayObras) "Retoma una de las que tienes en pausa o empieza una nueva, y su portada saldrá aquí en grande."
            else "Aquí empieza tu novela. Crea una obra y escribe por capítulos y escenas, con fichas, notas, borradores e historial. Se guarda sola, funciona sin conexión y sale en el PC y en tus otros móviles.",
            color = Paper.copy(alpha = 0.8f), fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        BotonC("Nueva obra", fondo = Paper, letra = Charcoal, onClick = alNueva)
        if (hayObras) Text("VER LAS QUE ESTÁN EN PAUSA →", color = acentoSobreTinta(), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(top = 12.dp).clickable(onClick = alPausa).padding(vertical = 6.dp))
    }
}
