package com.gonzalo.cobre

import com.gonzalo.familia.LaFamilia
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Todo lo que eliges en Cobre viaja con tu cuenta (`ajustes/cobre_*`, un campo por ajuste) y llega a tus otros dispositivos: el
 * reproductor, la guía y los canales (su orden, dónde estabas, abrir con el último). Y la apariencia, en `ajustes/cobre`.
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

    @Test fun loQueEligesViaja() {
        arranca()
        AjustesReproductor.ponAspecto(ctx, AjustesReproductor.Aspecto.ZOOM); AjustesReproductor.ponAhorro(ctx, false); AjustesReproductor.ponSubs(ctx, "spa")
        AjustesReproductor.ponInvertirCanales(ctx, true); AjustesReproductor.ponExterno(ctx, "org.videolan.vlc")
        AjustesGuia.ponHoras(ctx, 24); AjustesGuia.ponSoloWifi(ctx, false)
        AjustesDirecto.ponOrden(ctx, AjustesDirecto.Orden.NUMERO); AjustesDirecto.ponDonde(ctx, "lprueba001", "ES | Deportes"); AjustesDirecto.ponAlAbrir(ctx, true)
        Skin.accentId = "verde"; Skin.guardar(ctx)
        sincroniza()
        val aj = otro.coleccion("ajustes")
        val re = aj.nodo("cobre_reproductor")!!
        assertEquals("ZOOM", re.texto("aspecto")); assertEquals(false, re.si("ahorro")); assertEquals("spa", re.texto("subs")); assertEquals(true, re.si("invertirCanales")); assertEquals("org.videolan.vlc", re.texto("externo"))
        val gu = aj.nodo("cobre_guia")!!
        assertEquals(24L, gu.entero("horas")); assertEquals(false, gu.si("soloWifi"))
        val di = aj.nodo("cobre_directo")!!
        assertEquals("NUMERO", di.texto("orden")); assertEquals("lprueba001", di.texto("lista")); assertEquals("ES | Deportes", di.texto("grupo")); assertEquals(true, di.si("alAbrir"))
        assertEquals("verde", aj.nodo("cobre")!!.mapa("skin")["skin"].let { (it as Map<*, *>)["acento"] })
        // En tu otro dispositivo lo cambias: llega aquí
        aj.pon("cobre_reproductor", mapOf("aspecto" to "ANCHO"))
        aj.pon("cobre_guia", mapOf("dias" to 7L))
        sincroniza()
        espera("llega lo del otro dispositivo") { AjustesReproductor.aspecto(ctx) == AjustesReproductor.Aspecto.ANCHO }
        espera("y la guía") { AjustesGuia.dias(ctx) == 7 }
        assertEquals("lo que no ha cambiado allí, igual", 24, AjustesGuia.horas(ctx))
    }
}
