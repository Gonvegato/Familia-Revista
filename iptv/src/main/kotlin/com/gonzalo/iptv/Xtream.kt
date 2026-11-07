package com.gonzalo.iptv

import java.io.InputStreamReader
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * El cliente de la API de reproductores de Xtream Codes (`player_api.php`). No es una especificación oficial: es lo que hacen los paneles
 * (Xtream Codes, XUI.one y compañía) y lo que esperan los reproductores, comprobado con varias referencias de ahora (ver HISTORIA). Lo que
 * se ha tenido en cuenta:
 *  - todo va por `GET <servidor>/player_api.php?username=…&password=…&action=…`; sin `action`, la cuenta (`user_info`, `server_info`);
 *  - los mismos campos llegan unas veces como texto y otras como número (`"stream_id":"12"` o `12`), y los vacíos como `""`, `null` o `[]`;
 *  - las listas grandes (`get_live_streams`, `get_vod_streams`, `get_series`) se leen a trozos ([JsonFlujo]), elemento a elemento;
 *  - `get_short_epg` da `epg_listings` con el título y la descripción en base64 (UTF-8) en casi todos los paneles, y en texto en alguno:
 *    se descodifica solo si todo lo de la respuesta lo es ([Base64Guia]);
 *  - las direcciones de lo que se ve: `/live/<usuario>/<contraseña>/<id>.<ts|m3u8>`, `/movie/<usuario>/<contraseña>/<id>.<extensión>`,
 *    `/series/<usuario>/<contraseña>/<id del episodio>.<extensión>` y lo ya emitido, `/timeshift/<usuario>/<contraseña>/<minutos>/
 *    <AAAA-MM-DD:HH-MM>/<id>.ts`; la guía entera, `/xmltv.php?username=…&password=…`.
 * Ningún error lleva la dirección, ni el usuario, ni la contraseña.
 */
class Xtream(val lista: Lista, private val red: Red) {
    val servidor: String = Servidor.normaliza(lista.url)
    private val usuario = lista.usuario
    private val clave = lista.contrasena
    private val cabeceras: Map<String, String> = if (lista.agente.isNotBlank()) mapOf("User-Agent" to lista.agente) else emptyMap()

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun api(accion: String?, extra: String = "") =
        "$servidor/player_api.php?username=${q(usuario)}&password=${q(clave)}" + (if (accion != null) "&action=$accion" else "") + extra

    private fun <T> pide(url: String, que: String, leer: (JsonFlujo) -> T): T {
        val r = try { red.abre(url, cabeceras) } catch (e: Exception) { throw RedJava.traduce(e) }
        r.use {
            when (r.codigo) {
                in 200..299 -> {}
                401, 403 -> throw IptvError("El servidor no te deja entrar (usuario o contraseña, o la cuenta ha caducado).", r.codigo)
                404 -> throw IptvError("Ese servidor no tiene la API de Xtream (¿la dirección es la buena?).", r.codigo)
                else -> throw IptvError("El servidor ha contestado con un error (${r.codigo}) al pedir $que.", r.codigo)
            }
            return try { leer(JsonFlujo(InputStreamReader(r.cuerpo, Charsets.UTF_8))) }
            catch (e: JsonFlujo.Error) { throw IptvError("Lo que ha dado el servidor al pedir $que no se entiende.") }
            catch (e: IptvError) { throw e }
            catch (e: Exception) { throw IptvError("Se ha cortado la conexión al pedir $que.") }
        }
    }

    /** Recorre una respuesta que debería ser una lista de objetos (si es otra cosa —un `{}` o un error—, no da nada). */
    private fun cadaUno(j: JsonFlujo, alUno: (Map<String, Any?>) -> Unit): Int {
        var n = 0
        when (j.tipo()) {
            '[' -> { j.empiezaLista(); while (j.hayMas()) { val v = j.valor(); if (v is Map<*, *>) { alUno(v.mapa()); n++ } } }
            '{' -> j.valor().mapa().values.forEach { v -> if (v is Map<*, *>) { alUno(v.mapa()); n++ } }
            else -> {}
        }
        return n
    }

    // ---------------------------------------------------------------- la cuenta

    /** Tu cuenta en ese servidor. Lanza [IptvError] si el usuario o la contraseña no valen. */
    fun perfil(): Perfil = pide(api(null), "tu cuenta") { j ->
        val m = j.valor().mapa()
        val u = m["user_info"].mapa(); val s = m["server_info"].mapa()
        if (u.isEmpty()) throw IptvError("Eso no parece un servidor de Xtream: no dice nada de tu cuenta.")
        val auth = u["auth"].largo() == 1L
        if (!auth) throw IptvError("El servidor no te deja entrar: el usuario o la contraseña no valen.")
        Perfil(
            activo = u["status"].txt().equals("Active", true), estado = u["status"].txt(), caduca = u["exp_date"].largo() * 1000L,
            conexionesMax = u["max_connections"].largo().toInt(), conexionesActivas = u["active_cons"].largo().toInt(),
            formatos = u["allowed_output_formats"].lista().map { it.txt() }.filter { it.isNotBlank() }, zonaHoraria = s["timezone"].txt(),
            prueba = u["is_trial"].largo() == 1L, mensaje = u["message"].txt(),
        )
    }

    // ---------------------------------------------------------------- categorías

    private fun categorias(accion: String, que: String): List<Categoria> {
        val l = ArrayList<Categoria>()
        pide(api(accion), que) { j -> cadaUno(j) { m -> val id = m["category_id"].txt(); if (id.isNotBlank()) l += Categoria(id, m["category_name"].txt().ifBlank { "Sin nombre" }) } }
        return l
    }
    fun categoriasDirecto() = categorias("get_live_categories", "las categorías de canales")
    fun categoriasPeliculas() = categorias("get_vod_categories", "las categorías de películas")
    fun categoriasSeries() = categorias("get_series_categories", "las categorías de series")

    // ---------------------------------------------------------------- listas (a trozos)

    /** Los canales en directo, uno a uno. [nombresCategorias]: el nombre de cada categoría (para el grupo del canal). */
    fun canales(nombresCategorias: Map<String, String>, categoria: String? = null, alCanal: (Canal) -> Unit): Int =
        pide(api("get_live_streams", categoria?.let { "&category_id=${q(it)}" }.orEmpty()), "los canales") { j ->
            var n = 0; cadaUno(j) { m -> canalDe(m, nombresCategorias)?.let { alCanal(it); n++ } }; n
        }

    fun peliculas(categoria: String? = null, alPelicula: (Pelicula) -> Unit): Int =
        pide(api("get_vod_streams", categoria?.let { "&category_id=${q(it)}" }.orEmpty()), "las películas") { j ->
            var n = 0; cadaUno(j) { m -> peliculaDe(m)?.let { alPelicula(it); n++ } }; n
        }

    fun series(categoria: String? = null, alSerie: (Serie) -> Unit): Int =
        pide(api("get_series", categoria?.let { "&category_id=${q(it)}" }.orEmpty()), "las series") { j ->
            var n = 0; cadaUno(j) { m -> serieDe(m)?.let { alSerie(it); n++ } }; n
        }

    // ---------------------------------------------------------------- fichas

    fun fichaPelicula(p: Pelicula): FichaPelicula = pide(api("get_vod_info", "&vod_id=${p.id}"), "la ficha de la película") { j -> fichaPeliculaDe(j.valor().mapa(), p) }

    fun fichaSerie(s: Serie): FichaSerie = pide(api("get_series_info", "&series_id=${s.id}"), "la ficha de la serie") { j -> fichaSerieDe(j.valor().mapa(), s) }

    /** Lo que se emite ahora y lo siguiente en un canal (de la guía del propio servidor). */
    fun guiaCorta(c: Canal, limite: Int = 4, zona: String = ""): List<Programa> = pide(api("get_short_epg", "&stream_id=${c.idXtream}&limit=$limite"), "la guía del canal") { j ->
        programasDe(j.valor().mapa()["epg_listings"].lista().map { it.mapa() }, c.tvgId.ifBlank { c.clave }, zona)
    }

    // ---------------------------------------------------------------- direcciones (llevan tu usuario y tu contraseña: no se enseñan)

    private fun p(s: String) = Servidor.segmento(s)
    fun urlDirecto(c: Canal, formato: String = lista.formato): String = "$servidor/live/${p(usuario)}/${p(clave)}/${c.idXtream}.${formato.ifBlank { "ts" }}"
    fun urlPelicula(p: Pelicula): String = "$servidor/movie/${p(usuario)}/${p(clave)}/${p.id}.${p.extension.ifBlank { "mp4" }}"
    fun urlEpisodio(e: Episodio): String = "$servidor/series/${p(usuario)}/${p(clave)}/${e.id}.${e.extension.ifBlank { "mp4" }}"
    /** Lo ya emitido (si el canal lo tiene): desde [inicio] (en la zona horaria del servidor) durante [minutos]. */
    fun urlTimeshift(c: Canal, inicio: Long, minutos: Int, zona: String = ""): String {
        val z = runCatching { if (zona.isBlank()) ZoneOffset.UTC else ZoneId.of(zona) }.getOrDefault(ZoneOffset.UTC)
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm").format(java.time.Instant.ofEpochMilli(inicio).atZone(z))
        return "$servidor/timeshift/${p(usuario)}/${p(clave)}/$minutos/$f/${c.idXtream}.ts"
    }
    fun urlGuia(): String = "$servidor/xmltv.php?username=${q(usuario)}&password=${q(clave)}"

    companion object {
        /** Un canal de `get_live_streams`. La clave es `x<stream_id>`, que no cambia. */
        fun canalDe(m: Map<String, Any?>, categorias: Map<String, String>): Canal? {
            val id = m["stream_id"].largo()
            if (id <= 0) return null
            val cat = m["category_id"].txt()
            val tipo = m["stream_type"].txt()
            val archivo = m["tv_archive"].largo() == 1L
            return Canal(
                clave = "x$id", nombre = m["name"].txt().trim().ifBlank { "Canal $id" }, grupo = (categorias[cat] ?: "").intern(), logo = m["stream_icon"].txt(),
                tvgId = m["epg_channel_id"].txt().trim(), numero = m["num"].largo().toInt(), idXtream = id, radio = tipo == "radio_streams",
                catchup = if (archivo) Catchup("xc", m["tv_archive_duration"].largo().toInt()) else null,
            )
        }

        fun peliculaDe(m: Map<String, Any?>): Pelicula? {
            val id = m["stream_id"].largo()
            if (id <= 0) return null
            return Pelicula(id, m["name"].txt().trim().ifBlank { "Película $id" }, m["category_id"].txt(), m["stream_icon"].txt(), m["container_extension"].txt().ifBlank { "mp4" },
                m["rating"].decimal(), m["added"].largo() * 1000L, m["tmdb"].txt().ifBlank { m["tmdb_id"].txt() }, m["year"].txt())
        }

        fun serieDe(m: Map<String, Any?>): Serie? {
            val id = m["series_id"].largo()
            if (id <= 0) return null
            return Serie(id, m["name"].txt().trim().ifBlank { "Serie $id" }, m["category_id"].txt(), m["cover"].txt(), m["rating"].decimal(),
                m["year"].txt().ifBlank { (m["releaseDate"] ?: m["release_date"]).txt().take(4) }, m["genre"].txt(), m["plot"].txt(), primeraImagen(m["backdrop_path"]),
                m["last_modified"].largo() * 1000L)
        }

        private fun primeraImagen(v: Any?): String = when (v) { is List<*> -> v.firstOrNull().txt(); else -> v.txt() }

        fun fichaPeliculaDe(m: Map<String, Any?>, p: Pelicula): FichaPelicula {
            val info = m["info"].mapa(); val datos = m["movie_data"].mapa()
            val ext = datos["container_extension"].txt().ifBlank { p.extension }
            val tmdb = info["tmdb_id"].txt().ifBlank { p.tmdb }
            val dur = info["duration_secs"].largo().takeIf { it > 0 } ?: duracion(info["duration"].txt())
            return FichaPelicula(
                pelicula = p.copy(extension = ext, tmdb = tmdb, poster = p.poster.ifBlank { info["movie_image"].txt().ifBlank { info["cover_big"].txt() } },
                    ano = p.ano.ifBlank { (info["releasedate"] ?: info["release_date"]).txt().take(4) }, nota = if (p.nota > 0) p.nota else info["rating"].decimal()),
                descripcion = info["plot"].txt().ifBlank { info["description"].txt() }, duracionSeg = dur, generos = info["genre"].txt(),
                reparto = info["cast"].txt().ifBlank { info["actors"].txt() }, director = info["director"].txt(), fondo = primeraImagen(info["backdrop_path"]),
                trailer = info["youtube_trailer"].txt(), estreno = (info["releasedate"] ?: info["release_date"]).txt(), pais = info["country"].txt(),
            )
        }

        fun fichaSerieDe(m: Map<String, Any?>, s: Serie): FichaSerie {
            val info = m["info"].mapa()
            val serie = s.copy(poster = s.poster.ifBlank { info["cover"].txt() }, trama = s.trama.ifBlank { info["plot"].txt() }, generos = s.generos.ifBlank { info["genre"].txt() },
                fondo = s.fondo.ifBlank { primeraImagen(info["backdrop_path"]) })
            val porTemporada = LinkedHashMap<Int, MutableList<Episodio>>()
            // `episodes`: un objeto con la temporada como clave ({"1":[…],"2":[…]}) o, en algunos paneles, una lista de listas
            val eps = m["episodes"]
            val grupos: List<Pair<Int?, List<Any?>>> = when (eps) {
                is Map<*, *> -> eps.entries.map { (k, v) -> k.toString().trim().toIntOrNull() to v.lista() }
                is List<*> -> eps.map { null to it.lista() }
                else -> emptyList()
            }
            for ((t, l) in grupos) for (x in l) {
                val e = x.mapa()
                val id = e["id"].largo()
                if (id <= 0) continue
                val ei = e["info"].mapa()
                val temp = e["season"].largo().toInt().takeIf { it > 0 } ?: t ?: ei["season"].largo().toInt()
                val ep = Episodio(id, temp, e["episode_num"].largo().toInt(), e["title"].txt().trim(), e["container_extension"].txt().ifBlank { "mp4" },
                    ei["duration_secs"].largo().takeIf { it > 0 } ?: duracion(ei["duration"].txt()), ei["plot"].txt(), ei["movie_image"].txt().ifBlank { ei["cover_big"].txt() },
                    (ei["air_date"] ?: ei["releasedate"] ?: ei["release_date"]).txt(), ei["tmdb_id"].txt())
                porTemporada.getOrPut(temp) { ArrayList() } += ep
            }
            porTemporada.values.forEach { it.sortBy { e -> e.numero } }
            val ordenadas = porTemporada.toSortedMap(compareBy { if (it == 0) Int.MAX_VALUE else it })
            val temporadas = m["seasons"].lista().map { it.mapa() }.mapNotNull { t ->
                val n = t["season_number"].largo().toInt()
                if (!ordenadas.containsKey(n)) null else Temporada(n, t["name"].txt().ifBlank { if (n == 0) "Especiales" else "Temporada $n" }, t["cover"].txt().ifBlank { t["cover_big"].txt() }, ordenadas[n]?.size ?: 0)
            }.associateBy { it.numero }
            val todas = ordenadas.keys.map { n -> temporadas[n] ?: Temporada(n, if (n == 0) "Especiales" else "Temporada $n", "", ordenadas[n]?.size ?: 0) }
            return FichaSerie(serie, todas, ordenadas)
        }

        /** «01:52:10» (o «52:10», minutos y segundos) o «112 min», en segundos. */
        fun duracion(s: String): Long {
            val t = s.trim()
            if (t.isEmpty()) return 0L
            val partes = t.split(':')
            if (partes.size in 2..3 && partes.all { it.trim().toIntOrNull() != null }) return partes.fold(0L) { a, x -> a * 60 + x.trim().toLong() }
            return (Regex("\\d+").find(t)?.value?.toLongOrNull() ?: 0L) * 60
        }

        /** Los programas de `get_short_epg` (con el título y la descripción en base64 o no, y las horas en segundos o en la hora del servidor). */
        fun programasDe(l: List<Map<String, Any?>>, canal: String, zona: String = ""): List<Programa> {
            val b64 = Base64Guia.todoEnBase64(l.flatMap { listOf(it["title"].txt(), it["description"].txt()) })
            val z = runCatching { if (zona.isBlank()) ZoneOffset.UTC else ZoneId.of(zona) }.getOrDefault(ZoneOffset.UTC)
            fun hora(seg: Any?, txt: Any?): Long {
                val s = seg.largo()
                if (s > 0) return s * 1000L
                return runCatching { LocalDateTime.parse(txt.txt().trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(z).toInstant().toEpochMilli() }.getOrDefault(0L)
            }
            return l.mapNotNull { e ->
                val ini = hora(e["start_timestamp"], e["start"]); val fin = hora(e["stop_timestamp"], e["end"] ?: e["stop"])
                if (ini <= 0) null else Programa(canal, ini, if (fin > ini) fin else ini + 30 * 60_000L,
                    if (b64) Base64Guia.texto(e["title"].txt()) else e["title"].txt(), descripcion = if (b64) Base64Guia.texto(e["description"].txt()) else e["description"].txt())
            }.sortedBy { it.inicio }
        }
    }
}

/** Lo de la guía de Xtream en base64: se descodifica solo si TODO lo de la respuesta es base64 que da UTF-8 limpio (si no, va en texto). */
object Base64Guia {
    private val ALFABETO = Regex("^[A-Za-z0-9+/]*={0,2}$")

    fun descodifica(s: String): String? {
        val t = s.trim()
        if (t.isEmpty()) return ""
        if (t.length % 4 != 0 || !ALFABETO.matches(t)) return null
        val b = runCatching { Base64.getDecoder().decode(t) }.getOrNull() ?: return null
        val dec = Charsets.UTF_8.newDecoder()
        val r = runCatching { dec.decode(java.nio.ByteBuffer.wrap(b)).toString() }.getOrNull() ?: return null
        // (texto de verdad: sin caracteres de control raros)
        if (r.any { it.code < 0x20 && it != '\n' && it != '\r' && it != '\t' }) return null
        return r
    }

    fun todoEnBase64(textos: List<String>): Boolean {
        val l = textos.filter { it.isNotBlank() }
        return l.isNotEmpty() && l.all { descodifica(it) != null }
    }

    fun texto(s: String): String = descodifica(s) ?: s
}

/** Las direcciones de los servidores (las escribes tú, así que se arreglan un poco). */
object Servidor {
    /** «ejemplo.com:8080/» → «http://ejemplo.com:8080»; quita lo que sobre si se pega una dirección de la API o de la lista. */
    fun normaliza(s: String): String {
        var t = s.trim()
        if (t.isEmpty()) return ""
        if (!t.contains("://")) t = "http://$t"
        t = t.substringBefore('?').substringBefore('#')
        for (fin in listOf("/player_api.php", "/get.php", "/xmltv.php", "/panel_api.php")) if (t.endsWith(fin, true)) t = t.dropLast(fin.length)
        return t.trimEnd('/')
    }

    /** Para un trozo de la ruta de una dirección (un usuario con espacios o con «/» no rompe la dirección). */
    fun segmento(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /**
     * Si pegas la dirección M3U que da un servidor de Xtream (`http://servidor:puerto/get.php?username=…&password=…&type=m3u_plus…`), se
     * puede usar como login de Xtream (con su videoclub y su guía). Devuelve (servidor, usuario, contraseña), o null si no lo es.
     */
    fun deEnlace(url: String): Triple<String, String, String>? {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val ruta = u.rawPath.orEmpty()
        if (!(ruta.endsWith("/get.php", true) || ruta.endsWith("/player_api.php", true))) return null
        val q = u.rawQuery.orEmpty().split('&').associate { it.substringBefore('=') to runCatching { URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }.getOrDefault("") }
        val us = q["username"].orEmpty(); val pw = q["password"].orEmpty()
        if (us.isBlank() || pw.isBlank()) return null
        val base = "${u.scheme}://${u.rawAuthority}" + ruta.substringBeforeLast('/').trimEnd('/')
        return Triple(base, us, pw)
    }
}
