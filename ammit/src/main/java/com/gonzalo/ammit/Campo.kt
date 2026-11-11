package com.gonzalo.ammit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * El sinfín de cadenas: el fondo de Ammit y lo que se ve al encenderla, al apagarla y en el juicio. Son siempre las mismas 82 cadenas, en el
 * mismo sitio y en el mismo orden (dependen solo del tamaño de la pantalla), así que la ráfaga del juicio, la de encender y el fondo
 * coinciden y se puede pasar de una pantalla a otra sin que se note.
 *
 * Para que vaya fluido, cada tipo de eslabón (de frente, de canto, y sus sombras, en seis tamaños) se pinta una sola vez en una imagen
 * ([atlas]); luego cada cadena es copiar esa imagen a lo largo de su línea. Lo más hondo se oscurece con un filtro de color, sin repintar.
 */
class Campo private constructor(val ancho: Int, val alto: Int) {
    /** Una cadena: de (x1, y1) a (x2, y2), con eslabones de tamaño [esc]. */
    class Cadena(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val esc: Float)

    /** La unidad: la pantalla mide 1000 de alto (o de ancho, si es más estrecha). Así se ve igual en cualquier tamaño. */
    val u = min(ancho, alto) / 1000f
    val cadenas: List<Cadena>

    init {
        val w = ancho.toFloat(); val h = alto.toFloat(); val cx0 = w / 2; val cy0 = h / 2
        val largo = hypot(w, h) + 500 * u
        val l = ArrayList<Pair<Cadena, Double>>()
        var k = 0
        DIRECCIONES.forEachIndexed { f, a ->
            val nx = -sin(a); val ny = cos(a)
            val ps = listOf(0f to 0f, w to 0f, 0f to h, w to h).map { (x, y) -> (x - cx0) * nx + (y - cy0) * ny }
            val lo = ps.min() - 30 * u; val hi = ps.max() + 30 * u
            val n = CUANTAS[f]
            for (i in 0 until n) {
                val o = lo + (hi - lo) * ((i + 0.2f + 0.6f * azar(k * 7 + f).toFloat()) / n)
                val cx = cx0 + nx * o; val cy = cy0 + ny * o
                val d = if (azar(k + 300) < 0.5) 1f else -1f
                val esc = ((1.9 + azar(k + 600) * 1.3) * 4).roundToInt() / 4f
                l += Cadena(cx - cos(a) * largo / 2 * d, cy - sin(a) * largo / 2 * d, cx + cos(a) * largo / 2 * d, cy + sin(a) * largo / 2 * d, esc) to azar(k + 900)
                k++
            }
        }
        cadenas = l.sortedBy { it.second }.map { it.first }
    }

    val n get() = cadenas.size

    // ------------------------------------------------------------------ los eslabones, pintados una vez

    /** Dónde está cada eslabón en el atlas: por tamaño, [frente, canto, sombra de frente, sombra de canto]. */
    private val sitios = HashMap<Float, Array<Rect>>()
    private val atlas: Bitmap

    init {
        // Seis tamaños, en filas; en cada fila, los cuatro dibujos
        val filas = ESCALAS.map { e ->
            val rx = 20 * e * u; val ry = rx * 0.62f; val gros = 7 * e * u
            val pad = kotlin.math.ceil(gros * 1.2f + 4).toInt()
            Triple(e, (rx * 2 + pad * 2).toInt() + 2, (ry * 2 + pad * 2).toInt() + 2)
        }
        val anchoAtlas = filas.maxOf { it.second } * 4
        val altoAtlas = filas.sumOf { it.third }
        atlas = Bitmap.createBitmap(max(1, anchoAtlas), max(1, altoAtlas), Bitmap.Config.ARGB_8888)
        val c = Canvas(atlas)
        var y = 0
        filas.forEach { (e, sw, sh) ->
            val rx = 20 * e * u; val ry = rx * 0.62f; val gros = 7 * e * u
            val r = Array(4) { i -> Rect(i * sw, y, i * sw + sw, y + sh) }
            r.forEachIndexed { i, rect ->
                c.save(); c.translate(rect.exactCenterX(), rect.exactCenterY())
                when (i) { 0 -> frente(c, rx, ry, gros); 1 -> canto(c, rx, gros); 2 -> sombra(c, rx, ry, gros, false); else -> sombra(c, rx, ry, gros, true) }
                c.restore()
            }
            sitios[e] = r
            y += sh
        }
    }

    private fun frente(c: Canvas, rx: Float, ry: Float, gros: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = gros }
        p.shader = LinearGradient(0f, -ry - gros, 0f, ry + gros, intArrayOf(0xFFFFFBE9.toInt(), 0xFFDDD3B4.toInt(), 0xFF8A8168.toInt(), 0xFF3D382C.toInt()),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        val med = RectF(-(rx - gros / 2), -max(1f, ry - gros / 2), rx - gros / 2, max(1f, ry - gros / 2))
        c.drawOval(med, p)
        val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, gros * 0.08f); color = 0x99000000.toInt() }
        c.drawOval(RectF(-rx, -ry, rx, ry), borde)
        c.drawOval(RectF(-max(1f, rx - gros), -max(1f, ry - gros), max(1f, rx - gros), max(1f, ry - gros)), borde)
        val brillo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, gros * 0.18f); color = 0xBFFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND }
        c.drawArc(med, 198f, 90f, false, brillo)
    }

    private fun canto(c: Canvas, rx: Float, gros: Float) {
        val r = RectF(-rx, -gros * 0.55f, rx, gros * 0.55f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, -gros * 0.6f, 0f, gros * 0.6f, intArrayOf(0xFFFFFBE9.toInt(), 0xFFCFC4A4.toInt(), 0xFF5F5847.toInt()),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, gros * 0.55f, gros * 0.55f, p)
        c.drawRoundRect(r, gros * 0.55f, gros * 0.55f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, gros * 0.08f); color = 0x8C000000.toInt() })
    }

    private fun sombra(c: Canvas, rx: Float, ry: Float, gros: Float, deCanto: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = gros * 1.1f; color = 0x80000000.toInt() }
        if (deCanto) c.drawRoundRect(RectF(-rx, -gros * 0.55f, rx, gros * 0.55f), gros * 0.55f, gros * 0.55f, p)
        else c.drawOval(RectF(-rx, -ry, rx, ry), p)
    }

    // ------------------------------------------------------------------ pintar

    private val dst = RectF()
    private val pSombra = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pBase = Paint()
    /** Un pincel por cada nivel de oscuro (lo hondo, más oscuro), según [oscuro] del momento. */
    private val pinceles = Array(9) { Paint(Paint.FILTER_BITMAP_FLAG) }
    private var oscuroPuesto = -1f

    private fun pinceles(oscuro: Float) {
        if (abs(oscuro - oscuroPuesto) < 0.004f) return
        oscuroPuesto = oscuro
        pinceles.forEachIndexed { i, p ->
            val f = (1f - i / 8f * 0.7f) * (1f - oscuro)
            val m = (255 * f).roundToInt().coerceIn(0, 255)
            p.colorFilter = LightingColorFilter((m shl 16) or (m shl 8) or m, 0)
        }
    }

    /** Una cadena de (x1, y1) a (x2, y2), dibujada hasta la fracción [p] desde (x1, y1). [nivel]: lo honda que está (0..8). */
    fun cadena(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, p: Float, esc: Float, nivel: Int = 0, oscuro: Float = 0f) {
        if (p <= 0f) return
        pinceles(oscuro)
        val e = ESCALAS.minBy { abs(it - esc) }
        val s = sitios[e] ?: return
        val l = hypot(x2 - x1, y2 - y1); val ang = atan2(y2 - y1, x2 - x1)
        val rx = 20 * e * u; val paso = rx * 1.45f; val gros = 7 * e * u
        val cuantos = floor(l * p / paso).toInt()
        if (cuantos < 0) return
        // (la sombra cae abajo a la derecha de la pantalla: girada a lo local de la cadena)
        val ox = gros * (0.5f * cos(ang) + 0.8f * sin(ang)); val oy = gros * (-0.5f * sin(ang) + 0.8f * cos(ang))
        c.save(); c.translate(x1, y1); c.rotate(Math.toDegrees(ang.toDouble()).toFloat())
        for (k in 0..cuantos) pon(c, atlas, s[if (k % 2 == 1) 3 else 2], k * paso + ox, oy, pSombra)
        val pincel = pinceles[nivel.coerceIn(0, 8)]
        // primero los de canto y encima los de frente: así parece que pasan unos por dentro de otros
        var k = 1; while (k <= cuantos) { pon(c, atlas, s[1], k * paso, 0f, pincel); k += 2 }
        k = 0; while (k <= cuantos) { pon(c, atlas, s[0], k * paso, 0f, pincel); k += 2 }
        c.restore()
    }

    private fun pon(c: Canvas, b: Bitmap, r: Rect, x: Float, y: Float, p: Paint) {
        val w = r.width() / 2f; val h = r.height() / 2f
        dst.set(x - w, y - h, x + w, y + h)
        c.drawBitmap(b, r, dst, p)
    }

    /**
     * El campo entero: cada cadena hasta [prog] (0 nada, 1 entera), las hondas más oscuras, todo apagado [oscuro] (para leer encima) y,
     * detrás, el negro que tapa los huecos con opacidad [base].
     */
    fun pinta(c: Canvas, oscuro: Float, base: Float, prog: (Int) -> Float) {
        if (base > 0f) { pBase.color = 0x0A0908 or ((255 * base.coerceIn(0f, 1f)).roundToInt() shl 24); c.drawRect(-200f, -200f, ancho + 200f, alto + 200f, pBase) }
        val ult = n - 1
        cadenas.forEachIndexed { k, ca ->
            val p = prog(k)
            if (p > 0f) cadena(c, ca.x1, ca.y1, ca.x2, ca.y2, min(1f, p), ca.esc, ((ult - k) * 8f / ult).roundToInt(), oscuro)
        }
    }

    /** El fondo de Ammit ya hecho (todas las cadenas, apagadas para leer encima): se pinta una vez y se guarda. */
    private val fondoPerezoso = lazy { Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888).also { b -> pinta(Canvas(b), OSCURO, 1f) { 1f } } }
    val fondo: Bitmap by fondoPerezoso
    /** El fondo, si ya está hecho (para no hacerlo a medio fotograma). */
    fun fondoSiHay(): Bitmap? = if (fondoPerezoso.isInitialized()) fondo else null

    companion object {
        /** Cuánto se apagan las cadenas del fondo para que se lean las apps encima. */
        const val OSCURO = 0.45f
        private val DIRECCIONES = floatArrayOf(0.42f, -0.42f, 1.2f, -1.2f, 0.05f)
        private val CUANTAS = intArrayOf(19, 19, 16, 16, 12)
        private val ESCALAS = floatArrayOf(2f, 2.25f, 2.5f, 2.75f, 3f, 3.25f)

        /** Pseudoazar fijo (siempre lo mismo para el mismo n). */
        fun azar(n: Int): Double { val x = sin(n * 9301.0 + 49297) * 233280; return x - floor(x) }

        @Volatile private var ultimo: Campo? = null

        /**
         * El tamaño de la pantalla entera (barras incluidas). Todas las pantallas de Ammit pintan el campo con este tamaño desde la
         * esquina de arriba, así que el de la cortina, el del juicio y el del fondo coinciden al píxel.
         */
        fun pantalla(c: Context): Pair<Int, Int> {
            val wm = c.getSystemService(android.view.WindowManager::class.java)
            val r = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 30 && wm != null) wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
                else android.util.DisplayMetrics().also { @Suppress("DEPRECATION") wm?.defaultDisplay?.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
            }.getOrNull()
            if (r != null && r.first > 0 && r.second > 0) return r
            return c.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }

        fun dePantalla(c: Context): Campo = pantalla(c).let { (w, h) -> de(w, h) }

        /** El de la pantalla entera; si lo que hay que tapar es más grande (no debería), el de ese tamaño. */
        fun paraTapar(c: Context, w: Float, h: Float): Campo = dePantalla(c).let { if (it.ancho + 1 < w || it.alto + 1 < h) de(w.toInt(), h.toInt()) else it }

        /** El campo para una pantalla de ese tamaño (se guarda el último: el de la tablet). */
        fun de(ancho: Int, alto: Int): Campo {
            ultimo?.let { if (it.ancho == ancho && it.alto == alto) return it }
            return synchronized(this) {
                ultimo?.takeIf { it.ancho == ancho && it.alto == alto } ?: Campo(max(1, ancho), max(1, alto)).also { ultimo = it }
            }
        }
    }
}

/** Los tiempos de las animaciones (en ms) y el temblor de los golpes, iguales que en la maqueta. */
object Tiempos {
    fun easeIn(t: Float) = t * t * t
    fun ease(t: Float) = 1 - (1 - t).pow(3)
    fun clamp(v: Float) = v.coerceIn(0f, 1f)

    // Encender: el sinfín de cadenas, una cada 15 ms; luego aparecen la hora y las apps
    const val ENTRAR_MS = 2400
    fun entrar(ms: Float, k: Int) = easeIn(clamp((ms - 150 - k * 15) / 130))
    fun entrarOscuro(ms: Float) = Campo.OSCURO * clamp((ms - 1450) / 450)
    fun entrarBase(ms: Float) = clamp((ms - 1050) / 400)
    const val ENTRAR_APPS = 1550f

    // Apagar (encima de tu inicio de siempre): las cadenas se van por donde vinieron, la de arriba primero
    fun salidaFin(n: Int) = 80 + (n - 1) * 12 + 170 + 60
    fun salir(ms: Float, k: Int, n: Int) = 1 - easeIn(clamp((ms - 80 - (n - 1 - k) * 12) / 170))
    fun salirBase(ms: Float) = 1 - clamp((ms - 80) / 350)

    // El juicio: las cuatro (80–900), el rótulo (950, un buen rato solo), la ráfaga, que son las cadenas del fondo (2450–3550)
    const val JUICIO_RAFAGA = 2450f
    const val JUICIO_MS = 3950
    fun juicioCuatro(ms: Float, k: Int) = easeIn(clamp((ms - 80 - k * 200) / 130))
    fun juicioRotulo(ms: Float) = clamp((ms - 950) / 180)
    fun juicio(ms: Float, k: Int) = easeIn(clamp((ms - JUICIO_RAFAGA - k * 12) / 130))
    fun juicioBase(ms: Float) = clamp((ms - 3150) / 400)
    fun juicioOscuro(ms: Float) = Campo.OSCURO * clamp((ms - 3500) / 400)

    /** Lo que tiembla la pantalla por los golpes que han caído antes de [ms] (cada golpe: momento y fuerza), en unidades del campo. */
    fun temblor(ms: Float, golpes: Sequence<Pair<Float, Float>>): Pair<Float, Float> {
        var a = 0f
        for ((t, f) in golpes) if (ms >= t && ms - t < 600) a = max(a, f * 0.86f.pow((ms - t) / 16.67f))
        if (a < 0.3f) return 0f to 0f
        return a * 0.5f * sin(ms * 0.9f + 1.3f) to a * 0.5f * sin(ms * 1.13f)
    }

    fun golpesEntrar(n: Int) = (0 until n).asSequence().map { k -> (280f + k * 15) to (8f + (k % 4) * 4) } + sequenceOf(0f to 3f)
    fun golpesSalir(n: Int) = (0 until n).asSequence().map { k -> (80f + (n - 1 - k) * 12 + 170) to (5f + (k % 3) * 3) }
    fun golpesJuicio(n: Int) = (0 until 4).asSequence().map { k -> (80f + k * 200 + 130) to 26f } + sequenceOf(950f to 30f) +
        (0 until n).asSequence().map { k -> (JUICIO_RAFAGA + 130 + k * 12) to (8f + (k % 4) * 4) }
}
