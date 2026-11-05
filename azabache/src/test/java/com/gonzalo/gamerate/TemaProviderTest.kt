package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Los colores que Atrio ofrece a Pórtico salen de los ajustes guardados, con las mismas reglas que la pantalla. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemaProviderTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val p get() = ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
    private val revista = PALETTES.first { it.id == "revista" }

    @Test fun porDefectoSonLosDeLaRevistaClaraOOscura() {
        val (t, oscuro) = TemaProvider.calcula(p, 1, sistemaOscuro = true)
        assertFalse(oscuro); assertEquals(revista.light.paper, t.paper); assertEquals(revista.light.general, t.general)
        val (d, os) = TemaProvider.calcula(p, 2, sistemaOscuro = false)
        assertTrue(os); assertEquals(revista.dark.paper, d.paper)
        // «como el sistema» sigue al sistema
        assertTrue(TemaProvider.calcula(p, 0, sistemaOscuro = true).second); assertFalse(TemaProvider.calcula(p, 0, sistemaOscuro = false).second)
    }

    @Test fun elAcentoElegidoSeVeEnClaroYEnOscuro() {
        val pr = p; pr.edit().putString("skin_accent_id", "azul").commit()
        val a = ACCENT_COLORS.first { it.id == "azul" }
        assertEquals(a.light, TemaProvider.calcula(pr, 1, false).first.general)
        assertEquals(a.dark, TemaProvider.calcula(pr, 2, false).first.general)
    }

    @Test fun losColoresAManoMandanYFijanElModo() {
        val pr = p; pr.edit().putLong("skin_fondo_propio", 0xFF202020L).putLong("skin_tinta_propia", 0xFFEEDDCCL).putLong("skin_acento_propio", 0xFF00AA55L).commit()
        // aunque el tema sea claro, un fondo oscuro a mano hace oscuro el modo
        val (t, oscuro) = TemaProvider.calcula(pr, 1, false)
        assertTrue(oscuro); assertEquals(0xFF202020L, t.paper); assertEquals(0xFFEEDDCCL, t.ink); assertEquals(0xFF00AA55L, t.general)
    }

    @Test fun unFondoDeColorFijaElModoYSuAcento() {
        val pr = p; pr.edit().putString("skin_fondo", "granate").commit()
        val f = FONDOS.first { it.id == "granate" }
        val (t, oscuro) = TemaProvider.calcula(pr, 1, false)
        assertEquals(f.oscuro, oscuro); assertEquals(f.tones.paper, t.paper); assertEquals(f.tones.general, t.general)
    }

    @Test fun elProveedorDevuelveUnaFilaConLosNueveColores() {
        p.edit().putString("skin_accent_id", "verde").commit()
        Store(ctx).themeMode = 1   // (por defecto Atrio es oscuro)
        Robolectric.setupContentProvider(TemaProvider::class.java, "com.gonzalo.gamerate.tema")
        val q = ctx.contentResolver.query(android.net.Uri.parse("content://com.gonzalo.gamerate.tema/colores"), null, null, null, null)!!
        q.use {
            assertTrue(it.moveToFirst()); assertEquals(1, it.count)
            for (c in listOf("paper", "crudo", "ink", "acento", "sand", "line", "edge", "muted", "oro", "oscuro", "version")) assertTrue("falta $c", it.getColumnIndex(c) >= 0)
            assertEquals(ACCENT_COLORS.first { a -> a.id == "verde" }.light.toInt(), it.getInt(it.getColumnIndex("acento")))
        }
    }
}
