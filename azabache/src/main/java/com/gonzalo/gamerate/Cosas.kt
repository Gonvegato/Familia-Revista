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
 * Los juegos y los libros de Azabache SON los de la cuenta de la familia: no hay copia. Cada uno es su nodo (`juegos/<id>`,
 * `libros/<kid>`), el mismo que usan Hiedra y Marfil, y lo que se ve aquí sale de ahí cada vez (con una caché que se rehace sola
 * cuando cambia algo en la cuenta, venga de donde venga). Lo que cambias aquí va a ese mismo nodo, campo a campo, y solo lo que
 * has cambiado tú: así nunca se pisa lo que Hiedra o Marfil hayan cambiado mientras.
 *
 *  - De la cosa (lo comparten las apps): `t` (nombre), `estado`, `fav`, `fin` (cuándo lo terminaste), `portada`; en los juegos `pl`,
 *    `dh`/`de`/`dc`; en los libros `a` (autor) y `pg`.
 *  - De la valoración (la escribe Azabache, la leen todas): `nota` (0 = sin valorar), `crit` (los cuatro criterios), `resena`.
 *  - Lo que solo usa Azabache (apuntes, capítulos, fondo, logo, cuándo empezaste…): `az`, el resto de la ficha tal cual.
 *  - `azid`: el número de la ficha en Azabache, el mismo en todos tus dispositivos (lo usan tus sagas, el diario, las maquetas…).
 *
 * Sin conexión funciona igual (la cuenta guarda en el móvil lo que falta por subir).
 */
object Cosas {
    const val JUEGOS = 0
    const val LIBROS = 1

    /** Los nombres de las listas de Azabache que son de la cuenta (y no se guardan aparte). */
    val LISTAS = setOf("games", "plays", "books", "bplays")
    fun kindDe(lista: String): Int = if (lista == "games" || lista == "plays") JUEGOS else LIBROS
    private fun esValoradas(lista: String) = lista == "games" || lista == "books"

    /** Lo que se ve: las fichas valoradas (terminadas) y las demás, y de qué cosa de la cuenta es cada una. */
    class Vista(val games: List<Game>, val plays: List<Play>, val refPorId: Map<Long, String>, val nodoPorId: Map<Long, Nodo>)

    private val cache = arrayOfNulls<Vista>(2)
    private val sucia = booleanArrayOf(true, true)
    @Volatile private var oyendoDe: Any? = null
    private val principal by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var avisoPendiente = false

    private fun nombreCol(kind: Int) = if (kind == JUEGOS) "juegos" else "libros"
    private fun col(kind: Int): Coleccion = LaFamilia.coleccion(nombreCol(kind))

    /**
     * ¿Ya son de la cuenta los juegos y libros de este Azabache? Desde que, con la sesión abierta, la cuenta se ha bajado entera y lo de
     * antes ha pasado a ella ([migra]). Antes (sin haber entrado nunca), Azabache sigue con sus listas de siempre.
     */
    fun activa(c: Context): Boolean {
        val p = c.getSharedPreferences("cosas_cuenta", Context.MODE_PRIVATE)
        if (p.getBoolean("migradas", false)) return true
        return runCatching { intenta(c) }.getOrDefault(false)
    }

    private val migrando = java.util.concurrent.atomic.AtomicBoolean(false)
    /** (En las pruebas, la migración se hace en el momento.) */
    @Volatile var migrarEnElMomento = false

    private fun intenta(c: Context): Boolean {
        prepara(c)
        val fam = LaFamilia.familia
        if (!fam.sesion.dentro || !col(JUEGOS).completa || !col(LIBROS).completa) return false
        if (!migrando.compareAndSet(false, true)) return false
        val g = c.getSharedPreferences("gamerate", Context.MODE_PRIVATE)
        fun lee(n: String) = runCatching { JSONArray(g.getString(n, "[]") ?: "[]") }.getOrDefault(JSONArray()).let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
        val hazla = {
            try { migra(c, LISTAS.associateWith { lee(it) }) } finally { migrando.set(false) }
            Store.otraGeneracion()
            Hermanas.repinta()
        }
        // (pueden ser cientos de fichas: en segundo plano; mientras, Azabache sigue con sus listas de siempre)
        if (migrarEnElMomento) { hazla(); return true }
        kotlin.concurrent.thread(name = "azabache-a-la-cuenta") { runCatching { hazla() } }
        return false
    }

    /** La cuenta de la familia, y oírla (una vez por cuenta): cuando cambia algo, la vista se rehace y la app se repinta. */
    private fun prepara(c: Context) {
        LaFamilia.inicia(c)
        val fam = LaFamilia.familia
        if (oyendoDe === fam) return
        synchronized(this) {
            if (oyendoDe === fam) return
            oyendoDe = fam
            col(JUEGOS).alCambiar { cambio(JUEGOS) }
            col(LIBROS).alCambiar { cambio(LIBROS) }
            LaFamilia.coleccion("tiempo").alCambiar { cambio(JUEGOS); cambio(LIBROS) }
            sucia[0] = true; sucia[1] = true
        }
    }

    private fun cambio(kind: Int) {
        synchronized(this) { sucia[kind] = true }
        Store.otraGeneracion()
        if (avisoPendiente) return
        avisoPendiente = true
        // (varios cambios seguidos, un solo repintado)
        principal.postDelayed({ avisoPendiente = false; Hermanas.repinta() }, 60)
    }

    // ================================================================ leer

    fun vista(c: Context, kind: Int): Vista {
        prepara(c)
        synchronized(this) {
            cache[kind]?.let { if (!sucia[kind]) return it }
            sucia[kind] = false
            val v = construye(c, col(kind), LaFamilia.coleccion("tiempo"), kind, Hermanas.enlaces(c, if (kind == JUEGOS) "hiedra" else "marfil"))
            cache[kind] = v
            return v
        }
    }

    /** Lo de tu pareja (de su cuenta, solo para leer). */
    fun vistaDe(c: Context, uid: String, kind: Int): Vista {
        prepara(c)
        return construye(c, LaFamilia.ajena(uid, nombreCol(kind)), LaFamilia.ajena(uid, "tiempo"), kind, emptyMap(), deUid = uid)
    }

    private fun construye(c: Context, col: Coleccion, tiempo: Coleccion, kind: Int, enlaces: Map<String, Long>, deUid: String? = null): Vista {
        val horas = tiempo.vivos().groupBy { it.texto("ref") }
        val games = ArrayList<Game>(); val plays = ArrayList<Play>()
        val refs = HashMap<Long, String>(); val nodos = HashMap<Long, Nodo>()
        val faltan = ArrayList<String>()
        for (n in col.vivos().filter { it.texto("t").isNotBlank() }.sortedBy { it.ref }) {
            var id = idDe(kind, n, enlaces)
            if (id in refs) id = idDeRef(n.ref)
            if (id in refs) continue
            refs[id] = n.ref; nodos[id] = n
            val o = aFicha(c, kind, n, id, horas[n.ref].orEmpty(), faltan)
            runCatching { if (n.texto("estado") == "terminado") games += gameFromJson(o) else plays += playFromJson(o) }
        }
        if (faltan.isNotEmpty()) PortadasFamilia.baja(faltan, deUid) { if (deUid == null) cambio(kind) }
        return Vista(games, plays, refs, nodos)
    }

    /** El número de la ficha: el de la cuenta; si aún no lo tiene, el que tenía aquí (de antes de la familia); si no, uno fijo de la cosa. */
    private fun idDe(kind: Int, n: Nodo, enlaces: Map<String, Long>): Long =
        n.entero("azid").takeIf { it > 0 } ?: enlaces[if (kind == JUEGOS) n.ref else n.texto("clave")] ?: idDeRef(n.ref)

    /** Un número fijo para una cosa de la cuenta (el mismo en todos tus dispositivos). */
    fun idDeRef(ref: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (ch in "familia:$ref") { h = h xor ch.code.toLong(); h *= 0x100000001b3L }
        return (h and Long.MAX_VALUE) % 9_000_000_000_000L + 100_000_000_000L
    }

    private fun dia(ms: Long): Long = if (ms > 0) Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() else 0L

    private fun azDe(n: Nodo): JSONObject = n.texto("az").takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    private fun finDe(kind: Int, n: Nodo): Long {
        val ms = if (kind == JUEGOS) Fechas.ms(n.entero("fin")) else n.lista("fin").mapNotNull { (it as? Number)?.toLong() }.map { Fechas.ms(it) }.maxOrNull() ?: 0L
        // Terminado sin fecha: desde que pasó a «terminado» en la cuenta
        return if (ms > 0) ms else if (n.texto("estado") == "terminado") (n.m["estado"] ?: 0L).takeIf { it > 946_684_800_000L } ?: 0L else 0L
    }

    /** La ficha de Azabache (con la forma de siempre) de una cosa de la cuenta. */
    private fun aFicha(c: Context, kind: Int, n: Nodo, id: Long, horas: List<Nodo>, faltan: MutableList<String>): JSONObject {
        val o = azDe(n)
        o.put("id", id).put("name", n.texto("t")).put("fav", n.si("fav")).put("review", n.texto("resena"))
        // La portada: la imagen de la cuenta (si aún no ha llegado, la dirección de la que salió)
        val h = n.texto("portada")
        o.put("cover", portadaPuesta[n.ref] ?: PortadasFamilia.archivo(c, h).ifBlank { if (h.isNotBlank()) faltan += h; n.texto("pu") })
        val crit = n.lista("crit").map { (it as? Number)?.toInt() ?: 0 }
        val valorada = n["nota"] != null
        o.put("general", if (valorada) n.entero("nota").toInt().coerceIn(0, 100) else 0)
        listOf("gfx", "story", "music", "gameplay").forEachIndexed { i, k -> o.put(k, if (valorada) crit.getOrElse(i) { 0 } else 0) }
        if (kind == JUEGOS) {
            o.put("platform", n.texto("pl")).put("ttbMain", n.entero("dh").toInt()).put("ttbExtra", n.entero("de").toInt()).put("ttbFull", n.entero("dc").toInt())
        } else {
            o.put("creator", n.texto("a")).put("pages", n.entero("pg").toInt())
        }
        val ms = horas.sumOf { it.entero("total") }
        o.put("minutes", maxOf((ms / 60_000L).toInt(), o.optInt("minutes", 0)))
        o.put("end", dia(finDe(kind, n)))
        if (o.optLong("start", 0L) <= 0L) o.put("start", dia(Fechas.ms(n.entero("an"))))
        o.put("updated", n.m.values.maxOrNull() ?: n.u)
        o.put("status", when (n.texto("estado")) { "en_curso" -> 1; "abandonado" -> 2; else -> 0 })
        // El fondo: el de la cuenta (lo ponen Seshat y Bastet, y Jonsu al cambiarlo) manda sobre el que guardaba Jonsu para sí; «-» es que
        // se quitó a propósito. Si aún no ha bajado, se ve el de internet del que salió.
        val fh = n.texto("fondo")
        when {
            fondoPuesto[n.ref] != null -> o.put("hero", fondoPuesto[n.ref])
            fh == PortadasFamilia.SIN_FONDO -> o.put("hero", "")
            fh.isNotBlank() -> o.put("hero", PortadasFamilia.archivo(c, fh).ifBlank { faltan += fh; n.texto("fu") })
        }
        if (!o.has("hero")) o.put("hero", "")
        if (!o.has("entries")) o.put("entries", JSONArray())
        return o
    }

    /** Una lista de Azabache (como texto JSON): de la cuenta. */
    fun listaJson(c: Context, lista: String): JSONArray {
        val v = vista(c, kindDe(lista))
        return JSONArray().also { a -> if (esValoradas(lista)) v.games.forEach { a.put(gameToJson(it)) } else v.plays.forEach { a.put(playToJson(it)) } }
    }

    /** La cosa de la cuenta de una ficha de aquí (id del juego de Hiedra, o el nodo del libro), si la hay. */
    fun refDe(c: Context, kind: Int, id: Long): String? = vista(c, kind).refPorId[id]
    fun nodoDe(c: Context, kind: Int, id: Long): Nodo? = vista(c, kind).nodoPorId[id]

    // ================================================================ escribir

    /** De la ficha, lo que manda la cosa (no va en `az`). */
    private fun noAz(kind: Int) = setOf("id", "updated", "sv", "status", "name", "cover", "general", "gfx", "story", "music", "gameplay", "review", "fav", "end") +
        if (kind == JUEGOS) setOf("platform", "ttbMain", "ttbExtra", "ttbFull") else setOf("creator", "pages")

    private fun estadoDe(o: JSONObject, valorada: Boolean): String =
        if (valorada) "terminado" else when (o.optInt("status", 0)) { 1 -> "en_curso"; 2 -> "abandonado"; else -> "pendiente" }

    private fun azDeFicha(kind: Int, o: JSONObject): JSONObject = JSONObject().also { a -> val fuera = noAz(kind); o.keys().forEach { k -> if (k !in fuera) a.put(k, o.get(k)) } }

    /** Lo que se guardó la última vez de cada lista (para saber si algo se ha ido de las dos: borrado, o solo se ha cambiado de lista). */
    private val guardadas = HashMap<String, Set<Long>>()
    private val porBorrar = HashMap<Pair<Int, Long>, String>()
    private const val ESPERA_BORRADO = 1500L

    /**
     * Guarda una lista de Azabache: solo lo que has cambiado tú ([antes] es la lista tal como la tenías antes del cambio; sin ella, la
     * de la cuenta). Lo que desaparece de las dos listas (valoradas y en curso) se borra de la cuenta; lo que pasa de una a otra
     * cambia de estado.
     */
    fun guarda(c: Context, lista: String, nueva: List<JSONObject>, antes: List<JSONObject>?) {
        val kind = kindDe(lista)
        val valorada = esValoradas(lista)
        synchronized(this) {
            val v = vista(c, kind)
            val ahora = (if (valorada) v.games.map { gameToJson(it) } else v.plays.map { playToJson(it) }).associateBy { it.optLong("id") }
            val base = (antes ?: ahora.values.toList()).associateBy { it.optLong("id") }
            for (o in nueva) {
                val id = o.optLong("id")
                val a = base[id]
                if (a != null && Store.canon(a) == Store.canon(o)) continue      // sin tocar
                escribe(c, kind, v, id, o, a, valorada)
                porBorrar.remove(kind to id)
            }
            guardadas[lista] = nueva.map { it.optLong("id") }.toSet()
            // Lo que ya no está en esta lista: si tampoco está en la otra (al poco), se ha borrado
            val idos = base.keys - nueva.map { it.optLong("id") }.toSet()
            if (idos.isNotEmpty()) {
                idos.forEach { id -> v.refPorId[id]?.let { porBorrar[kind to id] = it } }
                principal.postDelayed({ borraLosQueSeFueron(c, kind) }, ESPERA_BORRADO)
            }
        }
    }

    private fun borraLosQueSeFueron(c: Context, kind: Int) {
        synchronized(this) {
            val (lv, lp) = if (kind == JUEGOS) "games" to "plays" else "books" to "bplays"
            val v = vista(c, kind)
            val enValoradas = guardadas[lv] ?: v.games.map { it.id }.toSet()
            val enCurso = guardadas[lp] ?: v.plays.map { it.id }.toSet()
            for ((k, ref) in porBorrar.entries.filter { it.key.first == kind }.toList()) {
                porBorrar.remove(k)
                if (k.second !in enValoradas && k.second !in enCurso) runCatching { col(kind).borra(ref) }
            }
            guardadas.remove(lv); guardadas.remove(lp)
        }
    }

    /**
     * Escribe en la cosa de la cuenta lo que ha cambiado de una ficha ([antes]: cómo estaba en tu pantalla; null si es nueva aquí o
     * viene de la otra lista). Solo lo que cambia de verdad respecto a la cuenta.
     */
    private fun escribe(c: Context, kind: Int, v: Vista, id: Long, o: JSONObject, antes: JSONObject?, valorada: Boolean) {
        val col = col(kind)
        val nodo = v.nodoPorId[id]
        val ref = v.refPorId[id] ?: buscaPorAzid(col, id) ?: refNuevo(kind, o)
        val actual = nodo ?: col.todos()[ref]
        val cambios = LinkedHashMap<String, Any?>()
        fun cambiado(k: String) = antes == null || Store.canon(antes.opt(k)) != Store.canon(o.opt(k))
        val nuevaAqui = actual == null || actual.borrado
        // (pasar de una lista a la otra no cambia lo que manda la cosa: solo el estado y la valoración)
        val deLaOtraLista = antes == null && !nuevaAqui
        if (nuevaAqui) {
            cambios["t"] = o.optString("name"); cambios["azid"] = id
            if (kind == LIBROS) cambios["clave"] = claveLibro(o.optString("name"), o.optString("creator"))
            cambios["an"] = Fechas.ms(o.optLong("start")).takeIf { it > 1_000_000_000_000L } ?: System.currentTimeMillis()
            if (actual?.borrado == true) cambios["b"] = 0L
        } else if (actual!!.entero("azid") <= 0L) cambios["azid"] = id
        if (!deLaOtraLista) {
            if (cambiado("name")) cambios["t"] = o.optString("name")
            if (cambiado("fav")) cambios["fav"] = o.optBoolean("fav")
            if (kind == JUEGOS) {
                if (cambiado("platform")) cambios["pl"] = o.optString("platform")
                if (cambiado("ttbMain")) cambios["dh"] = o.optInt("ttbMain").toLong()
                if (cambiado("ttbExtra")) cambios["de"] = o.optInt("ttbExtra").toLong()
                if (cambiado("ttbFull")) cambios["dc"] = o.optInt("ttbFull").toLong()
            } else {
                if (cambiado("creator")) cambios["a"] = o.optString("creator")
                if (cambiado("pages")) cambios["pg"] = o.optInt("pages").toLong()
            }
        }
        // El estado: el de la lista en la que está (y en curso, el suyo)
        val estado = estadoDe(o, valorada)
        val estadoAntes = if (antes == null) actual?.texto("estado") else estadoDe(antes, valorada)
        if (nuevaAqui || estado != estadoAntes) cambios["estado"] = estado
        // Cuándo lo terminaste (el día que pones aquí)
        if (valorada && (cambiado("end") || deLaOtraLista) && o.optLong("end") > 0) {
            val ms = Fechas.ms(o.optLong("end"))
            if (kind == JUEGOS) cambios["fin"] = ms
            else {
                val fin = actual?.lista("fin")?.mapNotNull { (it as? Number)?.toLong() }?.map { Fechas.ms(it) }.orEmpty()
                if (fin.none { dia(it) == o.optLong("end") }) cambios["fin"] = (if (fin.isEmpty() || deLaOtraLista) fin else fin.dropLast(1)) + ms
            }
        }
        // La valoración
        val notas = listOf("general", "gfx", "story", "music", "gameplay")
        if (valorada && (notas.any { cambiado(it) } || nuevaAqui || deLaOtraLista)) {
            val vs = notas.map { o.optInt(it) }
            if (!(nuevaAqui || deLaOtraLista) || vs.any { it != 0 } || actual?.get("nota") != null) { cambios["nota"] = vs[0].toLong(); cambios["crit"] = vs.drop(1).map { it.toLong() } }
        }
        if (cambiado("review") && (o.optString("review").isNotBlank() || antes != null)) cambios["resena"] = o.optString("review")
        // Lo de Azabache
        val az = azDeFicha(kind, o)
        val azAntes = antes?.let { azDeFicha(kind, it) } ?: actual?.let { azDe(it) }
        if (Store.canon(az) != Store.canon(azAntes ?: JSONObject())) cambios["az"] = az.toString()
        // Quita lo que ya es igual en la cuenta
        actual?.takeIf { !it.borrado }?.let { n -> cambios.keys.toList().forEach { k -> if (k != "estado" || !nuevaAqui) if (igual(n[k], cambios[k])) cambios.remove(k) } }
        if (cambios.isNotEmpty()) col.cambia(ref, cambios)
        // La portada, si la has cambiado aquí (en segundo plano: puede haber que bajarla)
        val cover = o.optString("cover")
        if (cover.isNotBlank() && cambiado("cover") && cover != PortadasFamilia.archivo(c, actual?.texto("portada").orEmpty()) && cover != actual?.texto("pu"))
            ponPortada(kind, ref, cover)
        // El fondo, si lo has cambiado aquí: a la cuenta, para que salga igual en Seshat y en Bastet (y en tus otros dispositivos)
        // (lo que llega de una copia o de antes no pisa el fondo que ya hay en la cuenta)
        if (cambiado("hero") && (antes != null || actual?.texto("fondo").isNullOrBlank())) {
            val hero = o.optString("hero")
            val fh = actual?.texto("fondo").orEmpty()
            if (hero.isBlank()) { if (antes != null && fh.isNotBlank() && fh != PortadasFamilia.SIN_FONDO) col.cambia(ref, mapOf("fondo" to PortadasFamilia.SIN_FONDO, "fu" to "")) }
            else if (hero != PortadasFamilia.archivo(c, fh) && hero != actual?.texto("fu")) ponFondo(kind, ref, hero)
        }
    }

    /** El fondo que acabas de poner, mientras sube: se ve ya (y no el de antes). */
    private val fondoPuesto = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun ponFondo(kind: Int, ref: String, hero: String) {
        fondoPuesto[ref] = hero
        portadas.execute {
            try {
                val bytes = when {
                    hero.startsWith("http") -> java.net.URL(hero).openConnection().apply { connectTimeout = 15_000; readTimeout = 20_000 }.getInputStream().use { it.readBytes() }
                    else -> java.io.File(hero.removePrefix("file://").removePrefix("file:")).readBytes()
                }
                PortadasFamilia.guardarFondo(bytes)?.let { h -> col(kind).cambia(ref, mapOf("fondo" to h, "fu" to if (hero.startsWith("http")) hero else "")) }
            } catch (e: Exception) {
            } finally { if (fondoPuesto[ref] == hero) fondoPuesto.remove(ref); cambio(kind) }
        }
    }

    private fun igual(a: Any?, b: Any?): Boolean = Store.canon(aOrg(a)) == Store.canon(aOrg(b))
    private fun aOrg(v: Any?): Any? = when (v) {
        is Map<*, *> -> JSONObject(Json.escribe(v)); is List<*> -> JSONArray(Json.escribe(v)); is Int -> v.toLong(); null -> JSONObject.NULL; else -> v
    }

    private fun buscaPorAzid(col: Coleccion, id: Long): String? = col.todos().values.firstOrNull { it.entero("azid") == id }?.ref

    private fun refNuevo(kind: Int, o: JSONObject): String =
        if (kind == LIBROS) Hermanas.kidDe(claveLibro(o.optString("name"), o.optString("creator")))
        else "j_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    /** La clave de un libro, como la pone Marfil (`libro:<título>:<autor>`, en minúsculas, sin tildes y solo letras y números). */
    fun claveLibro(titulo: String, autor: String): String = "libro:" + normLibro(titulo) + ":" + normLibro(autor)
    private fun normLibro(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Las portadas que pones aquí, a la cuenta de una en una (bajarlas de internet y reducirlas lleva su rato). */
    private val portadas by lazy { java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "azabache-portadas").apply { isDaemon = true } } }
    /** La portada que acabas de poner, mientras sube: se ve ya (y no la de antes). */
    private val portadaPuesta = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun ponPortada(kind: Int, ref: String, cover: String) {
        portadaPuesta[ref] = cover
        portadas.execute {
            try {
                val bytes = when {
                    cover.startsWith("http") -> java.net.URL(cover).openStream().use { it.readBytes() }
                    else -> java.io.File(cover.removePrefix("file://").removePrefix("file:")).readBytes()
                }
                PortadasFamilia.guardar(bytes)?.let { h -> col(kind).cambia(ref, if (cover.startsWith("http")) mapOf("portada" to h, "pu" to cover) else mapOf("portada" to h)) }
            } catch (e: Exception) {
            } finally { if (portadaPuesta[ref] == cover) portadaPuesta.remove(ref); cambio(kind) }
        }
    }

    /** Lo de una copia de seguridad o de antes: se pone en la cuenta (lo nuevo y lo cambiado), pero nunca borra nada de ella. */
    fun importa(c: Context, lista: String, items: List<JSONObject>) {
        val kind = kindDe(lista)
        synchronized(this) {
            val v = vista(c, kind)
            val ahora = (v.games.map { gameToJson(it) } + v.plays.map { playToJson(it) }).associateBy { it.optLong("id") }
            for (o in items) {
                val id = o.optLong("id")
                val a = ahora[id]
                if (a != null && Store.canon(a) == Store.canon(o)) continue
                escribe(c, kind, v, id, o, a?.takeIf { esValoradas(lista) == v.games.any { it.id == id } }, esValoradas(lista))
            }
        }
    }

    // ================================================================ de antes de la familia (una vez en cada dispositivo)

    /**
     * Las fichas que Azabache guardaba aparte (sus listas de antes): las que eran de un juego de Hiedra o un libro de Marfil ponen en él
     * su número, su nota, su reseña y lo suyo (si la cuenta aún no los tiene); las que solo estaban aquí pasan a ser juegos y libros de
     * la cuenta (los ven Hiedra y Marfil). Las listas de antes no se borran.
     */
    fun migra(c: Context, viejas: Map<String, List<JSONObject>>) {
        val p = c.getSharedPreferences("cosas_cuenta", Context.MODE_PRIVATE)
        if (p.getBoolean("migradas", false)) return
        for (kind in listOf(JUEGOS, LIBROS)) {
            val col = col(kind)
            val enl = Hermanas.enlaces(c, if (kind == JUEGOS) "hiedra" else "marfil")
            val refDeId = HashMap<Long, String>()
            enl.forEach { (k, id) -> refDeId[id] = if (kind == JUEGOS) k else Hermanas.kidDe(k) }
            val porNombre = col.vivos().filter { it.texto("t").isNotBlank() }.associateBy { normName(it.texto("t")) }
            val (lv, lp) = if (kind == JUEGOS) "games" to "plays" else "books" to "bplays"
            for ((lista, valorada) in listOf(lv to true, lp to false)) for (o in viejas[lista].orEmpty()) {
                val id = o.optLong("id")
                if (id == 0L || o.optString("name").isBlank()) continue
                val ref = refDeId[id]?.takeIf { col.nodo(it) != null }
                    ?: (if (kind == LIBROS) Hermanas.kidDe(claveLibro(o.optString("name"), o.optString("creator"))).takeIf { col.nodo(it) != null } else null)
                    ?: porNombre[normName(o.optString("name"))]?.ref
                    ?: buscaPorAzid(col, id)
                val n = ref?.let { col.nodo(it) }
                if (n != null && !n.borrado) {
                    // Ya está en la cuenta: lo de Azabache, si la cuenta aún no lo tiene (lo que manda la hermana no se toca)
                    val m = LinkedHashMap<String, Any?>()
                    if (n.entero("azid") <= 0L) m["azid"] = id
                    val vs = listOf("general", "gfx", "story", "music", "gameplay").map { o.optInt(it) }
                    if (valorada && n["nota"] == null && vs.any { it != 0 }) { m["nota"] = vs[0].toLong(); m["crit"] = vs.drop(1).map { it.toLong() } }
                    if (o.optString("review").isNotBlank() && n["resena"] == null) m["resena"] = o.optString("review")
                    val az = azDeFicha(kind, o)
                    if (az.length() > 0 && n["az"] == null) m["az"] = az.toString()
                    if (m.isNotEmpty()) col.pon(n.ref, m)
                    if (n.texto("portada").isBlank() && o.optString("cover").isNotBlank()) ponPortada(kind, n.ref, o.optString("cover"))
                } else {
                    // Solo estaba aquí: pasa a la cuenta, con la hora de su último cambio
                    val nuevo = LinkedHashMap<String, Any?>()
                    nuevo["t"] = o.optString("name"); nuevo["azid"] = id; nuevo["estado"] = estadoDe(o, valorada); nuevo["fav"] = o.optBoolean("fav")
                    if (kind == LIBROS) { nuevo["clave"] = claveLibro(o.optString("name"), o.optString("creator")); nuevo["a"] = o.optString("creator"); if (o.optInt("pages") > 0) nuevo["pg"] = o.optInt("pages").toLong() }
                    else { nuevo["pl"] = o.optString("platform"); listOf("ttbMain" to "dh", "ttbExtra" to "de", "ttbFull" to "dc").forEach { (a, b) -> if (o.optInt(a) > 0) nuevo[b] = o.optInt(a).toLong() } }
                    val start = Fechas.ms(o.optLong("start")); val end = Fechas.ms(o.optLong("end"))
                    nuevo["an"] = start.takeIf { it > 1_000_000_000_000L } ?: o.optLong("updated").takeIf { it > 1_000_000_000_000L } ?: System.currentTimeMillis()
                    if (valorada && end > 1_000_000_000_000L) nuevo["fin"] = if (kind == JUEGOS) end else listOf(end)
                    val vs = listOf("general", "gfx", "story", "music", "gameplay").map { o.optInt(it) }
                    if (valorada && vs.any { it != 0 }) { nuevo["nota"] = vs[0].toLong(); nuevo["crit"] = vs.drop(1).map { it.toLong() } }
                    if (o.optString("review").isNotBlank()) nuevo["resena"] = o.optString("review")
                    val az = azDeFicha(kind, o); if (az.length() > 0) nuevo["az"] = az.toString()
                    val r = ref ?: refNuevo(kind, o)
                    if (n?.borrado == true) nuevo["b"] = 0L
                    col.pon(r, nuevo, o.optLong("updated").takeIf { it > 1_000_000_000_000L })
                    if (o.optString("cover").isNotBlank()) ponPortada(kind, r, o.optString("cover"))
                }
            }
        }
        p.edit().putBoolean("migradas", true).apply()
        synchronized(this) { sucia[0] = true; sucia[1] = true }
    }

    /** (Para las pruebas.) */
    fun olvidaParaPruebas() { synchronized(this) { cache[0] = null; cache[1] = null; sucia[0] = true; sucia[1] = true; oyendoDe = null; guardadas.clear(); porBorrar.clear() } }
}
