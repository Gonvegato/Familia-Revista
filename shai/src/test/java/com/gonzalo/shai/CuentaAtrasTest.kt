package com.gonzalo.shai

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * La cuenta atrás de los récords que tiene a tiro: la prensa y la red hablan de ella cuando falta poco, y cuando el plazo pasa sin batirlo,
 * «se le escapa». Con el modo prueba (las plantillas, sin IA): no sale nada a la red ni se gasta nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CuentaAtrasTest {
    private val dia = LocalDate.of(2026, 10, 1)

    @Before fun prepara() {
        Ejemplo.prepara()
        Motor.claude = Claude({ ClaveApi.lee() }, TransporteDePrueba(kotlin.random.Random(5)))
        Motor.separacion = 0
    }
    @After fun suelta() { ClaveApi.fija = null; Gasto.mesFijo = null; Motor.separacion = 6 * 3600_000L }

    /** Con 17 años y 20 días: le quedan 20 días para ser el goleador más joven de la Champions (Ansu Fati, 17 años y 40 días). */
    private fun carrera(): Universo {
        val (u, _) = Motor.crea(Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(edad = 17, nacimiento = Fechas.iso(dia.minusDays(20).minusYears(17)))) })
        return u
    }

    private fun liga(f: LocalDate, goles: Int = 0) = Partido("2026/27", "LaLiga EA Sports", "Girona FC", true, 1, 0, 90, goles, 0, 7.0, fecha = Fechas.iso(f))

    @Test fun laCuentaAtrasSaleEnLaPrensaCuandoFaltaPoco() {
        val u0 = carrera()
        val (e, fallo) = Motor.partido(u0.id, liga(dia))
        assertNull(fallo)
        val u = Carreras.de(u0.id)!!
        val tiro = Historicos.aTiro(u, Carreras.partidos(u.id).map { it.second })
        val c = tiro.first { it.r.n == 8 }
        assertEquals(20L, c.quedanDias)
        assertEquals(Historicos.Urgencia.ALTA, Historicos.urgencia(u, c))
        // Lo que se le pasa a Claude: la cuenta atrás, con su urgencia
        val ctx = Narrador.contexto(u, Carreras.entradasDe(u.id), Carreras.partidos(u.id).map { it.second })
        assertTrue(ctx, ctx.contains("CUENTA ATRÁS · #8") && ctx.contains("quedan 20 días") && ctx.contains("urgencia ALTA"))
        // La prensa (modo prueba) la convierte en titular, y la red y su gente hablan de ella
        val p = Carreras.entrada(e.id)!!
        assertTrue(p.prensa!!.otros.toString(), p.prensa!!.otros.any { "Ansu Fati" in it && "días" in it })
        assertTrue(p.posts.any { "CUENTA ATRÁS" in it.texto || "ÚLTIMA OPORTUNIDAD" in it.texto })
        assertTrue(p.dms.any { "récord" in it.texto })
    }

    @Test fun conSieteDiasOMenosEsLaUltimaOportunidad() {
        val (u, _) = Motor.crea(Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(edad = 17, nacimiento = Fechas.iso(dia.minusDays(35).minusYears(17)))) })
        val (e, _) = Motor.partido(u.id, liga(dia))
        val p = Carreras.entrada(e.id)!!
        assertTrue(p.prensa!!.otros.toString(), p.prensa!!.otros.any { "Última bala" in it })
    }

    @Test fun cuandoPasaElPlazoSinBatirloSeLeEscapa() {
        val u0 = carrera()
        Motor.partido(u0.id, liga(dia))
        // Un mes después, sin marcar en la Champions: el plazo (21/10/2026) ya ha pasado
        val (e, _) = Motor.partido(u0.id, liga(dia.plusDays(30)))
        val u = Carreras.de(u0.id)!!
        assertTrue(u.hitos.any { it.real == 8 && "_escapa_" in it.id })
        assertTrue(Carreras.entradasDe(u.id).any { "Se le escapa" in it.titulo })
        assertNull(Historicos.aTiro(u, Carreras.partidos(u.id).map { it.second }).firstOrNull { it.r.n == 8 })
        val p = Carreras.entrada(e.id)!!
        assertTrue(p.prensa!!.otros.toString(), p.prensa!!.otros.any { "Se le escapa el récord de Ansu Fati" in it || "se le escapa el récord de Ansu Fati" in it })
        // Si lo hubiera conseguido a tiempo, nada de «se le escapa»
        val u1 = carrera()
        Motor.partido(u1.id, liga(dia))
        Motor.partido(u1.id, Partido("2026/27", "UEFA Champions League", "Inter de Milán", true, 2, 1, 90, 1, 0, 8.0, fecha = Fechas.iso(dia.plusDays(10))))
        assertFalse(Carreras.de(u1.id)!!.hitos.any { "_escapa_" in it.id })
        assertTrue(Carreras.de(u1.id)!!.recordsSuyos.any { it.n == 8 })
    }
}
