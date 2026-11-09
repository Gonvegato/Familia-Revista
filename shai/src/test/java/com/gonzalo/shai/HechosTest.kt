package com.gonzalo.shai

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los hechos históricos: lo que cuentas se entiende punto por punto, se comprueba en la web lo de precocidad, la fama salta de golpe
 * (sin el tope de la jornada) hasta donde deja el ruido, y queda en el contexto de todo lo que se escriba después. Con el transporte de
 * prueba: no sale nada a la red ni se gasta nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HechosTest {
    private val cuenta = "Nada más empezar la carrera me convocan con la selección para el Mundial por la lesión de los dos delanteros titulares. " +
        "Soy titular en octavos sin haber debutado como profesional, por algo que el seleccionador ha visto en mí."

    @Before fun prepara() {
        Ejemplo.prepara()
        Motor.claude = Claude({ ClaveApi.lee() }, TransporteDePrueba(kotlin.random.Random(7)))
    }
    @After fun suelta() { ClaveApi.fija = null; Gasto.mesFijo = null }

    private fun canterano(): Universo {
        val (u, _) = Motor.crea(Ejemplo.universo())
        return Carreras.guarda(u.copy(fuera = u.fuera + (Fuera.FAMA to 8)))
    }

    @Test fun todoElMundoHaceSaltarLaFamaDeGolpeYQuedaParaSiempre() {
        val u0 = canterano()
        assertEquals(Nivel.CANTERANO, u0.nivelActual)
        val (l, err) = Motor.entiende(u0.id, cuenta)
        assertNull(err); assertNotNull(l)
        assertTrue(l!!.hechos.isNotEmpty())
        assertTrue("sin haber debutado suena a precocidad", l.hayPrecocidad)
        val (e, fallo) = Motor.hecho(u0.id, cuenta, l, Ruido.MUNDO)
        assertNull(fallo); assertNotNull(e)
        val u = Carreras.de(u0.id)!!
        // La fama salta de golpe: más que el tope de una jornada y al menos un nivel (lo que pida la historia, con el mínimo)
        assertTrue("fama ${u.valorFuera(Fuera.FAMA)}", u.valorFuera(Fuera.FAMA) >= 8 + Ruido.MUNDO.minimo)
        assertTrue(u.nivelActual.ordinal > Nivel.CANTERANO.ordinal)
        val h = e!!.hecho!!
        assertEquals(TipoEntrada.HISTORICO, e.tipo)
        assertEquals(8, h.famaAntes); assertEquals(u.valorFuera(Fuera.FAMA), h.famaDespues)
        assertNotNull("la portada especial", e.prensa)
        assertTrue(e.posts.size >= 8)
        // Lo de precocidad, comprobado (aquí, la respuesta de prueba) y en sus hitos
        assertEquals(false, h.hayPrecedentes)
        assertTrue(u.hitos.any { it.titulo.startsWith("Precocidad:") && it.precoz })
        // Y queda en el contexto de todo lo que se escriba después, aunque pasen muchas cosas
        val ctx = Narrador.contexto(u, Carreras.entradasDe(u.id), Carreras.partidos(u.id).map { it.second }, cuantas = 0)
        assertTrue(ctx.contains("HECHOS HISTÓRICOS DE SU CARRERA"))
        assertTrue(ctx.contains(h.entendido.first()))
        // Se guarda y se lee igual
        assertEquals(h, Entrada.deJson(e.aJson())!!.hecho)
    }

    @Test fun suPaisNoPasaDeEstrellaYSuClubVaPocoAPoco() {
        val u0 = canterano()
        val l = Narrador.LoEntendido(listOf("Ficha por el Real Madrid" to ""), false, "", "")
        Motor.hecho(u0.id, "Ficho por el Real Madrid", l, Ruido.PAIS)
        val u1 = Carreras.de(u0.id)!!
        assertTrue(u1.valorFuera(Fuera.FAMA) >= 8 + Ruido.PAIS.minimo)
        assertTrue(u1.nivelActual.ordinal <= Nivel.ESTRELLA.ordinal)
        val u2 = canterano()
        Motor.hecho(u2.id, "Me nombran capitán del filial", Narrador.LoEntendido(listOf("Capitán del filial" to ""), false, "", ""), Ruido.CLUB)
        val u3 = Carreras.de(u2.id)!!
        assertTrue("con su club, como siempre: poco", u3.valorFuera(Fuera.FAMA) - 8 <= 10)
        // Sin precocidad no se busca nada
        assertEquals(null, Carreras.entradasDe(u2.id).first { it.tipo == TipoEntrada.HISTORICO }.hecho!!.hayPrecedentes)
    }
}
