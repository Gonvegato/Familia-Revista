package com.gonzalo.cobre

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.iptv.futbol.Clasificacion
import com.gonzalo.iptv.futbol.Equipo
import com.gonzalo.iptv.futbol.Estado
import com.gonzalo.iptv.futbol.Fila
import com.gonzalo.iptv.futbol.Partido
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Nut 0.8.0: la racha V/E/D, la semana vista, el encuadre de cada dispositivo, el paspartú, las páginas y el estado de los partidos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NutCTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val bar = Equipo("83", "Barcelona"); private val rma = Equipo("86", "Real Madrid"); private val atm = Equipo("1068", "Atlético")
    private val h = 3_600_000L
    private fun jugado(id: String, t: Long, l: Equipo, v: Equipo, gl: Int, gv: Int, pen: String = "") = Partido(id, "esp.1", t, l, v, Estado.FINAL, gl, gv, penaltis = pen)

    @After fun fin() { TiempoVisto.fijos = null; Encuadres.almacen = "encuadres" }

    @Test fun laRachaDeUnEquipoVaDeLoMasAntiguoALoUltimo() {
        val t = System.currentTimeMillis() - 100 * h
        val l = listOf(
            jugado("1", t, bar, rma, 3, 1),          // V (en casa)
            jugado("2", t + 10 * h, atm, bar, 2, 0),  // D (fuera)
            jugado("3", t + 20 * h, bar, atm, 1, 1),  // E
            jugado("4", t + 30 * h, rma, bar, 0, 2),  // V (fuera)
            jugado("5", t + 40 * h, bar, rma, 0, 1),  // D
            jugado("6", t + 50 * h, atm, bar, 1, 1, pen = "3-4"),  // E (decidido en los penaltis)
            Partido("7", "esp.1", t + 60 * h, bar, atm, Estado.POR_JUGAR),  // no cuenta
            jugado("8", t + 5 * h, rma, atm, 4, 0),   // no lo juega
        )
        assertEquals(listOf('D', 'E', 'V', 'D', 'E'), FormaEquipo.de(l, "83"))
        assertEquals(listOf('V', 'D', 'E', 'V', 'D', 'E'), FormaEquipo.de(l, "83", n = 10))
        assertNull(FormaEquipo.resultado(l[6], "83"))
        assertNull(FormaEquipo.resultado(l[7], "83"))
        assertEquals('V', FormaEquipo.resultado(l[7], "86"))
        assertTrue(FormaEquipo.de(emptyList(), "83").isEmpty())
        val c = Clasificacion("esp.1", listOf("" to listOf(Fila(1, rma, 10, 8, 1, 1, 20, 5, 15, 25), Fila(2, bar, 10, 7, 2, 1, 22, 8, 14, 23))))
        assertEquals(2, FormaEquipo.puesto(c, "83")); assertNull(FormaEquipo.puesto(c, "1068"))
    }

    @Test fun laSemanaVistaSumaLosDiasYLaRachaLlegaHastaAyer() {
        val hoy = LocalDate.of(2026, 11, 9)
        val m = 60_000L
        val dias = mapOf(hoy.minusDays(1).toString() to 95 * m, hoy.minusDays(2).toString() to 51 * m, hoy.minusDays(3).toString() to 130 * m, hoy.minusDays(5).toString() to 30_000L, hoy.minusDays(9).toString() to 60 * m)
        val s = SemanaVista.de(dias, hoy)
        assertEquals(7, s.semana.size); assertEquals(hoy, s.semana.last().first); assertEquals(hoy.minusDays(6), s.semana.first().first)
        assertEquals(0L, s.hoyMin); assertEquals(95L + 51 + 130, s.totalMin)
        assertEquals(3, s.racha)   // hoy aún nada: ayer, anteayer y el anterior (medio minuto no cuenta)
        assertEquals(4, SemanaVista.de(dias + (hoy.toString() to 2 * m), hoy).racha)
        assertEquals("3 H 01", minutosCorto(181)); assertEquals("52′", minutosCorto(52)); assertEquals("2 h 10", minutosDia(130)); assertEquals("nada", minutosDia(0))
    }

    @Test fun loVistoSeGuardaEnElDispositivoYSeSuma() {
        val hoy = LocalDate.now()
        TiempoVisto.suma(ctx, 90_000L, hoy); TiempoVisto.suma(ctx, 60_000L, hoy); TiempoVisto.suma(ctx, 0L, hoy)
        TiempoVisto.suma(ctx, 120_000L, hoy.minusDays(1))
        val d = TiempoVisto.dias(ctx)
        assertEquals(150_000L, d[hoy.toString()]); assertEquals(120_000L, d[hoy.minusDays(1).toString()])
        TiempoVisto.fijos = mapOf("2026-01-01" to 5L)
        assertEquals(mapOf("2026-01-01" to 5L), TiempoVisto.dias(ctx))
    }

    @Test fun elEncuadreDelFondoEsDeCadaDispositivo() {
        val f = java.io.File(ctx.cacheDir, "abc123").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        Encuadres.almacen = "movil"
        Encuadres.poner(ctx, "eq83", true, f.path, Encuadre(1.6f, 0.3f, 0.6f))
        assertEquals(Encuadre(1.6f, 0.3f, 0.6f), Encuadres.de(ctx, "eq83", true, f.path))
        Encuadres.almacen = "tablet"
        assertEquals(Encuadre(), Encuadres.de(ctx, "eq83", true, f.path))   // en otro dispositivo, centrado
        Encuadres.poner(ctx, "eq83", true, f.path, Encuadre(1.2f, 0.5f, 0.4f))
        Encuadres.almacen = "movil"
        assertEquals(Encuadre(1.6f, 0.3f, 0.6f), Encuadres.de(ctx, "eq83", true, f.path))
        // otra imagen (otro hash): el encuadre de antes ya no vale
        assertEquals(Encuadre(), Encuadres.de(ctx, "eq83", true, java.io.File(ctx.cacheDir, "otra456").path))
        // y una imagen de internet no se encuadra
        assertEquals(Encuadre(), Encuadres.de(ctx, "eq83", true, "https://a.espncdn.com/x.png"))
    }

    @Test fun elPaspartuEsCremaYOscuroSoloConEscudosMuyClaros() {
        assertEquals(Color(0xFFF4EBD6), colorPaspartu(0.2f))
        assertEquals(Color(0xFFF4EBD6), colorPaspartu(0.779f))
        assertEquals(Color(0xFF141210), colorPaspartu(0.78f))
        assertEquals(Color(0xFF141210), colorPaspartu(luminancia(Color.White)))
        assertEquals(Color(0xFFF4EBD6), colorPaspartu(luminancia(Color(0xFF004D98))))
        assertEquals(0.78f, UMBRAL_PASPARTU)
        // el color del club sale de lo que se ve del escudo (lo transparente no cuenta)
        val b = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
        for (x in 0 until 10) for (y in 0 until 10) b.setPixel(x, y, if (x < 5) 0xFFA50044.toInt() else 0x00000000)
        val c = colorMedio(b)!!
        assertEquals(0xA5, (c.red * 255).toInt()); assertEquals(0x44, (c.blue * 255).toInt())
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500-dark/83.png", ImagenesEquipo.escudoOscuroEspn("https://a.espncdn.com/i/teamlogos/soccer/500/83.png"))
        assertNull(ImagenesEquipo.escudoOscuroEspn("/data/escudo.png"))
    }

    @Test fun lasPaginasEnElOrdenDeLasPestanas() {
        assertEquals(listOf(Destino.Portada, Destino.Hoy, Destino.Competiciones, Destino.Futbol, Destino.Tele), PAGINAS)
        assertEquals(0, paginaDe(Destino.Portada)); assertEquals(1, paginaDe(Destino.Hoy)); assertEquals(4, paginaDe(Destino.Tele))
        // lo de la tele y los ajustes van encima (no son páginas); al volver, lo de la tele vuelve a IPTV
        listOf(Destino.Directo, Destino.Guia, Destino.Buscar, Destino.Ajustes, Destino.AjustesFutbol, Destino.Listas).forEach { assertEquals(-1, paginaDe(it)) }
        assertTrue(Destino.Guia.deLaTele); assertFalse(Destino.AjustesFutbol.deLaTele)
        // (el widget de los partidos de hoy abre la página Hoy)
        assertEquals(Destino.Hoy, Destino.entries.first { it.name == "Hoy" })
    }

    @Test fun elEstadoDeUnPartido() {
        val ahora = System.currentTimeMillis()
        val manana = java.time.LocalDate.now(com.gonzalo.iptv.futbol.Hora.ESPANA).plusDays(1).atTime(21, 0).atZone(com.gonzalo.iptv.futbol.Hora.ESPANA).toInstant().toEpochMilli()
        assertEquals("PRÓXIMO · MAÑANA · 21:00", etiquetaPartido(Partido("1", "esp.1", manana, atm, bar), ahora))
        assertEquals("EN DIRECTO · 67'", etiquetaPartido(Partido("1", "esp.1", ahora - 67 * 60_000L, atm, bar, Estado.EN_JUEGO, 1, 2, "67'"), ahora))
        assertEquals("EN DIRECTO · DESCANSO", etiquetaPartido(Partido("1", "esp.1", ahora - 50 * 60_000L, atm, bar, Estado.DESCANSO, 0, 0), ahora))
        assertEquals("FINAL", etiquetaPartido(jugado("1", ahora - 30 * h, bar, rma, 2, 1), ahora))
    }
}
