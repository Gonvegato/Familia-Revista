package com.gonzalo.mostaza

import android.content.Context
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.PortadasFamilia
import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Fechas
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lo que Azabache tenía de películas y series pasa a ser de Mostaza, una vez en cada dispositivo (como los juegos a Hiedra y los libros a
 * Marfil). Azabache guardaba cada ficha de sus categorías «Películas» y «Series» (las de buscador TMDB y TVmaze) en su colección
 * (`azabache/items_<categoría>~<id>` las vistas y valoradas, `azabache/iplays_<categoría>~<id>` las pendientes, en curso y abandonadas).
 * Cada una pasa a `titulos/<ref>` con TODO lo suyo:
 *  - el estado (vistas → `terminado`; pendiente, viendo, abandonada), cuándo la añadiste y cuándo la terminaste, el favorito;
 *  - la portada (la dirección de internet en `pu`; si la elegiste de tus archivos y está en este dispositivo, la imagen a la cuenta) y el fondo;
 *  - la valoración, como en los juegos y los libros, en el mismo nodo: `nota`, `crit`, `resena`, `azid` (el número de siempre de la
 *    ficha: lo usan tus sagas, tu diario y tus maquetas), `azk` (su categoría) y `az` (lo demás de la ficha tal cual: apuntes, dónde la
 *    viste, director…);
 *  - el tiempo que tenía apuntado (`tiempo/<ref>~azabache`) y, en las series, los episodios marcados (`progreso/<tt>:<t>:<e>`).
 * La `ref` es el código de IMDb (`tt…`, el que usan los addons de Stremio), sacado de TMDB (`external_ids`, con la clave de TMDB de tu
 * cuenta) o de TVmaze (`externals.imdb`); si no se puede, `tmdb:<id>` o `tvmaze:<id>` (se completa después) y se guardan siempre los ids de
 * antes (`tmdb`, `tvmaze`). Nunca se borra ni se cambia nada de Azabache: sus nodos se quedan como estaban (los sigue leyendo Azabache de
 * escritorio). Lo que ya está en Mostaza manda: de una ficha que ya existe solo se ponen los campos que le faltan, y una que borraste en
 * Mostaza no vuelve. Al terminar se apunta en la cuenta (`azabache/a_mostaza`): desde entonces Azabache lee las películas y series de Mostaza.
 */
object MigraAzabache {
    private val enMarcha = AtomicBoolean(false)
    @Volatile var http: Http = HttpJava()
    @Volatile var tmdbApi = "https://api.themoviedb.org/3"
    @Volatile var tvmazeApi = "https://api.tvmaze.com"
    /** (En las pruebas se hace en el momento.) */
    @Volatile var enElMomento = false

    class Resultado(val titulos: Int, val nuevos: Int, val conImdb: Int, val episodios: Int)

    private fun p(c: Context) = c.getSharedPreferences("mostaza_migracion", Context.MODE_PRIVATE)
    private fun clave() = "hecha:" + runCatching { LaFamilia.familia.sesion.uid }.getOrDefault("")
    fun hecha(c: Context) = p(c).getBoolean(clave(), false)

    /** Si hace falta (hay cuenta, las colecciones están bajadas y aún no se ha hecho aquí), se hace en segundo plano. */
    fun siHaceFalta(c: Context) {
        val fam = runCatching { LaFamilia.familia }.getOrNull() ?: return
        if (!fam.sesion.dentro || hecha(c)) return
        val app = c.applicationContext
        if (enElMomento) { runCatching { haz(app) }; return }
        kotlin.concurrent.thread(name = "mostaza-de-azabache") { runCatching { haz(app) } }
    }

    /** La hace ya (bloquea: red). Devuelve lo que ha pasado, o null si aún no se puede (sin la cuenta bajada). */
    fun haz(c: Context): Resultado? {
        if (!enMarcha.compareAndSet(false, true)) return null
        try {
            if (hecha(c)) return null
            val az = LaFamilia.coleccion("azabache"); val tit = LaFamilia.coleccion("titulos")
            val prog = LaFamilia.coleccion("progreso"); val tiempo = LaFamilia.coleccion("tiempo")
            if (!LaFamilia.familia.sesion.dentro) return null
            if (!az.completa || !tit.completa || !prog.completa) { LaFamilia.familia.programa(0); return null }
            val r = migra(c, az, tit, prog, tiempo)
            p(c).edit().putBoolean(clave(), true).putLong("cuando", System.currentTimeMillis()).apply()
            Biblioteca.refresca()
            return r
        } finally { enMarcha.set(false) }
    }

    /** Las categorías de películas (TMDB) y series (TVmaze) de Azabache: id → «movie»/«series». */
    fun categorias(az: Coleccion): Map<Int, String> {
        val n = az.nodo("kinds")?.takeIf { !it.borrado } ?: return emptyMap()
        val v = runCatching { Json.lee(n.texto("v")) }.getOrNull().comoLista().firstOrNull().comoLista()
        return v.mapNotNull { k ->
            val m = k.comoMapa(); val id = m["id"].comoLong().toInt()
            when (m["source"].comoTexto()) { "movie" -> id to "movie"; "tv" -> id to "series"; else -> null }
        }.toMap()
    }

    /** Un número de ficha de Azabache que es el de su web (TMDB o TVmaze); las añadidas a mano tienen números enormes. */
    private fun esDeLaWeb(id: Long) = id in 1 until 100_000_000_000L

    private fun get(url: String, cabeceras: Map<String, String> = emptyMap()): Map<String, Any?> = runCatching { Json.lee(http.get(url, cabeceras)).comoMapa() }.getOrDefault(emptyMap())

    /** TMDB, como en Azabache: la clave v3 va en la dirección; el token de lectura (empieza por «eyJ»), en la cabecera. */
    private fun tmdb(path: String, clave: String): Map<String, Any?> {
        val k = clave.trim()
        if (k.isBlank()) return emptyMap()
        val sep = if ('?' in path) '&' else '?'
        return if (k.startsWith("eyJ")) get(tmdbApi + path, mapOf("Authorization" to "Bearer $k")) else get(tmdbApi + path + sep + "api_key=" + URLEncoder.encode(k, "UTF-8"))
    }

    /** El código de IMDb de una película de TMDB. */
    fun imdbDePelicula(id: Long, clave: String): String = tmdb("/movie/$id/external_ids", clave)["imdb_id"].comoTexto().takeIf { it.startsWith("tt") }.orEmpty()

    /** El código de IMDb de una serie de TVmaze (y, si TVmaze no lo tiene, por TheTVDB en TMDB). */
    fun imdbDeSerie(id: Long, clave: String): String {
        val s = get("$tvmazeApi/shows/$id")
        val ext = s["externals"].comoMapa()
        ext["imdb"].comoTexto().takeIf { it.startsWith("tt") }?.let { return it }
        val tvdb = ext["thetvdb"].comoLong().takeIf { it > 0 } ?: return ""
        val tv = tmdb("/find/$tvdb?external_source=tvdb_id", clave)["tv_results"].comoLista().firstOrNull().comoMapa()["id"].comoLong().takeIf { it > 0 } ?: return ""
        return tmdb("/tv/$tv/external_ids", clave)["imdb_id"].comoTexto().takeIf { it.startsWith("tt") }.orEmpty()
    }

    /** «1×03 · Título», «2x10», «S01E03»: (temporada, episodio). */
    fun episodioDe(nombre: String): Pair<Int, Int>? {
        Regex("(\\d{1,2})\\s*[×xX]\\s*(\\d{1,3})").find(nombre)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        Regex("[sS](\\d{1,2})\\s*[eE](\\d{1,3})").find(nombre)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        return null
    }

    /** Lo que de una ficha de Azabache va a sus campos (lo demás va en `az`). */
    private val PROPIOS = setOf("id", "name", "cover", "general", "gfx", "story", "music", "gameplay", "review", "fav", "start", "end", "updated", "status", "sv", "minutes")

    fun migra(c: Context?, az: Coleccion, tit: Coleccion, prog: Coleccion, tiempo: Coleccion): Resultado {
        val cats = categorias(az)
        val clave = runCatching { LaFamilia.familia.clave("tmdb") }.getOrDefault("")
        var total = 0; var nuevos = 0; var conImdb = 0; var episodios = 0
        val ahora = System.currentTimeMillis()
        for ((k, tipo) in cats) for ((lista, valorada) in listOf("items_$k" to true, "iplays_$k" to false)) {
            for (n in az.vivos().filter { it.ref.startsWith("$lista~") }) {
                val o = runCatching { Json.lee(n.texto("o")) }.getOrNull().comoMapa()
                val azid = o["id"].comoLong().takeIf { it != 0L } ?: n.ref.substringAfter('~').toLongOrNull() ?: continue
                val nombre = o["name"].comoTexto(); if (nombre.isBlank()) continue
                total++
                val web = esDeLaWeb(azid)
                // Si ya pasó (desde otro dispositivo, o la añadiste en Mostaza), la misma: por su ficha de Azabache, o por su id de antes
                val ya = tit.todos().values.firstOrNull { it.entero("azid") == azid && it.entero("azk").toInt() == k }
                    ?: if (web) tit.todos().values.firstOrNull { (tipo == "movie" && it.entero("tmdb") == azid) || (tipo == "series" && it.entero("tvmaze") == azid) } else null
                val imdb = ya?.texto("imdb")?.ifBlank { null } ?: if (!web) "" else runCatching { if (tipo == "movie") imdbDePelicula(azid, clave) else imdbDeSerie(azid, clave) }.getOrDefault("")
                val ref = ya?.ref ?: when {
                    imdb.isNotBlank() -> Biblioteca.ref(imdb)
                    web && tipo == "movie" -> "tmdb:$azid"
                    web -> "tvmaze:$azid"
                    else -> "azabache:$azid"
                }
                val existente = ya ?: tit.nodo(ref)
                if (existente?.borrado == true) continue          // la borraste en Mostaza: no vuelve
                if (imdb.isNotBlank()) conImdb++
                val estado = if (valorada) "terminado" else when (o["status"].comoLong().toInt()) { 1 -> "en_curso"; 2 -> "abandonado"; else -> "pendiente" }
                val actualizada = maxOf(o["updated"].comoLong(), o["sv"].comoLong()).takeIf { it > 1_000_000_000_000L } ?: (n.m["o"] ?: 0L).takeIf { it > 0 } ?: ahora
                val campos = LinkedHashMap<String, Any?>()
                campos["id"] = imdb.ifBlank { EspejoCuenta.clave(ref) }; campos["tipo"] = tipo; campos["t"] = nombre; campos["estado"] = estado
                campos["fav"] = o["fav"].comoBool()
                val inicio = Fechas.ms(o["start"].comoLong()); val fin = Fechas.ms(o["end"].comoLong())
                campos["an"] = inicio.takeIf { it > 1_000_000_000_000L } ?: actualizada
                campos["ul"] = actualizada
                if (fin > 1_000_000_000_000L && estado != "pendiente") campos["fin"] = fin
                val cover = o["cover"].comoTexto()
                if (cover.startsWith("http")) campos["pu"] = cover
                o["hero"].comoTexto().takeIf { it.startsWith("http") }?.let { campos["fondo"] = it }
                if (imdb.isNotBlank()) campos["imdb"] = imdb
                if (web) campos[if (tipo == "movie") "tmdb" else "tvmaze"] = azid
                campos["azid"] = azid; campos["azk"] = k.toLong()
                val notas = listOf("general", "gfx", "story", "music", "gameplay").map { o[it].comoLong() }
                if (valorada && notas.any { it != 0L }) { campos["nota"] = notas[0]; campos["crit"] = notas.drop(1) }
                o["review"].comoTexto().takeIf { it.isNotBlank() }?.let { campos["resena"] = it }
                val resto = o.filterKeys { it !in PROPIOS }
                if (resto.isNotEmpty()) campos["az"] = Json.escribe(resto)
                // Lo que ya tiene la ficha (en Mostaza manda lo suyo): solo lo que falta
                val poner = if (existente == null) campos else campos.filterKeys { f -> existente[f] == null || existente[f] == "" }
                if (poner.isNotEmpty()) tit.pon(ref, poner, actualizada.coerceAtMost(ahora))
                if (existente == null) nuevos++
                // La portada que elegiste de tus archivos (si está en este dispositivo): a la cuenta (las de internet ya van en `pu`)
                if (c != null && !cover.startsWith("http") && cover.isNotBlank() && (existente == null || existente.texto("portada").isBlank()))
                    runCatching { File(cover.removePrefix("file://").removePrefix("file:")).takeIf { it.isFile }?.readBytes()?.let { PortadasFamilia.guardar(it) }?.let { h -> tit.pon(ref, mapOf("portada" to h), actualizada.coerceAtMost(ahora)) } }
                // El tiempo que Azabache tenía apuntado (sin días: Azabache no los guardaba)
                val min = o["minutes"].comoLong()
                if (min > 0 && tiempo.nodo("$ref~azabache") == null) tiempo.pon("$ref~azabache", mapOf("ref" to ref, "fuente" to "azabache", "t" to nombre, "total" to min * 60_000L), actualizada.coerceAtMost(ahora))
                // Los episodios marcados de una serie
                if (tipo == "series") for (pt in o["parts"].comoLista()) {
                    val pm = pt.comoMapa()
                    if (!pm["d"].comoBool()) continue
                    val (t, e) = episodioDe(pm["n"].comoTexto()) ?: continue
                    val vid = "${EspejoCuenta.clave(ref)}:$t:$e"
                    val vr = Biblioteca.ref(vid)
                    if (prog.nodo(vr) != null) continue
                    prog.pon(vr, mapOf("ref" to ref, "v" to vid, "temp" to t.toLong(), "ep" to e.toLong(), "visto" to true, "cuando" to actualizada.coerceAtMost(ahora)), actualizada.coerceAtMost(ahora))
                    episodios++
                }
            }
        }
        // Desde ahora, Azabache lee las películas y las series de aquí
        if (az.nodo("a_mostaza") == null) az.pon("a_mostaza", mapOf("v" to "[$ahora]", "por" to "mostaza", "cuantas" to total.toLong()))
        return Resultado(total, nuevos, conImdb, episodios)
    }

    /**
     * Lo que entró sin código de IMDb (de Azabache sin la clave de TMDB, o lo que añadió Azabache después): se busca, y al saberse se
     * apunta en `imdb` (y en `id`). La ref no cambia (es el mismo nodo para Azabache, tus sagas y tu diario).
     */
    fun completaImdb(c: Context) {
        val tit = runCatching { LaFamilia.coleccion("titulos") }.getOrNull() ?: return
        val clave = runCatching { LaFamilia.familia.clave("tmdb") }.getOrDefault("")
        val pr = p(c)
        for (n in tit.vivos().filter { it.texto("imdb").isBlank() && (it.entero("tmdb") > 0 || it.entero("tvmaze") > 0) }) {
            val intento = "imdb:${n.ref}"
            if (System.currentTimeMillis() - pr.getLong(intento, 0L) < 24 * 3600_000L) continue
            pr.edit().putLong(intento, System.currentTimeMillis()).apply()
            val imdb = runCatching { if (n.texto("tipo") == "series" && n.entero("tvmaze") > 0) imdbDeSerie(n.entero("tvmaze"), clave) else imdbDePelicula(n.entero("tmdb"), clave) }.getOrDefault("")
            if (imdb.isNotBlank() && Biblioteca.por(imdb) == null) tit.pon(n.ref, mapOf("imdb" to imdb, "id" to imdb))
        }
    }

    /** (Para las pruebas.) */
    fun olvidaParaPruebas(c: Context) { p(c).edit().clear().apply(); http = HttpJava(); tmdbApi = "https://api.themoviedb.org/3"; tvmazeApi = "https://api.tvmaze.com" }
}
