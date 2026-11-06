package com.gonzalo.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuentesTest {
    @Test fun trackersYDhtComoLosMandaStremio() {
        val f = LeeFuentes.lee(listOf(
            "tracker:udp://tracker.opentrackr.org:1337/announce",
            "tracker:http://tracker.ejemplo.org:80/announce",
            "dht:0123456789abcdef0123456789abcdef01234567",
        ))
        // (público: los suyos primero y luego los públicos de siempre, sin repetir)
        assertEquals((listOf("udp://tracker.opentrackr.org:1337/announce", "http://tracker.ejemplo.org:80/announce") + LeeFuentes.PUBLICOS).distinct(), f.trackers)
        assertTrue(f.dht)
    }

    @Test fun soloTrackersSinDhtEsSinDht() {
        // Los addons de trackers privados no mandan dht: (no se debe anunciar en la DHT)
        val f = LeeFuentes.lee(listOf("tracker:https://privado.ejemplo.org/announce?passkey=abc"))
        assertEquals(listOf("https://privado.ejemplo.org/announce?passkey=abc"), f.trackers)
        assertFalse(f.dht)
    }

    @Test fun soloDht() {
        val f = LeeFuentes.lee(listOf("dht:0123456789abcdef0123456789abcdef01234567"))
        assertEquals(LeeFuentes.PUBLICOS, f.trackers)
        assertTrue(f.dht)
    }

    @Test fun sinFuentesComoStremio() {
        val f = LeeFuentes.lee(emptyList())
        assertTrue(f.dht)
        assertEquals(LeeFuentes.PUBLICOS, f.trackers)
    }

    @Test fun loQueNoSeEntiendeSeIgnoraYSinNadaAprovechableComoSinFuentes() {
        val f = LeeFuentes.lee(listOf("tracker:", "tracker:ftp://raro.org/x", "otra:cosa", "  ", "tracker:udp://bien.org:6969"))
        assertEquals(listOf("udp://bien.org:6969"), f.trackers)
        assertFalse(f.dht)
        assertEquals(LeeFuentes.lee(emptyList()), LeeFuentes.lee(listOf("basura", "tracker:nada")))
    }

    @Test fun trackersSinPrefijoYRepetidos() {
        val f = LeeFuentes.lee(listOf("udp://a.org:1/announce", "tracker:udp://a.org:1/announce", "TRACKER:UDP://b.org:2", "DHT:x"))
        assertEquals(listOf("udp://a.org:1/announce", "UDP://b.org:2") + LeeFuentes.PUBLICOS, f.trackers)
        assertTrue(f.dht)
    }

    @Test fun magnet() {
        val m = LeeFuentes.magnet("0123456789abcdef0123456789abcdef01234567", Fuentes(listOf("udp://a.org:1/announce"), true), "Película (2024) 1080p")
        assertEquals("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Pel%C3%ADcula+%282024%29+1080p&tr=udp%3A%2F%2Fa.org%3A1%2Fannounce", m)
        assertEquals("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", LeeFuentes.magnet("0123456789abcdef0123456789abcdef01234567", Fuentes(emptyList(), true), " "))
        // Con el fileIdx del addon, solo ese archivo desde el principio (BEP 53)
        assertEquals("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&so=3", LeeFuentes.magnet("0123456789abcdef0123456789abcdef01234567", Fuentes(emptyList(), true), null, 3))
        assertEquals("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", LeeFuentes.magnet("0123456789abcdef0123456789abcdef01234567", Fuentes(emptyList(), true), null, -1))
    }

    @Test fun infoHash() {
        assertEquals("0123456789abcdef0123456789abcdef01234567", InfoHash.normaliza(" 0123456789ABCDEF0123456789abcdef01234567 "))
        assertEquals("0123456789abcdef0123456789abcdef01234567", InfoHash.normaliza("urn:btih:0123456789abcdef0123456789abcdef01234567"))
        // El mismo en base32 (los magnet viejos)
        assertEquals("0123456789abcdef0123456789abcdef01234567", InfoHash.normaliza("AERUKZ4JVPG66AJDIVTYTK6N54ASGRLH"))
        assertNull(InfoHash.normaliza("0123"))
        assertNull(InfoHash.normaliza("zz23456789abcdef0123456789abcdef01234567"))
        assertNull(InfoHash.normaliza(""))
    }
}
