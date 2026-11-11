package com.gonzalo.marfil

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El botón «Colores de Seshat» de Apariencia: los colores de la familia con el ocre de Seshat, quitando lo elegido a mano. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColoresDeSerieTest {
    @Test fun elBotonPoneLosDeSeshat() {
        Skin.fondoId = FONDOS.first().id; Skin.accentId = "azul"; Skin.origenAtrio = true; Skin.fondoPropio = 0xFF123456
        Skin.aColoresDeSerie()
        assertEquals("familia", Skin.palette.id); assertEquals(0L, Skin.fondoPropio); assertEquals(0L, Skin.tintaPropia); assertEquals(0L, Skin.acentoPropio)
        assertEquals("", Skin.fondoId); assertEquals("paleta", Skin.accentId); assertFalse(Skin.origenAtrio)
    }
}
