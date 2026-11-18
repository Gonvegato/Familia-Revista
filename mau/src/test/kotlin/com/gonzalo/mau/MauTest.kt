package com.gonzalo.mau

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MauTest {
    private val zona = ZoneId.of("Europe/Madrid")
    private val mediodia = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, zona).toInstant().toEpochMilli()

    @Test fun losOnceDibujosTienenSusGrupos() {
        for (p in Pose.entries) {
            val d = dibujoDe(p)
            val ids = HashSet<String>()
            fun mira(g: Grupo) { g.id?.let { ids += it }; g.hijos.forEach { if (it is Grupo) mira(it) } }
            mira(d.raiz)
            assertTrue("$p: cabeza", "cabeza" in ids)
            assertTrue("$p: contorno", "contorno" in ids)
            if (p == Pose.ESCONDIDO) { assertEquals(80f, d.alto); continue }
            assertEquals("$p alto", 220f, d.alto)
            assertTrue("$p: cuerpo", "cuerpo" in ids)
            assertTrue("$p: cola", "cola" in ids)
        }
    }

    @Test fun losTrazosSonPathsValidos() {
        val letras = Regex("^[MmLlHhVvCcSsQqTtAaZz0-9 .,\\-eE]+$")
        for (p in Pose.entries) {
            fun mira(g: Grupo) { g.hijos.forEach { if (it is Grupo) mira(it) else assertTrue((it as Trazo).d, letras.matches(it.d) && it.d.startsWith("M")) } }
            mira(dibujoDe(p).raiz)
        }
    }

    @Test fun curvas() {
        assertEquals(0f, Curva.SUAVE(0f), 1e-4f)
        assertEquals(1f, Curva.SUAVE(1f), 1e-4f)
        assertEquals(.5f, Curva.SUAVE(.5f), 1e-3f)
        // con rebote: se pasa de 1 antes de llegar
        assertTrue((1..99).any { Curva.ASOMARSE(it / 100f) > 1f })
    }

    @Test fun parpadeaYSeMueveSinSalirseDeMadre() {
        val a = Animador(42)
        var cerrados = 0
        var t = 0L
        while (t < 60_000) {
            val m = a.mueve(Pose.SENTADO, t)
            if (m["ojos-abiertos"]?.alfa == 0f) cerrados++
            m["cola"]?.let { assertTrue(it.giro in -4.01f..6.01f) }
            m["cuerpo"]?.let { assertTrue(it.ey in 1f..1.0151f) }
            m["oreja-izq"]?.let { assertTrue(it.giro in -12.01f..0.01f) }
            m["oreja-der"]?.let { assertTrue(it.giro in -0.01f..12.01f) }
            t += 10
        }
        // en un minuto, entre 10 y 25 parpadeos de 130 ms (cada 3–6 s; algunos dobles): 13 cuadros de 10 ms cada uno
        assertTrue("cerrados $cerrados", cerrados in 10 * 13..25 * 26)
    }

    @Test fun mismaSemillaMismoMovimiento() {
        val a = Animador(7); val b = Animador(7)
        for (t in 0L until 20_000L step 33) assertEquals(a.mueve(Pose.JUGANDO, t).toString(), b.mueve(Pose.JUGANDO, t).toString())
    }

    @Test fun dormidoNoMueveLaCola() {
        val a = Animador(1)
        for (t in 0L until 20_000L step 50) assertNull(a.mueve(Pose.DURMIENDO, t)["cola"])
    }

    @Test fun seDuermeSiNoHacesNadaYSeDespiertaAlTocar() {
        val c = Cerebro(zona = zona, semilla = 1)
        var t = mediodia
        c.toque(t)
        c.tick(t, Lugar.CASA)
        assertEquals(Pose.SENTADO, c.pose(Lugar.CASA, t))
        t += 3 * 60_000L
        c.tick(t, Lugar.CASA)
        assertEquals(Pose.BOSTEZANDO, c.pose(Lugar.CASA, t))
        t += c.DURA_BOSTEZO; c.tick(t, Lugar.CASA)
        assertNotNull(c.dormirse(t))
        t += DURA_DORMIRSE + FUNDIDO_DORMIR; c.tick(t, Lugar.CASA)
        assertEquals(Pose.DURMIENDO, c.pose(Lugar.CASA, t))
        assertFalse(c.di("hola", t))  // dormido no habla por su cuenta
        c.toque(t + 1)
        assertEquals(Pose.SENTADO, c.pose(Lugar.CASA, t + 1))
    }

    @Test fun viendoNoSeDuerme() {
        val c = Cerebro(zona = zona)
        c.toque(mediodia)
        c.tick(mediodia + 60 * 60_000L, Lugar.VIENDO)
        assertEquals(Pose.VIENDO, c.pose(Lugar.VIENDO, mediodia + 60 * 60_000L))
    }

    @Test fun deNocheEnCasaSeDuermeAntes() {
        val c = Cerebro(zona = zona)
        val noche = ZonedDateTime.of(2026, 11, 18, 2, 0, 0, 0, zona).toInstant().toEpochMilli()
        assertEquals(60_000L, c.dormirTras(Lugar.CASA, noche))
        assertEquals(12 * 60_000L, c.dormirTras(Lugar.LEYENDO, noche))
    }

    @Test fun callado_escondido_yLaPausaEntreFrases() {
        val g = GuardadoEnMemoria()
        val c = Cerebro(g, zona)
        val t = mediodia
        assertTrue(c.di("uno", t))
        c.callaBocadillo()
        assertFalse("habló hace poco", c.di("dos", t + 1_000))
        assertTrue("importante", c.di("tres", t + 2_000, importante = true))
        c.callaBocadillo()
        c.calla(t + 3_000)
        assertFalse(c.di("cuatro", t + 10 * 60_000L))
        assertTrue("lo pedido sí", c.di("cinco", t + 10 * 60_000L, espontaneo = false))
        // se guarda: otro Cerebro con lo mismo sigue callado
        assertTrue(Cerebro(g, zona).callado(t + 30 * 60_000L))
        assertFalse(Cerebro(g, zona).callado(t + 61 * 60_000L))
        c.esconde()
        assertTrue(Cerebro(g, zona).escondido)
        assertFalse(c.di("seis", t + 2 * 3_600_000L))
    }

    @Test fun elBocadilloSeVaSolo() {
        val c = Cerebro(zona = zona)
        c.di("hola", mediodia)
        assertNotNull(c.bocadillo)
        c.tick(mediodia + 20_000, Lugar.CASA)
        assertNull(c.bocadillo)
    }

    @Test fun sprint() {
        val c = Cerebro(zona = zona)
        var acabado = false
        c.empiezaSprint(25 * 60_000L, mediodia) { acabado = true; "¡412 palabras!" }
        c.tick(mediodia + 24 * 60_000L, Lugar.ESCRIBIENDO)
        assertFalse(acabado)
        // en un sprint no se duerme aunque no toques nada
        assertEquals(Pose.ESCRIBIENDO, c.pose(Lugar.ESCRIBIENDO, mediodia + 24 * 60_000L))
        c.tick(mediodia + 25 * 60_000L, Lugar.ESCRIBIENDO)
        assertTrue(acabado)
        assertEquals("¡412 palabras!", c.bocadillo?.texto)
        assertEquals(Pose.CELEBRANDO, c.pose(Lugar.ESCRIBIENDO, mediodia + 25 * 60_000L + 100))
    }

    @Test fun unaVezAlDia() {
        val c = Cerebro(zona = zona)
        assertTrue(c.primeraVezHoy("saludo", mediodia))
        assertFalse(c.primeraVezHoy("saludo", mediodia + 3_600_000L))
        assertTrue(c.primeraVezHoy("saludo", mediodia + 24 * 3_600_000L))
    }

    @Test fun eligeNoRepite() {
        val c = Cerebro(semilla = 3)
        var ultima = ""
        repeat(50) { val f = c.elige(Frases.atascada); assertTrue(f != ultima); ultima = f }
    }
}
