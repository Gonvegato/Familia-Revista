package com.gonzalo.torrent

/**
 * Cuánto se adelanta la descarga a lo que lee el reproductor.
 * - [criticaBytes]: lo que hace falta ya, con plazo (libtorrent lo pide a varios pares si se retrasa); entre [criticaMinPiezas] y
 *   [criticaMaxPiezas] piezas.
 * - [lecturaBytes]: lo siguiente, con prioridad alta pero sin plazo.
 * - [cabezaBytes] y [colaBytes]: el principio y el final del archivo, lo primero que se baja (MKV y MP4 guardan al final el índice
 *   para saltar, y el reproductor lo lee antes de empezar).
 * - [ritmoBs]: el ritmo con que se cuentan los plazos (una pieza cada tamaño/ritmo).
 */
internal data class Ventanas(
    val criticaBytes: Long = 8L shl 20,
    val criticaMinPiezas: Int = 2,
    val criticaMaxPiezas: Int = 48,
    val lecturaBytes: Long = 64L shl 20,
    val cabezaBytes: Long = 2L shl 20,
    val colaBytes: Long = 2L shl 20,
    val ritmoBs: Long = 1L shl 20,
)

/** Un archivo que se está viendo y dónde leen sus lectores (bytes del archivo), el más reciente primero. */
internal class EnJuego(val tramo: TramoArchivo, val lectores: List<Long>)

/** Lo que se le pide a libtorrent: la prioridad de cada pieza del torrent (0..7) y los plazos (en ms desde ahora) de las urgentes. */
internal class Plan(val prioridades: IntArray, val plazos: Map<Int, Int>)

/**
 * El orden de descarga, calculado de nuevo cada vez que un lector se abre (un salto) o pasa a la pieza siguiente:
 * - Lo que no se ve, nada (0). Del archivo que se ve: normal (4) desde el primer lector hasta el final, baja (1) lo de antes.
 * - Cabeza y cola del archivo: máxima (7). Mientras nadie lee todavía, además con plazo: primero la cabeza y luego la cola.
 * - Por cada lector, desde donde lee: la ventana crítica a máxima y con plazos crecientes (una pieza tras otra), y la de lectura a
 *   alta (6). Si hay varios lectores, el más reciente va primero (es el salto que acaba de pedir el reproductor).
 * Las piezas que ya están no llevan plazo.
 */
internal object Prioridades {
    const val NADA = 0
    const val BAJA = 1
    const val NORMAL = 4
    const val ALTA = 6
    const val MAXIMA = 7

    fun calcula(mapa: MapaPiezas, enJuego: List<EnJuego>, tiene: (Int) -> Boolean, v: Ventanas = Ventanas()): Plan {
        val pr = IntArray(mapa.numPiezas)
        val plazos = HashMap<Int, Int>()
        val paso = pasoMs(mapa.tamPieza, v)
        fun plazo(p: Int, ms: Int) { if (!tiene(p)) plazos[p] = minOf(plazos[p] ?: Int.MAX_VALUE, ms) }
        fun sube(p: Int, prioridad: Int) { if (pr[p] < prioridad) pr[p] = prioridad }

        for (j in enJuego) {
            val t = j.tramo
            val desde = j.lectores.minOrNull()?.let { t.piezaEn(it) } ?: t.primera
            for (p in t.primera..t.ultima) sube(p, if (p < desde) BAJA else NORMAL)
            val cabeza = t.piezas(0, v.cabezaBytes)
            val cola = t.piezas(t.tamano - v.colaBytes, v.colaBytes)
            for (p in cabeza) sube(p, MAXIMA)
            for (p in cola) sube(p, MAXIMA)
            if (j.lectores.isEmpty()) {
                var k = 0
                for (p in cabeza) plazo(p, k++ * paso)
                for (p in cola) if (p !in cabeza) plazo(p, k++ * paso)
            }
            j.lectores.forEachIndexed { r, pos ->
                for (p in t.piezas(pos, v.lecturaBytes)) sube(p, ALTA)
                var k = r
                for (p in critica(t, pos, v)) { sube(p, MAXIMA); plazo(p, k++ * paso) }
            }
        }
        return Plan(pr, plazos)
    }

    /** Las piezas urgentes a partir de [pos]: las de [Ventanas.criticaBytes], con un mínimo y un máximo de piezas. */
    fun critica(t: TramoArchivo, pos: Long, v: Ventanas): IntRange {
        val desde = t.piezaEn(pos)
        val porBytes = t.piezas(pos, v.criticaBytes).last - desde + 1
        val n = porBytes.coerceIn(v.criticaMinPiezas, v.criticaMaxPiezas)
        return desde..minOf(t.ultima, desde + n - 1)
    }

    /** Lo que se deja entre plazo y plazo: lo que tarda una pieza al ritmo esperado (entre 0,1 y 5 s). */
    fun pasoMs(tamPieza: Int, v: Ventanas): Int = (tamPieza * 1000L / v.ritmoBs).coerceIn(100, 5000).toInt()
}
