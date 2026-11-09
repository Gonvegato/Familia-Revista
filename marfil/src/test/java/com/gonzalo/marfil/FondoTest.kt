package com.gonzalo.marfil

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.nucleo.Nodo
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/** El fondo de un libro (0.19.0): se pone y se quita como la portada, viaja con la cuenta y en la copia, y lee el de Jonsu si aquí no hay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FondoTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun png(color: Int, w: Int, h: Int): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private fun inicio(): Libro {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Biblioteca.borrados.clear()
        return Libro("abc", "Dune", "Frank Herbert", archivo = "dune.epub", anadido = 1L).also { Biblioteca.fijaParaPruebas(listOf(it)) }
    }

    @Test fun ponerYQuitarElFondo() {
        val l = inicio()
        val a = Portadas.ponerFondo(ctx, l, png(0xFFAA0000.toInt(), 1200, 400), url = "https://x/fondo.jpg")!!
        assertTrue(File(a.fondo).exists()); assertEquals(a.fondo, Biblioteca.por("abc")!!.fondo)
        assertEquals("https://x/fondo.jpg", Biblioteca.por("abc")!!.fondoUrl)
        assertTrue("el fondo es una imagen de la cuenta", Biblioteca.hashFondo("abc").isNotBlank())
        assertEquals("la portada no se toca", "", Biblioteca.por("abc")!!.portada)
        val q = Portadas.quitarFondo(ctx, Biblioteca.por("abc")!!)
        assertEquals("", q.fondo); assertEquals("quitado también en la cuenta", "", Biblioteca.hashFondo("abc"))
    }

    @Test fun laPortadaComoFondo() {
        val l = inicio()
        val conPortada = Portadas.poner(ctx, l, png(0xFF00AA00.toInt(), 200, 300))!!
        val f = Portadas.portadaComoFondo(ctx, conPortada)!!
        assertTrue(File(f.fondo).exists())
    }

    @Test fun unFondoGrandeSeReduceSinPerderLoAncho() {
        val r = Portadas.reducirFondo(png(0xFF123456.toInt(), 4000, 1500))!!
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(r, 0, r.size, o)
        assertTrue("${o.outWidth}×${o.outHeight}", o.outWidth in 1000..1600 && o.outHeight <= 1000 && o.outWidth > o.outHeight * 2)
        assertNull("una página que no es imagen no vale", Portadas.reducirFondo("<html>".toByteArray() + ByteArray(2000)))
    }

    @Test fun viajaEnLaCopiaYUnaCopiaViejaSigueValiendo() {
        val l = Libro("l1", "Dune", "Frank Herbert", fondo = "/x/f.jpg", fondoUrl = "https://x/f.jpg")
        val r = Biblioteca.aLibro(Biblioteca.aJson(l))
        assertEquals("/x/f.jpg", r.fondo); assertEquals("https://x/f.jpg", r.fondoUrl)
        val vieja = JSONObject().put("id", "v").put("titulo", "Ubik").put("autor", "Philip K. Dick").put("portada", "/x/p.jpg")
        val v = Biblioteca.aLibro(vieja)
        assertEquals("", v.fondo); assertEquals("", v.fondoUrl); assertEquals("/x/p.jpg", v.portada)
    }

    @Test fun elFondoDeLaCuentaYElDeJonsu() {
        inicio()
        // Sin fondo en Seshat: el que se le puso en Jonsu (si es de internet)
        val az = JSONObject().put("hero", "https://img/dune-ancho.jpg").put("minutes", 30).toString()
        assertEquals("https://img/dune-ancho.jpg" to "https://img/dune-ancho.jpg", Biblioteca.fondoDe(ctx, Nodo("k", mapOf("t" to "Dune", "az" to az))))
        // Un fondo de Jonsu que es un archivo de su móvil no sirve aquí
        val azLocal = JSONObject().put("hero", "/data/user/0/com.gonzalo.gamerate/files/h.jpg").toString()
        assertEquals("" to "", Biblioteca.fondoDe(ctx, Nodo("k", mapOf("t" to "Dune", "az" to azLocal))))
        // Puesto aquí y aún no bajado: la dirección de la que salió
        assertEquals("https://x/f.jpg" to "https://x/f.jpg", Biblioteca.fondoDe(ctx, Nodo("k", mapOf("t" to "Dune", "fondo" to "h123", "fu" to "https://x/f.jpg", "az" to az))))
        // Quitado aquí a propósito: no vuelve el de Jonsu
        assertEquals("" to "", Biblioteca.fondoDe(ctx, Nodo("k", mapOf("t" to "Dune", "fondo" to Biblioteca.SIN_FONDO, "az" to az))))
    }
}
