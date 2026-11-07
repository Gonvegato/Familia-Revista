package com.gonzalo.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que Cobre escribe en la cuenta (el mismo formato en el móvil y en el ordenador) y las reglas de lo visto. */
class DatosCuentaTest {
    @Test fun lasListasVanYVuelvenTalCual() {
        val l = listOf(LISTA_XTREAM.copy(epg = "http://epg/x.xml", agente = "Mi agente", formato = "m3u8", creada = 5L),
            Lista("lm3u000001", "Lista M3U", TipoLista.M3U, "http://lista/get.m3u?token=abc"), Lista("larchivo01", "Archivo", TipoLista.ARCHIVO, dispositivo = "cobre-1234"))
        val t = DatosCuenta.listasATexto(l)
        assertEquals(l, DatosCuenta.listasDeTexto(t))
        // Lo que no se entiende, fuera (y sin romper nada)
        assertTrue(DatosCuenta.listasDeTexto("no es json").isEmpty())
        assertEquals(1, DatosCuenta.listasDeTexto("""[{"id":"l1","n":"a","t":"m3u","u":"http://x"},{"n":"sin id"},{"id":"mal/id"},{"id":"l1"}]""").size)
    }

    @Test fun losTextosNoEnsenanSecretos() {
        val l = LISTA_XTREAM
        assertFalse(l.toString().contains("s3cr3t0")); assertFalse(l.toString().contains("pepe")); assertFalse(l.toString().contains("tv.ejemplo"))
        assertEquals("•••", Secretos.tapa("s3cr3t0", l.secretos))
        val t = Secretos.tapa("error en http://tv.ejemplo.test:8080/live/pepe/s3cr3t0/1.ts y en x?username=ana&password=otra", emptyList())
        assertFalse(t.contains("pepe")); assertFalse(t.contains("s3cr3t0")); assertFalse(t.contains("otra")); assertFalse(t.contains("ana&"))
        assertEquals("•••", Secretos.tapa("a%2Fb", listOf("a/b")))
        assertEquals("••••••", Secretos.puntos("abc"))
    }

    @Test fun losNodosDeLaCuentaSonNombresValidosYSinSecretos() {
        val c = Canal("c0123456789abcdef~2", "La 1 HD", "Nacionales", url = "http://tv/live/pepe/s3cr3t0/1.ts")
        assertTrue(DatosCuenta.refValida(DatosCuenta.refFavorito("l1", c.clave)))
        assertTrue(DatosCuenta.refValida(DatosCuenta.refFavorito("l1", "raro.con/punto")))
        assertTrue(DatosCuenta.refValida(DatosCuenta.refGrupo("l1", "ES | Deportes / Fútbol.")))
        assertEquals(DatosCuenta.refGrupo("l1", "A"), DatosCuenta.refGrupo("l1", "A"))
        for (campos in listOf(DatosCuenta.camposFavorito("l1", c, 3), DatosCuenta.camposUltimo("l1", c, 9), DatosCuenta.camposGrupo("l1", "Nacionales", true)))
            assertFalse(JsonFlujo.escribe(campos).contains("s3cr3t0"))
        assertTrue(DatosCuenta.idNuevo().matches(Regex("l[0-9a-f]{10}")))
    }

    @Test fun loVistoVaYVuelve() {
        val p = Progreso("l1", "e9011", 9011, "mkv", "Episodio 1", 3001, "Juego de tronos", 1, 1, 600_000, 3_420_000, false, 77)
        assertEquals(p, DatosCuenta.progresoDe(DatosCuenta.camposProgreso(p)))
        assertEquals("episodio", DatosCuenta.camposProgreso(p)["tipo"])
        val peli = Progreso("l1", "p5001", 5001, "mkv", "Dune", pos = 1, dur = 2)
        assertEquals("pelicula", DatosCuenta.camposProgreso(peli)["tipo"])
        // Firebase devuelve los números como Long o Double, y los sí/no como Boolean
        val m = DatosCuenta.camposProgreso(p).mapValues { (_, v) -> if (v is Long) v.toDouble() else v }
        assertEquals(p, DatosCuenta.progresoDe(m))
    }

    @Test fun lasReglasDeLoVisto() {
        assertFalse(Visto.visto(89, 100)); assertTrue(Visto.visto(90, 100)); assertFalse(Visto.visto(5, 0))
        assertEquals(0L, Visto.desde(20_000, 100_000)); assertEquals(50_000L, Visto.desde(50_000, 100_000)); assertEquals(0L, Visto.desde(98_000, 100_000))
        val l = listOf(
            Progreso("l1", "p1", 1, "mp4", "Vista", pos = 95, dur = 100, visto = true, ultima = 50),
            Progreso("l1", "p2", 2, "mp4", "A medias", pos = 40, dur = 100, ultima = 40),
            Progreso("l1", "e10", 10, "mp4", "Ep 1", 7, "Serie", 1, 1, 100, 100, true, 60),
            Progreso("l1", "e11", 11, "mp4", "Ep 2", 7, "Serie", 1, 2, 10, 100, false, 70),
            Progreso("l2", "p3", 3, "mp4", "De otra lista", pos = 40, dur = 100, ultima = 99),
            Progreso("l1", "p4", 4, "mp4", "Sin empezar", pos = 0, dur = 100, ultima = 30),
        )
        val s = Visto.seguir(l, setOf("l1"))
        assertEquals("de cada serie lo último; las películas vistas, no; lo de otras listas, no", listOf("e11", "p2"), s.map { it.video })
    }
}
