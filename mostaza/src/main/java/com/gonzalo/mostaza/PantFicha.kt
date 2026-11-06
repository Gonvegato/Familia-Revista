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
        Elector.elige(cs, AjustesElector.prefs(c), Conexion.red(c), Conexion.capacidades(c), m?.minutos ?: 0, Torrents.disponible, grupo)
}

/**
 * La ficha de una película o una serie: el fondo, el póster, el titular, lo que dice su addon (año, duración, géneros, reparto, dirección,
 * descripción), dónde está en tu biblioteca (y «+ Añadir» o «Quitar»), «Reproducir» (la mejor fuente para ti, y cuál ha sido) y, en una
 * serie, sus temporadas y episodios con lo visto. Encima, si se pide, la lista de todas las fuentes ([PantFuentes]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantFicha(b: MetaBreve, alCerrar: () -> Unit, alAbrir: (MetaBreve) -> Unit) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    LaFamiliaCambios()
    Biblioteca.version
    val t = Biblioteca.por(b.id)
    val meta by produceState<Meta?>(Fichas.fija(b.id), b.id) { if (value == null) value = runCatching { Addons.meta(b.tipo.ifBlank { t?.tipo ?: "movie" }, t?.idStremio ?: b.id) }.getOrNull() }
    LaunchedEffect(meta, t?.ref) { val m = meta; if (m != null && t != null) Biblioteca.completa(t, m) }
    val m = meta
    val nombre = m?.nombre ?: t?.nombre ?: b.nombre
    val serie = m?.esSerie == true || b.tipo == "series" || t?.esSerie == true
    var temporada by remember(b.id) { mutableIntStateOf(-1) }
    var fuentesDe by remember { mutableStateOf<Pair<Video?, Boolean>?>(null) }
    var buscando by remember { mutableStateOf(false) }
    var elegida by remember { mutableStateOf<Eleccion?>(null) }
    var quitar by remember { mutableStateOf(false) }
    val tipo = b.tipo.ifBlank { m?.tipo ?: t?.tipo ?: "movie" }

    fun reproduce(v: Video?) {
        if (buscando) return
        if (!AjustesElector.auto(ctx)) { fuentesDe = v to true; return }
        buscando = true; elegida = null
        ambito.launch {
            val cs = runCatching { Busqueda.fuentes(tipo, v?.id ?: (t?.idStremio ?: b.id)) }.getOrDefault(emptyList())
            val e = Busqueda.elige(ctx, cs, m)
            buscando = false; elegida = e
            val c = e.elegida
            if (c == null) { fuentesDe = v to true; return@launch }
            Reproducir.pon(ctx, t?.breve() ?: b, m, v, c, e.ordenadas)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
        }
    }

    val ancha = esAncha()
    val apaisada = ancha && LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val mg = margen()
    Box(Modifier.fillMaxSize().background(Paper).atrapaFoco()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ---- El fondo, que se funde con el papel
            val fondo = m?.fondo?.ifBlank { null } ?: t?.fondo?.ifBlank { null } ?: b.fondo
            Box(Modifier.fillMaxWidth().height(if (ancha) 300.dp else 210.dp)) {
                if (fondo.isNotBlank()) AsyncImage(fondo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Paper.copy(alpha = 0.35f), Paper.copy(alpha = 0.55f), Paper))))
                Box(Modifier.statusBarsPadding().padding(horizontal = mg)) { RevistaTopBar(alCerrar, "Volver") }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = mg).offset(y = (-70).dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Poster(t?.imagen?.ifBlank { null } ?: m?.poster?.ifBlank { null } ?: b.poster, nombre, Modifier.width(if (ancha) 190.dp else 120.dp).border(1.dp, Line))
                    Column(Modifier.weight(1f).padding(start = 18.dp)) {
                        Text((tipoNombre(tipo) + (t?.let { " · " + it.estado.nombre } ?: "")).uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1)
                        FitTitle(nombre, if (ancha) 54.sp else 38.sp, if (ancha) 56.sp else 41.sp, letterSpacing = (-1).sp, maxLines = 4, minSize = 18f)
                        val datos = listOfNotNull(m?.ano?.ifBlank { null } ?: b.ano.ifBlank { null }, m?.minutos?.takeIf { it > 0 }?.let { duracion(it) }, m?.nota?.ifBlank { null }?.let { "★ $it" })
                        if (datos.isNotEmpty()) Text(datos.joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                val izquierda: @Composable ColumnScope.() -> Unit = {
                    // ---- Reproducir
                    val siguiente = if (serie && m != null && t != null) Biblioteca.siguiente(t, m) else null
                    val prog = if (!serie) Biblioteca.progreso(t?.idStremio ?: b.id) else siguiente?.let { Biblioteca.progreso(it.id) }
                    val seguir = prog != null && !prog.visto && prog.pos > 60_000L
                    val textoBoton = when {
                        buscando -> "Buscando fuentes…"
                        serie && siguiente != null -> (if (seguir) "Seguir · " else "Ver · ") + "T${siguiente.temporada}·E${siguiente.episodio}"
                        serie -> "Elige un episodio"
                        seguir -> "Seguir viendo"
                        else -> "Reproducir"
                    }
                    AccionGrande(Icons.Rounded.PlayArrow, textoBoton, Modifier.fillMaxWidth(), enabled = !buscando && (!serie || siguiente != null || m == null)) {
                        if (serie) { if (siguiente != null) reproduce(siguiente) } else reproduce(null)
                    }
                    elegida?.let { e ->
                        val texto = e.elegida?.let { c -> (if (e.fueraDeIdioma) "Ninguna en tus idiomas: " else "") + c.analisis.resumen().ifBlank { c.fuente.nombre.lineSequence().firstOrNull().orEmpty() } + " · " + c.addon } ?: "Ninguna fuente se puede poner sola"
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(texto, color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            EnlaceRevista("Cambiar", acento = true) { fuentesDe = (if (serie) siguiente else null) to true }
                        }
                    }
                    if (!serie) EnlaceRevista("Todas las fuentes →") { fuentesDe = null to true }
                    if (prog != null && prog.pos > 0 && !prog.visto && prog.dur > 0) {
                        Barra(prog.fraccion, Modifier.padding(top = 6.dp))
                        Text("Vas por ${tiempo(prog.pos)} de ${tiempo(prog.dur)}".uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    // ---- En tu biblioteca
                    if (t != null) {
                        Antetitulo("En tu biblioteca", Modifier.padding(bottom = 2.dp))
                        MagChoice(listOf(Estado.PENDIENTE, Estado.VIENDO, Estado.VISTO, Estado.ABANDONADO).map { it.nombre }, listOf(Estado.PENDIENTE, Estado.VIENDO, Estado.VISTO, Estado.ABANDONADO).indexOf(t.estado)) { i ->
                            Biblioteca.cambiaEstado(t.ref, listOf(Estado.PENDIENTE, Estado.VIENDO, Estado.VISTO, Estado.ABANDONADO)[i])
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            EnlaceRevista(if (t.fav) "★ Favorita" else "☆ Favorita", acento = t.fav) { Biblioteca.ponFavorito(t.ref, !t.fav) }
                            EnlaceRevista("Quitar de Mostaza") { quitar = true }
                        }
                        val ms = Biblioteca.msVistos(t.ref)
                        if (ms >= 60_000L) MagFact("Visto en total", duracion((ms / 60_000L).toInt()))
                        if (t.fin > 0 && t.estado == Estado.VISTO) MagFact("La terminaste", Formato.fecha(t.fin))
                        if (t.anadido > 0) MagFact("Añadida", Formato.fecha(t.anadido))
                    } else {
                        val borrada = Biblioteca.loBorraste(b.id)
                        AccionGrande(null, "+ Añadir a Pendiente", Modifier.fillMaxWidth(), acento = false) { Biblioteca.anade(b, m) }
                        Text(if (borrada) "La quitaste de tu biblioteca: no vuelve sola aunque la veas. «Añadir» la recupera, con lo que tenía."
                            else "Si la ves 10 minutos seguidos, entra sola en «Viendo».", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    // ---- En Azabache (la valoración, solo se lee)
                    if (t != null) {
                        val (nota, resena) = Biblioteca.valoracion(t.ref)
                        if (nota > 0 || resena.isNotBlank() || Hermanas.azabache(ctx)) {
                            Spacer(Modifier.height(14.dp))
                            Antetitulo("En Azabache", Modifier.padding(bottom = 4.dp))
                            if (nota > 0) Row(verticalAlignment = Alignment.Bottom) {
                                Text("$nota", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 48.sp)
                                Text(" / 100", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                            } else Nota("Sin valorar todavía.")
                            if (resena.isNotBlank()) Text("«$resena»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                            if (Hermanas.azabache(ctx)) MagLink("Valorar en Azabache") { Hermanas.abreEnAzabache(ctx, t.ref) }
                        }
                    }
                }
                val derecha: @Composable ColumnScope.() -> Unit = {
                    if (m == null) Nota(if (b.id.startsWith("tt") || t?.imdb?.isNotBlank() == true) "Leyendo la ficha…" else "Ninguno de tus addons tiene la ficha de esto.", Modifier.padding(vertical = 8.dp))
                    m?.descripcion?.takeIf { it.isNotBlank() }?.let { Text(it, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(bottom = 10.dp)) }
                    m?.let { x ->
                        if (x.generos.isNotEmpty()) MagFact("Género", x.generos.take(3).joinToString(", "))
                        if (x.direccion.isNotEmpty()) MagFact(if (x.direccion.size > 1) "Dirección" else "Dirección", x.direccion.take(2).joinToString(", "))
                        if (x.reparto.isNotEmpty()) MagFact("Reparto", x.reparto.take(3).joinToString(", "))
                        if (x.pais.isNotBlank()) MagFact("País", x.pais.split(',').first().trim())
                    }
                    if (serie && m != null && m.videos.isNotEmpty()) {
                        val temps = m.temporadas
                        val sel = if (temporada in temps) temporada else (t?.let { Biblioteca.siguiente(it, m)?.temporada } ?: temps.firstOrNull { it > 0 } ?: temps.first())
                        Spacer(Modifier.height(16.dp))
                        Antetitulo("Episodios")
                        PestanasRevista(temps.map { if (it == 0) "Especiales" else "Temporada $it" }, temps.indexOf(sel), cuentas = temps.map { s -> m.episodios(s).size }) { i -> temporada = temps[i] }
                        m.episodios(sel).forEach { v -> FilaEpisodio(v, t, m, alVer = { reproduce(v) }, alFuentes = { fuentesDe = v to true }) }
                    }
                }
                if (apaisada) Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(0.42f).padding(end = 28.dp), content = izquierda)
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
                    Column(Modifier.weight(0.58f).padding(start = 28.dp), content = derecha)
                } else Column(Modifier.fillMaxWidth().widthIn(max = 760.dp)) { izquierda(); Spacer(Modifier.height(16.dp)); derecha() }
                Spacer(Modifier.height(30.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
        }
        fuentesDe?.let { (v, _) -> PantFuentes(t?.breve() ?: b, m, v, alCerrar = { fuentesDe = null }) }
    }
    if (quitar && t != null) AlertDialog(
        onDismissRequest = { quitar = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Quitar «${t.nombre}»?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Se quita de tu biblioteca en todos tus dispositivos y en Azabache. Es para siempre: aunque la vuelvas a ver, no vuelve sola. Si la añades otra vez, vuelve la misma, con lo que tenía (lo visto y su nota).", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({ quitar = false; Biblioteca.quita(t.ref) }) { Text("QUITAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ quitar = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
    )
}

/** Para que la ficha se repinte con los cambios de la cuenta. */
@Composable
private fun LaFamiliaCambios() { com.gonzalo.familia.LaFamilia.cambios }

/** Fichas fijas para las pruebas de pantallas (sin red). */
object Fichas {
    @Volatile var fijas: Map<String, Meta> = emptyMap()
    fun fija(id: String): Meta? = fijas[id]
}

/** Un episodio: número y título, cuándo se estrenó, lo que llevas visto y la marca de visto (si la serie está en tu biblioteca). */
@Composable
private fun FilaEpisodio(v: Video, t: Titulo?, m: Meta, alVer: () -> Unit, alFuentes: () -> Unit) {
    val p = Biblioteca.progreso(v.id)
    val estrenado = v.estrenado()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().tocable(enabled = estrenado) { alVer() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (v.episodio > 0) "${v.episodio}" else "·", color = if (p?.visto == true) Muted else Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f)) {
                Text(v.titulo.ifBlank { "Episodio ${v.episodio}" }, color = if (estrenado) Charcoal else Muted, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                Text("Mostaza pondría: " + c.analisis.resumen().ifBlank { c.addon } + (if (e.porGrupo) " (la misma clase que el episodio anterior)" else "") + (if (e.fueraDeIdioma) " — ninguna está en tus idiomas" else ""),
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
