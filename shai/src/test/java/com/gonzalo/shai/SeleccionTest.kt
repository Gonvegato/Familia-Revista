package com.gonzalo.shai

import org.junit.Assert.*
import org.junit.Test

/** Los partidos con la selección (no con su club) y el físico del personaje. */
class SeleccionTest {
    @Test fun reconoceLosTorneosDeSeleccionesYNoLosDeClubes() {
        listOf("Selección", "Mundial 2026", "Copa del Mundo", "FIFA World Cup", "Eurocopa", "EURO 2028", "UEFA Euro 2028", "Copa América", "UEFA Nations League",
            "Liga de Naciones", "Clasificación para el Mundial", "Eliminatorias Mundial", "Amistoso internacional", "Juegos Olímpicos", "Mundial Sub-20", "Copa África")
            .forEach { assertTrue(it, Competiciones.deSelecciones(it)) }
        listOf("LaLiga EA Sports", "UEFA Europa League", "UEFA Champions League", "Copa del Rey", "Mundial de Clubes", "Amistoso", "Supercopa de España",
            "Premier League", "Conference League", "Clasificación de Champions")
            .forEach { assertFalse(it, Competiciones.deSelecciones(it)) }
    }

    @Test fun elNombreDelTorneoNoSeConvierteEnSeleccion() {
        val lista = listOf("LaLiga EA Sports", "Copa del Rey", Competiciones.AMISTOSO, Competiciones.SELECCION)
        assertEquals("Mundial 2026", Competiciones.sinPreguntar("Mundial 2026", lista, emptyMap()))
        assertEquals(Competiciones.SELECCION, Competiciones.sinPreguntar("selección", lista, emptyMap()))
    }

    @Test fun conLaSeleccionClaudeSabeQueNoEsSuClubYNoCobraLaPrimaPorGol() {
        val u = Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(nacionalidad = "España"), contrato = Contratos.primero(Nivel.TITULAR, it.club, "2026/27", Rol.JUGADOR)) }
        val p = Partido("2026/27", "Mundial 2026", "Alemania", true, 2, 1, 90, 1, 0, 7.8)
        val t = Narrador.partido(u, p)
        assertTrue(t.contains("CON LA SELECCIÓN DE ESPAÑA")); assertTrue(t.contains("NO CON SU CLUB (${u.club})"))
        assertFalse(Narrador.partido(u, p.copy(competicion = "LaLiga EA Sports")).contains("SELECCIÓN"))
        val porGol = u.contrato!!.porGol
        assertEquals(Contratos.cobra(u, p.copy(goles = 0)), Contratos.cobra(u, p))
        if (porGol > 0) assertEquals(Contratos.cobra(u, p.copy(competicion = "LaLiga EA Sports", goles = 0)) + porGol, Contratos.cobra(u, p.copy(competicion = "LaLiga EA Sports")))
        assertEquals("España 2-1 Alemania", marcador(u, p))
    }

    @Test fun elFisicoYLaSeleccionSeGuardanYVanAlContexto() {
        val pj = Personaje("Álex Cortés", nacionalidad = "España", altura = 185, peso = 78, pie = "Zurdo", aspecto = "Moreno, tatuaje en el brazo", seleccion = "Marruecos")
        assertEquals(pj, Personaje.de(pj.aMapa()))
        assertEquals("1,85 m · 78 kg · zurdo", pj.fisico)
        assertEquals("Marruecos", pj.suSeleccion)
        val u = Ejemplo.universo().copy(personaje = pj)
        val ctx = Narrador.contexto(u, emptyList(), emptyList())
        assertTrue(ctx.contains("- Físico: 1,85 m · 78 kg · zurdo. Moreno, tatuaje en el brazo"))
        assertTrue(ctx.contains("- Su selección: Marruecos (juega con ella aunque es España)"))
        assertEquals("España", pj.copy(seleccion = "").suSeleccion)
    }
}

/** Las ofertas: el texto dice las mismas cifras que la oferta, y nada de «(marca inventada)». */
class OfertasTest {
    @Test fun elTextoCuadraConLasCifrasRecortadas() {
        val o = Oferta("o1", "Nova Sport (marca inventada)", "patrocinio", 150_000, 6_000_000, 3,
            "La marca deportiva Nova Sport quiere hacerte su cara joven mundial con un contrato de imagen de tres años. Te ofrece 150.000 € a la semana y una prima de 6.000.000 € por firmar.")
        val r = Rangos.recorta(o, Nivel.TITULAR)
        assertEquals("Nova Sport", r.de)
        assertEquals(Rangos.de(Nivel.TITULAR).patroSemana, r.salario)
        assertTrue(r.texto, r.texto.contains("${Formato.cifra(r.salario)} € a la semana"))
        assertTrue(r.texto, r.texto.contains("prima de ${Formato.cifra(r.prima)} € por firmar"))
        assertFalse(r.texto.contains("150.000")); assertTrue(r.texto.contains("tres años"))
        assertEquals("Pagaría 25.000 € a la semana y 600.000 de euros de una vez.",
            Rangos.cuadraTexto("Pagaría 90.000 € a la semana y 3 millones de euros de una vez.", 25_000, 600_000))
        assertEquals("Aurum Mobile", Rangos.limpiaNombre("Aurum Mobile (empresa inventada)"))
        assertEquals("Real Betis", Rangos.limpiaNombre("Real Betis"))
    }
}
