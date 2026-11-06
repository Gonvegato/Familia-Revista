package com.gonzalo.marfil.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/*
 * Qué significa una palabra: el Wikcionario en español, como el diccionario del lector del móvil (Diccionario.kt de Marfil de Android:
 * las mismas reglas para sacar las acepciones, ir de «faldas» a «falda» y de «acordarme» a «acordar»). El idioma es el del libro; la
 * traducción del móvil (ML Kit) no está en el ordenador.
 */
object Diccionario {
    private const val UA = "Marfil/1 (lector de libros personal)"

    private fun extract(title: String): String? {
        val u = "https://es.wiktionary.org/w/api.php?action=query&prop=extracts&explaintext=1&format=json&redirects=1&titles=" + URLEncoder.encode(title, "UTF-8")
        fun pide() = (URL(u).openConnection() as HttpURLConnection).apply { setRequestProperty("User-Agent", UA); connectTimeout = 12000; readTimeout = 12000 }
        var c = pide()
        // Si Wikcionario pide ir más despacio (429), se espera un momento y se reintenta una vez
        if (c.responseCode == 429) { Thread.sleep(((c.getHeaderField("Retry-After")?.toLongOrNull() ?: 2L).coerceIn(1L, 4L)) * 1000); c = pide() }
        if (c.responseCode !in 200..299) error("Wikcionario respondió ${c.responseCode}")
        val pages = Json.lee(c.inputStream.bufferedReader().readText()).comoMapa()["query"].comoMapa()["pages"].comoMapa()
        val p = pages.values.firstOrNull().comoMapa()
        return if (p.containsKey("missing")) null else p["extract"].comoTexto().ifBlank { null }
    }

    /** Las acepciones de la sección del idioma (por defecto, «Español»): las líneas que empiezan por número. */
    fun parse(ex: String, langName: String = "Español"): List<String> {
        val all = parseAll(ex, langName)
        val real = all.filter { lemmaDe(it) == null }
        return (real.ifEmpty { all }).take(4)
    }

    private fun parseAll(ex: String, langName: String): List<String> {
        val lines = ex.lines().map { it.replace(' ', ' ').replace("¦", "").trim() }
        val head = Regex("^(=+)\\s*(.+?)\\s*=+$")
        var i = lines.indexOfFirst { head.matchEntire(it)?.let { m -> m.groupValues[1].length == 2 && m.groupValues[2].startsWith(langName, true) } == true }
        if (i < 0) i = lines.indexOfFirst { head.matchEntire(it)?.groupValues?.get(1)?.length == 2 }   // la primera lengua que haya
        if (i < 0) return emptyList()
        val out = ArrayList<String>()
        var j = i + 1
        val skip = Regex("^(Sinónimos?|Antónimos?|Hiperónimos?|Hipónimos?|Derivados?|Relacionados?|Véase|Ejemplos?|Uso|Ámbito|Variantes?|Información|Traducciones|Locuciones|Refranes|Parónimos?|Compuestos?|Abreviaturas?)\\b.*")
        while (j < lines.size && out.size < 14) {
            val l = lines[j]
            val m = head.matchEntire(l)
            if (m != null && m.groupValues[1].length == 2) break           // empieza otra lengua
            val num = Regex("^(\\d+)\\s*(.*)$").matchEntire(l)
            if (num != null && m == null) {
                var rest = num.groupValues[2].trim()
                // A veces el número va solo (o con una etiqueta de ámbito) y la definición en la línea siguiente
                if (rest.length < 25 && j + 1 < lines.size) {
                    val next = lines[j + 1]
                    if (next.isNotBlank() && head.matchEntire(next) == null && !Regex("^\\d+\\b.*").matches(next) && !skip.matches(next)) {
                        rest = if (rest.isBlank()) next else "($rest) $next"
                        j++
                    }
                }
                if (rest.length >= 3 && !skip.matches(rest)) out += rest
            }
            j++
        }
        return out
    }

    private val FORMA = Regex(
        "^(?:Forma\\s+(?:flexiva\\s+|verbal\\s+|sustantiva\\s+|adjetiva\\s+|conjugada\\s+)?(?:(?:del|de la)\\s+)?(?:plural|singular|femenin\\w*|masculin\\w*|diminutiv\\w*|aumentativ\\w*|superlativ\\w*|flexiva|verbal|sustantiva|adjetiva|conjugada|enclítica|apocopada)" +
            "|Plural|Singular|Femenino|Masculino|Superlativo|Diminutivo|Aumentativo|Participio|Gerundio|Infinitivo|Imperativo|(?:Primera|Segunda|Tercera)\\s+persona)" +
            "\\b.{0,120}?\\bde(?:l)?\\s+(?:verbo\\s+|la\\s+|lo\\s+)?([\\p{L}]+)\\.?$"
    )
    private val VERBAL = Regex("^(?:Primera|Segunda|Tercera|Participio|Gerundio|Infinitivo|Imperativo|Forma verbal)\\b.*")

    /** Si la acepción solo dice «forma del plural de falda», la palabra de la que es forma (falda); si no, null. */
    fun lemmaDe(def: String): String? = FORMA.matchEntire(def.trim().replace(Regex("\\s*\\(.*?\\)\\s*$"), ""))?.groupValues?.get(1)?.takeIf { it.length > 1 }

    private fun plain(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Las palabras que se prueban, por orden: tal cual, en minúscula, sin pronombres pegados (acordarme → acordar) y en singular. */
    fun candidates(word: String): List<String> {
        val w = word.trim()
        val low = w.lowercase(Locale.forLanguageTag("es"))
        val out = linkedSetOf(low, w)
        val cl = Regex("^(.+?)((?:me|te|se|nos|os|lo|la|le|los|las|les){1,2})$").matchEntire(low)
        if (cl != null) {
            val stem = cl.groupValues[1]
            if (Regex(".*(ar|er|ir|ando|iendo|yendo)$").matches(plain(stem))) { out += plain(stem); out += stem }
        }
        if (low.length > 4 && low.endsWith("es")) out += low.dropLast(2)
        if (low.length > 3 && low.endsWith("s")) out += low.dropLast(1)
        return out.toList()
    }

    /** Lo que se enseña: la palabra con la que se ha encontrado, sus acepciones y, si era una forma de otra, cuál. */
    data class Definicion(val palabra: String, val defs: List<String>, val nota: String = "")

    /** Las acepciones de una palabra (si es solo una forma de otra, las de la palabra de verdad, con la nota de qué forma es). Bloquea (red). */
    fun define(word: String, langName: String = "Español", prof: Int = 0): Definicion {
        for (t in candidates(word).take(if (prof == 0) 5 else 2)) {
            val ex = extract(t) ?: continue
            val all = parseAll(ex, langName)
            if (all.isEmpty()) continue
            val real = all.filter { lemmaDe(it) == null }
            if (real.isNotEmpty()) return Definicion(t, real.take(4))
            if (prof < 2) {
                val formas = all.sortedBy { if (VERBAL.matches(it)) 1 else 0 }
                val lemas = formas.mapNotNull { lemmaDe(it) }.distinct().filter { !it.equals(t, true) }.take(2)
                val sacados = lemas.mapNotNull { l -> runCatching { define(l, langName, prof + 1) }.getOrNull()?.takeIf { it.defs.isNotEmpty() } }
                if (sacados.isNotEmpty()) {
                    val defs = if (sacados.size == 1) sacados[0].defs else sacados.flatMap { d -> d.defs.take(3).map { "(${d.palabra}) $it" } }
                    val nota = formas.firstOrNull { lemmaDe(it)?.equals(lemas.first(), true) == true }.orEmpty()
                    return Definicion(sacados[0].palabra, defs, nota)
                }
            }
            return Definicion(t, all.take(4))
        }
        return Definicion(word, emptyList())
    }

    /** El nombre de un idioma como lo pone el Wikcionario («Español», «Inglés»…), por su código (el del libro). */
    fun lengua(codigo: String): String = codigo.substringBefore('-').trim().takeIf { it.isNotBlank() }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.forLanguageTag("es-ES")).replaceFirstChar { c -> c.uppercase() } }?.takeIf { it.isNotBlank() } ?: "Español"
}
