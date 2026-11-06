package com.gonzalo.mostaza

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las fuentes que dan un vídeo de aviso del debrid en vez de la película ([FuentesRotas]). */
class RotasTest {
    @Test fun reconoceElAviso() {
        // El de MediaFusion en ElfHosted («Content unavailable… infringing_file») dura unos dos minutos
        assertTrue(FuentesRotas.esAviso(119_000L, 96))
        assertTrue("sin saber lo que dura la película", FuentesRotas.esAviso(119_000L, 0))
        assertFalse("una película de verdad", FuentesRotas.esAviso(96 * 60_000L, 96))
        assertFalse("un episodio corto de verdad", FuentesRotas.esAviso(3 * 60_000L, 5))
        assertFalse("sin duración (directo, o aún no se sabe)", FuentesRotas.esAviso(0L, 96))
        assertFalse(FuentesRotas.esAviso(FuentesRotas.MAX_AVISO_MS, 96))
    }

    @Test fun elCampoValeParaLaCuentaYNoLlevaSecretos() {
        val c = Elector.clave(Fuente(nombre = "x", url = "https://rd.example/resolve/CLAVESECRETA/abc/Peli.2024.1080p.mkv"))
        val k = FuentesRotas.campo(c)
        assertEquals(25, k.length); assertTrue(k.startsWith("r"))
        assertTrue("sin . # \$ [ ] /", k.none { it in ".#\$[]/" })
        assertFalse(k.contains("CLAVESECRETA"))
        assertEquals(k, FuentesRotas.campo(c))
        assertNotEquals(k, FuentesRotas.campo(c + "x"))
        // El grupo de la cuenta lo acepta como número
        assertEquals(EspejoCuenta.Tipo.LARGO, FuentesRotas.GRUPO.tipo(k))
        assertEquals(null, FuentesRotas.GRUPO.tipo("otra"))
    }

    @Test fun caduca() {
        val ahora = 10_000_000_000L
        assertTrue(FuentesRotas.rota(ahora - 1000L, ahora))
        assertFalse(FuentesRotas.rota(ahora - FuentesRotas.CADUCA_MS, ahora))
        assertFalse(FuentesRotas.rota(0L, ahora))
    }

    @Test fun elElectorNoEligeSolaUnaRota() {
        val buena = Candidata(Fuente(nombre = "Peli (2024) [1080p] [Castellano]", url = "https://d/2"), "B", 1, 0)
        val rota = Candidata(Fuente(nombre = "Peli (2024) [1080p] [Castellano] 🇪🇸", url = "https://d/1"), "A", 0, 0)
        val claveRota = Elector.clave(rota.fuente)
        val e = Elector.elige(listOf(rota, buena), PrefsElector(), Red(50_000L), Capacidades(hevc = true, av1 = true, hdr = true, dv = true), 100, true, rotas = { it == claveRota })
        assertEquals(buena, e.elegida)
        assertEquals("sigue en la lista, al final", rota, e.ordenadas.last())
        val todas = Elector.elige(listOf(rota, buena), PrefsElector(), Red(50_000L), Capacidades(hevc = true, av1 = true, hdr = true, dv = true), 100, true, rotas = { true })
        assertEquals("si todas dan el aviso, ninguna sola", null, todas.elegida)
    }
}
