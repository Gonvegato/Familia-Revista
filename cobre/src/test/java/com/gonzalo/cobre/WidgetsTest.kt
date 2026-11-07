package com.gonzalo.cobre

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Los widgets de Nut (de momento, la cabecera), dibujados en el ordenador y probados con distintos temas. Con
 * -Proborazzi.test.record=true deja las vistas previas en build/outputs/widgets/ (las w_prev_* van luego a res/drawable-nodpi).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun siembra() {
        Skin.cargar(ctx)
        Skin.origenAtrio = false; Skin.invertido = false; Skin.fondoId = ""; Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L
    }

    @After fun limpia() { Skin.fondoId = ""; Skin.invertido = false; Skin.display = "Atrio"; Skin.body = "Atrio" }

    private val tamanos = mapOf(WTipo.MARCA to (256f to 64f), WTipo.MARCA_PAPEL to (264f to 132f))
    private val otros = listOf(330f to 165f, 160f to 160f, 150f to 260f, 330f to 80f, 400f to 200f, 110f to 40f, 330f to 100f)

    @Test fun laCabeceraAbreNut() = runBlocking {
        siembra()
        for (t in WTipo.entries) {
            val z = Widgets.componer(ctx, t, 1, 330f, 80f).zonas.single()
            assertEquals("Abrir Nut", z.desc); assertNotNull(z.toque)
        }
        val sola = Widgets.componer(ctx, WTipo.MARCA, 1, 256f, 64f).bitmap(ctx, 256f, 64f, false, 1f)
        assertEquals("la marca sola es transparente", 0, sola.getPixel(2, 2) ushr 24)
        val papel = Widgets.componer(ctx, WTipo.MARCA_PAPEL, 1, 264f, 132f).bitmap(ctx, 264f, 132f, false, 1f)
        assertEquals("en papel, el papel del tema", Hoja(ctx, 10f, 10f, false, 1f).papel, papel.getPixel(papel.width / 2, 12))
    }

    @Test fun cadaTipoTieneSuReceiver() {
        // (así un tipo nuevo no se queda sin clase: el manifiesto lo comprueba el compilador de recursos)
        assertEquals(WTipo.entries.size, WTipo.entries.map { Widgets.clase(it) }.toSet().size)
    }

    @Test fun dibujaTodosSinFallar() {
        siembra()
        for (t in WTipo.entries) for ((w, h) in otros + tamanos.values) for (noche in listOf(false, true)) {
            val pag = runBlocking { Widgets.componer(ctx, t, 1, w, h) }
            val bm = pag.bitmap(ctx, w, h, noche, 1f)
            assertTrue("$t ${w}x$h sale con tamaño", bm.width > 0 && bm.height > 0)
        }
    }

    @Test fun losWidgetsUsanLosColoresDeApariencia() {
        siembra()
        val h = Hoja(ctx, 10f, 10f, false, 1f)
        assertNotEquals("claro y oscuro tienen papeles distintos", h.papel, Hoja(ctx, 10f, 10f, true, 1f).papel)
        Skin.invertido = true
        assertEquals("invertido: el papel claro pasa a ser la tinta", h.tinta, Hoja(ctx, 10f, 10f, false, 1f).papel)
        Skin.invertido = false
        Skin.fondoId = FONDOS.first().id
        assertEquals("con un fondo, un solo dibujo", false, Widgets.noche())
    }

    private fun foto(nombre: String, tipo: WTipo, w: Float, h: Float, noche: Boolean, dir: String = "build/outputs/widgets") {
        runBlocking { Widgets.cargarLetras(ctx) }
        val pag = runBlocking { Widgets.componer(ctx, tipo, 1, w, h) }
        val bm = pag.bitmap(ctx, w, h, noche, 2f)
        File(dir).mkdirs()
        File("$dir/$nombre.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun dibujaLasVistasPrevias() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        for ((t, s) in tamanos) foto("w_prev_${t.name.lowercase()}", t, s.first, s.second, false)
        for ((t, s) in tamanos) foto("oscuro_${t.name.lowercase()}", t, s.first, s.second, true)
        foto("ancho_marca", WTipo.MARCA, 330f, 100f, false)
        foto("alto_marcapapel", WTipo.MARCA_PAPEL, 330f, 165f, false)
        foto("bajo_marcapapel", WTipo.MARCA_PAPEL, 330f, 70f, false)
        Skin.display = "Vitrina"
        for ((t, s) in tamanos) foto("vitrina_${t.name.lowercase()}", t, s.first, s.second, false)
        Skin.display = "Atrio"
    }
}
