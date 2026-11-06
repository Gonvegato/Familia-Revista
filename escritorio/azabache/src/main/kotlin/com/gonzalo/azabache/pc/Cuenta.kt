package com.gonzalo.azabache.pc

import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Fechas
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.Nodo
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Portadas
import java.time.Instant
import java.time.ZoneId

/**
 * Todo lo de Azabache, de la cuenta (como el Azabache del móvil desde la 1.4: sin copia).
 *
 *  - Los juegos y los libros SON los de la cuenta (`juegos/<id>`, `libros/<kid>`, los mismos nodos que Hiedra y Marfil): lo que se ve
 *    sale de ahí, y lo que cambias va a ese nodo, campo a campo y solo lo que has cambiado tú (así nunca se pisa lo que Hiedra o Marfil
 *    hayan cambiado mientras). La nota va en `nota` (sin el campo = sin valorar), los criterios en `crit`, la reseña en `resena`, lo
 *    demás de Azabache en `az` y el número de la ficha en `azid`.
 *  - Las películas y las series SON las de Mostaza (`titulos/<ref>`) en cuanto Mostaza ha traído lo de Azabache (la marca
 *    `azabache/a_mostaza`): ver [CosasMostaza]. Antes, como las demás categorías tuyas.
 *  - Lo propio de Azabache (tus categorías y sus fichas, el diario, las sagas…) en `azabache/<lista>~<id>` con `o` = el elemento.
 *
 * Es la misma lógica que `Cosas.kt`, `CosasMostaza.kt` y `Propias.kt` de Android (ver nube/ESQUEMA.md), con las pruebas cruzadas en
 * `CuentaTest` y `MostazaTest`.
 */
object Cuenta {
    const val JUEGOS = 0
    const val LIBROS = 1

    private fun col(kind: Int): Coleccion = LaFamilia.coleccion(if (kind == JUEGOS) "juegos" else "libros")
    private fun az(): Coleccion = LaFamilia.coleccion("azabache")
    private fun tiempo(): Coleccion = LaFamilia.coleccion("tiempo")

    // ================================================================ las vistas (se rehacen solas al cambiar la cuenta)

    @Volatile private var oyendoDe: Any? = null
    @Volatile private var version = 0L
    private val cache = HashMap<String, Pair<Long, Any>>()

    private fun prepara() {
        val fam = LaFamilia.familia
        if (oyendoDe === fam) return
        synchronized(this) {
            if (oyendoDe === fam) return
            oyendoDe = fam
            listOf(col(JUEGOS), col(LIBROS), tiempo(), az(), CosasMostaza.col(), CosasMostaza.progreso()).forEach { c -> c.alCambiar { synchronized(this) { version++ } } }
            version++
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cacheado(clave: String, hazlo: () -> T): T {
        prepara()
        synchronized(this) {
            cache[clave]?.let { (v, x) -> if (v == version) return x as T }
            val x = hazlo()
            cache[clave] = version to x
            return x
        }
    }

    /** Lo ya leído se vuelve a leer (lo que no es de la cuenta ha cambiado: una portada que se está poniendo). */
    fun invalida() { synchronized(this) { version++ } }

    /** Las categorías: Videojuegos, Libros y las tuyas. */
    fun kinds(): List<Kind> = listOf(GAMES, BOOKS) + propias()
    fun propias(): List<Kind> = cacheado("kinds") { lista("kinds").mapNotNull { kindDeMapa(it) }.distinctBy { it.id } }
    fun kind(id: Int): Kind = kinds().firstOrNull { it.id == id } ?: GAMES

    /** Las fichas de una categoría: terminadas (valoradas) y las demás. */
    fun fichas(kind: Int): List<Ficha> = cacheado("fichas_$kind") {
        if (kind == JUEGOS || kind == LIBROS) construye(kind)
        else mostazaDe(kind)?.let { vistaMostaza(it).fichas }
            ?: (lista("items_$kind").map { Ficha(kind, it, true) } + lista("iplays_$kind").map { Ficha(kind, it, false) })
    }

    /** La categoría de películas o series cuyas fichas son ya las de Mostaza (null: no lo es, o Mostaza aún no ha traído lo de Azabache). */
    fun mostazaDe(kind: Int): Kind? = if (kind == JUEGOS || kind == LIBROS) null else propias().firstOrNull { it.id == kind }?.takeIf { CosasMostaza.de(it) }

    /** Las películas o las series de Mostaza de una categoría, como fichas de Azabache. */
    fun vistaMostaza(k: Kind): CosasMostaza.Vista = cacheado("mostaza_${k.id}") {
        CosasMostaza.construye(CosasMostaza.col().vivos(), tiempo().vivos(), CosasMostaza.progreso().vivos(), CosasMostaza.tipoDe(k) ?: "movie", k.id) { CosasMostaza.puesta(it) }
    }

    fun ficha(kind: Int, id: Long): Ficha? = fichas(kind).firstOrNull { it.id == id }

    fun notas(): List<Nota> = cacheado("diario") { lista("diario").mapNotNull { notaDeMapa(it) }.sortedWith(compareByDescending<Nota> { it.dia }.thenByDescending { it.id }) }
    fun sagas(): List<Saga> = cacheado("sagas") { lista("sagas").mapNotNull { sagaDeMapa(it) } }

    private fun dia(ms: Long): Long = if (ms > 0) Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() else 0L

    private fun construye(kind: Int, juegosOLibros: Coleccion = col(kind), tiempos: Coleccion = tiempo()): List<Ficha> {
        val horas = tiempos.vivos().groupBy { it.texto("ref") }
        val out = ArrayList<Ficha>()
        val vistos = HashSet<Long>()
        for (n in juegosOLibros.vivos().filter { it.texto("t").isNotBlank() }.sortedBy { it.ref }) {
            var id = idDe(n)
            if (id in vistos) id = idDeRef(n.ref)
            if (id in vistos) continue
            vistos += id
            out += aFicha(kind, n, id, horas[n.ref].orEmpty())
        }
        return out
    }

    /** El número de la ficha: el de la cuenta (`azid`) o, si aún no lo tiene, uno fijo de la cosa (el mismo que en el móvil). */
    private fun idDe(n: Nodo): Long = n.entero("azid").takeIf { it > 0 } ?: idDeRef(n.ref)

    fun idDeRef(ref: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (ch in "familia:$ref") { h = h xor ch.code.toLong(); h *= 0x100000001b3L }
        return (h and Long.MAX_VALUE) % 9_000_000_000_000L + 100_000_000_000L
    }

    private fun finDe(kind: Int, n: Nodo): Long {
        val ms = if (kind == JUEGOS) Fechas.ms(n.entero("fin")) else n.lista("fin").mapNotNull { (it as? Number)?.toLong() }.map { Fechas.ms(it) }.maxOrNull() ?: 0L
        return if (ms > 0) ms else if (n.texto("estado") == "terminado") (n.m["estado"] ?: 0L).takeIf { it > 946_684_800_000L } ?: 0L else 0L
    }

    private fun aFicha(kind: Int, n: Nodo, id: Long, horas: List<Nodo>): Ficha {
        val o = LinkedHashMap(objDe(n.texto("az")))
        o["id"] = id; o["name"] = n.texto("t"); o["fav"] = n.si("fav"); o["review"] = n.texto("resena")
        o["cover"] = n.texto("pu")
        val crit = n.lista("crit").map { it.comoLong() }
        val conNota = n["nota"] != null
        o["general"] = if (conNota) n.entero("nota").coerceIn(0, 100) else 0L
        listOf("gfx", "story", "music", "gameplay").forEachIndexed { i, k -> o[k] = if (conNota) crit.getOrElse(i) { 0L } else 0L }
        if (kind == JUEGOS) {
            o["platform"] = n.texto("pl"); o["ttbMain"] = n.entero("dh"); o["ttbExtra"] = n.entero("de"); o["ttbFull"] = n.entero("dc")
        } else {
            o["creator"] = n.texto("a"); o["pages"] = n.entero("pg")
        }
        val ms = horas.sumOf { it.entero("total") }
        o["minutes"] = maxOf(ms / 60_000L, o["minutes"].comoLong())
        o["end"] = dia(finDe(kind, n))
        if (o["start"].comoLong() <= 0L) o["start"] = dia(Fechas.ms(n.entero("an")))
        o["updated"] = n.m.values.maxOrNull() ?: n.u
        o["status"] = when (n.texto("estado")) { "en_curso" -> 1L; "abandonado" -> 2L; else -> 0L }
        if (!o.containsKey("hero")) o["hero"] = ""
        if (!o.containsKey("entries")) o["entries"] = emptyList<Any?>()
        return Ficha(kind, o, n.texto("estado") == "terminado", n.ref, n.texto("portada"), n.texto("pu"), sinNota = !conNota)
    }

    /** La cosa de la cuenta de una ficha (para «tiempo», enlaces con Hiedra, Marfil y Mostaza…). */
    fun nodo(kind: Int, f: Ficha): Nodo? = f.ref?.let { (if (f.mostaza) CosasMostaza.col() else col(kind)).nodo(it) }

    // ================================================================ escribir

    /**
     * Guarda una ficha: [antes] es cómo estaba en tu pantalla (null si es nueva), [nueva] cómo queda y [valorada] si queda terminada.
     * En los juegos y libros solo va a la cuenta lo que ha cambiado de verdad; en tus categorías, el elemento entero si ha cambiado.
     */
    fun guarda(kind: Int, antes: Ficha?, nueva: Obj, valorada: Boolean) {
        synchronized(this) {
            if (kind == JUEGOS || kind == LIBROS) {
                val mismaLista = antes != null && antes.valorada == valorada
                escribe(kind, antes?.ref, nueva, if (mismaLista) antes!!.o else null, valorada)
            } else if (mostazaDe(kind) != null) {
                // Las películas y las series: a su título de Mostaza (nunca a lo de antes)
                CosasMostaza.guarda(mostazaDe(kind)!!, antes, nueva, valorada)
            } else {
                val (esta, otra) = if (valorada) "items_$kind" to "iplays_$kind" else "iplays_$kind" to "items_$kind"
                val id = nueva["id"].comoLong()
                escribeLista(esta, lista(esta).filter { it["id"].comoLong() != id } + nueva)
                if (antes != null && antes.valorada != valorada) escribeLista(otra, lista(otra).filter { it["id"].comoLong() != id })
            }
        }
    }

    /** Quita una ficha (en los juegos, libros, películas y series, la cosa entera de la cuenta: deja de estar en Hiedra, Marfil o Mostaza también). */
    fun borra(f: Ficha) {
        synchronized(this) {
            if (f.kind == JUEGOS || f.kind == LIBROS) { f.ref?.let { col(f.kind).borra(it) }; return }
            if (f.mostaza) { CosasMostaza.borra(f); return }
            val l = if (f.valorada) "items_${f.kind}" else "iplays_${f.kind}"
            escribeLista(l, lista(l).filter { it["id"].comoLong() != f.id })
        }
    }

    private fun noAz(kind: Int) = setOf("id", "updated", "sv", "status", "name", "cover", "general", "gfx", "story", "music", "gameplay", "review", "fav", "end") +
        if (kind == JUEGOS) setOf("platform", "ttbMain", "ttbExtra", "ttbFull") else setOf("creator", "pages")

    private fun estadoDe(o: Obj, valorada: Boolean): String =
        if (valorada) "terminado" else when (o["status"].comoLong().toInt()) { 1 -> "en_curso"; 2 -> "abandonado"; else -> "pendiente" }

    private fun azDeFicha(kind: Int, o: Obj): Obj { val fuera = noAz(kind); return o.filterKeys { it !in fuera } }

    private fun buscaPorAzid(c: Coleccion, id: Long): String? = c.todos().values.firstOrNull { it.entero("azid") == id }?.ref

    private fun refNuevo(kind: Int, o: Obj): String =
        if (kind == LIBROS) kidDe(claveLibro(o["name"].comoTexto(), o["creator"].comoTexto()))
        else "j_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    /** La clave de un libro, como la pone Marfil (`libro:<título>:<autor>`, en minúsculas, sin tildes y solo letras y números). */
    fun claveLibro(titulo: String, autor: String): String = "libro:" + normLibro(titulo) + ":" + normLibro(autor)
    private fun normLibro(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    /** El nodo de un libro: los 20 primeros hexadecimales del SHA-1 de su clave. */
    fun kidDe(clave: String): String = java.security.MessageDigest.getInstance("SHA-1").digest(clave.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    private fun escribe(kind: Int, refAntes: String?, o: Obj, antes: Obj?, valorada: Boolean) {
        val c = col(kind)
        val id = o["id"].comoLong()
        val ref = refAntes ?: buscaPorAzid(c, id) ?: refNuevo(kind, o)
        val actual = c.todos()[ref]
        val cambios = LinkedHashMap<String, Any?>()
        fun cambiado(k: String) = antes == null || canon(antes[k]) != canon(o[k])
        fun txt(k: String) = o[k].comoTexto()
        fun num(k: String) = o[k].comoLong()
        val nuevaAqui = actual == null || actual.borrado
        val deLaOtraLista = antes == null && !nuevaAqui
        if (nuevaAqui) {
            cambios["t"] = txt("name"); cambios["azid"] = id
            if (kind == LIBROS) cambios["clave"] = claveLibro(txt("name"), txt("creator"))
            cambios["an"] = Fechas.ms(num("start")).takeIf { it > 1_000_000_000_000L } ?: System.currentTimeMillis()
            if (actual?.borrado == true) cambios["b"] = 0L
        } else if (actual!!.entero("azid") <= 0L) cambios["azid"] = id
        if (!deLaOtraLista) {
            if (cambiado("name")) cambios["t"] = txt("name")
            if (cambiado("fav")) cambios["fav"] = o["fav"] == true
            if (kind == JUEGOS) {
                if (cambiado("platform")) cambios["pl"] = txt("platform")
                if (cambiado("ttbMain")) cambios["dh"] = num("ttbMain")
                if (cambiado("ttbExtra")) cambios["de"] = num("ttbExtra")
                if (cambiado("ttbFull")) cambios["dc"] = num("ttbFull")
            } else {
                if (cambiado("creator")) cambios["a"] = txt("creator")
                if (cambiado("pages")) cambios["pg"] = num("pages")
            }
        }
        val estado = estadoDe(o, valorada)
        val estadoAntes = if (antes == null) actual?.texto("estado") else estadoDe(antes, valorada)
        if (nuevaAqui || estado != estadoAntes) cambios["estado"] = estado
        if (valorada && (cambiado("end") || deLaOtraLista) && num("end") > 0) {
            val ms = Fechas.ms(num("end"))
            if (kind == JUEGOS) cambios["fin"] = ms
            else {
                val fin = actual?.lista("fin")?.mapNotNull { (it as? Number)?.toLong() }?.map { Fechas.ms(it) }.orEmpty()
                if (fin.none { dia(it) == num("end") }) cambios["fin"] = (if (fin.isEmpty() || deLaOtraLista) fin else fin.dropLast(1)) + ms
            }
        }
        if (valorada && (NOTAS.any { cambiado(it) } || nuevaAqui || deLaOtraLista)) {
            val vs = NOTAS.map { num(it) }
            if (!(nuevaAqui || deLaOtraLista) || vs.any { it != 0L } || actual?.get("nota") != null) { cambios["nota"] = vs[0]; cambios["crit"] = vs.drop(1) }
        }
        if (cambiado("review") && (txt("review").isNotBlank() || antes != null)) cambios["resena"] = txt("review")
        val azN = azDeFicha(kind, o)
        val azAntes = antes?.let { azDeFicha(kind, it) } ?: actual?.let { objDe(it.texto("az")) }
        if (canon(azN) != canon(azAntes ?: emptyMap<String, Any?>())) cambios["az"] = Json.escribe(azN)
        actual?.takeIf { !it.borrado }?.let { n -> cambios.keys.toList().forEach { k -> if (k != "estado" || !nuevaAqui) if (canon(n[k]) == canon(cambios[k])) cambios.remove(k) } }
        if (cambios.isNotEmpty()) c.cambia(ref, cambios)
    }

    /**
     * Pone la portada de un juego, un libro, una película o una serie: la imagen va a la cuenta (reducida, por su hash) y la ven Hiedra,
     * Marfil, Mostaza y tus otros dispositivos. [url]: de dónde salió, si fue de internet. Bloquea (puede haber que bajarla). Devuelve el
     * hash, o null si no es una imagen.
     */
    fun ponPortada(f: Ficha, bytes: ByteArray, url: String = ""): String? {
        val ref = f.ref ?: return null
        val h = Portadas.guardar(bytes) ?: return null
        (if (f.mostaza) CosasMostaza.col() else col(f.kind)).cambia(ref, if (url.startsWith("http")) mapOf("portada" to h, "pu" to url) else mapOf("portada" to h))
        return h
    }

    /** En tus categorías la portada es una dirección de internet (la que ve también el móvil). */
    fun ponPortadaWeb(f: Ficha, url: String) { guarda(f.kind, f, f.con(mapOf("cover" to url)), f.valorada) }

    // ================================================================ lo propio de Azabache (azabache/<lista>~<id>)

    /** Una lista de Azabache tal como está en la cuenta (elemento a elemento, por su número; o, de antes, entera en `v`). */
    fun lista(key: String): List<Obj> = lista(key, az())

    private fun lista(key: String, az: Coleccion): List<Obj> {
        val pre = "$key~"
        val els = sortedMapOf<Long, Obj>()
        for (n in az.vivos()) {
            if (!n.ref.startsWith(pre)) continue
            val id = n.ref.substring(pre.length).toLongOrNull() ?: continue
            objDe(n.texto("o")).takeIf { it.isNotEmpty() }?.let { els[id] = it }
        }
        if (els.isNotEmpty()) return els.values.toList()
        val v = az.nodo(key)?.takeIf { !it.borrado }?.texto("v")?.takeIf { it.startsWith("[") } ?: return emptyList()
        return runCatching { Json.lee(v).comoLista().firstOrNull().comoLista().map { it.comoMapa() }.filter { it.isNotEmpty() } }.getOrDefault(emptyList())
    }

    /** Escribe una lista de Azabache: solo los elementos que han cambiado (lo que ya no está se borra), como en el móvil. */
    fun escribeLista(key: String, nueva: List<Obj>) {
        val c = az()
        val pre = "$key~"
        val antes = HashMap<Long, Obj>()
        for (n in c.vivos()) if (n.ref.startsWith(pre)) n.ref.substring(pre.length).toLongOrNull()?.let { antes[it] = objDe(n.texto("o")) }
        if (nueva.isEmpty()) {
            antes.keys.forEach { c.borra("$key~$it") }
            c.nodo(key)?.takeIf { !it.borrado }?.let { c.cambia(key, mapOf("v" to "[[]]")) }
            return
        }
        val ids = HashSet<Long>()
        for (e in nueva) {
            val id = e["id"].comoLong(); ids += id
            val a = antes[id]
            if (a == null || canon(a) != canon(e)) c.cambia("$key~$id", mapOf("o" to Json.escribe(e), "b" to 0L))
        }
        (antes.keys - ids).forEach { c.borra("$key~$it") }
    }

    fun guardaNota(n: Nota) { synchronized(this) { escribeLista("diario", lista("diario").filter { it["id"].comoLong() != n.id } + notaAMapa(n.copy(updated = System.currentTimeMillis()))) } }
    fun borraNota(n: Nota) { synchronized(this) { escribeLista("diario", lista("diario").filter { it["id"].comoLong() != n.id }) } }
    fun guardaSaga(s: Saga) { synchronized(this) { escribeLista("sagas", lista("sagas").filter { it["id"].comoLong() != s.id } + sagaAMapa(s.copy(updated = System.currentTimeMillis()))) } }
    fun borraSaga(s: Saga) { synchronized(this) { escribeLista("sagas", lista("sagas").filter { it["id"].comoLong() != s.id }) } }
    fun guardaKind(k: Kind) { synchronized(this) { escribeLista("kinds", lista("kinds").filter { it["id"].comoLong().toInt() != k.id } + kindAMapa(k)) } }
    fun siguienteKind(): Int = (kinds().maxOf { it.id } + 1).coerceAtLeast(2)

    /**
     * Una maqueta a la cuenta (`maquetas~<id>`, `o` = {id, k, j, updated}), como `Store.saveMaqueta` del móvil: si ya estaba y cambia,
     * con su versión de sincronización (`sv`) como la pone el móvil al reescribirla.
     */
    fun guardaMaqueta(clave: String, j: String) {
        synchronized(this) {
            val id = Maquetas.idDe(clave); val ahora = System.currentTimeMillis()
            val l = lista("maquetas")
            val antes = l.firstOrNull { it["id"].comoLong() == id }
            if (antes != null && antes["j"].comoTexto() == j && antes["k"].comoTexto() == clave) return
            val e = linkedMapOf<String, Any?>("id" to id, "k" to clave, "j" to j, "updated" to ahora)
            if (antes != null) e["sv"] = maxOf(ahora, antes["sv"].comoLong() + 1)
            escribeLista("maquetas", l.filter { it["id"].comoLong() != id } + e)
        }
    }

    /** Quita maquetas (se restablece el diseño de siempre): como en el móvil, el elemento lleva su marca de borrado. */
    fun borraMaquetas(claves: List<String>) {
        synchronized(this) {
            val ids = claves.filter { Maquetas.claveValida(it) }.map { Maquetas.idDe(it) }.toSet()
            val l = lista("maquetas")
            if (l.none { it["id"].comoLong() in ids }) return
            escribeLista("maquetas", l.filter { it["id"].comoLong() !in ids })
        }
    }

    // ================================================================ las recomendaciones a tu pareja (`azabache/recs~<id>`)

    /** Lo que le has recomendado a tu pareja (lo tuyo, de la cuenta), lo último primero. */
    fun misRecs(): List<Rec> = cacheado("recs") { lista("recs").mapNotNull { recDeMapa(it) }.sortedByDescending { it.hora } }

    /** Lo que te recomienda tu pareja (de su cuenta), lo último primero. */
    fun recsDe(uid: String): List<Rec> = cacheado("recs_$uid") { lista("recs", ajena(uid, "azabache")).mapNotNull { recDeMapa(it) }.sortedByDescending { it.hora } }

    /**
     * Le recomiendas algo a tu pareja: un elemento nuevo en tu lista `recs` (el mismo que crea el móvil con «Recomendar»: un número
     * nuevo cada vez, y lo de antes no se toca). Solo juegos y libros, como en el móvil.
     */
    fun recomienda(f: Ficha, nota: String): Rec {
        val ahora = System.currentTimeMillis()
        val id = synchronized(this) { maxOf(ahora, (lista("recs").maxOfOrNull { it["id"].comoLong() } ?: 0L) + 1) }
        val r = Rec(id, f.kind, f.id, f.nombre, f.portadaWeb, f.creador, nota.trim(), ahora)
        az().cambia("recs~${r.id}", mapOf("o" to Json.escribe(recAMapa(r)), "b" to 0L))
        return r
    }

    /** Las horas jugadas o leídas de una cosa, por día (`tiempo/<ref>~<fuente>`: se suman las fuentes). */
    fun porDia(ref: String): Map<String, Long> {
        val out = HashMap<String, Long>()
        tiempo().vivos().filter { it.texto("ref") == ref }.forEach { n -> n.campos.forEach { (k, v) -> if (k.length == 11 && k.startsWith("d2")) out[k.substring(1)] = (out[k.substring(1)] ?: 0L) + v.comoLong() } }
        return out
    }

    /** Todo lo jugado y leído por día y por cosa (para el diario): día → (ref → ms). */
    fun tiempoPorDia(): Map<String, Map<String, Long>> = cacheado("tiempoPorDia") {
        val out = HashMap<String, HashMap<String, Long>>()
        tiempo().vivos().forEach { n ->
            val ref = n.texto("ref").ifBlank { n.ref.substringBefore('~') }
            n.campos.forEach { (k, v) -> if (k.length == 11 && k.startsWith("d2")) out.getOrPut(k.substring(1)) { HashMap() }.merge(ref, v.comoLong(), Long::plus) }
        }
        out
    }

    /** El objetivo de libros del año (lo fija Marfil: `ajustes/lectura`). */
    fun objetivoLibros(): Pair<Int, Int> {
        val n = LaFamilia.coleccion("ajustes").nodo("lectura") ?: return 0 to 0
        return n.entero("ano").toInt() to n.entero("anio").toInt()
    }

    // ================================================================ lo de tu pareja (de su cuenta, solo para leer)

    /** Tu pareja: su uid (`/users/<tú>/partner`), si la tienes. Se mira una vez por sesión (bloquea: la primera vez va a la red). */
    @Volatile private var pareja: Pair<String, String>? = null   // (tu uid, el suyo)
    fun uidPareja(): String {
        val yo = LaFamilia.uid
        if (yo.isBlank()) return ""
        pareja?.let { (de, pu) -> if (de == yo) return pu }
        val pu = runCatching { LaFamilia.db("GET", "/users/$yo/partner")?.let { Json.lee(it) as? String }.orEmpty() }.getOrDefault("")
        pareja = yo to pu
        return pu
    }
    /** Lo ya sabido de tu pareja (sin ir a la red): su uid, o "" si aún no se ha mirado o no hay. */
    fun uidParejaSabido(): String = pareja?.takeIf { it.first == LaFamilia.uid }?.second.orEmpty()

    /** Su nombre (el que puso en su Azabache) o su usuario. Bloquea. */
    fun nombreDe(uid: String): String = nombres.getOrPut(uid) {
        listOf("name", "user").firstNotNullOfOrNull { c -> runCatching { LaFamilia.db("GET", "/users/$uid/$c")?.let { Json.lee(it) as? String } }.getOrNull()?.takeIf { it.isNotBlank() } } ?: "Tu pareja"
    }
    private val nombres = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val ajenasOidas = java.util.Collections.synchronizedSet(HashSet<String>())
    private fun ajena(uid: String, nombre: String): Coleccion = LaFamilia.ajena(uid, nombre).also { c ->
        if (ajenasOidas.add("$uid/$nombre")) c.alCambiar { synchronized(this) { version++ } }
    }

    /** Sus categorías (las suyas, por si tiene alguna que tú no). */
    fun kindsDe(uid: String): List<Kind> = listOf(GAMES, BOOKS) + cacheado("kinds_$uid") { lista("kinds", ajena(uid, "azabache")).mapNotNull { kindDeMapa(it) }.distinctBy { it.id } }

    /** Sus fichas de una categoría (los juegos y libros de su cuenta, con sus notas; sus categorías, de su Azabache). */
    fun fichasDe(uid: String, kind: Int): List<Ficha> = cacheado("fichas_${uid}_$kind") {
        if (kind == JUEGOS || kind == LIBROS) construye(kind, ajena(uid, if (kind == JUEGOS) "juegos" else "libros"), ajena(uid, "tiempo"))
        else lista("items_$kind", ajena(uid, "azabache")).map { Ficha(kind, it, true) } + lista("iplays_$kind", ajena(uid, "azabache")).map { Ficha(kind, it, false) }
    }

    /** Lo suyo jugado o leído de una cosa, por día. */
    fun porDiaDe(uid: String, ref: String): Map<String, Long> {
        val out = HashMap<String, Long>()
        ajena(uid, "tiempo").vivos().filter { it.texto("ref") == ref }.forEach { n -> n.campos.forEach { (k, v) -> if (k.length == 11 && k.startsWith("d2")) out[k.substring(1)] = (out[k.substring(1)] ?: 0L) + v.comoLong() } }
        return out
    }

    /** (Para las pruebas.) */
    fun olvidaParaPruebas() { synchronized(this) { cache.clear(); oyendoDe = null; pareja = null; ajenasOidas.clear(); nombres.clear() } }
}
