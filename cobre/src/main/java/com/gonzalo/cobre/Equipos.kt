package com.gonzalo.cobre

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.PortadasFamilia
import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.iptv.futbol.Equipo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Las imágenes de tus equipos (Nut 0.8.0), como las portadas y los fondos de Seshat, Bastet y Hathor: un ESCUDO propio (si no, el de ESPN)
 * y un FONDO (una imagen a lo ancho para el cartel de la portada y la apertura de las fichas). Las dos viajan con tu cuenta
 * (`cobre_equipos/<id del equipo>`: `esc` y `fimg`, hashes de imágenes de la cuenta); qué parte del fondo se ve ([Encuadre]) es de cada
 * dispositivo. Sin fondo, el color del club (el medio de su escudo).
 */
object ImagenesEquipo {
    const val COLECCION = "cobre_equipos"
    var version by mutableIntStateOf(0)
    /** Para las pruebas y las capturas: id → (escudo, fondo) como archivos, sin cuenta. */
    val fijas = mutableStateMapOf<String, Pair<String, String>>()
    private var oyendo = false

    private fun col(): Coleccion? = runCatching { LaFamilia.coleccion(COLECCION) }.getOrNull()?.also { c ->
        if (!oyendo) { oyendo = true; c.alCambiar { android.os.Handler(android.os.Looper.getMainLooper()).post { version++ } } }
    }

    private fun archivo(c: Context, hash: String): String {
        if (hash.isBlank()) return ""
        val f = PortadasFamilia.archivo(c, hash)
        if (f.isBlank()) runCatching { PortadasFamilia.baja(listOf(hash)) { android.os.Handler(android.os.Looper.getMainLooper()).post { version++ } } }
        return f
    }

    /** El escudo propio de un equipo (un archivo de la app) o "" (va el de ESPN). */
    fun escudo(c: Context, id: String): String = fijas[id]?.first ?: archivo(c, col()?.nodo(id)?.texto("esc").orEmpty())
    /** El fondo de un equipo (un archivo de la app) o "". */
    fun fondo(c: Context, id: String): String = fijas[id]?.second ?: archivo(c, col()?.nodo(id)?.texto("fimg").orEmpty())

    private fun pon(id: String, campo: String, hash: String) {
        if (col() == null) { val (e, f) = fijas[id] ?: ("" to ""); fijas[id] = if (campo == "esc") hash to f else e to hash; return }
        col()?.cambia(id, mapOf(campo to hash)); version++
    }

    /** Un fondo listo para la cuenta: a lo ancho, como mucho 1600×1000 en JPEG. null si no es una imagen. */
    fun reducirFondo(bytes: ByteArray): ByteArray? {
        if (!PortadasFamilia.esImagen(bytes)) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1; while (o.outWidth / (s * 2) >= 1600 || o.outHeight / (s * 2) >= 1000) s *= 2
        val b0 = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val k = minOf(1f, 1600f / b0.width, 1000f / b0.height)
        val b = if (k < 1f) Bitmap.createScaledBitmap(b0, (b0.width * k).toInt().coerceAtLeast(1), (b0.height * k).toInt().coerceAtLeast(1), true) else b0
        return java.io.ByteArrayOutputStream().use { out -> b.compress(Bitmap.CompressFormat.JPEG, 84, out); out.toByteArray() }
    }

    /** Pone un escudo propio (en PNG, para no perder lo transparente; en la cuenta). false si no es una imagen. */
    fun ponerEscudo(id: String, bytes: ByteArray): Boolean {
        if (!PortadasFamilia.esImagen(bytes)) return false
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        val k = minOf(1f, 512f / maxOf(b.width, b.height))
        val r = if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
        val png = java.io.ByteArrayOutputStream().use { out -> r.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        val hash = runCatching { LaFamilia.familia.imagenes.guarda(png, "image/png") }.getOrNull() ?: return false
        pon(id, "esc", hash); return true
    }

    fun ponerFondo(id: String, bytes: ByteArray): Boolean {
        val r = reducirFondo(bytes) ?: return false
        val hash = runCatching { LaFamilia.familia.imagenes.guarda(r, "image/jpeg") }.getOrNull() ?: return false
        pon(id, "fimg", hash); return true
    }

    fun quitarEscudo(id: String) = pon(id, "esc", "")
    fun quitarFondo(id: String) = pon(id, "fimg", "")

    /** La variante oscura del escudo de ESPN (para fondos oscuros), si la dirección es de las suyas. */
    fun escudoOscuroEspn(url: String): String? = url.takeIf { "/teamlogos/" in it && "/500/" in it }?.replace("/500/", "/500-dark/")
}

/** El paspartú: crema fijo; oscuro si la imagen es muy clara (la misma regla que en las hermanas). */
val PASPARTU_CREMA = Color(0xFFF4EBD6)
val PASPARTU_OSCURO = Color(0xFF141210)
const val UMBRAL_PASPARTU = 0.78f
fun paspartuOscuro(l: Float): Boolean = l >= UMBRAL_PASPARTU
fun colorPaspartu(l: Float): Color = if (paspartuOscuro(l)) PASPARTU_OSCURO else PASPARTU_CREMA
fun luminancia(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
fun grosorPaspartu(ancho: Dp): Dp = (ancho * 0.054f).coerceIn(4.dp, 14.dp)
fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)
/** El velo de arriba de las aperturas: negro al 80 % arriba, transparente abajo. */
fun veloArriba() = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.35f), Color.Transparent))

/** El color medio de lo que se ve de una imagen (sin lo transparente: el de un escudo es el del club), o null. */
fun colorMedio(b: Bitmap): Color? {
    val p = Bitmap.createScaledBitmap(b.copy(Bitmap.Config.ARGB_8888, false), 24, 24, true)
    var r = 0L; var g = 0L; var bl = 0L; var n = 0L
    for (y in 0 until 24) for (x in 0 until 24) { val c = p.getPixel(x, y); if ((c ushr 24) < 128) continue; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; bl += c and 0xFF; n++ }
    return if (n == 0L) null else Color((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
}

/** El color de un equipo: el medio de su escudo (el propio o el de ESPN; mientras llega, null). */
@Composable
fun rememberColorEquipo(e: Equipo): Color? {
    val ctx = LocalContext.current
    ImagenesEquipo.version
    val propio = ImagenesEquipo.escudo(ctx, e.id)
    val r by produceState<Color?>(null, propio, e.escudo) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (propio.isNotBlank()) BitmapFactory.decodeFile(propio)?.let { colorMedio(it) }
                else if (e.escudo.isNotBlank()) {
                    val res = coil.Coil.imageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx).data(e.escudo).allowHardware(false).size(96).build())
                    ((res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap)?.let { colorMedio(it) }
                } else null
            }.getOrNull()
        }
    }
    return r
}

/** El escudo de un equipo: el tuyo (si pusiste uno) o el de ESPN (sin escudo, sus siglas). */
@Composable
fun EscudoEquipo(e: Equipo, tam: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    ImagenesEquipo.version
    val propio = ImagenesEquipo.escudo(ctx, e.id)
    if (propio.isNotBlank()) AsyncImage(File(propio), e.nombre, modifier.size(tam), contentScale = ContentScale.Fit)
    else Escudo(e, tam, modifier)
}

/**
 * El escudo sobre una imagen o un color (el cartel, la apertura de las fichas), en un PASPARTÚ cuadrado: el crema fijo de la familia, o el
 * oscuro si el escudo es muy claro ([UMBRAL_PASPARTU]).
 */
@Composable
fun EscudoConPaspartu(e: Equipo, tam: Dp, modifier: Modifier = Modifier, sombra: Dp = 10.dp) {
    val c = rememberColorEquipo(e)
    val marco = colorPaspartu(c?.let { luminancia(it) } ?: 0f)
    Box(modifier.size(tam).shadow(sombra).background(marco).padding(grosorPaspartu(tam) + tam * 0.08f), contentAlignment = Alignment.Center) {
        EscudoEquipo(e, tam - (grosorPaspartu(tam) + tam * 0.08f) * 2)
    }
}

/** Detrás de un equipo (cartel, apertura): su fondo encuadrado en este dispositivo o, sin fondo, el color de su club oscurecido. */
@Composable
fun FondoEquipo(e: Equipo?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    ImagenesEquipo.version
    val ruta = e?.let { ImagenesEquipo.fondo(ctx, it.id) }.orEmpty()
    val club = e?.let { rememberColorEquipo(it) }
    Box(modifier.clipToBounds().background(club?.let { lerpColor(it, Color.Black, 0.35f) } ?: Bloque2)) {
        if (ruta.isNotBlank() && e != null) ImagenEncuadrada(ruta, Encuadres.de(ctx, "eq" + e.id, true, ruta), Modifier.matchParentSize(), "Fondo de ${e.nombre}")
    }
}

/** Un botón recto con marco de 2 px (los de la dirección C que no son la acción principal). */
@Composable
fun BotonMarco(texto: String, modifier: Modifier = Modifier, color: Color = Charcoal, onClick: () -> Unit) {
    Text(texto.uppercase(), color = color, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp, maxLines = 1,
        modifier = modifier.border(2.dp, color).tocable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp))
}

/**
 * La hoja «IMÁGENES.» de un equipo: «El escudo» (el tuyo, de tu móvil, o las propuestas de ESPN) y «El fondo» (de tu móvil; encuadrar;
 * quitar). Las imágenes van con tu cuenta; el encuadre, solo en este dispositivo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoImagenesEquipo(e: Equipo, alCerrar: () -> Unit, fondoInicial: Boolean = false) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    ImagenesEquipo.version
    var esFondo by remember { mutableStateOf(fondoInicial) }
    var guardando by remember { mutableStateOf(false) }
    var encuadrar by remember { mutableStateOf(false) }
    var enc by remember { mutableIntStateOf(0) }
    fun dice(x: String) = Toast.makeText(ctx, x, Toast.LENGTH_LONG).show()
    suspend fun guardar(bytes: ByteArray?) {
        val ok = bytes != null && withContext(Dispatchers.IO) { if (esFondo) ImagenesEquipo.ponerFondo(e.id, bytes) else ImagenesEquipo.ponerEscudo(e.id, bytes) }
        guardando = false
        if (!ok) dice(if (esFondo) "Esa imagen no vale como fondo." else "Esa imagen no vale como escudo.")
    }
    fun deUrl(url: String) { guardando = true; ambito.launch { guardar(withContext(Dispatchers.IO) { runCatching { java.net.URL(url).readBytes() }.getOrNull() }) } }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) { guardando = true; ambito.launch { guardar(withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() }) } }
    }
    val fondo = ImagenesEquipo.fondo(ctx, e.id)
    val escudo = ImagenesEquipo.escudo(ctx, e.id)
    enc
    if (encuadrar && fondo.isNotBlank()) EditorEncuadre(fondo, Encuadres.de(ctx, "eq" + e.id, true, fondo), true,
        alGuardar = { x -> Encuadres.poner(ctx, "eq" + e.id, true, fondo, x); enc++; ImagenesEquipo.version++ }, alCerrar = { encuadrar = false })
    Column(Modifier.fillMaxSize().background(Paper).atrapaFoco().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
            EnlaceRevista("← " + e.nombre, Modifier.padding(top = 6.dp), color = Paper.copy(alpha = 0.75f)) { alCerrar() }
            NombreConPunto("Imágenes", 44.sp, Paper, Modifier.padding(top = 2.dp))
        }
        PestanasRevista(listOf("El escudo", "El fondo"), if (esFondo) 1 else 0) { esFondo = it == 1 }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            if (esFondo) {
                Box(Modifier.fillMaxWidth().aspectRatio(2.2f).tocable { galeria.launch("image/*") }, contentAlignment = Alignment.Center) {
                    FondoEquipo(e, Modifier.matchParentSize())
                    if (fondo.isBlank()) Text("Sin fondo: el color del club", color = Color.White.copy(alpha = 0.85f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                }
            } else {
                Box(Modifier.size(150.dp).background(Sand).tocable { galeria.launch("image/*") }, contentAlignment = Alignment.Center) { EscudoConPaspartu(e, 130.dp, sombra = 0.dp) }
                Text(if (escudo.isNotBlank()) "EL TUYO" else "EL DE ESPN (AUTOMÁTICO)", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (esFondo && fondo.isNotBlank()) BotonMarco("Encuadrar") { encuadrar = true }
                BotonMarco("De tu móvil") { galeria.launch("image/*") }
                if (!esFondo && escudo.isNotBlank()) BotonMarco("El de ESPN") { ImagenesEquipo.quitarEscudo(e.id) }
                if (esFondo && fondo.isNotBlank()) BotonMarco("Quitar") { ImagenesEquipo.quitarFondo(e.id) }
            }
            if (guardando) Text(if (esFondo) "Guardando el fondo…" else "Guardando el escudo…", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            Text(if (esFondo) "Va detrás del cartel de la portada y arriba en las fichas del equipo y de sus partidos en casa. Se recorta a lo ancho: con «Encuadrar» eliges qué parte se ve (solo en este dispositivo). Sin fondo, el color del club."
                else "Sale en el cartel, en las fichas y en «Tus equipos», con su paspartú. Va con tu cuenta: igual en tus otros dispositivos. «El de ESPN» vuelve al automático.",
                color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp))
            Text("PROPUESTAS · ESPN", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            if (esFondo) Text("ESPN no da fondos de los equipos: elige uno de tu móvil (una foto del estadio, de la afición…).", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
            else {
                val props = listOfNotNull(e.escudo.ifBlank { null }?.let { "El de siempre" to it }, ImagenesEquipo.escudoOscuroEspn(e.escudo)?.let { "Para fondo oscuro" to it })
                if (props.isEmpty()) Text("ESPN no tiene escudo de este equipo.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    props.forEachIndexed { i, (t, url) ->
                        Column(Modifier.width(120.dp).tocable(enabled = !guardando) { deUrl(url) }) {
                            Box(Modifier.size(120.dp).background(if (i == 0) PASPARTU_CREMA else PASPARTU_OSCURO).padding(16.dp)) { AsyncImage(url, t, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                            Text(t.uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
