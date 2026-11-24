package com.gonzalo.familia

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Los colores y las letras del visor: los de la app que lo abre (cada una pone los suyos en [VisorMapas.estilo]). */
class EstiloVisor(
    val fondo: Color = Color(0xFF171D29),
    val tinta: Color = Color(0xFFF2EEDC),
    val gris: Color = Color(0xFFA7A291),
    val acento: Color = Color(0xFFD9773F),
    val sobreAcento: Color = Color(0xFF171D29),
    val panel: Color = Color(0xFF222A39),
    val titulo: FontFamily = FontFamily.SansSerif,
    val cuerpo: FontFamily = FontFamily.SansSerif,
)

/**
 * El visor de los mapas de ATLAS en las apps de la familia: a pantalla completa y **solo para mirar** (nada se edita). Se abre con
 * [abre]; la app pone su aspecto en [estilo] al arrancar.
 */
object VisorMapas {
    /** El aspecto de la app (se lee al abrir, así sigue a la Apariencia elegida). */
    @Volatile var estilo: () -> EstiloVisor = { EstiloVisor() }

    /** Abre el visor con los mapas de [obra] ([tituloObra] sale arriba), en [mapa] (o el primero) y, si se da, centrado en [lugar]. */
    fun abre(ctx: Context, obra: String, tituloObra: String, mapa: String? = null, lugar: String? = null) {
        ctx.startActivity(Intent(ctx, VisorMapasActivity::class.java).apply {
            putExtra("obra", obra); putExtra("titulo", tituloObra); putExtra("mapa", mapa); putExtra("lugar", lugar)
            if (ctx !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /**
     * Un recorte de la lámina alrededor de [l] (de [ancho]×[alto] px, a media resolución: se ve una zona el doble de grande), con
     * el lugar donde cae dentro del recorte (0..1). Usa la lámina guardada; si no hay, la baja (bloquea). null si no hay lámina.
     */
    fun recorte(ctx: Context, m: MapaAtlas, l: LugarMapa, ancho: Int, alto: Int): Pair<android.graphics.Bitmap, Offset>? = runCatching {
        val f = MapasAtlas.laminaGuardada(ctx, m) ?: MapasAtlas.lamina(ctx, m) ?: return null
        @Suppress("DEPRECATION") val dec = android.graphics.BitmapRegionDecoder.newInstance(f.path, false) ?: return null
        try {
            val w = dec.width; val h = dec.height
            val rw = min(w, ancho * 2); val rh = min(h, alto * 2)
            val cx = (l.x * w).toInt(); val cy = (l.y * h).toInt()
            val x0 = (cx - rw / 2).coerceIn(0, w - rw); val y0 = (cy - rh / 2).coerceIn(0, h - rh)
            val b = dec.decodeRegion(android.graphics.Rect(x0, y0, x0 + rw, y0 + rh), BitmapFactory.Options().apply { inSampleSize = 2 }) ?: return null
            b to Offset((cx - x0).toFloat() / rw, (cy - y0).toFloat() / rh)
        } finally { dec.recycle() }
    }.getOrNull()

    /** El nombre de lo que es el mapa (campo `k`), para las fichas: «UN CONTINENTE»… ("" si no se sabe). */
    fun queEs(m: MapaAtlas): String = when (m.tipo) {
        "mundo" -> "EL MUNDO"; "continente" -> "UN CONTINENTE"; "region" -> "UNA REGIÓN"; "ciudad" -> "UNA CIUDAD"; "lugar" -> "UN LUGAR"; else -> ""
    }

    /** «UN CONTINENTE · 34 LUGARES», lo que cabe. */
    fun resumen(m: MapaAtlas): String = listOf(queEs(m), when (m.lugares.size) { 0 -> ""; 1 -> "1 LUGAR"; else -> "${m.lugares.size} LUGARES" })
        .filter { it.isNotEmpty() }.joinToString(" · ")
}

/** Los mapas de [obra], que se ponen al día solos cuando llega algo de ATLAS (lee [LaFamilia.cambios]). */
@Composable
fun mapasDeLaObra(obra: String): List<MapaAtlas> {
    val c = LaFamilia.cambios
    return remember(obra, c) { MapasAtlas.deLaObra(obra) }
}

class VisorMapasActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { LaFamilia.inicia(applicationContext) }
        val obra = intent.getStringExtra("obra").orEmpty()
        val titulo = intent.getStringExtra("titulo").orEmpty()
        val mapa = intent.getStringExtra("mapa")
        val lugar = intent.getStringExtra("lugar")
        val e = VisorMapas.estilo()
        window.decorView.setBackgroundColor(android.graphics.Color.argb((e.fondo.alpha * 255).toInt(), (e.fondo.red * 255).toInt(), (e.fondo.green * 255).toInt(), (e.fondo.blue * 255).toInt()))
        setContent {
            val mapas = mapasDeLaObra(obra)
            PantallaVisor(titulo, mapas, mapa, lugar, e) { finish() }
        }
    }
}

/** Lo que se ve de la lámina: cuánto se ha acercado ([escala], 1 = cabe entera) y cuánto se ha movido desde el centro. */
private class Vista {
    var escala by mutableFloatStateOf(1f)
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    fun reinicia() { escala = 1f; x = 0f; y = 0f }
}

const val ESCALA_MAXIMA = 6f

@Composable
fun PantallaVisor(tituloObra: String, mapas: List<MapaAtlas>, mapaInicial: String?, lugarInicial: String?, e: EstiloVisor, alCerrar: () -> Unit) {
    BackHandler(onBack = alCerrar)
    var cual by remember { mutableIntStateOf(max(0, mapas.indexOfFirst { it.id == mapaInicial })) }
    var buscando by remember { mutableStateOf(false) }
    var elegido by remember { mutableStateOf(lugarInicial?.let { n -> mapas.getOrNull(cual)?.lugares?.firstOrNull { MapasAtlas.plano(it.nombre) == MapasAtlas.plano(n) } }) }
    val etiqueta = TextStyle(fontFamily = e.cuerpo, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)

    Column(Modifier.fillMaxSize().background(e.fondo).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto("← VOLVER", etiqueta.copy(color = e.acento), Modifier.clickable(onClick = alCerrar).padding(vertical = 14.dp))
            Spacer(Modifier.weight(1f))
            Texto(tituloObra.uppercase(), etiqueta.copy(color = e.gris, fontWeight = FontWeight.Bold, fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp))
        }
        val m = mapas.getOrNull(cual)
        if (m == null) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Texto("Sin mapas", TextStyle(color = e.tinta, fontFamily = e.titulo, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp))
                Texto("Esta obra aún no tiene mapas. Se dibujan en ATLAS y se ligan a la obra desde allí.", TextStyle(color = e.gris, fontFamily = e.cuerpo, fontSize = 15.sp),
                    Modifier.padding(top = 10.dp))
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            Texto(m.nombre, TextStyle(color = e.tinta, fontFamily = e.titulo, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 40.sp), maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (mapas.size > 1) Texto("MAPA ${cual + 1} DE ${mapas.size}", etiqueta.copy(color = e.gris, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                Modifier.padding(start = 10.dp, bottom = 7.dp))
        }
        // Pestañas (si hay varios mapas) y BUSCAR
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                if (mapas.size > 1) mapas.forEachIndexed { i, x ->
                    Column(Modifier.width(IntrinsicSize.Max).clickable { cual = i; elegido = null; buscando = false }) {
                        Texto(x.nombre.uppercase(), etiqueta.copy(color = if (i == cual) e.tinta else e.gris, fontSize = 12.sp), maxLines = 1, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).background(if (i == cual) e.acento else Color.Transparent))
                    }
                }
            }
            if (m.lugares.isNotEmpty()) Texto(if (buscando) "CERRAR" else "BUSCAR", etiqueta.copy(color = e.acento, fontSize = 12.sp),
                Modifier.clickable { buscando = !buscando }.padding(start = 16.dp, top = 10.dp, bottom = 12.dp))
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(e.tinta))
        if (buscando) Buscador(m, e) { l -> elegido = l; buscando = false }
        Lamina(m, elegido, e, Modifier.weight(1f).fillMaxWidth().padding(top = if (buscando) 0.dp else 14.dp))
    }
}

@Composable
private fun Buscador(m: MapaAtlas, e: EstiloVisor, alElegir: (LugarMapa) -> Unit) {
    var q by remember { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    val hallados = remember(q, m.id) {
        val p = MapasAtlas.plano(q.trim())
        (if (p.isEmpty()) m.lugares else m.lugares.filter { MapasAtlas.plano(it.nombre).contains(p) })
            .sortedWith(compareBy({ !MapasAtlas.plano(it.nombre).startsWith(p) }, { it.nombre.lowercase() })).take(6)
    }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
        Box(Modifier.fillMaxWidth().height(48.dp).border(2.dp, e.tinta).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (q.isEmpty()) Texto("Un lugar del mapa", TextStyle(color = e.gris, fontFamily = e.cuerpo, fontSize = 15.sp))
            BasicTextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(foco).semantics { contentDescription = "Buscar un lugar del mapa" }, singleLine = true,
                textStyle = TextStyle(color = e.tinta, fontFamily = e.cuerpo, fontSize = 15.sp), cursorBrush = SolidColor(e.acento),
                keyboardOptions = KeyboardOptions.Default)
        }
        Column(Modifier.fillMaxWidth().background(e.panel)) {
            if (hallados.isEmpty()) Texto("Ningún lugar se llama así.", TextStyle(color = e.gris, fontFamily = e.cuerpo, fontSize = 14.sp), Modifier.padding(12.dp))
            hallados.forEach { l ->
                Row(Modifier.fillMaxWidth().clickable { alElegir(l) }.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.Bottom) {
                    Texto(l.nombre, TextStyle(color = e.tinta, fontFamily = e.cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (l.tipo.isNotBlank()) Texto(l.tipo.uppercase(), TextStyle(color = e.acento, fontFamily = e.cuerpo, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                        letterSpacing = 1.sp), Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

/** La lámina con sus gestos (pellizcar, arrastrar, dos toques) y el lugar elegido marcado. Sin lámina, la miniatura y un aviso. */
@Composable
private fun Lamina(m: MapaAtlas, elegido: LugarMapa?, e: EstiloVisor, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var imagen by remember(m.id) { mutableStateOf<ImageBitmap?>(null) }
    var estado by remember(m.id) { mutableStateOf("") }   // "", "bajando", "vieja", "sin"
    LaunchedEffect(m.id, m.versionLamina) {
        if (m.lamina == null) {
            estado = "sin"
            imagen = withContext(Dispatchers.IO) { MapasAtlas.portada(m)?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } }
            return@LaunchedEffect
        }
        // Primero lo guardado (se ve al momento y sin conexión); luego, si hay una lámina nueva, se baja
        val guardada = withContext(Dispatchers.IO) { MapasAtlas.laminaGuardada(ctx, m)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
        if (guardada != null) imagen = guardada
        if (MapasAtlas.laminaAlDia(ctx, m)) { estado = ""; return@LaunchedEffect }
        estado = "bajando"
        val f = withContext(Dispatchers.IO) { MapasAtlas.lamina(ctx, m) }
        if (f != null && MapasAtlas.laminaAlDia(ctx, m)) {
            imagen = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.path)?.asImageBitmap() } ?: imagen
            estado = ""
        } else {
            estado = if (imagen != null) "vieja" else "error"
        }
    }

    val vista = remember(m.id) { Vista() }
    val nota = TextStyle(color = e.tinta, fontFamily = e.cuerpo, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
    BoxWithConstraints(modifier.clipToBounds().background(Color(0xFFEFE4C4))) {
        val bw = constraints.maxWidth.toFloat(); val bh = constraints.maxHeight.toFloat()
        val img = imagen
        // El tamaño de la imagen cuando cabe entera
        val iw = img?.width?.toFloat() ?: 1f; val ih = img?.height?.toFloat() ?: 1f
        val k = min(bw / iw, bh / ih)
        val dw = iw * k; val dh = ih * k
        fun limita() {
            vista.escala = vista.escala.coerceIn(1f, ESCALA_MAXIMA)
            val mx = max(0f, (dw * vista.escala - bw) / 2f); val my = max(0f, (dh * vista.escala - bh) / 2f)
            vista.x = vista.x.coerceIn(-mx, mx); vista.y = vista.y.coerceIn(-my, my)
        }
        // Centrar en el lugar elegido (acercando a ×3)
        LaunchedEffect(elegido, img) {
            val l = elegido ?: return@LaunchedEffect
            if (img == null || m.lamina == null) return@LaunchedEffect
            vista.escala = max(vista.escala, 3f)
            vista.x = -(l.x - 0.5f) * dw * vista.escala; vista.y = -(l.y - 0.5f) * dh * vista.escala
            limita()
        }
        if (img != null) {
            Image(img, if (m.lamina == null) "La miniatura de ${m.nombre}" else "El mapa de ${m.nombre}", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(m.id, dw, dh) {
                        detectTransformGestures { centro, pan, zoom, _ ->
                            val antes = vista.escala
                            val despues = (antes * zoom).coerceIn(1f, ESCALA_MAXIMA)
                            val z = despues / antes
                            val rx = centro.x - bw / 2f; val ry = centro.y - bh / 2f
                            vista.x = rx + pan.x - (rx - vista.x) * z
                            vista.y = ry + pan.y - (ry - vista.y) * z
                            vista.escala = despues
                            limita()
                        }
                    }
                    .pointerInput(m.id, dw, dh) {
                        detectTapGestures(onDoubleTap = { p ->
                            if (vista.escala > 1.2f) vista.reinicia() else {
                                val rx = p.x - bw / 2f; val ry = p.y - bh / 2f
                                val z = 2.5f / vista.escala
                                vista.x = rx - (rx - vista.x) * z; vista.y = ry - (ry - vista.y) * z
                                vista.escala = 2.5f
                                limita()
                            }
                        })
                    }
                    .graphicsLayer { scaleX = vista.escala; scaleY = vista.escala; translationX = vista.x; translationY = vista.y })
            val l = elegido
            if (l != null && m.lamina != null) {
                val anillo = with(LocalDensity.current) { 26.dp.toPx() }
                val punto = with(LocalDensity.current) { 6.dp.toPx() }
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(bw / 2f + (l.x - 0.5f) * dw * vista.escala + vista.x, bh / 2f + (l.y - 0.5f) * dh * vista.escala + vista.y)
                    drawCircle(e.acento, anillo, c, style = Stroke(width = punto / 2f))
                    drawCircle(Color(0xFFF2EEDC), punto + 2f, c)
                    drawCircle(Color(0xFF8E2C1F), punto, c)
                }
            }
        } else if (estado != "bajando") {
            Texto(if (estado == "sin") "Este mapa aún no tiene miniatura." else "No se ha podido bajar el mapa (sin conexión o sin Google Drive en la cuenta).",
                TextStyle(color = Color(0xFF2A2418), fontFamily = e.cuerpo, fontSize = 15.sp), Modifier.align(Alignment.Center).padding(24.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (estado == "sin") Texto("ABRE EL MAPA EN ATLAS Y SÚBELO PARA VER LA LÁMINA", nota, Modifier.background(e.fondo).padding(horizontal = 10.dp, vertical = 8.dp))
            if (estado == "bajando") Texto("BAJANDO EL MAPA…", nota, Modifier.background(e.fondo).padding(horizontal = 10.dp, vertical = 8.dp))
            if (estado == "vieja") Texto("SIN CONEXIÓN · VERSIÓN GUARDADA", nota.copy(color = e.gris), Modifier.background(e.fondo).padding(horizontal = 10.dp, vertical = 8.dp))
            if (img != null && m.lamina != null) Texto(
                "×" + String.format(java.util.Locale("es"), "%.1f", vista.escala) + if (vista.escala < 1.05f) " · PELLIZCA O TOCA DOS VECES" else "",
                nota, Modifier.background(e.fondo).padding(horizontal = 10.dp, vertical = 8.dp))
        }
    }
}

/**
 * Las fichas de los mapas de una obra, de dos en dos (la miniatura, el nombre, «UN CONTINENTE · 34 LUGARES» y VER EL MAPA →), para la
 * sección MAPAS de cada app. El encabezado (numerado, con su estilo) lo pone la app.
 */
@Composable
fun FichasDeMapas(mapas: List<MapaAtlas>, e: EstiloVisor, alAbrir: (MapaAtlas) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(e.tinta))
        mapas.chunked(2).forEach { par ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                FichaMapa(par[0], e, Modifier.weight(1f).padding(end = 14.dp)) { alAbrir(par[0]) }
                Box(Modifier.width(2.dp).fillMaxHeight().background(if (par.size > 1) e.tinta else Color.Transparent))
                if (par.size > 1) FichaMapa(par[1], e, Modifier.weight(1f).padding(start = 16.dp)) { alAbrir(par[1]) } else Spacer(Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(e.tinta))
        }
    }
}

@Composable
private fun FichaMapa(m: MapaAtlas, e: EstiloVisor, modifier: Modifier, alTocar: () -> Unit) {
    var mini by remember(m.portada) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(m.portada) { mini = withContext(Dispatchers.IO) { MapasAtlas.portada(m)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } } }
    Column(modifier.clickable(onClick = alTocar).padding(top = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(112.dp).background(Color(0xFFEFE4C4)), contentAlignment = Alignment.Center) {
            mini?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Texto(m.nombre, TextStyle(color = e.tinta, fontFamily = e.titulo, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        val r = VisorMapas.resumen(m)
        if (r.isNotEmpty()) Texto(r, TextStyle(color = e.gris, fontFamily = e.cuerpo, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 14.sp), maxLines = 2)
        Texto("VER EL MAPA →", TextStyle(color = e.acento, fontFamily = e.cuerpo, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.2.sp))
    }
}

/** Un texto con su estilo (en este orden: texto, estilo, modifier), sin Material. */
@Composable
private fun Texto(texto: String, estilo: TextStyle, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip) =
    BasicText(texto, modifier, estilo, maxLines = maxLines, overflow = overflow)
