package com.gonzalo.azabache.pc

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.Fechas
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.Nodo
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Las películas y las series son de Mostaza (Azabache de escritorio 1.2.1, como el del móvil 1.6.3): en cuanto Mostaza ha traído lo de
 * Azabache (la marca `azabache/a_mostaza`), «Películas» y «Series» son los títulos de Mostaza (`titulos`, `progreso`, `tiempo`), sin copia y
 * con el número de ficha de siempre (`azid`), y lo que se cambia aquí va a esos nodos. Antes de la marca, todo como siempre; lo de antes no se
 * toca nunca.
 *
 * «El móvil» es aquí su código copiado tal cual (`CosasMostaza.kt` y `Data.kt` de Android, con el org.json de Android): lo que Mostaza
 * escribe (el contrato que deja su ContratoTest) se lee aquí igual que en el móvil; lo que escribe el ordenador lo lee el móvil igual; y lo
 * que escribe el ordenador es lo que escribiría el móvil en el mismo caso (los mismos campos con los mismos valores, y `az` letra a letra).
 * Con la cuenta de verdad (el emulador de Firebase); «mostaza» es Mostaza en otro dispositivo de la cuenta.
 */
class MostazaTest {
    @get:Rule val tmp = TemporaryFolder()

    // ================================================================ el código del móvil, tal cual (Data.kt y CosasMostaza.kt de Android)

    private data class Game(
        val id: Long, val name: String, val cover: String,
        val general: Int = 50, val gfx: Int = 50, val story: Int = 50,
        val music: Int = 50, val gameplay: Int = 50, val review: String = "",
        val hero: String = "", val start: Long = 0L, val end: Long = 0L,
        val creator: String = "",
        val fav: Boolean = false, val platform: String = "", val pages: Int = 0,
        val minutes: Int = 0, val updated: Long = 0L,
        val ttbMain: Int = 0, val ttbExtra: Int = 0, val ttbFull: Int = 0, val ttbSrc: String = "",
        val logo: String = ""
    )
    private data class Part(val name: String, val done: Boolean = false)
    private data class Entry(val time: Long, val general: Int, val gfx: Int, val story: Int, val music: Int, val gameplay: Int, val note: String, val chapter: Int = 0)
    private data class Memo(val t: Long, val x: String, val sh: String = "", val del: Boolean = false, val u: Long = 0L)
    private data class Play(
        val id: Long, val name: String, val cover: String, val hero: String,
        val status: Int, val entries: List<Entry>, val chapter: Int = 0, val every: Int = 3,
        val start: Long = 0L, val end: Long = 0L, val creator: String = "",
        val platform: String = "", val pages: Int = 0, val minutes: Int = 0, val updated: Long = 0L,
        val ttbMain: Int = 0, val ttbExtra: Int = 0, val ttbFull: Int = 0,
        val ttbSrc: String = "",
        val parts: List<Part> = emptyList(),
        val logo: String = "",
        val memos: List<Memo> = emptyList()
    )

    private fun memoToJson(m: Memo): JSONObject = JSONObject().put("t", m.t).put("x", m.x).apply {
        if (m.sh.isNotEmpty()) put("sh", m.sh)
        if (m.del) put("del", 1)
        if (m.u > 0L) put("u", m.u)
    }
    private fun memoFromJson(o: JSONObject) = Memo(o.optLong("t"), o.optString("x"), o.optString("sh", ""), o.optInt("del", 0) == 1 || o.optBoolean("del", false), o.optLong("u", 0L))

    private fun gameFromJson(o: JSONObject) = Game(
        o.getLong("id"), o.getString("name"), o.getString("cover"),
        o.getInt("general"), o.getInt("gfx"), o.getInt("story"),
        o.getInt("music"), o.getInt("gameplay"), o.getString("review"),
        o.optString("hero", ""), o.optLong("start", 0L), o.optLong("end", 0L),
        o.optString("creator", ""),
        o.optBoolean("fav", false), o.optString("platform", ""), o.optInt("pages", 0),
        o.optInt("minutes", 0), o.optLong("updated", 0L),
        o.optInt("ttbMain", 0), o.optInt("ttbExtra", 0), o.optInt("ttbFull", 0), o.optString("ttbSrc", ""),
        o.optString("logo", "")
    )

    private fun gameToJson(g: Game): JSONObject = JSONObject().put("id", g.id).put("name", g.name).put("cover", g.cover)
        .put("general", g.general).put("gfx", g.gfx).put("story", g.story)
        .put("music", g.music).put("gameplay", g.gameplay).put("review", g.review)
        .put("hero", g.hero).put("start", g.start).put("end", g.end).put("creator", g.creator)
        .put("fav", g.fav).put("platform", g.platform).put("pages", g.pages)
        .put("minutes", g.minutes).put("updated", g.updated)
        .put("ttbMain", g.ttbMain).put("ttbExtra", g.ttbExtra).put("ttbFull", g.ttbFull).put("ttbSrc", g.ttbSrc)
        .put("logo", g.logo)

    private fun playFromJson(o: JSONObject): Play {
        val es = o.optJSONArray("entries") ?: JSONArray()
        return Play(
            o.getLong("id"), o.getString("name"), o.getString("cover"), o.getString("hero"), o.getInt("status"),
            (0 until es.length()).map { i ->
                val e = es.getJSONObject(i)
                Entry(e.getLong("time"), e.getInt("general"), e.getInt("gfx"), e.getInt("story"),
                    e.getInt("music"), e.getInt("gameplay"), e.getString("note"), e.optInt("chapter", 0))
            },
            o.optInt("chapter", 0), o.optInt("every", 3),
            o.optLong("start", 0L), o.optLong("end", 0L), o.optString("creator", ""),
            o.optString("platform", ""), o.optInt("pages", 0), o.optInt("minutes", 0), o.optLong("updated", 0L),
            o.optInt("ttbMain", 0), o.optInt("ttbExtra", 0), o.optInt("ttbFull", 0), o.optString("ttbSrc", ""),
            (o.optJSONArray("parts") ?: JSONArray()).let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Part(it.optString("n"), it.optBoolean("d")) } }
            },
            o.optString("logo", ""),
            (o.optJSONArray("memos") ?: JSONArray()).let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { memoFromJson(it) } } }
        )
    }

    private fun playToJson(pl: Play): JSONObject {
        val es = JSONArray()
        pl.entries.forEach { e ->
            es.put(JSONObject().put("time", e.time).put("general", e.general).put("gfx", e.gfx)
                .put("story", e.story).put("music", e.music).put("gameplay", e.gameplay)
                .put("note", e.note).put("chapter", e.chapter))
        }
        return JSONObject().put("id", pl.id).put("name", pl.name).put("cover", pl.cover)
            .put("hero", pl.hero).put("status", pl.status).put("entries", es)
            .put("chapter", pl.chapter).put("every", pl.every)
            .put("start", pl.start).put("end", pl.end).put("creator", pl.creator)
            .put("platform", pl.platform).put("pages", pl.pages).put("minutes", pl.minutes)
            .put("updated", pl.updated)
            .put("ttbMain", pl.ttbMain).put("ttbExtra", pl.ttbExtra).put("ttbFull", pl.ttbFull).put("ttbSrc", pl.ttbSrc)
            .put("parts", JSONArray().apply { pl.parts.forEach { put(JSONObject().put("n", it.name).put("d", it.done)) } })
            .put("logo", pl.logo)
            .apply {
                if (pl.memos.isNotEmpty()) put("memos", JSONArray().apply { pl.memos.forEach { put(memoToJson(it)) } })
            }
    }

    /** Store.canon del móvil. */
    private fun canonMovil(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> v.keys().asSequence().filter { it != "sv" }.sorted().joinToString(",", "{", "}") { "$it:${canonMovil(v.opt(it))}" }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canonMovil(v.opt(it)) }
        is Double, is Float -> (v as Number).toDouble().let { d -> if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString() else d.toString() }
        is Number -> v.toLong().toString()
        is Boolean -> v.toString()
        else -> JSONObject.quote(v.toString())
    }

    /** CosasMostaza del móvil (lo que lee y lo que escribe), sin la cuenta: lo que escribiría, sin escribirlo. */
    private object Movil {
        class Vista(val games: List<Game>, val plays: List<Play>, val refPorId: Map<Long, String>, val nodoPorId: Map<Long, Nodo>)

        fun idDeRefCosas(ref: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (ch in "familia:$ref") { h = h xor ch.code.toLong(); h *= 0x100000001b3L }
            return (h and Long.MAX_VALUE) % 9_000_000_000_000L + 100_000_000_000L
        }
        fun idDeRef(ref: String): Long = idDeRefCosas("titulos:$ref")
        fun idDe(n: Nodo, tipo: String): Long =
            n.entero("azid").takeIf { it != 0L } ?: (if (tipo == "movie") n.entero("tmdb") else n.entero("tvmaze")).takeIf { it in 1 until 100_000_000_000L } ?: idDeRef(n.ref)
        private fun dia(ms: Long): Long = if (ms > 0) Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() else 0L
        fun azDe(n: Nodo): JSONObject = n.texto("az").takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        fun episodioDe(nombre: String): Pair<Int, Int>? {
            Regex("(\\d{1,2})\\s*[×xX]\\s*(\\d{1,3})").find(nombre)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            Regex("[sS](\\d{1,2})\\s*[eE](\\d{1,3})").find(nombre)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            return null
        }
    }

    private fun construyeMovil(titulos: List<Nodo>, tiempo: List<Nodo>, progreso: List<Nodo>, tipo: String, portada: (String) -> String = { "" }, puesta: (String) -> String? = { null }): Movil.Vista {
        val horas = tiempo.groupBy { it.texto("ref") }
        val vistos = progreso.groupBy { it.texto("ref") }
        val games = ArrayList<Game>(); val plays = ArrayList<Play>()
        val refs = HashMap<Long, String>(); val nodos = HashMap<Long, Nodo>()
        for (n in titulos.filter { it.texto("t").isNotBlank() && it.texto("tipo").ifBlank { "movie" } == tipo }.sortedBy { it.ref }) {
            var id = Movil.idDe(n, tipo)
            if (id in refs) id = Movil.idDeRef(n.ref)
            if (id in refs) continue
            refs[id] = n.ref; nodos[id] = n
            val o = aFichaMovil(n, id, horas[n.ref].orEmpty(), vistos[n.ref].orEmpty(), portada, puesta)
            runCatching { if (n.texto("estado") == "terminado") games += gameFromJson(o) else plays += playFromJson(o) }
        }
        return Movil.Vista(games, plays, refs, nodos)
    }

    private fun aFichaMovil(n: Nodo, id: Long, horas: List<Nodo>, vistos: List<Nodo>, portada: (String) -> String, puesta: (String) -> String?): JSONObject {
        val o = Movil.azDe(n)
        o.put("id", id).put("name", n.texto("t")).put("fav", n.si("fav")).put("review", n.texto("resena"))
        o.put("cover", puesta(n.ref) ?: portada(n.texto("portada")).ifBlank { n.texto("pu") })
        val crit = n.lista("crit").map { (it as? Number)?.toInt() ?: 0 }
        val valorada = n["nota"] != null
        o.put("general", if (valorada) n.entero("nota").toInt().coerceIn(0, 100) else 0)
        listOf("gfx", "story", "music", "gameplay").forEachIndexed { i, k -> o.put(k, if (valorada) crit.getOrElse(i) { 0 } else 0) }
        val ms = horas.sumOf { it.entero("total") }
        o.put("minutes", maxOf((ms / 60_000L).toInt(), o.optInt("minutes", 0)))
        val fin = Fechas.ms(n.entero("fin")).let { if (it > 0) it else if (n.texto("estado") == "terminado") (n.m["estado"] ?: 0L).takeIf { x -> x > 946_684_800_000L } ?: 0L else 0L }
        o.put("end", Instant.ofEpochMilli(fin).let { if (fin > 0) it.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() else 0L })
        if (o.optLong("start", 0L) <= 0L) o.put("start", Fechas.ms(n.entero("an")).let { if (it > 0) Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() else 0L })
        o.put("updated", n.m.values.maxOrNull() ?: n.u)
        o.put("status", when (n.texto("estado")) { "en_curso" -> 1; "abandonado" -> 2; else -> 0 })
        if (o.optString("hero").isBlank()) o.put("hero", n.texto("fondo"))
        if (!o.has("entries")) o.put("entries", JSONArray())
        val porEp = vistos.associate { (it.entero("temp").toInt() to it.entero("ep").toInt()) to it.si("visto") }
        o.optJSONArray("parts")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { p -> Movil.episodioDe(p.optString("n"))?.let { e -> porEp[e]?.let { v -> p.put("d", v) } } }
        }
        return o
    }

    private fun noAzMovil() = setOf("id", "updated", "sv", "status", "name", "cover", "general", "gfx", "story", "music", "gameplay", "review", "fav", "end", "minutes")
    private fun estadoDeMovil(o: JSONObject, valorada: Boolean): String =
        if (valorada) "terminado" else when (o.optInt("status", 0)) { 1 -> "en_curso"; 2 -> "abandonado"; else -> "pendiente" }
    private fun azDeFichaMovil(o: JSONObject): JSONObject = JSONObject().also { a -> val fuera = noAzMovil(); o.keys().forEach { k -> if (k !in fuera) a.put(k, o.get(k)) } }
    private fun refNuevoMovil(k: Kind, id: Long): String = when {
        id in 1 until 100_000_000_000L && k.isMovie -> "tmdb:$id"
        id in 1 until 100_000_000_000L -> "tvmaze:$id"
        else -> "azabache:$id"
    }
    private fun igualMovil(a: Any?, b: Any?): Boolean = canonMovil(aOrg(a)) == canonMovil(aOrg(b))
    private fun aOrg(v: Any?): Any? = when (v) {
        is Map<*, *> -> JSONObject(Json.escribe(v)); is List<*> -> JSONArray(Json.escribe(v)); is Int -> v.toLong(); null -> JSONObject.NULL; else -> v
    }
    private fun tipoDeMovil(k: Kind): String? = when { k.isMovie -> "movie"; k.isTv -> "series"; else -> null }

    /** `CosasMostaza.escribe` del móvil: (la ref, los cambios que irían a `col.cambia`, la portada que pondría). */
    private fun escribeMovil(k: Kind, v: Movil.Vista, todos: Map<String, Nodo>, id: Long, o: JSONObject, antes: JSONObject?, valorada: Boolean, ahora: Long): Triple<String, Map<String, Any?>, String?> {
        val tipo = tipoDeMovil(k)!!
        val ref = v.refPorId[id] ?: todos.values.firstOrNull { it.entero("azid") == id && it.entero("azk").toInt() == k.id }?.ref ?: refNuevoMovil(k, id)
        val actual = v.nodoPorId[id] ?: todos[ref]
        val cambios = LinkedHashMap<String, Any?>()
        fun cambiado(x: String) = antes == null || canonMovil(antes.opt(x)) != canonMovil(o.opt(x))
        val nuevaAqui = actual == null || actual.borrado
        val deLaOtraLista = antes == null && !nuevaAqui
        if (nuevaAqui) {
            cambios["t"] = o.optString("name"); cambios["tipo"] = tipo; cambios["azid"] = id; cambios["azk"] = k.id.toLong()
            if (actual == null) {
                cambios["id"] = ref
                if (id in 1 until 100_000_000_000L) cambios[if (tipo == "movie") "tmdb" else "tvmaze"] = id
            }
            cambios["an"] = Fechas.ms(o.optLong("start")).takeIf { it > 1_000_000_000_000L } ?: ahora
            if (actual?.borrado == true) cambios["b"] = 0L
        } else {
            if (actual!!.entero("azid") == 0L) cambios["azid"] = id
            if (actual.entero("azk") == 0L) cambios["azk"] = k.id.toLong()
        }
        if (!deLaOtraLista) {
            if (cambiado("name")) cambios["t"] = o.optString("name")
            if (cambiado("fav")) cambios["fav"] = o.optBoolean("fav")
        }
        val estado = estadoDeMovil(o, valorada)
        val estadoAntes = if (antes == null) actual?.texto("estado") else estadoDeMovil(antes, valorada)
        if (nuevaAqui || estado != estadoAntes) cambios["estado"] = estado
        if (valorada && (cambiado("end") || deLaOtraLista) && o.optLong("end") > 0) cambios["fin"] = Fechas.ms(o.optLong("end"))
        val notas = listOf("general", "gfx", "story", "music", "gameplay")
        if (valorada && (notas.any { cambiado(it) } || nuevaAqui || deLaOtraLista)) {
            val vs = notas.map { o.optInt(it) }
            if (!(nuevaAqui || deLaOtraLista) || vs.any { it != 0 } || actual?.get("nota") != null) { cambios["nota"] = vs[0].toLong(); cambios["crit"] = vs.drop(1).map { it.toLong() } }
        }
        if (cambiado("review") && (o.optString("review").isNotBlank() || antes != null)) cambios["resena"] = o.optString("review")
        val az = azDeFichaMovil(o)
        val azAntes = antes?.let { azDeFichaMovil(it) } ?: actual?.let { Movil.azDe(it) }
        if (canonMovil(az) != canonMovil(azAntes ?: JSONObject())) cambios["az"] = az.toString()
        actual?.takeIf { !it.borrado }?.let { n -> cambios.keys.toList().forEach { x -> if (x != "estado" || !nuevaAqui) if (igualMovil(n[x], cambios[x])) cambios.remove(x) } }
        val cover = o.optString("cover")
        val portada = if (cover.isNotBlank() && cambiado("cover") && cover != "" && cover != actual?.texto("pu")) cover else null
        return Triple(ref, cambios, portada)
    }

    // ---- y lo que el org.json del móvil de verdad conserva y la copia de 2013 de las pruebas no: el orden de los campos (`az` se hace
    //      recorriendo la ficha de gameToJson / playToJson) ----
    private val ORDEN_AZ = listOf("hero", "entries", "chapter", "every", "start", "creator", "platform", "pages", "ttbMain", "ttbExtra", "ttbFull", "ttbSrc", "parts", "logo", "memos")
    private val ORDEN_DENTRO = mapOf(
        "entries" to listOf("time", "general", "gfx", "story", "music", "gameplay", "note", "chapter"),
        "parts" to listOf("n", "d"), "memos" to listOf("t", "x", "sh", "del", "u"))
    private val ORDEN_GAME = listOf("id", "name", "cover", "general", "gfx", "story", "music", "gameplay", "review", "hero", "start", "end", "creator", "fav", "platform", "pages", "minutes", "updated", "ttbMain", "ttbExtra", "ttbFull", "ttbSrc", "logo")
    private val ORDEN_PLAY = listOf("id", "name", "cover", "hero", "status", "entries", "chapter", "every", "start", "end", "creator", "platform", "pages", "minutes", "updated", "ttbMain", "ttbExtra", "ttbFull", "ttbSrc", "parts", "logo", "memos")

    /** El texto de `az` como lo deja `az.toString()` en el móvil (su org.json guarda el orden en que se pusieron los campos). */
    private fun textoDelMovil(v: Any?, orden: List<String> = ORDEN_AZ): String = when (v) {
        is JSONObject -> v.keys().asSequence().toList().sortedBy { orden.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .joinToString(",", "{", "}") { k -> JSONObject.quote(k) + ":" + textoDelMovil(v.get(k), ORDEN_DENTRO[k] ?: orden) }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { textoDelMovil(v.get(it), orden) }
        is String -> JSONObject.quote(v)
        is Number -> JSONObject.numberToString(v)
        JSONObject.NULL -> "null"
        else -> v.toString()
    }

    /** Lo que escribe el ordenador es lo que escribiría el móvil: el mismo título, los mismos campos (en su orden) y `az` letra a letra. */
    private fun mismaEscritura(que: String, movil: Triple<String, Map<String, Any?>, String?>, aqui: CosasMostaza.Escritura?) {
        assertNotNull(que, aqui); aqui!!
        assertEquals("$que: el título", movil.first, aqui.ref)
        assertEquals("$que: los campos", movil.second.keys.toList(), aqui.cambios.keys.toList())
        for ((c, v) in movil.second) {
            if (c == "az") assertEquals("$que: az, letra a letra", textoDelMovil(JSONObject(v as String)), aqui.cambios[c])
            else assertEquals("$que: $c", canonMovil(aOrg(v)), canonMovil(aOrg(aqui.cambios[c])))
        }
        assertEquals("$que: la portada", movil.third, aqui.portada)
    }

    /** La ficha del móvil de una ficha de aquí (lo que el móvil haría con ella: gameFromJson o playFromJson). */
    private fun alMovil(o: Obj, valorada: Boolean): JSONObject = JSONObject(Json.escribe(o)).let { if (valorada) gameToJson(gameFromJson(it)) else playToJson(playFromJson(it)) }

    /** Lee igual que el móvil: las mismas fichas, con el mismo número, en la misma lista y con lo mismo dentro. */
    private fun leeIgual(que: String, aqui: CosasMostaza.Vista, movil: Movil.Vista) {
        assertEquals("$que: las mismas", movil.refPorId, aqui.refPorId)
        assertEquals("$que: vistas", movil.games.map { it.id }.toSet(), aqui.fichas.filter { it.valorada }.map { it.id }.toSet())
        assertEquals("$que: en curso", movil.plays.map { it.id }.toSet(), aqui.fichas.filter { !it.valorada }.map { it.id }.toSet())
        for (g in movil.games) {
            val f = aqui.fichas.single { it.id == g.id }
            assertEquals("$que: ${g.name}", canon(Json.lee(gameToJson(g).toString())), canon(f.o))
            assertEquals("$que: ${g.name}, los campos en el orden del móvil", ORDEN_GAME, f.o.keys.toList())
        }
        for (p in movil.plays) {
            val f = aqui.fichas.single { it.id == p.id }
            // (aquí, en curso, también la reseña y la estrella: no van en `az`)
            assertEquals("$que: ${p.name}", canon(Json.lee(playToJson(p).toString())), canon(f.o - "review" - "fav"))
            assertEquals("$que: ${p.name}, los campos en el orden del móvil", ORDEN_PLAY.filter { it != "memos" || p.memos.isNotEmpty() } + listOf("review", "fav"), f.o.keys.toList())
        }
    }

    private fun raiz(): File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "herramientas/casos/hermanas/mostaza.json").exists() }

    // ================================================================ las pruebas

    @Test fun loQueEscribeMostazaSeLeeComoEnElMovil() {
        val c = Json.lee(File(raiz(), "herramientas/casos/hermanas/mostaza.json").readText()).comoMapa()
        fun nodos(col: String) = c[col].comoMapa().map { (k, v) -> Nodo(k, v.comoMapa()) }
        val titulos = nodos("titulos"); val progreso = nodos("progreso"); val tiempo = nodos("tiempo")
        val vivos = titulos.filter { !it.borrado }
        assertTrue("lo que quitas en Hathor queda borrado (y no se lee)", titulos.single { it.ref == c["borrada"] }.borrado)
        val pelis = CosasMostaza.construye(vivos, tiempo.filter { !it.borrado }, progreso.filter { !it.borrado }, "movie", 4242)
        leeIgual("películas", pelis, construyeMovil(vivos, tiempo, progreso, "movie"))
        // Lo mismo que comprueba el ContratoHermanasTest del móvil
        val peli = pelis.fichas.single()
        assertTrue(peli.valorada); assertTrue(peli.mostaza); assertFalse(peli.sinValorar)
        assertEquals("Cadena perpetua", peli.nombre); assertEquals("su número de Jonsu", 278L, peli.id); assertEquals(92, peli.general); assertEquals(95, peli.notas[2])
        assertEquals("La esperanza es algo bueno.", peli.resena); assertTrue("vista: con su día", peli.terminado > 0); assertEquals("los 14 minutos que viste", 14, peli.minutos)
        assertEquals("https://images.metahub.space/poster/small/tt0111161/img", peli.portadaWeb)
        assertEquals("https://images.metahub.space/background/medium/tt0111161/img", peli.o["hero"])
        assertEquals("tt0111161", pelis.refPorId[278L]); assertEquals("tt0111161", peli.ref)
        val series = CosasMostaza.construye(vivos, tiempo, progreso, "series", 5151)
        leeIgual("series", series, construyeMovil(vivos, tiempo, progreso, "series"))
        val serie = series.fichas.single()
        assertFalse(serie.valorada); assertEquals("Juego de tronos", serie.nombre); assertEquals("en curso (viendo)", 1, serie.estado)
        assertEquals("sin número de Jonsu: uno fijo de su ref (el del móvil)", Movil.idDeRef("tt0944947"), serie.id)
        assertEquals(CosasMostaza.idDeRef("tt0944947"), serie.id)
        assertEquals(12, serie.minutos)
        assertTrue("sin nota: nunca un 0", serie.sinValorar)
        assertTrue(pelis.fichas.none { it.ref == c["borrada"] } && series.fichas.none { it.ref == c["borrada"] })

        // Y lo raro que pueda traer `az` (de otra versión, de la migración…) se lee igual que en el móvil
        val raro = Nodo("tt0133093", mapOf("t" to "Matrix", "tipo" to "movie", "estado" to "en_curso", "tmdb" to 603L, "fav" to true, "resena" to "A medias",
            "az" to """{"creator":"Lana y Lilly Wachowski","platform":"Cine/Netflix","pages":"12","ttbMain":3.7,"hero":null,"raro":[1,2],"start":"19000",""" +
                """"entries":[{"time":1700000000000,"general":80,"gfx":70,"story":60,"music":50,"gameplay":40,"note":"Hola/adiós","chapter":2}],"memos":[{"t":5,"x":"m","del":1},{"t":6,"x":"n","sh":"c.png","u":7}],""" +
                """"parts":[{"n":"Parte 1","d":"true"},{"n":"Parte 2"},"no"]}"""))
        val roto = Nodo("tt0234215", mapOf("t" to "Matrix Reloaded", "tipo" to "movie", "estado" to "en_curso", "az" to """{"entries":[{"time":1,"note":"sin notas"}]}"""))
        val todo = CosasMostaza.construye(listOf(raro, roto), emptyList(), emptyList(), "movie", 4242)
        val movil = construyeMovil(listOf(raro, roto), emptyList(), emptyList(), "movie")
        leeIgual("lo raro", todo, movil)
        assertEquals("la que el móvil no puede leer no sale (su número queda tomado)", listOf(603L), todo.fichas.map { it.id })
        assertEquals(2, todo.refPorId.size)
    }

    // ---------------------------------------------------------------- con la cuenta de verdad

    private lateinit var mostaza: Familia
    private lateinit var aqui: Familia
    private var uid = ""
    private val pelis = customKind(4242, "Películas", listOf("Guion", "Actuación", "Dirección", "Música"), "Viendo", 0, SRC_MOVIE)
    private val series = customKind(5151, "Series", listOf("Guion", "Personajes", "Actuación", "Ritmo"), "Viendo", 0, SRC_TV)

    private fun prepara() {
        Emulador.limpia()
        uid = Emulador.cuenta("gonzalo")
        mostaza = Emulador.dispositivo("gonzalo")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg
        LaFamilia.dirPruebas = tmp.root
        LaFamilia.llaveroPruebas = LlaveroMemoria()
        aqui = LaFamilia.inicia("azabache").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Cuenta.olvidaParaPruebas(); CosasMostaza.olvidaParaPruebas()
        listOf("juegos", "libros", "tiempo", "azabache", "ajustes", "titulos", "progreso").forEach { mostaza.coleccion(it); LaFamilia.coleccion(it) }
        sincroniza()
    }

    @Before fun antes() { CosasMostaza.olvidaParaPruebas() }
    @After fun fuera() { LaFamilia.reiniciaParaPruebas(); Cuenta.olvidaParaPruebas(); CosasMostaza.olvidaParaPruebas() }

    private fun sincroniza() { assertTrue(mostaza.sincroniza().ok); assertTrue(aqui.sincroniza().ok); assertTrue(mostaza.sincroniza().ok); assertTrue(aqui.sincroniza().ok) }

    private fun vistaMovil(k: Kind, f: Familia = mostaza) = construyeMovil(f.coleccion("titulos").vivos(), f.coleccion("tiempo").vivos(), f.coleccion("progreso").vivos(), tipoDeMovil(k)!!)

    /**
     * Guarda aquí una ficha ([antes] → [nueva]) como lo hace la página (Cuenta.guarda), comprobando antes que lo que va a escribir es lo que
     * escribiría el móvil en el mismo caso (con su vista de la cuenta, su forma de las fichas y su `escribe`).
     */
    private fun guardaComoElMovil(que: String, k: Kind, antes: Ficha?, nueva: Obj, valorada: Boolean) {
        val ahora = System.currentTimeMillis()
        val mismaLista = antes != null && antes.valorada == valorada
        val oMovil = alMovil(nueva, valorada)
        val oAqui = CosasMostaza.comoEnElMovil(nueva, valorada)!!
        assertEquals("$que: la ficha que se guarda", canonMovil(oMovil), canonMovil(JSONObject(Json.escribe(if (valorada) oAqui else oAqui - "review" - "fav"))))
        val antesMovil = if (mismaLista) alMovil(antes!!.o, valorada) else null
        val antesAqui = if (mismaLista) CosasMostaza.comoEnElMovil(antes!!.o, valorada) else null
        val col = LaFamilia.coleccion("titulos")
        val movil = escribeMovil(k, construyeMovil(col.vivos(), LaFamilia.coleccion("tiempo").vivos(), LaFamilia.coleccion("progreso").vivos(), tipoDeMovil(k)!!), col.todos(), oMovil.getLong("id"), oMovil, antesMovil, valorada, ahora)
        mismaEscritura(que, movil, CosasMostaza.escritura(k, Cuenta.vistaMostaza(k), col.todos(), oAqui["id"] as Long, oAqui, antesAqui, valorada, ahora))
        Cuenta.guarda(k.id, antes, nueva, valorada)
    }

    @Test fun antesDeMostazaComoSiempreYDespuesLasDeMostaza() {
        prepara()
        Cuenta.guardaKind(pelis); Cuenta.guardaKind(series)
        // Azabache 1.2.0: «El club de la lucha» (de TMDB, id 550), valorada, en lo de antes
        val club0 = linkedMapOf<String, Any?>("id" to 550L, "name" to "El club de la lucha", "cover" to "https://image.tmdb.org/club.jpg", "general" to 85L, "gfx" to 80L, "story" to 90L,
            "music" to 70L, "gameplay" to 88L, "review" to "La primera regla.", "hero" to "", "start" to 19000L, "end" to 19005L, "creator" to "David Fincher", "platform" to "Netflix")
        Cuenta.guarda(pelis.id, null, club0, true)
        sincroniza()
        assertFalse(CosasMostaza.activa())
        assertEquals(listOf(550L), Cuenta.fichas(pelis.id).map { it.id })
        assertFalse(Cuenta.fichas(pelis.id).single().mostaza)
        val viejo = Emulador.lee("/users/$uid/familia/azabache/items_${pelis.id}~550").comoMapa()
        assertTrue(viejo.isNotEmpty())
        val deAntes = mostaza.coleccion("azabache").todos().filterKeys { it.startsWith("items_") || it.startsWith("iplays_") }.mapValues { it.value.datos() }

        // Mostaza la trae (con su número de siempre), y otras, y deja la marca
        val hoy = LocalDate.now()
        val t = mostaza.coleccion("titulos")
        t.pon("tt0137523", mapOf("id" to "tt0137523", "tipo" to "movie", "t" to "El club de la lucha", "estado" to "terminado", "imdb" to "tt0137523", "tmdb" to 550L,
            "azid" to 550L, "azk" to pelis.id.toLong(), "nota" to 85L, "crit" to listOf(80L, 90L, 70L, 88L), "resena" to "La primera regla.", "fin" to Fechas.ms(19005), "an" to Fechas.ms(19000),
            "pu" to "https://image.tmdb.org/club.jpg", "az" to """{"creator":"David Fincher","platform":"Netflix"}"""), 1_700_000_000_000L)
        // El tiempo, de dos dispositivos (se suman), uno con su día
        mostaza.coleccion("tiempo").pon("tt0137523~mostaza-a", mapOf("ref" to "tt0137523", "fuente" to "mostaza-a", "total" to 139 * 60_000L))
        mostaza.coleccion("tiempo").pon("tt0137523~mostaza-b", mapOf("ref" to "tt0137523", "fuente" to "mostaza-b", "total" to 21 * 60_000L, "d$hoy" to 21 * 60_000L))
        // Una que has empezado en Mostaza (sin número de Azabache: el de TMDB)
        t.pon("tt0133093", mapOf("id" to "tt0133093", "tipo" to "movie", "t" to "Matrix", "estado" to "en_curso", "imdb" to "tt0133093", "tmdb" to 603L, "an" to System.currentTimeMillis()))
        // Una serie con un episodio visto en Mostaza (y sus capítulos de antes en Azabache, en «az»: el tercero marcado allí)
        t.pon("tt0944947", mapOf("id" to "tt0944947", "tipo" to "series", "t" to "Juego de tronos", "estado" to "en_curso", "tvmaze" to 82L, "azid" to 82L, "azk" to series.id.toLong(),
            "az" to """{"parts":[{"n":"1×01 · Se acerca el invierno","d":false},{"n":"1×02 · El camino real","d":false},{"n":"1×03 · Lord Nieve","d":true}],"creator":"HBO"}"""))
        mostaza.coleccion("progreso").pon("tt0944947:1:1", mapOf("ref" to "tt0944947", "v" to "tt0944947:1:1", "temp" to 1L, "ep" to 1L, "visto" to true))
        mostaza.coleccion("progreso").pon("tt0944947:1:2", mapOf("ref" to "tt0944947", "v" to "tt0944947:1:2", "temp" to 1L, "ep" to 2L, "pos" to 600_000L, "dur" to 3_400_000L))
        mostaza.coleccion("azabache").pon(CosasMostaza.MARCA, mapOf("v" to "[1]", "por" to "mostaza"))
        sincroniza()

        assertTrue(CosasMostaza.activa())
        leeIgual("lo de Hathor", Cuenta.vistaMostaza(pelis), vistaMovil(pelis))
        leeIgual("lo de Hathor", Cuenta.vistaMostaza(series), vistaMovil(series))
        val club = Cuenta.ficha(pelis.id, 550L)!!
        assertTrue(club.mostaza); assertTrue(club.valorada); assertEquals("tt0137523", club.ref)
        assertEquals("El club de la lucha", club.nombre); assertEquals(85, club.general); assertEquals(90, club.notas[2]); assertEquals("La primera regla.", club.resena)
        assertEquals("David Fincher", club.creador); assertEquals("Netflix", club.plataforma); assertEquals(19005L, club.terminado)
        assertEquals("los minutos, la suma de tus dispositivos", 160, club.minutos)
        assertEquals("el tiempo de cada día", mapOf(hoy.toString() to 21 * 60_000L), Cuenta.porDia("tt0137523"))
        assertEquals("ver no es jugar (como en el móvil)", 0L, diasDeLaCuenta()[hoy.toEpochDay()]?.jugadoMs ?: 0L)
        val matrix = Cuenta.fichas(pelis.id).single { it.nombre == "Matrix" }
        assertEquals("la de Hathor: el número de TMDB", 603L, matrix.id); assertEquals(1, matrix.estado); assertFalse(matrix.valorada)
        val got = Cuenta.fichas(series.id).single()
        assertEquals(82L, got.id)
        assertEquals("lo visto en Hathor manda; lo demás, lo marcado en Jonsu", listOf(true, false, true), got.o["parts"].let { it as List<*> }.map { (it as Map<*, *>)["d"] })
        assertEquals(listOf(true, false), CosasMostaza.vistosDe("tt0944947").map { it.visto })
        assertEquals("el mismo número de siempre: la saga y el diario lo encuentran", 550L, Cuenta.fichas(pelis.id).firstOrNull { it.ref == "tt0137523" }?.id)

        // Valoras «Matrix» al terminarla aquí (como «Terminar y valorar»): va al nodo de Mostaza, con su número
        guardaComoElMovil("terminar", pelis, matrix, matrix.con(mapOf("general" to 95L, "gfx" to 90L, "story" to 90L, "music" to 95L, "gameplay" to 99L, "review" to "Toma la pastilla roja.", "end" to 19500L)), true)
        sincroniza()
        val m = mostaza.coleccion("titulos").nodo("tt0133093")!!
        assertEquals("terminado", m.texto("estado")); assertEquals(95L, m.entero("nota")); assertEquals(listOf(90L, 90L, 95L, 99L), m.lista("crit"))
        assertEquals("Toma la pastilla roja.", m.texto("resena")); assertEquals(Fechas.ms(19500), m.entero("fin"))
        assertFalse("no se borra al pasar de una lista a otra", m.borrado)
        assertEquals("y queda su número", 603L, m.entero("azid")); assertEquals(pelis.id.toLong(), m.entero("azk"))
        assertEquals("lo de Hathor no se toca", "tt0133093", m.texto("imdb"))

        // Cambias solo la reseña del club mientras Mostaza la marca como favorita: no se pisan
        val club2 = Cuenta.ficha(pelis.id, 550L)!!
        mostaza.coleccion("titulos").cambia("tt0137523", mapOf("fav" to true))
        guardaComoElMovil("la reseña", pelis, club2, club2.con(mapOf("review" to "La primera regla es no hablar de ella.")), true)
        sincroniza()
        val cl = mostaza.coleccion("titulos").nodo("tt0137523")!!
        assertEquals("La primera regla es no hablar de ella.", cl.texto("resena")); assertTrue(cl.si("fav")); assertEquals(85L, cl.entero("nota"))
        assertEquals("""{"creator":"David Fincher","platform":"Netflix"}""", cl.texto("az"))
        // Y el director (lo de Azabache, en `az`, como lo escribe el móvil)
        val club3 = Cuenta.ficha(pelis.id, 550L)!!
        assertTrue(club3.fav)
        guardaComoElMovil("el director", pelis, club3, club3.con(mapOf("creator" to "D. Fincher", "platform" to "Cine/Filmin")), true)
        sincroniza()
        assertEquals(textoDelMovil(JSONObject(mostaza.coleccion("titulos").nodo("tt0137523")!!.texto("az"))), mostaza.coleccion("titulos").nodo("tt0137523")!!.texto("az"))
        assertTrue(mostaza.coleccion("titulos").nodo("tt0137523")!!.texto("az").contains("Cine\\/Filmin"))

        // La serie: la dejas («Abandonada»), desde su página
        val serie = Cuenta.fichas(series.id).single()
        guardaComoElMovil("abandonar", series, serie, serie.con(mapOf("status" to 2L, "end" to hoy.toEpochDay())), false)
        sincroniza()
        val s = mostaza.coleccion("titulos").nodo("tt0944947")!!
        assertEquals("abandonado", s.texto("estado")); assertTrue("las marcas de antes, como estaban", s.texto("az").contains("Lord Nieve"))

        // Una nueva desde aquí (como «+ Añadir»: no hay Mostaza en el ordenador): a la cuenta de Mostaza, con su id de TMDB
        val nueva = linkedMapOf<String, Any?>("id" to 680L, "name" to "Pulp Fiction", "cover" to "", "hero" to "", "creator" to "Quentin Tarantino", "platform" to "",
            "pages" to 0L, "start" to 0L, "end" to 0L, "updated" to System.currentTimeMillis(), "status" to 0L, "entries" to emptyList<Any?>())
        guardaComoElMovil("nueva", pelis, null, nueva, false)
        // Y una serie a mano (sin TVmaze)
        val aMano = linkedMapOf<String, Any?>("id" to customItemId("Mi serie"), "name" to "Mi serie", "cover" to "", "hero" to "", "status" to 1L, "start" to hoy.toEpochDay())
        guardaComoElMovil("a mano", series, null, aMano, false)
        sincroniza()
        val pulp = mostaza.coleccion("titulos").nodo("tmdb:680")!!
        assertEquals("tmdb:680", pulp.texto("id")); assertEquals("movie", pulp.texto("tipo")); assertEquals(680L, pulp.entero("tmdb")); assertEquals("pendiente", pulp.texto("estado"))
        assertEquals(680L, pulp.entero("azid")); assertEquals(pelis.id.toLong(), pulp.entero("azk")); assertEquals("Pulp Fiction", pulp.texto("t")); assertTrue(pulp.entero("an") > 0)
        assertNull("sin valorar: sin nota", pulp["nota"])
        assertEquals("Quentin Tarantino", Json.lee(pulp.texto("az")).comoMapa()["creator"])
        val mia = mostaza.coleccion("titulos").nodo("azabache:${customItemId("Mi serie")}")!!
        assertEquals("series", mia.texto("tipo")); assertEquals("en_curso", mia.texto("estado")); assertEquals(Fechas.ms(hoy.toEpochDay()), mia.entero("an"))
        assertEquals(0L, mia.entero("tvmaze"))

        // Lo que ha escrito el ordenador lo lee el móvil igual
        leeIgual("lo escrito aquí", Cuenta.vistaMostaza(pelis), vistaMovil(pelis))
        leeIgual("lo escrito aquí", Cuenta.vistaMostaza(series), vistaMovil(series))
        assertEquals(setOf(550L, 603L), vistaMovil(pelis).games.map { it.id }.toSet())
        assertEquals("Toma la pastilla roja.", vistaMovil(pelis).games.single { it.id == 603L }.review)

        // Quitarla aquí la quita en Mostaza (borrada, con su hora: como al quitarla allí)
        Cuenta.borra(Cuenta.ficha(pelis.id, 680L)!!)
        sincroniza()
        assertTrue(mostaza.coleccion("titulos").nodo("tmdb:680")!!.borrado)
        assertNull(Cuenta.ficha(pelis.id, 680L))
        // Solo «Añadir» la recupera: la misma, con lo suyo
        guardaComoElMovil("otra vez", pelis, null, nueva, false)
        sincroniza()
        val otraVez = mostaza.coleccion("titulos").nodo("tmdb:680")!!
        assertFalse(otraVez.borrado); assertEquals(680L, otraVez.entero("azid"))
        assertEquals(1, mostaza.coleccion("titulos").todos().count { it.value.entero("azid") == 680L })

        // Lo de antes de Azabache, intacto (ni se cambia ni se borra ni se añade nada)
        assertEquals(viejo, Emulador.lee("/users/$uid/familia/azabache/items_${pelis.id}~550").comoMapa())
        assertEquals(deAntes, mostaza.coleccion("azabache").todos().filterKeys { it.startsWith("items_") || it.startsWith("iplays_") }.mapValues { it.value.datos() })
    }
}
