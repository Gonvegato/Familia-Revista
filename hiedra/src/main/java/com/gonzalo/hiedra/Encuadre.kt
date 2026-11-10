package com.gonzalo.hiedra

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt

/*
 * Encuadrar una imagen (la portada o el fondo de un juego), como en Seshat: la imagen se guarda ENTERA y aparte se guarda qué parte se ve.
 * El encuadre es el punto de la imagen que queda en el centro del hueco ([cx], [cy], fracciones de su ancho y su alto) y un [zoom] (1 = la
 * imagen justo cubre el hueco). Así el mismo encuadre vale para huecos de otra forma: el fondo se encuadra con la forma del cartel de la
 * portada y la cabecera de la ficha (más baja) enseña una franja centrada en el mismo punto. La imagen siempre cubre el hueco entero
 * (zoom ≥ 1 y el punto se ajusta para que no quede borde vacío).
 */
data class Encuadre(val zoom: Float = 1f, val cx: Float = 0.5f, val cy: Float = 0.5f) {
    val neutro: Boolean get() = zoom == 1f && cx == 0.5f && cy == 0.5f

    /** El mismo encuadre, ajustado para que la imagen ([iw]×[ih]) cubra un hueco de [w]×[h] sin dejar borde. */
    fun ajustado(w: Float, h: Float, iw: Float, ih: Float): Encuadre {
        val z = zoom.coerceIn(ZOOM_MIN, ZOOM_MAX)
        val (dw, dh) = tamanoEncuadre(w, h, iw, ih, z)
        fun lim(c: Float, hueco: Float, d: Float): Float { val m = (hueco / 2f) / d; return if (m >= 0.5f) 0.5f else c.coerceIn(m, 1f - m) }
        return Encuadre(z, lim(cx, w, dw), lim(cy, h, dh))
    }

    companion object {
        const val ZOOM_MIN = 1f
        const val ZOOM_MAX = 6f
    }
}

/**
 * Los encuadres de ESTE dispositivo: cada móvil o tableta encuadra a su manera (tienen otra forma), así que no viajan con la cuenta; las
 * imágenes sí. Van por juego, por imagen (portada o fondo) y por el archivo de la imagen (su nombre es el hash de su contenido): si la
 * imagen cambia, aquí y en los demás dispositivos, su encuadre de antes ya no casa y vuelve a verse centrada.
 */
object Encuadres {
    /** El almacén (en las pruebas, otro nombre hace de otro dispositivo). */
    @Volatile var almacen = "encuadres"

    private fun prefs(c: android.content.Context) = c.getSharedPreferences(almacen, android.content.Context.MODE_PRIVATE)
    fun clave(id: String, fondo: Boolean, ruta: String) = "$id|${if (fondo) "f" else "p"}|${File(ruta).name}"

    fun de(c: android.content.Context, id: String, fondo: Boolean, ruta: String): Encuadre {
        if (ruta.isBlank() || ruta.startsWith("http")) return Encuadre()
        val v = prefs(c).getString(clave(id, fondo, ruta), null)?.split(',')?.mapNotNull { it.toFloatOrNull() } ?: return Encuadre()
        return if (v.size == 3) Encuadre(v[0], v[1], v[2]) else Encuadre()
    }

    /**
     * El encuadre de antes de Bastet 0.8 (zoom y desplazamiento, guardados en la cuenta, iguales en todos los dispositivos) pasa UNA vez a
     * ser el de este dispositivo para esa portada. Después manda el de aquí (y la cuenta ya no se toca).
     */
    fun migrarDeAntes(c: android.content.Context, id: String, ruta: String, zoom: Float, ox: Float, oy: Float) {
        if (ruta.isBlank() || ruta.startsWith("http")) return
        val marca = "antes|$id"
        val p = prefs(c)
        if (p.getBoolean(marca, false)) return
        p.edit().putBoolean(marca, true).apply()
        if (zoom == 1f && ox == 0f && oy == 0f) return
        if (p.contains(clave(id, false, ruta))) return
        val m = medidasFoto(File(ruta)) ?: return
        poner(c, id, false, ruta, encuadreDeAntes(zoom, ox, oy, m.first.toFloat(), m.second.toFloat()))
    }

    fun poner(c: android.content.Context, id: String, fondo: Boolean, ruta: String, e: Encuadre) {
        if (ruta.isBlank() || ruta.startsWith("http")) return
        val k = clave(id, fondo, ruta)
        // (los de imágenes de antes de este juego se olvidan)
        val ed = prefs(c).edit()
        prefs(c).all.keys.filter { it.startsWith("$id|${if (fondo) "f" else "p"}|") && it != k }.forEach { ed.remove(it) }
        if (e.neutro) ed.remove(k) else ed.putString(k, "${e.zoom},${e.cx},${e.cy}")
        ed.apply()
    }
}

/**
 * El encuadre de antes (zoom y desplazamiento [ox]/[oy] como fracción del hueco, en un hueco de proporción [forma], 3:4 entonces) en el
 * de ahora: el punto de la imagen que quedaba en el centro del hueco y el mismo zoom (como poco 1: la imagen cubre siempre el hueco).
 */
fun encuadreDeAntes(zoom: Float, ox: Float, oy: Float, iw: Float, ih: Float, forma: Float = 0.75f): Encuadre {
    if (zoom == 1f && ox == 0f && oy == 0f) return Encuadre()
    val h = 1000f; val w = h * forma
    val (dw, dh) = tamanoEncuadre(w, h, iw, ih, zoom)
    if (dw <= 0f || dh <= 0f) return Encuadre()
    return Encuadre(zoom.coerceIn(Encuadre.ZOOM_MIN, Encuadre.ZOOM_MAX), (0.5f - ox * w / dw).coerceIn(0f, 1f), (0.5f - oy * h / dh).coerceIn(0f, 1f))
}

/** El tamaño (px) con que se dibuja una imagen de [iw]×[ih] para cubrir un hueco de [w]×[h] con ese [zoom]. */
fun tamanoEncuadre(w: Float, h: Float, iw: Float, ih: Float, zoom: Float): Pair<Float, Float> {
    if (iw <= 0f || ih <= 0f) return w * zoom to h * zoom
    val ar = iw / ih
    val base = if (ar > w / h) (h * ar to h) else (w to w / ar)
    return base.first * zoom to base.second * zoom
}

/** Dónde se dibuja la imagen en el hueco: izquierda, arriba, ancho y alto (px), con el encuadre ya ajustado. */
fun colocarEncuadre(w: Float, h: Float, iw: Float, ih: Float, e: Encuadre): FloatArray {
    val a = e.ajustado(w, h, iw, ih)
    val (dw, dh) = tamanoEncuadre(w, h, iw, ih, a.zoom)
    return floatArrayOf(w / 2f - a.cx * dw, h / 2f - a.cy * dh, dw, dh)
}

/** El tamaño de la foto tal como se ve (con su giro EXIF), leyendo solo la cabecera del archivo; null si no se puede. */
fun medidasFoto(f: File): Pair<Int, Int>? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    if (o.outWidth <= 0 || o.outHeight <= 0) return@runCatching null
    val giro = runCatching { android.media.ExifInterface(f.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
    if (giro in setOf(5, 6, 7, 8)) o.outHeight to o.outWidth else o.outWidth to o.outHeight
}.getOrNull()

/** La parte de un bitmap que se ve en un hueco de proporción [forma] (ancho/alto) con ese encuadre: para los widgets, que dibujan bitmaps. */
fun recortarEncuadre(b: Bitmap, e: Encuadre, forma: Float = 2f / 3f): Bitmap {
    if (e.neutro) return b
    val h = 1000f; val w = h * forma
    val (izq, arr, dw, _) = colocarEncuadre(w, h, b.width.toFloat(), b.height.toFloat(), e).let { listOf(it[0], it[1], it[2], it[3]) }
    val esc = b.width / dw
    val x0 = (-izq * esc).roundToInt().coerceIn(0, b.width - 1); val y0 = (-arr * esc).roundToInt().coerceIn(0, b.height - 1)
    val cw = (w * esc).roundToInt().coerceIn(1, b.width - x0); val ch = (h * esc).roundToInt().coerceIn(1, b.height - y0)
    return Bitmap.createBitmap(b, x0, y0, cw, ch)
}

/**
 * Una imagen (archivo de la app o dirección de internet) recortada en su hueco con su encuadre. Sin encuadre, o si es de internet y aún no ha
 * bajado (no se saben sus medidas), recortada al centro como siempre.
 */
@Composable
fun ImagenEncuadrada(ruta: String, e: Encuadre, modifier: Modifier = Modifier, descripcion: String? = null) {
    val f = remember(ruta) { ruta.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { File(it) }?.takeIf { it.exists() } }
    val medidas = remember(f?.path, f?.lastModified()) { f?.let { medidasFoto(it) } }
    if (f == null || medidas == null || e.neutro) {
        AsyncImage(f ?: ruta.takeIf { it.startsWith("http") }, descripcion, modifier, contentScale = ContentScale.Crop)
        return
    }
    val ctx = LocalContext.current
    val lado = if (e.zoom > 1.5f) 3072 else 2048
    val peticion = remember(f.path, f.lastModified(), lado) { coil.request.ImageRequest.Builder(ctx).data(f).size(lado).build() }
    val d = LocalDensity.current
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val (izq, arr, dw, dh) = colocarEncuadre(w, h, medidas.first.toFloat(), medidas.second.toFloat(), e).let { listOf(it[0], it[1], it[2], it[3]) }
        // (centrada en el hueco y movida hasta su sitio)
        AsyncImage(peticion, descripcion, Modifier.requiredSize(with(d) { dw.toDp() }, with(d) { dh.toDp() })
            .offset { IntOffset((izq + dw / 2f - w / 2f).roundToInt(), (arr + dh / 2f - h / 2f).roundToInt()) }, contentScale = ContentScale.FillBounds)
    }
}

/** El alto del cartel «Ahora juegas» de la portada: el fondo se encuadra con esta forma (y el ancho de la pantalla). */
val ALTO_CARTEL: Dp = 470.dp
/** El alto de la cabecera de la ficha (la foto de apertura). */
val ALTO_APERTURA: Dp = 300.dp

/**
 * Encuadrar una imagen a pantalla completa: la ventana tiene la forma en que se verá (2:3 la portada; el fondo, la del cartel de la portada).
 * Arrastra para mover y pellizca (o usa la barra) para acercar. LISTO guarda; RESTABLECER vuelve a la imagen centrada.
 */
@Composable
fun EditorEncuadre(ruta: String, inicial: Encuadre, fondo: Boolean, alGuardar: (Encuadre) -> Unit, alCerrar: () -> Unit) {
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Paper)) { ContenidoEncuadre(ruta, inicial, fondo, alGuardar, alCerrar) }
    }
}

/** Lo de dentro del editor (aparte, para las capturas). */
@Composable
fun ContenidoEncuadre(ruta: String, inicial: Encuadre, fondo: Boolean, alGuardar: (Encuadre) -> Unit, alCerrar: () -> Unit) {
    var e by remember(ruta) { mutableStateOf(inicial) }
    val f = remember(ruta) { File(ruta).takeIf { ruta.isNotBlank() && it.exists() } }
    val medidas = remember(f?.path) { f?.let { medidasFoto(it) } }
    val iw = medidas?.first?.toFloat() ?: 0f; val ih = medidas?.second?.toFloat() ?: 0f
    val colores = SliderDefaults.colors(thumbColor = Acento, activeTrackColor = Acento, inactiveTrackColor = Sand)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
            EnlaceRevista("← Cancelar", Modifier.padding(top = 6.dp), color = Paper.copy(alpha = 0.75f)) { alCerrar() }
            NombreConPunto("Encuadrar", 44.sp, Paper, Modifier.padding(top = 2.dp))
        }
        Text(if (fondo) "EL FONDO · COMO EN «AHORA JUEGAS»" else "LA PORTADA", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = if (fondo) 0.dp else 20.dp), contentAlignment = Alignment.Center) {
            // La ventana, con la forma en que se verá: la portada a 2:3 (no más alta que media pantalla); el fondo, a todo el ancho y el alto del cartel
            val ventana = if (fondo) Modifier.fillMaxWidth().height(ALTO_CARTEL) else Modifier.width(minOf(maxWidth * 0.72f, 260.dp)).aspectRatio(2f / 3f)
            Box(ventana.testTag("encuadre").clipToBounds().background(Sand).then(if (fondo) Modifier else Modifier.border(2.dp, Charcoal))
                .pointerInput(iw, ih) {
                    detectTransformGestures { _, pan, z, _ ->
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        val zoom = (e.zoom * z).coerceIn(Encuadre.ZOOM_MIN, Encuadre.ZOOM_MAX)
                        val (dw, dh) = tamanoEncuadre(w, h, iw, ih, zoom)
                        e = Encuadre(zoom, e.cx - pan.x / dw, e.cy - pan.y / dh).ajustado(w, h, iw, ih)
                    }
                }) {
                if (f != null) ImagenEncuadrada(f.path, e, Modifier.fillMaxSize())
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Zoom", color = Muted, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            Slider(e.zoom, { e = e.copy(zoom = it) }, valueRange = Encuadre.ZOOM_MIN..Encuadre.ZOOM_MAX, colors = colores)
            Text((if (fondo) "Arrastra para mover y pellizca para acercar. Esto es lo que se ve en «Ahora juegas»; arriba en la ficha sale una franja más baja centrada en el mismo punto."
                else "Arrastra para mover y pellizca para acercar. Queda así en todas partes: la ficha, las listas, la portada y los widgets.") +
                " Este encuadre es solo para este dispositivo: la imagen es la misma en todos, y en cada uno la encuadras a tu manera.",
                color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonMarco("Restablecer") { e = Encuadre() }
                Spacer(Modifier.weight(1f))
                Text("LISTO →", color = SobreAcento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
                    modifier = Modifier.background(Acento).tocable { alGuardar(e); alCerrar() }.padding(horizontal = 16.dp, vertical = 11.dp))
            }
        }
    }
}
