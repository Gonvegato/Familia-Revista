package com.gonzalo.familia.pc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/** Las imágenes de internet que se enseñan (portadas de una búsqueda, de tus categorías): se guardan en la caché de la app. */
object ImagenWeb {
    private val memoria = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 200
    }

    private fun archivo(url: String): File {
        val h = java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(Sitio.de(LaFamilia.app.ifBlank { "familia" }), "cache/web"), h)
    }

    fun enMemoria(url: String): ImageBitmap? = synchronized(memoria) { memoria[url] }

    /** Bloquea (red la primera vez). */
    fun trae(url: String): ImageBitmap? {
        if (!url.startsWith("http")) return null
        enMemoria(url)?.let { return it }
        val f = archivo(url)
        val bytes = f.takeIf { it.isFile }?.readBytes() ?: runCatching {
            val c = URI(url).toURL().openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "Familia/1.0 (app personal; escritorio)")
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.inputStream.use { it.readBytes() }.takeIf { Portadas.esImagen(it) }?.also { b -> f.parentFile.mkdirs(); f.writeBytes(b) }
        }.getOrNull() ?: return null
        return Portadas.decodifica(bytes)?.also { synchronized(memoria) { memoria[url] = it } }
    }
}

/** Una imagen de internet para pintarla (llega sola). */
@Composable
fun rememberImagenWeb(url: String): ImageBitmap? {
    var img by remember(url) { mutableStateOf(ImagenWeb.enMemoria(url)) }
    LaunchedEffect(url) { if (img == null && url.startsWith("http")) img = withContext(Dispatchers.IO) { ImagenWeb.trae(url) } }
    return img
}
