package com.gonzalo.shai

import org.junit.Assert.*
import org.junit.Test

/** Las cláusulas: las ofertas dicen la suya, se aplica al aceptar y, si la de ahora es una ganga, el mercado lo sabe. */
class ClausulasTest {
    /** Un chaval de 16 años, titular de golpe (tras un hecho histórico), con la cláusula de canterano (5 M€). */
    private fun chaval(): Universo = Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(edad = 16),
        contrato = Contratos.primero(Nivel.CANTERANO, it.club, "2026/27", Rol.JUGADOR), fuera = it.fuera + (Fuera.FAMA to 45)) }

    @Test fun conLaClausulaDeCanteranoYNivelDeTitularEsUnaGanga() {
        val u = chaval()
        assertEquals(5_000_000L, u.contrato!!.clausula)
        assertTrue(Contratos.valorMercado(u) > 10_000_000)
        assertTrue(Contratos.ganga(u))
        assertTrue(Narrador.contexto(u, emptyList(), emptyList()).contains("ES UNA GANGA"))
        // Y su club quiere blindarle: la renovación trae una cláusula mucho más alta
        assertNotNull(Contratos.motivoRenovacion(u, 3))
        assertFalse(Contratos.ganga(Ejemplo.universo().let { it.copy(contrato = Contratos.primero(Nivel.CANTERANO, it.club, "2026/27", Rol.JUGADOR), fuera = it.fuera + (Fuera.FAMA to 5)) }))
    }

    @Test fun laClausulaDeLaOfertaSeVeYSeAplica() {
        val u = chaval()
        val o = Rangos.recorta(Oferta("o", u.club, "renovacion", 60_000, 1_000_000, 5, "El club quiere renovarte.", clausula = 120_000_000), Nivel.TITULAR)
        assertEquals(120_000_000L, o.clausula)
        assertEquals(120_000_000L, Vida.acepta(u, o).contrato!!.clausula)
        // Sin cláusula dicha: la de su nivel; en un patrocinio, ninguna
        assertTrue(Rangos.recorta(o.copy(clausula = 0), Nivel.TITULAR).clausula > 0)
        assertEquals(0L, Rangos.recorta(Oferta("p", "Marca", "patrocinio", 10_000, 0, 2, ""), Nivel.TITULAR).clausula)
        assertEquals(o, Oferta.de(o.aMapa()))
    }
}
