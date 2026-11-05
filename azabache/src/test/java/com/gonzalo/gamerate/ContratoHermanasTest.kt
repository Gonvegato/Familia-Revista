package com.gonzalo.gamerate

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * El contrato con las hermanas, con lo que ellas escriben DE VERDAD: los archivos de `herramientas/casos/hermanas/` los genera la prueba `ContratoTest`
 * de cada una (Hiedra, Marfil, Cobalto) con su propio código de subida, tal cual queda en la cuenta. Aquí se leen con el mismo código con el que
 * Azabache lee la cuenta. Si una hermana cambia lo que sube, se regeneran y esta prueba dice si Azabache lo sigue entendiendo.
 */
class ContratoHermanasTest {
    private fun raiz(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "herramientas/casos/hermanas/hiedra.json").exists()) dir = dir.parentFile
        return dir ?: error("no encuentro herramientas/casos/hermanas")
    }
    private fun caso(app: String) = JSONObject(File(raiz(), "herramientas/casos/hermanas/$app.json").readText())
    /** Como Hermanas.blobs: `dev/<dispositivo>/data` es un texto JSON (en la cuenta, Firebase lo devuelve entrecomillado; aquí ya es el texto). */
    private fun blobs(c: JSONObject): Map<String, JSONObject> {
        val dev = c.getJSONObject("dev"); val out = LinkedHashMap<String, JSONObject>()
        for (d in dev.keys()) out[d] = JSONObject(dev.getJSONObject(d).getString("data"))
        return out
    }
    private val dia1 = LocalDate.of(2026, 1, 1).toEpochDay()

    @Test fun loQueSubeHiedraSeLeeEntero() {
        val c = caso("hiedra")
        val juegos = Hermanas.juegosDe(blobs(c))
        assertEquals(listOf("j2", "j1"), juegos.map { it.id })   // por título: Metroid, The Legend of Zelda
        val z = juegos.first { it.id == "j1" }
        assertEquals("The Legend of Zelda", z.titulo); assertEquals("GBA", z.plataforma); assertEquals("PASADO", z.estado); assertTrue(z.favorito)
        assertEquals(1_700_000_000_000L, z.finalizado); assertEquals("https://x/zelda.jpg", z.portadaUrl)
        assertEquals("las horas propias y las de Steam", 900_000L + 120_000L, z.ms); assertEquals(1_200, z.dh); assertEquals(3_000, z.dc)
        // Las claves (rom:<sistema>:<archivo normalizado>) son las mismas que calcula Hiedra: por ellas se enlaza la ficha
        val kz = c.getJSONObject("claves").getJSONArray("j1"); assertEquals((0 until kz.length()).map { kz.getString(it) }, z.claves)
        val m = juegos.first { it.id == "j2" }
        assertEquals("sin portada tuya, la caja de libretro", true, m.portadaUrl.startsWith("https://thumbnails.libretro.com/"))
        // El reflejo en las listas de Azabache
        val enl = HashMap<String, Long>()
        val e = Hermanas.reflejarJuegos(juegos, emptyList(), emptyList(), enl, ahora = 1)
        assertEquals(1, e.games.size); assertEquals(1, e.plays.size)
        assertEquals("The Legend of Zelda", e.games[0].name); assertTrue(Hermanas.sinValorar(e.games[0])); assertEquals("https://x/zelda.jpg", e.games[0].cover)
        assertEquals("Metroid Fusion", e.plays[0].name); assertEquals(1, e.plays[0].status)
        // El tiempo por día
        val d = Hermanas.diasJuegos(c.getJSONObject("tiempo"), enl)
        assertEquals("propias + Steam el día 1", 600_000L + 120_000L, d[dia1]!![JuegoPc(enl["j1"]!!, "The Legend of Zelda")])
        assertEquals(60_000L, d[dia1 + 1]!![JuegoPc(enl["j1"]!!, "The Legend of Zelda")])
        assertEquals(300_000L, d[dia1 + 2]!![JuegoPc(enl["j2"]!!, "Metroid Fusion")])
    }

    @Test fun loQueSubeMarfilSeLeeEntero() {
        val c = caso("marfil")
        val libros = Hermanas.librosDe(blobs(c))
        assertEquals(2, libros.size)
        val dune = libros.first { it.titulo == "Dune" }
        assertEquals(c.getString("claveDune"), dune.clave); assertEquals("la misma clave → el mismo kid", c.getString("kidDune"), dune.kid)
        assertEquals("Frank Herbert", dune.autor); assertEquals("LEYENDO", dune.estado); assertTrue(dune.favorito); assertEquals(608, dune.paginas)
        assertEquals(0.8f, dune.fraccion, 0.001f); assertEquals(7_200_000L, dune.ms); assertEquals("https://covers.openlibrary.org/b/id/1-L.jpg", dune.portadaUrl)
        val nada = libros.first { it.titulo == "Nada" }
        assertEquals("LEIDO", nada.estado); assertEquals(listOf(1_700_000_000_000L), nada.terminados); assertEquals("", nada.portadaUrl)
        val enl = HashMap<String, Long>()
        val e = Hermanas.reflejarLibros(libros, emptyList(), emptyList(), enl, portada = { it.portadaUrl }, ahora = 1)
        assertEquals("Dune", e.plays.single().name); assertEquals(608, e.plays.single().pages); assertEquals(120, e.plays.single().minutes)
        assertEquals("https://covers.openlibrary.org/b/id/1-L.jpg", e.plays.single().cover)
        assertEquals("Nada", e.games.single().name); assertEquals(LocalDate.of(2023, 11, 14).toEpochDay(), e.games.single().end)
        val porKid = enl.entries.associate { (k, v) -> Hermanas.kidDe(k) to v }
        val d = Hermanas.diasLibros(c.getJSONObject("tiempo"), porKid)
        assertEquals(1_000L, d[dia1]!![enl[dune.clave]]); assertEquals(3_600_000L, d[dia1 + 1]!![enl[dune.clave]])
        val s = Hermanas.subrayadosDe(c.getJSONObject("subrayados"), porKid)[enl[dune.clave]]!!
        assertEquals(2, s.size); assertEquals("El miedo mata la mente.", s[0].text); assertEquals("Letanía", s[0].note); assertEquals("Capítulo 1", s[0].label); assertEquals("epubcfi(/6/4!/4/2)", s[0].value)
        // Los objetivos de lectura los fija Marfil: Azabache los lee tal cual
        val o = Hermanas.objetivosDe(c.getJSONObject("objetivos"))!!
        assertEquals(30, o.dia); assertEquals(24, o.ano)
        assertNull(Hermanas.objetivosDe(null)); assertNull(Hermanas.objetivosDe(org.json.JSONObject()))
    }

    @Test fun loQuePublicaCobaltoSeLeeEntero() {
        val c = caso("cobalto"); val cuenta = c.getJSONObject("cuenta")
        val caps = Hermanas.capitulosDe(cuenta.getJSONObject("lectura"))
        assertEquals(1, caps.size)
        val cap = caps[0]
        assertEquals(c.getString("obra"), cap.obra); assertEquals(c.getString("capitulo"), cap.id)
        assertEquals("La casa", cap.titulo); assertEquals("Gonzalo", cap.autor); assertEquals("El regreso", cap.nombre); assertEquals(1, cap.numero); assertEquals("Primera parte", cap.parte)
        assertEquals(6, cap.palabras); assertTrue(cap.texto.contains("**tarde**")); assertTrue(cap.u > 0)
        val p = Hermanas.palabrasPorDia(cuenta.getJSONObject("palabras"))
        assertEquals(500, p[dia1]); assertEquals(100, p[dia1 + 2])
        assertEquals("y sella stamps para que Azabache se entere", "timestamp", cuenta.getJSONObject("stamps").getJSONObject("cobalto-A").getString(".sv"))
    }
}
