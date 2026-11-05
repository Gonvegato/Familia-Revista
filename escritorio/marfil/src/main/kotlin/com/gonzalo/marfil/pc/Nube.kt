package com.gonzalo.marfil.pc

import com.gonzalo.familia.pc.KDrive
import java.io.File

/**
 * Los archivos de los libros por tu kDrive, como en Marfil del móvil: cada libro que añades aquí se sube a «Mi carpeta/Marfil/Libros/<título>/»
 * y en el libro de la cuenta queda dónde está (`nube`: id, nombre y tamaño); el que está en tu kDrive y no aquí se baja al abrirlo.
 */
object NubeLibros {
    var kdrive = KDrive()

    private fun seguro(s: String) = s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]+"""), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(80).trim().ifBlank { "Sin nombre" }

    /** Sube el archivo de un libro que aún no está en tu kDrive. Bloquea (red). */
    fun sube(l: Libro, f: File, progreso: (Long) -> Unit = {}): EnNube {
        val ext = (l.archivo.substringAfterLast('.', "").ifBlank { f.extension }).lowercase().take(5).ifBlank { "epub" }
        val nombre = "${seguro(l.titulo)}.$ext"
        val id = kdrive.sube("Marfil/Libros/${seguro(l.titulo)}", nombre, f.length(), progreso) { f.inputStream() }
        val x = EnNube(id, nombre, f.length())
        Libros.cambia(l.id, mapOf("nube" to mapOf("f" to x.f, "n" to x.n, "s" to x.s)))
        return x
    }

    /** Baja de tu kDrive el archivo de un libro a la carpeta de Marfil de este ordenador. Bloquea (red). */
    fun baja(l: Libro, progreso: (Long, Long) -> Unit = { _, _ -> }): File {
        val x = l.nube ?: error("Este libro no está en tu kDrive todavía (súbelo desde el móvil donde lo tienes).")
        val ext = x.n.substringAfterLast('.', "").lowercase().ifBlank { l.formato.ifBlank { "epub" } }
        val destino = File(Libros.carpeta(), "${l.id}.$ext")
        kdrive.baja(x.f, destino, progreso)
        Libros.ponArchivo(l.id, destino)
        return destino
    }

    /** Lo que hay aquí y no en tu kDrive: se sube (en segundo plano, al arrancar y al añadir). Devuelve cuántos ha subido. */
    fun subePendientes(): Int {
        if (!kdrive.activo) return 0
        var n = 0
        for (l in Libros.todos()) if (l.nube == null) Libros.archivo(l.id)?.let { f -> runCatching { sube(l, f) }.onSuccess { n++ } }
        return n
    }
}
