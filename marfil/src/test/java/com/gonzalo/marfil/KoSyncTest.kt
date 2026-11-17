package com.gonzalo.marfil

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * La posición por KOSync (SesKobo): la huella del libro es la de KOReader (comprobada con libros y huellas de KOReader de verdad,
 * herramientas/casos/kosync) y las peticiones son las del protocolo (con un servidor de mentira aquí mismo: nada va al público).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KoSyncTest {
    private val casos = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "herramientas/casos/kosync") }.first { it.isDirectory }

    @Test fun huellaComoKOReader() {
        for (n in listOf("book-a", "book-p", "book-q")) {
            val lua = File(casos, "$n.metadata.epub.lua").readText()
            val quiero = Regex("\\[\"partial_md5_checksum\"\\] = \"([0-9a-f]{32})\"").find(lua)!!.groupValues[1]
            assertEquals(n, quiero, File(casos, "$n.epub").inputStream().use { KoSync.huellaDe(it) })
        }
        // Un archivo pequeño: solo el primer tramo (y parte del segundo)
        val b = ByteArray(1500) { (it % 251).toByte() }
        val md = java.security.MessageDigest.getInstance("MD5"); md.update(b, 0, 1024); md.update(b, 1024, 476)
        assertEquals(md.digest().joinToString("") { "%02x".format(it) }, KoSync.huellaDe(b.inputStream()))
    }

    /** Un servidor HTTP mínimo (como FakeKDrive): método, ruta, cabeceras y cuerpo → (código, respuesta). */
    private class Servidor(val atiende: (metodo: String, ruta: String, cab: Map<String, String>, cuerpo: String) -> Pair<Int, String>) {
        val sock = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        val puerto get() = sock.localPort
        init {
            kotlin.concurrent.thread(isDaemon = true) {
                while (!sock.isClosed) {
                    val c = runCatching { sock.accept() }.getOrNull() ?: break
                    runCatching {
                        c.use {
                            val inp = java.io.BufferedInputStream(c.getInputStream())
                            fun linea(): String { val b = StringBuilder(); while (true) { val x = inp.read(); if (x < 0 || x == '\n'.code) break; if (x != '\r'.code) b.append(x.toChar()) }; return b.toString() }
                            val (metodo, ruta) = linea().split(' ').let { it[0] to it[1] }
                            val cab = HashMap<String, String>()
                            while (true) { val l = linea(); if (l.isEmpty()) break; val i = l.indexOf(':'); cab[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim() }
                            val n = cab["content-length"]?.toInt() ?: 0
                            val cuerpo = ByteArray(n).also { var r = 0; while (r < n) { val k = inp.read(it, r, n - r); if (k < 0) break; r += k } }.decodeToString()
                            val (cod, res) = atiende(metodo, ruta, cab, cuerpo)
                            val b = res.toByteArray()
                            c.getOutputStream().apply { write("HTTP/1.1 $cod X\r\nContent-Type: application/json\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(b); flush() }
                        }
                    }
                }
            }
        }
    }
    private var srv: Servidor? = null
    @After fun para() { srv?.sock?.close() }

    @Test fun protocolo() {
        assumeTrue(BuildConfig.SESKOBO)
        val ctx = RuntimeEnvironment.getApplication()
        val usuarios = HashMap<String, String>()
        var guardado: JSONObject? = null
        val vistos = ArrayList<String>()
        srv = Servidor { metodo, ruta, cab, cuerpo ->
            val u = cab["x-auth-user"]; val k = cab["x-auth-key"]
            val ok = u != null && usuarios[u] == k
            vistos += "$metodo $ruta"
            when {
                ruta == "/users/create" -> { val o = JSONObject(cuerpo); if (usuarios.containsKey(o.getString("username"))) 402 to "{}" else { usuarios[o.getString("username")] = o.getString("password"); 201 to "{}" } }
                ruta == "/users/auth" -> if (ok) 200 to "{\"authorized\":\"OK\"}" else 401 to "{}"
                !ok -> 401 to "{}"
                metodo == "PUT" -> { guardado = JSONObject(cuerpo).put("timestamp", 1794900000L); 200 to "{}" }
                ruta == "/syncs/progress/abc" -> 200 to (guardado?.toString() ?: "{}")
                else -> 200 to "{}"
            }
        }
        val url = "http://127.0.0.1:${srv!!.puerto}"
        // Un servidor sin cifrar (o sin http) no vale: Android no lo dejaría y parecería que no hay conexión
        for (malo in listOf("http://kosync.eu", "kosync.eu", "ftp://x")) assertTrue(malo, runCatching { KoSync.entra(ctx, malo, "ana", "secreto") }.exceptionOrNull()?.message?.contains("https://") == true)
        // La primera vez el usuario no existe: se crea (con el MD5 de la contraseña, nunca la contraseña)
        assertTrue(KoSync.entra(ctx, url, "ana", "secreto"))
        assertEquals(KoSync.md5("secreto"), usuarios["ana"])
        assertEquals(KoSync.md5("secreto"), KoSync.datosParaElKobo(ctx)!!.third)
        // La segunda, entra sin crear; con otra contraseña, no
        assertTrue(!KoSync.entra(ctx, url, "ana", "secreto"))
        assertTrue(runCatching { KoSync.entra(ctx, url, "ana", "otra") }.exceptionOrNull() is KoSync.Error)
        KoSync.entra(ctx, url, "ana", "secreto")
        // Sin nada guardado: nada
        assertNull(KoSync.baja(ctx, "abc"))
        // Subir y bajar
        KoSync.sube(ctx, "abc", "/body[1]/DocFragment[3]/body[1]/p[2]/text()[1].5", 0.123456)
        val g = guardado!!
        assertEquals("abc", g.getString("document")); assertEquals(0.1235, g.getDouble("percentage"), 1e-9); assertEquals("SesKobo", g.getString("device"))
        assertEquals(KoSync.idDispositivo(ctx), g.getString("device_id"))
        val p = KoSync.baja(ctx, "abc")!!
        assertEquals("/body[1]/DocFragment[3]/body[1]/p[2]/text()[1].5", p.xp); assertEquals(1794900000000L, p.hora)
        assertEquals(p.xp, KoSync.ultimaSubida(ctx, "abc").second)
        assertTrue(vistos.contains("PUT /syncs/progress"))
    }
}
