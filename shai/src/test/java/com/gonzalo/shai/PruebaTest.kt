package com.gonzalo.shai

import org.robolectric.RuntimeEnvironment
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El modo prueba: todo funciona sin clave ni IA, con 0 de gasto, y sus carreras no se mezclan con las de verdad. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PruebaTest {
    private val ctx get() = RuntimeEnvironment.getApplication()

    @Before fun prepara() {
        Carreras.almacen = AlmacenMemoria()
        ClaveApi.fija = ""
        Gasto.mesFijo = java.time.YearMonth.of(2026, 11)
        Motor.reloj = { System.currentTimeMillis() }
    }
    @After fun suelta() { Prueba.pon(ctx, false); ClaveApi.fija = null; Gasto.mesFijo = null }

    @Test fun todaLaCarreraFuncionaSinIaYSinGastar() {
        val real = Carreras.almacen
        assertFalse(ClaveApi.hay)
        Prueba.pon(ctx, true)
        assertTrue(ClaveApi.hay); assertFalse(ClaveApi.hayDeVerdad)
        assertNotSame(real, Carreras.almacen)

        val (u, fallo) = Motor.crea(Ejemplo.universo())
        assertNull(fallo)
        assertTrue(Carreras.de(u.id)!!.entorno.isNotEmpty())
        repeat(4) { i ->
            val (e, f) = Motor.partido(u.id, Ejemplo.partido(gf = i, gc = 1, rival = "Rival $i"))
            assertNull(f); assertTrue(e.pendiente.isEmpty())
            assertTrue(e.posts.isNotEmpty()); assertNotNull(e.prensa)
            assertTrue(e.posts.any { "el Niño" in it.texto })
        }
        // Los dos eventos de entre partidos llegan al momento en modo prueba
        val eventos = Carreras.entradasDe(u.id).filter { it.tipo == TipoEntrada.EVENTO }
        assertTrue("eventos: ${eventos.size}", eventos.size >= 6)
        assertTrue(eventos.all { it.aplicada })
        // Una decisión, el botón de «¿qué ha pasado?» y la foto
        val d = Carreras.entradasDe(u.id).first { it.decision?.abierta == true }
        assertNull(Motor.decide(d.id, d.decision!!.opciones.first()))
        assertEquals(Decision.RESUELTA, Carreras.entrada(d.id)!!.decision!!.estado)
        assertNull(Motor.eventoAhora(u.id).second)
        assertTrue(Motor.foto(u.id, "AAAA", "image/jpeg").seVe)
        assertEquals(0.0, Gasto.mes(), 0.0)

        // Al quitarlo, vuelven las de verdad (vacías) y la de prueba sigue en su sitio
        Prueba.pon(ctx, false)
        assertSame(real, Carreras.almacen)
        assertTrue(Carreras.todas().isEmpty())
        Prueba.pon(ctx, true)
        assertNotNull(Carreras.de(u.id))
        Prueba.vacia()
        assertTrue(Carreras.todas().isEmpty())
    }
}
