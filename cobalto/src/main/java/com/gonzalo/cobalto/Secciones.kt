package com.gonzalo.cobalto

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/** Cómo se puede ordenar un menú. */
enum class OrdenObras(val nombre: String) { RECIENTES("Recientes"), TITULO("Título"), PALABRAS("Palabras"), CREADAS("Creadas") }

fun ordenar(o: OrdenObras, e: Escritos, l: List<Doc>): List<Doc> = when (o) {
    OrdenObras.RECIENTES -> l.sortedByDescending { e.ultimaEdicion(it.id) }
    OrdenObras.TITULO -> l.sortedBy { norm(it.s("titulo")) }
    OrdenObras.PALABRAS -> l.sortedByDescending { e.palabrasObra(it.id) }
    OrdenObras.CREADAS -> l.sortedByDescending { it.n("creado", it.u) }
}

fun norm(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/**
 * Una obra en una lista: su color a la izquierda, el título, lo que dice debajo y, a la derecha y en el acento, lo que lleva de su meta
 * (o sus palabras).
 */
@Composable
fun FilaObra(e: Escritos, o: Doc, alAbrir: (String) -> Unit, modifier: Modifier = Modifier, conEstado: Boolean = false) {
    val palabras = remember(e.version, o.id) { e.palabrasObra(o.id) }
    val meta = o.i("meta")
    Row(modifier.fillMaxWidth().clickable { alAbrir(o.id) }.padding(top = 12.dp, bottom = 12.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).fillMaxHeight().background(colorEscrito(o.s("color"))))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(o.s("titulo").ifBlank { "Sin título" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(o.s("autor").ifBlank { null }, o.s("genero").ifBlank { null }, if (conEstado) estadoDe(o).nombre else null, "editada ${haceE(e.ultimaEdicion(o.id))}").joinToString(" · ")
            Text(sub.uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 2, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
        }
        // A la derecha: lo de hoy en esta obra (lo que se mira al abrir) y, debajo, lo que lleva de su meta
        val hoy = remember(e.version, o.id) { e.escritas(Escritos.hoy(), o.id) }
        Column(horizontalAlignment = Alignment.End) {
            if (hoy > 0) Text("${cifraE(hoy)} HOY", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.6.sp, maxLines = 1)
            Text(if (meta > 0) "${(palabras * 100L / meta).coerceAtMost(999)} %" else cifraE(palabras), color = if (hoy > 0) Muted else Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = if (hoy > 0) 10.sp else 12.sp, letterSpacing = 0.6.sp, maxLines = 1)
        }
    }
    Filete(1.dp, Line)
}

private fun vacioDe(e: EstadoObra) = when (e) {
    EstadoObra.ESCRIBIENDO -> "No tienes nada entre manos. Crea una obra y saldrá aquí."
    EstadoObra.PAUSA -> "Nada en pausa. Si dejas una obra para más adelante, márcala así desde sus ajustes."
    EstadoObra.TERMINADA -> "Aún no has terminado ninguna. Cuando pongas el punto final, márcala como terminada desde sus ajustes."
    EstadoObra.ARCHIVO -> "El archivo está vacío. Lo que archives desde los ajustes de una obra saldrá aquí."
}

/** Una página de un menú: título enorme y sus obras. */
@Composable
private fun PaginaMenu(estado: EstadoObra, e: Escritos, ver: Int, alAbrir: (String) -> Unit, alNueva: () -> Unit) {
    var orden by rememberSaveable(estado) { mutableStateOf(OrdenObras.RECIENTES) }
    val obras = remember(ver, estado, orden) { ordenar(orden, e, e.obrasDe(estado)) }
    val todas = remember(ver) { e.obras().size }
    var papelera by remember { mutableStateOf(false) }
    val enPapelera = remember(ver) { e.papelera().size }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo("Sección · ${obras.size} ${if (obras.size == 1) "obra" else "obras"}", Modifier.padding(top = 12.dp), color = Acento, conCuadro = false)
                FitTitle(estado.plural, 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(1.dp, Line)
                FiltrosRevista(OrdenObras.entries.map { it.nombre }, OrdenObras.entries.indexOf(orden), Modifier.padding(top = 4.dp)) { i -> orden = OrdenObras.entries[i] }
                Filete(1.dp, Line)
            }
        }
        if (obras.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                Text(if (todas == 0) "Todavía no hay nada aquí." else vacioDe(estado), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp)
                if (todas == 0) MagLink("Nueva obra", accent = true, onClick = alNueva)
            }
        }
        items(obras, key = { it.id }) { o -> FilaObra(e, o, alAbrir) }
        if (estado == EstadoObra.ARCHIVO) item {
            Text("PAPELERA" + if (enPapelera > 0) " · $enPapelera" else "", color = Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).clickable { papelera = true }.padding(vertical = 6.dp))
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (papelera) PapeleraEscribir(e, onCerrar = { papelera = false })
}

/** Lo que sale al buscar: todas las obras que cuadran, estén en el menú que estén. */
@Composable
private fun Resultados(busca: String, e: Escritos, ver: Int, alAbrir: (String) -> Unit) {
    val q = norm(busca)
    val l = remember(busca, ver) { e.obras().filter { norm(it.s("titulo")).contains(q) || norm(it.s("autor")).contains(q) || norm(it.s("genero")).contains(q) }.sortedByDescending { e.ultimaEdicion(it.id) } }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Antetitulo("Resultados · ${l.size}", Modifier.padding(top = 14.dp, bottom = 6.dp), color = Acento, conCuadro = false); Filete(1.dp, Line) }
        if (l.isEmpty()) item { Text("Nada con «$busca».", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(top = 24.dp)) }
        items(l, key = { it.id }) { o -> FilaObra(e, o, alAbrir, conEstado = true) }
    }
}

/**
 * Los menús como páginas por las que te mueves (con el dedo o con las pestañas): Escribiendo, En pausa, Terminadas y Archivo.
 * Buscar mira en todos a la vez. «Importar» trae un texto (Word, Markdown o texto) como obra nueva.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantSecciones(inicial: Destino, alVolver: () -> Unit, alAbrir: (String) -> Unit, alNueva: () -> Unit, buscando: Boolean = false) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    val ver = e.version
    val paginas = listOf(Destino.Escribiendo, Destino.EnPausa, Destino.Terminadas, Destino.Archivo)
    val pager = rememberPagerState(initialPage = paginas.indexOf(inicial).coerceAtLeast(0)) { paginas.size }
    val ambito = rememberCoroutineScope()
    var buscando by rememberSaveable { mutableStateOf(buscando) }
    var busca by rememberSaveable { mutableStateOf("") }
    var aviso by remember { mutableStateOf("") }
    val cuentas = remember(ver) { paginas.map { d -> e.obrasDe(d.estado()!!).size } }
    val importar = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ambito.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    val nombre = (ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Importada")
                    val txt = if (nombre.endsWith(".docx", true)) EscribirExport.textoDeDocx(bytes) else bytes.toString(Charsets.UTF_8)
                    EscribirExport.importaTexto(e, nombre.substringBeforeLast('.'), txt)
                }
            }
            aviso = r.fold({ "Importada. Ya la tienes entre las que escribes." }, { "No he podido importarla (${it.message ?: "archivo no válido"})." })
        }
    }
    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding().padding(horizontal = 28.dp)) {
        RevistaTopBar(alVolver, "Cobalto") {
            EnlaceRevista("Buscar", acento = buscando) { buscando = !buscando; if (!buscando) busca = "" }
            EnlaceRevista("Importar") { importar.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream")) }
            EnlaceRevista("+ Nueva", acento = true) { alNueva() }
        }
        Filete(3.dp)
        if (buscando) CampoRevista("Buscar", busca, "título, autor o género") { busca = it }
        if (aviso.isNotEmpty()) Text(aviso, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        PestanasPaginas(paginas.map { it.estado()!!.plural }, pager.currentPage, cuentas) { i -> ambito.launch { pager.animateScrollToPage(i) } }
        if (busca.isNotBlank()) Resultados(busca, e, ver, alAbrir)
        // (sin páginas vecinas precompuestas: con ellas, en las páginas que no son la primera los enlaces de la cabecera no recibían el toque)
        else HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { paginas[it].name }) { i ->
            PaginaMenu(paginas[i].estado()!!, e, ver, alAbrir, alNueva)
        }
    }
}

/** Las pestañas de las secciones (como las de Atrio): la elegida en tinta con el subrayado de acento; su número, en acento. Si no caben, se deslizan y la elegida siempre se ve. */
@Composable
private fun PestanasPaginas(nombres: List<String>, sel: Int, cuentas: List<Int>, onSel: (Int) -> Unit) {
    // La fila se desplaza sola solo si la pestaña elegida no se ve entera (y entonces la centra): así no se corta la de al lado
    val scroll = androidx.compose.foundation.rememberScrollState()
    val sitios = remember { androidx.compose.runtime.mutableStateMapOf<Int, Pair<Int, Int>>() }   // pestaña → (x, ancho), en coordenadas del contenido
    var anchoFila by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(sel, sitios[sel], anchoFila) {
        val (x, w) = sitios[sel] ?: return@LaunchedEffect
        if (anchoFila <= 0) return@LaunchedEffect
        if (x < scroll.value || x + w > scroll.value + anchoFila) scroll.animateScrollTo((x + w / 2 - anchoFila / 2).coerceIn(0, scroll.maxValue))
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().onGloballyPositioned { anchoFila = it.size.width }.drawWithContent {
                // Un degradado en los bordes cuando hay más pestañas por un lado o por otro (para que no parezcan cortadas)
                drawContent()
                val f = 28.dp.toPx()
                if (scroll.value > 0) drawRect(Brush.horizontalGradient(listOf(Paper, Color.Transparent), 0f, f), Offset.Zero, Size(f, size.height))
                if (scroll.value < scroll.maxValue) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Paper), size.width - f, size.width), Offset(size.width - f, 0f), Size(f, size.height))
            }.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Row(Modifier.onGloballyPositioned { c -> sitios[i] = (c.positionInParent().x + scroll.value).let { kotlin.math.round(it).toInt() } to c.size.width }.clickable { onSel(i) }
                    .drawBehind { if (on) drawRect(Acento, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 3.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx())) }
                    .padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(n.uppercase(), color = if (on) Charcoal else Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, maxLines = 1)
                    Text(" ${cuentas.getOrElse(i) { 0 }}", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        Filete(1.dp, Line)
    }
}
