package com.gonzalo.ptah

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Guardar de verdad, tocando la app como el usuario (lo que en el OPPO «no se guardaba»): una plantilla → cambiar el fondo, la tinta, la
 * letra y el tamaño del texto → «Guardar diseño» → el diseño guardado lleva los cambios (en la app y en el disco); después, editar ese
 * diseño desde su ficha y guardarlo; y el widget que lo lleva se vuelve a pintar con lo nuevo.
 */
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w411dp-h1400dp-xhdpi")
class GuardarTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @After fun suelta() { Widgets.datos = null }

    private fun toca(t: String) {
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        for (desunido in listOf(true, false)) {
            val todos = rule.onAllNodesWithText(t, substring = !desunido, useUnmergedTree = desunido)
            for (i in 0 until todos.fetchSemanticsNodes().size) {
                val x = todos[i]
                runCatching { x.performScrollTo() }
                if (runCatching { x.performClick() }.isSuccess) { rule.waitForIdle(); return }
            }
        }
        error("No puedo tocar «$t»")
    }

    /** El último que hay con ese texto (el del diálogo de encima, si lo hay). */
    private fun tocaUltimo(t: String) {
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        val todos = rule.onAllNodesWithText(t, useUnmergedTree = true)
        val n = todos.fetchSemanticsNodes().size
        check(n > 0) { "No hay «$t»" }
        todos[n - 1].performClick(); rule.waitForIdle()
    }

    private fun imagenes(v: View): List<android.graphics.Bitmap> =
        ((v as? ImageView)?.drawable as? BitmapDrawable)?.bitmap?.let { listOf(it) }.orEmpty() +
            ((v as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { imagenes(g.getChildAt(it)) } } ?: emptyList())

    /** El flujo entero (todo en este móvil). */
    private fun flujo() {
        ctx.getSharedPreferences("widgets_migracion", Context.MODE_PRIVATE).edit().putBoolean("hecha", true).apply()
        iniciar(ctx)
        Widgets.datos = { DatosDeMentira(it) }
        rule.setContent { WidgetsTheme { Surface(Modifier.fillMaxSize()) { WidgetsApp() } } }

        // Una plantilla → el fondo de un color, la tinta del acento, la letra de títulos Vitrina y el texto al 140 %
        toca("Usar esta →")
        toca("Colores"); toca("Color")
        toca("Tinta de tu estilo"); tocaUltimo("Acento"); toca("Ponerlo")
        toca("Letras"); toca("De los títulos"); toca("Vitrina")
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0].performSemanticsAction(SemanticsActions.SetProgress) { it(1.4f) }
        toca("Guardar diseño")
        fun comprueba(d: Diseno?, que: String) {
            assertNotNull(que, d)
            assertEquals(que, TipoFondo.COLOR, d!!.estilo.fondo); assertEquals(que, Col.ACENTO, d.estilo.tinta)
            assertEquals(que, "Vitrina", d.estilo.letraTitulos); assertEquals(que, 1.4f, d.estilo.escala, 0.01f)
        }
        val guardado = Disenos.todos(ctx).single()
        comprueba(guardado, "en la app")
        // De disco: lo que hay en las preferencias (lo que se lee al volver a abrir la app)
        comprueba(Diseno.deJson(ctx.getSharedPreferences(Disenos.GRUPO.archivo, Context.MODE_PRIVATE).getString(guardado.id, null)), "en el disco")

        // Ese diseño en un widget de la pantalla de inicio
        val mgr = shadowOf(AppWidgetManager.getInstance(ctx))
        val id = mgr.createWidget(WPtah::class.java, R.layout.w_marco)
        Widgets.asigna(ctx, id, guardado.id)
        kotlinx.coroutines.runBlocking { Widgets.pintarUno(ctx, id) }
        val antes = imagenes(mgr.getViewFor(id)).map { it.copy(it.config ?: android.graphics.Bitmap.Config.ARGB_8888, false) }

        // Su ficha → Editar → un degradado → Guardar diseño
        toca("Aún no está en tu pantalla").also { } // (o «En tu pantalla de inicio: 1»: la tarjeta del diseño)
        toca("Editar")
        toca("Colores"); toca("Degradado")
        toca("Guardar diseño")
        assertEquals(TipoFondo.DEGRADADO, Disenos.de(ctx, guardado.id)!!.estilo.fondo)
        assertEquals("sigue con la letra de antes", "Vitrina", Disenos.de(ctx, guardado.id)!!.estilo.letraTitulos)
        // El widget se ha vuelto a pintar con lo nuevo
        val hasta = System.currentTimeMillis() + 10_000
        var despues = imagenes(mgr.getViewFor(id))
        while (System.currentTimeMillis() < hasta && antes.zip(despues).all { (a, b) -> a.sameAs(b) }) { Thread.sleep(200); shadowOf(Looper.getMainLooper()).idle(); despues = imagenes(mgr.getViewFor(id)) }
        assertFalse("el widget lleva el diseño nuevo", antes.zip(despues).all { (a, b) -> a.sameAs(b) })
    }

    @Test fun guardaYRepinta() = flujo()
}
