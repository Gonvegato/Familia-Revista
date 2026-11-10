package com.gonzalo.marfil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import java.io.File

/** Capturas de la interfaz en el ordenador, con libros de ejemplo (solo con -Proborazzi.test.record=true). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun portada(nombre: String, a: Int, b: Int): String {
        val f = File(ctx.cacheDir, "p_${nombre.hashCode()}.png")
        val bmp = Bitmap.createBitmap(300, 440, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRect(0f, 0f, 300f, 440f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 440f, a, b, Shader.TileMode.CLAMP) })
            drawText(nombre, 24f, 380f, Paint().apply { color = 0xCCFFFFFF.toInt(); textSize = 30f; isAntiAlias = true; isFakeBoldText = true })
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    /** Un fondo de prueba a lo ancho: un atardecer con montañas (para el cartel y la cabecera de la ficha con fondo). */
    private fun fondo(nombre: String, cielo: Int, abajo: Int): String {
        val f = File(ctx.cacheDir, "f_${nombre.hashCode()}.png")
        val w = 1200f; val h = 500f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h, cielo, abajo, Shader.TileMode.CLAMP) })
            drawCircle(w * 0.68f, h * 0.38f, 70f, Paint().apply { color = 0xFFF2C46B.toInt(); isAntiAlias = true })
            val p = android.graphics.Path().apply { moveTo(0f, h * 0.72f); lineTo(w * 0.12f, h * 0.5f); lineTo(w * 0.25f, h * 0.66f); lineTo(w * 0.4f, h * 0.42f); lineTo(w * 0.55f, h * 0.62f)
                lineTo(w * 0.72f, h * 0.45f); lineTo(w * 0.88f, h * 0.6f); lineTo(w, h * 0.5f); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(p, Paint().apply { color = 0xFF14070A.toInt(); isAntiAlias = true })
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    private fun siembra() {
        Skin.cargar(ctx); Biblioteca.cargada = true; Biblioteca.libros.clear(); Biblioteca.borrados.clear(); Sagas.cargar(ctx); Sagas.lista.clear(); Sagas.borradas.clear()
        val ahora = System.currentTimeMillis(); val dia = 86_400_000L
        fun l(id: String, t: String, a: String, e: Estado, fr: Float = 0f, pag: Int = 0, ult: Long = 0, ms: Long = 0, anad: Long = 0, img: String = "") =
            Libro(id, t, a, portada = img, uri = "file:///x", archivo = "$id.epub", estado = e, fraccion = fr, paginas = pag, paginasOrigen = if (pag > 0) "openlibrary" else "", ultima = ult, msPropios = ms, anadido = ahora - anad)
        Biblioteca.fijaParaPruebas(listOf(
            l("1", "El nombre de la rosa", "Umberto Eco", Estado.LEYENDO, 0.37f, 540, ahora, 5 * 3600_000L + 20 * 60_000L, 9, portada("Rosa", 0xFF8C2A22.toInt(), 0xFF2B1410.toInt())),
            l("2", "Dune", "Frank Herbert", Estado.LEYENDO, 0.8f, 608, ahora - dia, 9 * 3600_000L, 8, portada("Dune", 0xFFD9A53A.toInt(), 0xFF5A3A0A.toInt())),
            l("11", "Ubik", "Philip K. Dick", Estado.LEYENDO, 0.1f, 224, ahora - 2 * dia, 30 * 60_000L, 7),
            l("3", "Cien años de soledad", "Gabriel García Márquez", Estado.PENDIENTE, 0f, 471, 0, 0, 7, portada("Cien", 0xFF2F5D8A.toInt(), 0xFF0B1B2E.toInt())),
            l("4", "La sombra del viento", "Carlos Ruiz Zafón", Estado.PENDIENTE, 0f, 528, 0, 0, 6),
            l("7", "Los pilares de la Tierra", "Ken Follett", Estado.PENDIENTE, 0f, 1056, 0, 0, 3),
            l("8", "Mesías de Dune", "Frank Herbert", Estado.PENDIENTE, 0f, 279, 0, 0, 2),
            l("9", "Hijos de Dune", "Frank Herbert", Estado.PENDIENTE, 0f, 504, 0, 0, 1),
            l("5", "Fahrenheit 451", "Ray Bradbury", Estado.LEIDO, 1f, 249, ahora - 20 * dia, 4 * 3600_000L, 4, portada("F451", 0xFFE0603A.toInt(), 0xFF5A1D0A.toInt())),
            l("6", "1984", "George Orwell", Estado.LEIDO, 1f, 328, ahora - 40 * dia, 6 * 3600_000L, 4),
            l("10", "Solaris", "Stanislaw Lem", Estado.ABANDONADO, 0.22f, 230, ahora - 60 * dia, 2 * 3600_000L, 5),
        ))
        // Dos terminados este año (y uno de ellos, por segunda vez) y un reto de 12
        Biblioteca.libros[8] = Biblioteca.libros[8].copy(terminados = listOf(ahora - 200 * dia, ahora - 20 * dia))
        Biblioteca.libros[9] = Biblioteca.libros[9].copy(terminados = listOf(ahora - 40 * dia))
        Ajustes.ponMetaAnual(ctx, 12)
        Sagas.poner(Saga("sd", "Crónicas de Dune", listOf(Biblioteca.libros[1].clave, Biblioteca.libros[6].clave, Biblioteca.libros[7].clave)))
        // Unos días de lectura para que se vean las barras de la semana, y subrayados en Dune
        val hoy = java.time.LocalDate.now()
        val dias = org.json.JSONObject().apply { listOf(0 to 52, 1 to 35, 2 to 0, 3 to 70, 4 to 25, 5 to 41, 6 to 18).forEach { (atras, min) -> if (min > 0) put(hoy.minusDays(atras.toLong()).toString(), min * 60_000L) } }
        val subr = org.json.JSONArray()
            .put(org.json.JSONObject().put("value", "a").put("created", ahora - 5_000_000L).put("text", "El miedo es la pequeña muerte que conduce a la aniquilación total.").put("note", "Frase clave de la Letanía").put("label", "Capítulo 2").put("frac", 0.2))
            .put(org.json.JSONObject().put("value", "b").put("created", ahora - 3_000_000L).put("text", "Quien controla la especia controla el universo.").put("note", "").put("label", "Capítulo 11").put("frac", 0.55))
        EstadoLectura.ponerSinAvisar(ctx, "2", org.json.JSONObject().put("notes", subr).put("label", "Capítulo 11"))
        EstadoLectura.ponerSinAvisar(ctx, "1", org.json.JSONObject().put("stats", org.json.JSONObject().put("days", dias)))
    }

    private fun siembraConFondo() {
        siembra()
        val i1 = Biblioteca.libros.indexOfFirst { it.id == "1" }; val i2 = Biblioteca.libros.indexOfFirst { it.id == "2" }
        Biblioteca.libros[i1] = Biblioteca.libros[i1].copy(fondo = fondo("rosa", 0xFFE0703A.toInt(), 0xFF2A0A0E.toInt()))
        Biblioteca.libros[i2] = Biblioteca.libros[i2].copy(fondo = fondo("dune", 0xFFF0B060.toInt(), 0xFF5A2A0A.toInt()))
    }

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    private fun montar(oscuro: Boolean) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = if (oscuro) 2 else 1
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { MarfilApp() } } }
    }

    private fun graba() = org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
    private fun pestana(nombre: String) { rule.onNodeWithContentDescription(nombre).performClick(); rule.waitForIdle() }

    /** Las pantallas de la dirección D, de un tirón: Hoy, Biblioteca (y un par de filtros), la ficha, Tú y los ajustes. */
    private fun recorrido(suf: String, oscuro: Boolean, conFondo: Boolean = false) {
        if (conFondo) siembraConFondo() else siembra()
        montar(oscuro); foto("d1_hoy$suf")
        pestana("Biblioteca"); foto("d2_biblioteca$suf")
        rule.onNodeWithText("Pendientes").performClick(); foto("d2b_pendientes$suf")
        rule.onNodeWithText("Sagas").performClick(); foto("d2c_sagas$suf")
        rule.onNodeWithText("Leyendo").performClick(); rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Dune", substring = true)[0].performClick(); foto("d3_ficha$suf")
        rule.onNodeWithContentDescription("Volver").performClick(); rule.waitForIdle()
        pestana("Tú"); foto("d4_tu$suf")
        rule.onNodeWithContentDescription("Ajustes").performClick(); foto("d5_ajustes$suf")
    }

    @Test fun claro() { graba(); recorrido("", false) }
    @Test fun noche() { graba(); recorrido("_noche", true) }
    @Test fun conFondo() { graba(); recorrido("_fondo", false, conFondo = true) }
    @Test @Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi", fontScale = 1.3f) fun letraGrande() { graba(); recorrido("_letra_grande", false) }
    @Test @Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi", fontScale = 1.3f) fun letraGrandeConFondo() { graba(); recorrido("_letra_grande_fondo", true, conFondo = true) }
    @Test @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi") fun tabletApaisada() { graba(); siembra(); montar(false); foto("d6_tablet_hoy"); pestana("Biblioteca"); foto("d6b_tablet_biblioteca") }

    @Test fun vacia() {
        graba()
        Skin.cargar(ctx); Biblioteca.cargada = true; Biblioteca.libros.clear(); Sagas.lista.clear()
        montar(false); foto("d7_hoy_vacia"); pestana("Biblioteca"); foto("d7b_biblioteca_vacia")
    }
}
