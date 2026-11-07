package com.gonzalo.cobre

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.Episodio
import com.gonzalo.iptv.Programa
import com.gonzalo.iptv.Progreso
import com.gonzalo.iptv.Visto
import com.gonzalo.iptv.Xtream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que enseñan los controles del reproductor (Compose encima del vídeo). */
class EstadoReproductor {
    var titulo by mutableStateOf(""); var subtitulo by mutableStateOf(""); var logo by mutableStateOf(""); var numero by mutableStateOf(0)
    var directo by mutableStateOf(true)
    var ahora by mutableStateOf<Programa?>(null); var despues by mutableStateOf<Programa?>(null)
    var reproduciendo by mutableStateOf(false); var cargando by mutableStateOf(true)
    var pos by mutableStateOf(0L); var dur by mutableStateOf(0L)
    /** Los controles (y, en directo, la banda del canal). */
    var visibles by mutableStateOf(true)
    /** La lista de canales para cambiar al momento (en directo). */
    var lista by mutableStateOf<List<Canal>?>(null); var enLista by mutableStateOf(0)
    var aviso by mutableStateOf<String?>(null)
    var mensaje by mutableStateOf<String?>(null)
    var siguiente by mutableStateOf<String?>(null); var cuentaAtras by mutableStateOf(-1)
    var eleccion by mutableStateOf<Triple<String, List<Pair<String, Boolean>>, (Int) -> Unit>?>(null)
    var tecleado by mutableStateOf("")
    var aspecto by mutableStateOf(AjustesReproductor.Aspecto.AJUSTAR)
    /** Preguntar antes de dar la dirección (con tu contraseña) a otra app. */
    var confirmaOtro by mutableStateOf(false)
    /** La guía del canal de ahora (para la banda). */
    var guiaDe: (Canal) -> Pair<Programa?, Programa?> = { null to null }
    /** La calidad del canal de ahora (en los grupos del fútbol: «Ultra HD», «HD», «SD»), o null. */
    var calidad by mutableStateOf<String?>(null)
    /** «Seguir sonando al salir»: encendido o no (null: no se ofrece, como en la tele). */
    var segundoPlano by mutableStateOf<Boolean?>(null)
}

/** La última velocidad que ha medido de verdad el reproductor (para elegir la calidad del fútbol). */
object Medida {
    @Volatile private var kbps = 0L
    @Volatile private var cuando = 0L
    fun pon(bps: Long) { if (bps > 0) { kbps = bps / 1000; cuando = SystemClock.elapsedRealtime() } }
    /** Lo medido en la última media hora (kb/s), o 0. */
    fun kbps(): Long = if (cuando > 0 && SystemClock.elapsedRealtime() - cuando < 30 * 60_000L) kbps else 0L
}

/**
 * El reproductor de Cobre (AndroidX Media3, ExoPlayer): canales en directo (MPEG-TS, HLS y DASH; si una dirección sin extensión resulta ser
 * HLS, se vuelve a probar como tal), películas y episodios. Con el sonido que el aparato no sepa (AC3, DTS…), FFmpeg (el de Jellyfin).
 *
 * En directo: arriba y abajo del mando (y los botones de canal) cambian de canal, los números van al canal por su número, OK abre la lista
 * de canales del grupo con lo que echan, y la banda dice qué se emite ahora y después. Si se corta, vuelve a conectar solo (unas cuantas
 * veces, cada vez esperando un poco más). En el videoclub: sigue donde lo dejaste (aquí o en otro dispositivo), apunta lo visto en tu cuenta
 * y pone el episodio siguiente. Las cabeceras que pide un canal (user-agent, referer) van solo a su servidor. La dirección de lo que se ve
 * lleva tu usuario y tu contraseña: no se enseña, no se apunta en ningún registro (los de Media3 están apagados aquí) y solo sale de la app si
 * tú lo pides («Abrir con otro reproductor», que lo avisa antes).
 */
@androidx.annotation.OptIn(UnstableApi::class)
class ReproductorActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var vista: PlayerView? = null
    private lateinit var encargo: Encargo
    private val estado = EstadoReproductor()
    private val ambito = MainScope()
    private val principal = Handler(Looper.getMainLooper())
    private var url = ""
    private var cabeceras: Map<String, String> = emptyMap()
    private var comoHls = false
    private var listo = false
    private var reintentos = 0
    private var ultimaInteraccion = SystemClock.elapsedRealtime()
    private var guardadoEn = 0L
    private var guiaEn = 0L
    private var parado = false
    /** «Seguir sonando al salir» en este reproductor (el ajuste o el botón). */
    private var segundoPlano = false
    /** En un grupo del fútbol: lo ya probado sin éxito (entrada, calidad), para pasar a la siguiente calidad y luego al siguiente canal. */
    private val probadas = HashSet<Pair<Int, com.gonzalo.iptv.futbol.Calidad>>()

    private val pulso = object : Runnable {
        override fun run() {
            val p = player ?: return
            estado.pos = p.currentPosition.coerceAtLeast(0L); estado.dur = p.duration.takeIf { it > 0 && !estado.directo } ?: estado.dur
            if (!estado.directo && p.isPlaying && SystemClock.elapsedRealtime() - guardadoEn > 15_000L) guarda()
            // La guía del canal, cada medio minuto (lo de ahora cambia)
            if (estado.directo && SystemClock.elapsedRealtime() - guiaEn > 30_000L) refrescaGuia()
            if (estado.visibles && p.isPlaying && estado.eleccion == null && estado.lista == null && SystemClock.elapsedRealtime() - ultimaInteraccion > 5_000L) estado.visibles = false
            principal.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Los registros de Media3 llevan a veces la dirección de lo que falla (y la dirección lleva tu contraseña): aquí, apagados
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
        Actualizador.ocupada++; Actualizador.init(applicationContext)
        iniciar(applicationContext)
        val e = Reproducir.toma()
        if (e == null) { finish(); return }
        encargo = e
        estado.aspecto = AjustesReproductor.aspecto(this)
        estado.guiaDe = { c -> Tele.enMemoria(encargo.lista.id)?.let { Tele.ahoraYDespuesRapido(it, c) } ?: (null to null) }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        if (!esTele(this)) { segundoPlano = AjustesReproductor.segundoPlano(this); estado.segundoPlano = segundoPlano }
        ReproduccionService.alParar = { principal.post { finish() } }
        setContent { CobreTheme { Pantalla() } }
        abre()
    }

    @SuppressLint("ClickableViewAccessibility")
    @Composable
    private fun Pantalla() {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val forzado = when (estado.aspecto) { AjustesReproductor.Aspecto.ANCHO -> 16f / 9f; AjustesReproductor.Aspecto.ESTRECHO -> 4f / 3f; else -> null }
            AndroidView({ c ->
                PlayerView(c).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    subtitleView?.setStyle(CaptionStyleCompat(AColor.WHITE, AColor.TRANSPARENT, AColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AColor.BLACK, null))
                    vista = this
                    player = this@ReproductorActivity.player
                }
            }, (if (forzado != null) Modifier.fillMaxHeight().aspectRatio(forzado, matchHeightConstraintsFirst = true) else Modifier.fillMaxSize()),
                update = { v ->
                    v.player = player
                    v.resizeMode = when (estado.aspecto) {
                        AjustesReproductor.Aspecto.AJUSTAR -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AjustesReproductor.Aspecto.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                })
            ControlesReproductor(estado, alTocar = { tocado() }, alPlay = { alternar() }, alSaltar = { salta(it) }, alIr = { player?.seekTo(it) }, alVolver = { finish() },
                alAudio = { eligeAudio() }, alSubs = { eligeSubs() }, alAspecto = { cambiaAspecto() }, alOtro = { estado.confirmaOtro = true }, alOtroSi = { conOtro() },
                alCanal = { d -> zapea(d) }, alLista = { abreLista() }, alElegirCanal = { i -> vaACanal(i) }, alSiguiente = { siguienteYa() },
                alCancelarSiguiente = { estado.cuentaAtras = -1; estado.siguiente = null }, alReintentar = { reintenta(manual = true) },
                alCalidad = { cambiaCalidad() }, alSegundoPlano = { activaSegundoPlano(!segundoPlano) })
        }
    }

    // ---------------------------------------------------------------- abrir lo que toca

    private val canalActual: Canal? get() = (encargo as? Encargo.Directo)?.let { it.canales.getOrNull(it.indice) }

    private fun abre() {
        estado.aviso = null; estado.mensaje = null; estado.siguiente = null; estado.cuentaAtras = -1; estado.cargando = true
        listo = false; reintentos = 0; comoHls = false
        val l = encargo.lista
        when (val e = encargo) {
            is Encargo.Directo -> {
                val c = e.canales[e.indice]
                estado.directo = true; estado.titulo = c.nombre; estado.numero = if (c.numero > 0) c.numero else e.indice + 1; estado.logo = c.logo; estado.subtitulo = e.plan?.grupo ?: c.grupo
                estado.calidad = e.plan?.calidadDe(e.indice, c)?.texto
                estado.dur = 0L; estado.ahora = null; estado.despues = null
                if (c.conDrm) { estado.cargando = false; estado.aviso = "Este canal va cifrado con DRM (Widevine o parecido): Nut no lo puede abrir. Prueba con otro reproductor."; return }
                url = Tele.url(l, c); cabeceras = Tele.cabeceras(l, c)
                MiCuenta.ponUltimo(l.id, c)
                refrescaGuia()
                estado.visibles = true; ultimaInteraccion = SystemClock.elapsedRealtime()
                empieza(0L)
            }
            is Encargo.DePelicula -> {
                estado.directo = false; estado.titulo = e.pelicula.nombre; estado.subtitulo = e.pelicula.ano; estado.logo = ""
                url = Xtream(l, Tele.red).urlPelicula(e.pelicula); cabeceras = if (l.agente.isNotBlank()) mapOf("User-Agent" to l.agente) else emptyMap()
                estado.dur = e.duracionSeg * 1000L
                empieza(desde(DatosCuenta.videoPelicula(e.pelicula)))
            }
            is Encargo.DeEpisodio -> {
                val ep = e.episodio
                estado.directo = false; estado.titulo = e.serie.nombre; estado.subtitulo = "T${ep.temporada} · E${ep.numero}" + if (ep.titulo.isNotBlank()) " · ${tituloEpisodio(ep, e.serie.nombre)}" else ""
                url = Xtream(l, Tele.red).urlEpisodio(ep); cabeceras = if (l.agente.isNotBlank()) mapOf("User-Agent" to l.agente) else emptyMap()
                estado.dur = ep.duracionSeg * 1000L
                empieza(desde(DatosCuenta.videoEpisodio(ep)))
            }
        }
    }

    /** Desde dónde seguir: lo más reciente entre lo de la cuenta (lo visto en otro dispositivo) y lo de aquí. */
    private fun desde(video: String): Long {
        val cuenta = MiCuenta.progreso(encargo.lista.id, video)
        val aqui = Reanudar.de(this, encargo.lista.id, video)
        val (pos, dur) = when {
            cuenta != null && (aqui == null || cuenta.ultima >= aqui.third) -> cuenta.pos to cuenta.dur
            aqui != null -> aqui.first to aqui.second
            else -> return 0L
        }
        return Visto.desde(pos, dur)
    }

    private fun empieza(desde: Long) {
        val agente = cabeceras.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: "Cobre/$VERSION"
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setUserAgent(agente).setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        // Las cabeceras del canal (referer, origin…) van solo a su servidor
        val host = Uri.parse(url).host.orEmpty()
        val cab = cabeceras.filterKeys { !it.equals("User-Agent", true) }
        val conCabeceras = ResolvingDataSource.Factory(http) { ds -> if (cab.isNotEmpty() && ds.uri.host == host) ds.withAdditionalHeaders(cab) else ds }
        val datos = DefaultDataSource.Factory(this, conCabeceras)
        player?.let { viejo -> viejo.removeListener(oyente); viejo.release() }
        principal.removeCallbacks(pulso)
        // (con FFmpeg detrás de los decodificadores del aparato: el AC3 o el DTS que este no sepa, lo decodifica la app)
        val renderers = DefaultRenderersFactory(this).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val p = ExoPlayer.Builder(this, renderers).setMediaSourceFactory(DefaultMediaSourceFactory(datos)).build().also { nuevo ->
            player = nuevo; if (!parado || !segundoPlano) vista?.player = nuevo
            nuevo.addListener(oyente)
            // (la velocidad que mide el reproductor: con ella se elige la calidad del fútbol la próxima vez)
            nuevo.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                override fun onBandwidthEstimate(t: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, ms: Int, bytes: Long, bps: Long) = Medida.pon(bps)
            })
            principal.post(pulso)
            if (segundoPlano) ReproduccionService.pon(this, nuevo)
        }
        val audio = AjustesReproductor.audio(this); val texto = AjustesReproductor.subs(this)
        var tsp = p.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(audio.ifBlank { null }).setPreferredTextLanguage(texto.ifBlank { null })
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, texto.isBlank())
        // Con datos del móvil, la calidad más baja que dé el canal (en los que dan varias: HLS y DASH)
        if (AjustesReproductor.ahorro(this) && Tele.datosMoviles(this)) tsp = tsp.setForceLowestBitrate(true)
        p.trackSelectionParameters = tsp.build()
        val item = MediaItem.Builder().setUri(url).setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(estado.titulo).setArtist(estado.subtitulo.ifBlank { "Nut" }).build())
        val ruta = Uri.parse(url).path.orEmpty().lowercase()
        when {
            comoHls || ruta.endsWith(".m3u8") || ruta.endsWith(".m3u") -> item.setMimeType(MimeTypes.APPLICATION_M3U8)
            ruta.endsWith(".mpd") -> item.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        if (estado.directo) p.setMediaItem(item.build()) else p.setMediaItem(item.build(), desde)
        p.prepare(); p.playWhenReady = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private val oyente = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            estado.reproduciendo = isPlaying
            if (!isPlaying && !estado.directo) guarda()
            if (isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        override fun onPlaybackStateChanged(s: Int) {
            val p = player ?: return
            estado.cargando = s == Player.STATE_BUFFERING || s == Player.STATE_IDLE
            when (s) {
                Player.STATE_READY -> {
                    listo = true; reintentos = 0
                    if (estado.mensaje?.startsWith("Se ha cortado") == true) estado.mensaje = null
                    if (!estado.directo) estado.dur = p.duration.coerceAtLeast(0L)
                }
                // Un canal en directo no se acaba: si el servidor corta, se vuelve a conectar
                Player.STATE_ENDED -> if (estado.directo) reintenta() else { guarda(fin = true); alTerminar() }
                else -> {}
            }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val audios = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (audios.isNotEmpty() && audios.none { g -> (0 until g.length).any { g.isTrackSupported(it) } })
                estado.aviso = "Este dispositivo no sabe reproducir el sonido de esto (${audios.first().getTrackFormat(0).sampleMimeType?.substringAfter('/')?.uppercase().orEmpty()}). Prueba con otro reproductor."
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            // Se ha quedado atrás del directo: al directo otra vez
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) { p.seekToDefaultPosition(); p.prepare(); return }
            // Una dirección sin extensión que resulta ser HLS: otra vez, como HLS
            if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && !comoHls) { comoHls = true; empieza(if (estado.directo) 0L else estado.pos); return }
            val red = error.errorCode in 2000..2999
            val codec = error.errorCode in 4000..4999 || error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
            // Un canal de un grupo del fútbol que no arranca: la siguiente calidad y, luego, el siguiente canal del grupo
            if (!listo && probarOtro()) return
            if (red && (estado.directo || listo) && AjustesReproductor.reconectar(this@ReproductorActivity) && reintentos < 6) { reintenta(); return }
            estado.cargando = false
            val http = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
            estado.aviso = when {
                codec -> "Este dispositivo no sabe reproducir esto (su formato o su calidad). «Abrir con otro reproductor» suele poder (VLC, Just Player…)."
                http == 401 || http == 403 -> "El servidor no deja verlo ($http): ¿ha caducado tu cuenta o estás viendo en demasiados sitios a la vez?"
                http == 404 -> "El servidor dice que eso ya no está (404)."
                http != null -> "El servidor ha contestado con un error ($http)."
                else -> "No se puede ver ahora (el servidor no responde, o no hay conexión)."
            }
        }
    }

    /** En un grupo del fútbol, lo siguiente que probar ([com.gonzalo.iptv.futbol.Calidades.siguienteIntento]); false si no hay plan o no queda nada. */
    private fun probarOtro(): Boolean {
        val e = encargo as? Encargo.Directo ?: return false
        val plan = e.plan ?: return false
        val c = e.canales.getOrNull(e.indice) ?: return false
        val q = plan.calidadDe(e.indice, c) ?: return false
        probadas += e.indice to q
        val (i, q2) = com.gonzalo.iptv.futbol.Calidades.siguienteIntento(plan.entradas, e.indice, q, plan.quiero, probadas) ?: return false
        val nuevo = plan.canales[i][q2] ?: return false
        val canales = e.canales.toMutableList().also { it[i] = nuevo }
        encargo = Encargo.Directo(e.lista, canales, i, plan)
        abre()
        estado.mensaje = "No arrancaba: probando ${nuevo.nombre}…"
        principal.postDelayed({ if (estado.mensaje?.startsWith("No arrancaba") == true) estado.mensaje = null }, 6_000L)
        return true
    }

    /** En un grupo del fútbol: la otra calidad del canal de ahora (Ultra HD → HD → SD → Ultra HD…), y se queda para los demás. */
    private fun cambiaCalidad() {
        ultimaInteraccion = SystemClock.elapsedRealtime()
        val e = encargo as? Encargo.Directo ?: return
        val plan = e.plan ?: return
        val hay = plan.canales.getOrNull(e.indice) ?: return
        if (hay.size < 2) return
        val ahora = plan.calidadDe(e.indice, e.canales[e.indice]) ?: return
        val todas = com.gonzalo.iptv.futbol.Calidad.entries
        val sig = (1..todas.size).map { todas[(ahora.ordinal + it) % todas.size] }.first { hay.containsKey(it) }
        plan.quiero = sig
        // (los demás canales del grupo, también en esa calidad si la tienen)
        val canales = e.canales.mapIndexed { i, c -> plan.canales.getOrNull(i)?.let { m -> com.gonzalo.iptv.futbol.Calidades.orden(sig).firstNotNullOfOrNull { m[it] } } ?: c }
        probadas.clear()
        encargo = Encargo.Directo(e.lista, canales, e.indice, plan)
        abre()
        estado.mensaje = "Calidad: ${sig.texto}"
        principal.postDelayed({ if (estado.mensaje?.startsWith("Calidad:") == true) estado.mensaje = null }, 2_500L)
    }

    /** «Seguir sonando al salir», ahora (sin tocar el ajuste de siempre). */
    private fun activaSegundoPlano(on: Boolean) {
        if (esTele(this)) return
        ultimaInteraccion = SystemClock.elapsedRealtime()
        segundoPlano = on; estado.segundoPlano = on
        val p = player
        if (on) {
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                runCatching { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 3) }
            if (p != null) ReproduccionService.pon(this, p)
        } else ReproduccionService.quita(this)
        estado.mensaje = if (on) "Al salir, seguirá sonando" else "Al salir, se para"
        principal.postDelayed({ if (estado.mensaje?.startsWith("Al salir") == true) estado.mensaje = null }, 2_500L)
    }

    /** Se ha cortado: se vuelve a conectar, esperando cada vez un poco más (1, 2, 4, 8… segundos). */
    private fun reintenta(manual: Boolean = false) {
        if (manual) reintentos = 0
        val p = player ?: return
        reintentos++
        val espera = if (manual) 0L else (1_000L shl (reintentos - 1).coerceAtMost(4))
        estado.aviso = null
        estado.mensaje = "Se ha cortado: volviendo a conectar…"
        estado.cargando = true
        principal.postDelayed({
            if (player !== p) return@postDelayed
            if (estado.directo) p.seekToDefaultPosition()
            p.prepare(); p.playWhenReady = true
        }, espera)
    }

    private fun refrescaGuia() {
        guiaEn = SystemClock.elapsedRealtime()
        val c = canalActual ?: return
        val l = encargo.lista
        ambito.launch {
            val prog = withContext(Dispatchers.IO) { runCatching { Tele.programas(l, c) }.getOrDefault(emptyList()) }
            if (canalActual?.clave != c.clave) return@launch
            val ahora = System.currentTimeMillis()
            estado.ahora = prog.firstOrNull { it.emitiendose(ahora) }; estado.despues = prog.firstOrNull { it.inicio >= (estado.ahora?.fin ?: ahora) }
        }
    }

    // ---------------------------------------------------------------- lo visto (videoclub)

    private fun videoActual(): String? = when (val e = encargo) { is Encargo.DePelicula -> DatosCuenta.videoPelicula(e.pelicula); is Encargo.DeEpisodio -> DatosCuenta.videoEpisodio(e.episodio); else -> null }

    private fun guarda(fin: Boolean = false) {
        val v = videoActual() ?: return
        guardadoEn = SystemClock.elapsedRealtime()
        val pos = if (fin) estado.dur else player?.currentPosition ?: estado.pos
        val dur = estado.dur
        if (pos <= 0 || dur <= 0) return
        Reanudar.pon(this, encargo.lista.id, v, pos, dur)
        val antes = MiCuenta.progreso(encargo.lista.id, v)
        val visto = (antes?.visto == true) || Visto.visto(pos, dur)
        val p = when (val e = encargo) {
            is Encargo.DePelicula -> Progreso(e.lista.id, v, e.pelicula.id, e.pelicula.extension, e.pelicula.nombre, pos = pos, dur = dur, visto = visto, ultima = System.currentTimeMillis())
            is Encargo.DeEpisodio -> Progreso(e.lista.id, v, e.episodio.id, e.episodio.extension, tituloEpisodio(e.episodio, e.serie.nombre), e.serie.id, e.serie.nombre,
                e.episodio.temporada, e.episodio.numero, pos, dur, visto, System.currentTimeMillis())
            else -> return
        }
        MiCuenta.ponProgreso(p)
    }

    private fun episodioSiguiente(): Episodio? = (encargo as? Encargo.DeEpisodio)?.let { e -> e.ficha?.siguiente(e.episodio) }

    private fun alTerminar() {
        val sig = episodioSiguiente()
        if (sig == null || !AjustesReproductor.siguiente(this)) { estado.visibles = true; return }
        estado.siguiente = "T${sig.temporada} · E${sig.numero}" + if (sig.titulo.isNotBlank()) " · ${tituloEpisodio(sig, (encargo as Encargo.DeEpisodio).serie.nombre)}" else ""
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

    private fun siguienteYa() {
        val e = encargo as? Encargo.DeEpisodio ?: return
        val sig = episodioSiguiente() ?: return
        guarda()
        encargo = Encargo.DeEpisodio(e.lista, e.serie, e.ficha, sig)
        abre()
    }

    // ---------------------------------------------------------------- canales

    /** Al canal de al lado (+1 el siguiente, −1 el anterior), en el grupo con el que se abrió. */
    private fun zapea(d: Int) {
        val e = encargo as? Encargo.Directo ?: return
        if (e.canales.size <= 1) return
        val i = (e.indice + d + e.canales.size) % e.canales.size
        encargo = Encargo.Directo(e.lista, e.canales, i, e.plan); probadas.clear()
        abre()
    }

    private fun vaACanal(i: Int) {
        val e = encargo as? Encargo.Directo ?: return
        estado.lista = null
        if (i == e.indice) return
        encargo = Encargo.Directo(e.lista, e.canales, i.coerceIn(0, e.canales.size - 1), e.plan); probadas.clear()
        abre()
    }

    private fun abreLista() {
        val e = encargo as? Encargo.Directo ?: return
        estado.lista = e.canales; estado.enLista = e.indice; estado.visibles = false
    }

    /** Ir a un canal por su número (el que tecleas con el mando). */
    private fun alNumero(n: Int) {
        val e = encargo as? Encargo.Directo ?: return
        val i = e.canales.indexOfFirst { it.numero == n }.takeIf { it >= 0 } ?: (n - 1).takeIf { it in e.canales.indices && e.canales.none { c -> c.numero > 0 } } ?: run { estado.mensaje = "No hay canal $n"; principal.postDelayed({ if (estado.mensaje == "No hay canal $n") estado.mensaje = null }, 2_000L); return }
        vaACanal(i)
    }

    private val tecleo = Runnable { val n = estado.tecleado.toIntOrNull(); estado.tecleado = ""; if (n != null) alNumero(n) }

    // ---------------------------------------------------------------- controles

    private fun tocado() { ultimaInteraccion = SystemClock.elapsedRealtime(); if (estado.lista != null) estado.lista = null else estado.visibles = !estado.visibles || estado.eleccion != null }
    private fun alternar() { ultimaInteraccion = SystemClock.elapsedRealtime(); player?.let { if (it.isPlaying) it.pause() else it.play() } }
    private fun salta(seg: Int) { ultimaInteraccion = SystemClock.elapsedRealtime(); player?.let { if (!estado.directo) it.seekTo((it.currentPosition + seg * 1000L).coerceIn(0L, it.duration.coerceAtLeast(0L))) } }

    private fun cambiaAspecto() {
        val a = AjustesReproductor.Aspecto.entries
        estado.aspecto = a[(a.indexOf(estado.aspecto) + 1) % a.size]
        AjustesReproductor.ponAspecto(this, estado.aspecto)
        estado.mensaje = "Imagen: ${estado.aspecto.nombre}"
        principal.postDelayed({ if (estado.mensaje?.startsWith("Imagen:") == true) estado.mensaje = null }, 2_000L)
    }

    private fun idioma(l: String?): String = when (l?.lowercase()?.take(2)) { null, "" -> ""; "es" -> "Español"; "en" -> "Inglés"; "fr" -> "Francés"; "it" -> "Italiano"; "de" -> "Alemán"; "pt" -> "Portugués"; "ca" -> "Catalán"; else -> l }

    private fun eligeAudio() {
        val p = player ?: return
        val ops = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { gr -> (0 until gr.length).map { i -> gr to i } }
        if (ops.size <= 1) { estado.mensaje = "Solo hay un audio"; principal.postDelayed({ if (estado.mensaje == "Solo hay un audio") estado.mensaje = null }, 2_000L); return }
        estado.eleccion = Triple("Audio", ops.map { (gr, i) -> val f = gr.getTrackFormat(i); listOfNotNull(f.label, idioma(f.language).ifBlank { null }, f.sampleMimeType?.substringAfter('/')?.uppercase(), if (f.channelCount > 0) "${f.channelCount} canales" else null).distinct().joinToString(" · ") + (if (!gr.isTrackSupported(i)) " (no se puede aquí)" else "") to gr.isTrackSelected(i) }) { k ->
            val (gr, i) = ops[k]
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(gr.mediaTrackGroup, i)).build()
            estado.eleccion = null
        }
    }

    private fun eligeSubs() {
        val p = player ?: return
        val ops = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { gr -> (0 until gr.length).map { i -> gr to i } }
        val ninguno = p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || ops.none { (gr, i) -> gr.isTrackSelected(i) }
        estado.eleccion = Triple("Subtítulos", listOf("Sin subtítulos" to ninguno) + ops.map { (gr, i) -> val f = gr.getTrackFormat(i); (f.label ?: idioma(f.language).ifBlank { "Pista ${i + 1}" }) to (!ninguno && gr.isTrackSelected(i)) }) { k ->
            p.trackSelectionParameters = if (k == 0) p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                else { val (gr, i) = ops[k - 1]; p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(gr.mediaTrackGroup, i)).build() }
            estado.eleccion = null
        }
    }

    private fun conOtro() {
        estado.confirmaOtro = false
        val pos = player?.currentPosition ?: 0L
        player?.pause()
        val r = Reproducir.conOtro(this, url, estado.titulo, pos, cabeceras)
        if (r != null) estado.mensaje = r else finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        ultimaInteraccion = SystemClock.elapsedRealtime()
        val directo = estado.directo
        val libre = !estado.visibles && estado.eleccion == null && estado.lista == null && !estado.confirmaOtro
        // Los números: el canal por su número (se espera un poco por si tecleas más cifras)
        if (directo && keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 && estado.lista == null) {
            estado.tecleado = (estado.tecleado + (keyCode - KeyEvent.KEYCODE_0)).takeLast(4)
            principal.removeCallbacks(tecleo); principal.postDelayed(tecleo, 1_800L); return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> { zapea(1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { zapea(-1); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { alternar(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); return true }
            KeyEvent.KEYCODE_GUIDE -> if (directo) { abreLista(); return true }
        }
        if (libre) when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (directo) { zapea(if (AjustesReproductor.invertirCanales(this)) -1 else 1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (directo) { zapea(if (AjustesReproductor.invertirCanales(this)) 1 else -1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> if (!directo) { salta(-AjustesReproductor.salto(this)); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> if (!directo) { salta(AjustesReproductor.salto(this)); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { if (directo) abreLista() else estado.visibles = true; return true }
        }
        if (libre && keyCode in setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO)) { estado.visibles = true; return true }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            when {
                estado.confirmaOtro -> { estado.confirmaOtro = false; return true }
                estado.eleccion != null -> { estado.eleccion = null; return true }
                estado.lista != null -> { estado.lista = null; return true }
                estado.visibles && estado.aviso == null && player?.isPlaying == true -> { estado.visibles = false; return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        // Vuelves de seguir sonando: el vídeo, otra vez en la pantalla (el mismo reproductor, sin cortar)
        if (enSegundoPlano) { enSegundoPlano = false; vista?.player = player }
        if (parado) { parado = false; player?.let { if (estado.directo) { it.seekToDefaultPosition(); it.prepare() }; it.playWhenReady = true } }
    }

    /** Sonando con Nut fuera (el vídeo no se pinta). */
    private var enSegundoPlano = false

    override fun onStop() {
        super.onStop()
        if (!estado.directo) guarda()
        val p = player
        // «Seguir sonando al salir»: el sonido sigue (el servicio con su notificación y los controles de la pantalla de bloqueo) y el vídeo se deja de pintar
        if (segundoPlano && p != null && (p.isPlaying || p.playWhenReady) && !isFinishing) {
            ReproduccionService.pon(this, p)
            vista?.player = null
            enSegundoPlano = true
            return
        }
        // Un canal en directo no se queda bajando datos en segundo plano (ni con la tele apagada)
        p?.let { if (estado.directo) it.stop() else it.pause() }
        parado = true
    }

    override fun onDestroy() {
        principal.removeCallbacksAndMessages(null)
        ambito.cancel()
        ReproduccionService.quita(this); ReproduccionService.alParar = null
        player?.release(); player = null
        Actualizador.ocupada = (Actualizador.ocupada - 1).coerceAtLeast(0)
        super.onDestroy()
    }
}

/** «Juego de tronos - S01E02 - El camino real» → «El camino real» (muchos servidores repiten la serie y el número en el título). */
fun tituloEpisodio(e: Episodio, serie: String): String {
    val t = e.titulo.trim()
    val sin = t.replace(Regex("^" + Regex.escape(serie) + "\\s*[-–:|]\\s*", RegexOption.IGNORE_CASE), "").replace(Regex("^S\\d{1,2}\\s*E\\d{1,3}\\s*[-–:|]?\\s*", RegexOption.IGNORE_CASE), "").trim()
    return sin.ifBlank { "Episodio ${e.numero}" }
}

/** Dónde lo dejaste en ESTE dispositivo (también sin cuenta). Es de cada dispositivo; lo de la cuenta, en [MiCuenta]. */
object Reanudar {
    private fun p(c: Context) = c.getSharedPreferences("cobre_reanudar", Context.MODE_PRIVATE)
    fun pon(c: Context, lista: String, video: String, pos: Long, dur: Long, ahora: Long = System.currentTimeMillis()) = p(c).edit().putString("$lista~$video", "$pos;$dur;$ahora").apply()
    fun de(c: Context, lista: String, video: String): Triple<Long, Long, Long>? = p(c).getString("$lista~$video", null)?.split(';')?.let { s ->
        if (s.size < 3) null else Triple(s[0].toLongOrNull() ?: 0L, s[1].toLongOrNull() ?: 0L, s[2].toLongOrNull() ?: 0L)
    }
}

/** Los controles: arriba el canal (o el título), en medio reproducir, abajo la guía o la barra y lo de audio, subtítulos e imagen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlesReproductor(
    e: EstadoReproductor, alTocar: () -> Unit, alPlay: () -> Unit, alSaltar: (Int) -> Unit, alIr: (Long) -> Unit, alVolver: () -> Unit,
    alAudio: () -> Unit, alSubs: () -> Unit, alAspecto: () -> Unit, alOtro: () -> Unit, alOtroSi: () -> Unit, alCanal: (Int) -> Unit, alLista: () -> Unit,
    alElegirCanal: (Int) -> Unit, alSiguiente: () -> Unit, alCancelarSiguiente: () -> Unit, alReintentar: () -> Unit,
    alCalidad: () -> Unit = {}, alSegundoPlano: () -> Unit = {},
) {
    val blanco = Color(0xFFF7F2E8)
    // (tocar la pantalla enseña o esconde los controles; sin «clickable», que se quedaría el foco y el OK del mando ya no abriría los canales)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { alTocar() } }) {
        if (e.cargando && e.aviso == null) Text("Cargando…", color = blanco, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
        if (e.visibles || e.aviso != null) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent, Color.Transparent, Color(0xDD000000)))))
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 32.dp, vertical = 18.dp)) {
                Text("← VOLVER", color = blanco, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp, modifier = Modifier.tocable { alVolver() }.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (e.directo) {
                        Text("${e.numero}", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.padding(end = 14.dp))
                        Logo(e.logo, e.titulo, Modifier.padding(end = 14.dp), 48.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(e.titulo, color = blanco, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (e.subtitulo.isNotBlank()) Text(e.subtitulo.uppercase(), color = blanco.copy(alpha = 0.8f), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1)
                    }
                }
            }
            if (e.aviso == null && !e.cargando) Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                if (e.directo) BotonReproductor(Icons.Rounded.KeyboardArrowUp, "Canal siguiente", blanco, 48.dp) { alCanal(1) }
                else BotonReproductor(Icons.Rounded.Replay10, "Atrás", blanco, 52.dp) { alSaltar(-10) }
                BotonReproductor(if (e.reproduciendo) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (e.reproduciendo) "Pausa" else "Reproducir", blanco, 76.dp, Modifier.focoInicial(e.titulo)) { alPlay() }
                if (e.directo) BotonReproductor(Icons.Rounded.KeyboardArrowDown, "Canal anterior", blanco, 48.dp) { alCanal(-1) }
                else BotonReproductor(Icons.Rounded.Forward10, "Adelante", blanco, 52.dp) { alSaltar(10) }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 14.dp)) {
                if (e.directo) {
                    val ahora = System.currentTimeMillis()
                    e.ahora?.let { p ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(end = 10.dp, bottom = 3.dp))
                            Text(p.titulo, color = blanco, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(franja(p), color = blanco.copy(alpha = 0.8f), fontFamily = Body, fontSize = 13.sp)
                        }
                        Barra(p.avance(ahora), Modifier.padding(vertical = 6.dp), fondo = blanco.copy(alpha = 0.25f))
                        if (p.descripcion.isNotBlank()) Text(p.descripcion, color = blanco.copy(alpha = 0.8f), fontFamily = Body, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } ?: Text("Sin guía para este canal", color = blanco.copy(alpha = 0.7f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                    e.despues?.let { p -> Text("DESPUÉS · ${hora(p.inicio)} · ${p.titulo}", color = blanco.copy(alpha = 0.75f), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
                } else if (e.dur > 0) {
                    Slider(e.pos.toFloat().coerceIn(0f, e.dur.toFloat()), { alIr(it.toLong()) }, valueRange = 0f..e.dur.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Acento, activeTrackColor = Acento, inactiveTrackColor = blanco.copy(alpha = 0.3f)))
                    Row(Modifier.fillMaxWidth()) {
                        Text(tiempo(e.pos), color = blanco, fontFamily = Body, fontSize = 12.sp); Spacer(Modifier.weight(1f))
                        Text("−" + tiempo((e.dur - e.pos).coerceAtLeast(0L)), color = blanco, fontFamily = Body, fontSize = 12.sp)
                    }
                }
                FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (e.directo) EnlaceClaro("Canales", blanco, alLista)
                    e.calidad?.let { EnlaceClaro("Calidad: $it", blanco, alCalidad) }
                    e.segundoPlano?.let { on -> EnlaceClaro(if (on) "Sonar al salir: sí" else "Sonar al salir: no", if (on) Acento else blanco, alSegundoPlano) }
                    EnlaceClaro("Audio", blanco, alAudio); EnlaceClaro("Subtítulos", blanco, alSubs); EnlaceClaro("Imagen: ${e.aspecto.nombre}", blanco, alAspecto)
                    EnlaceClaro("Abrir con otro reproductor", blanco, alOtro)
                }
            }
        }
        if (e.tecleado.isNotBlank()) Text(e.tecleado, color = blanco, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 54.sp,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(28.dp).background(Color(0x99000000)).padding(horizontal = 18.dp, vertical = 4.dp))
        e.mensaje?.let { m -> Text(m, color = blanco, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).offset(y = 64.dp).widthIn(max = 560.dp).padding(horizontal = 24.dp).background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 8.dp)) }
        e.lista?.let { canales -> ListaRapida(canales, e.enLista, e.guiaDe, alElegirCanal) }
        e.aviso?.let { a ->
            Column(Modifier.align(Alignment.Center).widthIn(max = 540.dp).padding(24.dp).background(Paper).padding(22.dp)) {
                Antetitulo("No se puede", Modifier.padding(bottom = 8.dp))
                Text(a, color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(10.dp))
                MagLink("Volver a intentarlo", accent = true) { alReintentar() }
                if (e.directo) MagLink("Otro canal") { alLista() }
                MagLink("Abrir con otro reproductor") { alOtro() }
                MagLink("Volver") { alVolver() }
            }
        }
        if (e.confirmaOtro) Column(Modifier.align(Alignment.Center).widthIn(max = 560.dp).padding(24.dp).background(Paper).padding(22.dp)) {
            Antetitulo("Abrir con otro reproductor", Modifier.padding(bottom = 8.dp))
            Text("Se le pasa a esa app la dirección de lo que ves, que lleva tu usuario y tu contraseña de la lista. Hazlo solo con un reproductor de confianza (VLC, Just Player, mpv…). Lo que veas allí no cuenta en Nut.",
                color = Charcoal, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(10.dp))
            MagLink("Sí, abrirlo allí", accent = true) { alOtroSi() }
            MagLink("Mejor no") { e.confirmaOtro = false }
        }
        e.siguiente?.let { s ->
            Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(28.dp).background(Paper).padding(18.dp)) {
                Antetitulo("Siguiente", Modifier.padding(bottom = 4.dp))
                Text(s, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    EnlaceRevista(if (e.cuentaAtras >= 0) "Ver ya (${e.cuentaAtras})" else "Ver ya", Modifier.focoInicial(s), acento = true) { alSiguiente() }
                    EnlaceRevista("Cancelar") { alCancelarSiguiente() }
                }
            }
        }
        e.eleccion?.let { (titulo, ops, elegir) ->
            Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 420.dp).fillMaxWidth(0.9f).background(Paper).statusBarsPadding().padding(20.dp).verticalScroll(rememberScrollState())) {
                Antetitulo(titulo, Modifier.padding(bottom = 6.dp))
                ops.forEachIndexed { i, (t, sel) ->
                    Text((if (sel) "✓ " else "   ") + t, color = if (sel) Acento else Charcoal, fontFamily = Body, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().let { if (i == 0) it.focoInicial(titulo) else it }.tocable { elegir(i) }.padding(vertical = 10.dp))
                    Filete(1.dp, Line)
                }
                EnlaceRevista("Cerrar") { e.eleccion = null }
            }
        }
    }
}

/** La lista de canales encima del vídeo (OK en el mando): el canal, su número y lo que echan, para cambiar sin salir. */
@Composable
private fun ListaRapida(canales: List<Canal>, actual: Int, guia: (Canal) -> Pair<Programa?, Programa?>, alElegir: (Int) -> Unit) {
    val estado = rememberLazyListState((actual - 3).coerceAtLeast(0))
    Column(Modifier.fillMaxHeight().widthIn(max = 460.dp).fillMaxWidth(0.92f).background(Paper.copy(alpha = 0.96f)).statusBarsPadding().padding(vertical = 16.dp)) {
        Antetitulo("Canales", Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        LazyColumn(state = estado, modifier = Modifier.fillMaxSize().atrapaFoco()) {
            itemsIndexed(canales, key = { _, c -> c.clave }) { i, c ->
                val (ahora, _) = remember(c.clave) { guia(c) }
                Row(Modifier.fillMaxWidth().let { if (i == actual) it.focoInicial(actual) else it }.tocable { alElegir(i) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${if (c.numero > 0) c.numero else i + 1}", color = if (i == actual) Acento else Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 34.dp).padding(end = 8.dp))
                    Logo(c.logo, c.nombre, Modifier.padding(end = 12.dp), 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(c.nombre, color = if (i == actual) Acento else Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (ahora != null) Text(ahora.titulo, color = Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Un botón del reproductor: el icono a buen tamaño (se ve desde el sofá), con el marco de foco del mando. */
@Composable
private fun BotonReproductor(icono: androidx.compose.ui.graphics.vector.ImageVector, descripcion: String, color: Color, tam: Dp, modifier: Modifier = Modifier, alHacer: () -> Unit) {
    Box(modifier.size(tam + 20.dp).tocable(fuera = 2.dp) { alHacer() }.semantics { contentDescription = descripcion }, contentAlignment = Alignment.Center) {
        Icon(icono, null, tint = color, modifier = Modifier.size(tam))
    }
}

@Composable
private fun EnlaceClaro(t: String, color: Color, alHacer: () -> Unit) =
    Text(t.uppercase(), color = color, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.3.sp, maxLines = 1, modifier = Modifier.tocable { alHacer() }.padding(vertical = 10.dp))
