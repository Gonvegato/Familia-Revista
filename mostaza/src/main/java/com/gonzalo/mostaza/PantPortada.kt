package com.gonzalo.mostaza

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Calendar
import java.util.Locale

/** Una fila de catálogo de la portada: de qué addon y catálogo es, y lo que trae. */
class FilaCatalogo(val addon: Addon, val catalogo: Catalogo, val cosas: List<MetaBreve>) {
    val clave: String get() = "${addon.manifiesto.id}|${catalogo.tipo}|${catalogo.id}"
    val titulo: String get() = "${catalogo.nombre} · ${tiposNombre(catalogo.tipo)}"
}

/** Los catálogos de la portada (los que elegiste o los primeros de tus addons), con una caché corta en memoria. */
object Catalogos {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<MetaBreve>>>()
    /** En las pruebas de pantallas: filas fijas. */
    @Volatile var fijas: List<FilaCatalogo>? = null

    fun mostrables(a: Addon): List<Catalogo> = a.manifiesto.catalogos.filter { !it.soloBusqueda && !it.generoObligatorio && !it.necesitaOtraCosa }

    suspend fun lista(a: Addon, c: Catalogo, extras: List<Pair<String, String>> = emptyList()): List<MetaBreve> {
        val k = a.url.hashCode().toString() + "|" + c.tipo + "|" + c.id + "|" + extras.joinToString("&") { it.first + "=" + it.second }
        cache[k]?.let { (t, l) -> if (System.currentTimeMillis() - t < 30 * 60_000L) return l }
        val l = runCatching { Addons.catalogo(a, c, extras) }.getOrDefault(emptyList())
        if (l.isNotEmpty()) cache[k] = System.currentTimeMillis() to l
        return l
    }

    suspend fun portada(ctx: android.content.Context): List<FilaCatalogo> {
        fijas?.let { return it }
        val addons = Addons.conManifiesto()
        val elegidas = AjustesPortada.filas(ctx)
        val pares = if (elegidas.isNotEmpty()) elegidas.mapNotNull { k ->
            val (aid, tipo, cid) = k.split('|').let { if (it.size < 3) return@mapNotNull null else Triple(it[0], it[1], it[2]) }
            addons.firstOrNull { it.manifiesto.id == aid }?.let { a -> a.manifiesto.catalogos.firstOrNull { it.tipo == tipo && it.id == cid }?.let { a to it } }
        } else addons.flatMap { a -> mostrables(a).map { a to it } }.take(AjustesPortada.cuantas(ctx))
        return coroutineScope {
            pares.map { (a, c) -> async { FilaCatalogo(a, c, lista(a, c)) } }.awaitAll().filter { it.cosas.isNotEmpty() }
        }
    }
}

/**
 * La portada de Mostaza: la cabecera y el menú de las hermanas; «Seguir viendo» (lo que estás viendo, con lo que llevas), «Pendientes»
 * y las filas de los catálogos de tus addons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantPortada(alAbrir: (MetaBreve) -> Unit, alIr: (Destino) -> Unit) {
    val ctx = LocalContext.current
    val hoy = remember { Calendar.getInstance() }
    val titulos = Biblioteca.titulos.toList()
    Biblioteca.version
    // (el mismo orden que los widgets)
    val viendo = Biblioteca.seguirViendo(titulos)
    val pendientes = Biblioteca.pendientes(titulos)
    Tmdb.version
    val conTmdb = Tmdb.activo
    // Con TMDB, la portada es la de una plataforma (sus filas); sin TMDB, los catálogos de tus addons
    val porqueViste = titulos.filter { it.estado == Estado.VISTO || it.estado == Estado.VIENDO }.maxByOrNull { maxOf(it.ultima, it.fin) }?.breve()
    val filasTmdb by produceState<List<FilaTmdb>?>(null, conTmdb, Tmdb.version, porqueViste?.id) {
        value = if (!conTmdb) emptyList() else runCatching { Tmdb.portada(porqueViste) }.getOrDefault(emptyList())
    }
    // Lo que tienes en tu kDrive («Mi carpeta/Mostaza»), con su ficha
    val enKDrive by produceState(emptyList<MetaBreve>(), CineKDrive.version, conTmdb) {
        value = runCatching {
            CineKDrive.indice(ctx)
            coroutineScope { CineKDrive.titulos().take(40).map { (id, tipo) -> async { Fichas.carga(tipo, id)?.first?.breve() } }.awaitAll().filterNotNull() }
        }.getOrDefault(emptyList())
    }
    val filas by produceState<List<FilaCatalogo>?>(null, Addons.version, conTmdb) { value = if (conTmdb) emptyList() else runCatching { Catalogos.portada(ctx) }.getOrDefault(emptyList()) }
    val secciones = listOf("Viendo" to Destino.Viendo, "Pendiente" to Destino.Pendiente, "Visto" to Destino.Visto, "Abandonado" to Destino.Abandonado, "Explorar" to Destino.Explorar, "Addons" to Destino.Addons)
    val desplazado = rememberScrollState()
    val umbral = with(LocalDensity.current) { 300.dp.toPx() }
    // (solo cambia al cruzar el umbral: leer el desplazamiento aquí recomponía la portada entera en cada píxel que bajabas)
    val plegada by remember(umbral) { derivedStateOf { desplazado.value > umbral } }
    val m = margen()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Paper).verticalScroll(desplazado).statusBarsPadding().padding(horizontal = m)) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                EnlaceRevista("Buscar") { alIr(Destino.Buscar) }
                EnlaceRevista("Explorar", Modifier.padding(start = 14.dp), acento = true) { alIr(Destino.Explorar) }
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
                    secciones.forEach { (t, x) ->
                        Text(t.uppercase(), color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1,
                            modifier = Modifier.tocable { alIr(x) }.padding(vertical = 9.dp))
                    }
                }
                Filete(1.dp)
            }
            if (!Cuenta.vinculada) {
                Spacer(Modifier.height(14.dp))
                Nota("Sin cuenta todavía: entra en Ajustes → Cuenta (o en Azabache, y Mostaza entra sola). Tu biblioteca, lo que ves y tus addons van con tu cuenta.")
            }
            if (conTmdb) filasTmdb?.firstOrNull()?.cosas?.firstOrNull { it.fondo.isNotBlank() }?.let { Destacado(it, alAbrir) }
            if (viendo.isNotEmpty()) FilaPosters("Seguir viendo", viendo.map { it.breve() }, alAbrir, verTodo = { alIr(Destino.Viendo) },
                sub = { b -> viendo.firstOrNull { it.idStremio == b.id }?.let { seguirTexto(it) }.orEmpty() },
                progreso = { b -> viendo.firstOrNull { it.idStremio == b.id }?.let { Biblioteca.ultimo(it.ref)?.takeIf { p -> !p.visto }?.fraccion } },
                imagen = { b -> viendo.firstOrNull { it.idStremio == b.id }?.imagen ?: b.poster })
            if (pendientes.isNotEmpty()) FilaPosters("Pendientes", pendientes.take(20).map { it.breve() }, alAbrir, verTodo = { alIr(Destino.Pendiente) },
                sub = { b -> tipoNombre(b.tipo) + if (b.ano.isNotBlank()) " · ${b.ano}" else "" }, imagen = { b -> pendientes.firstOrNull { it.idStremio == b.id }?.imagen ?: b.poster })
            if (enKDrive.isNotEmpty()) FilaPosters("En tu kDrive", enKDrive, alAbrir, sub = { tipoNombre(it.tipo) + if (it.ano.isNotBlank()) " · ${it.ano}" else "" })
            if (conTmdb) when (val f = filasTmdb) {
                null -> Nota("Mirando el catálogo…", Modifier.padding(top = 26.dp))
                else -> {
                    if (f.isEmpty()) Nota("TMDB no responde ahora mismo (¿sin conexión?, ¿la clave ya no vale?). Mira en Ajustes → Catálogo.", Modifier.padding(top = 26.dp))
                    f.forEach { fila -> FilaPosters(fila.titulo, fila.cosas.take(30), alAbrir, sub = { it.ano }) }
                }
            } else when (val f = filas) {
                null -> Nota("Mirando tus catálogos…", Modifier.padding(top = 26.dp))
                else -> {
                    Nota("Para que Mostaza tenga su propio catálogo, como una plataforma, pon tu clave de TMDB (gratis) en Ajustes → Catálogo.", Modifier.padding(top = 22.dp).tocable { alIr(Destino.Ajustes) })
                    if (f.isEmpty()) Nota("Tus addons no dan ningún catálogo ahora mismo (sin conexión, o no tienen). Añade addons en «Addons».", Modifier.padding(top = 26.dp))
                    f.forEach { fila -> FilaPosters(fila.titulo, fila.cosas.take(30), alAbrir, sub = { it.ano }) }
                }
            }
            if (titulos.isEmpty() && filas?.isEmpty() == true) {
                Spacer(Modifier.height(10.dp))
                MagLink("Explorar los catálogos", "Películas y series de tus addons", accent = true) { alIr(Destino.Explorar) }
            }
            Text("Mostaza · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 30.dp))
            Text("versión $VERSION", color = Muted.copy(alpha = 0.7f), fontFamily = Body, fontSize = 10.sp, letterSpacing = 0.6.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
            Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
        BarraPlegada("Mostaza", plegada) { EnlaceRevista("Buscar") { alIr(Destino.Buscar) }; EnlaceRevista("Explorar", Modifier.padding(start = 14.dp), acento = true) { alIr(Destino.Explorar) } }
    }
}

/** Lo que se dice de algo que estás viendo: «T2 · E5 · quedan 20 min», «quedan 34 min», «T1 · E3». */
fun seguirTexto(t: Titulo): String {
    val p = Biblioteca.ultimo(t.ref) ?: return if (t.esSerie) "Serie" else "Película"
    val ep = if (p.temporada > 0) "T${p.temporada} · E${p.episodio}" else ""
    val queda = if (!p.visto && p.dur > 0 && p.pos > 0) "quedan ${duracion(((p.dur - p.pos) / 60_000L).toInt().coerceAtLeast(1))}" else if (p.visto) "visto" else ""
    return listOf(ep, queda).filter { it.isNotBlank() }.joinToString(" · ")
}
