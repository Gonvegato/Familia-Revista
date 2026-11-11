package com.gonzalo.mostaza

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
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Capturas de Hathor 0.7.0 (dirección C, como Seshat y Bastet), con los colores «Palomitas» (los de serie: fondo #F4ECDD, texto #1B1A1F,
 * acento #D7263D), de noche (la paleta oscura con el mismo acento) y con la letra grande (1,3). Solo al grabar:
 *   gradle :mostaza:testDebugUnitTest --tests '*CapturasCTest*' -Proborazzi.test.record=true
 * Las imágenes quedan en mostaza/build/outputs/roborazzi/ (empiezan por «h»).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasCTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun grabando() = org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")

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

    private lateinit var e: Escaparate

    /** La biblioteca de ejemplo: series y películas en cada estado, con portadas, lo visto, la semana, el reto y los próximos capítulos. */
    private fun siembra(conFondo: Boolean = false, pocos: Boolean = false) {
        iniciar(ctx)
        e = Escaparate(ctx)
        Addons.http = e; Addons.guarda(e.addons)
        Tmdb.claveFija = null
        Encuadres.almacen = "encuadres_capturas"
        val ahora = System.currentTimeMillis(); val dia = 86_400_000L
        val p = e.pelis; val s = e.series
        fun t(x: Escaparate.P, est: Estado, an: Long, ul: Long = 0L) = e.titulo(x, est, an, ul).copy(portada = e.poster(x), minutos = x.dur.filter(Char::isDigit).toIntOrNull() ?: 0)
        val ano = LocalDate.now().year
        val esteAno = java.time.LocalDate.of(ano, 1, 15).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        var l = listOf(
            t(s[0], Estado.VIENDO, ahora - 60 * dia, ahora - 3_600_000L), t(p[1], Estado.VIENDO, ahora - 5 * dia, ahora - dia), t(s[3], Estado.VIENDO, ahora - 9 * dia, ahora - 4 * dia),
            t(p[0], Estado.PENDIENTE, ahora - 2 * dia), t(s[2], Estado.PENDIENTE, ahora - 3 * dia), t(p[5], Estado.PENDIENTE, ahora - 4 * dia), t(p[6], Estado.PENDIENTE, ahora - 6 * dia),
            t(p[3], Estado.VISTO, ahora - 300 * dia).copy(fin = esteAno + 20 * dia), t(p[4], Estado.VISTO, ahora - 280 * dia).copy(fin = esteAno + 90 * dia), t(s[1], Estado.VISTO, ahora - 90 * dia).copy(fin = ahora - 20 * dia),
            t(p[2], Estado.VISTO, ahora - 40 * dia).copy(fin = ahora - 10 * dia),
            t(s[4], Estado.ABANDONADO, ahora - 400 * dia),
        )
        if (pocos) l = l.filter { it.id !in setOf(p[1].id, s[2].id, p[5].id, p[6].id) }
        if (conFondo) l = l.map { x -> when (x.id) { s[0].id -> x.copy(fondoImg = fondo("tronos", 0xFF7AA0C8.toInt(), 0xFF1A2A3A.toInt())); p[3].id -> x.copy(fondoImg = fondo("cadena", 0xFFE0703A.toInt(), 0xFF2A0A0E.toInt())); else -> x } }
        val hoy = LocalDate.now()
        fun r(x: Escaparate.P, hace: Long, min: Long) = Rato(x.id, x.nombre, hoy.minusDays(hace).toString(), min * 60_000L)
        Biblioteca.fijaParaPruebas(l, listOf(
            Progreso("tt0944947:2:5", "tt0944947", 2, 5, 34 * 60_000L, 57 * 60_000L, false, 0L, ahora - 3_600_000L),
            Progreso("tt0944947:2:4", "tt0944947", 2, 4, 57 * 60_000L, 57 * 60_000L, true, ahora - dia, ahora - dia),
            Progreso("tt0944947:1:1", "tt0944947", 1, 1, 57 * 60_000L, 57 * 60_000L, true, ahora - 30 * dia, ahora - 30 * dia),
            Progreso("tt15398776", "tt15398776", 0, 0, 72 * 60_000L, 180 * 60_000L, false, 0L, ahora - dia),
            Progreso("tt11280740:1:3", "tt11280740", 1, 3, 20 * 60_000L, 55 * 60_000L, false, 0L, ahora - 4 * dia),
            Progreso("tt6468322:1:4", "tt6468322", 1, 4, 70 * 60_000L, 70 * 60_000L, true, ahora - 200 * dia, ahora - 200 * dia),
            Progreso("tt0111161", "tt0111161", 0, 0, 142 * 60_000L, 142 * 60_000L, true, esteAno + 20 * dia, esteAno + 20 * dia),
        ), listOf(r(s[0], 0, 34), r(s[0], 1, 57), r(p[1], 1, 72), r(s[0], 3, 114), r(s[3], 4, 51), r(p[2], 10, 144), r(s[0], 2, 20)))
        RetoCine.poner(ctx, 24)
        Proximos.fijos = listOf(
            Proximo("tt0944947", "Juego de tronos", 3, 1, "Valar Dohaeris", hoy.plusDays(2).toString()),
            Proximo("tt11280740", "Separación", 2, 1, "Hola, señora Cobel", hoy.plusDays(5).toString()),
            Proximo("tt2788316", "Shōgun", 2, 1, "", hoy.plusDays(40).toString()),
        )
    }

    /** Los colores de serie de Hathor 0.7.0: «Palomitas» (sin nada elegido a mano). */
    private fun palomitas() {
        Skin.origenAtrio = false; Skin.invertido = false; Skin.fondoId = ""
        Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L
        Skin.palette = PALETTES.first { it.id == "celuloide" }; Skin.accentId = "paleta"
        Skin.display = "Atrio"; Skin.body = "Atrio"
    }

    @After fun limpia() { Biblioteca.olvidaParaPruebas(); Proximos.fijos = null; Encuadres.almacen = "encuadres" }

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500)
        // (el fundido de las imágenes de Coil va con el reloj de Android, que en Robolectric está parado: se adelanta)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2)); rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    private fun montar(oscuro: Boolean, contenido: @androidx.compose.runtime.Composable () -> Unit = { MostazaApp() }) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = if (oscuro) 2 else 1
        palomitas()
        rule.setContent { MostazaTheme { Surface(Modifier.fillMaxSize(), color = Paper) { contenido() } } }
    }

    private fun esperaTexto(t: String) { runCatching { rule.waitUntil(10_000) { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } } }
    private fun pestana(t: String) { rule.onAllNodesWithText(t, useUnmergedTree = true)[0].performScrollTo().performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle() }
    private fun abrir(t: String) { rule.onAllNodesWithText(t, useUnmergedTree = true)[0].performScrollTo().performClick(); rule.waitForIdle() }
    private fun bajar(veces: Int = 1) = repeat(veces) { rule.onRoot().performTouchInput { swipeUp(startY = bottom - 60f, endY = top + 200f, durationMillis = 900) }; rule.waitForIdle() }
    private fun volver() { rule.onAllNodesWithText("← VOLVER", useUnmergedTree = true)[0].performScrollTo().performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle() }

    @Test fun portada() { grabando(); siembra(); montar(false); foto("h01_portada"); bajar(); foto("h01b_portada_2"); bajar(); foto("h01c_portada_3"); bajar(2); foto("h01d_portada_abajo") }
    @Test fun portadaNoche() { grabando(); siembra(); montar(true); foto("h02_portada_noche"); bajar(); foto("h02b_portada_noche_2") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaLetraGrande() {
        grabando(); siembra(); montar(false); foto("h03_portada_letra_grande"); bajar(); foto("h03b_portada_letra_grande_2"); bajar(); foto("h03c_portada_letra_grande_3")
    }
    @Test fun portadaConFondo() { grabando(); siembra(conFondo = true); montar(false); foto("h04_portada_con_fondo") }
    @Test fun portadaConFondoNoche() { grabando(); siembra(conFondo = true); montar(true); foto("h04b_portada_con_fondo_noche") }
    @Test fun portadaPocos() { grabando(); siembra(pocos = true); montar(false); rule.onNodeWithText("También ves").performScrollTo(); rule.waitForIdle(); foto("h07_portada_pocos") }

    /** La semana con un día tocado (sale encima lo que viste ese día). */
    private fun diaTocado(nombre: String) {
        siembra(); montar(false)
        bajar()
        val dia = LocalDate.now().minusDays(3).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "ES"))
        rule.onAllNodes(hasContentDescription(dia, substring = true, ignoreCase = true))[0].performClick(); rule.waitForIdle()
        foto(nombre)
    }
    @Test fun portadaSemana() { grabando(); diaTocado("h05_portada_dia_tocado") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaSemanaLetraGrande() { grabando(); diaTocado("h05b_portada_dia_tocado_letra_grande") }

    /** El plegado de la cabecera por pasos: el nombre encoge con su hueco (sin cortarse ni dejar banda vacía). */
    private fun pasos(prefijo: String) {
        listOf(25, 50, 75, 100).forEachIndexed { i, pct ->
            val d = if (i == 0) 85f else if (pct == 100) 400f else 62f
            rule.onRoot().performTouchInput { swipeUp(startY = centerY, endY = centerY - d, durationMillis = 900) }
            foto("${prefijo}_plegado_$pct")
        }
    }
    @Test fun plegado() { grabando(); siembra(); montar(false); pasos("h06") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun plegadoLetraGrande() { grabando(); siembra(); montar(false); pasos("h06b_letra_grande") }

    @Test fun paginas() {
        grabando(); siembra(); montar(false)
        pestana("VIENDO"); foto("h10_viendo")
        pestana("PENDIENTES"); foto("h11_pendientes")
        pestana("VISTAS"); foto("h12_vistas")
        pestana("ABANDONADAS"); foto("h13_abandonadas")
        pestana("PRÓXIMOS"); foto("h14_proximos")
    }
    @Test fun paginasNoche() { grabando(); siembra(); montar(true); pestana("VIENDO"); foto("h16_viendo_noche"); pestana("VISTAS"); foto("h16b_vistas_noche") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun paginasLetraGrande() {
        grabando(); siembra(); montar(false)
        pestana("VIENDO"); foto("h17_viendo_letra_grande")
        pestana("PENDIENTES"); foto("h17b_pendientes_letra_grande")
        pestana("PRÓXIMOS"); foto("h17c_proximos_letra_grande")
    }

    @Test fun fichas() {
        grabando(); siembra(); montar(false)
        pestana("VIENDO"); abrir("Juego de tronos"); esperaTexto("Temporada 2"); foto("h20_ficha_serie_viendo")
        bajar(); foto("h20b_ficha_serie_viendo_2"); bajar(); foto("h20c_ficha_serie_viendo_3"); bajar(3); foto("h20d_ficha_serie_viendo_abajo")
        volver()
        pestana("VISTAS"); abrir("Cadena perpetua"); esperaTexto("banquero"); foto("h21_ficha_pelicula_vista"); bajar(); foto("h21b_ficha_pelicula_vista_2")
        volver()
        pestana("PENDIENTES"); abrir("Dune: Parte dos"); esperaTexto("Atreides"); foto("h22_ficha_pendiente")
    }
    @Test fun fichaConFondo() { grabando(); siembra(conFondo = true); montar(false); pestana("VISTAS"); abrir("Cadena perpetua"); esperaTexto("banquero"); foto("h24_ficha_con_fondo") }
    @Test fun fichaNoche() { grabando(); siembra(conFondo = true); montar(true); pestana("VIENDO"); abrir("Juego de tronos"); esperaTexto("Temporada 2"); foto("h25_ficha_con_fondo_noche"); bajar(); foto("h25b_ficha_noche_2") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun fichaLetraGrande() {
        grabando(); siembra(conFondo = true); montar(false)
        pestana("VIENDO"); abrir("Juego de tronos"); esperaTexto("Temporada 2"); foto("h26_ficha_con_fondo_letra_grande"); bajar(); foto("h26b_ficha_letra_grande_2")
        volver()
        pestana("ABANDONADAS"); abrir("La casa de papel"); foto("h26c_ficha_abandonada_letra_grande")
    }

    /** La hoja «IMÁGENES.»: la portada y el fondo, con propuestas de TMDB de ejemplo. */
    private fun imagenes(sufijo: String, oscuro: Boolean) {
        siembra(conFondo = true)
        val props = e.todas.take(5).map { e.poster(it) }
        val fondos = listOf(fondo("p1", 0xFFE0703A.toInt(), 0xFF2A0A0E.toInt()), fondo("p2", 0xFF70A0E0.toInt(), 0xFF0A1A2A.toInt()))
        val esFondo = androidx.compose.runtime.mutableStateOf(false)
        val ref = Biblioteca.titulos.first { it.nombre == "Juego de tronos" }.ref
        montar(oscuro) { androidx.compose.runtime.key(esFondo.value) { ContenidoImagenes(ref, esFondo.value, {}, propuestas = (if (esFondo.value) fondos else props).map { it to it }) } }
        foto("h30_imagenes_portada$sufijo")
        esFondo.value = true; rule.waitForIdle(); foto("h31_imagenes_fondo$sufijo")
    }
    @Test fun imagenes() { grabando(); imagenes("", false) }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun imagenesLetraGrande() { grabando(); imagenes("_letra_grande", false) }

    /** El editor del encuadre: la portada (2:3) y el fondo (con la forma del cartel). */
    @Test fun encuadre() {
        grabando(); siembra(conFondo = true)
        val t = Biblioteca.titulos.first { it.nombre == "Juego de tronos" }
        val cual = androidx.compose.runtime.mutableStateOf(false)
        montar(false) { androidx.compose.runtime.key(cual.value) { ContenidoEncuadre(if (cual.value) t.fondoImg else t.portada, Encuadre(1.6f, 0.4f, 0.4f), cual.value, {}, {}) } }
        foto("h32_encuadre_portada")
        cual.value = true; rule.waitForIdle(); foto("h33_encuadre_fondo")
    }

    /** Ajustes → Avanzado: el reto del año. */
    @Test fun ajustesReto() {
        grabando(); siembra(); montar(false) { PantEstilo(alVolver = {}, pestanaInicial = 5) }
        foto("h40_ajustes_reto")
    }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun ajustesRetoLetraGrande() {
        grabando(); siembra(); montar(false) { PantEstilo(alVolver = {}, pestanaInicial = 5) }
        foto("h40b_ajustes_reto_letra_grande")
    }

    /** Sin nada en la biblioteca: la portada vacía (con el catálogo de tus addons debajo). */
    @Test fun portadaVacia() {
        grabando(); iniciar(ctx); val x = Escaparate(ctx); Addons.http = x; Addons.guarda(x.addons); Biblioteca.fijaParaPruebas(emptyList()); Proximos.fijos = emptyList()
        montar(false); foto("h08_portada_vacia")
    }
}
