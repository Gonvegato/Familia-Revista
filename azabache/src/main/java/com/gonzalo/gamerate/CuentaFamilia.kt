package com.gonzalo.gamerate

import android.content.Context
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.PortadasFamilia
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.Nodo
import com.gonzalo.familia.nucleo.comoMapa
import org.json.JSONArray
import org.json.JSONObject

/*
 * Azabache sobre la cuenta de la familia (nube/ESQUEMA.md).
 *
 *  - Lo de Hiedra, Marfil y Cobalto NO se copia: cada juego, libro u obra está una sola vez en la cuenta (`juegos`, `libros`, `obras`) y
 *    Azabache lo lee de ahí ([CuentaHermanas.refrescar]); su estado lo decide la hermana (si vuelves a leer un libro, está en curso).
 *  - La valoración (nota, criterios, reseña) y lo propio de Azabache de esas fichas (apuntes, capítulos, imagen de fondo…) van en el
 *    mismo nodo de la cosa ([CuentaHermanas.subeReflejo]): la ven Hiedra y Marfil, y tus otros Azabache.
 *  - Lo que es solo de Azabache (películas, series, tus categorías, el diario, las sagas, las maquetas…) va a la colección `azabache`,
 *    elemento a elemento ([AzabacheNube]): dos dispositivos que tocan cosas distintas nunca se pisan.
 *  - Las claves de los servicios, a `/claves` (las usan todas las apps de la familia).
 */

/** La valoración y lo de Azabache en el nodo de las hermanas. */
object CuentaHermanas {
    /** Los campos que manda la hermana (no viajan en «az»). */
    private val DE_LA_HERMANA = setOf("id", "name", "cover", "platform", "creator", "minutes", "pages", "start", "end", "fav", "ttbMain", "ttbExtra", "ttbFull",
        "updated", "sv", "general", "gfx", "story", "music", "gameplay", "review", "status")

    private fun prefs(c: Context) = c.getSharedPreferences("hermanas_cuenta", Context.MODE_PRIVATE)
    private fun derivado(c: Context, k: String): JSONObject? = prefs(c).getString("d:$k", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    private fun ponDerivado(c: Context, k: String, o: JSONObject?) { prefs(c).edit().apply { if (o == null) remove("d:$k") else putString("d:$k", o.toString()) }.apply() }

    private fun estadoHiedra(s: String) = when (s) { "en_curso" -> "JUGANDO"; "terminado" -> "PASADO"; "abandonado" -> "ABANDONADO"; else -> "PENDIENTE" }
    private fun estadoMarfil(s: String) = when (s) { "en_curso" -> "LEYENDO"; "terminado" -> "LEIDO"; "abandonado" -> "ABANDONADO"; else -> "PENDIENTE" }

    /** La portada de una cosa de la cuenta: la imagen ya en el móvil; si aún no está, la dirección de internet de la que salió. */
    private fun portada(c: Context, n: Nodo, faltan: MutableList<String>): String {
        val h = n.texto("portada")
        val f = PortadasFamilia.archivo(c, h)
        if (f.isNotBlank()) return f
        if (h.isNotBlank()) faltan += h
        return n.texto("pu")
    }

    private fun valorDe(n: Nodo): Hermanas.Valor? {
        if (n["nota"] == null && n["resena"] == null) return null
        val crit = n.lista("crit").map { (it as? Number)?.toInt() ?: 0 }
        return Hermanas.Valor(n.entero("nota").toInt().coerceIn(0, 100), crit, n.texto("resena"))
    }

    private fun claveLanzador(m: Map<*, *>): String? = when (m["tp"]?.toString()) {
        "ROM" -> "rom:${m["s"]}:${normName(m["a"]?.toString().orEmpty())}"; "APP" -> "app:${m["pk"]}"; "PC" -> "pc:${m["pck"]}"
        "WEB" -> "web:" + m["u"]?.toString().orEmpty().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/'); else -> null
    }

    /**
     * Lee lo de las hermanas de la cuenta y lo refleja en las listas de Azabache (y deja lo de cada día —jugado, leído, escrito—, los
     * subrayados, los capítulos de Cobalto y los objetivos donde lo leen el Diario y los widgets). Devuelve si ha cambiado algo.
     */
    fun refrescar(c: Context, store: Store): Boolean = synchronized(cerrojo) {
        // (de uno en uno: lo piden la sincronización, la vuelta a la app y la llegada de portadas, y nunca deben mezclarse)
        try { refrescarYa(c, store).also { diag(c, null) } }
        catch (e: Exception) { diag(c, e); throw e }
    }

    private val cerrojo = Any()

    /** Cómo fue el último reflejo (para «Cuenta de la familia» en Ajustes → Cuenta): hora, y el error si lo hubo. */
    class Diagnostico(val hora: Long, val error: String, val juegos: Int, val libros: Int)
    fun diagnostico(c: Context): Diagnostico = prefs(c).let { Diagnostico(it.getLong("diag_t", 0L), it.getString("diag_e", "").orEmpty(), it.getInt("diag_j", 0), it.getInt("diag_l", 0)) }
    private fun diag(c: Context, e: Exception?) {
        val j = runCatching { LaFamilia.coleccion("juegos").vivos().size }.getOrDefault(0); val l = runCatching { LaFamilia.coleccion("libros").vivos().size }.getOrDefault(0)
        val err = e?.let { x -> (x.javaClass.simpleName + ": " + x.message.orEmpty() + x.stackTrace.take(3).joinToString("") { " · ${it.fileName}:${it.lineNumber}" }).take(400) }.orEmpty()
        prefs(c).edit().putLong("diag_t", System.currentTimeMillis()).putString("diag_e", err).putInt("diag_j", j).putInt("diag_l", l).apply()
    }

    private fun refrescarYa(c: Context, store: Store): Boolean {
        if (!LaFamilia.familia.sesion.dentro) return false
        migraValoraciones(c, store)
        var cambio = false
        val faltan = ArrayList<String>()
        val tiempo = LaFamilia.coleccion("tiempo").vivos()
        val porRef = tiempo.groupBy { it.texto("ref") }
        // ---- Hiedra
        val nodosJ = LaFamilia.coleccion("juegos").vivos().filter { it.texto("t").isNotBlank() }
        val juegos = nodosJ.map { n ->
            val claves = n.lista("k").map { it.toString() } + n.lista("ln").mapNotNull { (it as? Map<*, *>)?.let(::claveLanzador) }
            Hermanas.JuegoH(n.ref, n.texto("t"), n.texto("pl"), estadoHiedra(n.texto("estado")), n.si("fav"), n.entero("fin"), n.entero("an"), n.entero("ul"),
                portada(c, n, faltan), claves.distinct(), n.entero("dh").toInt(), n.entero("de").toInt(), n.entero("dc").toInt(),
                porRef[n.ref].orEmpty().sumOf { it.entero("total") }, n.m.values.maxOrNull() ?: 0L)
        }
        val valJ = nodosJ.mapNotNull { n -> valorDe(n)?.let { n.ref to it } }.toMap()
        val enlJ = Hermanas.enlaces(c, "hiedra")
        // (la portada la manda la cuenta: se pone siempre la de la cosa; cambiarla aquí la cambia allí)
        val puestasJ = HashMap<String, String>().also { m -> val ls = store.load(GAMES.id).associate { it.id to it.cover } + store.loadPlays(GAMES.id).associate { it.id to it.cover }; enlJ.forEach { (k, v) -> ls[v]?.let { m[k] = it } } }
        val eJ = Hermanas.reflejarJuegos(juegos, store.load(GAMES.id), store.loadPlays(GAMES.id), enlJ, { it.portadaUrl }, puestasJ, System.currentTimeMillis(), valJ)
        val (gsJ, psJ) = conAz(c, "games", "plays", eJ.games, eJ.plays, nodosJ.associateBy { it.ref }, enlJ)
        if (eJ.cambio || gsJ != eJ.games || psJ != eJ.plays) { store.save(gsJ, GAMES.id); store.savePlays(psJ, GAMES.id); cambio = true }
        Hermanas.guardaEnlacesPublico(c, "hiedra", enlJ)
        recuerda(c, "games", gsJ.map { gameToJson(it) }); recuerda(c, "plays", psJ.map { playToJson(it) })
        // Lo jugado cada día (con la forma de siempre: id → fuente → {t, total, days})
        val tj = JSONObject()
        for (n in tiempo) {
            val j = nodosJ.firstOrNull { it.ref == n.texto("ref") } ?: continue
            val days = JSONObject(); n.campos.forEach { (k, v) -> if (k.length == 11 && k[0] == 'd' && k[5] == '-') days.put(k.substring(1), (v as? Number)?.toLong() ?: 0L) }
            (tj.optJSONObject(j.ref) ?: JSONObject().also { tj.put(j.ref, it) }).put(n.texto("fuente"), JSONObject().put("t", j.texto("t")).put("m", j.texto("menu")).put("total", n.entero("total")).put("days", days))
        }
        Hermanas.guardaPublico(c, "tiempo_hiedra", tj.toString())

        // ---- Marfil
        val nodosL = LaFamilia.coleccion("libros").vivos().filter { it.texto("t").isNotBlank() }
        val libros = nodosL.map { n ->
            Hermanas.LibroM(n.texto("clave"), n.texto("t"), n.texto("a"), estadoMarfil(n.texto("estado")), n.si("fav"), n.entero("an"), n.entero("ul"), n.decimal("fr").toFloat(),
                n.entero("pg").toInt(), n.lista("fin").map { (it as? Number)?.toLong() ?: 0L }.filter { it > 0 }, porRef[n.ref].orEmpty().sumOf { it.entero("total") },
                n.m.values.maxOrNull() ?: 0L, portada(c, n, faltan))
        }.filter { it.clave.isNotBlank() }
        val valL = nodosL.mapNotNull { n -> valorDe(n)?.let { n.texto("clave") to it } }.toMap()
        val enlL = Hermanas.enlaces(c, "marfil")
        val puestasL = HashMap<String, String>().also { m -> val ls = store.load(BOOKS.id).associate { it.id to it.cover } + store.loadPlays(BOOKS.id).associate { it.id to it.cover }; enlL.forEach { (k, v) -> ls[v]?.let { m[k] = it } } }
        val eL = Hermanas.reflejarLibros(libros, store.load(BOOKS.id), store.loadPlays(BOOKS.id), enlL, { it.portadaUrl }, puestasL, System.currentTimeMillis(), valL)
        val (gsL, psL) = conAz(c, "books", "bplays", eL.games, eL.plays, nodosL.associateBy { it.texto("clave") }, enlL)
        if (eL.cambio || gsL != eL.games || psL != eL.plays) { store.save(gsL, BOOKS.id); store.savePlays(psL, BOOKS.id); cambio = true }
        Hermanas.guardaEnlacesPublico(c, "marfil", enlL)
        recuerda(c, "books", gsL.map { gameToJson(it) }); recuerda(c, "bplays", psL.map { playToJson(it) })
        Hermanas.guardaPublico(c, "avance_marfil", Hermanas.avancePorFicha(libros, enlL).toString())
        // Lo leído cada día (kid → «cuenta» → {t, a, dev: {fuente: {total, days}}}) y los subrayados (kid → «cuenta» → {t, a, n: […]})
        val tl = JSONObject()
        for (n in tiempo) {
            val l = nodosL.firstOrNull { it.ref == n.texto("ref") } ?: continue
            val days = JSONObject(); n.campos.forEach { (k, v) -> if (k.length == 11 && k[0] == 'd' && k[5] == '-') days.put(k.substring(1), (v as? Number)?.toLong() ?: 0L) }
            val x = tl.optJSONObject(l.ref) ?: JSONObject().also { tl.put(l.ref, it) }
            val cu = x.optJSONObject("cuenta") ?: JSONObject().put("t", l.texto("t")).put("a", l.texto("a")).put("dev", JSONObject()).also { x.put("cuenta", it) }
            cu.getJSONObject("dev").put(n.texto("fuente"), JSONObject().put("total", n.entero("total")).put("days", days))
        }
        Hermanas.guardaPublico(c, "tiempo_marfil", tl.toString())
        val sub = JSONObject()
        for (n in LaFamilia.coleccion("marcas").vivos().filter { it.texto("tipo") == "notes" }) {
            val l = nodosL.firstOrNull { it.ref == n.texto("ref") } ?: continue
            val m = n.mapa("n")
            val x = sub.optJSONObject(l.ref) ?: JSONObject().also { sub.put(l.ref, it) }
            val cu = x.optJSONObject("cuenta") ?: JSONObject().put("t", l.texto("t")).put("a", l.texto("a")).put("n", JSONArray()).also { x.put("cuenta", it) }
            cu.getJSONArray("n").put(JSONObject().put("x", m["text"]?.toString().orEmpty()).put("o", m["note"]?.toString().orEmpty()).put("c", (m["created"] as? Number)?.toLong() ?: 0L)
                .put("l", m["label"]?.toString().orEmpty()).put("k", m["value"]?.toString().orEmpty()))
        }
        Hermanas.guardaPublico(c, "subrayados_marfil", sub.toString())
        LaFamilia.coleccion("ajustes").nodo("lectura")?.let { o -> Hermanas.guardaPublico(c, "objetivos_marfil", JSONObject().put("dia", o.entero("dia")).put("ano", o.entero("ano")).put("anio", o.entero("anio")).put("mod", o.m.values.maxOrNull() ?: 1L).toString()) }

        // ---- Cobalto: las palabras de cada día (dispositivo → día → palabras) y los capítulos terminados
        val pal = JSONObject()
        LaFamilia.coleccion("palabras").vivos().forEach { n -> pal.put(n.ref, JSONObject().also { d -> n.campos.forEach { (k, v) -> if (k.startsWith("d") && k.length == 11) d.put(k.substring(1), (v as? Number)?.toInt() ?: 0) } }) }
        Hermanas.guardaPublico(c, "palabras_cobalto", pal.toString())
        val lec = JSONObject()
        LaFamilia.coleccion("capitulos").vivos().forEach { n ->
            val obra = n.texto("ref"); val cap = n.ref.substringAfter('~')
            (lec.optJSONObject(obra) ?: JSONObject().also { lec.put(obra, it) }).put(cap, JSONObject().put("t", n.texto("t")).put("a", n.texto("a")).put("c", n.texto("c")).put("n", n.entero("n"))
                .put("pa", n.texto("pa")).put("p", n.entero("p")).put("x", n.texto("x")).put("u", n.entero("u")))
        }
        Hermanas.guardaPublico(c, "lectura_cobalto", lec.toString())

        // Las portadas que aún no están en el móvil: se bajan y, al llegar, se vuelve a reflejar
        if (faltan.isNotEmpty()) PortadasFamilia.baja(faltan) { kotlin.concurrent.thread { runCatching { refrescar(c, Store(c)) } } }
        return cambio
    }

    /** Lo de Azabache que viaja en el nodo («az»: apuntes, capítulos, imagen de fondo…): se pone en las fichas reflejadas que no tienen cambios sin subir. */
    private fun conAz(c: Context, listaG: String, listaP: String, gs: List<Game>, ps: List<Play>, nodos: Map<String, Nodo>, enl: Map<String, Long>): Pair<List<Game>, List<Play>> {
        val porId = enl.entries.associate { it.value to it.key }
        fun az(id: Long): Map<String, Any?>? = porId[id]?.let { nodos[it] }?.texto("az")?.takeIf { it.startsWith("{") }?.let { runCatching { Json.lee(it).comoMapa() }.getOrNull() }?.takeIf { it.isNotEmpty() }
        val g2 = gs.map { g ->
            val a = az(g.id) ?: return@map g
            val o = gameToJson(g)
            if (derivado(c, "$listaG:${g.id}")?.let { Store.canon(it) != Store.canon(o) } == true) return@map g   // cambiado aquí y aún no subido: manda lo de aquí
            a.forEach { (k, v) -> if (k !in DE_LA_HERMANA) o.put(k, aJson(v)) }
            runCatching { gameFromJson(o) }.getOrDefault(g)
        }
        val p2 = ps.map { p ->
            val a = az(p.id) ?: return@map p
            val o = playToJson(p)
            if (derivado(c, "$listaP:${p.id}")?.let { Store.canon(it) != Store.canon(o) } == true) return@map p
            a.forEach { (k, v) -> if (k !in DE_LA_HERMANA) o.put(k, aJson(v)) }
            runCatching { playFromJson(o) }.getOrDefault(p)
        }
        return g2 to p2
    }

    /** Cómo quedaron las fichas reflejadas al leerlas de la cuenta (para subir luego solo lo que cambies aquí). */
    private fun recuerda(c: Context, lista: String, l: List<JSONObject>) { l.forEach { ponDerivado(c, "$lista:${it.optLong("id")}", it) } }

    /**
     * Una vez en cada dispositivo: las notas, reseñas y apuntes que ya tenías aquí de los juegos de Hiedra y los libros de Marfil pasan
     * al nodo de cada uno en la cuenta (si la cuenta aún no tiene nota). Así no se pierde nada al pasar a la familia.
     */
    fun migraValoraciones(c: Context, store: Store) {
        val p = prefs(c)
        if (p.getBoolean("migradas", false)) return
        for ((lista, kind, valoradas) in listOf(Triple("games", GAMES.id, true), Triple("plays", GAMES.id, false), Triple("books", BOOKS.id, true), Triple("bplays", BOOKS.id, false))) {
            val elems = if (valoradas) store.load(kind).map { gameToJson(it) } else store.loadPlays(kind).map { playToJson(it) }
            for (e in elems) if (esReflejo(c, lista, e.optLong("id")) && derivado(c, "$lista:${e.optLong("id")}") == null) runCatching { subeReflejo(c, lista, e) }
        }
        p.edit().putBoolean("migradas", true).apply()
    }

    /** ¿Es esta ficha el reflejo de un juego de Hiedra o un libro de Marfil? */
    fun esReflejo(c: Context, lista: String, id: Long): Boolean = when (lista) {
        "games", "plays" -> Hermanas.enlaces(c, "hiedra").values.contains(id)
        "books", "bplays" -> Hermanas.enlaces(c, "marfil").values.contains(id)
        else -> false
    }

    /**
     * Lo que has cambiado aquí de una ficha reflejada va a la cosa de la cuenta: la nota, los criterios y la reseña; el estado (si lo
     * has cambiado aquí: «Terminar y valorar», volver a pendiente…); la portada (si la has cambiado aquí); y lo de Azabache («az»).
     * Lo que no has tocado aquí no se escribe: así nunca pisa lo que haya cambiado la hermana.
     */
    fun subeReflejo(c: Context, lista: String, e: JSONObject) {
        val deHiedra = lista == "games" || lista == "plays"
        val id = e.optLong("id")
        val enl = Hermanas.enlaces(c, if (deHiedra) "hiedra" else "marfil")
        val suyo = enl.entries.firstOrNull { it.value == id }?.key ?: return
        val col = LaFamilia.coleccion(if (deHiedra) "juegos" else "libros")
        val ref = if (deHiedra) suyo else Hermanas.kidDe(suyo)
        val valorada = lista == "games" || lista == "books"
        val otraLista = when (lista) { "games" -> "plays"; "plays" -> "games"; "books" -> "bplays"; else -> "books" }
        val antes = derivado(c, "$lista:$id")
        val veniaDeLaOtra = antes == null && derivado(c, "$otraLista:$id") != null
        val cambios = LinkedHashMap<String, Any?>()
        val nodo = col.nodo(ref)
        if (valorada) {
            val g = gameFromJson(e)
            val notaAntes = antes?.let { listOf(it.optInt("general"), it.optInt("gfx"), it.optInt("story"), it.optInt("music"), it.optInt("gameplay")) }
            val nota = listOf(g.general, g.gfx, g.story, g.music, g.gameplay)
            // (la primera vez —lo de antes de la familia— solo si de verdad tiene nota y la cuenta aún no)
            if ((antes != null && notaAntes != nota) || (antes == null && !Hermanas.sinValorar(g) && nodo?.get("nota") == null)) { cambios["nota"] = g.general.toLong(); cambios["crit"] = nota.drop(1).map { it.toLong() } }
            if ((antes != null && antes.optString("review") != g.review) || (antes == null && g.review.isNotBlank() && nodo?.get("resena") == null)) cambios["resena"] = g.review
            if (veniaDeLaOtra) cambios["estado"] = "terminado"
        } else {
            val p = playFromJson(e)
            val estado = when (p.status) { 1 -> "en_curso"; 2 -> "abandonado"; else -> "pendiente" }
            if ((antes != null && antes.optInt("status", -1) != p.status) || veniaDeLaOtra) cambios["estado"] = estado
        }
        // Lo de Azabache (sin lo que manda la hermana)
        val az = JSONObject(); e.keys().forEach { k -> if (k !in DE_LA_HERMANA) az.put(k, e.get(k)) }
        val azAntes = antes?.let { a -> JSONObject().also { o -> a.keys().forEach { k -> if (k !in DE_LA_HERMANA) o.put(k, a.get(k)) } } }
        if (az.length() > 0 && (azAntes == null || Store.canon(azAntes) != Store.canon(az)) && (antes != null || nodo?.get("az") == null)) cambios["az"] = az.toString()
        // La portada, si la has cambiado aquí: pasa a ser la de la cuenta (la misma en la hermana)
        val cover = e.optString("cover")
        if (antes != null && cover.isNotBlank() && cover != antes.optString("cover")) runCatching {
            val bytes = if (cover.startsWith("http")) java.net.URL(cover).openStream().use { it.readBytes() } else java.io.File(cover.removePrefix("file://")).readBytes()
            PortadasFamilia.guardar(bytes)?.let { h -> cambios["portada"] = h; if (cover.startsWith("http")) cambios["pu"] = cover }
        }
        if (cambios.isNotEmpty()) col.pon(ref, cambios)
        ponDerivado(c, "$lista:$id", e)
        if (veniaDeLaOtra) ponDerivado(c, "$otraLista:$id", null)
    }

    private fun aJson(v: Any?): Any? = when (v) {
        is Map<*, *> -> JSONObject(Json.escribe(v))
        is List<*> -> JSONArray(Json.escribe(v))
        null -> JSONObject.NULL
        else -> v
    }
}

/** Lo que es solo de Azabache, en la colección `azabache` de la cuenta: cada elemento de cada lista por separado. */
object AzabacheNube {
    private fun col() = LaFamilia.coleccion("azabache")
    /** Las claves de los servicios: cómo se llaman aquí y en la cuenta (`/claves`). */
    private val CLAVES = mapOf("sgdb" to "steamgriddb", "rawg" to "rawg", "igdbId" to "igdb_id", "igdbSecret" to "igdb_secreto", "steamKey" to "steam_key", "steamUser" to "steam_id", "tmdb" to "tmdb")

    /** La última versión de cada clave que este Azabache vio en la cuenta (o puso él): así sabe cuáles has cambiado tú aquí. */
    private fun vistas(c: Context) = c.getSharedPreferences("familia_claves", Context.MODE_PRIVATE)

    /**
     * Una clave que acabas de poner (o quitar) en Azabache: a la cuenta al momento, y la tienen todas las apps. Si es la misma que
     * ya hay en la cuenta (porque viene de ella), no se sube nada.
     */
    fun claveCambiada(c: Context, aqui: String, valor: String) {
        val alla = CLAVES[aqui] ?: return
        val x = valor.trim()
        runCatching {
            if (!LaFamilia.familia.sesion.dentro) return
            if (x != LaFamilia.familia.clave(alla)) LaFamilia.familia.ponClave(alla, x)
            vistas(c).edit().putString(alla, x).apply()
        }
    }

    private fun itemUpdated(o: JSONObject): Long = maxOf(o.optLong("updated", 0L), o.optLong("sv", 0L))
    private fun esLista(a: JSONArray): Boolean = a.length() > 0 && (0 until a.length()).all { (a.opt(it) as? JSONObject)?.has("id") == true }

    /** Sube lo de Azabache (solo lo que ha cambiado, elemento a elemento). Las fichas reflejadas van al nodo de su hermana. */
    fun sube(c: Context, store: Store) {
        // El token de kDrive de este móvil (de antes) pasa una vez a la cuenta: así lo tienen las cuatro apps
        runCatching { KDrive.migraALaCuenta(c) }
        val data = JSONObject(store.syncData())
        for (k in data.keys()) {
            val v = data.get(k)
            when {
                // Las claves que había aquí de antes pasan a la cuenta; una que otra app ha cambiado en la cuenta y aquí no has tocado, no
                // se pisa con la vieja (llega con la próxima bajada)
                k == "keys" -> (v as? JSONObject)?.let { o -> val vis = vistas(c); CLAVES.forEach { (aqui, alla) ->
                    val x = o.optString(aqui).trim(); val cuenta = LaFamilia.familia.clave(alla); val visto = vis.getString(alla, null)
                    val tuya = if (visto != null) x != visto else cuenta.isBlank()
                    if (x.isNotBlank() && x != cuenta && tuya) { LaFamilia.familia.ponClave(alla, x); vis.edit().putString(alla, x).apply() }
                } }
                k == "tombs" -> (v as? JSONObject)?.let { t -> t.keys().forEach { tk ->
                    val ref = tk.replaceFirst(':', '~'); val hora = t.optLong(tk).coerceAtLeast(1L)
                    val n = col().nodo(ref)
                    if (n == null || (!n.borrado && (n.m["o"] ?: 0L) < hora)) col().pon(ref, mapOf("b" to hora), hora)
                } }
                v is JSONArray && esLista(v) -> for (i in 0 until v.length()) {
                    val e = v.getJSONObject(i)
                    if (CuentaHermanas.esReflejo(c, k, e.optLong("id"))) { CuentaHermanas.subeReflejo(c, k, e); continue }
                    // (como texto: dentro puede haber nombres que Firebase no admite como claves)
                    col().cambia("$k~${e.optLong("id")}", mapOf("o" to e.toString(), "b" to 0L), itemUpdated(e).takeIf { it > 0 })
                }
                else -> col().cambia(k, mapOf("v" to JSONArray().put(v).toString()))
            }
        }
        // La apariencia de Azabache, para que las hermanas la puedan copiar
        data.optJSONObject("ajustes")?.let { aj -> LaFamilia.coleccion("ajustes").cambia("azabache", mapOf("at" to aj.optLong("at"), "skin" to Json.lee(aj.optJSONObject("skin")?.toString() ?: "{}"))) }
    }

    /** Lo de Azabache de la cuenta, con la forma de siempre (la de [Store.syncData]), para mezclarlo aquí. */
    fun datos(uid: String? = null): JSONObject {
        val c = if (uid == null) col() else LaFamilia.ajena(uid, "azabache")
        val o = JSONObject(); val tombs = JSONObject()
        for (n in c.todos().values) {
            if (n.ref.contains('~')) {
                val lista = n.ref.substringBefore('~')
                if (n.borrado) { tombs.put("$lista:${n.ref.substringAfter('~')}", n.entero(Nodo.B)); continue }
                val e = n.texto("o").takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() } ?: continue
                (o.optJSONArray(lista) ?: JSONArray().also { o.put(lista, it) }).put(e)
            } else if (!n.borrado) n.texto("v").takeIf { it.startsWith("[") }?.let { runCatching { JSONArray(it).get(0) }.getOrNull() }?.let { o.put(n.ref, it) }
        }
        o.put("tombs", tombs)
        if (uid == null) o.put("keys", JSONObject().also { k -> CLAVES.forEach { (aqui, alla) -> LaFamilia.familia.clave(alla).takeIf { it.isNotBlank() }?.let { k.put(aqui, it) } } })
        return o
    }

    /** Mezcla aquí lo que tus otros Azabache han puesto en la cuenta. Devuelve si ha cambiado algo. */
    fun baja(store: Store): Boolean {
        val d = datos()
        // Las claves de la cuenta mandan (se ponen una vez, en cualquier app de la familia)
        store.applyKeys(d.optJSONObject("keys"), onlyEmpty = false)
        vistas(store.contexto).edit().also { e -> CLAVES.values.forEach { alla -> LaFamilia.familia.clave(alla).takeIf { it.isNotBlank() }?.let { e.putString(alla, it) } } }.apply()
        if (d.length() <= 2) return false
        return store.mergeRemote(d.toString())
    }

    /** (Para las pruebas.) */
    @Suppress("unused") internal fun mapa(v: Any?) = v.comoMapa()
}
