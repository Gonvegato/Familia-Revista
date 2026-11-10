package com.gonzalo.marfil

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Arranca los datos de la app (una vez). */
fun iniciar(c: Context) {
    Cuenta.cargar(c)
    Skin.cargar(c); Biblioteca.cargar(c); Sagas.cargar(c)
    TemaAtrio.cargarGuardado(c)
    SincroP.iniciar(c)
    // Las páginas de los libros que aún no las tienen (del archivo o de Open Library), sin molestar
    Paginas.enSegundoPlano(c)
}

/** Dónde estás: la portada, las secciones (páginas por las que te mueves) y los ajustes. */
enum class Destino { Portada, Leyendo, Pendientes, Leidos, Abandonados, Autores, Sagas, Escritos, Ajustes }

/** La app: portada de revista, sus secciones, la ficha de un libro o una saga por encima y el selector de archivos para añadir. */
@Composable
fun MarfilApp(libroPedido: String? = null, alAbrirPedido: () -> Unit = {}, irPedido: String? = null, alIrPedido: () -> Unit = {}) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var destino by rememberSaveable { mutableIntStateOf(0) }
    var fichaId by rememberSaveable { mutableStateOf<String?>(null) }
    var sagaId by rememberSaveable { mutableStateOf<String?>(null) }
    val d = Destino.entries[destino]
    // Un widget (o un aviso) pide abrir un libro
    LaunchedEffect(libroPedido) { if (libroPedido != null && Biblioteca.por(libroPedido) != null) { fichaId = libroPedido; destino = 0 }; if (libroPedido != null) alAbrirPedido() }
    // Otra app (Azabache) pide una sección: «escritos», «leyendo»…
    var escritoPedido by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(irPedido) {
        if (irPedido != null) {
            val seccion = irPedido.substringBefore(':'); escritoPedido = irPedido.substringAfter(':', "").ifBlank { null }
            Destino.entries.firstOrNull { it.name.equals(seccion, true) }?.let { fichaId = null; sagaId = null; destino = it.ordinal }
            alIrPedido()
        }
    }

    // Añadir libros: se eligen uno o varios archivos (EPUB, PDF, MOBI…); se copian a la app y se leen sus datos
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) ambito.launch {
            val hechos = withContext(Dispatchers.IO) { uris.mapNotNull { runCatching { Metadatos.anadir(ctx, it) }.getOrNull() } }
            Toast.makeText(ctx, if (hechos.isEmpty()) "No he podido añadir ese archivo (vale EPUB, PDF, MOBI, AZW3, FB2, CBZ y TXT)" else if (hechos.size == 1) "Añadido: ${hechos[0].titulo}" else "Añadidos ${hechos.size} libros", Toast.LENGTH_LONG).show()
        }
    }
    fun anadir() = elegir.launch(arrayOf("application/epub+zip", "application/pdf", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook", "application/x-fictionbook+xml", "application/vnd.comicbook+zip", "text/plain", "application/octet-stream"))
    fun leer(l: Libro) { Lector.abrir(ctx, l) }

    AvisoActualizacion()
    // Desde cualquier página o desde Ajustes, Atrás vuelve a la portada (y desde la portada, sale)
    BackHandler(enabled = fichaId == null && sagaId == null && d != Destino.Portada) { destino = 0 }
    BackHandler(enabled = sagaId != null && fichaId == null) { sagaId = null }
    BackHandler(enabled = fichaId != null) { fichaId = null }

    Box(Modifier.fillMaxSize().background(Paper)) {
        // La portada y las secciones son páginas de una sola pantalla (se pasan con las pestañas o con el dedo); Ajustes va aparte
        if (d == Destino.Ajustes) PantEstilo(alVolver = { destino = 0 })
        else PantPrincipal(pagina = destino, alPagina = { destino = it }, alAbrir = { fichaId = it.id }, alLeer = ::leer, alAbrirSaga = { sagaId = it.id }, alAnadir = ::anadir,
            alAjustes = { destino = Destino.Ajustes.ordinal }, escritoPedido = escritoPedido, alEscritoAbierto = { escritoPedido = null })
        AnimatedVisibility(sagaId != null, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            sagaId?.let { id -> PantSaga(id, alCerrar = { sagaId = null }, alAbrir = { fichaId = it.id }) }
        }
        AnimatedVisibility(fichaId != null, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            fichaId?.let { id -> PantFicha(id, alCerrar = { fichaId = null }, alLeer = ::leer) }
        }
    }
}
