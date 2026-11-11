package com.gonzalo.marfil

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El botón «Colores de Seshat» de Apariencia: papel amarillo, tinta casi negra y mostaza, quitando lo demás elegido a mano. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColoresDeSerieTest {
    @Test fun elBotonPoneLosDeSeshat() {
        Skin.fondoId = FONDOS.first().id; Skin.accentId = "azul"; Skin.origenAtrio = true; Skin.fondoPropio = 0xFF123456
        Skin.aColoresDeSerie()
        assertEquals(0xFFF8F0D4L, Skin.fondoPropio); assertEquals(0xFF050404L, Skin.tintaPropia); assertEquals(0xFFC68B00L, Skin.acentoPropio)
        assertEquals("", Skin.fondoId); assertEquals("paleta", Skin.accentId); assertFalse(Skin.origenAtrio)
    }
}
