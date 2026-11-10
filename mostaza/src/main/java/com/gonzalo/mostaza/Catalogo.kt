package com.gonzalo.mostaza

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Las fichas: de TMDB si tienes tu clave puesta (en castellano, con reparto, tráiler, dónde verla y parecidas); si no, o si TMDB no la
 * tiene, la de tus addons. En una serie, los episodios son los de tus addons (es la numeración que usan para las fuentes, la de IMDb),
 * con el título, la imagen y el resumen de TMDB en castellano cuando coinciden temporada y episodio; si tus addons no los tienen, los de
 * TMDB.
 */
object Fichas {
    /** En las pruebas de pantallas: fichas fijas (sin red). */
    @Volatile var fijas: Map<String, Meta> = emptyMap()
    fun fija(id: String): Meta? = fijas[id]

    private val cache = ConcurrentHashMap<String, Pair<Meta, Extras?>>()
    fun deCache(id: String): Pair<Meta, Extras?>? = fijas[id]?.let { it to null } ?: cache[id]

    suspend fun carga(tipo: String, id: String): Pair<Meta, Extras?>? {
        deCache(id)?.let { return it }
        val r = coroutineScope {
            val t = async { runCatching { Tmdb.meta(id, tipo) }.getOrNull() }
            val a = async { if (TmdbDatos.deId(id) != null) null else runCatching { Addons.meta(tipo, id) }.getOrNull() }
            junta(t.await(), a.await())
        } ?: return null
        cache[id] = r
        if (r.first.id != id) cache[r.first.id] = r
        return r
    }

    /** Junta la de TMDB [t] y la de tus addons [a] (ver arriba). */
    fun junta(t: Pair<Meta, Extras>?, a: Meta?): Pair<Meta, Extras?>? {
        if (t == null) return a?.let { it to null }
        if (a == null || a.videos.isEmpty()) return t
        val deTmdb = t.first.videos.associateBy { it.temporada to it.episodio }
        val eps = a.videos.map { v ->
            deTmdb[v.temporada to v.episodio]?.let { e -> v.copy(titulo = e.titulo.ifBlank { v.titulo }, resumen = e.resumen.ifBlank { v.resumen }, miniatura = e.miniatura.ifBlank { v.miniatura }, estreno = if (v.estreno > 0) v.estreno else e.estreno) } ?: v
        }
        return t.first.copy(id = a.id.ifBlank { t.first.id }, videos = eps) to t.second
    }
}

/** El destacado de la portada: la imagen grande de lo primero de «Tendencias», con su título, y a la ficha al tocarlo. */
@Composable
fun Destacado(b: MetaBreve, alAbrir: (MetaBreve) -> Unit) {
    val ancha = esAncha()
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (ancha) 2.4f else 16f / 10f).clip(esquina(3.dp)).border(1.dp, Line, esquina(3.dp)).tocable { alAbrir(b) }) {
            AsyncImage(b.fondo.ifBlank { b.poster }, b.nombre, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.85f))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text("DESTACADO · ${tipoNombre(b.tipo).uppercase()}", color = Acento, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(b.nombre, color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = if (ancha) 40.sp else 28.sp, lineHeight = if (ancha) 42.sp else 30.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val datos = listOfNotNull(b.ano.ifBlank { null }, b.generos.take(2).joinToString(", ").ifBlank { null }, b.nota.ifBlank { null }?.let { "★ $it" })
                if (datos.isNotEmpty()) Text(datos.joinToString(" · "), color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (b.descripcion.isNotBlank()) Text(b.descripcion, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Dónde verla en España (suscripción, gratis o con anuncios), con el aviso de JustWatch. */
@Composable
fun DondeVer(p: List<Plataforma>) {
    if (p.isEmpty()) return
    Antetitulo("Dónde verla en España", Modifier.padding(top = 14.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        p.take(6).forEach { x ->
            if (x.logo.isNotBlank()) AsyncImage(x.logo, x.nombre, Modifier.size(38.dp).clip(esquina(6.dp)).border(1.dp, Line, esquina(6.dp)))
            else Text(x.nombre, color = Charcoal, fontFamily = Body, fontSize = 12.sp)
        }
    }
    Text(p.take(6).joinToString(" · ") { it.nombre } + ". Datos de JustWatch.", color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
}

/** El reparto, con sus fotos. */
@Composable
fun RepartoFila(ps: List<Persona>) {
    if (ps.isEmpty()) return
    Antetitulo("Reparto", Modifier.padding(top = 16.dp, bottom = 8.dp))
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ps, key = { it.nombre + it.papel }) { x ->
            Column(Modifier.width(84.dp)) {
                Box(Modifier.size(84.dp, 112.dp).clip(esquina(3.dp)).background(Line)) {
                    if (x.foto.isNotBlank()) AsyncImage(x.foto, x.nombre, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Text(x.nombre, color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                if (x.papel.isNotBlank()) Text(x.papel, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** El tráiler (en YouTube, o en la app que tengas para eso). */
@Composable
fun EnlaceTrailer(claves: List<String>) {
    val ctx = LocalContext.current
    val k = claves.firstOrNull() ?: return
    MagLink("Ver el tráiler", "En YouTube") { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$k")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

/**
 * «Explorar» con el catálogo de TMDB: películas o series, por género, por plataforma (las de suscripción en España) y por orden, como
 * en cualquier plataforma. Sin addons de por medio.
 */
@Composable
fun PantExplorarTmdb(alVolver: () -> Unit, alAbrir: (MetaBreve) -> Unit) {
    val ambito = rememberCoroutineScope()
    var tv by rememberSaveableBool(false)
    var genero by remember { mutableLongStateOf(0L) }
    var plataforma by remember { mutableLongStateOf(0L) }
    var orden by remember { mutableStateOf(Tmdb.Orden.POPULARES) }
    val generos by produceState<Map<Long, String>>(emptyMap(), tv, Tmdb.version) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Tmdb.generos(tv) } }
    val plataformas by produceState<List<Plataforma>>(emptyList(), Tmdb.version) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Tmdb.plataformas().take(12) } }
    var cosas by remember { mutableStateOf<List<MetaBreve>?>(null) }
    var pagina by remember { mutableIntStateOf(1) }
    var cargandoMas by remember { mutableStateOf(false) }
    var fin by remember { mutableStateOf(false) }
    LaunchedEffect(tv, genero, plataforma, orden, Tmdb.version) {
        cosas = null; pagina = 1; fin = false
        val l = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Tmdb.descubre(tv, genero, plataforma, orden, 1) }
        cosas = l; fin = l.isEmpty()
    }
    val m = margen()
    val ancho = if (esAncha()) 150.dp else 104.dp
    LazyVerticalGrid(GridCells.Adaptive(ancho), Modifier.fillMaxSize().background(Paper), contentPadding = PaddingValues(start = m, end = m, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                RevistaTopBar(alVolver, "Hathor")
                Antetitulo("El catálogo", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
                FitTitle("Explorar", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(1.dp, Line)
                PestanasRevista(listOf("Películas", "Series"), if (tv) 1 else 0) { i -> tv = i == 1; genero = 0L }
                Spacer(Modifier.height(6.dp))
                Etiqueta("Orden", Modifier.padding(top = 4.dp))
                FiltrosRevista(Tmdb.Orden.entries.map { it.nombre }, orden.ordinal) { i -> orden = Tmdb.Orden.entries[i] }
                if (plataformas.isNotEmpty()) {
                    Etiqueta("Plataforma", Modifier.padding(top = 4.dp))
                    val ops = listOf(0L to "Todas") + plataformas.map { it.id to it.nombre }
                    FiltrosRevista(ops.map { it.second }, ops.indexOfFirst { it.first == plataforma }.coerceAtLeast(0)) { i -> plataforma = ops[i].first }
                }
                if (generos.isNotEmpty()) {
                    Etiqueta("Género", Modifier.padding(top = 4.dp))
                    val ops = listOf(0L to "Todos") + generos.entries.sortedBy { it.value }.map { it.key to it.value }
                    FiltrosRevista(ops.map { it.second }, ops.indexOfFirst { it.first == genero }.coerceAtLeast(0)) { i -> genero = ops[i].first }
                }
                Filete(1.dp, Line)
                if (cosas == null) Nota("Cargando…", Modifier.padding(top = 14.dp))
                else if (cosas!!.isEmpty()) Nota("No hay nada con esto (o TMDB no responde ahora mismo).", Modifier.padding(top = 14.dp))
            }
        }
        items(cosas.orEmpty(), key = { it.tipo + it.id }) { b -> TarjetaPoster(b.poster, b.nombre, Dp.Unspecified, b.ano) { alAbrir(b) } }
        if (cosas?.isNotEmpty() == true && !fin) item(span = { GridItemSpan(maxLineSpan) }) {
            EnlaceRevista(if (cargandoMas) "Cargando…" else "Más →", acento = true) {
                if (cargandoMas) return@EnlaceRevista
                cargandoMas = true
                ambito.launch {
                    val ya = cosas.orEmpty()
                    val mas = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Tmdb.descubre(tv, genero, plataforma, orden, pagina + 1) }.filter { n -> ya.none { it.id == n.id } }
                    pagina++; cosas = ya + mas; fin = mas.isEmpty(); cargandoMas = false
                }
            }
        }
    }
}

@Composable
private fun rememberSaveableBool(v: Boolean) = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(v) }

/** «Ajustes → Catálogo»: tu clave de TMDB (va con tu cuenta, en `/claves`), cómo sacarla, y lo que TMDB y JustWatch piden que se diga. */
@Composable
fun SeccionCatalogo() {
    val ambito = rememberCoroutineScope()
    Tmdb.version
    val puesta = Tmdb.activo
    var texto by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf<String?>(null) }
    var probando by remember { mutableStateOf(false) }
    Antetitulo("El catálogo de Hathor", Modifier.padding(bottom = 4.dp))
    Text(if (puesta) "Con TMDB: la portada, «Explorar», «Buscar» y las fichas son las de una plataforma (tendencias, estrenos, géneros, lo que hay en cada plataforma, reparto, tráileres, en castellano). Tus addons quedan para las fuentes."
        else "Sin TMDB, lo que ves es lo de los catálogos de tus addons. Con tu clave de TMDB (gratis), Hathor tiene su propio catálogo, como una plataforma.",
        color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(8.dp))
    Etiqueta(if (puesta) "Tu clave está puesta (en tu cuenta, solo la puedes leer tú)" else "Cómo sacar tu clave")
    if (!puesta) Text("1. Hazte una cuenta en themoviedb.org.  2. En tu perfil → Ajustes → API, pide una clave (uso personal).  3. Copia el «Token de lectura de la API» (el largo, empieza por «eyJ») y pégalo aquí.",
        color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
    CampoRevista("Token", texto, if (puesta) "Pegar otra para cambiarla" else "eyJhbGciOi…", secreto = true) { texto = it; aviso = null }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        EnlaceRevista(if (probando) "Probando…" else "Guardar", acento = true) {
            val t = texto.trim(); if (t.isBlank() || probando) return@EnlaceRevista
            probando = true
            ambito.launch {
                val r = Tmdb.prueba(t)
                probando = false
                if (r == null) { Tmdb.guarda(t); texto = ""; aviso = "Hecho: el catálogo ya es el de TMDB, aquí y en tus otros dispositivos." } else aviso = r
            }
        }
        if (puesta) EnlaceRevista("Quitarla") { Tmdb.guarda(""); aviso = "Quitada: vuelven los catálogos de tus addons." }
    }
    aviso?.let { Nota(it, Modifier.padding(top = 8.dp)) }
    Spacer(Modifier.height(12.dp)); Filete(1.dp, Line); Spacer(Modifier.height(8.dp))
    Text(Tmdb.AVISO_TMDB + " Lo de dónde ver cada cosa es de JustWatch.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp)
}
