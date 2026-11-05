package com.gonzalo.hiedra.pc

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.RedJava
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Hiedra del ordenador con la cuenta de verdad (el emulador): sus juegos son los de Hiedra del móvil, y hace de «Tu PC» para el móvil. */
class HiedraTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var otra: Familia

    @Before fun prepara() {
        Emulador.limpia(); Emulador.cuenta("gonzalo")
        otra = Emulador.dispositivo("gonzalo")
        LaFamilia.reiniciaParaPruebas(); LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg; LaFamilia.dirPruebas = tmp.root; LaFamilia.llaveroPruebas = LlaveroMemoria()
        LaFamilia.inicia("hiedra").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Juegos.dispositivo = { "hiedra-linux-pc1" }; Pc.dirPruebas = tmp.root
        // Un Steam de mentira, con un juego y una herramienta (que no cuenta)
        val steam = File(tmp.root, "Steam"); File(steam, "steamapps").mkdirs()
        File(steam, "steamapps/appmanifest_1145360.acf").writeText("\"AppState\"\n{\n\t\"appid\"\t\t\"1145360\"\n\t\"name\"\t\t\"Hades\"\n}")
        File(steam, "steamapps/appmanifest_1628350.acf").writeText("\"AppState\"\n{\n\t\"appid\"\t\t\"1628350\"\n\t\"name\"\t\t\"Steam Linux Runtime 3.0 (sniper)\"\n}")
        Pc.steamPruebas = listOf(steam)
        listOf("juegos", "tiempo").forEach { LaFamilia.coleccion(it); otra.coleccion(it) }
    }

    @After fun fuera() { LaFamilia.reiniciaParaPruebas(); Pc.steamPruebas = null; Pc.dirPruebas = null }
    private fun sincroniza() { assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otra.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok) }

    @Test fun losJuegosDeSteamDeAquiSonLosQueYaTenias() {
        assertEquals(listOf("steam:1145360"), Pc.steam().map { it.clave })
        // Hades ya estaba en la cuenta (de Atrio del PC: su lanzador de PC con la misma clave)
        otra.coleccion("juegos").pon("j_aaaaaaaaaaaa", mapOf("t" to "Hades", "pl" to "PC", "estado" to "en_curso", "ln" to listOf(mapOf("tp" to "PC", "pck" to "steam:1145360", "pcd" to "pc-atrio"))))
        sincroniza()
        val id = Juegos.mete("steam:1145360", "Hades")
        assertEquals("el mismo juego, sin duplicar", "j_aaaaaaaaaaaa", id)
        sincroniza()
        val ln = otra.coleccion("juegos").nodo(id)!!.lista("ln").map { it.comoMapa() }
        assertTrue(ln.any { it["pck"] == "steam:1145360" && it["pcd"] == "hiedra-linux-pc1" })
        // Uno nuevo: un juego de la cuenta como los de Hiedra del móvil
        val otro = Juegos.mete("local:abc", "Celeste")
        assertTrue(otro.matches(Regex("j_[0-9a-f]{12}")))
        sincroniza()
        assertEquals("pendiente", otra.coleccion("juegos").nodo(otro)!!.texto("estado"))
    }

    @Test fun loJugadoAquiVaALasHorasDeEsteOrdenador() {
        val id = Juegos.mete("local:abc", "Celeste")
        Juegos.sumaPartida(id, 45 * 60_000L)
        Juegos.sumaPartida(id, 30_000L)   // menos de un minuto: no cuenta
        sincroniza()
        val t = otra.coleccion("tiempo").nodo("$id~hiedra-linux-pc1")!!
        assertEquals(45 * 60_000L, t.entero("total")); assertEquals(1L, t.entero("veces")); assertEquals(id, t.texto("ref"))
        assertEquals(45 * 60_000L, t.entero("d" + java.time.LocalDate.now()))
        assertEquals("en_curso", otra.coleccion("juegos").nodo(id)!!.texto("estado"))
    }

    @Test fun elMovilLoVeEnTuPcYPuedeAbrirUnJuego() {
        TuPc.publica(); TuPc.publicaBiblioteca()
        val uid = otra.sesion.uid
        val live = Emulador.lee("/users/$uid/suite/live/hiedra-linux-pc1").comoMapa()
        assertEquals("atrio", live["app"]); assertEquals(true, live["on"]); assertEquals(true, live["tiempo"])
        val lib = Emulador.lee("/users/$uid/suite/lib/hiedra-linux-pc1").comoMapa()["games"] as List<*>
        assertEquals("Hades", (lib.single() as Map<*, *>)["n"])
        // El móvil manda una orden (con su sesión) y el ordenador contesta
        val r = RedJava().pedir("POST", otra.sesion.url("/users/$uid/suite/cmd/hiedra-linux-pc1", conPase = true), Json.escribe(mapOf("op" to "ping", "a" to emptyMap<String, Any?>(), "t" to mapOf(".sv" to "timestamp"), "by" to "Hiedra · móvil")))
        assertTrue(r.ok)
        val cid = Json.lee(r.cuerpo).comoMapa()["name"] as String
        val res = TuPc.atiende(cid, mapOf("op" to "ping"))
        assertEquals(true, res["ok"])
        assertEquals(false, TuPc.atiende("x", mapOf("op" to "launch", "a" to mapOf("key" to "steam:999")))["ok"])
    }
}
