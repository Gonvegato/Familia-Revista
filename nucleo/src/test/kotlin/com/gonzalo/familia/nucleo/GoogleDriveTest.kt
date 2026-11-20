package com.gonzalo.familia.nucleo

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Pruebas reales de [GoogleDrive] contra un servidor HTTP local que simula la API v3 de Google Drive y OAuth2. */
class GoogleDriveTest {
    private data class FakeFile(
        val id: String,
        var name: String,
        val mimeType: String,
        val parents: List<String>,
        var trashed: Boolean = false,
        var data: ByteArray = ByteArray(0),
        var shared: Boolean = false,
    )

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val files = ConcurrentHashMap<String, FakeFile>()
    private val sessions = ConcurrentHashMap<String, Triple<String, String?, ByteArray>>() // sid -> (name, existingId?, dirId as bytes)
    private val sessionDirs = ConcurrentHashMap<String, String>()
    private val sessionBuffers = ConcurrentHashMap<String, MutableMap<Long, ByteArray>>()
    private val sessionSizes = ConcurrentHashMap<String, Long>()
    private val seq = AtomicInteger(1)
    private val tokenCalls = AtomicInteger(0)
    private var currentToken = "tok-1"
    private var force401Once = false

    @Before
    fun setUp() {
        files.clear()
        sessions.clear()
        sessionDirs.clear()
        sessionBuffers.clear()
        sessionSizes.clear()
        seq.set(1)
        tokenCalls.set(0)
        currentToken = "tok-1"
        force401Once = false

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/token") { ex -> handleToken(ex) }
        server.createContext("/drive/v3/files") { ex -> handleFiles(ex) }
        server.createContext("/upload/drive/v3/files") { ex -> handleUploadStart(ex) }
        server.createContext("/upload/session/") { ex -> handleUploadChunk(ex) }
        server.createContext("/uc") { ex -> handlePublicDownload(ex) }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun drive(carpeta: String = "") = GoogleDrive(
        clientId = { "cid-1" },
        clientSecret = { "csec-1" },
        refreshToken = { "rtok-1" },
        carpetaConfig = { carpeta },
        api = baseUrl,
        uploadApi = baseUrl,
        tokenUrl = "$baseUrl/token",
        webBase = baseUrl,
    )

    private fun handleToken(ex: HttpExchange) {
        val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
        if (body.contains("grant_type=authorization_code")) {
            if (!body.contains("code=codigo-ok")) {
                respond(ex, 400, """{"error":"invalid_grant"}""")
                return
            }
            respond(ex, 200, """{"access_token":"tok-auth","refresh_token":"rtok-nuevo","expires_in":3600}""")
            return
        }
        if (!body.contains("refresh_token=rtok-1")) {
            respond(ex, 400, """{"error":"invalid_grant","error_description":"revoked"}""")
            return
        }
        val n = tokenCalls.incrementAndGet()
        currentToken = "tok-$n"
        respond(ex, 200, """{"access_token":"$currentToken","expires_in":3600}""")
    }

    private fun checkAuth(ex: HttpExchange): Boolean {
        val auth = ex.requestHeaders.getFirst("Authorization")
        if (force401Once) {
            force401Once = false
            respond(ex, 401, """{"error":{"message":"expired"}}""")
            return false
        }
        if (auth != "Bearer $currentToken") {
            respond(ex, 401, """{"error":{"message":"unauthorized"}}""")
            return false
        }
        return true
    }

    private fun handleFiles(ex: HttpExchange) {
        if (!checkAuth(ex)) return
        val path = ex.requestURI.path
        val query = parseQuery(ex.requestURI.rawQuery.orEmpty())
        val method = ex.requestHeaders.getFirst("X-HTTP-Method-Override") ?: ex.requestMethod

        if (path == "/drive/v3/files" && method == "GET") {
            val q = query["q"].orEmpty()
            val matched = files.values.filter { f ->
                if (q.contains("trashed = false") && f.trashed) return@filter false
                Regex("""'([^']+)' in parents""").find(q)?.let { if (it.groupValues[1] !in f.parents) return@filter false }
                Regex("""name = '([^']+)'""").find(q)?.let { if (f.name != it.groupValues[1]) return@filter false }
                Regex("""name contains '([^']+)'""").find(q)?.let { if (!f.name.contains(it.groupValues[1], ignoreCase = true)) return@filter false }
                if (q.contains("mimeType = '${GoogleDrive.MIME_CARPETA}'") && f.mimeType != GoogleDrive.MIME_CARPETA) return@filter false
                if (q.contains("mimeType != '${GoogleDrive.MIME_CARPETA}'") && f.mimeType == GoogleDrive.MIME_CARPETA) return@filter false
                if (q.contains("mimeType contains 'image/'") && !f.mimeType.startsWith("image/")) return@filter false
                true
            }.map { fileJson(it) }
            respond(ex, 200, Json.escribe(mapOf("files" to matched)))
            return
        }
        if (path == "/drive/v3/files" && method == "POST") {
            val m = Json.lee(ex.requestBody.readBytes().toString(Charsets.UTF_8)).comoMapa()
            val id = "gd_${seq.getAndIncrement()}"
            val f = FakeFile(id, m["name"].comoTexto(), m["mimeType"].comoTexto().ifBlank { "application/octet-stream" }, m["parents"].comoLista().map { it.comoTexto() })
            files[id] = f
            respond(ex, 200, Json.escribe(fileJson(f)))
            return
        }
        val permMatch = Regex("""^/drive/v3/files/([^/]+)/permissions$""").find(path)
        if (permMatch != null && method == "POST") {
            val f = files[permMatch.groupValues[1]] ?: return respond(ex, 404, """{"error":{"message":"not found"}}""")
            f.shared = true
            respond(ex, 200, """{"id":"anyoneWithLink"}""")
            return
        }
        val idMatch = Regex("""^/drive/v3/files/([^/]+)$""").find(path)
        if (idMatch != null) {
            val f = files[idMatch.groupValues[1]] ?: return respond(ex, 404, """{"error":{"message":"not found"}}""")
            if (method == "GET" && query["alt"] == "media") {
                val range = ex.requestHeaders.getFirst("Range")
                if (range != null && range.startsWith("bytes=")) {
                    val parts = range.removePrefix("bytes=").split("-")
                    val a = parts[0].toInt()
                    val b = minOf(f.data.size - 1, parts[1].toInt())
                    val slice = f.data.copyOfRange(a, b + 1)
                    ex.responseHeaders.add("Content-Range", "bytes $a-$b/${f.data.size}")
                    ex.sendResponseHeaders(206, slice.size.toLong())
                    ex.responseBody.use { it.write(slice) }
                } else {
                    ex.sendResponseHeaders(200, f.data.size.toLong())
                    ex.responseBody.use { it.write(f.data) }
                }
                return
            }
            if (method == "GET") {
                respond(ex, 200, Json.escribe(fileJson(f)))
                return
            }
            if (method == "PATCH") {
                val m = Json.lee(ex.requestBody.readBytes().toString(Charsets.UTF_8)).comoMapa()
                if ("trashed" in m) f.trashed = m["trashed"].comoBool()
                respond(ex, 200, Json.escribe(fileJson(f)))
                return
            }
        }
        respond(ex, 404, """{"error":{"message":"unknown $method $path"}}""")
    }

    private fun handleUploadStart(ex: HttpExchange) {
        if (!checkAuth(ex)) return
        val path = ex.requestURI.path
        val existingId = Regex("""^/upload/drive/v3/files/([^/]+)$""").find(path)?.groupValues?.get(1)
        val m = Json.lee(ex.requestBody.readBytes().toString(Charsets.UTF_8)).comoMapa()
        val sid = "s_${seq.getAndIncrement()}"
        val name = m["name"].comoTexto()
        val dirId = m["parents"].comoLista().firstOrNull()?.comoTexto().orEmpty()
        val total = ex.requestHeaders.getFirst("X-Upload-Content-Length")?.toLongOrNull() ?: 0L
        sessions[sid] = Triple(name, existingId, ByteArray(0))
        sessionDirs[sid] = dirId
        sessionBuffers[sid] = ConcurrentHashMap()
        sessionSizes[sid] = total
        ex.responseHeaders.add("Location", "$baseUrl/upload/session/$sid")
        ex.sendResponseHeaders(200, -1)
        ex.close()
    }

    private fun handleUploadChunk(ex: HttpExchange) {
        val sid = ex.requestURI.path.substringAfterLast('/')
        val info = sessions[sid] ?: return respond(ex, 404, "no session")
        val chunk = ex.requestBody.readBytes()
        val cr = ex.requestHeaders.getFirst("Content-Range")
        val total = sessionSizes[sid] ?: 0L
        val bufMap = sessionBuffers[sid]!!
        if (cr != null && cr.startsWith("bytes ")) {
            val range = cr.removePrefix("bytes ").substringBefore('/')
            val start = range.substringBefore('-').toLong()
            val end = range.substringAfter('-').toLong()
            bufMap[start] = chunk
            if (end + 1 < total) {
                ex.sendResponseHeaders(308, -1)
                ex.close()
                return
            }
        } else {
            bufMap[0L] = chunk
        }
        val all = ByteArray(total.toInt())
        bufMap.forEach { (off, b) -> System.arraycopy(b, 0, all, off.toInt(), b.size) }
        val existingId = info.second
        val f = if (existingId != null) {
            files[existingId]!!.also { it.data = all }
        } else {
            val id = "gd_${seq.getAndIncrement()}"
            FakeFile(id, info.first, "application/octet-stream", listOf(sessionDirs[sid].orEmpty()), data = all).also { files[id] = it }
        }
        respond(ex, 200, Json.escribe(fileJson(f)))
    }

    private fun handlePublicDownload(ex: HttpExchange) {
        val id = parseQuery(ex.requestURI.rawQuery.orEmpty())["id"].orEmpty()
        val f = files[id]?.takeIf { it.shared && !it.trashed } ?: return respond(ex, 404, "not found")
        ex.sendResponseHeaders(200, f.data.size.toLong())
        ex.responseBody.use { it.write(f.data) }
    }

    private fun fileJson(f: FakeFile) = mapOf(
        "id" to f.id, "name" to f.name, "mimeType" to f.mimeType,
        "size" to f.data.size.toString(), "parents" to f.parents, "trashed" to f.trashed,
    )

    private fun parseQuery(raw: String): Map<String, String> = raw.split('&').filter { it.isNotBlank() }.associate {
        val k = URLDecoder.decode(it.substringBefore('='), "UTF-8")
        val v = URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        k to v
    }

    private fun respond(ex: HttpExchange, code: Int, body: String) {
        val b = body.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        ex.sendResponseHeaders(code, b.size.toLong())
        ex.responseBody.use { it.write(b) }
    }

    @Test
    fun subeActualizaBajaYComparte() {
        val gd = drive()
        assertTrue(gd.activo)
        assertTrue(gd.prueba().contains("Google Drive"))

        // Subir un archivo pequeño crea las carpetas y devuelve un id
        val datos1 = "hola libro epub".toByteArray()
        val id1 = gd.sube("Marfil/Libros/Dune", "Dune.epub", datos1.size.toLong()) { ByteArrayInputStream(datos1) }
        assertTrue(id1.startsWith("gd_"))
        assertEquals(true, gd.existe(id1))

        // Volver a subir con el mismo nombre actualiza el mismo archivo (sin duplicar)
        val datos2 = "hola libro epub v2 actualizado".toByteArray()
        val id2 = gd.sube("Marfil/Libros/Dune", "Dune.epub", datos2.size.toLong()) { ByteArrayInputStream(datos2) }
        assertEquals(id1, id2)

        // Descarga simple
        val tmp = Files.createTempFile("gd-test-", ".epub").toFile()
        try {
            gd.baja(id1, tmp)
            assertArrayEquals(datos2, tmp.readBytes())

            // Enlace público para tu pareja y descarga sin credenciales
            val link = gd.enlacePublico(id1)
            assertEquals(id1, GoogleDrive.extraeIdEnlace(link))
            val tmp2 = Files.createTempFile("gd-shared-", ".epub").toFile()
            try {
                gd.descargaEnlace(link, tmp2)
                assertArrayEquals(datos2, tmp2.readBytes())
            } finally { tmp2.delete() }
        } finally { tmp.delete() }

        // Borrado (a la papelera)
        gd.borra(id1)
        assertEquals(false, gd.existe(id1))
    }

    @Test
    fun archivoGrandeSubePorTrozosYBajaEnParaleloYRenueva401() {
        val gd = drive()
        // 5 MB (> 4 MB chunk upload and > 4 MB parallel range download)
        val grande = ByteArray(5 * 1024 * 1024 + 1234) { ((it * 31) % 251).toByte() }
        var ultimoProgreso = 0L
        val id = gd.sube("versiones", "Bastet-1.0.0.apk", grande.size.toLong(), { ultimoProgreso = it }) { ByteArrayInputStream(grande) }
        assertEquals(grande.size.toLong(), ultimoProgreso)

        // Forzar un 401: debe renovar automáticamente el access_token y reintentar
        force401Once = true
        val vers = gd.versiones()
        assertEquals(1, vers.size)
        assertEquals("Bastet-1.0.0.apk", vers[0].nombre)
        assertEquals(2, tokenCalls.get())

        // Descarga en paralelo por Range (206)
        val dest = Files.createTempFile("gd-big-", ".apk").toFile()
        try {
            gd.baja(id, dest)
            assertArrayEquals(grande, dest.readBytes())
        } finally { dest.delete() }
    }

    @Test
    fun extraeIdsDeUrlsDeDrive() {
        assertEquals("1AbC_23-xyz", GoogleDrive.extraeIdCarpeta("https://drive.google.com/drive/folders/1AbC_23-xyz?usp=sharing"))
        assertEquals("1AbC_23-xyz", GoogleDrive.extraeIdCarpeta("1AbC_23-xyz"))
        assertEquals("9ZyX_01", GoogleDrive.extraeIdEnlace("https://drive.google.com/file/d/9ZyX_01/view?usp=sharing"))
        assertNull(GoogleDrive.extraeIdEnlace("https://kdrive.infomaniak.com/app/share/123/abc"))
        assertFalse(GoogleDrive.esEnlaceGoogleDrive("https://kdrive.infomaniak.com/app/share/123/abc"))
        assertTrue(GoogleDrive.esEnlaceGoogleDrive("https://drive.google.com/file/d/9ZyX_01/view"))
    }

    @Test
    fun autorizarEnNavegadorObtieneRefreshToken() {
        val refresh = GoogleDrive.autorizarEnNavegador(
            clientId = "cid-test",
            clientSecret = "csec-test",
            abrirUrl = { authUrl ->
                val redirect = Regex("""redirect_uri=([^&]+)""").find(authUrl)!!.groupValues[1].let { java.net.URLDecoder.decode(it, "UTF-8") }
                val state = Regex("""state=([^&]+)""").find(authUrl)!!.groupValues[1].let { java.net.URLDecoder.decode(it, "UTF-8") }
                Thread {
                    runCatching { java.net.URL("$redirect/favicon.ico").openStream().close() }
                    java.net.URL("$redirect/?code=codigo-ok&state=$state").openStream().use { it.readBytes() }
                }.start()
            },
            timeoutMs = 10_000,
            tokenUrl = "$baseUrl/token",
        )
        assertEquals("rtok-nuevo", refresh)
    }
}
