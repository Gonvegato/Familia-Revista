package com.gonzalo.cobalto.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.gonzalo.cobalto.Escritos
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import com.gonzalo.familia.pc.TextoAireado
import com.gonzalo.familia.pc.revista.Paper
import com.gonzalo.familia.pc.revista.TemaFamilia
import org.jetbrains.skia.EncodedImageFormat
import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import java.io.File

/** Capturas de Cobalto del ordenador con una obra de ejemplo. Solo con `-Dcapturas=<carpeta>`. */
class CapturasTest {
    @Test fun capturas() {
        val dir = System.getProperty("capturas")?.let { File(it) }
        Assume.assumeTrue("Solo con -Dcapturas=<carpeta>", dir != null)
        dir!!.mkdirs()
        Emulador.limpia(); Emulador.cuenta("gonzalo")
        val tmp = File(System.getProperty("java.io.tmpdir"), "cobalto-capturas-" + System.nanoTime())
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg
        LaFamilia.dirPruebas = tmp
        LaFamilia.llaveroPruebas = LlaveroMemoria()
        LaFamilia.inicia("cobalto").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Cuaderno.reiniciaParaPruebas(); Cuaderno.dirPruebas = tmp; Cuaderno.dispositivo = { "cobalto-pc1" }
        val e = Cuaderno.escritos
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "La casa del lago"); put("autor", "Gonzalo"); put("genero", "Novela"); put("meta", 80000); put("diaria", 800) }
        val p1 = e.nuevo(Escritos.T_PARTE, obra.id) { put("titulo", "Invierno") }
        val c1 = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "La llegada"); put("aid", p1.id); put("hecho", 1) }
        val c2 = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "El cuarto cerrado"); put("aid", p1.id) }
        e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", c1.id); put("titulo", "El coche en la cuneta"); put("texto", "# La llegada\n\nLlovía sobre el lago desde hacía tres días, y *Marta* ya no recordaba el color del cielo.\n\n> Las casas viejas guardan el frío como guardan los secretos.\n\nBajó del coche y miró la fachada: **nadie** había vuelto a pintarla desde el verano en que se fue.\n\n***\n\nDentro olía a madera mojada y a algo más, algo dulce que no supo nombrar.") }
        val s2 = e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", c2.id); put("titulo", "La llave"); put("texto", "La llave no estaba donde la abuela la dejaba siempre, bajo la maceta del porche.\n\nMarta buscó en los bolsillos del abrigo, en la guantera, en el bolso. Nada.\n\n—Alguien ha estado aquí —dijo en voz alta, y la casa no le contestó.") }
        e.nuevo(Escritos.T_FICHA, obra.id) { put("tipo", "personaje"); put("nombre", "Marta"); put("resumen", "Vuelve a la casa del lago después de veinte años") }
        e.nuevo(Escritos.T_NOTA, obra.id) { put("tipo", "idea"); put("titulo", "El olor dulce"); put("texto", "Es el perfume de la abuela: lo descubre en el capítulo 9.") }
        val hoy = Escritos.hoy()
        listOf(420, 0, 960, 1210, 640, 300, 880, 1500, 0, 720, 1020, 560, 830, 760).forEachIndexed { i, n ->
            if (n > 0) e.pon(JSONObject().put("id", "l_${obra.id}_${hoy - 13 + i}").put("t", Escritos.T_LOG).put("pid", obra.id).put("dia", hoy - 13 + i).put("dev", JSONObject().put("cobalto-pc1", n)))
        }
        val otra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Cuentos del faro"); put("est", "pausa") }
        e.nuevo(Escritos.T_CAP, otra.id) { put("titulo", "El farero") }
        fun captura(nombre: String, p: Pantalla, oscuro: Boolean = false) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            val escena = ImageComposeScene(1440, 900, Density(1f)) { TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { AppCobalto(p) } } } }
            var img = escena.render(0)
            repeat(5) { i -> Thread.sleep(120); img = escena.render((i + 1) * 500_000_000L) }
            File(dir, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        captura("cobalto-portada", Pantalla.Portada)
        captura("cobalto-obra", Pantalla.Obra(obra.id, s2.id))
        captura("cobalto-obra-oscuro", Pantalla.Obra(obra.id, s2.id), oscuro = true)
        captura("cobalto-papelera", Pantalla.Papelera)
        Cuaderno.reiniciaParaPruebas(); LaFamilia.reiniciaParaPruebas()
    }
}
