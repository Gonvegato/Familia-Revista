package com.gonzalo.hiedra.pc

import com.gonzalo.familia.pc.Sitio
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Capturar la pantalla de este ordenador a su resolución de verdad, con la herramienta de su escritorio (lo mismo que hacía Atrio del PC,
 * capture.js). Se prueban por orden hasta que una da una imagen:
 *
 *  - KDE Plasma (también en Wayland): Spectacle, `spectacle -b -n -f -o <archivo>` (con varias pantallas, `-m`: la del puntero, que en
 *    un juego a pantalla completa es la suya). Puede escribir el archivo un instante después de salir: se espera un poco.
 *  - Sway, Hyprland y los demás de Wayland con wlroots: `grim <archivo>`.
 *  - GNOME, Unity, Cinnamon, Budgie: `gnome-screenshot -f <archivo>`.
 *  - X11: ImageMagick (`import -window root <archivo>`) y, si no, la de Java (en Wayland no: saldría en negro).
 *  - Mac: `/usr/sbin/screencapture -x -t png <archivo>` (la primera vez el Mac pide permiso de «Grabación de pantalla» para Hiedra, en
 *    Ajustes del Sistema → Privacidad y seguridad) y, si no, la de Java.
 */
object Captura {
    enum class Metodo(val id: String, val nombre: String) {
        SPECTACLE("spectacle", "Spectacle"), GRIM("grim", "grim"), GNOME("gnome-screenshot", "gnome-screenshot"), IMPORT("import", "ImageMagick (import)"),
        SCREENCAPTURE("screencapture", "screencapture del Mac"), JAVA("java", "Java")
    }

    /** Dónde se captura: el sistema, sus variables (el escritorio, si es Wayland) y qué programas hay. */
    class Entorno(val sistema: Sitio.Sistema, val variables: Map<String, String>, val hay: (String) -> Boolean, val variasPantallas: Boolean = false) {
        /** El escritorio («kde», «gnome», «hyprland»…), en minúsculas. */
        val escritorio: String get() = listOf("XDG_CURRENT_DESKTOP", "XDG_SESSION_DESKTOP", "DESKTOP_SESSION").joinToString(":") { variables[it].orEmpty() }.lowercase()
        val wayland: Boolean get() = !variables["WAYLAND_DISPLAY"].isNullOrBlank() || variables["XDG_SESSION_TYPE"] == "wayland"
        companion object {
            /** Este ordenador, ahora. */
            fun deAqui() = Entorno(Sitio.sistema, System.getenv(), Programas::enPath, pantallas() > 1)
        }
    }

    /** Una captura: la imagen tal cual (PNG o JPEG), su tamaño y con qué se hizo. */
    class Imagen(val bytes: ByteArray, val w: Int, val h: Int, val mime: String, val metodo: String)

    /** Ancho, alto y tipo de una imagen. */
    data class Medida(val w: Int, val h: Int, val mime: String)

    /** Las maneras de capturar de [e], por orden de preferencia. */
    fun metodos(e: Entorno): List<Metodo> {
        if (e.sistema == Sitio.Sistema.MAC) return listOf(Metodo.SCREENCAPTURE, Metodo.JAVA)
        val d = e.escritorio
        val kde = Regex("kde|plasma").containsMatchIn(d)
        val gnome = Regex("gnome").containsMatchIn(d)
        val l = ArrayList<Metodo>()
        if (kde && e.hay("spectacle")) l += Metodo.SPECTACLE
        if (e.wayland && e.hay("grim") && !kde && !gnome) l += Metodo.GRIM
        if (Regex("gnome|unity|cinnamon|budgie").containsMatchIn(d) && e.hay("gnome-screenshot")) l += Metodo.GNOME
        if (!e.wayland) { if (e.hay("import")) l += Metodo.IMPORT; l += Metodo.JAVA }
        if (e.hay("spectacle") && Metodo.SPECTACLE !in l) l += Metodo.SPECTACLE
        if (e.hay("grim") && Metodo.GRIM !in l) l += Metodo.GRIM
        return l
    }

    /** La orden de un método para capturar en [archivo] (null: el de Java, que no es una orden). */
    fun orden(m: Metodo, archivo: File, variasPantallas: Boolean): List<String>? = when (m) {
        Metodo.SPECTACLE -> listOf("spectacle", "-b", "-n", if (variasPantallas) "-m" else "-f", "-o", archivo.path)
        Metodo.GRIM -> listOf("grim", archivo.path)
        Metodo.GNOME -> listOf("gnome-screenshot", "-f", archivo.path)
        Metodo.IMPORT -> listOf("import", "-window", "root", archivo.path)
        Metodo.SCREENCAPTURE -> listOf("/usr/sbin/screencapture", "-x", "-t", "png", archivo.path)
        Metodo.JAVA -> null
    }

    /** Ancho y alto de un PNG o de un JPEG (de su cabecera, sin abrir la imagen); 0×0 si no es ninguno de los dos. */
    fun mide(b: ByteArray): Medida {
        fun u8(i: Int) = b[i].toInt() and 0xff
        fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
        fun u32(i: Int) = (u8(i).toLong() shl 24) or (u8(i + 1).toLong() shl 16) or (u8(i + 2).toLong() shl 8) or u8(i + 3).toLong()
        if (b.size > 24 && u32(0) == 0x89504e47L) return Medida(u32(16).toInt(), u32(20).toInt(), "image/png")
        if (b.size > 4 && u8(0) == 0xff && u8(1) == 0xd8) {
            var i = 2
            while (i + 9 < b.size) {
                if (u8(i) != 0xff) { i++; continue }
                val m = u8(i + 1)
                when {
                    m == 0xff -> { i++; continue }                                // relleno
                    m == 0x01 || m in 0xd0..0xd9 -> { i += 2; continue }          // marcas sin longitud
                    m in 0xc0..0xcf && m != 0xc4 && m != 0xc8 && m != 0xcc -> return Medida(u16(i + 7), u16(i + 5), "image/jpeg")
                }
                i += 2 + u16(i + 2)
            }
            return Medida(0, 0, "image/jpeg")
        }
        return Medida(0, 0, "application/octet-stream")
    }

    /** Lanza una orden y espera a que termine (o falla con lo que dijo). Se puede cambiar en las pruebas. */
    fun interface Ejecutor { fun ejecuta(orden: List<String>, variables: Map<String, String>, segundos: Long) }

    val ejecutorDelSistema = Ejecutor { orden, variables, segundos ->
        val pb = ProcessBuilder(orden).redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.environment().putAll(variables)
        val p = try { pb.start() } catch (e: IOException) { throw IOException("no está instalado") }
        val err = StringBuilder()
        val lector = Thread { runCatching { p.errorStream.bufferedReader().use { err.append(it.readText()) } } }.apply { isDaemon = true; start() }
        if (!p.waitFor(segundos, TimeUnit.SECONDS)) { p.destroyForcibly(); throw IOException("no ha terminado a tiempo") }
        lector.join(1_000)
        if (p.exitValue() != 0) throw IOException(err.toString().trim().lines().lastOrNull { it.isNotBlank() }?.take(200)?.ifBlank { null } ?: "ha salido con ${p.exitValue()}")
    }

    /**
     * Captura la pantalla (bloquea unos segundos). Prueba los [metodos] por orden y devuelve la primera imagen buena; si ninguno puede,
     * falla diciendo por qué no ha podido cada uno.
     */
    fun toma(e: Entorno = Entorno.deAqui(), metodos: List<Metodo> = metodos(e), ejecutor: Ejecutor = ejecutorDelSistema, java: () -> ByteArray = ::conJava): Imagen {
        if (metodos.isEmpty()) throw IOException(sinNada(e))
        val fallos = ArrayList<String>()
        for (m in metodos) {
            try {
                val b = if (m == Metodo.JAVA) java() else conHerramienta(m, e, ejecutor)
                val s = mide(b)
                if (s.w <= 0 || s.h <= 0) throw IOException("la imagen no vale")
                return Imagen(b, s.w, s.h, s.mime, m.id)
            } catch (x: Exception) {
                fallos += "${m.id}: ${x.message ?: x.javaClass.simpleName}"
            }
        }
        throw IOException("No se ha podido capturar la pantalla (${fallos.joinToString(" · ")})" + if (e.wayland && metodos.none { it == Metodo.SPECTACLE || it == Metodo.GRIM || it == Metodo.GNOME }) ". ${sinNada(e)}" else "")
    }

    private fun sinNada(e: Entorno) =
        if (e.wayland) "En Wayland hace falta la herramienta de capturas del escritorio: Spectacle (KDE), grim (Sway, Hyprland) o gnome-screenshot (GNOME)."
        else "No hay con qué capturar la pantalla: instala Spectacle o ImageMagick."

    private fun conHerramienta(m: Metodo, e: Entorno, ejecutor: Ejecutor): ByteArray {
        val f = File(System.getProperty("java.io.tmpdir"), "hiedra-captura-${ProcessHandle.current().pid()}-${System.nanoTime()}.png")
        try {
            val o = orden(m, f, e.variasPantallas) ?: throw IOException("no es una orden")
            ejecutor.ejecuta(o, e.variables, if (m == Metodo.SPECTACLE || m == Metodo.SCREENCAPTURE) 20 else 15)
            // Spectacle puede tardar un instante en escribir el archivo después de salir: se espera a que esté y a que deje de crecer
            var antes = -1L
            var i = 0
            while (i++ < 25) {
                val l = if (f.isFile) f.length() else 0L
                if (l > 0 && l == antes) break
                antes = l
                Thread.sleep(150)
            }
            if (!f.isFile) throw IOException("no ha escrito la imagen")
            val b = f.readBytes()
            if (b.size < 100) throw IOException("archivo vacío")
            return b
        } finally { f.delete() }
    }

    /** Cuántas pantallas tiene este ordenador (1 si no se sabe). */
    fun pantallas(): Int = runCatching {
        if (java.awt.GraphicsEnvironment.isHeadless()) 1 else java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size
    }.getOrDefault(1).coerceAtLeast(1)

    /** La de Java: la pantalla del puntero, a su resolución de verdad (también con la pantalla escalada), en PNG. */
    fun conJava(): ByteArray {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw IOException("sin pantalla")
        val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        val p = runCatching { java.awt.MouseInfo.getPointerInfo()?.location }.getOrNull()
        val dev = ge.screenDevices.firstOrNull { p != null && it.defaultConfiguration.bounds.contains(p) } ?: ge.defaultScreenDevice
        val zona = dev.defaultConfiguration.bounds
        val variantes = java.awt.Robot().createMultiResolutionScreenCapture(zona).resolutionVariants
        val img = variantes.maxByOrNull { it.getWidth(null).toLong() * it.getHeight(null) } ?: throw IOException("captura vacía")
        val bi = img as? java.awt.image.BufferedImage ?: java.awt.image.BufferedImage(img.getWidth(null), img.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_RGB).also { it.graphics.drawImage(img, 0, 0, null) }
        return ByteArrayOutputStream().also { javax.imageio.ImageIO.write(bi, "png", it) }.toByteArray()
    }
}
