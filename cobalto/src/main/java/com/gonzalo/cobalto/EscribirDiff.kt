package com.gonzalo.cobalto

/** Una parte de la comparación de dos textos: igual en los dos, o distinta (lo de cada lado). */
sealed class Parte {
    class Igual(val parrafos: List<String>) : Parte()
    class Cambio(val aqui: List<String>, val otro: List<String>) : Parte()
}

/** Un trozo de la comparación palabra a palabra: igual en los dos, solo en el de aquí (se quitó) o solo en el otro (se añadió). */
class TrozoDiff(val tipo: Int, val texto: String) {
    companion object { const val IGUAL = 0; const val AQUI = 1; const val OTRO = 2 }
}

/** Comparar dos versiones de un texto párrafo a párrafo, y mezclarlas eligiendo de cada diferencia. */
object EscribirDiff {
    fun parrafos(t: String): List<String> = t.replace("\r\n", "\n").split(Regex("\n\\s*\n|\n")).map { it.trim() }.filter { it.isNotEmpty() }

    /** Las diferencias entre [aqui] y [otro] (subsucesión común más larga de párrafos). */
    fun compara(aqui: String, otro: String): List<Parte> {
        val a = parrafos(aqui); val b = parrafos(otro)
        val n = a.size; val m = b.size
        val l = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) l[i][j] = if (a[i] == b[j]) l[i + 1][j + 1] + 1 else maxOf(l[i + 1][j], l[i][j + 1])
        val out = ArrayList<Parte>()
        var i = 0; var j = 0
        val igual = ArrayList<String>(); val xa = ArrayList<String>(); val xb = ArrayList<String>()
        fun cierraIgual() { if (igual.isNotEmpty()) { out += Parte.Igual(ArrayList(igual)); igual.clear() } }
        fun cierraCambio() { if (xa.isNotEmpty() || xb.isNotEmpty()) { out += Parte.Cambio(ArrayList(xa), ArrayList(xb)); xa.clear(); xb.clear() } }
        while (i < n || j < m) {
            when {
                i < n && j < m && a[i] == b[j] -> { cierraCambio(); igual += a[i]; i++; j++ }
                j < m && (i == n || l[i][j + 1] >= l[i + 1][j]) -> { cierraIgual(); xb += b[j]; j++ }
                else -> { cierraIgual(); xa += a[i]; i++ }
            }
        }
        cierraIgual(); cierraCambio()
        return out
    }

    private val PIEZA = Regex("\\s+|[\\p{L}\\p{N}]+|[^\\s\\p{L}\\p{N}]")

    /**
     * Las diferencias entre dos trozos de texto palabra a palabra (los espacios y los signos cuentan como piezas): para
     * enseñar, dentro de un párrafo cambiado, qué palabras se quitaron y cuáles se añadieron. Los trozos seguidos del mismo
     * tipo van juntos; unidos los de [TrozoDiff.IGUAL] y [TrozoDiff.AQUI] dan [aqui], y los de [TrozoDiff.IGUAL] y
     * [TrozoDiff.OTRO] dan [otro].
     */
    fun porPalabras(aqui: String, otro: String): List<TrozoDiff> {
        val a = PIEZA.findAll(aqui).map { it.value }.toList()
        val b = PIEZA.findAll(otro).map { it.value }.toList()
        val out = ArrayList<TrozoDiff>()
        fun pon(tipo: Int, x: String) {
            if (x.isEmpty()) return
            val u = out.lastOrNull()
            if (u != null && u.tipo == tipo) out[out.size - 1] = TrozoDiff(tipo, u.texto + x) else out += TrozoDiff(tipo, x)
        }
        var i = 0; var j = 0
        for ((x, y) in EscribirMezcla.comunes(a, b)) {
            while (i < x) pon(TrozoDiff.AQUI, a[i++])
            while (j < y) pon(TrozoDiff.OTRO, b[j++])
            pon(TrozoDiff.IGUAL, a[i]); i++; j++
        }
        while (i < a.size) pon(TrozoDiff.AQUI, a[i++])
        while (j < b.size) pon(TrozoDiff.OTRO, b[j++])
        return out
    }

    /** El texto mezclado: lo igual, y de cada diferencia lo de [aqui] o lo de [otro] según [usaOtro] (por orden de diferencia). */
    fun mezcla(partes: List<Parte>, usaOtro: List<Boolean>): String {
        var k = 0
        val ps = ArrayList<String>()
        for (p in partes) when (p) {
            is Parte.Igual -> ps += p.parrafos
            is Parte.Cambio -> { ps += if (usaOtro.getOrElse(k) { false }) p.otro else p.aqui; k++ }
        }
        return ps.joinToString("\n\n")
    }
}
