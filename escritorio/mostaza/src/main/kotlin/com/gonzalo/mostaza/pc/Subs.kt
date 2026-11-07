package com.gonzalo.mostaza.pc

import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Los subtítulos de fuera, bajados aquí (en la carpeta solo tuya del reproductor) antes de dárselos a mpv: mpv manda las cabeceras de la
 * fuente a todo lo que pide para ese archivo, y la de tu debrid o tu token de kDrive no tienen que ir a los servidores de los subtítulos.
 * Las cabeceras de la fuente solo van si el subtítulo es del mismo servidor que ella (los .srt de al lado en tu kDrive).
 */
object Subs {
    /** ¿Van las cabeceras de [f] al pedir [url]? Solo al mismo servidor. */
    fun conCabeceras(url: String, f: Fuente?): Boolean {
        if (f == null || f.cabeceras.isEmpty()) return false
        val a = runCatching { URI(url).host }.getOrNull() ?: return false
        val b = runCatching { URI(f.url).host }.getOrNull() ?: return false
        return a.equals(b, ignoreCase = true)
    }

    fun local(s: Subtitulo, f: Fuente?): File? = runCatching {
        if (!s.url.startsWith("http://") && !s.url.startsWith("https://")) return@runCatching File(URI(s.url).path).takeIf { it.isFile }
        val dir = File(Mpv.carpetaSocket(Local.dir), "subs").apply { mkdirs() }
        val ext = s.url.substringBefore('?').substringAfterLast('.', "srt").lowercase().takeIf { it in setOf("srt", "vtt", "ass", "ssa", "sub") } ?: "srt"
        val dest = File(dir, "${Integer.toHexString(s.url.hashCode())}.$ext")
        if (dest.isFile && dest.length() > 0) return@runCatching dest
        val c = URI(s.url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Hathor")
        if (conCabeceras(s.url, f)) f!!.cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (c.responseCode !in 200..299) { c.disconnect(); return@runCatching null }
        val tmp = File(dest.path + ".part")
        c.inputStream.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        c.disconnect()
        if (tmp.length() > 5_000_000) { tmp.delete(); return@runCatching null }
        tmp.renameTo(dest)
        dest
    }.getOrNull()
}
