package com.gonzalo.hiedra.pc

/**
 * El formato de texto KeyValues de Valve (`.vdf`, `.acf`): claves y valores entre comillas y bloques entre llaves. Devuelve mapas anidados
 * (los valores son textos o más mapas); [de] busca sin mirar mayúsculas («AppState», «appstate»…), como hace el propio Steam.
 */
object Vdf {
    fun lee(texto: String): Map<String, Any?> {
        var i = 0
        val n = texto.length
        fun salta() {
            while (i < n) {
                val c = texto[i]
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') { i++; continue }
                if (c == '/' && i + 1 < n && texto[i + 1] == '/') { while (i < n && texto[i] != '\n') i++; continue }
                break
            }
        }
        // Un trozo: «{», «}» o un texto (entre comillas, con sus escapes, o suelto hasta el siguiente blanco)
        fun trozo(): String? {
            salta()
            if (i >= n) return null
            val c = texto[i]
            if (c == '{' || c == '}') { i++; return c.toString() }
            val sb = StringBuilder()
            if (c == '"') {
                i++
                while (i < n && texto[i] != '"') {
                    if (texto[i] == '\\' && i + 1 < n) {
                        val e = texto[i + 1]
                        sb.append(when (e) { 'n' -> '\n'; 't' -> '\t'; else -> e }); i += 2
                    } else sb.append(texto[i++])
                }
                i++
                return "\u0000" + sb   // (marca de texto: así un texto «{» no se confunde con una llave)
            }
            while (i < n && !texto[i].isWhitespace() && texto[i] != '{' && texto[i] != '}' && texto[i] != '"') sb.append(texto[i++])
            return "\u0000" + sb
        }
        fun bloque(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            while (true) {
                val k = trozo() ?: return m
                if (k == "}") return m
                if (k == "{") continue
                val v = trozo() ?: return m
                when (v) {
                    "{" -> m[k.substring(1)] = bloque()
                    "}" -> return m
                    else -> m[k.substring(1)] = v.substring(1)
                }
                // Las condiciones de plataforma («[$WIN32]») no se miran
                salta()
                if (i < n && texto[i] == '[') { while (i < n && texto[i] != ']') i++; i++ }
            }
        }
        return bloque()
    }

    /** El valor de una ruta de claves, sin mirar mayúsculas. */
    fun de(m: Any?, vararg claves: String): Any? {
        var cur = m
        for (k in claves) {
            val mapa = cur as? Map<*, *> ?: return null
            cur = mapa.entries.firstOrNull { (it.key as? String).equals(k, ignoreCase = true) }?.value
        }
        return cur
    }

    fun texto(m: Any?, vararg claves: String): String = de(m, *claves) as? String ?: ""
}
