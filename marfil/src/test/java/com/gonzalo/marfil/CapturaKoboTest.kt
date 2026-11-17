package com.gonzalo.marfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** SesKobo: la sección «Kobo» de Ajustes → Lectura (solo con -Pseskobo -Proborazzi.test.record=true). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturaKoboTest {
    @get:Rule val rule = createComposeRule()

    @Test fun seccionKobo() {
        assumeTrue(BuildConfig.SESKOBO && System.getProperty("roborazzi.test.record") == "true")
        Skin.aColoresDeSerie()
        rule.setContent { MarfilTheme { androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = Paper) { Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Antetitulo("Kobo", Modifier.padding(bottom = 4.dp)); SeccionKobo() } } } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/capturas/seskobo-ajustes-kobo.png")
    }
}
