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
    Skin.cargar(c); Biblioteca.cargar(c); Sagas.cargar(c); Cola.cargar(c)
    TemaAtrio.cargarGuardado(c)
    SincroP.iniciar(c)
    // Las páginas de los libros que aún no las tienen (del archivo o de Open Library), sin molestar
    Paginas.enSegundoPlano(c)
}

/**
 * Las secciones que se pueden pedir desde fuera (otra app, un widget) con `go=<nombre>`: se conservan los nombres de siempre. Con la
 * dirección D son la pestaña Hoy, un filtro de la Biblioteca o los Ajustes.
 */
enum class Destino { Portada, Leyendo, Pendientes, Leidos, Abandonados, Autores, Sagas, Escritos, Ajustes }

/** El filtro de la biblioteca de una sección de las de siempre (null: no es de la biblioteca). */
fun estanteDe(d: Destino): Estante? = when (d) {
    Destino.Leyendo -> Estante.Leyendo; Destino.Pendientes -> Estante.Pendientes; Destino.Leidos -> Estante.Leidos; Destino.Abandonados -> Estante.Abandonados
    Destino.Autores -> Estante.Autores; Destino.Sagas -> Estante.Sagas; Destino.Escritos -> Estante.Escritos; else -> null
}

/** La app (dirección D): la barra de abajo con Hoy, Biblioteca, Añadir y Tú; por encima, los ajustes, una saga o la ficha de un libro. */
@Composable
fun MarfilApp(libroPedido: String? = null, alAbrirPedido: () -> Unit = {}, irPedido: String? = null, alIrPedido: () -> Unit = {}) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var estante by rememberSaveable { mutableIntStateOf(0) }
    var ajustes by rememberSaveable { mutableStateOf(false) }
    var fichaId by rememberSaveable { mutableStateOf<String?>(null) }
    var sagaId by rememberSaveable { mutableStateOf<String?>(null) }
    // Un widget (o un aviso) pide abrir un libro
    LaunchedEffect(libroPedido) { if (libroPedido != null && Biblioteca.por(libroPedido) != null) { fichaId = libroPedido; pestana = 0; ajustes = false }; if (libroPedido != null) alAbrirPedido() }
    // Otra app (Jonsu, Thot) pide una sección: «escritos», «leyendo»…
    var escritoPedido by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(irPedido) {
        if (irPedido != null) {
            val seccion = irPedido.substringBefore(':'); escritoPedido = irPedido.substringAfter(':', "").ifBlank { null }
            Destino.entries.firstOrNull { it.name.equals(seccion, true) }?.let { d ->
                fichaId = null; sagaId = null; ajustes = d == Destino.Ajustes
                val e = estanteDe(d)
                if (e != null) { estante = e.ordinal; pestana = Mesa.Biblioteca.ordinal } else if (d == Destino.Portada) pestana = 0
            }
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
    // Atrás: cierra lo de encima (ficha, saga, ajustes); desde otra pestaña vuelve a Hoy; desde Hoy, sale
    BackHandler(enabled = fichaId == null && sagaId == null && !ajustes && pestana != 0) { pestana = 0 }
    BackHandler(enabled = ajustes && fichaId == null && sagaId == null) { ajustes = false }
    BackHandler(enabled = sagaId != null && fichaId == null) { sagaId = null }
    BackHandler(enabled = fichaId != null) { fichaId = null }

    Box(Modifier.fillMaxSize().background(Paper)) {
        if (ajustes) PantEstilo(alVolver = { ajustes = false })
        else PantMesa(Mesa.entries[pestana], { pestana = it.ordinal }, Estante.entries[estante], { estante = it.ordinal }, alAbrir = { fichaId = it.id }, alLeer = ::leer, alAbrirSaga = { sagaId = it.id },
            alAnadir = ::anadir, alAjustes = { ajustes = true }, escritoPedido = escritoPedido, alEscritoAbierto = { escritoPedido = null })
        AnimatedVisibility(sagaId != null, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            sagaId?.let { id -> PantSaga(id, alCerrar = { sagaId = null }, alAbrir = { fichaId = it.id }) }
        }
        AnimatedVisibility(fichaId != null, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            fichaId?.let { id -> PantFicha(id, alCerrar = { fichaId = null }, alLeer = ::leer) }
        }
    }
}
