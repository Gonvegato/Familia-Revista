package com.gonzalo.cobalto

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Cómo se puede ordenar una página de obras. */
enum class OrdenObras(val nombre: String) {
    RECIENTES("Última vez"), PALABRAS("Más palabras"), AVANCE("Más avanzada"), TITULO("Título A-Z"), CREADAS("Creadas")
}

/**
 * Los órdenes que tienen sentido en cada página (y solo esos):
 *  - Escribiendo: la última que tocaste, más palabras, más avanzada (hacia su meta), título.
 *  - En pausa: la última que tocaste, más palabras, título, creadas.
 *  - Terminadas: la última que tocaste, más palabras, título, creadas.
 *  - Archivo: la última que tocaste, título, creadas.
 */
fun ordenesDe(e: EstadoObra): List<OrdenObras> = when (e) {
    EstadoObra.ESCRIBIENDO -> listOf(OrdenObras.RECIENTES, OrdenObras.PALABRAS, OrdenObras.AVANCE, OrdenObras.TITULO)
    EstadoObra.PAUSA, EstadoObra.TERMINADA -> listOf(OrdenObras.RECIENTES, OrdenObras.PALABRAS, OrdenObras.TITULO, OrdenObras.CREADAS)
    EstadoObra.ARCHIVO -> listOf(OrdenObras.RECIENTES, OrdenObras.TITULO, OrdenObras.CREADAS)
}

/** Lo que lleva una obra de su meta de palabras (0 si no tiene meta). */
fun Escritos.avance(o: Doc): Float = o.i("meta").takeIf { it > 0 }?.let { (palabrasObra(o.id).toFloat() / it).coerceIn(0f, 1f) } ?: 0f

fun ordenar(o: OrdenObras, e: Escritos, l: List<Doc>): List<Doc> = when (o) {
    OrdenObras.RECIENTES -> l.sortedByDescending { e.ultimaEdicion(it.id) }
    OrdenObras.TITULO -> l.sortedBy { norm(it.s("titulo")) }
    OrdenObras.PALABRAS -> l.sortedByDescending { e.palabrasObra(it.id) }
    OrdenObras.AVANCE -> l.sortedWith(compareByDescending<Doc> { e.avance(it) }.thenByDescending { e.ultimaEdicion(it.id) })
    OrdenObras.CREADAS -> l.sortedByDescending { it.n("creado", it.u) }
}

/** El orden elegido en cada página (en este dispositivo). */
object OrdenGuardado {
    private fun p(c: android.content.Context) = c.getSharedPreferences("cobalto_orden", android.content.Context.MODE_PRIVATE)
    fun de(c: android.content.Context, e: EstadoObra): OrdenObras = ordenesDe(e).firstOrNull { it.name == p(c).getString(e.name, null) } ?: ordenesDe(e).first()
    fun pon(c: android.content.Context, e: EstadoObra, o: OrdenObras) { p(c).edit().putString(e.name, o.name).apply() }
}

fun norm(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** La etiqueta del estado de una obra: «ESCRIBIENDO · CAP. 4», «EN PAUSA», «TERMINADA», «ARCHIVADA». */
fun etiquetaEstado(e: Escritos, o: Doc): String = when (estadoDe(o)) {
    EstadoObra.ESCRIBIENDO -> capituloActual(e, o.id)?.let { "ESCRIBIENDO · CAP. $it" } ?: "ESCRIBIENDO"
    EstadoObra.PAUSA -> "EN PAUSA"
    EstadoObra.TERMINADA -> "TERMINADA"
    EstadoObra.ARCHIVO -> "ARCHIVADA"
}

/** El número del capítulo por el que vas (el de la última escena que tocaste), o null si la obra no tiene capítulos. */
fun capituloActual(e: Escritos, pid: String): Int? {
    val caps = e.capitulos(pid)
    if (caps.isEmpty()) return null
    val cid = e.ultimaEscenaDe(pid)?.s("cid")
    val i = caps.indexOfFirst { it.id == cid }
    return if (i >= 0) i + 1 else null
}

/**
 * Una obra en una lista, con la portada grande (84×126 dp de serie): encima del título, pequeño y en el acento, su número en la lista
 * (01, 02…); el título en grotesca, el género en versalitas y, abajo, las palabras y el avance hacia su meta con su barra (o lo de hoy).
 */
@Composable
fun FilaObra(e: Escritos, o: Doc, alAbrir: (String) -> Unit, modifier: Modifier = Modifier, portada: Dp = 84.dp, orden: Int = 0, conEstado: Boolean = false) {
    val ver = e.version
    val palabras = remember(ver, o.id) { e.palabrasObra(o.id) }
    val hoy = remember(ver, o.id) { e.escritas(Escritos.hoy(), o.id) }
    val meta = o.i("meta")
    val alto = portada * 1.5f
    Row(modifier.fillMaxWidth().clickable { alAbrir(o.id) }.padding(top = 14.dp, bottom = 14.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        PortadaObra(o, Modifier.width(portada).height(alto))
        Column(Modifier.weight(1f).fillMaxHeight().heightIn(min = alto).padding(start = 14.dp)) {
            if (orden > 0) Text("%02d".format(orden), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1)
            Text(o.s("titulo").ifBlank { "Sin título" }, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 22.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (orden > 0) 2.dp else 0.dp))
            val genero = o.s("genero")
            if (genero.isNotBlank()) Text(genero.uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            val meta2 = listOfNotNull(if (conEstado) estadoDe(o).nombre else null, "editada ${haceE(e.ultimaEdicion(o.id))}").joinToString(" · ")
            Text(meta2.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 2, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp))
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(if (meta > 0) "${(palabras * 100L / meta).coerceAtMost(999)} %" else if (hoy > 0) "${cifraE(hoy)} HOY" else "", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black,
                    fontSize = 12.sp, letterSpacing = 0.6.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text("${cifraE(palabras)} PALABRAS", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.6.sp, maxLines = 1)
            }
            if (meta > 0) Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp).background(Line)) { Box(Modifier.fillMaxWidth(e.avance(o)).fillMaxHeight().background(Acento)) }
        }
    }
}

private fun vacioDe(e: EstadoObra) = when (e) {
    EstadoObra.ESCRIBIENDO -> "No tienes nada entre manos. Crea una obra y saldrá aquí."
    EstadoObra.PAUSA -> "Nada en pausa. Si dejas una obra para más adelante, márcala así desde su ficha."
    EstadoObra.TERMINADA -> "Aún no has terminado ninguna. Cuando pongas el punto final, márcala como terminada desde su ficha."
    EstadoObra.ARCHIVO -> "El archivo está vacío. Lo que archives desde la ficha de una obra saldrá aquí."
}

/** Una página de obras: su rejilla de cifras, cómo se ordena y sus obras con portada. En Archivo, la papelera al final. */
@Composable
private fun PaginaObras(estado: EstadoObra, e: Escritos, alAbrir: (String) -> Unit, alNueva: () -> Unit, alImportar: () -> Unit) {
    val ctx = LocalContext.current
    val ver = e.version
    var orden by remember(estado) { mutableStateOf(OrdenGuardado.de(ctx, estado)) }
    val obras = remember(ver, estado, orden) { ordenar(orden, e, e.obrasDe(estado)) }
    val todas = remember(ver) { e.obras().size }
    var papelera by remember { mutableStateOf(false) }
    val enPapelera = remember(ver) { if (estado == EstadoObra.ARCHIVO) e.papelera().size else 0 }
    val palabras = remember(ver, estado) { obras.sumOf { e.palabrasObra(it.id) } }
    val hoy = remember(ver, estado) { obras.sumOf { e.escritas(Escritos.hoy(), it.id) } }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo(estado.plural, Modifier.padding(top = 14.dp), color = Acento, conCuadro = false)
                RejillaCifras(listOf(
                    BloqueCifra("${obras.size}", if (obras.size == 1) "obra" else "obras", AcentoBloque, SobreBloque),
                    BloqueCifra(palabrasCorto(palabras), "palabras", Paper, Charcoal),
                    BloqueCifra(palabrasCorto(hoy), "hoy", Bloque2, Color.White),
                ), Modifier.padding(top = 8.dp))
                val ordenes = ordenesDe(estado)
                FiltrosRevista(ordenes.map { it.nombre }, ordenes.indexOf(orden).coerceAtLeast(0), Modifier.padding(top = 6.dp)) { i -> orden = ordenes[i]; OrdenGuardado.pon(ctx, estado, orden) }
                // Traer un texto (Word, Markdown o texto) como obra nueva: entre las que escribes
                if (estado == EstadoObra.ESCRIBIENDO) EnlaceRevista("+ Importar un texto (Word, Markdown…)", acento = true, onClick = alImportar)
                Filete(2.dp)
            }
        }
        if (obras.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                Text(if (todas == 0) "Todavía no hay nada aquí." else vacioDe(estado), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp)
                if (todas == 0 || estado == EstadoObra.ESCRIBIENDO) BotonC("Nueva obra", Modifier.padding(top = 14.dp), fondo = Acento, letra = SobreAcento, onClick = alNueva)
            }
        }
        itemsIndexed(obras, key = { _, it -> it.id }) { i, o -> FilaObra(e, o, alAbrir, orden = i + 1); Filete(2.dp) }
        if (estado == EstadoObra.ARCHIVO) item {
            Text("PAPELERA" + if (enPapelera > 0) " · $enPapelera" else "", color = Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).clickable { papelera = true }.padding(vertical = 6.dp))
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (papelera) PapeleraEscribir(e, onCerrar = { papelera = false })
}

/** Lo que sale al buscar: todas las obras que cuadran, estén en la página que estén. */
@Composable
private fun Resultados(busca: String, e: Escritos, alAbrir: (String) -> Unit) {
    val q = norm(busca)
    val ver = e.version
    val l = remember(busca, ver) { e.obras().filter { norm(it.s("titulo")).contains(q) || norm(it.s("autor")).contains(q) || norm(it.s("genero")).contains(q) }.sortedByDescending { e.ultimaEdicion(it.id) } }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Antetitulo("Resultados · ${l.size}", Modifier.padding(top = 14.dp, bottom = 6.dp), color = Acento, conCuadro = false); Filete(2.dp) }
        if (l.isEmpty()) item { Text("Nada con «$busca».", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(top = 24.dp)) }
        itemsIndexed(l, key = { _, it -> it.id }) { i, o -> FilaObra(e, o, alAbrir, orden = i + 1, conEstado = true); Filete(2.dp) }
    }
}

/** Las páginas de Thot, en el orden de las pestañas: la portada y luego las de obras (los índices son los de [Destino]). */
val PAGINAS = listOf(Destino.Portada, Destino.Escribiendo, Destino.EnPausa, Destino.Terminadas, Destino.Archivo)
private val NOMBRES_PAGINAS = listOf("Portada", "Escribiendo", "En pausa", "Terminadas", "Archivo")

/** El acento sobre la tinta de la cabecera (clara en Thot, con letra azul noche): el elegido, ajustado para leerse ahí. */
fun acentoSobreTinta(): Color = acentoLegible(AcentoBloque, Charcoal, Paper)

/**
 * Thot entera en una pantalla: la cabecera de tinta (BUSCAR · + NUEVA · fecha · ⚙ y «THOT.»), una sola fila de pestañas (Portada,
 * Escribiendo, En pausa, Terminadas, Archivo) y las páginas, que se pasan tocando la pestaña o deslizando el dedo. [pagina] es la que toca
 * (la de [Destino]); [alPagina] avisa cuando cambia. Al bajar en una página, el nombre grande se recoge y quedan la fila pequeña y las pestañas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantPrincipal(
    pagina: Int, alPagina: (Int) -> Unit, alAbrir: (String) -> Unit, alSeguir: (String, String?) -> Unit, alNueva: () -> Unit,
    alAjustes: () -> Unit, alPareja: () -> Unit, alMeta: () -> Unit, alSprint: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    val ver = e.version
    val pager = rememberPagerState(initialPage = pagina.coerceIn(0, PAGINAS.size - 1)) { PAGINAS.size }
    val ambito = rememberCoroutineScope()
    LaunchedEffect(pagina) { val p = pagina.coerceIn(0, PAGINAS.size - 1); if (pager.currentPage != p) pager.animateScrollToPage(p) }
    LaunchedEffect(pager.settledPage) { alPagina(pager.settledPage) }
    var buscando by rememberSaveable { mutableStateOf(false) }
    var busca by rememberSaveable { mutableStateOf("") }
    var aviso by remember { mutableStateOf("") }
    val cuentas: List<Int?> = remember(ver) { listOf(null) + PAGINAS.drop(1).map { d -> e.obrasDe(d.estado()!!).size } }
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
    val tablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
    val plegada = altoNombre > 0 && recogido >= altoNombre - 1
    Column(Modifier.fillMaxSize().background(Paper).nestedScroll(conexion)) {
        // ---- La cabecera de la dirección C: un bloque de tinta (crema en Thot) con la fila pequeña y «THOT.» en grotesca enorme
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = if (tablet) 40.dp else 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = if (plegada) 4.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                if (plegada) NombreConPunto("Thot", 22.sp, Paper, Modifier.padding(end = 14.dp))
                EnlaceRevista("Buscar", color = if (buscando) acentoSobreTinta() else Paper.copy(alpha = 0.75f)) { buscando = !buscando; if (!buscando) busca = "" }
                EnlaceRevista("+ Nueva", Modifier.padding(start = 14.dp), color = acentoSobreTinta()) { alNueva() }
                Spacer(Modifier.weight(1f))
                if (!plegada) Text(java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale("es", "ES")).format(java.util.Date()), color = Paper.copy(alpha = 0.75f), fontFamily = Body,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1)
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Settings, "Ajustes", tint = Paper.copy(alpha = 0.75f), modifier = Modifier.padding(start = 4.dp).clickable(onClick = alAjustes).padding(6.dp).size(20.dp))
            }
            Box(Modifier.fillMaxWidth().layout { m, c ->
                val p = m.measure(c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
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
                NombreConPunto("Thot", if (ancho >= 700) 104.sp else 64.sp, Paper, Modifier.padding(top = 2.dp, bottom = 12.dp))
            }
        }
        PestanasPaginas(NOMBRES_PAGINAS, pager.currentPage, cuentas) { i -> buscando = false; busca = ""; ambito.launch { pager.animateScrollToPage(i) } }
        if (buscando) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { CampoRevista("Buscar", busca, "título, autor o género") { busca = it } }
        if (aviso.isNotEmpty()) Text(aviso, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clickable { aviso = "" })
        if (busca.isNotBlank()) Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.TopCenter) { Box(Modifier.widthIn(max = 960.dp)) { Resultados(busca, e, alAbrir) } }
        // (sin páginas vecinas precompuestas: con ellas, en las páginas que no son la primera los enlaces de la cabecera no recibían el toque)
        else HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { PAGINAS[it].name }) { i ->
            when (val d = PAGINAS[i]) {
                Destino.Portada -> PaginaPortada(alAbrir = alAbrir, alSeguir = alSeguir, alNueva = alNueva, alIr = { x -> val p = PAGINAS.indexOf(x); if (p >= 0) ambito.launch { pager.animateScrollToPage(p) } },
                    alPareja = alPareja, alMeta = alMeta, alSprint = alSprint)
                else -> Box(Modifier.fillMaxSize().padding(horizontal = if (tablet) 40.dp else 20.dp).navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 960.dp)) { PaginaObras(d.estado()!!, e, alAbrir, alNueva) {
                        importar.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream"))
                    } }
                }
            }
        }
    }
}

/** Las pestañas de las páginas (dirección C): la elegida, en un bloque del acento; su número al lado. Si no caben, se deslizan y la elegida siempre se ve. */
@Composable
private fun PestanasPaginas(nombres: List<String>, sel: Int, cuentas: List<Int?>, onSel: (Int) -> Unit) {
    val scroll = rememberScrollState()
    val sitios = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    var anchoFila by remember { mutableIntStateOf(0) }
    LaunchedEffect(sel, sitios[sel] != null, anchoFila) {
        val (x, w) = sitios[sel] ?: return@LaunchedEffect
        if (anchoFila <= 0) return@LaunchedEffect
        if (x < scroll.value || x + w > scroll.value + anchoFila) scroll.animateScrollTo((x + w / 2 - anchoFila / 2).coerceIn(0, scroll.maxValue))
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().onGloballyPositioned { anchoFila = it.size.width }.drawWithContent {
                drawContent()
                val f = 28.dp.toPx()
                if (scroll.value > 0) drawRect(Brush.horizontalGradient(listOf(Paper, Color.Transparent), 0f, f), Offset.Zero, Size(f, size.height))
                if (scroll.value < scroll.maxValue) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Paper), size.width - f, size.width), Offset(size.width - f, 0f), Size(f, size.height))
            }.horizontalScroll(scroll).height(IntrinsicSize.Max), verticalAlignment = Alignment.CenterVertically) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Row(Modifier.fillMaxHeight().onGloballyPositioned { c -> sitios[i] = c.positionInParent().x.roundToInt() to c.size.width }.clickable { onSel(i) }
                    .background(if (on) Acento else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.uppercase(), color = if (on) SobreAcento else Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)
                    cuentas.getOrNull(i)?.let { c -> Text(" $c", color = if (on) SobreAcento else Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1) }
                }
            }
        }
        Filete(2.dp)
    }
}
