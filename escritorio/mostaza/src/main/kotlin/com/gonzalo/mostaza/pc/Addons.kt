package com.gonzalo.mostaza.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Un addon instalado: la dirección de su manifiesto (que puede llevar tu clave del debrid dentro) y su nombre (para enseñarlo sin red). */
data class AddonInstalado(val url: String, val nombre: String = "")

/** Un addon con su manifiesto ya leído. */
data class Addon(val url: String, val manifiesto: Manifiesto, val orden: Int) {
    val nombre: String get() = manifiesto.nombre
}

/**
 * Tus addons, los mismos que en el móvil. La lista (en orden) viaja con la cuenta, pero **solo en `/claves/<uid>`** (`mostaza_addons`): sus
 * direcciones pueden llevar la clave de tu debrid, y `/claves` es lo único de la cuenta que no puede leer ni tu pareja. Nunca van a
 * `/users/<uid>`, ni a un registro, ni a un mensaje de error, ni a la línea de órdenes de otro programa. En el ordenador, la copia de las
 * claves está cifrada con una clave del llavero del sistema (`DiscoSeguro`). Los manifiestos se guardan aquí por la huella de su
 * dirección (en el archivo no queda ninguna dirección).
 *
 * Sin nada puesto, el único addon es Cinemeta, el oficial de Stremio para las fichas de películas y series.
 */
object Addons {
    const val CLAVE = "mostaza_addons"
    const val CINEMETA = "https://v3-cinemeta.strem.io/manifest.json"
    const val OPENSUBTITLES = "https://opensubtitles-v3.strem.io/manifest.json"

    @Volatile var http: Http = HttpJava()
    val cliente: ClienteStremio get() = ClienteStremio(http)
    /** En las pruebas: la lista sin cuenta (texto como el de `/claves`). */
    @Volatile var textoFijo: String? = null
    private val manifiestos = ConcurrentHashMap<String, Manifiesto>()
    private val porHash = ConcurrentHashMap<String, Manifiesto>()
    private val crudos = ConcurrentHashMap<String, Map<String, Any?>>()
    @Volatile private var cacheLeida = false

    /** Sube con cada cambio de la cuenta (también de las claves): las pantallas vuelven a leer. */
    val version: Int get() = LaFamilia.cambios

    fun lista(): List<AddonInstalado> {
        val texto = textoFijo ?: runCatching { LaFamilia.familia.clave(CLAVE) }.getOrDefault("")
        if (texto.isBlank()) return listOf(AddonInstalado(CINEMETA, "Cinemeta"))
        return deTexto(texto)
    }

    fun deTexto(t: String): List<AddonInstalado> = runCatching { Json.lee(t) }.getOrNull().comoLista().mapNotNull { x ->
        val m = x.comoMapa(); val u = m["u"].comoTexto()
        if (u.isBlank()) null else AddonInstalado(u, m["n"].comoTexto())
    }.distinctBy { it.url }

    fun aTexto(l: List<AddonInstalado>): String = Json.escribe(l.map { mapOf("u" to it.url, "n" to it.nombre) })

    /** Guarda la lista (en `/claves`, que sube sola). */
    fun guarda(l: List<AddonInstalado>) {
        val t = aTexto(l.distinctBy { it.url })
        if (textoFijo != null) { textoFijo = t; return }
        LaFamilia.familia.ponClave(CLAVE, t)
    }

    /** Instala un addon por la dirección de su manifiesto (lo lee antes: si no es un addon, no se instala). Devuelve su manifiesto. */
    suspend fun instala(url: String): Manifiesto = withContext(Dispatchers.IO) {
        val u = Protocolo.normaliza(url)
        val m = pide(u)
        val l = lista()
        guarda(if (l.any { it.url == u }) l.map { if (it.url == u) AddonInstalado(u, m.nombre) else it } else l + AddonInstalado(u, m.nombre))
        m
    }

    /** Cambia la dirección de un addon (al configurarlo, su página da otra) sin cambiar su sitio en la lista. */
    suspend fun reemplaza(vieja: String, nueva: String): Manifiesto = withContext(Dispatchers.IO) {
        val u = Protocolo.normaliza(nueva)
        val m = pide(u)
        val l = lista()
        guarda(if (l.any { it.url == vieja }) l.map { if (it.url == vieja) AddonInstalado(u, m.nombre) else it }.distinctBy { it.url } else l + AddonInstalado(u, m.nombre))
        m
    }

    fun quita(url: String) = guarda(lista().filter { it.url != url })

    fun mueve(url: String, d: Int) {
        val l = lista().toMutableList()
        val i = l.indexOfFirst { it.url == url }; val j = i + d
        if (i < 0 || j !in l.indices) return
        val x = l.removeAt(i); l.add(j, x)
        guarda(l)
    }

    // ---------------------------------------------------------------- los manifiestos (de este ordenador: una caché)

    private fun cacheFile() = File(Local.dir, "addons_manifiestos.json")
    private fun hash(u: String) = MessageDigest.getInstance("SHA-256").digest(u.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    @Synchronized private fun leeCache() {
        if (cacheLeida) return
        cacheLeida = true
        val m = runCatching { Json.lee(cacheFile().readText()) }.getOrNull().comoMapa()
        m.forEach { (h, v) -> runCatching { Protocolo.manifiesto(v.comoMapa()) }.getOrNull()?.let { porHash[h] = it; crudos[h] = v.comoMapa() } }
    }

    private fun guardaCache() { runCatching { cacheFile().writeText(Json.escribe(crudos)) } }

    /** Pide el manifiesto (y lo guarda en la caché). Lanza un error con un texto para ti (sin la dirección) si no es un addon. */
    private fun pide(url: String): Manifiesto {
        leeCache()
        val txt = http.get(Protocolo.normaliza(url))
        val crudo = runCatching { Json.lee(txt).comoMapa() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: throw java.io.IOException("Eso no es el manifiesto de un addon")
        val m = Protocolo.manifiesto(crudo)
        manifiestos[url] = m; crudos[hash(url)] = crudo; porHash[hash(url)] = m; guardaCache()
        return m
    }

    /** El manifiesto de un addon instalado: el de la caché o, si no, se pide. Bloquea (red). */
    fun manifiesto(url: String, refrescar: Boolean = false): Manifiesto? {
        leeCache()
        if (!refrescar) { manifiestos[url]?.let { return it }; porHash[hash(url)]?.let { manifiestos[url] = it; return it } }
        return runCatching { pide(url) }.getOrNull() ?: manifiestos[url] ?: porHash[hash(url)]
    }

    /** Tus addons con su manifiesto (los que no responden y no están en la caché, fuera). Bloquea (red). */
    suspend fun conManifiesto(): List<Addon> = withContext(Dispatchers.IO) {
        coroutineScope { lista().mapIndexed { i, a -> async { manifiesto(a.url)?.let { Addon(a.url, it, i) } } }.awaitAll().filterNotNull() }
    }

    // ---------------------------------------------------------------- lo que se pide a todos

    /** La ficha completa: del primer addon (en tu orden) que da `meta` para ese tipo e id. */
    suspend fun meta(tipo: String, id: String): Meta? = withContext(Dispatchers.IO) {
        for (a in conManifiesto()) if (a.manifiesto.soporta("meta", tipo, id)) runCatching { cliente.meta(a.url, tipo, id) }.getOrNull()?.let { return@withContext it }
        null
    }

    /**
     * Las fuentes de un vídeo, de todos tus addons que las dan, cada una con su addon y su sitio. A la vez, con un tope de tiempo.
     * (Aquí se añadiría, cuando llegue al ordenador, la fuente «Tu kDrive»: el archivo de esa película o ese episodio en tu kDrive, como una
     * candidata más con su propio addon —«Tu kDrive»— y su orden, para que el elector la ordene igual que las demás. Ver [fuentesExtra].)
     */
    suspend fun fuentes(tipo: String, videoId: String, alLlegar: (List<Candidata>) -> Unit = {}): List<Candidata> = withContext(Dispatchers.IO) {
        val addons = conManifiesto().filter { it.manifiesto.soporta("stream", tipo, videoId) }
        val todas = java.util.Collections.synchronizedList(ArrayList<Candidata>())
        coroutineScope {
            val deAddons = addons.map { a ->
                async {
                    val l = withTimeoutOrNull(25_000) { runCatching { cliente.fuentes(a.url, tipo, videoId) }.getOrDefault(emptyList()) }.orEmpty()
                    todas.addAll(l.mapIndexed { i, f -> Candidata(f, a.nombre, a.orden, i) })
                    alLlegar(todas.toList())
                }
            }
            val extra = fuentesExtra.map { f ->
                async { val l = withTimeoutOrNull(25_000) { runCatching { f(tipo, videoId) }.getOrDefault(emptyList()) }.orEmpty(); todas.addAll(l); alLlegar(todas.toList()) }
            }
            (deAddons + extra).awaitAll()
        }
        todas.toList()
    }

    /**
     * Otras fuentes que no son de un addon (el punto donde entrará «Tu kDrive»): cada una da sus candidatas para un tipo y un id de vídeo.
     * Hoy no hay ninguna en el ordenador.
     */
    val fuentesExtra = java.util.concurrent.CopyOnWriteArrayList<suspend (String, String) -> List<Candidata>>()

    /** Los subtítulos de un vídeo, de tus addons de subtítulos. */
    suspend fun subtitulos(tipo: String, videoId: String, f: Fuente?): List<Subtitulo> = withContext(Dispatchers.IO) {
        coroutineScope {
            conManifiesto().filter { it.manifiesto.soporta("subtitles", tipo, videoId) }
                .map { a -> async { withTimeoutOrNull(15_000) { runCatching { cliente.subtitulos(a.url, tipo, videoId, f) }.getOrDefault(emptyList()) }.orEmpty() } }
                .awaitAll().flatten()
        }
    }

    suspend fun catalogo(a: Addon, c: Catalogo, extras: List<Pair<String, String>> = emptyList()): List<MetaBreve> = withContext(Dispatchers.IO) { cliente.catalogo(a.url, c, extras) }

    /** Buscar en todos los catálogos que buscan. Cada resultado, con su addon y su catálogo. */
    suspend fun busca(q: String): List<Triple<Addon, Catalogo, List<MetaBreve>>> = withContext(Dispatchers.IO) {
        coroutineScope {
            conManifiesto().flatMap { a -> a.manifiesto.buscables.map { c -> a to c } }
                .map { (a, c) -> async { withTimeoutOrNull(20_000) { runCatching { Triple(a, c, cliente.catalogo(a.url, c, listOf("search" to q))) }.getOrNull() } } }
                .awaitAll().filterNotNull().filter { it.third.isNotEmpty() }
        }
    }

    /** El sitio (host) de una dirección, para enseñarla sin lo que lleva dentro (la clave del debrid va en el camino). */
    fun sitioDe(url: String): String = runCatching { java.net.URI(Protocolo.normaliza(url)).host.orEmpty() }.getOrDefault("").ifBlank { "otro sitio" }

    /** (Para las pruebas.) */
    fun olvidaParaPruebas() { manifiestos.clear(); porHash.clear(); crudos.clear(); cacheLeida = false; http = HttpJava(); textoFijo = null; fuentesExtra.clear() }
}

/** Los catálogos de tus addons para la portada (sin TMDB), con una caché corta en memoria. */
class FilaCatalogo(val addon: Addon, val catalogo: Catalogo, val cosas: List<MetaBreve>) {
    val clave: String get() = "${addon.manifiesto.id}|${catalogo.tipo}|${catalogo.id}"
    val titulo: String get() = "${catalogo.nombre} · ${tiposNombre(catalogo.tipo)}"
}

object Catalogos {
    private val cache = ConcurrentHashMap<String, Pair<Long, List<MetaBreve>>>()

    fun mostrables(a: Addon): List<Catalogo> = a.manifiesto.catalogos.filter { !it.soloBusqueda && !it.generoObligatorio && !it.necesitaOtraCosa }
    fun explorables(a: Addon): List<Catalogo> = a.manifiesto.catalogos.filter { !it.soloBusqueda && !it.necesitaOtraCosa }

    suspend fun lista(a: Addon, c: Catalogo, extras: List<Pair<String, String>> = emptyList()): List<MetaBreve> {
        val k = a.url.hashCode().toString() + "|" + c.tipo + "|" + c.id + "|" + extras.joinToString("&") { it.first + "=" + it.second }
        cache[k]?.let { (t, l) -> if (System.currentTimeMillis() - t < 30 * 60_000L) return l }
        val l = runCatching { Addons.catalogo(a, c, extras) }.getOrDefault(emptyList())
        if (l.isNotEmpty()) cache[k] = System.currentTimeMillis() to l
        return l
    }

    suspend fun portada(): List<FilaCatalogo> {
        val addons = Addons.conManifiesto()
        val elegidas = AjustesPortada.filas()
        val pares = if (elegidas.isNotEmpty()) elegidas.mapNotNull { k ->
            val (aid, tipo, cid) = k.split('|').let { if (it.size < 3) return@mapNotNull null else Triple(it[0], it[1], it[2]) }
            addons.firstOrNull { it.manifiesto.id == aid }?.let { a -> a.manifiesto.catalogos.firstOrNull { it.tipo == tipo && it.id == cid }?.let { a to it } }
        } else addons.flatMap { a -> mostrables(a).map { a to it } }.take(AjustesPortada.cuantas())
        return coroutineScope { pares.map { (a, c) -> async { FilaCatalogo(a, c, lista(a, c)) } }.awaitAll().filter { it.cosas.isNotEmpty() } }
    }
}

/** «Película», «Serie»… (como en el móvil) */
fun tipoNombre(t: String): String = when (t) { "movie" -> "Película"; "series" -> "Serie"; "channel" -> "Canal"; "tv" -> "Tele"; else -> t.replaceFirstChar { it.uppercase() } }
fun tiposNombre(t: String): String = when (t) { "movie" -> "Películas"; "series" -> "Series"; "channel" -> "Canales"; "tv" -> "Tele"; else -> t.replaceFirstChar { it.uppercase() } }
/** «2 h 22 min». */
fun duracion(min: Int): String = if (min <= 0) "" else if (min >= 60) "${min / 60} h" + (if (min % 60 > 0) " ${min % 60} min" else "") else "$min min"
