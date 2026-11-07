package com.gonzalo.iptv

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.Reader

/** Un recurso de las pruebas (las listas, respuestas de Xtream y guías con la forma de las de verdad). */
fun recurso(n: String): ByteArray = Thread.currentThread().contextClassLoader.getResourceAsStream(n)!!.readBytes()
fun recursoTexto(n: String) = recurso(n).toString(Charsets.UTF_8)

/**
 * Una red de mentira: contesta lo que diga [responde] para cada dirección (null = 404). Apunta lo que se pide (para ver qué cabeceras
 * llegan a cada sitio).
 */
class RedDeMentira(val responde: (String) -> Any?) : Red {
    val pedidas = ArrayList<Pair<String, Map<String, String>>>()
    override fun abre(url: String, cabeceras: Map<String, String>): Respuesta {
        synchronized(pedidas) { pedidas += url to cabeceras }
        return when (val r = responde(url)) {
            null -> Respuesta(404, ByteArrayInputStream(ByteArray(0)))
            is Int -> Respuesta(r, ByteArrayInputStream(ByteArray(0)))
            is ByteArray -> Respuesta(200, ByteArrayInputStream(r))
            is InputStream -> Respuesta(200, r)
            is Exception -> throw r
            else -> Respuesta(200, ByteArrayInputStream(r.toString().toByteArray()))
        }
    }
}

/** Un servidor de Xtream de mentira con las respuestas de `resources/xtream`. Solo contesta con el usuario y la contraseña buenos. */
fun servidorXtream(usuario: String = "pepe", clave: String = "s3cr3t0", extra: (String) -> Any? = { null }): RedDeMentira = RedDeMentira { u ->
    extra(u) ?: run {
        if (!u.startsWith("http://tv.ejemplo.test:8080/")) return@run null
        if (u.contains("/player_api.php")) {
            if (!u.contains("username=$usuario&password=$clave")) return@run recurso("xtream/perfil_mal.json")
            val accion = Regex("action=([a-z_]+)").find(u)?.groupValues?.get(1)
            when (accion) {
                null -> recurso("xtream/perfil.json")
                "get_live_categories" -> recurso("xtream/live_categories.json")
                "get_live_streams" -> recurso("xtream/live_streams.json")
                "get_vod_categories" -> recurso("xtream/vod_categories.json")
                "get_vod_streams" -> recurso("xtream/vod_streams.json")
                "get_vod_info" -> recurso("xtream/vod_info.json")
                "get_series_categories" -> recurso("xtream/series_categories.json")
                "get_series" -> recurso("xtream/series.json")
                "get_series_info" -> if (u.contains("series_id=3002")) recurso("xtream/series_info_lista.json") else recurso("xtream/series_info.json")
                "get_short_epg" -> recurso("xtream/short_epg.json")
                else -> "[]"
            }
        } else if (u.contains("/xmltv.php")) { if (u.contains("password=$clave")) recurso("guia.xml") else 401 } else null
    }
}

val LISTA_XTREAM = Lista("l0123456789", "Mi servidor", TipoLista.XTREAM, "tv.ejemplo.test:8080", "pepe", "s3cr3t0")

/** Un lector que va inventando el texto según se lee (para listas y guías enormes que no caben en memoria: no se guarda nada). */
class TextoQueSeInventa(private val lineas: Sequence<String>) : Reader() {
    private val it = lineas.iterator()
    private var actual = ""; private var pos = 0
    var leidos = 0L; private set
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (pos >= actual.length) { if (!it.hasNext()) return -1; actual = it.next() + "\n"; pos = 0 }
        val n = minOf(len, actual.length - pos)
        actual.toCharArray(cbuf, off, pos, pos + n); pos += n; leidos += n
        return n
    }
    override fun close() {}
}

/** Lo mismo, en bytes (UTF-8). */
class BytesQueSeInventan(lineas: Sequence<String>) : InputStream() {
    private val it = lineas.iterator()
    private var actual = ByteArray(0); private var pos = 0
    var leidos = 0L; private set
    override fun read(): Int { if (!llena()) return -1; leidos++; return actual[pos++].toInt() and 0xFF }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!llena()) return -1
        val n = minOf(len, actual.size - pos); System.arraycopy(actual, pos, b, off, n); pos += n; leidos += n; return n
    }
    private fun llena(): Boolean { while (pos >= actual.size) { if (!it.hasNext()) return false; actual = (it.next() + "\n").toByteArray(); pos = 0 }; return true }
}
