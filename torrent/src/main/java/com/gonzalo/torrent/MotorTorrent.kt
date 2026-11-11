package com.gonzalo.torrent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.FileErrorAlert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.SaveResumeDataFailedAlert
import org.libtorrent4j.alerts.TorrentAlert
import org.libtorrent4j.alerts.TorrentDeleteFailedAlert
import org.libtorrent4j.alerts.TorrentDeletedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import java.io.File
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * El motor de torrents: convierte un stream de Stremio con `infoHash` (y su `fileIdx` y sus `sources`) en una dirección local que
 * el reproductor abre y en la que puede saltar mientras el torrent se descarga. Lo que hace Stremio con su servidor de streaming
 * (que no es libre), con libtorrent.
 *
 * Una sola sesión de libtorrent y un solo servidor local para todos los torrents, que arrancan con el primer [prepara] y se apagan
 * solos un rato después del último [para] (o con [paraTodo]). No guarda ningún ajuste: llegan siempre de la app en [AjustesTorrent]
 * (que los tiene en la cuenta), también en marcha con [ajusta].
 *
 * El reproductor: la dirección es http://127.0.0.1 sin cifrar (la app tiene que permitirlo, ver res/xml/torrent_red_local.xml), y
 * mientras faltan datos la respuesta espera, así que su tiempo de lectura debe ser largo ([ESPERA_LECTURA_MS]; el de Media3 por
 * defecto, 8 s, se queda corto con un torrent que arranca).
 */
class MotorTorrent internal constructor(
    private val carpetaApp: File,
    ajustes: AjustesTorrent,
    private val red: Red,
    private val config: ConfigSesion,
    private val tiempos: Tiempos,
    private val ventanas: Ventanas = Ventanas(),
    /** El sitio del disco (las pruebas fingen uno lleno). */
    private val disco: Disco = DiscoSimple,
) {
    constructor(context: Context, ajustes: AjustesTorrent = AjustesTorrent()) :
        this(File(context.applicationContext.cacheDir, "torrent"), ajustes, RedAndroid(context), ConfigSesion(), Tiempos(), Ventanas(), DiscoAndroid(context))

    @Volatile private var ajustes: AjustesTorrent = ajustes
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Todo lo que toca la sesión, el servidor o la carpeta va por aquí, de uno en uno y en orden. */
    private val serie = Dispatchers.IO.limitedParallelism(1)
    private val activos = ConcurrentHashMap<String, TorrentActivo>()
    private val compartidos = ConcurrentHashMap<String, TorrentActivo>()
    private val borrando = ConcurrentHashMap<String, Borrado>()
    private val reanudar = ConcurrentHashMap<String, CompletableFuture<ByteArray?>>()
    /** La parte secreta de cada dirección: otra app del móvil puede ver el puerto, pero sin esto no lee nada. */
    private val clave = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    // Solo desde la serie
    private var sesion: Sesion? = null
    private var servidor: ServidorLocal? = null
    private var vigilancia: AutoCloseable? = null
    private var apagado: Job? = null
    @Volatile private var sinWifi = false

    private class Borrado(val t: TorrentActivo) { val hecho = CompletableDeferred<Unit>() }

    init {
        // Lo que quedó de otras veces y no sirve (sin datos para reanudar: la app se cerró de golpe), y el tope de la caché
        ambito.launch(serie) { runCatching { limpiaRestos() } }
    }

    /**
     * Empieza (o reutiliza) el torrent, espera a sus metadatos, elige el archivo ([fileIdx], el que se llama [nombre] o el vídeo
     * más grande) y lo sirve en local. Devuelve lo que hay que reproducir. Si se cancela, el torrent se quita (si nadie más lo usa).
     * [fuentes] son los `sources` del stream (`tracker:…`, `dht:…`); [nombre], su `behaviorHints.filename` si lo trae (si no, el
     * título: solo sirve para el nombre del magnet).
     *
     * @throws ErrorTorrent con un mensaje para enseñar (sin wifi, nadie lo comparte, sin metadatos, no está el archivo, sin sitio…)
     */
    suspend fun prepara(infoHash: String, fileIdx: Int?, fuentes: List<String>, nombre: String?): Flujo = withContext(Dispatchers.IO) {
        val hash = InfoHash.normaliza(infoHash)
            ?: throw ErrorTorrent(ErrorTorrent.Motivo.HASH_NO_VALIDO, "«$infoHash» no es el infoHash de un torrent.")
        compruebaRed()
        val fu = LeeFuentes.lee(fuentes)
        // Si se está borrando el mismo torrent (un para justo antes), se espera: su carpeta es la misma
        var t: TorrentActivo? = null
        while (t == null) {
            borrando[hash]?.let { withTimeoutOrNull(tiempos.borradoMs) { it.hecho.await() } }
            t = withContext(serie) { if (borrando.containsKey(hash)) null else abre(hash, fu, fileIdx, nombre) }
        }
        try {
            esperaMetadatos(t)
            withContext(serie) { empieza(t, fileIdx, nombre) }
        } finally {
            withContext(NonCancellable + serie) {
                t.interesados--
                if (!t.entregado && t.interesados == 0 && activos[hash] === t) {
                    activos.remove(hash)
                    t.detiene()
                    descarta(t)
                }
            }
        }
    }

    /**
     * Cómo va el torrent: pares, velocidades, cuánto hay del archivo y cuánto hay bajado por delante de donde lee el reproductor.
     * Null si no está en marcha. (Pregunta a libtorrent: mejor una vez por segundo y fuera del hilo principal.)
     */
    fun estado(infoHash: String): EstadoTorrent? {
        val hash = InfoHash.normaliza(infoHash) ?: return null
        return activos[hash]?.estado(sinWifi)
    }

    /** Lo para. Se deja de compartir y se borra, salvo con [AjustesTorrent.seguirCompartiendoAlParar]. */
    fun para(infoHash: String) {
        val hash = InfoHash.normaliza(infoHash) ?: return
        val t = activos.remove(hash) ?: return
        t.detiene()
        ambito.launch(serie) { suelta(t) }
    }

    /**
     * Lo para y borra lo que hay bajado de él aquí (también lo apuntado para reanudarlo): la próxima vez se baja desde cero. Para cuando
     * lo guardado está mal (se ve roto en cualquier reproductor) aunque libtorrent lo dé por bueno.
     */
    fun olvida(infoHash: String) {
        val hash = InfoHash.normaliza(infoHash) ?: return
        val t = activos.remove(hash) ?: compartidos.remove(hash)
        t?.detiene()
        ambito.launch(serie) {
            if (t != null) runCatching { suelta(t) }
            Cache.borra(File(carpeta(), hash))
        }
    }

    /** Lo para todo y apaga la sesión y el servidor (sin esperar: se hace por detrás). */
    fun paraTodo() {
        val todos = activos.values.toList() + compartidos.values.toList()
        activos.clear()
        todos.forEach { it.detiene() }
        ambito.launch(serie) { apaga(todos) }
    }

    /**
     * Ajustes nuevos, en marcha: el límite de subida y «Solo con wifi» se aplican al momento; sin «seguir compartiendo», se deja
     * de compartir lo parado; con menos caché, se borra lo viejo que sobra. Una [AjustesTorrent.carpeta] nueva vale para los
     * torrents que empiecen a partir de ahora.
     */
    fun ajusta(nuevos: AjustesTorrent) {
        val antes = ajustes
        ajustes = nuevos
        ambito.launch(serie) {
            runCatching { sesion?.aplica(nuevos) }
            revisaRed()
            if (!nuevos.seguirCompartiendoAlParar) compartidos.values.toList().forEach { descarta(it) }
            if (nuevos.cacheMaxMB < antes.cacheMaxMB) runCatching { Cache.libera(carpeta(), nuevos.cacheMaxMB * MB, enUso()) }
        }
    }

    // --- Por dentro ---

    private fun carpeta(): File = (ajustes.carpeta ?: carpetaApp).also { it.mkdirs() }

    private fun enUso(): Set<String> = activos.keys + compartidos.keys + borrando.keys

    private fun compruebaRed() {
        when (red.actual()) {
            TipoRed.NINGUNA -> throw ErrorTorrent(ErrorTorrent.Motivo.SIN_RED, "No hay conexión a internet.")
            TipoRed.MOVIL -> if (ajustes.soloWifi) throw errorSinWifi()
            TipoRed.WIFI -> {}
        }
    }

    private fun errorSinWifi() = ErrorTorrent(
        ErrorTorrent.Motivo.SIN_WIFI,
        "Solo con wifi: ahora vas con datos móviles. Conéctate a una wifi o quita «Solo con wifi» en los ajustes.",
    )

    /** En la serie: la sesión (si hace falta) y el torrent, nuevo, reanudado o el que ya estaba. */
    private fun abre(hash: String, fu: Fuentes, fileIdx: Int?, nombre: String?): TorrentActivo {
        apagado?.cancel()
        apagado = null
        val s = sesion ?: arranca()
        activos[hash]?.let { it.interesados++; return it }
        compartidos.remove(hash)?.let {
            activos[hash] = it
            it.vuelve()
            File(it.dir, REANUDAR).delete()
            it.interesados++
            return it
        }
        val dir = File(carpeta(), hash)
        val reanudable = File(dir, REANUDAR).takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }
        val h = reanudable?.let { s.reanuda(it, dir, fu) } ?: run {
            Cache.borra(dir)
            dir.mkdirs()
            s.anade(LeeFuentes.magnet(hash, fu, nombre, fileIdx), dir, fu.dht)
        }
        File(dir, REANUDAR).delete()   // a partir de aquí cambia: se vuelve a guardar al parar
        Cache.toca(dir)
        val t = TorrentActivo(hash, h, dir, tiempos, ventanas)
        t.pedido = fileIdx to nombre
        activos[hash] = t
        t.interesados++
        if (sinWifi) t.ponError(errorSinWifi())
        return t
    }

    private fun arranca(): Sesion {
        val s = try {
            Sesion(config, ajustes, ::alAviso)
        } catch (e: ErrorTorrent) {
            throw e
        } catch (e: Throwable) {
            // Sin la biblioteca nativa para este móvil (UnsatisfiedLinkError) o algo peor
            throw ErrorTorrent(ErrorTorrent.Motivo.MOTOR, "El motor de torrents no ha podido arrancar en este móvil (${e.message}).", e)
        }
        sesion = s
        servidor = try { ServidorLocal(::busca) } catch (e: Exception) {
            s.close()
            sesion = null
            throw ErrorTorrent(ErrorTorrent.Motivo.MOTOR, "No se ha podido abrir el servidor local del reproductor (${e.message}).", e)
        }
        vigilancia = red.vigila { ambito.launch(serie) { revisaRed() } }
        revisaRed()
        return s
    }

    private suspend fun esperaMetadatos(t: TorrentActivo) {
        val inicio = System.currentTimeMillis()
        var conPares = false
        while (true) {
            if (t.parado) throw ErrorTorrent(ErrorTorrent.Motivo.PARADO, "Se ha parado el torrent.")
            t.error?.let { throw it }
            val st = t.estadoLibtorrent()
                ?: throw ErrorTorrent(ErrorTorrent.Motivo.PARADO, "Se ha parado el torrent.")
            if (st.hasMetadata()) return
            if (st.numPeers() > 0 || st.listPeers() > 0) conPares = true
            val pasado = System.currentTimeMillis() - inicio
            if (!conPares && pasado >= tiempos.sinParesMs || pasado >= tiempos.metadatosMs) {
                throw if (conPares) ErrorTorrent(
                    ErrorTorrent.Motivo.SIN_METADATOS,
                    "Hay gente con este torrent, pero en ${pasado / 1000} s no ha llegado la lista de sus archivos. Prueba otra vez o con otra fuente.",
                ) else ErrorTorrent(
                    ErrorTorrent.Motivo.SIN_PARES,
                    "Nadie comparte este torrent ahora mismo: en ${pasado / 1000} s no ha aparecido nadie. Prueba con otra fuente.",
                )
            }
            delay(250)
        }
    }

    /** En la serie: elige el archivo, mira que quepa y empieza a bajarlo. */
    private fun empieza(t: TorrentActivo, fileIdx: Int?, nombre: String?): Flujo {
        if (t.parado) throw ErrorTorrent(ErrorTorrent.Motivo.PARADO, "Se ha parado el torrent.")
        t.error?.let { throw it }
        val info = t.cargaInfo()
        val a = when (val r = Eleccion.elige(info.archivos, fileIdx, nombre)) {
            is Eleccion.Resultado.Elegido -> r.archivo
            is Eleccion.Resultado.NoEsta -> throw ErrorTorrent(ErrorTorrent.Motivo.ARCHIVO_NO_ENCONTRADO, r.mensaje)
        }
        val ya = runCatching { t.handle.fileProgress(TorrentHandle.PIECE_GRANULARITY)[a.indice] }.getOrDefault(0L)
        val falta = (a.tamano - ya).coerceAtLeast(0)
        val c = carpeta()
        runCatching { Cache.libera(c, ajustes.cacheMaxMB * MB, enUso(), reserva = falta, necesarios = falta + MARGEN) { disco.libre(c) } }
        val libre = disco.libre(c)
        if (libre >= falta + MARGEN) disco.pide(c, falta + MARGEN)
        else throw ErrorTorrent(
            ErrorTorrent.Motivo.SIN_ESPACIO,
            "No hay sitio para «${a.nombre}»: hacen falta ${legible(falta + MARGEN)} y en el móvil quedan ${legible(libre)}.",
        )
        t.juega(a)
        t.entregado = true
        val puerto = checkNotNull(servidor).puerto
        val ruta = "/$clave/${t.hash}/${a.indice}/" + URLEncoder.encode(a.nombre, "UTF-8").replace("+", "%20")
        return Flujo("http://127.0.0.1:$puerto$ruta", a.nombre, a.tamano, Mime.de(a.nombre))
    }

    /** Lo que sirve el servidor en cada ruta: /<clave>/<infoHash>/<índice>/<nombre>. */
    private fun busca(ruta: String): Contenido? {
        val partes = ruta.trim('/').split('/')
        if (partes.size < 3 || partes[0] != clave) return null
        val i = partes[2].toIntOrNull() ?: return null
        return activos[partes[1]]?.contenido(i)
    }

    /** En la serie: tras [para], o se queda compartiendo (sin bajar más) o se borra. */
    private fun suelta(t: TorrentActivo) {
        val s = sesion
        if (ajustes.seguirCompartiendoAlParar && s != null && t.info != null && t.error == null) {
            t.soloSubir()
            compartidos[t.hash] = t
            Cache.toca(t.dir)
            ambito.launch { guardaReanudar(t) }
            return
        }
        descarta(t)
    }

    /** En la serie: lo quita de la sesión y borra su carpeta (libtorrent borra sus archivos; si tarda, se borran a mano). */
    private fun descarta(t: TorrentActivo) {
        activos.remove(t.hash, t)
        compartidos.remove(t.hash, t)
        t.detiene()
        val s = sesion
        if (s == null || t.info == null) {
            runCatching { s?.quita(t.handle, borrar = false) }
            Cache.borra(t.dir)
        } else {
            val b = Borrado(t)
            borrando[t.hash] = b
            runCatching { s.quita(t.handle, borrar = true) }
            ambito.launch {
                if (withTimeoutOrNull(tiempos.borradoMs) { b.hecho.await() } == null) acabaBorrado(t.hash, b)
            }
        }
        programaApagado()
    }

    private fun acabaBorrado(hash: String, b: Borrado) {
        if (borrando.remove(hash, b)) Cache.borra(b.t.dir)
        b.hecho.complete(Unit)
    }

    /** Sin nada en marcha, la sesión (y la DHT, y el servidor) se apagan pasado un rato: así no se queda el móvil trabajando. */
    private fun programaApagado() {
        if (activos.isNotEmpty() || compartidos.isNotEmpty()) return
        apagado?.cancel()
        apagado = ambito.launch {
            delay(tiempos.apagadoMs)
            withContext(serie) {
                if (activos.isEmpty() && compartidos.isEmpty() && sesion != null) apaga(emptyList())
            }
        }
    }

    /**
     * En la serie: guarda lo que se sigue compartiendo (para reanudarlo otro día), apaga todo y borra lo que no se guarda. Sin
     * suspender en medio, para que nada de la serie se cuele mientras se apaga.
     */
    private fun apaga(parados: List<TorrentActivo>) {
        apagado?.cancel()
        apagado = null
        val guardar = ajustes.seguirCompartiendoAlParar
        val quedan = if (guardar) parados.filter { it.info != null && it.error == null } else emptyList()
        for (t in quedan) runCatching { guardaReanudar(t) }
        runCatching { vigilancia?.close() }
        runCatching { servidor?.close() }
        runCatching { sesion?.close() }
        vigilancia = null
        servidor = null
        sesion = null
        compartidos.clear()
        sinWifi = false
        for (t in parados) if (t !in quedan) Cache.borra(t.dir)
        for ((h, b) in borrando.toList()) acabaBorrado(h, b)
        runCatching { Cache.libera(carpeta(), ajustes.cacheMaxMB * MB, activos.keys) }
    }

    /**
     * Pide a libtorrent lo que hace falta para reanudar el torrent otro día (con sus metadatos) y lo deja en su carpeta. Espera al
     * aviso de libtorrent (unos milisegundos; como mucho 5 s).
     */
    private fun guardaReanudar(t: TorrentActivo) {
        val listo = CompletableFuture<ByteArray?>()
        reanudar[t.hash] = listo
        try {
            runCatching { t.handle.saveResumeData(TorrentHandle.SAVE_INFO_DICT) }.onFailure { return }
            val datos = runCatching { listo.get(5, TimeUnit.SECONDS) }.getOrNull() ?: return
            val tmp = File(t.dir, "$REANUDAR.tmp")
            tmp.writeBytes(datos)
            if (!tmp.renameTo(File(t.dir, REANUDAR))) tmp.delete()
        } catch (e: Exception) {
            Log.w(ETIQUETA, "No se ha podido guardar ${t.hash} para reanudarlo", e)
        } finally {
            reanudar.remove(t.hash, listo)
        }
    }

    /** En la serie: con «Solo con wifi» y datos móviles, se pone todo en pausa (y se avisa a quien espere); con wifi, sigue. */
    private fun revisaRed() {
        val s = sesion ?: return
        val bloquear = ajustes.soloWifi && red.actual() == TipoRed.MOVIL
        if (bloquear == sinWifi) return
        sinWifi = bloquear
        if (bloquear) {
            runCatching { s.pausa() }
            val e = errorSinWifi()
            activos.values.forEach { it.ponError(e) }
        } else {
            activos.values.forEach { if (it.error?.motivo == ErrorTorrent.Motivo.SIN_WIFI) it.ponError(null) }
            runCatching { s.sigue() }
        }
    }

    /** Al crear el motor: fuera los restos que no se pueden reanudar, y lo demás hasta el tope de la caché. */
    private fun limpiaRestos() {
        val c = carpeta()
        c.listFiles()?.filter { it.isDirectory && it.name !in enUso() && !File(it, REANUDAR).isFile }?.forEach { Cache.borra(it) }
        Cache.libera(c, ajustes.cacheMaxMB * MB, enUso())
    }

    /** Los avisos de libtorrent (en su hilo: nada lento aquí). */
    private fun alAviso(a: Alert<*>) {
        try {
            when (a) {
                is PieceFinishedAlert -> torrentDe(a)?.alLlegar(a.pieceIndex())
                is MetadataReceivedAlert -> torrentDe(a)?.alLlegarMetadatos()
                is FileErrorAlert -> torrentDe(a)?.ponError(errorDeDisco(a.error().getValue(), a.error().getMessage(), a.filename()))
                is TorrentErrorAlert -> if (a.error().isError) torrentDe(a)?.ponError(errorDeDisco(a.error().getValue(), a.error().getMessage(), a.filename()))
                is TorrentDeletedAlert -> hashV1(a)?.let { h -> borrando[h]?.let { acabaBorrado(h, it) } }
                is TorrentDeleteFailedAlert -> hashV1(a)?.let { h -> borrando[h]?.let { acabaBorrado(h, it) } }
                is SaveResumeDataAlert -> hashV1(a)?.let { h -> reanudar[h]?.complete(runCatching { Sesion.datosParaReanudar(a.params()) }.getOrNull()) }
                is SaveResumeDataFailedAlert -> hashV1(a)?.let { h -> reanudar[h]?.complete(null) }
                else -> {}
            }
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Aviso de libtorrent sin atender: ${a.what()}", e)
        }
    }

    private fun hashV1(a: TorrentAlert<*>): String? = runCatching { a.swig().handle.info_hashes().getV1().to_hex() }.getOrNull()
        ?.takeIf { it.any { c -> c != '0' } }
        ?: when (a) {
            is TorrentDeletedAlert -> runCatching { a.getInfoHashes().getV1().toHex() }.getOrNull()
            is TorrentDeleteFailedAlert -> runCatching { a.getInfoHashes().getV1().toHex() }.getOrNull()
            else -> null
        }

    private fun torrentDe(a: TorrentAlert<*>): TorrentActivo? = hashV1(a)?.let { activos[it] ?: compartidos[it] }

    private fun errorDeDisco(codigo: Int, mensaje: String?, archivo: String?): ErrorTorrent =
        if (codigo == ENOSPC || mensaje?.contains("space", ignoreCase = true) == true) {
            ErrorTorrent(ErrorTorrent.Motivo.SIN_ESPACIO, "El móvil se ha quedado sin sitio y el torrent no puede seguir bajando.")
        } else {
            ErrorTorrent(ErrorTorrent.Motivo.MOTOR, "El torrent no puede seguir: ${mensaje ?: "error $codigo"}${archivo?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}.")
        }

    // --- Para las pruebas ---

    /** El puerto donde la sesión escucha a otros pares (0 si no está en marcha). */
    internal suspend fun puertoSesion(): Int = withContext(serie) { sesion?.puerto ?: 0 }

    /** Espera a que acabe lo que está en la serie (lo que mandan [para], [paraTodo] y [ajusta]). */
    internal suspend fun esperaSerie() = withContext(serie) {}

    internal fun enMarcha(): Boolean = runBlocking { withContext(serie) { sesion != null } }

    internal fun cierra() {
        paraTodo()
        runBlocking { esperaSerie() }
        ambito.cancel()
    }

    companion object {
        /**
         * El tiempo de lectura que conviene en el reproductor (DefaultHttpDataSource.Factory().setReadTimeoutMs): mientras llega una
         * pieza la respuesta no avanza, y el motor espera hasta un minuto antes de cortar.
         */
        const val ESPERA_LECTURA_MS = 70_000

        private const val ETIQUETA = "MotorTorrent"
        internal const val REANUDAR = ".reanudar"
        private const val MB = 1024L * 1024
        /** Lo que se deja libre de más en el disco al empezar (las piezas que el archivo comparte con otros, y que respire el móvil). */
        private const val MARGEN = 64 * MB
        private const val ENOSPC = 28

        internal fun legible(bytes: Long): String = when {
            bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
            else -> "${bytes shr 20} MB"
        }
    }
}
