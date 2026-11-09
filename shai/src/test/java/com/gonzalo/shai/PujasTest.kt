package com.gonzalo.shai

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Las pujas solas entre ofertas que compiten: mejoran, se retiran, no pasan del tope del nivel ni de tres rondas. */
class PujasTest {
    private val u = Ejemplo.universo().let { it.copy(fuera = it.fuera + (Fuera.FAMA to 45)) }   // titular
    private val nova = Oferta("o1", "Nova Sport", "patrocinio", 20_000, 500_000, 3, "Nova quiere tu imagen.")
    private val aurum = Oferta("o2", "Aurum Mobile", "patrocinio", 15_000, 300_000, 2, "Aurum también.")
    private val e1 = Entrada("e1", u.id, TipoEntrada.EVENTO, 1, 1, ofertas = listOf(nova))
    private val e2 = Entrada("e2", u.id, TipoEntrada.EVENTO, 2, 1, ofertas = listOf(aurum, Oferta("o3", "Villarreal CF", "traspaso", 40_000, 0, 4, "")))

    private fun busca(f: (Entrada?) -> Boolean): Entrada? {
        for (s in 0 until 500) { val r = Pujas.calcula(u, listOf(e1, e2), 10, 20, Random(s)); if (f(r)) return r }
        fail("ninguna semilla da ese caso"); return null
    }

    @Test fun soloPujanLasDelMismoTipo() {
        val g = Pujas.grupos(listOf(e1, e2))
        assertEquals(1, g.size); assertEquals(setOf("o1", "o2"), g.first().map { it.second.id }.toSet())
    }

    @Test fun laQueVaDetrasMejoraSinPasarDelTope() {
        val p = busca { it?.ofertas?.firstOrNull()?.estado == Oferta.ABIERTA }!!
        val n = p.ofertas.first()
        assertEquals(TipoEntrada.PUJA, p.tipo); assertEquals("o2", n.antes); assertEquals(1, n.ronda); assertEquals("Aurum Mobile", n.de)
        assertTrue(n.salario >= aurum.salario && n.salario <= Rangos.de(Nivel.TITULAR).patroSemana)
        assertTrue(n.prima <= Rangos.de(Nivel.TITULAR).patroPrima)
        assertFalse(p.aplicada); assertEquals(20, p.visibleDesde)
        // Al salir, la de antes queda superada
        val (pe, vieja) = Pujas.aplica(p, listOf(e1, e2, p))!!
        assertEquals(Oferta.MEJORADA, vieja!!.ofertas.first { it.id == "o2" }.estado)
        assertEquals(Oferta.ABIERTA, pe.ofertas.first().estado)
        // Y a la siguiente, puja la otra (la que ahora va por detrás) o nadie
        val ronda2 = Pujas.grupos(listOf(e1, vieja, pe))
        assertEquals(setOf("o1", n.id), ronda2.first().map { it.second.id }.toSet())
    }

    @Test fun seRetiraYAlSalirDejaDeEstarSobreLaMesa() {
        val p = busca { it?.ofertas?.firstOrNull()?.estado == Oferta.RETIRADA }!!
        assertTrue(p.titulo.contains("se retira"))
        val (_, vieja) = Pujas.aplica(p, listOf(e1, e2, p))!!
        assertEquals(Oferta.MEJORADA, vieja!!.ofertas.first { it.id == "o2" }.estado)
        assertTrue(Pujas.grupos(listOf(e1, vieja, p)).isEmpty())
    }

    @Test fun siYaLaContestasteLaPujaNoCuenta() {
        val p = busca { it?.ofertas?.firstOrNull()?.estado == Oferta.ABIERTA }!!
        val contestada = e2.copy(ofertas = e2.ofertas.map { if (it.id == "o2") it.copy(estado = Oferta.RECHAZADA) else it })
        val (pe, vieja) = Pujas.aplica(p, listOf(e1, contestada, p))!!
        assertNull(vieja); assertEquals(Oferta.MEJORADA, pe.ofertas.first().estado)
    }

    @Test fun conTresRondasYaNoPuja() {
        val cansada = e2.copy(ofertas = listOf(aurum.copy(ronda = Pujas.MAX_RONDAS)))
        for (s in 0 until 50) assertNull(Pujas.calcula(u, listOf(e1, cansada), 10, 20, Random(s)))
    }
}
