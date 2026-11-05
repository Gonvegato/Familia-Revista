package com.gonzalo.gamerate

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Lo que Azabache lee de sus hermanas (Hiedra, Marfil, Cobalto) y cómo lo refleja en sus listas. Sin móvil ni red. */
class HermanasTest {
    private fun juegoH(id: String, t: String, es: String = "PENDIENTE", mod: Long = 100, msp: Long = 0, fi: Long = 0, pl: String = "", k: List<String> = emptyList(), ex: Map<String, Long> = emptyMap(), dh: Int = 0) =
        JSONObject().put("id", id).put("t", t).put("es", es).put("mod", mod).put("msp", msp).put("fi", fi).put("pl", pl).put("an", 1_700_000_000_000L).put("ul", 1_700_000_000_000L)
            .put("k", JSONArray(k)).put("ln", JSONArray().put(JSONObject().put("tp", "ROM").put("s", "gba").put("a", t))).put("dh", dh).put("pu", "https://x/$id.jpg")
            .put("ex", JSONObject().also { o -> ex.forEach { (f, ms) -> o.put(f, JSONObject().put("ms", ms)) } })
    private fun blobH(vararg js: JSONObject, borrados: Map<String, Long> = emptyMap()) = JSONObject().put("v", 1).put("juegos", JSONArray().also { a -> js.forEach { a.put(it) } })
        .put("borrados", JSONObject().also { o -> borrados.forEach { (k, v) -> o.put(k, v) } })

    @Test fun losJuegosDeHiedraSeMezclanPorIdYLasHorasSeJuntan() {
        val a = blobH(juegoH("j1", "Zelda", "JUGANDO", mod = 100, msp = 3_000, ex = mapOf("steam" to 10_000L)), juegoH("j2", "Metroid", "PASADO", fi = 1_700_000_000_000L))
        val b = blobH(juegoH("j1", "Zelda DX", "PASADO", mod = 200, msp = 7_000, ex = mapOf("steam" to 12_000L, "pc:pc1" to 5_000L)), juegoH("j3", "Borrado"), borrados = mapOf("j3" to 999L))
        val js = Hermanas.juegosDe(mapOf("A" to a, "B" to b))
        assertEquals(listOf("Metroid", "Zelda DX"), js.map { it.titulo })
        val z = js.first { it.id == "j1" }
        assertEquals("gana el cambio más reciente", "PASADO", z.estado)
        assertEquals("las horas de cada móvil se suman, las externas por máximo", 3_000L + 7_000L + 12_000L + 5_000L, z.ms)
        assertTrue("la clave del lanzador se conoce", "rom:gba:zeldadx" in z.claves)
    }

    @Test fun unJuegoDeHiedraEsUnaFichaDeAzabacheYLaNotaEsDeAqui() {
        val enl = HashMap<String, Long>()
        val juegos = Hermanas.juegosDe(mapOf("A" to blobH(juegoH("j1", "Zelda", "JUGANDO", msp = 90 * 60_000L, pl = "Switch", dh = 600), juegoH("j2", "Hades", "PASADO", fi = 1_700_000_000_000L), juegoH("j3", "Celeste", "ABANDONADO"))))
        // Lo que ya tenías: Hades valorado (se enlaza por el nombre y se conserva la nota)
        val hades = Game(555L, "Hades", "https://mia.jpg", 93, 90, 95, 99, 92, "Brutal", end = 19_000L)
        val e = Hermanas.reflejarJuegos(juegos, listOf(hades), emptyList(), enl, ahora = 1_000)
        assertTrue(e.cambio)
        assertEquals("Zelda está jugando: en curso", 1, e.plays.first { it.name == "Zelda" }.status)
        val z = e.plays.first { it.name == "Zelda" }
        assertEquals(90, z.minutes); assertEquals("Switch", z.platform); assertEquals(600, z.ttbMain); assertEquals("https://x/j1.jpg", z.cover); assertEquals(customItemId("Zelda"), z.id)
        assertEquals(2, e.plays.first { it.name == "Celeste" }.status)
        val h = e.games.single()
        assertEquals("Hades sigue siendo el tuyo, con tu nota", 555L, h.id); assertEquals(93, h.general); assertEquals("Brutal", h.review); assertEquals("https://mia.jpg", h.cover)
        assertEquals("y la fecha de pasado la manda Hiedra", LocalDate.of(2023, 11, 14).toEpochDay(), h.end)
        assertEquals(555L, enl["j2"]); assertEquals(customItemId("Zelda"), enl["j1"])
        // Repetir no cambia nada
        assertFalse(Hermanas.reflejarJuegos(juegos, e.games, e.plays, enl, ahora = 2_000).cambio)
        // Zelda se pasa en Hiedra: pasa a valorados, sin nota (por valorar)
        val j2 = Hermanas.juegosDe(mapOf("A" to blobH(juegoH("j1", "Zelda", "PASADO", mod = 300, fi = 1_700_100_000_000L))))
        val e2 = Hermanas.reflejarJuegos(j2, e.games, e.plays, enl, ahora = 3_000)
        val zg = e2.games.first { it.name == "Zelda" }
        assertTrue(Hermanas.sinValorar(zg)); assertFalse(Hermanas.sinValorar(hades)); assertTrue(e2.plays.none { it.name == "Zelda" })
        assertEquals("la ficha conserva su id", customItemId("Zelda"), zg.id)
        // Si la valoras y Hiedra dice que la rejuegas, la nota manda: no vuelve a en curso
        val valorada = e2.games.map { if (it.id == zg.id) it.copy(general = 80, gfx = 80, story = 80, music = 80, gameplay = 80) else it }
        val e3 = Hermanas.reflejarJuegos(Hermanas.juegosDe(mapOf("A" to blobH(juegoH("j1", "Zelda", "JUGANDO", mod = 400)))), valorada, e2.plays, enl, ahora = 4_000)
        assertEquals(80, e3.games.first { it.name == "Zelda" }.general); assertTrue(e3.plays.none { it.name == "Zelda" })
    }

    @Test fun laPortadaDeLaHermanaNoPisaLaQueCambiasTu() {
        val enl = HashMap<String, Long>(); val puestas = HashMap<String, String>()
        val js = Hermanas.juegosDe(mapOf("A" to blobH(juegoH("j1", "Zelda", "JUGANDO"))))
        val e = Hermanas.reflejarJuegos(js, emptyList(), emptyList(), enl, puestas = puestas, ahora = 1)
        assertEquals("https://x/j1.jpg", e.plays[0].cover); assertEquals("https://x/j1.jpg", puestas["j1"])
        // Tú la cambias: la siguiente vez no se toca
        val mia = e.plays.map { it.copy(cover = "file:///foto.jpg") }
        val e2 = Hermanas.reflejarJuegos(js, emptyList(), mia, enl, puestas = puestas, ahora = 2)
        assertEquals("file:///foto.jpg", e2.plays[0].cover)
        // Pero si Hiedra cambia la suya y tú no habías tocado nada, se actualiza
        val e3 = Hermanas.reflejarJuegos(js, emptyList(), e.plays, enl, { "https://x/nueva.jpg" }, puestas, ahora = 3)
        assertEquals("https://x/nueva.jpg", e3.plays[0].cover)
    }

    @Test fun sinPortadaTuyaHiedraMandaLaCajaAutomatica() {
        // Hiedra 0.3.0: «pu» es la que elegiste de internet; «pa», la caja de libretro cuando no hay ninguna tuya
        val sinPu = juegoH("j2", "Metroid").put("pu", "").put("pa", "https://thumbnails.libretro.com/x/Metroid.png")
        val js = Hermanas.juegosDe(mapOf("A" to blobH(sinPu, juegoH("j3", "Hades").put("pa", "https://thumbnails.libretro.com/x/Hades.png"))))
        assertEquals("https://thumbnails.libretro.com/x/Metroid.png", js.first { it.id == "j2" }.portadaUrl)
        assertEquals("la tuya manda sobre la automática", "https://x/j3.jpg", js.first { it.id == "j3" }.portadaUrl)
        assertEquals("", Hermanas.juegosDe(mapOf("A" to blobH(juegoH("j4", "Nada").put("pu", "")))).single().portadaUrl)
    }

    private fun libroM(k: String, t: String, a: String, e: String = "PENDIENTE", mod: Long = 100, msp: Long = 0, tm: List<Long> = emptyList(), pg: Int = 0, pu: String = "") =
        JSONObject().put("k", k).put("t", t).put("a", a).put("e", e).put("mod", mod).put("msp", msp).put("an", 1_700_000_000_000L).put("ul", 1_700_000_000_000L).put("pg", pg).put("fr", 0.5).put("tm", JSONArray(tm)).put("pu", pu)
    private fun blobM(vararg ls: JSONObject) = JSONObject().put("v", 1).put("libros", JSONArray().also { x -> ls.forEach { x.put(it) } }).put("borrados", JSONObject())

    @Test fun elAvanceDeCadaLibroVaPorFicha() {
        // Dune va por la mitad (fr 0,5) y tiene ficha 7; Nada no tiene ficha: no sale
        val libros = Hermanas.librosDe(mapOf("A" to blobM(libroM("libro:dune:herbert", "Dune", "Frank Herbert", "LEYENDO"), libroM("libro:nada:laforet", "Nada", "Carmen Laforet"))))
        val av = Hermanas.avancePorFicha(libros, mapOf("libro:dune:herbert" to 7L))
        assertEquals(1, av.length()); assertEquals(0.5, av.optDouble("7"), 0.001)
    }

    @Test fun losLibrosDeMarfilSonFichasDeLibros() {
        val enl = HashMap<String, Long>()
        val libros = Hermanas.librosDe(mapOf("A" to blobM(libroM("libro:dune:herbert", "Dune", "Frank Herbert", "LEYENDO", msp = 120 * 60_000L, pg = 700), libroM("libro:nada:laforet", "Nada", "Carmen Laforet", "LEIDO", tm = listOf(1_700_000_000_000L)))))
        assertEquals(2, libros.size); assertEquals(20, libros[0].kid.length)
        val e = Hermanas.reflejarLibros(libros, emptyList(), emptyList(), enl, ahora = 1)
        val d = e.plays.single(); assertEquals("Dune", d.name); assertEquals("Frank Herbert", d.creator); assertEquals(1, d.status); assertEquals(700, d.pages); assertEquals(120, d.minutes)
        val n = e.games.single(); assertEquals("Nada", n.name); assertTrue(Hermanas.sinValorar(n)); assertEquals(LocalDate.of(2023, 11, 14).toEpochDay(), n.end)
        assertEquals(customItemId("Dune"), enl["libro:dune:herbert"])
        assertFalse(Hermanas.reflejarLibros(libros, e.games, e.plays, enl, ahora = 2).cambio)
    }

    @Test fun laPortadaDeInternetDeMarfilViajaConElLibro() {
        // Marfil 0.12.0: «pu» es la dirección de la portada elegida de internet; vacío si es de la galería (esa va por kDrive)
        val libros = Hermanas.librosDe(mapOf("A" to blobM(libroM("libro:dune:herbert", "Dune", "Frank Herbert", pu = "https://covers.openlibrary.org/b/id/1-L.jpg"), libroM("libro:nada:laforet", "Nada", "Carmen Laforet"))))
        assertEquals("https://covers.openlibrary.org/b/id/1-L.jpg", libros.first { it.titulo == "Dune" }.portadaUrl); assertEquals("", libros.first { it.titulo == "Nada" }.portadaUrl)
        val e = Hermanas.reflejarLibros(libros, emptyList(), emptyList(), HashMap(), portada = { it.portadaUrl }, ahora = 1)
        assertEquals("https://covers.openlibrary.org/b/id/1-L.jpg", e.plays.first { it.name == "Dune" }.cover); assertEquals("", e.plays.first { it.name == "Nada" }.cover)
    }

    @Test fun elTiempoDeCadaDiaSaleDeLosNodosLigeros() {
        val enl = mapOf("j1" to 11L)
        val tiempo = JSONObject().put("j1", JSONObject()
            .put("hiedra-A", JSONObject().put("t", "Zelda").put("days", JSONObject().put("2026-01-01", 600_000L).put("2026-01-02", 60_000L)))
            .put("pc:pc1", JSONObject().put("t", "Zelda").put("days", JSONObject().put("2026-01-01", 300_000L))))
            .put("j9", JSONObject().put("steam", JSONObject().put("t", "Hades").put("days", JSONObject().put("2026-01-01", 120_000L))))
        val d = Hermanas.diasJuegos(tiempo, enl)
        val dia1 = LocalDate.of(2026, 1, 1).toEpochDay()
        assertEquals("las fuentes se suman", 900_000L, d[dia1]!![JuegoPc(11L, "Zelda")])
        assertEquals("un juego sin ficha aquí sale por su nombre", 120_000L, d[dia1]!![JuegoPc(0L, "Hades")])
        assertEquals(60_000L, d[dia1 + 1]!![JuegoPc(11L, "Zelda")])
        // Marfil: por dispositivo y día el máximo entre escritores, y luego se suman los dispositivos
        val kid = Hermanas.kidDe("libro:dune:herbert")
        val tm = JSONObject().put(kid, JSONObject()
            .put("movilA", JSONObject().put("dev", JSONObject().put("A", JSONObject().put("days", JSONObject().put("2026-01-01", 1_000L))).put("B", JSONObject().put("days", JSONObject().put("2026-01-01", 500L)))))
            .put("movilB", JSONObject().put("dev", JSONObject().put("A", JSONObject().put("days", JSONObject().put("2026-01-01", 800L))).put("B", JSONObject().put("days", JSONObject().put("2026-01-01", 700L))))))
        val l = Hermanas.diasLibros(tm, mapOf(kid to 77L))
        assertEquals(1_000L + 700L, l[dia1]!![77L])
        assertTrue("sin ficha aquí, nada", Hermanas.diasLibros(tm, emptyMap()).isEmpty())
        // Cobalto: las palabras de cada dispositivo se suman
        val p = Hermanas.palabrasPorDia(JSONObject().put("c1", JSONObject().put("2026-01-01", 500)).put("c2", JSONObject().put("2026-01-01", 250).put("2026-01-03", 100)))
        assertEquals(750, p[dia1]); assertEquals(100, p[dia1 + 2])
        // Y van al Diario como un día más
        val f = FuentesDiario(listOf(Triple(GAMES, emptyList(), emptyList())), palabras = p)
        val dias = diasDelDiario(f)
        assertEquals(listOf(dia1 + 2, dia1), dias.map { it.dia }); assertEquals(750, dias[1].palabras)
        assertTrue(resumenDiario(dias, dia1, dia1 + 2).texto().contains("850 palabras"))
    }

    @Test fun losCapitulosDeCobaltoSeLeenPorObraYEnOrden() {
        val l = JSONObject().put("p_1", JSONObject()
            .put("c_2", JSONObject().put("t", "La casa").put("a", "Gon").put("c", "El regreso").put("n", 2).put("pa", "Parte I").put("p", 1200).put("x", "# El regreso\n\nVolvió.").put("u", 5L))
            .put("c_1", JSONObject().put("t", "La casa").put("a", "Gon").put("c", "La llegada").put("n", 1).put("p", 900).put("x", "Llegó.").put("u", 3L)))
        val caps = Hermanas.capitulosDe(l)
        assertEquals(listOf("La llegada", "El regreso"), caps.map { it.nombre }); assertEquals("Parte I", caps[1].parte); assertEquals(1200, caps[1].palabras)
        assertTrue(Hermanas.capitulosDe(null).isEmpty())
    }

    @Test fun elEnlaceVaYVuelve() {
        val enl = HashMap<String, Long>()
        val js = Hermanas.juegosDe(mapOf("A" to blobH(juegoH("j1", "Zelda", "JUGANDO"))))
        Hermanas.reflejarJuegos(js, emptyList(), emptyList(), enl, ahora = 1)
        // Del id de Hiedra a la ficha de aquí, y de la ficha al id de Hiedra («Jugar» abre esa ficha en Hiedra)
        assertEquals(customItemId("Zelda"), enl["j1"])
        assertEquals("j1", enl.entries.firstOrNull { it.value == customItemId("Zelda") }?.key)
    }

    @Test fun losSubrayadosDeMarfilSeUnenPorSitioYHora() {
        val kid = Hermanas.kidDe("libro:dune:herbert")
        fun n(x: String, c: Long, k: String = "epubcfi(/6/4!/4/2)", o: String = "", l: String = "Cap. 1") = JSONObject().put("x", x).put("o", o).put("c", c).put("l", l).put("k", k)
        val sub = JSONObject().put(kid, JSONObject()
            .put("movilA", JSONObject().put("t", "Dune").put("a", "Frank Herbert").put("n", JSONArray().put(n("El miedo mata la mente.", 100L, o = "Letanía")).put(n("Quien controla la especia", 200L, k = "epubcfi(/6/8!/4/2)"))))
            .put("movilB", JSONObject().put("t", "Dune").put("a", "Frank Herbert").put("n", JSONArray().put(n("El miedo mata la mente.", 100L)).put(n("  ", 300L)))))
            .put("otro", JSONObject().put("movilA", JSONObject().put("n", JSONArray().put(n("De un libro sin ficha aquí", 50L)))))
        val m = Hermanas.subrayadosDe(sub, mapOf(kid to 77L))
        assertEquals(setOf(77L), m.keys)
        val ns = m[77L]!!
        assertEquals("el repetido entre escritores sale una vez; el vacío, no", 2, ns.size)
        assertEquals("El miedo mata la mente.", ns[0].text); assertEquals("Letanía", ns[0].note); assertEquals("Cap. 1", ns[0].label); assertEquals(100L, ns[0].created); assertEquals("epubcfi(/6/4!/4/2)", ns[0].value)
        assertEquals("Quien controla la especia", ns[1].text)
        assertTrue(Hermanas.subrayadosDe(null, mapOf(kid to 77L)).isEmpty())
        // Y van al Diario el día que se subrayaron
        val f = FuentesDiario(listOf(Triple(BOOKS, emptyList(), listOf(Play(77L, "Dune", "", "", 1, emptyList(), creator = "Frank Herbert")))), citas = m)
        val dias = diasDelDiario(f)
        assertEquals(1, dias.size); assertEquals(2, dias[0].toques.sumOf { it.citas.size })
    }
}
