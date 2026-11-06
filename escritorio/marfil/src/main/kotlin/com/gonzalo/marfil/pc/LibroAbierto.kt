package com.gonzalo.marfil.pc

import java.text.Normalizer

/** Un sitio del libro: la sección (del «spine», desde 0), el bloque dentro de ella y la letra del bloque. */
data class Sitio(val seccion: Int, val bloque: Int, val letra: Int = 0) : Comparable<Sitio> {
    override fun compareTo(other: Sitio) = compareValuesBy(this, other, { it.seccion }, { it.bloque }, { it.letra })
}

/** Lo encontrado al buscar: dónde, y el trozo de texto de alrededor. */
class Hallazgo(val desde: Sitio, val hasta: Sitio, val antes: String, val frase: String, val despues: String, val capitulo: String)

/**
 * Lo que se sabe de un libro abierto, sin pantalla (se prueba): por dónde vas en porcentaje (con las mismas cuentas que foliate en el móvil:
 * por el tamaño de cada capítulo, sin los que no se leen seguido), cuánto te queda (a tu ritmo), en qué capítulo estás, adónde lleva cada
 * entrada del índice y buscar en el libro.
 */
class LibroAbierto(val epub: Epub) {
    val secciones get() = epub.secciones
    fun bloques(s: Int): List<Bloque> = epub.bloques(s)

    /** Las secciones que tienen algo que leer (las que se saltan en orden: las vacías). */
    val conTexto: List<Int> by lazy { secciones.indices.filter { bloques(it).isNotEmpty() } }

    private val antes: LongArray by lazy { LongArray(secciones.size + 1).also { a -> for (i in secciones.indices) a[i + 1] = a[i] + epub.tamanos[i] } }

    /** El porcentaje del libro (0–1) a una fracción [enSeccion] de la sección [s] (foliate: `SectionProgress`). */
    fun fraccion(s: Int, enSeccion: Double): Double {
        if (s !in secciones.indices) return 0.0
        return ((antes[s] + enSeccion.coerceIn(0.0, 1.0) * epub.tamanos[s]) / epub.total.toDouble()).coerceIn(0.0, 1.0)
    }

    /** Lo contrario: la sección y la fracción dentro de ella de un porcentaje del libro (foliate: `getSection`). */
    fun deFraccion(fr: Double): Pair<Int, Double> {
        if (secciones.isEmpty()) return 0 to 0.0
        if (fr <= 0) return 0 to 0.0
        if (fr >= 1) return secciones.lastIndex to 1.0
        val x = fr * epub.total
        var i = (0 until secciones.size).firstOrNull { antes[it + 1] > x } ?: secciones.lastIndex
        while (i < secciones.lastIndex && epub.tamanos[i] == 0L) i++
        val t = epub.tamanos[i].coerceAtLeast(1)
        return i to ((x - antes[i]) / t).coerceIn(0.0, 1.0)
    }

    /** Lo que se ha leído de una sección hasta un sitio, por letras (sin paginar: desplazamiento continuo, estimaciones). */
    fun fraccionEnSeccion(x: Sitio): Double {
        val bs = bloques(x.seccion)
        val total = bs.sumOf { maxOf(it.largo, 1) }.coerceAtLeast(1)
        val hasta = bs.take(x.bloque.coerceIn(0, bs.size)).sumOf { maxOf(it.largo, 1) } + x.letra.coerceAtMost(bs.getOrNull(x.bloque)?.largo ?: 0)
        return (hasta.toDouble() / total).coerceIn(0.0, 1.0)
    }

    /** El sitio de una sección a una fracción suya, por letras. */
    fun sitioEnSeccion(s: Int, f: Double): Sitio {
        val bs = bloques(s)
        if (bs.isEmpty()) return Sitio(s, 0)
        val total = bs.sumOf { maxOf(it.largo, 1) }
        var resto = (f.coerceIn(0.0, 1.0) * total).toInt()
        for ((i, b) in bs.withIndex()) { val l = maxOf(b.largo, 1); if (resto < l) return Sitio(s, i, resto.coerceAtMost(b.largo)); resto -= l }
        return Sitio(s, bs.lastIndex, bs.last().largo)
    }

    private val destinos = java.util.concurrent.ConcurrentHashMap<String, Sitio>()

    /** El bloque al que lleva una entrada del índice. */
    fun destino(en: Entrada): Sitio {
        if (en.ancla.isBlank()) return Sitio(en.seccion, 0)
        return destinos.getOrPut("${en.seccion}#${en.ancla}") {
            val i = bloques(en.seccion).indexOfFirst { en.ancla in it.ids }
            Sitio(en.seccion, if (i >= 0) i else 0)
        }
    }

    /** Un enlace interno: su sitio. */
    fun destino(en: Epub.Enlace): Sitio = destino(Entrada("", 0, en.seccion, en.ancla))

    /** El capítulo en el que estás: la última entrada del índice que empieza antes (como `TOCProgress` de foliate). */
    fun capitulo(x: Sitio): Entrada? = epub.indice.lastOrNull { en -> destino(en) <= x }

    fun etiqueta(x: Sitio): String = capitulo(x)?.titulo.orEmpty()

    /** Las marcas de capítulo de la barra de lo leído: el porcentaje donde empieza cada capítulo de primer nivel y su nombre. */
    val marcasCapitulos: List<Pair<Double, String>> by lazy {
        val nivel = epub.indice.minOfOrNull { it.nivel } ?: 0
        epub.indice.filter { it.nivel == nivel }.map { en -> val d = destino(en); fraccion(d.seccion, if (d.bloque == 0) 0.0 else fraccionEnSeccion(d)) to en.titulo }
            .distinctBy { Math.round(it.first * 1000) }
    }

    /**
     * Los minutos que te quedan del capítulo y del libro desde una fracción [enSeccion] de la sección [s]: lo que queda por leer (en bytes,
     * como el móvil) entre tu velocidad (bytes por minuto; sin saberla, la de foliate: 1600).
     */
    fun quedan(s: Int, enSeccion: Double, velocidad: Int): Pair<Double, Double> {
        if (s !in secciones.indices) return 0.0 to 0.0
        val v = (if (velocidad > 0) velocidad else 1600).toDouble()
        val seccion = (1 - enSeccion.coerceIn(0.0, 1.0)) * epub.tamanos[s]
        val leido = antes[s] + enSeccion.coerceIn(0.0, 1.0) * epub.tamanos[s]
        return seccion / v to (epub.total - leido).coerceAtLeast(0.0) / v
    }

    // ---------------------------------------------------------------- buscar

    /** Busca en todo el libro (sin mirar mayúsculas ni tildes), como mucho [max] veces. */
    fun busca(q: String, max: Int = 300, sigue: () -> Boolean = { true }): List<Hallazgo> {
        val (nq, _) = pliega(q.trim())
        if (nq.isEmpty()) return emptyList()
        val out = ArrayList<Hallazgo>()
        for (s in secciones.indices) {
            if (!sigue()) break
            bloques(s).forEachIndexed { bi, b ->
                if (out.size >= max || b.largo == 0) return@forEachIndexed
                val t = b.texto.text
                val (ft, mapa) = pliega(t)
                var at = ft.indexOf(nq)
                while (at >= 0 && out.size < max) {
                    val ini = mapa[at]; val fin = if (at + nq.length < mapa.size) mapa[at + nq.length] else t.length
                    val desde = Sitio(s, bi, ini)
                    out += Hallazgo(desde, Sitio(s, bi, fin), t.substring(maxOf(0, ini - 40), ini).replace('\n', ' '), t.substring(ini, fin), t.substring(fin, minOf(t.length, fin + 60)).replace('\n', ' '), etiqueta(desde))
                    at = ft.indexOf(nq, at + maxOf(1, nq.length))
                }
            }
        }
        return out
    }

    companion object {
        /** El texto sin tildes y en minúsculas, con el sitio de cada letra en el original (para marcar lo encontrado). */
        fun pliega(t: String): Pair<String, IntArray> {
            val sb = StringBuilder(); val mapa = ArrayList<Int>(t.length + 1)
            for ((i, c) in t.withIndex()) {
                val p = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
                for (x in p) { sb.append(x); mapa += i }
            }
            mapa += t.length
            return sb.toString() to mapa.toIntArray()
        }

        /** «menos de 1 min», «12 min», «2 h 5 min» (como el lector del móvil). */
        fun duracion(min: Double): String = when {
            !min.isFinite() || min < 0 -> ""
            min < 1 -> "menos de 1 min"
            min < 60 -> "${Math.round(min)} min"
            else -> { val t = Math.round(min); val h = t / 60; val m = t % 60; "$h h" + if (m > 0) " $m min" else "" }
        }
    }
}
