package com.gonzalo.azabache.pc

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/**
 * Azabache del ordenador con la cuenta de verdad (el emulador de Firebase con las reglas del repositorio). «Otra» hace de Hiedra o de
 * Marfil (o del Azabache del móvil) en otro dispositivo: lo que escribe con el formato de la cuenta, aquí se ve; lo que se escribe aquí,
 * lo lee ella tal como lo leen las apps de Android (los mismos campos que comprueban sus pruebas).
 */
class CuentaTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var otra: Familia
    private lateinit var aqui: Familia

    @Before fun prepara() {
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        otra = Emulador.dispositivo("gonzalo")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg
        LaFamilia.dirPruebas = tmp.root
        LaFamilia.llaveroPruebas = LlaveroMemoria()
        aqui = LaFamilia.inicia("azabache").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Cuenta.olvidaParaPruebas()
        listOf("juegos", "libros", "tiempo", "azabache", "ajustes").forEach { otra.coleccion(it); LaFamilia.coleccion(it) }
        sincroniza()
    }

    @After fun fuera() { LaFamilia.reiniciaParaPruebas(); Cuenta.olvidaParaPruebas() }

    private fun sincroniza() { assertTrue(otra.sincroniza().ok); assertTrue(aqui.sincroniza().ok); assertTrue(otra.sincroniza().ok) }

    @Test fun loQueCambiasEnMarfilLlegaYUnLibroEnCursoNuncaSaleLeidoConUnCero() {
        val kid = Cuenta.kidDe("libro:dune:frank herbert")
        assertEquals("el mismo nodo que Marfil", kid, Cuenta.kidDe(Cuenta.claveLibro("Dune", "Frank Herbert")))
        otra.coleccion("libros").pon(kid, mapOf("clave" to "libro:dune:frank herbert", "t" to "Dune", "a" to "Frank Herbert", "estado" to "en_curso", "fr" to 0.3))
        sincroniza()
        val f = Cuenta.fichas(Cuenta.LIBROS).single { it.nombre == "Dune" }
        assertFalse(f.valorada); assertEquals(1, f.estado); assertEquals("Frank Herbert", f.creador)
        // Lo terminas en Marfil: pasa a leídos «sin valorar»
        otra.coleccion("libros").pon(kid, mapOf("estado" to "terminado", "fin" to listOf(System.currentTimeMillis())))
        sincroniza()
        val g = Cuenta.fichas(Cuenta.LIBROS).single { it.nombre == "Dune" }
        assertTrue(g.valorada); assertTrue("sin valorar, nunca un 0", g.sinValorar)
        assertEquals(LocalDate.now().toEpochDay(), g.terminado)
        // Lo valoras aquí: la nota va al libro de la cuenta y lo de Marfil no se toca
        Cuenta.guarda(Cuenta.LIBROS, g, g.con(mapOf("general" to 88L, "gfx" to 80L, "story" to 90L, "music" to 70L, "gameplay" to 85L, "review" to "La especia.")), true)
        sincroniza()
        val n = otra.coleccion("libros").nodo(kid)!!
        assertEquals(88L, n.entero("nota")); assertEquals(listOf(80L, 90L, 70L, 85L), n.lista("crit"))
        assertEquals("La especia.", n.texto("resena")); assertEquals("terminado", n.texto("estado"))
        assertEquals(0.3, n.decimal("fr"), 0.0001); assertEquals(1, n.lista("fin").size)
        assertTrue("con su número de Azabache", n.entero("azid") > 0)
        assertFalse(Cuenta.fichas(Cuenta.LIBROS).single { it.nombre == "Dune" }.sinValorar)
    }

    @Test fun valorarAquiNoPisaLoQueHiedraCambiaALaVez() {
        otra.coleccion("juegos").pon("j_aaaaaaaaaaaa", mapOf("t" to "Hades", "pl" to "PC", "estado" to "en_curso", "ln" to listOf(mapOf("tp" to "steam", "s" to "1145360"))))
        sincroniza()
        val f = Cuenta.fichas(Cuenta.JUEGOS).single { it.nombre == "Hades" }
        // Aquí lo das por pasado y lo valoras; en el móvil, mientras, Hiedra le cambia la plataforma
        Cuenta.guarda(Cuenta.JUEGOS, f, f.con(mapOf("general" to 95L, "gfx" to 90L, "story" to 85L, "music" to 100L, "gameplay" to 98L, "end" to LocalDate.now().toEpochDay())), true)
        otra.coleccion("juegos").cambia("j_aaaaaaaaaaaa", mapOf("pl" to "Switch"))
        sincroniza(); sincroniza()
        val n = otra.coleccion("juegos").nodo("j_aaaaaaaaaaaa")!!
        assertEquals("terminado", n.texto("estado")); assertEquals(95L, n.entero("nota"))
        assertEquals("Switch", n.texto("pl")); assertEquals(1, n.lista("ln").size)
        assertTrue(n.entero("fin") > 1_000_000_000_000L)
        assertEquals("Switch", Cuenta.fichas(Cuenta.JUEGOS).single { it.nombre == "Hades" }.plataforma)
        // Cambiar solo la reseña no vuelve a escribir el estado ni la plataforma
        val g = Cuenta.fichas(Cuenta.JUEGOS).single { it.nombre == "Hades" }
        otra.coleccion("juegos").cambia("j_aaaaaaaaaaaa", mapOf("estado" to "en_curso"))
        Cuenta.guarda(Cuenta.JUEGOS, g, g.con(mapOf("review" to "Otra vuelta.")), true)
        sincroniza(); sincroniza()
        val m = otra.coleccion("juegos").nodo("j_aaaaaaaaaaaa")!!
        assertEquals("en_curso", m.texto("estado")); assertEquals("Otra vuelta.", m.texto("resena"))
    }

    @Test fun unJuegoNuevoDeAquiEsUnJuegoDeLaCuentaQueVeHiedra() {
        val o = linkedMapOf<String, Any?>("id" to 4242L, "name" to "Celeste", "platform" to "PC", "status" to 1L, "cover" to "", "hero" to "", "entries" to emptyList<Any?>(), "start" to LocalDate.now().toEpochDay())
        Cuenta.guarda(Cuenta.JUEGOS, null, o, false)
        sincroniza()
        val n = otra.coleccion("juegos").vivos().single { it.texto("t") == "Celeste" }
        assertTrue(n.ref.matches(Regex("j_[0-9a-f]{12}")))
        assertEquals("en_curso", n.texto("estado")); assertEquals("PC", n.texto("pl")); assertEquals(4242L, n.entero("azid"))
        assertNull("sin valorar: sin nota", n["nota"])
        // Y un libro nuevo: el nodo es el de su clave (el mismo que crearía Marfil)
        Cuenta.guarda(Cuenta.LIBROS, null, linkedMapOf("id" to 77L, "name" to "Rayuela", "creator" to "Julio Cortázar", "status" to 0L, "cover" to ""), false)
        sincroniza()
        val kid = Cuenta.kidDe("libro:rayuela:julio cortazar")
        val l = otra.coleccion("libros").nodo(kid)!!
        assertEquals("libro:rayuela:julio cortazar", l.texto("clave")); assertEquals("pendiente", l.texto("estado")); assertEquals("Julio Cortázar", l.texto("a"))
        // Borrarlo aquí lo borra de la cuenta (con su marca: nada se borra de verdad)
        Cuenta.borra(Cuenta.fichas(Cuenta.LIBROS).single { it.nombre == "Rayuela" })
        sincroniza()
        assertTrue(otra.coleccion("libros").todos()[kid]!!.borrado)
    }

    @Test fun loPropioDeAzabacheVaElementoAElementoComoEnElMovil() {
        // Una categoría tuya y una película valorada en ella
        val pelis = customKind(Cuenta.siguienteKind(), "Películas", listOf("Guion", "Actuación", "Dirección", "Música"), "Viendo", 0, SRC_MOVIE)
        Cuenta.guardaKind(pelis)
        val o = linkedMapOf<String, Any?>("id" to 603L, "name" to "Matrix", "cover" to "https://image.tmdb.org/t/p/w342/m.jpg", "general" to 90L, "gfx" to 80L, "story" to 85L, "music" to 75L, "gameplay" to 95L, "review" to "", "hero" to "", "start" to 0L, "end" to 0L)
        Cuenta.guarda(pelis.id, null, o, true)
        Cuenta.guardaNota(Nota(11L, LocalDate.now().toEpochDay(), "Hoy, Matrix", listOf("${pelis.id}:603")))
        Cuenta.guardaSaga(Saga(99L, "Matrix", listOf(603L), kind = pelis.id))
        sincroniza()
        val az = otra.coleccion("azabache")
        val k = Json.lee(az.nodo("kinds~${pelis.id}")!!.texto("o")).comoMapa()
        assertEquals("Películas", k["title"]); assertEquals(SRC_MOVIE, k["source"])
        val e = az.nodo("items_${pelis.id}~603")!!
        assertEquals(0L, e.entero("b")); assertEquals("Matrix", Json.lee(e.texto("o")).comoMapa()["name"])
        assertTrue(az.nodo("diario~11")!!.texto("o").contains("Hoy, Matrix"))
        assertTrue(az.nodo("sagas~99")!!.texto("o").contains("\"items\":[603]"))
        // Lo que escribe tu otro Azabache se lee de la cuenta, sin copia
        az.pon("sagas~100", mapOf("o" to """{"id":100,"name":"Dune","kind":1,"items":[],"updated":5}""", "b" to 0L))
        sincroniza()
        assertTrue(Cuenta.sagas().any { it.id == 100L && it.name == "Dune" })
        // Pasar la película a «viendo» la quita de las valoradas y la pone en curso
        val f = Cuenta.fichas(pelis.id).single()
        Cuenta.guarda(pelis.id, f, f.con(mapOf("status" to 1L)), false)
        sincroniza()
        assertTrue(az.todos()["items_${pelis.id}~603"]!!.borrado)
        assertEquals(1L, Json.lee(az.nodo("iplays_${pelis.id}~603")!!.texto("o")).comoMapa()["status"])
        // Borrar la nota la borra de la cuenta
        Cuenta.borraNota(Cuenta.notas().single())
        sincroniza()
        assertTrue(az.todos()["diario~11"]!!.borrado)
    }

    @Test fun lasHorasDeHiedraYLoLeidoEnMarfilSeSumanEnLaFicha() {
        otra.coleccion("juegos").pon("j_bbbbbbbbbbbb", mapOf("t" to "Outer Wilds", "estado" to "en_curso"))
        otra.coleccion("tiempo").pon("j_bbbbbbbbbbbb~hiedra-1", mapOf("ref" to "j_bbbbbbbbbbbb", "fuente" to "hiedra-1", "total" to 3_600_000L, "d2026-11-01" to 3_600_000L))
        otra.coleccion("tiempo").pon("j_bbbbbbbbbbbb~steam", mapOf("ref" to "j_bbbbbbbbbbbb", "fuente" to "steam", "total" to 1_800_000L, "d2026-11-02" to 1_800_000L))
        sincroniza()
        val f = Cuenta.fichas(Cuenta.JUEGOS).single { it.nombre == "Outer Wilds" }
        assertEquals(90, f.minutos)
        assertEquals(mapOf("2026-11-01" to 3_600_000L, "2026-11-02" to 1_800_000L), Cuenta.porDia("j_bbbbbbbbbbbb"))
    }

    @Test fun elNumeroDeUnaFichaSinAzidEsElMismoQueEnElMovil() {
        // El mismo cálculo que Cosas.idDeRef en Android (FNV-1a de «familia:<ref>»): mismas sagas y diario en los dos
        assertEquals(Cuenta.idDeRef("j_aaaaaaaaaaaa"), Cuenta.idDeRef("j_aaaaaaaaaaaa"))
        var h = -0x340d631b7bdddcdbL
        for (ch in "familia:j_aaaaaaaaaaaa") { h = h xor ch.code.toLong(); h *= 0x100000001b3L }
        assertEquals((h and Long.MAX_VALUE) % 9_000_000_000_000L + 100_000_000_000L, Cuenta.idDeRef("j_aaaaaaaaaaaa"))
    }
}
