package com.gonzalo.marfil

import org.json.JSONArray
import org.json.JSONObject

/**
 * Un kDrive de mentira (lo mismo que herramientas/fakekdrive.js), con un servidor HTTP mínimo. Como el de verdad: en la
 * raíz no se puede escribir y lo tuyo va en «Mi carpeta» (visibility is_private_space).
 */
class FakeKDrive(private val drive: String = "777", private val token: String = "tok") {
    class F(val id: Long, val name: String, val dir: Boolean, val parent: Long, var bytes: ByteArray = ByteArray(0), val visibility: String = "", var trashed: Boolean = false)
    val files = java.util.Collections.synchronizedMap(linkedMapOf(
        1L to F(1, "", true, 0, visibility = "is_root"), 2L to F(2, "Mi carpeta", true, 1, visibility = "is_private_space"),
        3L to F(3, "Documentos comunes", true, 1, visibility = "is_team_space")))
    private var next = 100L
    private val sessions = HashMap<String, Pair<JSONObject, java.util.TreeMap<Int, ByteArray>>>()
    private val links = HashMap<String, Long>()
    private val sock = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    val url get() = "http://127.0.0.1:${sock.localPort}"
    @Volatile private var alive = true
    var finished = 0

    fun pathOf(f: F): String { val out = mutableListOf<String>(); var c: F? = f; while (c != null && c.id != 1L && c.id != 2L) { out.add(0, c.name); c = files[c.parent] }; return out.joinToString("/") }
    fun byPath(p: String) = synchronized(files) { files.values.firstOrNull { !it.dir && pathOf(it) == p } }
    fun stop() { alive = false; sock.close() }

    private class Res(val code: Int, val body: ByteArray)
    private fun json(code: Int, o: JSONObject) = Res(code, o.toString().toByteArray())
    private fun ok(data: Any) = json(200, JSONObject().put("result", "success").put("data", data).put("has_more", false))
    private fun err(code: Int, d: String = "err $code") = json(code, JSONObject().put("result", "error").put("error", JSONObject().put("code", "$code").put("description", d)))

    private fun ensure(parent: Long, path: String): Long {
        var cur = parent
        for (n in path.split('/').filter { it.isNotEmpty() }) {
            cur = files.values.firstOrNull { it.parent == cur && it.dir && it.name == n }?.id ?: run { val id = next++; files[id] = F(id, n, true, cur); id }
        }
        return cur
    }
    private fun put(dir: Long, name: String, b: ByteArray): F {
        files.values.firstOrNull { it.parent == dir && !it.dir && !it.trashed && it.name == name }?.let { it.bytes = b; return it } // conflict=version
        val id = next++; files[id] = F(id, name, false, dir, b); return files[id]!!
    }

    private fun handle(method: String, target: String, auth: String?, body: ByteArray): Res {
        val path = target.substringBefore('?')
        val q = target.substringAfter('?', "").split('&').filter { '=' in it }.associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        // Descarga pública por enlace (sin cuenta)
        Regex("""^/2/app/(\d+)/share/([a-z0-9-]+)/files/(\d+)/download$""").find(path)?.let { m ->
            val f = files[m.groupValues[3].toLong()]
            return if (m.groupValues[1] == drive && links[m.groupValues[2]] == m.groupValues[3].toLong() && f != null) Res(200, f.bytes) else err(404)
        }
        if (auth != "Bearer $token") return err(401)
        val m = Regex("""^/([23])/drive/(\d+)(/.*)$""").find(path) ?: return err(404)
        if (m.groupValues[2] != drive) return err(404)
        val p = m.groupValues[3]
        fun id(re: String) = Regex(re).find(p)?.groupValues?.get(1)
        val denied = err(403, "You do not have the required permissions")
        synchronized(files) {
            if (method == "GET") id("""^/files/(\d+)/files$""")?.toLong()?.let { d ->
                return ok(JSONArray().apply { files.values.filter { it.parent == d && !it.trashed }.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("type", if (it.dir) "dir" else "file").put("visibility", it.visibility)
                    .put("extension_type", if (it.dir) "" else if (Regex("(?i)\\.(png|jpe?g)$").containsMatchIn(it.name)) "image" else "unknown")) } })
            }
            // Búsqueda por nombre en todo el kDrive (como /3/drive/<id>/files/search?query=…&types[]=image&with=path)
            if (method == "GET" && p == "/files/search") {
                val qq = q["query"].orEmpty().lowercase()
                val tipo = q["types[]"]
                return ok(JSONArray().apply {
                    files.values.filter { it.id > 3 && !it.trashed && it.name.lowercase().contains(qq) }.filter { f ->
                        when (tipo) { "dir" -> f.dir; "image" -> !f.dir && Regex("(?i)\\.(png|jpe?g)$").containsMatchIn(f.name); else -> true }
                    }.forEach { f -> put(JSONObject().put("id", f.id).put("name", f.name).put("type", if (f.dir) "dir" else "file").put("parent_id", f.parent)
                        .put("extension_type", if (f.dir) "" else if (Regex("(?i)\\.(png|jpe?g)$").containsMatchIn(f.name)) "image" else "unknown")
                        .put("path", "/Private/" + pathOf(f))) }
                })
            }
            if (method == "GET") id("""^/files/(\d+)$""")?.toLong()?.let { f ->
                val x = files[f] ?: return err(404, "not_found")
                return ok(JSONObject().put("id", x.id).put("name", x.name).put("parent_id", x.parent).put("type", if (x.dir) "dir" else "file").put("status", if (x.trashed) "trashed" else "ok"))
            }
            if (method == "POST") id("""^/files/(\d+)/directory$""")?.toLong()?.let { d ->
                if (d == 1L) return denied
                val n = next++; files[n] = F(n, JSONObject(String(body)).getString("name"), true, d); return ok(JSONObject().put("id", n))
            }
            if (method == "POST" && p == "/upload") {
                if (q["total_size"]?.toLong() != body.size.toLong()) return err(400)
                val d = q["directory_id"]!!.toLong()
                if (d == 1L) return denied
                return ok(JSONObject().put("id", put(ensure(d, q["directory_path"].orEmpty()), q["file_name"]!!, body).id))
            }
            if (method == "POST" && p == "/upload/session/start") {
                val b = JSONObject(String(body))
                if (b.getLong("directory_id") == 1L) return denied
                val t = java.util.UUID.randomUUID().toString().replace("-", "")
                sessions[t] = b to java.util.TreeMap()
                return ok(JSONObject().put("token", t).put("upload_url", url))
            }
            if (method == "POST") id("""^/upload/session/([a-f0-9]+)/chunk$""")?.let { t ->
                val s = sessions[t] ?: return err(404)
                if (q["chunk_size"]?.toInt() != body.size) return err(400)
                s.second[q["chunk_number"]!!.toInt()] = body; return ok(JSONObject().put("status", "ok"))
            }
            if (method == "POST") id("""^/upload/session/([a-f0-9]+)/finish$""")?.let { t ->
                val (b, ch) = sessions.remove(t) ?: return err(404)
                if (ch.size != b.getInt("total_chunks")) return err(400)
                val all = java.io.ByteArrayOutputStream().apply { ch.values.forEach { write(it) } }.toByteArray()
                if (all.size.toLong() != b.getLong("total_size")) return err(400)
                finished++
                return ok(JSONObject().put("file", JSONObject().put("id", put(ensure(b.getLong("directory_id"), b.optString("directory_path")), b.getString("file_name"), all).id)))
            }
            if (method == "POST") id("""^/files/(\d+)/link$""")?.toLong()?.let { f ->
                val u = java.util.UUID.randomUUID().toString(); links[u] = f
                return ok(JSONObject().put("url", "https://kdrive.infomaniak.com/app/share/$drive/$u").put("file_id", f))
            }
            if (method == "GET") id("""^/files/(\d+)/thumbnail$""")?.toLong()?.let { f -> return files[f]?.let { Res(200, it.bytes.copyOf(minOf(16, it.bytes.size))) } ?: err(404) }
            if (method == "GET") id("""^/files/(\d+)/download$""")?.toLong()?.let { f -> return files[f]?.let { Res(200, it.bytes) } ?: err(404) }
            if (method == "DELETE") id("""^/files/(\d+)$""")?.toLong()?.let { f -> files.remove(f); return ok(true) }
        }
        return err(404)
    }

    init {
        Thread {
            while (alive) {
                val c = runCatching { sock.accept() }.getOrNull() ?: break
                Thread {
                    c.use { s ->
                        val inp = java.io.BufferedInputStream(s.getInputStream())
                        fun line(): String { val b = java.io.ByteArrayOutputStream(); while (true) { val x = inp.read(); if (x < 0 || x == '\n'.code) break; if (x != '\r'.code) b.write(x) }; return b.toString("UTF-8") }
                        val req = line().split(' ')
                        if (req.size < 2) return@use
                        val h = HashMap<String, String>()
                        while (true) { val l = line(); if (l.isEmpty()) break; h[l.substringBefore(':').trim().lowercase()] = l.substringAfter(':').trim() }
                        val len = h["content-length"]?.toInt() ?: 0
                        val body = ByteArray(len); var r = 0; while (r < len) { val n = inp.read(body, r, len - r); if (n < 0) break; r += n }
                        var res = handle(req[0], req[1], h["authorization"], body)
                        // Como kDrive: las descargas entienden «Range: bytes=a-b» (respuesta 206 con su Content-Range)
                        var extra = ""
                        val rg = h["range"]
                        if (rg != null && res.code == 200 && req[1].contains("/download")) Regex("""bytes=(\d+)-(\d*)""").find(rg)?.let { m ->
                            val a = m.groupValues[1].toInt(); val b = (m.groupValues[2].toIntOrNull() ?: (res.body.size - 1)).coerceAtMost(res.body.size - 1)
                            extra = "Content-Range: bytes $a-$b/${res.body.size}\r\n"; res = Res(206, res.body.copyOfRange(a, b + 1))
                        }
                        val out = s.getOutputStream()
                        out.write("HTTP/1.1 ${res.code} X\r\nContent-Length: ${res.body.size}\r\n${extra}Content-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(res.body); out.flush()
                    }
                }.start()
            }
        }.apply { isDaemon = true }.start()
    }
}
