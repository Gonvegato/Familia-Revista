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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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
 * Lo que sale en la portada, lo mismo en el móvil que en la sala de la tele: lo que estás viendo y lo pendiente (en el orden de los
 * widgets), lo de tu kDrive y, con TMDB, las filas del catálogo (si no, las de los catálogos de tus addons). null: aún llegando.
 */
class DatosPortada(
    val titulos: List<Titulo>, val viendo: List<Titulo>, val pendientes: List<Titulo>, val conTmdb: Boolean,
    val filasTmdb: List<FilaTmdb>?, val enKDrive: List<MetaBreve>, val filas: List<FilaCatalogo>?,
) {
    /** El destacado de la portada con TMDB: lo primero de su primera fila que tenga fondo. */
    val destacado: MetaBreve? get() = if (conTmdb) filasTmdb?.firstOrNull()?.cosas?.firstOrNull { it.fondo.isNotBlank() } else null
}

/** Carga (y vuelve a cargar cuando cambian la biblioteca, tus addons, TMDB o tu kDrive) lo de la portada. */
@Composable
fun rememberPortada(): DatosPortada {
    val ctx = LocalContext.current
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
    return DatosPortada(titulos, viendo, pendientes, conTmdb, filasTmdb, enKDrive, filas)
}

/** Lo último que viste (para el cartel): lo que estás viendo, lo último que tocaste primero; si no ves nada, lo último con algo visto. */
fun ahoraVes(titulos: List<Titulo>): Titulo? =
    Biblioteca.seguirViendo(titulos).firstOrNull()
        ?: titulos.filter { it.estado != Estado.PENDIENTE && Biblioteca.ultimo(it.ref) != null }.maxByOrNull { ultimaVez(it) }

/** Qué dice el botón grande de debajo del cartel. */
fun textoVer(t: Titulo): String {
    val p = Biblioteca.ultimo(t.ref)
    return when {
        t.estado == Estado.VISTO -> "Volver a ver"
        p != null && (p.pos > 60_000L || p.visto || t.estado == Estado.VIENDO) -> "Seguir viendo"
        else -> "Ver"
    }
}

/**
 * La portada de Hathor, la primera página (la cabecera y las pestañas son de [PantPrincipal]), como la de Seshat y Bastet: las portadas mandan.
 *  - El cartel «AHORA VES»: lo último que viste (película o episodio, con «T2 · E5» y lo que queda), su portada grande con paspartú sobre su
 *    fondo (el elegido o el de TMDB) o su color; debajo, «SEGUIR VIENDO →», que hace lo de siempre (abre la ficha y sigue donde lo dejaste).
 *  - El reto del año (películas y series terminadas), la semana en barras que se tocan y los días seguidos.
 *  - Próximos capítulos (los de TMDB, como el widget), «También ves» y «Pendientes» (lado a lado si hay pocos) y el mosaico de lo terminado
 *    este año. Debajo, lo de siempre: tu kDrive y las filas del catálogo (TMDB o tus addons).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaginaPortada(alAbrir: (Titulo) -> Unit, alAbrirBreve: (MetaBreve) -> Unit, alVer: (Titulo) -> Unit, alIr: (Destino) -> Unit, alReto: () -> Unit = {}) {
    val ctx = LocalContext.current
    val d = rememberPortada()
    val titulos = d.titulos
    val ahora = remember(titulos, Biblioteca.version) { ahoraVes(titulos) }
    val otros = d.viendo.filter { it.ref != ahora?.ref }
    val pendientes = d.pendientes.filter { it.ref != ahora?.ref }.take(12)
    val nPendientes = d.pendientes.size
    val semana = remember(Biblioteca.version) { datosCine(Biblioteca.ratos()) }
    val meta = remember(titulos, EspejoCuenta.version, LaFamiliaVersion()) { RetoCine.meta(ctx) }
    val ano = remember { anoDe(System.currentTimeMillis()) }
    val terminadas = terminadasEn(titulos, ano)
    val proximos = remember(Biblioteca.version) { Proximos.lista(ctx) }
    val tablet = LocalConfiguration.current.screenWidthDp >= 700
    Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 960.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!Cuenta.vinculada) Nota("Sin cuenta todavía: entra en Ajustes → Cuenta (o en Jonsu, y Hathor entra sola). Tu biblioteca, lo que ves y tus addons van con tu cuenta.",
            Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp))
        if (titulos.isEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text("Hathor es tu cine: películas y series de tus addons, de tus torrents y de tu kDrive, con lo que ves apuntado. Lo que veas 10 minutos entra solo en «Viendo».",
                color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(12.dp))
            BotonC("Explorar", fondo = AcentoBloque, letra = SobreBloque) { alIr(Destino.Explorar) }
            Spacer(Modifier.height(8.dp))
            MagLink("Buscar", "Una película o una serie por su nombre") { alIr(Destino.Buscar) }
            Filete(1.dp, Line)
            MagLink("Addons", "Las fuentes (con tu debrid) y los catálogos") { alIr(Destino.Addons) }
        } else {
            // ---- El cartel: lo que ves ahora, de protagonista; debajo, el botón a todo el ancho
            if (ahora != null) {
                val p = Biblioteca.ultimo(ahora.ref)
                val kicker = (if (ahora.estado == Estado.VIENDO) "Ahora ves" else "Lo último") + (p?.takeIf { it.temporada > 0 }?.let { " · T${it.temporada} · E${it.episodio}" } ?: "")
                Cartel(ahora, kicker, alAbrir = { alAbrir(ahora) })
                BotonC(textoVer(ahora), Modifier.testTag("seguir"), fondo = AcentoBloque, letra = SobreBloque) { alVer(ahora) }
            }
            // ---- El reto del año y la semana, justo debajo del botón
            BloqueReto(meta, terminadas.size, ano, alIr = { alIr(Destino.Visto) }, alPoner = alReto)
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp), verticalAlignment = Alignment.Bottom) {
                Text("Esta semana", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text("${minutosCorto(semana.semana.sumOf { it.second })} · HOY ${minutosCorto(semana.hoyMin)}", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1)
            }
            SemanaTocable(semana, Modifier.padding(horizontal = 20.dp))
            if (semana.racha > 0) Text("${semana.racha} ${if (semana.racha == 1) "DÍA SEGUIDO" else "DÍAS SEGUIDOS"} VIENDO ALGO", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black,
                fontSize = 10.5.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))

            // ---- Próximos capítulos (los primeros; todos, en su página)
            if (proximos.isNotEmpty()) {
                TituloEstante("Próximos capítulos", "${proximos.size} →") { alIr(Destino.Proximos) }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    proximos.take(3).forEach { x ->
                        val t = titulos.firstOrNull { it.ref == x.ref }
                        Row(Modifier.fillMaxWidth().tocable(enabled = t != null) { t?.let(alAbrir) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(x.serie, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Proximos.episodio(x).uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            x.dia?.let { dd -> Text(Proximos.cuando(dd).uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp)) }
                        }
                        Filete(1.dp, Line)
                    }
                }
            }

            // ---- También ves y Pendientes: con pocos (tres portadas entre los dos, como mucho), lado a lado; si no, cada uno en su fila
            val juntos = otros.isNotEmpty() && pendientes.isNotEmpty() && otros.size + pendientes.size <= 3
            if (juntos) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                    val n = otros.size + pendientes.size
                    val ancho = ((maxWidth.value - 18f - 10f * (n - 2)) / n).coerceIn(72f, 120f).toInt()
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Column {
                            EncabezadoCorto("También ves", "${otros.size} →") { alIr(Destino.Viendo) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { otros.forEach { t -> TapaConPie(t, ancho, alAbrir) } }
                        }
                        Column {
                            EncabezadoCorto("Pendientes", "$nPendientes →") { alIr(Destino.Pendiente) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { pendientes.forEach { t -> TapaConPie(t, ancho, alAbrir) } }
                        }
                    }
                }
            } else {
                if (otros.isNotEmpty()) {
                    TituloEstante("También ves", "${otros.size} →") { alIr(Destino.Viendo) }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        otros.take(12).forEach { t -> TapaConPie(t, if (tablet) 150 else 120, alAbrir) }
                    }
                }
                TituloEstante("Pendientes", if (nPendientes == 0) null else "$nPendientes →") { alIr(Destino.Pendiente) }
                if (pendientes.isEmpty()) Text("Nada pendiente. En la ficha de cualquier película o serie, «+ Añadir».", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(horizontal = 20.dp))
                else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pendientes.forEach { t -> TapaConPie(t, if (tablet) 130 else 104, alAbrir) }
                }
            }

            // ---- Tu año: el mosaico de lo terminado (el reto, con su barra, ya va arriba)
            TituloEstante("Tu $ano · ${terminadas.size} ${if (terminadas.size == 1) "terminada" else "terminadas"}", "Vistas →") { alIr(Destino.Visto) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                if (terminadas.isEmpty()) Text("Aún no has terminado nada este año. Una película está vista al 90 %; una serie, con todos sus episodios estrenados vistos.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
                else MosaicoTerminadas(terminadas, alAbrir) { alIr(Destino.Visto) }
            }
        }

        // ---- Lo de siempre: tu kDrive y el catálogo (TMDB o tus addons)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            if (d.enKDrive.isNotEmpty()) FilaPosters("En tu kDrive", d.enKDrive, alAbrirBreve, sub = { tipoNombre(it.tipo) + if (it.ano.isNotBlank()) " · ${it.ano}" else "" })
            if (d.conTmdb) when (val f = d.filasTmdb) {
                null -> Nota("Mirando el catálogo…", Modifier.padding(top = 26.dp))
                else -> {
                    if (f.isEmpty()) Nota("TMDB no responde ahora mismo (¿sin conexión?, ¿la clave ya no vale?). Mira en Ajustes → Catálogo.", Modifier.padding(top = 26.dp))
                    f.forEach { fila -> FilaPosters(fila.titulo, fila.cosas.take(30), alAbrirBreve, sub = { it.ano }) }
                }
            } else when (val f = d.filas) {
                null -> Nota("Mirando tus catálogos…", Modifier.padding(top = 26.dp))
                else -> {
                    Nota("Para que Hathor tenga su propio catálogo, como una plataforma, pon tu clave de TMDB (gratis) en Ajustes → Catálogo.", Modifier.padding(top = 22.dp).tocable { alIr(Destino.Ajustes) })
                    if (f.isEmpty()) Nota("Tus addons no dan ningún catálogo ahora mismo (sin conexión, o no tienen). Añade addons en «Addons».", Modifier.padding(top = 26.dp))
                    f.forEach { fila -> FilaPosters(fila.titulo, fila.cosas.take(30), alAbrirBreve, sub = { it.ano }) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Filete(2.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                EnlaceRevista("Explorar →", acento = true) { alIr(Destino.Explorar) }
                EnlaceRevista("Addons →") { alIr(Destino.Addons) }
            }
            if (Hermanas.azabache(ctx)) { Filete(1.dp, Line); MagLink("Abrir Jonsu", "Valora lo que acabas de ver") { Hermanas.abre(ctx, Hermanas.AZABACHE) } }
            Text("Hathor · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp))
            Text("versión $VERSION", color = Muted.copy(alpha = 0.7f), fontFamily = Body, fontSize = 10.sp, letterSpacing = 0.6.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
            Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
    }
}

/** (Para que el reto se vuelva a leer cuando llega un cambio de la cuenta.) */
@Composable
private fun LaFamiliaVersion(): Any? = com.gonzalo.familia.LaFamilia.cambios

/** El título de un estante de la portada: el nombre en grotesca y, a la derecha y en el acento, «N →» (lleva a su página). */
@Composable
private fun TituloEstante(titulo: String, enlace: String?, alIr: () -> Unit) {
    Row(Modifier.fillMaxWidth().tocable(enabled = enlace != null, onClick = alIr).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 24.sp, maxLines = 2, modifier = Modifier.weight(1f))
        if (enlace != null) Text(enlace.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp, bottom = 3.dp))
    }
}

/** El título pequeño de un estante cuando van dos lado a lado. */
@Composable
private fun EncabezadoCorto(titulo: String, enlace: String, alIr: () -> Unit) {
    Row(Modifier.tocable(onClick = alIr).padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
        Text("  $enlace", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/** Una tapa de la portada (2:3, recta) con su pie: por dónde vas (lo que ves) o «película · 2024» (lo pendiente). */
@Composable
private fun TapaConPie(t: Titulo, ancho: Int, alAbrir: (Titulo) -> Unit) {
    Column(Modifier.width(ancho.dp).tocable { alAbrir(t) }.testTag("loseta-" + t.ref)) {
        Box(Modifier.width(ancho.dp).height((ancho * 1.5f).dp)) { PortadaTitulo(t, Modifier.matchParentSize()) }
        val p = Biblioteca.ultimo(t.ref)
        if (t.estado == Estado.VIENDO) {
            if (p != null && !p.visto && p.fraccion > 0f) Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(4.dp).background(Line)) { Box(Modifier.fillMaxWidth(p.fraccion).fillMaxHeight().background(Acento)) }
            // Corto, para que quepa bajo el póster: «T1 · E3» o «QUEDAN 1 H 48» (sin «min», que se cortaba)
            val corto = porDonde(p, t.esSerie).split(" · ").let { ps -> ps.filter { it.matches(Regex("T\\d+|E\\d+")) }.joinToString(" · ").ifBlank { ps.joinToString(" · ") } }
            Text((if (" h " in corto) corto.removeSuffix(" min") else corto).uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.6.sp, lineHeight = 14.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
        } else Text(lineaTipo(t).uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 0.4.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}

/** El mosaico de lo terminado este año: 4 por fila, como mucho 3 filas; si hay más, la última casilla dice «+N» y lleva a Vistas. */
@Composable
private fun MosaicoTerminadas(l: List<Titulo>, alAbrir: (Titulo) -> Unit, alVerTodas: () -> Unit) {
    val col = 4
    val max = col * 3
    val sobran = (l.size - max).coerceAtLeast(0)
    val vistos = if (sobran > 0) l.take(max - 1) else l
    val casillas: List<Titulo?> = vistos + if (sobran > 0) listOf(null) else emptyList()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        casillas.chunked(col).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                fila.forEach { t ->
                    Box(Modifier.weight(1f).aspectRatio(2f / 3f)) {
                        if (t != null) Box(Modifier.fillMaxSize().tocable { alAbrir(t) }) { PortadaTitulo(t, Modifier.matchParentSize()) }
                        else Box(Modifier.fillMaxSize().background(Charcoal).tocable(onClick = alVerTodas), contentAlignment = Alignment.Center) {
                            Text("+${sobran + 1}", color = Paper, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1)
                        }
                    }
                }
                repeat(col - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * El cartel de la portada: lo que ves ahora, como un cartel. Su portada grande (con paspartú) sobre su fondo (el elegido o el de TMDB; la
 * portada, arriba a la izquierda) o sobre su color, oscurecido abajo; encima, abajo, el antetítulo («AHORA VES · T2 · E5»), el título en
 * grotesca, la barra de lo que llevas y «serie · 2022» · lo que queda. Un toque abre la ficha.
 */
@Composable
private fun Cartel(t: Titulo, kicker: String, alAbrir: () -> Unit) {
    val base = remember(t.portada, t.nombre) { colorDePortada(t.portada) ?: telaDe(t.nombre) }
    val claro = lerpColor(base, Color.White, 0.12f)
    val oscuro = lerpColor(base, Color.Black, 0.72f)
    val acentoClaro = acentoLegible(AcentoBloque, Color(0xFF050404), Color.White, 4.5f)
    val p = Biblioteca.ultimo(t.ref)
    Box(Modifier.fillMaxWidth().height(ALTO_CARTEL).background(Brush.verticalGradient(listOf(claro, oscuro))).tocable(onClick = alAbrir).testTag("cartel")) {
        val fondo = t.fondoVer
        val conFondo = fondo.isNotBlank()
        if (conFondo) {
            // Con fondo: ocupa el cartel (recortado) y la portada va encima, grande, arriba a la izquierda
            ImagenEncuadrada(fondo, t.encFondo, Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))))
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp).background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Color(0xCC050404), 1f to Color(0xF2050404))))
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = if (conFondo) 20.dp else 16.dp, bottom = 14.dp, start = 20.dp, end = 20.dp),
                contentAlignment = if (conFondo) Alignment.TopStart else Alignment.TopCenter) {
                val tope = if (conFondo) 225.dp else 330.dp
                PortadaSobreFondo(t, Modifier.heightIn(max = tope).fillMaxHeight().aspectRatio(2f / 3f, matchHeightConstraintsFirst = true), sombra = 22.dp)
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                Text(kicker.uppercase(), color = acentoClaro, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.nombre.uppercase(), color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 33.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp))
                if (p != null && !p.visto && p.fraccion > 0f) Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.22f))) { Box(Modifier.fillMaxWidth(p.fraccion).fillMaxHeight().background(acentoClaro)) }
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(listOfNotNull(if (t.esSerie) "serie" else "película", t.ano.ifBlank { null }).joinToString(" · ").uppercase(), color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Black,
                        fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val queda = when {
                        p == null -> ""
                        !p.visto && p.dur > 0 && p.pos > 0 -> "quedan " + duracion(((p.dur - p.pos) / 60_000L).toInt().coerceAtLeast(1))
                        else -> haceCuanto(p.ultima)
                    }
                    if (queda.isNotBlank()) Text(queda.uppercase(), color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

/** El reto del año: «Reto 2026 · 5 / 20», su barra y cómo vas. Sin reto, una invitación a ponértelo (en Ajustes). */
@Composable
private fun BloqueReto(meta: Int, hechos: Int, ano: Int, alIr: () -> Unit, alPoner: () -> Unit) {
    Column(Modifier.fillMaxWidth().tocable(onClick = if (meta > 0) alIr else alPoner).testTag("reto").padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Reto $ano", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (meta > 0) {
                Text("$hechos", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(" / $meta", color = Muted, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        if (meta > 0) {
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(9.dp).background(Line)) {
                Box(Modifier.fillMaxWidth((hechos.toFloat() / meta).coerceIn(0f, 1f)).fillMaxHeight().background(Acento))
            }
            Text(ritmoReto(meta, hechos).texto(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.sp, lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp))
        } else {
            Text("$hechos ${if (hechos == 1) "película o serie terminada" else "películas y series terminadas"} este año. Ponte un reto y verás cómo vas.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic,
                fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
            Text("PONERTE UN RETO →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Lo que se dice de algo que estás viendo: «T2 · E5 · quedan 20 min», «quedan 34 min», «T1 · E3». */
fun seguirTexto(t: Titulo): String = porDonde(Biblioteca.ultimo(t.ref), t.esSerie)

/**
 * Por dónde vas, de lo último que viste [p]: «T2 · E5 · quedan 20 min», «quedan 34 min», «T1 · E3 · visto». Sin nada visto, «Serie» o
 * «Película». Con [porcentaje], en una película también lo que llevas: «40 % · quedan 1 h 48 min».
 */
fun porDonde(p: Progreso?, esSerie: Boolean, porcentaje: Boolean = false): String {
    if (p == null) return if (esSerie) "Serie" else "Película"
    val ep = if (p.temporada > 0) "T${p.temporada} · E${p.episodio}" else ""
    val pct = if (porcentaje && ep.isEmpty() && !p.visto && p.dur > 0 && p.pos > 0) "${(p.fraccion * 100).toInt()} %" else ""
    val queda = if (!p.visto && p.dur > 0 && p.pos > 0) "quedan ${duracion(((p.dur - p.pos) / 60_000L).toInt().coerceAtLeast(1))}" else if (p.visto) "visto" else ""
    return listOf(ep, pct, queda).filter { it.isNotBlank() }.joinToString(" · ")
}
