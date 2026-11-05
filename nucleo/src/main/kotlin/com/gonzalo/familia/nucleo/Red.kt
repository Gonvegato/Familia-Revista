package com.gonzalo.familia.nucleo

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** La red, para que el núcleo no dependa de la plataforma (en las pruebas, un servidor de verdad: el emulador de Firebase). */
interface Red {
    class Respuesta(val codigo: Int, val cuerpo: String, val fecha: Long = 0L) {
        val ok: Boolean get() = codigo in 200..299
    }

    /** Sin conexión o el servidor no responde (se reintenta más tarde; no es un rechazo). */
    class SinRed(msg: String, causa: Throwable? = null) : Exception(msg, causa)

    fun pedir(metodo: String, url: String, cuerpo: String? = null, tipo: String = "application/json", cabeceras: Map<String, String> = emptyMap()): Respuesta

    /**
     * Escucha un flujo de eventos (text/event-stream) hasta que [seguir] diga que no o se corte. Cada evento llega como
     * (nombre, datos). Devuelve cuando el flujo termina; lanza [SinRed] si no se puede abrir.
     */
    fun escuchar(url: String, seguir: () -> Boolean, alEvento: (String, String) -> Unit)
}

/** La red de Java (HttpURLConnection): vale en Android y en el PC. */
class RedJava(private val espera: Int = 20_000) : Red {
    override fun pedir(metodo: String, url: String, cuerpo: String?, tipo: String, cabeceras: Map<String, String>): Red.Respuesta {
        val c = try { URL(url).openConnection() as HttpURLConnection } catch (e: Exception) { throw Red.SinRed("Dirección no válida", e) }
        try {
            c.connectTimeout = espera; c.readTimeout = espera
            // HttpURLConnection no admite PATCH: Firebase entiende la cabecera X-HTTP-Method-Override
            if (metodo == "PATCH") { c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", "PATCH") } else c.requestMethod = metodo
            cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (cuerpo != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "$tipo; charset=utf-8")
                c.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            }
            val codigo = c.responseCode
            val flujo = if (codigo in 200..299) c.inputStream else c.errorStream
            val texto = flujo?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return Red.Respuesta(codigo, texto, c.date)
        } catch (e: SocketTimeoutException) {
            throw Red.SinRed("El servidor no responde", e)
        } catch (e: java.io.IOException) {
            throw Red.SinRed("Sin conexión", e)
        } finally {
            c.disconnect()
        }
    }

    override fun escuchar(url: String, seguir: () -> Boolean, alEvento: (String, String) -> Unit) {
        val c = try { URL(url).openConnection() as HttpURLConnection } catch (e: Exception) { throw Red.SinRed("Dirección no válida", e) }
        try {
            c.connectTimeout = espera
            // Firebase manda un «keep-alive» cada 30 s: si en 75 s no llega nada, la conexión está muerta
            c.readTimeout = 75_000
            c.setRequestProperty("Accept", "text/event-stream")
            c.instanceFollowRedirects = true
            val codigo = try { c.responseCode } catch (e: java.io.IOException) { throw Red.SinRed("Sin conexión", e) }
            if (codigo !in 200..299) throw Red.SinRed("El flujo respondió $codigo")
            BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { r ->
                var evento = ""
                val datos = StringBuilder()
                while (seguir()) {
                    val linea = try { r.readLine() } catch (e: java.io.IOException) { return } ?: return
                    when {
                        linea.isEmpty() -> {
                            if (evento.isNotEmpty() || datos.isNotEmpty()) alEvento(evento, datos.toString())
                            evento = ""; datos.setLength(0)
                        }
                        linea.startsWith("event:") -> evento = linea.substring(6).trim()
                        linea.startsWith("data:") -> { if (datos.isNotEmpty()) datos.append('\n'); datos.append(linea.substring(5).trimStart()) }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }
}
