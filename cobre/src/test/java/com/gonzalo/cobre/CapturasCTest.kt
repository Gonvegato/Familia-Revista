package com.gonzalo.cobre

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
import com.gonzalo.iptv.futbol.Estado
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas de Nut 0.8.0 (dirección C, como Seshat, Bastet y Hathor), con los colores de Nut («A tu manera»: fondo #3D0E19, texto #F1DCB5,
 * acento #D4A06A), de noche (la paleta oscura con el mismo acento) y con la letra grande (1,3). Solo al grabar:
 *   gradle :cobre:testDebugUnitTest --tests '*CapturasCTest*' -Proborazzi.test.record=true
 * Las imágenes quedan en cobre/build/outputs/roborazzi/ (empiezan por «n»).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasCTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun grabando() = org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")

    /** Un fondo de prueba a lo ancho: un estadio de noche (gradas, focos y el césped). */
    private fun fondo(nombre: String, cielo: Int, abajo: Int): String {
        val f = File(ctx.cacheDir, "f_${nombre.hashCode()}.png")
        val w = 1200f; val h = 600f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h, cielo, abajo, Shader.TileMode.CLAMP) })
            listOf(0.15f, 0.85f).forEach { x -> drawCircle(w * x, h * 0.18f, 26f, Paint().apply { color = 0xFFFFF4D0.toInt(); isAntiAlias = true }) }
            drawRect(0f, h * 0.45f, w, h * 0.62f, Paint().apply { color = 0xFF2A1F3A.toInt() })
            drawRect(0f, h * 0.62f, w, h, Paint().apply { color = 0xFF1F6B35.toInt() })
            drawRect(w * 0.1f, h * 0.7f, w * 0.9f, h * 0.72f, Paint().apply { color = 0x88FFFFFF.toInt() })
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    private var futbol: Futbol.Fijo? = null

    /** El fútbol de mentira de las capturas de siempre, lo visto esta semana y, para la IPTV, el escaparate de canales. */
    private fun siembra(vivo: Boolean = true, sinEquipos: Boolean = false) {
        iniciar(ctx)
        val e = Escaparate(ctx)
        Tele.red = e.red
        Listas.guarda(listOf(e.lista, e.m3u))
        Tele.fijas = mapOf(e.lista.id to Cargada(e.lista, e.canales.map { it.grupo }.distinct(), e.canales, e.guia()), e.m3u.id to Cargada(e.m3u, emptyList(), e.canales.take(6), null))
        val ahora = System.currentTimeMillis()
        MiCuenta.fijo = MiCuenta.Fijo(favoritos = listOf(0, 2, 13).mapIndexed { k, i -> val c = e.canales[i]; Favorito("${e.lista.id}~${c.clave}", e.lista.id, c.clave, c.nombre, k.toLong()) },
            progresos = emptyList(), ultimo = Favorito("ultimo", e.lista.id, e.canales[2].clave, e.canales[2].nombre, ahora))
        val f = FutbolDeMentira.datos(ctx, vivo = vivo)
        futbol = if (sinEquipos) Futbol.Fijo(rivales = emptyList(), grupos = f.grupos, partidos = f.partidos, partidosDia = f.partidosDia, clasificaciones = f.clasificaciones, equiposDe = f.equiposDe) else f
        Futbol.fijo = futbol
        val hoy = java.time.LocalDate.now(); val m = 60_000L
        TiempoVisto.fijos = mapOf(hoy.toString() to 25 * m, hoy.minusDays(1).toString() to 95 * m, hoy.minusDays(2).toString() to 51 * m, hoy.minusDays(3).toString() to 130 * m, hoy.minusDays(5).toString() to 40 * m)
        Encuadres.almacen = "encuadres_capturas"
        ImagenesEquipo.fijas.clear()
    }

    private fun conFondo() { ImagenesEquipo.fijas["83"] = "" to fondo("campnou", 0xFF0B1A3A.toInt(), 0xFF3A1030.toInt()) }

    @After fun fin() { Futbol.fijo = null; TiempoVisto.fijos = null; Tele.fijas = null; MiCuenta.fijo = null; ImagenesEquipo.fijas.clear() }

    /** Los colores de Nut («A tu manera»); de noche, la paleta oscura con el acento de Nut. */
    private fun coloresNut(oscuro: Boolean) {
        Skin.origenAtrio = false; Skin.invertido = false; Skin.fondoId = ""
        // Los de serie desde noviembre de 2026 (paleta «vino»), sin nada a mano
        Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L
        Skin.palette = PALETTES.first { it.id == "vino" }; Skin.accentId = "paleta"
    }

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2)); rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    private fun montar(oscuro: Boolean, contenido: @androidx.compose.runtime.Composable () -> Unit = { CobreApp() }) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = if (oscuro) 2 else 1
        coloresNut(oscuro)
        rule.setContent { CobreTheme { Surface(Modifier.fillMaxSize(), color = Paper) { contenido() } } }
    }

    private fun pestana(t: String) { rule.onAllNodesWithText(t, useUnmergedTree = true)[0].performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle() }
    private fun bajar(veces: Int = 1) = repeat(veces) { rule.onRoot().performTouchInput { swipeUp(startY = bottom - 60f, endY = top + 200f, durationMillis = 900) }; rule.waitForIdle() }

    @Test fun portada() { grabando(); siembra(); montar(false); foto("n01_portada_en_directo"); bajar(); foto("n01b_portada_2"); bajar(); foto("n01c_portada_3"); bajar(2); foto("n01d_portada_abajo") }
    @Test fun portadaNoche() { grabando(); siembra(); montar(true); foto("n02_portada_noche"); bajar(); foto("n02b_portada_noche_2") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaLetraGrande() {
        grabando(); siembra(); montar(false); foto("n03_portada_letra_grande"); bajar(); foto("n03b_portada_letra_grande_2"); bajar(); foto("n03c_portada_letra_grande_3")
    }
    @Test fun portadaProximoConFondo() { grabando(); siembra(vivo = false); conFondo(); montar(false); foto("n04_portada_proximo_con_fondo") }
    @Test fun portadaProximoNoche() { grabando(); siembra(vivo = false); conFondo(); montar(true); foto("n04b_portada_proximo_noche") }
    @Test fun portadaSinEquipos() { grabando(); siembra(sinEquipos = true); montar(false); foto("n05_portada_sin_equipos") }

    private fun diaTocado(nombre: String) {
        siembra(); montar(false)
        bajar()
        val dia = java.time.LocalDate.now().minusDays(3).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "ES"))
        rule.onAllNodes(hasContentDescription(dia, substring = true, ignoreCase = true))[0].performScrollTo().performClick(); rule.waitForIdle()
        foto(nombre)
    }
    @Test fun portadaSemana() { grabando(); diaTocado("n06_portada_dia_tocado") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaSemanaLetraGrande() { grabando(); diaTocado("n06b_portada_dia_tocado_letra_grande") }

    private fun pasos(prefijo: String) {
        listOf(25, 50, 75, 100).forEachIndexed { i, pct ->
            val d = if (i == 0) 70f else if (pct == 100) 400f else 55f
            rule.onRoot().performTouchInput { swipeUp(startY = centerY, endY = centerY - d, durationMillis = 900) }
            foto("${prefijo}_plegado_$pct")
        }
    }
    @Test fun plegado() { grabando(); siembra(); montar(false); pasos("n07") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun plegadoLetraGrande() { grabando(); siembra(); montar(false); pasos("n07b_letra_grande") }

    private fun paginas(prefijo: String, oscuro: Boolean) {
        siembra(); montar(oscuro)
        pestana("HOY"); foto("${prefijo}_hoy")
        pestana("COMPETICIONES"); foto("${prefijo}_competiciones")
        pestana("TUS EQUIPOS"); foto("${prefijo}_tus_equipos")
        pestana("IPTV"); foto("${prefijo}_iptv")
    }
    @Test fun lasPaginas() { grabando(); paginas("n08", false) }
    @Test fun lasPaginasNoche() { grabando(); paginas("n08b_noche", true) }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun lasPaginasLetraGrande() { grabando(); paginas("n08c_letra_grande", false) }

    @Test fun fichaEnDirecto() { grabando(); siembra(); montar(false) { PantFichaPartido(futbol!!.partidos.first(), alCerrar = {}, alConfigurar = {}) }; foto("n09_ficha_en_directo"); bajar(); foto("n09b_ficha_en_directo_2"); bajar(2); foto("n09c_ficha_en_directo_3") }
    @Test fun fichaProximaConFondo() { grabando(); siembra(vivo = false); ImagenesEquipo.fijas["1068"] = "" to fondo("metropolitano", 0xFF3A0B10.toInt(), 0xFF10203A.toInt()); montar(false) { PantFichaPartido(futbol!!.partidos.first(), alCerrar = {}, alConfigurar = {}) }; foto("n10_ficha_proxima_con_fondo") }
    @Test fun fichaFinal() { grabando(); siembra(); montar(false) { PantFichaPartido(futbol!!.partidos.first { it.estado.terminado }, alCerrar = {}, alConfigurar = {}) }; foto("n11_ficha_final") }
    @Test fun fichaNoche() { grabando(); siembra(); montar(true) { PantFichaPartido(futbol!!.partidos.first(), alCerrar = {}, alConfigurar = {}) }; foto("n11b_ficha_noche") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun fichaLetraGrande() { grabando(); siembra(); montar(false) { PantFichaPartido(futbol!!.partidos.first(), alCerrar = {}, alConfigurar = {}) }; foto("n11c_ficha_letra_grande") }

    @Test fun fichaEquipo() { grabando(); siembra(); conFondo(); montar(false) { PantEquipo(futbol!!.equipos.first().first, "esp.1", alCerrar = {}, alFicha = {}) }; foto("n12_ficha_equipo"); bajar(); foto("n12b_ficha_equipo_2") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun fichaEquipoLetraGrande() { grabando(); siembra(); montar(false) { PantEquipo(futbol!!.equipos.first().first, "esp.1", alCerrar = {}, alFicha = {}) }; foto("n12c_ficha_equipo_letra_grande") }

    @Test fun imagenes() {
        grabando(); siembra(); conFondo()
        montar(false) { ContenidoImagenesEquipo(futbol!!.equipos.first().first, alCerrar = {}) }; foto("n13_imagenes_escudo")
        pestana("EL FONDO"); foto("n13b_imagenes_fondo")
    }
    @Test fun encuadre() { grabando(); siembra(); conFondo(); montar(false) { ContenidoEncuadre(ImagenesEquipo.fijas["83"]!!.second, Encuadre(1.3f, 0.5f, 0.6f), true, {}, {}) }; foto("n14_encuadre") }
}
