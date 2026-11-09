package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Jonsu 1.8: la portada del día. El cartel mide lo mismo sea lo que sea (un juego, un libro, nada) y no salta al pasar de página y volver;
 * en la tableta en horizontal también. (Antes: el carrusel «En portada», que ya no está.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
open class PortadaTest {
    open val prefijo = ""
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun hoyMin(clave: String, min: Int) = org.json.JSONObject().put(clave, org.json.JSONObject().put("movil", org.json.JSONObject().put("t", "Deadlock")
        .put("days", org.json.JSONObject().put(java.time.LocalDate.now().toString(), min * 60_000L)))).toString()

    @Test fun mismaAlturaEnTodas() {
        Mode.dark = false
        val s = Store(ctx)
        s.savePlays(listOf(
            Play(1, "Deadlock", "", "", 1, emptyList(), creator = "Valve", updated = System.currentTimeMillis()),
            Play(2, "Coiling Dragon Libro 10", "", "", 1, emptyList(), creator = "I Eat Tomatoes", updated = System.currentTimeMillis() - 1000)), 0)
        Hermanas.guardaEnlacesPublico(ctx, "hiedra", mapOf("j_dead" to 1L))
        Hermanas.guardaPublico(ctx, "tiempo_hiedra", hoyMin("j_dead", 95))
        Skin.load(ctx); Skin.applyShell(2, ctx)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { App(s, 0L, false, "") {} }
            }
        }
        rule.waitForIdle()
        foto("1")
        val c0 = rule.onNodeWithTag("cartel").fetchSemanticsNode().boundsInRoot
        assertTrue("el cartel es lo de hoy", rule.onAllNodesWithText("DEADLOCK").fetchSemanticsNodes().isNotEmpty())
        // Se pasa a la página siguiente y se vuelve: el cartel, en el mismo sitio y del mismo tamaño
        rule.onNodeWithTag("paginas").performTouchInput { swipeLeft(startX = width * 0.9f, endX = width * 0.1f, durationMillis = 250) }
        rule.waitForIdle()
        rule.onNodeWithTag("paginas").performTouchInput { swipeRight(startX = width * 0.1f, endX = width * 0.9f, durationMillis = 250) }
        rule.waitForIdle()
        foto("2")
        val c1 = rule.onNodeWithTag("cartel").fetchSemanticsNode().boundsInRoot
        assertEquals("el cartel no se mueve al pasar de página y volver", c0, c1)
        // Sin nada que contar, el cartel vacío (en su sitio)
        Hermanas.guardaPublico(ctx, "tiempo_hiedra", "{}")
        rule.runOnUiThread { Hermanas.repinta() }
        rule.waitForIdle()
        foto("3")
        assertTrue(rule.onAllNodesWithText("Aún no hay nada esta semana.").fetchSemanticsNodes().isNotEmpty())
        // (arriba del todo de la página, justo bajo las pestañas)
        assertEquals(rule.onNodeWithTag("paginas").fetchSemanticsNode().boundsInRoot.top, rule.onNodeWithTag("cartel").fetchSemanticsNode().boundsInRoot.top)
    }

    private fun foto(n: String) {
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/${prefijo}portada_igual_$n.png")
    }
}

@Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class PortadaOppoHorizontalTest : PortadaTest() { override val prefijo get() = "oppo_h_" }
