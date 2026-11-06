package com.gonzalo.torrent

import java.io.File
import java.net.NetworkInterface

/*
 * Lo que en el móvil es de Android (Disco.kt, Red.kt y el constructor de MotorTorrent con un Context), en el ordenador. El resto del
 * motor son los mismos archivos que el del móvil (se copian al compilar: ver build.gradle.kts).
 */

/** Por dónde sale el dispositivo: en el ordenador, cable o wifi (sin límite) o ninguna red. */
internal enum class TipoRed { WIFI, MOVIL, NINGUNA }

internal interface Red {
    fun actual(): TipoRed
    /** Avisa (en cualquier hilo) cada vez que cambia la red; se deja de avisar al cerrar lo que devuelve. */
    fun vigila(cambio: () -> Unit): AutoCloseable
}

/**
 * La red del ordenador: un ordenador no tiene «datos móviles», así que con cualquier red de verdad (una interfaz encendida que no es la
 * de dentro del propio ordenador) es como la wifi. Si se queda sin ninguna, el motor lo ve en la siguiente consulta (no hay avisos).
 */
internal object RedOrdenador : Red {
    override fun actual(): TipoRed = runCatching {
        val hay = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { it.isUp && !it.isLoopback && it.inetAddresses.hasMoreElements() }
        if (hay) TipoRed.WIFI else TipoRed.NINGUNA
    }.getOrDefault(TipoRed.WIFI)

    override fun vigila(cambio: () -> Unit): AutoCloseable = AutoCloseable { }
}

/** El sitio del disco: cuánto se puede usar y, antes de empezar a bajar, pedirlo. */
internal interface Disco {
    fun libre(dir: File): Long
    /** Pide [bytes] para la app en el disco de [dir] (en Android se borran cachés de otras apps si hace falta; aquí no hace nada). */
    fun pide(dir: File, bytes: Long) {}
}

/** En el ordenador: lo que queda libre, sin más. */
internal object DiscoSimple : Disco {
    override fun libre(dir: File): Long = dir.usableSpace
}

/** Lo que el motor apunta cuando algo va mal y no es para enseñarlo (en el móvil va al registro de Android). Nunca lleva direcciones. */
internal object Log {
    fun w(etiqueta: String, mensaje: String, e: Throwable? = null) { System.err.println("$etiqueta: $mensaje" + (e?.let { " (${it.javaClass.simpleName})" } ?: "")) }
}

/** El motor en el ordenador: los torrents en [carpeta] (la caché de la app), con la red del ordenador. */
fun motorDeEscritorio(carpeta: File, ajustes: AjustesTorrent = AjustesTorrent()): MotorTorrent =
    MotorTorrent(carpeta, ajustes, RedOrdenador, ConfigSesion(), Tiempos(), Ventanas(), DiscoSimple)

/**
 * La biblioteca nativa de libtorrent: viene dentro de su .jar (la de este sistema) y libtorrent4j la carga desde un archivo
 * (`libtorrent4j.jni.path`). Se saca una vez a [dir] (la carpeta de la app, no /tmp, que puede no dejar ejecutar), como las de los mandos.
 * Devuelve si está lista (si no, no hay torrents en este ordenador: un procesador que no es el de los paquetes).
 */
object NativaTorrent {
    @Volatile var lista = false; private set

    fun prepara(dir: File): Boolean {
        if (lista) return true
        System.getProperty("libtorrent4j.jni.path")?.takeIf { File(it).isFile }?.let { lista = true; return true }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val arq = System.getProperty("os.arch").orEmpty().lowercase()
        val (recurso, nombre) = when {
            os.contains("mac") && (arq == "aarch64" || arq == "arm64") -> "lib/arm64/libtorrent4j.dylib" to "libtorrent4j.dylib"
            os.contains("linux") && (arq == "amd64" || arq == "x86_64") -> "lib/x86_64/libtorrent4j.so" to "libtorrent4j.so"
            else -> return false
        }
        return runCatching {
            val entrada = NativaTorrent::class.java.classLoader.getResourceAsStream(recurso) ?: return false
            dir.mkdirs()
            val f = File(dir, nombre)
            val bytes = entrada.use { it.readBytes() }
            // (se vuelve a escribir solo si ha cambiado: otra versión de la app)
            if (!f.isFile || f.length() != bytes.size.toLong()) {
                val tmp = File(dir, "$nombre.parcial")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
            }
            System.setProperty("libtorrent4j.jni.path", f.absolutePath)
            lista = true
            true
        }.getOrDefault(false)
    }
}
