package com.gonzalo.shai

import android.content.Context
import androidx.compose.foundation.layout.Box
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

/** Capturas del hecho histórico (solo con -Proborazzi.test.record=true): el menú del +, el formulario con lo entendido y la edición especial. */
abstract class CapturasHechoBase {
    abstract val tema: String
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val dir: String get() = System.getProperty("capturas")?.takeIf { it.isNotBlank() } ?: "build/outputs/roborazzi"
    private fun foto(nombre: String) { rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle(); com.github.takahirom.roborazzi.captureScreenRoboImage("$dir/fcfeed-$tema-$nombre.png") }

    @Test fun hecho() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Skin.cargar(ctx)
        Ejemplo.prepara()
        Motor.claude = Claude({ ClaveApi.lee() }, TransporteDePrueba(kotlin.random.Random(3)))
        val (u0, _) = Motor.crea(Ejemplo.universo().copy(fuera = Fuera.INICIAL + (Fuera.FAMA to 8)))
        val cuenta = "Nada más empezar la carrera me convocan con la selección para el Mundial por la lesión de los dos delanteros titulares. " +
            "Soy titular en octavos sin haber debutado como profesional, por algo que el seleccionador ha visto en mí."
        // Lo que entendería Haiku (como en la maqueta)
        val ent = Narrador.LoEntendido(listOf("Convocado con la selección para el Mundial" to "Sin haber debutado con su club", "Por la lesión de los dos delanteros titulares" to "",
            "Titular en octavos de final" to "Se juega en el próximo partido que metas"), true, "Titular en un Mundial antes de debutar con su club",
            "¿Algún futbolista ha sido titular en un Mundial sin haber debutado como profesional con su club?")
        var cual by mutableIntStateOf(0)
        rule.setContent {
            FcTheme {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    key(cual) {
                        when (cual) {
                            0 -> Box(Modifier.fillMaxSize()) { PantCarrera(u0.id, {}, {}, {}, inicial = 1); MenuMas({}, {}, {}) }
                            1 -> PantHecho(Carreras.de(u0.id)!!, {}, {}, texto0 = cuenta)
                            2 -> PantHecho(Carreras.de(u0.id)!!, {}, {}, texto0 = cuenta, entendido0 = ent)
                            3 -> PantCarrera(u0.id, {}, {}, {}, inicial = 1)
                            5 -> PantNueva({}, {})
                            6 -> PantPartido(u0.id, {}, {}, comoInicial = 0)
                            4 -> PaginaRevista { Carreras.entradasDe(u0.id).first { it.tipo == TipoEntrada.HISTORICO }.let { EdicionEspecial(Carreras.de(u0.id)!!, it, abierta0 = true, {}, {}) } }
                        }
                    }
                }
            }
        }
        cual = 0; foto("h1-menu-mas")
        cual = 1; foto("h2-hecho-escribir")
        cual = 2; foto("h3-hecho-entendido")
        Motor.hecho(u0.id, cuenta, ent, Ruido.MUNDO)
        cual = 3; foto("h4-inicio-edicion-especial")
        cual = 4; foto("h5-edicion-abierta")
        cual = 5; foto("s1-nueva-carrera")
        Carreras.guarda(Carreras.de(u0.id)!!.let { it.copy(personaje = it.personaje.copy(nacionalidad = "España", altura = 184, peso = 77, pie = "Zurdo")) })
        Motor.partido(u0.id, Partido("2026/27", "Mundial 2026", "Alemania", true, 2, 1, 90, 1, 0, 8.4, fecha = "2026-07-04"))
        cual = 6; foto("s2-partido-seleccion")
        cual = 3; foto("s3-inicio-tras-mundial")
        ClaveApi.fija = null; Gasto.mesFijo = null
    }
}

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w411dp-h2000dp-xhdpi")
class CapturasHechoNormalTest : CapturasHechoBase() { override val tema = "normal" }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w360dp-h2000dp-xhdpi", fontScale = 1.3f)
class CapturasHechoLetraGrandeTest : CapturasHechoBase() { override val tema = "letra-grande" }
