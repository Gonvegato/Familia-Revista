@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.gonzalo.cobalto.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.gonzalo.cobalto.Escritos
import com.gonzalo.familia.pc.TextoAireado
import com.gonzalo.familia.pc.revista.Paper
import com.gonzalo.familia.pc.revista.TemaFamilia
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Las pantallas del estudio de verdad (pintadas sin ventana, con clics de ratón): una ficha se abre para leer, un clic en la página
 * la pasa a editar y «Listo» vuelve a leer; y el pie del editor cuenta las palabras al momento, antes de guardar.
 */
class EstudioUiTest {
    @get:Rule val tmp = TemporaryFolder()
    private var reloj = 0L

    @Before fun prepara() { Cuaderno.reiniciaParaPruebas(); Cuaderno.dirPruebas = tmp.root; PrefsEditor.reiniciaParaPruebas(); Estudio.reiniciaParaPruebas() }
    @After fun fuera() { Editor.guardaYa(); Cuaderno.reiniciaParaPruebas(); Cuaderno.dirPruebas = null; PrefsEditor.reiniciaParaPruebas(); Estudio.reiniciaParaPruebas() }

    private fun escena(contenido: @Composable () -> Unit) = ImageComposeScene(1300, 860, Density(1f)) {
        TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { contenido() } } }
    }
    private fun ImageComposeScene.pinta(n: Int = 4) { repeat(n) { Thread.sleep(60); reloj += 100_000_000L; render(reloj) } }
    private fun todos(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap { todos(it) }
    private fun ImageComposeScene.nodos() = semanticsOwners.flatMap { todos(it.unmergedRootSemanticsNode) }
    private fun ImageComposeScene.textos() = nodos().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
    private fun ImageComposeScene.conTexto(t: String) = nodos().firstOrNull { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == t } }
    private fun ImageComposeScene.clic(p: Offset) {
        sendPointerEvent(PointerEventType.Move, p)
        sendPointerEvent(PointerEventType.Press, p, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, p, button = PointerButton.Primary)
        pinta()
    }

    @Test fun unaFichaSeLeeYConUnClicSeEdita() {
        val e = Cuaderno.escritos
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra") }
        val marta = e.nuevo(Escritos.T_FICHA, obra.id) { put("tipo", "personaje"); put("nombre", "Marta"); put("resumen", "Vuelve a la casa del lago.") }
        val modo = ModoPagina()
        val s = escena { Fichas(obra, Nav({}, {}), modo, {}, {}) }
        s.pinta(6)
        // Se abre la primera, para leerla
        assertEquals(marta.id, modo.sel); assertFalse(modo.editando)
        assertTrue(s.textos().contains("Un clic en la página para editarla"))
        assertNull(s.conTexto("LISTO"))
        // Un clic en la entradilla: a editar (con todos los campos)
        val entradilla = s.conTexto("Vuelve a la casa del lago.")!!
        s.clic(entradilla.boundsInRoot.center)
        assertTrue(modo.editando)
        val listo = s.conTexto("LISTO")!!
        assertTrue(s.textos().containsAll(listOf("NOMBRE", "TIPO", "DATOS", "COLOR", "TAMAÑO EN EL ÁRBOL")))
        // Lo que se escribe se guarda solo (un momento después)
        val nombre = s.nodos().first { it.config.getOrNull(SemanticsProperties.EditableText)?.text == "Marta" }
        nombre.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Marta Gil"))
        s.pinta(14)
        assertEquals("Marta Gil", e.get(marta.id)!!.s("nombre"))
        // «Listo»: otra vez a leer
        s.clic(listo.boundsInRoot.center)
        assertFalse(modo.editando)
        assertTrue(s.textos().contains("Marta Gil"))
        s.close()
    }

    @Test fun elPieCuentaLasPalabrasAlMomentoYHoySeApuntaUnaVez() {
        val e = Cuaderno.escritos
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra"); put("diaria", 10) }
        val cap = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "Uno") }
        val esc = e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", cap.id); put("texto", "Llovía sobre el lago.") }
        // (como el manuscrito: con cada cambio, la escena de ahora)
        val s = escena { e.version; EditorEscena(e.get(esc.id)!!, e.get(obra.id)!!, Nav({}, {}), AbreDoc(), {}) }
        s.pinta(6)
        assertTrue(s.textos().contains("4 palabras"))
        assertTrue(s.textos().contains("hoy 0/10"))
        // Escribes: el pie lo cuenta ya, antes de guardar
        val campo = s.nodos().first { it.config.getOrNull(SemanticsProperties.EditableText)?.text == "Llovía sobre el lago." }
        campo.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Llovía sobre el lago. Marta bajó del coche."))
        s.pinta(1)
        assertTrue(s.textos().toString(), s.textos().contains("8 palabras"))
        assertTrue(s.textos().contains("+4 en esta sesión"))
        assertTrue(s.textos().contains("hoy 4/10"))
        assertEquals("Llovía sobre el lago. Marta bajó del coche.", Editor.pendienteDe(esc.id))
        // Al rato se guarda solo, y lo de hoy se apunta una vez (lo mismo que decía el pie)
        repeat(12) { s.pinta(1); Thread.sleep(60) }
        assertNull(Editor.pendienteDe(esc.id))
        assertEquals("Llovía sobre el lago. Marta bajó del coche.", e.get(esc.id)!!.s("texto"))
        assertEquals(4, e.escritas(Escritos.hoy(), obra.id))
        assertTrue(s.textos().contains("hoy 4/10"))
        s.close()
    }
}
