package com.gonzalo.torrent

import java.io.File

/**
 * La carpeta de los torrents: una subcarpeta por torrent, con su infoHash de nombre. La fecha de la subcarpeta es la de su último uso
 * (se pone al empezar y al parar). Lo que hay aquí es solo de este móvil: trozos de vídeo, nada que deba ir a la cuenta.
 */
internal object Cache {
    /** Lo que ocupa [dir] (todos sus archivos). Con archivos a medias puede contar de más: libtorrent los crea con su tamaño final. */
    fun ocupa(dir: File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun toca(dir: File) { runCatching { dir.mkdirs(); dir.setLastModified(System.currentTimeMillis()) } }

    fun borra(dir: File): Boolean = !dir.exists() || dir.deleteRecursively()

    /**
     * Borra de [carpeta] los torrents que no están [enUso], del más viejo al más nuevo, hasta que lo que queda (más [reserva], lo que
     * aún va a crecer el que empieza) quepa en [maxBytes] y haya [necesarios] bytes libres en el disco ([libre]). Devuelve los borrados.
     */
    fun libera(
        carpeta: File, maxBytes: Long, enUso: Set<String>, reserva: Long = 0, necesarios: Long = 0,
        libre: () -> Long = { carpeta.usableSpace },
    ): List<String> {
        val dirs = carpeta.listFiles()?.filter { it.isDirectory }.orEmpty()
        val tamanos = dirs.associateWith { ocupa(it) }
        var total = tamanos.values.sum() + reserva
        val borrados = ArrayList<String>()
        for (d in dirs.filter { it.name !in enUso }.sortedBy { it.lastModified() }) {
            if (total <= maxBytes && libre() >= necesarios) break
            if (borra(d)) {
                total -= tamanos.getValue(d)
                borrados += d.name
            }
        }
        return borrados
    }
}
