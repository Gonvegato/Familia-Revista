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
 * Lo común de las versiones de escritorio: el llavero, lo cifrado, entrar una vez para todas las apps (con la cuenta de verdad: el
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
        val linux = Actualizador.patron("azabache", Sitio.Sistema.LINUX, deb = false)
        val conDeb = Actualizador.patron("azabache", Sitio.Sistema.LINUX, deb = true)
        assertEquals("1.2.0", mac.find("Azabache-1.2.0-mac.dmg")?.groupValues?.get(1))
        assertNull(mac.find("Azabache-1.2.0-linux.deb"))
        assertNull(mac.find("Azabache-1.2.0.apk"))
        assertNull(mac.find("Marfil-1.2.0-mac.dmg"))
        // En Linux, el .tar.gz (se pone solo con su instalar.sh); el .deb solo si se instaló con él
        assertEquals("1.10.3", linux.find("Azabache-1.10.3-linux.tar.gz")?.groupValues?.get(1))
        assertNull(linux.find("Azabache-1.10.3-linux.deb"))
        assertEquals("1.10.3", conDeb.find("Azabache-1.10.3-linux.deb")?.groupValues?.get(1))
        assertNull(conDeb.find("Azabache-1.10.3-linux.tar.gz"))
        // Y los de Android nunca cogen los de escritorio
        val android = Regex("^Azabache-(\\d+(?:\\.\\d+)*)\\.apk$")
        assertFalse(android.matches("Azabache-1.2.0-mac.dmg")); assertFalse(android.matches("Azabache-1.2.0-linux.deb"))
        assertTrue(Actualizador.mayor("1.10.0", "1.9.9")); assertFalse(Actualizador.mayor("1.0", "1.0.0")); assertTrue(Actualizador.mayor("2", "1.99"))
    }

    @Test fun laAppSabeDondeEstaInstaladaYSiSeActualizaSola() {
        assertEquals(File("/home/yo/.local/share/familia-apps/Hiedra"), Actualizador.instalada("/home/yo/.local/share/familia-apps/Hiedra/lib/app/resources"))
        assertEquals(File("/Applications/Hiedra.app"), Actualizador.instalada("/Applications/Hiedra.app/Contents/app/resources"))
        assertNull("en desarrollo no hay instalación", Actualizador.instalada(null))
        assertTrue(Actualizador.conDeb(File("/opt/hiedra"), Sitio.Sistema.LINUX))
        assertFalse(Actualizador.conDeb(File("/home/yo/.local/share/familia-apps/Hiedra"), Sitio.Sistema.LINUX))
        val mia = File(tmp.root, "apps/Hiedra").also { it.mkdirs() }
        assertTrue(Actualizador.sePoneSola(mia))
        assertFalse(Actualizador.sePoneSola(File(tmp.root, "no-existe")))
    }

    /** Linux de verdad: la versión nueva (un .tar.gz con su instalar.sh, como el de GitHub) se pone encima de la vieja, y el menú queda al día. */
    @Test fun enLinuxLaVersionNuevaSePoneEncimaConSuInstalar() {
        org.junit.Assume.assumeTrue("Solo en Linux", Sitio.sistema == Sitio.Sistema.LINUX)
        fun app(dir: File, que: String) { val b = File(dir, "Hiedra/bin").also { it.mkdirs() }; File(b, "Hiedra").apply { writeText("#!/bin/sh\necho $que\n"); setExecutable(true) }; File(dir, "Hiedra/lib").mkdirs(); File(dir, "Hiedra/lib/Hiedra.png").writeText("png") }
        val donde = File(tmp.root, "datos/familia-apps/Hiedra")
        app(donde.parentFile, "vieja")
        // El paquete nuevo: la app y su instalar.sh hecho con la plantilla, como hace herramientas/escritorio/empaqueta-linux.sh
        val paquete = File(tmp.root, "paquete").also { it.mkdirs() }
        app(paquete, "nueva")
        val plantilla = File(System.getProperty("user.dir"), "../../herramientas/escritorio/instalar-linux.sh").readText()
        File(paquete, "instalar.sh").writeText(plantilla.replace("__APP__", "Hiedra").replace("__ID__", "hiedra").replace("__GENERICO__", "Juegos")
            .replace("__DESCRIPCION__", "Tus juegos").replace("__CATEGORIAS__", "Game;").replace("__WMCLASS__", "com-gonzalo-hiedra-pc-MainKt").replace("__AL_INICIAR__", " --sala"))
        val targz = File(tmp.root, "Hiedra-9.9.9-linux.tar.gz")
        assertEquals(0, ProcessBuilder("tar", "-C", paquete.absolutePath, "-czf", targz.absolutePath, "Hiedra", "instalar.sh").start().waitFor())
        val carpeta = Actualizador.descomprime(targz, File(tmp.root, "v9.9.9"))
        val entorno = mapOf("XDG_DATA_HOME" to File(tmp.root, "datos").absolutePath, "XDG_CONFIG_HOME" to File(tmp.root, "config").absolutePath)
        // Ya se abría al entrar en la sesión (con la entrada de antes): al ponerse la nueva, esa entrada también se pone al día
        val inicio = File(tmp.root, "config/autostart/familia-hiedra.desktop").apply { parentFile.mkdirs(); writeText("[Desktop Entry]\nExec=vieja\n") }
        val programa = Actualizador.ponLinux(carpeta, "hiedra", donde, entorno)
        assertEquals(File(donde, "bin/Hiedra"), programa)
        assertTrue(File(donde, "bin/Hiedra").readText().contains("nueva"))
        assertFalse("sin restos", File(donde.parentFile, "Hiedra.vieja").exists() || File(donde.parentFile, "Hiedra.nueva").exists())
        val menu = File(tmp.root, "datos/applications/familia-hiedra.desktop").readText()
        assertTrue(menu.contains("Exec=\"${donde.absolutePath}/bin/Hiedra\"")); assertTrue(menu.contains("Icon=${donde.absolutePath}/lib/Hiedra.png"))
        assertTrue(menu.contains("StartupWMClass=com-gonzalo-hiedra-pc-MainKt"))
        assertFalse("desde el menú, en su ventana", menu.contains("--sala"))
        assertTrue("al entrar en la sesión, ya en la sala", inicio.readText().contains("Exec=\"${donde.absolutePath}/bin/Hiedra\" --sala"))
    }

    /** El Mac de verdad (en GitHub): la app nueva sale de un .dmg montado sin enseñarlo y queda encima de la de ahora. */
    @Test fun enElMacLaVersionNuevaSaleDelDmgYSePoneEncima() {
        org.junit.Assume.assumeTrue("Solo en el Mac", Sitio.sistema == Sitio.Sistema.MAC)
        fun app(dir: File, que: String) { val c = File(dir, "Prueba.app/Contents").also { it.mkdirs() }; File(c, "que.txt").writeText(que) }
        val apps = File(tmp.root, "Applications").also { it.mkdirs() }
        app(apps, "vieja")
        val fuente = File(tmp.root, "dmg").also { it.mkdirs() }
        app(fuente, "nueva")
        val dmg = File(tmp.root, "actualizacion/Prueba-9.9.9-mac.dmg").also { it.parentFile.mkdirs() }
        assertEquals(0, ProcessBuilder("hdiutil", "create", "-quiet", "-volname", "Prueba", "-srcfolder", fuente.absolutePath, "-ov", "-format", "UDZO", dmg.absolutePath).inheritIO().start().waitFor())
        val puesta = Actualizador.ponMac(dmg, File(apps, "Prueba.app"))
        assertEquals("nueva", File(puesta, "Contents/que.txt").readText())
        assertFalse(File(apps, "Prueba.app.vieja").exists())
    }

    @Test fun unaSolaCopiaDeCadaAppYLaSegundaTraeAlFrenteLaPrimera() {
        val dir = File(tmp.root, "cobalto")
        val llamada = java.util.concurrent.CountDownLatch(1)
        assertTrue(Instancia.unica(dir))
        val pedido = java.util.concurrent.atomic.AtomicReference<List<String>>()
        Instancia.alFrente = { pedido.set(it); llamada.countDown() }
        assertFalse("ya hay una abierta", Instancia.unica(dir, listOf("--sala")))
        assertTrue("la abierta sale al frente", llamada.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals("y le llega lo que se le pasó a la segunda", listOf("--sala"), pedido.get())
        Instancia.suelta()
        assertTrue("al cerrarse, se puede abrir otra", Instancia.unica(dir))
        Instancia.suelta()
    }

    @Test fun lasHermanasSeEncuentranAlLadoDeEstaApp() {
        // Linux: instaladas juntas (familia-apps/<App>/bin/<App>)
        val apps = File(tmp.root, "familia-apps")
        fun linux(a: String) = File(apps, "$a/bin/$a").apply { parentFile.mkdirs(); writeText("#!/bin/sh\n"); setExecutable(true) }
        val hiedra = File(apps, "Hiedra").also { linux("Hiedra") }
        val cobalto = linux("Cobalto")
        val casa = File(tmp.root, "casa").also { it.mkdirs() }
        assertEquals(cobalto, HermanasPc.donde("cobalto", hiedra, Sitio.Sistema.LINUX, casa))
        assertNull("Marfil no está", HermanasPc.donde("marfil", hiedra, Sitio.Sistema.LINUX, casa))
        // Mac: <App>.app al lado
        val aplicaciones = File(tmp.root, "Applications").also { File(it, "Hiedra.app/Contents").mkdirs(); File(it, "Marfil.app/Contents").mkdirs() }
        assertEquals(File(aplicaciones, "Marfil.app"), HermanasPc.donde("marfil", File(aplicaciones, "Hiedra.app"), Sitio.Sistema.MAC, casa))
        assertEquals(listOf("azabache", "cobalto", "hiedra", "marfil", "mostaza", "cobre"), HermanasPc.todas.map { it.first })
    }

    /**
     * De punta a punta, en Linux: hay una versión nueva en el kDrive (uno de mentira, con la misma API), la app la encuentra, la baja,
     * la descomprime, y al cerrar queda puesta encima de la instalada (con su instalar.sh, como la de verdad).
     */
    @Test fun deUnaPuntaAOtraLaVersionNuevaLlegaDelKDriveYSePone() {
        org.junit.Assume.assumeTrue("Solo en Linux", Sitio.sistema == Sitio.Sistema.LINUX)
        val js = File(System.getProperty("user.dir"), "../../herramientas/azabache/fakekdrive.js")
        org.junit.Assume.assumeTrue("falta node o fakekdrive.js", js.isFile && runCatching { ProcessBuilder("node", "--version").start().waitFor() == 0 }.getOrDefault(false))
        val puerto = 8300 + (System.nanoTime() % 400).toInt().let { if (it < 0) -it else it }
        val servidor = ProcessBuilder("node", js.absolutePath, puerto.toString()).redirectErrorStream(true).start()
        val antes = listOf("compose.application.resources.dir", "familia.version", "familia.dir").associateWith { System.getProperty(it) }
        try {
            repeat(50) { try { java.net.URI("http://127.0.0.1:$puerto/").toURL().openConnection().apply { connectTimeout = 300 }.getInputStream().close(); return@repeat } catch (e: java.io.IOException) { if (e.message?.contains("Server returned") == true) return@repeat; Thread.sleep(100) } }
            val kd = KDrive({ "tok" }, { "777" }).also { it.api = "http://127.0.0.1:$puerto" }
            // La instalada (la 1.0.0) y la nueva (9.9.9) en un .tar.gz como el de GitHub, en «Mi carpeta/versiones»
            fun app(dir: File, que: String) { val b = File(dir, "Hiedra/bin").also { it.mkdirs() }; File(b, "Hiedra").apply { writeText("#!/bin/sh\necho $que\n"); setExecutable(true) }; File(dir, "Hiedra/lib/app/resources").mkdirs(); File(dir, "Hiedra/lib/Hiedra.png").writeText("png") }
            val datos = File(tmp.root, "datos")
            val donde = File(datos, "familia-apps/Hiedra")
            app(donde.parentFile, "1.0.0")
            val paquete = File(tmp.root, "paquete").also { it.mkdirs() }
            app(paquete, "9.9.9")
            val plantilla = File(System.getProperty("user.dir"), "../../herramientas/escritorio/instalar-linux.sh").readText()
            File(paquete, "instalar.sh").writeText(plantilla.replace("__APP__", "Hiedra").replace("__ID__", "hiedra").replace("__GENERICO__", "Juegos")
                .replace("__DESCRIPCION__", "Tus juegos").replace("__CATEGORIAS__", "Game;").replace("__WMCLASS__", "com-gonzalo-hiedra-pc-MainKt").replace("__AL_INICIAR__", " --sala"))
            val targz = File(tmp.root, "Hiedra-9.9.9-linux.tar.gz")
            assertEquals(0, ProcessBuilder("tar", "-C", paquete.absolutePath, "-czf", targz.absolutePath, "Hiedra", "instalar.sh").start().waitFor())
            kd.sube("versiones", targz.name, targz.length()) { targz.inputStream() }
            // (y otras que no le tocan: la de Mac, la de Android, una más vieja)
            listOf("Hiedra-9.9.9-mac.dmg", "Hiedra-9.9.9.apk", "Hiedra-0.9.0-linux.tar.gz", "Marfil-9.9.9-linux.tar.gz").forEach { n -> kd.sube("versiones", n, 3) { "xyz".byteInputStream() } }
            System.setProperty("compose.application.resources.dir", File(donde, "lib/app/resources").absolutePath)
            System.setProperty("familia.version", "1.0.0")
            System.setProperty("familia.dir", File(tmp.root, "familia").absolutePath)
            Actualizador.entornoDeInstalar = mapOf("XDG_DATA_HOME" to datos.absolutePath, "XDG_CONFIG_HOME" to File(tmp.root, "config").absolutePath)
            Actualizador.preparaParaPruebas(null)
            assertEquals(donde.absoluteFile, Actualizador.instalada())
            assertTrue(Actualizador.sePoneSola())
            val lista = Actualizador.automatico("hiedra", kd)
            assertNotNull(Actualizador.estado, lista)
            assertEquals("9.9.9", lista!!.version)
            assertTrue("sin tocar la de ahora hasta cerrar", File(donde, "bin/Hiedra").readText().contains("1.0.0"))
            // Al cerrar
            val programa = Actualizador.ponLaPreparada("hiedra")
            assertEquals(File(donde, "bin/Hiedra"), programa)
            assertTrue(File(donde, "bin/Hiedra").readText().contains("9.9.9"))
            assertNull("ya no hay nada preparado", Actualizador.preparada)
            assertTrue(File(datos, "applications/familia-hiedra.desktop").readText().contains("Exec=\"${donde.absolutePath}/bin/Hiedra\""))
            // Y ya al día: no vuelve a bajar nada
            System.setProperty("familia.version", "9.9.9")
            assertNull(Actualizador.automatico("hiedra", kd))
        } finally {
            servidor.destroy()
            Actualizador.entornoDeInstalar = emptyMap(); Actualizador.preparaParaPruebas(null)
            antes.forEach { (k, v) -> if (v == null) System.clearProperty(k) else System.setProperty(k, v) }
        }
    }
}
