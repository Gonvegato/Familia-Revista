package com.gonzalo.cobre

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.IptvError
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.Respuesta
import com.gonzalo.iptv.TipoLista
import com.gonzalo.iptv.futbol.Calidad
import com.gonzalo.iptv.futbol.CanalesFutbol
import com.gonzalo.iptv.futbol.GrupoCanales
import com.gonzalo.iptv.futbol.RefCanal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * El fútbol en la app (sin cuenta y sin internet: ESPN de mentira con sus respuestas de verdad): tus ajustes, la caché, el canal que se
 * pone de cada grupo (y en qué calidad) y el interruptor de las pelis y series.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FutbolAppTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun recurso(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.readBytes()
    private var pedidas = 0
    private var hayRed = true
    private val urls = java.util.Collections.synchronizedList(ArrayList<String>())
    private val espn = Red { url, _ ->
        pedidas++; urls += url
        if (!hayRed) throw IptvError("sin red")
        val cuerpo = when {
            url.contains("/esp.1/teams/83/schedule?fixture=true") -> recurso("espn/calendario_proximos.json")
            url.contains("/esp.1/teams/83/schedule?lang") -> recurso("espn/calendario_jugados.json")
            url.contains("/uefa.nations/teams/164/schedule?fixture=true") -> recurso("espn/selecciones_proximos.json")
            url.contains("/fifa.world/teams/164/schedule?lang") -> recurso("espn/selecciones_mundial.json")
            url.contains("/esp.1/teams/164/") -> error("a una selección no se le piden las ligas de clubes")
            url.contains("/uefa.nations/teams/83/") -> error("a un club no se le piden las de selecciones")
            url.contains("/schedule") -> """{"events":[]}""".toByteArray()
            url.contains("/scoreboard") -> recurso("espn/marcadores.json")
            else -> return@Red Respuesta(404, ByteArrayInputStream(ByteArray(0)))
        }
        Respuesta(200, ByteArrayInputStream(cuerpo))
    }

    @Before fun prepara() { iniciar(ctx); Futbol.olvidaParaPruebas(); Futbol.cargar(ctx); Futbol.limpia(todo = true); Futbol.red = espn }
    @After fun fin() { Futbol.olvidaParaPruebas(); Tele.fijas = null }

    @Test fun lasPelisYSeriesEscondidasDeSerie() {
        assertFalse("apagado de serie", AjustesDirecto.videoclub(ctx))
        val sin = seccionesMenu(hayXtream = true, videoclub = false).map { it.second }
        assertEquals(Destino.Futbol, sin.first())
        assertFalse(Destino.Peliculas in sin); assertFalse(Destino.Series in sin)
        AjustesDirecto.ponVideoclub(ctx, true)
        val con = seccionesMenu(hayXtream = true, videoclub = AjustesDirecto.videoclub(ctx)).map { it.second }
        assertTrue(Destino.Peliculas in con); assertTrue(Destino.Series in con)
        assertFalse("sin Xtream, no hay videoclub", Destino.Peliculas in seccionesMenu(hayXtream = false, videoclub = true).map { it.second })
        AjustesDirecto.ponVideoclub(ctx, false)
    }

    @Test fun tusAjustesDelFutbol() {
        assertTrue("sin equipos de serie (el Barça, solo sugerido)", AjustesFutbol.equipos(ctx).isEmpty())
        assertEquals(CanalesFutbol.GRUPOS, AjustesFutbol.grupos(ctx))
        assertEquals(listOf("movistar", "dazn"), AjustesFutbol.gruposDe(ctx, "esp.1").map { it.id })
        AjustesFutbol.alterna(ctx, CanalesFutbol.SUGERIDO, "esp.1")
        assertEquals(listOf("83"), AjustesFutbol.equipos(ctx).map { it.first.id })
        AjustesFutbol.alternaAsignado(ctx, "uefa.champions", "movistar")
        assertEquals(listOf("champions", "movistar"), AjustesFutbol.gruposDe(ctx, "uefa.champions").map { it.id })
        assertEquals(null, AjustesFutbol.calidad(ctx))
        AjustesFutbol.ponCalidad(ctx, Calidad.SD); assertEquals(Calidad.SD, AjustesFutbol.calidad(ctx))
        AjustesFutbol.ponCalidad(ctx, null)
    }

    @Test fun losPartidosDeTusEquiposConCacheYSinRed() {
        AjustesFutbol.ponEquipos(ctx, listOf(CanalesFutbol.SUGERIDO to "esp.1"))
        val l = Futbol.partidos(ctx)
        assertEquals("los 5 que vienen y los 4 jugados", 9, l.size)
        assertTrue(l.any { it.id == "401882822" && it.marcador == "6-1" })
        val antes = pedidas
        Futbol.partidos(ctx)
        assertEquals("todo de la caché: nada se pide", antes, pedidas)
        hayRed = false
        assertEquals("sin red, lo guardado", 9, Futbol.partidos(ctx, fresco = false).size)
    }

    @Test fun cadaBotonPoneSuCanalEnLaCalidadQueToca() {
        val lista = Lista("lfutbol0001", "Mi lista", TipoLista.M3U, "http://lista.ejemplo.test/get.m3u?token=abc")
        val canales = listOf(Canal("x1", "ES: DAZN LA LIGA UHD"), Canal("x2", "ES: DAZN LA LIGA HD"), Canal("x3", "ES: DAZN LA LIGA SD"), Canal("x4", "ES: M+ LALIGA HD"), Canal("x5", "ES: M+ LALIGA SD"))
        Listas.guarda(listOf(lista))
        Tele.fijas = mapOf(lista.id to Cargada(lista, emptyList(), canales, null))
        fun ref(c: Canal) = RefCanal(lista.id, c.clave, c.nombre)
        var g = CanalesFutbol.anade(GrupoCanales("dazn", "DAZN LaLiga"), CanalesFutbol.entradaCon(ref(canales[0]), canales.map(::ref)))
        g = CanalesFutbol.anade(g, CanalesFutbol.entradaCon(ref(canales[3]), canales.map(::ref)))
        assertEquals("al elegir la UHD, las otras dos se encuentran solas", 3, g.canales[0].calidades.size)
        assertEquals(listOf(Calidad.HD, Calidad.SD), g.canales[1].calidades)
        fun toma() = Reproducir.toma() as Encargo.Directo
        assertEquals(null, Futbol.pon(ctx, g, 0, Calidad.UHD))
        toma().let { e -> assertEquals("x1", e.canales[e.indice].clave); assertEquals(listOf("x1", "x4"), e.canales.map { it.clave }); assertNotNull(e.plan); assertEquals(3, e.plan!!.canales[0].size) }
        Futbol.pon(ctx, g, 0, Calidad.SD)
        toma().let { e -> assertEquals("x3", e.canales[e.indice].clave) }
        Futbol.pon(ctx, g, 1, Calidad.UHD)
        toma().let { e -> assertEquals("sin Ultra HD, la HD", "x4", e.canales[e.indice].clave); assertEquals(1, e.indice) }
        assertTrue("un grupo vacío lo dice", Futbol.pon(ctx, GrupoCanales("copa", "Copa"))!!.contains("no tiene canales"))
        // La conexión de Android se lee sin fallar (en las pruebas, sin red)
        Futbol.conexion(ctx)
    }

    @Test fun rivalesYSelecciones() {
        val esp = com.gonzalo.iptv.futbol.Equipo("164", "Spain", "Spain", "ESP")
        AjustesFutbol.alterna(ctx, CanalesFutbol.SUGERIDO, "esp.1")
        AjustesFutbol.alterna(ctx, esp, "uefa.nations")
        val rma = com.gonzalo.iptv.futbol.Equipo("86", "Real Madrid")
        AjustesFutbol.alterna(ctx, rma, "esp.1", rival = true)
        assertEquals(listOf("83", "164"), AjustesFutbol.equipos(ctx).map { it.first.id }); assertEquals(listOf("86"), AjustesFutbol.rivales(ctx).map { it.first.id })
        // Al hacer rival a uno tuyo, deja de ser tuyo
        AjustesFutbol.alterna(ctx, CanalesFutbol.SUGERIDO, "esp.1", rival = true)
        assertEquals(listOf("164"), AjustesFutbol.equipos(ctx).map { it.first.id }); assertEquals(listOf("86", "83"), AjustesFutbol.rivales(ctx).map { it.first.id })
        AjustesFutbol.mueve(ctx, "83", -1, rival = true)
        assertEquals(listOf("83", "86"), AjustesFutbol.rivales(ctx).map { it.first.id })
        // Los partidos: los de la selección (de sus competiciones) y los de los rivales
        val l = Futbol.partidos(ctx)
        assertTrue("España en la Liga de Naciones", l.any { it.liga == "uefa.nations" && it.visitante.id == "164" })
        assertTrue("y en el Mundial", l.any { it.liga == "fifa.world" && it.estado == com.gonzalo.iptv.futbol.Estado.FINAL_PRORROGA })
        assertTrue("los del Barça, ahora rival", l.any { it.id == "401882822" })
        assertTrue("a una selección no se le piden las ligas de clubes", urls.none { it.contains("/esp.1/teams/164/") })
        assertTrue("a un club no se le piden las de selecciones", urls.none { it.contains("/uefa.nations/teams/83/") })
        assertEquals(listOf("seleccion"), AjustesFutbol.gruposDe(ctx, "uefa.nations").map { it.id })
        assertTrue(AjustesFutbol.grupos(ctx).any { it.id == "seleccion" && it.nombre == "Selección" })
    }
}
