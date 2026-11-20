package com.gonzalo.marfil.pc

import com.gonzalo.familia.pc.KDrive
import com.gonzalo.familia.pc.Nube
import java.io.File

/**
 * Los archivos de los libros en tu nube (Google Drive, con respaldo en kDrive durante la transición), como en Marfil del móvil:
 * cada libro que añades aquí se sube a «Marfil/Libros/<título>/» y en el libro de la cuenta queda dónde está (`nube`: `gd` o `f`, nombre y tamaño).
 */
object NubeLibros {
    var kdrive = KDrive()
    val activo: Boolean get() = Nube.gdriveActivo || kdrive.activo

    private fun seguro(s: String) = s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]+"""), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(80).trim().ifBlank { "Sin nombre" }

    /** Sube el archivo de un libro que aún no está en tu nube. Bloquea (red). */
    fun sube(l: Libro, f: File, progreso: (Long) -> Unit = {}): EnNube {
        val ext = (l.archivo.substringAfterLast('.', "").ifBlank { f.extension }).lowercase().take(5).ifBlank { "epub" }
        val nombre = "${seguro(l.titulo)}.$ext"
        val ruta = "Lyra/Libros/${seguro(l.titulo)}"
        val g = Nube.gdrive()
        val x = if (g.activo) {
            val gdId = g.sube(ruta, nombre, f.length(), progreso) { f.inputStream() }
            EnNube(l.nube?.f ?: 0L, nombre, f.length(), gdId)
        } else {
            val id = kdrive.sube(ruta, nombre, f.length(), progreso) { f.inputStream() }
            EnNube(id, nombre, f.length())
        }
        val mapa = LinkedHashMap<String, Any>().apply {
            if (x.f > 0) put("f", x.f)
            if (x.gd.isNotBlank()) put("gd", x.gd)
            put("n", x.n)
            put("s", x.s)
        }
        Libros.cambia(l.id, mapOf("nube" to mapa))
        return x
    }

    /** Baja de tu nube (Google Drive o kDrive) el archivo de un libro a la carpeta de Marfil de este ordenador. Bloquea (red). */
    fun baja(l: Libro, progreso: (Long, Long) -> Unit = { _, _ -> }): File {
        val x = l.nube ?: error("Este libro no está en tu nube todavía (súbelo desde el móvil donde lo tienes).")
        val ext = x.n.substringAfterLast('.', "").lowercase().ifBlank { l.formato.ifBlank { "epub" } }
        val destino = File(Libros.carpeta(), "${l.id}.$ext")
        if (x.gd.isNotBlank() && Nube.gdriveActivo) {
            Nube.gdrive().baja(x.gd, destino, progreso)
        } else if (x.f > 0 && kdrive.activo) {
            kdrive.baja(x.f, destino, progreso)
        } else {
            Nube.baja(x.gd, x.f, destino, progreso)
        }
        Libros.ponArchivo(l.id, destino)
        return destino
    }

    /** Lo que hay aquí y no en tu nube: se sube (en segundo plano, al arrancar y al añadir). Devuelve cuántos ha subido. */
    fun subePendientes(): Int {
        if (!activo) return 0
        var n = 0
        for (l in Libros.todos()) if (l.nube == null) Libros.archivo(l.id)?.let { f -> runCatching { sube(l, f) }.onSuccess { n++ } }
        return n
    }
}
