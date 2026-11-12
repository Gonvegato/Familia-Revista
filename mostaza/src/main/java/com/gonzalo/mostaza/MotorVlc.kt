package com.gonzalo.mostaza

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * El motor de VLC (libVLC) dentro de Hathor, para lo que los decodificadores de Android no saben leer bien: el H.264 de 10 bits
 * («Hi10P»), que unos aparatos rechazan y otros enseñan roto en bloques. Decodifica por programa con sus propios decodificadores
 * (FFmpeg), como VLC con la aceleración por hardware desactivada. El reproductor de Hathor lo usa en lugar de Media3 cuando hace falta;
 * los controles, el tiempo visto, la reanudación y los torrents son los mismos.
 */
class MotorVlc(c: Context, private val oyente: Oyente) {
    interface Oyente {
        /** Empieza a verse (o vuelve tras cargar): [dur] la duración en ms (0 si no se sabe). */
        fun listo(dur: Long)
        fun reproduciendo(si: Boolean)
        fun cargando(si: Boolean)
        fun terminado()
        fun error()
    }

    private val principal = Handler(Looper.getMainLooper())
    private val libvlc = LibVLC(c.applicationContext, arrayListOf(
        "--no-drop-late-frames", "--no-skip-frames",
        // (un torrent que empieza puede tardar en tener el trozo que se pide: el servidor local espera; VLC tiene que esperarle)
        "--http-reconnect", "--network-caching=3000",
        "--audio-time-stretch", "--quiet",
    ))
    private val mp = MediaPlayer(libvlc)
    private var vista: VLCVideoLayout? = null
    private var visto = false
    @Volatile private var liberado = false

    init {
        mp.setEventListener { e ->
            principal.post {
                if (liberado) return@post
                when (e.type) {
                    MediaPlayer.Event.Playing -> { if (!visto) { visto = true; oyente.listo(mp.length.coerceAtLeast(0L)) }; oyente.cargando(false); oyente.reproduciendo(true) }
                    MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> oyente.reproduciendo(false)
                    MediaPlayer.Event.Buffering -> oyente.cargando(e.buffering < 100f)
                    MediaPlayer.Event.LengthChanged -> if (visto) oyente.listo(mp.length.coerceAtLeast(0L))
                    MediaPlayer.Event.EndReached -> oyente.terminado()
                    MediaPlayer.Event.EncounteredError -> oyente.error()
                }
            }
        }
    }

    /** Pone el vídeo en [layout] y empieza en [desde] ms. [subs]: subtítulos de fuera (direcciones). */
    fun empieza(url: String, layout: VLCVideoLayout, desde: Long, subs: List<String>, agente: String) {
        vista = layout
        mp.attachViews(layout, null, true, false)
        mp.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
        val m = Media(libvlc, Uri.parse(url))
        // Por programa (con los decodificadores de VLC): es justo para lo que el del aparato rompe
        m.setHWDecoderEnabled(false, false)
        m.addOption(":http-user-agent=$agente")
        if (desde > 0) m.addOption(":start-time=${desde / 1000.0}")
        subs.take(20).forEach { s -> runCatching { m.addSlave(org.videolan.libvlc.interfaces.IMedia.Slave(org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle, 4, s)) } }
        mp.media = m
        m.release()
        mp.play()
    }

    val posicion: Long get() = if (liberado) 0L else mp.time.coerceAtLeast(0L)
    val duracion: Long get() = if (liberado) 0L else mp.length.coerceAtLeast(0L)
    val reproduce: Boolean get() = !liberado && mp.isPlaying

    fun play() { if (!liberado) mp.play() }
    fun pausa() { if (!liberado && mp.isPlaying) mp.pause() }
    fun alterna() { if (reproduce) pausa() else play() }
    fun ve(ms: Long) { if (!liberado) mp.time = ms.coerceAtLeast(0L) }

    /** Las pistas de audio (id, nombre) y la de ahora. */
    fun audios(): List<Pair<Int, String>> = if (liberado) emptyList() else mp.audioTracks.orEmpty().filter { it.id >= 0 }.map { it.id to it.name.orEmpty() }
    val audio: Int get() = if (liberado) -1 else mp.audioTrack
    fun ponAudio(id: Int) { if (!liberado) mp.setAudioTrack(id) }

    /** Los subtítulos (id, nombre; sin el «desactivado») y el de ahora (−1: ninguno). */
    fun subtitulos(): List<Pair<Int, String>> = if (liberado) emptyList() else mp.spuTracks.orEmpty().filter { it.id >= 0 }.map { it.id to it.name.orEmpty() }
    val subtitulo: Int get() = if (liberado) -1 else mp.spuTrack
    fun ponSubtitulo(id: Int) { if (!liberado) mp.setSpuTrack(id) }

    fun libera() {
        if (liberado) return
        liberado = true
        runCatching { mp.setEventListener(null) }
        runCatching { mp.stop() }
        runCatching { mp.detachViews() }
        runCatching { mp.release() }
        runCatching { libvlc.release() }
        vista = null
    }
}
