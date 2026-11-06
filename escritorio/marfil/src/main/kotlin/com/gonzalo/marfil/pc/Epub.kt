package com.gonzalo.marfil.pc

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.Closeable
import java.io.File
import java.net.URLDecoder
import java.util.IdentityHashMap
import java.util.zip.ZipFile

/**
 * De dónde sale cada letra de un bloque: un nodo de texto del XHTML y, para cada letra suya, en qué letra del bloque cae ([mapa]: tantas
 * posiciones como letras tiene el nodo, más una). Con esto un tramo del bloque (lo que subrayas) se convierte en el EPUB CFI del móvil y al revés.
 */
class Pieza(val nodo: Node, val mapa: IntArray)

/**
 * Un bloque de un capítulo tal como se pinta: un párrafo, un título, una cita, una imagen o un separador. [camino] es su sitio en el
 * XHTML como en un EPUB CFI (los pasos pares desde `<html>`, contando los botones de capítulo que pone el lector del móvil): con él la
 * posición que guarda el ordenador es la misma que entiende el lector del móvil (foliate), y al revés.
 */
class Bloque(
    val tipo: Tipo, val texto: AnnotatedString, val seccion: Int, val camino: List<Int>, val imagen: String = "", val ids: Set<String> = emptySet(),
    val largo: Int = texto.length, val elemento: Element? = null, val piezas: List<Pieza> = emptyList(),
) {
    enum class Tipo { PARRAFO, TITULO1, TITULO2, TITULO3, CITA, IMAGEN, SEPARADOR }
    val esTitulo: Boolean get() = tipo == Tipo.TITULO1 || tipo == Tipo.TITULO2 || tipo == Tipo.TITULO3
}

/** Una entrada del índice del libro: su nombre, su nivel y adónde lleva (archivo y ancla). */
class Entrada(val titulo: String, val nivel: Int, val seccion: Int, val ancla: String)

/** Lo que se ha leído de un capítulo: su XHTML (con los botones de capítulo del móvil, vacíos), sus bloques y dónde está cada texto. */
class SeccionLeida(val doc: Document?, val bloques: List<Bloque>) {
    /** Todos los nodos en orden de documento (para saber qué va antes o después de qué) y dónde acaba cada uno. */
    internal val pre = IdentityHashMap<Node, Int>()
    internal val finSub = IdentityHashMap<Node, Int>()
    internal val textos = ArrayList<Node>()          // los de texto, en orden
    internal val ordenTexto = IdentityHashMap<Node, Int>()
    internal val dueno = IdentityHashMap<Node, Pair<Int, Pieza>>()

    init {
        doc?.let { d ->
            var n = 0
            fun recorre(x: Node) {
                pre[x] = n++
                if (EpubCfi.esTexto(x)) { ordenTexto[x] = textos.size; textos += x }
                x.childNodes().forEach { recorre(it) }
                finSub[x] = n - 1
            }
            recorre(d)
        }
        bloques.forEachIndexed { i, b -> b.piezas.forEach { p -> dueno[p.nodo] = i to p } }
    }
}

/**
 * Un EPUB: sus datos (título, autor, portada), su orden de lectura (el «spine»), su índice y sus capítulos en bloques. Se lee como XML,
 * sin reordenar nada (el analizador de HTML «arreglaría» el documento y las posiciones ya no serían las del móvil).
 */
class Epub(val archivo: File) : Closeable {
    private val zip = ZipFile(archivo)
    private fun entrada(ruta: String) = zip.getEntry(ruta) ?: zip.entries().asSequence().firstOrNull { it.name.equals(ruta, ignoreCase = true) }
    fun bytes(ruta: String): ByteArray? = entrada(ruta)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
    private fun texto(ruta: String): String? = bytes(ruta)?.toString(Charsets.UTF_8)
    private fun xml(t: String): Document = Jsoup.parse(t, "", Parser.xmlParser())

    val rutaOpf: String = texto("META-INF/container.xml")?.let { c -> xml(c).selectFirst("rootfile")?.attr("full-path") }?.takeIf { it.isNotBlank() }
        ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf") }?.name ?: error("No es un EPUB (no tiene .opf)")
    private val base = rutaOpf.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    private val opf: Document = xml(texto(rutaOpf) ?: error("No se puede leer el .opf"))
    private val paquete: Element = opf.children().firstOrNull { it.normalName().endsWith("package") } ?: error("El .opf no tiene <package>")

    private fun hijo(e: Element, nombre: String) = e.children().firstOrNull { it.normalName().substringAfter(':') == nombre }
    private fun hijos(e: Element, nombre: String) = e.children().filter { it.normalName().substringAfter(':') == nombre }

    private val metadata = hijo(paquete, "metadata")
    val titulo: String = metadata?.let { hijo(it, "title")?.text()?.trim() }.orEmpty()
    val autor: String = metadata?.let { m -> hijos(m, "creator").joinToString(", ") { it.text().trim() } }.orEmpty()
    val idioma: String = metadata?.let { hijo(it, "language")?.text()?.trim() }.orEmpty()

    private class Item(val id: String, val href: String, val tipo: String, val props: String)
    private val manifest: Map<String, Item> = hijo(paquete, "manifest")?.let { m ->
        hijos(m, "item").associate { it.attr("id") to Item(it.attr("id"), resuelve(base, it.attr("href")), it.attr("media-type"), it.attr("properties")) }
    }.orEmpty()

    private val spineEl = hijo(paquete, "spine") ?: error("El .opf no tiene <spine>")

    /**
     * Las secciones en orden de lectura: el archivo, su «idref», su tamaño (para el porcentaje, como el móvil), su paso CFI, si se lee
     * seguido (`linear`) y su sitio en el paquete tal como lo escribe foliate («/6/4» o «/6/10[id]»).
     */
    class Seccion(val ruta: String, val idref: String, val tamano: Long, val pasoCfi: Int, val lineal: Boolean = true, val base: List<EpubCfi.Paso> = emptyList())
    val secciones: List<Seccion> = spineEl.children().filter { it.normalName().substringAfter(':') == "itemref" }.mapIndexedNotNull { i, ir ->
        val it = manifest[ir.attr("idref")] ?: return@mapIndexedNotNull null
        Seccion(it.href, ir.attr("idref"), entrada(it.href)?.size?.coerceAtLeast(1) ?: 1, 2 * (ir.elementSiblingIndex() + 1), ir.attr("linear") != "no", EpubCfi.deItemref(ir))
    }
    /** El paso CFI del <spine> dentro de <package> (normalmente /6). */
    val pasoSpine: Int = 2 * (spineEl.elementSiblingIndex() + 1)
    /** Lo que cuenta para el porcentaje: como foliate, solo lo que se lee seguido. */
    val tamanos: List<Long> = secciones.map { if (it.lineal) it.tamano else 0L }
    val total: Long = tamanos.sum().coerceAtLeast(1)

    /** La portada (sus bytes), si el libro la dice. */
    fun portada(): ByteArray? {
        val porProp = manifest.values.firstOrNull { "cover-image" in it.props.split(' ') }
        val idMeta = metadata?.let { m -> hijos(m, "meta").firstOrNull { it.attr("name") == "cover" }?.attr("content") }
        val it = porProp ?: idMeta?.let { manifest[it] } ?: manifest.values.firstOrNull { it.tipo.startsWith("image/") && it.id.contains("cover", true) }
        return it?.let { bytes(it.href) }
    }

    /** El índice (el del EPUB 3, o el toc.ncx del EPUB 2). */
    val indice: List<Entrada> by lazy {
        val nav = manifest.values.firstOrNull { "nav" in it.props.split(' ') }
        val out = ArrayList<Entrada>()
        fun aSeccion(href: String, desde: String): Pair<Int, String> {
            val r = resuelve(desde.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }, href.substringBefore('#'))
            return secciones.indexOfFirst { it.ruta == r } to href.substringAfter('#', "")
        }
        if (nav != null) runCatching {
            val d = xml(texto(nav.href) ?: "")
            val toc = d.select("nav").firstOrNull { it.attr("epub:type") == "toc" || it.attr("type") == "toc" } ?: d.selectFirst("nav")
            fun recorre(ol: Element?, nivel: Int) {
                ol?.children()?.filter { it.normalName() == "li" }?.forEach { li ->
                    val a = li.children().firstOrNull { it.normalName() == "a" || it.normalName() == "span" }
                    if (a != null && a.attr("href").isNotBlank()) { val (s, an) = aSeccion(a.attr("href"), nav.href); if (s >= 0) out += Entrada(a.text().trim(), nivel, s, an) }
                    recorre(li.children().firstOrNull { it.normalName() == "ol" }, nivel + 1)
                }
            }
            recorre(toc?.children()?.firstOrNull { it.normalName() == "ol" }, 0)
        }
        if (out.isEmpty()) runCatching {
            val ncx = manifest.values.firstOrNull { it.tipo == "application/x-dtbncx+xml" } ?: return@runCatching
            val d = xml(texto(ncx.href) ?: "")
            fun recorre(e: Element, nivel: Int) {
                e.children().filter { it.normalName() == "navpoint" }.forEach { np ->
                    val t = np.selectFirst("navLabel text, navlabel text")?.text()?.trim().orEmpty()
                    val src = np.selectFirst("content")?.attr("src").orEmpty()
                    val (s, an) = aSeccion(src, ncx.href); if (s >= 0 && t.isNotBlank()) out += Entrada(t, nivel, s, an)
                    recorre(np, nivel + 1)
                }
            }
            d.selectFirst("navMap, navmap")?.let { recorre(it, 0) }
        }
        out
    }

    private val cache = HashMap<Int, SeccionLeida>()

    /** Un capítulo leído (la primera vez que hace falta). */
    fun seccion(i: Int): SeccionLeida = synchronized(cache) { cache.getOrPut(i) { runCatching { lee(i) }.getOrElse { SeccionLeida(null, emptyList()) } } }

    /** Los bloques de una sección. */
    fun bloques(i: Int): List<Bloque> = seccion(i).bloques

    /** ¿Tiene esta sección otra antes que se lea seguido? (el lector del móvil le pone entonces arriba el botón «← Capítulo anterior»). */
    private fun hayAnterior(i: Int) = (0 until i).any { secciones[it].lineal }
    private fun haySiguiente(i: Int) = (i + 1 until secciones.size).any { secciones[it].lineal }

    private fun lee(i: Int): SeccionLeida {
        val s = secciones[i]
        val d = xml(texto(s.ruta) ?: return SeccionLeida(null, emptyList()))
        val html = d.children().firstOrNull { it.normalName() == "html" } ?: return SeccionLeida(d, emptyList())
        val body = html.children().firstOrNull { it.normalName() == "body" } ?: return SeccionLeida(d, emptyList())
        // Los botones de capítulo que el lector del móvil mete en el <body> (lector.js, botonesDeCapitulo): aunque no se vean, cuentan en
        // sus posiciones (el de arriba corre dos pasos todo lo del <body>). Aquí van vacíos, solo para contar igual.
        if (hayAnterior(i)) body.prependChild(Element("div").attr("class", "atrio-cap ant"))
        if (haySiguiente(i)) body.appendChild(Element("div").attr("class", "atrio-cap sig"))
        val dir = s.ruta.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        return SeccionLeida(d, Troceador(i, dir).trocea(body))
    }

    override fun close() = zip.close()

    // ---------------------------------------------------------------- posiciones (EPUB CFI, como el móvil)

    /** El CFI de un punto: la letra [letra] del bloque [b] (las imágenes, el elemento). */
    fun cfiDe(b: Bloque, letra: Int = 0): String {
        val s = secciones.getOrNull(b.seccion) ?: return ""
        val dentro = if (b.piezas.isEmpty()) b.elemento?.let { EpubCfi.Leido(listOf(EpubCfi.pasosDe(it, null))) }
            else puntoDom(b, letra, false)?.let { (n, o) -> EpubCfi.deTramo(n, o, n, o) }
        return EpubCfi.completo(s.base, dentro ?: return EpubCfi.Leido(listOf(s.base)).toString()).toString()
    }

    /** El CFI de un tramo de un capítulo (un subrayado): de la letra [l1] del bloque [b1] a la [l2] (sin incluir) del [b2]. */
    fun cfiTramo(b1: Bloque, l1: Int, b2: Bloque, l2: Int): String? {
        if (b1.seccion != b2.seccion) return null
        val s = secciones.getOrNull(b1.seccion) ?: return null
        val (n1, o1) = puntoDom(b1, l1, false) ?: return null
        val (n2, o2) = puntoDom(b2, l2, true) ?: return null
        return EpubCfi.completo(s.base, EpubCfi.deTramo(n1, o1, n2, o2)).toString()
    }

    /**
     * La letra de un bloque en el nodo de texto del que sale: para el principio de un tramo, la letra que la escribe; para el final, justo
     * después de la última. (Si esa letra no sale de ningún texto —un salto de línea de un `<br>`—, la de al lado.)
     */
    fun puntoDom(b: Bloque, letra: Int, alFinal: Boolean): Pair<Node, Int>? {
        if (b.piezas.isEmpty()) return null
        fun inicio(k: Int): Pair<Node, Int>? { for (p in b.piezas) for (i in 0 until p.mapa.size - 1) if (p.mapa[i] == k && p.mapa[i + 1] == k + 1) return p.nodo to i; return null }
        fun fin(k: Int): Pair<Node, Int>? { for (p in b.piezas) for (i in 1 until p.mapa.size) if (p.mapa[i - 1] == k - 1 && p.mapa[i] == k) return p.nodo to i; return null }
        if (!alFinal) {
            for (k in letra until b.largo) inicio(k)?.let { return it }
            return b.piezas.last().let { it.nodo to it.mapa.size - 1 }
        }
        for (k in letra downTo 1) fin(k)?.let { return it }
        return b.piezas.first().let { it.nodo to 0 }
    }

    /** Dónde cae un CFI en este libro: la sección y, si se puede, el bloque y la letra del principio y del final. */
    class Lugar(val seccion: Int, val desde: Pair<Int, Int>?, val hasta: Pair<Int, Int>?)

    fun ubica(cfi: String): Lugar? {
        val c = EpubCfi.lee(cfi) ?: return null
        val paso = EpubCfi.pasoSeccion(c) ?: return null
        val sec = secciones.indexOfFirst { it.pasoCfi == paso }.takeIf { it >= 0 } ?: return null
        val sl = seccion(sec)
        val doc = sl.doc ?: return Lugar(sec, null, null)
        val (a, b) = EpubCfi.resuelve(doc, c) ?: return Lugar(sec, null, null)
        val desde = aLetra(sl, a, false)
        val hasta = if (c.esRango) aLetra(sl, b, true) else desde
        return Lugar(sec, desde, hasta)
    }

    /** Un extremo del documento, en letras de un bloque: (bloque de la sección, letra). */
    private fun aLetra(s: SeccionLeida, l: EpubCfi.Limite, alFinal: Boolean): Pair<Int, Int>? {
        // Primero, en qué texto (en orden) y en qué letra suya
        val (k, off) = posicionTexto(s, l) ?: return null
        if (!alFinal) {
            for (j in k until s.textos.size) s.dueno[s.textos[j]]?.let { (bi, p) -> return bi to p.mapa[if (j == k) off.coerceIn(0, p.mapa.size - 1) else 0] }
            return s.bloques.lastIndex.takeIf { it >= 0 }?.let { it to s.bloques[it].largo }
        }
        if (k < s.textos.size && off > 0) s.dueno[s.textos[k]]?.let { (bi, p) -> return bi to p.mapa[off.coerceIn(0, p.mapa.size - 1)] }
        for (j in minOf(k, s.textos.size) - 1 downTo 0) s.dueno[s.textos[j]]?.let { (bi, p) -> return bi to p.mapa[p.mapa.size - 1] }
        return if (s.bloques.isEmpty()) null else 0 to 0
    }

    /** (el número del texto en orden, la letra en él) de un extremo; un elemento cuenta desde el primer texto que tiene dentro o después. */
    private fun posicionTexto(s: SeccionLeida, l: EpubCfi.Limite): Pair<Int, Int>? {
        val n = l.nodo
        fun primeroDesde(orden: Int): Int { val i = s.textos.indexOfFirst { (s.pre[it] ?: -1) >= orden }; return if (i < 0) s.textos.size else i }
        if (EpubCfi.esTexto(n)) {
            val k = s.ordenTexto[n] ?: return null
            return when { l.antes -> k to 0; l.despues -> k to EpubCfi.largo(n); else -> k to l.offset }
        }
        val pre = s.pre[n] ?: return null
        val fin = s.finSub[n] ?: pre
        return when {
            l.despues -> primeroDesde(fin + 1) to 0
            l.antes || n !is Element -> primeroDesde(pre) to 0
            else -> {
                val h = n.childNodes().getOrNull(l.offset)
                (if (h != null) primeroDesde(s.pre[h] ?: pre) else primeroDesde(fin + 1)) to 0
            }
        }
    }

    // ---------------------------------------------------------------- enlaces y notas al pie

    /** Un enlace del libro: a otra parte del libro ([seccion], [ancla]) o fuera ([externo]); [nota]: es una llamada a una nota al pie. */
    class Enlace(val seccion: Int, val ancla: String, val externo: String = "", val nota: Boolean = false, val quizas: Boolean = false)

    /** Lo que guarda un bloque en sus enlaces («tipo|ruta#ancla»). */
    fun enlace(valor: String, desdeSeccion: Int): Enlace? {
        val tipo = valor.substringBefore('|'); val href = valor.substringAfter('|')
        if (Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(href)) return Enlace(-1, "", externo = href)
        val ruta = href.substringBefore('#'); val ancla = href.substringAfter('#', "")
        val sec = if (ruta.isEmpty()) desdeSeccion else secciones.indexOfFirst { it.ruta == ruta }
        if (sec < 0) return null
        return Enlace(sec, ancla, nota = tipo == "nota", quizas = tipo == "quizas")
    }

    /**
     * El texto de una nota al pie (como la hoja de notas del móvil, `footnotes.js`): el elemento al que lleva el enlace; si es algo de dentro
     * de un párrafo, el párrafo entero. null si no hay nada que enseñar.
     */
    fun textoDeNota(en: Enlace): Pair<String, List<AnnotatedString>>? {
        if (en.seccion < 0 || en.ancla.isBlank()) return null
        val sl = seccion(en.seccion)
        val doc = sl.doc ?: return null
        var el: Element = doc.getElementById(en.ancla) ?: return null
        val objetivo = el
        val enLinea = setOf("a", "span", "sup", "sub", "em", "strong", "i", "b", "small", "big")
        while (el.normalName() in enLinea) el = el.parent() ?: break
        if (el.normalName() == "body") el = objetivo.nextElementSibling()?.takeIf { it.normalName() !in enLinea } ?: return null
        val tipos = (objetivo.attr("epub:type") + " " + objetivo.attr("role") + " " + el.attr("epub:type") + " " + el.attr("role")).split(' ')
        val nombre = when {
            tipos.any { it == "biblioentry" || it == "doc-biblioentry" } -> "Referencia"
            tipos.any { it == "glossdef" || it == "definition" } -> "Definición"
            tipos.any { it == "footnote" || it == "doc-footnote" } -> "Nota al pie"
            else -> "Nota"
        }
        val dir = secciones[en.seccion].ruta.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val bs = Troceador(en.seccion, dir, notas = true).trocea(el, tambienElMismo = true).filter { it.tipo != Bloque.Tipo.IMAGEN }.map { it.texto }
        return if (bs.isEmpty()) null else nombre to bs
    }

    companion object {
        /** Una ruta relativa (con ../ y %20) desde una carpeta del EPUB. */
        fun resuelve(dir: String, href: String): String {
            val h = runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
            if (h.startsWith("/")) return h.removePrefix("/")
            val partes = ArrayList<String>()
            (dir + h).split('/').forEach { p -> when (p) { "", "." -> {}; ".." -> if (partes.isNotEmpty()) partes.removeAt(partes.size - 1); else -> partes += p } }
            return partes.joinToString("/")
        }
    }
}

/** El sitio CFI de un elemento: los pasos pares desde los hijos de `<html>` (como foliate). */
fun caminoDe(e: Element): List<Int> {
    val out = ArrayList<Int>()
    var x: Element? = e
    while (x != null && x.normalName() != "html") { out += 2 * (x.elementSiblingIndex() + 1); x = x.parent() }
    return out.reversed()
}

/** Los blancos de HTML (los que se juntan en un espacio); el espacio duro no lo es. */
private fun esBlanco(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C' || c == '\u000B'

/** Pasa el cuerpo de un capítulo a bloques (párrafos, títulos, citas, imágenes), con su texto, su formato y de dónde sale cada letra. */
private class Troceador(val seccion: Int, val dir: String, val notas: Boolean = false) {
    val out = ArrayList<Bloque>()
    private val BLOQUE = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "dt", "dd", "pre", "figcaption", "caption", "tr", "td", "th")
    private val CONTENEDOR = setOf("div", "section", "article", "aside", "header", "footer", "main", "nav", "body", "blockquote", "figure", "ul", "ol", "dl", "table", "tbody", "thead", "tfoot", "center", "hgroup")

    fun trocea(body: Element, tambienElMismo: Boolean = false): List<Bloque> {
        if (tambienElMismo && !tieneBloques(body)) parrafo(body, false) else recorre(body, false)
        return out
    }

    private fun tieneBloques(e: Element) = e.children().any { val n = it.normalName(); n in BLOQUE || n in CONTENEDOR || n == "hr" || esImagen(it) }
    private fun esImagen(e: Element) = e.normalName() == "img" || e.normalName() == "svg" || e.normalName() == "image"

    /** Lo que el lector del móvil no enseña en el texto: sus botones de capítulo y las notas al pie (se ven al tocar su llamada). */
    private fun oculto(e: Element): Boolean {
        if (e.hasClass("atrio-cap")) return true
        if (!notas && e.normalName() == "aside") { val t = e.attr("epub:type").split(' '); if ("footnote" in t || "endnote" in t || "rearnote" in t) return true }
        return false
    }

    private fun recorre(e: Element, cita: Boolean) {
        for (c in e.children()) {
            if (oculto(c)) continue
            val n = c.normalName()
            when {
                n == "hr" -> out += Bloque(Bloque.Tipo.SEPARADOR, AnnotatedString("* * *"), seccion, caminoDe(c), elemento = c)
                esImagen(c) -> imagen(c)
                n == "blockquote" -> if (tieneBloques(c)) recorre(c, true) else parrafo(c, true)
                n in BLOQUE -> { if (n.startsWith("h") && n.length == 2) titulo(c) else if (n == "tr") parrafo(c, cita) else if (tieneBloques(c) && n != "p") recorre(c, cita) else parrafo(c, cita) }
                n in CONTENEDOR -> if (tieneBloques(c)) recorre(c, cita) else if (c.text().isNotBlank()) parrafo(c, cita) else c.select("img, image").forEach { imagen(it) }
                n == "script" || n == "style" -> {}
                else -> if (c.text().isNotBlank()) parrafo(c, cita) else c.select("img, image").forEach { imagen(it) }
            }
        }
    }

    private fun ids(e: Element): Set<String> = (listOf(e) + e.select("[id]")).mapNotNull { it.attr("id").takeIf { a -> a.isNotBlank() } }.toSet()

    private fun imagen(e: Element) {
        val src = e.attr("src").ifBlank { e.attr("xlink:href").ifBlank { e.attr("href") } }.ifBlank { e.selectFirst("image")?.let { it.attr("xlink:href").ifBlank { it.attr("href") } }.orEmpty() }
        if (src.isNotBlank()) out += Bloque(Bloque.Tipo.IMAGEN, AnnotatedString(e.attr("alt")), seccion, caminoDe(e), Epub.resuelve(dir, src), ids(e), elemento = e)
    }

    private fun titulo(e: Element) {
        val (t, piezas) = formato(e)
        if (t.isBlank()) return
        val tipo = when (e.normalName()) { "h1" -> Bloque.Tipo.TITULO1; "h2" -> Bloque.Tipo.TITULO2; else -> Bloque.Tipo.TITULO3 }
        out += Bloque(tipo, t, seccion, caminoDe(e), ids = ids(e), elemento = e, piezas = piezas)
    }

    private fun parrafo(e: Element, cita: Boolean) {
        val (t, piezas) = formato(e)
        if (t.isNotBlank()) out += Bloque(if (cita) Bloque.Tipo.CITA else Bloque.Tipo.PARRAFO, t, seccion, caminoDe(e), ids = ids(e), elemento = e, piezas = piezas)
        e.select("img, image").forEach { imagen(it) }
    }

    /** ¿Es una llamada a una nota? (como `footnotes.js`: por su tipo, o porque va en superíndice y no es un «volver»). */
    private fun tipoEnlace(a: Element): String {
        val tipos = a.attr("epub:type").split(' ').toSet(); val roles = a.attr("role").split(' ').toSet()
        if (listOf("biblioref", "glossref", "noteref").any { it in tipos } || listOf("doc-biblioref", "doc-glossref", "doc-noteref").any { it in roles }) return "nota"
        val sup = a.normalName() == "sup" || (a.children().size == 1 && a.child(0).normalName() == "sup") || a.parent()?.normalName() == "sup"
        return if (sup && "backlink" !in tipos && "doc-backlink" !in roles) "quizas" else ""
    }

    /** El texto de un bloque con su formato (cursivas, negritas, superíndices, enlaces), con los espacios como en HTML, y de dónde sale cada letra. */
    private fun formato(e: Element): Pair<AnnotatedString, List<Pieza>> {
        val b = AnnotatedString.Builder()
        val piezas = ArrayList<Pieza>()
        var espacio = true
        fun nodo(n: Node) {
            when (n) {
                is TextNode -> {
                    val t = n.wholeText
                    val mapa = IntArray(t.length + 1)
                    for (i in t.indices) {
                        mapa[i] = b.length
                        val ch = t[i]
                        if (esBlanco(ch)) { if (!espacio) { b.append(' '); espacio = true } } else { b.append(ch); espacio = false }
                    }
                    mapa[t.length] = b.length
                    piezas += Pieza(n, mapa)
                }
                is Element -> {
                    val nm = n.normalName()
                    if (nm == "br") { b.append('\n'); espacio = true; return }
                    if (nm == "script" || nm == "style" || nm == "img" || nm == "image" || nm == "svg") return
                    if (oculto(n)) return
                    val estilo = when (nm) {
                        "em", "i", "cite", "dfn", "var" -> SpanStyle(fontStyle = FontStyle.Italic)
                        "strong", "b" -> SpanStyle(fontWeight = FontWeight.Bold)
                        "sup" -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em)
                        "sub" -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.7.em)
                        "small" -> SpanStyle(fontSize = 0.85.em)
                        else -> null
                    }
                    var pops = 0
                    if (nm == "a" && n.attr("href").isNotBlank()) {
                        val href = n.attr("href").let { h -> if (h.startsWith("#") || Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(h)) h else Epub.resuelve(dir, h.substringBefore('#')) + (if ('#' in h) "#" + h.substringAfter('#') else "") }
                        b.pushStringAnnotation(ENLACE, tipoEnlace(n) + "|" + href); pops++
                    }
                    if (estilo != null) { b.pushStyle(estilo); pops++ }
                    n.childNodes().forEach { nodo(it) }
                    repeat(pops) { b.pop() }
                }
            }
        }
        e.childNodes().forEach { nodo(it) }
        // Sin espacios al principio ni al final (y las piezas, con las letras del texto que queda)
        val s = b.toAnnotatedString()
        val ini = s.text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val fin = s.text.indexOfLast { !it.isWhitespace() } + 1
        if (fin <= ini) return AnnotatedString("") to emptyList()
        val largo = fin - ini
        piezas.forEach { p -> for (i in p.mapa.indices) p.mapa[i] = (p.mapa[i] - ini).coerceIn(0, largo) }
        return s.subSequence(ini, fin) to piezas
    }

    companion object { const val ENLACE = "enlace" }
}

/** La etiqueta de los enlaces en el texto de un bloque. */
const val ETIQUETA_ENLACE = "enlace"
