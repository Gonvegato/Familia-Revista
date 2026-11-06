package com.gonzalo.mostaza

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Lo que enseñan los controles del reproductor (Compose encima del vídeo). */
class EstadoReproductor {
    var titulo by mutableStateOf(""); var subtitulo by mutableStateOf("")
    var reproduciendo by mutableStateOf(false); var cargando by mutableStateOf(true)
    var pos by mutableStateOf(0L); var dur by mutableStateOf(0L)
    var visibles by mutableStateOf(true)
    /** Un aviso que no se va solo (no se puede reproducir…). */
    var aviso by mutableStateOf<String?>(null)
    /** Un mensaje que se va solo («Ha entrado en Viendo»). */
    var mensaje by mutableStateOf<String?>(null)
    /** Se corta: se propone la siguiente fuente. */
    var corte by mutableStateOf<String?>(null)
    /** El episodio siguiente, y en cuántos segundos empieza (−1: sin cuenta atrás). */
    var siguiente by mutableStateOf<String?>(null); var cuentaAtras by mutableStateOf(-1)
    /** Para elegir audio o subtítulos: (título, opciones (texto, elegida), al elegir). */
    var eleccion by mutableStateOf<Triple<String, List<Pair<String, Boolean>>, (Int) -> Unit>?>(null)
    var fuente by mutableStateOf("")
}

/**
 * El reproductor de Mostaza (AndroidX Media3, ExoPlayer): las fuentes con `url` (http/https, también HLS y DASH; con las cabeceras de
 * `proxyHeaders` si las pide) y, con el motor de torrents, los torrents (por una dirección local). Subtítulos de tus addons y los que trae
 * el vídeo, elegir audio y subtítulos, seguir donde lo dejaste (aquí o en otro dispositivo), el episodio siguiente y «Abrir con otro
 * reproductor» para lo que este dispositivo no sabe decodificar. Mientras está abierto, la app no se actualiza (`Actualizador.ocupada`).
 */
@androidx.annotation.OptIn(UnstableApi::class)
class ReproductorActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var vista: PlayerView? = null
    private lateinit var encargo: Encargo
    private var visionado: Visionado? = null
    private val estado = EstadoReproductor()
    private val ambito = MainScope()
    private val principal = Handler(Looper.getMainLooper())
    private var url = ""
    private var cabeceras: Map<String, String> = emptyMap()
    private var torrentAbierto = ""
    private var listo = false
    private val cortes = ArrayList<Long>()
    private var cargandoDesde = 0L
    /** Cuándo se empezó a abrir esta fuente (para el tope de un torrent que no arranca). */
    private var arrancaDesde = 0L
    /** Desde cuándo el torrent que se está abriendo casi no baja nada. */
    private var lentoDesde = 0L
    private var subsDecididos = false
    /** Lo abierto es un vídeo de aviso de tu debrid ([esAvisoDelDebrid]): ya se ha pasado a otra. */
    private var avisoDelDebrid = false
    /** Probando fuentes hasta dar con una en tu idioma (solo si la eligió el elector solo). */
    private var tanteo: Tanteo? = null
    private val tanteando: Boolean get() = tanteo?.terminado == false
    private var ultimaInteraccion = SystemClock.elapsedRealtime()
    private var parado = false

    private val pulso = object : Runnable {
        override fun run() {
            val p = player ?: return
            estado.pos = p.currentPosition.coerceAtLeast(0L); estado.dur = p.duration.takeIf { it > 0 } ?: estado.dur
            if (p.isPlaying) visionado?.tick(estado.pos, estado.dur)
            visionado?.let { v -> if (v.entro && estado.mensaje == null && !avisadoEntrada) { avisadoEntrada = true; muestra("Ha entrado en «Viendo»") } }
            // Los controles se esconden solos mientras se reproduce
            if (estado.visibles && p.isPlaying && estado.eleccion == null && SystemClock.elapsedRealtime() - ultimaInteraccion > 4_000L) estado.visibles = false
            // Un torrent que aún no ha empezado: cómo va y, si tarda demasiado, otra fuente
            if (torrentAbierto.isNotBlank() && !listo) {
                val m = estado.mensaje
                if (m == null || m.startsWith("Cargando el torrent")) estado.mensaje = "Cargando el torrent…" + (runCatching { Torrents.fuente.estado(torrentAbierto) }.getOrNull()?.let { " · $it" } ?: "")
                // Sin bajar nada (o casi) durante 45 s: así no va a empezar
                val bajada = runCatching { Torrents.fuente.bajada(torrentAbierto) }.getOrNull()
                val ahora = SystemClock.elapsedRealtime()
                if (bajada != null && bajada < 8_000L) { if (lentoDesde == 0L) lentoDesde = ahora } else lentoDesde = 0L
                val parado = lentoDesde > 0 && ahora - lentoDesde > 45_000L
                if (arrancaDesde > 0 && (parado || ahora - arrancaDesde > TORRENT_MAX_ARRANQUE_MS + 60_000L) && estado.corte == null) {
                    arrancaDesde = 0L
                    if (!fallaEnTanteo()) siguienteCandidata()?.let { sig -> estado.corte = "Este torrent casi no baja (hay muy poca gente compartiéndolo). ¿Probar con ${sig.analisis.resumen().ifBlank { sig.addon }}?" }
                }
            } else if (listo && estado.mensaje?.startsWith("Cargando el torrent") == true) estado.mensaje = null
            // Cargando demasiado tiempo: se corta
            if (cargandoDesde > 0 && listo && SystemClock.elapsedRealtime() - cargandoDesde > 15_000L && estado.corte == null) seCorta()
            // Probando fuentes y se acaba el tiempo sin ver las pistas de esta: a la mejor comprobada, o se queda con esta
            if (tanteando && !subsDecididos) tanteo?.let { t -> val d = t.trasTiempo(encargo.elegida, SystemClock.elapsedRealtime()); if (t.terminado) haz(d) }
            principal.postDelayed(this, 1_000L)
        }
    }
    private var avisadoEntrada = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Con algo en marcha, la versión nueva no se instala (Android cerraría la app y te sacaría de la película); y el actualizador se
        // pone en marcha también si se entra directamente aquí
        Actualizador.ocupada++; Actualizador.init(applicationContext)
        iniciar(applicationContext)
        val e = Reproducir.toma()
        if (e == null) { finish(); return }
        encargo = e
        if (e.automatica) tanteo = nuevoTanteo(e)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        setContent { MostazaTheme { PantallaReproductor() } }
        abre(e)
    }

    @SuppressLint("ClickableViewAccessibility")
    @Composable
    private fun PantallaReproductor() {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView({ c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    estiloSubtitulos(subtitleView)
                    vista = this
                    player = this@ReproductorActivity.player
                }
            }, Modifier.fillMaxSize(), update = { it.player = player })
            ControlesReproductor(estado, alTocar = { tocado() }, alPlay = { alternar() }, alSaltar = { s -> salta(s) }, alIr = { p -> player?.seekTo(p); visionado?.sesion?.salto() },
                alVolver = { finish() }, alAudio = { eligeAudio() }, alSubs = { eligeSubs() }, alFuente = { eligeFuente() }, alOtro = { conOtro() },
                alSiguiente = { siguienteYa() }, alCancelarSiguiente = { estado.cuentaAtras = -1; estado.siguiente = null }, alCorte = { siguienteFuente() })
        }
    }

    private fun estiloSubtitulos(s: SubtitleView?) {
        s ?: return
        val tam = AjustesReproductor.subTam(this) / 100f
        s.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * tam)
        s.setStyle(if (AjustesReproductor.subFondo(this)) CaptionStyleCompat(AColor.WHITE, 0xB0000000.toInt(), AColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, AColor.BLACK, null)
            else CaptionStyleCompat(AColor.WHITE, AColor.TRANSPARENT, AColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AColor.BLACK, null))
    }

    private fun nombreDe(e: Encargo): Pair<String, String> {
        val v = e.video
        return e.breve.nombre to (if (v != null && v.temporada > 0) "T${v.temporada} · E${v.episodio}" + (if (v.titulo.isNotBlank()) " · ${v.titulo}" else "") else e.breve.ano)
    }

    /** Pone el encargo: la dirección (o el torrent), los subtítulos de tus addons y por dónde ibas. */
    private fun abre(e: Encargo) {
        encargo = e
        val (t, s) = nombreDe(e); estado.titulo = t; estado.subtitulo = s
        estado.fuente = e.elegida.analisis.resumen().ifBlank { e.elegida.addon }
        estado.cargando = true; estado.aviso = null; estado.corte = null; estado.siguiente = null; estado.cuentaAtras = -1
        listo = false; cortes.clear(); cargandoDesde = 0L; arrancaDesde = SystemClock.elapsedRealtime(); lentoDesde = 0L; subsDecididos = false; avisadoEntrada = false; avisoDelDebrid = false
        val v = visionado
        visionado = if (v != null && v.breve.id == e.breve.id && e.video != null && v.videoId != e.video.id) v.siguiente(e.video, e.meta)
            else Visionado(applicationContext, e.breve, e.meta, e.video, previo = e.previo, pendiente = e.pendiente)
        val f = e.elegida.fuente
        ambito.launch {
            try {
                url = if (f.tipo == TipoFuente.TORRENT) {
                    // Lo que va pasando, cada segundo (para que no parezca colgado), y un tope: si no arranca, a otra fuente
                    val ini = SystemClock.elapsedRealtime()
                    val tic = ambito.launch {
                        while (true) {
                            val como = withContext(Dispatchers.IO) { runCatching { Torrents.fuente.estado(f.infoHash) }.getOrNull() }
                            estado.mensaje = "Preparando el torrent… ${(SystemClock.elapsedRealtime() - ini) / 1000} s" + (como?.let { " · $it" } ?: "")
                            delay(1_000)
                        }
                    }
                    try {
                        withTimeoutOrNull(TORRENT_MAX_ARRANQUE_MS) { Torrents.fuente.prepara(f.infoHash, f.fileIdx, f.trackers, f.archivo.ifBlank { e.breve.nombre }) }
                            ?.also { torrentAbierto = f.infoHash; estado.mensaje = null }
                            ?: run {
                                runCatching { Torrents.fuente.para(f.infoHash) }
                                throw SinTorrent("Este torrent no arranca: en ${TORRENT_MAX_ARRANQUE_MS / 60_000} minutos y medio no ha llegado nada. Prueba con otra fuente.")
                            }
                    } finally { tic.cancel() }
                } else f.url
                cabeceras = f.cabeceras
                val tipo = e.breve.tipo.ifBlank { if (e.video != null) "series" else "movie" }
                val subs = withTimeoutOrNull(4_000) { runCatching { Addons.subtitulos(tipo, e.video?.id ?: e.breve.id, f) }.getOrDefault(emptyList()) }.orEmpty() + f.subtitulos
                empieza(url, subs, Reanudar.desde(applicationContext, visionado!!.videoId))
            } catch (x: SinTorrent) { if (!fallaEnTanteo()) { estado.cargando = false; estado.aviso = x.message } }
            catch (x: kotlinx.coroutines.CancellationException) { throw x }
            catch (x: Exception) { if (!fallaEnTanteo()) { estado.cargando = false; estado.aviso = "No se ha podido abrir esta fuente: ${x.message ?: "sin respuesta"}." } }
        }
    }

    private fun mimeSubtitulo(u: String): String {
        val p = Uri.parse(u).lastPathSegment.orEmpty().lowercase()
        return when { p.endsWith(".vtt") -> MimeTypes.TEXT_VTT; p.endsWith(".ass") || p.endsWith(".ssa") -> MimeTypes.TEXT_SSA; p.endsWith(".ttml") || p.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML; else -> MimeTypes.APPLICATION_SUBRIP }
    }

    private fun empieza(u: String, subs: List<Subtitulo>, desde: Long) {
        // (un torrent que empieza puede tardar en tener el trozo que se pide: el motor espera, y el reproductor tiene que esperarle)
        val espera = if (torrentAbierto.isNotBlank()) com.gonzalo.torrent.MotorTorrent.ESPERA_LECTURA_MS else 30_000
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setUserAgent("Mostaza/$VERSION")
            .setConnectTimeoutMs(20_000).setReadTimeoutMs(espera)
        // Las cabeceras de la fuente (las de tu debrid, o tu token de kDrive) van solo a su servidor, no a los de los subtítulos de fuera
        val host = Uri.parse(u).host.orEmpty()
        val cab = cabeceras
        val conCabeceras = androidx.media3.datasource.ResolvingDataSource.Factory(http) { ds ->
            if (cab.isNotEmpty() && ds.uri.host == host) ds.withAdditionalHeaders(cab) else ds
        }
        val datos = DefaultDataSource.Factory(this, conCabeceras)
        // Un reproductor nuevo para cada fuente (cada una con sus cabeceras)
        player?.let { viejo -> viejo.removeListener(oyente); viejo.release() }
        principal.removeCallbacks(pulso)
        // (con FFmpeg detrás de los decodificadores del aparato: el AC3 o el DTS que este no sepa, lo decodifica la app)
        val renderers = androidx.media3.exoplayer.DefaultRenderersFactory(this).setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val p = ExoPlayer.Builder(this, renderers).setMediaSourceFactory(DefaultMediaSourceFactory(datos))
            .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(this)).build().also { nuevo ->
                player = nuevo; vista?.player = nuevo
                nuevo.addListener(oyente)
                principal.post(pulso)
            }
        val audio = AjustesReproductor.audio(this); val texto = AjustesReproductor.subs(this)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverrides()
            .setPreferredAudioLanguage(audio.ifBlank { null }).setPreferredTextLanguage(texto.ifBlank { null }).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        val item = MediaItem.Builder().setUri(u).setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(estado.titulo).build())
            .setSubtitleConfigurations(subs.distinctBy { it.url }.take(40).map { s ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url)).setMimeType(mimeSubtitulo(s.url)).setLanguage(s.idioma.ifBlank { null })
                    .setLabel(s.etiqueta.ifBlank { idiomaBonito(s.idioma) }).setId(s.id).build()
            })
        val lower = u.substringBefore('?').lowercase()
        if (lower.endsWith(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8) else if (lower.endsWith(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD)
        p.setMediaItem(item.build(), desde)
        // (probando fuentes: no empieza hasta ver sus pistas, para no ver un trozo en otro idioma)
        p.prepare(); p.playWhenReady = !tanteando
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private val oyente = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            estado.reproduciendo = isPlaying
            visionado?.sesion?.reproduciendo(isPlaying)
            if (!isPlaying) visionado?.guarda(estado.pos, estado.dur)
            if (isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) visionado?.sesion?.salto()
        }

        override fun onPlaybackStateChanged(s: Int) {
            val p = player ?: return
            estado.cargando = s == Player.STATE_BUFFERING || s == Player.STATE_IDLE
            when (s) {
                Player.STATE_READY -> {
                    if (esAvisoDelDebrid(p)) return
                    listo = true; cargandoDesde = 0L; estado.dur = p.duration.coerceAtLeast(0L)
                    if (!subsDecididos) { subsDecididos = true; alVerPistas(p.currentTracks) }
                }
                Player.STATE_BUFFERING -> if (listo && p.playWhenReady) {
                    val ahora = SystemClock.elapsedRealtime()
                    cargandoDesde = ahora; cortes += ahora; cortes.removeAll { ahora - it > 120_000L }
                    if (cortes.size >= 3 && estado.corte == null) seCorta()
                }
                Player.STATE_ENDED -> { if (esAvisoDelDebrid(p)) return; visionado?.guarda(estado.dur, estado.dur, forzar = true); alTerminar() }
                else -> {}
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            // El audio que no se puede decodificar aquí (DTS, TrueHD… según el dispositivo): se avisa, con la salida de otro reproductor
            val audios = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (audios.isNotEmpty() && audios.none { g -> (0 until g.length).any { g.isTrackSupported(it) } })
                estado.aviso = "Este dispositivo no sabe reproducir el sonido de esta fuente (${audios.first().getTrackFormat(0).sampleMimeType?.substringAfter('/')?.uppercase().orEmpty()}). Prueba con otro reproductor o con otra fuente."
            if (!subsDecididos && tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }) { subsDecididos = true; alVerPistas(tracks) }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (fallaEnTanteo()) return
            estado.cargando = false
            val codec = error.errorCode in setOf(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
            estado.aviso = if (codec) "Este dispositivo no sabe reproducir este vídeo (su formato o su calidad). «Abrir con otro reproductor» suele poder (VLC, Just Player…), o prueba otra fuente."
                else "No se puede ver esta fuente ahora (no responde, o el enlace ha caducado). Prueba con otra."
        }
    }

    private fun nuevoTanteo(e: Encargo): Tanteo {
        val rotas = FuentesRotas.busca(applicationContext)
        return Tanteo(AjustesElector.prefs(applicationContext), e.candidatas.filter { Reproducir.accion(it.fuente) is Accion.Reproductor && (it === e.elegida || !rotas(Elector.clave(it.fuente))) },
            SystemClock.elapsedRealtime())
    }

    /**
     * ¿Lo que se ha abierto es un vídeo de aviso de tu debrid ([FuentesRotas]) y no lo que querías ver? Entonces no cuenta para nada (ni
     * la posición, ni el tiempo, ni «Visto»): se apunta en tu cuenta que esta fuente no vale y se pasa sola a la siguiente.
     */
    private fun esAvisoDelDebrid(p: Player): Boolean {
        if (avisoDelDebrid) return true
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        if (!FuentesRotas.esAviso(dur, encargo.meta?.minutos ?: 0)) return false
        avisoDelDebrid = true
        FuentesRotas.apunta(applicationContext, Elector.clave(encargo.elegida.fuente))
        // (lo de esta sesión sigue con la otra fuente; de este vídeo, nada)
        val v = visionado
        visionado = null
        p.playWhenReady = false
        estado.reproduciendo = false
        val previo = v?.sesion?.vistoMs ?: encargo.previo
        val pendiente = v?.takeIf { !it.enCuenta }?.pendiente() ?: encargo.pendiente
        val t = tanteo
        val otra = if (t != null && !t.terminado) {
            when (val d = t.trasFallo(encargo.elegida, SystemClock.elapsedRealtime())) { is Decision.Prueba -> d.candidata; is Decision.Vuelve -> d.candidata; else -> null }
        } else null
        val sig = otra ?: siguienteCandidata()
        if (sig == null) {
            tanteo?.cancela()
            estado.cargando = false; estado.mensaje = null
            estado.aviso = "Tu debrid no da este archivo: en vez de la película, el addon manda un vídeo de aviso. No queda otra fuente que probar sola; mira en «Fuente»."
            return true
        }
        estado.mensaje = "Tu debrid no da este archivo: probando otra…"
        principal.post {
            cierraTorrent()
            abre(Encargo(encargo.breve, encargo.meta, encargo.video, sig, encargo.candidatas, previo, pendiente, encargo.automatica))
        }
        return true
    }

    private fun pistasDe(tracks: Tracks, tipo: Int) = tracks.groups.filter { it.type == tipo }.flatMap { g -> (0 until g.length).map { g to it } }

    /**
     * Ya se ven las pistas de verdad del archivo: se recuerda lo que trae (en este dispositivo), se pone su mejor audio según tu orden (y
     * subtítulos en español si el audio no lo es y aceptas la original con subtítulos) y, si se están probando fuentes, se decide si esta vale.
     */
    private fun alVerPistas(tracks: Tracks) {
        // (las pistas de un vídeo de aviso no dicen nada de la fuente)
        player?.let { if (esAvisoDelDebrid(it)) return }
        val prefs = AjustesElector.prefs(applicationContext)
        val audios = pistasDe(tracks, C.TRACK_TYPE_AUDIO)
        val textos = pistasDe(tracks, C.TRACK_TYPE_TEXT)
        val v = Pistas.verifica(audios.map { (g, i) -> g.getTrackFormat(i).let { it.language to it.label } }, textos.map { (g, i) -> g.getTrackFormat(i).let { it.language to it.label } }, prefs)
        if (audios.isNotEmpty()) MemoriaPistas.apunta(applicationContext, Elector.clave(encargo.elegida.fuente), v)
        decidePistas(tracks, prefs)
        val t = tanteo
        if (t == null || t.terminado || audios.isEmpty()) { player?.playWhenReady = true; return }
        haz(t.tras(encargo.elegida, v, SystemClock.elapsedRealtime()))
    }

    /** Lo que ha decidido el [Tanteo]. */
    private fun haz(d: Decision) {
        when (d) {
            is Decision.SeQueda -> { estado.mensaje = null; player?.playWhenReady = true; d.mensaje?.let { muestra(it, 7_000L) } }
            is Decision.Prueba -> { estado.mensaje = d.mensaje; pruebaOtra(d.candidata) }
            is Decision.Vuelve -> { pruebaOtra(d.candidata); muestra(d.mensaje, 8_000L) }
            Decision.Nada -> { estado.mensaje = null; player?.playWhenReady = true }
        }
    }

    /** Falla la fuente que se está probando: ¿hay otra? (true si se encarga el tanteo y no hay que avisar) */
    private fun fallaEnTanteo(): Boolean {
        val t = tanteo ?: return false
        if (t.terminado) return false
        val d = t.trasFallo(encargo.elegida, SystemClock.elapsedRealtime())
        if (d is Decision.Nada) { estado.mensaje = null; return false }
        principal.post { haz(d) }
        return true
    }

    /** Otra fuente mientras se prueba: sin apuntar nada de esta (no se ha visto) y desde el mismo sitio. */
    private fun pruebaOtra(c: Candidata) {
        cierraTorrent()
        val v = visionado
        visionado = null
        abre(Encargo(encargo.breve, encargo.meta, encargo.video, c, encargo.candidatas, v?.sesion?.vistoMs ?: 0L, v?.takeIf { !it.enCuenta }?.pendiente().orEmpty(), encargo.automatica))
    }

    /**
     * El audio y los subtítulos. Audio: si tu idioma de audio es el español (el de serie), el mejor de este archivo según el orden del
     * elector (castellano antes que latino, o al revés). Subtítulos: si el audio ya está en tu idioma, sin ellos (salvo que los quieras
     * siempre); si no lo está y tu orden acepta la original con subtítulos, unos en español, si los hay.
     */
    private fun decidePistas(tracks: Tracks, prefs: PrefsElector) {
        val p = player ?: return
        val audioPref = AjustesReproductor.audio(this)
        val prefEspanol = audioPref.isBlank() || normal(audioPref) == "es"
        val audios = pistasDe(tracks, C.TRACK_TYPE_AUDIO)
        val pistas = audios.map { (g, i) -> val f = g.getTrackFormat(i); PistaAudio(Pistas.clasifica(f.language, f.label), g.isTrackSupported(i), g.isTrackSelected(i)) }
        var audio: Idioma? = pistas.firstOrNull { it.elegida }?.idioma
        if (prefEspanol) Pistas.mejorAudio(pistas, prefs)?.let { k ->
            val (g, i) = audios[k]
            audio = pistas[k].idioma
            if (!g.isTrackSelected(i)) p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
        }
        val audioSel = audios.firstOrNull { (g, i) -> g.isTrackSelected(i) }?.let { (g, i) -> g.getTrackFormat(i).language }
        val enMiIdioma = if (prefEspanol) audio?.espanol == true else audioSel != null && normal(audioSel) == normal(audioPref)
        if (enMiIdioma) {
            if (!AjustesReproductor.subsSiempre(this)) p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            return
        }
        if (Pistas.quiereSubtitulos(audio, prefs)) {
            val esp = pistasDe(tracks, C.TRACK_TYPE_TEXT).filter { (g, i) -> g.isTrackSupported(i) && g.getTrackFormat(i).let { Pistas.subtituloEspanol(it.language, it.label) } }
            if (esp.isEmpty() || esp.any { (g, i) -> g.isTrackSelected(i) }) return
            // (los de latino, después, salvo que en tu orden vaya el latino antes que el castellano)
            val latinoPrimero = prefs.idiomas.indexOf("latino").let { l -> l >= 0 && (prefs.idiomas.indexOf("castellano").let { c -> c < 0 || l < c }) }
            val (g, i) = esp.sortedBy { (g, i) -> g.getTrackFormat(i).let { (Pistas.clasifica(it.language, it.label) == Idioma.LATINO) != latinoPrimero } }.first()
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
        }
    }

    private fun normal(l: String) = when (val x = l.lowercase().take(3)) { "spa", "esp", "es-", "es" -> "es"; "eng", "en-", "en" -> "en"; else -> x.take(2) }

    private fun idiomaBonito(l: String): String = when (normal(l)) { "es" -> "Español"; "en" -> "Inglés"; "fr" -> "Francés"; "it" -> "Italiano"; "de" -> "Alemán"; "pt" -> "Portugués"; else -> l.ifBlank { "Subtítulos" } }

    private fun muestra(m: String, ms: Long = 4_000L) {
        estado.mensaje = m
        principal.postDelayed({ if (estado.mensaje == m) estado.mensaje = null }, ms)
    }

    private fun tocado() { ultimaInteraccion = SystemClock.elapsedRealtime(); estado.visibles = !estado.visibles || estado.eleccion != null }
    private fun alternar() { ultimaInteraccion = SystemClock.elapsedRealtime(); player?.let { if (it.isPlaying) it.pause() else it.play() } }
    private fun salta(seg: Int) { ultimaInteraccion = SystemClock.elapsedRealtime(); player?.let { it.seekTo((it.currentPosition + seg * 1000L).coerceIn(0L, it.duration.coerceAtLeast(0L))) } }

    private fun eligeAudio() {
        val p = player ?: return
        val g = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val ops = g.flatMap { gr -> (0 until gr.length).map { i -> gr to i } }
        if (ops.isEmpty()) { muestra("Esta fuente solo tiene un audio"); return }
        estado.eleccion = Triple("Audio", ops.map { (gr, i) -> val f = gr.getTrackFormat(i); listOfNotNull(f.label, f.language?.let { idiomaBonito(it) }, f.sampleMimeType?.substringAfter('/')?.uppercase(), if (f.channelCount > 0) "${f.channelCount} canales" else null).distinct().joinToString(" · ") + (if (!gr.isTrackSupported(i)) " (no se puede aquí)" else "") to gr.isTrackSelected(i) }) { k ->
            val (gr, i) = ops[k]
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(gr.mediaTrackGroup, i)).build()
            estado.eleccion = null
        }
    }

    private fun eligeSubs() {
        val p = player ?: return
        val g = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val ops = g.flatMap { gr -> (0 until gr.length).map { i -> gr to i } }
        val ninguno = p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || ops.none { (gr, i) -> gr.isTrackSelected(i) }
        estado.eleccion = Triple("Subtítulos", listOf("Sin subtítulos" to ninguno) + ops.map { (gr, i) -> val f = gr.getTrackFormat(i); (f.label ?: f.language?.let { idiomaBonito(it) } ?: "Pista ${i + 1}") to (!ninguno && gr.isTrackSelected(i)) }) { k ->
            p.trackSelectionParameters = if (k == 0) p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                else { val (gr, i) = ops[k - 1]; p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(gr.mediaTrackGroup, i)).build() }
            estado.eleccion = null
        }
    }

    /** Otra fuente de las que había (en el orden del elector). */
    private fun eligeFuente() {
        val cs = encargo.candidatas.filter { Reproducir.accion(it.fuente) is Accion.Reproductor }
        if (cs.size <= 1) { muestra("No hay otra fuente que se pueda poner aquí"); return }
        estado.eleccion = Triple("Fuente", cs.map { c -> (c.analisis.resumen().ifBlank { c.fuente.nombre.lineSequence().firstOrNull().orEmpty() } + " · " + c.addon) to (c === encargo.elegida) }) { k ->
            estado.eleccion = null
            cambiaA(cs[k])
        }
    }

    private fun cambiaA(c: Candidata) {
        // La eliges tú: se acabó probar solas
        tanteo?.cancela(); tanteo = null; estado.mensaje = null
        val pos = player?.currentPosition ?: 0L
        visionado?.guarda(pos, estado.dur, forzar = true)
        cierraTorrent()
        Reanudar.pon(applicationContext, visionado?.videoId ?: encargo.breve.id, pos, estado.dur)
        val v = visionado
        visionado = null   // (la misma sesión sigue con la otra fuente: lo visto y lo que falta por apuntar pasan al nuevo)
        abre(Encargo(encargo.breve, encargo.meta, encargo.video, c, encargo.candidatas, v?.sesion?.vistoMs ?: 0L, v?.takeIf { !it.enCuenta }?.pendiente().orEmpty()))
    }

    /** Se corta una y otra vez: se apunta que la conexión da menos y se propone la siguiente. */
    private fun seCorta() {
        val kbps = DefaultBandwidthMeter.getSingletonInstance(this).bitrateEstimate / 1000L
        Conexion.apunta(applicationContext, kbps, corte = true)
        val sig = siguienteCandidata() ?: return
        estado.corte = "Se corta. ¿Probar con ${sig.analisis.resumen().ifBlank { sig.addon }}?"
    }

    private fun siguienteCandidata(): Candidata? {
        val cs = encargo.candidatas.filter { Reproducir.accion(it.fuente) is Accion.Reproductor }
        val i = cs.indexOfFirst { it === encargo.elegida }
        val rotas = FuentesRotas.busca(applicationContext)
        return cs.drop(i + 1).firstOrNull { !rotas(Elector.clave(it.fuente)) }
    }

    private fun siguienteFuente() { estado.corte = null; siguienteCandidata()?.let { cambiaA(it) } }

    private fun conOtro() {
        val pos = player?.currentPosition ?: 0L
        player?.pause()
        if (encargo.elegida.fuente.privada) { muestra("Esta es de tu kDrive: no se abre en otra app (le darías tu token). Se ve aquí."); return }
        val r = Reproducir.conOtro(this, url, estado.titulo, pos, cabeceras)
        if (r != null) muestra(r) else finish()
    }

    // ---------------------------------------------------------------- el episodio siguiente

    private fun videoSiguiente(): Video? {
        val m = encargo.meta ?: return null
        val v = encargo.video ?: return null
        val eps = m.videos.filter { it.temporada > 0 }.sortedWith(compareBy({ it.temporada }, { it.episodio }))
        val i = eps.indexOfFirst { it.id == v.id }
        return eps.getOrNull(i + 1)?.takeIf { it.estrenado() }
    }

    private fun alTerminar() {
        val sig = videoSiguiente()
        if (sig == null || !AjustesReproductor.siguiente(this)) { estado.visibles = true; return }
        estado.siguiente = "T${sig.temporada} · E${sig.episodio}" + if (sig.titulo.isNotBlank()) " · ${sig.titulo}" else ""
        estado.cuentaAtras = 8
        val cuenta = object : Runnable {
            override fun run() {
                if (estado.cuentaAtras < 0) return
                if (estado.cuentaAtras == 0) { siguienteYa(); return }
                estado.cuentaAtras--; principal.postDelayed(this, 1_000L)
            }
        }
        principal.postDelayed(cuenta, 1_000L)
    }

    /** El siguiente episodio: sus fuentes, y la que toca (la del mismo grupo que esta, si la hay). */
    private fun siguienteYa() {
        val sig = videoSiguiente() ?: return
        estado.cuentaAtras = -1; estado.siguiente = null; estado.cargando = true
        val grupo = encargo.elegida.fuente.grupo
        ambito.launch {
            val cs = Addons.fuentes("series", sig.id)
            val m = encargo.meta
            val red = Conexion.red(applicationContext)
            val e = Elector.elige(cs, AjustesElector.prefs(applicationContext), red, Conexion.capacidades(applicationContext), m?.minutos ?: 0, Torrents.usables(applicationContext, red), grupo,
                MemoriaPistas.busca(applicationContext), FuentesRotas.busca(applicationContext))
            val c = e.elegida ?: run { estado.cargando = false; estado.aviso = "No hay fuentes para el episodio siguiente."; return@launch }
            visionado?.termina(estado.dur, estado.dur)
            cierraTorrent()
            val nuevo = Encargo(encargo.breve, m, sig, c, e.ordenadas, automatica = true)
            tanteo = nuevoTanteo(nuevo)
            abre(nuevo)
        }
    }

    private fun cierraTorrent() { if (torrentAbierto.isNotBlank()) { runCatching { Torrents.fuente.para(torrentAbierto) }; torrentAbierto = "" } }

    // ---------------------------------------------------------------- el mando (cruceta, OK, reproducir/pausa)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        ultimaInteraccion = SystemClock.elapsedRealtime()
        if (!estado.visibles && estado.eleccion == null) when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { salta(-AjustesReproductor.salto(this)); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { salta(AjustesReproductor.salto(this)); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { estado.visibles = true; return true }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { alternar(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        // Mientras se ve, los widgets no se repintan (ver Widgets.reproductorAbierto)
        Widgets.reproductorAbierto = true
        // Volver al reproductor después de salir es otra sesión (la de antes terminó al irte)
        if (parado) { parado = false; visionado = visionado?.let { v -> Visionado(applicationContext, v.breve, v.meta, v.video) } }
    }

    override fun onStop() {
        super.onStop()
        // Salir del reproductor termina la sesión (regla 1): lo de antes no se junta con lo de cuando vuelvas
        player?.pause()
        visionado?.termina(player?.currentPosition ?: estado.pos, estado.dur)
        parado = true
        val kbps = DefaultBandwidthMeter.getSingletonInstance(this).bitrateEstimate / 1000L
        if (listo && cortes.isEmpty()) Conexion.apunta(applicationContext, kbps)
        // Y ahora sí, los widgets: «Seguir viendo» con lo que acabas de ver
        Widgets.reproductorCerrado(applicationContext)
    }

    override fun onDestroy() {
        principal.removeCallbacksAndMessages(null)
        ambito.cancel()
        if (::encargo.isInitialized && !parado) visionado?.termina(player?.currentPosition ?: estado.pos, estado.dur)
        cierraTorrent()
        player?.release(); player = null
        Actualizador.ocupada = (Actualizador.ocupada - 1).coerceAtLeast(0)
        super.onDestroy()
    }
}

/** Los controles: arriba el título, en medio reproducir y saltar, abajo la barra y lo de audio, subtítulos, fuente y otro reproductor. */
@Composable
fun ControlesReproductor(
    e: EstadoReproductor, alTocar: () -> Unit, alPlay: () -> Unit, alSaltar: (Int) -> Unit, alIr: (Long) -> Unit, alVolver: () -> Unit,
    alAudio: () -> Unit, alSubs: () -> Unit, alFuente: () -> Unit, alOtro: () -> Unit, alSiguiente: () -> Unit, alCancelarSiguiente: () -> Unit, alCorte: () -> Unit,
) {
    val blanco = Color(0xFFF7F2E8)
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { alTocar() }) {
        if (e.cargando && e.aviso == null) Text("Cargando…", color = blanco, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
        if (e.visibles || e.aviso != null) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent, Color.Transparent, Color(0xCC000000)))))
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text("← VOLVER", color = blanco, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp, modifier = Modifier.tocable { alVolver() }.padding(vertical = 8.dp))
                Text(e.titulo, color = blanco, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.subtitulo.isNotBlank()) Text(e.subtitulo.uppercase(), color = blanco.copy(alpha = 0.8f), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1)
                if (e.fuente.isNotBlank()) Text(e.fuente, color = blanco.copy(alpha = 0.7f), fontFamily = Body, fontSize = 12.sp, maxLines = 1)
            }
            if (e.aviso == null && !e.cargando) Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonReproductor(Icons.Rounded.Replay10, "Atrás", blanco, 52.dp) { alSaltar(-10) }
                BotonReproductor(if (e.reproduciendo) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (e.reproduciendo) "Pausa" else "Reproducir", blanco, 76.dp) { alPlay() }
                BotonReproductor(Icons.Rounded.Forward10, "Adelante", blanco, 52.dp) { alSaltar(10) }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
                if (e.dur > 0) {
                    Slider(e.pos.toFloat().coerceIn(0f, e.dur.toFloat()), { alIr(it.toLong()) }, valueRange = 0f..e.dur.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Acento, activeTrackColor = Acento, inactiveTrackColor = blanco.copy(alpha = 0.3f)))
                    Row(Modifier.fillMaxWidth()) {
                        Text(tiempo(e.pos), color = blanco, fontFamily = Body, fontSize = 12.sp); Spacer(Modifier.weight(1f))
                        Text("−" + tiempo((e.dur - e.pos).coerceAtLeast(0L)), color = blanco, fontFamily = Body, fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    EnlaceClaro("Audio", blanco, alAudio); EnlaceClaro("Subtítulos", blanco, alSubs); EnlaceClaro("Fuente", blanco, alFuente)
                    Spacer(Modifier.weight(1f))
                    EnlaceClaro("Abrir con otro reproductor", blanco, alOtro)
                }
            }
        }
        // (debajo de «Cargando…», en el centro: arriba tapaba el título)
        e.mensaje?.let { m -> Text(m, color = blanco, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).offset(y = 64.dp).widthIn(max = 560.dp).padding(horizontal = 24.dp).background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 8.dp)) }
        e.aviso?.let { a ->
            Column(Modifier.align(Alignment.Center).widthIn(max = 520.dp).padding(24.dp).background(Paper).padding(22.dp)) {
                Antetitulo("No se puede", Modifier.padding(bottom = 8.dp))
                Text(a, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(10.dp))
                MagLink("Abrir con otro reproductor", accent = true) { alOtro() }
                MagLink("Probar otra fuente") { alFuente() }
                MagLink("Volver") { alVolver() }
            }
        }
        e.corte?.let { c ->
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 110.dp).background(Paper).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c, color = Charcoal, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(14.dp))
                EnlaceRevista("Sí, cambiar", acento = true) { alCorte() }
            }
        }
        e.siguiente?.let { s ->
            Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(24.dp).background(Paper).padding(18.dp)) {
                Antetitulo("Siguiente", Modifier.padding(bottom = 4.dp))
                Text(s, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    EnlaceRevista(if (e.cuentaAtras >= 0) "Ver ya (${e.cuentaAtras})" else "Ver ya", acento = true) { alSiguiente() }
                    EnlaceRevista("Cancelar") { alCancelarSiguiente() }
                }
            }
        }
        e.eleccion?.let { (titulo, ops, elegir) ->
            Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 420.dp).fillMaxWidth(0.9f).background(Paper).statusBarsPadding().padding(20.dp).verticalScroll(rememberScrollState())) {
                Antetitulo(titulo, Modifier.padding(bottom = 6.dp))
                ops.forEachIndexed { i, (t, sel) ->
                    Text((if (sel) "✓ " else "   ") + t, color = if (sel) Acento else Charcoal, fontFamily = Body, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().tocable { elegir(i) }.padding(vertical = 10.dp))
                    Filete(1.dp, Line)
                }
                EnlaceRevista("Cerrar") { e.eleccion = null }
            }
        }
    }
}

/** Un botón del reproductor: el icono a buen tamaño (se ve desde el sofá), con el marco de foco del mando. */
@Composable
private fun BotonReproductor(icono: androidx.compose.ui.graphics.vector.ImageVector, descripcion: String, color: Color, tam: androidx.compose.ui.unit.Dp, alHacer: () -> Unit) {
    Box(Modifier.size(tam + 20.dp).tocable(fuera = 2.dp) { alHacer() }.semantics { contentDescription = descripcion }, contentAlignment = Alignment.Center) {
        Icon(icono, null, tint = color, modifier = Modifier.size(tam))
    }
}

@Composable
private fun EnlaceClaro(t: String, color: Color, alHacer: () -> Unit) =
    Text(t.uppercase(), color = color, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.3.sp, maxLines = 1, modifier = Modifier.tocable { alHacer() }.padding(vertical = 10.dp))

/** «1:23:45» o «23:45». */
fun tiempo(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}
