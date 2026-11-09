package com.gonzalo.hiedra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Las imágenes de un juego, como en Seshat (y en Jonsu): su PORTADA (2:3) y su FONDO (una imagen a lo ancho para el cartel «Ahora juegas»
 * y la apertura de la ficha). Las dos viajan con tu cuenta (por su contenido); qué parte se ve ([Encuadre]) es de cada dispositivo.
 */

private const val NAVEGADOR = "Mozilla/5.0 (Linux; Android 14) Bastet"

/** El fondo, la portada como fondo y quitarlo: lo que hace la hoja «IMÁGENES.». */
object ImagenesJuego {
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

    /** Pone una imagen como fondo de un juego (en la cuenta, como la portada: sale igual en tus otros dispositivos). false si la imagen no vale. */
    fun ponerFondo(j: Juego, bytes: ByteArray, url: String = ""): Boolean {
        val r = reducirFondo(bytes) ?: return false
        val hash = runCatching { com.gonzalo.familia.LaFamilia.familia.imagenes.guarda(r, "image/jpeg") }.getOrNull() ?: return false
        Biblioteca.ponerFondo(j.id, hash, url)
        return true
    }

    /** Usa la portada como fondo (la misma imagen, que se recorta a lo ancho). */
    fun portadaComoFondo(j: Juego): Boolean {
        val f = File(j.portada).takeIf { j.portada.isNotBlank() && it.isFile } ?: return false
        return ponerFondo(j, f.readBytes(), j.portadaUrl)
    }

    /** Quita el fondo: la cabecera vuelve al color de la portada (también en tus otros dispositivos). */
    fun quitarFondo(j: Juego) = Biblioteca.ponerFondo(j.id, "", "")

    /** Baja una imagen y la pone de portada (la imagen entera, sin recortar: el encuadre va aparte). false si no se pudo. */
    suspend fun ponerPortadaDe(c: Context, j: Juego, url: String): Boolean {
        val ruta = withContext(Dispatchers.IO) { runCatching { guardarPortadaBytes(c, j.id, Internet.descargar(url, NAVEGADOR).first, original = true) }.getOrNull() } ?: return false
        Biblioteca.cambiar(j.id) { it.copy(portada = ruta, portadaUrl = url) }
        return true
    }

    /** Baja una imagen y la pone de fondo. */
    suspend fun ponerFondoDe(j: Juego, url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { ponerFondo(j, Internet.descargar(url, NAVEGADOR).first, url) }.getOrDefault(false)
    }
}

/** La hoja «IMÁGENES.» de un juego (portada y fondo, como en Jonsu), a pantalla completa. [fondo]: abre en la pestaña del fondo. */
@Composable
fun SelectorImagenes(j: Juego, fondo: Boolean = false, alCerrar: () -> Unit) {
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Paper)) { ContenidoImagenes(j.id, fondo, alCerrar) }
    }
}

/**
 * Lo de dentro de la hoja (aparte, para verlo en las capturas). Dos pestañas: LA PORTADA (2:3) y EL FONDO (a lo ancho). En cada una, lo que hay
 * ahora y los botones (encuadrar, SteamGridDB, buscar en internet, de tu móvil, usar la portada, quitar) y, si tienes la API key de
 * SteamGridDB, sus propuestas: las portadas (600×900) o los fondos («heroes», la imagen ancha de la cabecera de Steam) del juego.
 * [propuestas]: ya hechas (para las capturas).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoImagenes(id: String, fondoInicial: Boolean, alCerrar: () -> Unit, propuestas: List<Pair<String, String>>? = null) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val j = Biblioteca.por(id) ?: run { LaunchedEffect(Unit) { alCerrar() }; return }
    var esFondo by remember { mutableStateOf(fondoInicial) }
    var guardando by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf(false) }
    var sgdb by remember { mutableStateOf(false) }
    var encuadrar by remember { mutableStateOf(false) }
    fun dice(t: String) = Lanzar.aviso(ctx, t)
    val key = remember { Servicios.claves(ctx).sgdb }
    // Las propuestas de SteamGridDB: del primer juego que encuentra con este nombre
    var hallados by remember(esFondo) { mutableStateOf(propuestas) }
    var buscando by remember(esFondo) { mutableStateOf(false) }
    LaunchedEffect(esFondo) {
        if (propuestas != null || key.isBlank()) return@LaunchedEffect
        buscando = true
        hallados = runCatching { Sgdb.buscar(j.titulo, key).firstOrNull()?.let { h -> if (esFondo) Sgdb.fondos(h.id, key) else Sgdb.portadas(h.id, key) }.orEmpty() }.getOrDefault(emptyList()).take(12)
        buscando = false
    }

    fun elegirUrl(url: String) {
        if (guardando) return
        guardando = true
        ambito.launch {
            val ok = if (esFondo) ImagenesJuego.ponerFondoDe(j, url) else ImagenesJuego.ponerPortadaDe(ctx, j, url)
            guardando = false
            if (!ok) dice(if (esFondo) "No he podido poner ese fondo." else "No he podido bajar esa portada.")
        }
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) {
            guardando = true
            ambito.launch {
                if (esFondo) {
                    val bytes = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() }
                    val ok = bytes != null && withContext(Dispatchers.IO) { ImagenesJuego.ponerFondo(j, bytes) }
                    if (!ok) dice("Esa imagen no vale como fondo.")
                } else guardarPortada(ctx, j.id, u)?.let { r -> Biblioteca.cambiar(j.id) { it.copy(portada = r) } } ?: dice("Esa imagen no vale como portada.")
                guardando = false
            }
        }
    }
    // Buscar en internet (el mismo buscador de imágenes de siempre): para el fondo, con «wallpaper» detrás
    val sistema = Catalogo.sistema(j.sistema)
    if (web) InternetPicker(
        initialQuery = if (esFondo) j.titulo + " wallpaper" else j.titulo + (if (j.rom != null) " " + (sistema?.nombre ?: "") + " portada" else " portada"),
        idJuego = j.id, original = true,
        onPick = { ruta ->
            web = false
            if (esFondo) { guardando = true; ambito.launch { val ok = withContext(Dispatchers.IO) { runCatching { ImagenesJuego.ponerFondo(j, File(ruta).readBytes()) }.getOrDefault(false) }; guardando = false; if (!ok) dice("Esa imagen no vale como fondo.") } }
            else Biblioteca.cambiar(j.id) { it.copy(portada = ruta) }
        },
        onDismiss = { web = false })
    if (sgdb) DialogoSgdb(j, alCerrar = { sgdb = false }) { url -> sgdb = false; elegirUrl(url) }

    // Encuadrar la imagen de la pestaña (solo una imagen ya guardada en el móvil: se sabe cómo es entera)
    val rutaActual = if (esFondo) j.fondo else j.portada
    val sePuedeEncuadrar = rutaActual.isNotBlank() && !rutaActual.startsWith("http") && File(rutaActual).isFile
    if (encuadrar && sePuedeEncuadrar) EditorEncuadre(rutaActual, if (esFondo) j.encFondo else j.encPortada, esFondo,
        alGuardar = { e -> Biblioteca.ponerEncuadre(j.id, esFondo, e) }, alCerrar = { encuadrar = false })

    Column(Modifier.fillMaxSize().atrapaFoco().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
            EnlaceRevista("← " + j.titulo, Modifier.padding(top = 6.dp), color = Paper.copy(alpha = 0.75f)) { alCerrar() }
            NombreConPunto("Imágenes", 44.sp, Paper, Modifier.padding(top = 2.dp))
        }
        PestanasRevista(listOf("La portada", "El fondo"), if (esFondo) 1 else 0) { esFondo = it == 1 }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            if (esFondo) {
                Box(Modifier.fillMaxWidth().aspectRatio(3f).clipToBounds().background(lerpColor(telaDe(j.titulo), Color.Black, 0.4f)).tocable { galeria.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (j.fondo.isNotBlank()) ImagenEncuadrada(j.fondo, j.encFondo, Modifier.fillMaxSize(), "Fondo de ${j.titulo}")
                    else Text("Sin fondo", color = Color.White.copy(alpha = 0.8f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                }
            } else {
                Box(Modifier.width(150.dp).height(225.dp).tocable { galeria.launch("image/*") }) { PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 64) }
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sePuedeEncuadrar) BotonMarco("Encuadrar") { encuadrar = true }
                if (!esFondo) BotonMarco("SteamGridDB") { sgdb = true }
                BotonMarco("Buscar en internet") { web = true }
                BotonMarco("De tu móvil") { galeria.launch("image/*") }
                if (esFondo && j.portada.isNotBlank() && File(j.portada).isFile) BotonMarco("Usar la portada") {
                    guardando = true; ambito.launch { val ok = withContext(Dispatchers.IO) { ImagenesJuego.portadaComoFondo(j) }; guardando = false; if (!ok) dice("No he podido usar la portada.") }
                }
                if (!esFondo && j.portada.isNotBlank()) BotonMarco("La automática") { Biblioteca.cambiar(j.id) { it.copy(portada = "") } }
                if (esFondo && j.fondo.isNotBlank()) BotonMarco("Quitar") { ImagenesJuego.quitarFondo(j) }
            }
            if (guardando) Text(if (esFondo) "Guardando el fondo…" else "Guardando la portada…", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            Text(if (esFondo) "Se recorta a lo ancho: con «Encuadrar» eliges qué parte se ve (en este dispositivo). Sin fondo, se usa el color de la portada. Sale en «Ahora juegas» de la portada y arriba en la ficha."
                else "«La automática» vuelve a la de SteamGridDB, la caja de libretro, el icono de la app o la de la web. Con «Encuadrar» eliges qué parte se ve (en este dispositivo).",
                color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp))
            Text("PROPUESTAS · STEAMGRIDDB", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            val lista = hallados
            when {
                key.isBlank() && propuestas == null -> Text("Con tu API key de SteamGridDB (Ajustes → Cuenta → Servicios) salen aquí ${if (esFondo) "los fondos" else "las portadas"} del juego.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
                buscando && lista == null -> Text("Buscando…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                lista.isNullOrEmpty() -> Text(if (esFondo) "SteamGridDB no tiene fondos de «${j.titulo}». Prueba a buscar en internet." else "SteamGridDB no tiene portadas de «${j.titulo}». Prueba con «SteamGridDB» (otro nombre) o en internet.",
                    color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
                esFondo -> lista.forEach { (mini, grande) ->
                    Box(Modifier.padding(bottom = 10.dp).fillMaxWidth().aspectRatio(3.1f).background(Sand).tocable(enabled = !guardando) { elegirUrl(grande) }) {
                        AsyncImage(mini, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                else -> lista.chunked(3).forEach { fila ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fila.forEach { (mini, grande) ->
                            Box(Modifier.weight(1f).aspectRatio(2f / 3f).background(Sand).tocable(enabled = !guardando) { elegirUrl(grande) }) {
                                AsyncImage(mini, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Un botón recto con marco de 2 px (los de la dirección C que no son la acción principal). */
@Composable
fun BotonMarco(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(texto.uppercase(), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp, maxLines = 1,
        modifier = modifier.border(2.dp, Charcoal).tocable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp))
}

/**
 * La portada de un juego encima de una imagen o de un color (el cartel «Ahora juegas» de la portada y la apertura de la ficha), como en
 * Seshat: con un PASPARTÚ, un marco liso crema fijo (igual de día y de noche) para que se despegue de lo que tenga detrás.
 * Si la portada es muy clara ([paspartuOscuro]), el marco pasa a un oscuro fijo. Sin esquinas; la sombra, por fuera.
 * El grosor va con el tamaño de la tapa (unos 8 dp en una de 150).
 */
@Composable
fun PortadaSobreFondo(j: Juego, modifier: Modifier, sombra: Dp, alTocar: (() -> Unit)? = null) {
    val lPortada = remember(j.portada, j.titulo) { luminanciaDePortada(j.portada) ?: luminancia(telaDe(j.titulo)) }
    val marco = colorPaspartu(lPortada)
    BoxWithConstraints(modifier.shadow(sombra).background(marco).then(if (alTocar != null) Modifier.tocable(onClick = alTocar) else Modifier)) {
        val w = maxWidth
        Box(Modifier.fillMaxSize().padding(grosorPaspartu(w))) {
            PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = (w.value * 0.4f).toInt().coerceIn(18, 120))
        }
    }
}

/** El grosor del paspartú para una tapa de [ancho]: un 5,4 % (8 dp en una de 150), entre 4 y 14 dp. */
fun grosorPaspartu(ancho: Dp): Dp = (ancho * 0.054f).coerceIn(4.dp, 14.dp)

/**
 * El paspartú es SIEMPRE el mismo, en todas las apps de la familia, de día y de noche, sin depender del tema ni de «A tu manera»: un crema
 * claro fijo ([PASPARTU_CREMA]). Solo si la portada es muy clara ([paspartuOscuro]: su luminancia media llega a [UMBRAL_PASPARTU], lo que
 * con el crema (≈0,93) es una diferencia menor que 0,15: casi blanca o crema) pasa a un oscuro fijo ([PASPARTU_OSCURO]).
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
