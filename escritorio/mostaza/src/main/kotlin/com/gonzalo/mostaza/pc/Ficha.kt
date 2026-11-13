package com.gonzalo.mostaza.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.HermanasPc
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que pasa al pulsar una fuente o «Reproducir»: si no hay mpv, se dice cómo ponerlo y se ofrece el reproductor del sistema. */
class Poner(val nav: Nav) {
    var sinMpv by mutableStateOf<Pair<Candidata, String>?>(null)

    fun pon(b: MetaBreve, m: Meta?, v: Video?, c: Candidata, todas: List<Candidata>, automatica: Boolean) {
        val r = Reproductor.pon(b, m, v, c, todas, automatica = automatica) ?: return
        if (r == Mpv.comoInstalar()) sinMpv = c to b.nombre else nav.aviso(r)
    }
}

@Composable
fun DialogoSinMpv(p: Poner) {
    val (c, nombre) = p.sinMpv ?: return
    val ambito = rememberCoroutineScope()
    Dialogo("Falta mpv", { p.sinMpv = null }, botones = {
        BotonSuave("Cerrar") { p.sinMpv = null }
        BotonAcento("Abrir con el del sistema", alto = 44.dp) {
            p.sinMpv = null
            ambito.launch { withContext(Dispatchers.IO) { Reproductor.conElSistema(c, nombre) }?.let { p.nav.aviso(it) } }
        }
    }) {
        Cuerpo(Mpv.comoInstalar(), color = Charcoal, tam = 15)
        Spacer(Modifier.height(10.dp))
        Cuerpo("También puedes abrirla ahora con el reproductor de vídeo que tenga el sistema, pero así Hathor no sabe por dónde vas: no entra en tu biblioteca, ni se apunta lo visto ni el tiempo, y no se pasa sola a otra fuente si falla.", tam = 13)
    }
}

/**
 * La ficha de una película o una serie (como la del móvil, a dos columnas): el fondo, el póster, el titular, «Reproducir» (la mejor fuente
 * para ti, y cuál ha sido), dónde está en tu biblioteca (y «+ Añadir» o «Quitar»), lo que dice Azabache y, a la derecha, lo que se sabe
 * de ella (descripción, tráiler, reparto, dónde verla, «Más como esta») y, en una serie, sus temporadas y episodios con lo visto.
 */
@Composable
fun PantFicha(b: MetaBreve, nav: Nav) {
    // Lo que viene del catálogo de TMDB («tmdb:movie:…»): primero su ficha, para saber su id de IMDb (el de tus addons y tu biblioteca)
    if (TmdbDatos.deId(b.id) == null) { FichaDe(b, nav); return }
    var hecho by remember(b.id) { mutableStateOf(Fichas.deCache(b.id) != null) }
    val r by produceState(Fichas.deCache(b.id), b.id) { if (value == null) value = withContext(Dispatchers.IO) { runCatching { Fichas.carga(b.tipo, b.id) }.getOrNull() }; hecho = true }
    val x = r
    if (x == null) {
        Pagina {
            VolverRevista({ nav.volver() })
            Text(b.nombre, color = Charcoal, fontFamily = Grotesca, fontSize = 48.sp, lineHeight = 50.sp, modifier = Modifier.padding(top = 18.dp))
            Nota(if (hecho) "No se ha podido leer la ficha (¿sin conexión?)." else "Leyendo la ficha…", Modifier.padding(top = 12.dp))
        }
        return
    }
    FichaDe(b.copy(id = x.first.id, tipo = x.first.tipo), nav)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FichaDe(b: MetaBreve, nav: Nav) {
    val ambito = rememberCoroutineScope()
    val t = Biblioteca.por(b.id)
    val carga by produceState(Fichas.deCache(t?.idStremio ?: b.id), b.id) {
        if (value == null) value = withContext(Dispatchers.IO) { runCatching { Fichas.carga(b.tipo.ifBlank { t?.tipo ?: "movie" }, t?.idStremio ?: b.id) }.getOrNull() }
    }
    val m = carga?.first
    val extras = carga?.second
    LaunchedEffect(m, t?.ref) { val mm = m; if (mm != null && t != null) withContext(Dispatchers.IO) { Biblioteca.completa(t, mm) } }
    val nombre = m?.nombre ?: t?.nombre ?: b.nombre
    val serie = m?.esSerie == true || b.tipo == "series" || t?.esSerie == true
    var temporada by remember(b.id) { mutableIntStateOf(-1) }
    var buscando by remember { mutableStateOf(false) }
    var elegida by remember { mutableStateOf<Eleccion?>(null) }
    var quitar by remember { mutableStateOf(false) }
    val tipo = b.tipo.ifBlank { m?.tipo ?: t?.tipo ?: "movie" }
    val poner = remember { Poner(nav) }
    val breve = t?.breve() ?: b

    fun fuentes(v: Video?) = nav.ir(Pantalla.Fuentes(breve.copy(tipo = tipo), m, v, nav.actual()))
    fun reproduce(v: Video?) {
        if (buscando) return
        if (!AjustesElector.auto()) { fuentes(v); return }
        buscando = true; elegida = null
        ambito.launch {
            val cs = withContext(Dispatchers.IO) { runCatching { Busqueda.fuentes(tipo, v?.id ?: (t?.idStremio ?: b.id)) }.getOrDefault(emptyList()) }
            val e = withContext(Dispatchers.IO) { Busqueda.elige(cs, m) }
            buscando = false; elegida = e
            val c = e.elegida ?: run { fuentes(v); return@launch }
            poner.pon(breve, m, v, c, e.ordenadas, automatica = true)
        }
    }

    Pagina(ancho = 1280.dp) {
        // ---- El fondo, que se funde con el papel
        val fondo = m?.fondo?.ifBlank { null } ?: t?.fondo?.ifBlank { null } ?: b.fondo
        Box(Modifier.fillMaxWidth().height(280.dp).clip(esquina(3.dp))) {
            if (fondo.isNotBlank()) ImagenCosa("", fondo, nombre, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Paper.copy(alpha = 0.25f), Paper.copy(alpha = 0.55f), Paper))))
            Box(Modifier.padding(horizontal = 4.dp)) { VolverRevista({ nav.volver() }) }
        }
        Row(Modifier.fillMaxWidth().offset(y = (-90).dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.width(200.dp).aspectRatio(2f / 3f).border(1.dp, Line)) { ImagenCosa(t?.portada.orEmpty(), t?.poster?.ifBlank { null } ?: m?.poster?.ifBlank { null } ?: b.poster, nombre, Modifier.fillMaxSize()) }
            Column(Modifier.weight(1f).padding(start = 24.dp)) {
                Text((tipoNombre(tipo) + (t?.let { " · " + it.estado.nombre } ?: "")).uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1)
                Text(nombre, color = Charcoal, fontFamily = Grotesca, fontSize = 56.sp, lineHeight = 58.sp, letterSpacing = (-1).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val datos = listOfNotNull(m?.ano?.ifBlank { null } ?: b.ano.ifBlank { null }, m?.minutos?.takeIf { it > 0 }?.let { duracion(it) }, m?.nota?.ifBlank { null }?.let { "★ $it" })
                if (datos.isNotEmpty()) Text(datos.joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        Row(Modifier.fillMaxWidth().offset(y = (-70).dp)) {
            // ---- La columna de la izquierda: ver, tu biblioteca y Azabache
            Column(Modifier.weight(0.4f).padding(end = 28.dp)) {
                val siguiente = if (serie && m != null && t != null) Biblioteca.siguiente(t, m) else if (serie && m != null) m.videos.filter { it.temporada > 0 }.minWithOrNull(compareBy({ it.temporada }, { it.episodio })) else null
                val prog = if (!serie) Biblioteca.progreso(t?.idStremio ?: b.id) else siguiente?.let { Biblioteca.progreso(it.id) }
                val seguir = prog != null && !prog.visto && prog.pos > 60_000L
                val textoBoton = when {
                    buscando -> "Buscando fuentes…"
                    serie && siguiente != null -> (if (seguir) "Seguir · " else "Ver · ") + "T${siguiente.temporada}·E${siguiente.episodio}"
                    serie -> "Elige un episodio"
                    seguir -> "Seguir viendo"
                    else -> "Reproducir"
                }
                val f = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                AccionGrande(Icons.Rounded.PlayArrow, textoBoton, Modifier.fillMaxWidth().marcoFoco(f), enabled = !buscando && (!serie || siguiente != null || m == null)) {
                    if (serie) { if (siguiente != null) reproduce(siguiente) } else reproduce(null)
                }
                elegida?.let { e ->
                    val texto = e.elegida?.let { c -> (if (e.fueraDeIdioma) "Ninguna en tus idiomas: " else "") + c.analisis.resumen().ifBlank { c.fuente.nombre.lineSequence().firstOrNull().orEmpty() } + " · " + c.addon } ?: "Ninguna fuente se puede poner sola"
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(texto, color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        EnlaceRevista("Cambiar", acento = true) { fuentes(if (serie) siguiente else null) }
                    }
                }
                if (!serie) EnlaceRevista("Todas las fuentes →") { fuentes(null) }
                if (prog != null && prog.pos > 0 && !prog.visto && prog.dur > 0) {
                    Barra(prog.fraccion, Modifier.padding(top = 6.dp))
                    Text("Vas por ${tiempo(prog.pos)} de ${tiempo(prog.dur)}".uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(18.dp))
                if (t != null) {
                    val estados = listOf(Estado.PENDIENTE, Estado.VIENDO, Estado.VISTO, Estado.ABANDONADO)
                    Antetitulo("En tu biblioteca", Modifier.padding(bottom = 2.dp))
                    MagChoice(estados.map { it.nombre }, estados.indexOf(t.estado)) { i -> Biblioteca.cambiaEstado(t.ref, estados[i]) }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        EnlaceRevista(if (t.fav) "★ Favorita" else "☆ Favorita", acento = t.fav) { Biblioteca.ponFavorito(t.ref, !t.fav) }
                        EnlaceRevista("Quitar de Hathor") { quitar = true }
                    }
                    val ms = Biblioteca.msVistos(t.ref)
                    if (ms >= 60_000L) MagFact("Visto en total", duracion((ms / 60_000L).toInt()))
                    if (t.fin > 0 && t.estado == Estado.VISTO) MagFact("La terminaste", Formato.fecha(t.fin))
                    if (t.anadido > 0) MagFact("Añadida", Formato.fecha(t.anadido))
                    val (nota, resena) = Biblioteca.valoracion(t.ref)
                    Spacer(Modifier.height(16.dp))
                    Antetitulo("En Jonsu", Modifier.padding(bottom = 4.dp))
                    if (nota > 0) Row(verticalAlignment = Alignment.Bottom) {
                        Text("$nota", color = Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 48.sp)
                        Text(" / 100", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    } else Nota("Sin valorar todavía.")
                    if (resena.isNotBlank()) Text("«$resena»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    if (HermanasPc.esta("azabache")) EnlaceRevista("Valorar en Jonsu →", acento = true) { HermanasPc.abre("azabache") }
                } else {
                    val borrada = Biblioteca.loBorraste(b.id)
                    AccionGrande(null, "+ Añadir a Pendiente", Modifier.fillMaxWidth(), acento = false) { Biblioteca.anade(b, m) }
                    Cuerpo(if (borrada) "La quitaste de tu biblioteca: no vuelve sola aunque la veas. «Añadir» la recupera, con lo que tenía."
                        else "Si la ves 10 minutos seguidos, entra sola en «Viendo».", Modifier.padding(top = 6.dp), tam = 12)
                }
            }
            Box(Modifier.width(1.dp).heightIn(min = 200.dp).background(Line))
            // ---- La columna de la derecha: lo que se sabe de ella y sus episodios
            Column(Modifier.weight(0.6f).padding(start = 28.dp)) {
                if (m == null) Nota(if (carga == null) "Leyendo la ficha…" else "Ninguno de tus addons tiene la ficha de esto.", Modifier.padding(vertical = 8.dp))
                extras?.lema?.takeIf { it.isNotBlank() }?.let { Text("«$it»", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 25.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                m?.descripcion?.takeIf { it.isNotBlank() }?.let { Text(it, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(bottom = 10.dp)) }
                m?.trailers?.firstOrNull()?.let { k -> MagLink("Ver el tráiler", "En YouTube, en el navegador") { abreEnNavegador("https://www.youtube.com/watch?v=$k") } }
                m?.let { x ->
                    if (x.generos.isNotEmpty()) MagFact("Género", x.generos.take(3).joinToString(", "))
                    if (x.direccion.isNotEmpty()) MagFact("Dirección", x.direccion.take(2).joinToString(", "))
                    if (x.reparto.isNotEmpty()) MagFact("Reparto", x.reparto.take(3).joinToString(", "))
                    if (x.pais.isNotBlank()) MagFact("País", x.pais.split(',').first().trim())
                }
                extras?.plataformas?.takeIf { it.isNotEmpty() }?.let { p ->
                    Antetitulo("Dónde verla en España", Modifier.padding(top = 14.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { p.take(6).forEach { x -> ImagenCosa("", x.logo, x.nombre, Modifier.size(40.dp).clip(esquina(6.dp)).border(1.dp, Line, esquina(6.dp))) } }
                    Text(p.take(6).joinToString(" · ") { it.nombre } + ". Datos de JustWatch.", color = Muted, fontFamily = Body, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (serie && m != null && m.videos.isNotEmpty()) {
                    val temps = m.temporadas
                    val sel = if (temporada in temps) temporada else (t?.let { Biblioteca.siguiente(it, m)?.temporada } ?: temps.firstOrNull { it > 0 } ?: temps.first())
                    Spacer(Modifier.height(16.dp))
                    Antetitulo("Episodios")
                    PestanasRevista(temps.map { if (it == 0) "Especiales" else "Temporada $it" }, temps.indexOf(sel)) { i -> temporada = temps[i] }
                    m.episodios(sel).forEach { v -> FilaEpisodio(v, t, m, alVer = { reproduce(v) }, alFuentes = { fuentes(v) }) }
                }
                extras?.personas?.takeIf { it.isNotEmpty() }?.let { ps ->
                    Antetitulo("Reparto", Modifier.padding(top = 18.dp, bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(ps, key = { it.nombre + it.papel }) { x ->
                            Column(Modifier.width(92.dp)) {
                                ImagenCosa("", x.foto, x.nombre, Modifier.size(92.dp, 124.dp).clip(esquina(3.dp)))
                                Text(x.nombre, color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                if (x.papel.isNotBlank()) Text(x.papel, color = Muted, fontFamily = Body, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                extras?.similares?.let { FilaPosters("Más como esta", it, { x -> nav.abre(x) }) }
                if (extras != null) Text(Tmdb.AVISO_TMDB, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp))
            }
        }
    }
    DialogoSinMpv(poner)
    if (quitar && t != null) Dialogo("¿Quitar «${t.nombre}»?", { quitar = false }, botones = {
        BotonSuave("Cancelar") { quitar = false }
        BotonAcento("Quitar", alto = 44.dp) { quitar = false; Biblioteca.quita(t.ref) }
    }) {
        Cuerpo("Se quita de tu biblioteca en todos tus dispositivos y en Jonsu. Es para siempre: aunque la vuelvas a ver, no vuelve sola. Si la añades otra vez, vuelve la misma, con lo que tenía (lo visto y su nota).", tam = 14)
    }
}

/** Un episodio: número y título, cuándo se estrenó, lo que llevas visto y la marca de visto (si la serie está en tu biblioteca). */
@Composable
private fun FilaEpisodio(v: Video, t: Titulo?, m: Meta, alVer: () -> Unit, alFuentes: () -> Unit) {
    val p = Biblioteca.progreso(v.id)
    val estrenado = v.estrenado()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().pulsable(enabled = estrenado) { alVer() }.padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (v.episodio > 0) "${v.episodio}" else "·", color = if (p?.visto == true) Muted else Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f)) {
                Text(v.titulo.ifBlank { "Episodio ${v.episodio}" }, color = if (estrenado) Charcoal else Muted, fontFamily = Grotesca, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val linea = listOfNotNull(if (v.estreno > 0) Formato.fecha(v.estreno) else null, if (!estrenado) "aún no se ha estrenado" else null, if (p?.visto == true) "visto" else if (p != null && p.pos > 0 && p.dur > 0) "vas por ${tiempo(p.pos)}" else null)
                if (linea.isNotEmpty()) Text(linea.joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                if (p != null && !p.visto && p.fraccion > 0f) Barra(p.fraccion, Modifier.padding(top = 4.dp))
            }
            if (estrenado) EnlaceRevista("Fuentes", Modifier.padding(start = 8.dp)) { alFuentes() }
            if (t != null) Text(if (p?.visto == true) "✓" else "○", color = if (p?.visto == true) Acento else Muted, fontWeight = FontWeight.Black, fontSize = 20.sp,
                modifier = Modifier.padding(start = 10.dp).pulsable { Biblioteca.marcaVisto(t, v, p?.visto != true, m) }.padding(8.dp))
        }
        Filete(1.dp, Line)
    }
}

/**
 * Todas las fuentes de un vídeo, agrupadas por addon (en tu orden), cada una con sus líneas tal como las da su addon, lo que se entiende de
 * ella (calidad, idioma, tamaño) y si es directa, torrent, YouTube o una web. La que elegiría Mostaza va marcada. Pulsar una la pone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantFuentes(b: MetaBreve, m: Meta?, v: Video?, nav: Nav) {
    val tipo = b.tipo.ifBlank { if (v != null) "series" else "movie" }
    var todas by remember { mutableStateOf<List<Candidata>>(emptyList()) }
    var terminado by remember { mutableStateOf(false) }
    LaunchedEffect(b.id, v?.id) {
        todas = withContext(Dispatchers.IO) { runCatching { Busqueda.fuentes(tipo, v?.id ?: b.id) { parciales -> todas = parciales } }.getOrDefault(todas) }
        terminado = true
    }
    val e = remember(todas) { Busqueda.elige(todas, m) }
    val poner = remember { Poner(nav) }
    Pagina {
        CabeceraPagina(b.nombre + (v?.let { if (it.temporada > 0) " · T${it.temporada}·E${it.episodio}" else "" } ?: ""), "Fuentes", volver = { nav.volver() })
        if (!terminado) Nota("Preguntando a tus addons…", Modifier.padding(top = 10.dp))
        if (terminado && todas.isEmpty()) Nota("Ninguno de tus addons da fuentes para esto. Los addons de fuentes los pones tú en «Addons» (con tu debrid configurado en el suyo).", Modifier.padding(top = 10.dp))
        if (!Torrents.disponible && todas.any { it.fuente.tipo == TipoFuente.TORRENT }) Cuerpo("Los torrents no se pueden poner en este ordenador (no se ha podido cargar su motor): elige una directa o de tu debrid.", Modifier.padding(top = 8.dp), tam = 12)
        e.elegida?.let { c ->
            Cuerpo("Hathor pondría: " + c.analisis.resumen().ifBlank { c.addon } + (if (e.porGrupo) " (la misma clase que el episodio anterior)" else "") + (if (e.fueraDeIdioma) " — ninguna está en tus idiomas" else ""),
                Modifier.padding(top = 10.dp), tam = 12)
        }
        todas.groupBy { it.ordenAddon to it.addon }.toSortedMap(compareBy { it.first }).forEach { (k, cs) ->
            Spacer(Modifier.height(18.dp))
            Antetitulo(k.second, Modifier.padding(bottom = 4.dp))
            Filete(1.dp)
            cs.sortedBy { it.ordenEnAddon }.forEach { c -> FilaFuente(c, c.fuente === e.elegida?.fuente) { poner.pon(b, m, v, c, e.ordenadas, automatica = false) } }
        }
    }
    DialogoSinMpv(poner)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaFuente(c: Candidata, elegida: Boolean, alPoner: () -> Unit) {
    val f = c.fuente
    val marca = when (f.tipo) {
        TipoFuente.DIRECTA -> "Directa"; TipoFuente.TORRENT -> "Torrent"; TipoFuente.YOUTUBE -> "YouTube"; TipoFuente.EXTERNA -> "Web"
        TipoFuente.NZB -> "Usenet"; TipoFuente.ARCHIVO -> "Comprimido"; TipoFuente.NADA -> ""
    }
    Column(Modifier.fillMaxWidth().pulsable { alPoner() }.padding(vertical = 12.dp, horizontal = 4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(marca.uppercase(), color = if (f.tipo == TipoFuente.DIRECTA) SobreAcento else Paper, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                modifier = Modifier.background(if (f.tipo == TipoFuente.DIRECTA) Acento else Charcoal).padding(horizontal = 6.dp, vertical = 2.dp))
            if (elegida) Text("LA MEJOR PARA TI", color = Acento, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.border(1.dp, Acento).padding(horizontal = 6.dp, vertical = 2.dp))
            c.analisis.resumen().takeIf { it.isNotBlank() }?.let { Text(it.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp) }
        }
        if (f.nombre.isNotBlank()) Text(f.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
        if (f.descripcion.isNotBlank()) Text(f.descripcion, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
    }
    Filete(1.dp, Line)
}
