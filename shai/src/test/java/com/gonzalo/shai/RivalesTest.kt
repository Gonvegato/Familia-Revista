package com.gonzalo.shai

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Los rivales: surgen solos, se calientan con los choques y, si son futbolistas de verdad, solo hay rivalidad deportiva. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RivalesTest {
    @Before fun prepara() { Ejemplo.prepara(); Gasto.mesFijo = java.time.YearMonth.of(2026, 11) }
    @After fun suelta() { ClaveApi.fija = null; Gasto.mesFijo = null; runCatching { Prueba.pon(RuntimeEnvironment.getApplication(), false) } }

    private fun contra(rival: String, gf: Int, gc: Int, goles: Int = 0, minutos: Int = 90, nota: Double = 7.0) = Partido("2026/27", "LaLiga", rival, true, gf, gc, minutos, goles, 0, nota)

    @Test fun surgeUnoTrasDosDuelosConElMismoClubYSeCalienta() {
        val (u, _) = Motor.crea(Ejemplo.universo())
        Motor.partido(u.id, contra("Sevilla FC", 1, 0, goles = 1))
        assertTrue(Carreras.de(u.id)!!.rivales.none { it.club == "Sevilla FC" })
        // Segundo duelo y te marcan: nace el rival (inventado) del Sevilla
        Motor.partido(u.id, contra("Sevilla FC", 1, 2))
        val r = Carreras.de(u.id)!!.rivales.single { it.club == "Sevilla FC" }
        assertFalse(r.real); assertTrue(r.origen, "Duelos con el Sevilla FC" in r.origen)
        assertTrue(Carreras.entradasDe(u.id).any { it.tipo == TipoEntrada.RIVAL && r.nombre in it.titulo })
        // Y va en el contexto de Claude
        val ctx = Narrador.contexto(Carreras.de(u.id)!!, emptyList(), Carreras.partidos(u.id).map { it.second })
        assertTrue("- RIVAL · ${r.nombre} · Sevilla FC" in ctx && "INVENTADO" in ctx && "RIVALIDAD QUE ACABA DE NACER" in ctx)
        // Otro duelo: sube la intensidad y queda el choque
        Motor.partido(u.id, contra("Sevilla FC", 0, 1))
        val r2 = Carreras.de(u.id)!!.rivales.single { it.id == r.id }
        assertTrue("${r.intensidad} → ${r2.intensidad}", r2.intensidad >= r.intensidad + 8)
        assertTrue(r2.choques.any { "Duelo directo" in it.texto })
    }

    @Test fun elCentralQueTeAnulaYElQueCompitePorTuPuesto() {
        val (u, _) = Motor.crea(Ejemplo.universo())
        Motor.partido(u.id, contra("Girona FC", 0, 0, nota = 5.2))
        assertTrue(Carreras.de(u.id)!!.rivales.any { it.club == "Girona FC" && it.posicion == "central" })
        Motor.partido(u.id, contra("Getafe CF", 2, 0, goles = 1)); Motor.partido(u.id, contra("Elche CF", 1, 0, minutos = 20))
        val p = Carreras.de(u.id)!!.rivales.single { it.origen.startsWith("Compite por tu puesto") }
        assertEquals("Real Betis", p.club)
        // Y al irse a otro club, el excompañero
        val ex = Rivales.excompanero(Carreras.de(u.id)!!.copy(club = "Villarreal CF"), "Real Betis")
        assertTrue(ex.rivales.any { it.origen.startsWith("Excompañero en el Real Betis") })
    }

    @Test fun conUnRivalRealSoloHayLoDeportivo() {
        val (u0, _) = Motor.crea(Ejemplo.universo())
        val u = Carreras.guarda(Rivales.anade(Carreras.de(u0.id)!!, "Futbolista Real", "Sevilla FC", real = true))
        val r = u.rivales.single()
        assertTrue(r.real)
        // Lo personal que diga Claude se tira; lo deportivo, con tope
        val tras = Vida.aplica(u, Efectos(rivales = listOf(CambioRival("Futbolista Real", cambio = 12, choque = "Le contestó en redes con una pulla", tipo = Choque.PERSONAL))))
        assertEquals(r.intensidad, tras.rivales.single().intensidad); assertTrue(tras.rivales.single().choques.isEmpty())
        val dep = Vida.aplica(u, Efectos(rivales = listOf(CambioRival("Futbolista Real", cambio = 12, choque = "La prensa compara sus goles", tipo = Choque.DEPORTIVO))))
        assertEquals(r.intensidad + 5, dep.rivales.single().intensidad)
        assertTrue(Narrador.contexto(u, emptyList(), emptyList()).contains("REAL: solo rivalidad deportiva"))
        // Claude puede proponer uno nuevo, siempre inventado
        val nuevo = Vida.aplica(u, Efectos(rivales = listOf(CambioRival("Iván Saldaña", "Getafe CF", "extremo", "Te hizo el gesto de silencio", 10, "Celebró con el dedo en la boca", Choque.PERSONAL, true))))
        assertFalse(nuevo.rivales.single { it.nombre == "Iván Saldaña" }.real)
    }

    @Test fun enModoPruebaElRivalRealNoRecibeNadaPersonalYElInventadoSi() {
        Prueba.pon(RuntimeEnvironment.getApplication(), true)
        val (u0, _) = Motor.crea(Ejemplo.universo())
        Carreras.guarda(Rivales.anade(Rivales.anade(Carreras.de(u0.id)!!, "Futbolista Real", "Sevilla FC", real = true), "Bruno Lafuente", "Sevilla FC", real = false))
        val (e, fallo) = Motor.partido(u0.id, contra("Sevilla FC", 2, 1, goles = 1))
        assertNull(fallo)
        val u = Carreras.de(u0.id)!!
        val real = u.rivales.single { it.real }; val inv = u.rivales.single { !it.real }
        assertTrue(real.choques.none { it.tipo == Choque.PERSONAL })
        assertTrue(inv.choques.any { it.tipo == Choque.PERSONAL })
        val posts = Carreras.entrada(e.id)!!.posts
        assertTrue(posts.none { it.autor == "Futbolista Real" })            // al real no se le ponen palabras
        assertTrue(posts.any { it.autor == "Bruno Lafuente" })              // el inventado, su pulla
        assertTrue(posts.any { "Futbolista Real" in it.texto && "📊" in it.texto })
        Prueba.vacia()
    }
}
