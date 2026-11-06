package com.gonzalo.hiedra.pc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Portadas
import com.gonzalo.familia.pc.Sitio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Las imágenes de Hiedra en la pantalla: las portadas (de la cuenta, de internet o de un archivo de este ordenador, como las de Lutris) y los
 * fondos grandes de la sala. Se guardan ya reducidas, en memoria (las últimas) y en la caché de la app, para que una biblioteca de cientos
 * de juegos pase rápida y sin llenar la memoria (un fondo de Steam entero son 3840×1240).
 */
object Imagenes {
    /** El ancho al que se reducen las portadas (en la tele, a 1080 líneas, una portada mide unos 200 puntos) y los fondos. */
    const val ANCHO_PORTADA = 400
    const val ANCHO_FONDO = 1920

    private class Lru<V>(val max: Int) : LinkedHashMap<String, V>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?) = size > max
    }
    private val portadas = Lru<ImageBitmap>(260)
    private val fondos = Lru<ImageBitmap>(10)
    private val fallidas = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun dir(que: String) = File(Sitio.de(LaFamilia.app.ifBlank { "hiedra" }), "cache/$que").also { it.mkdirs() }
    private fun sha(s: String) = java.security.MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun enMemoria(clave: String, fondo: Boolean = false): ImageBitmap? = if (fondo) synchronized(fondos) { fondos[clave] } else synchronized(portadas) { portadas[clave] }

    /** Reduce una imagen a [ancho] (si es más ancha): JPEG para guardarla y la imagen para pintarla. */
    private fun reduce(b: ByteArray, ancho: Int): Pair<ByteArray?, ImageBitmap>? {
        if (!Portadas.esImagen(b)) return null
        val img = runCatching { Image.makeFromEncoded(b) }.getOrNull() ?: return null
        if (img.width <= 0 || img.height <= 0) return null
        if (img.width <= ancho) return null to img.toComposeImageBitmap()
        val w = ancho; val h = (img.height * ancho.toFloat() / img.width).toInt().coerceAtLeast(1)
        val s = Surface.makeRasterN32Premul(w, h)
        s.canvas.drawImageRect(img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()), FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), Paint(), true)
        val r = s.makeImageSnapshot()
        return r.encodeToData(EncodedImageFormat.JPEG, 88)?.bytes to r.toComposeImageBitmap()
    }

    /** Baja una imagen (null si no hay red o no está; si el servidor dice que no existe, no se vuelve a pedir en esta sesión). */
    private fun baja(url: String): ByteArray? = runCatching {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Hiedra/1.1 (app personal; escritorio)")
        c.connectTimeout = 12_000; c.readTimeout = 25_000
        try {
            val code = c.responseCode
            if (code == 404 || code == 410 || code == 403) fallidas += url
            if (code !in 200..299) null else c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }.getOrNull()

    /**
     * La imagen de [fuente] (un hash de la cuenta, una dirección de internet o un `file:` de este ordenador), reducida. Bloquea (la red, la
     * primera vez); null si no hay.
     */
    fun trae(fuente: String, fondo: Boolean = false): ImageBitmap? {
        if (fuente.isBlank() || fuente in fallidas) return null
        enMemoria(fuente, fondo)?.let { return it }
        val ancho = if (fondo) ANCHO_FONDO else ANCHO_PORTADA
        val cache = File(dir(if (fondo) "fondos" else "portadas"), sha(fuente) + ".jpg")
        val guardada = cache.takeIf { it.isFile }?.readBytes()?.let { b -> runCatching { Image.makeFromEncoded(b).toComposeImageBitmap() }.getOrNull() }
        val bmp = guardada ?: run {
            val bytes = when {
                fuente.startsWith("http") -> baja(fuente)
                fuente.startsWith("file:") -> runCatching { File(URI(fuente)).readBytes() }.getOrNull()
                else -> runCatching { LaFamilia.familia.imagenes.local(fuente) ?: LaFamilia.familia.imagenes.baja(fuente) }.getOrNull()
            }
            val r = bytes?.let { reduce(it, ancho) }
            if (r == null) { if (fuente.startsWith("http") && bytes != null) fallidas += fuente; return null }
            (r.first ?: bytes).let { runCatching { cache.writeBytes(it) } }
            r.second
        }
        if (fondo) synchronized(fondos) { fondos[fuente] = bmp } else synchronized(portadas) { portadas[fuente] = bmp }
        return bmp
    }

    /** Pocas a la vez (una biblioteca entera pidiendo portadas no debe ahogar la red ni el procesador). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val hilo = Dispatchers.IO.limitedParallelism(6)
}

/** La primera imagen que haya de [fuentes] (por orden: tu portada de la cuenta, la de internet…), para pintarla: llega sola. */
@Composable
fun rememberImagen(vararg fuentes: String, fondo: Boolean = false): ImageBitmap? {
    val l = fuentes.filter { it.isNotBlank() }
    val clave = l.joinToString("|")
    var img by remember(clave) { mutableStateOf(l.firstNotNullOfOrNull { Imagenes.enMemoria(it, fondo) }) }
    LaunchedEffect(clave, if (l.any { !it.startsWith("http") && !it.startsWith("file:") }) LaFamilia.cambios else 0) {
        if (img == null) img = withContext(Imagenes.hilo) { l.firstNotNullOfOrNull { Imagenes.trae(it, fondo) } }
    }
    return img
}
