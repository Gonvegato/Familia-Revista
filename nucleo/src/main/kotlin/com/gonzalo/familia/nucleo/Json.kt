package com.gonzalo.familia.nucleo

/**
 * JSON sin dependencias (el núcleo es Kotlin puro: el mismo código sirve en Android y, más adelante, en el PC).
 *
 * Los valores son los de Kotlin: `null`, `Boolean`, `Long` (los enteros), `Double` (los decimales), `String`,
 * `List<Any?>` y `Map<String, Any?>` (con el orden en que llegan).
 */
object Json {
    class Error(msg: String) : Exception(msg)

    fun lee(texto: String): Any? {
        val p = Lector(texto)
        p.blancos()
        val v = p.valor()
        p.blancos()
        if (p.i != texto.length) throw Error("Sobra texto en la posición ${p.i}")
        return v
    }

    fun escribe(v: Any?): String = StringBuilder().also { escribe(v, it) }.toString()

    fun escribe(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> if (v.isFinite() && v == Math.floor(v) && Math.abs(v) < 1e15) sb.append(v.toLong().toString()) else if (v.isFinite()) sb.append(v.toString()) else sb.append("null")
            is Float -> escribe(v.toDouble(), sb)
            is Number -> sb.append(v.toString())
            is String -> cadena(v, sb)
            is Map<*, *> -> {
                sb.append('{')
                var primero = true
                for ((k, x) in v) {
                    if (!primero) sb.append(',')
                    primero = false
                    cadena(k.toString(), sb); sb.append(':'); escribe(x, sb)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var primero = true
                for (x in v) { if (!primero) sb.append(','); primero = false; escribe(x, sb) }
                sb.append(']')
            }
            is Array<*> -> escribe(v.toList(), sb)
            else -> cadena(v.toString(), sb)
        }
    }

    private fun cadena(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ' || c == ' ' || c == ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Lector(val s: String) {
        var i = 0
        fun blancos() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }
        fun valor(): Any? {
            if (i >= s.length) throw Error("Falta un valor")
            return when (s[i]) {
                '{' -> objeto()
                '[' -> lista()
                '"' -> cadena()
                't' -> palabra("true", true)
                'f' -> palabra("false", false)
                'n' -> palabra("null", null)
                else -> numero()
            }
        }
        private fun palabra(p: String, v: Any?): Any? {
            if (!s.startsWith(p, i)) throw Error("Valor no válido en la posición $i")
            i += p.length
            return v
        }
        private fun objeto(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            blancos()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                blancos()
                if (i >= s.length || s[i] != '"') throw Error("Se esperaba una clave en la posición $i")
                val k = cadena()
                blancos()
                if (i >= s.length || s[i] != ':') throw Error("Se esperaba «:» en la posición $i")
                i++
                blancos()
                m[k] = valor()
                blancos()
                if (i >= s.length) throw Error("Objeto sin cerrar")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw Error("Se esperaba «,» o «}» en la posición $i")
                }
            }
        }
        private fun lista(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            blancos()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                blancos()
                l.add(valor())
                blancos()
                if (i >= s.length) throw Error("Lista sin cerrar")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> throw Error("Se esperaba «,» o «]» en la posición $i")
                }
            }
        }
        fun cadena(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw Error("Texto sin cerrar")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw Error("Escape sin terminar")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw Error("Escape \\u sin terminar")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> throw Error("Escape «\\$e» no válido")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        private fun numero(): Any {
            val ini = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            var decimal = false
            while (i < s.length) {
                val c = s[i]
                if (c in '0'..'9') i++
                else if (c == '.' || c == 'e' || c == 'E' || ((c == '-' || c == '+') && (s[i - 1] == 'e' || s[i - 1] == 'E'))) { decimal = true; i++ }
                else break
            }
            val t = s.substring(ini, i)
            if (t.isEmpty() || t == "-") throw Error("Valor no válido en la posición $ini")
            if (!decimal) t.toLongOrNull()?.let { return it }
            return t.toDoubleOrNull() ?: throw Error("Número no válido «$t»")
        }
    }
}

/** Lecturas cómodas de lo que viene de JSON (un número puede llegar como Long o como Double). */
fun Any?.comoMapa(): Map<String, Any?> = (this as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
fun Any?.comoLista(): List<Any?> = (this as? List<*>)?.toList() ?: (this as? Map<*, *>)?.values?.toList() ?: emptyList()
fun Any?.comoLong(): Long = when (this) { is Long -> this; is Number -> this.toLong(); is String -> this.toLongOrNull() ?: this.toDoubleOrNull()?.toLong() ?: 0L; is Boolean -> if (this) 1L else 0L; else -> 0L }
fun Any?.comoDouble(): Double = when (this) { is Number -> this.toDouble(); is String -> this.toDoubleOrNull() ?: 0.0; else -> 0.0 }
fun Any?.comoTexto(): String = when (this) { null -> ""; is String -> this; is Double -> if (this == Math.floor(this) && Math.abs(this) < 1e15) this.toLong().toString() else this.toString(); else -> this.toString() }
fun Any?.comoBool(): Boolean = when (this) { is Boolean -> this; is Number -> this.toLong() != 0L; is String -> this == "true" || this == "1"; else -> false }
