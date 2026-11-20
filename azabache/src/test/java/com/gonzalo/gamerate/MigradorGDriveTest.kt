package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.NubeFamilia
import com.gonzalo.familia.nucleo.GoogleDrive
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigradorGDriveTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var kd: FakeKDrive
    private lateinit var sock: ServerSocket
    @Volatile private var alive = true
    private lateinit var gdUrl: String

    private data class GdNode(val id: String, val name: String, val parent: String, val mime: String, var bytes: ByteArray = ByteArray(0))
    private val gdNodes = ConcurrentHashMap<String, GdNode>()
    private val seq = AtomicInteger(1)

    @Before
    fun setUp() {
        kd = FakeKDrive()
        KDrive.api = kd.url
        KDrive.share = kd.url
        KDrive.save(ctx, "tok", "777")

        gdNodes.clear()
        gdNodes["root"] = GdNode("root", "My Drive", "", GoogleDrive.MIME_CARPETA)
        alive = true
        sock = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        gdUrl = "http://127.0.0.1:${sock.localPort}"
        Thread {
            while (alive) {
                val s = try { sock.accept() } catch (_: Exception) { break }
                Thread { serve(s) }.start()
            }
        }.apply { isDaemon = true; start() }

        NubeFamilia.gdrivePrueba = GoogleDrive(
            clientId = { "cid" },
            clientSecret = { "csec" },
            refreshToken = { "rtok" },
            api = gdUrl,
            uploadApi = gdUrl,
            tokenUrl = "$gdUrl/token",
            webBase = gdUrl,
        )
    }

    @After
    fun tearDown() {
        alive = false
        runCatching { sock.close() }
        NubeFamilia.gdrivePrueba = null
        KDrive.forget(ctx)
        kd.stop()
    }

    private fun readLine(inp: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val c = inp.read()
            if (c == -1 || c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun serve(s: java.net.Socket) = s.use {
        val inp = it.getInputStream()
        val reqLine = readLine(inp).ifBlank { return@use }
        val parts = reqLine.split(' ')
        val method = parts[0]
        val target = parts[1]
        var len = 0
        while (true) {
            val h = readLine(inp)
            if (h.isEmpty()) break
            if (h.startsWith("Content-Length:", ignoreCase = true)) len = h.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val body = ByteArray(len)
        var off = 0
        while (off < len) {
            val r = inp.read(body, off, len - off)
            if (r <= 0) break
            off += r
        }
        val (code, json, extra) = handle(method, target, body)
        val outBytes = json.toByteArray()
        val hdr = buildString {
            append("HTTP/1.1 $code OK\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${outBytes.size}\r\n")
            extra.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Connection: close\r\n\r\n")
        }
        it.getOutputStream().apply { write(hdr.toByteArray()); write(outBytes); flush() }
    }

    private fun handle(method: String, target: String, body: ByteArray): Triple<Int, String, Map<String, String>> {
        val path = target.substringBefore('?')
        val rawQ = target.substringAfter('?', "")
        if (path == "/token") return Triple(200, """{"access_token":"gd-tok","expires_in":3600}""", emptyMap())
        if (path == "/drive/v3/files" && method == "GET") {
            val q = rawQ.split('&').filter { '=' in it }.associate {
                it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }["q"].orEmpty()
            val par = Regex("'([^']+)' in parents").find(q)?.groupValues?.get(1).orEmpty()
            val nam = Regex("name = '([^']+)'").find(q)?.groupValues?.get(1)
            val onlyDir = q.contains("mimeType = '${GoogleDrive.MIME_CARPETA}'")
            val onlyFile = q.contains("mimeType != '${GoogleDrive.MIME_CARPETA}'")
            val matches = gdNodes.values.filter { n ->
                n.parent == par && (nam == null || n.name == nam) &&
                    (!onlyDir || n.mime == GoogleDrive.MIME_CARPETA) &&
                    (!onlyFile || n.mime != GoogleDrive.MIME_CARPETA)
            }
            val arr = JSONArray().apply {
                matches.forEach { n ->
                    put(JSONObject().put("id", n.id).put("name", n.name).put("mimeType", n.mime).put("size", n.bytes.size.toString()).put("parents", JSONArray().put(n.parent)))
                }
            }
            return Triple(200, JSONObject().put("files", arr).toString(), emptyMap())
        }
        if (path == "/drive/v3/files" && method == "POST") {
            val o = JSONObject(body.toString(Charsets.UTF_8))
            val parent = o.getJSONArray("parents").getString(0)
            // Crucial: reject nonexistent parent IDs with 404 (matches real Google Drive!)
            if (!gdNodes.containsKey(parent)) return Triple(404, """{"error":{"message":"File not found: $parent"}}""", emptyMap())
            val id = "gd_${seq.getAndIncrement()}"
            gdNodes[id] = GdNode(id, o.getString("name"), parent, o.optString("mimeType", "application/octet-stream"))
            return Triple(200, JSONObject().put("id", id).toString(), emptyMap())
        }
        if (path == "/upload/drive/v3/files" && method == "POST") {
            val o = JSONObject(body.toString(Charsets.UTF_8))
            val parent = o.getJSONArray("parents").getString(0)
            if (!gdNodes.containsKey(parent)) return Triple(404, """{"error":{"message":"File not found: $parent"}}""", emptyMap())
            val id = "gd_${seq.getAndIncrement()}"
            gdNodes[id] = GdNode(id, o.getString("name"), parent, "application/octet-stream")
            return Triple(200, "{}", mapOf("Location" to "$gdUrl/upload/sess/$id"))
        }
        if (path.startsWith("/upload/sess/") && method == "PUT") {
            val id = path.substringAfterLast('/')
            gdNodes[id]!!.bytes = body
            return Triple(200, JSONObject().put("id", id).toString(), emptyMap())
        }
        return Triple(404, "{}", emptyMap())
    }

    @Test
    fun migraCarpetasAnidadasYEnlazaStoreSinErrores() {
        val epub = "contenido-dune-epub".toByteArray()
        val shot = "png-captura-1".toByteArray()
        val save = "save-gba-zelda".toByteArray()

        val idBook = KDrive.upload(ctx, "Marfil/Libros/Dune", "Dune.epub", epub.size.toLong()) { ByteArrayInputStream(epub) }
        val idShot = KDrive.upload(ctx, "Atrio/Capturas", "s1.png", shot.size.toLong()) { ByteArrayInputStream(shot) }
        KDrive.upload(ctx, "Hiedra/Saves/gba", "zelda.sav", save.size.toLong()) { ByteArrayInputStream(save) }

        val store = Store(ctx)
        store.addNube(listOf(NubeFile("shot:1", idShot, "s1.png", shot.size.toLong())))

        val r1 = MigradorGDrive.ejecutar(ctx)
        assertTrue(r1, r1.contains("3 copiados"))
        assertTrue(r1, !r1.contains("con error"))

        val gdEpub = gdNodes.values.first { it.name == "Dune.epub" }
        assertArrayEquals(epub, gdEpub.bytes)
        val gdSave = gdNodes.values.first { it.name == "zelda.sav" }
        assertArrayEquals(save, gdSave.bytes)

        val updated = store.loadNube().first { it.k == "shot:1" }
        assertEquals(idShot, updated.f)
        assertTrue(updated.gd.startsWith("gd_"))
        assertTrue(idBook > 0)

        val r2 = MigradorGDrive.ejecutar(ctx)
        assertTrue(r2, r2.contains("0 copiados, 3 ya estaban"))
    }
}
