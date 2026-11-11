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

    /** Lo de [siembra] y, además, fondo elegido para «El nombre de la rosa» (el que se lee ahora) y para Dune. */
    private fun siembraConFondo() {
        siembra()
        val i1 = Biblioteca.libros.indexOfFirst { it.id == "1" }; val i2 = Biblioteca.libros.indexOfFirst { it.id == "2" }
        Biblioteca.libros[i1] = Biblioteca.libros[i1].copy(fondo = fondo("rosa", 0xFFE0703A.toInt(), 0xFF2A0A0E.toInt()))
        Biblioteca.libros[i2] = Biblioteca.libros[i2].copy(fondo = fondo("dune", 0xFFF0B060.toInt(), 0xFF5A2A0A.toInt()))
    }

    @Test fun conFondo() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembraConFondo(); montar(false); foto("1e_portada_con_fondo")
        rule.onNodeWithText("LEYENDO").performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle()
        rule.onAllNodesWithText("Dune", useUnmergedTree = true)[0].performClick(); foto("4h_ficha_con_fondo")
    }

    @Test fun conFondoNoche() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembraConFondo(); montar(true); foto("1f_portada_con_fondo_oscuro")
        rule.onNodeWithText("LEYENDO").performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle()
        rule.onAllNodesWithText("Dune", useUnmergedTree = true)[0].performClick(); foto("4i_ficha_con_fondo_oscuro")
    }

    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun conFondoLetraGrande() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembraConFondo(); montar(false); foto("c6_portada_con_fondo_letra_grande")
        rule.onNodeWithText("LEYENDO").performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle()
        rule.onAllNodesWithText("Dune", useUnmergedTree = true)[0].performClick(); foto("c7_ficha_con_fondo_letra_grande")
    }

    /** Encuadrar (0.20.0): el editor de la portada y del fondo, y el cartel y la ficha con el fondo acercado al sol y la portada encuadrada. */
    private fun siembraEncuadrada() {
        siembraConFondo()
        val i1 = Biblioteca.libros.indexOfFirst { it.id == "1" }; val i2 = Biblioteca.libros.indexOfFirst { it.id == "2" }
        Biblioteca.libros[i1] = Biblioteca.libros[i1].copy(encFondo = Encuadre(1.6f, 0.68f, 0.45f), encPortada = Encuadre(1.8f, 0.3f, 0.8f))
        Biblioteca.libros[i2] = Biblioteca.libros[i2].copy(encFondo = Encuadre(1.4f, 0.7f, 0.4f), encPortada = Encuadre(1.6f, 0.35f, 0.75f))
    }

    private fun editores(sufijo: String) {
        siembraEncuadrada(); Skin.cargar(ctx); coloresSeshat(false)
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val dune = Biblioteca.por("2")!!
        val fondo = androidx.compose.runtime.mutableStateOf(false)
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { androidx.compose.runtime.key(fondo.value) {
            if (fondo.value) ContenidoEncuadre(dune.fondo, dune.encFondo, true, {}, {}) else ContenidoEncuadre(dune.portada, dune.encPortada, false, {}, {})
        } } } }
        foto("7d_encuadre_portada$sufijo")
        fondo.value = true; rule.waitForIdle(); foto("7e_encuadre_fondo$sufijo")
    }

    @Test fun encuadre() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        editores("")
    }

    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun encuadreLetraGrande() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        editores("_letra_grande")
    }

    @Test fun conFondoEncuadrado() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembraEncuadrada(); montar(false); foto("1g_portada_encuadrada")
        rule.onNodeWithText("LEYENDO").performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle()
        rule.onAllNodesWithText("Dune", useUnmergedTree = true)[0].performClick(); foto("4j_ficha_encuadrada")
    }

    /** Paspartú (0.20.0): crema con la portada oscura, en la tinta con una portada casi blanca; y el velo arriba de la ficha. */
    private fun paspartu(sufijo: String, oscuro: Boolean) {
        siembraConFondo()
        val blanca = portada("", 0xFFF8F6F0.toInt(), 0xFFEDE9E0.toInt())
        val i2 = Biblioteca.libros.indexOfFirst { it.id == "2" }
        Biblioteca.libros[i2] = Biblioteca.libros[i2].copy(portada = blanca)
        montar(oscuro); foto("1h_portada_paspartu$sufijo")
        val i1 = Biblioteca.libros.indexOfFirst { it.id == "1" }
        Biblioteca.libros[i1] = Biblioteca.libros[i1].copy(portada = blanca); rule.waitForIdle(); foto("1i_portada_paspartu_blanca$sufijo")
        rule.onNodeWithText("LEYENDO").performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle()
        rule.onAllNodesWithText("Dune", useUnmergedTree = true)[0].performClick(); foto("4k_ficha_paspartu_blanca$sufijo")
    }

    @Test fun paspartu() { org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true"); paspartu("", false) }
    @Test fun paspartuNoche() { org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true"); paspartu("_oscuro", true) }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun paspartuLetraGrande() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true"); paspartu("_letra_grande", false)
    }

    @Test fun imagenes() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembraConFondo(); Skin.cargar(ctx); coloresSeshat(false)
        val c = listOf("Dune" to 0xFFD9A53A.toInt(), "Dune 2" to 0xFF8C5A22.toInt(), "Arena" to 0xFFB0702A.toInt(), "Otra" to 0xFF35523F.toInt())
            .mapIndexed { i, (n, col) -> portada(n, col, col and 0x7f7f7f).let { Candidata(it, it, if (i % 2 == 0) "iTunes" else "Open Library", "Dune · Frank Herbert") } }
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val fondo = androidx.compose.runtime.mutableStateOf(true)
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { androidx.compose.runtime.key(fondo.value) { ContenidoImagenes("2", fondo.value, {}, hallados = c) } } } }
        foto("7b_imagenes_fondo")
        fondo.value = false; rule.waitForIdle(); foto("7c_imagenes_portada")
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

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    private fun montar(oscuro: Boolean) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = if (oscuro) 2 else 1
        coloresSeshat(oscuro)
        rule.setContent { PorticoLike { MarfilApp() } }
    }

    @androidx.compose.runtime.Composable
    private fun PorticoLike(c: @androidx.compose.runtime.Composable () -> Unit) = MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { c() } }

    /** Los colores de Seshat («A tu manera»: fondo #F8F0D4, texto #050404, acento #C68B00); de noche, solo el acento sobre la paleta oscura. */
    private fun coloresSeshat(oscuro: Boolean) {
        // Los de la familia con el ocre de Seshat (sin nada a mano)
        Skin.aColoresDeSerie()
    }

    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun letraGrande() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false); foto("c1_portada_letra_grande")
        rule.onNodeWithText("LEÍDOS").performClick(); rule.waitForIdle(); foto("c2_leidos_letra_grande")
    }

    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun fichaLetraGrande() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("LEYENDO").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Dune", useUnmergedTree = true)[0].performClick(); foto("c3_ficha_letra_grande")
    }

    @Test fun portada() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false); foto("1_portada")
    }

    /** La cabecera a mitad de plegarse (el nombre encoge y se desvanece; nunca se corta) y la portada entera, abajo del todo. */
    @Test fun portadaPlegando() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onRoot().performTouchInput { swipeUp(startY = centerY, endY = centerY - 110f, durationMillis = 900) }
        foto("1b_portada_plegando")
        rule.onRoot().performTouchInput { swipeUp(startY = bottom - 40f, endY = top + 40f, durationMillis = 900) }
        rule.onRoot().performTouchInput { swipeUp(startY = bottom - 40f, endY = top + 40f, durationMillis = 900) }
        foto("1c_portada_abajo")
    }

    /** El plegado por pasos (≈25 %, 50 %, 75 % y 100 %): en cada paso la banda negra mide lo que el nombre, nunca un hueco vacío. */
    @Test fun portadaPlegadoPorPasos() { org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true"); siembra(); montar(false); pasos("1p") }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaPlegadoPorPasosLetraGrande() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true"); siembra(); montar(false); pasos("c9")
    }
    private fun pasos(prefijo: String) {
        // (el primer gesto se come el umbral de arrastre; los siguientes mueven ~60 px cada uno, una cuarta parte del nombre)
        listOf(25, 50, 75, 100).forEachIndexed { i, pct ->
            val d = if (i == 0) 85f else if (pct == 100) 400f else 62f
            rule.onRoot().performTouchInput { swipeUp(startY = centerY, endY = centerY - d, durationMillis = 900) }
            foto("${prefijo}_plegado_$pct")
        }
    }

    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaPlegandoLetraGrande() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onRoot().performTouchInput { swipeUp(startY = centerY, endY = centerY - 110f, durationMillis = 900) }
        foto("c4_portada_plegando_letra_grande")
        rule.onRoot().performTouchInput { swipeUp(startY = bottom - 40f, endY = top + 40f, durationMillis = 900) }
        rule.onRoot().performTouchInput { swipeUp(startY = bottom - 40f, endY = top + 40f, durationMillis = 900) }
        foto("c5_portada_abajo_letra_grande")
    }

    @Test fun portadaSinNadaALaMitad() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        for (i in Biblioteca.libros.indices) Biblioteca.libros[i].let { if (it.estado == Estado.LEYENDO) Biblioteca.libros[i] = it.copy(estado = Estado.PENDIENTE, fraccion = 0f) }
        montar(false); foto("1d_portada_sin_leyendo")
    }

    /** La portada con un día de la semana tocado (sale encima lo que leíste ese día): normal, noche y letra grande. */
    private fun portadaDiaTocado(nombre: String, oscuro: Boolean) {
        siembra(); montar(oscuro)
        rule.onNodeWithContentDescription("viernes", substring = true, ignoreCase = true).let { runCatching { it.performScrollTo() }; it.performClick() }
        rule.waitForIdle()
        rule.onNodeWithText("Esta semana").performScrollTo(); rule.waitForIdle()
        foto(nombre)
    }
    /** Con pocos libros, «También lees» y «Pendientes» lado a lado (uno a medias más y un pendiente). */
    @Test fun portadaPocos() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        Biblioteca.libros.removeAll { it.id in setOf("4", "7", "8", "9", "11") }
        montar(false)
        rule.onNodeWithText("Tu 2", substring = true).performScrollTo(); rule.waitForIdle(); foto("1k_portada_pocos")
    }

    @Test fun portadaSemana() { org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true"); portadaDiaTocado("1j_portada_semana", false) }
    @Test fun portadaSemanaNoche() { org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true"); portadaDiaTocado("1j_portada_semana_oscuro", true) }
    @Test @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f) fun portadaSemanaLetraGrande() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true"); portadaDiaTocado("c8_portada_semana_letra_grande", false)
    }

    @Test fun portadaOscuro() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(true); foto("2_portada_oscuro")
    }

    @Test fun seccionesYSagas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("LEYENDO").performClick(); foto("3_lista")
        rule.onAllNodesWithText("PENDIENTES")[0].performClick(); rule.waitForIdle(); foto("3b_pendientes")
        rule.onNodeWithText("Crónicas de Dune", useUnmergedTree = true).performClick(); foto("3c_saga_abierta")
        rule.onNodeWithText("VER LA SAGA →", useUnmergedTree = true).performClick(); foto("3d_pagina_saga")
    }

    @Test fun otrasSecciones() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("LEÍDOS").performClick(); rule.waitForIdle(); foto("3e_leidos")
        rule.onAllNodesWithText("ABANDONADOS")[0].performScrollTo().performClick(); rule.waitForIdle(); foto("3f_abandonados")
        rule.onAllNodesWithText("AUTORES")[0].performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithText("Frank Herbert", useUnmergedTree = true).performClick(); foto("3g_autores")
        rule.onAllNodesWithText("SAGAS")[0].performScrollTo().performClick(); rule.waitForIdle(); foto("3h_sagas")
        rule.onAllNodesWithText("Crónicas de Dune", useUnmergedTree = true).onLast().performClick(); rule.waitForIdle(); foto("3i_sagas_abierta")
    }

    @Test fun vacia() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Skin.cargar(ctx); Biblioteca.cargada = true; Biblioteca.libros.clear(); montar(false); foto("5_portada_vacia")
    }

    @Test fun fichaCompleta() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("LEYENDO").performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle()
        // (en Leyendo solo está «Dune»: sale como libro suelto, con su saga al lado)
        rule.onAllNodesWithText("Dune", useUnmergedTree = true)[0].performClick(); foto("4_ficha")
        rule.onNodeWithText("Añadido", useUnmergedTree = true).performScrollTo(); foto("4b_ficha_abajo")
    }

    /** La ficha de un pendiente (la de una portada generada, recortada) y la de Dune de noche. */
    @Test fun fichaPendienteYNoche() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onAllNodesWithText("PENDIENTES")[0].performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle()
        rule.onAllNodesWithText("La sombra del viento", useUnmergedTree = true)[0].performClick(); foto("4f_ficha_pendiente")
    }

    @Test fun fichaOscuro() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(true)
        rule.onNodeWithText("LEYENDO").performClick(); rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle()
        rule.onAllNodesWithText("Dune", useUnmergedTree = true)[0].performClick(); foto("4g_ficha_oscuro")
    }

    @Test fun fichaSinArchivoNiPortada() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        Biblioteca.libros[5] = Biblioteca.libros[5].copy(uri = "")
        rule.onNodeWithText("PENDIENTES").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Los pilares de la Tierra", useUnmergedTree = true)[0].performClick(); foto("4c_ficha_sin_archivo")
    }

    @Test fun tapas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Skin.cargar(ctx)
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) {
            androidx.compose.foundation.layout.Column(Modifier.padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    listOf("Los pilares de la Tierra" to "Ken Follett", "La sombra del viento" to "Carlos Ruiz Zafón", "Cien años de soledad" to "Gabriel García Márquez").forEach { (t, a) ->
                        PortadaLibro(Libro("x", t, a), Modifier.width(104.dp).aspectRatio(0.68f))
                    }
                }
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    listOf("Dune", "1984", "Ulises", "Fahrenheit 451", "Don Quijote de la Mancha", "El Principito").forEach { t -> PortadaLibro(Libro("x", t), Modifier.width(50.dp).aspectRatio(0.68f)) }
                }
                PortadaLibro(Libro("x", "El nombre de la rosa", "Umberto Eco"), Modifier.width(210.dp).aspectRatio(0.68f))
            }
        } } }
        foto("6_tapas")
    }

    @Test fun selectorDePortada() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); Skin.cargar(ctx)
        val c = listOf("Rosa" to 0xFF8C2A22.toInt(), "Dune" to 0xFFD9A53A.toInt(), "Cien" to 0xFF2F5D8A.toInt(), "F451" to 0xFFE0603A.toInt(), "Otra" to 0xFF35523F.toInt(), "Mas" to 0xFF5B3F6B.toInt())
            .mapIndexed { i, (n, col) -> portada(n, col, col and 0x7f7f7f).let { Candidata(it, it, if (i % 2 == 0) "iTunes" else "Open Library", "El nombre de la rosa · Umberto Eco") } }
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { ContenidoSelectorPortada(Biblioteca.libros.first(), {}, hallados = c) } } }
        foto("7_selector_portada")
    }

    @Test fun ajustes() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithContentDescription("Ajustes").performClick(); rule.waitForIdle(); foto("8_ajustes")
        rule.onNodeWithText("LECTURA").performClick(); rule.waitForIdle(); foto("8b_ajustes_lectura")
        rule.onNodeWithText("CUENTA").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Tener todos mis libros aquí", useUnmergedTree = true).performScrollTo(); foto("8c_ajustes_cuenta")
        rule.onNodeWithText("AVANZADO").performClick(); rule.waitForIdle(); foto("8d_ajustes_avanzado")
    }

    @Test fun escritos() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        val lectura = org.json.JSONObject().put("novela", org.json.JSONObject()
            .put("c1", org.json.JSONObject().put("t", "La casa del acantilado").put("a", "Gonzalo").put("c", "El regreso").put("n", 1).put("pa", "Primera parte").put("p", 2140).put("x", "Era de noche.").put("u", System.currentTimeMillis() - 86_400_000L))
            .put("c2", org.json.JSONObject().put("t", "La casa del acantilado").put("a", "Gonzalo").put("c", "La marea").put("n", 2).put("pa", "").put("p", 1870).put("x", "Subía *despacio*.").put("u", System.currentTimeMillis() - 3_600_000L)))
            .put("relatos", org.json.JSONObject().put("c1", org.json.JSONObject().put("t", "Relatos de invierno").put("a", "Gonzalo").put("c", "Nieve").put("n", 1).put("pa", "").put("p", 950).put("x", "Nevaba.").put("u", System.currentTimeMillis() - 20 * 86_400_000L)))
        ctx.getSharedPreferences("marfil_escritos", Context.MODE_PRIVATE).edit().putString("lectura", lectura.toString()).putLong("ultima", System.currentTimeMillis() - 600_000L).commit()
        montar(false)
        rule.onNodeWithText("ESCRITOS").performScrollTo().performClick(); rule.waitForIdle(); foto("9_escritos")
    }

    @Test fun fichaDeUnLeido() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("LEÍDOS").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Fahrenheit 451", useUnmergedTree = true)[0].performClick(); foto("4d_ficha_leido")
        rule.onNodeWithText("Añadido", useUnmergedTree = true).performScrollTo(); foto("4e_ficha_leido_abajo")
    }
}
