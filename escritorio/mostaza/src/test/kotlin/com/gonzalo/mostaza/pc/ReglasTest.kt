package com.gonzalo.mostaza.pc

import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Nodo
import com.gonzalo.familia.nucleo.comoMapa
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Las tres reglas, con la cuenta de verdad y un reloj de mentira para la sesión del reproductor:
 *  1. nada entra en tu biblioteca hasta llevar 10 minutos viéndolo de verdad en una sesión (las pausas y los saltos no cuentan; salir
 *     termina la sesión); antes, nada se escribe en la cuenta. A los 10 minutos entra en «Viendo» (o en «Visto» si llega al final);
 *  2. el tiempo se apunta por día y dispositivo (`tiempo/<ref>~<dispositivo>`), la posición viaja, visto es al 90 % y una serie está
 *     vista con todos sus episodios estrenados vistos;
 *  3. borrar es para siempre: lo que entra solo no lo devuelve, ni aunque lo vuelvas a ver; solo «Añadir», el mismo y con lo suyo.
 * (La misma prueba que la del móvil, letra a letra: en el ordenador, la cuenta es la de [LaFamilia] del escritorio y «otro» es el móvil.)
 */
class ReglasTest : ConCuenta() {
    private var reloj = 1_000_000L

    @Before fun prepara() { preparaCuenta() }
    @After fun fin() { suelta() }

    private val peli = MetaBreve("tt0111161", "movie", "Cadena perpetua", poster = "", ano = "1994")
    private val metaPeli = Meta("tt0111161", "movie", "Cadena perpetua", ano = "1994", duracion = "142 min", imdb = "tt0111161")
    private val dur = 142 * 60_000L

    private fun visionado(b: MetaBreve = peli, m: Meta? = metaPeli, v: Video? = null) = Visionado(b, m, v, reloj = { reloj })

    /** Reproduce [ms] de verdad (a pasos de cinco segundos) desde [desde]. Devuelve la posición al acabar. */
    private fun ve(v: Visionado, ms: Long, desde: Long, d: Long = dur): Long {
        v.sesion.reproduciendo(true)
        var pos = desde; var t = 0L
        while (t < ms) { reloj += 5_000; t += 5_000; pos = (pos + 5_000).coerceAtMost(d); v.tick(pos, d) }
        v.sesion.reproduciendo(false)
        return pos
    }

    private fun cuenta(col: String) = Emulador.lee("/users/$uid/familia/$col").comoMapa()

    @Test fun regla1NadaEntraHastaDiezMinutosSeguidos() {
        var v = visionado()
        var pos = ve(v, 5 * 60_000L, 0L)
        // Una pausa de veinte minutos no cuenta
        reloj += 20 * 60_000L; v.tick(pos, dur)
        // Saltar una hora adelante tampoco
        v.sesion.reproduciendo(true); v.sesion.salto(); pos += 3_600_000L; v.sesion.reproduciendo(false)
        pos = ve(v, 4 * 60_000L + 55_000L, pos)
        assertEquals("9:55 de verdad", 595_000L, v.sesion.vistoMs)
        assertNull(Biblioteca.por(peli.id))
        assertEquals("nada en la cola de la cuenta", 0, LaFamilia.familia.pendientes)
        v.termina(pos, dur)
        sincroniza()
        assertTrue("nada en la cuenta: ni el título…", cuenta("titulos").isEmpty())
        assertTrue("…ni lo visto…", cuenta("progreso").isEmpty())
        assertTrue("…ni el tiempo", cuenta("tiempo").isEmpty())
        assertNotNull("el punto para seguir, solo en este dispositivo", Reanudar.de(peli.id))
        assertEquals(pos, Reanudar.desde(peli.id))

        // Salir del reproductor termina la sesión: la siguiente empieza de cero (5 + 6 no son 10 seguidos)
        v = visionado(); pos = ve(v, 6 * 60_000L, pos); v.termina(pos, dur)
        sincroniza()
        assertTrue(cuenta("titulos").isEmpty()); assertNull(Biblioteca.por(peli.id))

        // Diez minutos en una sesión: entra en «Viendo», con el tiempo de esa sesión
        v = visionado()
        pos = ve(v, 10 * 60_000L, 0L)
        assertTrue(v.entro); assertEquals(Estado.VIENDO, Biblioteca.por(peli.id)!!.estado)
        v.termina(pos, dur)
        sincroniza()
        val n = otro.coleccion("titulos").nodo("tt0111161")!!
        assertEquals("Cadena perpetua", n.texto("t")); assertEquals("movie", n.texto("tipo")); assertEquals("en_curso", n.texto("estado")); assertEquals("tt0111161", n.texto("imdb"))
        assertEquals(142L, n.entero("dur"))
        val hoy = Biblioteca.hoy()
        val t = otro.coleccion("tiempo").nodo("tt0111161~${Biblioteca.dispositivo}")!!
        assertEquals("tt0111161", t.texto("ref")); assertEquals(Biblioteca.dispositivo, t.texto("fuente"))
        assertEquals("solo los 10 minutos de esa sesión", 600_000L, t.entero("total")); assertEquals(600_000L, t.entero("d$hoy")); assertEquals(1L, t.entero("veces"))
        val p = otro.coleccion("progreso").nodo("tt0111161")!!
        assertEquals(600_000L, p.entero("pos")); assertEquals(dur, p.entero("dur")); assertFalse(p.si("visto"))
    }

    @Test fun regla1SiLlegaAlFinalEnEsaSesionEntraEnVisto() {
        val v = visionado()
        // Empieza a ver por el minuto 125 y llega al final: 17 minutos de verdad
        val pos = ve(v, 17 * 60_000L, 125 * 60_000L)
        v.termina(pos, dur)
        sincroniza()
        val n = otro.coleccion("titulos").nodo("tt0111161")!!
        assertEquals("terminado", n.texto("estado")); assertTrue(n.entero("fin") > 0)
        assertTrue(otro.coleccion("progreso").nodo("tt0111161")!!.si("visto"))
    }

    @Test fun regla2ElTiempoLaPosicionYLasSeries() {
        // Una película que ya está en tu biblioteca: cualquier rato cuenta (por día y dispositivo) y su posición viaja
        Biblioteca.anade(peli, metaPeli)
        var v = visionado()
        var pos = ve(v, 2 * 60_000L, 0L)
        v.termina(pos, dur)
        sincroniza()
        assertEquals(120_000L, otro.coleccion("tiempo").nodo("tt0111161~${Biblioteca.dispositivo}")!!.entero("total"))
        assertEquals(120_000L, otro.coleccion("progreso").nodo("tt0111161")!!.entero("pos"))
        assertEquals("un rato suelto no la pasa a «Viendo»", "pendiente", otro.coleccion("titulos").nodo("tt0111161")!!.texto("estado"))
        // En la tablet la sigues hasta el minuto 40: aquí se sigue desde ahí
        otro.coleccion("progreso").pon("tt0111161", mapOf("ref" to "tt0111161", "v" to "tt0111161", "pos" to 2_400_000L, "dur" to dur, "ul" to System.currentTimeMillis() + 5_000))
        otro.coleccion("tiempo").pon("tt0111161~tablet", mapOf("ref" to "tt0111161", "fuente" to "tablet", "total" to 2_280_000L, "d${Biblioteca.hoy()}" to 2_280_000L))
        sincroniza()
        espera("llega la posición de la tablet") { Biblioteca.progreso("tt0111161")?.pos == 2_400_000L }
        assertEquals(2_400_000L, Reanudar.desde("tt0111161"))
        assertEquals("lo visto suma tus dispositivos", 2_400_000L, Biblioteca.msVistos("tt0111161"))
        // Al 89 % no está vista; al 90 %, sí
        v = visionado(); pos = ve(v, 60_000L, (dur * 0.88).toLong()); v.termina(pos, dur)
        assertFalse(Biblioteca.progreso("tt0111161")!!.visto)
        v = visionado(); pos = ve(v, 90_000L, (dur * 0.89).toLong()); v.termina(pos, dur)
        sincroniza()
        assertTrue(otro.coleccion("progreso").nodo("tt0111161")!!.si("visto"))
        assertEquals("terminado", otro.coleccion("titulos").nodo("tt0111161")!!.texto("estado"))

        // Una serie: vista cuando lo están todos sus episodios estrenados (sin los especiales ni los que aún no han salido)
        val ahora = System.currentTimeMillis()
        val eps = listOf(Video("tt0944947:1:1", "Se acerca el invierno", 1, 1, ahora - 400 * 86_400_000L), Video("tt0944947:1:2", "El camino real", 1, 2, ahora - 390 * 86_400_000L),
            Video("tt0944947:1:3", "Lord Nieve", 1, 3, ahora - 380 * 86_400_000L), Video("tt0944947:0:1", "Especial", 0, 1, ahora - 500 * 86_400_000L),
            Video("tt0944947:2:1", "Aún no", 2, 1, ahora + 30 * 86_400_000L))
        val serie = MetaBreve("tt0944947", "series", "Juego de tronos")
        val ms = Meta("tt0944947", "series", "Juego de tronos", duracion = "55 min", videos = eps, imdb = "tt0944947")
        val dEp = 55 * 60_000L
        Biblioteca.anade(serie, ms)
        v = visionado(serie, ms, eps[0]); pos = ve(v, 12 * 60_000L, dEp - 12 * 60_000L, dEp); v.termina(pos, dEp)
        assertEquals("tras diez minutos, en «Viendo»", Estado.VIENDO, Biblioteca.por(serie.id)!!.estado)
        v = visionado(serie, ms, eps[1]); pos = ve(v, 3 * 60_000L, dEp - 3 * 60_000L, dEp); v.termina(pos, dEp)
        assertEquals(Estado.VIENDO, Biblioteca.por(serie.id)!!.estado)
        assertEquals("el siguiente que toca", eps[2], Biblioteca.siguiente(Biblioteca.por(serie.id)!!, ms))
        Biblioteca.marcaVisto(Biblioteca.por(serie.id)!!, eps[2], true, ms)
        assertEquals("todos los estrenados vistos", Estado.VISTO, Biblioteca.por(serie.id)!!.estado)
        Biblioteca.marcaVisto(Biblioteca.por(serie.id)!!, eps[1], false, ms)
        assertEquals("y si no, «Viendo»", Estado.VIENDO, Biblioteca.por(serie.id)!!.estado)
        sincroniza()
        val p1 = otro.coleccion("progreso").nodo("tt0944947:1:1")!!
        assertEquals("tt0944947", p1.texto("ref")); assertEquals(1L, p1.entero("temp")); assertEquals(1L, p1.entero("ep")); assertTrue(p1.si("visto"))
        assertFalse(otro.coleccion("progreso").nodo("tt0944947:1:2")!!.si("visto"))
        assertEquals("en_curso", otro.coleccion("titulos").nodo("tt0944947")!!.texto("estado"))
        // El tiempo de la serie va al título (no a cada episodio), por día y dispositivo
        assertEquals(15 * 60_000L, otro.coleccion("tiempo").nodo("tt0944947~${Biblioteca.dispositivo}")!!.entero("total"))
    }

    @Test fun regla3BorrarEsParaSiempre() {
        Biblioteca.anade(peli, metaPeli)
        var v = visionado(); var pos = ve(v, 12 * 60_000L, 0L); v.termina(pos, dur)
        // Azabache la valora (en el mismo nodo)
        otro.coleccion("titulos").pon("tt0111161", mapOf("nota" to 90L, "resena" to "Esperanza."))
        sincroniza()
        val antes = otro.coleccion("titulos").nodo("tt0111161")!!
        assertEquals("en_curso", antes.texto("estado"))
        Biblioteca.quita(Biblioteca.por(peli.id)!!.ref)
        sincroniza()
        assertTrue("queda borrada en la cuenta", otro.coleccion("titulos").nodo("tt0111161")!!.borrado)
        assertTrue(Biblioteca.loBorraste(peli.id))
        val totalAntes = otro.coleccion("tiempo").nodo("tt0111161~${Biblioteca.dispositivo}")!!.entero("total")
        val posAntes = otro.coleccion("progreso").nodo("tt0111161")!!.entero("pos")
        // La vuelves a ver, y mucho: no vuelve sola, y no se escribe nada
        v = visionado(); pos = ve(v, 25 * 60_000L, 30 * 60_000L); v.termina(pos, dur)
        assertFalse(v.entro); assertFalse(v.enCuenta)
        assertEquals(0, LaFamilia.familia.pendientes)
        sincroniza()
        assertTrue(otro.coleccion("titulos").nodo("tt0111161")!!.borrado)
        assertNull(Biblioteca.por(peli.id))
        assertEquals("ni el tiempo", totalAntes, otro.coleccion("tiempo").nodo("tt0111161~${Biblioteca.dispositivo}")!!.entero("total"))
        assertEquals("ni la posición", posAntes, otro.coleccion("progreso").nodo("tt0111161")!!.entero("pos"))
        // «Añadir» la recupera: la misma, con lo suyo (su estado, su nota, lo visto)
        val t = Biblioteca.anade(peli, metaPeli)!!
        assertEquals("tt0111161", t.ref); assertEquals(Estado.VIENDO, t.estado)
        sincroniza()
        val n = otro.coleccion("titulos").nodo("tt0111161")!!
        assertFalse(n.borrado); assertEquals(90L, n.entero("nota")); assertEquals("en_curso", n.texto("estado")); assertEquals(antes.entero("an"), n.entero("an"))
        assertEquals(1, otro.coleccion("titulos").vivos().size)

        // Lo que vino de Azabache con otra ref (tmdb:550) y su código de IMDb: borrado, tampoco vuelve al verlo por su código
        otro.coleccion("titulos").pon("tmdb:550", mapOf("id" to "tmdb:550", "tipo" to "movie", "t" to "El club de la lucha", "estado" to "terminado", "tmdb" to 550L, "imdb" to "tt0137523", "azid" to 550L))
        sincroniza(); espera("llega") { Biblioteca.por("tt0137523") != null }
        Biblioteca.quita("tmdb:550"); sincroniza()
        val club = MetaBreve("tt0137523", "movie", "El club de la lucha")
        v = visionado(club, null); pos = ve(v, 15 * 60_000L, 0L, 139 * 60_000L); v.termina(pos, 139 * 60_000L)
        sincroniza()
        assertNull("no nace otra con su código", otro.coleccion("titulos").nodo("tt0137523"))
        assertTrue(otro.coleccion("titulos").nodo("tmdb:550")!!.borrado)
        assertEquals("tmdb:550", Biblioteca.anade(club)!!.ref)
        sincroniza()
        assertEquals(0L, otro.coleccion("titulos").nodo("tmdb:550")!!.entero(Nodo.B))
    }
}
