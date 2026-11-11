package com.gonzalo.familia

import android.content.Context
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Cómo lleva el acento cada widget: se elige al ponerlo en el escritorio (y se cambia manteniéndolo pulsado → Ajustes del widget).
 * Lo comparten las seis apps; cada una aplica el estilo en su Hoja (Widgets.kt).
 */
enum class EstiloWidget(val clave: String, val nombre: String, val explica: String) {
    COMO_AHORA("", "Como ahora", "Papel o negro; el acento, solo en las etiquetas."),
    CABECERA("cabecera", "Cabecera de color", "Una franja del acento arriba, con el nombre del widget."),
    TENIDO("tenido", "Papel teñido", "Todo el fondo lleva un poco del acento."),
    MARCO("marco", "Marco de color", "Un borde grueso del acento alrededor."),
    ACENTO("acento", "Todo acento", "El fondo es el acento; el texto, en papel o en negro.");

    companion object {
        fun de(clave: String?): EstiloWidget = entries.firstOrNull { it.clave == clave } ?: COMO_AHORA
    }
}

/** Los colores con que se dibuja un widget (ARGB). [bloque]: el acento tal cual, para lo que va relleno de color. */
data class TonosWidget(val papel: Int, val tinta: Int, val gris: Int, val linea: Int, val arena: Int, val crudo: Int, val bloque: Int)

/** Lo que necesita la pantalla de elegir: el tamaño del widget, si va sin papel (la cabecera sobre el fondo de pantalla) y cómo queda con cada estilo. */
class PreviasWidget(val ancho: Float, val alto: Float, val transparente: Boolean, val dibujo: (EstiloWidget, Boolean) -> android.graphics.Bitmap) {
    /** Los estilos que tienen sentido: sin papel, solo «como ahora» o el nombre en el color del acento. */
    val estilos: List<EstiloWidget> get() = if (transparente) listOf(EstiloWidget.COMO_AHORA, EstiloWidget.ACENTO) else EstiloWidget.entries
}

object EstilosWidget {
    private fun prefs(c: Context) = c.getSharedPreferences("familia_widgets_estilo", Context.MODE_PRIVATE)

    fun de(c: Context, id: Int): EstiloWidget = EstiloWidget.de(prefs(c).getString("$id", null))
    fun pon(c: Context, id: Int, e: EstiloWidget) { prefs(c).edit().putString("$id", e.clave).apply() }
    fun olvidar(c: Context, ids: IntArray) { val ed = prefs(c).edit(); ids.forEach { ed.remove("$it") }; ed.apply() }

    // ------------------------------------------------------------------ colores (Kotlin puro: se prueban sin Android)

    private fun rojo(c: Int) = (c shr 16) and 0xFF
    private fun verde(c: Int) = (c shr 8) and 0xFF
    private fun azul(c: Int) = c and 0xFF
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** De [a] hacia [b] en [t] (0..1). */
    fun mezcla(a: Int, b: Int, t: Float): Int {
        fun m(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        return rgb(m(rojo(a), rojo(b)), m(verde(a), verde(b)), m(azul(a), azul(b)))
    }

    fun luminancia(c: Int): Double {
        fun l(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * l(rojo(c)) + 0.7152 * l(verde(c)) + 0.0722 * l(azul(c))
    }

    /** Contraste WCAG entre dos colores (1..21). */
    fun contraste(a: Int, b: Int): Double { val x = luminancia(a); val y = luminancia(b); return (max(x, y) + 0.05) / (min(x, y) + 0.05) }

    /** El de los dos ([papel] o [tinta]) que mejor se lee sobre [fondo]. */
    fun sobre(fondo: Int, papel: Int, tinta: Int): Int = if (contraste(papel, fondo) >= contraste(tinta, fondo)) papel else tinta

    /** El acento, aclarado u oscurecido lo justo para que se vea como bloque sobre [fondo] (el granate de Nut sobre negro, de noche). */
    fun bloqueVisible(bloque: Int, fondo: Int, hacia: Int, minimo: Double = 2.0): Int {
        var t = 0f; var c = bloque
        while (contraste(c, fondo) < minimo && t < 1f) { t += 0.05f; c = mezcla(bloque, hacia, t) }
        return c
    }

    /** Los colores del widget con el estilo: teñido (el papel con algo del acento) o todo acento (el acento de papel). */
    fun aplicar(e: EstiloWidget, noche: Boolean, t: TonosWidget): TonosWidget = when (e) {
        EstiloWidget.TENIDO -> {
            val k = if (noche) 0.30f else 0.17f
            t.copy(papel = mezcla(t.papel, t.bloque, k), linea = mezcla(t.linea, t.bloque, k), arena = mezcla(t.arena, t.bloque, k), crudo = mezcla(t.crudo, t.bloque, k))
        }
        EstiloWidget.ACENTO -> {
            val fondo = t.bloque
            val texto = sobre(fondo, t.papel, t.tinta)
            TonosWidget(papel = fondo, tinta = texto, gris = mezcla(fondo, texto, 0.72f), linea = mezcla(fondo, texto, 0.30f),
                arena = mezcla(fondo, texto, 0.14f), crudo = mezcla(fondo, texto, 0.08f), bloque = texto)
        }
        else -> t
    }

    /** El color de la franja (cabecera) o del marco: el acento, visible sobre el papel. */
    fun colorBorde(t: TonosWidget): Int = bloqueVisible(t.bloque, t.papel, t.tinta)
}
