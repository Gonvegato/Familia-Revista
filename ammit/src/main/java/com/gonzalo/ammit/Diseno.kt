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

/** Cuánto encadenadas están las apps de fuera (0 sueltas, 1 encadenadas). */
val LocalCadena = androidx.compose.runtime.compositionLocalOf { 0f }
