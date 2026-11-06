package com.gonzalo.mostaza

import android.content.Context

/*
 * Los torrents (`infoHash`) los pone el motor de la familia (el módulo `:torrent`, `com.gonzalo.torrent`, con libtorrent4j). Mostaza
 * solo habla con él por esta interfaz: le da el torrent y recibe una dirección local (http://127.0.0.1:…) que el reproductor abre como
 * cualquier otra. Lo conecta [FuenteMotor.conecta] al arrancar.
 */

/** Lo que Mostaza le pide al motor de torrents. */
interface FuenteTorrent {
    /**
     * Prepara el torrent [infoHash] (el archivo [fileIdx]; sin él, el más grande) con los rastreadores y nodos de [fuentes] (los
     * `sources` de la fuente: `tracker:udp://…`, `dht:…`) y devuelve la dirección local para reproducirlo. [nombre]: el del archivo o el
     * título, para enseñarlo. Lanza una excepción con un texto para ti si no se puede.
     */
    suspend fun prepara(infoHash: String, fileIdx: Int?, fuentes: List<String>, nombre: String?): String

    /** Deja ese torrent (al cerrar el reproductor). */
    fun para(infoHash: String)
}

/** Un torrent que no se puede poner (el texto es para enseñártelo tal cual). */
class SinTorrent(msg: String) : Exception(msg)

/** Mientras no está el motor: lo dice, y propone una fuente directa o de tu debrid. */
object SinTorrents : FuenteTorrent {
    const val AVISO = "No se ha podido poner en marcha el motor de torrents en este dispositivo. Elige una fuente directa o de tu debrid (las que no dicen «torrent»): con tu Real-Debrid puesto en tu addon, salen las mismas películas sin torrent."
    override suspend fun prepara(infoHash: String, fileIdx: Int?, fuentes: List<String>, nombre: String?): String = throw SinTorrent(AVISO)
    override fun para(infoHash: String) {}
}

/**
 * Los ajustes que se le pasan al motor de torrents: [soloWifi], los límites de subida y bajada (KB/s; 0 = sin límite) y si se sigue
 * compartiendo mientras ves ([compartir]) viajan con tu cuenta (`ajustes/mostaza_torrent`); [cacheMB], lo que puede ocupar en este
 * dispositivo, es de cada uno.
 */
/** La subida por defecto, limitada (lo justo para que los demás te sigan enviando trozos sin gastarte la conexión). */
const val SUBIDA_POR_DEFECTO_KBS = 100

data class AjustesTorrent(val soloWifi: Boolean = true, val subidaKBs: Int = SUBIDA_POR_DEFECTO_KBS, val bajadaKBs: Int = 0, val compartir: Boolean = true, val cacheMB: Int = 4096)

object Torrents {
    /** El motor ([FuenteMotor]; [SinTorrents] si no se pudo poner en marcha). */
    @Volatile var fuente: FuenteTorrent = SinTorrents

    val disponible: Boolean get() = fuente !== SinTorrents

    /** Los ajustes de tu cuenta para el motor. */
    fun ajustes(c: Context): AjustesTorrent = AjustesTorrents.ajustes(c)

    /** Lo llama el motor (al conectarlo) para enterarse de los cambios de ajustes (de aquí o de otro dispositivo). */
    @Volatile var alCambiarAjustes: ((AjustesTorrent) -> Unit)? = null

    internal fun ajustesCambiados(c: Context) { alCambiarAjustes?.let { f -> runCatching { f(ajustes(c)) } } }
}
