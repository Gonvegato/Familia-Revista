package com.gonzalo.torrent

/** La cabecera Range de una petición (RFC 9110, solo bytes y solo el primer tramo: los reproductores no piden más de uno). */
internal sealed interface Rango {
    /** Sin Range (o una que no se entiende, que se ignora): el archivo entero, 200. */
    data object Todo : Rango
    /** Los bytes [inicio, fin], los dos incluidos: 206 con Content-Range. */
    data class Parte(val inicio: Long, val fin: Long) : Rango { val bytes: Long get() = fin - inicio + 1 }
    /** Empieza después del final: 416, con un Content-Range que solo dice el tamaño (un asterisco donde iría el tramo). */
    data object Imposible : Rango

    companion object {
        private val forma = Regex("^\\s*(\\d*)\\s*-\\s*(\\d*)\\s*$")

        fun analiza(cabecera: String?, tamano: Long): Rango {
            val c = cabecera?.trim() ?: return Todo
            if (!c.startsWith("bytes=", ignoreCase = true)) return Todo
            val primero = c.substring("bytes=".length).split(',').first()
            val m = forma.matchEntire(primero) ?: return Todo
            val (a, b) = m.destructured
            val ini = a.toLongOrNull()
            val fin = b.toLongOrNull()
            if (a.isNotEmpty() && ini == null || b.isNotEmpty() && fin == null) return Todo   // números que no caben: se ignora
            return when {
                ini == null && fin == null -> Todo
                // bytes=-N: los N últimos
                ini == null -> if (fin!! == 0L || tamano == 0L) Imposible else Parte(maxOf(0, tamano - fin), tamano - 1)
                ini >= tamano -> Imposible
                fin == null -> Parte(ini, tamano - 1)
                fin < ini -> Todo
                else -> Parte(ini, minOf(fin, tamano - 1))
            }
        }
    }
}
