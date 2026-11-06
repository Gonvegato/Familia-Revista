package com.gonzalo.hiedra.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.KDrive
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Sitio
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/** Lo de «Tu PC» que es de este ordenador (no viaja con la cuenta): qué deja hacer desde el móvil y adónde van las capturas. */
object AjustesTuPc {
    private fun archivo() = File(Pc.dirPruebas ?: Sitio.de("hiedra"), "tupc.json")
    private val m: Map<String, Any?> get() = runCatching { Json.lee(archivo().readText()).comoMapa() }.getOrDefault(emptyMap())
    private fun pon(k: String, v: Any) { runCatching { archivo().writeText(Json.escribe(m + (k to v))) } }

    /** El móvil puede pedir una captura de esta pantalla (como en Atrio del PC, sí salvo que lo quites). */
    var capturasDesdeMovil by mutableStateOf(true); private set
    /** Las capturas que haces aquí también van al móvil (las que pide el móvil van siempre). */
    var capturasAlMovil by mutableStateOf(true); private set
    /** Una copia de cada captura en tu kDrive (si está conectado), en «Mi carpeta/Hiedra/Capturas/<juego>». */
    var capturasAKDrive by mutableStateOf(true); private set
    /** El móvil puede preparar el streaming (abrir Steam y el juego) para jugar con Steam Link. */
    var streamingDesdeMovil by mutableStateOf(true); private set

    init { recarga() }

    /** Vuelve a leer los ajustes (al arrancar; en las pruebas, al cambiar de carpeta). */
    fun recarga() {
        val a = m
        capturasDesdeMovil = a["capturas"] as? Boolean ?: true
        capturasAlMovil = a["alMovil"] as? Boolean ?: true
        capturasAKDrive = a["kdrive"] as? Boolean ?: true
        streamingDesdeMovil = a["streaming"] as? Boolean ?: true
    }

    fun ponCapturasDesdeMovil(v: Boolean) { capturasDesdeMovil = v; pon("capturas", v); Thread { TuPc.publica() }.start() }
    fun ponCapturasAlMovil(v: Boolean) { capturasAlMovil = v; pon("alMovil", v) }
    fun ponCapturasAKDrive(v: Boolean) { capturasAKDrive = v; pon("kdrive", v) }
    fun ponStreamingDesdeMovil(v: Boolean) { streamingDesdeMovil = v; pon("streaming", v); Thread { TuPc.publica() }.start() }
}

/**
 * Las capturas de este ordenador, como las hacía Atrio del PC (suite.js, takeAndUpload), para que el móvil las reciba igual:
 *
 *  - La imagen tal cual (PNG sin pérdida, a la resolución de la pantalla), en tu carpeta de Imágenes: `Imágenes/Hiedra/<juego>/<juego>
 *    AAAA-MM-DD HH.MM.SS.png` (la carpeta de Imágenes es la de tu escritorio, `xdg-user-dir PICTURES`; en el Mac, `~/Pictures`). Un índice
 *    de las de aquí, en `capturas.json` (de este ordenador).
 *  - En la cuenta, para el móvil: los datos en `suite/shots/<id>` (`id`, `dev`, `pc`, `w`, `h`, `mime`, `bytes`, `n`, `up`, `done`, `m`,
 *    `name`, `by`, `game: {n, k, sg}`, `t`) y la imagen en base64 a trozos en `shotdata/<id>/<i>` (cada trozo se decodifica solo: son de
 *    [TROZO] caracteres, múltiplo de 4), subidos de [TANDA] en [TANDA] con `up` (los que van) y al final `done: true`. El móvil la baja
 *    entera, comprueba `bytes` y apunta en `got/<dispositivo>` que ya la tiene; entonces aquí se borran los trozos ([limpia]: `gone: true`).
 *  - Una copia en tu kDrive, si lo tienes conectado (en segundo plano).
 */
object Capturas {
    /** Caracteres de base64 por trozo (Firebase admite hasta 10 MB por texto). */
    const val TROZO = 512 * 1024
    /** Trozos por petición. */
    const val TANDA = 6
    /** Los trozos de una captura que nadie ha bajado se borran a la semana; la ficha (sin la imagen), al mes. */
    const val GUARDAR_MS = 7 * 86_400_000L
    const val FICHA_MS = 30 * 86_400_000L

    /** (En las pruebas: otra carpeta de Imágenes y otra manera de capturar.) */
    @Volatile var imagenesPruebas: File? = null
    @Volatile var capturaPruebas: (() -> Captura.Imagen)? = null

    /** Cambia con cada captura hecha aquí (leído desde Compose, repinta). */
    var version by mutableIntStateOf(0)
        private set

    /** Un nombre que vale para una carpeta o un archivo en cualquier sistema («Escritorio» si no hay juego). */
    fun seguro(s: String?): String = s.orEmpty().replace(Regex("[\\\\/:*?\"<>|]+"), " ").replace(Regex("\\s+"), " ").trim().take(80).trim().ifBlank { "Escritorio" }

    private val FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")
    /** «Hades 2026-11-06 21.04.09.png» */
    fun nombre(juego: String?, cuando: LocalDateTime, ext: String) = "${seguro(juego)} ${cuando.format(FORMATO)}.$ext"

    /**
     * La carpeta de Imágenes de tu escritorio: la que dice `xdg-user-dir PICTURES` ([xdg]), si no la de `user-dirs.dirs` ([userDirs]), si no
     * `~/Imágenes` (si existe) o `~/Pictures`. (xdg-user-dir dice tu carpeta personal cuando no hay ninguna puesta: esa no vale.)
     */
    fun carpetaImagenes(casa: File, xdg: String?, userDirs: String?, existe: (File) -> Boolean = File::isDirectory, mac: Boolean = Sitio.sistema == Sitio.Sistema.MAC): File {
        if (mac) return File(casa, "Pictures")
        fun vale(p: String?): File? = p?.trim()?.takeIf { it.startsWith("/") }?.let { File(it) }?.takeIf { it.absoluteFile != casa.absoluteFile }
        vale(xdg)?.let { return it }
        userDirs?.lines()?.map { it.trim() }?.firstOrNull { it.startsWith("XDG_PICTURES_DIR=") }?.let { l ->
            vale(l.substringAfter('=').trim().removeSurrounding("\"").replace("\$HOME", casa.path).replace("\${HOME}", casa.path))?.let { return it }
        }
        return File(casa, "Imágenes").takeIf(existe) ?: File(casa, "Pictures")
    }

    /** La carpeta de Imágenes de este ordenador. */
    fun imagenes(): File {
        imagenesPruebas?.let { return it }
        val casa = File(System.getProperty("user.home"))
        if (Sitio.sistema == Sitio.Sistema.MAC) return File(casa, "Pictures")
        val xdg = if (Programas.enPath("xdg-user-dir")) Programas.salida(listOf("xdg-user-dir", "PICTURES"), 3) else null
        val conf = File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: File(casa, ".config").path, "user-dirs.dirs")
        return carpetaImagenes(casa, xdg, runCatching { conf.readText() }.getOrNull())
    }

    /** Donde van las capturas de Hiedra: `Imágenes/Hiedra`. */
    fun carpeta(): File = File(imagenes(), "Hiedra")

    // ---------------------------------------------------------------- las de este ordenador

    /** Una captura hecha aquí. [archivo]: dónde quedó (null si no se pudo guardar). [id]: el de la cuenta. */
    class Hecha(val id: String, val archivo: File?, val w: Int, val h: Int, val mime: String, val bytes: Long, val metodo: String, val juego: String, val t: Long, val subida: Boolean)

    private fun indice() = File(Pc.dirPruebas ?: Sitio.de("hiedra"), "capturas.json")
    @Synchronized private fun apunta(h: Hecha) {
        val l = runCatching { Json.lee(indice().readText()).comoLista() }.getOrDefault(emptyList()).map { it.comoMapa() }
        val nueva = mapOf("id" to h.id, "f" to h.archivo?.path.orEmpty(), "t" to h.t, "n" to h.juego, "w" to h.w, "h" to h.h, "mime" to h.mime, "bytes" to h.bytes, "m" to h.metodo)
        runCatching { indice().writeText(Json.escribe((l + nueva).takeLast(500))) }
    }

    /** Las últimas capturas de este ordenador (las que siguen en su sitio), la última primero. */
    fun recientes(max: Int = 12): List<Hecha> = runCatching { Json.lee(indice().readText()).comoLista() }.getOrDefault(emptyList()).map { it.comoMapa() }
        .map { Hecha(it["id"].comoTexto(), it["f"].comoTexto().ifBlank { null }?.let(::File), it["w"].comoLong().toInt(), it["h"].comoLong().toInt(), it["mime"].comoTexto(), it["bytes"].comoLong(), it["m"].comoTexto(), it["n"].comoTexto(), it["t"].comoLong(), false) }
        .filter { it.archivo?.isFile == true }.sortedByDescending { it.t }.take(max)

    /** Un id como los de Atrio: el momento en base 36 y seis cifras al azar. */
    fun idNuevo(ahora: Long = System.currentTimeMillis()): String = ahora.toString(36) + "%06x".format(java.security.SecureRandom().nextInt(1 shl 24))

    @Volatile private var enCurso: CompletableFuture<Hecha>? = null

    /**
     * Hace una captura: la guarda en Imágenes/Hiedra, la apunta y (si [alMovil] y hay cuenta) la sube para el móvil; la copia del kDrive, en
     * segundo plano. Bloquea hasta que está subida. [por]: quién la pidió (lo que se apunta en `by`: «Hiedra · <móvil>» o «Hiedra»).
     * Si ya se está haciendo una, espera a esa y devuelve la misma (o, si hacía falta para el móvil y esa no ha ido, hace otra).
     */
    fun haz(por: String? = null, alMovil: Boolean = AjustesTuPc.capturasAlMovil): Hecha {
        val nueva = CompletableFuture<Hecha>()
        val otra = synchronized(this) { enCurso ?: run { enCurso = nueva; null } }
        if (otra != null) {
            val h = try { otra.get() } catch (e: ExecutionException) { throw e.cause ?: e }
            return if (alMovil && !h.subida && LaFamilia.iniciada && LaFamilia.dentro) haz(por, alMovil) else h
        }
        try {
            val h = hazYa(por, alMovil)
            synchronized(this) { enCurso = null }
            nueva.complete(h)
            return h
        } catch (e: Throwable) {
            synchronized(this) { enCurso = null }
            nueva.completeExceptionally(e); throw e
        }
    }

    private fun hazYa(por: String?, alMovil: Boolean): Hecha {
        val p = Pc.ahora
        val img = (capturaPruebas ?: { Captura.toma() })()
        val id = idNuevo()
        val ext = if (img.mime == "image/jpeg") "jpg" else "png"
        val nombre = nombre(p?.nombre, LocalDateTime.now(), ext)
        val t = System.currentTimeMillis()
        // Copia en este ordenador (Imágenes/Hiedra/<juego>)
        val archivo = runCatching { File(carpeta(), seguro(p?.nombre)).also { it.mkdirs() }.let { File(it, nombre) }.also { it.writeBytes(img.bytes) } }.getOrNull()
        val aLaCuenta = alMovil && LaFamilia.iniciada && LaFamilia.dentro
        val h = Hecha(id, archivo, img.w, img.h, img.mime, img.bytes.size.toLong(), img.metodo, p?.nombre.orEmpty(), t, aLaCuenta)
        apunta(h)
        javax.swing.SwingUtilities.invokeLater { version++ }
        // A tu kDrive, en segundo plano
        if (AjustesTuPc.capturasAKDrive && LaFamilia.iniciada && LaFamilia.dentro) Thread({ runCatching { aKDrive(img.bytes, nombre, p?.nombre) } }, "hiedra-captura-kdrive").apply { isDaemon = true; start() }
        // A la cuenta, por trozos, para que el móvil la baje tal cual (sin recomprimir)
        if (aLaCuenta) sube(id, img, nombre, por ?: "Hiedra", p)
        return h
    }

    private fun base() = "/users/${LaFamilia.uid}"

    /** Lo que se apunta en `suite/shots/<id>` antes de subir los trozos (lo que lee el móvil; `t` lo pone el servidor). */
    fun ficha(id: String, img: Captura.Imagen, nombre: String, por: String, juego: Pc.Partida?, n: Int): Map<String, Any?> = linkedMapOf(
        "id" to id, "dev" to Juegos.dispositivo(), "pc" to Sitio.nombreEquipo, "w" to img.w, "h" to img.h, "mime" to img.mime, "bytes" to img.bytes.size,
        "n" to n, "up" to 0, "done" to false, "m" to img.metodo, "name" to nombre, "by" to por,
        "game" to juego?.let { mapOf("n" to it.nombre, "k" to it.clave, "sg" to 0L) }, "t" to mapOf(".sv" to "timestamp"))

    /** Cuántos trozos de [trozo] caracteres de base64 salen de [tamano] bytes. */
    fun cuantos(tamano: Int, trozo: Int = TROZO): Int { require(trozo > 0 && trozo % 4 == 0) { "el trozo tiene que ser múltiplo de 4" }; val b = trozo / 4 * 3; return (tamano + b - 1) / b }

    /**
     * El trozo [i] de una imagen en base64: lo mismo que cortar el base64 entero cada [trozo] caracteres (como Atrio), pero sin tenerlo
     * entero en memoria (una captura en 4K son decenas de MB): cada trozo es el base64 de sus 3·[trozo]/4 bytes, y se decodifica solo.
     */
    fun parte(bytes: ByteArray, i: Int, trozo: Int = TROZO): String {
        val b = trozo / 4 * 3
        return Base64.getEncoder().encodeToString(bytes.copyOfRange(i * b, minOf(bytes.size, (i + 1) * b)))
    }

    /** Todos los trozos (para las pruebas). */
    fun trozos(bytes: ByteArray, trozo: Int = TROZO): List<String> = (0 until cuantos(bytes.size, trozo)).map { parte(bytes, it, trozo) }

    /** Sube una captura a la cuenta para el móvil (bloquea hasta el final). */
    fun sube(id: String, img: Captura.Imagen, nombre: String, por: String, juego: Pc.Partida?, trozo: Int = TROZO, tanda: Int = TANDA) {
        val n = cuantos(img.bytes.size, trozo)
        LaFamilia.db("PUT", "${base()}/suite/shots/$id", Json.escribe(ficha(id, img, nombre, por, juego, n)), "print=silent")
        for (i in 0 until n step tanda) {
            val hasta = minOf(n, i + tanda)
            LaFamilia.db("PATCH", "${base()}/shotdata/$id", Json.escribe((i until hasta).associate { it.toString() to parte(img.bytes, it, trozo) }), "print=silent")
            LaFamilia.db("PATCH", "${base()}/suite/shots/$id", Json.escribe(mapOf("up" to hasta)), "print=silent")
        }
        LaFamilia.db("PATCH", "${base()}/suite/shots/$id", Json.escribe(mapOf("done" to true)), "print=silent")
    }

    /** La copia en tu kDrive: «Mi carpeta/Hiedra/Capturas/<juego>/<nombre>». */
    private fun aKDrive(bytes: ByteArray, nombre: String, juego: String?) {
        val k = KDrive()
        if (!k.activo) return
        k.sube("Hiedra/Capturas/${seguro(juego)}", nombre, bytes.size.toLong()) { bytes.inputStream() }
    }

    /**
     * Limpieza (cada rato): de las capturas de este ordenador, los trozos que el móvil ya ha bajado (o con más de una semana) se borran y la
     * captura queda con `gone: true`; la ficha, al mes. [ahora]: el reloj (para las pruebas).
     */
    fun limpia(ahora: Long = System.currentTimeMillis()) {
        val t = LaFamilia.db("GET", "${base()}/suite/shots") ?: return
        val yo = Juegos.dispositivo()
        Json.lee(t).comoMapa().forEach { (id, v) ->
            val s = v.comoMapa()
            if (s["dev"].comoTexto() != yo) return@forEach
            val vieja = ahora - s["t"].comoLong() > GUARDAR_MS
            val gone = s["gone"] == true
            if (!gone && (s["got"].comoMapa().isNotEmpty() || vieja)) runCatching {
                LaFamilia.db("DELETE", "${base()}/shotdata/$id")
                LaFamilia.db("PATCH", "${base()}/suite/shots/$id", Json.escribe(mapOf("gone" to true)), "print=silent")
            } else if (gone && ahora - s["t"].comoLong() > FICHA_MS) runCatching { LaFamilia.db("DELETE", "${base()}/suite/shots/$id") }
        }
    }
}
