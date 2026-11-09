package com.gonzalo.marfil

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** La hoja «IMÁGENES.» de un libro (portada y fondo, como en Jonsu), a pantalla completa. [fondo]: abre en la pestaña del fondo. */
@Composable
fun SelectorImagenes(l: Libro, fondo: Boolean = false, alCerrar: () -> Unit) {
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Paper)) { ContenidoImagenes(l.id, fondo, alCerrar) }
    }
}

/** Una imagen tal cual (archivo de la app o dirección de internet), recortada. */
@Composable
fun ImagenRecortada(ruta: String, modifier: Modifier = Modifier, descripcion: String? = null) {
    val f = remember(ruta) { ruta.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { File(it) }?.takeIf { it.exists() } }
    AsyncImage(f ?: ruta.takeIf { it.startsWith("http") }, descripcion, modifier, contentScale = ContentScale.Crop)
}

/**
 * Lo de dentro de la hoja (aparte, para verlo en las capturas). Dos pestañas: LA PORTADA (2:3) y EL FONDO (a lo ancho). En cada una, lo que hay
 * ahora y los botones: buscar en internet, de tu móvil, quitar (y en la portada, las propuestas de iTunes y Open Library; en el fondo, usar la portada).
 * [hallados]: propuestas de portada ya hechas (para las capturas).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoImagenes(id: String, fondoInicial: Boolean, alCerrar: () -> Unit, hallados: List<Candidata>? = null) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val l = Biblioteca.por(id) ?: run { LaunchedEffect(Unit) { alCerrar() }; return }
    var esFondo by remember { mutableStateOf(fondoInicial) }
    var resultados by remember { mutableStateOf(hallados) }
    var buscando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf(false) }
    var encuadrar by remember { mutableStateOf(false) }
    fun dice(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()
    val consulta = listOf(l.titulo, l.autor).filter { it.isNotBlank() }.joinToString(" ")

    fun buscarPortadas() {
        if (buscando || consulta.isBlank()) return
        buscando = true
        ambito.launch {
            resultados = withContext(Dispatchers.IO) { runCatching { Portadas.buscar(consulta) }.getOrDefault(emptyList()) }
            buscando = false
        }
    }
    suspend fun guardar(bytes: ByteArray?, url: String = "") {
        if (bytes == null) { dice("No he podido leer esa imagen."); guardando = false; return }
        val n = withContext(Dispatchers.IO) { if (esFondo) Portadas.ponerFondo(ctx, l, bytes, url) else Portadas.poner(ctx, l, bytes, url) }
        guardando = false
        if (n == null) dice(if (esFondo) "Esa imagen no vale como fondo." else "Esa imagen no vale como portada.")
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) { guardando = true; ambito.launch { guardar(withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() }) } }
    }
    LaunchedEffect(Unit) { if (hallados == null) buscarPortadas() }
    // Buscar en internet (la misma búsqueda de imágenes que la portada): para el fondo, con «fondo» detrás, como hace Jonsu
    if (web) InternetPicker(if (esFondo) "$consulta fondo" else consulta, alElegir = { bytes -> web = false; guardando = true; ambito.launch { guardar(bytes) } }, alCerrar = { web = false })

    // Encuadrar la imagen de la pestaña (solo una imagen ya guardada en el móvil: se sabe cómo es entera)
    val rutaActual = if (esFondo) l.fondo else l.portada
    val sePuedeEncuadrar = rutaActual.isNotBlank() && !rutaActual.startsWith("http") && File(rutaActual).isFile
    if (encuadrar && sePuedeEncuadrar) EditorEncuadre(rutaActual, if (esFondo) l.encFondo else l.encPortada, esFondo,
        alGuardar = { e -> Biblioteca.ponerEncuadre(l.id, esFondo, e) }, alCerrar = { encuadrar = false })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
            EnlaceRevista("← " + l.titulo, Modifier.padding(top = 6.dp), color = Paper.copy(alpha = 0.75f)) { alCerrar() }
            NombreConPunto("Imágenes", 44.sp, Paper, Modifier.padding(top = 2.dp))
        }
        PestanasRevista(listOf("La portada", "El fondo"), if (esFondo) 1 else 0) { esFondo = it == 1 }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            if (esFondo) {
                Box(Modifier.fillMaxWidth().aspectRatio(3f).clipToBounds().background(lerpColor(telaDe(l.titulo), androidx.compose.ui.graphics.Color.Black, 0.4f)).clickable { galeria.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (l.fondo.isNotBlank()) ImagenEncuadrada(l.fondo, l.encFondo, Modifier.fillMaxSize(), "Fondo de ${l.titulo}")
                    else Text("Sin fondo", color = PAPEL_CLARO.copy(alpha = 0.8f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                }
            } else {
                PortadaLibro(l, Modifier.width(150.dp).height(225.dp).clickable { galeria.launch("image/*") }, radio = 0)
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sePuedeEncuadrar) BotonMarco("Encuadrar") { encuadrar = true }
                BotonMarco("Buscar en internet") { web = true }
                BotonMarco("De tu móvil") { galeria.launch("image/*") }
                if (esFondo && l.portada.isNotBlank() && File(l.portada).isFile) BotonMarco("Usar la portada") {
                    guardando = true; ambito.launch { withContext(Dispatchers.IO) { Portadas.portadaComoFondo(ctx, l) }; guardando = false }
                }
                if (!esFondo && !l.enOtroDispositivo) BotonMarco("La del libro") {
                    guardando = true; ambito.launch { val n = withContext(Dispatchers.IO) { Portadas.delLibro(ctx, l) }; guardando = false; if (n == null) dice("Este libro no trae portada propia.") }
                }
                if (if (esFondo) l.fondo.isNotBlank() else l.portada.isNotBlank()) BotonMarco("Quitar") { if (esFondo) Portadas.quitarFondo(ctx, l) else Portadas.quitar(ctx, l) }
            }
            if (guardando) Text(if (esFondo) "Guardando el fondo…" else "Guardando la portada…", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            if (esFondo) {
                Text("Se recorta a lo ancho: con «Encuadrar» eliges qué parte se ve. Sin fondo, se usa el color de la portada como ahora. Sale en «Ahora lees» de la portada y arriba en la ficha.",
                    color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp, bottom = 24.dp))
            } else {
                Text("PROPUESTAS", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                val lista = resultados
                when {
                    buscando && lista == null -> Text("Buscando portadas…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                    lista.isNullOrEmpty() -> Text("No he encontrado portadas de «$consulta». Prueba a buscar en internet.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                    else -> lista.chunked(3).forEach { fila ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            fila.forEach { c ->
                                Column(Modifier.weight(1f).clickable(enabled = !guardando) { guardando = true; ambito.launch { guardar(withContext(Dispatchers.IO) { Portadas.descargar(c.grande) }, c.grande) } }) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Sand)) { AsyncImage(c.miniatura, c.titulo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                    Text(c.fuente.uppercase(), color = Acento, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 4.dp))
                                    Text(c.titulo, color = Muted, fontFamily = Body, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
}

/** Un botón recto con marco de 2 px (los de la dirección C que no son la acción principal). */
@Composable
fun BotonMarco(texto: String, onClick: () -> Unit) {
    Text(texto.uppercase(), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp, maxLines = 1,
        modifier = Modifier.border(2.dp, Charcoal).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp))
}

/**
 * La portada de un libro encima de una imagen o de un color (el cartel «Ahora lees» de la portada y la apertura de la ficha), en un solo sitio:
 * con un PASPARTÚ, un marco liso del color de la página (crema de serie; sigue «A tu manera» y la noche) para que se despegue de lo que
 * tenga detrás. Si la portada se parece demasiado a ese color ([paspartuEnTinta]: una portada casi blanca sobre el crema, o una muy oscura de
 * noche), el marco pasa a la tinta de la app. Sin esquinas; la sombra, por fuera.
 * El grosor va con el tamaño de la tapa (unos 8 dp en una de 150).
 */
@Composable
fun PortadaSobreFondo(l: Libro, modifier: Modifier, sombra: androidx.compose.ui.unit.Dp, alTocar: (() -> Unit)? = null) {
    val lPortada = remember(l.portada, l.titulo) { luminanciaDePortada(l.portada) ?: luminancia(telaDe(l.titulo)) }
    val marco = if (paspartuEnTinta(luminancia(Paper), lPortada)) Charcoal else Paper
    BoxWithConstraints(modifier.shadow(sombra).background(marco).then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier)) {
        PortadaLibro(l, Modifier.fillMaxSize().padding(grosorPaspartu(maxWidth)), radio = 0, conAutor = false)
    }
}

/** El grosor del paspartú para una tapa de [ancho]: un 5,4 % (8 dp en una de 150), entre 4 y 14 dp. */
fun grosorPaspartu(ancho: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = (ancho * 0.054f).coerceIn(4.dp, 14.dp)

/**
 * Si la portada y el color de la página se parecen menos que esto (en luminancia, 0–1), el paspartú va en la tinta. Con el crema de serie
 * (≈0,94) eso es una portada de luminancia media > ≈0,8 (casi blanca); de noche, con la página oscura, una portada muy oscura.
 */
const val PARECIDO_PASPARTU = 0.15f

fun paspartuEnTinta(lPagina: Float, lPortada: Float): Boolean = kotlin.math.abs(lPagina - lPortada) < PARECIDO_PASPARTU

fun luminancia(c: androidx.compose.ui.graphics.Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

/** La luminancia media (0–1) de la imagen de una portada, o null si no hay imagen. */
fun luminanciaDePortada(ruta: String): Float? = colorDePortada(ruta)?.let { luminancia(it) }
