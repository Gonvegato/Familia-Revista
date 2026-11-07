package com.gonzalo.mostaza

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Lo de los widgets nuevos que no necesita pantalla: los próximos capítulos (TMDB, orden y fechas), tu semana y «por dónde vas». */
class ProximosTest {
    private val hoy = LocalDate.of(2026, 11, 7)   // (un sábado)

    @Test fun leeElSiguienteEpisodioDeTmdb() {
        val json = """{"id":136315,"name":"The Bear","next_episode_to_air":{"id":1,"name":"Pasta fresca","air_date":"2026-11-14","episode_number":3,"season_number":5,"runtime":30}}"""
        assertEquals(Proximo("tt14452776", "The Bear", 5, 3, "Pasta fresca", "2026-11-14"), Proximos.deTmdb(json, "tt14452776", "The Bear"))
        assertEquals("sin nombre en la biblioteca, el de TMDB", "The Bear", Proximos.deTmdb(json, "r", "")!!.serie)
        assertNull("terminada: nada anunciado", Proximos.deTmdb("""{"id":1,"name":"X","next_episode_to_air":null}""", "r", "X"))
        assertNull("anunciado sin fecha", Proximos.deTmdb("""{"next_episode_to_air":{"air_date":"","episode_number":1,"season_number":2}}""", "r", "X"))
        assertNull("basura", Proximos.deTmdb("no es json", "r", "X"))
    }

    @Test fun ordenaPorFechaYQuitaLoEstrenado() {
        val l = listOf(
            Proximo("a", "Shōgun", 2, 1, "", "2026-11-20"), Proximo("b", "separación", 3, 1, "", "2026-11-08"),
            Proximo("c", "Andor", 3, 1, "", "2026-11-08"), Proximo("d", "Viejo", 1, 9, "", "2026-11-06"),
            Proximo("e", "Hoy", 1, 2, "", "2026-11-07"), Proximo("f", "Mal", 1, 1, "", "pronto"),
        )
        assertEquals(listOf("e", "c", "b", "a"), Proximos.ordena(l, hoy).map { it.ref })
    }

    @Test fun diceCuandoComoLaRevista() {
        assertEquals("hoy", Proximos.cuando(hoy, hoy))
        assertEquals("mañana", Proximos.cuando(hoy.plusDays(1), hoy))
        assertEquals("sáb 14 nov", Proximos.cuando(LocalDate.of(2026, 11, 14), hoy))
        assertEquals("lun 9 nov", Proximos.cuando(LocalDate.of(2026, 11, 9), hoy))
        assertEquals("con el año si es otro", "mar 5 ene 2027", Proximos.cuando(LocalDate.of(2027, 1, 5), hoy))
    }

    @Test fun elEpisodioConSuTituloSiDiceAlgo() {
        assertEquals("T3 · E2 · El regreso", Proximos.episodio(Proximo("r", "S", 3, 2, "El regreso", "2026-11-08")))
        assertEquals("T3 · E2", Proximos.episodio(Proximo("r", "S", 3, 2, "Episodio 2", "2026-11-08")))
        assertEquals("T1 · E1", Proximos.episodio(Proximo("r", "S", 1, 1, "", "2026-11-08")))
    }

    @Test fun laCacheVaYVuelve() {
        val l = listOf(Proximo("tt1", "Una «serie»", 1, 2, "Ñu", "2026-11-08"), Proximo("tt2", "Otra", 4, 10, "", "2026-12-01"))
        assertEquals(l, Proximos.desdeTexto(Proximos.aTexto(l)))
        assertTrue(Proximos.desdeTexto("").isEmpty())
        assertTrue(Proximos.desdeTexto("{roto").isEmpty())
    }

    @Test fun tuSemanaPorDias() {
        val m = 60_000L
        val ratos = listOf(
            Rato("a", "Juego de tronos", "2026-11-07", 57 * m), Rato("a", "Juego de tronos", "2026-11-07", 3 * m),   // (dos dispositivos)
            Rato("b", "Oppenheimer", "2026-11-05", 120 * m), Rato("a", "Juego de tronos", "2026-11-01", 30 * m),
            Rato("c", "Viejo", "2026-10-31", 500 * m), Rato("d", "Mañana", "2026-11-08", 50 * m), Rato("e", "Poco", "2026-11-06", 20_000L),
        )
        val s = Semana.de(ratos, hoy) { r -> if (r == "a") "Juego de tronos (nombre de la biblioteca)" else null }
        assertEquals(listOf("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06", "2026-11-07"), s.dias.map { it.first.toString() })
        assertEquals(listOf(30L, 0L, 0L, 0L, 120L, 0L, 60L), s.dias.map { it.second / m })
        assertEquals("de más a menos; lo de menos de un minuto no sale", listOf("b" to 120L, "a" to 90L), s.titulos.map { it.first to it.third / m })
        assertEquals("Juego de tronos (nombre de la biblioteca)", s.titulos[1].second)
        assertEquals(210 * m + 20_000L, s.total)
        assertFalse(s.vacia)
        assertTrue(Semana.de(emptyList(), hoy).vacia)
        assertEquals("3 h 30 min", Semana.horas(210 * m)); assertEquals("45 min", Semana.horas(45 * m)); assertEquals("0 min", Semana.horas(0))
        assertEquals("2 h", Semana.corto(120 * m)); assertEquals("1,5 h", Semana.corto(90 * m)); assertEquals("40 min", Semana.corto(40 * m))
    }

    @Test fun porDondeVas() {
        val min = 60_000L
        assertEquals("T2 · E5 · quedan 23 min", porDonde(Progreso("v", "r", 2, 5, 34 * min, 57 * min, false, 0L, 1L), true))
        assertEquals("T2 · E5 · quedan 23 min", porDonde(Progreso("v", "r", 2, 5, 34 * min, 57 * min, false, 0L, 1L), true, porcentaje = true))
        assertEquals("quedan 1 h 48 min", porDonde(Progreso("v", "r", 0, 0, 72 * min, 180 * min, false, 0L, 1L), false))
        assertEquals("40 % · quedan 1 h 48 min", porDonde(Progreso("v", "r", 0, 0, 72 * min, 180 * min, false, 0L, 1L), false, porcentaje = true))
        assertEquals("T1 · E3 · visto", porDonde(Progreso("v", "r", 1, 3, 50 * min, 50 * min, true, 1L, 1L), true))
        assertEquals("T1 · E3", porDonde(Progreso("v", "r", 1, 3, 0, 0, false, 0L, 1L), true))
        assertEquals("Serie", porDonde(null, true)); assertEquals("Película", porDonde(null, false))
    }
}
