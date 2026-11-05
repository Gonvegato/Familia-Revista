package com.gonzalo.hiedra.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import com.gonzalo.familia.pc.TextoAireado
import com.gonzalo.familia.pc.revista.Paper
import com.gonzalo.familia.pc.revista.TemaFamilia
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume
import org.junit.Test
import java.io.File

/** Capturas de Hiedra del ordenador. Solo con `-Dcapturas=<carpeta>`. */
class CapturasTest {
    @Test fun capturas() {
        val dir = System.getProperty("capturas")?.let { File(it) }
        Assume.assumeTrue("Solo con -Dcapturas=<carpeta>", dir != null)
        dir!!.mkdirs()
        Emulador.limpia(); Emulador.cuenta("gonzalo")
        val otra = Emulador.dispositivo("gonzalo")
        val hoy = java.time.LocalDate.now()
        fun juego(id: String, t: String, pl: String, estado: String, ms: Long = 0) {
            otra.coleccion("juegos").pon(id, mapOf("t" to t, "pl" to pl, "estado" to estado, "an" to System.currentTimeMillis(), "ul" to System.currentTimeMillis(), "menu" to if (pl == "PC") "pc" else "emulacion"))
            if (ms > 0) otra.coleccion("tiempo").pon("$id~hiedra-movil", mapOf("ref" to id, "fuente" to "hiedra-movil", "total" to ms, "veces" to 3L, "d$hoy" to ms / 3))
        }
        juego("j_000000000001", "Hades", "PC", "en_curso", 31 * 3600_000L)
        juego("j_000000000002", "Hollow Knight", "Switch", "en_curso", 12 * 3600_000L)
        juego("j_000000000003", "The Legend of Zelda: The Minish Cap", "GBA", "en_curso", 5 * 3600_000L)
        juego("j_000000000004", "Celeste", "PC", "pendiente")
        juego("j_000000000005", "Outer Wilds", "PC", "terminado", 22 * 3600_000L)
        check(otra.sincroniza().ok)
        val tmp = File(System.getProperty("java.io.tmpdir"), "hiedra-capturas-" + System.nanoTime()).also { it.mkdirs() }
        LaFamilia.reiniciaParaPruebas(); LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg; LaFamilia.dirPruebas = tmp; LaFamilia.llaveroPruebas = LlaveroMemoria()
        val aqui = LaFamilia.inicia("hiedra").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Juegos.dispositivo = { "hiedra-linux-pc1" }; Pc.dirPruebas = tmp
        Pc.steamPruebas = emptyList()
        listOf("juegos", "tiempo", "ajustes").forEach { LaFamilia.coleccion(it) }
        check(aqui.sincroniza().ok)
        fun captura(nombre: String, p: Pantalla, oscuro: Boolean = false) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            val escena = ImageComposeScene(1440, 900, Density(1f)) { TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { AppHiedra(p) } } } }
            var img = escena.render(0)
            repeat(5) { i -> Thread.sleep(120); img = escena.render((i + 1) * 500_000_000L) }
            File(dir, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        captura("hiedra-portada", Pantalla.Portada)
        captura("hiedra-ficha", Pantalla.Ficha("j_000000000001", Pantalla.Portada), oscuro = true)
        LaFamilia.reiniciaParaPruebas()
    }
}
