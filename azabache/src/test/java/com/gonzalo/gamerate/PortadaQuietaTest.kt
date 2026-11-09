package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Jonsu 1.8: al bajar por la portada, la cabecera se recoge poco a poco y el nombre encoge con ella (nunca se corta ni deja una banda vacía);
 * al subir, vuelve. Fotograma a fotograma, la cabecera nunca crece mientras se baja.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PortadaQuietaTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun laCabeceraSeRecogeSinSaltos() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val s = Store(ctx)
        s.savePlays(listOf(
            Play(201, "Cyberpunk 2077", "", "", 1, emptyList(), creator = "CD Projekt Red", minutes = 728, updated = System.currentTimeMillis()),
            Play(202, "Kingdom Come: Deliverance II", "", "", 1, emptyList(), creator = "Warhorse", updated = System.currentTimeMillis() - 1000),
            Play(203, "Hellblade: Senua's Sacrifice", "", "", 1, emptyList(), creator = "Ninja Theory", updated = System.currentTimeMillis() - 2000),
        ), 0)
        Skin.load(ctx); Mode.dark = true; Mode.themeMode = 2; Skin.applyShell(2, ctx)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { App(s, 0L, false, "") {} }
            }
        }
        rule.waitForIdle()
        val alto = { Math.round(rule.onNodeWithTag("cabecera").fetchSemanticsNode().boundsInRoot.height) }
        val inicial = alto()
        val altos = mutableListOf(inicial)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("paginas").performTouchInput { down(center.copy(y = height * 0.8f)) }
        repeat(30) {
            rule.onNodeWithTag("paginas").performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, -height * 0.02f)) }
            rule.mainClock.advanceTimeByFrame(); altos += alto()
        }
        rule.onNodeWithTag("paginas").performTouchInput { up() }
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue("la cabecera nunca crece al bajar: $altos", altos.zipWithNext().all { (a, b) -> b <= a })
        assertTrue("la cabecera se recoge: $altos", altos.last() < inicial)
        // Recogida del todo, queda la fila pequeña con «JONSU.» (sin banda vacía): no menos de una línea
        assertTrue(rule.onAllNodesWithText("JONSU.").fetchSemanticsNodes().isNotEmpty())
    }
}
