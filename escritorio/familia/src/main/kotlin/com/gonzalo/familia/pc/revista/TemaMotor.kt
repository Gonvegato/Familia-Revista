package com.gonzalo.familia.pc.revista

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Formas: cómo dibuja cada estilo las esquinas y los bordes. Son las mismas ideas que las «formas» de Atrio, más simples:
 * en Marfil la portada es un ARCO (la puerta de Atrio, repetida), y según la forma el arco es redondo, biselado o recto.
 */
enum class Esq { Redonda, Cortada, Recta }
data class Forma(val id: String, val esq: Esq, val radio: Float, val borde: Dp, val bordeTinta: Boolean, val sombraDura: Boolean, val arco: Float)

val FORMAS: List<Forma> = listOf(
    Forma("vitrina", Esq.Redonda, 1.3f, 0.dp, false, false, 1f),
    Forma("clasico", Esq.Redonda, 1f, 1.dp, false, false, 0.85f),
    Forma("editorial", Esq.Redonda, 0.3f, 1.dp, false, false, 0.55f),
    Forma("suave", Esq.Redonda, 1.8f, 0.dp, false, false, 1f),
    Forma("minimal", Esq.Redonda, 0.5f, 0.dp, false, false, 0.35f),
    Forma("comic", Esq.Redonda, 0.7f, 2.5.dp, true, true, 0.7f),
    Forma("neon", Esq.Cortada, 0.6f, 1.5.dp, false, false, 0.6f),
    Forma("militar", Esq.Cortada, 0.4f, 2.dp, true, false, 0.5f),
    Forma("pixel", Esq.Recta, 0f, 3.dp, true, true, 0f),
    Forma("terminal", Esq.Recta, 0f, 1.dp, true, false, 0f),
    Forma("brutal", Esq.Recta, 0f, 3.dp, true, true, 0f),
    Forma("revista", Esq.Recta, 0f, 0.dp, false, false, 0f),
)

/** Esquina normal de radio [r] según la forma. */
fun esquina(r: Dp): Shape {
    val f = Skin.forma
    val k = (r.value * f.radio).dp
    return when (f.esq) {
        Esq.Recta -> if (k.value < 0.5f) RectangleShape else RoundedCornerShape(k)
        Esq.Cortada -> if (k.value < 0.5f) RectangleShape else CutCornerShape(k)
        Esq.Redonda -> RoundedCornerShape(k)
    }
}

/** Botones y chips «píldora». */
fun pildora(): Shape = when (Skin.forma.esq) {
    Esq.Redonda -> if (Skin.forma.radio < 0.6f) RoundedCornerShape(10.dp) else RoundedCornerShape(50)
    Esq.Cortada -> CutCornerShape(30)
    Esq.Recta -> RectangleShape
}

/**
 * La portada de Marfil: un arco. Arriba, media circunferencia (o menos, según la forma); abajo, esquinas pequeñas.
 * En las formas rectas es un rectángulo con borde; en las biseladas, el arco es un chaflán.
 */
class ArcoShape(private val f: Forma) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val abajo = (minOf(w, h) * 0.07f * f.radio.coerceAtLeast(0.6f)).coerceAtMost(minOf(w, h) / 4f)
        return when (f.esq) {
            Esq.Recta -> Outline.Rectangle(Rect(0f, 0f, w, h))
            Esq.Cortada -> {
                val c = minOf(w * 0.28f * f.arco.coerceAtLeast(0.5f), h / 3f)
                Outline.Generic(Path().apply {
                    moveTo(c, 0f); lineTo(w - c, 0f); lineTo(w, c); lineTo(w, h); lineTo(0f, h); lineTo(0f, c); close()
                })
            }
            Esq.Redonda -> {
                val r = (w / 2f * f.arco).coerceAtMost(h / 2f)
                Outline.Rounded(RoundRect(0f, 0f, w, h, CornerRadius(r), CornerRadius(r), CornerRadius(abajo), CornerRadius(abajo)))
            }
        }
    }
}

/** Mismo arco, más pequeño en el radio de las esquinas de abajo y menos alto: para el cartel del juego (apaisado). */
fun arco(): Shape = ArcoShape(Skin.forma)

/** Estado del modo claro/oscuro: lo pone la raíz de la app según Ajustes y el sistema. */
object Modo {
    var oscuro by mutableStateOf(true)
    /** 0 como el sistema, 1 claro, 2 oscuro. */
    var eleccion by mutableStateOf(0)
}

object Skin {
    /** Los tamaños del texto: por ciento → nombre. */
    val ESCALAS_NOMBRES = mapOf(90 to "Pequeño", 100 to "Normal", 112 to "Grande", 125 to "Muy grande")
    var palette by mutableStateOf(PALETTES.first { it.id == "familia" })
    var display by mutableStateOf("Atrio")
    var body by mutableStateOf("Atrio")
    var forma by mutableStateOf(FORMAS.first { it.id == "revista" })
    /** Id de [ACCENT_COLORS] o "paleta" (el de la paleta). */
    var accentId by mutableStateOf("paleta")
    /** Tamaño del texto en por ciento. */
    var escala by mutableStateOf(100)
    /** Colores invertidos: el fondo con el color del texto y el texto con el del fondo; el acento se mantiene. */
    var invertido by mutableStateOf(false)
    /** Los colores de Azabache (los que tienes puestos allí, leídos de la cuenta) en lugar de los de la paleta de esta app. */
    var origenAtrio by mutableStateOf(false)
    /** Colores elegidos a mano, como «A tu manera» de Azabache (0 = automático): por encima de todo lo demás. */
    var fondoPropio by mutableStateOf(0L)
    /** Un fondo de color de [FONDOS]; "" = papel. */
    var fondoId by mutableStateOf("")
    var tintaPropia by mutableStateOf(0L)
    var acentoPropio by mutableStateOf(0L)

    /** El fondo (las tarjetas, líneas y grises salen de él y del texto), el texto y el acento elegidos a mano, sobre [t]. */
    fun conPropios(t: Tones, fondo: Long = fondoPropio, tintaP: Long = tintaPropia, acentoP: Long = acentoPropio): Tones {
        var r = t
        if (fondo != 0L || tintaP != 0L) {
            val papel = if (fondo != 0L) fondo else r.paper
            val tinta = when {
                tintaP != 0L -> tintaP
                fondo != 0L -> if (Color(papel).luminance() < 0.4f) 0xFFF1EBE0 else 0xFF15120F
                else -> r.ink
            }
            val crudo = lerp(Color(papel), Color(tinta), 0.04f).toArgb().toLong() and 0xFFFFFFFFL
            r = r.copy(paper = papel, crudo = crudo, ink = tinta, gfx = tinta, sand = 0, line = 0, edge = 0, muted = 0)
        }
        if (acentoP != 0L) r = r.copy(general = acentoP)
        return r
    }

    /** La paleta de la app (con su acento elegido), en claro o en oscuro. */
    private fun propiosPara(noche: Boolean): Tones {
        // (la de la familia, con el acento de la app que está abierta)
        val pal = if (palette.id == "familia") paletaFamilia() else palette
        return propiosDe(pal, noche)
    }
    private fun propiosDe(pal: Palette, noche: Boolean): Tones = (if (noche) pal.dark else pal.light).let { b ->
        ACCENT_COLORS.firstOrNull { it.id == accentId }?.let { a -> b.copy(general = (if (noche) a.dark else a.light) or 0xFF000000) } ?: b
    }

    /** Los colores que se ven en claro o en oscuro ([noche]). */
    fun tonosPara(noche: Boolean): Tones {
        val fondo = FONDOS.firstOrNull { it.id == fondoId }
        if (fondo != null) {
            val b = fondo.tones
            val a = ACCENT_COLORS.firstOrNull { it.id == accentId }
            return conPropios(if (a == null || accentId == "paleta" || accentId == "rojo") b else b.copy(general = (if (fondo.oscuro) a.dark else a.light) or 0xFF000000))
        }
        return conPropios(invertidoSi(invertido, TemaAzabache.tonos.takeIf { origenAtrio && !soyAzabache } ?: propiosPara(noche)))
    }

    /** Los colores que se ven ahora. */
    val tones: Tones get() = if (soyAzabache) TemaAzabache.tonos ?: propiosPara(Modo.oscuro) else tonosPara(Modo.oscuro)

    /** ¿Los colores no dependen de claro/oscuro? (los de Azabache ya vienen resueltos) */
    val coloresFijos: Boolean get() = (origenAtrio && TemaAzabache.tonos != null) || fondoId.isNotEmpty()

    private fun invertidoSi(on: Boolean, t: Tones) = if (on) invertir(t) else t

    /**
     * La letra por su nombre, o null si es una de las que van dentro de la app. En el ordenador solo van dentro las de Atrio y
     * Vitrina: las demás de Google Fonts (que en Android baja el sistema) se ven con la de Atrio.
     */
    fun family(name: String): FontFamily? {
        if (name.isBlank() || name == "Atrio") return null
        if (name == "Vitrina") return VitrinaDisplay
        return null
    }

    fun aplicar(s: SkinStyle) {
        palette = PALETTES.firstOrNull { it.id == s.palette } ?: palette
        display = s.display; body = s.body
        forma = FORMAS.firstOrNull { it.id == s.form } ?: forma
    }

    /** El estilo listo que coincide con lo elegido ahora (si es uno de la lista). */
    fun estiloActual(): SkinStyle? = STYLES.firstOrNull { it.palette == palette.id && it.display == display && it.body == body && it.form == forma.id }

    /** Azabache no sigue a nadie: sus colores son los suyos (`ajustes/azabache`, en el formato de Azabache). */
    @Volatile var soyAzabache = false

    /** Cuándo se cambió la apariencia (milisegundos): gana la más reciente entre tus dispositivos. */
    var ajustesAt = 0L

    /** La apariencia que viaja con la cuenta (la misma que la de la app en Android: `ajustes/<app>` → `skin`). */
    fun ajustesMapa(): Map<String, Any?> = mapOf("at" to ajustesAt, "skin" to mapOf(
        "paleta" to palette.id, "display" to display, "body" to body, "acento" to accentId, "escala" to escala.toLong(),
        "invertido" to invertido, "origenAtrio" to origenAtrio, "modo" to Modo.eleccion.toLong(),
        "fondoId" to fondoId, "fondoPropio" to fondoPropio, "tintaPropia" to tintaPropia, "acentoPropio" to acentoPropio))

    private fun Map<String, Any?>.texto(k: String, d: String) = (this[k] as? String) ?: d
    private fun Map<String, Any?>.num(k: String, d: Long) = (this[k] as? Number)?.toLong() ?: d
    private fun Map<String, Any?>.si(k: String, d: Boolean) = (this[k] as? Boolean) ?: d

    /** Aplica una apariencia (de la cuenta o del archivo de aquí) si es más nueva que la de ahora. Devuelve true si ha cambiado algo. */
    fun aplicarAjustes(o: Map<String, Any?>): Boolean {
        val at = o.num("at", 0L)
        if (at <= ajustesAt) return false
        @Suppress("UNCHECKED_CAST") val k = (o["skin"] as? Map<String, Any?>) ?: return false
        palette = PALETTES.firstOrNull { it.id == k.texto("paleta", "") } ?: palette
        display = k.texto("display", display).ifBlank { "Atrio" }; body = k.texto("body", body).ifBlank { "Atrio" }
        accentId = k.texto("acento", accentId).takeIf { id -> id == "paleta" || ACCENT_COLORS.any { it.id == id } } ?: "paleta"
        escala = k.num("escala", escala.toLong()).toInt().coerceIn(80, 140)
        invertido = k.si("invertido", invertido); origenAtrio = k.si("origenAtrio", origenAtrio)
        Modo.eleccion = k.num("modo", Modo.eleccion.toLong()).toInt().coerceIn(0, 2)
        fondoId = k.texto("fondoId", "").takeIf { id -> FONDOS.any { it.id == id } } ?: ""
        fondoPropio = k.num("fondoPropio", 0L); tintaPropia = k.num("tintaPropia", 0L); acentoPropio = k.num("acentoPropio", 0L)
        ajustesAt = at
        return true
    }
}

// ───── colores (los mismos nombres que en Atrio) ─────
val Paper: Color get() = Skin.tones.cPaper
val Crudo: Color get() = Skin.tones.cCrudo
val Sand: Color get() = Skin.tones.cSand
val Line: Color get() = Skin.tones.cLine
val Charcoal: Color get() = Skin.tones.cInk
val Edge: Color get() = Skin.tones.cEdge
val Muted: Color get() = Skin.tones.cMuted
/** El acento (el coral de Vitrina). */
val Acento: Color get() = Color(Skin.tones.general)
val Dorado: Color get() = Color(Skin.tones.gold)
/** Texto sobre el acento: blanco o tinta oscura, el que se lea. */
val SobreAcento: Color get() = if (Acento.luminance2() > 0.5f) Color(0xFF14151A) else Color.White
fun Color.luminance2(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

fun appColors() = if (Skin.tones.darkBg) darkColorScheme(
    primary = Charcoal, onPrimary = Crudo, secondary = Acento, onSecondary = SobreAcento,
    background = Paper, onBackground = Charcoal, surface = Crudo, onSurface = Charcoal,
    surfaceVariant = Sand, onSurfaceVariant = Charcoal, surfaceContainer = Crudo, surfaceContainerHigh = Crudo,
    surfaceContainerHighest = Sand, surfaceContainerLow = Crudo, outline = Edge, outlineVariant = Line, error = Acento
) else lightColorScheme(
    primary = Charcoal, onPrimary = Crudo, secondary = Acento, onSecondary = SobreAcento,
    background = Paper, onBackground = Charcoal, surface = Crudo, onSurface = Charcoal,
    surfaceVariant = Sand, onSurfaceVariant = Charcoal, surfaceContainer = Crudo, surfaceContainerHigh = Crudo,
    surfaceContainerHighest = Sand, surfaceContainerLow = Crudo, outline = Edge, outlineVariant = Line, error = Acento
)

// ───── letras ─────
private fun fuente(archivo: String): ByteArray = Skin::class.java.getResourceAsStream("/fuentes/$archivo")?.use { it.readBytes() } ?: ByteArray(0)
private fun f(archivo: String, peso: FontWeight, estilo: FontStyle = FontStyle.Normal) = Font("familia-$archivo-${peso.weight}-$estilo", fuente(archivo), peso, estilo)

val AtrioDisplay = FontFamily(
    f("display_regular.ttf", FontWeight.Normal), f("display_medium.ttf", FontWeight.Medium),
    f("display_semibold.ttf", FontWeight.SemiBold), f("display_semibold.ttf", FontWeight.Bold),
    f("display_italic.ttf", FontWeight.Normal, FontStyle.Italic)
)

val VitrinaDisplay = FontFamily(listOf(400, 500, 600, 700, 800).map { w -> f("bricolage.ttf", FontWeight(w)) })

val AtrioBody = FontFamily(
    f("body_regular.ttf", FontWeight.Normal), f("body_italic.ttf", FontWeight.Normal, FontStyle.Italic),
    f("body_medium.ttf", FontWeight.Medium), f("body_semibold.ttf", FontWeight.SemiBold),
    f("body_bold.ttf", FontWeight.Bold), f("body_bold.ttf", FontWeight.ExtraBold)
)

val Display: FontFamily get() = Skin.family(Skin.display) ?: AtrioDisplay
val Body: FontFamily get() = Skin.family(Skin.body) ?: AtrioBody

private val base = Typography()
private fun TextStyle.body() = copy(fontFamily = Body)
private fun TextStyle.display() = copy(fontFamily = Display, fontWeight = FontWeight.Normal)

val AppTypography: Typography get() = Typography(
    displayLarge = base.displayLarge.display(), displayMedium = base.displayMedium.display(), displaySmall = base.displaySmall.display(),
    headlineLarge = base.headlineLarge.display(), headlineMedium = base.headlineMedium.display(), headlineSmall = base.headlineSmall.display(),
    titleLarge = base.titleLarge.display(), titleMedium = base.titleMedium.body(), titleSmall = base.titleSmall.body(),
    bodyLarge = base.bodyLarge.body().copy(fontSize = 15.sp, lineHeight = 22.sp), bodyMedium = base.bodyMedium.body(), bodySmall = base.bodySmall.body(),
    labelLarge = base.labelLarge.body().copy(fontWeight = FontWeight.SemiBold), labelMedium = base.labelMedium.body().copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.body()
)

/** La raíz del tema: modo claro/oscuro, colores, letras y tamaño del texto. */
@Composable
fun TemaFamilia(content: @Composable () -> Unit) {
    val sistema = isSystemInDarkTheme()
    Modo.oscuro = when (Modo.eleccion) { 1 -> false; 2 -> true; else -> sistema }
    val d = LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalDensity provides Density(d.density, d.fontScale * Skin.escala / 100f)
    ) {
        MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
            // El texto sin color propio sale con la tinta del tema (si no, negro: se veía mal sobre un fondo oscuro)
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Charcoal, content = content)
        }
    }
}
