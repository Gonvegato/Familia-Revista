package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Las pantallas principales en la OPPO Pad Mini: 8,8", 2520 × 1680 px (3:2) a 344 ppi, que Android ve como 1260 × 840 dp (densidad 2,0),
 * tumbada y de pie (como en Azabache, CapturasOppoTest). Solo al grabar capturas.
 */
abstract class CapturasTabletBase {
    abstract val prefijo: String
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$prefijo$nombre.png")
    }

    private fun montar() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = 1
        rule.setContent { HiedraTheme { Surface(Modifier.fillMaxSize(), color = Paper) { HiedraApp() } } }
    }

    private fun pulsar(texto: String) { val n = rule.onNodeWithText(texto); runCatching { n.performScrollTo() }; n.performClick(); rule.waitForIdle() }

    @Test fun pantallas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        CapturasTest().siembra()
        montar()
        foto("t1_portada")
        rule.onNodeWithText("MIS ELEGIDOS").performScrollTo(); foto("t2_estantes")
        rule.onAllNodesWithText("JUGANDO")[0].performScrollTo().performClick(); rule.waitForIdle(); foto("t3_jugando")
        rule.onAllNodesWithText("Pokémon Platino", substring = true, useUnmergedTree = true)[0].performClick(); foto("t4_ficha")
        pulsar("← VOLVER"); pulsar("← BASTET")
        rule.onNodeWithContentDescription("Ajustes").performClick()
        rule.onNodeWithText("JUEGOS").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Sistemas y emuladores", substring = true).performScrollTo().performClick(); foto("t5_sistemas")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class CapturasTabletHorizontalTest : CapturasTabletBase() { override val prefijo = "oppo_h_" }

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w840dp-h1260dp-port-xhdpi")
class CapturasTabletVerticalTest : CapturasTabletBase() { override val prefijo = "oppo_v_" }
