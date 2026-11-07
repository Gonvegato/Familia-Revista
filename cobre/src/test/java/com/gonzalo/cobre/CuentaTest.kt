package com.gonzalo.cobre

import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Progreso
import com.gonzalo.iptv.TipoLista
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Todo lo tuyo de Cobre está en la cuenta y llega a tus otros dispositivos, con la cuenta de verdad (el emulador de Firebase con las
 * reglas del repositorio): tus favoritos y su orden, los grupos que escondes, el último canal y lo visto de cada película y episodio
 * («Seguir viendo»). Con el formato de `nube/ESQUEMA.md`, y nada secreto en `/users`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CuentaTest : ConCuenta() {
    @Before fun prepara() { preparaCuenta() }
    @After fun fin() { suelta() }

    private val l1 = Canal("x101", "La 1 HD", "Nacionales", idXtream = 101)
    private val l2 = Canal("c0123456789abcdef", "Antena 3", "Nacionales", url = "http://tv.ejemplo.test/live/pepe/S3cr3t/2.ts")
    private val l3 = Canal("x205", "DAZN LaLiga", "Deportes", idXtream = 205)

    @Test fun losFavoritosYSuOrdenViajan() {
        MiCuenta.alternaFavorito("lprueba001", l1); MiCuenta.alternaFavorito("lprueba001", l2); MiCuenta.alternaFavorito("lprueba001", l3)
        assertEquals(listOf("x101", "c0123456789abcdef", "x205"), MiCuenta.favoritos().map { it.canal })
        sincroniza()
        val col = otro.coleccion(DatosCuenta.FAVORITOS)
        val n = col.nodo("lprueba001~x101")!!
        assertEquals("lprueba001", n.texto("lista")); assertEquals("x101", n.texto("canal")); assertEquals("La 1 HD", n.texto("n")); assertEquals(1L, n.entero("o"))
        // El orden: DAZN arriba del todo
        MiCuenta.mueveFavorito("lprueba001~x205", -1); MiCuenta.mueveFavorito("lprueba001~x205", -1)
        assertEquals(listOf("x205", "x101", "c0123456789abcdef"), MiCuenta.favoritos().map { it.canal })
        sincroniza()
        assertEquals(listOf("x205", "x101", "c0123456789abcdef"), col.vivos().sortedBy { it.entero("o") }.map { it.texto("canal") })
        // Quitar es «b» (el nodo se queda); y en el otro dispositivo lo vuelves a poner: vuelve aquí
        MiCuenta.alternaFavorito("lprueba001", l1)
        sincroniza()
        assertTrue(col.nodo("lprueba001~x101")!!.borrado)
        otro.coleccion(DatosCuenta.FAVORITOS).pon("lprueba001~x101", mapOf("b" to 0L, "o" to 9L))
        sincroniza()
        espera("vuelve") { MiCuenta.esFavorito("lprueba001", "x101") }
        // En la cuenta, nada de direcciones (la de Antena 3 lleva la contraseña)
        assertFalse(Json.escribe(Emulador.lee("/users/$uid")).contains("S3cr3t"))
    }

    @Test fun losGruposEscondidosYElUltimoCanal() {
        MiCuenta.ocultaGrupo("lprueba001", "ES | Deportes / Fútbol.", true)
        MiCuenta.ponUltimo("lprueba001", l3)
        sincroniza()
        val g = otro.coleccion(DatosCuenta.GRUPOS).vivos().single()
        assertEquals("ES | Deportes / Fútbol.", g.texto("g")); assertTrue(g.si("oculto")); assertEquals("lprueba001", g.texto("lista"))
        val u = otro.coleccion(DatosCuenta.ESTADO).nodo("ultimo")!!
        assertEquals("x205", u.texto("canal")); assertEquals("DAZN LaLiga", u.texto("n")); assertTrue(u.entero("ul") > 0)
        // Volver a enseñarlo, desde el otro dispositivo
        otro.coleccion(DatosCuenta.GRUPOS).pon(g.ref, mapOf("oculto" to false))
        sincroniza()
        espera("se ve otra vez") { MiCuenta.ocultos("lprueba001").isEmpty() }
        // El último canal que ves en el otro dispositivo, aquí
        otro.coleccion(DatosCuenta.ESTADO).pon("ultimo", DatosCuenta.camposUltimo("lprueba001", l1, System.currentTimeMillis() + 1000))
        sincroniza()
        espera("el último, el del otro") { MiCuenta.ultimo()?.canal == "x101" }
    }

    @Test fun loVistoYSeguirViendo() {
        Listas.pon(Lista("lprueba001", "Mi servidor", TipoLista.XTREAM, "http://tv.ejemplo.test:8080", "pepe", "S3cr3t"))
        val ahora = System.currentTimeMillis()
        MiCuenta.ponProgreso(Progreso("lprueba001", "p5001", 5001, "mkv", "Dune: Parte dos", pos = 40 * 60_000L, dur = 166 * 60_000L, ultima = ahora - 10_000))
        MiCuenta.ponProgreso(Progreso("lprueba001", "e9011", 9011, "mkv", "Se acerca el invierno", 3001, "Juego de tronos", 1, 1, 57 * 60_000L, 57 * 60_000L, true, ahora - 5_000))
        sincroniza()
        val p = otro.coleccion(DatosCuenta.PROGRESO).nodo("lprueba001~e9011")!!
        assertEquals("episodio", p.texto("tipo")); assertEquals(3001L, p.entero("serie")); assertEquals("Juego de tronos", p.texto("st")); assertTrue(p.si("visto")); assertEquals(1L, p.entero("temp"))
        assertEquals(listOf("e9011", "p5001"), MiCuenta.seguir().map { it.video })
        // En la tablet sigues la película hasta el minuto 120: aquí se sigue desde ahí
        otro.coleccion(DatosCuenta.PROGRESO).pon("lprueba001~p5001", mapOf("pos" to 120 * 60_000L, "ul" to ahora + 60_000))
        sincroniza()
        espera("llega") { MiCuenta.progreso("lprueba001", "p5001")?.pos == 120 * 60_000L }
        assertEquals("la película, ahora la primera", "p5001", MiCuenta.seguir().first().video)
        // Vista (al 90 %, o a mano): sale de «Seguir viendo»
        MiCuenta.marcaVisto(MiCuenta.progreso("lprueba001", "p5001")!!, true)
        assertEquals(listOf("e9011"), MiCuenta.seguir().map { it.video })
        // Lo de una lista que ya no tienes, no sale
        Listas.quita("lprueba001")
        assertTrue(MiCuenta.seguir().isEmpty())
    }

    @Test fun sinCuentaNoSeEscribeNada() {
        Cuenta.salir()
        espera("fuera") { !LaFamilia.familia.sesion.dentro }
        MiCuenta.alternaFavorito("lprueba001", l1); MiCuenta.ponUltimo("lprueba001", l1); MiCuenta.ocultaGrupo("lprueba001", "x", true)
        assertEquals(0, LaFamilia.familia.pendientes)
        assertNull(MiCuenta.ultimo())
    }
}
