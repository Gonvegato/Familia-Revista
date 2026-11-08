package com.gonzalo.shai

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de las pantallas de Shai (solo con -Proborazzi.test.record=true), con una carrera de ejemplo montada con la API de mentira: tus
 * carreras, cada pestaña de una carrera, meter un partido (rápido y completo), empezar una carrera y los ajustes; con la letra normal y con
 * la grande (130 %). En un móvil alto (411 × 2000 dp), para ver cada pantalla entera. En -Dcapturas (o build/outputs/roborazzi): `shai-<tema>-<pantalla>.png`.
 */
abstract class CapturasBase {
    abstract val tema: String
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val dir: String get() = System.getProperty("capturas")?.takeIf { it.isNotBlank() } ?: "build/outputs/roborazzi"

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$dir/shai-$tema-$nombre.png")
    }

    @Test fun pantallas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Cuenta.cargar(ctx); Skin.cargar(ctx)
        val api = Ejemplo.prepara()
        Cuenta.dentroFijo = true
        val u = Ejemplo.completa(api)
        Gasto.apunta(Claude.SONNET, 3.42); Gasto.apunta(Claude.HAIKU, 0.37)
        // (otra carrera, de míster, para la portada)
        Carreras.guarda(Universo(Universo.nuevoId(), Rol.MISTER, Personaje("Marcelo Ruiz", edad = 44, nacionalidad = "Argentina"), "Real Valladolid CF", "LaLiga Hypermotion", "2026/27", 9_000, dinero = 1_200_000))
        var cual by mutableIntStateOf(0)
        var otra by androidx.compose.runtime.mutableStateOf(u.id)
        rule.setContent {
            ShaiTheme {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    key(cual) {
                        when (cual) {
                            0 -> PantPortada({}, {}, {})
                            in 10..16 -> PantCarrera(otra, {}, {}, {}, inicial = cual - 10)
                            20 -> PantPartido(u.id, {}, {})
                            21 -> PantPartido(u.id, {}, {}, comoInicial = 1)
                            30 -> PantNueva({}, {})
                            40 -> PantAjustes({}, inicial = 2)
                            41 -> PantAjustes({}, inicial = 0)
                        }
                    }
                }
            }
        }
        foto("01-portada")
        PESTANAS.forEachIndexed { i, n -> cual = 10 + i; foto("1$i-carrera-${n.lowercase()}") }
        cual = 20; foto("20-partido-rapido")
        cual = 21; foto("21-partido-completo")
        cual = 30; foto("30-nueva-carrera")
        cual = 40; foto("40-ajustes-ia")
        cual = 41; foto("41-ajustes-apariencia")
        // El modo prueba (sin IA): una carrera nueva con sus plantillas de ejemplo
        Prueba.pon(ctx, true)
        val (p, _) = Motor.crea(Ejemplo.universo())
        Motor.partido(p.id, Ejemplo.partido(1, 0, "Valencia CF")); Motor.partido(p.id, Ejemplo.partido(0, 2, "Girona FC"))
        cual = 0; foto("50-prueba-portada")
        otra = p.id
        cual = 10; foto("51-prueba-carrera")
        cual = 11; foto("52-prueba-red")
        cual = 12; foto("53-prueba-mensajes")
        cual = 40; foto("54-prueba-ajustes-ia")
        Prueba.vacia(); Prueba.pon(ctx, false)
        Cuenta.dentroFijo = null; ClaveApi.fija = null; Gasto.mesFijo = null
    }
}

private const val ALTO = "w411dp-h2000dp-xhdpi"

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = ALTO)
class CapturasNormalTest : CapturasBase() { override val tema = "normal" }

/** Con la letra grande (el tamaño de texto del móvil al 130 %): nada cortado. */
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = ALTO, fontScale = 1.3f)
class CapturasLetraGrandeTest : CapturasBase() { override val tema = "letra-grande" }
