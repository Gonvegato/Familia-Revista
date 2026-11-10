package com.gonzalo.shai

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lo que vale se recalcula tras cada partido (en un Mundial, de forma exponencial) y las ofertas abiertas suben con ello. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ValorTest {
    @Before fun prepara() { Ejemplo.prepara(); Motor.claude = Claude({ ClaveApi.lee() }, TransporteDePrueba(kotlin.random.Random(4))); Motor.pideAgentes = false }
    @After fun suelta() { ClaveApi.fija = null; Gasto.mesFijo = null; Motor.pideAgentes = true }

    @Test fun unChavalQueRompeElMundialSeDispara() {
        val (u0, _) = Motor.crea(Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(edad = 16)) })
        Carreras.guarda(Carreras.de(u0.id)!!.let { it.copy(fuera = it.fuera + (Fuera.FAMA to 45), contrato = Contratos.primero(Nivel.CANTERANO, it.club, "2026/27", Rol.JUGADOR)) })
        // El Barça le ofrece renovar (y la deja descansar)
        val barca = Oferta("ob", "FC Barcelona", "renovacion", 40_000, 500_000, 5, "El Barça quiere renovarte.", clausula = 50_000_000)
        Carreras.guarda(Entrada(Universo.nuevoId("e"), u0.id, TipoEntrada.EVENTO, 1, 0, "El Barça mueve ficha", ofertas = listOf(barca)))
        val v0 = Contratos.valorMercado(Carreras.de(u0.id)!!)
        // Octavos: hat-trick a Portugal
        Motor.partido(u0.id, Partido("2026/27", "Copa del Mundo", "Portugal", true, 3, 1, 90, 3, 0, 9.1, mvp = true, fase = "Octavos de final"))
        val v1 = Carreras.de(u0.id)!!.valor
        assertTrue("v0 $v0 → v1 $v1", v1 > v0 * 3 / 2)
        val ofertas1 = Carreras.entradasDe(u0.id).flatMap { it.ofertas }
        val barca1 = ofertas1.first { it.de == "FC Barcelona" && it.estado == Oferta.ABIERTA }
        assertTrue(barca1.salario > barca.salario); assertTrue(barca1.clausula > barca.clausula)
        assertEquals(Oferta.MEJORADA, ofertas1.first { it.id == "ob" }.estado)
        assertTrue(Carreras.entradasDe(u0.id).any { it.titulo == "Tu precio se dispara: suben las ofertas" })
        // Cuartos: 2 goles y 3 asistencias → otra vez arriba (encadenado)
        Motor.partido(u0.id, Partido("2026/27", "Copa del Mundo", "Francia", false, 3, 2, 90, 2, 3, 9.4, mvp = true, fase = "Cuartos de final"))
        val u = Carreras.de(u0.id)!!
        assertTrue("v1 $v1 → v2 ${u.valor}", u.valor > v1 * 3 / 2)
        val barca2 = Carreras.entradasDe(u0.id).flatMap { it.ofertas }.first { it.de == "FC Barcelona" && it.estado == Oferta.ABIERTA }
        assertTrue(barca2.salario > barca1.salario)
        // Las ofertas ya van por lo que vale, no por su fama
        assertTrue(Contratos.nivelOfertas(u).ordinal >= Nivel.ESTRELLA.ordinal)
        assertTrue(Narrador.contexto(u, Carreras.entradasDe(u.id), Carreras.partidos(u.id).map { it.second }).contains("Ofertas que ya tiene sobre la mesa"))
    }

    @Test fun unAmistosoYUnMalPartidoNoLoDisparan() {
        val u = Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(edad = 25)) }
        val v = Contratos.valorMercado(u)
        assertTrue(Contratos.revalora(u, Partido("2026/27", "Amistoso", "Ajax", true, 1, 0, 90, 1, 0, 7.0)) < v * 11 / 10)
        assertTrue(Contratos.revalora(u, Partido("2026/27", "LaLiga EA Sports", "Getafe CF", true, 0, 2, 90, 0, 0, 5.0)) < v)
    }
}
