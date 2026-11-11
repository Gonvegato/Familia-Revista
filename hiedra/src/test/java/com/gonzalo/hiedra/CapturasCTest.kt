package com.gonzalo.hiedra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas de Bastet 0.8.0 (dirección C, como Seshat), con los colores de Bastet («A tu manera»: fondo #1C241B, texto #F1E1C3, acento
 * #6A6D2C), de noche (la paleta oscura con el mismo acento) y con la letra grande (1,3). Solo al grabar:
 *   gradle :hiedra:testDebugUnitTest --tests '*CapturasCTest*' -Proborazzi.test.record=true
 * Las imágenes quedan en hiedra/build/outputs/roborazzi/ (empiezan por «b»).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasCTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun grabando() = org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")

    /** Una portada de prueba (600×900) con un degradado y su nombre. */
    private fun tapa(nombre: String, a: Int, b: Int, clara: Boolean = false): String {
        val f = File(ctx.cacheDir, "t_${nombre.hashCode()}.png")
        val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRect(0f, 0f, 300f, 450f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 450f, a, b, Shader.TileMode.CLAMP) })
            drawCircle(150f, 170f, 70f, Paint().apply { color = if (clara) 0x33000000 else 0x55FFFFFF; isAntiAlias = true })
            drawText(nombre, 22f, 400f, Paint().apply { color = if (clara) 0xCC111111.toInt() else 0xCCFFFFFF.toInt(); textSize = 28f; isAntiAlias = true; isFakeBoldText = true })
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    /** Un fondo de prueba a lo ancho: un cielo, un sol y montañas (para el cartel y la apertura de la ficha con fondo). */
    private fun fondo(nombre: String, cielo: Int, abajo: Int): String {
        val f = File(ctx.cacheDir, "f_${nombre.hashCode()}.png")
        val w = 1200f; val h = 500f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h, cielo, abajo, Shader.TileMode.CLAMP) })
            drawCircle(w * 0.68f, h * 0.38f, 70f, Paint().apply { color = 0xFFF2C46B.toInt(); isAntiAlias = true })
            val p = android.graphics.Path().apply { moveTo(0f, h * 0.72f); lineTo(w * 0.12f, h * 0.5f); lineTo(w * 0.25f, h * 0.66f); lineTo(w * 0.4f, h * 0.42f); lineTo(w * 0.55f, h * 0.62f)
                lineTo(w * 0.72f, h * 0.45f); lineTo(w * 0.88f, h * 0.6f); lineTo(w, h * 0.5f); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(p, Paint().apply { color = 0xFF0A1408.toInt(); isAntiAlias = true })
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    private fun pon(id: String, f: (Juego) -> Juego) { val i = Biblioteca.juegos.indexOfFirst { it.id == id }; Biblioteca.juegos[i] = f(Biblioteca.juegos[i]) }

    /** Los juegos de ejemplo de [CapturasTest] y, además, portadas de verdad, el reto del año y un pendiente que suele durar poco. */
    private fun siembra() {
        CapturasTest().siembra()
        Inicio.cargar(ctx); Inicio.poner(ConfigInicio(), 1)
        RetoJuegos.poner(ctx, 12)
        Encuadres.almacen = "encuadres_capturas"
        pon("w1") { it.copy(portada = tapa("PokéRogue", 0xFF2E5E8C.toInt(), 0xFF0B1A2E.toInt())) }
        pon("r1") { it.copy(portada = tapa("Esmeralda", 0xFF1E7A4A.toInt(), 0xFF0A2E1A.toInt())) }
        pon("r5") { it.copy(portada = tapa("Platino", 0xFF6A6A78.toInt(), 0xFF15151C.toInt()), durHistoria = 40 * 60) }
        pon("r2") { it.copy(portada = tapa("Fusion", 0xFFB0402A.toInt(), 0xFF2A0A06.toInt()), durHistoria = 7 * 60) }
        pon("r3") { it.copy(portada = tapa("Advance Wars", 0xFFF2F0EA.toInt(), 0xFFDCD8CC.toInt(), clara = true)) }
        Biblioteca.juegos.first { it.titulo == "Tears of the Kingdom" }.id.let { id -> pon(id) { it.copy(portada = tapa("Zelda", 0xFF3A6A5A.toInt(), 0xFF0E1E18.toInt()), durHistoria = 60 * 60) } }
    }

    private fun conFondo() { pon("w1") { it.copy(fondo = fondo("pokerogue", 0xFF7AA0C8.toInt(), 0xFF1A2A3A.toInt())) }; pon("r1") { it.copy(fondo = fondo("esmeralda", 0xFF5AB07A.toInt(), 0xFF0A2010.toInt())) } }

    /** Los colores de Bastet («A tu manera»); de noche, la paleta oscura con el acento de Bastet. */
    private fun coloresBastet(oscuro: Boolean) {
        Skin.origenAtrio = false; Skin.invertido = false; Skin.fondoId = ""
        // Los de serie desde noviembre de 2026 (paleta «arena»), sin nada a mano
        Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L
        Skin.palette = PALETTES.first { it.id == "familia" }; Skin.accentId = "paleta"
    }

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500)
        // (el fundido de las imágenes de Coil va con el reloj de Android, que en Robolectric está parado: se adelanta)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2)); rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    private fun montar(oscuro: Boolean, contenido: @androidx.compose.runtime.Composable () -> Unit = { HiedraApp() }) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = if (oscuro) 2 else 1
        coloresBastet(oscuro)
        rule.setContent { HiedraTheme { Surface(Modifier.fillMaxSize(), color = Paper) { contenido() } } }
    }

    private fun pestana(t: String) { rule.onAllNodesWithText(t, useUnmergedTree = true)[0].performScrollTo().performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle() }
    private fun abrir(t: String) { rule.onAllNodesWithText(t, useUnmergedTree = true)[0].performScrollTo().performClick(); rule.waitForIdle() }
    private fun bajar(veces: Int = 1) = repeat(veces) { rule.onRoot().performTouchInput { swipeUp(startY = bottom - 60f, endY = top + 200f, durationMillis = 900) }; rule.waitForIdle() }

    @Test fun portada() { grabando(); siembra(); montar(false); foto("b01_portada"); bajar(); foto("b01b_portada_2"); bajar(); foto("b01c_portada_3"); bajar(2); foto("b01d_portada_abajo") }
    @Test fun portadaNoche() { grabando(); siembra(); montar(true); foto("b02_portada_noche"); bajar(); foto("b02b_portada_noche_2") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaLetraGrande() {
        grabando(); siembra(); montar(false); foto("b03_portada_letra_grande"); bajar(); foto("b03b_portada_letra_grande_2"); bajar(); foto("b03c_portada_letra_grande_3")
    }
    @Test fun portadaConFondo() { grabando(); siembra(); conFondo(); montar(false); foto("b04_portada_con_fondo") }
    @Test fun portadaConFondoNoche() { grabando(); siembra(); conFondo(); montar(true); foto("b04b_portada_con_fondo_noche") }

    /** La semana con un día tocado (sale encima lo que jugaste ese día). */
    private fun diaTocado(nombre: String, oscuro: Boolean) {
        siembra(); montar(oscuro)
        bajar()
        rule.onAllNodes(hasContentDescription("Hoy", substring = true))[0].performClick(); rule.waitForIdle()
        rule.onAllNodes(hasContentDescription("Hoy", substring = true))[0].performClick(); rule.waitForIdle()
        val dia = java.time.LocalDate.now().minusDays(3).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "ES"))
        rule.onAllNodes(hasContentDescription(dia, substring = true, ignoreCase = true))[0].performClick(); rule.waitForIdle()
        foto(nombre)
    }
    @Test fun portadaSemana() { grabando(); diaTocado("b05_portada_dia_tocado", false) }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaSemanaLetraGrande() { grabando(); diaTocado("b05b_portada_dia_tocado_letra_grande", false) }

    /** El plegado de la cabecera por pasos: el nombre encoge con su hueco (sin cortarse ni dejar banda vacía). */
    private fun pasos(prefijo: String) {
        listOf(25, 50, 75, 100).forEachIndexed { i, pct ->
            val d = if (i == 0) 85f else if (pct == 100) 400f else 62f
            rule.onRoot().performTouchInput { swipeUp(startY = centerY, endY = centerY - d, durationMillis = 900) }
            foto("${prefijo}_plegado_$pct")
        }
    }
    @Test fun plegado() { grabando(); siembra(); montar(false); pasos("b06") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun plegadoLetraGrande() { grabando(); siembra(); montar(false); pasos("b06b_letra_grande") }

    /** Con pocos juegos, «También juegas» y «Pendientes» lado a lado. */
    @Test fun portadaPocos() {
        grabando(); siembra()
        Biblioteca.juegos.removeAll { it.estado == Estado.PENDIENTE && it.id != "r2" }
        Biblioteca.juegos.removeAll { it.titulo == "Tears of the Kingdom" }
        montar(false)
        rule.onNodeWithText("También juegas").performScrollTo(); rule.waitForIdle(); foto("b07_portada_pocos")
    }

    @Test fun paginas() {
        grabando(); siembra(); montar(false)
        pestana("JUGANDO"); foto("b10_jugando")
        pestana("PENDIENTES"); foto("b11_pendientes")
        pestana("PASADOS"); foto("b12_pasados")
        pestana("ABANDONADOS"); foto("b13_abandonados")
        pestana("SAGAS"); foto("b14_sagas")
        pestana("SISTEMAS"); foto("b15_sistemas")
    }
    @Test fun paginasNoche() { grabando(); siembra(); montar(true); pestana("JUGANDO"); foto("b16_jugando_noche"); pestana("PASADOS"); foto("b16b_pasados_noche") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun paginasLetraGrande() {
        grabando(); siembra(); montar(false)
        pestana("JUGANDO"); foto("b17_jugando_letra_grande")
        pestana("PENDIENTES"); foto("b17b_pendientes_letra_grande")
        pestana("SAGAS"); abrir("The Legend of Zelda"); foto("b17c_saga_abierta_letra_grande")
    }

    @Test fun fichas() {
        grabando(); siembra(); montar(false)
        pestana("JUGANDO"); abrir("Pokémon Platino"); foto("b20_ficha_jugando")
        bajar(); foto("b20b_ficha_jugando_2"); bajar(); foto("b20c_ficha_jugando_3"); bajar(2); foto("b20d_ficha_jugando_abajo")
        rule.onNodeWithText("← VOLVER").performScrollTo().performClick(); rule.waitForIdle()
        pestana("PASADOS"); abrir("Pokémon Esmeralda"); foto("b21_ficha_pasado"); bajar(); foto("b21b_ficha_pasado_2")
        rule.onNodeWithText("← VOLVER").performScrollTo().performClick(); rule.waitForIdle()
        pestana("PENDIENTES"); abrir("Metroid Fusion"); foto("b22_ficha_pendiente")
        rule.onNodeWithText("← VOLVER").performScrollTo().performClick(); rule.waitForIdle()
        pestana("PASADOS"); abrir("Hades"); foto("b23_ficha_sin_lanzador")
    }
    @Test fun fichaConFondo() {
        grabando(); siembra(); conFondo(); montar(false)
        pestana("PASADOS"); abrir("Pokémon Esmeralda"); foto("b24_ficha_con_fondo")
    }
    @Test fun fichaNoche() { grabando(); siembra(); conFondo(); montar(true); pestana("JUGANDO"); abrir("PokéRogue"); foto("b25_ficha_con_fondo_noche"); bajar(); foto("b25b_ficha_noche_2") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun fichaLetraGrande() {
        grabando(); siembra(); conFondo(); montar(false)
        pestana("PASADOS"); abrir("Pokémon Esmeralda"); foto("b26_ficha_con_fondo_letra_grande"); bajar(); foto("b26b_ficha_letra_grande_2")
        rule.onNodeWithText("← VOLVER").performScrollTo().performClick(); rule.waitForIdle()
        pestana("ABANDONADOS"); abrir("The Minish Cap"); foto("b26c_ficha_abandonado_letra_grande")
    }

    /** La hoja «IMÁGENES.»: la portada y el fondo, con propuestas de SteamGridDB de ejemplo. */
    private fun imagenes(sufijo: String, oscuro: Boolean) {
        siembra(); conFondo()
        val props = listOf(tapa("A", 0xFF884422.toInt(), 0xFF221100.toInt()), tapa("B", 0xFF224488.toInt(), 0xFF001122.toInt()), tapa("C", 0xFF448844.toInt(), 0xFF112211.toInt()), tapa("D", 0xFF888844.toInt(), 0xFF222211.toInt()))
        val fondos = listOf(fondo("p1", 0xFFE0703A.toInt(), 0xFF2A0A0E.toInt()), fondo("p2", 0xFF70A0E0.toInt(), 0xFF0A1A2A.toInt()))
        val esFondo = androidx.compose.runtime.mutableStateOf(false)
        montar(oscuro) { androidx.compose.runtime.key(esFondo.value) { ContenidoImagenes("r1", esFondo.value, {}, propuestas = (if (esFondo.value) fondos else props).map { it to it }) } }
        foto("b30_imagenes_portada$sufijo")
        esFondo.value = true; rule.waitForIdle(); foto("b31_imagenes_fondo$sufijo")
    }
    @Test fun imagenes() { grabando(); imagenes("", false) }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun imagenesLetraGrande() { grabando(); imagenes("_letra_grande", false) }

    /** El editor de encuadre (portada y fondo) y el cartel con lo encuadrado. */
    private fun encuadre(sufijo: String) {
        siembra(); conFondo()
        pon("r1") { it.copy(encPortada = Encuadre(1.8f, 0.3f, 0.7f), encFondo = Encuadre(1.6f, 0.68f, 0.45f)) }
        val j = Biblioteca.por("r1")!!
        val fondo = androidx.compose.runtime.mutableStateOf(false)
        montar(false) { androidx.compose.runtime.key(fondo.value) { if (fondo.value) ContenidoEncuadre(j.fondo, j.encFondo, true, {}, {}) else ContenidoEncuadre(j.portada, j.encPortada, false, {}, {}) } }
        foto("b32_encuadre_portada$sufijo")
        fondo.value = true; rule.waitForIdle(); foto("b33_encuadre_fondo$sufijo")
    }
    @Test fun encuadre() { grabando(); encuadre("") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun encuadreLetraGrande() { grabando(); encuadre("_letra_grande") }

    @Test fun ajustes() {
        grabando(); siembra(); montar(false)
        rule.onNodeWithContentDescription("Ajustes").performClick(); rule.waitForIdle(); foto("b40_ajustes")
        rule.onNodeWithText("JUEGOS").performClick(); rule.waitForIdle(); foto("b41_ajustes_juegos_reto")
    }
    @Test fun vacia() { grabando(); CapturasTest().siembra(); Biblioteca.juegos.clear(); montar(false); foto("b42_vacia") }
    @Test fun diaClaro() {
        // La paleta clara de serie con el acento de Bastet (por si alguien la usa de día): el oliva se lee sobre el papel
        grabando(); siembra(); Modo.eleccion = 1
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Skin.origenAtrio = false; Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0xFF6A6D2C
        rule.setContent { HiedraTheme { Surface(Modifier.fillMaxSize(), color = Paper) { HiedraApp() } } }
        foto("b43_portada_paleta_clara"); pestana("JUGANDO"); foto("b43b_jugando_paleta_clara")
    }
}
