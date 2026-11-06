package com.gonzalo.hiedra.pc

import com.gonzalo.familia.pc.Sitio
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** Imágenes para las pruebas: una generada (PNG o JPEG) y, si se puede, una captura de verdad de una pantalla de Xvfb. */
object ImagenesPrueba {
    /** Una imagen con ruido (no se comprime: así ocupa varios trozos). */
    fun ruido(w: Int, h: Int, semilla: Long = 7): BufferedImage {
        val r = java.util.Random(semilla)
        return BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also { img -> for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, r.nextInt(0x1000000)) }
    }
    fun png(img: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    fun jpeg(img: BufferedImage, progresivo: Boolean = false): ByteArray {
        val w = ImageIO.getImageWritersByFormatName("jpeg").next()
        val p = w.defaultWriteParam
        if (progresivo) p.progressiveMode = ImageWriteParam.MODE_DEFAULT
        val o = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(o).use { s -> w.output = s; w.write(null, IIOImage(img, null, null), p) }
        w.dispose()
        return o.toByteArray()
    }

    private fun programa(n: String) = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).map { File(it, n) }.firstOrNull { it.canExecute() }

    /**
     * Una captura de verdad: `import -window root` (ImageMagick) de una pantalla de Xvfb de [w]×[h], con el mismo código que en un
     * escritorio X11. Null si no se puede (no es Linux, o no hay Xvfb o ImageMagick).
     */
    fun deXvfb(w: Int = 800, h: Int = 600): Captura.Imagen? {
        if (Sitio.sistema != Sitio.Sistema.LINUX) return null
        val xvfb = programa("Xvfb") ?: return null
        programa("import") ?: return null
        val n = (91..250).firstOrNull { !File("/tmp/.X11-unix/X$it").exists() && !File("/tmp/.X$it-lock").exists() } ?: return null
        val p = ProcessBuilder(xvfb.path, ":$n", "-screen", "0", "${w}x${h}x24", "-nolisten", "tcp").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        try {
            val sock = File("/tmp/.X11-unix/X$n")
            var i = 0
            while (!sock.exists() && i++ < 60 && p.isAlive) Thread.sleep(100)
            if (!sock.exists()) return null
            val e = Captura.Entorno(Sitio.Sistema.LINUX, mapOf("DISPLAY" to ":$n", "XDG_SESSION_TYPE" to "x11", "XDG_CURRENT_DESKTOP" to ""), { programa(it) != null })
            return Captura.toma(e, listOf(Captura.Metodo.IMPORT))
        } finally { p.destroy(); p.waitFor(5, TimeUnit.SECONDS) }
    }
}

/** Capturar la pantalla: con qué en cada escritorio, cómo se llama el archivo, qué tamaño tiene la imagen y cómo se trocea para el móvil. */
class CapturaTest {
    private val kdeWayland = mapOf("XDG_CURRENT_DESKTOP" to "KDE", "XDG_SESSION_DESKTOP" to "KDE", "DESKTOP_SESSION" to "plasma", "WAYLAND_DISPLAY" to "wayland-0", "XDG_SESSION_TYPE" to "wayland")
    private fun linux(v: Map<String, String>, vararg hay: String, varias: Boolean = false) = Captura.Entorno(Sitio.Sistema.LINUX, v, { it in hay }, varias)

    @After fun fuera() { SteamLink.ordenPruebas = null; SteamLink.abiertoPruebas = null; SteamLink.abrePruebas = null }

    @Test fun conQueSeCapturaEnCadaEscritorio() {
        // KDE Plasma en Wayland (CachyOS): Spectacle primero; ni ImageMagick ni Java (en Wayland saldría en negro)
        assertEquals(listOf(Captura.Metodo.SPECTACLE, Captura.Metodo.GRIM), Captura.metodos(linux(kdeWayland, "spectacle", "grim", "import")))
        assertEquals(listOf(Captura.Metodo.SPECTACLE), Captura.metodos(linux(kdeWayland, "spectacle", "import")))
        // Hyprland o Sway: grim
        assertEquals(listOf(Captura.Metodo.GRIM), Captura.metodos(linux(mapOf("XDG_CURRENT_DESKTOP" to "Hyprland", "WAYLAND_DISPLAY" to "wayland-1"), "grim", "import")))
        // GNOME en Wayland: gnome-screenshot (grim, solo de repuesto)
        assertEquals(listOf(Captura.Metodo.GNOME, Captura.Metodo.GRIM), Captura.metodos(linux(mapOf("XDG_CURRENT_DESKTOP" to "ubuntu:GNOME", "XDG_SESSION_TYPE" to "wayland"), "gnome-screenshot", "grim")))
        // X11: la del escritorio si la hay, ImageMagick y Java
        assertEquals(listOf(Captura.Metodo.SPECTACLE, Captura.Metodo.IMPORT, Captura.Metodo.JAVA), Captura.metodos(linux(mapOf("XDG_CURRENT_DESKTOP" to "KDE", "XDG_SESSION_TYPE" to "x11", "DISPLAY" to ":0"), "spectacle", "import")))
        assertEquals(listOf(Captura.Metodo.IMPORT, Captura.Metodo.JAVA), Captura.metodos(linux(mapOf("XDG_CURRENT_DESKTOP" to "XFCE", "DISPLAY" to ":0"), "import")))
        assertEquals(listOf(Captura.Metodo.GNOME, Captura.Metodo.JAVA), Captura.metodos(linux(mapOf("XDG_CURRENT_DESKTOP" to "X-Cinnamon", "DISPLAY" to ":0"), "gnome-screenshot")))
        assertEquals(listOf(Captura.Metodo.JAVA), Captura.metodos(linux(mapOf("DISPLAY" to ":0"))))
        // Wayland sin ninguna herramienta: nada, y lo dice
        val nada = linux(mapOf("XDG_CURRENT_DESKTOP" to "KDE", "WAYLAND_DISPLAY" to "wayland-0"))
        assertTrue(Captura.metodos(nada).isEmpty())
        val e = assertThrows(IOException::class.java) { Captura.toma(nada) }
        assertTrue(e.message, e.message!!.contains("Spectacle") && e.message!!.contains("Wayland"))
        // El Mac: screencapture (y Java), sin mirar el PATH
        assertEquals(listOf(Captura.Metodo.SCREENCAPTURE, Captura.Metodo.JAVA), Captura.metodos(Captura.Entorno(Sitio.Sistema.MAC, emptyMap(), { false })))
    }

    @Test fun lasOrdenesSonLasDeAtrio() {
        val f = File("/tmp/x.png")
        assertEquals(listOf("spectacle", "-b", "-n", "-f", "-o", f.path), Captura.orden(Captura.Metodo.SPECTACLE, f, variasPantallas = false))
        assertEquals("con varias pantallas, la del puntero", listOf("spectacle", "-b", "-n", "-m", "-o", f.path), Captura.orden(Captura.Metodo.SPECTACLE, f, variasPantallas = true))
        assertEquals(listOf("grim", f.path), Captura.orden(Captura.Metodo.GRIM, f, false))
        assertEquals(listOf("gnome-screenshot", "-f", f.path), Captura.orden(Captura.Metodo.GNOME, f, false))
        assertEquals(listOf("import", "-window", "root", f.path), Captura.orden(Captura.Metodo.IMPORT, f, false))
        assertEquals(listOf("/usr/sbin/screencapture", "-x", "-t", "png", f.path), Captura.orden(Captura.Metodo.SCREENCAPTURE, f, false))
        assertNull(Captura.orden(Captura.Metodo.JAVA, f, false))
    }

    @Test fun seProbaronPorOrdenYSeEsperaASpectacle() {
        val png = ImagenesPrueba.png(ImagenesPrueba.ruido(64, 36))
        val llamadas = ArrayList<List<String>>()
        // Spectacle sale enseguida y escribe el archivo un poco después: se espera
        val tarde = Captura.Ejecutor { o, _, _ -> llamadas += o; val f = File(o.last()); Thread { Thread.sleep(400); f.writeBytes(png) }.start() }
        val i = Captura.toma(linux(kdeWayland, "spectacle", "grim"), ejecutor = tarde)
        assertEquals("spectacle", i.metodo); assertEquals(64, i.w); assertEquals(36, i.h); assertEquals("image/png", i.mime)
        assertArrayEquals(png, i.bytes)
        assertEquals("spectacle", llamadas.single().first())
        assertFalse("el temporal se borra", File(llamadas.single().last()).exists())
        // Si Spectacle falla, el siguiente
        llamadas.clear()
        val falla = Captura.Ejecutor { o, _, _ -> llamadas += o; if (o[0] == "spectacle") throw IOException("no está instalado") else File(o.last()).writeBytes(png) }
        assertEquals("grim", Captura.toma(linux(kdeWayland, "spectacle", "grim"), ejecutor = falla).metodo)
        assertEquals(listOf("spectacle", "grim"), llamadas.map { it[0] })
        // Si ninguno puede, se dice por qué no ha podido cada uno; una imagen que no es imagen no vale
        val basura = Captura.Ejecutor { o, _, _ -> File(o.last()).writeBytes(ByteArray(500) { 7 }) }
        val e = assertThrows(IOException::class.java) { Captura.toma(linux(kdeWayland, "spectacle", "grim"), ejecutor = basura) }
        assertTrue(e.message, e.message!!.contains("spectacle: la imagen no vale") && e.message!!.contains("grim: la imagen no vale"))
        // En X11, al final, la de Java
        val nadaX11 = Captura.Ejecutor { _, _, _ -> throw IOException("no está instalado") }
        assertEquals("java", Captura.toma(linux(mapOf("DISPLAY" to ":0"), "import"), ejecutor = nadaX11, java = { png }).metodo)
    }

    @Test fun elTamanoDeUnPngODeUnJpeg() {
        val img = ImagenesPrueba.ruido(37, 21)
        assertEquals(Captura.Medida(37, 21, "image/png"), Captura.mide(ImagenesPrueba.png(img)))
        val jpg = ImagenesPrueba.jpeg(img)
        assertEquals(Captura.Medida(37, 21, "image/jpeg"), Captura.mide(jpg))
        assertEquals("progresivo (SOF2)", Captura.Medida(37, 21, "image/jpeg"), Captura.mide(ImagenesPrueba.jpeg(img, progresivo = true)))
        // Con un bloque EXIF delante (como los de las cámaras y de screencapture) y relleno entre marcas
        val exif = byteArrayOf(0xff.toByte(), 0xe1.toByte(), 0, 10) + "Exif\u0000\u0000ab".toByteArray(Charsets.ISO_8859_1)
        val conExif = jpg.copyOfRange(0, 2) + exif + byteArrayOf(0xff.toByte()) + jpg.copyOfRange(2, jpg.size)
        assertEquals(Captura.Medida(37, 21, "image/jpeg"), Captura.mide(conExif))
        // Una de 4K (lo que dice la cabecera, sin abrirla)
        val png4k = ImagenesPrueba.png(BufferedImage(3840, 2160, BufferedImage.TYPE_INT_RGB))
        assertEquals(Captura.Medida(3840, 2160, "image/png"), Captura.mide(png4k))
        assertEquals(Captura.Medida(0, 0, "application/octet-stream"), Captura.mide("hola, no soy una imagen".toByteArray()))
        assertEquals(Captura.Medida(0, 0, "application/octet-stream"), Captura.mide(png4k.copyOf(12)))
        assertEquals(Captura.Medida(0, 0, "image/jpeg"), Captura.mide(jpg.copyOf(8)))
    }

    @Test fun elNombreYLaCarpetaDeCadaCaptura() {
        val t = LocalDateTime.of(2026, 11, 6, 9, 5, 7)
        assertEquals("Hades 2026-11-06 09.05.07.png", Capturas.nombre("Hades", t, "png"))
        assertEquals("Hades II Edición final 2026-11-06 09.05.07.jpg", Capturas.nombre("Hades II: Edición <final>?", t, "jpg"))
        assertEquals("Escritorio 2026-11-06 09.05.07.png", Capturas.nombre(null, t, "png"))
        assertEquals("Escritorio", Capturas.seguro("  /  "))
        assertEquals("AC DC", Capturas.seguro("AC/DC"))
        assertEquals(80, Capturas.seguro("x".repeat(200)).length)
        // La carpeta de Imágenes: la de xdg-user-dir; si no hay ninguna puesta (dice la carpeta personal), la de user-dirs.dirs; si no, ~/Imágenes o ~/Pictures
        val casa = File("/home/gonzalo")
        assertEquals(File("/home/gonzalo/Imágenes"), Capturas.carpetaImagenes(casa, "/home/gonzalo/Imágenes\n", null, { false }, mac = false))
        val dirs = "# comentario\nXDG_DESKTOP_DIR=\"\$HOME/Escritorio\"\nXDG_PICTURES_DIR=\"\$HOME/Fotos\"\n"
        assertEquals(File("/home/gonzalo/Fotos"), Capturas.carpetaImagenes(casa, "/home/gonzalo\n", dirs, { false }, mac = false))
        assertEquals(File("/home/gonzalo/Fotos"), Capturas.carpetaImagenes(casa, null, dirs, { false }, mac = false))
        assertEquals(File("/home/gonzalo/Imágenes"), Capturas.carpetaImagenes(casa, "", "XDG_PICTURES_DIR=\"\$HOME/\"", { it.name == "Imágenes" }, mac = false))
        assertEquals(File("/home/gonzalo/Pictures"), Capturas.carpetaImagenes(casa, null, null, { false }, mac = false))
        assertEquals(File("/Users/gonzalo/Pictures"), Capturas.carpetaImagenes(File("/Users/gonzalo"), "/x", null, { true }, mac = true))
    }

    @Test fun losTrozosSeLeenUnoAUno() {
        val r = java.util.Random(3)
        for (tam in listOf(1, 2, 3, 4, 5, 767, 768, 769, 3000, 100_003)) {
            val b = ByteArray(tam).also { r.nextBytes(it) }
            for (trozo in listOf(4, 8, 1024, Capturas.TROZO)) {
                val l = Capturas.trozos(b, trozo)
                val entero = Base64.getEncoder().encodeToString(b)
                assertEquals((entero.length + trozo - 1) / trozo, l.size)
                // Lo mismo que cortar el base64 entero cada «trozo» caracteres, como Atrio del PC
                assertEquals(entero.chunked(trozo), l)
                assertTrue(l.dropLast(1).all { it.length == trozo }); assertTrue(l.last().length in 1..trozo)
                // Como el móvil: cada trozo se decodifica solo y se juntan
                val o = ByteArrayOutputStream(); l.forEach { o.write(Base64.getDecoder().decode(it)) }
                assertArrayEquals("$tam bytes en trozos de $trozo", b, o.toByteArray())
            }
        }
        assertEquals(0, Capturas.TROZO % 4)
        assertThrows(IllegalArgumentException::class.java) { Capturas.trozos(ByteArray(10), 6) }
    }

    @Test fun unaCapturaDeVerdadEnXvfb() {
        val i = ImagenesPrueba.deXvfb(800, 600)
        Assume.assumeTrue("Sin Xvfb o ImageMagick (o no es Linux)", i != null)
        assertEquals("import", i!!.metodo); assertEquals(800, i.w); assertEquals(600, i.h); assertEquals("image/png", i.mime)
        val leida = ImageIO.read(i.bytes.inputStream())
        assertEquals(800, leida.width); assertEquals(600, leida.height)
    }

    @Test fun steamParaElStreaming() {
        assertTrue(SteamLink.esVirtual("docker0")); assertTrue(SteamLink.esVirtual("tailscale0")); assertTrue(SteamLink.esVirtual("virbr0")); assertTrue(SteamLink.esVirtual("utun3"))
        assertFalse(SteamLink.esVirtual("enp5s0")); assertFalse(SteamLink.esVirtual("wlan0")); assertFalse(SteamLink.esVirtual("en0")); assertFalse(SteamLink.esVirtual("eth0"))
        assertTrue(SteamLink.esCgnat("100.101.2.3")); assertFalse(SteamLink.esCgnat("100.63.0.1")); assertFalse(SteamLink.esCgnat("100.128.0.1")); assertFalse(SteamLink.esCgnat("192.168.1.5"))
        assertEquals("0a:1b:2c:3d:4e:ff", SteamLink.mac(byteArrayOf(0x0a, 0x1b, 0x2c, 0x3d, 0x4e, 0xff.toByte())))
        assertNull(SteamLink.mac(ByteArray(6))); assertNull(SteamLink.mac(null)); assertNull(SteamLink.mac(ByteArray(8) { 1 }))
        val st = SteamLink.presencia(SteamLink.Info(true, false, "pc-salon", listOf("192.168.1.20"), listOf("0a:1b:2c:3d:4e:ff")), permitido = true)
        assertEquals(mapOf("on" to true, "run" to false, "name" to "pc-salon", "lan" to listOf("192.168.1.20"), "mac" to listOf("0a:1b:2c:3d:4e:ff"), "ok" to true, "host" to "steamlink"), st)
        // Sin Steam
        SteamLink.ordenPruebas = { null }; SteamLink.abiertoPruebas = { false }
        assertTrue(assertThrows(IOException::class.java) { SteamLink.abreSteam() }.message!!.contains("Steam no está instalado"))
        // Con Steam cerrado: se abre y se espera a verlo abierto
        var abierto = false
        val abiertos = ArrayList<List<String>>()
        SteamLink.ordenPruebas = { listOf("steam", "-silent") }; SteamLink.abiertoPruebas = { abierto }; SteamLink.abrePruebas = { abiertos += it; Thread { Thread.sleep(300); abierto = true }.start() }
        SteamLink.abreSteam(esperaMs = 5_000, paso = 50)
        assertEquals(listOf(listOf("steam", "-silent")), abiertos)
        // Ya abierto: no se vuelve a abrir
        SteamLink.abreSteam(esperaMs = 5_000, paso = 50)
        assertEquals(1, abiertos.size)
        // Steam no llega a abrirse
        abierto = false; SteamLink.abrePruebas = { }
        assertTrue(assertThrows(IOException::class.java) { SteamLink.abreSteam(esperaMs = 300, paso = 50) }.message!!.contains("No he podido abrir Steam"))
    }
}
