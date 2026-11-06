package com.gonzalo.hiedra.pc

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

/** Moverse por la sala con el mando: el foco, las filas, las capas y lo que hace cada tecla y cada botón. */
class NavegacionTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun lasFilasDePortadasConElMando() {
        val foco = FocoSala()
        val pulsados = ArrayList<String>()
        var fm: FocusManager? = null
        // Tres filas de 20 portadas; caben 6 a lo ancho (el resto se compone al llegar a ellas)
        val escena = ImageComposeScene(700, 600, Density(1f)) {
            fm = LocalFocusManager.current
            CompositionLocalProvider(LocalFoco provides foco) {
                Column {
                    repeat(3) { r ->
                        LazyRow(Modifier.width(660.dp).focusRestorer().focusGroup().focusProperties {
                            onExit = { if (requestedFocusDirection == FocusDirection.Left || requestedFocusDirection == FocusDirection.Right) cancelFocusChange() }
                        }) {
                            items((0 until 20).toList()) { i -> Box(Modifier.size(100.dp, 150.dp).enfocable("$r/$i") { pulsados += "$r/$i" }) }
                        }
                    }
                    // Algo de otra capa (cerrada): no se puede enfocar
                    CompositionLocalProvider(LocalCapa provides "menu") { Box(Modifier.size(50.dp).enfocable("menu/0") {}) }
                }
            }
        }
        var t = 0L
        fun frames(n: Int = 8) = repeat(n) { t += 50_000_000L; escena.render(t) }
        frames()
        foco.pedido = "0/0"; frames()
        assertEquals("0/0", foco.actual)
        assertNull("lo pedido se borra al enfocarlo", foco.pedido)
        val antes = foco.marco
        fm!!.moveFocus(FocusDirection.Right); frames()
        assertEquals("0/1", foco.actual)
        assertTrue("el marco va con el foco", foco.marco.center.x > antes.center.x + 50f)
        fm!!.moveFocus(FocusDirection.Left); frames()
        // A la derecha, más allá de lo que se ve
        repeat(9) { fm!!.moveFocus(FocusDirection.Right); frames(4) }
        assertEquals("0/9", foco.actual)
        // Al final de la fila no salta a la de abajo
        repeat(15) { fm!!.moveFocus(FocusDirection.Right); frames(3) }
        assertEquals("0/19", foco.actual)
        // Abajo: a la segunda fila; arriba: vuelve a donde estaba en la primera
        fm!!.moveFocus(FocusDirection.Down); frames()
        assertTrue(foco.actual!!.startsWith("1/"))
        fm!!.moveFocus(FocusDirection.Up); frames()
        assertEquals("0/19", foco.actual)
        // A pulsa lo enfocado
        assertTrue(foco.pulsa())
        assertEquals(listOf("0/19"), pulsados)
        // Con una capa encima, lo de debajo no se enfoca, y lo de la capa sí
        foco.abre("menu"); frames()
        foco.pedido = "1/0"; frames()
        assertNotEquals("1/0", foco.actual)
        foco.pedido = "menu/0"; frames()
        assertEquals("menu/0", foco.actual)
        foco.cierra("menu"); foco.pedido = "2/0"; frames()
        assertEquals("2/0", foco.actual)
        escena.close()
    }

    @Test fun lasTeclasSonLosBotonesDelMando() {
        assertEquals(Accion.A, Entrada.deTecla(java.awt.event.KeyEvent.VK_ENTER, false))
        assertEquals(Accion.B, Entrada.deTecla(java.awt.event.KeyEvent.VK_ESCAPE, true))
        assertEquals(Accion.B, Entrada.deTecla(java.awt.event.KeyEvent.VK_BACK_SPACE, false))
        assertNull("buscando, el retroceso borra", Entrada.deTecla(java.awt.event.KeyEvent.VK_BACK_SPACE, true))
        assertEquals(Accion.X, Entrada.deTecla(java.awt.event.KeyEvent.VK_F, false))
        assertNull("buscando, la F es una F", Entrada.deTecla(java.awt.event.KeyEvent.VK_F, true))
        assertEquals(Accion.LB, Entrada.deTecla(java.awt.event.KeyEvent.VK_PAGE_UP, true))
        assertEquals(Accion.VISTA, Entrada.deTecla(java.awt.event.KeyEvent.VK_F11, false))
        assertEquals(Accion.ARRIBA, Entrada.deTecla(java.awt.event.KeyEvent.VK_UP, true))
    }

    @Test fun laTeclaQueAbreLaBusquedaNoSeEscribe() {
        val c = javax.swing.JPanel()
        val escrito = StringBuilder(); val acciones = ArrayList<Accion>()
        Entrada.manejador = { a -> acciones += a; true }
        fun ev(id: Int, code: Int, ch: Char) = java.awt.event.KeyEvent(c, id, 0L, 0, code, ch)
        val P = java.awt.event.KeyEvent.KEY_PRESSED; val T = java.awt.event.KeyEvent.KEY_TYPED; val U = java.awt.event.KeyEvent.VK_UNDEFINED
        assertTrue(Entrada.teclaAwt(ev(P, java.awt.event.KeyEvent.VK_F, 'f'), escribiendo = false))
        assertTrue(Entrada.teclaAwt(ev(T, U, 'f'), escribiendo = true) { escrito.append(it) })
        Entrada.teclaAwt(ev(P, java.awt.event.KeyEvent.VK_C, 'c'), escribiendo = true)
        Entrada.teclaAwt(ev(T, U, 'c'), escribiendo = true) { escrito.append(it) }
        Entrada.teclaAwt(ev(P, java.awt.event.KeyEvent.VK_BACK_SPACE, '\b'), escribiendo = true)
        Entrada.teclaAwt(ev(T, U, '\b'), escribiendo = true) { escrito.append(if (it == '\b') "⌫" else it.toString()) }
        assertEquals(listOf(Accion.X), acciones)
        assertEquals("c⌫", escrito.toString())
        // Con Control o Alt, las teclas son del sistema
        assertFalse(Entrada.teclaAwt(java.awt.event.KeyEvent(c, P, 0L, java.awt.event.InputEvent.CTRL_DOWN_MASK, java.awt.event.KeyEvent.VK_F, 'f'), escribiendo = false))
        Entrada.manejador = null
    }

    @Test fun conSteamElMandoYSuTeclaCuentanUnaVez() {
        // Steam con su configuración de escritorio: el botón A llega del mando y, a la vez, como Intro
        assertFalse(Entrada.repetida(Accion.A, deMando = true, ahora = 10_000))
        assertTrue(Entrada.repetida(Accion.A, deMando = false, ahora = 10_060))
        // Dos pulsaciones seguidas del mismo origen sí cuentan las dos
        assertFalse(Entrada.repetida(Accion.ABAJO, deMando = true, ahora = 20_000))
        assertFalse(Entrada.repetida(Accion.ABAJO, deMando = true, ahora = 20_050))
        // Y la del otro origen, pasado el margen, también
        assertFalse(Entrada.repetida(Accion.ABAJO, deMando = false, ahora = 20_400))
    }

    @Test fun mantenerUnaDireccionLaRepite() {
        val r = Repeticion()
        assertTrue("al pulsar, una", r.paso(true, 0))
        assertFalse(r.paso(true, 100)); assertFalse(r.paso(true, 370))
        assertTrue("tras la espera, otra", r.paso(true, 400))
        assertFalse(r.paso(true, 450)); assertTrue(r.paso(true, 500))
        assertTrue("pasado un rato, más deprisa", r.paso(true, 1600)); assertTrue(r.paso(true, 1650))
        assertFalse("al soltar, nada", r.paso(false, 1700))
        assertTrue("y al volver a pulsar, una al momento", r.paso(true, 1710))
    }

    @Test fun traerAVistaConAire() {
        val h = TraerAVista(100f)
        assertEquals(0f, h.calculateScrollDistance(300f, 200f, 1000f), 0f)
        assertEquals("a la derecha, con aire", 100f, h.calculateScrollDistance(800f, 200f, 1000f), 0f)
        assertEquals("a la izquierda, con aire", -60f, h.calculateScrollDistance(40f, 200f, 1000f), 0f)
        val v = TraerAVista(80f, inicio = 46f)
        assertEquals("en vertical, la fila sube a su sitio", 454f, v.calculateScrollDistance(500f, 300f, 900f), 0f)
    }
}
