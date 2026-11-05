package com.gonzalo.familia.pc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * Las portadas de la familia: una imagen de la cuenta (`familia_img/<hash>`), la misma en todas las apps y en todos los
 * dispositivos. Aquí se prepara como en Android (como mucho 600×900, en JPEG 85) y se guarda por el mismo hash (los 40 primeros
 * hexadecimales del SHA-256 de los bytes): una portada puesta en el ordenador es la misma imagen en el móvil, y al revés.
 */
object Portadas {
    const val ANCHO = 600
    const val ALTO = 900

    /** ¿Empiezan estos bytes como una imagen de verdad (JPEG, PNG, GIF o WebP)? Una página de error con código 200 no vale. */
    fun esImagen(b: ByteArray): Boolean {
        if (b.size < 12) return false
        fun u(i: Int) = b[i].toInt() and 0xFF
        return (u(0) == 0xFF && u(1) == 0xD8) || (u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) ||
            (u(0) == 'G'.code && u(1) == 'I'.code && u(2) == 'F'.code) || (u(0) == 'R'.code && u(1) == 'I'.code && u(2) == 'F'.code && u(3) == 'F'.code && u(8) == 'W'.code && u(9) == 'E'.code)
    }

    /** La imagen reducida para la cuenta (JPEG), o null si no es una imagen. */
    fun reducir(bytes: ByteArray): ByteArray? {
        if (!esImagen(bytes)) return null
        val img = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        if (img.width <= 0 || img.height <= 0) return null
        val k = minOf(1f, ANCHO.toFloat() / img.width, ALTO.toFloat() / img.height)
        val w = (img.width * k).toInt().coerceAtLeast(1); val h = (img.height * k).toInt().coerceAtLeast(1)
        val s = Surface.makeRasterN32Premul(w, h)
        // (un JPEG no tiene transparencia: lo transparente sale blanco)
        s.canvas.clear(0xFFFFFFFF.toInt())
        s.canvas.drawImageRect(img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), Paint(), true)
        return s.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 85)?.bytes
    }

    /** Guarda una imagen como portada (reducida) en la cuenta y devuelve su hash; null si no es una imagen. */
    fun guardar(bytes: ByteArray): String? = reducir(bytes)?.let { LaFamilia.familia.imagenes.guarda(it, "image/jpeg") }

    private val cache = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 300
    }

    /** La imagen ya preparada para pintar, si está en memoria o en el ordenador (sin red). */
    fun enMemoria(hash: String): ImageBitmap? {
        if (hash.isBlank()) return null
        synchronized(cache) { cache[hash]?.let { return it } }
        val b = runCatching { LaFamilia.familia.imagenes.local(hash) }.getOrNull() ?: return null
        return decodifica(b)?.also { synchronized(cache) { cache[hash] = it } }
    }

    fun decodifica(b: ByteArray): ImageBitmap? = runCatching { Image.makeFromEncoded(b).toComposeImageBitmap() }.getOrNull()

    /** La imagen: del ordenador o de la cuenta (la tuya o la de tu pareja). Bloquea; null si no hay red o no existe. */
    fun trae(hash: String, deUid: String? = null): ImageBitmap? {
        enMemoria(hash)?.let { return it }
        val b = runCatching { if (deUid != null) LaFamilia.familia.imagenes.baja(hash, deUid) else LaFamilia.familia.imagenes.baja(hash) }.getOrNull() ?: return null
        return decodifica(b)?.also { synchronized(cache) { cache[hash] = it } }
    }
}

/** La portada de un hash para pintarla: al momento si ya está; si no, llega sola de la cuenta. */
@Composable
fun rememberPortada(hash: String, deUid: String? = null): ImageBitmap? {
    var img by remember(hash) { mutableStateOf(Portadas.enMemoria(hash)) }
    LaunchedEffect(hash, LaFamilia.cambios) {
        if (img == null && hash.isNotBlank()) img = withContext(Dispatchers.IO) { Portadas.trae(hash, deUid) }
    }
    return img
}
