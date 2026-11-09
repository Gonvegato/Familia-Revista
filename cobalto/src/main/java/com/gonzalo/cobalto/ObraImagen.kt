package com.gonzalo.cobalto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.PortadasFamilia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Las imágenes de una obra: su portada (2:3) y su fondo (a lo ancho). Se guardan en la cuenta de la familia por su contenido
 * (`familia_img/<hash>`, como las portadas de Seshat y Bastet) y la obra apunta a ellas con dos campos de su documento: [PORTADA] y
 * [FONDO]. Atrio del PC no los mira (no le molestan). Sin portada, la obra tiene una tapa de su color con el título; sin fondo, el cartel
 * y la apertura de la ficha usan el color de la portada. Qué parte se ve de cada una se encuadra en cada dispositivo ([Encuadres]).
 */
object ImagenesObra {
    const val PORTADA = "pt"
    const val FONDO = "fd"

    /** Sube cuando llega una imagen o cambia un encuadre: las portadas se vuelven a pintar. */
    var version by mutableIntStateOf(0)
        private set
    fun avisa() { version++ }

    fun hashPortada(o: Doc): String = o.s(PORTADA)
    fun hashFondo(o: Doc): String = o.s(FONDO)
    /** El archivo de la portada en este dispositivo ("" si no tiene o aún no ha llegado). */
    fun portada(c: Context, o: Doc): String = PortadasFamilia.archivo(c, hashPortada(o))
    fun fondo(c: Context, o: Doc): String = PortadasFamilia.archivo(c, hashFondo(o))
    fun encPortada(c: Context, o: Doc): Encuadre = Encuadres.de(c, o.id, false, portada(c, o))
    fun encFondo(c: Context, o: Doc): Encuadre = Encuadres.de(c, o.id, true, fondo(c, o))

    /** Un fondo listo para la cuenta: a lo ancho, como mucho 1600×1000 en JPEG; se recorta al enseñarlo. null si no es una imagen. */
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

    /** Pone una imagen como portada (en la cuenta: sale igual en tus otros dispositivos). false si no es una imagen. */
    fun ponerPortada(e: Escritos, id: String, bytes: ByteArray): Boolean {
        val h = PortadasFamilia.guardar(bytes) ?: return false
        e.edita(id) { put(PORTADA, h) }; avisa()
        return true
    }

    fun ponerFondo(e: Escritos, id: String, bytes: ByteArray): Boolean {
        val r = reducirFondo(bytes) ?: return false
        val h = LaFamilia.familia.imagenes.guarda(r, "image/jpeg")
        e.edita(id) { put(FONDO, h) }; avisa()
        return true
    }

    /** Usa la portada como fondo (la misma imagen, recortada a lo ancho). */
    fun portadaComoFondo(c: Context, e: Escritos, o: Doc): Boolean {
        val f = File(portada(c, o)).takeIf { it.isFile } ?: return false
        return ponerFondo(e, o.id, f.readBytes())
    }

    /** Quita la portada (vuelve la tapa de su color) o el fondo (vuelve el color de la portada), también en tus otros dispositivos. */
    fun quitar(e: Escritos, id: String, fondo: Boolean) { e.edita(id) { put(if (fondo) FONDO else PORTADA, "") }; avisa() }

    /** El encuadre de este dispositivo para la portada o el fondo de una obra. */
    fun ponerEncuadre(c: Context, o: Doc, fondo: Boolean, en: Encuadre) {
        Encuadres.poner(c, o.id, fondo, if (fondo) fondo(c, o) else portada(c, o), en); avisa()
    }

    /** Las imágenes de la cuenta que aún no están en este dispositivo se bajan en segundo plano (y las portadas se pintan al llegar). */
    fun bajarQueFalten(c: Context, e: Escritos) {
        val faltan = e.obras().flatMap { o -> listOf(hashPortada(o), hashFondo(o)) }.filter { it.isNotBlank() && PortadasFamilia.archivo(c, it).isBlank() }
        if (faltan.isEmpty()) return
        runCatching { PortadasFamilia.baja(faltan) { avisa() } }
    }
}

/** Los colores de tela de las obras sin color ni portada: uno por título, siempre el mismo. */
private val TELAS = listOf(0xFF7A2E2E, 0xFF2F4A6B, 0xFF35523F, 0xFF5B3F6B, 0xFF8A5A2B, 0xFF3F5E63, 0xFF6B6B3A, 0xFF4A4A52).map { Color(it) }
internal val PAPEL_CLARO = Color(0xFFF3EDE0)

/** El color de tapa de una obra: el suyo (el que eliges al crearla) o, si no tiene, uno por su título. */
fun telaDe(o: Doc): Color = o.s("color").takeIf { it.isNotBlank() }?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    ?: TELAS[(norm(o.s("titulo")).hashCode() and 0x7fffffff) % TELAS.size]

internal fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** El color medio de una imagen (para el cartel y la apertura de la ficha), o null si no hay imagen o no se puede leer. */
fun colorDeImagen(ruta: String): Color? = runCatching {
    val f = File(ruta).takeIf { ruta.isNotBlank() && it.exists() } ?: return null
    val o = BitmapFactory.Options().apply { inSampleSize = 16 }
    val b = BitmapFactory.decodeFile(f.path, o) ?: return null
    Color(Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0))
}.getOrNull()

/**
 * La portada de una obra: su imagen (con el encuadre de este dispositivo) o, si no tiene, una tapa de su color con el título (y su autor, en
 * grande). En pequeño solo la inicial; sin título ([conTitulo] = false), solo la tela: así es como se amplía en la apertura de la ficha.
 */
@Composable
fun PortadaObra(o: Doc, modifier: Modifier = Modifier, conTitulo: Boolean = true, conAutor: Boolean = true) {
    val ctx = LocalContext.current
    val v = ImagenesObra.version
    val ruta = remember(o.s(ImagenesObra.PORTADA), v) { ImagenesObra.portada(ctx, o) }
    val enc = remember(ruta, v) { ImagenesObra.encPortada(ctx, o) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val ancho = maxWidth
        if (ruta.isNotBlank()) {
            Box(Modifier.fillMaxSize().background(Sand))
            ImagenEncuadrada(ruta, enc, Modifier.fillMaxSize(), o.s("titulo"))
        } else {
            val tela = telaDe(o)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerpColor(tela, Color.White, 0.10f), lerpColor(tela, Color.Black, 0.32f)))))
            Box(Modifier.fillMaxSize().padding(ancho * 0.07f).border(0.8.dp, PAPEL_CLARO.copy(alpha = 0.32f)))
            fun sp(f: Float) = (ancho.value * f).sp
            val titulo = o.s("titulo").ifBlank { "Sin título" }
            if (conTitulo && ancho < 64.dp) {
                Text(titulo.trim().firstOrNull()?.uppercase() ?: "·", color = PAPEL_CLARO.copy(alpha = 0.88f), fontFamily = Display, fontWeight = FontWeight.SemiBold,
                    fontSize = sp(0.46f), modifier = Modifier.align(Alignment.Center))
            } else if (conTitulo) {
                val chica = ancho < 110.dp
                Column(Modifier.fillMaxSize().padding(horizontal = ancho * (if (chica) 0.12f else 0.15f), vertical = ancho * 0.16f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(titulo, color = PAPEL_CLARO, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = sp(if (chica) 0.14f else 0.115f), lineHeight = sp(if (chica) 0.165f else 0.135f),
                            textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    val autor = o.s("autor")
                    if (autor.isNotBlank() && conAutor && !chica) {
                        Box(Modifier.padding(vertical = ancho * 0.03f).width(ancho * 0.12f).height(1.dp).background(PAPEL_CLARO.copy(alpha = 0.55f)))
                        Text(autor.uppercase(), color = PAPEL_CLARO.copy(alpha = 0.78f), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = sp(0.052f), letterSpacing = sp(0.006f),
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // El lomo: una sombra fina a la izquierda
        Box(Modifier.fillMaxHeight().width(ancho * 0.07f).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent))))
    }
}

/** El color de fondo de una obra para el cartel y la apertura: el medio de su portada, o su tela. */
@Composable
fun baseDeObra(o: Doc): Color {
    val ctx = LocalContext.current
    val v = ImagenesObra.version
    return remember(o.s(ImagenesObra.PORTADA), o.s("color"), o.s("titulo"), v) { colorDeImagen(ImagenesObra.portada(ctx, o)) ?: telaDe(o) }
}

/**
 * La portada de una obra encima de una imagen o de un color (el cartel «Ahora escribes» y la apertura de la ficha), con su PASPARTÚ: el
 * marco crema fijo de toda la familia (igual de día y de noche, sin depender del tema), o uno oscuro fijo si la portada es casi blanca.
 */
@Composable
fun PortadaSobreFondo(o: Doc, modifier: Modifier, sombra: Dp, alTocar: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val v = ImagenesObra.version
    val lPortada = remember(o.s(ImagenesObra.PORTADA), o.s("color"), v) { colorDeImagen(ImagenesObra.portada(ctx, o))?.let { luminancia(it) } ?: luminancia(telaDe(o)) }
    BoxWithConstraints(modifier.shadow(sombra).background(colorPaspartu(lPortada)).then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier)) {
        PortadaObra(o, Modifier.fillMaxSize().padding(grosorPaspartu(maxWidth)), conAutor = false)
    }
}

/** El grosor del paspartú para una tapa de [ancho]: un 5,4 % (8 dp en una de 150), entre 4 y 14 dp. */
fun grosorPaspartu(ancho: Dp): Dp = (ancho * 0.054f).coerceIn(4.dp, 14.dp)

/** El paspartú de la familia: crema fijo; oscuro fijo si la portada es casi blanca (luminancia ≥ [UMBRAL_PASPARTU]). */
val PASPARTU_CREMA = Color(0xFFF4EBD6)
val PASPARTU_OSCURO = Color(0xFF141210)
const val UMBRAL_PASPARTU = 0.78f
fun paspartuOscuro(lPortada: Float): Boolean = lPortada >= UMBRAL_PASPARTU
fun colorPaspartu(lPortada: Float): Color = if (paspartuOscuro(lPortada)) PASPARTU_OSCURO else PASPARTU_CREMA
fun luminancia(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

/** Un botón recto con marco de 2 px (los de la dirección C que no son la acción principal). */
@Composable
fun BotonMarco(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(texto.uppercase(), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp, maxLines = 1,
        modifier = modifier.border(2.dp, Charcoal).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp))
}

/** La hoja «IMÁGENES.» de una obra (portada y fondo), a pantalla completa. [fondo]: abre en la pestaña del fondo. */
@Composable
fun SelectorImagenes(id: String, fondo: Boolean = false, alCerrar: () -> Unit) {
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Paper)) { ContenidoImagenes(id, fondo, alCerrar) }
    }
}

/**
 * Lo de dentro de la hoja (aparte, para verlo en las capturas). Dos pestañas: LA PORTADA (2:3) y EL FONDO (a lo ancho). En cada una, lo que hay
 * ahora y los botones: encuadrar (en este dispositivo), buscar en internet, de tu móvil, quitar (y en el fondo, usar la portada).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoImagenes(id: String, fondoInicial: Boolean, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val e = remember { Escritos.get(ctx) }
    @Suppress("UNUSED_VARIABLE") val ver = e.version
    val o = e.get(id) ?: run { LaunchedEffect(Unit) { alCerrar() }; return }
    val v = ImagenesObra.version
    var esFondo by remember { mutableStateOf(fondoInicial) }
    var guardando by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf(false) }
    var encuadrar by remember { mutableStateOf(false) }
    fun dice(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()
    val titulo = o.s("titulo").ifBlank { "Sin título" }
    val consulta = listOf(titulo, o.s("genero")).filter { it.isNotBlank() }.joinToString(" ")

    suspend fun guardar(bytes: ByteArray?) {
        if (bytes == null) { dice("No he podido leer esa imagen."); guardando = false; return }
        val ok = withContext(Dispatchers.IO) { runCatching { if (esFondo) ImagenesObra.ponerFondo(e, id, bytes) else ImagenesObra.ponerPortada(e, id, bytes) }.getOrDefault(false) }
        guardando = false
        if (!ok) dice(if (esFondo) "Esa imagen no vale como fondo." else "Esa imagen no vale como portada.")
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) { guardando = true; ambito.launch { guardar(withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() }) } }
    }
    if (web) InternetPicker(if (esFondo) "$consulta fondo" else "$consulta portada", alElegir = { bytes -> web = false; guardando = true; ambito.launch { guardar(bytes) } }, alCerrar = { web = false })

    val rutaPortada = remember(o.s(ImagenesObra.PORTADA), v) { ImagenesObra.portada(ctx, o) }
    val rutaFondo = remember(o.s(ImagenesObra.FONDO), v) { ImagenesObra.fondo(ctx, o) }
    val rutaActual = if (esFondo) rutaFondo else rutaPortada
    val sePuedeEncuadrar = rutaActual.isNotBlank() && File(rutaActual).isFile
    if (encuadrar && sePuedeEncuadrar) EditorEncuadre(rutaActual, if (esFondo) ImagenesObra.encFondo(ctx, o) else ImagenesObra.encPortada(ctx, o), esFondo,
        alGuardar = { en -> ImagenesObra.ponerEncuadre(ctx, o, esFondo, en) }, alCerrar = { encuadrar = false })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
            EnlaceRevista("← $titulo", Modifier.padding(top = 6.dp), color = Paper.copy(alpha = 0.75f)) { alCerrar() }
            NombreConPunto("Imágenes", 44.sp, Paper, Modifier.padding(top = 2.dp))
        }
        PestanasRevista(listOf("La portada", "El fondo"), if (esFondo) 1 else 0) { esFondo = it == 1 }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            if (esFondo) {
                Box(Modifier.fillMaxWidth().aspectRatio(3f).clipToBounds().background(lerpColor(telaDe(o), Color.Black, 0.4f)).clickable { galeria.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (rutaFondo.isNotBlank()) ImagenEncuadrada(rutaFondo, ImagenesObra.encFondo(ctx, o), Modifier.fillMaxSize(), "Fondo de $titulo")
                    else Text("Sin fondo", color = PAPEL_CLARO.copy(alpha = 0.8f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                }
            } else {
                PortadaObra(o, Modifier.width(150.dp).height(225.dp).clickable { galeria.launch("image/*") })
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sePuedeEncuadrar) BotonMarco("Encuadrar") { encuadrar = true }
                BotonMarco("Buscar en internet") { web = true }
                BotonMarco("De tu móvil") { galeria.launch("image/*") }
                if (esFondo && rutaPortada.isNotBlank()) BotonMarco("Usar la portada") {
                    guardando = true; ambito.launch { withContext(Dispatchers.IO) { runCatching { ImagenesObra.portadaComoFondo(ctx, e, o) } }; guardando = false }
                }
                if (if (esFondo) ImagenesObra.hashFondo(o).isNotBlank() else ImagenesObra.hashPortada(o).isNotBlank()) BotonMarco("Quitar") { ImagenesObra.quitar(e, id, esFondo) }
            }
            if (guardando) Text(if (esFondo) "Guardando el fondo…" else "Guardando la portada…", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            Text(if (esFondo) "Se recorta a lo ancho: con «Encuadrar» eliges qué parte se ve. Sin fondo, se usa el color de la portada. Sale en «Ahora escribes» de la portada y arriba en la ficha."
                else "Sin portada, la obra lleva una tapa de su color con el título. La imagen va a tu cuenta y sale igual en tus otros dispositivos; el encuadre es de cada uno.",
                color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp, bottom = 24.dp))
        }
    }
}
