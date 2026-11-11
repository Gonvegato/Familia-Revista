package com.gonzalo.gamerate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Capturas de Jonsu 1.8 (la dirección C): la portada del día de cada tipo (juego, libro, serie, lo escrito y nada), los retos de todas, la
 * semana con un día tocado, la cabecera plegada, cada página, la ficha a doble página, Imágenes y el encuadre. De día, de noche y con la
 * letra a 1,3. Para verlas: gradle :azabache:testDebugUnitTest --tests '*Capturas18Test*' -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class Capturas18Test {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val d = LocalDate.now().toEpochDay()

    private fun cover(name: String, a: Int, b: Int, w: Int = 300, h: Int = 450): String {
        val f = File(ctx.cacheDir, "c18_${name.hashCode()}_$w.png")
        if (f.exists()) return f.absolutePath
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a, b, Shader.TileMode.CLAMP) })
        c.drawText(name.take(1), 30f, 160f, Paint().apply { color = 0x55FFFFFF; textSize = 120f; isAntiAlias = true; isFakeBoldText = true })
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    private fun dias(vararg l: Pair<Long, Int>) = org.json.JSONObject().also { o -> l.forEach { (n, min) -> o.put(LocalDate.ofEpochDay(d - n).toString(), min * 60_000L) } }

    private fun seed(): Store {
        val s = Store(ctx)
        s.myName = "Gonzalo"
        s.save(listOf(
            Game(101, "Clair Obscur: Expedition 33", cover("Clair", 0xFF8E2DE2.toInt(), 0xFF4A00E0.toInt()), 100, 100, 100, 100, 100, creator = "Sandfall Interactive", end = d - 20, fav = true),
            Game(102, "Hades II", cover("Hades", 0xFFE96443.toInt(), 0xFF904E95.toInt()), 91, creator = "Supergiant", end = d - 60,
                hero = cover("HadesFondo", 0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 900, 500)),
            Game(103, "Hollow Knight: Silksong", cover("Silk", 0xFF134E5E.toInt(), 0xFF71B280.toInt()), 88, creator = "Team Cherry", end = d - 3),
        ), 0)
        s.savePlays(listOf(
            Play(201, "Deadlock", cover("Dead", 0xFFF7971E.toInt(), 0xFF7F3800.toInt()), cover("DeadFondo", 0xFF0F2027.toInt(), 0xFFF7971E.toInt(), 900, 500), 1, emptyList(),
                creator = "Valve", minutes = 3120, updated = System.currentTimeMillis()),
            Play(202, "Elden Ring Nightreign", cover("Elden", 0xFFC9D6FF.toInt(), 0xFF6A5B3B.toInt()), "", 1, emptyList(), creator = "FromSoftware", minutes = 840, updated = System.currentTimeMillis() - 3600_000),
            Play(203, "Death Stranding 2", cover("DS2", 0xFF00B4DB.toInt(), 0xFF0083B0.toInt()), "", 0, emptyList(), creator = "Kojima Productions"),
        ), 0)
        s.savePlays(listOf(
            Play(301, "El nombre del viento", cover("Viento", 0xFF603813.toInt(), 0xFFB29F94.toInt()), "", 1, emptyList(), pages = 880, creator = "Patrick Rothfuss", updated = System.currentTimeMillis() - 7200_000),
            Play(302, "Proyecto Hail Mary", cover("Hail", 0xFF0F2027.toInt(), 0xFF2C5364.toInt()), "", 0, emptyList(), creator = "Andy Weir"),
        ), 1)
        s.save(listOf(Game(401, "Dune", cover("Dune", 0xFFF2994A.toInt(), 0xFFF2C94C.toInt()), 89, creator = "Frank Herbert", end = d - 30),
            Game(402, "El extranjero", cover("Extranjero", 0xFFFBFAF6.toInt(), 0xFFEDE8DC.toInt()), 80, creator = "Albert Camus", end = d - 50)), 1)
        Hermanas.guardaEnlacesPublico(ctx, "hiedra", mapOf("j_dead" to 201L, "j_hades" to 102L))
        Hermanas.guardaEnlacesPublico(ctx, "marfil", mapOf(CLAVE to 301L))
        MetasCuenta.fijas = MetasCuenta(30, 20, 40, 500)
        return s
    }

    private val CLAVE = "libro:el nombre del viento:patrick rothfuss"

    /** Lo de cada día de las hermanas: [juego], [libro] y [serie] son los minutos de hoy (los demás días, fijos), [palabras] las de hoy. */
    private fun dia(juego: Int, libro: Int, serie: Int, palabras: Int, semana: Boolean = true) {
        fun conHoy(hoy: Int, vararg resto: Pair<Long, Int>) = if (semana) dias(*(listOf(0L to hoy) + resto).toTypedArray()) else dias(0L to hoy)
        Hermanas.guardaPublico(ctx, "tiempo_hiedra", org.json.JSONObject()
            .put("j_dead", org.json.JSONObject().put("movil", org.json.JSONObject().put("t", "Deadlock").put("days", conHoy(juego, 1L to 40, 3L to 120, 4L to 30)))).toString())
        Hermanas.guardaPublico(ctx, "tiempo_marfil", org.json.JSONObject().put(Hermanas.kidDe(CLAVE), org.json.JSONObject().put("cuenta", org.json.JSONObject()
            .put("dev", org.json.JSONObject().put("movil", org.json.JSONObject().put("days", conHoy(libro, 1L to 50, 2L to 20, 4L to 65, 5L to 40))))) ).toString())
        Hermanas.guardaPublico(ctx, "tiempo_mostaza", org.json.JSONObject().put("tv_sev", org.json.JSONObject().put("t", "Severance").put("tipo", "tv").put("days", conHoy(serie, 2L to 55, 5L to 90))).toString())
        Hermanas.guardaPublico(ctx, "palabras_cobalto", org.json.JSONObject().put("movil", org.json.JSONObject().also { o ->
            if (palabras > 0) o.put(LocalDate.ofEpochDay(d).toString(), palabras); if (semana) { o.put(LocalDate.ofEpochDay(d - 1).toString(), 640); o.put(LocalDate.ofEpochDay(d - 3).toString(), 1210) } }).toString())
        Hermanas.guardaPublico(ctx, "lectura_cobalto", org.json.JSONObject().put("obra1", org.json.JSONObject().put("c1", org.json.JSONObject().put("t", "La casa del faro").put("c", "El muelle").put("n", 1).put("u", 5L))).toString())
        rule.runOnUiThread { Hermanas.repinta() }
        rule.waitForIdle()
    }

    private fun shoot(name: String) {
        rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/j18_$name.png")
    }

    private fun abrir(store: Store) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Skin.load(ctx)
        Mode.themeMode = 1
        Skin.applyShell(2, ctx)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    App(store, 0L, false, "") {}
                    // (Apariencia se pinta encima, como en MainActivity)
                    if (Skin.open) Surface(Modifier.fillMaxSize(), color = Paper) { StyleScreen(1, {}, {}) { Skin.open = false } }
                }
            }
        }
        rule.waitForIdle()
    }

    /** Sube la página algo menos de media pantalla (swipeUp se va al final de golpe). */
    private fun subir() {
        rule.onNodeWithTag("paginas").performTouchInput { swipe(androidx.compose.ui.geometry.Offset(centerX, height * 0.8f), androidx.compose.ui.geometry.Offset(centerX, height * 0.35f), 600) }
        rule.waitForIdle()
    }

    private fun texto(t: String) = rule.onAllNodesWithText(t, ignoreCase = true, substring = false)

    private fun recorrido(pre: String) {
        val store = seed()
        dia(95, 35, 0, 812)
        abrir(store)
        shoot("${pre}01_portada_juego")
        assertTrue("el cartel es Deadlock (lo de más tiempo hoy)", texto("DEADLOCK").fetchSemanticsNodes().isNotEmpty())
        assertTrue("el botón abre Bastet", texto("ABRIR EN BASTET").fetchSemanticsNodes().isNotEmpty())
        dia(0, 80, 20, 0); shoot("${pre}02_portada_libro")
        assertTrue(texto("ABRIR EN SESHAT").fetchSemanticsNodes().isNotEmpty())
        dia(10, 20, 120, 300); shoot("${pre}03_portada_serie")
        assertTrue(texto("ABRIR EN HATHOR").fetchSemanticsNodes().isNotEmpty())
        dia(0, 0, 0, 900); shoot("${pre}04_portada_escrito")
        dia(0, 0, 0, 0, semana = false); shoot("${pre}05_portada_nada")
        dia(95, 35, 0, 812)
        // Los retos y la semana; un día tocado
        subir()
        shoot("${pre}06_retos_plegada")
        subir()
        shoot("${pre}07_semana")
        val letraJ = LocalDate.ofEpochDay(d - 3).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale("es", "ES")).uppercase()
        texto(letraJ).fetchSemanticsNodes().let { if (it.isNotEmpty()) texto(letraJ)[texto(letraJ).fetchSemanticsNodes().size - 1].performClick() }
        shoot("${pre}08_semana_tocada")
        subir(); shoot("${pre}09_portada_abajo")
        subir(); subir(); shoot("${pre}10_portada_final")
        repeat(6) { rule.onNodeWithTag("paginas").performTouchInput { swipeDown() } }
        // Cada página
        listOf("DIARIO", "VIDEOJUEGOS", "LIBROS", "ESCRITOS").forEachIndexed { i, n ->
            val l = texto(n).fetchSemanticsNodes()
            if (l.isNotEmpty()) { texto(n)[0].performClick(); shoot("${pre}1${i + 1}_pagina_${n.lowercase()}") }
        }
        texto("PORTADA")[0].performClick(); rule.waitForIdle()
        rule.onNodeWithTag("paginas").performTouchInput { swipeLeft() }; shoot("${pre}15_deslizada")
        // Las fichas, a doble página
        texto("VIDEOJUEGOS")[0].performClick(); rule.waitForIdle()
        texto("Deadlock").let { it[it.fetchSemanticsNodes().size - 1].performClick() }; shoot("${pre}20_ficha_jugando")
        assertTrue("la ficha abre con la apertura", rule.onAllNodes(androidx.compose.ui.test.hasTestTag("apertura")).fetchSemanticsNodes().isNotEmpty())
        rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(centerX, height * 0.8f), androidx.compose.ui.geometry.Offset(centerX, height * 0.3f), 600) }
        shoot("${pre}21_ficha_secciones")
        rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(centerX, height * 0.3f), androidx.compose.ui.geometry.Offset(centerX, height * 0.9f), 400) }
        rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(centerX, height * 0.3f), androidx.compose.ui.geometry.Offset(centerX, height * 0.9f), 400) }
        texto("ENCUADRAR")[0].performClick(); shoot("${pre}22_encuadre")
        texto("CANCELAR")[0].performClick(); rule.waitForIdle()
        texto("IMÁGENES")[0].performClick(); shoot("${pre}23_imagenes")
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        if (rule.onAllNodes(androidx.compose.ui.test.hasTestTag("apertura")).fetchSemanticsNodes().isNotEmpty()) { androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle() }
        texto("VIDEOJUEGOS")[0].performClick(); rule.waitForIdle()
        texto("PASADOS").let { it[it.fetchSemanticsNodes().size - 1].performClick() }; rule.waitForIdle()
        texto("Hades II").let { it[it.fetchSemanticsNodes().size - 1].performClick() }; shoot("${pre}24_ficha_valorado")
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        texto("LIBROS")[0].performClick(); rule.waitForIdle()
        texto("LEÍDOS").let { it[it.fetchSemanticsNodes().size - 1].performClick() }; rule.waitForIdle()
        texto("El extranjero").let { it[it.fetchSemanticsNodes().size - 1].performClick() }; shoot("${pre}25_ficha_portada_blanca")
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        // Apariencia
        rule.runOnUiThread { Skin.open = true }; shoot("${pre}26_apariencia")
        rule.runOnUiThread { Skin.open = false }; rule.waitForIdle()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() = this.onNode(androidx.compose.ui.test.isRoot())

    /** Ajustes tal como están (Apariencia, Cuenta, Avanzado), de arriba abajo. */
    @Test fun ajustes() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Mode.dark = false
        val store = seed(); dia(95, 35, 0, 812); abrir(store)
        fun bajando(n: String) { shoot("aj_${n}_0"); repeat(5) { i -> rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(centerX, height * 0.85f), androidx.compose.ui.geometry.Offset(centerX, height * 0.2f), 500) }; shoot("aj_${n}_${i + 1}") } }
        rule.runOnUiThread { Skin.open = true }; rule.waitForIdle(); bajando("0apariencia")
        listOf("JONSU" to "1jonsu", "CUENTA" to "2cuenta", "CLAVES" to "3claves", "AVANZADO" to "4avanzado").forEach { (t, n) ->
            repeat(6) { rule.onRoot().performTouchInput { swipeDown() } }
            texto(t)[0].performClick(); rule.waitForIdle(); repeat(6) { rule.onRoot().performTouchInput { swipeDown() } }; bajando(n)
        }
    }

    @Test fun dia() { org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true"); Mode.dark = false; recorrido("d_") }

    @Test fun noche() { org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true"); Mode.dark = true; recorrido("n_") }

    @Test fun letraGrande() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        RuntimeEnvironment.setFontScale(1.3f)
        Mode.dark = false
        recorrido("g_")
    }
}
