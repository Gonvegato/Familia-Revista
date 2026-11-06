package com.gonzalo.torrent

import java.net.URLEncoder

/**
 * Dónde buscar pares, sacado de `sources` de un stream de Stremio (docs/api/responses/stream.md del SDK de addons):
 * - `tracker:<protocolo>://<host>:<puerto>…` es un tracker (http, https o udp).
 * - `dht:<hash>` pide buscar en la DHT. Sin ninguna entrada `dht:` (y con trackers) no se usa la DHT para ese torrent: así lo quieren
 *   los addons de trackers privados, que no dejan anunciarse en la DHT.
 * Si `sources` no trae nada aprovechable, se busca con la DHT y unos cuantos trackers públicos de siempre; si es público (trae `dht:`),
 * a sus trackers se les suman esos públicos.
 */
internal data class Fuentes(val trackers: List<String>, val dht: Boolean)

internal object LeeFuentes {
    /** Trackers públicos y abiertos, para cuando el stream no trae los suyos. */
    val PUBLICOS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
    )

    private val tracker = Regex("^(udp|https?)://[^\\s/:]+(:\\d{1,5})?(/\\S*)?$", RegexOption.IGNORE_CASE)

    fun lee(fuentes: List<String>): Fuentes {
        val trackers = LinkedHashSet<String>()
        var dht = false
        for (f in fuentes) {
            val s = f.trim()
            when {
                s.startsWith("tracker:", ignoreCase = true) -> s.substring("tracker:".length).trim().takeIf { tracker.matches(it) }?.let(trackers::add)
                // dht:<hash>: el hash es el del propio torrent (o el de un nodo); basta con saber que se puede buscar en la DHT
                s.startsWith("dht:", ignoreCase = true) -> dht = true
                // Hay addons que mandan el tracker sin el prefijo
                tracker.matches(s) -> trackers.add(s)
            }
        }
        if (trackers.isEmpty() && !dht) return Fuentes(PUBLICOS, dht = true)
        // Un torrent público (se puede buscar en la DHT): además de los suyos, los trackers públicos de siempre, para dar con más gente
        // (con pocos trackers propios se encontraba uno o dos pares). Uno de un tracker privado (sin `dht:`) se queda solo con los suyos.
        if (dht) trackers.addAll(PUBLICOS)
        return Fuentes(trackers.toList(), dht)
    }

    /**
     * El enlace magnet con el que se pide el torrent a libtorrent (los pares y los metadatos llegan por los trackers y la DHT). Con
     * [soloArchivo] (el `fileIdx` del addon), solo se baja ese archivo desde el primer momento (`so=`, BEP 53).
     */
    fun magnet(infoHash: String, fuentes: Fuentes, nombre: String?, soloArchivo: Int? = null): String = buildString {
        append("magnet:?xt=urn:btih:").append(infoHash)
        nombre?.takeIf { it.isNotBlank() }?.let { append("&dn=").append(URLEncoder.encode(it, "UTF-8")) }
        for (t in fuentes.trackers) append("&tr=").append(URLEncoder.encode(t, "UTF-8"))
        soloArchivo?.takeIf { it >= 0 }?.let { append("&so=").append(it) }
    }
}

internal object InfoHash {
    private val hex = Regex("^[0-9a-fA-F]{40}$")
    private val base32 = Regex("^[A-Za-z2-7]{32}$")

    /** El infoHash en hexadecimal y en minúsculas (Stremio lo manda así; los magnet viejos lo traen en base32), o null si no lo es. */
    fun normaliza(s: String): String? {
        val t = s.trim().removePrefix("urn:btih:")
        return when {
            hex.matches(t) -> t.lowercase()
            base32.matches(t) -> deBase32(t.uppercase())
            else -> null
        }
    }

    private fun deBase32(s: String): String {
        val alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val sb = StringBuilder(40)
        var buffer = 0L
        var bits = 0
        for (c in s) {
            buffer = (buffer shl 5) or alfabeto.indexOf(c).toLong()
            bits += 5
            while (bits >= 8) {
                bits -= 8
                sb.append("%02x".format(((buffer shr bits) and 0xff).toInt()))
            }
        }
        return sb.toString()
    }
}
