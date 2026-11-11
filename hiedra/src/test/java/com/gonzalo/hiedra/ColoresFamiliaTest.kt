package com.gonzalo.hiedra

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Noviembre de 2026: los colores de la familia de Bastet pasan a la paleta «arena»; lo que se eligió a mano se queda. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColoresFamiliaTest {
    private fun limpio() { Skin.fondoId = ""; Skin.accentId = "paleta"; Skin.palette = PALETTES.first { it.id == "revista" }; Skin.origenAtrio = true }

    @Test fun laFamiliaEsLaDeSerie() {
        ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("hiedra_tema", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        Skin.cargar(ApplicationProvider.getApplicationContext())
        assertEquals("familia", Skin.palette.id); assertEquals(0L, Skin.fondoPropio); assertFalse(Skin.origenAtrio)
        val p = PALETTES.first { it.id == "familia" }
        assertEquals(0xFFF8F0D4L, p.light.paper); assertEquals(0xFF000000L, p.light.ink); assertEquals(0xFF000000L, p.dark.paper); assertEquals(0xFFF8F0D4L, p.dark.ink)
        assertEquals(p.light.general, p.dark.general)
    }

    /** El botón «Colores de …» de Apariencia: los últimos de serie, quitando lo elegido a mano. */
    @Test fun elBotonPoneLosUltimosColores() {
        limpio(); Skin.fondoPropio = 0xFF123456; Skin.tintaPropia = 0xFFFFFFFF; Skin.acentoPropio = 0xFF00AA00; Skin.accentId = "azul"; Skin.fondoId = FONDOS.first().id; Skin.origenAtrio = true
        Skin.aColoresDeSerie()
        assertEquals("familia", Skin.palette.id); assertEquals("paleta", Skin.accentId); assertEquals("", Skin.fondoId)
        assertEquals(0L, Skin.fondoPropio); assertEquals(0L, Skin.tintaPropia); assertEquals(0L, Skin.acentoPropio); assertFalse(Skin.origenAtrio)
    }
}
