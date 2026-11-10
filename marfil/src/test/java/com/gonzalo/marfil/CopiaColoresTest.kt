package com.gonzalo.marfil

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Copiar de Atrio los colores uno a uno, y escribir el código de un color directamente. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CopiaColoresTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After fun limpia() {
        Skin.fondoId = ""; Skin.accentId = "paleta"; Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L; Skin.escala = 100
        Skin.display = "Atrio"; Skin.body = "Atrio"; Modo.eleccion = 0; Skin.origenAtrio = true; Skin.invertido = true; Skin.ajustesAt = 0L
    }

    @Test fun elCodigoDeUnColor() {
        assertEquals(Color(0xFF4F8A5B), colorDeCodigo("#4F8A5B")); assertEquals(Color(0xFF4F8A5B), colorDeCodigo("4f8a5b")); assertEquals(Color(0xFF4F8A5B), colorDeCodigo(" #4f8a5b "))
        assertEquals("tres cifras", Color(0xFFAABBCC), colorDeCodigo("#abc")); assertEquals("con opacidad delante", Color(0xFF123456), colorDeCodigo("#FF123456"))
        assertEquals(Color(0xFF123456), colorDeCodigo("0xFF123456"))
        assertNull(colorDeCodigo("#12345")); assertNull(colorDeCodigo("#GGGGGG")); assertNull(colorDeCodigo("")); assertNull(colorDeCodigo("#"))
    }

    @Test fun escribirElCodigoAplicaElColorExacto() {
        Skin.cargar(ctx)
        var cambiado = 0L; var guardado = 0
        rule.mainClock.autoAdvance = false   // (el cursor parpadeante del campo no deja nunca «quieto» a Compose en las pruebas)
        rule.setContent { MarfilTheme { Column { SelectorColor("Fondo", cambiado, Paper, { cambiado = it }, { guardado++ }) } } }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Fondo").performClick(); rule.mainClock.advanceTimeBy(100)
        val campo = rule.onNode(hasSetTextAction())
        campo.performTextReplacement("#102030"); rule.mainClock.advanceTimeBy(100)
        assertEquals(0xFF102030L, cambiado); assertTrue(guardado >= 1)
        // En minúsculas y sin #
        campo.performTextReplacement("a1b2c3"); rule.mainClock.advanceTimeBy(100)
        assertEquals(0xFFA1B2C3L, cambiado)
        // Mientras no es un color válido, no cambia nada (y se puede seguir escribiendo)
        val antes = cambiado
        campo.performTextReplacement("#12"); rule.mainClock.advanceTimeBy(100)
        assertEquals(antes, cambiado)
        campo.performTextReplacement("#1234567"); rule.mainClock.advanceTimeBy(100)
        assertEquals("siete cifras no valen", antes, cambiado)
    }
}
