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
import java.util.zip.ZipFile

/**
 * Un bloque de un capítulo tal como se pinta: un párrafo, un título, una cita, una imagen o un separador. [camino] es su sitio en el
 * XHTML como en un EPUB CFI (los pasos pares desde `<html>`: `/4/2/10` = el 5.º elemento del 1.º del `<body>`): con él la posición
 * que guarda el ordenador es la misma que entiende el lector del móvil (foliate), y al revés.
 */
class Bloque(val tipo: Tipo, val texto: AnnotatedString, val seccion: Int, val camino: List<Int>, val imagen: String = "", val ids: Set<String> = emptySet(), val largo: Int = texto.length) {
    enum class Tipo { PARRAFO, TITULO1, TITULO2, TITULO3, CITA, IMAGEN, SEPARADOR }
}

/** Una entrada del índice del libro: su nombre, su nivel y adónde lleva (archivo y ancla). */
class Entrada(val titulo: String, val nivel: Int, val seccion: Int, val ancla: String)

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

    private class Item(val id: String, val href: String, val tipo: String, val props: String)
    private val manifest: Map<String, Item> = hijo(paquete, "manifest")?.let { m ->
        hijos(m, "item").associate { it.attr("id") to Item(it.attr("id"), resuelve(base, it.attr("href")), it.attr("media-type"), it.attr("properties")) }
    }.orEmpty()

    private val spineEl = hijo(paquete, "spine") ?: error("El .opf no tiene <spine>")

    /** Las secciones en orden de lectura: el archivo, su «idref», su tamaño (para el porcentaje, como el móvil) y su paso CFI. */
    class Seccion(val ruta: String, val idref: String, val tamano: Long, val pasoCfi: Int)
    val secciones: List<Seccion> = spineEl.children().mapIndexedNotNull { i, ir ->
        if (ir.normalName().substringAfter(':') != "itemref") return@mapIndexedNotNull null
        val it = manifest[ir.attr("idref")] ?: return@mapIndexedNotNull null
        Seccion(it.href, ir.attr("idref"), entrada(it.href)?.size?.coerceAtLeast(1) ?: 1, 2 * (i + 1))
    }
    /** El paso CFI del <spine> dentro de <package> (normalmente /6). */
    val pasoSpine: Int = 2 * (spineEl.elementSiblingIndex() + 1)
    val total: Long = secciones.sumOf { it.tamano }.coerceAtLeast(1)

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

    private val cache = HashMap<Int, List<Bloque>>()

    /** Los bloques de una sección (se leen la primera vez que hacen falta). */
    fun bloques(i: Int): List<Bloque> = synchronized(cache) { cache.getOrPut(i) { runCatching { lee(i) }.getOrDefault(emptyList()) } }

    private fun lee(i: Int): List<Bloque> {
        val s = secciones[i]
        val d = xml(texto(s.ruta) ?: return emptyList())
        val html = d.children().firstOrNull { it.normalName() == "html" } ?: return emptyList()
        val body = html.children().firstOrNull { it.normalName() == "body" } ?: return emptyList()
        val dir = s.ruta.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        return Troceador(i, dir).trocea(body)
    }

    override fun close() = zip.close()

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

/** Pasa el cuerpo de un capítulo a bloques (párrafos, títulos, citas, imágenes), con su texto y su formato. */
private class Troceador(val seccion: Int, val dir: String) {
    val out = ArrayList<Bloque>()
    private val BLOQUE = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "dt", "dd", "pre", "figcaption", "caption", "tr", "td", "th")
    private val CONTENEDOR = setOf("div", "section", "article", "aside", "header", "footer", "main", "nav", "body", "blockquote", "figure", "ul", "ol", "dl", "table", "tbody", "thead", "tfoot", "center", "hgroup")

    fun trocea(body: Element): List<Bloque> { recorre(body, false); return out }

    private fun tieneBloques(e: Element) = e.children().any { val n = it.normalName(); n in BLOQUE || n in CONTENEDOR || n == "hr" || esImagen(it) }
    private fun esImagen(e: Element) = e.normalName() == "img" || e.normalName() == "svg" || e.normalName() == "image"

    private fun recorre(e: Element, cita: Boolean) {
        for (c in e.children()) {
            val n = c.normalName()
            when {
                n == "hr" -> out += Bloque(Bloque.Tipo.SEPARADOR, AnnotatedString("* * *"), seccion, caminoDe(c))
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
        if (src.isNotBlank()) out += Bloque(Bloque.Tipo.IMAGEN, AnnotatedString(e.attr("alt")), seccion, caminoDe(e), Epub.resuelve(dir, src), ids(e))
    }

    private fun titulo(e: Element) {
        val t = formato(e)
        if (t.isBlank()) return
        val tipo = when (e.normalName()) { "h1" -> Bloque.Tipo.TITULO1; "h2" -> Bloque.Tipo.TITULO2; else -> Bloque.Tipo.TITULO3 }
        out += Bloque(tipo, t, seccion, caminoDe(e), ids = ids(e))
    }

    private fun parrafo(e: Element, cita: Boolean) {
        val t = formato(e)
        if (t.isNotBlank()) out += Bloque(if (cita) Bloque.Tipo.CITA else Bloque.Tipo.PARRAFO, t, seccion, caminoDe(e), ids = ids(e))
        e.select("img, image").forEach { imagen(it) }
    }

    /** El texto de un bloque con su formato (cursivas, negritas, superíndices), con los espacios como en HTML. */
    private fun formato(e: Element): AnnotatedString {
        val b = AnnotatedString.Builder()
        var espacio = true
        fun anade(t: String) {
            val limpio = t.replace(Regex("\\s+"), " ")
            for (ch in limpio) { if (ch == ' ') { if (!espacio) { b.append(' '); espacio = true } } else { b.append(ch); espacio = ch == '\n' } }
        }
        fun nodo(n: Node) {
            when (n) {
                is TextNode -> anade(n.wholeText)
                is Element -> {
                    val nm = n.normalName()
                    if (nm == "br") { b.append('\n'); espacio = true; return }
                    if (nm == "script" || nm == "style" || nm == "img" || nm == "image" || nm == "svg") return
                    val estilo = when (nm) {
                        "em", "i", "cite", "dfn", "var" -> SpanStyle(fontStyle = FontStyle.Italic)
                        "strong", "b" -> SpanStyle(fontWeight = FontWeight.Bold)
                        "sup" -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em)
                        "sub" -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.7.em)
                        "small" -> SpanStyle(fontSize = 0.85.em)
                        else -> null
                    }
                    if (estilo != null) b.pushStyle(estilo)
                    n.childNodes().forEach { nodo(it) }
                    if (estilo != null) b.pop()
                }
            }
        }
        e.childNodes().forEach { nodo(it) }
        // Sin espacios al principio ni al final
        val s = b.toAnnotatedString()
        val ini = s.text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val fin = s.text.indexOfLast { !it.isWhitespace() } + 1
        return if (fin <= ini) AnnotatedString("") else s.subSequence(ini, fin)
    }
}

/**
 * Una posición en EPUB CFI, la del lector del móvil: `epubcfi(/6/14[cap1]!/4/2/10/1:15)`. [itemref]: el paso del capítulo en el
 * spine; [pasos]: los del XHTML (pares = elementos; impares = texto).
 */
class Cfi(val spine: Int, val itemref: Int, val idref: String, val pasos: List<Int>) {
    override fun toString(): String = "epubcfi(/$spine/$itemref" + (if (idref.isNotBlank()) "[$idref]" else "") + "!/" + pasos.joinToString("/") + "/1:0)"

    companion object {
        private val PASO = Regex("/(\\d+)(\\[([^\\]]*)])?(:\\d+)?")

        fun lee(s: String): Cfi? {
            val t = s.trim().removePrefix("epubcfi(").removeSuffix(")")
            if (!t.contains('!')) return null
            // Un rango «a,b,c»: vale el principio (el sitio común y el primer trozo)
            val (padre, resto) = t.split(',', limit = 3).let { p -> p[0] to (p.getOrNull(1) ?: "") }
            val (antes, despues) = padre.split('!', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val arriba = PASO.findAll(antes).toList()
            if (arriba.size < 2) return null
            val abajo = (PASO.findAll(despues) + PASO.findAll(resto)).map { it.groupValues[1].toInt() }.toList()
            return Cfi(arriba[arriba.size - 2].groupValues[1].toInt(), arriba.last().groupValues[1].toInt(), arriba.last().groupValues[3], abajo)
        }

        /** La posición de un bloque de un libro. */
        fun de(epub: Epub, b: Bloque): String {
            val s = epub.secciones[b.seccion]
            return Cfi(epub.pasoSpine, s.pasoCfi, s.idref, b.camino).toString()
        }
    }

    /** El bloque de [epub] que corresponde a esta posición (el más hondo que la contiene), o null si no es de este libro. */
    fun bloque(epub: Epub): Pair<Int, Int>? {
        val sec = epub.secciones.indexOfFirst { (idref.isNotBlank() && it.idref == idref) || (idref.isBlank() && it.pasoCfi == itemref) }.takeIf { it >= 0 }
            ?: epub.secciones.indexOfFirst { it.pasoCfi == itemref }.takeIf { it >= 0 } ?: return null
        val elem = pasos.takeWhile { it % 2 == 0 }
        val bs = epub.bloques(sec)
        if (bs.isEmpty()) return sec to 0
        // El que tiene el camino más largo que es principio del de la posición; si no, el primero que va después
        var mejor = -1; var largo = -1
        bs.forEachIndexed { i, b -> if (b.camino.size <= elem.size && elem.subList(0, b.camino.size) == b.camino && b.camino.size > largo) { mejor = i; largo = b.camino.size } }
        if (mejor < 0) mejor = bs.indexOfFirst { b -> compara(b.camino, elem) >= 0 }.takeIf { it >= 0 } ?: (bs.size - 1)
        return sec to mejor
    }

    private fun compara(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until minOf(a.size, b.size)) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return a.size.compareTo(b.size)
    }
}
