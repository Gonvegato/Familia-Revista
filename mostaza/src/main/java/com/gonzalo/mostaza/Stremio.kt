package com.gonzalo.mostaza

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/*
 * El protocolo de los addons de Stremio, tal como lo documenta su SDK (stremio-addon-sdk/docs: protocol.md y api/responses): un addon
 * es una dirección que sirve `/manifest.json` y responde a `/{recurso}/{tipo}/{id}.json` o `/{recurso}/{tipo}/{id}/{extra}.json`, donde
 * `extra` son pares `nombre=valor` (como una consulta: `search=game%20of%20thrones&skip=100`). Los recursos que se usan aquí: `catalog`,
 * `meta`, `stream` y `subtitles`. Todo es Kotlin sin Android (se prueba contra un addon de mentira en el ordenador).
 *
 * Las direcciones de los addons pueden llevar secretos (la clave de tu debrid va dentro): nunca se escriben en un registro, en un mensaje
 * de error ni en la cuenta fuera de `/claves` (ver [Addons]).
 */

/** Una propiedad extra de un catálogo (`search`, `genre`, `skip`…). */
data class Extra(val nombre: String, val obligatorio: Boolean = false, val opciones: List<String> = emptyList(), val limite: Int = 1)

/** Un catálogo de un addon. */
data class Catalogo(val tipo: String, val id: String, val nombre: String, val extras: List<Extra> = emptyList()) {
    fun extra(n: String): Extra? = extras.firstOrNull { it.nombre == n }
    /** Admite buscar. */
    val buscable: Boolean get() = extra("search") != null
    /** Solo sirve para buscar (no da una lista sin buscar). */
    val soloBusqueda: Boolean get() = extra("search")?.obligatorio == true
    /** Pide algo que no se le puede dar sin preguntar (un extra obligatorio que no es la búsqueda ni el género). */
    val necesitaOtraCosa: Boolean get() = extras.any { it.obligatorio && it.nombre != "search" && it.nombre != "genre" }
    /** Los géneros (o años) por los que se puede filtrar. */
    val generos: List<String> get() = extra("genre")?.opciones.orEmpty()
    /** Pide un género siempre (los de «por año» de Cinemeta, por ejemplo). */
    val generoObligatorio: Boolean get() = extra("genre")?.obligatorio == true
    val pagina: Boolean get() = extra("skip") != null
    /** ¿Los «géneros» son años? (el catálogo «Por año»). */
    val porAno: Boolean get() = generos.size >= 5 && generos.take(5).all { it.length == 4 && it.all(Char::isDigit) }
}

/** Un recurso que da un addon, con los tipos y los prefijos de id a los que responde (null = los del manifiesto). */
data class Recurso(val nombre: String, val tipos: List<String>? = null, val prefijos: List<String>? = null)

data class Manifiesto(
    val id: String, val version: String, val nombre: String, val descripcion: String,
    val tipos: List<String>, val prefijos: List<String>?, val recursos: List<Recurso>, val catalogos: List<Catalogo>,
    val logo: String = "", val fondo: String = "",
    val configurable: Boolean = false, val configuracionObligatoria: Boolean = false, val p2p: Boolean = false, val adultos: Boolean = false,
) {
    /**
     * ¿Responde este addon a [recurso] para [tipo] e [id]? Un recurso puesto como texto usa los tipos y prefijos del manifiesto; como
     * objeto, los suyos (sin prefijos: todos los ids). El de catálogos no mira los ids: se pide si el catálogo está en `catalogs`.
     */
    fun soporta(recurso: String, tipo: String, id: String = ""): Boolean {
        val r = recursos.firstOrNull { it.nombre == recurso } ?: return false
        val tipos = r.tipos ?: this.tipos
        if (tipo !in tipos) return false
        if (recurso == "catalog") return true
        val pre = if (r.tipos != null) r.prefijos else (r.prefijos ?: prefijos)
        return pre.isNullOrEmpty() || pre.any { id.startsWith(it) }
    }

    val buscables: List<Catalogo> get() = catalogos.filter { it.buscable }
}

/** Lo que sale en un catálogo (la «Meta Preview»). */
data class MetaBreve(
    val id: String, val tipo: String, val nombre: String, val poster: String = "", val forma: String = "poster",
    val fondo: String = "", val ano: String = "", val generos: List<String> = emptyList(), val nota: String = "", val descripcion: String = "",
)

/** Un vídeo de una serie (un episodio) o de un canal. */
data class Video(
    val id: String, val titulo: String, val temporada: Int = 0, val episodio: Int = 0, val estreno: Long = 0L,
    val miniatura: String = "", val resumen: String = "",
) {
    /** Ya se ha estrenado (los que no tienen fecha cuentan como estrenados si no son el futuro). */
    fun estrenado(ahora: Long = System.currentTimeMillis()) = estreno in 1..ahora || estreno == 0L
}

/** La ficha completa de una película o una serie. */
data class Meta(
    val id: String, val tipo: String, val nombre: String, val poster: String = "", val fondo: String = "", val logo: String = "",
    val descripcion: String = "", val ano: String = "", val estreno: Long = 0L, val duracion: String = "", val generos: List<String> = emptyList(),
    val reparto: List<String> = emptyList(), val direccion: List<String> = emptyList(), val guion: List<String> = emptyList(),
    val nota: String = "", val pais: String = "", val idioma: String = "", val videos: List<Video> = emptyList(),
    val trailers: List<String> = emptyList(), val videoPorDefecto: String = "", val tmdb: Long = 0L, val tvdb: Long = 0L, val imdb: String = "",
) {
    val esSerie: Boolean get() = tipo == "series" || videos.size > 1 || (videos.isNotEmpty() && videos.any { it.temporada > 0 })
    /** La duración en minutos («142 min», «2h 22min», «120m»). 0 = no se sabe. */
    val minutos: Int get() = minutosDe(duracion)
    /** Las temporadas (sin la 0, la de los especiales, que va al final si la hay). */
    val temporadas: List<Int> get() = videos.map { it.temporada }.distinct().sortedWith(compareBy({ it == 0 }, { it }))
    fun episodios(t: Int): List<Video> = videos.filter { it.temporada == t }.sortedBy { it.episodio }
    fun breve() = MetaBreve(id, tipo, nombre, poster, "poster", fondo, ano, generos, nota, descripcion)

    companion object {
        fun minutosDe(s: String): Int {
            val t = s.lowercase().trim()
            if (t.isBlank()) return 0
            val h = Regex("(\\d+)\\s*h").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val m = Regex("(\\d+)\\s*(min|m\\b|m$)").find(t)?.groupValues?.get(1)?.toIntOrNull()
            if (h > 0 || m != null) return h * 60 + (m ?: 0)
            return t.filter(Char::isDigit).toIntOrNull() ?: 0
        }
    }
}

/** De qué es una fuente (y cómo se ve). */
enum class TipoFuente { DIRECTA, TORRENT, YOUTUBE, EXTERNA, NZB, ARCHIVO, NADA }

/** Una fuente para ver algo: lo que devuelve `stream`. */
data class Fuente(
    val nombre: String = "", val descripcion: String = "",
    val url: String = "", val ytId: String = "", val infoHash: String = "", val fileIdx: Int? = null, val externalUrl: String = "",
    val nzbUrl: String = "", val archivos: List<String> = emptyList(), val trackers: List<String> = emptyList(),
    val subtitulos: List<Subtitulo> = emptyList(),
    val noWeb: Boolean = false, val grupo: String = "", val cabeceras: Map<String, String> = emptyMap(),
    val tamano: Long = 0L, val archivo: String = "", val hashVideo: String = "", val paises: List<String> = emptyList(),
) {
    val tipo: TipoFuente get() = when {
        url.isNotBlank() -> TipoFuente.DIRECTA
        infoHash.isNotBlank() -> TipoFuente.TORRENT
        ytId.isNotBlank() -> TipoFuente.YOUTUBE
        externalUrl.isNotBlank() -> TipoFuente.EXTERNA
        nzbUrl.isNotBlank() -> TipoFuente.NZB
        archivos.isNotEmpty() -> TipoFuente.ARCHIVO
        else -> TipoFuente.NADA
    }
    /** Todo el texto de la fuente (para el elector: calidad, idioma, tamaño…). */
    val texto: String get() = listOf(nombre, descripcion, archivo).filter { it.isNotBlank() }.joinToString("\n")
}

data class Subtitulo(val id: String, val url: String, val idioma: String, val etiqueta: String = "")

/** Leer las respuestas de los addons (JSON) con el lector de la familia. */
object Protocolo {
    private fun textos(v: Any?): List<String> = when (v) {
        is String -> if (v.isBlank()) emptyList() else listOf(v)
        else -> v.comoLista().map { it.comoTexto() }.filter { it.isNotBlank() }
    }
    private fun texto(m: Map<String, Any?>, vararg k: String): String = k.asSequence().map { m[it] }.firstOrNull { it != null && it.comoTexto().isNotBlank() }.comoTexto()

    fun manifiesto(json: String): Manifiesto = manifiesto(Json.lee(json).comoMapa())

    fun manifiesto(m: Map<String, Any?>): Manifiesto {
        val id = m["id"].comoTexto(); val nombre = m["name"].comoTexto()
        if (id.isBlank() && nombre.isBlank()) throw IOException("Eso no es el manifiesto de un addon")
        val recursos = m["resources"].comoLista().mapNotNull { r ->
            when (r) {
                is String -> Recurso(r)
                is Map<*, *> -> { val o = r.comoMapa(); o["name"].comoTexto().takeIf { it.isNotBlank() }?.let { Recurso(it, o["types"]?.let(::textos), o["idPrefixes"]?.let(::textos)) } }
                else -> null
            }
        }
        val catalogos = m["catalogs"].comoLista().mapNotNull { c ->
            val o = c.comoMapa()
            val tipo = o["type"].comoTexto(); val cid = o["id"].comoTexto()
            if (tipo.isBlank() || cid.isBlank()) return@mapNotNull null
            val extras = o["extra"].comoLista().mapNotNull { e ->
                val x = e.comoMapa(); val n = x["name"].comoTexto()
                if (n.isBlank()) null else Extra(n, x["isRequired"].comoBool(), textos(x["options"]), x["optionsLimit"].comoLong().toInt().takeIf { it > 0 } ?: 1)
            }.toMutableList()
            // El formato de antes («short»): extraSupported / extraRequired y genres sueltos
            textos(o["extraSupported"]).forEach { n -> if (extras.none { it.nombre == n }) extras += Extra(n, n in textos(o["extraRequired"]), if (n == "genre") textos(o["genres"]) else emptyList()) }
            Catalogo(tipo, cid, o["name"].comoTexto().ifBlank { cid }, extras)
        }
        val bh = m["behaviorHints"].comoMapa()
        return Manifiesto(
            id, m["version"].comoTexto(), nombre.ifBlank { id }, m["description"].comoTexto(), textos(m["types"]),
            m["idPrefixes"]?.let(::textos), recursos, catalogos, m["logo"].comoTexto(), m["background"].comoTexto(),
            bh["configurable"].comoBool(), bh["configurationRequired"].comoBool(), bh["p2p"].comoBool(), bh["adult"].comoBool(),
        )
    }

    fun metas(json: String): List<MetaBreve> = Json.lee(json).comoMapa()["metas"].comoLista().mapNotNull { metaBreve(it.comoMapa()) }

    fun metaBreve(o: Map<String, Any?>): MetaBreve? {
        val id = o["id"].comoTexto(); val nombre = o["name"].comoTexto()
        if (id.isBlank() || nombre.isBlank()) return null
        return MetaBreve(id, o["type"].comoTexto(), nombre, o["poster"].comoTexto(), o["posterShape"].comoTexto().ifBlank { "poster" }, o["background"].comoTexto(),
            texto(o, "releaseInfo", "year"), textos(o["genres"] ?: o["genre"]), o["imdbRating"].comoTexto(), texto(o, "description"))
    }

    fun meta(json: String): Meta? = Json.lee(json).comoMapa()["meta"].let { m -> if (m is List<*>) m.firstOrNull() else m }.comoMapa().let { meta(it) }

    fun meta(o: Map<String, Any?>): Meta? {
        val id = o["id"].comoTexto(); val nombre = o["name"].comoTexto()
        if (id.isBlank() || nombre.isBlank()) return null
        val videos = o["videos"].comoLista().mapNotNull { v ->
            val x = v.comoMapa(); val vid = x["id"].comoTexto()
            if (vid.isBlank()) null else Video(vid, texto(x, "title", "name"), x["season"].comoLong().toInt(), (x["episode"] ?: x["number"]).comoLong().toInt(),
                fecha(texto(x, "released", "firstAired")), x["thumbnail"].comoTexto(), texto(x, "overview", "description"))
        }
        val trailers = o["trailers"].comoLista().mapNotNull { it.comoMapa()["source"].comoTexto().takeIf { s -> s.isNotBlank() } } +
            o["trailerStreams"].comoLista().mapNotNull { it.comoMapa()["ytId"].comoTexto().takeIf { s -> s.isNotBlank() } }
        return Meta(
            id, o["type"].comoTexto(), nombre, o["poster"].comoTexto(), o["background"].comoTexto(), o["logo"].comoTexto(), texto(o, "description"),
            texto(o, "releaseInfo", "year"), fecha(o["released"].comoTexto()), o["runtime"].comoTexto(), textos(o["genres"] ?: o["genre"]),
            textos(o["cast"]), textos(o["director"]), textos(o["writer"]), o["imdbRating"].comoTexto(), o["country"].comoTexto(), o["language"].comoTexto(),
            videos, trailers.distinct(), o["behaviorHints"].comoMapa()["defaultVideoId"].comoTexto(),
            o["moviedb_id"].comoLong(), o["tvdb_id"].comoLong(), o["imdb_id"].comoTexto().ifBlank { if (id.startsWith("tt")) id.substringBefore(':') else "" },
        )
    }

    fun fuentes(json: String): List<Fuente> = Json.lee(json).comoMapa()["streams"].comoLista().mapNotNull { fuente(it.comoMapa()) }

    fun fuente(o: Map<String, Any?>): Fuente? {
        val bh = o["behaviorHints"].comoMapa()
        val ph = bh["proxyHeaders"].comoMapa()["request"].comoMapa().mapValues { it.value.comoTexto() }.filterValues { it.isNotBlank() }
        val archivos = listOf("rarUrls", "zipUrls", "7zipUrls", "tgzUrls", "tarUrls").flatMap { k ->
            o[k].comoLista().mapNotNull { s -> (if (s is Map<*, *>) s.comoMapa()["url"].comoTexto() else s.comoTexto()).takeIf { it.isNotBlank() } }
        }
        val f = Fuente(
            o["name"].comoTexto(), texto(o, "description", "title"),
            o["url"].comoTexto(), o["ytId"].comoTexto(), o["infoHash"].comoTexto().lowercase(), o["fileIdx"]?.let { if (it is Number) it.toInt() else it.comoTexto().toIntOrNull() },
            o["externalUrl"].comoTexto(), o["nzbUrl"].comoTexto(), archivos, textos(o["sources"]),
            o["subtitles"].comoLista().mapNotNull { subtitulo(it.comoMapa()) },
            bh["notWebReady"].comoBool(), bh["bingeGroup"].comoTexto(), ph, bh["videoSize"].comoLong(), bh["filename"].comoTexto(), bh["videoHash"].comoTexto(),
            textos(bh["countryWhitelist"]),
        )
        return if (f.tipo == TipoFuente.NADA) null else f
    }

    fun subtitulos(json: String): List<Subtitulo> = Json.lee(json).comoMapa()["subtitles"].comoLista().mapNotNull { subtitulo(it.comoMapa()) }

    fun subtitulo(o: Map<String, Any?>): Subtitulo? {
        val url = o["url"].comoTexto(); if (url.isBlank()) return null
        return Subtitulo(o["id"].comoTexto().ifBlank { url }, url, o["lang"].comoTexto(), o["label"].comoTexto())
    }

    /** Una fecha ISO 8601 («2010-12-06T05:00:00.000Z» o «2010-12-06») en ms; 0 si no se entiende. */
    fun fecha(s: String): Long {
        if (s.isBlank()) return 0L
        return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(s.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() ?: 0L
    }

    /** encodeURIComponent de JavaScript (lo que usan los clientes de Stremio para el id y los extras). */
    fun codifica(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (c < 128 && (ch.isLetterOrDigit() || ch in "-_.!~*'()")) sb.append(ch) else sb.append('%').append("%02X".format(c))
        }
        return sb.toString()
    }

    /** La dirección del manifiesto, como se instala: `stremio://` pasa a `https://`, y sin `/manifest.json` se le pone. */
    fun normaliza(url: String): String {
        var u = url.trim()
        if (u.startsWith("stremio://", ignoreCase = true)) u = "https://" + u.substring("stremio://".length)
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "https://$u"
        val sinConsulta = u.substringBefore('?')
        if (!sinConsulta.endsWith("/manifest.json")) u = sinConsulta.trimEnd('/') + "/manifest.json"
        return u
    }

    /** La base del addon (lo que va antes de `/manifest.json`). */
    fun base(manifiestoUrl: String): String = manifiestoUrl.substringBefore('?').removeSuffix("/manifest.json")

    /** `/{recurso}/{tipo}/{id}.json` o, con extras, `/{recurso}/{tipo}/{id}/{extra}.json`. */
    fun url(base: String, recurso: String, tipo: String, id: String, extras: List<Pair<String, String>> = emptyList()): String {
        val ex = extras.filter { it.second.isNotEmpty() }.joinToString("&") { (k, v) -> codifica(k) + "=" + codifica(v) }
        return "$base/$recurso/${codifica(tipo)}/${codifica(id)}" + (if (ex.isEmpty()) "" else "/$ex") + ".json"
    }

    /** La página de configurar de un addon (la suya: `/configure`). */
    fun configurar(manifiestoUrl: String): String = base(manifiestoUrl) + "/configure"
}

/** Pedir algo por la red. Se cambia en las pruebas (un addon de mentira). */
interface Http {
    /** Devuelve el cuerpo. Los errores no llevan la dirección (puede llevar secretos). */
    fun get(url: String, cabeceras: Map<String, String> = emptyMap()): String
}

/** Por HTTP de verdad. */
class HttpJava(private val espera: Int = 15_000) : Http {
    override fun get(url: String, cabeceras: Map<String, String>): String {
        val c = try { URL(url).openConnection() as HttpURLConnection } catch (e: Exception) { throw IOException("Dirección no válida") }
        c.connectTimeout = espera; c.readTimeout = espera * 2
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "Mostaza/" + VERSION + " (Android)")
        cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            val codigo = try { c.responseCode } catch (e: IOException) { throw IOException("Sin conexión con el addon") }
            if (codigo !in 200..299) throw IOException("El addon respondió $codigo")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally { c.disconnect() }
    }
}

/** Lo que se le pide a un addon. */
class ClienteStremio(private val http: Http) {
    fun manifiesto(url: String): Manifiesto = Protocolo.manifiesto(http.get(Protocolo.normaliza(url)))

    fun catalogo(manifiestoUrl: String, c: Catalogo, extras: List<Pair<String, String>> = emptyList()): List<MetaBreve> =
        Protocolo.metas(http.get(Protocolo.url(Protocolo.base(manifiestoUrl), "catalog", c.tipo, c.id, extras)))

    fun meta(manifiestoUrl: String, tipo: String, id: String): Meta? = Protocolo.meta(http.get(Protocolo.url(Protocolo.base(manifiestoUrl), "meta", tipo, id)))

    fun fuentes(manifiestoUrl: String, tipo: String, videoId: String): List<Fuente> = Protocolo.fuentes(http.get(Protocolo.url(Protocolo.base(manifiestoUrl), "stream", tipo, videoId)))

    /** Los subtítulos de un vídeo: el id es el del vídeo y los extras, lo que se sepa del archivo (`videoHash`, `videoSize`, `filename`). */
    fun subtitulos(manifiestoUrl: String, tipo: String, videoId: String, f: Fuente? = null): List<Subtitulo> {
        val ex = listOfNotNull(f?.hashVideo?.takeIf { it.isNotBlank() }?.let { "videoHash" to it }, f?.tamano?.takeIf { it > 0 }?.let { "videoSize" to it.toString() },
            f?.archivo?.takeIf { it.isNotBlank() }?.let { "filename" to it })
        return Protocolo.subtitulos(http.get(Protocolo.url(Protocolo.base(manifiestoUrl), "subtitles", tipo, videoId, ex)))
    }
}
