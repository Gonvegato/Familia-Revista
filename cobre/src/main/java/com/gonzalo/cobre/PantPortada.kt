package com.gonzalo.cobre

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Progreso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * La portada de Cobre: la cabecera y el menú de secciones; «Seguir viendo» (películas y episodios a medias), tus favoritos (con lo que
 * echan ahora), el último canal que viste y cómo van tus listas. Sin listas, cómo poner la primera.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantPortada(alIr: (Destino) -> Unit, alFicha: (Ficha) -> Unit) {
    val ctx = LocalContext.current
    val hoy = remember { Calendar.getInstance() }
    Listas.version; Tele.version
    val listas = Listas.lista()
    val usables = Listas.usables()
    val favs = MiCuenta.favoritos()
    val ultimo = MiCuenta.ultimo()
    // Las listas de los favoritos, leídas (para sus logos y lo que echan)
    val cargadas = rememberCargadas(usables)
    // (Favoritos lo primero: es donde se está casi todo el rato)
    val secciones = listOf("Favoritos" to Destino.Favoritos, "Directo" to Destino.Directo, "Guía" to Destino.Guia) +
        (if (usables.any { it.esXtream }) listOf("Películas" to Destino.Peliculas, "Series" to Destino.Series) else emptyList()) + listOf("Listas" to Destino.Listas)
    val desplazado = rememberScrollState()
    val umbral = with(LocalDensity.current) { 300.dp.toPx() }
    val plegada by remember(umbral) { derivedStateOf { desplazado.value > umbral } }
    val m = margen()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Paper).verticalScroll(desplazado).statusBarsPadding().padding(horizontal = m)) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                EnlaceRevista("Buscar") { alIr(Destino.Buscar) }
                EnlaceRevista("Guía", Modifier.padding(start = 14.dp), acento = true) { alIr(Destino.Guia) }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Settings, "Ajustes", tint = Charcoal, modifier = Modifier.padding(start = 6.dp).tocable { alIr(Destino.Ajustes) }.padding(8.dp).size(22.dp))
            }
            val ancho = minOf(LocalConfiguration.current.screenWidthDp, 1080)
            Cabecera(if (ancho >= 700) 132.sp else 84.sp, Modifier.padding(top = 8.dp))
            FileteCabecera(3.dp)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Nº ${hoy.get(Calendar.DAY_OF_YEAR)}", color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text(SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES")).format(hoy.time).uppercase(), color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Filete(1.dp)
            Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Filete(1.dp)
                FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    secciones.forEachIndexed { i, (t, x) ->
                        Text(t.uppercase(), color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1,
                            modifier = Modifier.let { if (i == 0) it.focoInicial() else it }.tocable { alIr(x) }.padding(vertical = 9.dp))
                    }
                }
                Filete(1.dp)
            }
            if (!Cuenta.vinculada) {
                Spacer(Modifier.height(14.dp))
                Nota("Sin cuenta todavía: entra en Ajustes → Cuenta (o en Azabache, y Cobre entra sola). Tus listas, tus favoritos y lo que ves van con tu cuenta.")
            }
            if (listas.isEmpty()) {
                Spacer(Modifier.height(26.dp))
                Antetitulo("Para empezar")
                Filete(1.dp)
                Spacer(Modifier.height(10.dp))
                Text("Cobre no trae canales: es tu reproductor. Pon tu lista M3U (una dirección o un archivo) o el login de tu servidor de Xtream Codes (servidor, usuario y contraseña) y aquí tendrás tus canales con su guía, y, con Xtream, sus películas y series.",
                    color = Charcoal, fontFamily = Display, fontSize = 19.sp, lineHeight = 26.sp)
                Spacer(Modifier.height(14.dp))
                BotonAcento("Añadir una lista", modifier = Modifier.focoInicial("vacia")) { alIr(Destino.Listas) }
            }
            // El último canal (como una tele: seguir donde lo dejaste)
            ultimo?.let { u ->
                val l = usables.firstOrNull { it.id == u.lista }
                val c = l?.let { cargadas[it.id]?.porClave?.get(u.canal) }
                if (l != null && c != null) {
                    Spacer(Modifier.height(22.dp))
                    val (ahora, _) = cargadas[l.id]?.let { Tele.ahoraYDespuesRapido(it, c) } ?: (null to null)
                    MagLink("Seguir con ${c.nombre}", ahora?.titulo?.let { "Ahora: $it" } ?: l.nombre, accent = true) {
                        cargadas[l.id]?.let { cg -> Reproducir.canal(ctx, l, Tele.visibles(cg, ctx, c.grupo).ifEmpty { listOf(c) }, c) }
                    }
                }
            }
            // Tus favoritos, todos y en grande (con lo que echan ahora y lo siguiente): es lo que más se mira. Sin «Seguir viendo» aquí:
            // lo empezado del videoclub está en «Películas» y «Series».
            val favVivos = favs.mapNotNull { f -> usables.firstOrNull { it.id == f.lista }?.let { l -> cargadas[l.id]?.porClave?.get(f.canal)?.let { Triple(l, it, f) } } }
            if (favVivos.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Antetitulo("Tus favoritos", Modifier.weight(1f)); EnlaceRevista("Abrir →", Modifier.padding(start = 12.dp), acento = true) { alIr(Destino.Favoritos) } }
                    Filete(1.dp)
                    ListaFavoritos(favVivos, cargadas)
                }
            } else if (favs.isEmpty() && usables.isNotEmpty()) Nota("Tus canales favoritos saldrán aquí, en grande: márcalos con ★ en «Directo».", Modifier.padding(top = 22.dp))
            // Cómo van tus listas
            if (listas.isNotEmpty()) {
                Spacer(Modifier.height(26.dp)); Antetitulo("Tus listas"); Filete(1.dp)
                listas.forEach { l ->
                    val cg = cargadas[l.id]
                    val que = Tele.ocupada[l.id] ?: Tele.errores[l.id] ?: when {
                        l !in usables -> "Es un archivo de otro dispositivo"
                        cg == null -> "Aún sin canales"
                        else -> "${com.gonzalo.cobre.Formato.cifra(cg.canales.size)} canales" + if (cg.guia != null) " · con guía" else ""
                    }
                    FilaRevista(l.nombre, Tele.tipoNombre(l.tipo) + " · " + que) {
                        if (l in usables) { AjustesDirecto.ponDonde(ctx, l.id, ""); alIr(Destino.Directo) } else alIr(Destino.Listas)
                    }
                }
            }
            Text("Cobre · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 30.dp))
            Text("versión $VERSION", color = Muted.copy(alpha = 0.7f), fontFamily = Body, fontSize = 10.sp, letterSpacing = 0.6.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
            Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
        BarraPlegada("Cobre", plegada) { EnlaceRevista("Buscar") { alIr(Destino.Buscar) }; EnlaceRevista("Guía", Modifier.padding(start = 14.dp), acento = true) { alIr(Destino.Guia) } }
    }
}

/** «Seguir viendo»: lo empezado del videoclub, con su póster (de la caché de la lista) y lo que llevas. */
@Composable
private fun FilaSeguir(seguir: List<Progreso>, usables: List<Lista>, alFicha: (Ficha) -> Unit) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val ancho = if (esAncha()) 140.dp else 108.dp
    // Los pósteres y las fichas, de la caché del videoclub (sin red)
    val imagenes by produceState(emptyMap<String, String>(), seguir) {
        value = withContext(Dispatchers.IO) {
            seguir.associate { p ->
                val l = usables.firstOrNull { it.id == p.lista }
                p.video to (l?.let { if (p.esEpisodio) Tele.almacen.serie(it, p.serie)?.poster else Tele.almacen.pelicula(it, p.id)?.poster }.orEmpty())
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Antetitulo("Seguir viendo"); Filete(1.dp); Spacer(Modifier.height(12.dp))
        LazyRow(Modifier.fillMaxWidth().sangrado(8.dp), contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(seguir, key = { it.lista + it.video }) { p ->
                val sub = if (p.esEpisodio) "T${p.temporada} · E${p.episodio}" + if (p.visto) " · vista" else "" else "quedan ${duracion(((p.dur - p.pos) / 60_000L).toInt().coerceAtLeast(1))}"
                TarjetaPoster(imagenes[p.video].orEmpty(), if (p.esEpisodio) p.tituloSerie else p.titulo, ancho, sub, if (p.visto) null else p.fraccion) {
                    val l = usables.firstOrNull { it.id == p.lista } ?: return@TarjetaPoster
                    ambito.launch {
                        val f = withContext(Dispatchers.IO) {
                            if (p.esEpisodio) Tele.almacen.serie(l, p.serie)?.let { Ficha.DeSerie(l, it) } else Tele.almacen.pelicula(l, p.id)?.let { Ficha.DePelicula(l, it) }
                        }
                        if (f != null) alFicha(f)
                        else if (!p.esEpisodio) Reproducir.pon(ctx, Encargo.DePelicula(l, com.gonzalo.iptv.Pelicula(p.id, p.titulo, extension = p.extension)))
                    }
                }
            }
        }
    }
}

/** Un canal en una tarjeta (los favoritos): su logo grande, su nombre y lo que echan ahora, con lo que va. */
@Composable
fun TarjetaCanal(l: Lista, c: Canal, cg: Cargada?, alAbrir: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val foco by src.collectIsFocusedAsState()
    val escala by androidx.compose.animation.core.animateFloatAsState(if (foco) 1.05f else 1f, label = "canal")
    val ancho = if (esAncha()) 176.dp else 140.dp
    val (ahora, _) = cg?.let { Tele.ahoraYDespuesRapido(it, c) } ?: (null to null)
    Column(Modifier.width(ancho).tocableCon(src) { alAbrir() }) {
        Box(Modifier.scale(escala).fillMaxWidth().aspectRatio(16f / 10f).background(Crudo), contentAlignment = Alignment.Center) {
            Logo(c.logo, c.nombre, Modifier, ancho * 0.42f)
            if (ahora != null) Barra(ahora.avance(System.currentTimeMillis()), Modifier.align(Alignment.BottomStart))
        }
        Text(c.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text((ahora?.titulo ?: l.nombre).uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
