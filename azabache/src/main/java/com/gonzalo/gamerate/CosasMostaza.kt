package com.gonzalo.gamerate

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.PortadasFamilia
import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Fechas
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.Nodo
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Las películas y las series son de Mostaza (desde Azabache 1.6.3), como los juegos de Hiedra y los libros de Marfil: no hay copia. Cada
 * una es su nodo `titulos/<ref>` de la cuenta (`ref` = su código de IMDb, o `tmdb:<id>` / `tvmaze:<id>` si no se sabe) y Azabache lo LEE tal
 * cual para sus categorías «Películas» (las de TMDB: `tipo` = `movie`) y «Series» (las de TVmaze: `tipo` = `series`). Lo que cambias aquí va
 * a ese mismo nodo, campo a campo, y solo lo que has cambiado tú (Mostaza no se pisa):
 *
 *  - de la cosa (lo manda Mostaza): `t`, `estado`, `fav`, `fin` (cuándo la terminaste), `portada`/`pu`, `fondo`, `an`;
 *  - la valoración (la escribe Azabache, la lee Mostaza): `nota`, `crit`, `resena`;
 *  - lo propio de Azabache (apuntes, dónde la viste, director, capítulos…): `az`; `azid`, el número de la ficha en Azabache (el de antes:
 *    sagas, diario y maquetas lo siguen encontrando), y `azk`, su categoría;
 *  - lo visto: el tiempo de `tiempo/<ref>~<dispositivo>` (suma de tus dispositivos) y, en las series, los episodios de `progreso`.
 *
 * Empieza cuando Mostaza ha traído lo que había aquí (su migración deja `azabache/a_mostaza` en la cuenta); antes, todo sigue como siempre.
 * Los nodos de antes (`azabache/items_<categoría>~<id>`, `iplays_…`) no se tocan: se quedan como estaban (Azabache de escritorio aún los lee).
 */
object CosasMostaza {
    class Vista(val games: List<Game>, val plays: List<Play>, val refPorId: Map<Long, String>, val nodoPorId: Map<Long, Nodo>)

    private val cache = HashMap<Int, Vista>()
    private val sucias = HashSet<Int>()
    @Volatile private var oyendoDe: Any? = null
    private val principal by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var avisoPendiente = false
    /** (En las pruebas: activa sin mirar la marca de la cuenta.) */
    @Volatile var siempreParaPruebas = false

    private fun col(): Coleccion = LaFamilia.coleccion("titulos")

    /** ¿Ya son de Mostaza las películas y series? (lo dice la cuenta: Mostaza ha traído lo de aquí) */
    fun activa(c: Context): Boolean {
        if (siempreParaPruebas) return true
        return runCatching {
            LaFamilia.inicia(c)
            LaFamilia.familia.sesion.dentro && LaFamilia.coleccion("azabache").nodo("a_mostaza")?.let { !it.borrado } == true
        }.getOrDefault(false)
    }

    /** El tipo de Mostaza de una categoría de Azabache (null: no es de películas ni de series). */
    fun tipoDe(k: Kind?): String? = when { k == null -> null; k.isMovie -> "movie"; k.isTv -> "series"; else -> null }

    /** ¿Esta categoría se lee de Mostaza? */
    fun de(c: Context, k: Kind?): Boolean = tipoDe(k) != null && activa(c)

    private fun prepara(c: Context) {
        LaFamilia.inicia(c)
        val fam = LaFamilia.familia
        if (oyendoDe === fam) return
        synchronized(this) {
            if (oyendoDe === fam) return
            oyendoDe = fam
            val todo = { synchronized(this) { cache.clear() }; aviso() }
            col().alCambiar { todo() }
            LaFamilia.coleccion("tiempo").alCambiar { todo() }
            LaFamilia.coleccion("progreso").alCambiar { todo() }
            cache.clear()
        }
    }

    private fun aviso() {
        Store.otraGeneracion()
        if (avisoPendiente) return
        avisoPendiente = true
        principal.postDelayed({ avisoPendiente = false; Hermanas.repinta() }, 60)
    }

    // ================================================================ leer

    fun vista(c: Context, k: Kind): Vista {
        prepara(c)
        synchronized(this) {
            cache[k.id]?.let { if (k.id !in sucias) return it }
            sucias.remove(k.id)
            val faltan = ArrayList<String>()
            val v = construye(col().vivos(), LaFamilia.coleccion("tiempo").vivos(), LaFamilia.coleccion("progreso").vivos(), tipoDe(k) ?: "movie", k.id,
                portada = { h -> PortadasFamilia.archivo(c, h).ifBlank { if (h.isNotBlank()) faltan += h; "" } }, puesta = { portadaPuesta[it] })
            if (faltan.isNotEmpty()) PortadasFamilia.baja(faltan) { synchronized(this) { cache.clear() }; aviso() }
            cache[k.id] = v
            return v
        }
    }

    /** Lo de tu pareja (de su cuenta, solo para leer). */
    fun vistaDe(c: Context, uid: String, k: Kind): Vista {
        prepara(c)
        return construye(LaFamilia.ajena(uid, "titulos").vivos(), LaFamilia.ajena(uid, "tiempo").vivos(), LaFamilia.ajena(uid, "progreso").vivos(), tipoDe(k) ?: "movie", k.id,
            portada = { h -> PortadasFamilia.archivo(c, h) })
    }

    /** Un número fijo para un título de la cuenta (el mismo en todos tus dispositivos), si no tiene el suyo de Azabache. */
    fun idDeRef(ref: String): Long = Cosas.idDeRef("titulos:$ref")

    /** El número de la ficha: el de Azabache (`azid`); si no tiene, el de su web (TMDB o TVmaze, el que usaba Azabache); si no, uno fijo. */
    fun idDe(n: Nodo, tipo: String): Long =
        n.entero("azid").takeIf { it != 0L } ?: (if (tipo == "movie") n.entero("tmdb") else n.entero("tvmaze")).takeIf { it in 1 until 100_000_000_000L } ?: idDeRef(n.ref)

    private fun dia(ms: Long): Long = if (ms > 0) Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() else 0L
    private fun azDe(n: Nodo): JSONObject = n.texto("az").takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    /** «1×03 · Título», «2x10», «S01E03»: (temporada, episodio). */
    fun episodioDe(nombre: String): Pair<Int, Int>? {
        Regex("(\\d{1,2})\\s*[×xX]\\s*(\\d{1,3})").find(nombre)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        Regex("[sS](\\d{1,2})\\s*[eE](\\d{1,3})").find(nombre)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        return null
    }

    /**
     * Las fichas de Azabache de una categoría a partir de los nodos de Mostaza (sin red: lo prueba ContratoHermanasTest con lo que escribe
     * Mostaza de verdad). [portada]: el archivo de una imagen de la cuenta ("" si aún no está).
     */
    fun construye(titulos: List<Nodo>, tiempo: List<Nodo>, progreso: List<Nodo>, tipo: String, kindId: Int, portada: (String) -> String = { "" }, puesta: (String) -> String? = { null }): Vista {
        val horas = tiempo.groupBy { it.texto("ref") }
        val vistos = progreso.groupBy { it.texto("ref") }
        val games = ArrayList<Game>(); val plays = ArrayList<Play>()
        val refs = HashMap<Long, String>(); val nodos = HashMap<Long, Nodo>()
        for (n in titulos.filter { it.texto("t").isNotBlank() && it.texto("tipo").ifBlank { "movie" } == tipo }.sortedBy { it.ref }) {
            var id = idDe(n, tipo)
            if (id in refs) id = idDeRef(n.ref)
            if (id in refs) continue
            refs[id] = n.ref; nodos[id] = n
            val o = aFicha(n, id, horas[n.ref].orEmpty(), vistos[n.ref].orEmpty(), portada, puesta)
            runCatching { if (n.texto("estado") == "terminado") games += gameFromJson(o) else plays += playFromJson(o) }
        }
        return Vista(games, plays, refs, nodos)
    }

    private fun aFicha(n: Nodo, id: Long, horas: List<Nodo>, vistos: List<Nodo>, portada: (String) -> String, puesta: (String) -> String?): JSONObject {
        val o = azDe(n)
        o.put("id", id).put("name", n.texto("t")).put("fav", n.si("fav")).put("review", n.texto("resena"))
        o.put("cover", puesta(n.ref) ?: portada(n.texto("portada")).ifBlank { n.texto("pu") })
        val crit = n.lista("crit").map { (it as? Number)?.toInt() ?: 0 }
        val valorada = n["nota"] != null
        o.put("general", if (valorada) n.entero("nota").toInt().coerceIn(0, 100) else 0)
        listOf("gfx", "story", "music", "gameplay").forEachIndexed { i, k -> o.put(k, if (valorada) crit.getOrElse(i) { 0 } else 0) }
        val ms = horas.sumOf { it.entero("total") }
        o.put("minutes", maxOf((ms / 60_000L).toInt(), o.optInt("minutes", 0)))
        val fin = Fechas.ms(n.entero("fin")).let { if (it > 0) it else if (n.texto("estado") == "terminado") (n.m["estado"] ?: 0L).takeIf { x -> x > 946_684_800_000L } ?: 0L else 0L }
        o.put("end", dia(fin))
        if (o.optLong("start", 0L) <= 0L) o.put("start", dia(Fechas.ms(n.entero("an"))))
        o.put("updated", n.m.values.maxOrNull() ?: n.u)
        o.put("status", when (n.texto("estado")) { "en_curso" -> 1; "abandonado" -> 2; else -> 0 })
        if (o.optString("hero").isBlank()) o.put("hero", n.texto("fondo"))
        if (!o.has("entries")) o.put("entries", JSONArray())
        // Los episodios: lo que dice Mostaza (lo visto en `progreso`) manda sobre las marcas de antes
        val porEp = vistos.associate { (it.entero("temp").toInt() to it.entero("ep").toInt()) to it.si("visto") }
        o.optJSONArray("parts")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { p -> episodioDe(p.optString("n"))?.let { e -> porEp[e]?.let { v -> p.put("d", v) } } }
        }
        return o
    }

    fun refDe(c: Context, k: Kind, id: Long): String? = vista(c, k).refPorId[id]

    /** El título de Mostaza con esa ref (para «titulo:<ref>», lo que manda Mostaza): su categoría y su número aquí. */
    fun buscaRef(c: Context, kinds: List<Kind>, ref: String): Pair<Kind, Long>? {
        for (k in kinds.filter { tipoDe(it) != null }) vista(c, k).refPorId.entries.firstOrNull { it.value == ref }?.let { return k to it.key }
        return null
    }

    // ================================================================ escribir

    private fun noAz() = setOf("id", "updated", "sv", "status", "name", "cover", "general", "gfx", "story", "music", "gameplay", "review", "fav", "end", "minutes")

    private fun estadoDe(o: JSONObject, valorada: Boolean): String =
        if (valorada) "terminado" else when (o.optInt("status", 0)) { 1 -> "en_curso"; 2 -> "abandonado"; else -> "pendiente" }

    private fun azDeFicha(o: JSONObject): JSONObject = JSONObject().also { a -> val fuera = noAz(); o.keys().forEach { k -> if (k !in fuera) a.put(k, o.get(k)) } }

    private val guardadas = HashMap<String, Set<Long>>()
    private val porBorrar = HashMap<Pair<Int, Long>, String>()

    /**
     * Guarda una lista de una categoría de películas o series: solo lo que has cambiado tú ([antes]: la lista como la tenías). Lo que se va
     * de las dos listas (vistas y en curso) se quita de la cuenta (queda borrado, como al quitarla en Mostaza); lo que pasa de una a otra
     * cambia de estado.
     */
    fun guarda(c: Context, k: Kind, valorada: Boolean, nueva: List<JSONObject>, antes: List<JSONObject>?) {
        synchronized(this) {
            val v = vista(c, k)
            val ahora = (if (valorada) v.games.map { gameToJson(it) } else v.plays.map { playToJson(it) }).associateBy { it.optLong("id") }
            val base = (antes ?: ahora.values.toList()).associateBy { it.optLong("id") }
            for (o in nueva) {
                val id = o.optLong("id")
                val a = base[id]
                if (a != null && Store.canon(a) == Store.canon(o)) continue
                escribe(c, k, v, id, o, a, valorada)
                porBorrar.remove(k.id to id)
            }
            val clave = "${k.id}:$valorada"
            guardadas[clave] = nueva.map { it.optLong("id") }.toSet()
            val idos = base.keys - nueva.map { it.optLong("id") }.toSet()
            if (idos.isNotEmpty()) {
                idos.forEach { id -> v.refPorId[id]?.let { porBorrar[k.id to id] = it } }
                principal.postDelayed({ borraLosQueSeFueron(c, k) }, 1500L)
            }
        }
    }

    private fun borraLosQueSeFueron(c: Context, k: Kind) {
        synchronized(this) {
            val v = vista(c, k)
            val enVistas = guardadas["${k.id}:true"] ?: v.games.map { it.id }.toSet()
            val enCurso = guardadas["${k.id}:false"] ?: v.plays.map { it.id }.toSet()
            for ((x, ref) in porBorrar.entries.filter { it.key.first == k.id }.toList()) {
                porBorrar.remove(x)
                if (x.second !in enVistas && x.second !in enCurso) runCatching { col().borra(ref) }
            }
            guardadas.remove("${k.id}:true"); guardadas.remove("${k.id}:false")
        }
    }

    /** Lo de una copia de seguridad: lo nuevo y lo cambiado a la cuenta, sin borrar nada. */
    fun importa(c: Context, k: Kind, valorada: Boolean, items: List<JSONObject>) {
        synchronized(this) {
            val v = vista(c, k)
            val ahora = (v.games.map { gameToJson(it) } + v.plays.map { playToJson(it) }).associateBy { it.optLong("id") }
            for (o in items) {
                val id = o.optLong("id"); val a = ahora[id]
                if (a != null && Store.canon(a) == Store.canon(o)) continue
                escribe(c, k, v, id, o, a?.takeIf { valorada == v.games.any { it.id == id } }, valorada)
            }
        }
    }

    private fun refNuevo(k: Kind, id: Long): String = when {
        id in 1 until 100_000_000_000L && k.isMovie -> "tmdb:$id"
        id in 1 until 100_000_000_000L -> "tvmaze:$id"
        else -> "azabache:$id"
    }

    private fun escribe(c: Context, k: Kind, v: Vista, id: Long, o: JSONObject, antes: JSONObject?, valorada: Boolean) {
        val col = col()
        val tipo = tipoDe(k) ?: return
        val ref = v.refPorId[id] ?: col.todos().values.firstOrNull { it.entero("azid") == id && it.entero("azk").toInt() == k.id }?.ref ?: refNuevo(k, id)
        val actual = v.nodoPorId[id] ?: col.todos()[ref]
        val cambios = LinkedHashMap<String, Any?>()
        fun cambiado(x: String) = antes == null || Store.canon(antes.opt(x)) != Store.canon(o.opt(x))
        val nuevaAqui = actual == null || actual.borrado
        val deLaOtraLista = antes == null && !nuevaAqui
        if (nuevaAqui) {
            cambios["t"] = o.optString("name"); cambios["tipo"] = tipo; cambios["azid"] = id; cambios["azk"] = k.id.toLong()
            if (actual == null) {
                cambios["id"] = ref
                if (id in 1 until 100_000_000_000L) cambios[if (tipo == "movie") "tmdb" else "tvmaze"] = id
            }
            cambios["an"] = Fechas.ms(o.optLong("start")).takeIf { it > 1_000_000_000_000L } ?: System.currentTimeMillis()
            if (actual?.borrado == true) cambios["b"] = 0L
        } else {
            if (actual!!.entero("azid") == 0L) cambios["azid"] = id
            if (actual.entero("azk") == 0L) cambios["azk"] = k.id.toLong()
        }
        if (!deLaOtraLista) {
            if (cambiado("name")) cambios["t"] = o.optString("name")
            if (cambiado("fav")) cambios["fav"] = o.optBoolean("fav")
        }
        val estado = estadoDe(o, valorada)
        val estadoAntes = if (antes == null) actual?.texto("estado") else estadoDe(antes, valorada)
        if (nuevaAqui || estado != estadoAntes) cambios["estado"] = estado
        if (valorada && (cambiado("end") || deLaOtraLista) && o.optLong("end") > 0) cambios["fin"] = Fechas.ms(o.optLong("end"))
        val notas = listOf("general", "gfx", "story", "music", "gameplay")
        if (valorada && (notas.any { cambiado(it) } || nuevaAqui || deLaOtraLista)) {
            val vs = notas.map { o.optInt(it) }
            if (!(nuevaAqui || deLaOtraLista) || vs.any { it != 0 } || actual?.get("nota") != null) { cambios["nota"] = vs[0].toLong(); cambios["crit"] = vs.drop(1).map { it.toLong() } }
        }
        if (cambiado("review") && (o.optString("review").isNotBlank() || antes != null)) cambios["resena"] = o.optString("review")
        val az = azDeFicha(o)
        val azAntes = antes?.let { azDeFicha(it) } ?: actual?.let { azDe(it) }
        if (Store.canon(az) != Store.canon(azAntes ?: JSONObject())) cambios["az"] = az.toString()
        actual?.takeIf { !it.borrado }?.let { n -> cambios.keys.toList().forEach { x -> if (x != "estado" || !nuevaAqui) if (igual(n[x], cambios[x])) cambios.remove(x) } }
        if (cambios.isNotEmpty()) col.cambia(ref, cambios)
        val cover = o.optString("cover")
        if (cover.isNotBlank() && cambiado("cover") && cover != PortadasFamilia.archivo(c, actual?.texto("portada").orEmpty()) && cover != actual?.texto("pu")) ponPortada(ref, cover)
    }

    private fun igual(a: Any?, b: Any?): Boolean = Store.canon(aOrg(a)) == Store.canon(aOrg(b))
    private fun aOrg(v: Any?): Any? = when (v) {
        is Map<*, *> -> JSONObject(Json.escribe(v)); is List<*> -> JSONArray(Json.escribe(v)); is Int -> v.toLong(); null -> JSONObject.NULL; else -> v
    }

    private val portadas by lazy { java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "azabache-portadas-mostaza").apply { isDaemon = true } } }
    private val portadaPuesta = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun ponPortada(ref: String, cover: String) {
        portadaPuesta[ref] = cover
        portadas.execute {
            try {
                val bytes = if (cover.startsWith("http")) java.net.URL(cover).openConnection().apply { connectTimeout = 15_000; readTimeout = 20_000 }.getInputStream().use { it.readBytes() }
                    else java.io.File(cover.removePrefix("file://").removePrefix("file:")).readBytes()
                PortadasFamilia.guardar(bytes)?.let { h -> col().cambia(ref, if (cover.startsWith("http")) mapOf("portada" to h, "pu" to cover) else mapOf("portada" to h)) }
            } catch (e: Exception) {
            } finally { if (portadaPuesta[ref] == cover) portadaPuesta.remove(ref); synchronized(this) { cache.clear() }; aviso() }
        }
    }

    /** (Para las pruebas.) */
    fun olvidaParaPruebas() { synchronized(this) { cache.clear(); sucias.clear(); oyendoDe = null; guardadas.clear(); porBorrar.clear() }; siempreParaPruebas = false }
}
