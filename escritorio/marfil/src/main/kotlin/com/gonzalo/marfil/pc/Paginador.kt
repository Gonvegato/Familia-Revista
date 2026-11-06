package com.gonzalo.marfil.pc

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize

/** Cómo se pinta un bloque: su estilo de texto, lo que se mete a cada lado (px) y el aire de encima (px). */
class EstiloBloque(val estilo: TextStyle, val izquierda: Float = 0f, val derecha: Float = 0f, val antes: Float = 0f)

/**
 * Un trozo de un bloque en una página: las líneas [desde, hasta) de su texto (o la imagen entera), dónde va en la página (px) y su
 * maquetación ([capa]: la del bloque entero; de ella salen las líneas, las letras bajo el ratón y los tramos que se pintan).
 */
class Trozo(val bloque: Int, val desde: Int, val hasta: Int, val x: Float, val y: Float, val ancho: Float, val alto: Float, val capa: TextLayoutResult?) {
    /** La primera letra del bloque que se ve en este trozo y la que va justo después de la última. */
    val letraDesde: Int get() = capa?.getLineStart(desde) ?: 0
    val letraHasta: Int get() = capa?.let { if (hasta >= it.lineCount) Int.MAX_VALUE else it.getLineStart(hasta) } ?: Int.MAX_VALUE
    /** Lo que hay que subir la capa para que su línea [desde] quede arriba del trozo. */
    val subida: Float get() = capa?.getLineTop(desde) ?: 0f
}

class Pagina(val trozos: List<Trozo>)

/** Un capítulo en páginas, para un tamaño de página y unos ajustes. */
class Paginado(val seccion: Int, val paginas: List<Pagina>, val ancho: Int, val alto: Int) {
    val total: Int get() = paginas.size
    /** Para qué letra, tamaño y ventana se hizo. */
    var clave: String = ""

    /** La página en la que está la letra [letra] del bloque [bloque] (o la primera que va después). */
    fun paginaDe(bloque: Int, letra: Int): Int {
        for ((i, p) in paginas.withIndex()) for (t in p.trozos) {
            if (t.bloque > bloque) return i
            if (t.bloque == bloque && (t.capa == null || letra < t.letraHasta)) return i
        }
        return (paginas.size - 1).coerceAtLeast(0)
    }

    /** El principio de una página: (bloque, letra). */
    fun inicio(p: Int): Pair<Int, Int>? = paginas.getOrNull(p)?.trozos?.firstOrNull()?.let { it.bloque to it.letraDesde }
}

/**
 * Pasa un capítulo a páginas: cada bloque se mide de verdad (con la letra, el tamaño y el ancho de la página) y, si no cabe entero, se parte
 * por una línea. Como en un libro: al menos dos líneas a cada lado de un corte (sin viudas ni huérfanas), un título nunca se queda solo al
 * pie de una página (se lleva con él el principio de lo que sigue) y una imagen nunca se parte (se encoge para caber).
 */
class Paginador(private val medidor: TextMeasurer, private val estilo: (Bloque, Bloque?) -> EstiloBloque, private val tamImagen: (String) -> IntSize?, private val densidad: Float = 1f) {
    private fun mide(b: Bloque, eb: EstiloBloque, ancho: Int): TextLayoutResult =
        medidor.measure(b.texto, eb.estilo, constraints = Constraints.fixedWidth((ancho - eb.izquierda - eb.derecha).toInt().coerceAtLeast(40)))

    fun pagina(seccion: Int, bloques: List<Bloque>, ancho: Int, alto: Int): Paginado {
        val paginas = ArrayList<Pagina>()
        var actual = ArrayList<Trozo>()
        var y = 0f
        fun nueva() { if (actual.isNotEmpty()) paginas += Pagina(actual); actual = ArrayList(); y = 0f }
        val capas = HashMap<Int, TextLayoutResult>()
        fun capa(k: Int): TextLayoutResult? {
            val b = bloques.getOrNull(k) ?: return null
            if (b.tipo == Bloque.Tipo.IMAGEN) return null
            return capas.getOrPut(k) { mide(b, estilo(b, bloques.getOrNull(k - 1)), ancho) }
        }
        for ((k, b) in bloques.withIndex()) {
            val eb = estilo(b, bloques.getOrNull(k - 1))
            if (b.tipo == Bloque.Tipo.IMAGEN) {
                val tam = tamImagen(b.imagen) ?: continue
                if (tam.width <= 0 || tam.height <= 0) continue
                var w = minOf(ancho.toFloat(), tam.width * densidad); var h = w * tam.height / tam.width
                val maxH = alto * 0.94f
                if (h > maxH) { h = maxH; w = h * tam.width / tam.height }
                val a = if (actual.isEmpty()) 0f else eb.antes
                if (y + a + h > alto && actual.isNotEmpty()) nueva()
                val a2 = if (actual.isEmpty()) 0f else eb.antes
                actual += Trozo(k, 0, 0, (ancho - w) / 2, y + a2, w, h, null)
                y += a2 + h
                continue
            }
            val c = capa(k) ?: continue
            val n = c.lineCount
            if (n == 0) continue
            // Un título se lleva el principio de lo que sigue (dos líneas): si no caben juntos, a la página siguiente
            if (b.esTitulo && actual.isNotEmpty()) {
                val sig = capa(k + 1)
                val ebSig = bloques.getOrNull(k + 1)?.let { estilo(it, b) }
                val conSig = if (sig != null && sig.lineCount > 0) (ebSig?.antes ?: 0f) + sig.getLineBottom(minOf(1, sig.lineCount - 1)) else 0f
                if (y + eb.antes + c.size.height + conSig > alto) nueva()
            }
            var linea = 0
            while (linea < n) {
                val a = if (actual.isEmpty() || linea > 0) 0f else eb.antes
                val arriba = c.getLineTop(linea)
                val libre = alto - y - a
                var caben = 0
                while (linea + caben < n && c.getLineBottom(linea + caben) - arriba <= libre) caben++
                if (b.esTitulo && caben < n - linea && actual.isNotEmpty()) { nueva(); continue }
                if (caben == 0) { if (actual.isEmpty()) caben = 1 else { nueva(); continue } }
                val restan = n - linea - caben
                if (restan > 0) {
                    // Huérfana: solo cabría la primera línea al pie → el párrafo entero a la siguiente
                    if (linea == 0 && caben < 2 && actual.isNotEmpty()) { nueva(); continue }
                    // Viuda: quedaría sola la última línea arriba de la siguiente → baja una más
                    if (restan == 1 && caben >= 3) caben -= 1
                }
                val altoT = c.getLineBottom(linea + caben - 1) - arriba
                actual += Trozo(k, linea, linea + caben, eb.izquierda, y + a, c.size.width.toFloat(), altoT, c)
                y += a + altoT
                linea += caben
                if (linea < n) nueva()
            }
        }
        nueva()
        if (paginas.isEmpty()) paginas += Pagina(emptyList())
        return Paginado(seccion, paginas, ancho, alto)
    }
}
