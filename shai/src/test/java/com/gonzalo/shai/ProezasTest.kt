package com.gonzalo.shai

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Las proezas (siete goles, una temporada de otro planeta) rompen los topes: la fama salta, lo que vale se dispara y el mundo pierde la cabeza. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProezasTest {
    @Before fun prepara() { Ejemplo.prepara(); Motor.claude = Claude({ ClaveApi.lee() }, TransporteDePrueba(kotlin.random.Random(4))); Motor.pideAgentes = false }
    @After fun suelta() { ClaveApi.fija = null; Gasto.mesFijo = null; Motor.pideAgentes = true }

    private fun p(goles: Int, asis: Int = 0, comp: String = "LaLiga EA Sports", fase: String = "", rival: String = "Getafe CF") =
        Partido("2026/27", comp, rival, true, goles + 1, 0, 90, goles, asis, 9.5, mvp = true, fase = fase)

    @Test fun losGrados() {
        assertEquals(Proeza.NINGUNA, Proezas.dePartido(Rol.JUGADOR, p(3)))
        assertEquals(Proeza.ENORME, Proezas.dePartido(Rol.JUGADOR, p(4)))
        assertEquals(Proeza.ENORME, Proezas.dePartido(Rol.JUGADOR, p(3, comp = "Copa del Mundo", fase = "Octavos de final")))
        assertEquals(Proeza.HISTORICA, Proezas.dePartido(Rol.JUGADOR, p(5)))
        assertEquals(Proeza.HISTORICA, Proezas.dePartido(Rol.JUGADOR, p(3, comp = "UEFA Champions League", fase = "Final")))
        assertEquals(Proeza.SURREAL, Proezas.dePartido(Rol.JUGADOR, p(7)))
        assertEquals(Proeza.SURREAL, Proezas.dePartido(Rol.JUGADOR, p(5, 4)))
        // En un amistoso, un escalón menos; sin jugar, nada
        assertEquals(Proeza.HISTORICA, Proezas.dePartido(Rol.JUGADOR, p(7, comp = "Amistoso")))
        assertEquals(Proeza.NINGUNA, Proezas.dePartido(Rol.JUGADOR, p(7).copy(minutos = 0)))
    }

    @Test fun laTemporadaSoloDisparaAlCruzar() {
        val u = Ejemplo.universo()
        val doce = List(11) { p(1) }
        // 11 partidos con 11 goles; el 12.º con 4 → 15 en 12 (1,25 por partido): histórica, y la cruza ahora
        val l = Proezas.lee(u, p(4, rival = "Sevilla FC"), doce)
        assertEquals(Proeza.HISTORICA, l.temporada); assertTrue(l.temporadaNueva); assertEquals(Proeza.HISTORICA, l.dispara)
        // El siguiente, con un gol: sigue histórica, pero ya no dispara nada
        val l2 = Proezas.lee(u, p(1, rival = "Villarreal CF"), doce + p(4, rival = "Sevilla FC"))
        assertEquals(Proeza.HISTORICA, l2.temporada); assertFalse(l2.temporadaNueva); assertEquals(Proeza.NINGUNA, l2.dispara)
        assertEquals(Proeza.SURREAL, Proezas.deTemporada(Rol.JUGADOR, List(15) { p(2) }))
    }

    @Test fun sieteGolesYElMundoPierdeLaCabeza() {
        val (u0, _) = Motor.crea(Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(edad = 24)) })
        Carreras.guarda(Carreras.de(u0.id)!!.let { it.copy(fuera = it.fuera + (Fuera.FAMA to 30), valor = 15_000_000) })
        Motor.partido(u0.id, p(7))
        val u = Carreras.de(u0.id)!!
        // La fama salta de golpe hasta estrella (sin el tope de la jornada) y lo que vale, al menos 150 M€
        assertTrue("fama ${u.valorFuera(Fuera.FAMA)}", u.valorFuera(Fuera.FAMA) >= Nivel.ESTRELLA.desde)
        assertTrue("valor ${u.valor}", u.valor >= 150_000_000)
        assertTrue(Carreras.entradasDe(u.id).any { it.tipo == TipoEntrada.HITO && it.titulo == "Partido de otro planeta" })
        val txt = Narrador.partido(u, Carreras.partidos(u.id).last().second)
        assertTrue(txt.contains("PIERDA LA CABEZA"))
        assertEquals(Proeza.SURREAL, Narrador.proeza(u))
        // Es superestrella: puede llegar la apuesta, y las ofertas van a lo grande
        assertTrue(Contratos.superestrella(u))
    }

    @Test fun unChavalQueMeteSieteEnUnMundialRozaLos300() {
        val (u0, _) = Motor.crea(Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(edad = 16)) })
        Carreras.guarda(Carreras.de(u0.id)!!.let { it.copy(valor = 40_000_000) })
        Motor.partido(u0.id, p(7, comp = "Copa del Mundo", fase = "Cuartos de final", rival = "Brasil"))
        val u = Carreras.de(u0.id)!!
        assertTrue("valor ${u.valor}", u.valor in 290_000_000L..Contratos.VALOR_MAX)
        // Y su cláusula, si le blindan, por encima del récord
        assertTrue(Contratos.clausulaMinima(u) > 400_000_000)
    }

    @Test fun unoNormalNoCambiaNada() {
        val (u0, _) = Motor.crea(Ejemplo.universo())
        Motor.partido(u0.id, p(1))
        val u = Carreras.de(u0.id)!!
        assertNull(Proezas.paraClaude(u, Proezas.lee(u, Carreras.partidos(u.id).last().second, Carreras.partidos(u.id).map { it.second })))
        assertTrue(Carreras.entradasDe(u.id).none { it.tipo == TipoEntrada.HITO && it.titulo.startsWith("Partido") })
    }
}
