package com.gonzalo.mostaza.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.KDrive
import com.gonzalo.familia.pc.LaFamilia
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// (Lo de los nombres es el mismo código que en el móvil: mostaza/…/CineKDrive.kt)
/**
 * Lo que entiende Mostaza de los nombres de tus archivos de «Mi carpeta/Mostaza» del kDrive (sin red; ver los LEEME.txt de allí):
 *  - En «Películas»: el título y el año (`Dune Parte dos (2024).mkv`, `Dune.Part.Two.2024.1080p.mkv`), suelto o en su carpeta.
 *  - En «Series», una carpeta por serie: la carpeta de la serie (título y año) y, dentro, cada episodio con `S01E02` o `1x02` (suelto o en carpetas
 *    por temporada).
 *  - Si el nombre (o la carpeta) lleva su número de IMDb (`tt1234567`), manda ese.
 */
object NombresCine {
    val VIDEO = setOf("mkv", "mp4", "m4v", "avi", "webm", "mov", "ts", "m2ts", "wmv", "mpg", "mpeg")
    val SUBS = setOf("srt", "vtt", "ass", "ssa")

    data class Pelicula(val titulo: String, val ano: String, val imdb: String)
    data class Episodio(val serie: Pelicula, val temporada: Int, val episodio: Int)

    fun extension(n: String) = n.substringAfterLast('.', "").lowercase()
    fun esVideo(n: String) = extension(n) in VIDEO && !n.startsWith(".")
    fun esSub(n: String) = extension(n) in SUBS

    private val imdbRx = Regex("(?<![a-z0-9])(tt\\d{6,9})(?![0-9])", RegexOption.IGNORE_CASE)
    private val anoRx = Regex("(?<![0-9])(19[0-9]{2}|20[0-9]{2})(?![0-9])")
    private val epRx = listOf(Regex("(?i)s(\\d{1,2})\\s*[._ -]?\\s*e(\\d{1,3})"), Regex("(?i)(?<![0-9])(\\d{1,2})x(\\d{1,3})(?![0-9])"))
    /** Lo que suele venir detrás del título en un nombre de archivo: desde ahí se corta. */
    private val ruido = Regex("(?i)(?<![a-z])(2160p|1080p|720p|576p|480p|4k|uhd|bluray|blu-ray|brrip|bdrip|bdremux|remux|web-?dl|webrip|web|hdtv|dvdrip|hdrip|x264|x265|h\\.?264|h\\.?265|hevc|avc|hdr10?|dv|dolby|atmos|ac3|dts|aac|ddp?5\\.?1|castellano|español|espanol|spanish|latino|vose|dual|multi|subs?|proper|repack|extended|edicion|edición|remastered|imax)(?![a-z])")

    /** El título y el año de un nombre («Dune.Part.Two.2024.1080p.mkv» → «Dune Part Two», «2024»). */
    fun pelicula(nombre: String): Pelicula {
        val sinExt = if (esVideo(nombre) || esSub(nombre)) nombre.substringBeforeLast('.') else nombre
        val imdb = imdbRx.find(sinExt)?.groupValues?.get(1)?.lowercase().orEmpty()
        var t = sinExt.replace(imdbRx, " ")
        // El año: el último que hay (por «2001 Una odisea del espacio (1968)»), pero no si es todo el título
        val anos = anoRx.findAll(t).toList()
        val ano = anos.lastOrNull()?.takeIf { it.range.first > 0 }
        var corte = t.length
        ano?.let { corte = minOf(corte, it.range.first) }
        ruido.find(t)?.let { corte = minOf(corte, it.range.first) }
        epRx.forEach { r -> r.find(t)?.let { corte = minOf(corte, it.range.first) } }
        t = t.substring(0, corte)
        val titulo = t.replace(Regex("[._]+"), " ").replace(Regex("[\\[\\](){}]"), " ").replace(Regex("\\s+-\\s*$"), " ").replace(Regex("\\s+"), " ").trim().trimEnd('-').trim()
        return Pelicula(titulo, ano?.value.orEmpty(), imdb)
    }

    /** Un episodio por su ruta dentro de «Series» ([ruta]: las carpetas, la primera es la de la serie) y su nombre; null si no lo es. */
    fun episodio(ruta: List<String>, nombre: String): Episodio? {
        val serie = ruta.firstOrNull()?.let { pelicula(it) } ?: return null
        val donde = (ruta.drop(1) + nombre).joinToString(" ")
        for (r in epRx) r.find(nombre)?.let { m -> return Episodio(serie, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        // «Temporada 1/Episodio 3.mkv» o «Temporada 1/03.mkv»
        val t = Regex("(?i)(temporada|season|t)\\s*(\\d{1,2})").find(donde)?.groupValues?.get(2)?.toIntOrNull() ?: return null
        val e = Regex("(?i)(episodio|episode|capitulo|capítulo|e|ep)\\s*(\\d{1,3})").find(nombre.substringBeforeLast('.'))?.groupValues?.get(2)?.toIntOrNull()
            ?: Regex("^(\\d{1,3})(?![0-9])").find(nombre)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return Episodio(serie, t, e)
    }

    /** Lo mismo escrito igual para comparar («Dune: Parte dos» y «dune parte dos»). */
    fun normal(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    /** De los resultados de una búsqueda ([candidatos]: id, título, año), el que es: mismo título, y año si se sabe; si no, el primero con ese año. */
    fun elige(p: Pelicula, candidatos: List<Triple<String, String, String>>): String? {
        val n = normal(p.titulo)
        val conAno = if (p.ano.isNotBlank()) candidatos.filter { it.third == p.ano || (it.third.toIntOrNull()?.let { a -> kotlin.math.abs(a - p.ano.toInt()) <= 1 } == true) } else candidatos
        return (conAno.firstOrNull { normal(it.second) == n } ?: conAno.firstOrNull() ?: candidatos.firstOrNull { normal(it.second) == n })?.first
    }
}

/** Un archivo de vídeo de tu kDrive, ya sabido qué es: [videoId] es el de Stremio (`tt…` o `tt…:T:E`). */
data class ArchivoCine(val fileId: Long, val nombre: String, val tamano: Long, val videoId: String, val tipo: String, val subtitulos: List<Pair<Long, String>> = emptyList())

/**
 * Tus películas y series de tu kDrive («Mi carpeta/Mostaza/Películas» y «…/Series»), como en el móvil: se miran cada poco, se averigua
 * qué es cada archivo por su nombre (TMDB si tienes la clave; si no, tus addons) y salen como la fuente «Tu kDrive», la primera. Tu token
 * de kDrive solo va a kDrive (a mpv por su socket, como las demás cabeceras; los subtítulos de fuera se bajan aquí sin él) y el
 * reproductor del sistema no se ofrece con estas fuentes.
 */
object CineKDrive {
    const val ADDON = "Tu kDrive"
    const val CARPETA = "Mostaza"

    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    @Volatile var kdrive: KDrive = KDrive()
    @Volatile private var indice: List<ArchivoCine> = emptyList()
    @Volatile private var cuando = 0L
    private val mutex = Mutex()
    private const val VIDA = 10 * 60_000L

    private fun token() = LaFamilia.clave("kdrive_token")
    private fun drive() = LaFamilia.clave("kdrive_id")
    fun disponible() = runCatching { kdrive.activo }.getOrDefault(false)
    fun direccion(id: Long) = "${kdrive.api}/2/drive/${drive()}/files/$id/download"
    fun cabeceras() = mapOf("Authorization" to "Bearer ${token()}")

    /** Se pone en marcha con la app: sus fuentes entran en las de cada vídeo. */
    fun engancha() { if (Addons.fuentesExtra.none { it === buscaFuentes }) Addons.fuentesExtra += buscaFuentes }
    private val buscaFuentes: suspend (String, String) -> List<Candidata> = { _, videoId -> fuentes(videoId) }

    suspend fun indice(ya: Boolean = false): List<ArchivoCine> = withContext(Dispatchers.IO) {
        if (!disponible()) return@withContext emptyList()
        mutex.withLock {
            if (!ya && System.currentTimeMillis() - cuando < VIDA) return@withLock indice
            val nuevo = runCatching { mira() }.getOrNull()
            if (nuevo != null) { indice = nuevo; cuando = System.currentTimeMillis(); version++ }
            indice
        }
    }

    private data class Visto(val id: Long, val nombre: String, val tamano: Long, val ruta: List<String>)

    private fun recorre(dir: Long, ruta: List<String>, out: MutableList<Visto>, hondo: Int) {
        if (hondo > 4 || out.size > 5000) return
        for (x in kdrive.dentro(dir)) {
            if (x.carpeta) recorre(x.id, ruta + x.nombre, out, hondo + 1)
            else if (NombresCine.esVideo(x.nombre) || NombresCine.esSub(x.nombre)) out += Visto(x.id, x.nombre, x.tamano, ruta)
        }
    }

    private suspend fun mira(): List<ArchivoCine> {
        val raiz = kdrive.dentro(kdrive.miCarpeta()).firstOrNull { it.carpeta && it.nombre.equals(CARPETA, ignoreCase = true) }?.id ?: return emptyList()
        val dentro = kdrive.dentro(raiz)
        val pelis = ArrayList<Visto>(); val series = ArrayList<Visto>()
        dentro.firstOrNull { it.carpeta && NombresCine.normal(it.nombre) == "peliculas" }?.let { recorre(it.id, emptyList(), pelis, 0) }
        dentro.firstOrNull { it.carpeta && NombresCine.normal(it.nombre) == "series" }?.let { recorre(it.id, emptyList(), series, 0) }
        val out = ArrayList<ArchivoCine>()
        for (v in pelis.filter { NombresCine.esVideo(it.nombre) }) {
            val delArchivo = NombresCine.pelicula(v.nombre)
            val p = if (delArchivo.ano.isBlank() && delArchivo.imdb.isBlank() && v.ruta.isNotEmpty()) NombresCine.pelicula(v.ruta.last()) else delArchivo
            val id = averigua(p, tv = false) ?: continue
            out += ArchivoCine(v.id, v.nombre, v.tamano, id, "movie", subsDe(v, pelis))
        }
        for (v in series.filter { NombresCine.esVideo(it.nombre) }) {
            val e = NombresCine.episodio(v.ruta, v.nombre) ?: continue
            val id = averigua(e.serie, tv = true) ?: continue
            out += ArchivoCine(v.id, v.nombre, v.tamano, "$id:${e.temporada}:${e.episodio}", "series", subsDe(v, series))
        }
        return out
    }

    private fun subsDe(v: Visto, todos: List<Visto>): List<Pair<Long, String>> {
        val base = v.nombre.substringBeforeLast('.').lowercase()
        return todos.filter { it.ruta == v.ruta && NombresCine.esSub(it.nombre) && it.nombre.lowercase().startsWith(base) }.map { s ->
            val medio = s.nombre.substringBeforeLast('.').drop(base.length).trim('.', '_', ' ', '-').lowercase()
            s.id to when { medio.startsWith("es") || medio.contains("spa") || medio.contains("cast") -> "spa"; medio.startsWith("en") || medio.contains("eng") -> "eng"; else -> medio }
        }
    }

    private suspend fun averigua(p: NombresCine.Pelicula, tv: Boolean): String? {
        if (p.imdb.isNotBlank()) return p.imdb
        if (p.titulo.isBlank()) return null
        val k = "kdrive:" + (if (tv) "tv|" else "movie|") + NombresCine.normal(p.titulo) + "|" + p.ano
        (Local.lee(k) as? String)?.let { return it.ifBlank { null } }
        val id = runCatching { buscaEnTmdb(p, tv) }.getOrNull() ?: runCatching { buscaEnAddons(p, tv) }.getOrNull()
        Local.pon(k, id.orEmpty())
        return id
    }

    private fun buscaEnTmdb(p: NombresCine.Pelicula, tv: Boolean): String? {
        if (!Tmdb.activo) return null
        val params = listOf("query" to p.titulo, "include_adult" to "false") + if (p.ano.isNotBlank()) listOf((if (tv) "first_air_date_year" else "primary_release_year") to p.ano) else emptyList()
        var lista = Json.lee(Tmdb.pide(if (tv) "search/tv" else "search/movie", params)).comoMapa()["results"].comoLista().map { it.comoMapa() }
        if (lista.isEmpty() && p.ano.isNotBlank()) lista = Json.lee(Tmdb.pide(if (tv) "search/tv" else "search/movie", params.take(2))).comoMapa()["results"].comoLista().map { it.comoMapa() }
        val cands = lista.map { m -> Triple(m["id"].comoLong().toString(), (if (tv) m["name"] else m["title"]).comoTexto(), TmdbDatos.ano((if (tv) m["first_air_date"] else m["release_date"]).comoTexto())) } +
            lista.map { m -> Triple(m["id"].comoLong().toString(), (if (tv) m["original_name"] else m["original_title"]).comoTexto(), TmdbDatos.ano((if (tv) m["first_air_date"] else m["release_date"]).comoTexto())) }
        val tmdb = NombresCine.elige(p, cands)?.toLongOrNull() ?: return null
        val ext = Json.lee(Tmdb.pide((if (tv) "tv/" else "movie/") + tmdb + "/external_ids", vida = 7 * 24 * 3600_000L)).comoMapa()
        return ext["imdb_id"].comoTexto().takeIf { it.startsWith("tt") }
    }

    private suspend fun buscaEnAddons(p: NombresCine.Pelicula, tv: Boolean): String? {
        val tipo = if (tv) "series" else "movie"
        val l = Addons.busca(p.titulo).filter { it.second.tipo == tipo }.flatMap { it.third }.filter { it.id.startsWith("tt") }
        return NombresCine.elige(p, l.map { Triple(it.id, it.nombre, it.ano.take(4)) })
    }

    suspend fun fuentes(videoId: String): List<Candidata> {
        if (!disponible()) return emptyList()
        val cab = cabeceras()
        return indice().filter { it.videoId == videoId }.mapIndexed { i, a ->
            Candidata(Fuente(nombre = "$ADDON\n${a.nombre}", descripcion = a.nombre, url = direccion(a.fileId), cabeceras = cab, tamano = a.tamano, archivo = a.nombre,
                subtitulos = a.subtitulos.map { (id, idioma) -> Subtitulo("kdrive-$id", direccion(id), idioma, "Tu kDrive") }, privada = true), ADDON, -1, i)
        }
    }

    fun titulos(): List<Pair<String, String>> = indice.map { it.videoId.substringBefore(':') to it.tipo }.distinct()
}
