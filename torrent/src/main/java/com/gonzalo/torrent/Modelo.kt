package com.gonzalo.torrent

import java.io.File

/**
 * Lo que se reproduce: [url] es local (http://127.0.0.1:<puerto>/…), admite saltos (Range) y se lee mientras el torrent baja.
 * [archivo] es el nombre del archivo dentro del torrent, [tamano] sus bytes y [mime] su tipo, sacado de la extensión.
 */
data class Flujo(val url: String, val archivo: String, val tamano: Long, val mime: String)

/**
 * Lo que eliges del motor. No se guarda aquí: vive en tu cuenta (la app lo lee de allí y se lo da al motor, también en marcha con
 * [MotorTorrent.ajusta]). En el móvil solo quedan los trozos del torrent que se está viendo (y los que sigues compartiendo).
 *
 * - [soloWifi]: con datos móviles no se baja nada (prepara falla con un mensaje claro, y lo que está en marcha se para hasta la wifi).
 * - [subidaMaxKBs]: lo más que se sube a los demás, en KB/s (0 o menos: lo mínimo, 1 KB/s; nunca «sin límite»).
 * - [bajadaMaxKBs]: lo más que se baja, en KB/s (0: sin límite).
 * - [cacheMaxMB]: lo que pueden ocupar los torrents viejos; al empezar uno nuevo se borran los más antiguos hasta que quepan.
 *   El que se está viendo nunca se borra, aunque él solo ocupe más.
 * - [seguirCompartiendoAlParar]: al parar, el torrent se queda compartiendo lo que tiene (sin bajar más) y no se borra hasta que
 *   haga falta sitio o pares todo. Si no, al parar se deja de compartir y se borra.
 * - [carpeta]: dónde se guardan los torrents (por defecto, la caché privada de la app: nada de esto es visible para otras apps).
 */
data class AjustesTorrent(
    val soloWifi: Boolean = false,
    val subidaMaxKBs: Int = 64,
    val bajadaMaxKBs: Int = 0,
    val cacheMaxMB: Int = 4096,
    val seguirCompartiendoAlParar: Boolean = false,
    val carpeta: File? = null,
)

/** Cómo va un torrent, para enseñarlo mientras carga o se ve. */
data class EstadoTorrent(
    val infoHash: String,
    val fase: Fase,
    /** Pares conectados (y de ellos, los que lo tienen entero). */
    val pares: Int,
    val semillas: Int,
    /** Bytes por segundo, solo de datos (sin contar lo que añade el protocolo). */
    val bajadaBs: Long,
    val subidaBs: Long,
    /** Del archivo que se ve: lo que ya está en el móvil (0..1) y en bytes. */
    val progreso: Float,
    val descargado: Long,
    val tamano: Long,
    /** Dónde lee el reproductor (byte del archivo) y cuántos bytes seguidos hay ya bajados a partir de ahí. */
    val posicion: Long,
    val listoAdelante: Long,
    /** El nombre del archivo que se ve, cuando ya se sabe. */
    val archivo: String?,
    /** Si algo ha ido mal (sin espacio, sin wifi…): el mensaje para enseñar. */
    val error: String?,
    /**
     * Para saber de quién es la culpa si va lento: la gente que libtorrent conoce (de los trackers, la DHT y los demás pares), conectada
     * o no, y lo que dicen los trackers que hay en todo el enjambre (semillas y los que lo están bajando; −1 si ningún tracker lo dice).
     * Muchos conocidos y pocos conectados: no se consigue conectar. Pocos conocidos y el tracker dice que hay pocos: hay poca gente.
     */
    val conocidos: Int = 0,
    val semillasEnjambre: Int = -1,
    val bajandoEnjambre: Int = -1,
) {
    enum class Fase { BUSCANDO, DESCARGANDO, COMPLETO, EN_PAUSA }
}

/** Lo que puede salir mal, con su mensaje en español para enseñarlo tal cual. */
class ErrorTorrent(val motivo: Motivo, mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa) {
    enum class Motivo { HASH_NO_VALIDO, SIN_RED, SIN_WIFI, SIN_PARES, SIN_METADATOS, ARCHIVO_NO_ENCONTRADO, SIN_ESPACIO, SIN_DATOS, PARADO, MOTOR }
}
