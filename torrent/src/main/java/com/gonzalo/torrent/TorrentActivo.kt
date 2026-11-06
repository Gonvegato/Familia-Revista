package com.gonzalo.torrent

import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentStatus
import java.io.File
import java.io.RandomAccessFile
import java.util.BitSet
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Los tiempos de espera del motor (en la app, los de siempre; las pruebas los acortan). */
internal data class Tiempos(
    /** Sin ningún par a la vista en este tiempo: no hay nadie compartiéndolo. */
    val sinParesMs: Long = 30_000,
    /** Con pares, pero sin la lista de archivos del torrent (sus metadatos) en este tiempo. */
    val metadatosMs: Long = 90_000,
    /** Lo más que espera una lectura del reproductor a que llegue la pieza que necesita. */
    val esperaPiezaMs: Long = 60_000,
    /** Sin torrents en marcha, se apaga la sesión (y la DHT) pasado este tiempo. */
    val apagadoMs: Long = 30_000,
    /** Lo que se espera a que libtorrent borre los archivos de un torrent antes de borrarlos a mano. */
    val borradoMs: Long = 10_000,
)

/** Lo que una lectura espera a que libtorrent pase al archivo lo que tenía aparte (ver [TorrentActivo.Lector]). */
private const val ESPERA_DISCO_MS = 5_000L

/** Los metadatos de un torrent: sus piezas, sus archivos (en el orden de libtorrent) y dónde está cada uno en el disco. */
internal class InfoTorrent(val mapa: MapaPiezas, val archivos: List<ArchivoTorrent>, val offsets: LongArray, val rutas: List<File>)

/**
 * Un torrent en la sesión: lo que se sabe de él, sus piezas ya descargadas y los archivos que se ven (cada uno, con sus lectores).
 * Lo que cambia por los avisos de libtorrent (otro hilo) y por las lecturas (los hilos del servidor) va bajo [cerrojo].
 */
internal class TorrentActivo(
    val hash: String,
    val handle: TorrentHandle,
    val dir: File,
    private val tiempos: Tiempos,
    private val ventanas: Ventanas,
) {
    private val cerrojo = ReentrantLock()
    private val llega = cerrojo.newCondition()
    private val piezas = BitSet()
    private val enJuego = LinkedHashMap<Int, ArchivoEnJuego>()
    private var aplicado: Plan? = null
    private var ultimo: ArchivoEnJuego? = null

    @Volatile var info: InfoTorrent? = null
        private set
    @Volatile var error: ErrorTorrent? = null
        private set
    @Volatile var parado = false
        private set

    /** Lo que pidió el primer `prepara` (fileIdx y nombre): con ello se elige el archivo en cuanto llegan los metadatos. */
    @Volatile var pedido: Pair<Int?, String?> = null to null

    /** Cuántos `prepara` esperan a este torrent, y si alguno ya ha entregado su [Flujo] (solo desde la serie del motor). */
    var interesados = 0
    var entregado = false

    /** Los metadatos, al llegar (una vez): las piezas que ya están (de una vez anterior) y la lista de archivos. */
    fun cargaInfo(): InfoTorrent = cerrojo.withLock { info ?: leeInfo() }

    private fun leeInfo(): InfoTorrent {
        val ti = handle.torrentFile() ?: throw ErrorTorrent(ErrorTorrent.Motivo.SIN_METADATOS, "Aún no ha llegado la lista de archivos del torrent.")
        val fs = ti.files()
        val n = fs.numFiles()
        val archivos = (0 until n).map { ArchivoTorrent(it, fs.filePath(it), fs.fileSize(it), fs.padFileAt(it)) }
        val nueva = InfoTorrent(
            MapaPiezas(ti.pieceLength(), ti.numPiezasSeguro(), fs.totalSize()),
            archivos,
            LongArray(n) { fs.fileOffset(it) },
            (0 until n).map { File(fs.filePath(it, dir.absolutePath)) },
        )
        val bits = handle.status(TorrentHandle.QUERY_PIECES).pieces()
        cerrojo.withLock {
            for (i in 0 until minOf(bits.size(), nueva.mapa.numPiezas)) if (bits.getBit(i)) piezas.set(i)
            info = nueva
        }
        return nueva
    }

    private fun org.libtorrent4j.TorrentInfo.numPiezasSeguro(): Int = numPieces().coerceAtLeast(1)

    /** Empieza a ver el archivo [a] (o sigue con él): su prioridad, y la descarga desde el principio (cabeza y cola primero). */
    fun juega(a: ArchivoTorrent): ArchivoEnJuego {
        val info = checkNotNull(info) { "Sin metadatos" }
        return cerrojo.withLock {
            val ya = enJuego[a.indice]
            val e = ya ?: ArchivoEnJuego(a, TramoArchivo(info.mapa, info.offsets[a.indice], a.tamano), info.rutas[a.indice]).also { enJuego[a.indice] = it }
            ultimo = e
            if (ya == null) {
                // Prioridad por archivos: este (y los que ya se veían), a normal; los demás, nada. Luego, pieza a pieza.
                val prios = Array(info.archivos.size) { if (it in enJuego) Priority.DEFAULT else Priority.IGNORE }
                handle.prioritizeFiles(prios)
                handle.unsetFlags(TorrentFlags.UPLOAD_MODE)
                // Lo que ya bajó de este archivo cuando no se veía (en piezas que comparte con otro) está en el archivo aparte de
                // libtorrent (.parts); al elegirlo, libtorrent lo pasa a su sitio. Una llamada que espera a libtorrent llega detrás:
                // cuando vuelve, ya está, y el servidor puede leerlo.
                runCatching { handle.filePriorities() }
                aplicado = null
            }
            replanifica()
            e
        }
    }

    fun contenido(indice: Int): Contenido? = cerrojo.withLock { enJuego[indice] }

    /** Deja de bajar (al parar con «seguir compartiendo»): lo que ya tiene se sigue subiendo. */
    fun soloSubir() {
        cerrojo.withLock { enJuego.clear(); aplicado = null }
        runCatching { handle.clearPieceDeadlines(); handle.setFlags(TorrentFlags.UPLOAD_MODE) }
    }

    /** Vuelve a ponerlo en marcha (estaba compartiendo, parado, y se vuelve a ver). */
    fun vuelve() {
        cerrojo.withLock { parado = false }
    }

    /** Lo para: las lecturas que esperan acaban con error y las nuevas también. */
    fun detiene() {
        cerrojo.withLock { parado = true; llega.signalAll() }
    }

    fun ponError(e: ErrorTorrent?) {
        cerrojo.withLock { error = e; llega.signalAll() }
    }

    /** Aviso de libtorrent: ha llegado (y está en el disco) la pieza [p]. */
    fun alLlegar(p: Int) {
        cerrojo.withLock {
            piezas.set(p)
            llega.signalAll()
        }
    }

    /**
     * Aviso de libtorrent: han llegado los metadatos. Sin el archivo elegido en el magnet, libtorrent empieza a bajarlo todo: se
     * elige ya, aquí (sin esperar a que `prepara` lo vea), para no bajar nada de los demás.
     */
    fun alLlegarMetadatos() {
        runCatching {
            val info = cargaInfo()
            val (idx, nombre) = pedido
            val elegido = (Eleccion.elige(info.archivos, idx, nombre) as? Eleccion.Resultado.Elegido)?.archivo
            cerrojo.withLock {
                if (enJuego.isEmpty() && !parado) {
                    handle.prioritizeFiles(Array(info.archivos.size) { if (it == elegido?.indice) Priority.DEFAULT else Priority.IGNORE })
                }
            }
        }
        cerrojo.withLock { llega.signalAll() }
    }

    /** Vuelve a calcular el orden de descarga y se lo pasa a libtorrent (solo lo que cambia). */
    private fun replanifica() {
        val info = info ?: return
        cerrojo.withLock {
            val lista = enJuego.values.map { a -> EnJuego(a.tramo, a.lectores.asReversed().map { it.pos }) }
            val plan = Prioridades.calcula(info.mapa, lista, { piezas.get(it) }, ventanas)
            aplica(plan)
        }
    }

    private fun aplica(plan: Plan) {
        if (parado) return
        val antes = aplicado
        runCatching {
            antes?.plazos?.keys?.forEach { if (it !in plan.plazos && !piezas.get(it)) handle.resetPieceDeadline(it) }
            val cambios = if (antes == null) null else plan.prioridades.indices.filter { plan.prioridades[it] != antes.prioridades[it] }
            when {
                cambios == null || cambios.size > 64 -> handle.prioritizePieces(Array(plan.prioridades.size) { Priority.fromSwig(plan.prioridades[it]) })
                else -> for (p in cambios) handle.piecePriority(p, Priority.fromSwig(plan.prioridades[p]))
            }
            for ((p, ms) in plan.plazos) if (antes == null || p !in antes.plazos) handle.setPieceDeadline(p, ms)
            aplicado = plan
        }
    }

    /** Espera a que la pieza [p] esté en el disco, como mucho [Tiempos.esperaPiezaMs]. */
    private fun espera(p: Int) {
        cerrojo.withLock {
            if (piezas.get(p)) return
            val limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(tiempos.esperaPiezaMs)
            while (!piezas.get(p)) {
                if (parado) throw ErrorTorrent(ErrorTorrent.Motivo.PARADO, "Se ha parado el torrent.")
                error?.let { throw it }
                val resto = limite - System.nanoTime()
                if (resto <= 0) throw ErrorTorrent(
                    ErrorTorrent.Motivo.SIN_DATOS,
                    "Los datos no llegan: en ${tiempos.esperaPiezaMs / 1000} s no ha bajado lo que hace falta. Puede que quede poca gente compartiéndolo.",
                )
                llega.awaitNanos(minOf(resto, TimeUnit.SECONDS.toNanos(1)))
                // Por si se ha perdido algún aviso (la cola de libtorrent tiene un tope)
                if (!piezas.get(p) && runCatching { handle.havePiece(p) }.getOrDefault(false)) piezas.set(p)
            }
        }
    }

    /** Cómo va; [enPausa]: la sesión entera está parada (sin wifi). */
    /**
     * El estado de libtorrent, recién pedido (el de libtorrent4j sin forzar se guarda medio segundo en un campo que no es seguro entre
     * hilos: con dos a la vez, uno puede recibir null). Null si el torrent ya no está en la sesión.
     */
    fun estadoLibtorrent(): TorrentStatus? = runCatching { handle.status(true) }.getOrNull()

    fun estado(enPausa: Boolean): EstadoTorrent {
        val st = estadoLibtorrent()
        val info = info
        val (a, pos, listos) = cerrojo.withLock {
            val a = ultimo
            val pos = a?.ultimaPos ?: 0L
            Triple(a, pos, a?.tramo?.listosDesde(pos) { piezas.get(it) } ?: 0L)
        }
        val descargado = if (a != null) runCatching { handle.fileProgress(TorrentHandle.PIECE_GRANULARITY)[a.archivo.indice] }.getOrDefault(0L) else 0L
        val tamano = a?.archivo?.tamano ?: 0L
        val fase = when {
            info == null || st == null || !st.hasMetadata() -> EstadoTorrent.Fase.BUSCANDO
            enPausa || error?.motivo == ErrorTorrent.Motivo.SIN_WIFI -> EstadoTorrent.Fase.EN_PAUSA
            tamano > 0 && descargado >= tamano -> EstadoTorrent.Fase.COMPLETO
            else -> EstadoTorrent.Fase.DESCARGANDO
        }
        return EstadoTorrent(
            infoHash = hash,
            fase = fase,
            pares = st?.numPeers() ?: 0,
            semillas = st?.numSeeds() ?: 0,
            bajadaBs = (st?.downloadPayloadRate() ?: 0).toLong(),
            subidaBs = (st?.uploadPayloadRate() ?: 0).toLong(),
            progreso = if (tamano > 0) (descargado.toDouble() / tamano).toFloat().coerceIn(0f, 1f) else 0f,
            descargado = descargado,
            tamano = tamano,
            posicion = pos,
            listoAdelante = listos,
            archivo = a?.archivo?.nombre,
            error = error?.message,
        )
    }

    /** Un archivo que se ve: lo que sirve el servidor local para su dirección. */
    inner class ArchivoEnJuego(val archivo: ArchivoTorrent, val tramo: TramoArchivo, val ruta: File) : Contenido {
        val lectores = ArrayList<Lector>()
        /** Dónde leyó el reproductor por última vez (también cuando ya no hay lectores abiertos). */
        var ultimaPos = 0L
        override val tamano: Long get() = archivo.tamano
        override val mime: String = Mime.de(archivo.nombre)

        override fun abre(desde: Long): Lectura = cerrojo.withLock {
            if (parado) throw ErrorTorrent(ErrorTorrent.Motivo.PARADO, "Se ha parado el torrent.")
            Lector(this, desde).also {
                lectores += it
                ultimaPos = desde
                replanifica()
            }
        }
    }

    /** Un lector del reproductor (una respuesta del servidor): espera cada pieza y la lee del archivo. */
    inner class Lector(private val a: ArchivoEnJuego, @Volatile var pos: Long) : Lectura {
        private var archivo: RandomAccessFile? = null
        private var pieza = a.tramo.piezaEn(pos)

        override fun lee(pos: Long, buf: ByteArray, max: Int): Int {
            val p = a.tramo.piezaEn(pos)
            if (p != pieza || pos != this.pos) cerrojo.withLock {
                this.pos = pos
                a.ultimaPos = pos
                if (p != pieza) { pieza = p; replanifica() }
            }
            espera(p)
            val n = minOf(max.toLong(), a.tramo.hastaFinDePieza(pos), a.tamano - pos).toInt()
            val leidos = leeDelDisco(pos, buf, n)
            cerrojo.withLock {
                this.pos = pos + leidos
                a.ultimaPos = this.pos
            }
            return leidos
        }

        /**
         * Lee del archivo lo que libtorrent ya tiene. Si el archivo aún no está (o es más corto), es que libtorrent lo está pasando
         * de su archivo aparte: se reintenta un momento.
         */
        private fun leeDelDisco(pos: Long, buf: ByteArray, n: Int): Int {
            val limite = System.currentTimeMillis() + ESPERA_DISCO_MS
            while (true) {
                val leidos = try {
                    val f = archivo ?: RandomAccessFile(a.ruta, "r").also { archivo = it }
                    f.seek(pos)
                    f.read(buf, 0, n)
                } catch (e: java.io.FileNotFoundException) {
                    -1
                }
                if (leidos > 0) return leidos
                if (parado) throw ErrorTorrent(ErrorTorrent.Motivo.PARADO, "Se ha parado el torrent.")
                if (System.currentTimeMillis() > limite) {
                    throw ErrorTorrent(ErrorTorrent.Motivo.MOTOR, "El archivo del torrent no está en el móvil («${a.archivo.nombre}»).")
                }
                runCatching { archivo?.close() }
                archivo = null
                Thread.sleep(50)
            }
        }

        override fun close() {
            runCatching { archivo?.close() }
            archivo = null
            cerrojo.withLock { a.lectores.remove(this) }
        }
    }
}
