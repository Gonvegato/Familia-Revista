package com.gonzalo.mau

import kotlin.math.abs

/** Una curva de tiempo como las de CSS: cubic-bezier(x1, y1, x2, y2). De 0..1 a 0..1 (puede pasarse un poco con y > 1: un rebote). */
class Curva(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) {
    private fun bx(t: Float) = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t
    private fun by(t: Float) = 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
    private fun dbx(t: Float) = 3 * (1 - t) * (1 - t) * x1 + 6 * (1 - t) * t * (x2 - x1) + 3 * t * t * (1 - x2)

    operator fun invoke(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        // t tal que bx(t) = x: Newton y, si no converge, bisección
        var t = x
        repeat(8) {
            val err = bx(t) - x
            if (abs(err) < 1e-4f) return by(t)
            val d = dbx(t)
            if (abs(d) < 1e-6f) return@repeat
            t -= err / d
        }
        var lo = 0f; var hi = 1f; t = x
        repeat(30) {
            val v = bx(t)
            if (abs(v - x) < 1e-5f) return by(t)
            if (v < x) lo = t else hi = t
            t = (lo + hi) / 2
        }
        return by(t)
    }

    companion object {
        /** cubic-bezier(.45, 0, .55, 1): la de casi todo (respirar, la cola, la cabeza). */
        val SUAVE = Curva(.45f, 0f, .55f, 1f)
        val LINEAL = Curva(0f, 0f, 1f, 1f)
        val OREJA_IDA = Curva(.2f, .8f, .4f, 1f)
        val OREJA_VUELTA = Curva(.4f, 0f, .6f, 1f)
        val MAULLIDO = Curva(.3f, 1.4f, .5f, 1f)
        val OVILLO = Curva(.2f, .8f, .3f, 1f)
        val ASOMARSE = Curva(.2f, .9f, .3f, 1.15f)
        val ESCONDERSE = Curva(.5f, 0f, .9f, .4f)
        val SUBIR = Curva(.2f, .9f, .25f, 1.08f)
    }
}

/**
 * Valores por tramos: [marcas] son (tiempo de 0..1, valor); entre dos marcas se pasa con [curva] (la del tramo, como en CSS: se aplica
 * a cada tramo por separado).
 */
internal fun tramos(x: Float, marcas: List<Pair<Float, Float>>, curva: Curva = Curva.SUAVE): Float {
    if (x <= marcas.first().first) return marcas.first().second
    for (i in 1 until marcas.size) {
        val (t1, v1) = marcas[i]
        val (t0, v0) = marcas[i - 1]
        if (x <= t1) {
            val f = if (t1 == t0) 1f else (x - t0) / (t1 - t0)
            return v0 + (v1 - v0) * curva(f)
        }
    }
    return marcas.last().second
}
