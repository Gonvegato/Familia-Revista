package com.gonzalo.azabache.pc

import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Fechas
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.Nodo
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Portadas
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Las películas y las series son de Mostaza (desde Azabache 1.6.3 del móvil y 1.2.1 de aquí), como los juegos de Hiedra y los libros de
 * Marfil: no hay copia. Cada una es su nodo `titulos/<ref>` de la cuenta (`ref` = su código de IMDb, o `tmdb:<id>` / `tvmaze:<id>` /
 * `azabache:<id>`) y Azabache lo LEE tal cual para sus categorías «Películas» (las de TMDB: `tipo` = `movie`) y «Series» (las de TVmaze:
 * `tipo` = `series`). Lo que cambias aquí va a ese mismo nodo, campo a campo, y solo lo que has cambiado tú (Mostaza no se pisa):
 *
 *  - de la cosa (lo manda Mostaza): `t`, `estado`, `fav`, `fin` (cuándo la terminaste), `portada`/`pu`, `fondo`, `an`;
 *  - la valoración (la escribe Azabache, la lee Mostaza): `nota`, `crit`, `resena`;
 *  - lo propio de Azabache (apuntes, dónde la viste, director, capítulos…): `az`; `azid`, el número de la ficha en Azabache (el de antes:
 *    sagas, diario y maquetas lo siguen encontrando), y `azk`, su categoría;
 *  - lo visto: el tiempo de `tiempo/<ref>~<dispositivo>` (suma de tus dispositivos) y, en las series, los episodios de `progreso`.
 *
 * Empieza cuando Mostaza ha traído lo que había en Azabache (su migración deja `azabache/a_mostaza` en la cuenta); antes, todo sigue como
 * siempre. Los nodos de antes (`azabache/items_<categoría>~<id>`, `iplays_…`) no se tocan nunca.
 *
 * Es la lógica de `CosasMostaza.kt` del móvil, línea a línea: la ficha tiene la forma de las del móvil (la de `gameToJson` / `playToJson`,
 * con sus campos en su orden), así que lo que se escribe aquí (también el texto de `az`, letra a letra) es lo que escribiría el móvil.
 * Lo prueba `MostazaTest` con el código del móvil copiado (y el contrato que deja Mostaza, `herramientas/casos/hermanas/mostaza.json`).
 */
object CosasMostaza {
    /** La marca que deja Mostaza en la cuenta al traer lo de Azabache. */
    const val MARCA = "a_mostaza"
    /** (En las pruebas: activa sin mirar la marca de la cuenta.) */
    @Volatile var siempreParaPruebas = false

    fun col(): Coleccion = LaFamilia.coleccion("titulos")
    fun progreso(): Coleccion = LaFamilia.coleccion("progreso")

    /** ¿Ya son de Mostaza las películas y series? (lo dice la cuenta: Mostaza ha traído lo de Azabache) */
    fun activa(): Boolean {
        if (siempreParaPruebas) return true
        return runCatching { LaFamilia.familia.sesion.dentro && LaFamilia.coleccion("azabache").nodo(MARCA)?.let { !it.borrado } == true }.getOrDefault(false)
    }

    /** El tipo de Mostaza de una categoría de Azabache (null: no es de películas ni de series). */
    fun tipoDe(k: Kind?): String? = when { k == null -> null; k.isMovie -> "movie"; k.isTv -> "series"; else -> null }

    /** ¿Esta categoría se lee de Mostaza? */
    fun de(k: Kind?): Boolean = tipoDe(k) != null && activa()

    // ================================================================ leer

    class Vista(val fichas: List<Ficha>, val refPorId: Map<Long, String>, val nodoPorId: Map<Long, Nodo>)

    /** Un número fijo para un título de la cuenta (el mismo en todos tus dispositivos), si no tiene el suyo de Azabache. */
    fun idDeRef(ref: String): Long = Cuenta.idDeRef("titulos:$ref")

    /** Un número de TMDB o TVmaze (los hechos a mano en Azabache son más grandes). */
    private fun deLaWeb(id: Long) = id in 1 until 100_000_000_000L

    /** El número de la ficha: el de Azabache (`azid`); si no tiene, el de su web (TMDB o TVmaze, el que usaba Azabache); si no, uno fijo. */
    fun idDe(n: Nodo, tipo: String): Long =
        n.entero("azid").takeIf { it != 0L } ?: (if (tipo == "movie") n.entero("tmdb") else n.entero("tvmaze")).takeIf { deLaWeb(it) } ?: idDeRef(n.ref)

    private fun dia(ms: Long): Long = if (ms > 0) Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() else 0L

    /** Lo de Azabache de un título (`az`, un texto JSON de un objeto). */
    fun azDe(n: Nodo): Obj = objDe(n.texto("az"))

    /** «1×03 · Título», «2x10», «S01E03»: (temporada, episodio). */
    fun episodioDe(nombre: String): Pair<Int, Int>? {
        Regex("(\\d{1,2})\\s*[×xX]\\s*(\\d{1,3})").find(nombre)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        Regex("[sS](\\d{1,2})\\s*[eE](\\d{1,3})").find(nombre)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        return null
    }

    /**
     * Las fichas de una categoría a partir de los nodos de Mostaza (sin red: lo prueba MostazaTest con lo que escribe Mostaza de verdad y con
     * el código del móvil). [puesta]: la portada que se está poniendo ahora mismo (aún sin su imagen en la cuenta).
     */
    fun construye(titulos: List<Nodo>, tiempo: List<Nodo>, progreso: List<Nodo>, tipo: String, kindId: Int, puesta: (String) -> String? = { null }): Vista {
        val horas = tiempo.groupBy { it.texto("ref") }
        val vistos = progreso.groupBy { it.texto("ref") }
        val fichas = ArrayList<Ficha>()
        val refs = LinkedHashMap<Long, String>(); val nodos = HashMap<Long, Nodo>()
        for (n in titulos.filter { it.texto("t").isNotBlank() && it.texto("tipo").ifBlank { "movie" } == tipo }.sortedBy { it.ref }) {
            var id = idDe(n, tipo)
            if (id in refs) id = idDeRef(n.ref)
            if (id in refs) continue
            refs[id] = n.ref; nodos[id] = n
            val valorada = n.texto("estado") == "terminado"
            val ya = puesta(n.ref)
            // (como en el móvil: la que no se puede leer, con apuntes rotos, no sale; su número queda tomado)
            val o = comoEnElMovil(aFicha(n, id, horas[n.ref].orEmpty(), vistos[n.ref].orEmpty(), ya), valorada, estricta = true) ?: continue
            fichas += Ficha(kindId, o, valorada, n.ref, if (ya != null) "" else n.texto("portada"), ya ?: n.texto("pu"), sinNota = n["nota"] == null, mostaza = true)
        }
        return Vista(fichas, refs, nodos)
    }

    private fun aFicha(n: Nodo, id: Long, horas: List<Nodo>, vistos: List<Nodo>, puesta: String?): LinkedHashMap<String, Any?> {
        val o = LinkedHashMap(azDe(n))
        o["id"] = id; o["name"] = n.texto("t"); o["fav"] = n.si("fav"); o["review"] = n.texto("resena")
        // (en el móvil, el archivo de la portada de la cuenta o, si aún no está, `pu`; aquí la imagen de la cuenta va aparte, en la ficha)
        o["cover"] = puesta ?: n.texto("pu")
        val crit = n.lista("crit").map { (it as? Number)?.toInt() ?: 0 }
        val valorada = n["nota"] != null
        o["general"] = if (valorada) n.entero("nota").toInt().coerceIn(0, 100) else 0
        listOf("gfx", "story", "music", "gameplay").forEachIndexed { i, k -> o[k] = if (valorada) crit.getOrElse(i) { 0 } else 0 }
        val ms = horas.sumOf { it.entero("total") }
        o["minutes"] = maxOf((ms / 60_000L).toInt(), optInt(o, "minutes", 0))
        val fin = Fechas.ms(n.entero("fin")).let { if (it > 0) it else if (n.texto("estado") == "terminado") (n.m["estado"] ?: 0L).takeIf { x -> x > 946_684_800_000L } ?: 0L else 0L }
        o["end"] = dia(fin)
        if (optLong(o, "start", 0L) <= 0L) o["start"] = dia(Fechas.ms(n.entero("an")))
        o["updated"] = n.m.values.maxOrNull() ?: n.u
        o["status"] = when (n.texto("estado")) { "en_curso" -> 1; "abandonado" -> 2; else -> 0 }
        if (optString(o, "hero", "").isBlank()) o["hero"] = n.texto("fondo")
        if (!o.containsKey("entries")) o["entries"] = emptyList<Any?>()
        // Los episodios: lo que dice Mostaza (lo visto en `progreso`) manda sobre las marcas de antes
        val porEp = vistos.associate { (it.entero("temp").toInt() to it.entero("ep").toInt()) to it.si("visto") }
        (o["parts"] as? List<*>)?.let { a ->
            o["parts"] = a.map { p ->
                @Suppress("UNCHECKED_CAST")
                (p as? Map<String, Any?>)?.let { pm -> episodioDe(optString(pm, "n", ""))?.let { e -> porEp[e] }?.let { v -> LinkedHashMap(pm).also { it["d"] = v } } } ?: p
            }
        }
        return o
    }

    // ---------------------------------------------------------------- la forma de las fichas del móvil

    /*
     * Lo que hace el org.json del móvil al leer un valor (getInt, optString…): un número de un texto que lo es, el texto de un número…
     * Así una ficha lee aquí lo mismo que allí aunque `az` traiga algo raro.
     */
    private fun entero(v: Any?): Int? = when (v) { is Int -> v; is Number -> v.toInt(); is String -> v.toDoubleOrNull()?.toInt(); else -> null }
    private fun largo(v: Any?): Long? = when (v) { is Long -> v; is Number -> v.toLong(); is String -> v.toDoubleOrNull()?.toLong(); else -> null }
    private fun siNo(v: Any?): Boolean? = when (v) { is Boolean -> v; is String -> if (v.equals("true", true)) true else if (v.equals("false", true)) false else null; else -> null }
    private fun texto(o: Map<String, Any?>, k: String): String? = if (!o.containsKey(k)) null else when (val v = o[k]) {
        null -> "null"; is String -> v; is Map<*, *>, is List<*> -> Json.escribe(v); else -> v.toString()
    }
    private fun optInt(o: Map<String, Any?>, k: String, def: Int) = entero(o[k]) ?: def
    private fun optLong(o: Map<String, Any?>, k: String, def: Long) = largo(o[k]) ?: def
    private fun optString(o: Map<String, Any?>, k: String, def: String) = texto(o, k) ?: def
    private fun optBoolean(o: Map<String, Any?>, k: String, def: Boolean) = siNo(o[k]) ?: def

    private class NoSeLee : Exception()
    private fun getInt(o: Map<String, Any?>, k: String, estricta: Boolean) = entero(o[k]) ?: if (estricta) throw NoSeLee() else 0
    private fun getLong(o: Map<String, Any?>, k: String, estricta: Boolean) = largo(o[k]) ?: if (estricta) throw NoSeLee() else 0L
    private fun getString(o: Map<String, Any?>, k: String, estricta: Boolean) = texto(o, k) ?: if (estricta) throw NoSeLee() else ""

    /**
     * Una ficha con la forma exacta de las del móvil: la de `gameToJson(gameFromJson(o))` si está terminada y la de
     * `playToJson(playFromJson(o))` si no (los mismos campos, en el mismo orden y con sus valores por defecto). En las que no están
     * terminadas se quedan además la reseña y la estrella (aquí se ven y se cambian también en curso; no van en `az`). [estricta]: como el
     * móvil al leer (null si el móvil no podría leerla: un apunte roto); si no, lo que falta va con su valor por defecto (una ficha nueva de aquí).
     */
    fun comoEnElMovil(o: Obj, valorada: Boolean, estricta: Boolean = false): Obj? = try {
        val r = LinkedHashMap<String, Any?>()
        r["id"] = getLong(o, "id", estricta); r["name"] = getString(o, "name", estricta); r["cover"] = getString(o, "cover", estricta)
        if (valorada) {
            NOTAS.forEach { r[it] = getInt(o, it, estricta).toLong() }
            r["review"] = getString(o, "review", estricta)
            r["hero"] = optString(o, "hero", ""); r["start"] = optLong(o, "start", 0L); r["end"] = optLong(o, "end", 0L)
            r["creator"] = optString(o, "creator", ""); r["fav"] = optBoolean(o, "fav", false); r["platform"] = optString(o, "platform", "")
            r["pages"] = optInt(o, "pages", 0).toLong(); r["minutes"] = optInt(o, "minutes", 0).toLong(); r["updated"] = optLong(o, "updated", 0L)
            listOf("ttbMain", "ttbExtra", "ttbFull").forEach { r[it] = optInt(o, it, 0).toLong() }
            r["ttbSrc"] = optString(o, "ttbSrc", ""); r["logo"] = optString(o, "logo", "")
        } else {
            r["hero"] = getString(o, "hero", estricta); r["status"] = getInt(o, "status", estricta).toLong()
            r["entries"] = (o["entries"] as? List<*>).orEmpty().mapNotNull { e ->
                @Suppress("UNCHECKED_CAST")
                val m = e as? Map<String, Any?> ?: if (estricta) throw NoSeLee() else return@mapNotNull null
                try {
                    linkedMapOf<String, Any?>("time" to getLong(m, "time", true)).also { x ->
                        NOTAS.forEach { x[it] = getInt(m, it, true).toLong() }
                        x["note"] = getString(m, "note", true); x["chapter"] = optInt(m, "chapter", 0).toLong()
                    }
                } catch (_: NoSeLee) { if (estricta) throw NoSeLee() else null }
            }
            r["chapter"] = optInt(o, "chapter", 0).toLong(); r["every"] = optInt(o, "every", 3).toLong()
            r["start"] = optLong(o, "start", 0L); r["end"] = optLong(o, "end", 0L)
            r["creator"] = optString(o, "creator", ""); r["platform"] = optString(o, "platform", "")
            r["pages"] = optInt(o, "pages", 0).toLong(); r["minutes"] = optInt(o, "minutes", 0).toLong(); r["updated"] = optLong(o, "updated", 0L)
            listOf("ttbMain", "ttbExtra", "ttbFull").forEach { r[it] = optInt(o, it, 0).toLong() }
            r["ttbSrc"] = optString(o, "ttbSrc", "")
            @Suppress("UNCHECKED_CAST")
            r["parts"] = (o["parts"] as? List<*>).orEmpty().mapNotNull { (it as? Map<String, Any?>)?.let { p -> linkedMapOf<String, Any?>("n" to optString(p, "n", ""), "d" to optBoolean(p, "d", false)) } }
            r["logo"] = optString(o, "logo", "")
            @Suppress("UNCHECKED_CAST")
            val memos = (o["memos"] as? List<*>).orEmpty().mapNotNull { (it as? Map<String, Any?>)?.let { m ->
                val sh = optString(m, "sh", ""); val del = optInt(m, "del", 0) == 1 || optBoolean(m, "del", false); val u = optLong(m, "u", 0L)
                linkedMapOf<String, Any?>("t" to optLong(m, "t", 0L), "x" to optString(m, "x", "")).also { x -> if (sh.isNotEmpty()) x["sh"] = sh; if (del) x["del"] = 1L; if (u > 0L) x["u"] = u }
            } }
            if (memos.isNotEmpty()) r["memos"] = memos
            // (lo que aquí se ve también en curso)
            r["review"] = optString(o, "review", ""); r["fav"] = optBoolean(o, "fav", false)
        }
        r
    } catch (_: NoSeLee) { null }

    /** El texto JSON como lo escribe el org.json del móvil (las «/» con su barra, como «https:\/\/…»): `az` sale letra a letra igual. */
    fun jsonComoElMovil(v: Any?): String = StringBuilder().also { comoElMovil(v, it) }.toString()

    private fun comoElMovil(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is Map<*, *> -> { sb.append('{'); var p = true; for ((k, x) in v) { if (!p) sb.append(','); p = false; cadena(k.toString(), sb); sb.append(':'); comoElMovil(x, sb) }; sb.append('}') }
            is List<*> -> { sb.append('['); var p = true; for (x in v) { if (!p) sb.append(','); p = false; comoElMovil(x, sb) }; sb.append(']') }
            is String -> cadena(v, sb)
            is Boolean -> sb.append(v.toString())
            is Double, is Float -> { val d = (v as Number).toDouble(); val l = d.toLong(); sb.append(if (d == l.toDouble()) l.toString() else d.toString()) }
            is Number -> sb.append(v.toLong().toString())
            else -> cadena(v.toString(), sb)
        }
    }

    private fun cadena(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) when (c) {
            '"', '\\', '/' -> sb.append('\\').append(c)
            '\t' -> sb.append("\\t"); '\b' -> sb.append("\\b"); '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r"); '\u000C' -> sb.append("\\f")
            else -> if (c.code <= 0x1F) sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
        sb.append('"')
    }

    // ================================================================ escribir

    private val NO_AZ = setOf("id", "updated", "sv", "status", "name", "cover", "general", "gfx", "story", "music", "gameplay", "review", "fav", "end", "minutes")

    private fun estadoDe(o: Obj, valorada: Boolean): String =
        if (valorada) "terminado" else when (optInt(o, "status", 0)) { 1 -> "en_curso"; 2 -> "abandonado"; else -> "pendiente" }

    private fun azDeFicha(o: Obj): Obj = o.filterKeys { it !in NO_AZ }

    private fun refNuevo(k: Kind, id: Long): String = when {
        deLaWeb(id) && k.isMovie -> "tmdb:$id"
        deLaWeb(id) -> "tvmaze:$id"
        else -> "azabache:$id"
    }

    /** Lo que va a la cuenta al guardar una ficha: el título, sus campos cambiados y, si cambia, la portada que hay que poner. */
    class Escritura(val ref: String, val cambios: Map<String, Any?>, val portada: String?)

    /**
     * Lo que escribe el móvil (`CosasMostaza.escribe`) al guardar [o] con la forma de sus fichas ([comoEnElMovil]): [antes] es cómo estaba
     * en tu pantalla en esa misma lista (null si es nueva o viene de la otra lista: de en curso a vistas o al revés), [todos] los títulos de
     * la cuenta (también los borrados). Puro: no escribe nada.
     */
    fun escritura(k: Kind, v: Vista, todos: Map<String, Nodo>, id: Long, o: Obj, antes: Obj?, valorada: Boolean, ahora: Long = System.currentTimeMillis()): Escritura? {
        val tipo = tipoDe(k) ?: return null
        val ref = v.refPorId[id] ?: todos.values.firstOrNull { it.entero("azid") == id && it.entero("azk").toInt() == k.id }?.ref ?: refNuevo(k, id)
        val actual = v.nodoPorId[id] ?: todos[ref]
        val cambios = LinkedHashMap<String, Any?>()
        fun cambiado(x: String) = antes == null || canon(antes[x]) != canon(o[x])
        val nuevaAqui = actual == null || actual.borrado
        val deLaOtraLista = antes == null && !nuevaAqui
        if (nuevaAqui) {
            cambios["t"] = optString(o, "name", ""); cambios["tipo"] = tipo; cambios["azid"] = id; cambios["azk"] = k.id.toLong()
            if (actual == null) {
                cambios["id"] = ref
                if (deLaWeb(id)) cambios[if (tipo == "movie") "tmdb" else "tvmaze"] = id
            }
            cambios["an"] = Fechas.ms(optLong(o, "start", 0L)).takeIf { it > 1_000_000_000_000L } ?: ahora
            if (actual?.borrado == true) cambios["b"] = 0L
        } else {
            if (actual!!.entero("azid") == 0L) cambios["azid"] = id
            if (actual.entero("azk") == 0L) cambios["azk"] = k.id.toLong()
        }
        if (!deLaOtraLista) {
            if (cambiado("name")) cambios["t"] = optString(o, "name", "")
            if (cambiado("fav")) cambios["fav"] = optBoolean(o, "fav", false)
        }
        val estado = estadoDe(o, valorada)
        val estadoAntes = if (antes == null) actual?.texto("estado") else estadoDe(antes, valorada)
        if (nuevaAqui || estado != estadoAntes) cambios["estado"] = estado
        if (valorada && (cambiado("end") || deLaOtraLista) && optLong(o, "end", 0L) > 0) cambios["fin"] = Fechas.ms(optLong(o, "end", 0L))
        if (valorada && (NOTAS.any { cambiado(it) } || nuevaAqui || deLaOtraLista)) {
            val vs = NOTAS.map { optInt(o, it, 0) }
            if (!(nuevaAqui || deLaOtraLista) || vs.any { it != 0 } || actual?.get("nota") != null) { cambios["nota"] = vs[0].toLong(); cambios["crit"] = vs.drop(1).map { it.toLong() } }
        }
        if (cambiado("review") && (optString(o, "review", "").isNotBlank() || antes != null)) cambios["resena"] = optString(o, "review", "")
        val az = azDeFicha(o)
        val azAntes = antes?.let { azDeFicha(it) } ?: actual?.let { azDe(it) }
        if (canon(az) != canon(azAntes ?: emptyMap<String, Any?>())) cambios["az"] = jsonComoElMovil(az)
        actual?.takeIf { !it.borrado }?.let { n -> cambios.keys.toList().forEach { x -> if (x != "estado" || !nuevaAqui) if (canon(n[x]) == canon(cambios[x])) cambios.remove(x) } }
        val cover = optString(o, "cover", "")
        val portada = cover.takeIf { it.isNotBlank() && cambiado("cover") && it != actual?.texto("pu") }
        return Escritura(ref, cambios, portada)
    }

    /**
     * Guarda una ficha: [antes] es cómo estaba en tu pantalla (null si es nueva), [nueva] cómo queda y [valorada] si queda vista. Va a la
     * cuenta solo lo que has cambiado tú (lo que mientras haya cambiado Mostaza no se pisa); pasar de en curso a vistas cambia el estado.
     */
    fun guarda(k: Kind, antes: Ficha?, nueva: Obj, valorada: Boolean) {
        val o = comoEnElMovil(nueva, valorada) ?: return
        val a = if (antes != null && antes.valorada == valorada) comoEnElMovil(antes.o, valorada) else null
        if (a != null && canon(a) == canon(o)) return
        val c = col()
        val e = escritura(k, Cuenta.vistaMostaza(k), c.todos(), o["id"].comoLong(), o, a, valorada) ?: return
        if (e.cambios.isNotEmpty()) c.cambia(e.ref, e.cambios)
        e.portada?.let { ponPortada(e.ref, it) }
    }

    /** Quitarla aquí la quita en Mostaza (queda borrada, con su hora, como al quitarla allí: no vuelve sola aunque la veas). */
    fun borra(f: Ficha) { f.ref?.let { col().borra(it) } }

    // ---------------------------------------------------------------- la portada (la imagen a la cuenta, por su hash, como en el móvil)

    private val portadas by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "azabache-portadas-mostaza").apply { isDaemon = true } } }
    private val portadaPuesta = ConcurrentHashMap<String, String>()

    /** La portada que se está poniendo a un título (mientras se baja y se sube). */
    fun puesta(ref: String): String? = portadaPuesta[ref]

    private fun ponPortada(ref: String, cover: String) {
        portadaPuesta[ref] = cover
        Cuenta.invalida()
        portadas.execute {
            try {
                val bytes = if (cover.startsWith("http")) Buscar.bytes(cover) else File(cover.removePrefix("file://").removePrefix("file:")).readBytes()
                Portadas.guardar(bytes)?.let { h -> col().cambia(ref, if (cover.startsWith("http")) mapOf("portada" to h, "pu" to cover) else mapOf("portada" to h)) }
            } catch (_: Exception) {
            } finally { if (portadaPuesta[ref] == cover) portadaPuesta.remove(ref); Cuenta.invalida() }
        }
    }

    // ---------------------------------------------------------------- lo visto (para la casilla «Progreso»)

    /** Un vídeo visto o empezado en Mostaza (`progreso/<vídeo>`): la película entera o un episodio. */
    class Visto(val temporada: Int, val episodio: Int, val visto: Boolean, val posMs: Long, val durMs: Long, val cuando: Long)

    /** Lo de Mostaza de un título: la película o sus episodios, en orden. */
    fun vistosDe(ref: String): List<Visto> = runCatching {
        progreso().vivos().filter { it.texto("ref") == ref }.map { n ->
            Visto(n.entero("temp").toInt(), n.entero("ep").toInt(), n.si("visto"), n.entero("pos"), n.entero("dur"), maxOf(n.entero("ul"), n.entero("cuando")))
        }.sortedWith(compareBy({ it.temporada }, { it.episodio }))
    }.getOrDefault(emptyList())

    /** (Para las pruebas.) */
    fun olvidaParaPruebas() { portadaPuesta.clear(); siempreParaPruebas = false }
}
