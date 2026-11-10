package com.gonzalo.shai

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Elegir agente al empezar (tres candidatos), su comisión de verdad y cambiar de agente después. Con el transporte de prueba: sin gastar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentesTest {
    @Before fun prepara() { Ejemplo.prepara(); Motor.claude = Claude({ ClaveApi.lee() }, TransporteDePrueba(kotlin.random.Random(2))) }
    @After fun suelta() { ClaveApi.fija = null; Gasto.mesFijo = null }

    @Test fun alEmpezarHayTresCandidatosYElElegidoCobraSuComision() {
        val (u0, fallo) = Motor.crea(Ejemplo.universo())
        assertNull(fallo)
        assertEquals(3, u0.candidatos.size)
        assertEquals(3, u0.candidatos.map { it.nombre }.distinct().size)
        val bruto = Contratos.cobra(u0, null)
        assertNull(Motor.eligeAgente(u0.id, 1))
        val u = Carreras.de(u0.id)!!
        val c = u0.candidatos[1]
        assertTrue(u.candidatos.isEmpty())
        assertEquals(c.nombre, u.suAgente!!.nombre); assertEquals(c.comision, u.comision)
        assertEquals(bruto - bruto * c.comision / 100, Contratos.cobra(u, null))
        assertTrue(Carreras.entradasDe(u.id).any { it.tipo == TipoEntrada.AGENTE })
        assertTrue(Narrador.contexto(u, emptyList(), emptyList()).contains("SU AGENTE es ${c.nombre}"))
        // La prima de firma también: el agente se queda su parte
        val antes = u.dinero
        val conPrima = Vida.acepta(u, Oferta("o", "Marca", "patrocinio", 1_000, 100_000, 1, ""))
        assertEquals(antes + 100_000 - 100_000 * c.comision / 100, conPrima.dinero)
    }

    @Test fun cambiarDeAgenteDejaAlDeAntesComoExrepresentante() {
        val (u0, _) = Motor.crea(Ejemplo.universo())
        Motor.eligeAgente(u0.id, 0)
        val primero = Carreras.de(u0.id)!!.suAgente!!
        assertNull(Motor.candidatos(u0.id))
        val u1 = Carreras.de(u0.id)!!
        assertTrue(u1.candidatos.none { Vida.mismo(it.nombre, primero.nombre) })
        Motor.eligeAgente(u0.id, 0)
        val u = Carreras.de(u0.id)!!
        assertNotEquals(primero.id, u.agente)
        val viejo = (u.entorno + u.exEntorno).first { it.id == primero.id }
        assertEquals("Exrepresentante", viejo.rol)
        assertEquals(1, u.entorno.count { it.rol.startsWith("Representante") })
    }
}
