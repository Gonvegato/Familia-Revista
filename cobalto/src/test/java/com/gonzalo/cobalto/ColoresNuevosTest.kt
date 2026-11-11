package com.gonzalo.cobalto

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Noviembre de 2026: los colores de serie de Thot pasan a la paleta «noche»; lo que se eligió a mano se queda. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColoresNuevosTest {
    private fun limpio() { Skin.fondoId = ""; Skin.accentId = "paleta"; Skin.palette = PALETTES.first { it.id == "revista" }; Skin.origenAtrio = true }

    @Test fun losDeSerieDeAntesPasanALosNuevos() {
        limpio(); Skin.fondoPropio = 0xFF171E2B; Skin.tintaPropia = 0xFFF1EBE0; Skin.acentoPropio = 0xFFC8703F
        assertTrue(Skin.aColoresNuevos())
        assertEquals("noche", Skin.palette.id); assertEquals(0L, Skin.fondoPropio); assertEquals(0L, Skin.tintaPropia); assertEquals(0L, Skin.acentoPropio)
        assertFalse("ya no sigue los colores de Jonsu", Skin.origenAtrio)
        assertFalse("la segunda vez no cambia nada", Skin.aColoresNuevos())
    }

    @Test fun sinNadaElegidoTambien() {
        limpio(); Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L
        assertTrue(Skin.aColoresNuevos()); assertEquals("noche", Skin.palette.id)
    }

    @Test fun loElegidoAManoSeQueda() {
        limpio(); Skin.fondoPropio = 0xFF123456; Skin.tintaPropia = 0xFFFFFFFF; Skin.acentoPropio = 0xFF00AA00
        assertFalse(Skin.aColoresNuevos())
        assertEquals(0xFF123456L, Skin.fondoPropio); assertEquals("revista", Skin.palette.id)
        limpio(); Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L; Skin.accentId = "azul"
        assertFalse("con otro acento elegido, la paleta se queda", Skin.aColoresNuevos()); assertEquals("revista", Skin.palette.id)
        Skin.accentId = "paleta"
    }

    @Test fun laNuevaEsLaDeSerie() {
        ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("cobalto_tema", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        Skin.cargar(ApplicationProvider.getApplicationContext())
        assertEquals("noche", Skin.palette.id); assertEquals(0L, Skin.fondoPropio)
    }

    /** El botón «Colores de …» de Apariencia: los últimos de serie, quitando lo elegido a mano. */
    @Test fun elBotonPoneLosUltimosColores() {
        limpio(); Skin.fondoPropio = 0xFF123456; Skin.tintaPropia = 0xFFFFFFFF; Skin.acentoPropio = 0xFF00AA00; Skin.accentId = "azul"; Skin.fondoId = FONDOS.first().id; Skin.origenAtrio = true
        Skin.aColoresDeSerie()
        assertEquals("noche", Skin.palette.id); assertEquals("paleta", Skin.accentId); assertEquals("", Skin.fondoId)
        assertEquals(0L, Skin.fondoPropio); assertEquals(0L, Skin.tintaPropia); assertEquals(0L, Skin.acentoPropio); assertFalse(Skin.origenAtrio)
    }
}
