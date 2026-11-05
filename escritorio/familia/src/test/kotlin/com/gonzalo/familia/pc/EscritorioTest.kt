package com.gonzalo.familia.pc

import com.gonzalo.familia.nucleo.Emulador
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Lo común de las versiones de escritorio: el llavero, lo cifrado, entrar una vez para las cuatro apps (con la cuenta de verdad: el
 * emulador de Firebase con las reglas del repositorio), las portadas con el mismo hash y el actualizador.
 */
class EscritorioTest {
    @get:Rule val tmp = TemporaryFolder()

    @After fun fuera() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null; LaFamilia.dirPruebas = null; LaFamilia.llaveroPruebas = null }

    // ---------------------------------------------------------------- llavero y disco cifrado

    @Test fun elLlaveroDeRepuestoEsUnArchivoQueSoloLeeTuUsuario() {
        val f = File(tmp.root, "llavero.json")
        val l = LlaveroArchivo(f)
        assertNull(l.lee("sesion"))
        assertTrue(l.guarda("sesion", "{\"uid\":\"abc\", \"ñ\":\"á é\"}"))
        assertEquals("{\"uid\":\"abc\", \"ñ\":\"á é\"}", l.lee("sesion"))
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(f.toPath()))
        // lo guardado no se lee a simple vista
        assertFalse(f.readText().contains("abc"))
        LlaveroArchivo(f).let { otra -> assertEquals("{\"uid\":\"abc\", \"ñ\":\"á é\"}", otra.lee("sesion")) }
        l.borra("sesion")
        assertNull(l.lee("sesion"))
    }

    /** El Llavero del Mac de verdad (solo en un Mac: en GitHub, en el Mac de las compilaciones). */
    @Test fun elLlaveroDelMacGuardaLeeYBorra() {
        org.junit.Assume.assumeTrue("Solo en el Mac", Sitio.sistema == Sitio.Sistema.MAC)
        val l = LlaveroMac("FamiliaPruebas")
        assertTrue(l.funciona())
        assertTrue(l.guarda("sesion", "{\"uid\":\"abc\",\"ñ\":\"á\"}"))
        assertEquals("{\"uid\":\"abc\",\"ñ\":\"á\"}", l.lee("sesion"))
        assertTrue(l.guarda("sesion", "otra"))
        assertEquals("otra", l.lee("sesion"))
        l.borra("sesion")
        assertNull(l.lee("sesion"))
    }

    @Test fun loDelicadoVaCifradoYSinElLlaveroNoSeLee() {
        val dir = File(tmp.root, "seguro")
        val llavero = LlaveroMemoria()
        val d = DiscoSeguro(dir, llavero)
        d.escribeTexto("sesion.json", "refresco-secreto")
        assertEquals("refresco-secreto", d.leeTexto("sesion.json"))
        assertFalse(File(dir, "sesion.json").readBytes().toString(Charsets.ISO_8859_1).contains("refresco-secreto"))
        // Otra app de este ordenador (el mismo llavero) lo lee; con otro llavero, no
        assertEquals("refresco-secreto", DiscoSeguro(dir, llavero).leeTexto("sesion.json"))
        assertNull(DiscoSeguro(dir, LlaveroMemoria()).leeTexto("sesion.json"))
    }

    // ---------------------------------------------------------------- entrar una vez

    private fun app(nombre: String, llavero: Llavero): com.gonzalo.familia.nucleo.Familia {
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg
        LaFamilia.dirPruebas = File(tmp.root, nombre)
        LaFamilia.llaveroPruebas = llavero
        return LaFamilia.inicia(nombre).also { it.automatico = false }
    }

    @Test fun entrasEnUnaYLasOtrasTresEntranSolasSinLaContrasena() {
        Emulador.limpia()
        val uid = Emulador.cuenta("gonzalo")
        val llavero = LlaveroMemoria()
        val azabache = app("azabache", llavero)
        assertFalse(azabache.sesion.dentro)
        LaFamilia.entrar("Gonzalo", "secreto1")
        assertEquals(uid, azabache.sesion.uid)
        // En el llavero queda la sesión, nunca la contraseña
        val guardado = llavero.lee("sesion")!!
        assertFalse(guardado.contains("secreto1"))
        azabache.coleccion("juegos").pon("j_1", "t" to "Hollow Knight", "estado" to "en_curso")
        assertTrue(azabache.sincroniza().ok)

        // Marfil se abre después en el mismo ordenador: entra sola y ve la cuenta
        val marfil = app("marfil", llavero)
        LaFamilia.pideSesion(esperar = true)
        assertTrue(marfil.sesion.dentro)
        assertEquals(uid, marfil.sesion.uid)
        assertEquals("gonzalo", marfil.sesion.usuario)
        marfil.coleccion("juegos")
        assertTrue(marfil.sincroniza().ok)
        assertEquals("Hollow Knight", marfil.coleccion("juegos").nodo("j_1")!!.texto("t"))
    }

    @Test fun unaSesionQueLaCuentaYaNoAceptaSeOlvida() {
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        val llavero = LlaveroMemoria()
        llavero.guarda("sesion", """{"uid":"nadie","usuario":"gonzalo","refresco":"no-vale"}""")
        val hiedra = app("hiedra", llavero)
        LaFamilia.pideSesion(esperar = true)
        assertFalse(hiedra.sesion.dentro)
        assertNull(llavero.lee("sesion"))
    }

    @Test fun salirEnUnaNoSacaALasDemasPeroNoLasDejaEntrarSolas() {
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        val llavero = LlaveroMemoria()
        app("azabache", llavero); LaFamilia.entrar("gonzalo", "secreto1")
        val cobalto = app("cobalto", llavero); LaFamilia.pideSesion(esperar = true)
        assertTrue(cobalto.sesion.dentro)
        LaFamilia.salir()
        assertFalse(cobalto.sesion.dentro)
        assertNull(llavero.lee("sesion"))
        // Azabache (con su propia sesión guardada) sigue dentro
        val otraVez = app("azabache", llavero)
        assertTrue(otraVez.sesion.dentro)
    }

    // ---------------------------------------------------------------- portadas

    @Test fun unaPortadaSeReduceComoEnElMovilYSuHashEsElDeSusBytes() {
        val img = java.awt.image.BufferedImage(1200, 1800, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply { color = java.awt.Color(200, 55, 45); fillRect(0, 0, 1200, 1800); dispose() }
        val png = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(img, "png", it) }.toByteArray()
        assertTrue(Portadas.esImagen(png))
        assertFalse(Portadas.esImagen("<html>error</html>".toByteArray()))
        val r = Portadas.reducir(png)!!
        assertEquals(0xFF, r[0].toInt() and 0xFF); assertEquals(0xD8, r[1].toInt() and 0xFF)    // JPEG
        val leida = javax.imageio.ImageIO.read(r.inputStream())
        assertEquals(600, leida.width); assertEquals(900, leida.height)
        // El hash: los 40 primeros hexadecimales del SHA-256 (el de familia_img en todas las apps)
        val h = java.security.MessageDigest.getInstance("SHA-256").digest(r).joinToString("") { "%02x".format(it) }.take(40)
        val fam = com.gonzalo.familia.nucleo.Familia(Emulador.cfg, com.gonzalo.familia.nucleo.RedJava(), com.gonzalo.familia.nucleo.DiscoMemoria())
        assertEquals(h, fam.imagenes.hash(r))
    }

    // ---------------------------------------------------------------- actualizador

    @Test fun elActualizadorSoloCogeLaVersionDeSuAppYDeSuSistema() {
        val mac = Actualizador.patron("azabache", Sitio.Sistema.MAC)
        val linux = Actualizador.patron("azabache", Sitio.Sistema.LINUX)
        assertEquals("1.2.0", mac.find("Azabache-1.2.0-mac.dmg")?.groupValues?.get(1))
        assertNull(mac.find("Azabache-1.2.0-linux.deb"))
        assertNull(mac.find("Azabache-1.2.0.apk"))
        assertNull(mac.find("Marfil-1.2.0-mac.dmg"))
        assertEquals("1.10.3", linux.find("Azabache-1.10.3-linux.deb")?.groupValues?.get(1))
        // Y los de Android nunca cogen los de escritorio
        val android = Regex("^Azabache-(\\d+(?:\\.\\d+)*)\\.apk$")
        assertFalse(android.matches("Azabache-1.2.0-mac.dmg")); assertFalse(android.matches("Azabache-1.2.0-linux.deb"))
        assertTrue(Actualizador.mayor("1.10.0", "1.9.9")); assertFalse(Actualizador.mayor("1.0", "1.0.0")); assertTrue(Actualizador.mayor("2", "1.99"))
    }
}
