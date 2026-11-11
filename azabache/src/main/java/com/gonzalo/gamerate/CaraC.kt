package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * La dirección C («editorial moderna»), la misma de Seshat, Bastet, Hathor, Nut y Thot: la grotesca (Bricolage con opsz 48), el acento
 * legible, los nombres con punto («JONSU.»), los bloques de cifras, el botón recto de ancho completo, el paspartú de las portadas y las
 * pestañas en bloque. Aquí solo las piezas; las pantallas las usan.
 */

/** Los colores de Jonsu de serie (los del icono; «Ciruela» desde noviembre de 2026): fondo, texto y acento. Se usan si no has elegido otros en Apariencia. */
const val JONSU_FONDO = 0xFF2B2529L
const val JONSU_TINTA = 0xFFEAE2DDL
const val JONSU_ACENTO = 0xFF9C7B86L

/** La grotesca de la dirección C: Bricolage Grotesque con el tamaño óptico de titular (48) y el ancho normal. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Grotesca = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        androidx.compose.ui.text.font.Font(R.font.bricolage, FontWeight(w), variationSettings = FontVariation.Settings(
            FontVariation.weight(w), FontVariation.width(100f), FontVariation.Setting("opsz", 48f)))
    }
)

/** El acento tal cual lo eliges (el granate de Jonsu de serie): para los bloques de color y el icono. */
val AcentoBloque: Color get() = Color(Skin.tones.general)

/** Contraste WCAG entre dos colores (1 a 21). */
fun contrasteWcag(a: Color, b: Color): Float {
    val la = a.luminance(); val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

private var cacheLegible = Triple(0L, 0L, 0L) to Color.Unspecified
/** [c] mezclado poco a poco con [hacia] (la tinta) hasta leerse sobre [fondo] con [minimo] (AA: 4,5:1). Si ya se lee, el mismo. */
fun acentoLegible(c: Color, fondo: Color, hacia: Color, minimo: Float = 4.5f): Color {
    val k = Triple(c.value.toLong(), fondo.value.toLong(), hacia.value.toLong())
    val cc = cacheLegible
    if (cc.first == k && minimo == 4.5f) return cc.second
    var x = c; var t = 0f
    while (contrasteWcag(x, fondo) < minimo && t < 1f) { t += 0.04f; x = androidx.compose.ui.graphics.lerp(c, hacia, t.coerceAtMost(1f)) }
    if (minimo == 4.5f) cacheLegible = k to x
    return x
}

/**
 * El acento legible sobre la página: el elegido, aclarado lo justo para leerse con 4,5:1 (el malva de Jonsu, #9C7B86, sobre su gris
 * ciruela #2B2529 se queda corto): en letras, cifras, la pestaña elegida y el botón principal se aclara; en bloques e icono, tal cual.
 */
val Acento: Color get() = acentoLegible(AcentoBloque, Paper, Charcoal)
/** El acento sobre la tinta (la cabecera crema con letra oscura): ajustado para leerse ahí. */
fun acentoSobreTinta(): Color = acentoLegible(AcentoBloque, Charcoal, Paper)
private fun Color.claridad(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
/** Texto sobre un bloque del acento elegido. */
val SobreBloque: Color get() = if (AcentoBloque.claridad() > 0.5f) Color(0xFF14151A) else Color.White
/** Texto sobre el acento legible. */
val SobreAcento: Color get() = if (Acento.claridad() > 0.5f) Color(0xFF14151A) else Color.White

/** El nombre en grotesca enorme con su punto de acento: «JONSU.». */
@Composable
fun NombreConPunto(nombre: String, tam: TextUnit, color: Color = Charcoal, modifier: Modifier = Modifier, punto: Color = Acento) {
    Text(androidx.compose.ui.text.buildAnnotatedString {
        append(nombre.uppercase())
        pushStyle(androidx.compose.ui.text.SpanStyle(color = punto)); append("."); pop()
    }, modifier, color = color, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = tam, lineHeight = tam * 1.0f, letterSpacing = (-0.015f * tam.value).sp, maxLines = 1, softWrap = false,
        style = androidx.compose.ui.text.TextStyle(lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None)))
}

/** Un bloque de la rejilla de cifras: la cifra, su etiqueta y sus colores. */
class BloqueCifra(val cifra: String, val etiqueta: String, val fondo: Color, val letra: Color, val alHacer: (() -> Unit)? = null)

/** La rejilla dura de cifras de la dirección C: bloques de igual ancho con bordes de 2 px. */
@Composable
fun RejillaCifras(bloques: List<BloqueCifra>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).border(2.dp, Charcoal)) {
        bloques.forEachIndexed { i, b ->
            if (i > 0) Box(Modifier.fillMaxHeight().width(2.dp).background(Charcoal))
            Column(Modifier.weight(1f).fillMaxHeight().background(b.fondo).let { m -> if (b.alHacer != null) m.clickable(onClick = b.alHacer) else m }.padding(horizontal = 12.dp, vertical = 12.dp)) {
                FitTitle(b.cifra, 30.sp, 32.sp, color = b.letra, family = Grotesca, fontWeight = FontWeight.ExtraBold, maxLines = 1, unaLinea = true, minSize = 14f, letterSpacing = 0.sp, conEdicion = false)
                Text(b.etiqueta.uppercase(), color = b.letra.copy(alpha = 0.85f), fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** El botón recto de ancho completo: texto a la izquierda y flecha a la derecha («ABRIR EN BASTET →»). */
@Composable
fun BotonC(texto: String, modifier: Modifier = Modifier, fondo: Color = Charcoal, letra: Color = Paper, enabled: Boolean = true, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 54.dp).background(if (enabled) fondo else fondo.copy(alpha = 0.35f)).semantics { role = Role.Button }
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto.uppercase(), color = letra, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = 1.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text("→", color = letra, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

/** Un título de sección numerado: «01» en acento y el título en grotesca. */
@Composable
fun TituloNumerado(n: Int, titulo: String, modifier: Modifier = Modifier, extra: String? = null, alExtra: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 26.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text("%02d".format(n), color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = 0.sp, modifier = Modifier.padding(end = 10.dp))
        Text(titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 23.sp, modifier = Modifier.weight(1f))
        if (extra != null && alExtra != null) Text(extra.uppercase(), color = Acento, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1,
            modifier = Modifier.clickable(onClick = alExtra).padding(start = 8.dp, top = 6.dp, bottom = 2.dp))
    }
}

// ───── el paspartú ─────

/**
 * El paspartú es SIEMPRE el mismo, en todas las apps de la familia, de día y de noche, sin depender del tema ni de «A tu manera»: un crema
 * claro fijo ([PASPARTU_CREMA]). Solo si la portada es muy clara (su luminancia media llega a [UMBRAL_PASPARTU]) pasa a un oscuro fijo.
 */
val PASPARTU_CREMA = Color(0xFFF4EBD6)
val PASPARTU_OSCURO = Color(0xFF141210)
const val UMBRAL_PASPARTU = 0.78f
fun paspartuOscuro(lPortada: Float): Boolean = lPortada >= UMBRAL_PASPARTU
fun colorPaspartu(lPortada: Float?): Color = if (lPortada != null && paspartuOscuro(lPortada)) PASPARTU_OSCURO else PASPARTU_CREMA
fun luminancia(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
/** El grosor del paspartú para una tapa de [ancho]: un 5,4 % (8 dp en una de 150), entre 4 y 14 dp. */
fun grosorPaspartu(ancho: Dp): Dp = (ancho * 0.054f).coerceIn(4.dp, 14.dp)

/** La luminancia media (0–1) de una imagen (archivo o internet), leída en pequeño; null mientras no llega o si no hay. */
@Composable
fun luminanciaDe(imagen: String): Float? {
    val ctx = LocalContext.current
    val l by produceState<Float?>(cacheLum[imagen], imagen) {
        if (imagen.isBlank() || cacheLum.containsKey(imagen)) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = coil.Coil.imageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx).data(imagen).allowHardware(false).size(24).build())
                val b = (r.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@runCatching null
                luminancia(Color(android.graphics.Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)))
            }.getOrNull()
        }?.also { cacheLum[imagen] = it }
    }
    return l
}
private val cacheLum = java.util.concurrent.ConcurrentHashMap<String, Float>()

/** Una portada con su paspartú (crema; oscuro si la portada es casi blanca) y una sombra; sin portada, una tapa de su color con el título. */
@Composable
fun PortadaConPaspartu(imagen: String, titulo: String, ancho: Dp, alto: Dp, modifier: Modifier = Modifier, color: Color = AcentoBloque, sombra: Dp = 14.dp, alTocar: (() -> Unit)? = null) {
    val lum = luminanciaDe(imagen)
    val marco = colorPaspartu(lum ?: if (imagen.isBlank()) luminancia(color) else null)
    Box(modifier.size(ancho, alto).shadow(sombra).background(marco).then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier).padding(grosorPaspartu(ancho))) {
        if (imagen.isNotBlank()) AsyncImage(imagen, titulo, Modifier.fillMaxSize().background(Sand), contentScale = ContentScale.Crop, colorFilter = Ediciones.filtro)
        else TapaDeColor(titulo, color, Modifier.fillMaxSize())
    }
}

/** La tapa de algo sin portada: su color con el título en grotesca. */
@Composable
fun TapaDeColor(titulo: String, color: Color, modifier: Modifier = Modifier) {
    val letra = if (color.claridad() > 0.5f) Color(0xFF14151A) else Color(0xFFF4EBD6)
    BoxWithConstraints(modifier.background(color).padding(10.dp), contentAlignment = Alignment.BottomStart) {
        Text(titulo, color = letra, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = (maxWidth.value * 0.15f).coerceIn(10f, 26f).sp,
            lineHeight = (maxWidth.value * 0.17f).coerceIn(12f, 29f).sp, maxLines = 5, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start)
    }
}

/** El velo de arriba de la foto de apertura: para que se lean «← Volver» y los botones sobre cualquier foto. */
fun veloArriba() = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.35f), Color.Transparent))

// ───── las pestañas en bloque ─────

/** Las pestañas de las páginas: la elegida, en un bloque del acento a toda la altura; su número, en acento. Si no caben, se deslizan. */
@Composable
fun PestanasC(nombres: List<String>, sel: Int, cuentas: List<Int?>, onSel: (Int) -> Unit) {
    val scroll = rememberScrollState()
    val sitios = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    var anchoFila by remember { mutableIntStateOf(0) }
    LaunchedEffect(sel, sitios[sel] != null, anchoFila) {
        val (x, w) = sitios[sel] ?: return@LaunchedEffect
        if (anchoFila <= 0) return@LaunchedEffect
        if (x < scroll.value || x + w > scroll.value + anchoFila) scroll.animateScrollTo((x + w / 2 - anchoFila / 2).coerceIn(0, scroll.maxValue))
    }
    val papel = Paper
    Column(Modifier.fillMaxWidth().background(papel)) {
        Row(Modifier.fillMaxWidth().onGloballyPositioned { anchoFila = it.size.width }.drawWithContent {
                drawContent()
                val f = 28.dp.toPx()
                if (scroll.value > 0) drawRect(Brush.horizontalGradient(listOf(papel, Color.Transparent), 0f, f), Offset.Zero, Size(f, size.height))
                if (scroll.value < scroll.maxValue) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, papel), size.width - f, size.width), Offset(size.width - f, 0f), Size(f, size.height))
            }.horizontalScroll(scroll).height(IntrinsicSize.Max), verticalAlignment = Alignment.CenterVertically) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Row(Modifier.fillMaxHeight().onGloballyPositioned { c -> sitios[i] = c.positionInParent().x.roundToInt() to c.size.width }
                    .semantics { selected = on; role = Role.Tab }.clickable { onSel(i) }
                    .background(if (on) Acento else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.uppercase(), color = if (on) SobreAcento else Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)
                    cuentas.getOrNull(i)?.let { c -> Text(" $c", color = if (on) SobreAcento else Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.sp, maxLines = 1) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
    }
}
