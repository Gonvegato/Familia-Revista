package com.gonzalo.cobre.pc

import com.gonzalo.familia.pc.Sitio
import com.gonzalo.iptv.Episodio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Lo de mpv que no necesita mpv: su línea de órdenes, sus teclas, lo que se le manda por el socket y lo que contesta. */
class MpvTest {
    @Test fun laLineaDeOrdenesNoLlevaNadaSecreto() {
        val o = Mpv.orden(File("/usr/bin/mpv"), File("/tmp/m/s1.sock"), Mpv.Opciones(audio = Mpv.idiomas("spa"), subs = Mpv.idiomas(""), pantallaCompleta = false, teclas = File("/tmp/m/teclas.conf"), agente = "Cobre/1.0.0"))
        assertEquals("/usr/bin/mpv", o.first())
        assertTrue("--input-ipc-server=/tmp/m/s1.sock" in o)
        // Sin dirección ni cabeceras (van por el socket), sin yt-dlp (le daría la dirección en su línea de órdenes) y sin que mpv guarde nada suyo
        assertTrue(o.none { it.startsWith("http") || it.contains("http-header-fields") || it.contains("://") })
        assertTrue("--ytdl=no" in o); assertTrue("--save-position-on-quit=no" in o); assertTrue("--resume-playback=no" in o)
        assertTrue("--idle=yes" in o); assertTrue("--no-terminal" in o); assertTrue("--input-gamepad=yes" in o)
        assertTrue("--input-conf=/tmp/m/teclas.conf" in o)
        assertTrue("--alang=spa,es" in o); assertTrue("sin idioma de subtítulos, sin subtítulos", "--sid=no" in o); assertTrue(o.none { it.startsWith("--slang") })
        assertTrue("--user-agent=Cobre/1.0.0" in o)
        assertFalse("--fs" in o)
        assertTrue("--fs" in Mpv.orden(File("/usr/bin/mpv"), File("/tmp/s"), Mpv.Opciones(pantallaCompleta = true)))
        assertTrue("--slang=eng,en" in Mpv.orden(File("mpv"), File("s"), Mpv.Opciones(subs = Mpv.idiomas("eng"))))
    }

    @Test fun lasTeclasDeCobreEnLaVentanaDeMpv() {
        val t = Mpv.teclas().lines().filter { it.isNotBlank() && !it.startsWith("#") }
        fun que(k: String) = t.firstOrNull { it.substringBefore(' ') == k }?.substringAfter(' ')
        assertEquals("script-message cobre tecla arriba", que("UP")); assertEquals("script-message cobre tecla abajo", que("DOWN"))
        assertEquals("script-message cobre tecla arriba", que("PGUP")); assertEquals("script-message cobre tecla abajo", que("PGDWN"))
        assertEquals("el mando: la cruceta", "script-message cobre tecla arriba", que("GAMEPAD_DPAD_UP")); assertEquals("script-message cobre tecla abajo", que("GAMEPAD_DPAD_DOWN"))
        assertEquals("A abre la lista", "script-message cobre tecla lista", que("GAMEPAD_ACTION_DOWN")); assertEquals("script-message cobre tecla lista", que("ENTER"))
        assertEquals("script-message cobre tecla volver", que("ESC")); assertEquals("script-message cobre tecla info", que("i"))
        for (n in 0..9) { assertEquals("script-message cobre numero $n", que("$n")); assertEquals("script-message cobre numero $n", que("KP$n")) }
        // (espacio, izquierda, derecha, «f» y «q» siguen siendo los de mpv)
        listOf("SPACE", "LEFT", "RIGHT", "f", "q").forEach { assertNull(it, que(it)) }
    }

    @Test fun losIdiomasComoLosEntiendeMpv() {
        assertEquals(listOf("spa", "es"), Mpv.idiomas("spa"))
        assertEquals(listOf("eng", "en"), Mpv.idiomas("eng"))
        assertEquals(listOf("cat", "ca"), Mpv.idiomas("cat"))
        assertEquals(listOf("spa", "es"), Mpv.idiomas("es"))
        assertEquals(emptyList<String>(), Mpv.idiomas(""))
    }

    @Test fun lasCabecerasDelCanalNoDejanColarOtra() {
        assertEquals(listOf("Referer: http://ejemplo.test/"), Mpv.cabeceras(linkedMapOf("User-Agent" to "Reproductor/1.0", "Referer" to "http://ejemplo.test/")))
        assertEquals("el user-agent va aparte", "Reproductor/1.0", Mpv.agente(mapOf("user-agent" to "Reproductor/1.0"), "Cobre/1"))
        assertEquals("Cobre/1", Mpv.agente(emptyMap(), "Cobre/1"))
        assertEquals("con un salto de línea, fuera", emptyList<String>(), Mpv.cabeceras(mapOf("X" to "a\r\nY: b")))
        assertEquals(emptyList<String>(), Mpv.cabeceras(mapOf("X: Y" to "a")))
        assertEquals("Cobre/1", Mpv.agente(mapOf("User-Agent" to "a\nb"), "Cobre/1"))
    }

    @Test fun loQueSeMandaYLoQueContestaMpv() {
        assertEquals("""{"command":["loadfile","http://tv/live/u/p/1.ts","replace"],"request_id":7}""", Mpv.linea(7, "loadfile", "http://tv/live/u/p/1.ts", "replace"))
        val r = MensajeMpv.lee("""{"data":30.023000,"request_id":4,"error":"success"}""") as MensajeMpv.Respuesta
        assertEquals(4L, r.id); assertTrue(r.ok); assertEquals(30.023, r.datos as Double, 0.0001)
        val fin = MensajeMpv.lee("""{"event":"end-file","reason":"eof","playlist_entry_id":3}""") as MensajeMpv.Suceso
        assertEquals("end-file", fin.nombre); assertEquals("eof", fin.razon); assertEquals(3L, fin.entrada)
        // Lo que pulsas en la ventana de mpv (con las teclas de Cobre) llega así (lo dice mpv 0.37, probado de verdad en MpvDeVerdadTest)
        val tecla = MensajeMpv.lee("""{"event":"client-message","args":["cobre","tecla","arriba"]}""") as MensajeMpv.Suceso
        assertEquals("client-message", tecla.nombre); assertEquals(listOf("cobre", "tecla", "arriba"), tecla.palabras)
        assertNull(MensajeMpv.lee("no es json"))
    }

    @Test fun dondeSeBuscaMpvYComoSePone() {
        val mac = Mpv.candidatos(Sitio.Sistema.MAC, "/usr/bin:/bin", File("/Users/g")).map { it.path }
        assertTrue("el de Homebrew, aunque no esté en el PATH de una app del Mac", "/opt/homebrew/bin/mpv" in mac)
        assertEquals("primero el del PATH", "/usr/bin/mpv", mac.first())
        assertTrue("/usr/bin/mpv" in Mpv.candidatos(Sitio.Sistema.LINUX, "", File("/home/g")).map { it.path })
        assertTrue(Mpv.comoInstalar(Sitio.Sistema.LINUX).contains("sudo pacman -S mpv"))
        assertTrue(Mpv.comoInstalar(Sitio.Sistema.MAC).contains("brew install mpv"))
    }

    @Test fun elTituloDeUnEpisodioSinLaSerieDelante() {
        assertEquals("El camino real", tituloEpisodio(Episodio(1, 1, 2, "Juego de tronos - S01E02 - El camino real"), "Juego de tronos"))
        assertEquals("Episodio 3", tituloEpisodio(Episodio(1, 1, 3, "S01E03"), "Juego de tronos"))
    }

    @Test fun lasFechasYLasCifras() {
        assertEquals("4.010", Formato.cifra(4010)); assertEquals("150.000", Formato.cifra(150_000))
        assertEquals("1 h 52 min", Formato.duracion(112 * 60_000L)); assertEquals("45 min", Formato.duracion(45 * 60_000L))
        assertEquals("1:02:03", Formato.tiempo(3_723_000L)); assertEquals("2:05", Formato.tiempo(125_000L))
    }
}
