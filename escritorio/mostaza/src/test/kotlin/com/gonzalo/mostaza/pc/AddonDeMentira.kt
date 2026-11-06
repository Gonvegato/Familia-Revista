package com.gonzalo.mostaza.pc

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Un servidor HTTP de mentira en el ordenador (para las pruebas; a mano, con un ServerSocket: en las pruebas de Android no está el de la
 * JDK). Responde a lo que se le pida con [respuestas] (camino → cuerpo; el camino es el que llega, sin decodificar, con su consulta si la
 * hay, o sin ella) y apunta cada petición en [pedidos] y sus cabeceras en [cabeceras]. Lo que no está, 404.
 */
class AddonDeMentira(val respuestas: MutableMap<String, String> = ConcurrentHashMap()) : AutoCloseable {
    val pedidos = CopyOnWriteArrayList<String>()
    val cabeceras = CopyOnWriteArrayList<Map<String, String>>()
    private val servidor = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    @Volatile private var abierto = true

    init {
        Thread({
            while (abierto) {
                val s = try { servidor.accept() } catch (e: Exception) { break }
                Thread({ runCatching { atiende(s) }; runCatching { s.close() } }, "addon-de-mentira-peticion").apply { isDaemon = true; start() }
            }
        }, "addon-de-mentira").apply { isDaemon = true; start() }
    }

    private fun atiende(s: Socket) {
        val entrada = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val linea = entrada.readLine() ?: return
        val camino = linea.split(' ').getOrNull(1) ?: return
        val cab = HashMap<String, String>()
        while (true) { val l = entrada.readLine() ?: break; if (l.isEmpty()) break; val i = l.indexOf(':'); if (i > 0) cab[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim() }
        pedidos += camino; cabeceras += cab
        val cuerpo = respuestas[camino] ?: respuestas[camino.substringBefore('?')]
        val salida = s.getOutputStream()
        if (cuerpo == null) { salida.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); salida.flush(); return }
        val b = cuerpo.toByteArray(Charsets.UTF_8)
        salida.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n").toByteArray())
        salida.write(b); salida.flush()
    }

    val base: String get() = "http://127.0.0.1:${servidor.localPort}"
    override fun close() { abierto = false; runCatching { servidor.close() } }
}
