package com.gonzalo.mostaza.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tus películas y series del kDrive: lo que se entiende de los nombres (como dicen los LEEME.txt de «Mi carpeta/Mostaza»). */
class CineKDriveTest {
    private fun p(n: String) = NombresCine.pelicula(n)

    @Test fun losNombresDePeliculas() {
        assertEquals(NombresCine.Pelicula("Dune Parte dos", "2024", ""), p("Dune Parte dos (2024).mkv"))
        assertEquals(NombresCine.Pelicula("Dune Part Two", "2024", ""), p("Dune.Part.Two.2024.1080p.WEB-DL.Castellano.mkv"))
        assertEquals(NombresCine.Pelicula("La sociedad de la nieve", "2023", ""), p("La sociedad de la nieve [2023] [1080p] [Castellano].mp4"))
        assertEquals(NombresCine.Pelicula("Oppenheimer", "", ""), p("Oppenheimer 1080p x265.mkv"))
        assertEquals("con su número de IMDb", NombresCine.Pelicula("Dune Parte dos", "2024", "tt15239678"), p("Dune Parte dos (2024) tt15239678.mkv"))
        assertEquals("un año que es el título no se quita", "1917", p("1917 (2019).mkv").titulo)
        assertEquals("2019", p("1917 (2019).mkv").ano)
        assertEquals("Blade Runner 2049", p("Blade Runner 2049 (2017).mkv").titulo)
        assertEquals("una carpeta (sin extensión)", NombresCine.Pelicula("Cadena perpetua", "1994", ""), p("Cadena perpetua (1994)"))
    }

    @Test fun losEpisodios() {
        val e = NombresCine.episodio(listOf("Breaking Bad (2008)"), "S01E02.mkv")!!
        assertEquals(NombresCine.Pelicula("Breaking Bad", "2008", ""), e.serie); assertEquals(1, e.temporada); assertEquals(2, e.episodio)
        val e2 = NombresCine.episodio(listOf("Breaking Bad (2008)", "Temporada 1"), "Breaking.Bad.S01E03.1080p.mkv")!!
        assertEquals(1 to 3, e2.temporada to e2.episodio)
        assertEquals(2 to 10, NombresCine.episodio(listOf("The Bear"), "the.bear.2x10.mkv")!!.let { it.temporada to it.episodio })
        assertEquals(3 to 4, NombresCine.episodio(listOf("Shogun", "Temporada 3"), "04 - El final.mkv")!!.let { it.temporada to it.episodio })
        assertEquals("tt0903747", NombresCine.episodio(listOf("Breaking Bad (2008) tt0903747"), "S01E01.mkv")!!.serie.imdb)
        assertNull("sin temporada ni episodio", NombresCine.episodio(listOf("Breaking Bad"), "extras.mkv"))
        assertNull("suelto en «Series», sin carpeta de serie", NombresCine.episodio(emptyList(), "S01E01.mkv"))
    }

    @Test fun queArchivosSon() {
        assertTrue(NombresCine.esVideo("a.MKV")); assertTrue(NombresCine.esVideo("a.mp4")); assertFalse(NombresCine.esVideo("LEEME.txt"))
        assertFalse(NombresCine.esVideo(".a.mkv")); assertTrue(NombresCine.esSub("a.es.srt"))
    }

    @Test fun eligeElResultadoBueno() {
        val dune = NombresCine.Pelicula("Dune Parte dos", "2024", "")
        val cands = listOf(Triple("1", "Dune", "1984"), Triple("2", "Dune: Parte dos", "2024"), Triple("3", "Dune: Parte dos", "2025"))
        assertEquals("2", NombresCine.elige(dune, cands))
        assertEquals("sin año: el mismo título", "2", NombresCine.elige(dune.copy(ano = ""), cands.drop(0)))
        assertEquals("con el año a uno de distancia (estreno en otro país)", "x", NombresCine.elige(dune, listOf(Triple("x", "Dune: Part Two", "2023"))))
        assertNull(NombresCine.elige(dune, emptyList()))
        assertEquals("dune parte dos", NombresCine.normal("Dune: Parte dos"))
        assertEquals("pelicula", NombresCine.normal("Película"))
    }

    @Test fun loDeTuKDriveVaPrimero() {
        val tuya = Candidata(Fuente(nombre = "Tu kDrive\nDune 720p.mkv", url = "https://api.infomaniak.com/2/drive/1/files/5/download", archivo = "Dune 720p.mkv", privada = true), CineKDrive.ADDON, -1, 0)
        val addon = Candidata(Fuente(nombre = "Dune 2160p Castellano", url = "https://d/1"), "Torrentio", 0, 0)
        val e = Elector.elige(listOf(addon, tuya), PrefsElector(), Red(50_000L), Capacidades(hevc = true, av1 = true, hdr = true, dv = true), 166, true)
        assertEquals(tuya, e.elegida)
    }

    @Test fun lasCabecerasNoVanAOtrosServidores() {
        val f = Fuente(url = "https://api.infomaniak.com/2/drive/1/files/5/download", cabeceras = mapOf("Authorization" to "Bearer x"), privada = true)
        assertTrue("el .srt de al lado, en tu kDrive", Subs.conCabeceras("https://api.infomaniak.com/2/drive/1/files/6/download", f))
        assertFalse("los de OpenSubtitles, no", Subs.conCabeceras("https://dl.opensubtitles.org/x.srt", f))
        assertFalse(Subs.conCabeceras("https://api.infomaniak.com/x", f.copy(cabeceras = emptyMap())))
        assertFalse(Subs.conCabeceras("https://api.infomaniak.com/x", null))
    }
}
