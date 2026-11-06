@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.gonzalo.cobalto.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.SprintEscribir
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import com.gonzalo.familia.pc.TextoAireado
import com.gonzalo.familia.pc.revista.Paper
import com.gonzalo.familia.pc.revista.TemaFamilia
import org.jetbrains.skia.EncodedImageFormat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import java.io.File

/** Capturas de Cobalto del ordenador con una obra de ejemplo (y algún clic de verdad). Solo con `-Dcapturas=<carpeta>`. */
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
        PrefsEditor.reiniciaParaPruebas(); Estudio.reiniciaParaPruebas(); SprintEscribir.reiniciaParaPruebas()
        val e = Cuaderno.escritos
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "La casa del lago"); put("autor", "Gonzalo"); put("genero", "Novela"); put("meta", 80000); put("diaria", 800); put("color", "#4A6FA5")
            put("gOn", 1); put("idea", "Una mujer vuelve a la casa de su infancia para venderla y descubre que alguien ha vivido allí todos estos años.") }
        val p1 = e.nuevo(Escritos.T_PARTE, obra.id) { put("titulo", "Invierno") }
        val c1 = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "La llegada"); put("aid", p1.id); put("hecho", 1); put("resumen", "Marta llega a la casa.") }
        val c2 = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "El cuarto cerrado"); put("aid", p1.id) }
        val s1 = e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", c1.id); put("titulo", "El coche en la cuneta"); put("estado", 2); put("cuando", "Día 1, noche"); put("texto", "# La llegada\n\nLlovía sobre el lago desde hacía tres días, y *Marta* ya no recordaba el color del cielo.\n\n> Las casas viejas guardan el frío como guardan los secretos.\n\nBajó del coche y miró la fachada: **nadie** había vuelto a pintarla desde el verano en que se fue.\n\n***\n\nDentro olía a madera mojada y a algo más, algo dulce que no supo nombrar. Lucas la esperaba en la cocina, con la llave en la mano.") }
        val s2 = e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", c2.id); put("titulo", "La llave"); put("estado", 1); put("meta", 1200); put("resumen", "La llave no está; alguien ha entrado."); put("texto", "La llave no estaba donde la abuela la dejaba siempre, bajo la maceta del porche.\n\nMarta buscó en los bolsillos del abrigo, en la guantera, en el bolso. Nada.\n\n—Alguien ha estado aquí —dijo en voz alta, y la casa no le contestó.\n\nSubió la escalera despacio. En el rellano, la puerta del cuarto cerrado tenía la cerradura nueva, brillante, como si alguien la hubiera cambiado ayer mismo. Lucas no sabía nada; o eso dijo.") }
        val marta = e.nuevo(Escritos.T_FICHA, obra.id) { put("tipo", "personaje"); put("nombre", "Marta"); put("alias", "Marti"); put("color", "#B0102F"); put("resumen", "Vuelve a la casa del lago después de veinte años, para venderla.")
            put("campos", JSONArray().put(JSONObject().put("k", "Edad").put("v", "42")).put(JSONObject().put("k", "Oficio").put("v", "Restauradora de muebles"))
                .put(JSONObject().put("k", "Quiere").put("v", "Vender la casa y no volver nunca")).put(JSONObject().put("k", "Teme").put("v", "Lo que pasó el verano en que se fue, y que nadie le ha contado entero")))
            put("texto", "## De dónde viene\n\nCreció en la casa del lago con su abuela, hasta los veintidós. Se fue a Madrid **sin despedirse** de nadie y no ha vuelto desde entonces.\n\n> «Si vuelvo, me quedo», le dijo a su hermano por teléfono, y no volvió.\n\nEs meticulosa, desconfiada y tiene un humor seco que la salva casi siempre. Restaura muebles viejos: sabe ver lo que hay debajo de la pintura.\n\n- Fuma cuando está nerviosa, aunque lo dejó hace años.\n- Lleva siempre el reloj de su abuelo.") }
        val lucas = e.nuevo(Escritos.T_FICHA, obra.id) { put("tipo", "personaje"); put("nombre", "Lucas"); put("color", "#3E8E9E"); put("resumen", "El hermano que se quedó."); put("ax", 260); put("ay", -40) }
        val casa = e.nuevo(Escritos.T_FICHA, obra.id) { put("tipo", "lugar"); put("nombre", "La casa del lago"); put("color", "#5B8C5A"); put("resumen", "Dos plantas, un porche y el cuarto cerrado."); put("ax", 10); put("ay", 190) }
        val llave = e.nuevo(Escritos.T_FICHA, obra.id) { put("tipo", "objeto"); put("nombre", "La llave"); put("color", "#C9A227"); put("ax", 300); put("ay", 200) }
        e.edita(marta.id) { put("ax", -250); put("ay", -30) }
        e.nuevo(Escritos.T_REL, obra.id) { put("a", marta.id); put("b", lucas.id); put("titulo", "hermanos"); put("texto", "Se quieren y no se perdonan.") }
        e.nuevo(Escritos.T_REL, obra.id) { put("a", marta.id); put("b", casa.id); put("titulo", "hogar"); put("flecha", 1); put("color", "#5B8C5A") }
        e.nuevo(Escritos.T_REL, obra.id) { put("a", lucas.id); put("b", llave.id); put("titulo", "la esconde"); put("flecha", 1); put("color", "#B0102F") }
        e.nuevo(Escritos.T_NOTA, obra.id) { put("tipo", "idea"); put("titulo", "El olor dulce"); put("color", "#C9A227"); put("fija", 1); put("texto", "Es el perfume de la abuela: lo descubre en el capítulo 9.\n\nTiene que salir **tres veces** antes, sin que se note.") }
        e.nuevo(Escritos.T_NOTA, obra.id) { put("tipo", "investigacion"); put("titulo", "Cerraduras de los años 80"); put("texto", "Las de cilindro europeo llegan a España a finales de los 80. Una cerradura nueva brilla: latón sin oxidar.") }
        e.nuevo(Escritos.T_HITO, obra.id) { put("titulo", "Marta se va de casa"); put("cuando", "Verano de 1998"); put("nota", "Sin despedirse.") }
        val h2 = e.nuevo(Escritos.T_HITO, obra.id) { put("titulo", "Muere la abuela"); put("cuando", "Marzo de 2018"); put("eid", s1.id) }
        val rama = e.nuevo(Escritos.T_RAMA, obra.id) { put("titulo", "Lo que hizo Lucas"); put("color", "#7B5EA7"); put("desde", h2.orden); put("texto", "Mientras Marta no estaba.") }
        e.nuevo(Escritos.T_HITO, obra.id) { put("titulo", "Lucas cambia la cerradura"); put("cuando", "Otoño de 2018"); put("rama", rama.id); put("orden", h2.orden + 500); put("nota", "Nadie lo sabe.") }
        e.nuevo(Escritos.T_HITO, obra.id) { put("titulo", "Marta vuelve"); put("cuando", "Día 1"); put("eid", s2.id) }
        val hoy = Escritos.hoy()
        listOf(420, 0, 960, 1210, 640, 300, 880, 1500, 0, 720, 1020, 560, 830, 760).forEachIndexed { i, n ->
            if (n > 0) e.pon(JSONObject().put("id", "l_${obra.id}_${hoy - 13 + i}").put("t", Escritos.T_LOG).put("pid", obra.id).put("dia", hoy - 13 + i).put("dev", JSONObject().put("cobalto-pc1", n)))
        }
        SprintEscribir.cuenta = CuentaSprintsPC
        SprintEscribir.migra(JSONArray().put(JSONObject().put("pid", obra.id).put("desde", System.currentTimeMillis() - 86_400_000L).put("min", 25).put("pal", 640).put("meta", 500))
            .put(JSONObject().put("pid", obra.id).put("desde", System.currentTimeMillis() - 3 * 86_400_000L).put("min", 15).put("pal", 310).put("meta", 0)), null)
        val otra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Cuentos del faro"); put("est", "pausa") }
        e.nuevo(Escritos.T_CAP, otra.id) { put("titulo", "El farero") }

        fun captura(nombre: String, p: Pantalla, oscuro: Boolean = false, antes: () -> Unit = {}, gestos: (ImageComposeScene) -> Unit = {}) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            antes()
            val escena = ImageComposeScene(1440, 900, Density(1f)) { TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { AppCobalto(p) } } } }
            var t = 0L
            fun pinta(n: Int = 5) { repeat(n) { Thread.sleep(120); t += 500_000_000L; escena.render(t) } }
            escena.render(0); pinta()
            gestos(escena)
            if (escena.hasInvalidations()) pinta(3)
            val img = escena.render(t + 1_000_000_000L)
            File(dir, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
            Editor.guardaYa(); PrefsEditor.reiniciaParaPruebas(); Estudio.reiniciaParaPruebas()
        }
        var reloj = 10_000_000_000L
        fun ImageComposeScene.espera(n: Int = 3) { repeat(n) { Thread.sleep(150); reloj += 200_000_000L; render(reloj) } }
        fun ImageComposeScene.clic(x: Float, y: Float, derecho: Boolean = false) {
            sendPointerEvent(PointerEventType.Move, Offset(x, y))
            val b = if (derecho) PointerButtons(isSecondaryPressed = true) else PointerButtons(isPrimaryPressed = true)
            val boton = if (derecho) PointerButton.Secondary else PointerButton.Primary
            sendPointerEvent(PointerEventType.Press, Offset(x, y), buttons = b, button = boton)
            sendPointerEvent(PointerEventType.Release, Offset(x, y), button = boton)
            espera()
        }
        fun ImageComposeScene.doble(x: Float, y: Float) {
            sendPointerEvent(PointerEventType.Move, Offset(x, y))
            repeat(2) {
                sendPointerEvent(PointerEventType.Press, Offset(x, y), buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
                sendPointerEvent(PointerEventType.Release, Offset(x, y), button = PointerButton.Primary)
                Thread.sleep(60)
            }
            espera(4)
        }
        fun ImageComposeScene.encima(x: Float, y: Float) { sendPointerEvent(PointerEventType.Move, Offset(x - 4, y)); espera(1); sendPointerEvent(PointerEventType.Move, Offset(x, y)); espera(5) }
        captura("cobalto-portada", Pantalla.Portada)
        captura("cobalto-obra", Pantalla.Obra(obra.id, s2.id))
        captura("cobalto-obra-oscuro", Pantalla.Obra(obra.id, s2.id), oscuro = true)
        captura("cobalto-editor-panel", Pantalla.Obra(obra.id, s2.id), antes = { PrefsEditor.asegura(); PrefsEditor.panel = true; PrefsEditor.pestanaPanel = 1 })
        captura("cobalto-editor-escena", Pantalla.Obra(obra.id, s2.id), antes = { PrefsEditor.asegura(); PrefsEditor.panel = true; PrefsEditor.pestanaPanel = 0; PrefsEditor.maquina = true })
        captura("cobalto-editor-buscar", Pantalla.Obra(obra.id, s2.id), antes = { Estudio.buscarAlAbrir = s2.id to "Marta" })
        captura("cobalto-concentracion", Pantalla.Obra(obra.id, s1.id), antes = { Estudio.concentracion = true })
        captura("cobalto-concentracion-oscuro", Pantalla.Obra(obra.id, s1.id), oscuro = true, antes = { Estudio.concentracion = true; PrefsEditor.asegura(); PrefsEditor.tam = 23f })
        // (en la lista: Marta es la cuarta)
        captura("cobalto-fichas", Pantalla.Obra(obra.id, pestana = 1), gestos = { it.clic(420f, 548f) })
        captura("cobalto-fichas-oscuro", Pantalla.Obra(obra.id, pestana = 1), oscuro = true, gestos = { it.clic(420f, 548f) })
        captura("cobalto-fichas-edicion", Pantalla.Obra(obra.id, pestana = 1), gestos = { it.clic(420f, 548f); it.clic(1000f, 480f) })
        // El ratón encima de «Marta» en el texto: su ficha en pequeño; el botón derecho en «puerta»: los sinónimos en el menú
        captura("cobalto-editor-previa", Pantalla.Obra(obra.id, s2.id), gestos = { it.encima(700f, 518f) })
        captura("cobalto-editor-menu", Pantalla.Obra(obra.id, s2.id), gestos = { it.clic(1010f, 653f, derecho = true) })
        // Doble clic en «Marta»: su ficha encima del editor; el botón derecho y «Sinónimos»: el globo de los sinónimos
        captura("cobalto-editor-ficha", Pantalla.Obra(obra.id, s2.id), gestos = { it.doble(700f, 518f) })
        captura("cobalto-editor-sinonimos", Pantalla.Obra(obra.id, s2.id), gestos = { it.clic(1010f, 653f, derecho = true); it.clic(1100f, 800f) })
        captura("cobalto-sprint", Pantalla.Obra(obra.id, s2.id), gestos = { it.clic(973f, 348f) })
        captura("cobalto-sprint-en-marcha", Pantalla.Obra(obra.id, s2.id), antes = { SprintEscribir.empieza(e, obra.id, 25, 500) })
        // (se acaba al momento: de menos de un minuto, no queda en el historial)
        SprintEscribir.termina(e, ya = true); SprintEscribir.terminado = null
        // El manuscrito en cuadros: el enlace «Cuadros» del índice
        captura("cobalto-cuadros", Pantalla.Obra(obra.id, s2.id), gestos = { it.clic(530f, 410f) })
        captura("cobalto-indice-menu", Pantalla.Obra(obra.id, s2.id), gestos = { it.clic(420f, 594f, derecho = true) })
        captura("cobalto-arbol", Pantalla.Obra(obra.id, pestana = 2))
        captura("cobalto-arbol-oscuro", Pantalla.Obra(obra.id, pestana = 2), oscuro = true)
        captura("cobalto-notas", Pantalla.Obra(obra.id, pestana = 3))
        captura("cobalto-linea", Pantalla.Obra(obra.id, pestana = 4))
        captura("cobalto-cifras", Pantalla.Obra(obra.id, pestana = 5))
        captura("cobalto-guia", Pantalla.Obra(obra.id, pestana = 6))
        captura("cobalto-lectura", Pantalla.Obra(obra.id, pestana = 7))
        captura("cobalto-papelera", Pantalla.Papelera)
        Cuaderno.reiniciaParaPruebas(); LaFamilia.reiniciaParaPruebas()
    }
}
