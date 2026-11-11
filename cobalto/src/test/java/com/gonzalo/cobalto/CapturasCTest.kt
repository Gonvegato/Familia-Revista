package com.gonzalo.cobalto

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
 * Capturas de Thot 0.6.0 (dirección C, como Seshat, Bastet, Hathor y Nut), con los colores de Thot («A tu manera»: fondo #171E2B, texto
 * #F1EBE0, acento cobre #C8703F), de noche (la paleta oscura con el cobre) y con la letra grande (1,3). Solo al grabar:
 *   gradle :cobalto:testDebugUnitTest --tests '*CapturasCTest*' -Proborazzi.test.record=true
 * Las imágenes quedan en cobalto/build/outputs/roborazzi/ (empiezan por «t»).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasCTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun grabando() = org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
    private val e: Escritos get() = Escritos.get(ctx)

    /** Guarda una imagen como las de la cuenta (familia/img/<hash>) y devuelve su hash. */
    private fun guarda(nombre: String, bmp: Bitmap): String {
        val h = "prueba${nombre.hashCode().toUInt()}"
        val f = File(File(ctx.filesDir, "familia"), "img/$h").apply { parentFile?.mkdirs() }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return h
    }

    /** Una portada de prueba (2:3) con un degradado, un círculo y su nombre. */
    private fun tapa(nombre: String, a: Int, b: Int, clara: Boolean = false): String {
        val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRect(0f, 0f, 300f, 450f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 450f, a, b, Shader.TileMode.CLAMP) })
            drawCircle(150f, 170f, 70f, Paint().apply { color = if (clara) 0x33000000 else 0x55FFFFFF; isAntiAlias = true })
            drawText(nombre, 22f, 400f, Paint().apply { color = if (clara) 0xCC111111.toInt() else 0xCCFFFFFF.toInt(); textSize = 28f; isAntiAlias = true; isFakeBoldText = true })
        }
        return guarda("t$nombre", bmp)
    }

    /** Un fondo de prueba a lo ancho: un cielo, una luna y tejados. */
    private fun fondo(nombre: String, cielo: Int, abajo: Int): String {
        val w = 1200f; val h = 500f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h, cielo, abajo, Shader.TileMode.CLAMP) })
            drawCircle(w * 0.7f, h * 0.32f, 60f, Paint().apply { color = 0xFFF2E6C8.toInt(); isAntiAlias = true })
            val p = android.graphics.Path().apply { moveTo(0f, h * 0.75f); lineTo(w * 0.1f, h * 0.6f); lineTo(w * 0.2f, h * 0.7f); lineTo(w * 0.32f, h * 0.5f); lineTo(w * 0.45f, h * 0.68f)
                lineTo(w * 0.6f, h * 0.55f); lineTo(w * 0.75f, h * 0.7f); lineTo(w * 0.9f, h * 0.52f); lineTo(w, h * 0.62f); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(p, Paint().apply { color = 0xFF0A0D14.toInt(); isAntiAlias = true })
        }
        return guarda("f$nombre", bmp)
    }

    private fun id(titulo: String) = e.obras().first { it.s("titulo") == titulo }.id

    /** Las obras de ejemplo (las de [Ejemplo]) y, además, portadas de verdad en algunas. */
    private fun siembra(vacia: Boolean = false) {
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        ctx.getSharedPreferences("cobalto_ajustes", Context.MODE_PRIVATE).edit().clear().commit()
        Escritos.reiniciaParaPruebas(); File(ctx.filesDir, "escritos").deleteRecursively()
        Encuadres.almacen = "encuadres_capturas"
        if (vacia) return
        Ejemplo.siembra(e); Ejemplo.estudio(e)
        e.edita(id("La ciudad de cristal")) { put(ImagenesObra.PORTADA, tapa("Cristal", 0xFF3E7C9E.toInt(), 0xFF0B1A2E.toInt())) }
        e.edita(id("Cartas a nadie")) { put(ImagenesObra.PORTADA, tapa("Cartas", 0xFFF2F0EA.toInt(), 0xFFDCD8CC.toInt(), clara = true)) }
        e.edita(id("El último verano")) { put(ImagenesObra.PORTADA, tapa("Verano", 0xFFE0A040.toInt(), 0xFF5A2A0A.toInt())) }
        e.vacia()
    }

    private fun conFondo() {
        e.edita(id("La ciudad de cristal")) { put(ImagenesObra.FONDO, fondo("ciudad", 0xFF2A3A5A.toInt(), 0xFF0E1420.toInt())) }
        e.edita(id("El último verano")) { put(ImagenesObra.FONDO, fondo("verano", 0xFFE89A50.toInt(), 0xFF3A1A0A.toInt())) }
    }

    /** Los colores de Thot («A tu manera»); de noche, la paleta oscura con el cobre. */
    private fun coloresThot(oscuro: Boolean) {
        Skin.origenAtrio = false; Skin.invertido = false; Skin.fondoId = ""
        // Los de serie desde noviembre de 2026 (paleta «noche»), sin nada a mano
        Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L
        Skin.palette = PALETTES.first { it.id == "familia" }; Skin.accentId = "paleta"
    }

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2)); rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    private fun montar(oscuro: Boolean, contenido: @androidx.compose.runtime.Composable () -> Unit = { CobaltoApp() }) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = if (oscuro) 2 else 1
        coloresThot(oscuro)
        rule.setContent { CobaltoTheme { Surface(Modifier.fillMaxSize(), color = Paper) { contenido() } } }
    }

    private fun pestana(t: String) { rule.onAllNodesWithText(t, useUnmergedTree = true)[0].performScrollTo().performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle() }
    private fun abrir(t: String) { rule.onAllNodesWithText(t, useUnmergedTree = true).let { n -> n[n.fetchSemanticsNodes().size - 1] }.performScrollTo().performClick(); rule.waitForIdle() }
    private fun bajar(veces: Int = 1) = repeat(veces) { rule.onRoot().performTouchInput { swipeUp(startY = bottom - 60f, endY = top + 200f, durationMillis = 900) }; rule.waitForIdle() }
    private fun volver() {
        rule.onAllNodesWithText("← VOLVER").let { n -> n[n.fetchSemanticsNodes().size - 1] }.performClick()
        // (que acabe de irse la ficha: mientras se va, sus textos siguen ahí)
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
    }

    @Test fun portada() { grabando(); siembra(); montar(false); foto("t01_portada"); bajar(); foto("t01b_portada_2"); bajar(); foto("t01c_portada_3"); bajar(2); foto("t01d_portada_abajo") }
    @Test fun portadaNoche() { grabando(); siembra(); montar(true); foto("t02_portada_noche"); bajar(); foto("t02b_portada_noche_2") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaLetraGrande() {
        grabando(); siembra(); montar(false); foto("t03_portada_letra_grande"); bajar(); foto("t03b_portada_letra_grande_2"); bajar(); foto("t03c_portada_letra_grande_3"); bajar(2); foto("t03d_portada_letra_grande_abajo")
    }
    @Test fun portadaConFondo() { grabando(); siembra(); conFondo(); montar(false); foto("t04_portada_con_fondo") }
    @Test fun portadaConFondoNoche() { grabando(); siembra(); conFondo(); montar(true); foto("t04b_portada_con_fondo_noche") }
    @Test fun portadaSinObras() { grabando(); siembra(vacia = true); montar(false); foto("t05_portada_sin_obras"); bajar(); foto("t05b_portada_sin_obras_2") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaSinObrasLetraGrande() { grabando(); siembra(vacia = true); montar(false); foto("t05c_portada_sin_obras_letra_grande") }

    /** La semana con un día tocado (sale encima lo que escribiste ese día). */
    private fun diaTocado(nombre: String, oscuro: Boolean) {
        siembra(); montar(oscuro)
        bajar()
        rule.onAllNodes(hasContentDescription("Hoy", substring = true))[0].performClick(); rule.waitForIdle()
        val dia = java.time.LocalDate.now().minusDays(3).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "ES"))
        rule.onAllNodes(hasContentDescription(dia, substring = true, ignoreCase = true))[0].performClick(); rule.waitForIdle()
        foto(nombre)
    }
    @Test fun portadaSemana() { grabando(); diaTocado("t06_portada_dia_tocado", false) }
    @Test fun portadaSemanaNoche() { grabando(); diaTocado("t06b_portada_dia_tocado_noche", true) }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaSemanaLetraGrande() { grabando(); diaTocado("t06c_portada_dia_tocado_letra_grande", false) }

    /** El plegado de la cabecera por pasos. */
    private fun pasos(prefijo: String) {
        listOf(25, 50, 75, 100).forEachIndexed { i, pct ->
            val d = if (i == 0) 85f else if (pct == 100) 400f else 62f
            rule.onRoot().performTouchInput { swipeUp(startY = centerY, endY = centerY - d, durationMillis = 900) }
            foto("${prefijo}_plegado_$pct")
        }
    }
    @Test fun plegado() { grabando(); siembra(); montar(false); pasos("t07") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun plegadoLetraGrande() { grabando(); siembra(); montar(false); pasos("t07b_letra_grande") }

    @Test fun paginas() {
        grabando(); siembra(); montar(false)
        pestana("ESCRIBIENDO"); foto("t10_escribiendo")
        pestana("EN PAUSA"); foto("t11_en_pausa")
        pestana("TERMINADAS"); foto("t12_terminadas")
        pestana("ARCHIVO"); foto("t13_archivo")
    }
    @Test fun paginasNoche() { grabando(); siembra(); montar(true); pestana("ESCRIBIENDO"); foto("t14_escribiendo_noche"); pestana("ARCHIVO"); foto("t14b_archivo_noche") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun paginasLetraGrande() {
        grabando(); siembra(); montar(false)
        pestana("ESCRIBIENDO"); foto("t15_escribiendo_letra_grande")
        pestana("TERMINADAS"); foto("t15b_terminadas_letra_grande")
    }

    @Test fun fichas() {
        grabando(); siembra(); montar(false)
        pestana("ESCRIBIENDO"); abrir("La ciudad de cristal"); foto("t20_ficha_escribiendo")
        bajar(); foto("t20b_ficha_escribiendo_2"); bajar(); foto("t20c_ficha_escribiendo_3"); bajar(2); foto("t20d_ficha_escribiendo_abajo")
    }
    @Test fun fichaEnPausa() { grabando(); siembra(); montar(false); pestana("EN PAUSA"); abrir("Cartas a nadie"); foto("t21_ficha_en_pausa") }
    @Test fun fichaTerminada() { grabando(); siembra(); montar(false); pestana("TERMINADAS"); abrir("El último verano"); foto("t22_ficha_terminada") }
    @Test fun fichaSinPortada() { grabando(); siembra(); montar(false); pestana("ESCRIBIENDO"); abrir("Cuentos del norte"); foto("t23_ficha_sin_portada") }
    @Test fun fichaConFondo() { grabando(); siembra(); conFondo(); montar(false); pestana("ESCRIBIENDO"); abrir("La ciudad de cristal"); foto("t24_ficha_con_fondo") }
    @Test fun fichaNoche() { grabando(); siembra(); conFondo(); montar(true); pestana("TERMINADAS"); abrir("El último verano"); foto("t25_ficha_con_fondo_noche"); bajar(); foto("t25b_ficha_noche_2") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun fichaLetraGrande() {
        grabando(); siembra(); conFondo(); montar(false)
        pestana("ESCRIBIENDO"); abrir("La ciudad de cristal"); foto("t26_ficha_con_fondo_letra_grande"); bajar(); foto("t26b_ficha_letra_grande_2"); bajar(); foto("t26c_ficha_letra_grande_3")
    }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun fichaEnPausaLetraGrande() {
        grabando(); siembra(); montar(false); pestana("EN PAUSA"); abrir("Cartas a nadie"); foto("t26d_ficha_en_pausa_letra_grande")
    }

    /** La hoja «IMÁGENES.»: la portada y el fondo. */
    private fun imagenes(sufijo: String, oscuro: Boolean) {
        siembra(); conFondo()
        val esFondo = androidx.compose.runtime.mutableStateOf(false)
        val obra = id("La ciudad de cristal")
        montar(oscuro) { androidx.compose.runtime.key(esFondo.value) { ContenidoImagenes(obra, esFondo.value, {}) } }
        foto("t30_imagenes_portada$sufijo")
        esFondo.value = true; rule.waitForIdle(); foto("t31_imagenes_fondo$sufijo")
    }
    @Test fun imagenes() { grabando(); imagenes("", false) }
    @Test fun imagenesNoche() { grabando(); imagenes("_noche", true) }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun imagenesLetraGrande() { grabando(); imagenes("_letra_grande", false) }

    /** El editor de encuadre (portada y fondo). */
    private fun encuadre(sufijo: String) {
        siembra(); conFondo()
        val o = e.get(id("La ciudad de cristal"))!!
        ImagenesObra.ponerEncuadre(ctx, o, false, Encuadre(1.8f, 0.3f, 0.6f))
        ImagenesObra.ponerEncuadre(ctx, o, true, Encuadre(1.6f, 0.68f, 0.4f))
        val fondo = androidx.compose.runtime.mutableStateOf(false)
        montar(false) { androidx.compose.runtime.key(fondo.value) {
            if (fondo.value) ContenidoEncuadre(ImagenesObra.fondo(ctx, o), ImagenesObra.encFondo(ctx, o), true, {}, {}) else ContenidoEncuadre(ImagenesObra.portada(ctx, o), ImagenesObra.encPortada(ctx, o), false, {}, {})
        } }
        foto("t32_encuadre_portada$sufijo")
        fondo.value = true; rule.waitForIdle(); foto("t33_encuadre_fondo$sufijo")
    }
    @Test fun encuadre() { grabando(); encuadre("") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun encuadreLetraGrande() { grabando(); encuadre("_letra_grande") }
    /** Lo encuadrado, en el cartel y en la ficha. */
    @Test fun encuadrado() {
        grabando(); siembra(); conFondo()
        val o = e.get(id("La ciudad de cristal"))!!
        ImagenesObra.ponerEncuadre(ctx, o, false, Encuadre(1.8f, 0.3f, 0.6f)); ImagenesObra.ponerEncuadre(ctx, o, true, Encuadre(1.6f, 0.68f, 0.4f))
        montar(false); foto("t34_portada_encuadrada")
    }

    /** El estudio: la obra con su barra de tinta, el menú y el editor. */
    @Test fun estudio() {
        grabando(); siembra(); montar(false)
        pestana("ESCRIBIENDO"); abrir("La ciudad de cristal"); abrir("MANUSCRITO →"); foto("t40_estudio_manuscrito")
        rule.onAllNodesWithContentDescription("Ajustes de la obra").let { n -> n[n.fetchSemanticsNodes().size - 1] }.performClick(); rule.waitForIdle(); foto("t41_estudio_ajustes_obra")
    }
    @Test fun estudioNoche() { grabando(); siembra(); montar(true); pestana("ESCRIBIENDO"); abrir("La ciudad de cristal"); abrir("MANUSCRITO →"); foto("t42_estudio_noche"); abrir("FICHAS"); foto("t42b_estudio_fichas_noche") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun estudioLetraGrande() {
        grabando(); siembra(); montar(false); pestana("ESCRIBIENDO"); abrir("La ciudad de cristal"); abrir("MANUSCRITO →"); foto("t43_estudio_letra_grande")
        abrir("Donde empieza todo"); foto("t43b_capitulo_letra_grande")
    }
    /** El modo lectura de una ficha (el panel «ELENA.») y su panel en letra grande. */
    @Test fun lectura() { grabando(); siembra(); montar(false); pestana("ESCRIBIENDO"); abrir("La ciudad de cristal"); abrir("MANUSCRITO →"); abrir("FICHAS"); abrir("Elena"); foto("t46_ficha_lectura") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun lecturaLetraGrande() {
        grabando(); siembra(); montar(false); pestana("ESCRIBIENDO"); abrir("La ciudad de cristal"); abrir("MANUSCRITO →"); abrir("FICHAS"); abrir("Elena"); foto("t46b_ficha_lectura_letra_grande")
    }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun editorLetraGrande() {
        grabando(); siembra(); montar(false); rule.onNodeWithText("SEGUIR ESCRIBIENDO").performClick(); rule.waitForIdle(); foto("t44b_editor_letra_grande")
    }
    @Test fun editor() {
        grabando(); siembra(); montar(false)
        rule.onNodeWithText("SEGUIR ESCRIBIENDO").performClick(); rule.waitForIdle(); foto("t44_editor")
        rule.onAllNodesWithContentDescription("Más").let { n -> if (n.fetchSemanticsNodes().isNotEmpty()) { n[n.fetchSemanticsNodes().size - 1].performClick(); rule.waitForIdle(); foto("t45_editor_menu") } }
    }

    @Test fun ajustesMeta() {
        grabando(); siembra(); montar(false)
        rule.onNodeWithContentDescription("Ajustes").performClick(); rule.waitForIdle(); foto("t50_ajustes")
        rule.onAllNodesWithText("ESCRITURA").let { n -> n[n.fetchSemanticsNodes().size - 1] }.performClick(); rule.waitForIdle(); foto("t51_ajustes_meta_del_dia")
    }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun ajustesMetaLetraGrande() {
        grabando(); siembra(); montar(false)
        rule.onNodeWithContentDescription("Ajustes").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("ESCRITURA").let { n -> n[n.fetchSemanticsNodes().size - 1] }.performClick(); rule.waitForIdle(); foto("t51b_ajustes_meta_letra_grande")
    }
}
