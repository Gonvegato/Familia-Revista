package com.gonzalo.hiedra.pc

import com.gonzalo.familia.pc.Sitio
import java.io.File

/**
 * Qué se está ejecutando en este ordenador, para saber cuándo empieza y cuándo acaba un juego que no abre Hiedra directamente (Steam, Heroic
 * y Lutris abren el juego y su orden termina enseguida). En Linux se leen las líneas de órdenes de `/proc/<pid>/cmdline`; en el Mac, `ps`.
 * Un juego se reconoce porque su carpeta sale en la línea de alguno de sus procesos (también como la ve Wine: `Z:\home\…`).
 */
object Procesos {
    @Volatile var lineasPruebas: (() -> List<String>)? = null

    /** Las líneas de órdenes (con los argumentos separados por espacios). */
    fun lineas(): List<String> {
        lineasPruebas?.let { return it() }
        val yo = ProcessHandle.current().pid().toString()
        if (Sitio.sistema == Sitio.Sistema.MAC) return Programas.salida(listOf("/bin/ps", "-axww", "-o", "command="), 5)?.lines()?.filter { it.isNotBlank() }.orEmpty()
        return File("/proc").listFiles { f -> f.name.all(Char::isDigit) && f.name != yo }.orEmpty().mapNotNull { d ->
            runCatching { File(d, "cmdline").readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { String(it).replace('\u0000', ' ').trim() }
        }
    }

    /** Las formas en que sale una carpeta en las líneas: la normal (terminada en «/») y la de Wine (`Z:\…\`, sin mirar mayúsculas). */
    fun formas(carpeta: String): List<String> {
        val c = carpeta.trimEnd('/', '\\')
        if (c.length < 4) return emptyList()   // («/», «/home»… valdrían para todo)
        return listOf("$c/", "z:" + c.replace('/', '\\').lowercase() + "\\")
    }

    /** ¿Hay algún proceso de esta carpeta? */
    fun hayDe(carpeta: String, lineas: List<String>): Boolean {
        val f = formas(carpeta)
        if (f.isEmpty()) return false
        return lineas.any { l -> l.contains(f[0]) || l.lowercase().contains(f[1]) }
    }
}
