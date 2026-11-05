package com.gonzalo.cobalto

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
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Capturas de la interfaz en el ordenador, con obras de ejemplo (solo con -Proborazzi.test.record=true). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun siembra(vacia: Boolean = false) {
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        Escritos.reiniciaParaPruebas(); File(ctx.filesDir, "escritos").deleteRecursively()
        val e = Escritos.get(ctx)
        if (!vacia) Ejemplo.siembra(e)
    }

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    private fun montar(oscuro: Boolean) {
        Modo.eleccion = if (oscuro) 2 else 1
        rule.setContent { CobaltoTheme { Surface(Modifier.fillMaxSize(), color = Paper) { CobaltoApp() } } }
    }

    @Test fun portada() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false); foto("1_portada")
        rule.onNodeWithText("Sobre la mesa".uppercase(), useUnmergedTree = true).performScrollTo(); foto("1b_portada_abajo")
    }

    @Test fun portadaOscuro() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(true); foto("2_portada_oscuro")
    }

    @Test fun secciones() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("ESCRIBIENDO").performClick(); foto("3_escribiendo")
        rule.onAllNodesWithText("EN PAUSA")[0].performClick(); rule.waitForIdle(); foto("3b_en_pausa")
        rule.onAllNodesWithText("ARCHIVO")[0].performScrollTo().performClick(); rule.waitForIdle(); foto("3c_archivo")
    }

    @Test fun estudio() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("ESCRIBIENDO").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("La ciudad de cristal", useUnmergedTree = true)[0].performClick(); foto("4_obra_manuscrito")
        rule.onAllNodesWithText("FICHAS")[0].performScrollTo().performClick(); rule.waitForIdle(); foto("4b_obra_fichas")
        rule.onAllNodesWithText("NOTAS")[0].performScrollTo().performClick(); rule.waitForIdle(); foto("4c_obra_notas")
    }

    @Test fun editor() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("SEGUIR ESCRIBIENDO").performClick(); foto("4d_editor")
    }

    @Test fun ajustes() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithContentDescription("Ajustes").performClick(); foto("5_ajustes")
        rule.onNodeWithText("ESCRITURA").performClick(); rule.waitForIdle(); foto("5b_ajustes_escritura")
        rule.onNodeWithText("CUENTA").performClick(); rule.waitForIdle(); foto("5c_ajustes_cuenta")
        rule.onNodeWithText("AVANZADO").performClick(); rule.waitForIdle(); foto("5d_ajustes_avanzado")
    }

    @Test fun vacia() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(vacia = true); montar(false); foto("6_portada_vacia")
    }
}
