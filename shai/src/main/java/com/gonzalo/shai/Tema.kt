package com.gonzalo.shai

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/*
 * El aspecto de FcFeed: el de una red social. Negro puro de noche y blanco de día (como diga el sistema, o lo que elijas en
 * Ajustes), una sola letra sans (Inter), un azul para lo que se toca y nada más.
 */

/** Los tonos de un modo: fondo, tarjetas, texto, líneas, gris y acento. */
data class Tones(val paper: Long, val crudo: Long, val ink: Long, val sand: Long, val line: Long, val edge: Long, val muted: Long, val general: Long, val gold: Long) {
    val cPaper get() = Color(paper)
    val cCrudo get() = Color(crudo)
    val cInk get() = Color(ink)
    val cSand get() = Color(sand)
    val cLine get() = Color(line)
    val cEdge get() = Color(edge)
    val cMuted get() = Color(muted)
    /** ¿Fondo oscuro? (para las barras del sistema) */
    val darkBg: Boolean get() = Color(paper).let { 0.299f * it.red + 0.587f * it.green + 0.114f * it.blue } < 0.5f
}

val NOCHE = Tones(paper = 0xFF000000, crudo = 0xFF16181C, ink = 0xFFE7E9EA, sand = 0xFF202327, line = 0xFF2F3336, edge = 0xFF3E4246, muted = 0xFF71767B, general = 0xFF1D9BF0, gold = 0xFFFFD400)
val DIA = Tones(paper = 0xFFFFFFFF, crudo = 0xFFF7F9F9, ink = 0xFF0F1419, sand = 0xFFEFF3F4, line = 0xFFE6ECF0, edge = 0xFFCFD9DE, muted = 0xFF536471, general = 0xFF1D9BF0, gold = 0xFFE0A800)

/** Claro, oscuro o como el sistema; y el tamaño del texto. */
object Modo {
    var oscuro by mutableStateOf(true)
    /** 0 como el sistema, 1 claro, 2 oscuro. */
    var eleccion by mutableStateOf(0)
}

object Skin {
    /** Los tamaños del texto: por ciento → nombre. */
    val ESCALAS_NOMBRES = mapOf(90 to "Pequeño", 100 to "Normal", 112 to "Grande", 125 to "Muy grande")
    var escala by mutableStateOf(100)
    val tones: Tones get() = if (Modo.oscuro) NOCHE else DIA

    private fun p(c: Context) = c.getSharedPreferences("fcfeed_aspecto", Context.MODE_PRIVATE)
    fun cargar(c: Context) {
        val p = p(c)
        Modo.eleccion = p.getInt("modo", 0).coerceIn(0, 2)
        escala = p.getInt("escala", 100).coerceIn(80, 140)
    }
    fun guardar(c: Context) { p(c).edit().putInt("modo", Modo.eleccion).putInt("escala", escala).apply() }
}

/** Esquinas redondas de radio [r]. */
fun esquina(r: Dp): Shape = RoundedCornerShape(r)

val Paper: Color get() = Skin.tones.cPaper
val Crudo: Color get() = Skin.tones.cCrudo
val Sand: Color get() = Skin.tones.cSand
val Line: Color get() = Skin.tones.cLine
val Charcoal: Color get() = Skin.tones.cInk
val Edge: Color get() = Skin.tones.cEdge
val Muted: Color get() = Skin.tones.cMuted
/** El azul de lo que se toca. */
val Acento: Color get() = Color(Skin.tones.general)
val Dorado: Color get() = Color(Skin.tones.gold)
/** Texto sobre el acento. */
val SobreAcento: Color get() = Color.White
fun Color.luminance2(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

fun appColors() = if (Skin.tones.darkBg) darkColorScheme(
    primary = Charcoal, onPrimary = Paper, secondary = Acento, onSecondary = SobreAcento,
    background = Paper, onBackground = Charcoal, surface = Paper, onSurface = Charcoal,
    surfaceVariant = Sand, onSurfaceVariant = Charcoal, surfaceContainer = Crudo, surfaceContainerHigh = Crudo,
    surfaceContainerHighest = Sand, surfaceContainerLow = Crudo, outline = Edge, outlineVariant = Line, error = Color(0xFFF4212E)
) else lightColorScheme(
    primary = Charcoal, onPrimary = Paper, secondary = Acento, onSecondary = SobreAcento,
    background = Paper, onBackground = Charcoal, surface = Paper, onSurface = Charcoal,
    surfaceVariant = Sand, onSurfaceVariant = Charcoal, surfaceContainer = Crudo, surfaceContainerHigh = Crudo,
    surfaceContainerHighest = Sand, surfaceContainerLow = Crudo, outline = Edge, outlineVariant = Line, error = Color(0xFFF4212E)
)

/** La única letra: Inter. */
val Inter = FontFamily(
    Font(R.font.body_regular, FontWeight.Normal), Font(R.font.body_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.body_medium, FontWeight.Medium), Font(R.font.body_semibold, FontWeight.SemiBold),
    Font(R.font.body_bold, FontWeight.Bold), Font(R.font.body_bold, FontWeight.ExtraBold), Font(R.font.body_bold, FontWeight.Black)
)
/** (Los nombres de siempre: los títulos y el texto van con la misma letra.) */
val Display: FontFamily get() = Inter
val Body: FontFamily get() = Inter

private val base = Typography()
private fun TextStyle.inter() = copy(fontFamily = Inter)
val AppTypography: Typography get() = Typography(
    displayLarge = base.displayLarge.inter(), displayMedium = base.displayMedium.inter(), displaySmall = base.displaySmall.inter(),
    headlineLarge = base.headlineLarge.inter(), headlineMedium = base.headlineMedium.inter(), headlineSmall = base.headlineSmall.inter(),
    titleLarge = base.titleLarge.inter(), titleMedium = base.titleMedium.inter(), titleSmall = base.titleSmall.inter(),
    bodyLarge = base.bodyLarge.inter().copy(fontSize = 15.sp, lineHeight = 21.sp), bodyMedium = base.bodyMedium.inter(), bodySmall = base.bodySmall.inter(),
    labelLarge = base.labelLarge.inter().copy(fontWeight = FontWeight.SemiBold), labelMedium = base.labelMedium.inter(), labelSmall = base.labelSmall.inter()
)

@Composable
fun FcTheme(content: @Composable () -> Unit) {
    val sistema = isSystemInDarkTheme()
    Modo.oscuro = when (Modo.eleccion) { 1 -> false; 2 -> true; else -> sistema }
    val d = LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * Skin.escala / 100f)) {
        MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Charcoal, content = content)
        }
    }
}

/** Una píldora (los botones). */
fun pildora(): Shape = RoundedCornerShape(50)
