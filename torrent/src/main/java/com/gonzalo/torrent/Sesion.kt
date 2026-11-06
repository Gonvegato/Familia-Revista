package com.gonzalo.torrent

import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TcpEndpoint
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.Vectors
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.swig.address
import org.libtorrent4j.swig.alert
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.ip_filter
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.session_handle
import org.libtorrent4j.swig.settings_pack
import java.io.File

/**
 * Cómo arranca la sesión de libtorrent. En la app, lo de siempre; en las pruebas, solo en este ordenador: escucha en 127.0.0.1, sin
 * DHT ni descubrimiento local, con [pares] fijos (un sembrador en otra sesión) y con los límites de subida también en la red local
 * ([limitesEnLocal]; libtorrent no los aplica ahí, y la descarga sería instantánea).
 */
internal data class ConfigSesion(
    val interfaces: String = "0.0.0.0:0,[::]:0",
    val dht: Boolean = true,
    val lsd: Boolean = true,
    val pares: List<Pair<String, Int>> = emptyList(),
    val limitesEnLocal: Boolean = false,
)

/**
 * La sesión de libtorrent (una para todos los torrents). Guarda los datos con la E/S «posix» de libtorrent (en 32 bits es la que
 * usa siempre), y no con la de mmap que usa por defecto en 64 bits, por dos cosas:
 * - Escribe cada bloque en el archivo antes de comprobar la pieza, así que una pieza que libtorrent da por descargada ya está en el
 *   archivo y el servidor local puede leerla de ahí. Con la de mmap, la pieza se da por buena aunque parte de sus bloques aún vaya
 *   camino del disco.
 * - La de mmap, la primera vez que lee o escribe, pone su propio manejador de SIGSEGV y SIGBUS (try_signal), que no pasa la señal
 *   al de la máquina virtual: en una JVM, la primera comprobación de null que use esa señal tumba el proceso (pasaba en las pruebas).
 */
internal class Sesion(private val config: ConfigSesion, ajustes: AjustesTorrent, avisos: (Alert<*>) -> Unit) : AutoCloseable {
    private val sm = SessionManager(false)
    private val oyente = object : AlertListener {
        override fun types(): IntArray? = null
        override fun alert(a: Alert<*>) = avisos(a)
    }

    init {
        val sp = SettingsPack()
            .listenInterfaces(config.interfaces)
            .connectionsLimit(100)
            .alertQueueSize(5000)
            .stopTrackerTimeout(1)
            .uploadRateLimit(subida(ajustes))
        // Volver a llamar a un par al que ya se llamó: 10 s y no el minuto de libtorrent (tras un corte)
        sp.setInteger(settings_pack.int_types.min_reconnect_time.swigValue(), 10)
        // Con el archivo ya entero, libtorrent cuelga a las semillas (y les dice que ya no quiere nada, y ellas le cuelgan): si luego
        // se elige otro archivo del mismo torrent (el episodio siguiente), hay que esperar a volver a conectar. Aquí no: se quedan.
        sp.setBoolean(settings_pack.bool_types.close_redundant_connections.swigValue(), false)
        sp.setEnableDht(config.dht)
        sp.setEnableLsd(config.lsd)
        // Sin abrir puertos en el router desde el móvil: para ver algo basta con conectarse a los demás
        sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false)
        sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false)
        if (!config.dht) sp.setDhtBootstrapNodes("")
        val params = SessionParams(sp)
        params.setPosixDiskIO()
        sm.addListener(oyente)
        sm.start(params)
        if (!sm.isRunning) throw ErrorTorrent(ErrorTorrent.Motivo.MOTOR, "El motor de torrents no ha podido arrancar.")
        // Solo los avisos que hacen falta (libtorrent4j los pide todos, y los de cada bloque son miles por segundo)
        val mascara = alert.error_notification.or_(alert.status_notification).or_(alert.storage_notification).or_(alert.piece_progress_notification)
        sm.applySettings(SettingsPack().setInteger(settings_pack.int_types.alert_mask.swigValue(), mascara.to_int()))
        if (config.limitesEnLocal) {
            val ec = error_code()
            val todas = ip_filter()
            todas.add_rule(address.from_string("0.0.0.0", ec), address.from_string("255.255.255.255", ec), 1L shl CLASE_GLOBAL)
            sm.swig().set_peer_class_filter(todas)
        }
    }

    /** El puerto donde escucha a los demás pares (para las pruebas). */
    val puerto: Int get() = sm.swig().listen_port()

    /** Añade el torrent de [magnet] en [carpeta]. [dht]: si se busca en la DHT para este torrent. */
    fun anade(magnet: String, carpeta: File, dht: Boolean): TorrentHandle {
        val p = try { AddTorrentParams.parseMagnetUri(magnet) } catch (e: IllegalArgumentException) {
            throw ErrorTorrent(ErrorTorrent.Motivo.HASH_NO_VALIDO, "Este torrent no se puede abrir (${e.message}).", e)
        }
        return anade(p, carpeta, dht)
    }

    /**
     * Vuelve a poner un torrent que ya estuvo aquí, con lo que guardó libtorrent al pararlo ([datos]: sus metadatos y qué piezas
     * tenía). Así no hay que esperar a los metadatos ni volver a comprobar lo descargado. Null si los datos no sirven.
     */
    fun reanuda(datos: ByteArray, carpeta: File, fuentes: Fuentes): TorrentHandle? {
        val ec = error_code()
        val atp = libtorrent.read_resume_data_ex(Vectors.bytes2byte_vector(datos), ec)
        if (ec.value() != 0) return null
        val p = AddTorrentParams(atp)
        if (p.torrentInfo?.isValid != true) return null
        p.trackers = (p.trackers + fuentes.trackers).distinct()
        return runCatching { anade(p, carpeta, fuentes.dht) }.getOrNull()
    }

    private fun anade(p: AddTorrentParams, carpeta: File, dht: Boolean): TorrentHandle {
        p.savePath = carpeta.absolutePath
        // Sin DEFAULT_DONT_DOWNLOAD: con todo a 0 al llegar los metadatos, el torrent está «terminado» y libtorrent cuelga a las
        // semillas (y tarda en volver a llamarlas). El archivo se elige en el magnet (so=) o en cuanto llegan los metadatos.
        var f = p.flags.and_(TorrentFlags.AUTO_MANAGED.inv()).and_(TorrentFlags.PAUSED.inv()).and_(TorrentFlags.UPLOAD_MODE.inv())
        f = if (!dht || !config.dht) f.or_(TorrentFlags.DISABLE_DHT) else f.and_(TorrentFlags.DISABLE_DHT.inv())
        p.flags = f
        if (config.pares.isNotEmpty()) p.peers(config.pares.map { TcpEndpoint(it.first, it.second) })
        val ec = error_code()
        val th = sm.swig().add_torrent(p.swig(), ec)
        if (ec.value() != 0 || !th.is_valid()) throw ErrorTorrent(ErrorTorrent.Motivo.MOTOR, "No se ha podido empezar el torrent: ${ec.message()}")
        return TorrentHandle(th)
    }


    /** Quita el torrent; con [borrar], libtorrent borra sus archivos (y avisa al acabar, con TorrentDeletedAlert). */
    fun quita(th: TorrentHandle, borrar: Boolean) {
        if (!th.isValid) return
        if (borrar) sm.remove(th, session_handle.delete_files) else sm.remove(th)
    }

    fun conecta(th: TorrentHandle) {
        for ((ip, puerto) in config.pares) runCatching { th.swig().connect_peer(TcpEndpoint(ip, puerto).swig()) }
    }

    fun aplica(ajustes: AjustesTorrent) {
        sm.applySettings(SettingsPack().uploadRateLimit(subida(ajustes)))
    }

    fun pausa() = sm.pause()
    fun sigue() = sm.resume()
    val enPausa: Boolean get() = sm.isPaused

    /** Cierra la sesión (bloquea un par de segundos: libtorrent se despide de los trackers). */
    override fun close() {
        runCatching { sm.removeListener(oyente) }
        runCatching { sm.stop() }
    }

    companion object {
        /** Lo que guarda libtorrent de un torrent para [reanuda] (con sus metadatos). */
        fun datosParaReanudar(p: AddTorrentParams): ByteArray = AddTorrentParams.writeResumeDataBuf(p)

        /** La clase de pares «global» de libtorrent, la que lleva los límites de velocidad (session_handle::global_peer_class_id). */
        private const val CLASE_GLOBAL = 0

        /** Lo que se sube como mucho, en bytes/s: nunca 0, que para libtorrent es sin límite. */
        fun subida(a: AjustesTorrent): Int = maxOf(1, a.subidaMaxKBs) * 1024
    }
}
