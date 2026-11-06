package com.gonzalo.torrent

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Lo que sirve el servidor local: un archivo que se lee mientras se descarga. */
internal interface Contenido {
    val tamano: Long
    val mime: String
    /**
     * Un lector que empieza en [desde]: cuenta como un salto del reproductor (la descarga pasa a ir desde ahí). [sigue] dice si el
     * reproductor sigue al otro lado (mientras se espera una pieza, para dejar de esperar si ya se ha ido).
     */
    fun abre(desde: Long, sigue: () -> Boolean = { true }): Lectura
}

internal interface Lectura : Closeable {
    /**
     * Lee hasta [max] bytes desde [pos] en [buf] y dice cuántos (al menos uno). Si aún no están descargados, espera a que lleguen;
     * si no llegan a tiempo, o el torrent se para, lanza [ErrorTorrent].
     */
    fun lee(pos: Long, buf: ByteArray, max: Int): Int
}

/**
 * Un servidor HTTP/1.1 mínimo, solo en 127.0.0.1 (ninguna otra máquina lo ve) y en un puerto libre cualquiera. GET y HEAD, con
 * Range (206, Content-Range, Accept-Ranges), conexiones que se reutilizan y un hilo por conexión (el reproductor abre una o dos).
 * [busca] da el contenido de cada ruta, o null (404).
 */
internal class ServidorLocal(
    private val busca: (String) -> Contenido?,
    private val inactividadMs: Int = 60_000,
    maxConexiones: Int = 16,
) : Closeable {
    private val escucha = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val puerto: Int = escucha.localPort
    private val abiertas = ConcurrentHashMap.newKeySet<Socket>()
    private val numero = AtomicInteger()
    private val hilos = ThreadPoolExecutor(0, maxConexiones, 15, TimeUnit.SECONDS, SynchronousQueue()) { r ->
        Thread(r, "torrent-http-${numero.incrementAndGet()}").apply { isDaemon = true }
    }
    @Volatile private var cerrado = false
    private val aceptador = thread(name = "torrent-http", isDaemon = true) { acepta() }

    private fun acepta() {
        while (!cerrado) {
            val s = try { escucha.accept() } catch (e: IOException) { if (cerrado) return else continue }
            abiertas += s
            try {
                hilos.execute { atiende(s) }
            } catch (e: RejectedExecutionException) {
                runCatching { cabecera(s.getOutputStream(), 503, "Service Unavailable", mapOf("Content-Length" to "0", "Connection" to "close")) }
                cierra(s)
            }
        }
    }

    private fun atiende(s: Socket) {
        try {
            s.soTimeout = inactividadMs
            s.tcpNoDelay = true
            val entrada = BufferedInputStream(s.getInputStream())
            val salida = BufferedOutputStream(s.getOutputStream(), 64 * 1024)
            while (!cerrado) {
                val pet = Peticion.lee(entrada) ?: break
                val seguir = responde(pet, salida) { sigueAhi(s, entrada) }
                salida.flush()
                if (!seguir) break
            }
        } catch (_: IOException) {
            // El reproductor ha cerrado (un salto, o ya no quiere más) o lleva mucho sin pedir nada
        } catch (_: ErrorTorrent) {
            // Los datos no han llegado a tiempo o el torrent se ha parado: se corta la respuesta y el reproductor lo vuelve a pedir
        } catch (_: InterruptedException) {
        } finally {
            cierra(s)
        }
    }

    /** Responde a una petición; devuelve si la conexión sigue abierta para la siguiente. */
    private fun responde(pet: Peticion, salida: OutputStream, sigue: () -> Boolean): Boolean {
        val seguir = pet.mantener
        val conexion = if (seguir) "keep-alive" else "close"
        if (pet.metodo != "GET" && pet.metodo != "HEAD") {
            cabecera(salida, 405, "Method Not Allowed", mapOf("Allow" to "GET, HEAD", "Content-Length" to "0", "Connection" to conexion))
            return seguir
        }
        val c = busca(pet.ruta)
        if (c == null) {
            cabecera(salida, 404, "Not Found", mapOf("Content-Length" to "0", "Connection" to conexion))
            return seguir
        }
        val rango = Rango.analiza(pet.cabeceras["range"], c.tamano)
        val comunes = mapOf("Content-Type" to c.mime, "Accept-Ranges" to "bytes", "Cache-Control" to "no-store", "Connection" to conexion)
        val (inicio, fin) = when (rango) {
            Rango.Imposible -> {
                cabecera(salida, 416, "Range Not Satisfiable", comunes + mapOf("Content-Range" to "bytes */${c.tamano}", "Content-Length" to "0"))
                return seguir
            }
            Rango.Todo -> {
                cabecera(salida, 200, "OK", comunes + mapOf("Content-Length" to "${c.tamano}"))
                0L to c.tamano - 1
            }
            is Rango.Parte -> {
                cabecera(salida, 206, "Partial Content", comunes + mapOf("Content-Range" to "bytes ${rango.inicio}-${rango.fin}/${c.tamano}", "Content-Length" to "${rango.bytes}"))
                rango.inicio to rango.fin
            }
        }
        if (pet.metodo == "HEAD") return seguir
        salida.flush()
        c.abre(inicio, sigue).use { l ->
            val buf = ByteArray(64 * 1024)
            var pos = inicio
            while (pos <= fin) {
                if (cerrado) throw SocketException("Servidor cerrado")
                val n = l.lee(pos, buf, minOf(buf.size.toLong(), fin - pos + 1).toInt())
                salida.write(buf, 0, n)
                salida.flush()
                pos += n
            }
        }
        return seguir
    }

    /**
     * Si el reproductor sigue conectado: un vistazo de 1 ms a lo que llega. Si ha cerrado, se lee el final; si no, no llega nada (o
     * llega su siguiente petición, que se deja donde estaba). Solo desde el hilo de esta conexión.
     */
    private fun sigueAhi(s: Socket, entrada: BufferedInputStream): Boolean {
        if (cerrado || s.isClosed) return false
        val antes = s.soTimeout
        return try {
            s.soTimeout = 1
            entrada.mark(1)
            val b = entrada.read()
            if (b >= 0) entrada.reset()
            b >= 0
        } catch (_: java.net.SocketTimeoutException) {
            true
        } catch (_: IOException) {
            false
        } finally {
            runCatching { s.soTimeout = antes }
        }
    }

    private fun cabecera(salida: OutputStream, codigo: Int, frase: String, campos: Map<String, String>) {
        val sb = StringBuilder("HTTP/1.1 ").append(codigo).append(' ').append(frase).append("\r\n")
        for ((k, v) in campos) sb.append(k).append(": ").append(v).append("\r\n")
        sb.append("\r\n")
        salida.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        salida.flush()
    }

    private fun cierra(s: Socket) {
        abiertas -= s
        runCatching { s.close() }
    }

    override fun close() {
        cerrado = true
        runCatching { escucha.close() }
        for (s in abiertas.toList()) cierra(s)
        hilos.shutdownNow()
        runCatching { aceptador.join(2000) }
        runCatching { hilos.awaitTermination(2, TimeUnit.SECONDS) }
    }
}

/** La línea de petición y las cabeceras (en minúsculas) de una petición HTTP. */
internal class Peticion(val metodo: String, val ruta: String, val version: String, val cabeceras: Map<String, String>) {
    /** HTTP/1.1 mantiene la conexión salvo «Connection: close»; HTTP/1.0 solo con «keep-alive». */
    val mantener: Boolean get() {
        val c = cabeceras["connection"]?.lowercase().orEmpty()
        return if (version == "HTTP/1.1") !c.contains("close") else c.contains("keep-alive")
    }

    companion object {
        private const val MAX_LINEA = 8 * 1024
        private const val MAX_CABECERAS = 64

        /** Lee una petición, o null si la conexión se ha cerrado antes de empezar otra. */
        fun lee(entrada: InputStream): Peticion? {
            var linea = leeLinea(entrada) ?: return null
            while (linea.isEmpty()) linea = leeLinea(entrada) ?: return null   // líneas en blanco entre peticiones (se permiten)
            val partes = linea.split(' ')
            if (partes.size != 3) throw IOException("Petición rara: $linea")
            val cabeceras = HashMap<String, String>()
            while (true) {
                val l = leeLinea(entrada) ?: throw IOException("Cabeceras cortadas")
                if (l.isEmpty()) break
                if (cabeceras.size >= MAX_CABECERAS) throw IOException("Demasiadas cabeceras")
                val i = l.indexOf(':')
                if (i > 0) cabeceras[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            }
            val ruta = partes[1].substringBefore('?').substringBefore('#')
            return Peticion(partes[0].uppercase(), ruta, partes[2].uppercase(), cabeceras)
        }

        private fun leeLinea(entrada: InputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val b = entrada.read()
                if (b < 0) return if (sb.isEmpty()) null else throw IOException("Línea cortada")
                if (b == '\n'.code) return sb.toString().removeSuffix("\r")
                if (sb.length >= MAX_LINEA) throw IOException("Línea demasiado larga")
                sb.append(b.toChar())
            }
        }
    }
}
