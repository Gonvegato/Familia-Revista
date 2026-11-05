package com.gonzalo.marfil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Una portada que se ha encontrado en internet: una miniatura para elegir y la imagen grande para guardar. */
class Candidata(val miniatura: String, val grande: String, val fuente: String, val titulo: String)

/**
 * Las portadas: elegir una imagen del móvil, buscarla en internet (iTunes Libros y Open Library; ambas sin clave) o usar la del propio libro.
 * La que se elige se guarda en el móvil (con un nombre nuevo, para que se vea al momento) y, si tienes kDrive, se sube para que salga en tus otros dispositivos.
 */
object Portadas {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String, ms: Int = 12000): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = ms; c.readTimeout = ms; c.setRequestProperty("User-Agent", "Marfil/1 (lector de libros personal)")
        if (c.responseCode !in 200..299) return null
        c.inputStream.use { it.readBytes().decodeToString() }
    }.getOrNull()

    /** iTunes: {"results":[{"trackName","artistName","artworkUrl100"}]} → la misma imagen en pequeño y en grande. */
    fun deItunes(json: String): List<Candidata> = runCatching {
        val a = JSONObject(json).optJSONArray("results") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val u = o.optString("artworkUrl100").takeIf { it.contains("100x100") } ?: return@mapNotNull null
            Candidata(u.replace("100x100", "240x240"), u.replace("100x100", "800x800"), "iTunes", listOf(o.optString("trackName"), o.optString("artistName")).filter { it.isNotBlank() }.joinToString(" · "))
        }
    }.getOrDefault(emptyList())

    /** Open Library: {"docs":[{"title","author_name":[…],"cover_i":123}]} → la portada por su número. */
    fun deOpenLibrary(json: String): List<Candidata> = runCatching {
        val a = JSONObject(json).optJSONArray("docs") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("cover_i", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val autor = o.optJSONArray("author_name")?.optString(0).orEmpty()
            Candidata("https://covers.openlibrary.org/b/id/$id-M.jpg", "https://covers.openlibrary.org/b/id/$id-L.jpg", "Open Library", listOf(o.optString("title"), autor).filter { it.isNotBlank() }.joinToString(" · "))
        }
    }.getOrDefault(emptyList())

    /** Busca portadas por título y autor (o por lo que escribas). Bloqueante (red). Primero las de iTunes (en español), luego las de Open Library. */
    fun buscar(titulo: String, autor: String = ""): List<Candidata> {
        val q = (titulo + " " + autor).trim()
        if (q.isBlank()) return emptyList()
        val it = get("https://itunes.apple.com/search?term=${enc(q)}&entity=ebook&limit=14&country=es")?.let { deItunes(it) }.orEmpty()
        val ol = get("https://openlibrary.org/search.json?q=${enc(q)}&limit=14&fields=title,author_name,cover_i")?.let { deOpenLibrary(it) }.orEmpty()
        return (it + ol).distinctBy { it.grande }
    }

    /** Baja una imagen (como mucho 8 MB). Bloqueante (red). */
    fun descargar(url: String): ByteArray? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000; c.setRequestProperty("User-Agent", "Marfil/1")
        if (c.responseCode !in 200..299) return null
        c.inputStream.use { i -> val b = i.readBytes(); b.takeIf { it.size in 1000..8_000_000 } }
    }.getOrNull()

    /** ¿Empiezan estos bytes como una imagen de verdad (JPEG, PNG, GIF o WebP)? Una página de error con código 200 no vale. */
    fun esImagen(b: ByteArray): Boolean {
        if (b.size < 12) return false
        fun u(i: Int) = b[i].toInt() and 0xFF
        return (u(0) == 0xFF && u(1) == 0xD8) || (u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) ||
            (u(0) == 'G'.code && u(1) == 'I'.code && u(2) == 'F'.code) || (u(0) == 'R'.code && u(1) == 'I'.code && u(2) == 'F'.code && u(3) == 'F'.code && u(8) == 'W'.code && u(9) == 'E'.code)
    }

    /**
     * Una imagen lista para la cuenta: reducida a 600×900 como mucho (la proporción de una tapa; unos 60–120 KB en JPEG), para
     * que pese poco y se vea igual en todas partes. null si no es una imagen.
     */
    fun reducir(bytes: ByteArray): ByteArray? {
        if (!esImagen(bytes)) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1; while (o.outWidth / (s * 2) >= 600 && o.outHeight / (s * 2) >= 900) s *= 2
        val b0 = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val k = minOf(1f, 600f / b0.width, 900f / b0.height)
        val b = if (k < 1f) Bitmap.createScaledBitmap(b0, (b0.width * k).toInt().coerceAtLeast(1), (b0.height * k).toInt().coerceAtLeast(1), true) else b0
        return java.io.ByteArrayOutputStream().use { out -> b.compress(Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
    }

    /**
     * Pone una imagen como portada de un libro: se guarda en la cuenta (por su contenido) y el libro apunta a ella, así que sale igual
     * en tus otros dispositivos y en Azabache. Devuelve el libro con su portada, o null si la imagen no vale.
     */
    fun poner(ctx: Context, l: Libro, bytes: ByteArray, url: String = ""): Libro? {
        val r = reducir(bytes) ?: return null
        val hash = com.gonzalo.familia.LaFamilia.familia.imagenes.guarda(r, "image/jpeg")
        Biblioteca.ponerPortada(l.id, hash, url)
        return Biblioteca.por(l.id) ?: l.copy(portada = Biblioteca.archivoPortada(ctx, hash), portadaUrl = url)
    }

    /** Quita la portada elegida: el libro vuelve a su tapa de color con la letra (también en tus otros dispositivos y en Azabache). */
    fun quitar(ctx: Context, l: Libro): Libro {
        Biblioteca.ponerPortada(l.id, "", "")
        return Biblioteca.por(l.id) ?: l.copy(portada = "", portadaUrl = "")
    }

    private val bajando = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Las portadas de la cuenta que aún no están en este móvil se bajan (en segundo plano) y el libro las enseña en cuanto llegan. */
    fun bajarQueFalten(ctx: Context) {
        val faltan = Biblioteca.propios().filter { it.portada.isBlank() }.mapNotNull { l -> Biblioteca.hashPortada(l.id).takeIf { it.isNotBlank() } }
        if (faltan.isEmpty() || !bajando.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        kotlin.concurrent.thread(name = "marfil-portadas") {
            var alguna = false
            try { for (h in faltan) if (com.gonzalo.familia.LaFamilia.familia.imagenes.baja(h) != null) alguna = true }
            finally { bajando.set(false) }
            if (alguna) android.os.Handler(android.os.Looper.getMainLooper()).post { Biblioteca.refresca() }
        }
    }

    /** Pone la portada que lleva el propio archivo del libro, si la lleva (solo los EPUB; solo si lo pides tú desde «Portada»). */
    fun delLibro(ctx: Context, l: Libro): Libro? {
        val f = runCatching { File(android.net.Uri.parse(l.uri).path ?: return null) }.getOrNull()?.takeIf { it.exists() } ?: return null
        val bytes = Metadatos.leer(f, l.archivo.ifBlank { f.name }).portada ?: return null
        return poner(ctx, l, bytes)
    }

    /** Las portadas con una dirección de internet (las que trajo Atrio) se guardan en el móvil para que no dependan de la red ni de que esa web siga ahí. */
    fun cachear(ctx: Context, l: Libro): Libro? {
        if (!l.portada.startsWith("http")) return null
        val bytes = descargar(l.portada) ?: return null
        return poner(ctx, l, bytes, url = l.portada)
    }
}
