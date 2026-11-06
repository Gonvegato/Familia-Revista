package com.gonzalo.marfil.pc

import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/*
 * Las posiciones de un EPUB (EPUB CFI) exactamente como las escribe y las lee el lector del móvil: foliate-js (`epubcfi.js`), con sus
 * mismas reglas, paso a paso (los hijos de un elemento se cuentan como [elemento, texto, elemento, texto…], los textos seguidos son un
 * solo trozo, los comentarios no cuentan, cada elemento con id lo lleva entre corchetes…). Así un subrayado hecho en el ordenador se
 * pinta en el móvil sobre las mismas letras, y al revés. Lo comprueba `CfiTest` con lo que calcula foliate de verdad en Chromium
 * (`herramientas/casos/marfil-cfi.json`, que hace `herramientas/marfil/lector-prueba/cfi.js`).
 */
object EpubCfi {
    /** Un paso: su número (pares = elementos; impares = trozos de texto), el id del elemento y, en el texto, la letra. */
    class Paso(val indice: Int, val id: String? = null, val offset: Int? = null) {
        override fun toString() = "/$indice" + (if (!id.isNullOrEmpty()) "[${escapa(id)}]" else "") + (if (offset != null && indice % 2 == 1) ":$offset" else "")
        override fun equals(other: Any?) = other is Paso && other.indice == indice && other.id == id && other.offset == offset
        override fun hashCode() = indice * 31 + (offset ?: 0)
    }

    /** Un camino: los pasos de cada documento, separados por «!» (el paquete y luego el capítulo). */
    class Leido(val padre: List<List<Paso>>, val inicio: List<List<Paso>>? = null, val fin: List<List<Paso>>? = null) {
        val esRango: Boolean get() = inicio != null
        /** Un punto: el principio (o el final) del rango, o el camino tal cual. */
        fun punto(alFinal: Boolean = false): List<List<Paso>> = if (inicio == null) padre else junta(padre, if (alFinal) fin!! else inicio)
        override fun toString(): String = "epubcfi(" + (if (inicio == null) interior(padre) else listOf(padre, inicio, fin!!).joinToString(",") { interior(it) }) + ")"
    }

    private fun interior(p: List<List<Paso>>) = p.joinToString("!") { c -> c.joinToString("") }
    private fun escapa(s: String) = s.replace(Regex("[\\^\\[\\](),;=]")) { "^" + it.value }
    private fun junta(a: List<List<Paso>>, b: List<List<Paso>>): List<List<Paso>> = a.dropLast(1) + listOf(a.last() + b.first()) + b.drop(1)

    // ---------------------------------------------------------------- leer

    private class Token(val tipo: String, val valor: Any?)

    /** El analizador de foliate (`tokenizer`), carácter a carácter. */
    private fun tokens(s: String): List<Token> {
        val out = ArrayList<Token>()
        var estado: String? = null
        var escape = false
        val valor = StringBuilder()
        fun push(t: String, v: Any?) { out += Token(t, v); estado = null; valor.setLength(0) }
        fun cat(c: Char) { valor.append(c); escape = false }
        val letras: List<Char?> = s.trim().toList() + listOf(null)
        for (c in letras) {
            if (c == '^' && !escape) { escape = true; continue }
            val e = estado
            if (e == "!") push("!", null)
            else if (e == ",") push(",", null)
            else if (e == "/" || e == ":") {
                if (c != null && c.isDigit()) { cat(c); continue } else push(e, valor.toString().toIntOrNull())
            } else if (e == "~") {
                if (c != null && (c.isDigit() || c == '.')) { cat(c); continue } else push("~", valor.toString().toDoubleOrNull())
            } else if (e == "@") {
                if (c == ':') { push("@", valor.toString().toDoubleOrNull()); estado = "@"; continue }
                if (c != null && (c.isDigit() || c == '.')) { cat(c); continue } else push("@", valor.toString().toDoubleOrNull())
            } else if (e == "[") {
                if (c == ';' && !escape) { push("[", valor.toString()); estado = ";" }
                else if (c == ',' && !escape) { push("[", valor.toString()); estado = "[" }
                else if (c == ']' && !escape) push("[", valor.toString())
                else if (c != null) cat(c)
                continue
            } else if (e != null && e.startsWith(";")) {
                if (c == '=' && !escape) { estado = ";$valor"; valor.setLength(0) }
                else if (c == ';' && !escape) { push(e, valor.toString()); estado = ";" }
                else if (c == ']' && !escape) push(e, valor.toString())
                else if (c != null) cat(c)
                continue
            }
            if (c == '/' || c == ':' || c == '~' || c == '@' || c == '[' || c == '!' || c == ',') estado = c.toString()
        }
        return out
    }

    private class PasoMut(var indice: Int, var id: String? = null, var offset: Int? = null)

    /** Los pasos de un trozo (`parser`): «/4[id]/1:5». */
    private fun pasos(ts: List<Token>): List<Paso> {
        val out = ArrayList<PasoMut>()
        var estado: String? = null
        for (t in ts) {
            if (t.tipo == "/") out += PasoMut((t.valor as? Int) ?: -1)
            else {
                val ultimo = out.lastOrNull()
                when {
                    t.tipo == ":" -> ultimo?.offset = t.valor as? Int
                    t.tipo == "[" -> if (estado == "/" && (t.valor as? String).orEmpty().isNotEmpty()) ultimo?.id = t.valor as String else continue
                }
            }
            estado = t.tipo
        }
        return out.map { Paso(it.indice, it.id, it.offset) }
    }

    private fun parte(ts: List<Token>, separador: String): List<List<Token>> {
        val out = ArrayList<List<Token>>(); var actual = ArrayList<Token>()
        for (t in ts) if (t.tipo == separador) { out += actual; actual = ArrayList() } else actual += t
        out += actual
        return out
    }

    /** Lee un CFI («epubcfi(/6/4!/4/2,/1:0,/1:7)»). null si no lo es. */
    fun lee(cfi: String): Leido? = runCatching {
        val t = cfi.trim().let { Regex("^epubcfi\\((.*)\\)$").find(it)?.groupValues?.get(1) ?: it }
        val ts = tokens(t)
        val trozos = parte(ts, ",").map { tz -> parte(tz, "!").map { pasos(it) } }
        if (trozos.size == 1) Leido(trozos[0]) else Leido(trozos[0], trozos[1], trozos.getOrElse(2) { trozos[1] })
    }.getOrNull()?.takeIf { it.padre.isNotEmpty() && it.padre.first().isNotEmpty() }

    // ---------------------------------------------------------------- el documento, como lo cuenta foliate

    /** Lo que hay entre los hijos de un elemento, tal como los numera foliate. */
    sealed class Hijo {
        object Antes : Hijo(); object Primero : Hijo(); object Ultimo : Hijo(); object Despues : Hijo(); object Hueco : Hijo()
        class Elem(val e: Element) : Hijo()
        class Trozo(val nodos: List<Node>) : Hijo()
    }

    fun esTexto(n: Node) = n is TextNode || n is DataNode
    fun largo(n: Node): Int = when (n) { is TextNode -> n.wholeText.length; is DataNode -> n.wholeData.length; else -> 0 }

    /** `indexChildNodes`: [antes, (primero), elemento, texto, elemento, …, (último), después]. */
    fun hijos(e: Element): List<Hijo> {
        val nodos = e.childNodes().filter { esTexto(it) || it is Element }
        val arr = ArrayList<Hijo>()
        for (n in nodos) {
            val ultimo = arr.lastOrNull()
            if (ultimo == null) arr += if (n is Element) Hijo.Elem(n) else Hijo.Trozo(listOf(n))
            else if (esTexto(n)) {
                if (ultimo is Hijo.Trozo) arr[arr.size - 1] = Hijo.Trozo(ultimo.nodos + n) else arr += Hijo.Trozo(listOf(n))
            } else {
                if (ultimo is Hijo.Elem) { arr += Hijo.Hueco; arr += Hijo.Elem(n as Element) } else arr += Hijo.Elem(n as Element)
            }
        }
        if (arr.firstOrNull() is Hijo.Elem) arr.add(0, Hijo.Primero)
        if (arr.lastOrNull() is Hijo.Elem) arr += Hijo.Ultimo
        arr.add(0, Hijo.Antes)
        arr += Hijo.Despues
        return arr
    }

    private fun raiz(n: Node): Element? = n.ownerDocument()?.children()?.firstOrNull()

    /** `nodeToParts`: los pasos desde la raíz del documento hasta [nodo] (y la letra [offset], si es texto). */
    fun pasosDe(nodo: Node, offset: Int?): List<Paso> {
        val padre = nodo.parent() as? Element ?: return emptyList()
        val lista = hijos(padre)
        val i = lista.indexOfFirst { h -> (h is Hijo.Trozo && h.nodos.any { it === nodo }) || (h is Hijo.Elem && h.e === nodo) }
        var off = offset
        val h = lista.getOrNull(i)
        if (h is Hijo.Trozo && h.nodos.size > 1) {
            var suma = 0
            for (x in h.nodos) { if (x === nodo) { suma += offset ?: 0; break } else suma += largo(x) }
            off = suma
        }
        val paso = Paso(i, (nodo as? Element)?.attr("id")?.ifEmpty { null }, off)
        val r = if (padre !== raiz(nodo)) pasosDe(padre, null) + paso else listOf(paso)
        return r.filter { it.indice != -1 }
    }

    /** Un CFI de un punto o de un tramo, dentro de un documento (sin el paquete): lo de `fromRange`. */
    fun deTramo(desde: Node, offDesde: Int, hasta: Node, offHasta: Int): Leido {
        val a = pasosDe(desde, offDesde)
        if (desde === hasta && offDesde == offHasta) return Leido(listOf(a))
        val b = pasosDe(hasta, offHasta)
        return rango(listOf(a), listOf(b))
    }

    /** `buildRange`: lo común, el principio y el final. */
    private fun rango(de: List<List<Paso>>, a: List<List<Paso>>): Leido {
        val lf = de.last(); val lt = a.last()
        val padre = ArrayList<Paso>(); val ini = ArrayList<Paso>(); val fin = ArrayList<Paso>()
        var alPadre = true
        for (i in 0 until maxOf(lf.size, lt.size)) {
            val x = lf.getOrNull(i); val y = lt.getOrNull(i)
            alPadre = alPadre && x?.indice == y?.indice && (x?.offset ?: 0) == 0 && (y?.offset ?: 0) == 0
            if (alPadre) padre += x!! else { if (x != null) ini += x; if (y != null) fin += y }
        }
        return Leido(de.dropLast(1) + listOf(padre), listOf(ini), listOf(fin))
    }

    /** El paso de una sección en el paquete (`fromElements`): «/6/4» o «/6/10[id]». */
    fun deItemref(itemref: Element): List<Paso> = pasosDe(itemref, null)

    /** Junta el paso del capítulo con lo de dentro: «epubcfi(/6/4!/4/2,/1:0,/1:7)». */
    fun completo(base: List<Paso>, dentro: Leido): Leido =
        Leido(listOf(base) + dentro.padre, dentro.inicio, dentro.fin)

    // ---------------------------------------------------------------- resolver

    /** Un extremo de un tramo en el documento: un nodo y su letra; [antes]/[despues]: justo antes o después del nodo. */
    class Limite(val nodo: Node, val offset: Int = 0, val antes: Boolean = false, val despues: Boolean = false)

    /** `partsToNode`: adónde lleva un camino dentro de un documento. */
    fun aNodo(doc: Document, pasos: List<Paso>): Limite? {
        if (pasos.isEmpty()) return null
        pasos.last().id?.let { id -> doc.getElementById(id)?.let { return Limite(it, 0) } }
        var nodo: Any? = doc.children().firstOrNull() ?: return null
        for (p in pasos) {
            val e = nodo as? Element ?: return null
            val nuevo = hijos(e).getOrNull(p.indice) ?: return null
            when (nuevo) {
                Hijo.Primero -> return Limite(if (e.childNodeSize() > 0) e.childNode(0) else e)
                Hijo.Ultimo -> return Limite(if (e.childNodeSize() > 0) e.childNode(e.childNodeSize() - 1) else e)
                Hijo.Antes -> return Limite(e, antes = true)
                Hijo.Despues -> return Limite(e, despues = true)
                Hijo.Hueco -> nodo = null
                is Hijo.Elem -> nodo = nuevo.e
                is Hijo.Trozo -> nodo = nuevo
            }
        }
        val off = pasos.last().offset ?: 0
        return when (val n = nodo) {
            is Hijo.Trozo -> {
                var suma = 0
                for (x in n.nodos) { val l = largo(x); if (suma + l >= off) return Limite(x, off - suma); suma += l }
                Limite(n.nodos.last(), largo(n.nodos.last()))
            }
            is Node -> Limite(n, off)
            else -> null
        }
    }

    /** Un CFI dentro de su documento: el principio y el final (sin el paso del paquete, que ya dice el capítulo). */
    fun resuelve(doc: Document, cfi: Leido): Pair<Limite, Limite>? {
        val sinPaquete = Leido(cfi.padre.drop(1), cfi.inicio, cfi.fin)
        if (sinPaquete.padre.isEmpty() && !cfi.esRango) return null
        val a = runCatching { aNodo(doc, sinPaquete.punto(false).firstOrNull() ?: return null) }.getOrNull() ?: return null
        val b = runCatching { aNodo(doc, sinPaquete.punto(true).firstOrNull() ?: return null) }.getOrNull() ?: a
        return a to b
    }

    /** El paso del capítulo (el último del paquete) de un CFI: su número en el «spine». */
    fun pasoSeccion(cfi: Leido): Int? = cfi.padre.firstOrNull()?.lastOrNull()?.indice

    /** Compara dos CFI como foliate (`compare`): para ordenar subrayados en el orden del libro. */
    fun compara(a: Leido, b: Leido): Int {
        if (a.esRango || b.esRango) return compara(Leido(a.punto(false)), Leido(b.punto(false))).takeIf { it != 0 } ?: compara(Leido(a.punto(true)), Leido(b.punto(true)))
        val pa = a.padre; val pb = b.padre
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val p = pa.getOrElse(i) { emptyList() }; val q = pb.getOrElse(i) { emptyList() }
            val max = maxOf(p.size, q.size) - 1
            for (j in 0..max) {
                val x = p.getOrNull(j) ?: return -1
                val y = q.getOrNull(j) ?: return 1
                if (x.indice != y.indice) return x.indice.compareTo(y.indice)
                if (j == max) { val ox = x.offset; val oy = y.offset; if (ox != null && oy != null && ox != oy) return ox.compareTo(oy) }
            }
        }
        return 0
    }
}
