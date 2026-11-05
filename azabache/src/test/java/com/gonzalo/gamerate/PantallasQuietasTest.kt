package com.gonzalo.gamerate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Cada página, una vez pintada, se queda quieta: sin tocar nada, no se vuelve a componer ni un fotograma. Si algo apunta su medida en el
 * estado mientras se mide (como el titular que encoge en una fila de dos casillas, en la 1.5.2), la página se redibuja sin parar: en el
 * móvil gasta batería y en las pruebas nunca llega a estar quieta. Se mira en el móvil y en la tableta (en horizontal, con las dos columnas).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
open class PantallasQuietasTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun portada(n: String): String {
        val f = File(ctx.cacheDir, "q_$n.png")
        if (!f.exists()) {
            val b = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
            Canvas(b).drawColor(0xFF000000.toInt() or (n.hashCode() and 0xFFFFFF))
            f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return f.absolutePath
    }

    /** Cuántas veces se ha compuesto algo, entre todas las composiciones en marcha. */
    private fun composiciones(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

    private fun quieta(que: String) {
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        repeat(10) { rule.mainClock.advanceTimeByFrame() }
        val antes = composiciones()
        repeat(90) { rule.mainClock.advanceTimeByFrame() }
        val despues = composiciones()
        rule.mainClock.autoAdvance = true
        assertEquals("«$que» se sigue componiendo sin que pase nada (${despues - antes} veces en 90 fotogramas)", antes, despues)
    }

    @Test fun lasPaginasSeQuedanQuietas() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val s = Store(ctx)
        val d = LocalDate.now().toEpochDay()
        s.save(listOf(
            Game(102, "Hades II", portada("hades"), 91, 90, 88, 97, 93, creator = "Supergiant", end = d - 60, review = "El mejor roguelike que he jugado."),
            Game(103, "Hollow Knight: Silksong", portada("silk"), 88, creator = "Team Cherry", end = d - 3),
        ), 0)
        s.savePlays(listOf(
            Play(201, "Deadlock", portada("dead"), "", 1, emptyList(), creator = "Valve", minutes = 3120, ttbMain = 4000, updated = System.currentTimeMillis()),
            Play(203, "Death Stranding 2", portada("ds2"), "", 0, emptyList(), creator = "Kojima Productions"),
        ), 0)
        s.savePlays(listOf(Play(301, "El nombre del viento", portada("viento"), "", 1, emptyList(), pages = 880, creator = "Patrick Rothfuss", updated = System.currentTimeMillis() - 7200_000)), 1)
        // Lo jugado de cada día (lo que deja la cuenta): con ello salen las «Sesiones» de las fichas y el «Hoy» de la portada
        val dias = org.json.JSONObject().also { o -> listOf(0L, 1L, 3L, 9L, 20L).forEach { o.put(LocalDate.ofEpochDay(d - it).toString(), 3_600_000L) } }
        Hermanas.guardaPublico(ctx, "tiempo_hiedra", org.json.JSONObject().put("j_dead", org.json.JSONObject().put("movil", org.json.JSONObject().put("t", "Deadlock").put("days", dias))).toString())
        Hermanas.guardaEnlacesPublico(ctx, "hiedra", mapOf("j_dead" to 201L))
        Skin.load(ctx); Mode.dark = false; Mode.themeMode = 1; Skin.applyShell(2, ctx)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { App(s, 0L, false, "") {} }
            }
        }
        fun ir(t: String) { val n = rule.onAllNodesWithText(t, ignoreCase = true)[0]; runCatching { n.performScrollTo() }; n.performClick(); rule.waitForIdle() }
        fun atras() { androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle() }
        quieta("Portada")
        ir("Videojuegos"); quieta("Videojuegos")
        ir("Pasados"); quieta("Videojuegos · Pasados")
        ir("Hades II"); quieta("Ficha de Hades II")
        ir("EDITAR"); quieta("Hades II, editando la página")
        ir("LISTO"); atras()
        ir("Jugando"); ir("Deadlock"); quieta("Ficha de Deadlock (jugando)")
        atras(); atras()
        ir("El nombre del viento"); quieta("Ficha del libro")
        atras()
        ir("Diario"); quieta("Diario")
        atras()
        ir("Cifras"); quieta("Cifras")
    }
}

/** Lo mismo en la OPPO Pad Mini en horizontal (la página a dos columnas). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class PantallasQuietasTabletaTest : PantallasQuietasTest()
