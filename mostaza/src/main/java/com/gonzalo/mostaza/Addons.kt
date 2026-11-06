package com.gonzalo.mostaza

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.DiscoCifrado
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
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
 * Tus addons. La lista (en orden) es tuya y viaja con la cuenta, pero **solo en `/claves/<uid>`** (`mostaza_addons`): sus direcciones
 * pueden llevar la clave de tu debrid, y `/claves` es lo único de la cuenta que no puede leer ni tu pareja (las reglas). Nunca van a
 * `/users/<uid>` (lo que tu pareja sí puede leer), ni a un registro, ni a un mensaje de error. En el dispositivo, la copia de las claves
 * está cifrada (la de la familia). Los manifiestos se guardan aquí (es lo que dice cada addon: se vuelve a pedir cuando hace falta).
 *
 * Sin nada puesto, el único addon es Cinemeta, el oficial de Stremio para las fichas de películas y series. Ningún addon de torrents ni
 * de nada parecido viene puesto: los pones tú.
 */
object Addons {
    const val CLAVE = "mostaza_addons"
    /** Cinemeta: el addon oficial de Stremio con las fichas (catálogos, búsqueda y datos) de películas y series. */
    const val CINEMETA = "https://v3-cinemeta.strem.io/manifest.json"
    /** Una sugerencia (no viene puesta): los subtítulos oficiales de Stremio. */
    const val OPENSUBTITLES = "https://opensubtitles-v3.strem.io/manifest.json"

    /** Sube al cambiar tus addons (aquí o en otro dispositivo). */
    var version by mutableIntStateOf(0)
        private set

    @Volatile var http: Http = HttpJava()
    val cliente: ClienteStremio get() = ClienteStremio(http)
    private var appCtx: Context? = null
    private val manifiestos = ConcurrentHashMap<String, Manifiesto>()

    fun cargar(c: Context) {
        if (appCtx != null) return
        appCtx = c.applicationContext
        leeCache()
        LaFamilia.alCambiar { version++ }
        LaFamilia.alEntrar { kotlin.concurrent.thread { runCatching { pasaLoDeSinCuenta() } } }
    }

    // ---------------------------------------------------------------- la lista (en la cuenta, en /claves)

    /** Sin cuenta todavía: lo que instalas se queda aquí (cifrado) y pasa a la cuenta al entrar. */
    private fun sinCuenta() = appCtx?.let { DiscoCifrado.de(it, File(it.filesDir, "mostaza_seguro")) }

    fun lista(): List<AddonInstalado> {
        version
        val texto = runCatching { LaFamilia.familia.clave(CLAVE) }.getOrDefault("").ifBlank { sinCuenta()?.leeTexto("addons.json").orEmpty() }
        if (texto.isBlank()) return listOf(AddonInstalado(CINEMETA, "Cinemeta"))
        return deTexto(texto)
    }

    fun deTexto(t: String): List<AddonInstalado> = runCatching { Json.lee(t) }.getOrNull().comoLista().mapNotNull { x ->
        val m = x.comoMapa(); val u = m["u"].comoTexto()
        if (u.isBlank()) null else AddonInstalado(u, m["n"].comoTexto())
    }.distinctBy { it.url }

    fun aTexto(l: List<AddonInstalado>): String = Json.escribe(l.map { mapOf("u" to it.url, "n" to it.nombre) })

    /** Guarda la lista (en `/claves`; sin cuenta, aquí hasta que entres). */
    fun guarda(l: List<AddonInstalado>) {
        val t = aTexto(l.distinctBy { it.url })
        val fam = runCatching { LaFamilia.familia }.getOrNull()
        if (fam != null && fam.sesion.dentro) fam.ponClave(CLAVE, t) else sinCuenta()?.escribeTexto("addons.json", t)
        version++
    }

    private fun pasaLoDeSinCuenta() {
        val d = sinCuenta() ?: return
        val t = d.leeTexto("addons.json") ?: return
        val fam = LaFamilia.familia
        if (!fam.sesion.dentro) return
        if (fam.clave(CLAVE).isBlank()) fam.ponClave(CLAVE, t)
        d.borra("addons.json")
        version++
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

    // ---------------------------------------------------------------- los manifiestos (de cada dispositivo: una caché)

    private fun cacheFile() = appCtx?.let { File(it.filesDir, "addons_manifiestos.json") }
    private fun hash(u: String) = MessageDigest.getInstance("SHA-256").digest(u.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    private fun leeCache() {
        val f = cacheFile() ?: return
        // (por el hash de la dirección: en el archivo no queda ninguna)
        val m = runCatching { Json.lee(f.readText()) }.getOrNull().comoMapa()
        porHash.clear(); m.forEach { (h, v) -> runCatching { Protocolo.manifiesto(v.comoMapa()) }.getOrNull()?.let { porHash[h] = it; crudos[h] = v.comoMapa() } }
    }
    private val porHash = ConcurrentHashMap<String, Manifiesto>()
    private val crudos = ConcurrentHashMap<String, Map<String, Any?>>()

    private fun guardaCache() {
        val f = cacheFile() ?: return
        runCatching { f.writeText(Json.escribe(crudos)) }
    }

    /** Pide el manifiesto (y lo guarda en la caché). Lanza un error con un texto para ti (sin la dirección) si no es un addon. */
    private fun pide(url: String): Manifiesto {
        val txt = http.get(Protocolo.normaliza(url))
        val crudo = runCatching { Json.lee(txt).comoMapa() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: throw java.io.IOException("Eso no es el manifiesto de un addon")
        val m = Protocolo.manifiesto(crudo)
        manifiestos[url] = m; crudos[hash(url)] = crudo; porHash[hash(url)] = m; guardaCache()
        return m
    }

    /** El manifiesto de un addon instalado: el de la caché o, si no, se pide. Bloquea (red). */
    fun manifiesto(url: String, refrescar: Boolean = false): Manifiesto? {
        if (!refrescar) { manifiestos[url]?.let { return it }; porHash[hash(url)]?.let { manifiestos[url] = it; return it } }
        return runCatching { pide(url) }.getOrNull() ?: manifiestos[url] ?: porHash[hash(url)]
    }

    /** Tus addons con su manifiesto (los que no responden y no están en la caché, fuera). Bloquea (red). */
    suspend fun conManifiesto(): List<Addon> = withContext(Dispatchers.IO) {
        coroutineScope {
            lista().mapIndexed { i, a -> async { manifiesto(a.url)?.let { Addon(a.url, it, i) } } }.awaitAll().filterNotNull()
        }
    }

    // ---------------------------------------------------------------- lo que se pide a todos

    /** La ficha completa: del primer addon (en tu orden) que da `meta` para ese tipo e id. */
    suspend fun meta(tipo: String, id: String): Meta? = withContext(Dispatchers.IO) {
        for (a in conManifiesto()) if (a.manifiesto.soporta("meta", tipo, id)) runCatching { cliente.meta(a.url, tipo, id) }.getOrNull()?.let { return@withContext it }
        null
    }

    /** Las fuentes de un vídeo, de todos tus addons que las dan, cada una con su addon y su sitio. A la vez, con un tope de tiempo. */
    suspend fun fuentes(tipo: String, videoId: String, alLlegar: (List<Candidata>) -> Unit = {}): List<Candidata> = withContext(Dispatchers.IO) {
        val addons = conManifiesto().filter { it.manifiesto.soporta("stream", tipo, videoId) }
        val todas = java.util.Collections.synchronizedList(ArrayList<Candidata>())
        coroutineScope {
            addons.map { a ->
                async {
                    val l = withTimeoutOrNull(25_000) { runCatching { cliente.fuentes(a.url, tipo, videoId) }.getOrDefault(emptyList()) }.orEmpty()
                    val cs = l.mapIndexed { i, f -> Candidata(f, a.nombre, a.orden, i) }
                    todas.addAll(cs)
                    alLlegar(todas.toList())
                }
            }.awaitAll()
        }
        todas.toList()
    }

    /** Los subtítulos de un vídeo, de tus addons de subtítulos. */
    suspend fun subtitulos(tipo: String, videoId: String, f: Fuente?): List<Subtitulo> = withContext(Dispatchers.IO) {
        coroutineScope {
            conManifiesto().filter { it.manifiesto.soporta("subtitles", tipo, videoId) }
                .map { a -> async { withTimeoutOrNull(15_000) { runCatching { cliente.subtitulos(a.url, tipo, videoId, f) }.getOrDefault(emptyList()) }.orEmpty() } }
                .awaitAll().flatten()
        }
    }

    /** Un catálogo de un addon (con sus extras: género, búsqueda, página). */
    suspend fun catalogo(a: Addon, c: Catalogo, extras: List<Pair<String, String>> = emptyList()): List<MetaBreve> = withContext(Dispatchers.IO) {
        cliente.catalogo(a.url, c, extras)
    }

    /** Buscar en todos los catálogos que buscan. Cada resultado, con su addon y su catálogo. */
    suspend fun busca(q: String): List<Triple<Addon, Catalogo, List<MetaBreve>>> = withContext(Dispatchers.IO) {
        coroutineScope {
            conManifiesto().flatMap { a -> a.manifiesto.buscables.map { c -> a to c } }
                .map { (a, c) -> async { withTimeoutOrNull(20_000) { runCatching { Triple(a, c, cliente.catalogo(a.url, c, listOf("search" to q))) }.getOrNull() } } }
                .awaitAll().filterNotNull().filter { it.third.isNotEmpty() }
        }
    }

    /** (Para las pruebas.) */
    fun olvidaParaPruebas() { manifiestos.clear(); porHash.clear(); crudos.clear(); appCtx = null; http = HttpJava() }
}
