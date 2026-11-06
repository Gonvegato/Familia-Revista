package com.gonzalo.mostaza.pc

import com.gonzalo.familia.pc.Sitio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Lo de mpv que no necesita mpv: su línea de órdenes, lo que se le manda por el socket, lo que contesta y sus pistas. */
class MpvTest {
    @Test fun laLineaDeOrdenesNoLlevaNadaSecreto() {
        val o = Mpv.orden(File("/usr/bin/mpv"), File("/tmp/m/s1.sock"), Mpv.Opciones(audio = Mpv.idiomas("spa"), subs = Mpv.idiomas("spa"), subTam = 125, subFondo = true, pantallaCompleta = false))
        assertEquals("/usr/bin/mpv", o.first())
        assertTrue("--input-ipc-server=/tmp/m/s1.sock" in o)
        // Sin dirección ni cabeceras (van por el socket), sin yt-dlp (le daría la dirección en su línea de órdenes) y sin que mpv guarde nada suyo
        assertTrue(o.none { it.startsWith("http") || it.contains("http-header-fields") || it.contains("://") })
        assertTrue("--ytdl=no" in o); assertTrue("--save-position-on-quit=no" in o); assertTrue("--resume-playback=no" in o)
        assertTrue("--idle=yes" in o); assertTrue("--no-terminal" in o)
        assertTrue("--alang=spa,es" in o); assertTrue("--slang=spa,es" in o)
        assertTrue("--sub-scale=1.25" in o); assertTrue(o.any { it.startsWith("--sub-back-color=") })
        assertFalse("--fs" in o)
        assertTrue("--fs" in Mpv.orden(File("/usr/bin/mpv"), File("/tmp/s"), Mpv.Opciones(pantallaCompleta = true)))
        assertTrue("sin idioma de subtítulos, sin --slang", Mpv.orden(File("mpv"), File("s"), Mpv.Opciones(subs = Mpv.idiomas(""))).none { it.startsWith("--slang") })
    }

    @Test fun losIdiomasComoLosEntiendeMpv() {
        assertEquals(listOf("spa", "es"), Mpv.idiomas("spa"))
        assertEquals(listOf("eng", "en"), Mpv.idiomas("eng"))
        assertEquals(listOf("spa", "es"), Mpv.idiomas("es"))
        assertEquals(emptyList<String>(), Mpv.idiomas(""))
    }

    @Test fun lasCabecerasNoDejanColarOtra() {
        assertEquals(listOf("Authorization: Bearer x", "Referer: https://a"), Mpv.cabeceras(linkedMapOf("Authorization" to "Bearer x", "Referer" to "https://a")))
        assertEquals("con un salto de línea, fuera", emptyList<String>(), Mpv.cabeceras(mapOf("X" to "a\r\nY: b")))
        assertEquals(emptyList<String>(), Mpv.cabeceras(mapOf("X: Y" to "a")))
    }

    @Test fun loQueSeMandaPorElSocket() {
        assertEquals("""{"command":["loadfile","https://d/x.mkv","replace"],"request_id":7}""", Mpv.linea(7, "loadfile", "https://d/x.mkv", "replace"))
        assertEquals("""{"command":["set_property","http-header-fields",["A: b"]],"request_id":1}""", Mpv.linea(1, "set_property", "http-header-fields", listOf("A: b")))
    }

    @Test fun loQueContestaMpv() {
        val r = MensajeMpv.lee("""{"data":30.023000,"request_id":4,"error":"success"}""") as MensajeMpv.Respuesta
        assertEquals(4L, r.id); assertTrue(r.ok); assertEquals(30.023, r.datos as Double, 0.0001)
        val mal = MensajeMpv.lee("""{"request_id":5,"error":"property unavailable"}""") as MensajeMpv.Respuesta
        assertFalse(mal.ok); assertEquals("property unavailable", mal.error)
        val fin = MensajeMpv.lee("""{"event":"end-file","reason":"eof","playlist_entry_id":3}""") as MensajeMpv.Suceso
        assertEquals("end-file", fin.nombre); assertEquals("eof", fin.razon); assertEquals(3L, fin.entrada)
        val cambio = MensajeMpv.lee("""{"event":"property-change","id":1,"name":"core-idle","data":false}""") as MensajeMpv.Suceso
        assertEquals("core-idle", cambio.propiedad); assertEquals(false, cambio.valor)
        assertNull(MensajeMpv.lee("no es json"))
    }

    /** La `track-list` tal cual la da mpv 0.37 (de un archivo con un audio en inglés y otro en castellano, y dos subtítulos). */
    private val pistas = PistaMpv.deLista(com.gonzalo.familia.nucleo.Json.lee("""[
        {"id":1,"type":"video","src-id":1,"external":false,"selected":true,"codec":"h264","demux-w":320,"demux-h":240},
        {"id":1,"type":"audio","src-id":2,"lang":"eng","audio-channels":1,"default":true,"external":false,"selected":true,"codec":"aac","demux-channel-count":2},
        {"id":2,"type":"audio","src-id":3,"lang":"spa","title":"Castellano","external":false,"selected":false,"codec":"ac3","demux-channel-count":6},
        {"id":1,"type":"sub","lang":"eng","external":false,"selected":false,"codec":"subrip"},
        {"id":2,"type":"sub","lang":"es-419","title":"Latino","external":true,"selected":false,"codec":"subrip"},
        {"id":3,"type":"sub","lang":"spa","title":"Español (España)","external":false,"selected":false,"codec":"subrip"}]"""))

    @Test fun lasPistasDeVerdadConLoDeLasPistas() {
        assertEquals(2, PistasDeMpv.audios(pistas).size); assertEquals(3, PistasDeMpv.subtitulos(pistas).size)
        assertEquals("Castellano · Español · AC3 · 6 canales", pistas.first { it.tipo == "audio" && it.id == 2 }.etiqueta)
        val p = PrefsElector()
        val v = PistasDeMpv.verifica(pistas, p)
        assertEquals(setOf(Idioma.INGLES, Idioma.CASTELLANO), v.idiomas); assertTrue(v.subtitulosEsp); assertTrue(v.concluyente)
        assertEquals("pone el castellano", 2, PistasDeMpv.mejorAudio(pistas, p))
        assertEquals("con el latino primero, el latino; no hay audio latino: el castellano igual", 2, PistasDeMpv.mejorAudio(pistas, PrefsElector(idiomas = listOf("latino", "castellano"))))
        assertNull("la original antes que el español: no se cambia", PistasDeMpv.mejorAudio(pistas, PrefsElector(idiomas = listOf("original", "castellano"))))
        assertEquals("los subtítulos de España antes que los latinos", 3, PistasDeMpv.subtituloEspanol(pistas, p))
        assertEquals(2, PistasDeMpv.subtituloEspanol(pistas, PrefsElector(idiomas = listOf("latino", "castellano"))))
    }

    @Test fun dondeSeBuscaMpv() {
        val mac = Mpv.candidatos(Sitio.Sistema.MAC, "/usr/bin:/bin", File("/Users/g")).map { it.path }
        assertTrue("el de Homebrew, aunque no esté en el PATH de una app del Mac", "/opt/homebrew/bin/mpv" in mac)
        assertTrue("/usr/local/bin/mpv" in mac); assertTrue("/Applications/mpv.app/Contents/MacOS/mpv" in mac)
        assertEquals("primero el del PATH", "/usr/bin/mpv", mac.first())
        val linux = Mpv.candidatos(Sitio.Sistema.LINUX, "", File("/home/g")).map { it.path }
        assertTrue("/usr/bin/mpv" in linux)
        assertTrue(Mpv.comoInstalar(Sitio.Sistema.LINUX).contains("sudo pacman -S mpv"))
        assertTrue(Mpv.comoInstalar(Sitio.Sistema.MAC).contains("brew install mpv"))
    }

    @Test fun queSeHaceConCadaFuente() {
        assertTrue(Reproductor.accion(Fuente(url = "https://d/x.mkv")) is Accion.Reproductor)
        assertTrue("sin motor, un torrent no", Reproductor.accion(Fuente(infoHash = "a".repeat(40)), torrents = false) is Accion.Aviso)
        assertTrue(Reproductor.accion(Fuente(infoHash = "a".repeat(40)), torrents = true) is Accion.Reproductor)
        assertEquals("https://www.youtube.com/watch?v=abc", (Reproductor.accion(Fuente(ytId = "abc")) as Accion.Fuera).url)
        assertTrue(Reproductor.accion(Fuente(externalUrl = "stremio:///detail/x")) is Accion.Aviso)
        assertTrue(Reproductor.accion(Fuente(nzbUrl = "https://n/x.nzb")) is Accion.Aviso)
    }
}
