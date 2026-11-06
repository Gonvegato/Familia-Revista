package com.gonzalo.mostaza.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/* (La misma prueba que la de Mostaza del móvil, con el mismo código: el elector, las pistas, el protocolo y TMDB son los mismos archivos.) */
/**
 * Las pistas de verdad (lo que da Media3 de cada pista de audio y de subtítulos), probar otra fuente cuando la que se eligió sola no trae
 * tu idioma, lo que se recuerda de cada fuente y lo seguro que es el idioma que se lee del texto (con fuentes de Torrentio de verdad).
 */
class PistasTest {
    private val p = PrefsElector()
    private fun cl(l: String?, e: String? = null) = Pistas.clasifica(l, e)

    // ---------------------------------------------------------------- entender una pista

    @Test fun losCodigos() {
        assertEquals(Idioma.ESPANOL, cl("spa")); assertEquals(Idioma.ESPANOL, cl("es")); assertEquals(Idioma.ESPANOL, cl("esp"))
        assertEquals(Idioma.CASTELLANO, cl("es-ES")); assertEquals(Idioma.CASTELLANO, cl("es_ES")); assertEquals(Idioma.CASTELLANO, cl("spa-ES"))
        assertEquals(Idioma.LATINO, cl("es-419")); assertEquals(Idioma.LATINO, cl("es-MX")); assertEquals(Idioma.LATINO, cl("es_AR"))
        assertEquals(Idioma.LATINO, cl("es-US")); assertEquals(Idioma.LATINO, cl("es-CO"))
        assertEquals(Idioma.INGLES, cl("eng")); assertEquals(Idioma.INGLES, cl("en-US")); assertEquals(Idioma.FRANCES, cl("fre"))
        assertEquals(Idioma.PORTUGUES, cl("pt-BR")); assertEquals(Idioma.JAPONES, cl("ja")); assertEquals(Idioma.ALEMAN, cl("deu"))
        assertEquals("un idioma que no se distingue", Idioma.OTRO, cl("hin")); assertEquals(Idioma.OTRO, cl("pl"))
        assertNull("sin idioma", cl(null)); assertNull(cl("und")); assertNull(cl("")); assertNull(cl("mul"))
    }

    @Test fun losNombresMandan() {
        assertEquals(Idioma.CASTELLANO, cl("spa", "Castellano"))
        assertEquals(Idioma.LATINO, cl("spa", "Latino"))
        assertEquals(Idioma.CASTELLANO, cl("es", "Español (España)"))
        assertEquals(Idioma.LATINO, cl("es", "Spanish (Latin America)"))
        assertEquals(Idioma.LATINO, cl("es", "Español Latino 5.1"))
        assertEquals(Idioma.LATINO, cl("es", "Latin Spanish"))
        assertEquals(Idioma.LATINO, cl("spa", "Mexicano"))
        assertEquals(Idioma.LATINO, cl("es", "Español (Latinoamérica)"))
        assertEquals("el nombre dice más que el código", Idioma.LATINO, cl("es-ES", "Latino"))
        assertEquals(Idioma.CASTELLANO, cl("spa", "Español [Castellano] AC3 5.1"))
        assertEquals(Idioma.CASTELLANO, cl("es", "Spanish (Spain)"))
        assertEquals(Idioma.CASTELLANO, cl("es", "European Spanish"))
        assertEquals("«Spanish» a secas: no se sabe cuál", Idioma.ESPANOL, cl("es", "Spanish"))
        assertEquals(Idioma.ESPANOL, cl("spa", "Español"))
        // Sin código, el nombre
        assertEquals(Idioma.CASTELLANO, cl("und", "Castellano"))
        assertEquals(Idioma.ESPANOL, cl(null, "Spanish"))
        assertEquals(Idioma.LATINO, cl(null, "Latino"))
        assertEquals(Idioma.INGLES, cl(null, "English 5.1"))
        assertNull(cl("und", "Pista 1"))
        // Un nombre que no dice de dónde no cambia el código
        assertEquals(Idioma.INGLES, cl("eng", "Commentary"))
        assertEquals(Idioma.CASTELLANO, cl("es-ES", "AC3 5.1"))
    }

    @Test fun losSubtitulos() {
        assertTrue(Pistas.subtituloEspanol("spa", null)); assertTrue(Pistas.subtituloEspanol("es-419", "Latino")); assertTrue(Pistas.subtituloEspanol(null, "Español"))
        assertFalse(Pistas.subtituloEspanol("eng", "English")); assertFalse(Pistas.subtituloEspanol(null, null))
    }

    @Test fun loQueTraeUnArchivo() {
        val dual = Pistas.verifica(listOf("es-ES" to "Castellano", "eng" to null), emptyList(), p)
        assertEquals(setOf(Idioma.CASTELLANO, Idioma.INGLES), dual.idiomas); assertTrue(dual.concluyente)
        assertEquals(0.0, Pistas.puesto(dual, p), 0.0)
        val latino = Pistas.verifica(listOf("es-419" to null, "en" to null), listOf("spa" to null), p)
        assertEquals(1.0, Pistas.puesto(latino, p), 0.0); assertTrue(latino.subtitulosEsp)
        val espanol = Pistas.verifica(listOf("spa" to null), emptyList(), p)
        assertEquals("español sin saber: cuenta para castellano a medias", 0.5, Pistas.puesto(espanol, p), 0.0)
        val original = Pistas.verifica(listOf("eng" to null), listOf("es" to null), p)
        assertEquals("la original con subtítulos en español", 2.0, Pistas.puesto(original, p), 0.0)
        // Sin saber el idioma de una pista no se puede decir que no esté el tuyo
        assertFalse(Pistas.verifica(listOf("und" to null), emptyList(), p).concluyente)
        assertFalse(Pistas.verifica(listOf("es-419" to null, "und" to null), emptyList(), p).concluyente)
        assertTrue("si ya tiene castellano, da igual la otra", Pistas.verifica(listOf("es-ES" to null, "und" to null), emptyList(), p).concluyente)
        assertFalse("sin audio no se sabe nada", Pistas.verifica(emptyList(), emptyList(), p).concluyente)
        assertEquals("en latino", Pistas.describe(latino, p))
        assertEquals("en versión original con subtítulos", Pistas.describe(original, p))
        assertEquals("en inglés", Pistas.describe(Pistas.verifica(listOf("eng" to null), emptyList(), p.copy(idiomas = listOf("castellano", "latino"))), p.copy(idiomas = listOf("castellano", "latino"))))
    }

    // ---------------------------------------------------------------- el audio y los subtítulos que se ponen

    @Test fun elMejorAudio() {
        val eng = PistaAudio(Idioma.INGLES, elegida = true); val lat = PistaAudio(Idioma.LATINO); val cas = PistaAudio(Idioma.CASTELLANO)
        assertEquals(2, Pistas.mejorAudio(listOf(eng, lat, cas), p))
        assertEquals("tu orden manda", 1, Pistas.mejorAudio(listOf(eng, lat, cas), p.copy(idiomas = listOf("latino", "castellano"))))
        assertNull("sin español, la que pone el reproductor", Pistas.mejorAudio(listOf(eng, PistaAudio(Idioma.FRANCES)), p))
        assertEquals("español sin saber antes que latino (con castellano primero)", 1, Pistas.mejorAudio(listOf(lat.copy(elegida = true), PistaAudio(Idioma.ESPANOL)), p))
        assertNull("una que este dispositivo no sabe reproducir, no", Pistas.mejorAudio(listOf(eng, cas.copy(soportada = false)), p))
        assertEquals("si la que suena ya es la mejor, esa", 1, Pistas.mejorAudio(listOf(cas, cas.copy(elegida = true)), p))
        assertNull("si prefieres la original, no se cambia", Pistas.mejorAudio(listOf(eng, cas), p.copy(idiomas = listOf("original", "castellano"))))
        assertNull("sin castellano ni latino en tu orden, no", Pistas.mejorAudio(listOf(eng, cas), p.copy(idiomas = listOf("vose"))))
    }

    @Test fun losSubtitulosSiElAudioNoEsEspanol() {
        assertTrue(Pistas.quiereSubtitulos(Idioma.INGLES, p))
        assertTrue(Pistas.quiereSubtitulos(Idioma.OTRO, p))
        assertFalse(Pistas.quiereSubtitulos(Idioma.CASTELLANO, p)); assertFalse(Pistas.quiereSubtitulos(Idioma.LATINO, p))
        assertFalse("no sabiendo el audio, no se tocan", Pistas.quiereSubtitulos(null, p))
        assertFalse("sin aceptar subtítulos", Pistas.quiereSubtitulos(Idioma.INGLES, p.copy(subtitulados = false)))
        assertFalse("sin «vose» en tu orden", Pistas.quiereSubtitulos(Idioma.INGLES, p.copy(idiomas = listOf("castellano", "latino", "original"))))
    }

    // ---------------------------------------------------------------- probar otra

    private var n = 0
    private fun d(t: String) = Candidata(Fuente(nombre = t, url = "https://d/${n++}"), "Addon", 0, n)
    private fun v(vararg i: Idioma, subs: Boolean = false) = Verificacion(i.toSet(), subs, true)

    @Test fun pruebaHastaDarConTuIdioma() {
        val a = d("Peli [1080p] [Castellano]"); val b = d("Peli 1080p"); val c = d("Peli 720p")
        val t = Tanteo(p, listOf(a, b, c), 0L)
        val d1 = t.tras(a, v(Idioma.LATINO, Idioma.INGLES), 2_000L)
        assertTrue(d1 is Decision.Prueba); d1 as Decision.Prueba
        assertEquals(b, d1.candidata); assertEquals("Esta no tiene audio en castellano: probando otra…", d1.mensaje)
        assertEquals(Decision.SeQueda(), t.tras(b, v(Idioma.CASTELLANO), 5_000L))
        assertTrue(t.terminado)
        assertEquals("ya terminado, no se prueba más", Decision.SeQueda(), t.tras(c, v(Idioma.INGLES), 6_000L))
    }

    @Test fun siNingunaVuelveALaMejorYLoDice() {
        val a = d("Peli 1080p"); val b = d("Peli 720p"); val c = d("Peli 480p")
        val t = Tanteo(p, listOf(a, b, c), 0L)
        assertEquals(b, (t.tras(a, v(Idioma.LATINO), 1_000L) as Decision.Prueba).candidata)
        assertEquals(c, (t.tras(b, v(Idioma.INGLES, subs = true), 2_000L) as Decision.Prueba).candidata)
        val fin = t.tras(c, v(Idioma.FRANCES), 3_000L)
        assertEquals(Decision.Vuelve(a, "No hay ninguna en castellano; la mejor es en latino"), fin)
        // La original con subtítulos, si es lo mejor que hay
        val x = d("x"); val y = d("y")
        val t2 = Tanteo(p, listOf(x, y), 0L)
        t2.tras(x, v(Idioma.INGLES, subs = true), 1_000L)
        assertEquals(Decision.Vuelve(x, "No hay ninguna en castellano; la mejor es en versión original con subtítulos"), t2.tras(y, v(Idioma.ALEMAN), 2_000L))
        // Si la mejor es la que ya está, se queda (y lo dice)
        val s = d("s")
        assertEquals(Decision.SeQueda("No hay ninguna en castellano; la mejor es en latino"), Tanteo(p, listOf(s), 0L).tras(s, v(Idioma.LATINO), 1_000L))
    }

    @Test fun losLimites() {
        val cs = (1..8).map { d("Peli $it") }
        val t = Tanteo(p, cs, 0L)
        var dec: Decision = t.tras(cs[0], v(Idioma.LATINO), 1_000L)
        var i = 1
        while (dec is Decision.Prueba) { dec = t.tras(dec.candidata, v(Idioma.INGLES), 1_000L + i * 1_000L); i++ }
        assertEquals("como mucho ${Tanteo.MAX_PRUEBAS} archivos", Tanteo.MAX_PRUEBAS, t.intentos)
        assertEquals(Decision.Vuelve(cs[0], "No he encontrado ninguna en castellano (he probado 4); la mejor es en latino"), dec)
        // Y como mucho 45 s en total
        val t2 = Tanteo(p, cs, 0L)
        assertTrue(t2.tras(cs[0], v(Idioma.LATINO), 1_000L) is Decision.Prueba)
        assertEquals(Decision.Vuelve(cs[0], "No he encontrado ninguna en castellano (he probado 2); la mejor es en latino"), t2.tras(cs[1], v(Idioma.INGLES), 46_000L))
        // Se acaba el tiempo sin ver las pistas de la que se está probando: a la mejor comprobada
        val t3 = Tanteo(p, cs, 0L)
        t3.tras(cs[0], v(Idioma.LATINO), 1_000L)
        assertEquals(Decision.SeQueda(), t3.trasTiempo(cs[1], 30_000L))
        assertEquals(Decision.Vuelve(cs[0], "No he encontrado ninguna en castellano; la mejor es en latino"), t3.trasTiempo(cs[1], 45_000L))
    }

    @Test fun soloLasQuePodrianSerMejoresYSeAbrenRapido() {
        val a = d("Peli 1080p")
        val latino = d("Peli 1080p LATINO")                                                     // el texto dice que es igual: no
        val ingles = d("Peli 1080p 🇬🇧")                                                         // peor: no
        val sinSemillas = Candidata(Fuente(nombre = "Peli [Castellano] 👤 0", infoHash = "h1"), "T", 0, 90)  // torrent sin semillas: no
        val porBajar = d("[RD download] Peli [Castellano]")                                     // de tu debrid, aún por bajar: no
        val multi = d("Peli MULTI 1080p")                                                       // puede tener castellano: sí
        val t = Tanteo(p, listOf(a, latino, ingles, sinSemillas, porBajar, multi), 0L)
        assertEquals(multi, (t.tras(a, v(Idioma.LATINO), 1_000L) as Decision.Prueba).candidata)
        val enCache = Candidata(Fuente(nombre = "[RD+] Peli 👤 1", infoHash = "h2"), "T", 0, 91)
        val conSemillas = Candidata(Fuente(nombre = "Peli 👤 40", infoHash = "h3"), "T", 0, 92)
        val t2 = Tanteo(p, listOf(a, enCache, conSemillas), 0L)
        assertEquals(enCache, (t2.tras(a, v(Idioma.LATINO), 1_000L) as Decision.Prueba).candidata)
        assertEquals(conSemillas, (t2.tras(enCache, v(Idioma.LATINO), 2_000L) as Decision.Prueba).candidata)
        // Una ya comprobada (en otra ocasión) que es peor, tampoco
        val vista = d("Peli [Castellano]").copy(verificacion = v(Idioma.INGLES))
        assertEquals(Decision.SeQueda("No hay ninguna en castellano; la mejor es en latino"), Tanteo(p, listOf(a, vista), 0L).tras(a, v(Idioma.LATINO), 1_000L))
    }

    @Test fun sinSaberNoSeCambiaYLaQueFallaSeSalta() {
        val a = d("a"); val b = d("b"); val c = d("c")
        assertEquals("una pista sin idioma: no se puede decir que le falte el tuyo", Decision.SeQueda(),
            Tanteo(p, listOf(a, b), 0L).tras(a, Pistas.verifica(listOf("und" to null), emptyList(), p), 1_000L))
        val t = Tanteo(p, listOf(a, b, c), 0L)
        assertEquals(Decision.Prueba(b, "Esta no se abre: probando otra…"), t.trasFallo(a, 1_000L))
        assertEquals(Decision.Prueba(c, "Esta no tiene audio en castellano: probando otra…"), t.tras(b, v(Idioma.LATINO), 2_000L))
        assertEquals("falla la última: a la mejor que se vio", Decision.Vuelve(b, "No hay ninguna en castellano; la mejor es en latino"), t.trasFallo(c, 3_000L))
        assertEquals("falla y no hay otra ni ninguna vista: lo dice el reproductor", Decision.Nada, Tanteo(p, listOf(a), 0L).trasFallo(a, 1_000L))
    }

    @Test fun loQueEligesTuNoSeCambia() {
        val a = d("a"); val b = d("b")
        val t = Tanteo(p, listOf(a, b), 0L)
        t.cancela()
        assertEquals(Decision.SeQueda(), t.tras(a, v(Idioma.INGLES), 1_000L))
        assertEquals(Decision.Nada, t.trasFallo(a, 1_000L))
        assertFalse("el encargo de una fuente que tocas no se prueba", Encargo(MetaBreve("tt1", "movie", "Peli"), null, null, a, listOf(a, b)).automatica)
    }

    // ---------------------------------------------------------------- lo que se recuerda

    @Test fun laClaveNoGuardaSecretos() {
        val rd = Fuente(url = "https://addon.example/resolve/realdebrid/CLAVESECRETA123/0123456789abcdef0123456789abcdef01234567/null/2/peli.mkv", fileIdx = 2)
        assertEquals("bt:0123456789abcdef0123456789abcdef01234567:2", Elector.clave(rd))
        assertEquals("bt:abcd:3", Elector.clave(Fuente(infoHash = "ABCD", fileIdx = 3)))
        assertEquals("f:peli.mkv|123", Elector.clave(Fuente(url = "https://x/y?token=SECRETO", archivo = "Peli.mkv", tamano = 123)))
        val u1 = Elector.clave(Fuente(url = "https://cdn.example/v/abc.mp4?token=SECRETO1"))
        val u2 = Elector.clave(Fuente(url = "https://cdn.example/v/abc.mp4?token=OTRO"))
        assertEquals("la misma dirección con otra clave detrás de «?» es la misma fuente", u1, u2)
        assertTrue(u1.startsWith("u:")); assertFalse(u1.contains("SECRETO")); assertFalse(Elector.clave(rd).contains("CLAVESECRETA"))
    }

    @Test fun laMemoriaSeGuardaYSeLee() {
        var m = MemoriaPistas.con(emptyMap(), "bt:a:1", v(Idioma.CASTELLANO, Idioma.INGLES, subs = true), 10L)
        m = MemoriaPistas.con(m, "u:b", v(Idioma.LATINO), 20L)
        val leida = MemoriaPistas.decodifica(MemoriaPistas.codifica(m))
        assertEquals(v(Idioma.CASTELLANO, Idioma.INGLES, subs = true), leida["bt:a:1"]?.v)
        assertEquals(v(Idioma.LATINO), leida["u:b"]?.v)
        assertTrue(MemoriaPistas.decodifica("basura\n\t\t").isEmpty())
        // Como mucho MAX (las más recientes)
        val muchas = (1..MemoriaPistas.MAX + 10).fold(emptyMap<String, MemoriaPistas.Entrada>()) { acc, i -> MemoriaPistas.con(acc, "k$i", v(Idioma.INGLES), i.toLong()) }
        val recortada = MemoriaPistas.decodifica(MemoriaPistas.codifica(muchas))
        assertEquals(MemoriaPistas.MAX, recortada.size); assertTrue("k${MemoriaPistas.MAX + 10}" in recortada); assertFalse("k1" in recortada)
    }

    @Test fun loComprobadoMandaSobreElTexto() {
        val dice = d("Peli [1080p] [Castellano]")      // dice castellano… y era latino
        val calla = d("Peli 1080p")                     // no dice nada… y era castellano
        val red = Red(50_000L); val cap = Capacidades(hevc = true, av1 = true, hdr = true, dv = true)
        assertEquals(dice, Elector.elige(listOf(dice, calla), p, red, cap, 120, true).elegida)
        val mem = mapOf(Elector.clave(dice.fuente) to v(Idioma.LATINO), Elector.clave(calla.fuente) to v(Idioma.CASTELLANO, Idioma.INGLES))
        val e = Elector.elige(listOf(dice, calla), p, red, cap, 120, true, verificados = { mem[it] })
        assertEquals(calla.fuente, e.elegida?.fuente)
        assertEquals("1080p · Castellano ✓", e.elegida?.analisis?.resumen())
        assertEquals(listOf(calla.fuente, dice.fuente), e.ordenadas.map { it.fuente })
        // A igual idioma, la comprobada antes
        val otra = d("Peli [2160p] [Castellano]")
        val mem2 = mapOf(Elector.clave(calla.fuente) to v(Idioma.CASTELLANO))
        assertEquals(calla.fuente, Elector.elige(listOf(otra, calla), p, red, cap, 120, true, verificados = { mem2[it] }).elegida?.fuente)
    }

    // ---------------------------------------------------------------- MULTI y DUAL

    @Test fun multiYDualPuedenTenerEspanol() {
        val red = Red(50_000L); val cap = Capacidades(hevc = true, av1 = true, hdr = true, dv = true)
        val cs = listOf(d("Peli.2024.1080p 🇬🇧"), d("Peli.2024.1080p.MULTI"), d("Peli 1080p LATINO"), d("Peli.2024.1080p.DUAL"), d("Peli [1080p] [Castellano]"), d("Peli 1080p SPANISH"), d("Peli 1080p"))
        val ord = Elector.ordena(cs, p, red, cap, 120).map { it.fuente.nombre }
        assertEquals(listOf("Peli [1080p] [Castellano]", "Peli 1080p SPANISH", "Peli 1080p LATINO", "Peli.2024.1080p.MULTI", "Peli.2024.1080p.DUAL", "Peli.2024.1080p 🇬🇧", "Peli 1080p"), ord)
        assertEquals(1.75, Elector.puestoIdioma(Analisis.de("Peli MULTI"), p), 0.0)
        assertEquals("con la original con subtítulos antes que el latino, MULTI va delante de ella", 0.75,
            Elector.puestoIdioma(Analisis.de("Peli DUAL"), p.copy(idiomas = listOf("castellano", "vose", "latino"))), 0.0)
        assertTrue(Analisis.de("Peli MULTI").puedeTenerEspanol); assertFalse(Analisis.de("Peli MULTI Castellano").puedeTenerEspanol)
    }

    // ---------------------------------------------------------------- fuentes de Torrentio de verdad (con tu debrid)

    private fun tio(desc: String, tam: Long, nombre: String = "[RD+] Torrentio") =
        Candidata(Fuente(nombre = nombre, descripcion = desc, url = "https://d/t${n++}", tamano = tam), "Torrentio", 0, n)

    private val wolf4k = tio("Obsession (2025) [4k 2160p][Esp]\n👤 31 💾 18.2 GB ⚙️ Wolfmax4k\n🇪🇸", 18_200_000_000L, "[RD+] Torrentio\n4k")
    private val wolf1080 = tio("Obsession (2025) [Bluray 1080p][Esp]\n👤 22 💾 6.1 GB ⚙️ Wolfmax4k\n🇪🇸", 6_100_000_000L, "[RD+] Torrentio\n1080p")
    private val wolfLigera = tio("Obsession (2025) [Bluray][Esp]\n👤 9 💾 1.09 GB ⚙️ Wolfmax4k\n🇪🇸", 1_090_000_000L)
    private val btm = tio("Obsession.2025.2026.2160p.UHD.BluRay.DV.HDR.ENG.LATINO.DDP5.1.H265.MP4-BTM\n👤 14 💾 25.4 GB ⚙️ TorrentGalaxy\n🇬🇧 / 🇪🇸 / 🇲🇽", 25_400_000_000L)
    private val sepiol = tio("Obsession (2026) (720p BluRay AV1 Sepiol)\n👤 40 💾 584 MB ⚙️ 1337x\n🇬🇧 / 🇮🇹 / 🇵🇹 / 🇪🇸 / 🇫🇷 / 🇩🇪 / 🇳🇱 / 🇮🇳 / 🇵🇱 / 🇩🇰 / 🇸🇪 / 🇳🇴", 584_000_000L, "[RD+] Torrentio\n720p")
    private val micro = tio("Obsession (2025) (Spanish English Subs) Micro4K 2160P Sdr x265-Ac3 Aac\n👤 6 💾 3.2 GB\n🇬🇧 / 🇪🇸", 3_200_000_000L)
    private val hdts = tio("Obsession.2025.1080p.HDTS.h264.Dual.YG\n👤 120 💾 2.1 GB\nDual Audio / 🇬🇧 / 🇪🇸", 2_100_000_000L)
    private val upscale = tio("Obsession (2025) 4320p HDR Ai Upscale x265 Multidub/Sub\n👤 3 💾 31 GB\nMulti Subs", 31_000_000_000L)
    private val todas get() = listOf(sepiol, upscale, hdts, micro, btm, wolf4k, wolf1080, wolfLigera)

    @Test fun loSeguroQueEsElIdioma() {
        assertEquals(setOf(Idioma.CASTELLANO), wolf4k.analisis.idiomas); assertEquals(Confianza.FUERTE, wolf4k.analisis.confianza)
        assertEquals(Confianza.FUERTE, wolfLigera.analisis.confianza); assertEquals(Idioma.CASTELLANO, wolfLigera.analisis.idiomaPrincipal())
        assertEquals("con 🇲🇽 (y «LATINO»), la 🇪🇸 no es castellano", setOf(Idioma.INGLES, Idioma.LATINO), btm.analisis.idiomas)
        assertEquals(Confianza.FUERTE, btm.analisis.confianza)
        assertEquals(Idioma.CASTELLANO, sepiol.analisis.idiomaPrincipal()); assertEquals("12 banderas: casi siempre son subtítulos", Confianza.DEBIL, sepiol.analisis.confianza)
        assertEquals(Confianza.MEDIA, micro.analisis.confianza); assertTrue(micro.analisis.tieneEspanol)
        assertEquals(Confianza.MEDIA, hdts.analisis.confianza); assertTrue(hdts.analisis.mala)
        assertTrue(Idioma.MULTI in upscale.analisis.idiomas); assertTrue(upscale.analisis.reescalada); assertEquals("4320p cuenta como 4K", 2160, upscale.analisis.resolucion)
        assertEquals(setOf(Idioma.LATINO), Analisis.de("Peli 1080p 🇪🇸 🇲🇽").idiomas)
        assertEquals(setOf(Idioma.CASTELLANO, Idioma.LATINO), Analisis.de("Peli 1080p Castellano Latino 🇪🇸 🇲🇽").idiomas)
        // Y se ve en la ficha
        assertEquals("720p · ¿Castellano? · 584 MB", sepiol.analisis.resumen())
        assertEquals("4K · Castellano · 18,2 GB", wolf4k.analisis.resumen())
        // El orden: seguro > a medias > poco seguro (detrás del latino seguro)
        assertTrue(Elector.puestoIdioma(wolf4k.analisis, p) < Elector.puestoIdioma(micro.analisis, p))
        assertTrue(Elector.puestoIdioma(micro.analisis, p) < Elector.puestoIdioma(btm.analisis, p))
        assertTrue(Elector.puestoIdioma(btm.analisis, p) < Elector.puestoIdioma(sepiol.analisis, p))
    }

    @Test fun elCasoDeVerdadConDatosDelMovil() {
        val cap = Capacidades(hevc = true, av1 = true, hdr = true, dv = true)
        val movil = Elector.elige(todas, p, Red(20_000L, movil = true), cap, 110, true)
        assertEquals("con «Ahorrar datos»: la ligera en castellano seguro, no la de las 12 banderas", wolfLigera, movil.elegida)
        val wifi = Elector.elige(todas, p, Red(80_000L), cap, 110, true)
        assertEquals("con wifi rápida: la 4K en castellano", wolf4k, wifi.elegida)
        // Nunca una TS si hay otra en castellano que no lo es
        assertEquals(micro, Elector.elige(listOf(hdts, micro), p, Red(80_000L), cap, 110, true).elegida)
        // Y si la ligera resulta no tener castellano (lo dicen sus pistas), el tanteo va a la siguiente que podría
        val t = Tanteo(p, movil.ordenadas, 0L)
        val sig = t.tras(wolfLigera, v(Idioma.LATINO), 1_000L)
        assertTrue(sig is Decision.Prueba)
        assertEquals(Confianza.FUERTE, (sig as Decision.Prueba).candidata.analisis.confianza)
    }
}
