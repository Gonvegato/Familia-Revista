package com.gonzalo.mostaza

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.DiscoCifrado
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Una plataforma donde se puede ver en España (los datos son de JustWatch, a través de TMDB). */
data class Plataforma(val id: Long, val nombre: String, val logo: String = "")

/** Alguien del reparto: su nombre, su papel y su foto. */
data class Persona(val nombre: String, val papel: String = "", val foto: String = "")

/** Lo que TMDB añade a una ficha (además de lo que ya tiene [Meta]). */
data class Extras(
    val lema: String = "", val similares: List<MetaBreve> = emptyList(), val plataformas: List<Plataforma> = emptyList(),
    val personas: List<Persona> = emptyList(), val tmdbTv: Boolean = false,
)

/** Una fila de la portada o de «Explorar»: su título y lo que trae. */
data class FilaTmdb(val titulo: String, val cosas: List<MetaBreve>, val clave: String = titulo)

/**
 * Leer lo que contesta TMDB (sin red: se prueba con respuestas como las de su documentación). Los ids de lo que sale en un catálogo son
 * `tmdb:movie:<id>` o `tmdb:tv:<id>`; al abrir la ficha se pasan al de IMDb (`tt…`), que es el que usan tus addons para las fuentes y
 * el que tiene tu biblioteca. Los episodios llevan el id de Stremio (`tt…:<temporada>:<episodio>`).
 */
object TmdbDatos {
    const val IMG = "https://image.tmdb.org/t/p/"
    const val REGION = "ES"

    /** De la ruta de una imagen de TMDB a su dirección (en las pruebas de pantallas: imágenes hechas aquí). */
    @Volatile var imagen: (String, String) -> String = { path, tam -> IMG + tam + path }
    fun img(path: String, tam: String) = if (path.isBlank()) "" else imagen(path, tam)

    data class IdTmdb(val tv: Boolean, val id: Long) { val ruta: String get() = (if (tv) "tv/" else "movie/") + id }

    fun id(tv: Boolean, id: Long) = "tmdb:${if (tv) "tv" else "movie"}:$id"

    fun deId(s: String): IdTmdb? {
        val p = s.split(':')
        if (p.size != 3 || p[0] != "tmdb" || p[1] !in setOf("movie", "tv")) return null
        return p[2].toLongOrNull()?.takeIf { it > 0 }?.let { IdTmdb(p[1] == "tv", it) }
    }

    fun ano(fecha: String) = fecha.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty()
    fun nota(v: Double) = if (v > 0) String.format(Locale.ROOT, "%.1f", v) else ""

    /** Un resultado de una lista (de películas, de series o de las dos; las personas no). [tvPorDefecto]: si no dice qué es. */
    fun breve(m: Map<String, Any?>, tvPorDefecto: Boolean, generos: Map<Long, String> = emptyMap()): MetaBreve? {
        val tv = when (m["media_type"].comoTexto()) { "tv" -> true; "movie" -> false; "" -> tvPorDefecto; else -> return null }
        val id = m["id"].comoLong(); if (id <= 0) return null
        val poster = m["poster_path"].comoTexto(); if (poster.isBlank()) return null
        val nombre = (if (tv) m["name"] else m["title"]).comoTexto().ifBlank { (if (tv) m["original_name"] else m["original_title"]).comoTexto() }
        if (nombre.isBlank()) return null
        val fecha = (if (tv) m["first_air_date"] else m["release_date"]).comoTexto()
        return MetaBreve(
            id(tv, id), if (tv) "series" else "movie", nombre, img(poster, "w342"), "poster", img(m["backdrop_path"].comoTexto(), "w1280"), ano(fecha),
            m["genre_ids"].comoLista().mapNotNull { generos[it.comoLong()] }, nota(m["vote_average"].comoDouble()), m["overview"].comoTexto(),
        )
    }

    fun breves(json: String, tvPorDefecto: Boolean, generos: Map<Long, String> = emptyMap()): List<MetaBreve> =
        lee(json)["results"].comoLista().mapNotNull { breve(it.comoMapa(), tvPorDefecto, generos) }.distinctBy { it.id }

    fun paginas(json: String): Int = lee(json)["total_pages"].comoLong().toInt()

    fun generos(json: String): Map<Long, String> = lee(json)["genres"].comoLista().associate { g -> g.comoMapa().let { it["id"].comoLong() to it["name"].comoTexto() } }
        .filter { it.key > 0 && it.value.isNotBlank() }

    /** Las plataformas de suscripción de [region], en el orden en que las enseña JustWatch allí. */
    fun plataformas(json: String, region: String = REGION): List<Plataforma> = lee(json)["results"].comoLista().map { it.comoMapa() }
        .sortedBy { p -> p["display_priorities"].comoMapa()[region]?.comoLong() ?: p["display_priority"].comoLong() }
        .mapNotNull { p -> val id = p["provider_id"].comoLong(); if (id <= 0) null else Plataforma(id, p["provider_name"].comoTexto(), img(p["logo_path"].comoTexto(), "w92")) }

    /** Las de [region] en una ficha (`watch/providers`): suscripción, gratis o con anuncios (no alquiler ni compra). */
    fun dondeVer(m: Map<String, Any?>, region: String = REGION): List<Plataforma> {
        val r = m["watch/providers"].comoMapa()["results"].comoMapa()[region].comoMapa()
        return listOf("flatrate", "free", "ads").flatMap { r[it].comoLista() }.map { it.comoMapa() }.sortedBy { it["display_priority"].comoLong() }
            .mapNotNull { p -> val id = p["provider_id"].comoLong(); if (id <= 0) null else Plataforma(id, p["provider_name"].comoTexto(), img(p["logo_path"].comoTexto(), "w92")) }
            .distinctBy { it.id }
    }

    /** Los tráilers de YouTube (su clave), en castellano antes que en inglés y los oficiales antes. */
    fun trailers(m: Map<String, Any?>): List<String> = m["videos"].comoMapa()["results"].comoLista().map { it.comoMapa() }
        .filter { it["site"].comoTexto() == "YouTube" && it["type"].comoTexto() in setOf("Trailer", "Teaser") && it["key"].comoTexto().isNotBlank() }
        .sortedWith(compareBy({ if (it["iso_639_1"].comoTexto() == "es") 0 else 1 }, { if (it["type"].comoTexto() == "Trailer") 0 else 1 }, { if (it["official"] == true) 0 else 1 }))
        .map { it["key"].comoTexto() }.distinct()

    /** La ficha entera (detalles con `external_ids,credits,videos,recommendations,watch/providers` y, en una serie, `season/N`). */
    fun meta(json: String, tv: Boolean): Pair<Meta, Extras>? {
        val m = lee(json)
        val tmdb = m["id"].comoLong(); if (tmdb <= 0) return null
        val imdb = (m["imdb_id"].comoTexto().ifBlank { m["external_ids"].comoMapa()["imdb_id"].comoTexto() }).takeIf { it.startsWith("tt") }.orEmpty()
        val id = imdb.ifBlank { id(tv, tmdb) }
        val nombre = (if (tv) m["name"] else m["title"]).comoTexto().ifBlank { (if (tv) m["original_name"] else m["original_title"]).comoTexto() }
        val fecha = (if (tv) m["first_air_date"] else m["release_date"]).comoTexto()
        val minutos = if (tv) m["episode_run_time"].comoLista().firstOrNull().comoLong() else m["runtime"].comoLong()
        val creditos = m["credits"].comoMapa()
        val reparto = creditos["cast"].comoLista().map { it.comoMapa() }.sortedBy { it["order"].comoLong() }
        val equipo = creditos["crew"].comoLista().map { it.comoMapa() }
        val direccion = if (tv) m["created_by"].comoLista().map { it.comoMapa()["name"].comoTexto() }.filter { it.isNotBlank() }
            else equipo.filter { it["job"].comoTexto() == "Director" }.map { it["name"].comoTexto() }
        val guion = equipo.filter { it["job"].comoTexto() in setOf("Screenplay", "Writer", "Novel") }.map { it["name"].comoTexto() }.distinct()
        val videos = if (!tv) emptyList() else (0..60).flatMap { n -> m["season/$n"].comoMapa()["episodes"].comoLista() }.mapNotNull { e -> episodio(e.comoMapa(), imdb, tmdb) }
            .distinctBy { it.id }
        val meta = Meta(
            id, if (tv) "series" else "movie", nombre, img(m["poster_path"].comoTexto(), "w500"), img(m["backdrop_path"].comoTexto(), "w1280"), "",
            m["overview"].comoTexto(), ano(fecha), Protocolo.fecha(fecha), if (minutos > 0) "$minutos min" else "",
            m["genres"].comoLista().map { it.comoMapa()["name"].comoTexto() }.filter { it.isNotBlank() },
            reparto.map { it["name"].comoTexto() }.filter { it.isNotBlank() }.take(12), direccion, guion,
            nota(m["vote_average"].comoDouble()),
            m["production_countries"].comoLista().map { it.comoMapa()["name"].comoTexto() }.filter { it.isNotBlank() }.joinToString(", "),
            m["original_language"].comoTexto(), videos, trailers(m), "", tmdb, m["external_ids"].comoMapa()["tvdb_id"].comoLong(), imdb,
        )
        val rec = m["recommendations"].comoMapa()["results"].comoLista().mapNotNull { breve(it.comoMapa(), tv) }
        val sim = m["similar"].comoMapa()["results"].comoLista().mapNotNull { breve(it.comoMapa(), tv) }
        val extras = Extras(
            m["tagline"].comoTexto(), (rec.ifEmpty { sim }).distinctBy { it.id }.take(20), dondeVer(m),
            reparto.take(15).map { Persona(it["name"].comoTexto(), it["character"].comoTexto(), img(it["profile_path"].comoTexto(), "w185")) }.filter { it.nombre.isNotBlank() },
            tv,
        )
        return meta to extras
    }

    /** Un episodio, con el id de Stremio si se sabe el de IMDb de la serie. */
    fun episodio(e: Map<String, Any?>, imdb: String, tmdb: Long): Video? {
        val t = e["season_number"].comoLong().toInt(); val n = e["episode_number"].comoLong().toInt()
        if (n <= 0) return null
        val base = imdb.ifBlank { "tmdb:tv:$tmdb" }
        return Video("$base:$t:$n", e["name"].comoTexto(), t, n, Protocolo.fecha(e["air_date"].comoTexto()), img(e["still_path"].comoTexto(), "w300"), e["overview"].comoTexto())
    }

    /** Las temporadas de una serie (de sus detalles): sus números. */
    fun temporadas(json: String): List<Int> = lee(json)["seasons"].comoLista().map { it.comoMapa()["season_number"].comoLong().toInt() }.filter { it >= 0 }.distinct().sorted()

    /** `find/<tt…>`: el de TMDB de un id de IMDb (película o serie). */
    fun encontrado(json: String): IdTmdb? {
        val m = lee(json)
        m["movie_results"].comoLista().firstOrNull()?.comoMapa()?.get("id")?.comoLong()?.takeIf { it > 0 }?.let { return IdTmdb(false, it) }
        m["tv_results"].comoLista().firstOrNull()?.comoMapa()?.get("id")?.comoLong()?.takeIf { it > 0 }?.let { return IdTmdb(true, it) }
        return null
    }

    /** Lo que dice TMDB cuando algo va mal (`status_message`), para un aviso. */
    fun error(json: String): String = runCatching { lee(json)["status_message"].comoTexto() }.getOrDefault("")

    /** Junta dos listas alternando (películas y series de una plataforma), sin repetir. */
    fun alterna(a: List<MetaBreve>, b: List<MetaBreve>): List<MetaBreve> {
        val out = ArrayList<MetaBreve>()
        for (i in 0 until maxOf(a.size, b.size)) { a.getOrNull(i)?.let(out::add); b.getOrNull(i)?.let(out::add) }
        return out.distinctBy { it.id }
    }

    /** Una dirección de la API con sus parámetros (en castellano de España); la clave va en la cabecera, nunca aquí. */
    fun url(ruta: String, params: List<Pair<String, String>> = emptyList()): String {
        val todos = listOf("language" to "es-ES") + params
        return "https://api.themoviedb.org/3/" + ruta.trimStart('/') + "?" + todos.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
    }

    private fun lee(json: String): Map<String, Any?> = runCatching { Json.lee(json) }.getOrNull().comoMapa()
}

/**
 * El catálogo propio de Mostaza: TMDB (themoviedb.org), con tu clave. Es lo que se ve en la portada, en «Explorar», en «Buscar» y en la
 * ficha, como en cualquier plataforma; tus addons quedan para las fuentes (y para lo que TMDB no tenga). Sin clave, todo sigue como
 * antes, con los catálogos de tus addons.
 *
 * La clave (el «token de lectura» de TMDB) es tuya y va con tu cuenta, en `/claves/<uid>` (`mostaza_tmdb`), como tus addons: no la ve
 * tu pareja, no va a ningún registro ni a ningún mensaje. Se manda en la cabecera `Authorization: Bearer`, nunca en la dirección.
 *
 * TMDB pide que se diga: «This product uses the TMDB API but is not endorsed or certified by TMDB», y que lo de dónde verlo es de
 * JustWatch (está en la ficha y en «Catálogo»).
 */
object Tmdb {
    const val CLAVE = "mostaza_tmdb"
    const val AVISO_TMDB = "This product uses the TMDB API but is not endorsed or certified by TMDB."

    var version by mutableIntStateOf(0)
        private set

    @Volatile var http: Http = HttpJava()
    /** En las pruebas: la clave sin cuenta. */
    @Volatile var claveFija: String? = null
    private var appCtx: Context? = null
    private val cache = ConcurrentHashMap<String, Pair<Long, String>>()
    @Volatile private var generosPeli: Map<Long, String>? = null
    @Volatile private var generosSerie: Map<Long, String>? = null
    @Volatile private var lasPlataformas: List<Plataforma>? = null

    fun cargar(c: Context) {
        if (appCtx != null) return
        appCtx = c.applicationContext
        LaFamilia.alCambiar { version++ }
        LaFamilia.alEntrar { kotlin.concurrent.thread { runCatching { pasaLoDeSinCuenta() } } }
    }

    private fun sinCuenta() = appCtx?.let { DiscoCifrado.de(it, File(it.filesDir, "mostaza_seguro")) }

    fun clave(): String {
        version
        claveFija?.let { return it }
        return runCatching { LaFamilia.familia.clave(CLAVE) }.getOrDefault("").ifBlank { sinCuenta()?.leeTexto("tmdb.txt").orEmpty() }.trim()
    }

    val activo: Boolean get() = clave().isNotBlank()

    fun guarda(clave: String) {
        val t = clave.trim()
        val fam = runCatching { LaFamilia.familia }.getOrNull()
        if (fam != null && fam.sesion.dentro) fam.ponClave(CLAVE, t) else sinCuenta()?.escribeTexto("tmdb.txt", t)
        cache.clear(); generosPeli = null; generosSerie = null; lasPlataformas = null
        version++
    }

    private fun pasaLoDeSinCuenta() {
        val d = sinCuenta() ?: return
        val t = d.leeTexto("tmdb.txt") ?: return
        val fam = LaFamilia.familia
        if (!fam.sesion.dentro) return
        if (fam.clave(CLAVE).isBlank()) fam.ponClave(CLAVE, t)
        d.borra("tmdb.txt")
        version++
    }

    /** Pide algo a TMDB (con caché de [vida] ms en memoria). Los errores no llevan ni la dirección ni la clave. */
    fun pide(ruta: String, params: List<Pair<String, String>> = emptyList(), vida: Long = 30 * 60_000L): String {
        val k = clave().ifBlank { throw IOException("Sin clave de TMDB") }
        val u = TmdbDatos.url(ruta, params)
        cache[u]?.let { (t, s) -> if (System.currentTimeMillis() - t < vida) return s }
        val s = try { http.get(u, mapOf("Authorization" to "Bearer $k")) }
            catch (e: IOException) { throw IOException(if (e.message.orEmpty().contains("401")) "TMDB no acepta tu clave (revisa que sea el «token de lectura»)" else "TMDB no responde") }
        cache[u] = System.currentTimeMillis() to s
        return s
    }

    /** Prueba la clave (para «Catálogo»): null si vale, o lo que pasa. */
    suspend fun prueba(clave: String): String? = withContext(Dispatchers.IO) {
        try { http.get(TmdbDatos.url("configuration"), mapOf("Authorization" to "Bearer ${clave.trim()}")); null }
        catch (e: IOException) { if (e.message.orEmpty().contains("401")) "TMDB dice que esa clave no vale. Copia el «token de lectura de la API» (el largo)." else "No se ha podido hablar con TMDB (¿sin conexión?)." }
    }

    fun generos(tv: Boolean): Map<Long, String> {
        (if (tv) generosSerie else generosPeli)?.let { return it }
        val g = runCatching { TmdbDatos.generos(pide(if (tv) "genre/tv/list" else "genre/movie/list", vida = 24 * 3600_000L)) }.getOrDefault(emptyMap())
        if (g.isNotEmpty()) { if (tv) generosSerie = g else generosPeli = g }
        return g
    }

    /** Las plataformas de suscripción en España, en el orden de JustWatch. */
    fun plataformas(): List<Plataforma> {
        lasPlataformas?.let { return it }
        val p = runCatching { TmdbDatos.plataformas(pide("watch/providers/movie", listOf("watch_region" to TmdbDatos.REGION), vida = 24 * 3600_000L)) }.getOrDefault(emptyList())
        if (p.isNotEmpty()) lasPlataformas = p
        return p
    }

    private fun lista(ruta: String, tv: Boolean, params: List<Pair<String, String>> = emptyList()): List<MetaBreve> =
        runCatching { TmdbDatos.breves(pide(ruta, params), tv, generos(tv)) }.getOrDefault(emptyList())

    /** Películas o series filtradas (como «Explorar»). [orden]: `popularity.desc`, `vote_average.desc`, o lo más reciente. */
    fun descubre(tv: Boolean, genero: Long = 0, plataforma: Long = 0, orden: Orden = Orden.POPULARES, pagina: Int = 1): List<MetaBreve> {
        val hoy = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(java.util.Date())
        val p = ArrayList<Pair<String, String>>()
        p += "page" to pagina.toString()
        p += "include_adult" to "false"
        when (orden) {
            Orden.POPULARES -> p += "sort_by" to "popularity.desc"
            Orden.VALORADAS -> { p += "sort_by" to "vote_average.desc"; p += "vote_count.gte" to if (tv) "200" else "500" }
            Orden.RECIENTES -> {
                p += "sort_by" to if (tv) "first_air_date.desc" else "primary_release_date.desc"
                p += (if (tv) "first_air_date.lte" else "primary_release_date.lte") to hoy
                p += "vote_count.gte" to "20"
            }
        }
        if (genero > 0) p += "with_genres" to genero.toString()
        if (plataforma > 0) { p += "with_watch_providers" to plataforma.toString(); p += "watch_region" to TmdbDatos.REGION; p += "with_watch_monetization_types" to "flatrate|free|ads" }
        else if (!tv) p += "region" to TmdbDatos.REGION
        return lista(if (tv) "discover/tv" else "discover/movie", tv, p)
    }

    enum class Orden(val nombre: String) { POPULARES("Populares"), VALORADAS("Mejor valoradas"), RECIENTES("Más recientes") }

    /** La portada: el destacado (lo primero de «Tendencias» con imagen de fondo) y las filas, como una plataforma. */
    suspend fun portada(porqueViste: MetaBreve?): List<FilaTmdb> = withContext(Dispatchers.IO) {
        val r = TmdbDatos.REGION to TmdbDatos.REGION
        val plataformas = plataformas().take(8)
        val gp = generos(false)
        val generosFila = listOf("Acción", "Comedia", "Terror", "Animación", "Ciencia ficción", "Suspense", "Romance", "Documental")
            .mapNotNull { n -> gp.entries.firstOrNull { it.value.equals(n, ignoreCase = true) }?.let { n to it.key } }
        coroutineScope {
            val filas = ArrayList<kotlinx.coroutines.Deferred<FilaTmdb?>>()
            fun fila(t: String, f: () -> List<MetaBreve>) { filas += async { f().takeIf { it.isNotEmpty() }?.let { FilaTmdb(t, it) } } }
            fila("Tendencias de la semana") { lista("trending/all/week", false) }
            porqueViste?.let { pv -> fila("Porque viste «${pv.nombre}»") { porque(pv) } }
            fila("Estrenos en cines") { lista("movie/now_playing", false, listOf("region" to r.second)) }
            fila("Series del momento") { lista("trending/tv/week", true) }
            fila("Películas populares") { lista("movie/popular", false, listOf("region" to r.second)) }
            plataformas.forEach { p -> fila("En ${p.nombre}") { TmdbDatos.alterna(descubre(false, plataforma = p.id), descubre(true, plataforma = p.id)) } }
            fila("Las más valoradas") { lista("movie/top_rated", false, listOf("region" to r.second)) }
            fila("Series mejor valoradas") { lista("tv/top_rated", true) }
            generosFila.forEach { (n, id) -> fila(n) { descubre(false, genero = id) } }
            fila("Próximamente en cines") { lista("movie/upcoming", false, listOf("region" to r.second)) }
            filas.awaitAll().filterNotNull()
        }
    }

    /** «Porque viste…»: lo que TMDB recomienda a partir de algo (por su id de TMDB o de IMDb). */
    private fun porque(b: MetaBreve): List<MetaBreve> {
        val id = TmdbDatos.deId(b.id) ?: if (b.id.startsWith("tt")) runCatching { TmdbDatos.encontrado(pide("find/${b.id}", listOf("external_source" to "imdb_id"), vida = 7 * 24 * 3600_000L)) }.getOrNull() else null
        id ?: return emptyList()
        return lista(id.ruta + "/recommendations", id.tv)
    }

    suspend fun busca(q: String): List<MetaBreve> = withContext(Dispatchers.IO) {
        if (q.isBlank()) emptyList() else lista("search/multi", false, listOf("query" to q.trim(), "include_adult" to "false"))
    }

    /**
     * La ficha de [id] (`tmdb:…` o `tt…`), en castellano, con lo que añade TMDB. null si TMDB no la tiene (o no hay clave): entonces
     * se usa la de tus addons.
     */
    suspend fun meta(id: String, tipo: String): Pair<Meta, Extras>? = withContext(Dispatchers.IO) {
        if (!activo) return@withContext null
        val base = id.split(':').let { p -> if (p.firstOrNull()?.startsWith("tt") == true) p.first() else id }
        val t = TmdbDatos.deId(base) ?: if (base.startsWith("tt"))
            runCatching { TmdbDatos.encontrado(pide("find/$base", listOf("external_source" to "imdb_id"), vida = 7 * 24 * 3600_000L)) }.getOrNull()
            else null
        t ?: return@withContext null
        val apps = "external_ids,credits,videos,recommendations,similar,watch/providers"
        val detalles = runCatching { pide(t.ruta, listOf("append_to_response" to apps)) }.getOrNull() ?: return@withContext null
        var r = TmdbDatos.meta(detalles, t.tv) ?: return@withContext null
        if (t.tv) {
            // Las temporadas, de 19 en 19 (TMDB admite hasta 20 cosas añadidas por petición)
            val temps = TmdbDatos.temporadas(detalles)
            val trozos = temps.chunked(19).map { ts -> async { runCatching { pide(t.ruta, listOf("append_to_response" to (listOf("external_ids") + ts.map { "season/$it" }).joinToString(","))) }.getOrNull() } }.awaitAll()
            val eps = trozos.filterNotNull().flatMap { j -> TmdbDatos.meta(j, true)?.first?.videos.orEmpty() }.distinctBy { it.id }
            r = r.first.copy(videos = eps.sortedWith(compareBy({ it.temporada }, { it.episodio }))) to r.second
        }
        // Sin tráiler en castellano: los de cualquier idioma
        if (r.first.trailers.isEmpty()) runCatching {
            val v = http.get("https://api.themoviedb.org/3/${t.ruta}/videos", mapOf("Authorization" to "Bearer ${clave()}"))
            val tr = TmdbDatos.trailers(mapOf("videos" to Json.lee(v)))
            if (tr.isNotEmpty()) r = r.first.copy(trailers = tr) to r.second
        }
        r
    }
}
