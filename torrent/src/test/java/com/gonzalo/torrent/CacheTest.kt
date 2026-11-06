package com.gonzalo.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun torrent(carpeta: File, nombre: String, bytes: Int, hace: Long): File {
        val d = File(carpeta, nombre)
        File(d, "sub").mkdirs()
        File(d, "sub/video.mkv").writeBytes(ByteArray(bytes))
        File(d, ".reanudar").writeBytes(ByteArray(10))
        d.setLastModified(System.currentTimeMillis() - hace)
        return d
    }

    @Test fun borraLosMasViejosHastaQueQuepa() {
        val c = tmp.newFolder("cache")
        torrent(c, "viejo", 1000, 300_000)
        torrent(c, "medio", 1000, 200_000)
        torrent(c, "nuevo", 1000, 100_000)
        assertEquals(1010L, Cache.ocupa(File(c, "viejo")))
        // Caben 2100: sobra uno (el más viejo)
        assertEquals(listOf("viejo"), Cache.libera(c, 2100, emptySet()))
        assertFalse(File(c, "viejo").exists())
        assertTrue(File(c, "medio").exists() && File(c, "nuevo").exists())
        // Ya cabe: no se borra nada
        assertEquals(emptyList<String>(), Cache.libera(c, 2100, emptySet()))
    }

    @Test fun loQueSeUsaNoSeBorraNunca() {
        val c = tmp.newFolder("cache")
        torrent(c, "viejo", 1000, 300_000)
        torrent(c, "nuevo", 1000, 100_000)
        // El viejo se está viendo: aunque no quepa, se borra el otro (y nada más)
        assertEquals(listOf("nuevo"), Cache.libera(c, 0, setOf("viejo")))
        assertTrue(File(c, "viejo").exists())
        assertEquals(emptyList<String>(), Cache.libera(c, 0, setOf("viejo")))
    }

    @Test fun loQueVaACrecerCuenta() {
        val c = tmp.newFolder("cache")
        torrent(c, "a", 1000, 300_000)
        torrent(c, "b", 1000, 200_000)
        // Con 2020 ocupados y 500 por llegar, en 2100 no cabe: fuera el más viejo
        assertEquals(listOf("a"), Cache.libera(c, 2100, setOf("b"), reserva = 500))
    }

    @Test fun haceFaltaSitioEnElDisco() {
        val c = tmp.newFolder("cache")
        torrent(c, "a", 1000, 300_000)
        torrent(c, "b", 1000, 200_000)
        var libre = 100L
        // Cabe en la caché, pero el disco está lleno: se borra hasta tener lo que hace falta (aquí no llega nunca: se borra todo)
        assertEquals(listOf("a", "b"), Cache.libera(c, Long.MAX_VALUE, emptySet(), necesarios = 1500, libre = { libre }))
        libre = 5000
        torrent(c, "c", 1000, 100_000)
        assertEquals(emptyList<String>(), Cache.libera(c, Long.MAX_VALUE, emptySet(), necesarios = 1500, libre = { libre }))
    }

    @Test fun carpetaVaciaONoExiste() {
        assertEquals(emptyList<String>(), Cache.libera(File(tmp.root, "no-existe"), 0, emptySet()))
        assertTrue(Cache.borra(File(tmp.root, "no-existe")))
    }
}
