package com.gonzalo.familia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Las portadas de la familia: una imagen de la cuenta (`familia_img/<hash>`), la misma en todas las apps y dispositivos. Aquí se
 * prepara (reducida a 600×900 como mucho, la proporción 2:3 de una caja o una tapa, en JPEG: unos 60–120 KB) y se sabe dónde está
 * en el móvil.
 */
object PortadasFamilia {
    const val ANCHO = 600
    const val ALTO = 900

    /** ¿Empiezan estos bytes como una imagen de verdad (JPEG, PNG, GIF o WebP)? Una página de error con código 200 no vale. */
    fun esImagen(b: ByteArray): Boolean {
        if (b.size < 12) return false
        fun u(i: Int) = b[i].toInt() and 0xFF
        return (u(0) == 0xFF && u(1) == 0xD8) || (u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) ||
            (u(0) == 'G'.code && u(1) == 'I'.code && u(2) == 'F'.code) || (u(0) == 'R'.code && u(1) == 'I'.code && u(2) == 'F'.code && u(3) == 'F'.code && u(8) == 'W'.code && u(9) == 'E'.code)
    }

    /** La imagen reducida para la cuenta, o null si no es una imagen. */
    fun reducir(bytes: ByteArray): ByteArray? {
        if (!esImagen(bytes)) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1; while (o.outWidth / (s * 2) >= ANCHO && o.outHeight / (s * 2) >= ALTO) s *= 2
        val b0 = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val k = minOf(1f, ANCHO.toFloat() / b0.width, ALTO.toFloat() / b0.height)
        val b = if (k < 1f) Bitmap.createScaledBitmap(b0, (b0.width * k).toInt().coerceAtLeast(1), (b0.height * k).toInt().coerceAtLeast(1), true) else b0
        return java.io.ByteArrayOutputStream().use { out -> b.compress(Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
    }

    /**
     * Un fondo listo para la cuenta: a lo ancho, como mucho 1600×1000 (unos 150–300 KB en JPEG), el mismo tamaño que usan Seshat, Bastet
     * y Hathor. null si no es una imagen.
     */
    fun reducirFondo(bytes: ByteArray): ByteArray? {
        if (!esImagen(bytes)) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1; while (o.outWidth / (s * 2) >= 1600 || o.outHeight / (s * 2) >= 1000) s *= 2
        val b0 = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val k = minOf(1f, 1600f / b0.width, 1000f / b0.height)
        val b = if (k < 1f) Bitmap.createScaledBitmap(b0, (b0.width * k).toInt().coerceAtLeast(1), (b0.height * k).toInt().coerceAtLeast(1), true) else b0
        return java.io.ByteArrayOutputStream().use { out -> b.compress(Bitmap.CompressFormat.JPEG, 84, out); out.toByteArray() }
    }

    /** Guarda una imagen como fondo en la cuenta y devuelve su hash; null si no es una imagen. */
    fun guardarFondo(bytes: ByteArray): String? = reducirFondo(bytes)?.let { LaFamilia.familia.imagenes.guarda(it, "image/jpeg") }

    /** «Sin fondo, a propósito»: lo que se escribe en la cuenta cuando quitas el fondo (en todas las apps de la familia). */
    const val SIN_FONDO = "-"

    /** Guarda una imagen como portada (reducida) en la cuenta y devuelve su hash; null si no es una imagen. */
    fun guardar(bytes: ByteArray): String? = reducir(bytes)?.let { LaFamilia.familia.imagenes.guarda(it, "image/jpeg") }

    /** El archivo de una portada en este móvil ("" si aún no está: [baja] la trae). */
    fun archivo(c: Context, hash: String): String = if (hash.isBlank()) "" else File(File(c.filesDir, "familia"), "img/$hash").takeIf { it.isFile }?.absolutePath.orEmpty()

    private val bajando = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Trae (en segundo plano) las portadas que aún no están en el móvil; [alLlegar] cuando ha llegado alguna. */
    fun baja(hashes: Collection<String>, deUid: String? = null, alLlegar: () -> Unit) {
        val faltan = hashes.filter { it.isNotBlank() && bajando.add(it) }
        if (faltan.isEmpty()) return
        kotlin.concurrent.thread(name = "familia-portadas") {
            var alguna = false
            try { for (h in faltan) if ((if (deUid != null) LaFamilia.familia.imagenes.baja(h, deUid) else LaFamilia.familia.imagenes.baja(h)) != null) alguna = true }
            finally { bajando.removeAll(faltan.toSet()) }
            if (alguna) android.os.Handler(android.os.Looper.getMainLooper()).post(alLlegar)
        }
    }
}
