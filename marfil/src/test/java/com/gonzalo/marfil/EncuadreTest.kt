package com.gonzalo.marfil

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/** Encuadrar la portada y el fondo (0.20.0): la imagen se guarda entera y el encuadre aparte; se aplica igual en huecos de otra forma. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncuadreTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun png(color: Int, w: Int, h: Int): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private fun inicio(): Libro {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Biblioteca.borrados.clear()
        return Libro("abc", "Dune", "Frank Herbert", archivo = "dune.epub", anadido = 1L).also { Biblioteca.fijaParaPruebas(listOf(it)) }
    }

    @Test fun sinEncuadreLaImagenVaCentradaYCubreElHueco() {
        // Una imagen ancha (2000×1000) en un hueco 2:3 (200×300): cubre por el alto y se ve su centro
        val (x, y, dw, dh) = colocarEncuadre(200f, 300f, 2000f, 1000f, Encuadre()).toList()
        assertEquals(600f, dw, 0.01f); assertEquals(300f, dh, 0.01f)
        assertEquals(-200f, x, 0.01f); assertEquals(0f, y, 0.01f)
    }

    @Test fun elPuntoElegidoQuedaEnElCentroYNuncaHayBordeVacio() {
        // El punto (0,3; 0,5) de la imagen, en el centro del hueco
        val (x, _, dw, _) = colocarEncuadre(200f, 300f, 2000f, 1000f, Encuadre(1f, 0.3f, 0.5f)).toList()
        assertEquals(100f, x + 0.3f * dw, 0.01f)
        // Pedir un punto del borde se ajusta: la imagen sigue cubriendo el hueco (no queda hueco a la izquierda)
        val (x2, _, dw2, _) = colocarEncuadre(200f, 300f, 2000f, 1000f, Encuadre(1f, 0f, 0.5f)).toList()
        assertEquals(0f, x2, 0.01f); assertTrue(x2 + dw2 >= 200f)
        // Y el zoom nunca baja de 1 (la imagen no se hace más pequeña que el hueco)
        assertEquals(1f, Encuadre(0.3f).ajustado(200f, 300f, 2000f, 1000f).zoom, 0f)
    }

    @Test fun elMismoEncuadreValeParaElCartelYLaFicha() {
        // El fondo se encuadra con la forma del cartel (411×470) y la ficha (411×300) enseña lo centrado en el mismo punto
        val e = Encuadre(1.5f, 0.7f, 0.4f)
        val cartel = colocarEncuadre(411f, 470f, 1600f, 1000f, e)
        val ficha = colocarEncuadre(411f, 300f, 1600f, 1000f, e)
        fun centro(r: FloatArray, w: Float, h: Float) = ((w / 2 - r[0]) / r[2]) to ((h / 2 - r[1]) / r[3])
        assertEquals(0.7f, centro(cartel, 411f, 470f).first, 0.01f); assertEquals(0.4f, centro(cartel, 411f, 470f).second, 0.01f)
        assertEquals(0.7f, centro(ficha, 411f, 300f).first, 0.01f); assertEquals(0.4f, centro(ficha, 411f, 300f).second, 0.01f)
    }

    @Test fun recortarParaLosWidgets() {
        val b = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        assertSame("sin encuadre, la imagen tal cual", b, recortarEncuadre(b, Encuadre()))
        val r = recortarEncuadre(b, Encuadre(2f, 0.25f, 0.5f))
        // A 2:3, con zoom 2: un trozo de un tercio de ancho y la mitad de alto
        assertEquals(333f, r.width.toFloat(), 2f); assertEquals(500f, r.height.toFloat(), 2f)
    }

    @Test fun elEncuadreEsDeCadaDispositivoYLaImagenLaMismaEnTodos() {
        val l = inicio()
        Encuadres.almacen = "encuadres_movil"
        val conPortada = Portadas.poner(ctx, l, png(0xFF00AA00.toInt(), 600, 900))!!
        val imagen = conPortada.portada
        Biblioteca.ponerEncuadre("abc", false, Encuadre(2f, 0.3f, 0.6f))
        assertEquals(Encuadre(2f, 0.3f, 0.6f), Biblioteca.por("abc")!!.encPortada)
        // La cuenta no lleva el encuadre (solo la imagen)
        assertTrue(Biblioteca.hashPortada("abc").isNotBlank())
        // La tableta: la misma imagen, sin encuadre (centrada) y con el suyo propio
        Encuadres.almacen = "encuadres_tableta"
        assertEquals(Encuadre(), Encuadres.de(ctx, "abc", false, imagen))
        Encuadres.poner(ctx, "abc", false, imagen, Encuadre(1.3f, 0.5f, 0.2f))
        assertEquals(Encuadre(1.3f, 0.5f, 0.2f), Encuadres.de(ctx, "abc", false, imagen))
        // El móvil sigue con el suyo
        Encuadres.almacen = "encuadres_movil"
        assertEquals(Encuadre(2f, 0.3f, 0.6f), Encuadres.de(ctx, "abc", false, imagen))
        // Y el fondo va aparte
        assertEquals(Encuadre(), Encuadres.de(ctx, "abc", true, imagen))
        Encuadres.almacen = "encuadres"
    }

    @Test fun unaImagenNuevaEmpiezaCentradaEnTodosLosDispositivos() {
        val l = inicio()
        val a = Portadas.poner(ctx, l, png(0xFF00AA00.toInt(), 600, 900))!!
        Biblioteca.ponerEncuadre("abc", false, Encuadre(2f, 0.3f, 0.6f))
        // Otra imagen (otro archivo, otro hash): su encuadre aún no existe en ningún dispositivo
        val b = Portadas.poner(ctx, Biblioteca.por("abc")!!, png(0xFF0000AA.toInt(), 600, 900))!!
        assertNotEquals(a.portada, b.portada)
        assertEquals(Encuadre(), Encuadres.de(ctx, "abc", false, b.portada))
        // (y el de la anterior se olvida al encuadrar la nueva)
        Encuadres.poner(ctx, "abc", false, b.portada, Encuadre(1.5f, 0.5f, 0.5f))
        assertEquals(Encuadre(), Encuadres.de(ctx, "abc", false, a.portada))
        // Sin imagen guardada (de internet), siempre centrada
        assertEquals(Encuadre(), Encuadres.de(ctx, "abc", false, "https://x/p.jpg"))
    }

    @Test fun laLineaDeTuLecturaSoloDiceTerminadoEnUnLeido() {
        val hace = { _: Long -> "ayer" }; val fecha = { _: Long -> "5 nov 2026" }
        // Leyendo con una fecha de fin de antes (lo volviste a empezar, o pasó de Leído a Leyendo): manda la última lectura
        val leyendo = Libro("m", "Morning Star", "Pierce Brown", estado = Estado.LEYENDO, ultima = 1_000L, terminados = listOf(500L), msPropios = 51 * 60_000L)
        val t = lineaDeLectura(leyendo, fecha, hace)
        assertFalse(t, t.contains("terminado")); assertTrue(t, t.startsWith("leído por última vez ayer"))
        // Leído: la fecha de fin
        assertTrue(lineaDeLectura(leyendo.copy(estado = Estado.LEIDO), fecha, hace).startsWith("terminado el 5 nov 2026"))
    }

    @Test fun elPaspartuVaEnTintaSoloSiLaPortadaSeParecePagina() {
        val blanca = java.io.File(ctx.cacheDir, "blanca.png").apply { writeBytes(png(0xFFF7F5F0.toInt(), 60, 90)) }
        val oscura = java.io.File(ctx.cacheDir, "oscura.png").apply { writeBytes(png(0xFF20304A.toInt(), 60, 90)) }
        // Crema fijo salvo portadas muy claras; no depende del tema (ni de la noche)
        assertEquals(PASPARTU_OSCURO, colorPaspartu(luminanciaDePortada(blanca.path)!!))
        assertEquals(PASPARTU_CREMA, colorPaspartu(luminanciaDePortada(oscura.path)!!))
        assertEquals("una portada media, crema", PASPARTU_CREMA, colorPaspartu(0.5f))
        assertEquals("una crema como el paspartú, oscuro", PASPARTU_OSCURO, colorPaspartu(luminancia(PASPARTU_CREMA)))
        assertEquals("una muy oscura (de noche también), crema", PASPARTU_CREMA, colorPaspartu(0.03f))
        assertTrue("el oscuro se distingue del crema", kotlin.math.abs(luminancia(PASPARTU_CREMA) - luminancia(PASPARTU_OSCURO)) > 0.8f)
        assertNull("sin imagen no hay luminancia de portada", luminanciaDePortada(""))
        assertEquals(8.1f, grosorPaspartu(androidx.compose.ui.unit.Dp(150f)).value, 0.01f)
        assertEquals("tope en las tapas grandes", 14f, grosorPaspartu(androidx.compose.ui.unit.Dp(400f)).value, 0.01f)
    }
}
