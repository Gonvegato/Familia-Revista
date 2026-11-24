package com.gonzalo.cobalto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.gonzalo.familia.FichasDeMapas
import com.gonzalo.familia.LaminaAtlas
import com.gonzalo.familia.LugarMapa
import com.gonzalo.familia.MapaAtlas
import com.gonzalo.familia.MapasAtlas
import com.gonzalo.familia.PantallaVisor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas del visor de mapas de ATLAS (módulo familia) con la Apariencia de Nova: la sección MAPAS de la ficha, el visor centrado en
 * un lugar, el buscador y la tarjeta del lugar en el manuscrito. Solo al grabar:
 *   gradle :cobalto:testDebugUnitTest --tests '*CapturasMapasTest*' -Proborazzi.test.record=true
 * Las imágenes quedan en cobalto/build/outputs/roborazzi/ (empiezan por «mapas_»).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasMapasTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun grabando() = org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(400)
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    private val lugares = listOf(
        LugarMapa("Aldreth", "capital", 0.52f, 0.56f), LugarMapa("Aldea del Vado", "aldea", 0.36f, 0.66f),
        LugarMapa("Varenne", "ciudad", 0.70f, 0.40f), LugarMapa("Torre de Ur", "ruinas", 0.30f, 0.32f),
    )
    private val ithria = MapaAtlas("m_ithria", "Ithria", "continente", listOf("o_cristal"), 8192, 6144, "", LaminaAtlas("GD1", "m_ithria.lamina.webp", 1, 1536, 1152),
        40L, lugares, 50L)
    private val varenne = MapaAtlas("m_varenne", "Varenne", "ciudad", listOf("o_cristal"), 4096, 4096, "", null, 0L, emptyList(), 60L)

    /** Una lámina de prueba (un continente, un río, montañas, una calzada y los rótulos), guardada como si ya se hubiera bajado. */
    private fun siembraLamina() {
        val w = 1536; val h = 1152
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            drawColor(0xFFB9CBD6.toInt())
            val tierra = Path().apply {
                moveTo(w * 0.08f, h * 0.45f); cubicTo(w * 0.15f, h * 0.12f, w * 0.45f, h * 0.10f, w * 0.60f, h * 0.20f)
                cubicTo(w * 0.80f, h * 0.10f, w * 0.95f, h * 0.35f, w * 0.90f, h * 0.60f); cubicTo(w * 0.85f, h * 0.90f, w * 0.50f, h * 0.95f, w * 0.30f, h * 0.88f)
                cubicTo(w * 0.10f, h * 0.82f, w * 0.03f, h * 0.65f, w * 0.08f, h * 0.45f); close()
            }
            drawPath(tierra, Paint().apply { color = 0xFFE7D9AE.toInt(); isAntiAlias = true })
            drawPath(tierra, Paint().apply { color = 0xFF3B3324.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true })
            val tinta = Paint().apply { color = 0xFF3B3324.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
            for (i in 0 until 7) { val x = w * (0.22f + i * 0.05f); val y = h * (0.30f + (i % 2) * 0.03f); drawPath(Path().apply { moveTo(x, y); lineTo(x + 30f, y - 55f); lineTo(x + 60f, y) }, tinta) }
            drawPath(Path().apply { moveTo(w * 0.42f, h * 0.30f); cubicTo(w * 0.48f, h * 0.45f, w * 0.40f, h * 0.60f, w * 0.55f, h * 0.90f) },
                Paint().apply { color = 0xFF4A6E8A.toInt(); style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true })
            drawPath(Path().apply { moveTo(w * 0.36f, h * 0.66f); cubicTo(w * 0.45f, h * 0.60f, w * 0.60f, h * 0.50f, w * 0.70f, h * 0.40f) },
                Paint().apply { color = 0xFF8E2C1F.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 10f), 0f); isAntiAlias = true })
            val rotulo = Paint().apply { color = 0xFF2A2418.toInt(); textSize = 40f; isAntiAlias = true; typeface = android.graphics.Typeface.SERIF }
            lugares.forEach { l ->
                drawCircle(l.x * w, l.y * h, 9f, Paint().apply { color = 0xFF8E2C1F.toInt(); isAntiAlias = true })
                drawText(l.nombre, l.x * w + 16f, l.y * h + 14f, rotulo)
            }
            drawText("REINO DE HALVEN", w * 0.14f, h * 0.50f, Paint().apply { color = 0xFF3B3324.toInt(); textSize = 34f; letterSpacing = 0.3f; isAntiAlias = true; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC) })
        }
        val dir = File(ctx.filesDir, "atlas").apply { mkdirs() }
        File(dir, "m_ithria.webp").outputStream().use { b.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it) }
        File(dir, "m_ithria.version").writeText(ithria.versionLamina)
    }

    @Test fun visorCentradoEnUnLugar() {
        grabando(); Skin.cargar(ctx); siembraLamina()
        rule.setContent { PantallaVisor("La ciudad de cristal", listOf(ithria, varenne), "m_ithria", "Aldreth", estiloVisor()) {} }
        foto("mapas_1_visor_aldreth")
    }

    @Test fun visorBuscando() {
        grabando(); Skin.cargar(ctx); siembraLamina()
        rule.setContent { PantallaVisor("La ciudad de cristal", listOf(ithria, varenne), "m_ithria", null, estiloVisor()) {} }
        rule.waitForIdle()
        rule.onNodeWithText("BUSCAR").performClick()
        foto("mapas_2_visor_buscar")
    }

    @Test fun visorSinLamina() {
        grabando(); Skin.cargar(ctx); siembraLamina()
        rule.setContent { PantallaVisor("La ciudad de cristal", listOf(ithria, varenne), "m_varenne", null, estiloVisor()) {} }
        foto("mapas_3_visor_sin_lamina")
    }

    @Test fun fichasYTarjetaDelLugar() {
        grabando(); Skin.cargar(ctx); siembraLamina()
        val mencion = MapasAtlas.menciones("Tres jornadas hasta Aldreth", listOf(ithria)).first()
        rule.setContent {
            Column(Modifier.fillMaxSize().background(Paper).padding(top = 40.dp)) {
                Column(Modifier.padding(horizontal = 20.dp)) { FichasDeMapas(listOf(ithria, varenne), estiloVisor()) {} }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 40.dp))
                PreviaLugar(mencion, onVer = {}, onCerrar = {})
            }
        }
        foto("mapas_4_fichas_y_tarjeta")
    }
}
