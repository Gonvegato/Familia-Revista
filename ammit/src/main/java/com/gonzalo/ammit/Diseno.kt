package com.gonzalo.ammit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.cos
import kotlin.math.sin

/** Los colores y las letras de la familia: papel y negro (de noche, al revés) y el acento de Ammit. */
object Diseno {
    val PAPEL = Color(0xFFF8F0D4)
    val NEGRO = Color(0xFF000000)
    val ACENTO = Color(Ammit.ACENTO)
    val ACENTO_NOCHE = Color(Ammit.ACENTO_NOCHE)

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    val Grotesca = FontFamily(
        Font(R.font.bricolage, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.width(92f))),
        Font(R.font.bricolage, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    )
    val Texto = FontFamily(
        Font(R.font.body_regular, FontWeight.Normal), Font(R.font.body_medium, FontWeight.Medium), Font(R.font.body_bold, FontWeight.Bold),
    )
    val Cursiva = FontFamily(Font(R.font.display_italic, FontWeight.Normal, FontStyle.Italic))
}

/** Papel, tinta y acento según el modo del sistema. */
class Tonos(val fondo: Color, val tinta: Color, val acento: Color, val gris: Color)

@Composable
fun tonos(): Tonos = if (isSystemInDarkTheme()) Tonos(Diseno.NEGRO, Diseno.PAPEL, Diseno.ACENTO_NOCHE, Diseno.PAPEL.copy(alpha = 0.62f))
else Tonos(Diseno.PAPEL, Diseno.NEGRO, Diseno.ACENTO, Diseno.NEGRO.copy(alpha = 0.6f))

/** Las cadenas: eslabones en diagonal que cruzan la pantalla (o un icono). [grosor] y [paso] en px. */
@Composable
fun Cadenas(color: Color, modifier: Modifier = Modifier.fillMaxSize(), alfa: Float = 0.14f, escala: Float = 1f) {
    Canvas(modifier) {
        val c = color.copy(alpha = alfa)
        val paso = 30f * escala * density / 2.6f
        val rx = 18f * escala * density / 2.6f
        val grosor = Stroke(5f * escala * density / 2.6f)
        fun cadena(x0: Float, y0: Float, ang: Float, n: Int) {
            for (i in 0 until n) {
                val x = x0 + cos(ang) * i * paso; val y = y0 + sin(ang) * i * paso
                val ry = if (i % 2 == 1) rx / 3f else rx * 0.55f
                rotate(Math.toDegrees(ang.toDouble()).toFloat(), Offset(x, y)) {
                    drawOval(c, Offset(x - rx, y - ry), Size(rx * 2, ry * 2), style = grosor)
                }
            }
        }
        val n = ((size.width + size.height) / paso).toInt() + 4
        cadena(-20f, size.height * 0.15f, 0.42f, n)
        cadena(-20f, size.height * 0.85f, -0.38f, n)
        cadena(size.width * 0.6f, -20f, 1.2f, n)
    }
}

/** Cuánto encadenadas están ahora las apps de fuera (0 sueltas, 1 encadenadas): cambia durante la animación de entrar y salir. */
val LocalCadena = androidx.compose.runtime.compositionLocalOf { 0f }

/**
 * Las cadenas grandes de la animación. [entrando]: llegan cruzando la pantalla una tras otra (con un poco de rebote) y se desvanecen al
 * final; si no, están puestas y se caen. [t] va de 0 a 1.
 */
@Composable
fun CadenasGrandes(t: Float, entrando: Boolean, color: Color = Diseno.PAPEL) {
    Canvas(Modifier.fillMaxSize()) {
        val esc = 1.7f
        val paso = 30f * esc * density / 2.6f
        val rx = 18f * esc * density / 2.6f
        val grosor = Stroke(6f * esc * density / 2.6f)
        val largo = size.width + size.height
        val n = (largo / paso).toInt() + 4
        // (x0, y0, ángulo) de cada cadena: las mismas tres diagonales de la pantalla del juicio
        val cadenas = listOf(Triple(-20f, size.height * 0.15f, 0.42f), Triple(-20f, size.height * 0.85f, -0.38f), Triple(size.width * 0.6f, -20f, 1.2f))
        cadenas.forEachIndexed { k, (x0, y0, ang) ->
            val alfa: Float
            var dx = 0f; var dy = 0f; var giro = 0f
            if (entrando) {
                val p = ((t - k * 0.09f) / 0.42f).coerceIn(0f, 1f)
                if (p <= 0f) return@forEachIndexed
                // Entra deslizándose a lo largo de su diagonal, con un pequeño rebote al cerrarse
                val r = 1f - (1f - p) * (1f - p) * (1f - p)
                val rebote = if (p >= 1f) 0f else kotlin.math.sin(p * Math.PI.toFloat()) * 0.06f
                val atras = (1f - r + rebote) * largo
                dx = -cos(ang) * atras; dy = -sin(ang) * atras
                alfa = 0.9f * (1f - ((t - 0.78f) / 0.22f).coerceIn(0f, 1f))
            } else {
                // Están puestas y se caen (cada una un poco después que la anterior), girando algo
                val p = ((t - k * 0.07f) / 0.7f).coerceIn(0f, 1f)
                dy = p * p * size.height * 1.2f
                giro = p * (if (k % 2 == 0) 9f else -7f)
                alfa = 0.9f * (1f - ((t - 0.55f) / 0.45f).coerceIn(0f, 1f))
            }
            if (alfa <= 0f) return@forEachIndexed
            val c = color.copy(alpha = alfa)
            rotate(giro, Offset(size.width / 2, size.height / 2)) {
                for (i in 0 until n) {
                    val x = x0 + dx + cos(ang) * i * paso; val y = y0 + dy + sin(ang) * i * paso
                    val ry = if (i % 2 == 1) rx / 3f else rx * 0.55f
                    rotate(Math.toDegrees(ang.toDouble()).toFloat(), Offset(x, y)) {
                        drawOval(c, Offset(x - rx, y - ry), Size(rx * 2, ry * 2), style = grosor)
                    }
                }
            }
        }
    }
}

/**
 * Una cadena recta que cruza la pantalla por (x0, y0) con el ángulo [ang], dibujada hasta [p] (0..1: de nada a entera, entrando desde
 * fuera a lo largo de su línea). [esc]: el tamaño de los eslabones.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.cadenaQueEntra(x0: Float, y0: Float, ang: Float, p: Float, color: Color, esc: Float) {
    if (p <= 0f) return
    val paso = 30f * esc * density / 2.6f
    val rx = 18f * esc * density / 2.6f
    val grosor = Stroke(6f * esc * density / 2.6f)
    val largo = size.width + size.height
    val n = (largo * 1.4f / paso).toInt()
    // Desde fuera de la pantalla, por detrás de (x0, y0), hasta cruzarla entera
    val ix = x0 - cos(ang) * largo * 0.7f; val iy = y0 - sin(ang) * largo * 0.7f
    val r = 1f - (1f - p) * (1f - p) * (1f - p)
    val atras = (1f - r) * largo
    for (i in 0 until n) {
        val d = i * paso - atras
        if (d < 0f) continue
        val x = ix + cos(ang) * d; val y = iy + sin(ang) * d
        val ry = if (i % 2 == 1) rx / 3f else rx * 0.55f
        rotate(Math.toDegrees(ang.toDouble()).toFloat(), Offset(x, y)) { drawOval(color, Offset(x - rx, y - ry), Size(rx * 2, ry * 2), style = grosor) }
    }
}
