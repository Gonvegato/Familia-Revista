package com.gonzalo.iptv.futbol

import java.io.File
import java.security.MessageDigest

/**
 * Lo que se ha pedido de fútbol, guardado en el disco tal cual (el JSON de la fuente), para no pedirlo de más y para que haya algo que
 * enseñar sin red. Cada dirección es un archivo (`<hash>.json`) y su hora es la del archivo. [texto] devuelve lo guardado si tiene menos
 * de `edad` ms; si no, lo pide con [pedir] y lo guarda; si eso falla, devuelve lo guardado aunque sea viejo (y si no hay nada, el fallo).
 */
class CacheFutbol(private val dir: File, private val pedir: (String) -> String, private val reloj: () -> Long = System::currentTimeMillis) {
    private fun archivo(url: String): File {
        val h = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(24)
        return File(dir, "$h.json")
    }

    /** Lo guardado de [url] (o null) y su hora. */
    fun guardado(url: String): Pair<String, Long>? = archivo(url).takeIf { it.isFile }?.let { f -> runCatching { f.readText() to f.lastModified() }.getOrNull() }

    fun texto(url: String, edad: Long): String {
        val g = guardado(url)
        if (g != null && edad > 0 && reloj() - g.second < edad) return g.first
        return try {
            val t = pedir(url)
            dir.mkdirs()
            val f = archivo(url)
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeText(t); tmp.setLastModified(reloj())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            f.setLastModified(reloj())
            t
        } catch (e: Exception) {
            g?.first ?: throw e
        }
    }

    /** Borra lo que lleva más de [dias] sin pedirse (las fichas de partidos viejos, los marcadores de otros días). */
    fun limpia(dias: Int = 14) {
        val tope = reloj() - dias * 86_400_000L
        dir.listFiles()?.filter { it.isFile && it.lastModified() < tope }?.forEach { it.delete() }
    }

    fun borraTodo() { dir.listFiles()?.forEach { it.delete() } }
}
