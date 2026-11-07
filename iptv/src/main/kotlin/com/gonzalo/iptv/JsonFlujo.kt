package com.gonzalo.iptv

import java.io.Reader

/**
 * Un lector de JSON a trozos. Lo que da Xtream de un servidor grande (`get_live_streams`, `get_vod_streams`: decenas de miles de cosas, a
 * veces más de 50 MB) no cabe entero en la memoria de un móvil o de una tele como mapas y listas: se lee la lista elemento a elemento,
 * cada uno (un objeto pequeño) se convierte en lo que es y se suelta. Kotlin puro, sin bibliotecas (igual en el móvil y en el ordenador).
 *
 * Lo que da [valor]: `Map<String, Any?>`, `List<Any?>`, `String`, `Long`, `Double`, `Boolean` o `null`.
 */
class JsonFlujo(private val r: Reader) {
    private val buf = CharArray(32 * 1024)
    private var n = 0
    private var i = 0
    private var mirado: Int = -2

    class Error(msg: String) : IptvError(msg)

    private fun lee(): Int {
        if (mirado != -2) { val c = mirado; mirado = -2; return c }
        if (i >= n) { n = r.read(buf); i = 0; if (n <= 0) { n = 0; return -1 } }
        return buf[i++].code
    }
    private fun mira(): Int { if (mirado == -2) mirado = lee(); return mirado }
    private fun blancos() { while (true) { val c = mira(); if (c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code || c == 0xFEFF) lee() else return } }

    /** Qué viene ahora: '{', '[', '"', 'n' (null), 't'/'f', '0' (un número) o 'E' (se acabó). */
    fun tipo(): Char {
        blancos()
        return when (val c = mira()) { -1 -> 'E'; '{'.code, '['.code, '"'.code -> c.toChar(); 'n'.code -> 'n'; 't'.code, 'f'.code -> 't'; else -> '0' }
    }

    /** Entra en una lista (`[`). Luego, [hayMas] antes de cada elemento. */
    fun empiezaLista() { blancos(); espera('[') ; primero = true }
    private var primero = false

    /** ¿Queda otro elemento en la lista? (se come la coma y el `]` del final). */
    fun hayMas(): Boolean {
        blancos()
        if (mira() == ']'.code) { lee(); return false }
        if (!primero) { espera(','); blancos() }
        primero = false
        if (mira() == ']'.code) { lee(); return false }
        return true
    }

    private fun espera(ch: Char) { val c = lee(); if (c != ch.code) throw Error("Respuesta rara del servidor (no es el JSON que se esperaba).") }

    /** Lee el valor que viene, entero (para cosas pequeñas: un canal, una ficha). */
    fun valor(): Any? {
        blancos()
        return when (mira()) {
            '{'.code -> { lee(); objeto() }
            '['.code -> { lee(); lista() }
            '"'.code -> { lee(); cadena() }
            't'.code -> { palabra("true"); true }
            'f'.code -> { palabra("false"); false }
            'n'.code -> { palabra("null"); null }
            -1 -> throw Error("La respuesta del servidor se ha cortado.")
            else -> numero()
        }
    }

    /** Se salta el valor que viene sin guardarlo. */
    fun salta() { valor() }

    private fun palabra(p: String) { for (ch in p) if (lee() != ch.code) throw Error("Respuesta rara del servidor (no es JSON).") }

    private fun objeto(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        blancos()
        if (mira() == '}'.code) { lee(); return m }
        while (true) {
            blancos(); espera('"')
            val k = cadena()
            blancos(); espera(':')
            m[k] = valor()
            blancos()
            when (lee()) { ','.code -> continue; '}'.code -> return m; else -> throw Error("Respuesta rara del servidor (un objeto JSON mal cerrado).") }
        }
    }

    private fun lista(): List<Any?> {
        val l = ArrayList<Any?>()
        blancos()
        if (mira() == ']'.code) { lee(); return l }
        while (true) {
            l += valor()
            blancos()
            when (lee()) { ','.code -> continue; ']'.code -> return l; else -> throw Error("Respuesta rara del servidor (una lista JSON mal cerrada).") }
        }
    }

    private val sb = StringBuilder()
    private fun cadena(): String {
        sb.setLength(0)
        while (true) {
            val c = lee()
            when (c) {
                -1 -> throw Error("La respuesta del servidor se ha cortado.")
                '"'.code -> return sb.toString()
                '\\'.code -> when (val e = lee()) {
                    'n'.code -> sb.append('\n'); 't'.code -> sb.append('\t'); 'r'.code -> sb.append('\r'); 'b'.code -> sb.append('\b'); 'f'.code -> sb.append('\u000C')
                    'u'.code -> { var h = 0; repeat(4) { h = h * 16 + Character.digit(lee(), 16).coerceAtLeast(0) }; sb.append(h.toChar()) }
                    else -> sb.append(e.toChar())
                }
                else -> sb.append(c.toChar())
            }
        }
    }

    private fun numero(): Any {
        sb.setLength(0)
        while (true) {
            val c = mira()
            if (c == -1 || !(c.toChar().isDigit() || c == '-'.code || c == '+'.code || c == '.'.code || c == 'e'.code || c == 'E'.code)) break
            sb.append(lee().toChar())
        }
        val s = sb.toString()
        if (s.isEmpty()) throw Error("Respuesta rara del servidor (no es JSON).")
        return s.toLongOrNull() ?: s.toDoubleOrNull() ?: throw Error("Respuesta rara del servidor (un número que no lo es).")
    }

    companion object {
        /** Todo el texto de una vez (para lo pequeño). */
        fun lee(texto: String): Any? = JsonFlujo(texto.reader()).valor()

        /** Escribe un valor como JSON (mapas, listas, textos, números, sí/no y null). */
        fun escribe(v: Any?): String = StringBuilder().also { escribe(v, it) }.toString()

        private fun escribe(v: Any?, sb: StringBuilder) {
            when (v) {
                null -> sb.append("null")
                is String -> cadena(v, sb)
                is Boolean -> sb.append(v)
                is Int, is Long, is Short, is Byte -> sb.append(v)
                is Number -> { val d = v.toDouble(); if (d == Math.floor(d) && Math.abs(d) < 1e15) sb.append(d.toLong()) else sb.append(d) }
                is Map<*, *> -> { sb.append('{'); var p = true; v.forEach { (k, x) -> if (!p) sb.append(','); p = false; cadena(k.toString(), sb); sb.append(':'); escribe(x, sb) }; sb.append('}') }
                is Iterable<*> -> { sb.append('['); var p = true; v.forEach { if (!p) sb.append(','); p = false; escribe(it, sb) }; sb.append(']') }
                else -> cadena(v.toString(), sb)
            }
        }

        private fun cadena(s: String, sb: StringBuilder) {
            sb.append('"')
            for (c in s) when {
                c == '"' -> sb.append("\\\""); c == '\\' -> sb.append("\\\\"); c == '\n' -> sb.append("\\n"); c == '\r' -> sb.append("\\r"); c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
            sb.append('"')
        }
    }
}

// Lo que dan los servidores de Xtream viene a veces como texto y a veces como número (el mismo campo, según el servidor): se lee de las dos
internal fun Any?.txt(): String = when (this) { null -> ""; is String -> this; is Double -> if (this == Math.floor(this) && Math.abs(this) < 1e15) this.toLong().toString() else this.toString(); is Map<*, *>, is List<*> -> ""; else -> this.toString() }
internal fun Any?.largo(): Long = when (this) { is Long -> this; is Number -> this.toLong(); is String -> this.trim().toLongOrNull() ?: this.trim().toDoubleOrNull()?.toLong() ?: 0L; is Boolean -> if (this) 1L else 0L; else -> 0L }
internal fun Any?.decimal(): Double = when (this) { is Number -> this.toDouble(); is String -> this.trim().replace(',', '.').toDoubleOrNull() ?: 0.0; else -> 0.0 }
internal fun Any?.mapa(): Map<String, Any?> = (this as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
internal fun Any?.lista(): List<Any?> = (this as? List<*>)?.toList() ?: (this as? Map<*, *>)?.values?.toList() ?: emptyList()
