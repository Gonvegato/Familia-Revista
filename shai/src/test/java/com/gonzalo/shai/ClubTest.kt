package com.gonzalo.shai

import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Identificar el club con la búsqueda web (con la API de mentira: no sale nada a la red ni se gasta nada). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClubTest {
    private lateinit var api: ApiDeMentira

    @Before fun prepara() { api = Ejemplo.prepara() }
    @After fun suelta() { ClaveApi.fija = null; Gasto.mesFijo = null }

    @Test fun laPeticionLlevaLaBusquedaWebYNoLaSalidaEstructurada() {
        Clubes.identifica("Betis", "LaLiga")
        val c = api.mensajes().last().cuerpo
        assertEquals(Claude.SONNET, c["model"])
        val t = c["tools"].comoLista().single().comoMapa()
        assertEquals("web_search_20260209", t["type"]); assertEquals("web_search", t["name"]); assertEquals(3L, (t["max_uses"] as Number).toLong())
        // (con la búsqueda web van las citas, y la salida estructurada no las admite: el JSON va en el texto)
        assertNull(c["output_config"].comoMapa()["format"])
        assertTrue(c["messages"].comoLista().single().comoMapa()["content"].comoLista().single().comoMapa()["text"].comoTexto().contains("«Betis»"))
        assertEquals("2026-10-21", api.mensajes().last().cabeceras["anthropic-version"])
    }

    @Test fun leeLaRespuestaConSusBloquesYSusFuentesYApuntaElGasto() {
        val antes = Gasto.mes()
        val c = Clubes.identifica("Betis", "LaLiga")
        assertEquals("Real Betis Balompié", c.nombre); assertEquals("Benito Villamarín", c.estadio); assertEquals(60_721L, c.aforo)
        assertEquals(listOf("Sevilla FC"), c.rivales); assertFalse(c.confirmado); assertFalse(c.deMentira)
        assertTrue(c.fuentes.any { it.second == "https://www.realbetisbalompie.es/" }); assertTrue(c.fuentes.any { it.second == "https://www.laliga.com/" })
        assertEquals(1, c.busquedas)
        // 10.000 de entrada y 800 de salida en Sonnet (2 $ y 10 $ el millón) + una búsqueda (1 céntimo)
        val esperado = 10_000 * 2.0 / 1e6 + 800 * 10.0 / 1e6 + 0.01
        assertEquals(esperado, c.coste, 1e-6)
        assertEquals(esperado, Gasto.mes() - antes, 1e-5)
        assertTrue(c.resumen.startsWith("Real Betis Balompié · Sevilla · Benito Villamarín (60.721) · LaLiga, Primera"))
    }

    @Test fun siLaApiParaLaVueltaSeLeDevuelveParaQueSiga() {
        api.pausaClub = true
        val c = Clubes.identifica("Betis", "LaLiga")
        assertEquals(2, api.mensajes().size)
        val segunda = api.mensajes().last().cuerpo["messages"].comoLista()
        assertEquals("assistant", segunda.last().comoMapa()["role"])
        assertEquals(2, c.busquedas)
        assertTrue(c.fuentes.any { it.second.contains("wikipedia") })
    }

    @Test fun busquedaApagadaEnLaOrganizacion() {
        api.siguiente = Transporte.Respuesta(400, """{"type":"error","error":{"type":"invalid_request_error","message":"Web search is not enabled for this organization."}}""")
        val e = runCatching { Clubes.identifica("Betis", "LaLiga") }.exceptionOrNull()
        assertTrue(e is Clubes.Desactivada)
        assertTrue(Motor.mensaje(e!!).contains("Settings → Capabilities"))
    }

    @Test fun sinJsonHaikuLoPasaYSiNoExisteLoDice() {
        api.textoClub = """{"encontrado": false, "nombre": "", "dudas": "No hay ningún club con ese nombre."}"""
        val e = runCatching { Clubes.identifica("Atlético Inventadísimo", "") }.exceptionOrNull()
        assertTrue(e is Clubes.NoEncontrado)
        assertTrue(e!!.message!!.contains("No hay ningún club"))
        assertNull(Clubes.jsonDe("sin llaves"))
        assertEquals("Betis", Clubes.jsonDe("bla {\"nombre\": \"Betis\"} bla")!!["nombre"])
    }

    @Test fun seGuardaSeConfirmaYVaEnElContexto() {
        val (u, _) = Motor.crea(Ejemplo.universo())
        assertNull(Clubes.contexto(u))
        assertNull(Motor.identifica(u.id))
        var x = Carreras.de(u.id)!!
        assertNotNull(x.clubPorConfirmar); assertFalse(x.clubIdentificado)
        // Sin confirmar, no va a Claude
        assertNull(Clubes.contexto(x))
        Motor.confirmaClub(u.id)
        x = Carreras.de(u.id)!!
        assertTrue(x.clubIdentificado)
        // Se guarda en la carrera (JSON) y se lee igual; las carreras de antes (sin «ic») siguen leyéndose
        assertEquals(x.clubInfo, Universo.deJson(x.aJson())!!.clubInfo)
        assertNull(Universo.deJson(x.copy(clubInfo = null).aJson())!!.clubInfo)
        val ctx = Narrador.contexto(x, emptyList(), emptyList())
        assertTrue(ctx.contains("LO QUE SE SABE DEL CLUB")); assertTrue(ctx.contains("Benito Villamarín (60.721 espectadores)"))
        assertTrue(ctx.contains("SOLO como estilo"))
        // Con un traspaso, el club de ahora ya no es el identificado
        val otro = x.copy(club = "Villarreal CF")
        assertFalse(otro.clubIdentificado); assertNull(Clubes.contexto(otro))
    }

    @Test fun enModoPruebaNoSeBuscaNada() {
        Prueba.pon(org.robolectric.RuntimeEnvironment.getApplication(), true)
        try {
            val n = api.mensajes().size
            val c = Clubes.identifica("Betis", "LaLiga")
            assertTrue(c.deMentira); assertTrue(c.dudas.contains("no se ha buscado nada"))
            assertEquals(n, api.mensajes().size)
        } finally { Prueba.pon(org.robolectric.RuntimeEnvironment.getApplication(), false) }
    }
}
