package com.gonzalo.ptah

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de las pantallas de Ptah (solo con -Proborazzi.test.record=true): la portada (con un diseño de cada tipo), «+ Nuevo», el editor de
 * cada tipo (con su vista previa), las pestañas del editor, el selector de color y los ajustes; en claro, en oscuro y con la letra grande
 * (130 %). En un móvil alto (411 × 1400 dp), para ver cada pantalla entera. Se dejan en -Dcapturas (o en build/outputs/roborazzi) como
 * `ptah-<tema>-<pantalla>.png`.
 */
abstract class CapturasBase {
    abstract val tema: String
    abstract val oscuro: Boolean
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val dir: String get() = System.getProperty("capturas")?.takeIf { it.isNotBlank() } ?: "build/outputs/roborazzi"

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$dir/ptah-$tema-$nombre.png")
    }

    @Test fun pantallas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        iniciar(ctx)
        Widgets.datos = { DatosDeMentira(it) }
        CacheTiempo.red = { throw java.io.IOException("Sin red en las pruebas") }
        val disenos = DatosDeMentira.disenos()
        Disenos.fijos = disenos
        listOf("#C8372D", "#12393A", "#D98A3D", "#2F5D8A", "#80F2E6CF").forEach { Recientes.pon(ctx, it) }
        Modo.eleccion = if (oscuro) 2 else 1
        var cual by mutableIntStateOf(0)
        val porTipo = Tipos.TODOS.map { t -> disenos.firstOrNull { it.tipo == t.id } ?: t.nuevo() }
        val accesos = Accesos.nuevo().copy(nombre = "Mis apps").con("items", listOf("app:com.android.chrome", "app:com.whatsapp", "web:elpais.com|El País"))
        rule.setContent {
            PtahTheme {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    when (cual) {
                        0 -> PantPortada(false, {}, {}, {}, {})
                        1 -> PantNuevo({}, {})
                        in 100..199 -> androidx.compose.runtime.key(cual) { PantEditor(porTipo[cual - 100], false, alVolver = {}, alGuardar = {}) }
                        in 200..204 -> androidx.compose.runtime.key(cual) { PantEditor(porTipo[0], false, alVolver = {}, alGuardar = {}, pestanaInicial = cual - 200) }
                        210 -> PantEditor(accesos, false, alVolver = {}, alGuardar = {})
                        211 -> androidx.compose.runtime.key(cual) { PantEditor(porTipo[1], false, alVolver = {}, alGuardar = {}, pestanaInicial = PESTANA_COLORES) }
                        3 -> Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                            ContenidoSelector("El acento", Col.ACENTO, Skin.tones.general.toInt(), true, "El de serie", {}, {})
                        }
                        4 -> Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                            ContenidoSelector("La aguja de las horas", "#12393A", 0xFF12393A.toInt(), true, "Con la tinta o el acento", {}, {})
                        }
                        in 300..302 -> androidx.compose.runtime.key(cual) { PantAjustes(alVolver = {}, inicial = cual - 300) }
                        5 -> PtahApp(eligiendo = true)
                    }
                }
            }
        }
        foto("01-portada")
        cual = 1; foto("02-nuevo")
        Tipos.TODOS.forEachIndexed { i, t -> cual = 100 + i; foto("1${"%02d".format(i)}-editor-${t.id}") }
        cual = 210; foto("120-editor-accesos-con-apps")
        listOf("contenido", "colores", "forma", "letras", "partes").forEachIndexed { i, n -> cual = 200 + i; foto("2${i}-editor-pestana-$n") }
        cual = 211; foto("25-editor-agujas-colores")
        cual = 3; foto("30-selector-color")
        cual = 4; foto("31-selector-color-propio")
        cual = 300; foto("40-ajustes-apariencia")
        cual = 301; foto("41-ajustes-cuenta")
        cual = 302; foto("42-ajustes-avanzado")
        cual = 5; foto("50-elegir-para-un-widget")
        Widgets.datos = null; Disenos.fijos = null
    }
}

private const val ALTO = "w411dp-h1400dp-xhdpi"

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = ALTO)
class CapturasClaroTest : CapturasBase() { override val tema = "claro"; override val oscuro = false }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = ALTO)
class CapturasOscuroTest : CapturasBase() { override val tema = "oscuro"; override val oscuro = true }

/** Con la letra grande (el tamaño de texto del móvil al 130 %): nada cortado. */
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = ALTO, fontScale = 1.3f)
class CapturasLetraGrandeTest : CapturasBase() { override val tema = "letra-grande"; override val oscuro = false }
