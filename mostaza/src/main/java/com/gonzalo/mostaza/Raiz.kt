package com.gonzalo.mostaza

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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

/** Arranca los datos de la app (una vez). */
fun iniciar(c: Context) {
    Cuenta.cargar(c)
    Skin.cargar(c); TemaAtrio.cargarGuardado(c)
    Biblioteca.cargar(c); Addons.cargar(c); Tmdb.cargar(c)
    Sala.cargar(c)
    SincroP.iniciar(c)
    // El motor de torrents (no arranca nada hasta que se abre un torrent)
    FuenteMotor.conecta(c)
}

/** Dónde estás: la portada y sus páginas (en el orden de [PAGINAS]) y, encima, Explorar, Buscar, Addons y Ajustes. */
enum class Destino { Portada, Viendo, Pendiente, Visto, Abandonado, Proximos, Explorar, Buscar, Addons, Ajustes }

/** La sección pedida desde fuera (un widget, un aviso, otra app): el nombre del destino o el de la pestaña («vistas», «próximos»…). */
fun destinoDe(go: String?): Destino? {
    val g = java.text.Normalizer.normalize(go ?: return null, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase().trim()
    return Destino.entries.firstOrNull { it.name.lowercase() == g } ?: when (g) {
        "pendientes" -> Destino.Pendiente; "vistas", "vistos", "terminadas" -> Destino.Visto; "abandonadas", "abandonados" -> Destino.Abandonado
        "proximos capitulos", "proximo" -> Destino.Proximos; else -> null
    }
}

private val entraHoja = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 10 } + fadeIn(tween(220))
private val saleHoja = slideOutVertically(tween(200, easing = FastOutLinearInEasing)) { it / 14 } + fadeOut(tween(160))

/**
 * La app: la portada y sus páginas en una sola pantalla ([PantPrincipal]); encima, Explorar, Buscar, Addons o Ajustes; encima de todo, la
 * ficha (y, desde ella, las fuentes de lo que vas a ver). Atrás cierra lo de encima; desde cualquier página vuelve a la portada.
 */
@Composable
fun MostazaApp(fichaPedida: String? = null, addonPedido: String? = null, seccionPedida: String? = null, alAtender: () -> Unit = {}) {
    /** La página de [PAGINAS] que se ve. */
    var pagina by rememberSaveable { mutableIntStateOf(0) }
    /** Lo que va encima de las páginas (null = nada). */
    var encima by rememberSaveable { mutableStateOf<Destino?>(null) }
    var ficha by remember { mutableStateOf<MetaBreve?>(null) }
    /** Al abrir la ficha, que siga donde lo dejaste («SEGUIR VIENDO →» de la portada y de las listas). */
    var verAlAbrir by remember { mutableStateOf(false) }
    var instalar by remember { mutableStateOf<String?>(null) }
    var pestanaAjustes by rememberSaveable { mutableIntStateOf(0) }
    fun ir(x: Destino) { val p = paginaDe(x); if (p >= 0) { encima = null; pagina = p } else encima = x }
    LaunchedEffect(fichaPedida, addonPedido, seccionPedida) {
        // (una sección que pide un widget: la de debajo; si además pide una ficha, la ficha va por encima)
        seccionPedida?.let { s -> destinoDe(s)?.let { ir(it); ficha = null } }
        fichaPedida?.let { r -> Biblioteca.porRef(r)?.let { ficha = it.breve(); verAlAbrir = false } }
        addonPedido?.let { instalar = it; encima = Destino.Addons }
        if (fichaPedida != null || addonPedido != null || seccionPedida != null) alAtender()
    }
    AvisoActualizacion()
    BackHandler(enabled = ficha == null && encima == null && pagina != 0) { pagina = 0 }
    BackHandler(enabled = ficha == null && encima != null) { encima = null }
    BackHandler(enabled = ficha != null) { ficha = null }
    Box(Modifier.fillMaxSize().background(Paper)) {
        when (encima) {
            Destino.Explorar -> PantExplorar(alVolver = { encima = null }, alAbrir = { ficha = it; verAlAbrir = false })
            Destino.Buscar -> PantBuscar(alVolver = { encima = null }, alAbrir = { ficha = it; verAlAbrir = false })
            Destino.Addons -> PantAddons(alVolver = { encima = null }, instalar = instalar, alInstalado = { instalar = null })
            Destino.Ajustes -> PantEstilo(alVolver = { encima = null; pestanaAjustes = 0 }, alAddons = { encima = Destino.Addons }, pestanaInicial = pestanaAjustes)
            else -> PantPrincipal(pagina = pagina, alPagina = { pagina = it }, alAbrir = { ficha = it.breve(); verAlAbrir = false }, alAbrirBreve = { ficha = it; verAlAbrir = false },
                alVer = { ficha = it.breve(); verAlAbrir = true }, alIr = ::ir, alReto = { pestanaAjustes = 5; encima = Destino.Ajustes })
        }
        AnimatedVisibility(ficha != null, enter = entraHoja, exit = saleHoja) {
            ficha?.let { b -> PantFicha(b, alCerrar = { ficha = null }, alAbrir = { ficha = it; verAlAbrir = false }, verAlAbrir = verAlAbrir, alVisto = { verAlAbrir = false }) }
        }
    }
}
