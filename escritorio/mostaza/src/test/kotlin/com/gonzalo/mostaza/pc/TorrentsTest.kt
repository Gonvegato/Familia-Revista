package com.gonzalo.mostaza.pc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * El motor de torrents como lo pone la app: su biblioteca nativa sale del .jar de este sistema a la carpeta de Mostaza y el motor arranca
 * (sin decirle nada a libtorrent4j por fuera, como en el paquete). Solo en Linux x86-64 y en el Mac con Apple Silicon (las que trae).
 */
class TorrentsTest {
    private val dir = kotlin.io.path.createTempDirectory("mostaza-torrents").toFile()

    @After fun fin() { Torrents.paraTodo(); Local.dirPruebas = null; Local.olvidaParaPruebas(); dir.deleteRecursively() }

    @Test fun laBibliotecaDeEsteSistemaSaleDelJarYElMotorArranca() {
        val os = System.getProperty("os.name").lowercase(); val arq = System.getProperty("os.arch").lowercase()
        assumeTrue("Solo en Linux x86-64 o en el Mac con Apple Silicon", (os.contains("linux") && arq in setOf("amd64", "x86_64")) || (os.contains("mac") && arq in setOf("aarch64", "arm64")))
        assumeTrue("(otra prueba ya le ha dicho a libtorrent4j dónde está)", System.getProperty("libtorrent4j.jni.path") == null)
        Local.dirPruebas = dir; Local.olvidaParaPruebas()
        Torrents.apagado = false
        try {
            assertTrue("hay motor de torrents", Torrents.disponible)
            val nativa = File(dir, "nativas").listFiles().orEmpty().map { it.name }
            assertTrue(nativa.toString(), nativa.any { it == "libtorrent4j.so" || it == "libtorrent4j.dylib" })
            // Lo de la cuenta, en lo que entiende el motor (como en el móvil): sin compartir, la subida en lo mínimo; al parar, se borra
            AjustesCuenta.enMemoria = mutableMapOf("mostaza_torrent" to mutableMapOf<String, Any?>("compartir" to false, "subida" to 500L))
            assertEquals(0, Torrents.ajustes().subidaMaxKBs)
            AjustesCuenta.enMemoria = mutableMapOf("mostaza_torrent" to mutableMapOf<String, Any?>("subida" to 0L))
            assertEquals("sin límite (el motor nunca sube sin límite de verdad)", Torrents.SIN_LIMITE_KBS, Torrents.ajustes().subidaMaxKBs)
            assertTrue(!Torrents.ajustes().seguirCompartiendoAlParar)
        } finally { AjustesCuenta.enMemoria = null; Torrents.apagado = true }
    }
}
