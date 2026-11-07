package com.gonzalo.cobre

import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.Respuesta
import com.gonzalo.iptv.TipoLista
import com.gonzalo.iptv.futbol.CanalesFutbol
import com.gonzalo.iptv.futbol.RefCanal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Tus equipos y tus grupos de canales viajan con la cuenta (`ajustes/cobre_futbol`) y llegan a tu otro dispositivo, **sin nada secreto**:
 * `/users/<uid>` lo puede leer tu pareja, así que de cada canal va su clave y su nombre, nunca la dirección (que lleva usuario y contraseña).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FutbolCuentaTest : ConCuenta() {
    private val clave = "Pw-9f8e7d6c5b"
    private val m3u = Lista("lm3u0000001", "Mi lista", TipoLista.M3U, "http://lista.ejemplo.test/get.php?token=TOKEN-a1b2")
    private val red = Red { url, _ ->
        if (!url.startsWith("http://lista.ejemplo.test/")) throw com.gonzalo.iptv.IptvError("sin internet")
        Respuesta(200, ByteArrayInputStream("""
            #EXTM3U
            #EXTINF:-1 tvg-id="dazn.es" group-title="Deportes",ES: DAZN LA LIGA UHD
            http://tv.ejemplo.test:8080/live/usuarioPepe/$clave/201.ts
            #EXTINF:-1 tvg-id="dazn.es" group-title="Deportes",ES: DAZN LA LIGA HD
            http://tv.ejemplo.test:8080/live/usuarioPepe/$clave/202.ts
        """.trimIndent().toByteArray()))
    }

    @Before fun prepara() { preparaCuenta() }
    @After fun fin() { suelta() }

    @Test fun losGruposViajanSinSecretos() {
        EspejoCuenta.inicia(ctx, SincroP.GRUPOS)
        val hasta = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < hasta) { LaFamilia.sincronizaYa(); if (EspejoCuenta.intenta(ctx)) break; Thread.sleep(100) }
        Listas.pon(m3u)
        Tele.red = red
        Tele.almacen.actualizaCanales(m3u)
        val cs = Tele.cargada(m3u)!!.canales
        val refs = cs.map { RefCanal(m3u.id, it.clave, it.nombre) }
        AjustesFutbol.cambiaGrupo(ctx, "dazn") { CanalesFutbol.anade(it, CanalesFutbol.entradaCon(refs[0], refs)) }
        AjustesFutbol.ponEquipos(ctx, listOf(CanalesFutbol.SUGERIDO to "esp.1"))
        sincroniza()
        val n = otro.coleccion("ajustes").nodo("cobre_futbol")!!
        val grupos = CanalesFutbol.gruposDeTexto(n.texto("grupos"))
        assertEquals("llega a tu otro dispositivo, con sus dos calidades", listOf("ES: DAZN LA LIGA UHD", "ES: DAZN LA LIGA HD"), grupos.first { it.id == "dazn" }.canales[0].todas.map { it.nombre })
        assertEquals(listOf("83"), CanalesFutbol.equiposDeTexto(n.texto("equipos")).map { it.first.id })
        val users = Json.escribe(Emulador.lee("/users/$uid"))
        assertTrue(users.contains("DAZN LA LIGA UHD"))
        assertFalse("ni la contraseña", users.contains(clave)); assertFalse("ni el token", users.contains("TOKEN-a1b2")); assertFalse("ni el usuario", users.contains("usuarioPepe"))
        assertFalse("ni ninguna dirección de la lista", users.contains("ejemplo.test"))
    }
}
