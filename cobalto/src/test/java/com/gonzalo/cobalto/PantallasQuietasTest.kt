package com.gonzalo.cobalto

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Ninguna pantalla se queda recomponiéndose sola cuando no pasa nada (eso gasta batería y CPU mientras la tienes abierta, y
 * en las pruebas no deja que la pantalla se quede quieta nunca). Se mira cuántas composiciones hace cada pantalla en un
 * segundo sin tocar nada: tienen que ser ninguna.
 *
 * El reloj de la prueba va a mano todo el rato (y solo se «toca» con la acción de pulsar, sin desplazar nada): si una
 * pantalla no se quedara quieta nunca, esperar a que lo hiciera dejaría la prueba colgada para siempre.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
open class PantallasQuietasTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun cambios(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

    private fun pasa(frames: Int) = repeat(frames) { rule.mainClock.advanceTimeByFrame() }

    /** Deja que la pantalla se asiente (animaciones de entrada…) y cuenta lo que se recompone en el segundo siguiente. */
    private fun quieta(nombre: String, inquietas: MutableList<String>) {
        pasa(45)
        val antes = cambios()
        pasa(60)
        val n = cambios() - antes
        if (n > 0) inquietas += "$nombre ($n)"
    }

    /** Pulsar (la acción de pulsar de lo que lleva ese texto o esa descripción): vale aunque no se vea entero en la pantalla. */
    private fun toca(texto: String, descripcion: Boolean = false) {
        val n = if (descripcion) rule.onAllNodesWithContentDescription(texto) else rule.onAllNodesWithText(texto)
        n[0].performSemanticsAction(SemanticsActions.OnClick)
        pasa(20)
    }

    @Test fun ningunaPantallaSeRecomponeSola() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Modo.eleccion = 1
        Escritos.reiniciaParaPruebas(); File(ctx.filesDir, "escritos").deleteRecursively()
        val e = Escritos.get(ctx)
        Ejemplo.siembra(e); Ejemplo.estudio(e)
        rule.mainClock.autoAdvance = false
        rule.setContent { CobaltoTheme { Surface(Modifier.fillMaxSize(), color = Paper) { CobaltoApp() } } }
        pasa(5)
        assertTrue("no hay recomposiciones que contar", Recomposer.runningRecomposers.value.isNotEmpty())
        val inquietas = mutableListOf<String>()
        quieta("portada", inquietas)
        toca("ESCRIBIENDO"); quieta("Escribiendo", inquietas)
        toca("La ciudad de cristal"); quieta("manuscrito", inquietas)
        for (t in listOf("FICHAS", "ÁRBOL", "NOTAS", "LÍNEA", "CIFRAS", "GUÍA", "MANUSCRITO")) { toca(t); quieta("obra · $t", inquietas) }
        toca("FICHAS"); toca("Elena"); quieta("ficha de Elena (para leer)", inquietas)
        toca("Editar", descripcion = true); quieta("ficha de Elena (editando)", inquietas)
        toca("GUARDAR"); quieta("ficha de Elena (otra vez para leer)", inquietas)
        toca("← VOLVER")
        toca("MANUSCRITO"); toca("Donde empieza todo"); quieta("capítulo", inquietas)
        toca("El despertar"); quieta("editor", inquietas)
        toca("Concentración", descripcion = true); quieta("editor en concentración", inquietas)
        toca("SALIR")
        toca("La página", descripcion = true); quieta("la página (letra, tamaño…)", inquietas)
        assertTrue("se recomponen solas: $inquietas", inquietas.isEmpty())
    }
}

/** Lo mismo en la tableta (la OPPO Pad Mini, en horizontal): allí cabe el panel al lado y los titulares son más grandes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class PantallasQuietasTabletaTest : PantallasQuietasTest()
