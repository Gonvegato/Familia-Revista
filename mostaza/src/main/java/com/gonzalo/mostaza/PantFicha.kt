package com.gonzalo.mostaza

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Buscar las fuentes de un vídeo en tus addons y elegir la mejor (sin reproducir todavía). */
object Busqueda {
    /** En las pruebas de pantallas: fuentes fijas. */
    @Volatile var fijas: List<Candidata>? = null

    suspend fun fuentes(tipo: String, videoId: String, alLlegar: (List<Candidata>) -> Unit = {}): List<Candidata> = fijas ?: Addons.fuentes(tipo, videoId, alLlegar)

    fun elige(c: Context, cs: List<Candidata>, m: Meta?, grupo: String = ""): Eleccion =
        Conexion.red(c).let { red -> Elector.elige(cs, AjustesElector.prefs(c), red, Conexion.capacidades(c), m?.minutos ?: 0, Torrents.usables(c, red), grupo, MemoriaPistas.busca(c), FuentesRotas.busca(c)) }

    /**
     * «Reproducir» (en la ficha del móvil y en la de la sala): pregunta a tus addons por las fuentes de [v] (o de la película) y elige la
     * mejor para ti; si hay una que se puede poner sola, la pone. Devuelve la elección (para decir cuál ha sido; sin elegida, hay que
     * enseñar la lista) y el aviso si no se ha podido poner.
     */
    suspend fun reproduce(c: Context, tipo: String, b: MetaBreve, t: Titulo?, m: Meta?, v: Video?): Pair<Eleccion, String?> {
        val cs = runCatching { fuentes(tipo, v?.id ?: (t?.idStremio ?: b.id)) }.getOrDefault(emptyList())
        val e = elige(c, cs, m)
        val x = e.elegida ?: return e to null
        return e to Reproducir.pon(c, t?.breve() ?: b, m, v, x, e.ordenadas, automatica = true)
    }
}

/**
 * La ficha de una película o una serie: el fondo, el póster, el titular, lo que dice su addon (año, duración, géneros, reparto, dirección,
 * descripción), dónde está en tu biblioteca (y «+ Añadir» o «Quitar»), «Reproducir» (la mejor fuente para ti, y cuál ha sido) y, en una
 * serie, sus temporadas y episodios con lo visto. Encima, si se pide, la lista de todas las fuentes ([PantFuentes]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantFicha(b: MetaBreve, alCerrar: () -> Unit, alAbrir: (MetaBreve) -> Unit, verAlAbrir: Boolean = false, alVisto: () -> Unit = {}) {
    // Lo que viene del catálogo de TMDB («tmdb:movie:…»): primero su ficha, para saber su id de IMDb (el de tus addons y tu biblioteca)
    if (TmdbDatos.deId(b.id) == null) { PantFichaDe(b, alCerrar, alAbrir, verAlAbrir, alVisto); return }
    var hecho by remember(b.id) { mutableStateOf(Fichas.deCache(b.id) != null) }
    val r by produceState(Fichas.deCache(b.id), b.id) { if (value == null) { value = runCatching { Fichas.carga(b.tipo, b.id) }.getOrNull() }; hecho = true }
    val x = r
    if (x == null) {
        Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().padding(horizontal = margen())) {
            RevistaTopBar(alCerrar, "Volver")
            FitTitle(b.nombre, 38.sp, 41.sp, letterSpacing = (-1).sp, maxLines = 4, minSize = 18f, modifier = Modifier.padding(top = 18.dp))
            Nota(if (hecho) "No se ha podido leer la ficha (¿sin conexión?)." else "Leyendo la ficha…", Modifier.padding(top = 12.dp))
        }
        return
    }
    PantFichaDe(b.copy(id = x.first.id, tipo = x.first.tipo), alCerrar, alAbrir, verAlAbrir, alVisto)
}

/** El encabezado de una sección de la ficha: su número (01, 02…) en el acento y el nombre en grotesca; a la derecha, su acción si la tiene. */
@Composable
private fun SeccionNumerada(n: Int, texto: String, accion: Pair<String, () -> Unit>? = null, arriba: androidx.compose.ui.unit.Dp = 26.dp) {
    Row(Modifier.fillMaxWidth().padding(top = arriba, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text("%02d".format(n), color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, maxLines = 1)
        Text(texto, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 2, lineHeight = 22.sp, modifier = Modifier.weight(1f).padding(start = 10.dp, bottom = 1.dp))
        accion?.let { (t, f) -> Text(t.uppercase(), color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1,
            modifier = Modifier.tocable(onClick = f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) }
    }
}

/**
 * La etiqueta del estado en la apertura: «VIENDO · T2 · E5», «PENDIENTE», «VISTA · 2 NOV 2026», «ABANDONADA · T1 · E3»; fuera de tu biblioteca,
 * «PELÍCULA» o «SERIE». (En Viendo nunca dice «terminado» ni «visto», aunque el último episodio lo esté.)
 */
fun etiquetaEstado(t: Titulo?, tipo: String): String {
    if (t == null) return tipoNombre(tipo).uppercase()
    val p = Biblioteca.ultimo(t.ref)
    val ep = p?.takeIf { it.temporada > 0 }?.let { "T${it.temporada} · E${it.episodio}" }
    return when (t.estado) {
        Estado.VIENDO -> "VIENDO" + (ep?.let { " · $it" } ?: p?.takeIf { !it.visto && it.fraccion > 0f }?.let { " · ${(it.fraccion * 100).toInt()} %" } ?: "")
        Estado.PENDIENTE -> "PENDIENTE"
        Estado.VISTO -> (if (t.esSerie) "VISTA" else "VISTA") + if (t.fin > 0) " · " + fechaJunta(t.fin).uppercase() else ""
        Estado.ABANDONADO -> "ABANDONADA" + (ep?.let { " · $it" } ?: "")
    }
}

/**
 * La ficha de una película o una serie, como la de Seshat y Bastet («doble página»): su fondo (el elegido o el de TMDB) o el póster ampliado como
 * foto de apertura, con un velo arriba para «← Volver · Imágenes · Editar · Borrar»; la etiqueta del estado pisando su borde, el título enorme y
 * «de <dirección o creadores> · <año>»; el botón principal (VER, SEGUIR VIENDO, VOLVER A VER: la mejor fuente para ti, como siempre) y las
 * secciones numeradas: 01 Tu visionado, 02 Temporadas y capítulos (en una serie), 03 Dónde verlo, 04 La película o la serie, En Jonsu,
 * reparto y «Más como esta». [verAlAbrir]: al abrirla, sigue donde lo dejaste («SEGUIR VIENDO →» de la portada).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PantFichaDe(b: MetaBreve, alCerrar: () -> Unit, alAbrir: (MetaBreve) -> Unit, verAlAbrir: Boolean = false, alVisto: () -> Unit = {}) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    LaFamiliaCambios()
    Biblioteca.version
    val t = Biblioteca.por(b.id)
    var hecho by remember(b.id) { mutableStateOf(Fichas.deCache(t?.idStremio ?: b.id) != null) }
    val carga by produceState(Fichas.deCache(t?.idStremio ?: b.id), b.id) {
        if (value == null) value = runCatching { Fichas.carga(b.tipo.ifBlank { t?.tipo ?: "movie" }, t?.idStremio ?: b.id) }.getOrNull()
        hecho = true
    }
    val meta = carga?.first
    val extras = carga?.second
    LaunchedEffect(meta, t?.ref) { val m = meta; if (m != null && t != null) Biblioteca.completa(t, m) }
    val m = meta
    val nombre = m?.nombre ?: t?.nombre ?: b.nombre
    val serie = m?.esSerie == true || b.tipo == "series" || t?.esSerie == true
    var temporada by remember(b.id) { mutableIntStateOf(-1) }
    var fuentesDe by remember { mutableStateOf<Pair<Video?, Boolean>?>(null) }
    var buscando by remember { mutableStateOf(false) }
    var elegida by remember { mutableStateOf<Eleccion?>(null) }
    var quitar by remember { mutableStateOf(false) }
    var editar by remember { mutableStateOf(false) }
    var imagenes by remember { mutableStateOf(false) }
    val tipo = b.tipo.ifBlank { m?.tipo ?: t?.tipo ?: "movie" }

    fun reproduce(v: Video?) {
        if (buscando) return
        if (!AjustesElector.auto(ctx)) { fuentesDe = v to true; return }
        buscando = true; elegida = null
        ambito.launch {
            val (e, aviso) = Busqueda.reproduce(ctx, tipo, b, t, m, v)
            buscando = false; elegida = e
            if (e.elegida == null) { fuentesDe = v to true; return@launch }
            aviso?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
        }
    }

    val siguiente = if (serie && m != null && t != null) Biblioteca.siguiente(t, m) else if (serie && m != null) m.videos.filter { it.temporada > 0 }.minWithOrNull(compareBy({ it.temporada }, { it.episodio })) else null
    val prog = if (!serie) Biblioteca.progreso(t?.idStremio ?: b.id) else siguiente?.let { Biblioteca.progreso(it.id) }
    val seguir = prog != null && !prog.visto && prog.pos > 60_000L
    val volver = t?.estado == Estado.VISTO
    val textoBoton = when {
        buscando -> "Buscando fuentes…"
        serie && siguiente != null -> (if (seguir || (t?.estado == Estado.VIENDO && Biblioteca.ultimo(t.ref) != null)) "Seguir viendo" else if (volver) "Volver a ver" else "Ver") + " · T${siguiente.temporada} · E${siguiente.episodio}"
        serie -> if (m == null) "Ver" else "Elige un episodio"
        seguir -> "Seguir viendo"
        volver -> "Volver a ver"
        else -> "Ver"
    }
    fun principal() { if (serie) { if (siguiente != null) reproduce(siguiente) } else reproduce(null) }
    // «SEGUIR VIENDO →» de la portada: en cuanto se sabe qué toca, se pone (lo mismo que tocar el botón)
    LaunchedEffect(verAlAbrir, m != null, hecho) {
        if (verAlAbrir && (m != null || (hecho && !serie))) { alVisto(); principal() }
    }

    val ancha = esAncha()
    val apaisada = ancha && LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val mg = if (ancha) 40.dp else 20.dp
    // La de la apertura: la tuya o, si no está en tu biblioteca, una de paso con lo que se sabe
    val vista = t ?: Titulo(ref = "", id = b.id, tipo = tipo, nombre = nombre, ano = m?.ano ?: b.ano, poster = m?.poster?.ifBlank { null } ?: b.poster, fondo = m?.fondo?.ifBlank { null } ?: b.fondo)
    var nSec = 0
    fun sig() = ++nSec
    Box(Modifier.fillMaxSize().background(Paper).atrapaFoco()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ---- «Doble página»: el fondo (o el póster ampliado) como foto de apertura, a todo el ancho y recortado
            val tela = remember(vista.portada, vista.nombre) { colorDePortada(vista.portada) ?: telaDe(vista.nombre) }
            val fondo = vista.fondoVer.ifBlank { if (t == null) "" else m?.fondo.orEmpty().takeIf { !vista.sinFondo }.orEmpty() }
            val conFondo = fondo.isNotBlank()
            Box(Modifier.fillMaxWidth().height(if (apaisada) 260.dp else ALTO_APERTURA).clipToBounds()
                .background(Brush.verticalGradient(listOf(lerpColor(tela, Color.White, 0.12f), lerpColor(tela, Color.Black, 0.55f)))).testTag("apertura")) {
                if (conFondo) ImagenEncuadrada(fondo, vista.encFondo, Modifier.fillMaxSize(), "Fondo de $nombre")
                else BoxWithConstraints(Modifier.fillMaxSize()) {
                    val w = minOf(maxWidth, 720.dp) + 24.dp
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 30.dp).requiredWidth(w).requiredHeight(w * 1.5f)) { PortadaTitulo(vista, Modifier.matchParentSize()) }
                }
                // Un velo arriba para que se lean «Volver» y las acciones sobre cualquier imagen
                Box(Modifier.fillMaxWidth().height(VELO_APERTURA).background(veloArriba()))
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = mg).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    EnlaceRevista("← Volver", color = Color.White) { alCerrar() }
                    Spacer(Modifier.weight(1f))
                    if (t != null) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        EnlaceRevista("Imágenes", Modifier.testTag("imagenes"), color = Color.White) { imagenes = true }
                        EnlaceRevista("Editar", color = Color.White) { editar = true }
                        EnlaceRevista("Borrar", color = Color.White) { quitar = true }
                    }
                }
            }
            // La etiqueta del estado, en el acento, pisando el borde de la foto; con fondo, el póster (120×180) asoma abajo a la derecha
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(start = mg).offset(y = (-16).dp).background(AcentoBloque).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(etiquetaEstado(t, tipo), color = SobreBloque, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1)
                    }
                    Column(Modifier.fillMaxWidth().padding(start = mg, end = if (conFondo) mg + 136.dp else mg)) {
                        FitTitle(nombre.uppercase(), if (ancha) 52.sp else 38.sp, if (ancha) 54.sp else 40.sp, color = Charcoal, family = Grotesca, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, maxLines = 4, minSize = 20f)
                        val quien = m?.direccion?.take(2)?.joinToString(" y ")?.ifBlank { null }
                        val de = listOfNotNull(quien?.let { "de $it" }, (m?.ano?.ifBlank { null } ?: vista.ano.ifBlank { null }), m?.minutos?.takeIf { it > 0 && !serie }?.let { duracion(it) }).joinToString(" · ")
                        if (de.isNotBlank()) Text(de, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (conFondo) Box(Modifier.align(Alignment.TopEnd).padding(end = mg).width(120.dp).height(116.dp)) {
                    PortadaSobreFondo(vista, Modifier.align(Alignment.BottomCenter).wrapContentHeight(Alignment.Bottom, unbounded = true).requiredHeight(180.dp).fillMaxWidth(), sombra = 14.dp,
                        alTocar = if (t != null) ({ imagenes = true }) else null)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = mg)) {
                Spacer(Modifier.height(16.dp))
                // ---- La acción principal, ancha y en el acento (la mejor fuente para ti, como siempre)
                BotonC(textoBoton, Modifier.testTag("ver"), fondo = AcentoBloque, letra = SobreBloque, enabled = !buscando && (!serie || siguiente != null || m == null)) { principal() }
                elegida?.let { e ->
                    val texto = e.elegida?.let { c -> (if (e.fueraDeIdioma) "Ninguna en tus idiomas: " else "") + c.analisis.resumen().ifBlank { c.fuente.nombre.lineSequence().firstOrNull().orEmpty() } + " · " + c.addon } ?: "Ninguna fuente se puede poner sola"
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(texto, color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        EnlaceRevista("Cambiar", acento = true) { fuentesDe = (if (serie) siguiente else null) to true }
                    }
                }
                if (t == null) {
                    val borrada = Biblioteca.loBorraste(b.id)
                    BotonMarco("+ Añadir a Pendiente", Modifier.padding(top = 10.dp)) { Biblioteca.anade(b, m) }
                    Text(if (borrada) "La quitaste de tu biblioteca: no vuelve sola aunque la veas. «Añadir» la recupera, con lo que tenía."
                        else "Si la ves 10 minutos seguidos, entra sola en «Viendo».", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                }
                val izquierda: @Composable ColumnScope.() -> Unit = {
                    // ---- 01 Tu visionado: lo que llevas, el estado (cambiarlo), favorita y lo visto en total
                    if (t != null) {
                        SeccionNumerada(sig(), "Tu visionado", "Editar" to { editar = true })
                        if (prog != null && prog.pos > 0 && !prog.visto && prog.dur > 0) {
                            Box(Modifier.fillMaxWidth().height(8.dp).background(Line)) { Box(Modifier.fillMaxWidth(prog.fraccion).fillMaxHeight().background(Acento)) }
                            Text(((if (serie && siguiente != null) "T${siguiente.temporada} · E${siguiente.episodio} · " else "") + "vas por ${tiempo(prog.pos)} de ${tiempo(prog.dur)}").uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp,
                                fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        val ms = Biblioteca.msVistos(t.ref)
                        val vistos = if (serie && m != null) Biblioteca.progresos(t.ref).count { it.visto && it.temporada > 0 } else 0
                        val estrenados = if (serie && m != null) m.videos.count { it.temporada > 0 && it.estrenado() } else 0
                        RejillaCifras(listOfNotNull(
                            BloqueCifra(if (ms >= 60_000L) horasCortas(ms).replace(" ", "\u00A0") else "—", "visto en total", Paper, Charcoal),
                            if (serie && estrenados > 0) BloqueCifra("$vistos/$estrenados", "capítulos", Paper, Charcoal) else null,
                            BloqueCifra(when { t.estado == Estado.VISTO && t.fin > 0 -> Formato.fecha(t.fin).substringBeforeLast(' '); t.anadido > 0 -> Formato.fecha(t.anadido).substringBeforeLast(' '); else -> "—" },
                                if (t.estado == Estado.VISTO && t.fin > 0) "terminada" else "añadida", Bloque2, Color.White),
                        ), Modifier.padding(top = 10.dp))
                        MagChoice(listOf(Estado.PENDIENTE, Estado.VIENDO, Estado.VISTO, Estado.ABANDONADO).map { it.nombre }, listOf(Estado.PENDIENTE, Estado.VIENDO, Estado.VISTO, Estado.ABANDONADO).indexOf(t.estado)) { i ->
                            Biblioteca.cambiaEstado(t.ref, listOf(Estado.PENDIENTE, Estado.VIENDO, Estado.VISTO, Estado.ABANDONADO)[i])
                        }
                        EnlaceRevista(if (t.fav) "★ Favorita" else "☆ Favorita", acento = t.fav) { Biblioteca.ponFavorito(t.ref, !t.fav) }
                    }
                    // ---- 02 Temporadas y capítulos (en una serie)
                    if (serie && m != null && m.videos.isNotEmpty()) {
                        val temps = m.temporadas
                        val sel = if (temporada in temps) temporada else (siguiente?.temporada ?: temps.firstOrNull { it > 0 } ?: temps.first())
                        SeccionNumerada(sig(), "Temporadas y capítulos")
                        PestanasRevista(temps.map { if (it == 0) "Especiales" else "Temporada $it" }, temps.indexOf(sel)) { i -> temporada = temps[i] }
                        m.episodios(sel).forEach { v -> FilaEpisodio(v, t, m, alVer = { reproduce(v) }, alFuentes = { fuentesDe = v to true }) }
                    }
                    // ---- 03 Dónde verlo: tus fuentes y, con TMDB, las plataformas
                    SeccionNumerada(sig(), "Dónde verlo", if (!serie) "Todas las fuentes" to { fuentesDe = null to true } else null)
                    Text(if (serie) "Cada capítulo tiene sus fuentes («Fuentes», en su fila). Hathor pone la mejor para ti al tocar el botón."
                        else "Hathor pregunta a tus addons y pone la mejor fuente para ti (tus idiomas, tu red, tu debrid); «Todas las fuentes» las enseña todas.",
                        color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
                    extras?.let { DondeVer(it.plataformas) }
                    // ---- En Jonsu (la valoración, solo se lee)
                    if (t != null) {
                        val (nota, resena) = Biblioteca.valoracion(t.ref)
                        if (nota > 0 || resena.isNotBlank() || Hermanas.azabache(ctx)) {
                            SeccionNumerada(sig(), "En Jonsu", if (Hermanas.azabache(ctx)) "Valorar" to { Hermanas.abreEnAzabache(ctx, t.ref) } else null)
                            if (nota > 0) Row(verticalAlignment = Alignment.Bottom) {
                                Text("$nota", color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 48.sp)
                                Text(" / 100", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                            } else Nota("Sin valorar todavía.")
                            if (resena.isNotBlank()) Text("«$resena»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                val derecha: @Composable ColumnScope.() -> Unit = {
                    // ---- La película o la serie: lo que dice TMDB (o su addon)
                    SeccionNumerada(sig(), if (serie) "La serie" else "La película")
                    if (m == null) Nota(if (!hecho) "Leyendo la ficha…" else "Ninguno de tus addons tiene la ficha de esto (¿sin conexión?).", Modifier.padding(vertical = 8.dp))
                    extras?.lema?.takeIf { it.isNotBlank() }?.let { Text("«$it»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                    m?.descripcion?.takeIf { it.isNotBlank() }?.let { Text(it, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(bottom = 10.dp)) }
                    m?.trailers?.let { EnlaceTrailer(it) }
                    m?.let { x ->
                        if (x.generos.isNotEmpty()) MagFact("Género", x.generos.take(3).joinToString(", "))
                        if (x.direccion.isNotEmpty()) MagFact(if (serie) "Creada por" else "Dirección", x.direccion.take(2).joinToString(", "))
                        if (x.reparto.isNotEmpty()) MagFact("Reparto", x.reparto.take(3).joinToString(", "))
                        if (x.pais.isNotBlank()) MagFact("País", x.pais.split(',').first().trim())
                        if (x.nota.isNotBlank()) MagFact("Nota", "★ " + x.nota)
                    }
                    extras?.let { e ->
                        RepartoFila(e.personas)
                        if (e.similares.isNotEmpty()) FilaPosters("Más como esta", e.similares, alAbrir)
                    }
                }
                if (apaisada) Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(0.5f).padding(end = 28.dp), content = izquierda)
                    Box(Modifier.width(2.dp).fillMaxHeight().background(Charcoal))
                    Column(Modifier.weight(0.5f).padding(start = 28.dp), content = derecha)
                } else Column(Modifier.fillMaxWidth().widthIn(max = 760.dp)) { izquierda(); derecha() }
                Spacer(Modifier.height(30.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
        }
        fuentesDe?.let { (v, _) -> PantFuentes(t?.breve() ?: b, m, v, alCerrar = { fuentesDe = null }) }
    }
    if (imagenes && t != null) SelectorImagenes(t) { imagenes = false }
    if (editar && t != null) AlertDialog(
        onDismissRequest = { editar = false }, containerColor = Paper, shape = esquina(0.dp),
        title = { Text("Editar", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Antetitulo("En tu biblioteca", Modifier.padding(bottom = 2.dp), conCuadro = false)
                MagChoice(listOf(Estado.PENDIENTE, Estado.VIENDO, Estado.VISTO, Estado.ABANDONADO).map { it.nombre }, listOf(Estado.PENDIENTE, Estado.VIENDO, Estado.VISTO, Estado.ABANDONADO).indexOf(t.estado)) { i ->
                    Biblioteca.cambiaEstado(t.ref, listOf(Estado.PENDIENTE, Estado.VIENDO, Estado.VISTO, Estado.ABANDONADO)[i])
                }
                EnlaceRevista(if (t.fav) "★ Favorita" else "☆ Favorita", acento = t.fav) { Biblioteca.ponFavorito(t.ref, !t.fav) }
                Filete(1.dp, Line)
                MagLink("Imágenes", "La portada y el fondo") { editar = false; imagenes = true }
                Filete(1.dp, Line)
                MagLink("Quitar de Hathor", "En todos tus dispositivos y en Jonsu") { editar = false; quitar = true }
            }
        },
        confirmButton = { TextButton({ editar = false }) { Text("LISTO", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
    )
    if (quitar && t != null) AlertDialog(
        onDismissRequest = { quitar = false }, containerColor = Paper, shape = esquina(0.dp),
        title = { Text("¿Quitar «${t.nombre}»?", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold) },
        text = { Text("Se quita de tu biblioteca en todos tus dispositivos y en Jonsu. Es para siempre: aunque la vuelvas a ver, no vuelve sola. Si la añades otra vez, vuelve la misma, con lo que tenía (lo visto y su nota).", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({ quitar = false; Biblioteca.quita(t.ref) }) { Text("QUITAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ quitar = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
    )
}

/** Para que la ficha se repinte con los cambios de la cuenta. */
@Composable
private fun LaFamiliaCambios() { com.gonzalo.familia.LaFamilia.cambios }

/** Fichas fijas para las pruebas de pantallas (sin red). */

/** Un episodio: número y título, cuándo se estrenó, lo que llevas visto y la marca de visto (si la serie está en tu biblioteca). */
@Composable
private fun FilaEpisodio(v: Video, t: Titulo?, m: Meta, alVer: () -> Unit, alFuentes: () -> Unit) {
    val p = Biblioteca.progreso(v.id)
    val estrenado = v.estrenado()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().tocable(enabled = estrenado) { alVer() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (v.episodio > 0) "${v.episodio}" else "·", color = if (p?.visto == true) Muted else Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f)) {
                Text(v.titulo.ifBlank { "Episodio ${v.episodio}" }, color = if (estrenado) Charcoal else Muted, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val linea = listOfNotNull(if (v.estreno > 0) Formato.fecha(v.estreno) else null, if (!estrenado) "aún no se ha estrenado" else null, if (p?.visto == true) "visto" else if (p != null && p.pos > 0 && p.dur > 0) "vas por ${tiempo(p.pos)}" else null)
                if (linea.isNotEmpty()) Text(linea.joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                if (p != null && !p.visto && p.fraccion > 0f) Barra(p.fraccion, Modifier.padding(top = 4.dp))
            }
            if (estrenado) EnlaceRevista("Fuentes", Modifier.padding(start = 8.dp)) { alFuentes() }
            if (t != null) Text(if (p?.visto == true) "✓" else "○", color = if (p?.visto == true) Acento else Muted, fontWeight = FontWeight.Black, fontSize = 20.sp,
                modifier = Modifier.padding(start = 10.dp).tocable { Biblioteca.marcaVisto(t, v, p?.visto != true, m) }.padding(8.dp))
        }
        Filete(1.dp, Line)
    }
}

/**
 * Todas las fuentes de un vídeo, agrupadas por addon (en tu orden), cada una con sus líneas tal como las da su addon, lo que se entiende de
 * ella (calidad, idioma, tamaño) y si es directa, torrent, YouTube o una web. La que elegiría Mostaza va marcada. Tocar una la pone.
 */
@Composable
fun PantFuentes(b: MetaBreve, m: Meta?, v: Video?, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val tipo = b.tipo.ifBlank { if (v != null) "series" else "movie" }
    var todas by remember { mutableStateOf<List<Candidata>>(emptyList()) }
    var terminado by remember { mutableStateOf(false) }
    LaunchedEffect(b.id, v?.id) {
        todas = runCatching { Busqueda.fuentes(tipo, v?.id ?: b.id) { parciales -> todas = parciales } }.getOrDefault(todas)
        terminado = true
    }
    val e = remember(todas) { Busqueda.elige(ctx, todas, m) }
    val mg = margen()
    Box(Modifier.fillMaxSize().background(Paper).atrapaFoco()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = mg)) {
            RevistaTopBar(alCerrar, "Ficha")
            Antetitulo(b.nombre + (v?.let { if (it.temporada > 0) " · T${it.temporada}·E${it.episodio}" else "" } ?: ""), Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
            FitTitle("Fuentes", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
            Filete(3.dp)
            if (!terminado) Nota("Preguntando a tus addons…", Modifier.padding(top = 10.dp))
            if (terminado && todas.isEmpty()) Nota("Ninguno de tus addons da fuentes para esto. Los addons de fuentes los pones tú en «Addons» (con tu debrid configurado en el suyo).", Modifier.padding(top = 10.dp))
            e.elegida?.let { c ->
                Text("Hathor pondría: " + c.analisis.resumen().ifBlank { c.addon } + (if (e.porGrupo) " (la misma clase que el episodio anterior)" else "") + (if (e.fueraDeIdioma) " — ninguna está en tus idiomas" else ""),
                    color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
            val porAddon = todas.groupBy { it.ordenAddon to it.addon }.toSortedMap(compareBy { it.first })
            porAddon.forEach { (k, cs) ->
                Spacer(Modifier.height(18.dp))
                Antetitulo(k.second, Modifier.padding(bottom = 4.dp))
                Filete(1.dp)
                cs.sortedBy { it.ordenEnAddon }.forEach { c -> FilaFuente(c, c.fuente === e.elegida?.fuente) {
                    Reproducir.pon(ctx, b, m, v, c, e.ordenadas)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
                } }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaFuente(c: Candidata, elegida: Boolean, alPoner: () -> Unit) {
    val f = c.fuente
    val marca = when (f.tipo) {
        TipoFuente.DIRECTA -> "Directa"; TipoFuente.TORRENT -> "Torrent"; TipoFuente.YOUTUBE -> "YouTube"; TipoFuente.EXTERNA -> "Web"
        TipoFuente.NZB -> "Usenet"; TipoFuente.ARCHIVO -> "Comprimido"; TipoFuente.NADA -> ""
    }
    Column(Modifier.fillMaxWidth().tocable { alPoner() }.padding(vertical = 12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(marca.uppercase(), color = if (f.tipo == TipoFuente.DIRECTA) SobreAcento else Paper, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                modifier = Modifier.background(if (f.tipo == TipoFuente.DIRECTA) Acento else Charcoal).padding(horizontal = 6.dp, vertical = 2.dp))
            if (elegida) Text("LA MEJOR PARA TI", color = Acento, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.border(1.dp, Acento).padding(horizontal = 6.dp, vertical = 2.dp))
            c.analisis.resumen().takeIf { it.isNotBlank() }?.let { Text(it.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp) }
        }
        if (f.nombre.isNotBlank()) Text(f.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
        if (f.descripcion.isNotBlank()) Text(f.descripcion, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
    }
    Filete(1.dp, Line)
}
