@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
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
open class CapturasTest {
    /** Delante del nombre de cada captura (las de la tableta llevan el suyo). */
    open val prefijo: String get() = ""
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun siembra(vacia: Boolean = false) {
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        Escritos.reiniciaParaPruebas(); File(ctx.filesDir, "escritos").deleteRecursively()
        val e = Escritos.get(ctx)
        if (!vacia) { Ejemplo.siembra(e); Ejemplo.estudio(e) }
    }

    /** Lo de encima con ese texto (la ficha de la obra sigue compuesta debajo del estudio). */
    private fun ultimo(t: String) = rule.onAllNodesWithText(t).let { n -> n[n.fetchSemanticsNodes().size - 1] }

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$prefijo$nombre.png")
    }

    private fun montar(oscuro: Boolean) {
        Modo.eleccion = if (oscuro) 2 else 1
        rule.setContent { CobaltoTheme { Surface(Modifier.fillMaxSize(), color = Paper) { CobaltoApp() } } }
    }

    @Test fun portada() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false); foto("1_portada")
        rule.onNodeWithText("También escribes", useUnmergedTree = true).performScrollTo(); foto("1b_portada_abajo")
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
        rule.onAllNodesWithText("La ciudad de cristal", useUnmergedTree = true)[0].performClick(); rule.waitForIdle()
        ultimo("MANUSCRITO →").performClick(); foto("4_obra_manuscrito")
        ultimo("FICHAS").performScrollTo().performClick(); rule.waitForIdle(); foto("4b_obra_fichas")
        ultimo("NOTAS").performScrollTo().performClick(); rule.waitForIdle(); foto("4c_obra_notas")
        ultimo("ÁRBOL").performScrollTo().performClick(); rule.waitForIdle(); foto("4e_obra_arbol")
        ultimo("LÍNEA").performScrollTo().performClick(); rule.waitForIdle(); foto("4f_obra_linea")
        ultimo("CIFRAS").performScrollTo().performClick(); rule.waitForIdle(); foto("4g_obra_cifras")
        ultimo("GUÍA").performScrollTo().performClick(); rule.waitForIdle(); foto("4h_obra_guia")
        ultimo("MANUSCRITO").performScrollTo().performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Donde empieza todo")[0].performClick(); rule.waitForIdle(); foto("4i_capitulo")
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

    private fun alEditor() {
        rule.onNodeWithText("SEGUIR ESCRIBIENDO").performClick(); rule.waitForIdle()
    }

    /** El cursor en [palabra] (la vez [vez] que sale) de la escena abierta. */
    private fun cursorEn(palabra: String, vez: Int = 0) {
        val e = Escritos.get(ctx)
        val t = e.ultimaEscena()!!.s("texto")
        var i = -1; repeat(vez + 1) { i = t.indexOf(palabra, i + 1) }
        rule.onNodeWithTag("pagina").performClick()
        rule.onNodeWithTag("pagina").performTextInputSelection(TextRange(i + 2))
    }

    @Test fun editorFichaYBuscar() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false); alEditor()
        cursorEn("Elena"); foto("7_editor_ficha")
        rule.onNodeWithContentDescription("Buscar").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Buscar en la escena").performTextInput("Elena"); foto("7b_editor_buscar")
        rule.onAllNodesWithText("EN LA OBRA", substring = true)[0].performClick(); foto("7c_buscar_en_la_obra")
    }

    @Test fun editorPagina() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false); alEditor()
        rule.onNodeWithContentDescription("La página").performClick(); foto("7d_la_pagina")
    }

    @Test fun editorConcentracion() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false); alEditor()
        rule.onNodeWithContentDescription("Concentración").performClick(); rule.waitForIdle()
        cursorEn("silencio"); foto("7e_concentracion")
    }

    @Test fun editorComparar() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false); alEditor()
        rule.onNodeWithContentDescription("Panel de la escena").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("BORRADORES")[0].performClick(); rule.waitForIdle(); foto("7f_panel_borradores")
        rule.onAllNodesWithText("COMPARAR Y MEZCLAR")[0].performScrollTo().performClick(); foto("7g_comparar")
    }

    @Test fun leerFichaYNota() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("ESCRIBIENDO").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("La ciudad de cristal", useUnmergedTree = true)[0].performClick(); rule.waitForIdle()
        ultimo("MANUSCRITO →").performClick(); rule.waitForIdle()
        ultimo("FICHAS").performScrollTo().performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Elena")[0].performClick(); foto("8_ficha_leer")
        rule.onAllNodesWithText("SALE EN")[0].performScrollTo(); foto("8b_ficha_leer_abajo")
        rule.onNodeWithContentDescription("Editar").performClick(); foto("8c_ficha_editar")
        rule.onAllNodesWithText("GUARDAR")[0].performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Cerrar").performClick(); rule.waitForIdle()
        ultimo("NOTAS").performScrollTo().performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Cómo se afina un clavicordio")[0].performClick(); foto("8d_nota_leer")
    }

    @Test fun vacia() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(vacia = true); montar(false); foto("6_portada_vacia")
    }
}
