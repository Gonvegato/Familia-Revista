package com.gonzalo.marfil.pc

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as FontDe

/**
 * Las letras del lector, las mismas que lleva el lector del móvil (`assets/lector/fuentes`): Literata, EB Garamond y Atkinson Hyperlegible
 * (licencia OFL, junto a los archivos), Fraunces (la de los títulos de la familia) y las del sistema para Serif, Sans y Mono.
 * Literata y Garamond son de grosor variable: «un poco más gruesa» es su eje de peso, como en el móvil.
 */
object Letras {
    private val cache = HashMap<String, FontFamily>()
    private fun bytes(ruta: String): ByteArray = Letras::class.java.getResourceAsStream(ruta)?.use { it.readBytes() } ?: ByteArray(0)

    private fun variable(nombre: String, normal: String, cursiva: String, base: Int): FontFamily {
        val n = bytes("/lector/fuentes/$normal"); val c = bytes("/lector/fuentes/$cursiva")
        if (n.isEmpty()) return FontFamily.Serif
        fun f(d: ByteArray, peso: Int, estilo: FontStyle, real: Int) = FontDe("marfil-$nombre-$peso-$estilo-$real", d, FontWeight(peso), estilo, FontVariation.Settings(FontVariation.weight(real)))
        val pesos = listOf(400 to base, 500 to base + 80, 600 to 600, 700 to 700)
        return FontFamily(pesos.flatMap { (p, r) -> listOfNotNull(f(n, p, FontStyle.Normal, r), if (c.isNotEmpty()) f(c, p, FontStyle.Italic, r) else null) })
    }

    private fun fija(nombre: String, vararg archivos: Triple<String, Int, FontStyle>): FontFamily {
        val fs = archivos.mapNotNull { (a, p, e) -> bytes(a).takeIf { it.isNotEmpty() }?.let { FontDe("marfil-$nombre-$p-$e", it, FontWeight(p), e) } }
        return if (fs.isEmpty()) FontFamily.Serif else FontFamily(fs)
    }

    /** La familia de una letra del lector («literata», «garamond»…). [gruesa]: el peso normal, un poco más grueso. */
    fun de(letra: String, gruesa: Boolean = false): FontFamily = synchronized(cache) {
        cache.getOrPut("$letra|$gruesa") {
            when (letra) {
                // Sin la hoja de estilos del libro, «Original» es la letra de leer de la familia
                "original", "literata" -> variable("literata", "literata.ttf", "literata_italic.ttf", if (gruesa) 480 else 400)
                "garamond" -> variable("garamond", "garamond.ttf", "garamond_italic.ttf", if (gruesa) 500 else 400)
                "atrio" -> fija("atrio", Triple("/fuentes/display_regular.ttf", 400, FontStyle.Normal), Triple("/fuentes/display_semibold.ttf", 600, FontStyle.Normal),
                    Triple("/fuentes/display_semibold.ttf", 700, FontStyle.Normal), Triple("/fuentes/display_italic.ttf", 400, FontStyle.Italic))
                "facil" -> fija("atkinson", Triple("/lector/fuentes/atkinson_regular.ttf", 400, FontStyle.Normal), Triple("/lector/fuentes/atkinson_bold.ttf", 700, FontStyle.Normal),
                    Triple("/lector/fuentes/atkinson_italic.ttf", 400, FontStyle.Italic), Triple("/lector/fuentes/atkinson_bolditalic.ttf", 700, FontStyle.Italic))
                "sans" -> FontFamily.SansSerif
                "mono" -> FontFamily.Monospace
                else -> FontFamily.Serif
            }
        }
    }
}
