package com.gonzalo.iptv

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLException

/**
 * Un error de Cobre con un texto para ti. **Nunca lleva una dirección ni un usuario ni una contraseña**: los textos se escriben aquí, a
 * mano, y lo que viene de fuera (el mensaje de una excepción de Java, que a veces lleva la dirección entera) no se copia nunca.
 */
open class IptvError(mensaje: String, val codigo: Int = 0) : IOException(mensaje)

/** Lo que responde un servidor: el código y el cuerpo, para leerlo a trozos (una lista o una guía pueden ser enormes). */
class Respuesta(val codigo: Int, val cuerpo: InputStream) : Closeable {
    val ok: Boolean get() = codigo in 200..299
    override fun close() = runCatching { cuerpo.close() }.let { }
}

/** La red de Cobre: se cambia en las pruebas por un servidor de mentira. */
fun interface Red {
    /** Abre [url] con [cabeceras] (bloquea). Lanza [IptvError] (sin la dirección en el texto) si no se puede. */
    fun abre(url: String, cabeceras: Map<String, String>): Respuesta
}

/**
 * La red de verdad (HttpURLConnection, la de Java y Android). Sigue las redirecciones también de http a https y al revés (muchos
 * servidores de listas lo hacen, y Java no lo hace solo), pide el cuerpo comprimido y lo descomprime al leer, y traduce los fallos a
 * textos sin la dirección.
 */
class RedJava(private val agente: String = "Cobre", private val conectar: Int = 20_000, private val leer: Int = 60_000) : Red {
    override fun abre(url: String, cabeceras: Map<String, String>): Respuesta {
        var u = url
        repeat(8) {
            val c = try { URL(u).openConnection() as HttpURLConnection } catch (e: Exception) { throw IptvError("La dirección no es válida.") }
            c.connectTimeout = conectar; c.readTimeout = leer
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept-Encoding", "gzip")
            if (cabeceras.keys.none { it.equals("User-Agent", true) }) c.setRequestProperty("User-Agent", agente)
            cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val codigo = try { c.responseCode } catch (e: Exception) { c.disconnect(); throw traduce(e) }
            if (codigo in 300..399) {
                val destino = c.getHeaderField("Location")
                c.disconnect()
                if (destino.isNullOrBlank()) throw IptvError("El servidor manda a otro sitio sin decir a cuál.", codigo)
                u = URL(URL(u), destino).toString()
                return@repeat
            }
            val crudo = (if (codigo in 200..299) c.inputStream else c.errorStream) ?: InputStream.nullInputStream()
            val gz = c.contentEncoding.equals("gzip", true)
            val cuerpo = BufferedInputStream(if (gz) GZIPInputStream(crudo, 64 * 1024) else crudo, 64 * 1024)
            return Respuesta(codigo, object : java.io.FilterInputStream(cuerpo) { override fun close() { runCatching { super.close() }; c.disconnect() } })
        }
        throw IptvError("Demasiadas redirecciones.")
    }

    companion object {
        fun traduce(e: Exception): IptvError = when (e) {
            is IptvError -> e
            is UnknownHostException -> IptvError("No encuentro el servidor (¿está bien escrito?, ¿hay conexión?).")
            is SocketTimeoutException -> IptvError("El servidor no contesta (tarda demasiado).")
            is SSLException -> IptvError("No se puede hablar de forma segura con el servidor (su certificado no vale). Prueba con http://.")
            is java.net.ConnectException -> IptvError("El servidor no acepta la conexión (¿el puerto es el bueno?).")
            else -> IptvError("Sin conexión con el servidor.")
        }
    }
}

/** Para no enseñar nunca un secreto: lo tapa en un texto (también su forma en una dirección, %XX). */
object Secretos {
    const val TAPADO = "•••"

    fun tapa(texto: String, secretos: Collection<String>): String {
        var t = texto
        for (s in secretos.filter { it.length >= 3 }.sortedByDescending { it.length }) {
            t = t.replace(s, TAPADO)
            val cod = runCatching { java.net.URLEncoder.encode(s, "UTF-8") }.getOrDefault(s)
            if (cod != s) t = t.replace(cod, TAPADO)
        }
        // Y lo que tiene pinta de usuario y contraseña en una dirección, aunque no se sepa cuáles son
        t = Regex("(?i)((?:username|password|user|pass|token|key)=)[^&\\s\"']+").replace(t) { it.groupValues[1] + TAPADO }
        t = Regex("(?i)/(live|movie|series|timeshift)/[^/\\s]+/[^/\\s]+/").replace(t) { "/${it.groupValues[1]}/$TAPADO/$TAPADO/" }
        return t
    }

    /** «••••••••» del largo de la contraseña (para enseñarla tapada en Ajustes). */
    fun puntos(s: String): String = if (s.isEmpty()) "" else "•".repeat(s.length.coerceIn(6, 12))
}
