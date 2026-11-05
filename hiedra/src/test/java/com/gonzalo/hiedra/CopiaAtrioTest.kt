package com.gonzalo.hiedra

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Copiar la apariencia que Atrio ha subido a la cuenta, contra el Firebase de mentira de Atrio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopiaAtrioTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @org.junit.After fun limpia() {   // (el tema es un objeto global: que no se lo lleven los demás tests)
        Skin.fondoId = ""; Skin.accentId = "paleta"; Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L; Skin.escala = 100
        Skin.display = "Atrio"; Skin.body = "Atrio"; Modo.eleccion = 0; Skin.origenAtrio = true; Skin.invertido = true; Skin.ajustesAt = 0L
    }

    @Test fun leeYCopiaLoQueAzabacheTiene() {
        Skin.cargar(ctx)   // (la primera carga marca las preferencias como ya estrenadas)
        Cuenta.cargar(ctx)
        val nuevo = JSONObject().put("theme31", 2).put("skin_fondo", "granate").put("skin_accent_id", "verde").put("skin_fondo_propio", 0xFF101820L)
            .put("skin_tinta_propia", 0).put("skin_acento_propio", 0xFF06D6A0L).put("skin_letra_titulos", "Playfair Display").put("skin_letra_texto", "").put("skin_escala", 112)
        // Lo que Azabache tiene en la cuenta (ajustes/azabache)
        com.gonzalo.familia.LaFamilia.coleccion("ajustes").pon("azabache", mapOf("at" to 20L, "skin" to com.gonzalo.familia.nucleo.Json.lee(nuevo.toString())))

        val a = CopiaAtrio.leer()!!
        assertEquals(20L, a.at)
        val filas = CopiaAtrio.filas(a).associateBy { it.id }
        assertEquals("Oscuro", filas["tema"]!!.valor); assertEquals("Granate", filas["fondo"]!!.valor); assertEquals("Verde", filas["acento"]!!.valor)
        assertTrue(filas["c_fondo"]!!.valor, filas["c_fondo"]!!.valor.contains("#101820")); assertTrue(filas["c_acento"]!!.valor, filas["c_acento"]!!.valor.contains("#06D6A0"))
        assertTrue(filas["letras"]!!.valor.contains("Playfair Display")); assertEquals("Grande", filas["tamano"]!!.valor)

        // Copiar solo lo marcado: el tema y las letras; lo demás de Pórtico queda como estaba
        Skin.fondoId = ""; Skin.accentId = "paleta"; Skin.fondoPropio = 0L; Skin.escala = 100; Skin.origenAtrio = true; Modo.eleccion = 0
        CopiaAtrio.aplicar(ctx, a, setOf("tema", "letras"))
        assertEquals(2, Modo.eleccion); assertEquals("Playfair Display", Skin.display); assertEquals("Atrio", Skin.body)
        assertEquals("", Skin.fondoId); assertEquals(100, Skin.escala); assertEquals(0L, Skin.fondoPropio)
        // y ahora todo
        CopiaAtrio.aplicar(ctx, a, filas.keys)
        assertEquals("granate", Skin.fondoId); assertEquals("verde", Skin.accentId); assertEquals(0xFF101820L, Skin.fondoPropio); assertEquals(0xFF06D6A0L, Skin.acentoPropio); assertEquals(112, Skin.escala)
        assertFalse("ya no sigue a Atrio en directo: tiene sus propios colores, iguales", Skin.origenAtrio)
        // Se guarda de verdad
        Skin.fondoId = ""; Skin.cargar(ctx); assertEquals("granate", Skin.fondoId)
    }
}
