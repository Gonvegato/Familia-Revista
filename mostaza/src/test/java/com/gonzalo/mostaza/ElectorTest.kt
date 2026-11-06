package com.gonzalo.mostaza

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El elector de fuentes: lo que se entiende del texto libre de cada fuente (con títulos como los que dan los addons de verdad) y cuál se
 * elige según tus idiomas, tu conexión, lo que sabe reproducir el dispositivo y el grupo del episodio anterior.
 */
class ElectorTest {
    private fun a(t: String, tam: Long = 0L) = Analisis.de(t, tam)

    @Test fun laCalidad() {
        assertEquals(2160, a("Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC.TrueHD.Atmos-FGT").resolucion)
        assertEquals(2160, a("[RD+] Addon\n4k HDR").resolucion)
        assertEquals(1080, a("Dune Parte Dos (2024) [1080p] [Castellano] [AC3 5.1] x264").resolucion)
        assertEquals(720, a("Dune.Part.Two.2024.720p.WEBRip.x265").resolucion)
        assertEquals(480, a("Dune 2 [DVDRip] [Español]").resolucion)
        assertEquals(0, a("Dune 2 (2024)").resolucion)
        val dv = a("Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC.TrueHD.Atmos-FGT")
        assertTrue(dv.dv); assertTrue(dv.hdr); assertEquals("hevc", dv.codec)
        assertEquals("avc", a("Movie.2024.1080p.AMZN.WEB-DL.DDP5.1.H.264-FLUX").codec)
        assertEquals("hevc", a("Serie.S01E01.1080p.x265.10bit").codec)
        assertEquals("av1", a("Movie.2024.1080p.WEB.AV1.Opus").codec)
        assertTrue(a("Dune 2 (2024) HDCAM").mala)
        assertFalse("un .ts no es telesync", a("canal/directo/video.ts").mala)
        assertFalse(a("Dune.Part.Two.2024.1080p.WEB-DL").hdr)
    }

    @Test fun losIdiomas() {
        assertEquals(setOf(Idioma.CASTELLANO), a("Dune Parte Dos (2024) [1080p] [Castellano] [AC3 5.1] x264").idiomas)
        assertEquals(setOf(Idioma.LATINO), a("Dune.Part.Two.2024.1080p.WEB-DL.LATINO.DD5.1.x264").idiomas)
        // Las banderas: España es castellano; México o Argentina, latino; Reino Unido, inglés
        assertEquals(setOf(Idioma.INGLES, Idioma.CASTELLANO, Idioma.FRANCES), a("Dune.2024.2160p\n👤 312 💾 64.2 GB\n🇬🇧 / 🇪🇸 / 🇫🇷").idiomas)
        assertEquals(setOf(Idioma.CASTELLANO, Idioma.LATINO, Idioma.MULTI), a("Dune.Part.Two.2024.MULTI.1080p.BluRay.x264 🇪🇸 🇲🇽").idiomas)
        assertEquals(setOf(Idioma.LATINO), a("Pelicula 2024 1080p 🇦🇷").idiomas)
        val dual = a("Dune.Parte.Dos.2024.BDRip.1080p.Dual.Castellano-Inglés")
        assertTrue(Idioma.CASTELLANO in dual.idiomas); assertTrue(Idioma.INGLES in dual.idiomas); assertTrue(Idioma.DUAL in dual.idiomas)
        // «Español» sin decir cuál
        assertEquals(setOf(Idioma.ESPANOL), a("Dune 2 [DVDRip] [Español]").idiomas)
        assertEquals(setOf(Idioma.ESPANOL), a("Dune.2024.1080p.SPANISH.WEB-DL").idiomas)
        assertEquals(setOf(Idioma.CASTELLANO), a("Dune 2024 1080p Spanish (Spain)").idiomas)
        assertEquals(setOf(Idioma.LATINO), a("Dune 2024 1080p Spanish Latino").idiomas)
        // Los subtítulos no son audio
        val subs = a("Dune.Part.Two.2024.720p.WEBRip.x265.Spanish.Subs")
        assertTrue(subs.subtitulosEsp); assertFalse(subs.tieneEspanol)
        val vose = a("Dune: Part Two 2024 VOSE 1080p")
        assertTrue(vose.subtitulosEsp); assertFalse(vose.tieneEspanol)
        assertTrue(a("Dune.2024.1080p.ENG.SUB.ESP").subtitulosEsp)
        assertEquals(setOf(Idioma.INGLES), a("Dune.2024.1080p.ENG.SUB.ESP").idiomas)
        assertTrue(a("Movie.2024.1080p.AMZN.WEB-DL.DDP5.1.H.264-FLUX").idiomas.isEmpty())
    }

    @Test fun tamanoSemillasYCache() {
        assertEquals(64_200_000_000L, a("👤 312 💾 64.2 GB").tamano)
        assertEquals(3_200_000_000L, a("💾 3,2 GB 👤 45").tamano)
        assertEquals(700_000_000L, a("Peli 700 MB").tamano)
        assertEquals("el del addon manda", 123L, a("💾 3,2 GB", 123L).tamano)
        assertEquals(312, a("👤 312 💾 64.2 GB").semillas)
        assertEquals(45, a("Seeders: 45").semillas)
        assertEquals(-1, a("Peli 1080p").semillas)
        assertEquals(true, a("[RD+] Addon\n1080p").enCache)
        assertEquals(true, a("⚡ Instant").enCache)
        assertEquals(false, a("[RD download] Addon\n1080p").enCache)
        assertNull(a("Addon\n1080p").enCache)
        assertEquals("1080p · Castellano · 4,4 GB", a("Dune [1080p] [Castellano]", 4_400_000_000L).resumen())
        assertEquals("4K · Dolby Vision · Inglés", a("Dune.2160p.DV.HDR 🇬🇧").resumen())
    }

    // ---------------------------------------------------------------- elegir

    private var n = 0
    private fun directa(t: String, tam: Long = 0L, addon: Int = 0, grupo: String = "") = Candidata(Fuente(nombre = t, url = "https://d/${n++}", tamano = tam, grupo = grupo), "Addon $addon", addon, n)
    private fun torrent(t: String, tam: Long = 0L, addon: Int = 0) = Candidata(Fuente(nombre = t, infoHash = "h${n++}", tamano = tam), "Addon $addon", addon, n)
    private val rapida = Red(50_000L)
    private val todo = Capacidades(hevc = true, av1 = true, hdr = true, dv = true)
    private val p = PrefsElector()
    private fun elige(cs: List<Candidata>, prefs: PrefsElector = p, red: Red = rapida, cap: Capacidades = todo, torrents: Boolean = true, grupo: String = "", min: Int = 166) =
        Elector.elige(cs, prefs, red, cap, min, torrents, grupo)

    @Test fun elIdiomaPrimero() {
        val ingles4k = directa("Dune.2024.2160p 🇬🇧", 40_000_000_000L)
        val castellano720 = torrent("Dune [720p] [Castellano] 👤 50", 2_000_000_000L)
        val latino1080 = directa("Dune 1080p LATINO", 4_000_000_000L)
        val cs = listOf(ingles4k, latino1080, castellano720)
        assertEquals("en tu idioma gana a cualquier calidad", castellano720, elige(cs).elegida)
        assertEquals("sin motor de torrents, el torrent no se elige solo", latino1080, elige(cs, torrents = false).elegida)
        assertEquals("tu orden manda", latino1080, elige(cs, p.copy(idiomas = listOf("latino", "castellano"))).elegida)
        val e = elige(listOf(ingles4k), p.copy(idiomas = listOf("castellano", "latino")))
        assertEquals(ingles4k, e.elegida); assertTrue("ninguna en tus idiomas", e.fueraDeIdioma)
        assertFalse("con «original con subtítulos» en tu orden, la original sí vale (con los subtítulos de tus addons)", elige(listOf(ingles4k)).fueraDeIdioma)
        // «Español» sin saber cuál: detrás del castellano de verdad y delante del latino (en los nombres, «Spanish» suele ser de España)
        val espanol = directa("Dune 1080p SPANISH")
        val castellano1080 = directa("Dune [1080p] [Castellano]")
        assertEquals(espanol, elige(listOf(latino1080, espanol)).elegida)
        assertEquals(castellano1080, elige(listOf(espanol, castellano1080)).elegida)
        assertEquals(espanol, elige(listOf(ingles4k, espanol)).elegida)
    }

    @Test fun conSubtitulosOSinEllos() {
        val vose = directa("Dune 2024 VOSE 1080p")
        val original4k = directa("Dune.2024.2160p 🇬🇧")
        assertEquals("la original con subtítulos en español, antes que la original sin ellos", vose, elige(listOf(original4k, vose)).elegida)
        val sinSubs = elige(listOf(original4k, vose), p.copy(subtitulados = false))
        assertTrue(sinSubs.fueraDeIdioma); assertEquals("sin aceptar subtítulos, la mejor calidad", original4k, sinSubs.elegida)
    }

    @Test fun laQueCabeEnTuConexion() {
        val c4k = directa("Dune [2160p] [Castellano]", 60_000_000_000L)   // ~48 Mbit/s en 166 minutos
        val c1080 = directa("Dune [1080p] [Castellano]", 4_400_000_000L)   // ~3,5 Mbit/s
        val c720 = directa("Dune [720p] [Castellano]", 1_500_000_000L)     // ~1,2 Mbit/s
        val cs = listOf(c720, c4k, c1080)
        assertEquals(c4k, elige(cs, red = Red(80_000L)).elegida)
        assertEquals(c1080, elige(cs, red = Red(10_000L)).elegida)
        assertEquals(c720, elige(cs, red = Red(3_000L)).elegida)
        assertEquals("sin saber la conexión, la mejor", c4k, elige(cs, red = Red(0L)).elegida)
        assertEquals("con datos y ahorrando, como mucho 720p", c720, elige(cs, red = Red(80_000L, movil = true)).elegida)
        assertEquals(c1080, elige(cs, red = Red(80_000L, movil = true), prefs = p.copy(ahorroDatos = false, maxima = 1080)).elegida)
        // Ninguna cabe: la que menos pesa (es la que más fácil se verá)
        assertEquals(c720, elige(listOf(c4k, c1080, c720), red = Red(500L)).elegida)
        // Sin tamaño, lo típico de su calidad
        val sinTam4k = directa("Dune [2160p] [Castellano]")
        val sinTam1080 = directa("Dune [1080p] [Castellano]")
        assertEquals(sinTam1080, elige(listOf(sinTam4k, sinTam1080), red = Red(15_000L)).elegida)
    }

    @Test fun loQueSabeReproducirElDispositivo() {
        val hevc4k = directa("Dune.2160p.DV.HEVC Castellano")
        val avc1080 = directa("Dune.1080p.x264 Castellano")
        assertEquals(hevc4k, elige(listOf(avc1080, hevc4k)).elegida)
        assertEquals("sin HEVC, la que sí puede", avc1080, elige(listOf(avc1080, hevc4k), cap = Capacidades(hevc = false)).elegida)
        assertEquals("una tele de 1080p", avc1080, elige(listOf(avc1080, directa("Dune.2160p.x264 Castellano")), cap = Capacidades(maxima = 1080)).elegida)
        val soloDv = directa("Dune.2160p.DV.x264 Castellano")
        assertEquals("Dolby Vision sin HDR10 en un dispositivo que no lo entiende", avc1080, elige(listOf(soloDv, avc1080), cap = Capacidades(dv = false, hdr = false)).elegida)
    }

    @Test fun directasYTorrents() {
        val t = torrent("Dune [1080p] [Castellano] 👤 200", 4_000_000_000L, addon = 0)
        val d = directa("Dune [1080p] [Castellano]", 4_000_000_000L, addon = 1)
        assertEquals("las directas (o de tu debrid) antes", d, elige(listOf(t, d)).elegida)
        assertEquals("si no, el orden de tus addons", t, elige(listOf(t, d), p.copy(directasPrimero = false)).elegida)
        // Un torrent sin semillas no cabe; uno que tu debrid ya tiene, sí
        val pocas = torrent("Dune [1080p] [Castellano] 👤 1")
        val muchas = torrent("Dune [720p] [Castellano] 👤 100")
        assertEquals(muchas, elige(listOf(pocas, muchas)).elegida)
        val enCache = torrent("[RD+] Dune [1080p] [Castellano] 👤 1")
        assertEquals(enCache, elige(listOf(enCache, muchas)).elegida)
        // Una directa de tu debrid aún por bajar, después de una que ya está
        val porBajar = directa("[RD download] Dune [1080p] [Castellano]", addon = 0)
        val ya = directa("[RD+] Dune [720p] [Castellano]", addon = 1)
        assertEquals(ya, elige(listOf(porBajar, ya)).elegida)
        // YouTube, una web, NZB: nunca se eligen solas
        val yt = Candidata(Fuente(nombre = "Tráiler [Castellano] 4K", ytId = "x"), "A", 0, 0)
        assertEquals(d, elige(listOf(yt, d)).elegida)
        assertNull(elige(listOf(yt)).elegida)
    }

    @Test fun elMismoGrupoQueElEpisodioAnterior() {
        val mejor = directa("Serie.S01E02.2160p Castellano", grupo = "otro-4k", addon = 0)
        val mismo = directa("Serie.S01E02.1080p Castellano", grupo = "addon-1080p", addon = 1)
        assertEquals(mejor, elige(listOf(mejor, mismo)).elegida)
        val e = elige(listOf(mejor, mismo), grupo = "addon-1080p")
        assertEquals(mismo, e.elegida); assertTrue(e.porGrupo)
        assertEquals("si el grupo no está, lo de siempre", mejor, elige(listOf(mejor, mismo), grupo = "nadie").elegida)
        // Del mismo grupo, pero este dispositivo no la puede reproducir: no
        val hevc = directa("Serie.S01E02.1080p.HEVC Castellano", grupo = "g-hevc")
        assertEquals(mejor, elige(listOf(mejor, hevc), cap = Capacidades(hevc = false), grupo = "g-hevc").elegida)
    }

    @Test fun laListaEnteraVaOrdenada() {
        val cs = listOf(directa("Dune.2160p 🇬🇧"), directa("Dune 1080p LATINO"), directa("Dune [1080p] [Castellano]"), Candidata(Fuente(nombre = "web", externalUrl = "https://x"), "A", 0, 9))
        val ord = elige(cs).ordenadas
        assertEquals(listOf("Dune [1080p] [Castellano]", "Dune 1080p LATINO", "Dune.2160p 🇬🇧", "web"), ord.map { it.fuente.nombre })
    }
}
