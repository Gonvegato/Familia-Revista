package com.gonzalo.hiedra

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lo de Bastet 0.8.0 (como Seshat): el reto de juegos terminados, la semana, los órdenes de cada sección, el encuadre de cada dispositivo
 * (y el de antes, que pasa una vez), el paspartú, el acento legible y las secciones pedidas desde fuera.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DireccionCTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun ms(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 3_600_000L

    @Test fun elRetoCuentaLosPasadosDeEsteAnoYDiceComoVas() {
        val hoy = LocalDate.of(2026, 11, 9)
        val js = listOf(
            Juego("a", "Hades", finalizado = ms(hoy.minusDays(3))).conEstado(Estado.PASADO, ms(hoy.minusDays(3))),
            Juego("b", "Celeste").conEstado(Estado.PASADO, ms(LocalDate.of(2026, 2, 1))),
            Juego("c", "Viejo").conEstado(Estado.PASADO, ms(LocalDate.of(2025, 12, 30))),
            Juego("d", "Jugando").conEstado(Estado.JUGANDO),
        )
        assertEquals("solo los pasados de 2026, el más reciente primero", listOf("Hades", "Celeste"), terminadosEn(js, 2026).map { it.titulo })
        // 20 al año: a 9 de noviembre (día 313 de 365) tocarían 17; con 2, vas 15 por detrás y te quedan 18 en 53 días
        val r = ritmoReto(20, 2, hoy)
        assertEquals(-15, r.diferencia); assertEquals(18, r.quedan); assertEquals(53, r.dias)
        assertEquals("VAS 15 POR DETRÁS · TE QUEDAN 18 EN 53 DÍAS", r.texto())
        assertEquals("¡RETO CUMPLIDO! Y 2 DE PROPINA", ritmoReto(5, 7, hoy).texto())
        assertEquals("VAS AL DÍA · TE QUEDA 1 EN 53 DÍAS", ritmoReto(1, 0, LocalDate.of(2026, 11, 9)).let { RitmoReto(0, 1, 0, 1, it.dias) }.texto())
    }

    @Test fun elRetoSeGuardaYSinCuentaVaEnElMovil() {
        RetoJuegos.poner(ctx, 24)
        assertEquals(24, RetoJuegos.meta(ctx))
        RetoJuegos.poner(ctx, 0)
        assertEquals(0, RetoJuegos.meta(ctx))
    }

    @Test fun laSemanaSumaTodasLasFuentesYCuentaLaRacha() {
        val hoy = LocalDate.of(2026, 11, 9)
        val d = { n: Long -> hoy.minusDays(n).toString() }
        val a = Juego("a", "A", propias = Horas(0, 0, mapOf(d(0) to 30 * 60_000L, d(1) to 60 * 60_000L)), externas = mapOf(Fuentes.STEAM to Horas(0, 0, mapOf(d(1) to 10 * 60_000L))))
        val b = Juego("b", "B", ajenas = mapOf("otro" to Horas(0, 0, mapOf(d(2) to 5 * 60_000L, d(9) to 99 * 60_000L))))
        val s = datosJuego(listOf(a, b), hoy)
        assertEquals(30L, s.hoyMin)
        assertEquals(listOf(0L, 0L, 0L, 0L, 5L, 70L, 30L), s.semana.map { it.second })
        assertEquals("hoy, ayer y anteayer", 3, s.racha)
        assertEquals("3 H 01", minutosCorto(181)); assertEquals("51′", minutosCorto(51))
        assertEquals("2 h 10", minutosDia(130)); assertEquals("nada", minutosDia(0)); assertEquals("1 h", minutosDia(60))
    }

    @Test fun cadaSeccionTieneSusOrdenesYLoDeOtraNoVale() {
        assertEquals(listOf(Orden.Recientes, Orden.Jugados, Orden.Nombre), ordenesDe(Estado.JUGANDO))
        assertEquals(listOf(Orden.Anadidos, Orden.Duracion, Orden.Nombre), ordenesDe(Estado.PENDIENTE))
        assertEquals(Orden.Pasados, ordenInicial(Estado.PASADO))
        assertFalse("«Cuándo lo pasaste» solo en Pasados", Orden.Pasados in ordenesDe(Estado.JUGANDO))
        assertFalse("«Más jugados» no dice nada de lo que no has empezado", Orden.Jugados in ordenesDe(Estado.PENDIENTE))
        // Un orden guardado que ya no vale en esa sección vuelve al de serie
        OrdenGuardado.pon(ctx, Estado.PENDIENTE, Orden.Jugados)
        assertEquals(Orden.Anadidos, OrdenGuardado.de(ctx, Estado.PENDIENTE))
        // Más cortos: los que no se sabe cuánto duran, al final
        val l = ordenar(Orden.Duracion, listOf(Juego("x", "X"), Juego("y", "Y", durHistoria = 600), Juego("z", "Z", durHistoria = 120)))
        assertEquals(listOf("Z", "Y", "X"), l.map { it.titulo })
    }

    private fun imagen(nombre: String, w: Int, h: Int): String {
        val f = File(ctx.filesDir, nombre)
        f.outputStream().use { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    @Test fun elEncuadreEsDeCadaDispositivoYVuelveAlCentroSiCambiaLaImagen() {
        val p = imagen("aaa111", 600, 900)
        Encuadres.almacen = "enc_movil"
        Encuadres.poner(ctx, "j1", false, p, Encuadre(2f, 0.3f, 0.6f))
        assertEquals(Encuadre(2f, 0.3f, 0.6f), Encuadres.de(ctx, "j1", false, p))
        assertEquals("el fondo va aparte", Encuadre(), Encuadres.de(ctx, "j1", true, p))
        Encuadres.almacen = "enc_tableta"
        assertEquals("la tableta, la misma imagen centrada", Encuadre(), Encuadres.de(ctx, "j1", false, p))
        Encuadres.almacen = "enc_movil"
        val nueva = imagen("bbb222", 600, 900)
        assertEquals("otra imagen: centrada", Encuadre(), Encuadres.de(ctx, "j1", false, nueva))
        Encuadres.poner(ctx, "j1", false, nueva, Encuadre(1.5f, 0.5f, 0.5f))
        assertEquals("y lo de la imagen de antes se olvida", Encuadre(), Encuadres.de(ctx, "j1", false, p))
        Encuadres.almacen = "encuadres"
    }

    @Test fun elEncuadreDeAntesPasaUnaVezAEsteDispositivo() {
        // Antes: zoom 2 y movida a la izquierda un 10 % del hueco (3:4) → el punto del centro se va a la derecha de la imagen
        val e = encuadreDeAntes(2f, -0.1f, 0f, 600f, 900f)
        assertEquals(2f, e.zoom, 0f); assertTrue(e.cx > 0.5f); assertEquals(0.5f, e.cy, 0.001f)
        assertEquals("sin encuadre, nada", Encuadre(), encuadreDeAntes(1f, 0f, 0f, 600f, 900f))
        val p = imagen("ccc333", 600, 900)
        Encuadres.almacen = "enc_migra"
        Encuadres.migrarDeAntes(ctx, "j2", p, 2f, -0.1f, 0f)
        assertEquals(e, Encuadres.de(ctx, "j2", false, p))
        // Una sola vez: si luego lo cambias aquí, el de la cuenta ya no lo pisa
        Encuadres.poner(ctx, "j2", false, p, Encuadre(1.2f, 0.4f, 0.5f))
        Encuadres.migrarDeAntes(ctx, "j2", p, 3f, 0.2f, 0.2f)
        assertEquals(Encuadre(1.2f, 0.4f, 0.5f), Encuadres.de(ctx, "j2", false, p))
        Encuadres.almacen = "encuadres"
    }

    @Test fun elPaspartuEsCremaFijoSalvoPortadasMuyClaras() {
        // Igual en Bastet (página oscura) que en Seshat, de día y de noche: crema fijo; oscuro solo si la portada es muy clara
        assertEquals(PASPARTU_CREMA, colorPaspartu(0.2f))
        assertEquals(PASPARTU_CREMA, colorPaspartu(0.05f))
        assertEquals(PASPARTU_CREMA, colorPaspartu(0.6f))
        assertEquals(PASPARTU_OSCURO, colorPaspartu(0.88f))
        assertEquals(PASPARTU_OSCURO, colorPaspartu(luminancia(Color(0xFFFFFFFF))))
        assertEquals(8.1f, grosorPaspartu(androidx.compose.ui.unit.Dp(150f)).value, 0.01f)
        assertEquals(4f, grosorPaspartu(androidx.compose.ui.unit.Dp(40f)).value, 0f)
    }

    @Test fun elAcentoSeAclaraSoloDondeHaceFalta() {
        // Los colores de Bastet: el oliva sobre el verde oscuro no se lee (≈2,3:1); el legible llega a 4,5:1 sin dejar de ser de su familia
        val fondo = Color(0xFF1C241B); val tinta = Color(0xFFF1E1C3); val oliva = Color(0xFF6A6D2C)
        assertTrue(contraste(oliva, fondo) < 3f)
        val claro = acentoLegible(oliva, fondo, tinta)
        assertTrue(contraste(claro, fondo) >= 4.5f)
        assertTrue("más claro que el de serie", luminancia(claro) > luminancia(oliva))
        assertTrue("sin llegar a ser la tinta", contraste(claro, tinta) > 1.3f)
        // Un acento que ya se lee no se toca
        val granate = Color(0xFF8C2A22); val papel = Color(0xFFF8F2DF)
        assertTrue(contraste(granate, papel) >= 4.5f)
        assertEquals(granate, acentoLegible(granate, papel, Color(0xFF2B2418)))
    }

    @Test fun lasSeccionesPedidasDesdeFuera() {
        assertEquals(Destino.Pendiente, destinoDe("pendientes"))
        assertEquals(Destino.Pasado, destinoDe("Pasados"))
        assertEquals(Destino.Sistemas, destinoDe("sistemas"))
        assertEquals(Destino.PC, destinoDe("pc"))
        assertNull(destinoDe("cualquier cosa"))
        assertEquals(6, paginaDe(Destino.Sistemas)); assertEquals(-1, paginaDe(Destino.Ajustes))
    }

    @Test fun laEtiquetaDeJugandoNuncaDiceTerminado() {
        val j = Juego("a", "Hades", finalizado = 5L, propias = Horas(3 * 3_600_000L)).conEstado(Estado.JUGANDO)
        assertEquals("JUGANDO · 3 H 00", etiquetaEstado(j))
        assertEquals("PENDIENTE", etiquetaEstado(Juego("b", "B")))
        assertTrue(etiquetaEstado(j.conEstado(Estado.PASADO, ms(LocalDate.of(2026, 11, 2)))).startsWith("PASADO · "))
    }
}
