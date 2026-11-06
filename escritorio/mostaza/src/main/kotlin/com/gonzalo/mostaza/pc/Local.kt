package com.gonzalo.mostaza.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.torrent.AjustesTorrent as AjustesMotor
import com.gonzalo.torrent.ErrorTorrent
import com.gonzalo.torrent.MotorTorrent
import com.gonzalo.torrent.NativaTorrent
import com.gonzalo.torrent.motorDeEscritorio
import java.io.File

/**
 * Lo que es de ESTE ordenador y no viaja (`nube/ESQUEMA.md`, «No viaja»): el punto para seguir de lo que aún no está en tu biblioteca, lo
 * que mide la conexión y lo que puede ocupar la caché de torrents. En la carpeta de Mostaza (`Sitio.de("mostaza")`), en un JSON pequeño.
 */
object Local {
    /** En las pruebas: otra carpeta. */
    @Volatile var dirPruebas: File? = null
    val dir: File get() = dirPruebas?.also { it.mkdirs() } ?: Sitio.de("mostaza")
    private fun archivo() = File(dir, "local.json")
    @Volatile private var datos: MutableMap<String, Any?>? = null

    @Synchronized private fun todo(): MutableMap<String, Any?> = datos ?: LinkedHashMap(runCatching { Json.lee(archivo().readText()).comoMapa() }.getOrDefault(emptyMap())).also { datos = it }

    @Synchronized fun lee(k: String): Any? = todo()[k]
    @Synchronized fun pon(k: String, v: Any?) {
        val t = todo(); if (v == null) t.remove(k) else t[k] = v
        runCatching { val tmp = File(dir, "local.json.parcial"); tmp.writeText(Json.escribe(t)); if (!tmp.renameTo(archivo())) { archivo().writeText(Json.escribe(t)); tmp.delete() } }
    }

    fun olvidaParaPruebas() { datos = null }
}

/** Dónde lo dejaste en ESTE ordenador (también lo que no está en tu biblioteca: eso no va a la cuenta hasta que entra). */
object Reanudar {
    private fun clave(v: String) = "reanudar:$v"

    fun pon(video: String, pos: Long, dur: Long, ahora: Long = System.currentTimeMillis()) = Local.pon(clave(video), "$pos;$dur;$ahora")

    /** (posición, duración, cuándo) de aquí. */
    fun de(video: String): Triple<Long, Long, Long>? = (Local.lee(clave(video)) as? String)?.split(';')?.let { s ->
        if (s.size < 3) null else Triple(s[0].toLongOrNull() ?: 0L, s[1].toLongOrNull() ?: 0L, s[2].toLongOrNull() ?: 0L)
    }

    /**
     * Desde dónde seguir: lo más reciente entre lo de la cuenta (lo visto en otro dispositivo) y lo de aquí. Al final (visto) o al
     * principio, desde el principio. (Lo mismo que en el móvil.)
     */
    fun desde(video: String): Long {
        val cuenta = Biblioteca.progreso(video)
        val aqui = de(video)
        val (pos, dur) = when {
            cuenta != null && (aqui == null || cuenta.ultima >= aqui.third) -> cuenta.pos to cuenta.dur
            aqui != null -> aqui.first to aqui.second
            else -> return 0L
        }
        if (pos < 30_000L) return 0L
        if (dur > 0 && pos >= dur * 0.97) return 0L
        return pos
    }
}

/**
 * La conexión de ESTE ordenador (no viaja): lo medido al ver cosas (una media que se va corrigiendo y que baja en cuanto algo se corta).
 * Un ordenador no tiene «datos del móvil»: para el elector nunca es una red de pago. Y mpv sabe reproducir casi todo (HEVC, AV1, VP9, HDR
 * y Dolby Vision con su salida de vídeo de ahora), así que lo que sabe decodificar no limita nada.
 */
object Conexion {
    fun medido(): Long = Local.lee("kbps").comoLong()

    /** Apunta lo medido al ver algo ([kbps]); si se ha cortado ([corte]), se queda con lo más bajo (y un poco menos). Igual que en el móvil. */
    fun apunta(kbps: Long, corte: Boolean = false) {
        if (kbps <= 0) return
        val antes = medido()
        val nuevo = when {
            corte -> minOf(if (antes > 0) antes else kbps, kbps) * 85 / 100
            antes <= 0 -> kbps
            else -> (antes * 7 + kbps * 3) / 10
        }
        Local.pon("kbps", nuevo.coerceAtLeast(300L))
    }

    fun red(): Red = medido().let { Red(it, movil = false, medida = it > 0) }

    val capacidades = Capacidades(hevc = true, av1 = true, vp9 = true, hdr = true, dv = true, maxima = 2160)
}

/** Las fuentes que no dan la película sino un vídeo de aviso del debrid (como `FuentesRotas` del móvil; en `ajustes/mostaza_rotas`). */
object FuentesRotas {
    const val NODO = "mostaza_rotas"
    const val MAX_AVISO_MS = 4 * 60_000L
    const val CADUCA_MS = 30L * 24 * 3600_000L

    /**
     * ¿Es un vídeo de aviso y no lo que se quería ver? Dura algo y menos de [MAX_AVISO_MS], y lo que se quería ver (según su addon, en
     * minutos; 0 = no se sabe) dura más de 10 minutos o no se sabe.
     */
    fun esAviso(duracionMs: Long, minutosEsperados: Int): Boolean =
        duracionMs in 1 until MAX_AVISO_MS && (minutosEsperados <= 0 || minutosEsperados > 10)

    /** El campo de la cuenta de una fuente: «r» y 24 cifras hexadecimales de la huella SHA-256 de su clave (sin secretos). */
    fun campo(clave: String): String {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(clave.toByteArray())
        return "r" + d.take(12).joinToString("") { "%02x".format(it) }
    }

    fun rota(cuando: Long, ahora: Long) = cuando > 0 && ahora - cuando < CADUCA_MS

    /** Para el elector: clave de una fuente → ¿da un aviso? */
    fun busca(): (String) -> Boolean {
        val x = AjustesCuenta.todos(NODO); val ahora = System.currentTimeMillis()
        return { k -> k.isNotBlank() && rota(x[campo(k)].comoLong(), ahora) }
    }

    fun apunta(clave: String) {
        if (clave.isBlank()) return
        AjustesCuenta.pon(NODO, mapOf(campo(clave) to System.currentTimeMillis()))
    }
}

/** Un torrent que no se puede poner (el texto es para enseñártelo tal cual). */
class SinTorrent(msg: String) : Exception(msg)

/**
 * Los torrents en el ordenador: el motor de la familia (`escritorio/torrent`, los mismos archivos que el del móvil, con libtorrent4j y su
 * biblioteca de Linux x86-64 o del Mac con Apple Silicon). Convierte un torrent en una dirección local (http://127.0.0.1:…) que mpv abre
 * como cualquier otra. Si la biblioteca no se puede cargar en este ordenador, no hay torrents: el elector no los elige y se dice por qué.
 */
object Torrents {
    const val AVISO = "No se ha podido poner en marcha el motor de torrents en este ordenador. Elige una fuente directa o de tu debrid (las que no dicen «torrent»): con tu debrid puesto en tu addon, salen las mismas películas sin torrent."
    /** Para el motor, «sin límite» de subida es esto (unos 1 GB/s): el motor nunca sube sin límite. */
    const val SIN_LIMITE_KBS = 1_000_000
    /** En las pruebas: sin motor (para no cargar la biblioteca nativa). */
    @Volatile var apagado = false

    @Volatile private var motor: MotorTorrent? = null
    @Volatile private var fallo = false

    fun cacheMB(): Int = Local.lee("torrentCacheMB").comoLong().toInt().takeIf { it > 0 }?.coerceIn(512, 65536) ?: 4096
    fun ponCacheMB(mb: Int) { Local.pon("torrentCacheMB", mb.toLong()); motor?.ajusta(ajustes()) }

    /** Lo de tu cuenta en lo que entiende el motor (como en el móvil: sin compartir, la subida en lo mínimo; al parar, se borra). */
    fun ajustes() = AjustesMotor(
        soloWifi = false,
        subidaMaxKBs = when { !AjustesTorrents.compartir() -> 0; AjustesTorrents.subida() <= 0 -> SIN_LIMITE_KBS; else -> AjustesTorrents.subida() },
        bajadaMaxKBs = AjustesTorrents.bajada(),
        cacheMaxMB = cacheMB(),
        seguirCompartiendoAlParar = false,
    )

    /** ¿Hay motor en este ordenador? (lo pone en marcha la primera vez; sin red, no hace nada todavía). */
    val disponible: Boolean get() = !apagado && (motor != null || (!fallo && arranca() != null))

    @Synchronized private fun arranca(): MotorTorrent? {
        motor?.let { return it }
        if (fallo || apagado) return null
        val m = runCatching {
            if (!NativaTorrent.prepara(File(Local.dir, "nativas"))) error("sin biblioteca")
            // (una llamada a la biblioteca: si no se puede cargar aquí, falla ya y no al abrir un torrent)
            org.libtorrent4j.LibTorrent.version()
            motorDeEscritorio(File(Local.dir, "torrents"), ajustes())
        }.getOrNull()
        if (m == null) fallo = true else motor = m
        return m
    }

    /** La dirección local del torrent, para mpv. Lanza [SinTorrent] con un texto para ti si no se puede. */
    suspend fun prepara(infoHash: String, fileIdx: Int?, fuentes: List<String>, nombre: String?): String {
        val m = arranca() ?: throw SinTorrent(AVISO)
        return try { m.prepara(infoHash, fileIdx, fuentes, nombre).url } catch (e: ErrorTorrent) { throw SinTorrent(e.message ?: "No se ha podido abrir el torrent.") }
    }

    fun para(infoHash: String) { runCatching { motor?.para(infoHash) } }
    fun ajustesCambiados() { runCatching { motor?.ajusta(ajustes()) } }
    fun paraTodo() { runCatching { motor?.paraTodo() } }
}
