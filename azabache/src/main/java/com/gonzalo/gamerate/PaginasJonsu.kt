package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Settings

/** Una página de Jonsu: su pestaña, su número (o nada) y su destino (el de la app: 0 portada, 1 juegos, 2 libros, 3 cine, 4 diario, 5 escritos, 100+id: una categoría tuya). */
class PaginaJonsu(val nombre: String, val cuenta: Int?, val destino: Int)

/** Las páginas, en el orden de las pestañas: la portada, el diario, las categorías (juegos, libros, cine y las tuyas) y los escritos. */
fun paginasJonsu(kinds: List<Kind>, cuenta: (Kind) -> Int, conEscritos: Boolean): List<PaginaJonsu> = buildList {
    add(PaginaJonsu("Portada", null, 0))
    add(PaginaJonsu("Diario", null, 4))
    kinds.firstOrNull { it.isGame }?.let { add(PaginaJonsu(it.title, cuenta(it), 1)) }
    kinds.firstOrNull { it.isBook }?.let { add(PaginaJonsu(it.title, cuenta(it), 2)) }
    val cine = kinds.filter { it.isMovie || it.isTv }
    if (cine.isNotEmpty()) add(PaginaJonsu("Cine", cine.sumOf(cuenta), 3))
    kinds.filter { it.custom && !it.isMovie && !it.isTv }.forEach { add(PaginaJonsu(it.title, cuenta(it), 100 + it.id)) }
    if (conEscritos) add(PaginaJonsu("Escritos", null, 5))
}

/**
 * Jonsu entera en una pantalla, como sus hermanas: la cabecera (un bloque crema con letra oscura: BUSCAR · PAREJA · la fecha · ⚙ y «JONSU.» en
 * grotesca enorme, que se recoge al bajar sin cortarse), una sola fila de pestañas y las páginas, que se pasan tocando la pestaña o con el
 * dedo. [destino] es el de la página que toca; [alDestino] avisa cuando cambia con el dedo. Las fichas se pintan encima (fuera de aquí).
 */
@Composable
fun PantPaginas(
    nombre: String, paginas: List<PaginaJonsu>, destino: Int, alDestino: (Int) -> Unit,
    alBuscar: () -> Unit, buscando: Boolean, alPareja: (() -> Unit)?, alAjustes: (() -> Unit)?,
    buscador: @Composable () -> Unit, pagina: @Composable (PaginaJonsu) -> Unit,
) {
    val indice = paginas.indexOfFirst { it.destino == destino }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = indice) { paginas.size }
    val ambito = rememberCoroutineScope()
    // Lo que pide fuera (Atrás, un widget, «Ver todo» de la portada) mueve las páginas; lo que mueves tú con el dedo, se cuenta fuera
    LaunchedEffect(indice) { if (pager.currentPage != indice) pager.animateScrollToPage(indice) }
    LaunchedEffect(pager.settledPage) { paginas.getOrNull(pager.settledPage)?.let { if (it.destino != destino) alDestino(it.destino) } }
    // El nombre grande se recoge al bajar (y vuelve al subir): lo que la página aún no ha movido, lo mueve la cabecera
    var altoNombre by remember { mutableIntStateOf(0) }
    var recogido by remember { mutableFloatStateOf(0f) }
    val conexion = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || altoNombre == 0) return Offset.Zero
                val antes = recogido; recogido = (recogido - available.y).coerceIn(0f, altoNombre.toFloat())
                return Offset(0f, -(recogido - antes))
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val antes = recogido; recogido = (recogido - available.y).coerceIn(0f, altoNombre.toFloat())
                return Offset(0f, antes - recogido)
            }
        }
    }
    val ancho = minOf(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp, 1080)
    val tablet = ancho >= 700
    val plegada = altoNombre > 0 && recogido >= altoNombre - 1
    // La cabecera es un bloque de la tinta (crema en Jonsu) con letra del color de la página (oscura)
    val tinta = Charcoal; val sobreTinta = Paper; val acentoCab = acentoSobreTinta()
    Column(Modifier.fillMaxSize().background(Paper).nestedScroll(conexion)) {
        Column(Modifier.fillMaxWidth().background(tinta).statusBarsPadding().padding(horizontal = if (tablet) 40.dp else 20.dp).testTag("cabecera")) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = if (plegada) 4.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                if (plegada) NombreConPunto(nombre, 22.sp, sobreTinta, Modifier.padding(end = 14.dp), punto = acentoCab)
                EnlaceCabecera(if (buscando) "Cerrar" else "Buscar", if (buscando) acentoCab else sobreTinta.copy(alpha = 0.8f), alBuscar)
                if (alPareja != null) EnlaceCabecera("Pareja", sobreTinta.copy(alpha = 0.8f), alPareja, Modifier.padding(start = 14.dp))
                Spacer(Modifier.weight(1f))
                if (!plegada) Text(java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale("es", "ES")).format(java.util.Date()), color = sobreTinta.copy(alpha = 0.8f), fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1)
                if (alAjustes != null) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Settings, "Ajustes", tint = sobreTinta.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 4.dp).clickable(onClick = alAjustes).padding(6.dp).size(20.dp))
            }
            // Al recogerse, el hueco se encoge y el nombre encoge con él (desde la esquina de arriba a la izquierda): nunca se corta a media letra
            Box(Modifier.fillMaxWidth().layout { m, c ->
                val p = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                if (altoNombre != p.height) altoNombre = p.height
                val h = (p.height - recogido.toInt()).coerceAtLeast(0)
                val escala = if (p.height > 0) h.toFloat() / p.height else 1f
                layout(p.width, h) {
                    if (h > 0) p.placeWithLayer(0, 0) {
                        scaleX = escala; scaleY = escala
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                }
            }) {
                NombreConPunto(nombre, if (tablet) 104.sp else 64.sp, sobreTinta, Modifier.padding(top = 2.dp, bottom = 12.dp), punto = acentoCab)
            }
        }
        PestanasC(paginas.map { it.nombre }, pager.currentPage, paginas.map { it.cuenta }) { i -> ambito.launch { pager.animateScrollToPage(i) } }
        if (buscando) Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars)) { buscador() }
        // (sin páginas vecinas precompuestas: con ellas, en las páginas que no son la primera los enlaces de la cabecera no recibían el toque)
        else HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars).testTag("paginas"), key = { paginas[it].destino }) { i ->
            pagina(paginas[i])
        }
    }
}

@Composable
private fun EnlaceCabecera(texto: String, color: androidx.compose.ui.graphics.Color, alTocar: () -> Unit, modifier: Modifier = Modifier) =
    Text(texto.uppercase(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1,
        modifier = modifier.clickable(onClick = alTocar).padding(vertical = 10.dp))
