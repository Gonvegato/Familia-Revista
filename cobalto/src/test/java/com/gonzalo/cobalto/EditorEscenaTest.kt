@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gonzalo.cobalto

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** El editor guarda lo que escribes donde toca: al cambiar de escena sin salir, y aunque llegue a la vez un cambio de otro dispositivo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class EditorEscenaTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun obra(): Triple<Escritos, Doc, Doc> {
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        Escritos.reiniciaParaPruebas(); File(ctx.filesDir, "escritos").deleteRecursively()
        val e = Escritos.get(ctx)
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Prueba") }
        val c = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val a = e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("titulo", "Primera"); put("texto", "Texto de la primera.") }
        val b = e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("titulo", "Segunda"); put("texto", "Texto de la segunda.") }
        return Triple(e, a, b)
    }

    @Test fun cambiarDeEscenaNoPisaLaAnterior() {
        val (e, a, b) = obra()
        var eid by mutableStateOf(a.id)
        rule.setContent { CobaltoTheme { Surface(Modifier.fillMaxSize(), color = Paper) { EscribirEditor(eid, onBack = {}, abrirEscena = { eid = it }) } } }
        rule.waitForIdle()
        rule.onNodeWithTag("pagina").performClick()
        rule.onNodeWithTag("pagina").performTextInput(" Y algo más.")
        // A la siguiente escena desde el final de la página, sin esperar al guardado
        rule.onNodeWithText("SIGUIENTE →").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(b.id, eid)
        assertEquals("la primera, con lo escrito", "Texto de la primera. Y algo más.", e.get(a.id)!!.s("texto"))
        assertEquals("la segunda, intacta", "Texto de la segunda.", e.get(b.id)!!.s("texto"))
        // Y de vuelta: la primera sigue bien
        rule.onNodeWithText("← ESCENA ANTERIOR").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(a.id, eid)
        assertEquals("Texto de la segunda.", e.get(b.id)!!.s("texto"))
        assertEquals("Texto de la primera. Y algo más.", e.get(a.id)!!.s("texto"))
    }

    @Test fun loQueLlegaDeFueraSeJuntaConLoQueEscribes() {
        val (e, a, _) = obra()
        e.edita(a.id) { put("texto", "Primer párrafo.\n\nSegundo párrafo.") }
        rule.setContent { CobaltoTheme { Surface(Modifier.fillMaxSize(), color = Paper) { EscribirEditor(a.id, onBack = {}, abrirEscena = {}) } } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("pagina").performClick()
        rule.onNodeWithTag("pagina").performTextInputSelection(TextRange("Primer párrafo.\n\nSegundo párrafo.".length))
        rule.onNodeWithTag("pagina").performTextInput(" Y sigo aquí.")
        // Antes de que se guarde (0,6 s), otro dispositivo cambia el primer párrafo
        rule.mainClock.advanceTimeBy(100)
        val fuera = JSONObject(e.get(a.id)!!.o.toString()).put("texto", "Primer párrafo, cambiado fuera.\n\nSegundo párrafo.").put("u", e.get(a.id)!!.u + 5)
        e.pon(fuera)
        rule.mainClock.advanceTimeBy(2000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals("se quedan los dos cambios", "Primer párrafo, cambiado fuera.\n\nSegundo párrafo. Y sigo aquí.", e.get(a.id)!!.s("texto"))
        assertTrue("sin choques, sin borrador", e.borradores(a.id).isEmpty())
    }
}
