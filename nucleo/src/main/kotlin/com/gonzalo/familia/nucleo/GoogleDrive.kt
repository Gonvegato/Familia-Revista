package com.gonzalo.familia.nucleo

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Tu Google Drive (API REST v3, OAuth2 con `refresh_token`): sirve igual en Android y en el ordenador (Kotlin puro).
 *
 * Las credenciales viven una sola vez en `/claves/<uid>` de tu cuenta de la familia:
 *  - `gdrive_client_id`: Client ID del cliente OAuth (tipo «Desktop app») en Google Cloud Console.
 *  - `gdrive_client_secret`: Client Secret de ese cliente.
 *  - `gdrive_refresh_token`: token de refresco generado una vez con `herramientas/gdrive/autorizar.py`.
 *  - `gdrive_carpeta` (opcional): id (o URL) de la carpeta raíz en tu Drive; vacía, usa o crea «Familia» en «Mi unidad».
 */
class GoogleDrive(
    private val clientId: () -> String,
    private val clientSecret: () -> String,
    private val refreshToken: () -> String,
    private val carpetaConfig: () -> String = { "" },
    val api: String = System.getProperty("familia.gdrive") ?: "https://www.googleapis.com",
    val uploadApi: String = System.getProperty("familia.gdrive.upload") ?: api,
    val tokenUrl: String = System.getProperty("familia.gdrive.token") ?: "https://oauth2.googleapis.com/token",
    val webBase: String = System.getProperty("familia.gdrive.web") ?: "https://drive.google.com",
) {
    class Fallo(msg: String) : IOException(msg)

    class Archivo(
        val id: String,
        val nombre: String,
        val carpeta: Boolean,
        val tamano: Long = 0L,
        val mime: String = "",
        val padres: List<String> = emptyList(),
        val miniatura: String = "",
    )

    val activo: Boolean
        get() = clientId().isNotBlank() && clientSecret().isNotBlank() && refreshToken().isNotBlank()

    @Volatile private var accessToken = ""
    @Volatile private var caducaEn = 0L
    @Volatile private var huellaCreds = ""
    @Volatile private var raizId = ""
    private val cacheCarpetas = ConcurrentHashMap<String, String>()
    private val cerrojoToken = Any()

    private fun limpiaCacheSiCambiaCreds() {
        val h = "${clientId()}|${refreshToken()}|${carpetaConfig()}"
        if (h != huellaCreds) {
            huellaCreds = h
            accessToken = ""
            caducaEn = 0L
            raizId = ""
            cacheCarpetas.clear()
        }
    }

    /** Invalida el access token en memoria (p. ej. tras un 401). */
    fun invalidaPase() = synchronized(cerrojoToken) {
        accessToken = ""
        caducaEn = 0L
    }

    /** Devuelve un access token válido (lo renueva automáticamente con el `refresh_token` si caduca). */
    fun paseValido(forzar: Boolean = false): String = synchronized(cerrojoToken) {
        limpiaCacheSiCambiaCreds()
        if (!activo) throw Fallo("Google Drive no está configurado")
        val ahora = System.currentTimeMillis()
        if (!forzar && accessToken.isNotBlank() && ahora < caducaEn - 120_000L) return accessToken
        val cuerpo = "client_id=${enc(clientId().trim())}" +
            "&client_secret=${enc(clientSecret().trim())}" +
            "&refresh_token=${enc(refreshToken().trim())}" +
            "&grant_type=refresh_token"
        val c = abreSinAuth(tokenUrl)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        try {
            c.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val m = runCatching { Json.lee(txt) }.getOrNull().comoMapa()
            if (code !in 200..299) {
                val err = m["error"].comoTexto()
                val desc = m["error_description"].comoTexto()
                throw Fallo(when {
                    err == "invalid_grant" -> "Google Drive pide autorizar de nuevo (el token ha caducado o fue revocado)"
                    err == "invalid_client" -> "Client ID o Client Secret de Google Drive no válidos"
                    else -> "Google Drive OAuth ($code): ${desc.ifBlank { err.ifBlank { "error de acceso" } }}"
                })
            }
            val tok = m["access_token"].comoTexto()
            if (tok.isBlank()) throw Fallo("Google Drive no devolvió un token de acceso")
            val exp = m["expires_in"].comoLong().coerceAtLeast(120L)
            accessToken = tok
            caducaEn = System.currentTimeMillis() + exp * 1000L
            return tok
        } finally {
            c.disconnect()
        }
    }

    /** Cabeceras con autorización para reproductores (Media3 / mpv) o descargas autenticadas. */
    fun cabeceras(): Map<String, String> = mapOf("Authorization" to "Bearer ${paseValido()}")

    /** URL directa de descarga/streaming (soporta `Range` con la cabecera de [cabeceras]). */
    fun urlDescarga(fileId: String): String = "${api.trimEnd('/')}/drive/v3/files/${enc(fileId)}?alt=media&acknowledgeAbuse=true"

    private fun abreSinAuth(url: String, readTimeoutMs: Int = 60_000): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
        }

    private fun abre(url: String, token: String = paseValido(), readTimeoutMs: Int = 60_000): HttpURLConnection =
        abreSinAuth(url, readTimeoutMs).apply {
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun llamaJson(metodo: String, ruta: String, cuerpo: String? = null, base: String = api): Map<String, Any?> {
        var reintento = false
        while (true) {
            val tok = paseValido(forzar = reintento)
            val c = abre(base.trimEnd('/') + ruta, tok)
            try {
                if (metodo == "PATCH") {
                    c.requestMethod = "POST"
                    c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                } else {
                    c.requestMethod = metodo
                }
                if (cuerpo != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    c.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                if (code == 401 && !reintento) {
                    invalidaPase()
                    reintento = true
                    continue
                }
                val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val m = runCatching { Json.lee(txt) }.getOrNull().comoMapa()
                if (code !in 200..299) {
                    val msg = m["error"].comoMapa()["message"].comoTexto()
                    throw Fallo(when (code) {
                        401 -> "Google Drive no acepta las credenciales (revisa tu token)"
                        403 -> "Sin permiso en Google Drive${if (msg.isNotBlank()) ": $msg" else ""}"
                        404 -> "No encuentro ese archivo o carpeta en Google Drive"
                        else -> "Google Drive ($code): ${msg.ifBlank { txt.take(200) }}"
                    })
                }
                return m
            } finally {
                c.disconnect()
            }
        }
    }

    /** Comprueba la conexión y localiza (o crea) la carpeta raíz en Google Drive. */
    fun prueba(): String {
        invalidaPase()
        raizId = ""
        cacheCarpetas.clear()
        val id = carpetaRaiz()
        return "Conectado a tu Google Drive (carpeta $id)"
    }

    /**
     * La carpeta raíz de la familia en tu Google Drive: si configuraste `gdrive_carpeta`, usa esa; si no,
     * busca o crea la carpeta «Familia» en «Mi unidad» (`root`).
     */
    fun carpetaRaiz(): String {
        limpiaCacheSiCambiaCreds()
        if (raizId.isNotBlank()) return raizId
        val cfg = extraeIdCarpeta(carpetaConfig())
        if (cfg.isNotBlank()) {
            // Comprobar que existe y es accesible
            val m = llamaJson("GET", "/drive/v3/files/${enc(cfg)}?fields=id,name,mimeType,trashed")
            if (m["trashed"].comoBool()) throw Fallo("La carpeta raíz de Google Drive está en la papelera")
            raizId = m["id"].comoTexto().ifBlank { cfg }
            return raizId
        }
        val existente = buscaHijo("root", CARPETA_DEFECTO, carpeta = true)
        val id = existente?.id ?: creaCarpeta("root", CARPETA_DEFECTO)
        raizId = id
        return id
    }

    /** Lo que hay dentro de una carpeta (no borrado). */
    fun dentro(carpetaId: String): List<Archivo> {
        val out = ArrayList<Archivo>()
        var pageToken = ""
        val q = "'${escQuery(carpetaId)}' in parents and trashed = false"
        do {
            val pt = if (pageToken.isNotEmpty()) "&pageToken=${enc(pageToken)}" else ""
            val ruta = "/drive/v3/files?q=${enc(q)}&fields=${enc("nextPageToken,files(id,name,mimeType,size,parents,thumbnailLink)")}&pageSize=1000$pt"
            val o = llamaJson("GET", ruta)
            o["files"].comoLista().forEach { x -> out += aArchivo(x.comoMapa()) }
            pageToken = o["nextPageToken"].comoTexto()
        } while (pageToken.isNotEmpty() && out.size < 5000)
        return out
    }

    /** Busca un hijo exacto por nombre dentro de [padreId]. */
    fun buscaHijo(padreId: String, nombre: String, carpeta: Boolean? = null): Archivo? {
        val filtroTipo = when (carpeta) {
            true -> " and mimeType = '$MIME_CARPETA'"
            false -> " and mimeType != '$MIME_CARPETA'"
            null -> ""
        }
        val q = "'${escQuery(padreId)}' in parents and name = '${escQuery(nombre)}' and trashed = false$filtroTipo"
        val ruta = "/drive/v3/files?q=${enc(q)}&fields=${enc("files(id,name,mimeType,size,parents,thumbnailLink)")}&pageSize=1"
        val lista = llamaJson("GET", ruta)["files"].comoLista()
        return lista.firstOrNull()?.let { aArchivo(it.comoMapa()) }
    }

    /** Crea una subcarpeta dentro de [padreId] y devuelve su id. */
    fun creaCarpeta(padreId: String, nombre: String): String {
        val body = Json.escribe(mapOf("name" to nombre, "mimeType" to MIME_CARPETA, "parents" to listOf(padreId)))
        val m = llamaJson("POST", "/drive/v3/files?fields=id", body)
        return m["id"].comoTexto().ifBlank { throw Fallo("Google Drive no creó la carpeta «$nombre»") }
    }

    /** Navega por [ruta] relativa a [carpetaRaiz] sin crear nada; null si alguna carpeta falta. */
    fun carpeta(ruta: String): String? {
        val limpia = ruta.trim('/').trim()
        if (limpia.isEmpty()) return carpetaRaiz()
        cacheCarpetas[limpia]?.let { return it }
        var actual = carpetaRaiz()
        val acum = StringBuilder()
        for (parte in limpia.split('/').filter { it.isNotBlank() }) {
            if (acum.isNotEmpty()) acum.append('/')
            acum.append(parte)
            val clave = acum.toString()
            val hit = cacheCarpetas[clave]
            if (hit != null) {
                actual = hit
            } else {
                val h = buscaHijo(actual, parte, carpeta = true) ?: return null
                cacheCarpetas[clave] = h.id
                actual = h.id
            }
        }
        return actual
    }

    /** Navega por [ruta] relativa a [carpetaRaiz], creando las carpetas que falten. */
    fun aseguraCarpeta(ruta: String): String {
        val limpia = ruta.trim('/').trim()
        if (limpia.isEmpty()) return carpetaRaiz()
        cacheCarpetas[limpia]?.let { return it }
        var actual = carpetaRaiz()
        val acum = StringBuilder()
        for (parte in limpia.split('/').filter { it.isNotBlank() }) {
            if (acum.isNotEmpty()) acum.append('/')
            acum.append(parte)
            val clave = acum.toString()
            val hit = cacheCarpetas[clave]
            if (hit != null) {
                actual = hit
            } else {
                val h = buscaHijo(actual, parte, carpeta = true)?.id ?: creaCarpeta(actual, parte)
                cacheCarpetas[clave] = h
                actual = h
            }
        }
        return actual
    }

    /** Los archivos de la carpeta `versiones` (para actualizar las apps). */
    fun versiones(carpetaVersiones: String = System.getenv("GDRIVE_CARPETA_VERSIONES")?.takeIf { it.isNotBlank() } ?: "versiones"): List<Archivo> {
        val dir = carpeta(carpetaVersiones) ?: return emptyList()
        return runCatching { dentro(dir).filter { !it.carpeta } }.getOrElse {
            cacheCarpetas.remove(carpetaVersiones.trim('/'))
            val d2 = carpeta(carpetaVersiones) ?: return emptyList()
            dentro(d2).filter { !it.carpeta }
        }
    }

    /**
     * Sube un archivo a `<carpetaRaiz>/[ruta]/[nombre]` creando las carpetas que falten.
     * Si ya existe un archivo con ese nombre en esa carpeta, actualiza su contenido (sin duplicados).
     * Usa subida resumible por trozos de 4 MB (múltiplo de 256 KB, como exige Google Drive).
     */
    fun sube(
        ruta: String,
        nombre: String,
        tamano: Long,
        progreso: (Long) -> Unit = {},
        entrada: () -> InputStream,
    ): String {
        val dirId = aseguraCarpeta(ruta)
        val existente = buscaHijo(dirId, nombre, carpeta = false)?.id
        if (tamano <= 0L) {
            return subeSimple(dirId, existente, nombre, ByteArray(0)).also { progreso(0L) }
        }
        val sesionUrl = iniciaSubidaResumible(dirId, existente, nombre, tamano)
        val trozoTam = TROZO_SUBIDA
        var enviado = 0L
        entrada().use { inp ->
            val buf = ByteArray(trozoTam)
            while (enviado < tamano) {
                val faltan = minOf(trozoTam.toLong(), tamano - enviado).toInt()
                var leidos = 0
                while (leidos < faltan) {
                    val n = inp.read(buf, leidos, faltan - leidos)
                    if (n < 0) throw Fallo("El archivo se acabó antes de tiempo")
                    leidos += n
                }
                val desde = enviado
                val hasta = enviado + leidos - 1
                var intentos = 0
                while (true) {
                    val c = abreSinAuth(sesionUrl, readTimeoutMs = 300_000)
                    try {
                        c.requestMethod = "PUT"
                        c.doOutput = true
                        c.setFixedLengthStreamingMode(leidos)
                        c.setRequestProperty("Content-Type", "application/octet-stream")
                        c.setRequestProperty("Content-Range", "bytes $desde-$hasta/$tamano")
                        c.outputStream.use { it.write(buf, 0, leidos) }
                        val code = c.responseCode
                        if (code == 308) {
                            // Trozo recibido; seguimos con el siguiente
                            enviado += leidos
                            progreso(enviado)
                            break
                        }
                        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                        if (code in 200..299) {
                            enviado += leidos
                            progreso(enviado)
                            val id = runCatching { Json.lee(txt) }.getOrNull().comoMapa()["id"].comoTexto()
                            return id.ifBlank { existente ?: throw Fallo("Google Drive no devolvió el id del archivo subido") }
                        }
                        if (code in 500..599 && ++intentos < 3) {
                            Thread.sleep(1500L * intentos)
                            continue
                        }
                        throw Fallo("Google Drive al subir ($code): ${txt.take(200)}")
                    } catch (e: IOException) {
                        if (e is Fallo || ++intentos >= 3) throw e
                        Thread.sleep(1500L * intentos)
                    } finally {
                        c.disconnect()
                    }
                }
            }
        }
        throw Fallo("Google Drive no cerró la subida de $nombre")
    }

    private fun subeSimple(dirId: String, existente: String?, nombre: String, datos: ByteArray): String {
        val meta = if (existente == null) Json.escribe(mapOf("name" to nombre, "parents" to listOf(dirId))) else Json.escribe(mapOf("name" to nombre))
        val url = if (existente == null) "${uploadApi.trimEnd('/')}/upload/drive/v3/files?uploadType=resumable&fields=id"
        else "${uploadApi.trimEnd('/')}/upload/drive/v3/files/${enc(existente)}?uploadType=resumable&fields=id"
        val c = abre(url)
        try {
            if (existente != null) { c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", "PATCH") } else c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
            c.setRequestProperty("X-Upload-Content-Length", datos.size.toString())
            c.outputStream.use { it.write(meta.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val loc = c.getHeaderField("Location")
            if (code !in 200..299 || loc.isNullOrBlank()) throw Fallo("Google Drive no abrió la subida ($code)")
            val u = abreSinAuth(loc)
            try {
                u.requestMethod = "PUT"
                u.doOutput = true
                u.setFixedLengthStreamingMode(datos.size)
                u.setRequestProperty("Content-Type", "application/octet-stream")
                u.outputStream.use { it.write(datos) }
                val uc = u.responseCode
                val txt = (if (uc in 200..299) u.inputStream else u.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (uc !in 200..299) throw Fallo("Google Drive al subir ($uc): ${txt.take(200)}")
                return runCatching { Json.lee(txt) }.getOrNull().comoMapa()["id"].comoTexto().ifBlank { existente ?: throw Fallo("Sin id") }
            } finally { u.disconnect() }
        } finally { c.disconnect() }
    }

    private fun iniciaSubidaResumible(dirId: String, existente: String?, nombre: String, tamano: Long): String {
        val meta = if (existente == null) Json.escribe(mapOf("name" to nombre, "parents" to listOf(dirId))) else Json.escribe(mapOf("name" to nombre))
        val ruta = if (existente == null) "/upload/drive/v3/files?uploadType=resumable&fields=id"
        else "/upload/drive/v3/files/${enc(existente)}?uploadType=resumable&fields=id"
        var reintento = false
        while (true) {
            val c = abre(uploadApi.trimEnd('/') + ruta, paseValido(forzar = reintento))
            try {
                if (existente != null) {
                    c.requestMethod = "POST"
                    c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                } else {
                    c.requestMethod = "POST"
                }
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
                c.setRequestProperty("X-Upload-Content-Length", tamano.toString())
                c.outputStream.use { it.write(meta.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                if (code == 401 && !reintento) {
                    invalidaPase()
                    reintento = true
                    continue
                }
                val loc = c.getHeaderField("Location")
                if (code !in 200..299 || loc.isNullOrBlank()) {
                    val txt = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw Fallo("Google Drive no inició la subida ($code): ${txt.take(200)}")
                }
                return loc
            } finally {
                c.disconnect()
            }
        }
    }

    /**
     * Baja un archivo a [dest] (primero a `.part` y luego renombrado). Los archivos grandes se bajan en 4 trozos en paralelo
     * si el servidor soporta `Range`, y si no, de una vez.
     */
    fun baja(fileId: String, dest: File, progreso: (Long, Long) -> Unit = { _, _ -> }) {
        if (runCatching { descargaEnTrozos(fileId, dest, progreso) }.getOrDefault(false)) return
        descargaSimple(fileId, dest, progreso)
    }

    private fun descargaEnTrozos(fileId: String, dest: File, progreso: (Long, Long) -> Unit): Boolean {
        val url = urlDescarga(fileId)
        val sonda = abre(url, readTimeoutMs = 300_000).apply { setRequestProperty("Range", "bytes=0-0") }
        val total = try {
            if (sonda.responseCode != 206) return false
            val t = sonda.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: return false
            sonda.inputStream.use { it.readBytes() }
            t
        } finally {
            sonda.disconnect()
        }
        if (total < MINIMO_EN_TROZOS) return false
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        RandomAccessFile(tmp, "rw").use { it.setLength(total) }
        val trozo = (total + TROZOS_BAJADA - 1) / TROZOS_BAJADA
        val bajado = AtomicLong(0L)
        val fallo = AtomicReference<Throwable?>(null)
        val hilos = (0 until TROZOS_BAJADA).map { i ->
            Thread {
                try {
                    val a = i * trozo
                    val b = minOf(total - 1, a + trozo - 1)
                    if (a > b) return@Thread
                    var intentos = 0
                    while (true) {
                        val c = abre(url, readTimeoutMs = 300_000).apply { setRequestProperty("Range", "bytes=$a-$b") }
                        try {
                            val code = c.responseCode
                            if (code == 401 && intentos == 0) { invalidaPase(); intentos++; continue }
                            if (code != 206) throw Fallo("Google Drive: error $code")
                            c.inputStream.use { inp ->
                                RandomAccessFile(tmp, "rw").use { f ->
                                    f.seek(a)
                                    val buf = ByteArray(64 * 1024)
                                    var pos = a
                                    while (true) {
                                        val n = inp.read(buf)
                                        if (n < 0) break
                                        f.write(buf, 0, n)
                                        pos += n
                                        progreso(bajado.addAndGet(n.toLong()), total)
                                    }
                                    if (pos != b + 1) throw IOException("trozo incompleto")
                                }
                            }
                            break
                        } catch (e: IOException) {
                            if (e is Fallo || ++intentos >= 3) throw e
                            Thread.sleep(1000L * intentos)
                        } finally {
                            c.disconnect()
                        }
                    }
                } catch (e: Throwable) {
                    fallo.compareAndSet(null, e)
                }
            }.also { it.start() }
        }
        hilos.forEach { it.join() }
        fallo.get()?.let { tmp.delete(); throw it }
        if (tmp.length() != total) { tmp.delete(); throw Fallo("Google Drive: la descarga quedó incompleta") }
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
        return true
    }

    private fun descargaSimple(fileId: String, dest: File, progreso: (Long, Long) -> Unit) {
        val url = urlDescarga(fileId)
        var reintento = false
        while (true) {
            val c = abre(url, paseValido(forzar = reintento), readTimeoutMs = 300_000)
            try {
                val code = c.responseCode
                if (code == 401 && !reintento) { invalidaPase(); reintento = true; continue }
                if (code !in 200..299) {
                    val txt = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw Fallo(when (code) {
                        404 -> "No encuentro ese archivo en Google Drive"
                        403 -> "Sin permiso para bajar ese archivo de Google Drive"
                        else -> "Google Drive ($code): ${txt.take(200)}"
                    })
                }
                val total = c.contentLengthLong
                dest.parentFile?.mkdirs()
                val tmp = File(dest.path + ".part")
                try {
                    c.inputStream.use { inp ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var hecho = 0L
                            while (true) {
                                val n = inp.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                hecho += n
                                progreso(hecho, total)
                            }
                        }
                    }
                    if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
                } catch (e: Throwable) {
                    tmp.delete()
                    throw e
                }
                return
            } finally {
                c.disconnect()
            }
        }
    }

    /** Crea un enlace de solo lectura para cualquiera con el enlace (para tu pareja). */
    fun enlacePublico(fileId: String): String = runCatching {
        llamaJson("POST", "/drive/v3/files/${enc(fileId)}/permissions", Json.escribe(mapOf("role" to "reader", "type" to "anyone")))
        "${webBase.trimEnd('/')}/file/d/$fileId/view?usp=sharing"
    }.getOrDefault("")

    /** Baja un archivo compartido públicamente de Google Drive (sin cuenta, para tu pareja). */
    fun descargaEnlace(enlace: String, dest: File) {
        val id = extraeIdEnlace(enlace) ?: throw Fallo("Ese enlace no es de Google Drive")
        val url = "${webBase.trimEnd('/')}/uc?export=download&confirm=t&id=${enc(id)}"
        val c = abreSinAuth(url, readTimeoutMs = 300_000)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw Fallo("El enlace de Google Drive ya no funciona (error $code)")
            dest.parentFile?.mkdirs()
            val tmp = File(dest.path + ".part")
            try {
                c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
            } catch (e: Throwable) {
                tmp.delete()
                throw e
            }
        } finally {
            c.disconnect()
        }
    }

    /** Busca archivos o carpetas por nombre en Google Drive (`tipo`: `"image"` o `"dir"`). */
    fun busca(texto: String, tipo: String = "image", max: Int = 300): List<Archivo> {
        val t = texto.trim()
        if (t.isEmpty()) return emptyList()
        val filtro = when (tipo) {
            "dir" -> "mimeType = '$MIME_CARPETA'"
            "image" -> "mimeType contains 'image/'"
            else -> "mimeType != '$MIME_CARPETA'"
        }
        val q = "name contains '${escQuery(t)}' and $filtro and trashed = false"
        val out = ArrayList<Archivo>()
        var pageToken = ""
        do {
            val pt = if (pageToken.isNotEmpty()) "&pageToken=${enc(pageToken)}" else ""
            val ruta = "/drive/v3/files?q=${enc(q)}&fields=${enc("nextPageToken,files(id,name,mimeType,size,parents,thumbnailLink)")}&pageSize=${minOf(max, 200)}$pt"
            val o = llamaJson("GET", ruta)
            o["files"].comoLista().forEach { x -> out += aArchivo(x.comoMapa()) }
            pageToken = o["nextPageToken"].comoTexto()
        } while (pageToken.isNotEmpty() && out.size < max)
        return out.take(max)
    }

    /** ¿Sigue existiendo este archivo (y no está en la papelera)? null si no se puede comprobar (sin red). */
    fun existe(fileId: String): Boolean? = try {
        val m = llamaJson("GET", "/drive/v3/files/${enc(fileId)}?fields=id,trashed")
        if (m["id"].comoTexto().isBlank()) null else !m["trashed"].comoBool()
    } catch (e: Fallo) {
        if (e.message?.startsWith("No encuentro") == true) false else null
    } catch (_: Exception) {
        null
    }

    /** Manda un archivo a la papelera de Google Drive. Si ya no existe, no falla. */
    fun borra(fileId: String) {
        try {
            llamaJson("PATCH", "/drive/v3/files/${enc(fileId)}", Json.escribe(mapOf("trashed" to true)))
        } catch (e: Fallo) {
            if (e.message?.startsWith("No encuentro") != true) throw e
        }
    }

    private fun aArchivo(m: Map<String, Any?>): Archivo {
        val mime = m["mimeType"].comoTexto()
        val sizeVal = m["size"]
        val tam = when (sizeVal) {
            is Number -> sizeVal.toLong()
            is String -> sizeVal.toLongOrNull() ?: 0L
            else -> 0L
        }
        return Archivo(
            id = m["id"].comoTexto(),
            nombre = m["name"].comoTexto(),
            carpeta = mime == MIME_CARPETA,
            tamano = tam,
            mime = mime,
            padres = m["parents"].comoLista().map { it.comoTexto() }.filter { it.isNotBlank() },
            miniatura = m["thumbnailLink"].comoTexto(),
        )
    }

    companion object {
        const val MIME_CARPETA = "application/vnd.google-apps.folder"
        const val CARPETA_DEFECTO = "Familia"
        /** Claves guardadas en `/claves/<uid>`. */
        const val CLAVE_CLIENT_ID = "gdrive_client_id"
        const val CLAVE_CLIENT_SECRET = "gdrive_client_secret"
        const val CLAVE_REFRESH_TOKEN = "gdrive_refresh_token"
        const val CLAVE_CARPETA = "gdrive_carpeta"

        /** Múltiplo de 256 KB (4 MB): lo que pide el protocolo resumible de Google Drive. */
        private const val TROZO_SUBIDA = 4 * 1024 * 1024
        private const val TROZOS_BAJADA = 4
        private const val MINIMO_EN_TROZOS = 4L * 1024 * 1024

        private fun enc(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
        private fun escQuery(v: String) = v.replace("\\", "\\\\").replace("'", "\\'")

        /** Extrae el id de carpeta si pegaste la URL entera (`…/folders/<id>`) o el id tal cual. */
        fun extraeIdCarpeta(s: String): String {
            val t = s.trim()
            if (t.isBlank()) return ""
            Regex("""/folders/([A-Za-z0-9_-]+)""").find(t)?.let { return it.groupValues[1] }
            Regex("""[?&]id=([A-Za-z0-9_-]+)""").find(t)?.let { return it.groupValues[1] }
            return t.substringBefore('?').substringBefore('/').trim()
        }

        /** Extrae el id de archivo de un enlace compartido de Google Drive. */
        fun extraeIdEnlace(enlace: String): String? {
            Regex("""/file/d/([A-Za-z0-9_-]+)""").find(enlace)?.let { return it.groupValues[1] }
            Regex("""[?&]id=([A-Za-z0-9_-]+)""").find(enlace)?.let { return it.groupValues[1] }
            return null
        }

        fun esEnlaceGoogleDrive(enlace: String): Boolean =
            enlace.contains("drive.google.com/", ignoreCase = true) && extraeIdEnlace(enlace) != null

        /**
         * Autorización OAuth2 sin terminal ni scripts (vale igual en Android y en escritorio):
         * abre un servidor local efímero en `127.0.0.1:<puerto>`, lanza el navegador con [abrirUrl],
         * espera la redirección de Google con el código y lo canjea por un `refresh_token`.
         *
         * Bloqueante (red y espera de usuario): llamar fuera del hilo principal.
         */
        fun autorizarEnNavegador(
            clientId: String,
            clientSecret: String,
            abrirUrl: (String) -> Unit,
            timeoutMs: Int = 180_000,
            authUrlBase: String = "https://accounts.google.com/o/oauth2/v2/auth",
            tokenUrl: String = "https://oauth2.googleapis.com/token",
        ): String {
            val cid = clientId.trim()
            val csec = clientSecret.trim()
            if (cid.isBlank() || csec.isBlank()) throw Fallo("Pega tu Client ID y tu Client Secret antes de continuar")
            val estado = java.util.UUID.randomUUID().toString().replace("-", "")
            val srv = java.net.ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
            srv.soTimeout = timeoutMs
            val redirectUri = "http://127.0.0.1:${srv.localPort}"
            val authUrl = authUrlBase +
                "?client_id=${enc(cid)}" +
                "&redirect_uri=${enc(redirectUri)}" +
                "&response_type=code" +
                "&scope=${enc("https://www.googleapis.com/auth/drive")}" +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=${enc(estado)}"
            val codigo = try {
                abrirUrl(authUrl)
                esperaCodigoOAuth(srv, estado, timeoutMs)
            } finally {
                runCatching { srv.close() }
            }
            return canjeaCodigoPorRefresh(cid, csec, codigo, redirectUri, tokenUrl)
        }

        private fun esperaCodigoOAuth(srv: java.net.ServerSocket, estadoEsperado: String, timeoutMs: Int): String {
            val limite = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < limite) {
                val restante = (limite - System.currentTimeMillis()).coerceAtLeast(500L).toInt()
                srv.soTimeout = restante
                val sock = try {
                    srv.accept()
                } catch (_: java.net.SocketTimeoutException) {
                    throw Fallo("Tiempo agotado esperando la autorización de Google. Vuelve a intentarlo.")
                }
                sock.use { s ->
                    s.soTimeout = 5_000
                    val primera = s.getInputStream().bufferedReader(Charsets.UTF_8).readLine().orEmpty()
                    val target = primera.split(' ').getOrNull(1).orEmpty()
                    if (target.startsWith("/favicon.ico")) {
                        val r = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        s.getOutputStream().write(r.toByteArray(Charsets.UTF_8))
                        return@use
                    }
                    val query = target.substringAfter('?', "")
                    val params = query.split('&').filter { '=' in it }.associate {
                        java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") to
                            java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8")
                    }
                    val err = params["error"]
                    val st = params["state"]
                    val code = params["code"]
                    val html = if (err == null && st == estadoEsperado && !code.isNullOrBlank()) {
                        "<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                            "<body style='font-family:sans-serif;padding:2rem;text-align:center'>" +
                            "<h2>¡Listo!</h2><p>Google Drive conectado. Ya puedes cerrar esta pestaña y volver a Jonsu.</p></body>"
                    } else {
                        "<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                            "<body style='font-family:sans-serif;padding:2rem;text-align:center'>" +
                            "<h2>No se pudo conectar</h2><p>${err ?: "Respuesta no válida"}. Puedes volver a Jonsu e intentarlo otra vez.</p></body>"
                    }
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    val hdr = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    s.getOutputStream().apply { write(hdr.toByteArray(Charsets.UTF_8)); write(bytes); flush() }
                    if (!err.isNullOrBlank()) throw Fallo("Google canceló el acceso ($err)")
                    if (st != estadoEsperado) throw Fallo("El estado de seguridad de Google no coincide")
                    if (!code.isNullOrBlank()) return code
                }
            }
            throw Fallo("Tiempo agotado esperando la autorización de Google.")
        }

        private fun canjeaCodigoPorRefresh(cid: String, csec: String, codigo: String, redirectUri: String, tokenUrl: String): String {
            val cuerpo = "code=${enc(codigo)}&client_id=${enc(cid)}&client_secret=${enc(csec)}&redirect_uri=${enc(redirectUri)}&grant_type=authorization_code"
            val c = (java.net.URL(tokenUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                c.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw Fallo("Google no aceptó el código ($code): ${txt.take(200)}")
                val m = runCatching { Json.lee(txt) }.getOrNull().comoMapa()
                val refresh = m["refresh_token"].comoTexto()
                if (refresh.isBlank()) throw Fallo("Google no devolvió refresh_token. Revoca «Familia» en myaccount.google.com/permissions y vuelve a intentarlo.")
                return refresh
            } finally {
                c.disconnect()
            }
        }
    }
}
