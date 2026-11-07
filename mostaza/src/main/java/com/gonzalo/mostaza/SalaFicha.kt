package com.gonzalo.mostaza

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * La ficha en la sala: el fondo grande, el póster, el titular y «Reproducir» (la mejor fuente para ti, elegida sola como en el móvil, y
 * cuál ha sido), «Todas las fuentes», dónde está en tu biblioteca, lo que dice su addon o TMDB y, en una serie, sus temporadas y sus
 * episodios (OK en uno lo pone; «Fuentes» al lado, todas las suyas; ✓, visto o no). Debajo, «Más como esta».
 */
@Composable
fun FichaSala(b: MetaBreve, est: EstadoSala, ctl: ControlSala) {
    // Lo que viene del catálogo de TMDB («tmdb:movie:…»): primero su ficha, para saber su id de IMDb (el de tus addons y tu biblioteca)
    if (TmdbDatos.deId(b.id) == null) { FichaSalaDe(b, est, ctl); return }
    var hecho by remember(b.id) { mutableStateOf(Fichas.deCache(b.id) != null) }
    val r by produceState(Fichas.deCache(b.id), b.id) { if (value == null) { value = runCatching { Fichas.carga(b.tipo, b.id) }.getOrNull() }; hecho = true }
    val x = r
    if (x == null) {
        val tinta = LocalTinta.current
        SideEffect { est.primero[est.vista] = "f:volver" }
        Column(Modifier.fillMaxSize().background(tinta.fondo).padding(horizontal = MARGEN_H, vertical = 40.dp)) {
            FitTitle(b.nombre, 80.sp, 84.sp, color = tinta.tinta, maxLines = 2, minSize = 40f)
            NotaSala(if (hecho) "No se ha podido leer la ficha (¿sin conexión?)." else "Leyendo la ficha…", Modifier.padding(vertical = 20.dp))
            BotonSala("Volver", "f:volver") { ctl.atras() }
        }
        return
    }
    FichaSalaDe(b.copy(id = x.first.id, tipo = x.first.tipo), est, ctl)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun FichaSalaDe(b: MetaBreve, est: EstadoSala, ctl: ControlSala) {
    val ctx = LocalContext.current
    val tinta = LocalTinta.current
    val ambito = rememberCoroutineScope()
    com.gonzalo.familia.LaFamilia.cambios
    Biblioteca.version
    val t = Biblioteca.por(b.id)
    val carga by produceState(Fichas.deCache(t?.idStremio ?: b.id), b.id) {
        if (value == null) value = runCatching { Fichas.carga(b.tipo.ifBlank { t?.tipo ?: "movie" }, t?.idStremio ?: b.id) }.getOrNull()
    }
    val m = carga?.first
    val extras = carga?.second
    LaunchedEffect(m, t?.ref) { val mm = m; if (mm != null && t != null) Biblioteca.completa(t, mm) }
    val nombre = m?.nombre ?: t?.nombre ?: b.nombre
    val serie = m?.esSerie == true || b.tipo == "series" || t?.esSerie == true
    val tipo = b.tipo.ifBlank { m?.tipo ?: t?.tipo ?: "movie" }
    var temporada by remember(b.id) { mutableIntStateOf(-1) }
    var buscando by remember { mutableStateOf(false) }
    var elegida by remember { mutableStateOf<Eleccion?>(null) }
    val breve = t?.breve() ?: b
    SideEffect { est.primero[est.vista] = "f:reproducir" }

    fun fuentes(v: Video?) = ctl.abreFuentes(PideFuentes(breve, m, v))
    // «Reproducir»: lo mismo que en la ficha del móvil (Busqueda.reproduce: el elector elige y, si puede, la pone sola)
    fun reproduce(v: Video?) {
        if (buscando) return
        if (!AjustesElector.auto(ctx)) { fuentes(v); return }
        buscando = true; elegida = null
        ambito.launch {
            val (e, aviso) = Busqueda.reproduce(ctx, tipo, b, t, m, v)
            buscando = false; elegida = e
            if (e.elegida == null) { fuentes(v); return@launch }
            aviso?.let { est.avisa(it) }
        }
    }

    val siguiente = if (serie && m != null && t != null) Biblioteca.siguiente(t, m) else null
    val prog = if (!serie) Biblioteca.progreso(t?.idStremio ?: b.id) else siguiente?.let { Biblioteca.progreso(it.id) }
    val seguir = prog != null && !prog.visto && prog.pos > 60_000L
    val textoBoton = when {
        buscando -> "Buscando fuentes…"
        serie && siguiente != null -> (if (seguir) "Seguir · " else "Ver · ") + "T${siguiente.temporada}·E${siguiente.episodio}"
        serie && m == null -> "Leyendo la ficha…"
        serie -> "Elige un episodio"
        seguir -> "Seguir viendo"
        else -> "Reproducir"
    }
    val fondo = m?.fondo?.ifBlank { null } ?: t?.fondo?.ifBlank { null } ?: b.fondo
    val poster = t?.imagen?.ifBlank { null } ?: m?.poster?.ifBlank { null } ?: b.poster
    Box(Modifier.fillMaxSize().background(tinta.fondo)) {
        FondoSala(fondo, poster, b.tipo + b.id, Modifier.fillMaxSize())
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember { TraerAVista(140f) }) {
            Row(Modifier.fillMaxSize().verticalScroll(est.pagina(est.vista)).padding(start = MARGEN_H, end = MARGEN_H, top = 40.dp, bottom = 50.dp)) {
                PosterSala(poster, nombre, Modifier.width(340.dp), radio = 14.dp, sombra = 30.dp)
                Spacer(Modifier.width(64.dp))
                Column(Modifier.weight(1f)) {
                    Text((tipoNombre(tipo) + (t?.let { "  ·  En " + it.estado.nombre } ?: "")).uppercase(), color = tinta.acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 3.sp)
                    Spacer(Modifier.height(10.dp))
                    FitTitle(nombre, 88.sp, 92.sp, color = tinta.tinta, letterSpacing = (-2).sp, maxLines = 3, overflow = TextOverflow.Ellipsis, minSize = 44f)
                    val datos = listOfNotNull(m?.ano?.ifBlank { null } ?: b.ano.ifBlank { null }, m?.minutos?.takeIf { it > 0 }?.let { duracion(it) }, m?.nota?.ifBlank { null }?.let { "★ $it" },
                        m?.generos?.take(3)?.joinToString(", ")?.ifBlank { null })
                    if (datos.isNotEmpty()) Text(datos.joinToString("  ·  "), color = tinta.tinta.copy(alpha = 0.8f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 26.sp, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(30.dp))
                    // ---- Reproducir, las fuentes, tu biblioteca
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        BotonSala(textoBoton, "f:reproducir", principal = true, icono = "▶", alto = 76.dp, activo = !buscando && (!serie || siguiente != null), modifier = Modifier.widthIn(min = 320.dp)) {
                            if (serie) { if (siguiente != null) reproduce(siguiente) } else reproduce(null)
                        }
                        if (!serie || siguiente != null) BotonSala("Todas las fuentes", "f:fuentes", alto = 76.dp) { fuentes(if (serie) siguiente else null) }
                        if (t == null) BotonSala("+ Añadir a Pendiente", "f:anadir", alto = 76.dp) { Biblioteca.anade(b, m); est.avisa("«$nombre», en Pendiente") }
                        else BotonSala(if (t.fav) "★ Favorita" else "☆ Favorita", "f:fav", alto = 76.dp) { Biblioteca.ponFavorito(t.ref, !t.fav) }
                    }
                    elegida?.let { e ->
                        val texto = e.elegida?.let { c -> (if (e.fueraDeIdioma) "Ninguna en tus idiomas: " else "") + c.analisis.resumen().ifBlank { c.fuente.nombre.lineSequence().firstOrNull().orEmpty() } + " · " + c.addon } ?: "Ninguna fuente se puede poner sola"
                        Text("Hathor ha puesto: $texto", color = tinta.gris, fontFamily = Body, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
                    }
                    if (prog != null && prog.pos > 0 && !prog.visto && prog.dur > 0) {
                        Box(Modifier.padding(top = 18.dp).width(620.dp).height(6.dp).background(tinta.linea)) { Box(Modifier.fillMaxWidth(prog.fraccion).fillMaxHeight().background(tinta.acento)) }
                        Text("Vas por ${tiempo(prog.pos)} de ${tiempo(prog.dur)}".uppercase(), color = tinta.gris, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (t == null) NotaSala(if (Biblioteca.loBorraste(b.id)) "La quitaste de tu biblioteca: no vuelve sola aunque la veas. «Añadir» la recupera, con lo que tenía."
                        else "Si la ves 10 minutos seguidos, entra sola en «Viendo».", Modifier.padding(top = 16.dp), tam = 19.sp)
                    // ---- Lo que dice su ficha
                    extras?.lema?.takeIf { it.isNotBlank() }?.let { Text("«$it»", color = tinta.tinta, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 26.sp, lineHeight = 34.sp, modifier = Modifier.padding(top = 30.dp)) }
                    m?.descripcion?.takeIf { it.isNotBlank() }?.let { Text(it, color = tinta.tinta.copy(alpha = 0.86f), fontFamily = Body, fontSize = 21.sp, lineHeight = 31.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp).widthIn(max = 1150.dp)) }
                    if (m == null) NotaSala(if (b.id.startsWith("tt") || t?.imdb?.isNotBlank() == true) "Leyendo la ficha…" else "Ninguno de tus addons tiene la ficha de esto.", Modifier.padding(top = 20.dp))
                    Spacer(Modifier.height(30.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(tinta.tinta.copy(alpha = 0.8f)))
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                        Column(Modifier.weight(1f)) {
                            AnteSala("La ficha")
                            Spacer(Modifier.height(10.dp))
                            m?.let { x ->
                                if (x.generos.isNotEmpty()) DatoSala("Género", x.generos.take(3).joinToString(", "))
                                if (x.direccion.isNotEmpty()) DatoSala("Dirección", x.direccion.take(2).joinToString(", "))
                                if (x.reparto.isNotEmpty()) DatoSala("Reparto", x.reparto.take(3).joinToString(", "))
                                if (x.pais.isNotBlank()) DatoSala("País", x.pais.split(',').first().trim())
                            }
                            extras?.plataformas?.takeIf { it.isNotEmpty() }?.let { p -> DatoSala("Dónde verla", p.take(3).joinToString(", ") { it.nombre }) }
                        }
                        Column(Modifier.weight(1f)) {
                            AnteSala("En tu biblioteca")
                            Spacer(Modifier.height(10.dp))
                            if (t != null) {
                                val ms = Biblioteca.msVistos(t.ref)
                                if (ms >= 60_000L) DatoSala("Visto en total", duracion((ms / 60_000L).toInt()))
                                if (t.fin > 0 && t.estado == Estado.VISTO) DatoSala("La terminaste", Formato.fecha(t.fin))
                                if (t.anadido > 0) DatoSala("Añadida", Formato.fecha(t.anadido))
                                val (nota, _) = Biblioteca.valoracion(t.ref)
                                if (nota > 0) DatoSala("En Jonsu", "$nota / 100")
                            } else NotaSala("Aún no está.", tam = 20.sp)
                        }
                    }
                    if (t != null) {
                        Spacer(Modifier.height(18.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            listOf(Estado.PENDIENTE, Estado.VIENDO, Estado.VISTO, Estado.ABANDONADO).forEach { e ->
                                ChipSala(e.nombre, "f:e:${e.name}", t.estado == e) { if (t.estado != e) { Biblioteca.cambiaEstado(t.ref, e); est.avisa("«$nombre», en ${e.nombre}") } }
                            }
                            ChipSala("Quitar de Hathor", "f:quitar", false) {
                                ctl.confirma(Confirmacion("¿Quitar «${t.nombre}»?",
                                    "Se quita de tu biblioteca en todos tus dispositivos y en Jonsu. Es para siempre: aunque la vuelvas a ver, no vuelve sola. Si la añades otra vez, vuelve la misma, con lo que tenía (lo visto y su nota).",
                                    "Quitar") { Biblioteca.quita(t.ref); est.avisa("Quitada de tu biblioteca") })
                            }
                        }
                    }
                    // ---- Los episodios
                    if (serie && m != null && m.videos.isNotEmpty()) {
                        val temps = m.temporadas
                        val sel = if (temporada in temps) temporada else (siguiente?.temporada ?: temps.firstOrNull { it > 0 } ?: temps.first())
                        Spacer(Modifier.height(34.dp))
                        AnteSala("Episodios")
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            temps.forEach { x -> ChipSala(if (x == 0) "Especiales" else "Temporada $x", "f:t:$x", x == sel) { temporada = x } }
                        }
                        Spacer(Modifier.height(10.dp))
                        m.episodios(sel).forEach { v -> EpisodioSala(v, t, m, siguiente?.id == v.id, alVer = { reproduce(v) }, alFuentes = { fuentes(v) }) }
                    }
                    extras?.similares?.takeIf { it.isNotEmpty() }?.let { s ->
                        Spacer(Modifier.height(30.dp))
                        SimilaresSala(s, est, ctl)
                    }
                }
            }
        }
    }
}

/** Un episodio: número y título, cuándo se estrenó y lo que llevas visto; OK lo pone, y al lado sus fuentes y la marca de visto. */
@Composable
private fun EpisodioSala(v: Video, t: Titulo?, m: Meta, toca: Boolean, alVer: () -> Unit, alFuentes: () -> Unit) {
    val tinta = LocalTinta.current
    val foco = LocalFoco.current
    val p = Biblioteca.progreso(v.id)
    val estrenado = v.estrenado()
    val id = "f:ep:${v.id}"
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).enfocable(id, radio = 10.dp, crece = 1.015f) { if (estrenado) alVer() }.clip(formaSala(10.dp))
            .background(if (foco.actual == id && foco.visible) tinta.tarjeta else if (toca) tinta.tarjeta.copy(alpha = 0.5f) else Color.Transparent).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (v.episodio > 0) "${v.episodio}" else "·", color = if (p?.visto == true) tinta.gris else tinta.acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 38.sp, modifier = Modifier.width(76.dp))
            Column(Modifier.weight(1f)) {
                Text(v.titulo.ifBlank { "Episodio ${v.episodio}" }, color = if (estrenado) tinta.tinta else tinta.gris, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val linea = listOfNotNull(if (toca) "el siguiente" else null, if (v.estreno > 0) Formato.fecha(v.estreno) else null, if (!estrenado) "aún no se ha estrenado" else null,
                    if (p?.visto == true) "visto" else if (p != null && p.pos > 0 && p.dur > 0) "vas por ${tiempo(p.pos)}" else null)
                if (linea.isNotEmpty()) Text(linea.joinToString(" · ").uppercase(), color = tinta.gris, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                if (p != null && !p.visto && p.fraccion > 0f) Box(Modifier.padding(top = 6.dp).fillMaxWidth(0.6f).height(4.dp).background(tinta.linea)) { Box(Modifier.fillMaxWidth(p.fraccion).fillMaxHeight().background(tinta.acento)) }
            }
        }
        if (estrenado) { Spacer(Modifier.width(14.dp)); ChipSala("Fuentes", "f:epf:${v.id}", false) { alFuentes() } }
        if (t != null) {
            Spacer(Modifier.width(14.dp))
            ChipSala(if (p?.visto == true) "✓ Visto" else "○ Visto", "f:epv:${v.id}", p?.visto == true) { Biblioteca.marcaVisto(t, v, p?.visto != true, m) }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SimilaresSala(s: List<MetaBreve>, est: EstadoSala, ctl: ControlSala) {
    AnteSala("Más como esta")
    CompositionLocalProvider(LocalBringIntoViewSpec provides remember { TraerAVista(300f) }) {
        LazyRow(Modifier.fillMaxWidth().focusGroup(), contentPadding = PaddingValues(top = 26.dp, bottom = 18.dp, end = 20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            itemsIndexed(s, key = { _, x -> x.tipo + ":" + x.id }) { _, x -> CartaSala(x, idCarta("f:mas", x), est, ctl, ancho = 170.dp) }
        }
    }
}

/**
 * Todas las fuentes de un vídeo en la sala, agrupadas por addon (en tu orden), cada una con lo que se entiende de ella (calidad, idioma,
 * tamaño) y si es directa, torrent, YouTube o una web. La que elegiría Mostaza va marcada y es la primera que se enfoca. OK la pone.
 */
@Composable
fun PanelFuentes(f: PideFuentes, est: EstadoSala, ctl: ControlSala) {
    val ctx = LocalContext.current
    val tinta = LocalTinta.current
    val b = f.breve; val v = f.video
    val tipo = b.tipo.ifBlank { if (v != null) "series" else "movie" }
    var todas by remember { mutableStateOf<List<Candidata>>(emptyList()) }
    var terminado by remember { mutableStateOf(false) }
    LaunchedEffect(b.id, v?.id) {
        todas = runCatching { Busqueda.fuentes(tipo, v?.id ?: b.id) { parciales -> todas = parciales } }.getOrDefault(todas)
        terminado = true
    }
    val e = remember(todas) { Busqueda.elige(ctx, todas, f.meta) }
    val porAddon = todas.groupBy { it.ordenAddon to it.addon }.toSortedMap(compareBy { it.first })
    val enOrden = porAddon.values.flatMap { cs -> cs.sortedBy { it.ordenEnAddon } }
    // Lo primero que se enfoca: la que elegiría Mostaza (y, si aún no hay ninguna, la primera que llegue)
    val iElegida = enOrden.indexOfFirst { it.fuente === e.elegida?.fuente }.coerceAtLeast(0)
    LaunchedEffect(enOrden.isNotEmpty()) { if (enOrden.isNotEmpty() && ctl.foco.actual == null) ctl.foco.pedido = "fu:$iElegida" }
    Lateral(b.nombre + (v?.let { if (it.temporada > 0) " · T${it.temporada}·E${it.episodio}" else "" } ?: ""), "Fuentes", ancho = 1080.dp, alCerrar = { ctl.cierra("fuentes") }) {
        if (!terminado) NotaSala("Preguntando a tus addons…", Modifier.padding(bottom = 12.dp), tam = 22.sp)
        if (terminado && todas.isEmpty()) NotaSala("Ninguno de tus addons da fuentes para esto. Los addons de fuentes los pones tú en Menú → Addons (con tu debrid configurado en el suyo).", tam = 22.sp)
        e.elegida?.let { c ->
            Text("Hathor pondría: " + c.analisis.resumen().ifBlank { c.addon } + (if (e.fueraDeIdioma) " — ninguna está en tus idiomas" else ""),
                color = tinta.gris, fontFamily = Body, fontSize = 17.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
        var i = 0
        porAddon.forEach { (k, cs) ->
            Spacer(Modifier.height(14.dp))
            AnteSala(k.second, tam = 15.sp)
            Spacer(Modifier.height(4.dp))
            cs.sortedBy { it.ordenEnAddon }.forEach { c ->
                val fu = c.fuente
                val marca = when (fu.tipo) {
                    TipoFuente.DIRECTA -> "Directa"; TipoFuente.TORRENT -> "Torrent"; TipoFuente.YOUTUBE -> "YouTube"; TipoFuente.EXTERNA -> "Web"
                    TipoFuente.NZB -> "Usenet"; TipoFuente.ARCHIVO -> "Comprimido"; TipoFuente.NADA -> ""
                }
                val elegidaEsta = fu === e.elegida?.fuente
                val titulo = c.analisis.resumen().ifBlank { fu.nombre.lineSequence().firstOrNull().orEmpty().ifBlank { marca } }
                val sub = listOfNotNull(marca.ifBlank { null }, if (elegidaEsta) "la mejor para ti" else null, fu.descripcion.lineSequence().firstOrNull()?.ifBlank { null } ?: fu.nombre.ifBlank { null })
                    .joinToString(" · ")
                OpcionSala(titulo, "fu:${i++}", sub, marcada = elegidaEsta) {
                    Reproducir.pon(ctx, b, f.meta, v, c, e.ordenadas)?.let { est.avisa(it) } ?: ctl.cierra("fuentes")
                }
            }
        }
    }
}
