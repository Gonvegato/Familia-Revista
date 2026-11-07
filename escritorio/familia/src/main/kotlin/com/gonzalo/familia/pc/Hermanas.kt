package com.gonzalo.familia.pc

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * Una sola copia de cada app abierta a la vez en este ordenador. Si abres Cobalto dos veces, la segunda no abre otra ventana: le pide a
 * la que ya estaba que salga al frente y se cierra. Así dos copias nunca escriben a la vez lo de este ordenador (la caché de la cuenta,
 * lo pendiente de subir), que podría estropearse.
 *
 * El cerrojo es un archivo de la carpeta de la app (`abierta.lock`, bloqueado mientras la app vive); el aviso va por una conexión local
 * (solo de este ordenador) al puerto que la app abierta apunta en `abierta.puerto`. Lo único que se puede pedir es «sal al frente», con
 * lo que se le pasó a la segunda al abrirla (p. ej. `--sala` en Hiedra), por si la abierta quiere hacer algo con ello.
 */
object Instancia {
    /** Lo que hace la app abierta cuando otra copia le pide salir al frente (lo pone la ventana), con lo que se le pasó a esa otra. */
    @Volatile var alFrente: (List<String>) -> Unit = {}
    private var canal: FileChannel? = null
    private var cerrojo: FileLock? = null
    private var servidor: ServerSocket? = null

    /**
     * ¿Es esta la única copia? true: sigue (y ya escucha a las que vengan). false: ya había otra abierta, que ya sabe que tiene que
     * salir al frente; esta debe terminar sin abrir ventana.
     */
    @Synchronized
    fun unica(dir: File, args: List<String> = emptyList()): Boolean {
        dir.mkdirs()
        val ch = RandomAccessFile(File(dir, "abierta.lock"), "rw").channel
        val lock = try { ch.tryLock() } catch (e: OverlappingFileLockException) { null } catch (e: Exception) { null }
        if (lock == null) { runCatching { ch.close() }; pideFrente(dir, args); return false }
        canal = ch; cerrojo = lock
        val s = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
        servidor = s
        runCatching { File(dir, "abierta.puerto").writeText(s.localPort.toString()) }
        Thread({
            while (!s.isClosed) {
                runCatching {
                    s.accept().use { c ->
                        c.soTimeout = 2_000
                        // «frente» y, una por línea, lo que se le pasó a la otra copia (las de antes mandaban solo «frente»)
                        val lineas = c.getInputStream().bufferedReader().readLines()
                        if (lineas.firstOrNull() == "frente") alFrente(lineas.drop(1).filter { it.isNotEmpty() && it.length <= 4096 }.take(32))
                    }
                }
            }
        }, "familia-instancia").apply { isDaemon = true; start() }
        return true
    }

    private fun pideFrente(dir: File, args: List<String>) {
        runCatching {
            val puerto = File(dir, "abierta.puerto").readText().trim().toInt()
            val mensaje = (listOf("frente") + args.map { it.replace('\n', ' ') }).joinToString("\n", postfix = "\n")
            Socket(InetAddress.getLoopbackAddress(), puerto).use { it.getOutputStream().write(mensaje.toByteArray()) }
        }
    }

    /** Suelta el cerrojo (al cerrar; y en las pruebas). */
    @Synchronized
    fun suelta() {
        runCatching { servidor?.close() }; servidor = null
        runCatching { cerrojo?.release() }; cerrojo = null
        runCatching { canal?.close() }; canal = null
        alFrente = {}
    }
}

/**
 * Las seis apps de la familia en este ordenador: dónde está instalada cada una y abrirla (desde la columna de cualquiera, como en el
 * móvil). Si ya estaba abierta, sale al frente ([Instancia]).
 */
object HermanasPc {
    /** Las seis, por orden: el nombre de la carpeta de datos y el que se ve. */
    val todas = listOf("azabache" to "Azabache", "cobalto" to "Cobalto", "hiedra" to "Hiedra", "marfil" to "Marfil", "mostaza" to "Mostaza", "cobre" to "Cobre")

    private fun nombre(app: String) = app.replaceFirstChar { it.uppercase() }

    /**
     * Dónde está instalada [app] (en el Mac el `<App>.app`; en Linux su programa `<App>/bin/<App>`), o null si no está en este ordenador.
     * Se mira al lado de esta (se instalan juntas), en las carpetas de siempre y, en Linux, la del .deb.
     */
    fun donde(app: String, aqui: File? = Actualizador.instalada(), sistema: Sitio.Sistema = Sitio.sistema, casa: File = File(System.getProperty("user.home"))): File? {
        val A = nombre(app)
        return if (sistema == Sitio.Sistema.MAC) {
            listOfNotNull(aqui?.parentFile?.let { File(it, "$A.app") }, File("/Applications/$A.app"), File(casa, "Applications/$A.app")).firstOrNull { it.isDirectory }
        } else {
            val datos = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: File(casa, ".local/share")
            listOfNotNull(aqui?.parentFile?.let { File(it, "$A/bin/$A") }, File(datos, "familia-apps/$A/bin/$A"), File("/opt/$app/bin/$A")).firstOrNull { it.isFile && it.canExecute() }
        }
    }

    fun esta(app: String): Boolean = donde(app) != null

    /** Abre [app] (o la trae al frente si ya estaba abierta). false si no está instalada aquí. */
    fun abre(app: String): Boolean {
        val d = donde(app) ?: return false
        return runCatching {
            val orden = if (Sitio.sistema == Sitio.Sistema.MAC) listOf("open", d.absolutePath) else listOf(d.absolutePath)
            ProcessBuilder(orden).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start(); true
        }.getOrDefault(false)
    }
}
