package com.gonzalo.torrent

import org.junit.Assume.assumeTrue
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.LibTorrent
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.address
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.ip_filter
import org.libtorrent4j.swig.settings_pack
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/** Las pruebas de verdad necesitan la libtorrent de Linux x86_64 (build.gradle.kts la saca de libtorrent4j-linux). */
internal fun hayLibtorrent() {
    val ok = runCatching { LibTorrent.version() }
    assumeTrue("Sin la libtorrent nativa de este ordenador: ${ok.exceptionOrNull()}", ok.isSuccess)
}

/** Una red que se cambia a mano. */
internal class RedFija(@Volatile var tipo: TipoRed = TipoRed.WIFI) : Red {
    private val oyentes = CopyOnWriteArrayList<() -> Unit>()
    val vigilando: Int get() = oyentes.size
    override fun actual() = tipo
    override fun vigila(cambio: () -> Unit): AutoCloseable {
        oyentes += cambio
        return AutoCloseable { oyentes -= cambio }
    }
    fun cambia(t: TipoRed) { tipo = t; oyentes.forEach { it() } }
}

/** Un archivo «de vídeo» con bytes al azar (siempre los mismos para la misma [semilla]) y la cabecera de un MKV. */
internal fun creaVideo(f: File, tamano: Int, semilla: Int = 1): ByteArray {
    val b = Random(semilla).nextBytes(tamano)
    byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()).copyInto(b)
    f.parentFile?.mkdirs()
    f.writeBytes(b)
    return b
}

/**
 * El .torrent (v1) de [ruta], un archivo o una carpeta (sus archivos por orden de ruta), con piezas de [tamPieza]. Con [relleno],
 * cada archivo de una carpeta acaba en una pieza y le sigue uno de relleno (BEP 47), como los que hace libtorrent. Se hace aquí, a
 * mano: el TorrentBuilder de libtorrent lee los archivos con la E/S de mmap, que deja un manejador de SIGSEGV que tumba la JVM.
 */
internal fun creaTorrent(ruta: File, tamPieza: Int, relleno: Boolean = false): TorrentInfo {
    val sha = MessageDigest.getInstance("SHA-1")
    val piezas = ByteArrayOutputStream()
    var enPieza = 0
    fun mete(b: ByteArray, desde: Int = 0, n: Int = b.size) {
        var i = desde
        while (i < desde + n) {
            val k = minOf(tamPieza - enPieza, desde + n - i)
            sha.update(b, i, k)
            enPieza += k
            i += k
            if (enPieza == tamPieza) { piezas.write(sha.digest()); enPieza = 0 }
        }
    }
    val info = linkedMapOf<String, Any>("name" to ruta.name, "piece length" to tamPieza.toLong())
    if (ruta.isFile) {
        mete(ruta.readBytes())
        info["length"] = ruta.length()
    } else {
        val archivos = ruta.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(ruta).invariantSeparatorsPath }.toList()
        val lista = ArrayList<Any>()
        archivos.forEachIndexed { i, f ->
            mete(f.readBytes())
            lista += mapOf("length" to f.length(), "path" to f.relativeTo(ruta).invariantSeparatorsPath.split('/'))
            val falta = (tamPieza - enPieza) % tamPieza
            if (relleno && i < archivos.size - 1 && falta > 0) {
                mete(ByteArray(falta))
                lista += mapOf("attr" to "p", "length" to falta.toLong(), "path" to listOf(".pad", "$falta"))
            }
        }
        info["files"] = lista
    }
    if (enPieza > 0) piezas.write(sha.digest())
    info["pieces"] = piezas.toByteArray()
    return TorrentInfo.bdecode(bencode(mapOf("info" to info, "created by" to "pruebas de Mostaza")))
}

/** Bencode, lo justo para un .torrent: números, textos, bytes, listas y diccionarios (con las claves en orden). */
internal fun bencode(v: Any): ByteArray = ByteArrayOutputStream().also { escribe(it, v) }.toByteArray()

private fun escribe(o: ByteArrayOutputStream, v: Any) {
    when (v) {
        is Int -> o.write("i${v}e".toByteArray())
        is Long -> o.write("i${v}e".toByteArray())
        is String -> escribe(o, v.toByteArray(Charsets.UTF_8))
        is ByteArray -> { o.write("${v.size}:".toByteArray()); o.write(v) }
        is List<*> -> { o.write('l'.code); v.forEach { escribe(o, it!!) }; o.write('e'.code) }
        is Map<*, *> -> {
            o.write('d'.code)
            for (k in v.keys.map { it as String }.sortedBy { String(it.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1) }) {
                escribe(o, k)
                escribe(o, v[k]!!)
            }
            o.write('e'.code)
        }
        else -> error("No sé codificar $v")
    }
}

internal val TorrentInfo.hashV1: String get() = infoHashes().getV1().toHex()

/**
 * Otra sesión de libtorrent que siembra en 127.0.0.1, sin DHT ni nada que salga de este ordenador, y sube como mucho [subidaBs]
 * (también a los pares locales, que libtorrent no limita).
 */
internal class Sembrador(subidaBs: Int) : AutoCloseable {
    private val sm = SessionManager(false)

    init {
        val sp = SettingsPack().listenInterfaces("127.0.0.1:0").uploadRateLimit(subidaBs)
        sp.setEnableDht(false)
        sp.setEnableLsd(false)
        sp.setDhtBootstrapNodes("")
        sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false)
        sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false)
        // Con la E/S posix, como el motor: la de mmap pone un manejador de SIGSEGV que tumba la JVM (ver Sesion)
        sm.start(SessionParams(sp).apply { setPosixDiskIO() })
        val ec = error_code()
        val todas = ip_filter()
        todas.add_rule(address.from_string("0.0.0.0", ec), address.from_string("255.255.255.255", ec), 1)
        sm.swig().set_peer_class_filter(todas)
    }

    val puerto: Int get() = sm.swig().listen_port()

    /**
     * Siembra [ti], cuyos archivos están en [carpeta] (la que contiene el archivo o la carpeta del torrent), y espera a que libtorrent
     * los compruebe. (Sin SEED_MODE: libtorrent 2.1 da por mala la primera pieza que le piden en ese modo, lo comprueba todo otra vez
     * y entretanto cuelga a quien la pidió.)
     */
    fun siembra(ti: TorrentInfo, carpeta: File): TorrentHandle {
        val p = AddTorrentParams()
        p.torrentInfo = ti
        p.savePath = carpeta.absolutePath
        p.flags = p.flags.and_(TorrentFlags.AUTO_MANAGED.inv()).and_(TorrentFlags.PAUSED.inv())
        val ec = error_code()
        val th = TorrentHandle(sm.swig().add_torrent(p.swig(), ec))
        check(ec.value() == 0) { ec.message() }
        check(hastaQue(20_000) { th.status(true).isSeeding }) { "El sembrador no tiene los archivos: ${th.status(true).state()}" }
        return th
    }

    override fun close() = sm.stop()
}

internal class Respuesta(val codigo: Int, val cabeceras: Map<String, String>, val cuerpo: ByteArray)

internal fun pide(url: String, rango: String? = null, metodo: String = "GET"): Respuesta {
    val c = URL(url).openConnection() as HttpURLConnection
    c.requestMethod = metodo
    c.connectTimeout = 5_000
    c.readTimeout = 60_000
    rango?.let { c.setRequestProperty("Range", it) }
    try {
        val codigo = c.responseCode
        val cab = c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(",") }
        val cuerpo = (if (codigo >= 400) c.errorStream else c.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
        return Respuesta(codigo, cab, cuerpo)
    } finally {
        c.disconnect()
    }
}

/** Espera (como mucho [ms]) a que se cumpla [que]. */
internal fun hastaQue(ms: Long = 10_000, que: () -> Boolean): Boolean {
    val fin = System.currentTimeMillis() + ms
    while (System.currentTimeMillis() < fin) {
        if (que()) return true
        Thread.sleep(50)
    }
    return que()
}

internal fun hilosDelMotor(): List<String> = Thread.getAllStackTraces().keys
    .filter { it.isAlive && (it.name.startsWith("torrent-http") || it.name.startsWith("SessionManager")) }
    .map { it.name }
