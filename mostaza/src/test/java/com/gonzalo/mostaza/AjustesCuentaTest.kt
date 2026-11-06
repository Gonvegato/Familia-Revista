package com.gonzalo.mostaza

import com.gonzalo.familia.LaFamilia
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Todo lo que eliges en Mostaza viaja con tu cuenta (`ajustes/mostaza_*`, un campo por ajuste) y llega a tus otros dispositivos: el
 * reproductor (subtítulos, audio, el episodio siguiente, el reproductor de fuera), el elector de fuentes (tus idiomas, la calidad, el
 * ahorro de datos), los torrents (para el motor) y el orden de cada sección. Lo de cada dispositivo (lo que puede ocupar la caché de
 * torrents) se queda en él. Y la apariencia, en `ajustes/mostaza`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AjustesCuentaTest : ConCuenta() {
    @Before fun prepara() { preparaCuenta() }
    @After fun fin() { suelta() }

    private fun arranca() {
        EspejoCuenta.inicia(ctx, SincroP.GRUPOS)
        val hasta = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < hasta) { LaFamilia.sincronizaYa(); if (EspejoCuenta.intenta(ctx)) break; Thread.sleep(100) }
        assertTrue(EspejoCuenta.intenta(ctx))
        sincroniza()
    }

    @Test fun loQueEligesViajaYLoDeAquiSeQuedaAqui() {
        arranca()
        // Aquí: tus idiomas (latino primero), sin subtítulos, como mucho 1080p; los subtítulos en inglés y grandes; los torrents
        AjustesElector.pon(ctx, PrefsElector(idiomas = listOf("latino", "castellano"), subtitulados = false, maxima = 1080, directasPrimero = false, ahorroDatos = true))
        AjustesReproductor.ponSubs(ctx, "eng"); AjustesReproductor.ponSubTam(ctx, 150); AjustesReproductor.ponSiguiente(ctx, false); AjustesReproductor.ponExterno(ctx, "org.videolan.vlc")
        AjustesTorrents.pon(ctx, AjustesTorrent(soloWifi = false, subidaKBs = 500, bajadaKBs = 0, compartir = true, cacheMB = 16384))
        OrdenGuardado.pon(ctx, Estado.VISTO, OrdenGuardado.Orden.TITULO); OrdenGuardado.ponFiltro(ctx, Estado.VISTO, OrdenGuardado.Filtro.SERIES)
        Skin.accentId = "verde"; Skin.guardar(ctx)
        sincroniza()
        val aj = otro.coleccion("ajustes")
        val el = aj.nodo("mostaza_elector")!!
        assertEquals("latino,castellano", el.texto("idiomas")); assertEquals(false, el.si("subtitulados")); assertEquals(1080L, el.entero("maxima")); assertEquals(false, el.si("directas"))
        val re = aj.nodo("mostaza_reproductor")!!
        assertEquals("eng", re.texto("subs")); assertEquals(150L, re.entero("subTam")); assertEquals(false, re.si("siguiente")); assertEquals("org.videolan.vlc", re.texto("externo"))
        val to = aj.nodo("mostaza_torrent")!!
        assertEquals(false, to.si("soloWifi")); assertEquals(500L, to.entero("subida"))
        assertNull("lo que ocupa la caché es de cada dispositivo", to["cacheMB"])
        assertEquals("TITULO", aj.nodo("mostaza_orden")!!.texto("VISTO")); assertEquals("SERIES", aj.nodo("mostaza_orden")!!.texto("filtro_VISTO"))
        assertEquals("verde", aj.nodo("mostaza")!!.mapa("skin")["skin"].let { (it as Map<*, *>)["acento"] })
        // En tu otro dispositivo lo cambias: llega aquí
        aj.pon("mostaza_elector", mapOf("idiomas" to "castellano,vose", "ahorro" to false))
        aj.pon("mostaza_torrent", mapOf("subida" to 100L))
        sincroniza()
        espera("llega lo del otro dispositivo") { AjustesElector.prefs(ctx).idiomas == listOf("castellano", "vose") }
        val p = AjustesElector.prefs(ctx)
        assertEquals(false, p.ahorroDatos); assertEquals("lo que no ha cambiado allí, igual", 1080, p.maxima)
        espera("y lo de los torrents, para el motor") { Torrents.ajustes(ctx).subidaKBs == 100 }
        assertEquals("y la caché, la de aquí", 16384, Torrents.ajustes(ctx).cacheMB)
    }
}
