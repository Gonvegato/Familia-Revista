package com.gonzalo.familia.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.gonzalo.mau.Borde
import com.gonzalo.mau.GuardadoEnMemoria
import com.gonzalo.mau.Lugar
import com.gonzalo.mau.ui.Mau
import com.gonzalo.mau.ui.MauCapa
import com.gonzalo.mau.ui.MauCasa
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mau se pinta y responde: tocarlo abre el menú; escondido, solo las orejas. Con `-Dcapturas=<carpeta>` guarda las capturas (móvil
 * y ordenador) para mirarlas.
 */
@OptIn(ExperimentalComposeUiApi::class)
class MauCapturasTest {
    private object Casa : MauCasa {
        override val app = "marfil"
        var pc = false
        override val ordenador get() = pc
        override val guardado = GuardadoEnMemoria()
        override val sinAnimaciones = false
        val abiertas = ArrayList<String>()
        override fun instalada(app: String) = app != "cobre"
        override fun abre(app: String): Boolean { abiertas += app; return true }
        override fun ronronea() {}
        override fun tic() {}
        override fun enPrincipal(f: () -> Unit) = f()
    }

    private val dir = System.getProperty("capturas")?.let { File(it).also { d -> d.mkdirs() } }

    private fun escena(nombre: String, ancho: Int, alto: Int, densidad: Float, clave: String, fondo: Color = Color(0xFFF8F0D4), acciones: (ImageComposeScene, Density) -> Unit = { _, _ -> }) {
        val d = Density(densidad)
        val e = ImageComposeScene(ancho, alto, d) { Box(Modifier.fillMaxSize().background(fondo)) { MauCapa(clave) } }
        var t = 0L
        fun frames(n: Int) { repeat(n) { Thread.sleep(16); t += 16_000_000L; e.render(t) } }
        frames(5)
        acciones(e, d)
        frames(40)
        val png = e.render(t + 1).encodeToData(EncodedImageFormat.PNG)!!.bytes
        dir?.let { File(it, "$nombre.png").writeBytes(png) }
        e.close()
    }

    /** Toca a Mau (sentado abajo a la derecha, tamaño normal: 84 dp en el móvil, 104 en el ordenador). */
    private fun tocaMau(e: ImageComposeScene, d: Density, ancho: Int, alto: Int) {
        val w = (if (Casa.pc) 104f else 84f) * d.density
        val p = Offset(ancho - w / 2, alto - w * 1.1f / 2)
        e.sendPointerEvent(PointerEventType.Press, p)
        e.sendPointerEvent(PointerEventType.Release, p)
        // (espera a que pase el tiempo del doble toque)
        repeat(25) { Thread.sleep(16); e.render(System.nanoTime()) }
    }

    @Test fun mau() {
        Mau.inicia(Casa)
        val c = Mau.cerebro
        Mau.base("movil", Lugar.LEYENDO)
        // móvil: 390 × 844 a densidad 2
        escena("mau-movil-leyendo", 780, 1688, 2f, "movil") { _, _ -> c.di("Te quedan 3 páginas para acabar el capítulo. ¿Seguimos?", Mau.ahora(), espontaneo = false) }
        c.callaBocadillo()
        escena("mau-movil-menu", 780, 1688, 2f, "movil") { e, d -> tocaMau(e, d, 780, 1688) }
        // (el menú abre las apps: tocar Thot la abre)
        c.esconde(); c.ponBorde(Borde.DERECHA, .4f)
        escena("mau-movil-escondido-derecha", 780, 1688, 2f, "movil")
        c.ponBorde(Borde.ABAJO, .8f)
        escena("mau-movil-escondido-abajo", 780, 1688, 2f, "movil")
        c.vuelve(Mau.ahora())
        assertTrue(!c.escondido)
        // ordenador
        Casa.pc = true
        Mau.base("pc", Lugar.CASA)
        escena("mau-pc-menu", 1280, 800, 1f, "pc", Color(0xFFF3F0E6)) { e, d -> tocaMau(e, d, 1280, 800) }
        c.pon(com.gonzalo.mau.Pose.CELEBRANDO, 60_000, Mau.ahora())
        escena("mau-pc-celebrando", 1280, 800, 1f, "pc", Color(0xFF140E09))
        Casa.pc = false
        assertEquals(emptyList<String>(), Casa.abiertas)
    }
}
