package com.gonzalo.hiedra.pc

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.RedJava
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import com.gonzalo.familia.pc.Sitio
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

/**
 * «Tu PC» con la cuenta de verdad (el emulador, con sus reglas): el móvil pide una captura y la baja tal cual, leyendo los mismos nodos y
 * de la misma manera que Hiedra del móvil (Suite.kt: `suite/cmd/<pc>/<id>/res`, `suite/shots/<id>`, `shotdata/<id>/<i>` decodificados uno a
 * uno); y prepara el streaming.
 */
class TuPcCapturasTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var movil: Familia
    private val pc = "hiedra-linux-pc1"
    private val uid get() = movil.sesion.uid
    private var imagen: Captura.Imagen? = null

    @Before fun prepara() {
        Emulador.limpia(); Emulador.cuenta("gonzalo")
        movil = Emulador.dispositivo("gonzalo")
        LaFamilia.reiniciaParaPruebas(); LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg; LaFamilia.dirPruebas = tmp.root; LaFamilia.llaveroPruebas = LlaveroMemoria()
        LaFamilia.inicia("hiedra").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Juegos.dispositivo = { pc }; Pc.paraPruebas(tmp.root, emptyList())
        val steam = File(tmp.root, "Steam"); File(steam, "steamapps").mkdirs()
        File(steam, "steamapps/appmanifest_1145360.acf").writeText("\"AppState\"\n{\n\t\"appid\"\t\t\"1145360\"\n\t\"name\"\t\t\"Hades\"\n}")
        Pc.steamPruebas = listOf(steam)
        AjustesTuPc.recarga()
        Capturas.imagenesPruebas = File(tmp.root, "Imágenes")
        Capturas.capturaPruebas = { imagen!! }
    }

    @After fun fuera() {
        terminaPartida()
        LaFamilia.reiniciaParaPruebas(); Pc.paraPruebas(null, null)
        Capturas.imagenesPruebas = null; Capturas.capturaPruebas = null
        SteamLink.ordenPruebas = null; SteamLink.abiertoPruebas = null; SteamLink.abrePruebas = null
    }

    // ---------------------------------------------------------------- como el móvil

    /** Lo que lee el móvil, con su sesión (las reglas de verdad). */
    private fun lee(ruta: String): Any? {
        val r = RedJava().pedir("GET", movil.sesion.url("/users/$uid$ruta", conPase = true), null)
        assertTrue("el móvil puede leer $ruta (${r.codigo})", r.ok)
        return Json.lee(r.cuerpo)
    }

    /** El móvil manda una orden al PC (Suite.sendRaw) y devuelve su id. */
    private fun manda(op: String, a: Map<String, Any?> = emptyMap()): String {
        val r = RedJava().pedir("POST", movil.sesion.url("/users/$uid/suite/cmd/$pc", conPase = true),
            Json.escribe(mapOf("op" to op, "a" to a, "t" to mapOf(".sv" to "timestamp"), "by" to "Bastet · Pixel 8")))
        assertTrue(r.ok)
        return Json.lee(r.cuerpo).comoMapa()["name"].comoTexto()
    }

    /** La respuesta del PC a una orden (la que espera el móvil). */
    private fun respuesta(id: String): Map<String, Any?> = lee("/suite/cmd/$pc/$id/res").comoMapa()

    /** Baja una captura como Suite.download del móvil: trozo a trozo, cada uno decodificado solo, y comprueba `bytes`. */
    private fun bajaComoElMovil(id: String): ByteArray {
        val o = lee("/suite/shots/$id").comoMapa()
        assertEquals(true, o["done"]); assertNotEquals(true, o["gone"])
        val n = o["n"].comoLong().toInt()
        val out = ByteArrayOutputStream()
        for (i in 0 until n) {
            val parte = lee("/shotdata/$id/$i") as? String ?: error("falta el trozo $i")
            out.write(Base64.getDecoder().decode(parte))
        }
        val b = out.toByteArray()
        assertEquals("el tamaño que dice la captura", o["bytes"].comoLong(), b.size.toLong())
        return b
    }

    private fun juegaHades(): Pc.Partida {
        Pc.lista()
        val p = Pc.Partida("steam:1145360", "Hades", "", System.currentTimeMillis(), "")
        Pc.vigilaParaPruebas(p, "")
        return p
    }
    /** (Sin partida en marcha para las demás pruebas: se da por terminada.) */
    private fun terminaPartida() { runCatching { Pc.mira(System.currentTimeMillis() + 3_600_000L, emptyList()) } }

    // ---------------------------------------------------------------- capturas

    @Test fun elMovilPideUnaCapturaYLaBajaTalCual() {
        // Ruido (no se comprime): varios trozos de los de verdad, en dos tandas
        val png = ImagenesPrueba.png(ImagenesPrueba.ruido(1400, 900))
        imagen = Captura.Imagen(png, 1400, 900, "image/png", "spectacle")
        assertTrue(Capturas.trozos(png).size > Capturas.TANDA)
        val cid = manda("shot")
        TuPc.mira(esperar = true)
        val res = respuesta(cid)
        assertEquals(true, res["ok"]); assertEquals("Captura de 1400×900", res["msg"])
        val id = res["shot"].comoTexto()
        assertTrue(id.isNotBlank())
        // Lo que lee el móvil de la captura (Suite.shotOf)
        val o = lee("/suite/shots/$id").comoMapa()
        assertEquals(id, o["id"]); assertEquals(pc, o["dev"]); assertEquals(Sitio.nombreEquipo, o["pc"])
        assertEquals(1400L, o["w"].comoLong()); assertEquals(900L, o["h"].comoLong()); assertEquals("image/png", o["mime"]); assertEquals(png.size.toLong(), o["bytes"].comoLong())
        assertEquals(Capturas.trozos(png).size.toLong(), o["n"].comoLong()); assertEquals("subida entera", o["n"].comoLong(), o["up"].comoLong())
        assertEquals("spectacle", o["m"]); assertEquals("Bastet · Pixel 8", o["by"]); assertNull("sin juego: el móvil la llama «Escritorio»", o["game"])
        assertTrue(o["t"].comoLong() > 0)
        assertTrue(o["name"].comoTexto().matches(Regex("Escritorio \\d{4}-\\d\\d-\\d\\d \\d\\d\\.\\d\\d\\.\\d\\d\\.png")))
        // El móvil la baja tal cual: byte a byte la misma
        assertArrayEquals(png, bajaComoElMovil(id))
        // Y aquí, en Imágenes/Bastet/Escritorio
        val f = File(tmp.root, "Imágenes/Bastet/Escritorio/${o["name"]}")
        assertArrayEquals(png, f.readBytes())
        assertEquals(id, Capturas.recientes().single().id)
        // El móvil apunta que ya la tiene: aquí se borran los trozos (la ficha queda, con «gone»), y la ficha al mes
        val r = RedJava().pedir("PATCH", movil.sesion.url("/users/$uid/suite/shots/$id/got", conPase = true), Json.escribe(mapOf("hiedra-movil" to mapOf(".sv" to "timestamp"))))
        assertTrue(r.ok)
        Capturas.limpia()
        assertNull(Emulador.lee("/users/$uid/shotdata/$id"))
        assertEquals(true, lee("/suite/shots/$id").comoMapa()["gone"])
        Capturas.limpia(System.currentTimeMillis() + 31 * 86_400_000L)
        assertNull(Emulador.lee("/users/$uid/suite/shots/$id"))
        // La orden contestada se borra a los cinco minutos
        TuPc.mira(momento = System.currentTimeMillis() + 6 * 60_000L, esperar = true)
        assertNull(Emulador.lee("/users/$uid/suite/cmd/$pc/$cid"))
    }

    @Test fun unaCapturaDeVerdadJugandoAHades() {
        // Una captura de verdad (import -window root de una pantalla de Xvfb) si se puede; si no, una generada
        imagen = ImagenesPrueba.deXvfb(1024, 768) ?: Captura.Imagen(ImagenesPrueba.png(ImagenesPrueba.ruido(320, 200)), 320, 200, "image/png", "prueba")
        val png = imagen!!.bytes
        juegaHades()
        val h = Capturas.haz(por = "Bastet · Pixel 8", alMovil = false)
        assertFalse(h.subida)
        assertNull("las de aquí no van al móvil si no se quiere", Emulador.lee("/users/$uid/suite/shots/${h.id}"))
        assertEquals("Hades", h.juego)
        assertTrue(h.archivo!!.path.contains("/Bastet/Hades/Hades "))
        // En trozos pequeños (unos veinte, múltiplos de 4), para que sean muchos y en varias tandas
        val trozo = maxOf(4, Base64.getEncoder().encodeToString(png).length / 20 / 4 * 4)
        Capturas.sube(h.id, imagen!!, h.archivo!!.name, "Bastet", Pc.ahora, trozo = trozo, tanda = 3)
        val o = lee("/suite/shots/${h.id}").comoMapa()
        assertEquals(mapOf("n" to "Hades", "k" to "steam:1145360", "sg" to 0L), o["game"].comoMapa().mapValues { (k, v) -> if (k == "sg") v.comoLong() else v })
        assertEquals(imagen!!.w.toLong(), o["w"].comoLong()); assertEquals(imagen!!.h.toLong(), o["h"].comoLong())
        assertArrayEquals(png, bajaComoElMovil(h.id))
        assertArrayEquals(png, h.archivo!!.readBytes())
    }

    @Test fun sinPermisoNoSeCapturaYLoDice() {
        imagen = Captura.Imagen(ImagenesPrueba.png(ImagenesPrueba.ruido(16, 16)), 16, 16, "image/png", "prueba")
        assertEquals(true, TuPc.presencia(true)["shot"])
        AjustesTuPc.ponCapturasDesdeMovil(false)
        assertEquals(false, TuPc.presencia(true)["shot"])
        AjustesTuPc.recarga()
        assertFalse("se guarda en este ordenador", AjustesTuPc.capturasDesdeMovil)
        val cid = manda("shot")
        TuPc.mira(esperar = true)
        val res = respuesta(cid)
        assertEquals(false, res["ok"]); assertTrue(res["msg"].comoTexto().contains("no se pueden pedir capturas desde el móvil"))
        assertNull(res["shot"])
        assertTrue(Capturas.recientes().isEmpty())
        // Si la captura falla, el móvil lo sabe
        AjustesTuPc.ponCapturasDesdeMovil(true)
        Capturas.capturaPruebas = { throw java.io.IOException("No se ha podido capturar la pantalla (spectacle: no está instalado)") }
        val otra = manda("shot")
        TuPc.mira(esperar = true)
        assertEquals(false, respuesta(otra)["ok"]); assertTrue(respuesta(otra)["msg"].comoTexto().contains("spectacle"))
    }

    @Test fun dosALaVezSonLaMisma() {
        val png = ImagenesPrueba.png(ImagenesPrueba.ruido(32, 18))
        val veces = java.util.concurrent.atomic.AtomicInteger()
        Capturas.capturaPruebas = { veces.incrementAndGet(); Thread.sleep(400); Captura.Imagen(png, 32, 18, "image/png", "prueba") }
        val hechas = java.util.Collections.synchronizedList(ArrayList<Capturas.Hecha>())
        val hilos = (1..3).map { Thread { hechas += Capturas.haz(alMovil = false) }.apply { start() } }
        hilos.forEach { it.join() }
        assertEquals("una sola captura", 1, veces.get())
        assertEquals(1, hechas.map { it.id }.distinct().size)
        // La que hacía falta para el móvil y no ha ido: otra
        val h = Capturas.haz(alMovil = true)
        assertTrue(h.subida); assertEquals(2, veces.get())
    }

    // ---------------------------------------------------------------- streaming

    @Test fun elMovilPreparaElStreaming() {
        var abierto = false
        val abiertos = ArrayList<List<String>>()
        SteamLink.ordenPruebas = { listOf("steam", "-silent") }; SteamLink.abiertoPruebas = { abierto }; SteamLink.abrePruebas = { abiertos += it; abierto = true }
        // Lo que lee el móvil de su Steam (StreamHost.from: on, run, name, lan, mac)
        TuPc.publica()
        val st = lee("/suite/live/$pc").comoMapa()["st"].comoMapa()
        assertEquals(true, st["on"]); assertEquals(false, st["run"]); assertEquals(Sitio.nombreEquipo, st["name"]); assertEquals(true, st["ok"]); assertEquals("steamlink", st["host"])
        assertTrue(st["lan"].comoLista().all { it is String }); assertTrue(st["mac"].comoLista().all { (it as String).matches(Regex("([0-9a-f]{2}:){5}[0-9a-f]{2}")) })
        // Streaming sin juego: se abre Steam
        val c1 = manda("stream")
        TuPc.mira(esperar = true)
        assertEquals(mapOf("ok" to true, "msg" to "Steam listo"), respuesta(c1).filterKeys { it != "t" })
        assertEquals(listOf(listOf("steam", "-silent")), abiertos)
        assertEquals("ya con Steam abierto", true, lee("/suite/live/$pc").comoMapa()["st"].comoMapa()["run"])
        // Con el juego que ya se está jugando: no se vuelve a abrir
        juegaHades()
        assertEquals(mapOf("ok" to true, "msg" to "Hades ya está en marcha"), TuPc.atiende("x", mapOf("op" to "stream", "a" to mapOf("key" to "steam:1145360"))))
        assertEquals(1, abiertos.size)
        val c2 = manda("stream", mapOf("key" to "steam:999"))
        TuPc.mira(esperar = true)
        assertEquals(false, respuesta(c2)["ok"]); assertTrue(respuesta(c2)["msg"].comoTexto().startsWith("Ese juego no está en"))
        // Sin Steam en este ordenador
        abierto = false; SteamLink.ordenPruebas = { null }
        val c3 = manda("stream")
        TuPc.mira(esperar = true)
        assertEquals(false, respuesta(c3)["ok"]); assertTrue(respuesta(c3)["msg"].comoTexto().startsWith("Steam no está instalado en"))
        TuPc.publica()
        assertEquals(false, lee("/suite/live/$pc").comoMapa()["st"].comoMapa()["on"])
        // Sin permiso
        SteamLink.ordenPruebas = { listOf("steam", "-silent") }
        AjustesTuPc.ponStreamingDesdeMovil(false)
        val r = TuPc.atiende("y", mapOf("op" to "stream"))
        assertEquals(false, r["ok"]); assertTrue(r["msg"].comoTexto().contains("no está permitido preparar el streaming"))
        assertEquals(false, SteamLink.presencia()["ok"])
        AjustesTuPc.ponStreamingDesdeMovil(true)
    }

}
