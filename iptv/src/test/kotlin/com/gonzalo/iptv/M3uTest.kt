package com.gonzalo.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las listas M3U de IPTV, con la forma de las de verdad (`resources/lista.m3u`: lo que dicen Kodi —PVR IPTV Simple— y VLC). */
class M3uTest {
    private val leida = M3u.leeTexto(recursoTexto("lista.m3u"))
    private val cab = leida.first
    private val canales = leida.second
    private fun c(n: String) = canales.first { it.nombre == n }

    @Test fun laCabecera() {
        assertEquals(listOf("http://epg.ejemplo.test/guia.xml.gz", "http://epg2.ejemplo.test/otra.xml"), cab.guias)
        assertEquals("append", cab.catchup); assertEquals(3, cab.catchupDias)
    }

    @Test fun losAtributosDeCadaCanal() {
        // (la entrada sin dirección no cuenta; la marca de orden de bytes del principio, tampoco)
        assertEquals(11, canales.size)
        val la1 = canales.first()
        assertEquals("La 1 HD", la1.nombre); assertEquals("La1.es", la1.tvgId); assertEquals("La 1 HD", la1.tvgNombre)
        assertEquals("http://logos.ejemplo.test/la1.png", la1.logo); assertEquals("Nacionales", la1.grupo); assertEquals(1, la1.numero)
        assertEquals("http://tv.ejemplo.test:8080/live/pepe/s3cr3t0/101.ts", la1.url)
        assertEquals("el catchup de la cabecera vale para todos", Catchup("append", 3), la1.catchup)
        // Un nombre con comas
        assertEquals("La 2, cultura y más", canales[1].nombre)
        // Atributos sin comillas
        val cuatro = c("Cuatro")
        assertEquals("Cuatro.es", cuatro.tvgId); assertEquals("http://logos.ejemplo.test/cuatro.png", cuatro.logo); assertEquals("Nacionales", cuatro.grupo)
        assertTrue(c("Radio Nacional").radio)
        assertEquals("rtmp://rtmp.ejemplo.test/vivo/sin", c("Sin atributos").url)
    }

    @Test fun elCatchupPropio() {
        val a3 = c("ES: Antena 3 FHD")
        assertEquals(Catchup("default", 7, "http://tv.ejemplo.test/a3/{utc}-{duration}.m3u8"), a3.catchup)
    }

    @Test fun lasCabecerasDeCadaCanal() {
        // #EXTVLCOPT
        assertEquals(mapOf("User-Agent" to "Reproductor/1.0", "Referer" to "http://ejemplo.test/"), c("ES: Antena 3 FHD").cabeceras)
        // Detrás de la barra, con %XX (y la dirección, sin ellas)
        val sur = c("Canal Sur")
        assertEquals("http://cdn.ejemplo.test/canalsur.m3u8", sur.url)
        assertEquals(mapOf("User-Agent" to "Mozilla/5.0 (Linux)", "Referer" to "http://canalsur.test/"), sur.cabeceras)
        // Las de una entrada no pasan a la siguiente
        assertTrue(c("TV3").cabeceras.isEmpty())
        // #KODIPROP: las cabeceras, y que lleva DRM
        val mplus = c("M+ Estrenos")
        assertEquals(mapOf("User-Agent" to "Kodi", "Referer" to "https://ejemplo.test/"), mplus.cabeceras)
        assertTrue(mplus.conDrm); assertFalse(c("TV3").conDrm)
        assertEquals("mpd", mplus.propiedades["inputstream.adaptive.manifest_type"])
    }

    @Test fun elGrupoDeExtgrpComoEnKodi() {
        // #EXTGRP vale para lo que sigue hasta otro (también uno vacío); group-title manda sobre él
        assertEquals("Autonómicas", c("Canal Sur").grupo)
        assertEquals("Autonómicas", c("TV3").grupo)
        assertEquals("Nacionales", c("Cuatro").grupo)
        assertEquals("", c("Sin atributos").grupo)
    }

    @Test fun lasClavesNoCambianYNoSeRepiten() {
        val la1 = canales.first(); val copia = canales.first { it.grupo == "Nacionales (copia)" }
        assertEquals("c" + la1.clave.drop(1), la1.clave); assertEquals(17, la1.clave.length)
        assertEquals("el mismo canal en otro grupo: otra clave", la1.clave + "~2", copia.clave)
        assertEquals(canales.size, canales.map { it.clave }.toSet().size)
        // Si la lista se vuelve a bajar con otras direcciones (otro token), las claves no cambian
        val otra = M3u.leeTexto(recursoTexto("lista.m3u").replace("s3cr3t0", "otroToken")).second
        assertEquals(canales.map { it.clave }, otra.map { it.clave })
        assertEquals(M3u.clave("La1.es", "La 1 HD"), la1.clave)
        assertNotEquals(M3u.clave("La1.es", "La 1"), la1.clave)
    }

    @Test fun losSecretosNoSalenEnLosTextos() {
        assertFalse(canales.first().toString().contains("s3cr3t0"))
        assertFalse(cab.toString().contains("epg.ejemplo"))
    }

    @Test fun finesDeLineaDeWindowsYCosasRaras() {
        val t = "#EXTM3U\r\n#EXTINF:-1 tvg-id=\"a\" group-title=\"G, con coma\",Uno\r\nhttp://x/1\r\n\r\n#EXTINF:0,\r\nhttp://x/dos.ts?token=abc\r\nesto no es una dirección\r\n"
        val l = M3u.leeTexto(t).second
        assertEquals(2, l.size)
        assertEquals("G, con coma", l[0].grupo); assertEquals("Uno", l[0].nombre); assertEquals("http://x/1", l[0].url)
        assertEquals("sin nombre: el del final de la dirección", "dos.ts", l[1].nombre)
        assertNull(l[1].catchup)
        // Una dirección suelta sin #EXTINF también es un canal
        assertEquals(1, M3u.leeTexto("http://solo/uno.m3u8\n").second.size)
    }

    @Test fun unaListaEnormeSeLeeATrozos() {
        // 150 000 canales (más de 30 MB de texto) que no están en ningún sitio: se inventan según se leen
        val n = 150_000
        val r = TextoQueSeInventa(sequenceOf("#EXTM3U") + (1..n).asSequence().flatMap { i ->
            sequenceOf("#EXTINF:-1 tvg-id=\"canal$i.es\" tvg-name=\"Canal $i\" tvg-logo=\"http://logos.ejemplo.test/$i.png\" group-title=\"Grupo ${i % 300}\",Canal número $i con un nombre largo para que ocupe",
                "http://tv.ejemplo.test:8080/live/usuario/contrasena/$i.ts")
        })
        var cuantos = 0; var ultimo: Canal? = null
        val t0 = System.nanoTime()
        val (_, k) = M3u.lee(r) { cuantos++; ultimo = it }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(n, k); assertEquals(n, cuantos)
        assertEquals("Canal número $n con un nombre largo para que ocupe", ultimo!!.nombre)
        assertTrue("más de 30 MB leídos", r.leidos > 30_000_000)
        assertTrue("rápido: ${ms} ms", ms < 30_000)
    }

    @Test fun atributosSueltos() {
        assertEquals(mapOf("tvg-id" to "a b", "x" to "1", "y" to "c,d"), M3u.atributos("""-1 tvg-id="a b" x=1 y='c,d'"""))
        assertEquals(14, M3u.comaDelNombre("""-1 a="1,2,3" b,Nombre""").let { it })
    }
}
