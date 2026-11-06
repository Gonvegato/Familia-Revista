package com.gonzalo.torrent

/**
 * Las piezas de un torrent como bytes seguidos: todos sus archivos uno detrás de otro, cortados en piezas de [tamPieza] (la última,
 * más corta). Un archivo empieza y acaba donde caiga, a menudo en mitad de una pieza que comparte con el de al lado.
 */
internal class MapaPiezas(val tamPieza: Int, val numPiezas: Int, val tamTotal: Long) {
    init { require(tamPieza > 0 && numPiezas > 0 && tamTotal > 0) }

    fun piezaDe(offset: Long): Int = (offset / tamPieza).toInt().coerceIn(0, numPiezas - 1)
    fun inicio(pieza: Int): Long = pieza.toLong() * tamPieza
    fun fin(pieza: Int): Long = minOf(inicio(pieza) + tamPieza, tamTotal)
}

/** Un archivo del torrent dentro del [mapa]: empieza en el byte [offset] del torrent y ocupa [tamano] bytes. */
internal class TramoArchivo(val mapa: MapaPiezas, val offset: Long, val tamano: Long) {
    init { require(tamano > 0 && offset >= 0 && offset + tamano <= mapa.tamTotal) }

    val primera: Int = mapa.piezaDe(offset)
    val ultima: Int = mapa.piezaDe(offset + tamano - 1)
    val numPiezas: Int get() = ultima - primera + 1

    /** La pieza donde está el byte [pos] del archivo. */
    fun piezaEn(pos: Long): Int = mapa.piezaDe(offset + pos.coerceIn(0, tamano - 1))

    /** Cuántos bytes del archivo hay desde [pos] hasta el final de su pieza (o del archivo, si acaba antes). */
    fun hastaFinDePieza(pos: Long): Long = minOf(mapa.fin(piezaEn(pos)), offset + tamano) - (offset + pos)

    /** Las piezas que hacen falta para los bytes [desde, desde + bytes) del archivo (al menos una). */
    fun piezas(desde: Long, bytes: Long): IntRange {
        val d = desde.coerceIn(0, tamano - 1)
        val h = (d + maxOf(bytes, 1) - 1).coerceAtMost(tamano - 1)
        return piezaEn(d)..piezaEn(h)
    }

    /** Dónde empieza (en bytes del archivo) la pieza [pieza]; 0 si es la primera. */
    fun inicioEnArchivo(pieza: Int): Long = (mapa.inicio(pieza) - offset).coerceAtLeast(0)

    /** Los bytes seguidos ya descargados desde [pos] (según [tiene]), sin pasar del final del archivo. */
    fun listosDesde(pos: Long, tiene: (Int) -> Boolean): Long {
        if (pos >= tamano) return 0
        var p = piezaEn(pos)
        while (p <= ultima && tiene(p)) p++
        val hasta = if (p > ultima) tamano else inicioEnArchivo(p)
        return (hasta - pos).coerceAtLeast(0)
    }
}
