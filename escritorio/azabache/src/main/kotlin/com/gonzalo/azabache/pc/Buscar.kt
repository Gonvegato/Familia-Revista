package com.gonzalo.azabache.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** Lo que sale al buscar en internet (un juego, un libro, una película o una serie). */
class Hallazgo(val id: Long, val nombre: String, val sub: String, val portada: String = "", val paginas: Int = 0, val creador: String = "")

/**
 * Buscar para añadir, en los mismos sitios que el Azabache del móvil: los juegos en SteamGridDB (con tu clave `steamgriddb`), los libros
 * en Open Library, las películas en TMDB (clave `tmdb`) y las series en TVmaze. Las claves son las de la cuenta.
 */
object Buscar {
    private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8")

    private fun get(url: String, bearer: String = ""): Any? {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Azabache/1.0 (app personal; escritorio)")
        c.setRequestProperty("Accept", "application/json")
        if (bearer.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $bearer")
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        try {
            val code = c.responseCode
            if (code == 401) throw IllegalArgumentException("La clave no es válida (revísala en Ajustes → Servicios).")
            if (code == 429) throw IllegalStateException("Demasiadas búsquedas seguidas: espera un momento.")
            if (code !in 200..299) throw IllegalStateException("El servidor respondió $code.")
            return Json.lee(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    }

    /** Busca en el sitio que toca a esa categoría. */
    fun en(k: Kind, q: String): List<Hallazgo> = when {
        k.id == GAMES.id -> juegos(q)
        k.id == BOOKS.id -> libros(q)
        k.isMovie -> peliculas(q)
        k.isTv -> series(q)
        else -> emptyList()
    }

    fun juegos(q: String): List<Hallazgo> {
        val key = LaFamilia.clave("steamgriddb").ifBlank { throw IllegalArgumentException("Pon tu clave de SteamGridDB en Ajustes → Servicios para buscar juegos.") }
        val term = enc(q).replace("+", "%20")
        return get("https://www.steamgriddb.com/api/v2/search/autocomplete/$term", key).comoMapa()["data"].comoLista().map { it.comoMapa() }.map { o ->
            val rel = o["release_date"].comoLong()
            val ano = if (rel > 0L) java.time.Instant.ofEpochSecond(rel).atZone(java.time.ZoneOffset.UTC).year.toString() else ""
            Hallazgo(o["id"].comoLong(), o["name"].comoTexto(), ano)
        }
    }

    /** Las portadas (miniatura, grande) de un juego de SteamGridDB, en la proporción de las cajas (600×900). */
    fun portadasJuego(id: Long): List<Pair<String, String>> {
        val key = LaFamilia.clave("steamgriddb").ifBlank { return emptyList() }
        return get("https://www.steamgriddb.com/api/v2/grids/game/$id?dimensions=600x900", key).comoMapa()["data"].comoLista().map { it.comoMapa() }
            .map { it["thumb"].comoTexto() to it["url"].comoTexto() }.filter { it.second.isNotBlank() }
    }

    fun libros(q: String): List<Hallazgo> {
        val r = get("https://openlibrary.org/search.json?q=${enc(q)}&fields=key,title,author_name,first_publish_year,cover_i,number_of_pages_median&limit=20").comoMapa()
        return r["docs"].comoLista().map { it.comoMapa() }.mapNotNull { o ->
            val id = o["key"].comoTexto().filter { it.isDigit() }.toLongOrNull() ?: return@mapNotNull null
            val autor = o["author_name"].comoLista().firstOrNull().comoTexto()
            val ano = o["first_publish_year"]?.comoLong()?.takeIf { it > 0 }?.toString().orEmpty()
            val ci = o["cover_i"].comoLong(); val pags = o["number_of_pages_median"].comoLong().toInt()
            Hallazgo(id, o["title"].comoTexto(), listOf(autor, ano, if (pags > 0) "$pags págs." else "").filter { it.isNotBlank() }.joinToString(" · "),
                if (ci > 0) "https://covers.openlibrary.org/b/id/$ci-L.jpg" else "", pags, autor)
        }
    }

    fun peliculas(q: String): List<Hallazgo> {
        val k = LaFamilia.clave("tmdb").trim().ifBlank { throw IllegalArgumentException("Pon tu clave de TMDB en Ajustes → Servicios para buscar películas.") }
        val token = k.startsWith("eyJ")
        val url = "https://api.themoviedb.org/3/search/movie?query=${enc(q)}&language=es-ES&include_adult=false" + if (token) "" else "&api_key=${enc(k)}"
        return get(url, if (token) k else "").comoMapa()["results"].comoLista().map { it.comoMapa() }.mapNotNull { o ->
            val id = o["id"].comoLong().takeIf { it > 0 } ?: return@mapNotNull null
            val titulo = o["title"].comoTexto().ifBlank { o["original_title"].comoTexto() }
            val orig = o["original_title"].comoTexto()
            val poster = o["poster_path"].comoTexto().takeIf { it.startsWith("/") }
            Hallazgo(id, titulo, listOf(o["release_date"].comoTexto().take(4), if (orig.isNotBlank() && normName(orig) != normName(titulo)) orig else "").filter { it.isNotBlank() }.joinToString(" · "),
                if (poster != null) "https://image.tmdb.org/t/p/w500$poster" else "")
        }
    }

    fun series(q: String): List<Hallazgo> =
        get("https://api.tvmaze.com/search/shows?q=${enc(q)}").comoLista().mapNotNull { it.comoMapa()["show"]?.comoMapa() }.mapNotNull { s ->
            val id = s["id"].comoLong().takeIf { it > 0 } ?: return@mapNotNull null
            val red = (s["network"] ?: s["webChannel"]).comoMapa()["name"].comoTexto()
            Hallazgo(id, s["name"].comoTexto(), listOf(s["premiered"].comoTexto().take(4), red).filter { it.isNotBlank() }.joinToString(" · "), s["image"].comoMapa()["original"].comoTexto())
        }

    // ---------------------------------------------------------------- imágenes para las casillas de la maqueta (las mismas fuentes que el móvil)

    /** Un número de fuera (SteamGridDB, TMDB, TVmaze, Open Library): lo de antes de 100.000 millones (los hechos a mano son más grandes). */
    fun esDeInternet(id: Long) = id in 1 until 100_000_000_000L

    /** Los tipos de imagen de una categoría (como `MagImages.categories` del móvil). */
    fun tiposDeImagen(k: Kind): List<String> = when {
        k.isGame -> listOf("Portada", "Fondo", "Logo")
        k.isMovie || k.isTv -> listOf("Póster", "Fondo")
        k.isBook -> listOf("Portada")
        else -> emptyList()
    }

    private fun sgdb(ruta: String): List<Pair<String, String>> {
        val key = LaFamilia.clave("steamgriddb").ifBlank { throw IllegalArgumentException("Pon tu clave de SteamGridDB en Ajustes → Servicios para ver las imágenes de juegos.") }
        return get("https://www.steamgriddb.com/api/v2$ruta", key).comoMapa()["data"].comoLista().map { it.comoMapa() }
            .map { it["thumb"].comoTexto().ifBlank { it["url"].comoTexto() } to it["url"].comoTexto() }.filter { it.second.isNotBlank() }
    }

    private fun tmdb(ruta: String): Any? {
        val k = LaFamilia.clave("tmdb").trim().ifBlank { throw IllegalArgumentException("Pon tu clave de TMDB en Ajustes → Servicios.") }
        val token = k.startsWith("eyJ")
        val sep = if ('?' in ruta) '&' else '?'
        return get("https://api.themoviedb.org/3$ruta" + if (token) "" else "${sep}api_key=${enc(k)}", if (token) k else "")
    }

    /** Las imágenes (miniatura, grande) de una ficha para una casilla: [tipo], uno de [tiposDeImagen]. Bloquea. */
    fun imagenes(k: Kind, f: Ficha, tipo: String): List<Pair<String, String>> {
        val fondo = tipo == "Fondo"
        return when {
            k.isGame -> {
                val ids = listOfNotNull(f.id.takeIf { esDeInternet(it) }) + juegos(f.nombre).let { h -> (h.firstOrNull { normName(it.nombre) == normName(f.nombre) } ?: h.firstOrNull())?.id }.let { listOfNotNull(it) }
                ids.distinct().firstNotNullOfOrNull { id ->
                    runCatching { sgdb(when (tipo) { "Fondo" -> "/heroes/game/$id"; "Logo" -> "/logos/game/$id"; else -> "/grids/game/$id?dimensions=600x900" }) }.getOrNull()?.takeIf { it.isNotEmpty() }
                }.orEmpty()
            }
            k.isMovie -> {
                val id = f.id.takeIf { esDeInternet(it) } ?: peliculas(f.nombre).firstOrNull()?.id ?: return emptyList()
                tmdb("/movie/$id/images?include_image_language=es,en,null").comoMapa()[if (fondo) "backdrops" else "posters"].comoLista().map { it.comoMapa() }
                    .sortedBy { if (it["iso_639_1"].comoTexto() == "es") 0 else 1 }
                    .mapNotNull { it["file_path"].comoTexto().takeIf { p -> p.startsWith("/") } }.take(30)
                    .map { p -> if (fondo) "https://image.tmdb.org/t/p/w780$p" to "https://image.tmdb.org/t/p/w1280$p" else "https://image.tmdb.org/t/p/w342$p" to "https://image.tmdb.org/t/p/w500$p" }
            }
            k.isTv -> {
                val id = f.id.takeIf { esDeInternet(it) } ?: series(f.nombre).firstOrNull()?.id ?: return emptyList()
                get("https://api.tvmaze.com/shows/$id/images").comoLista().map { it.comoMapa() }
                    .filter { it["type"].comoTexto() == if (fondo) "background" else "poster" }
                    .sortedByDescending { it["main"] == true }
                    .mapNotNull { o ->
                        val r = o["resolutions"].comoMapa()
                        val grande = r["original"].comoMapa()["url"].comoTexto().ifBlank { return@mapNotNull null }
                        r["medium"].comoMapa()["url"].comoTexto().ifBlank { grande } to grande
                    }
            }
            k.isBook -> {
                val deLaObra = if (esDeInternet(f.id)) runCatching {
                    get("https://openlibrary.org/works/OL${f.id}W.json").comoMapa()["covers"].comoLista().map { it.comoLong() }.filter { it > 0 }.take(18)
                        .map { "https://covers.openlibrary.org/b/id/$it-M.jpg" to "https://covers.openlibrary.org/b/id/$it-L.jpg" }
                }.getOrDefault(emptyList()) else emptyList()
                deLaObra.ifEmpty {
                    libros(listOf(f.nombre, f.creador).filter { it.isNotBlank() }.joinToString(" ")).mapNotNull { h -> h.portada.takeIf { it.isNotBlank() } }.distinct()
                        .map { it.replace("-L.jpg", "-M.jpg") to it }
                }
            }
            else -> emptyList()
        }
    }

    /** Baja una imagen de internet (para ponerla de portada). */
    fun bytes(url: String): ByteArray {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Azabache/1.0 (app personal; escritorio)")
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("La imagen no se ha podido bajar (${c.responseCode}).")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }
}
