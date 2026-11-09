package com.gonzalo.mostaza

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Las imágenes de una película o una serie, como en Seshat y Bastet (y en Jonsu): su PORTADA (2:3) y su FONDO (una imagen a lo ancho para el
 * cartel «Ahora ves» y la apertura de la ficha). Las dos viajan con tu cuenta (por su contenido); qué parte se ve ([Encuadre]) es de cada
 * dispositivo. Sin fondo elegido, se usa el de TMDB (o el del addon), si lo hay.
 */

private const val NAVEGADOR = "Mozilla/5.0 (Linux; Android 14) Hathor"

/** Poner y quitar la portada y el fondo de un título: lo que hace la hoja «IMÁGENES.». */
object ImagenesTitulo {
    /**
     * Un fondo listo para la cuenta: a lo ancho, como mucho 1600×1000 (unos 150–300 KB en JPEG); se recorta al enseñarlo, no aquí, para
     * que se pueda ver entero en otros sitios. null si no es una imagen.
     */
    fun reducirFondo(bytes: ByteArray): ByteArray? {
        if (!com.gonzalo.familia.PortadasFamilia.esImagen(bytes)) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1; while (o.outWidth / (s * 2) >= 1600 || o.outHeight / (s * 2) >= 1000) s *= 2
        val b0 = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val k = minOf(1f, 1600f / b0.width, 1000f / b0.height)
        val b = if (k < 1f) Bitmap.createScaledBitmap(b0, (b0.width * k).toInt().coerceAtLeast(1), (b0.height * k).toInt().coerceAtLeast(1), true) else b0
        return java.io.ByteArrayOutputStream().use { out -> b.compress(Bitmap.CompressFormat.JPEG, 84, out); out.toByteArray() }
    }

    /** Pone una imagen como fondo (en la cuenta, como la portada: sale igual en tus otros dispositivos). false si la imagen no vale. */
    fun ponerFondo(t: Titulo, bytes: ByteArray, url: String = ""): Boolean {
        val r = reducirFondo(bytes) ?: return false
        val hash = runCatching { com.gonzalo.familia.LaFamilia.familia.imagenes.guarda(r, "image/jpeg") }.getOrNull() ?: return false
        Biblioteca.ponerFondo(t.ref, hash, url)
        return true
    }

    /** Pone una imagen como portada (reducida, como todas las de la familia). false si la imagen no vale. */
    fun ponerPortada(t: Titulo, bytes: ByteArray): Boolean {
        val hash = runCatching { com.gonzalo.familia.PortadasFamilia.guardar(bytes) }.getOrNull() ?: return false
        Biblioteca.ponerPortada(t.ref, hash)
        return true
    }

    /** Usa la portada como fondo (la misma imagen, que se recorta a lo ancho). */
    fun portadaComoFondo(t: Titulo): Boolean {
        val f = File(t.portada).takeIf { t.portada.isNotBlank() && it.isFile } ?: return false
        return ponerFondo(t, f.readBytes(), t.poster)
    }

    /** Quita el fondo a propósito: la cabecera va con el color de la portada (también en tus otros dispositivos). */
    fun quitarFondo(t: Titulo) = Biblioteca.ponerFondo(t.ref, "", "")

    suspend fun bajar(url: String): ByteArray? = withContext(Dispatchers.IO) { runCatching { Internet.descargar(url, NAVEGADOR).first }.getOrNull() }

    suspend fun ponerPortadaDe(t: Titulo, url: String): Boolean = bajar(url)?.let { b -> withContext(Dispatchers.IO) { ponerPortada(t, b) } } ?: false
    suspend fun ponerFondoDe(t: Titulo, url: String): Boolean = bajar(url)?.let { b -> withContext(Dispatchers.IO) { ponerFondo(t, b, url) } } ?: false
}

/** La hoja «IMÁGENES.» de un título (portada y fondo, como en Jonsu), a pantalla completa. [fondo]: abre en la pestaña del fondo. */
@Composable
fun SelectorImagenes(t: Titulo, fondo: Boolean = false, alCerrar: () -> Unit) {
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Paper)) { ContenidoImagenes(t.ref, fondo, alCerrar) }
    }
}

/**
 * Lo de dentro de la hoja (aparte, para verlo en las capturas). Dos pestañas: LA PORTADA (2:3) y EL FONDO (a lo ancho). En cada una, lo que hay
 * ahora y los botones (encuadrar, buscar en internet, de tu móvil, usar la portada, la automática, quitar) y, con tu clave de TMDB, sus
 * propuestas: los pósteres o los fondos («backdrops») de esa película o serie. [propuestas]: ya hechas (para las capturas).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoImagenes(ref: String, fondoInicial: Boolean, alCerrar: () -> Unit, propuestas: List<Pair<String, String>>? = null) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    Biblioteca.version
    val t = Biblioteca.titulos.firstOrNull { it.ref == ref } ?: run { LaunchedEffect(Unit) { alCerrar() }; return }
    var esFondo by remember { mutableStateOf(fondoInicial) }
    var guardando by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf(false) }
    var encuadrar by remember { mutableStateOf(false) }
    fun dice(x: String) = Toast.makeText(ctx, x, Toast.LENGTH_LONG).show()
    val conTmdb = Tmdb.activo
    // Las propuestas de TMDB: sus pósteres o sus fondos (en castellano, en inglés y sin texto)
    var hallados by remember(esFondo) { mutableStateOf(propuestas) }
    var buscando by remember(esFondo) { mutableStateOf(false) }
    LaunchedEffect(esFondo) {
        if (propuestas != null || !conTmdb) return@LaunchedEffect
        buscando = true
        hallados = runCatching { Tmdb.imagenes(t, esFondo) }.getOrDefault(emptyList()).take(12)
        buscando = false
    }
    fun elegirUrl(url: String) {
        if (guardando) return
        guardando = true
        ambito.launch {
            val ok = if (esFondo) ImagenesTitulo.ponerFondoDe(t, url) else ImagenesTitulo.ponerPortadaDe(t, url)
            guardando = false
            if (!ok) dice(if (esFondo) "No he podido poner ese fondo." else "No he podido bajar esa portada.")
        }
    }
    suspend fun guardar(bytes: ByteArray?) {
        val ok = bytes != null && withContext(Dispatchers.IO) { if (esFondo) ImagenesTitulo.ponerFondo(t, bytes) else ImagenesTitulo.ponerPortada(t, bytes) }
        guardando = false
        if (!ok) dice(if (esFondo) "Esa imagen no vale como fondo." else "Esa imagen no vale como portada.")
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) { guardando = true; ambito.launch { guardar(withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() }) } }
    }
    // Buscar en internet (el buscador de imágenes de la familia): para el fondo, con «wallpaper» detrás
    if (web) InternetPicker(if (esFondo) t.nombre + " wallpaper" else t.nombre + " poster", alElegir = { bytes -> web = false; guardando = true; ambito.launch { guardar(bytes) } }, alCerrar = { web = false })

    // Encuadrar la imagen de la pestaña (solo una imagen ya guardada en el dispositivo: se sabe cómo es entera)
    val rutaActual = if (esFondo) t.fondoImg else t.portada
    val sePuedeEncuadrar = rutaActual.isNotBlank() && !rutaActual.startsWith("http") && File(rutaActual).isFile
    if (encuadrar && sePuedeEncuadrar) EditorEncuadre(rutaActual, if (esFondo) t.encFondo else t.encPortada, esFondo,
        alGuardar = { e -> Biblioteca.ponerEncuadre(t.ref, esFondo, e) }, alCerrar = { encuadrar = false })

    Column(Modifier.fillMaxSize().atrapaFoco().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
            EnlaceRevista("← " + t.nombre, Modifier.padding(top = 6.dp), color = Paper.copy(alpha = 0.75f)) { alCerrar() }
            NombreConPunto("Imágenes", 44.sp, Paper, Modifier.padding(top = 2.dp))
        }
        PestanasRevista(listOf("La portada", "El fondo"), if (esFondo) 1 else 0) { esFondo = it == 1 }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            if (esFondo) {
                Box(Modifier.fillMaxWidth().aspectRatio(3f).clipToBounds().background(lerpColor(telaDe(t.nombre), Color.Black, 0.4f)).tocable { galeria.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (t.fondoVer.isNotBlank()) ImagenEncuadrada(t.fondoVer, t.encFondo, Modifier.fillMaxSize(), "Fondo de ${t.nombre}")
                    else Text("Sin fondo", color = Color.White.copy(alpha = 0.8f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                }
                if (t.fondoImg.isBlank() && !t.sinFondo && t.fondo.isNotBlank()) Text("EL DE TMDB (AUTOMÁTICO)", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
            } else {
                Box(Modifier.width(150.dp).height(225.dp).tocable { galeria.launch("image/*") }) { PortadaTitulo(t, Modifier.matchParentSize()) }
                Text(if (t.portadaPropia) "LA TUYA" else "LA DE TMDB (AUTOMÁTICA)", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sePuedeEncuadrar) BotonMarco("Encuadrar") { encuadrar = true }
                BotonMarco("Buscar en internet") { web = true }
                BotonMarco("De tu móvil") { galeria.launch("image/*") }
                if (esFondo && t.portada.isNotBlank() && File(t.portada).isFile) BotonMarco("Usar la portada") {
                    guardando = true; ambito.launch { val ok = withContext(Dispatchers.IO) { ImagenesTitulo.portadaComoFondo(t) }; guardando = false; if (!ok) dice("No he podido usar la portada.") }
                }
                if (!esFondo && t.portadaPropia) BotonMarco("La automática") { Biblioteca.portadaAutomatica(t.ref) }
                if (esFondo && (t.fondoImg.isNotBlank() || t.sinFondo) && t.fondo.isNotBlank()) BotonMarco("El de TMDB") { Biblioteca.fondoAutomatico(t.ref) }
                if (esFondo && t.fondoVer.isNotBlank()) BotonMarco("Quitar") { ImagenesTitulo.quitarFondo(t) }
            }
            if (guardando) Text(if (esFondo) "Guardando el fondo…" else "Guardando la portada…", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            Text(if (esFondo) "Se recorta a lo ancho: con «Encuadrar» eliges qué parte se ve (en este dispositivo). Sin fondo elegido, va el de TMDB; si lo quitas, el color de la portada. Sale en «Ahora ves» de la portada y arriba en la ficha."
                else "«La automática» vuelve al póster de TMDB (o el de tu addon). Con «Encuadrar» eliges qué parte se ve (en este dispositivo). La portada sale igual en tus otros dispositivos y en Jonsu.",
                color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp))
            Text("PROPUESTAS · TMDB", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            val lista = hallados
            when {
                !conTmdb && propuestas == null -> Text("Con tu clave de TMDB (Ajustes → Catálogo) salen aquí ${if (esFondo) "los fondos" else "los pósteres"} de «${t.nombre}».", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
                buscando && lista == null -> Text("Buscando…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                lista.isNullOrEmpty() -> Text("TMDB no tiene ${if (esFondo) "fondos" else "pósteres"} de «${t.nombre}». Prueba a buscar en internet.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
                esFondo -> lista.forEach { (mini, grande) ->
                    Box(Modifier.padding(bottom = 10.dp).fillMaxWidth().aspectRatio(16f / 9f).background(Sand).tocable(enabled = !guardando) { elegirUrl(grande) }) {
                        AsyncImage(imagenDe(mini), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                else -> lista.chunked(3).forEach { fila ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fila.forEach { (mini, grande) ->
                            Box(Modifier.weight(1f).aspectRatio(2f / 3f).background(Sand).tocable(enabled = !guardando) { elegirUrl(grande) }) {
                                AsyncImage(imagenDe(mini), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (conTmdb || propuestas != null) Text(Tmdb.AVISO_TMDB, color = Muted, fontFamily = Body, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Lo que se le da a Coil: un archivo de la app o una dirección. */
private fun imagenDe(ruta: String): Any = if (ruta.startsWith("http")) ruta else File(ruta)

/** Un botón recto con marco de 2 px (los de la dirección C que no son la acción principal). */
@Composable
fun BotonMarco(texto: String, modifier: Modifier = Modifier, color: Color = Charcoal, onClick: () -> Unit) {
    Text(texto.uppercase(), color = color, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp, maxLines = 1,
        modifier = modifier.border(2.dp, color).tocable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp))
}

/**
 * La portada de un título, recta y a 2:3: la que hay en el dispositivo (con su encuadre), o el póster de internet mientras baja; sin imagen,
 * una tapa de tela con el título en grotesca (como las de Seshat y Bastet).
 */
@Composable
fun PortadaTitulo(t: Titulo, modifier: Modifier = Modifier, conTexto: Boolean = true) {
    val local = remember(t.portada) { t.portada.takeIf { it.isNotBlank() && File(it).isFile } }
    Box(modifier.clipToBounds().background(telaDe(t.nombre))) {
        @Composable fun Tapa() = TapaDeTela(t.nombre, Modifier.fillMaxSize(), conTexto)
        when {
            local != null -> ImagenEncuadrada(local, t.encPortada, Modifier.fillMaxSize(), t.nombre)
            t.poster.isNotBlank() -> SubcomposeAsyncImage(model = t.poster, contentDescription = t.nombre, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), loading = { Tapa() }, error = { Tapa() })
            else -> Tapa()
        }
    }
}

/** Una tapa de tela (sin imagen): su color, sacado del título, y el título en grotesca clara (sin él, [conTexto] = false: ampliada como foto de apertura). */
@Composable
fun TapaDeTela(nombre: String, modifier: Modifier = Modifier, conTexto: Boolean = true) {
    val c = telaDe(nombre)
    if (!conTexto) { Box(modifier.background(c)); return }
    BoxWithConstraints(modifier.background(c).padding(8.dp), contentAlignment = Alignment.BottomStart) {
        val tam = (maxWidth.value * 0.15f).coerceIn(10f, 26f)
        Text(nombre.uppercase(), color = sobre(c), fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = tam.sp, lineHeight = (tam * 1.08f).sp,
            maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start)
    }
}

/** El color de la tela de un título sin imagen: uno de una paleta sobria, siempre el mismo para el mismo título. */
fun telaDe(nombre: String): Color {
    val telas = listOf(0xFF7B1E2B, 0xFF1F3A5F, 0xFF2E5E4E, 0xFF5A3A1A, 0xFF3A3F48, 0xFF6B4E8C, 0xFF8C5A2A, 0xFF24464F)
    return Color(telas[(nombre.lowercase().hashCode() and 0x7FFFFFFF) % telas.size])
}

/**
 * La portada de un título encima de una imagen o de un color (el cartel «Ahora ves» de la portada y la apertura de la ficha), como en
 * Seshat: con un PASPARTÚ, un marco liso crema fijo (igual de día y de noche) para que se despegue de lo que tenga detrás.
 * Si la portada es muy clara ([paspartuOscuro]), el marco pasa a un oscuro fijo. Sin esquinas; la sombra, por fuera.
 */
@Composable
fun PortadaSobreFondo(t: Titulo, modifier: Modifier, sombra: Dp, alTocar: (() -> Unit)? = null) {
    val lPortada = remember(t.portada, t.nombre) { luminanciaDePortada(t.portada) ?: luminancia(telaDe(t.nombre)) }
    val marco = colorPaspartu(lPortada)
    BoxWithConstraints(modifier.shadow(sombra).background(marco).then(if (alTocar != null) Modifier.tocable(onClick = alTocar) else Modifier)) {
        val w = maxWidth
        Box(Modifier.fillMaxSize().padding(grosorPaspartu(w))) { PortadaTitulo(t, Modifier.matchParentSize()) }
    }
}

/** El grosor del paspartú para una tapa de [ancho]: un 5,4 % (8 dp en una de 150), entre 4 y 14 dp. */
fun grosorPaspartu(ancho: Dp): Dp = (ancho * 0.054f).coerceIn(4.dp, 14.dp)

/**
 * El paspartú es SIEMPRE el mismo, en todas las apps de la familia, de día y de noche, sin depender del tema ni de «A tu manera»: un crema
 * claro fijo ([PASPARTU_CREMA]). Solo si la portada es muy clara (su luminancia media llega a [UMBRAL_PASPARTU]) pasa a un oscuro fijo.
 */
val PASPARTU_CREMA = Color(0xFFF4EBD6)
val PASPARTU_OSCURO = Color(0xFF141210)
const val UMBRAL_PASPARTU = 0.78f

fun paspartuOscuro(lPortada: Float): Boolean = lPortada >= UMBRAL_PASPARTU

fun colorPaspartu(lPortada: Float): Color = if (paspartuOscuro(lPortada)) PASPARTU_OSCURO else PASPARTU_CREMA

fun luminancia(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

/** La luminancia media (0–1) de la imagen de una portada, o null si no hay imagen. */
fun luminanciaDePortada(ruta: String): Float? = colorDePortada(ruta)?.let { luminancia(it) }

/** El color medio de una imagen (para el cartel y la cabecera de la ficha), o null si no hay imagen o no se puede leer. */
fun colorDePortada(ruta: String): Color? = runCatching {
    val f = File(ruta).takeIf { ruta.isNotBlank() && it.exists() } ?: return null
    val o = BitmapFactory.Options().apply { inSampleSize = 16 }
    val b = BitmapFactory.decodeFile(f.path, o) ?: return null
    Color(Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0))
}.getOrNull()

/** El alto del velo de arriba de la apertura (para que se lean «Volver» y las acciones sobre cualquier foto). */
val VELO_APERTURA = 110.dp

/** El velo: negro al 80 % arriba, transparente abajo. */
fun veloArriba() = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.35f), Color.Transparent))

fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** El color del texto sobre un color (claro u oscuro, según lo claro que sea). */
fun sobre(c: Color): Color = if (c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f > 0.55f) Color(0xFF050404) else Color.White
