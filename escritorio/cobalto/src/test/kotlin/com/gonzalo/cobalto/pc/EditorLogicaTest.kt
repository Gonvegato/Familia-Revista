package com.gonzalo.cobalto.pc

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.gonzalo.cobalto.Escritos
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Lo que el editor hace sin pantalla: buscar y reemplazar, las cuentas del pie, la máquina de escribir y sus preferencias. */
class EditorLogicaTest {
    @get:Rule val tmp = TemporaryFolder()

    @Before fun prepara() { Cuaderno.reiniciaParaPruebas(); Cuaderno.dirPruebas = tmp.root; PrefsEditor.reiniciaParaPruebas() }
    @After fun fuera() { Cuaderno.reiniciaParaPruebas(); Cuaderno.dirPruebas = null; PrefsEditor.reiniciaParaPruebas() }

    private val texto = "Marta abrió la puerta. La casa olía a marta y a madera. MARTA no dijo nada."

    @Test fun buscaSinMirarMayusculasSalvoQueSePida() {
        assertEquals(3, Busqueda.coincidencias(texto, "marta").size)
        assertEquals(listOf(0..4), Busqueda.coincidencias(texto, "Marta", mayus = true))
        assertTrue(Busqueda.coincidencias(texto, "").isEmpty())
        // Lo que se busca va tal cual (no es una expresión): un punto es un punto
        assertEquals(3, Busqueda.coincidencias(texto, "a.").size)
        assertEquals(1, Busqueda.coincidencias("¿(sí)?", "(sí)").size)
    }

    @Test fun saltaDesdeElCursorYDaLaVuelta() {
        val l = Busqueda.coincidencias(texto, "marta")
        // Sin ninguna elegida: la primera después del cursor (o la última antes, hacia atrás)
        assertEquals(1, Busqueda.salta(l, -1, 1, desde = 10))
        assertEquals(0, Busqueda.salta(l, -1, -1, desde = 10))
        assertEquals(0, Busqueda.salta(l, -1, 1, desde = texto.length))
        // Con una elegida: la siguiente, y al final vuelve a la primera (y al revés)
        assertEquals(2, Busqueda.salta(l, 1, 1))
        assertEquals(0, Busqueda.salta(l, 2, 1))
        assertEquals(2, Busqueda.salta(l, 0, -1))
        assertEquals(-1, Busqueda.salta(emptyList(), -1, 1))
    }

    @Test fun reemplazaUnaYTodas() {
        val v = TextFieldValue(texto, TextRange(0))
        val r = Busqueda.coincidencias(texto, "marta")[1]
        val u = Busqueda.reemplazaUna(v, r, "Lucía")
        assertTrue(u.text.contains("olía a Lucía y"))
        assertEquals(r.first + "Lucía".length, u.selection.start)
        val (t, n) = Busqueda.reemplazaTodas(texto, "marta", "Ana")
        assertEquals(3, n)
        assertEquals("Ana abrió la puerta. La casa olía a Ana y a madera. Ana no dijo nada.", t)
        // Lo que se pone va tal cual (un «$1» no es un grupo)
        assertEquals("cuesta $1 euro", Busqueda.reemplazaTodas("cuesta X euro", "X", "$1").first)
        assertEquals(texto to 0, Busqueda.reemplazaTodas(texto, "nadie", "alguien"))
        assertEquals(1, Busqueda.reemplazaTodas(texto, "MARTA", "Eva", mayus = true).second)
    }

    @Test fun lasCuentasDelPieSonLasQueSeApuntanAlGuardar() {
        val e = Escritos(File(tmp.root, "e"))
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra") }
        val cap = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "Uno") }
        val esc = e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", cap.id); put("texto", "Una dos tres.") }
        e.escribe(esc.id, "Una dos tres cuatro.")
        val guardado = e.get(esc.id)!!
        assertEquals(1, e.escritas(Escritos.hoy(), obra.id))
        // Mientras escribes (aún sin guardar), el pie ya cuenta lo nuevo…
        val ahora = "Una dos tres cuatro. Cinco seis siete."
        val c = Cuentas.de(ahora, TextRange(0, 3), inicio = 3, guardadas = guardado.i("palabras"), hoyGuardado = e.escritas(Escritos.hoy(), obra.id),
            capituloGuardado = e.palabrasCapitulo(cap.id), obraGuardada = e.palabrasObra(obra.id))
        assertEquals(7, c.palabras); assertEquals(1, c.elegidas); assertEquals(4, c.sesion); assertEquals(4, c.hoy); assertEquals(7, c.capitulo); assertEquals(7, c.obra)
        assertEquals(1, c.minutos)
        // …y al guardar se apunta exactamente eso (una sola vez)
        e.escribe(esc.id, ahora)
        e.escribe(esc.id, ahora)
        assertEquals(c.hoy, e.escritas(Escritos.hoy(), obra.id))
        // Borrar no resta de hoy (lo escrito, escrito está), pero sí de la escena
        val menos = Cuentas.de("Una dos.", TextRange(0), 3, e.get(esc.id)!!.i("palabras"), e.escritas(Escritos.hoy(), obra.id), 7, 7)
        assertEquals(4, menos.hoy); assertEquals(-1, menos.sesion); assertEquals(2, menos.obra)
    }

    @Test fun elEditorGuardaLoPendienteUnaVez() {
        val e = Cuaderno.escritos
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra") }
        val cap = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "Uno") }
        val esc = e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", cap.id); put("texto", "") }
        Editor.apunta(esc.id, "Llovía")
        Editor.apunta(esc.id, "Llovía sobre el lago.")
        assertEquals("Llovía sobre el lago.", Editor.pendienteDe(esc.id))
        assertNull(Editor.pendienteDe("otra"))
        Editor.guardaYa(); Editor.guardaYa()
        assertNull(Editor.pendienteDe(esc.id))
        assertEquals("Llovía sobre el lago.", e.get(esc.id)!!.s("texto"))
        assertEquals(4, e.escritas(Escritos.hoy(), obra.id))
        assertEquals(1, e.versiones(esc.id).size)
    }

    @Test fun laMaquinaDeEscribirDejaLaLineaAl40PorCiento() {
        // La línea del cursor (arriba 1000, alto 30), con 40 de margen y 800 de alto: su centro a 320 desde arriba
        assertEquals(1000 + 40 + 15 - 320, destinoMaquina(1000f, 30f, 40f, 800, 5000))
        // Al principio no se pasa de 0, ni al final del máximo
        assertEquals(0, destinoMaquina(10f, 30f, 40f, 800, 5000))
        assertEquals(500, destinoMaquina(4000f, 30f, 40f, 800, 500))
        // Sin máquina: solo se mueve si el cursor no se ve (lo justo, con holgura)
        assertNull(destinoVisible(300f, 30f, 40f, actual = 0, alto = 800, maximo = 5000))
        assertEquals(1000 + 40 + 30 + 48 - 800, destinoVisible(1000f, 30f, 40f, actual = 0, alto = 800, maximo = 5000))
        assertEquals(100 + 40 - 48, destinoVisible(100f, 30f, 40f, actual = 900, alto = 800, maximo = 5000))
    }

    @Test fun lasPreferenciasSeGuardanEnEsteOrdenador() {
        PrefsEditor.asegura()
        assertEquals(20f, PrefsEditor.tam); assertTrue(PrefsEditor.tipografia); assertFalse(PrefsEditor.maquina)
        PrefsEditor.letra(+1); PrefsEditor.letra(+1); PrefsEditor.otraFuente(); PrefsEditor.maquina = true; PrefsEditor.panel = true; PrefsEditor.pestanaPanel = 3; PrefsEditor.guarda()
        assertTrue(File(tmp.root, PrefsEditor.ARCHIVO).isFile)
        PrefsEditor.reiniciaParaPruebas(); PrefsEditor.asegura()
        assertEquals(22f, PrefsEditor.tam); assertEquals(1, PrefsEditor.fuente); assertTrue(PrefsEditor.maquina); assertTrue(PrefsEditor.panel); assertEquals(3, PrefsEditor.pestanaPanel)
        // Con límites: ni más pequeña de 14 ni más grande de 34; 0 vuelve a la normal
        repeat(30) { PrefsEditor.letra(+1) }; assertEquals(34f, PrefsEditor.tam)
        repeat(40) { PrefsEditor.letra(-1) }; assertEquals(14f, PrefsEditor.tam)
        PrefsEditor.letra(0); assertEquals(20f, PrefsEditor.tam)
        // Un archivo roto no rompe nada: las de siempre
        File(tmp.root, PrefsEditor.ARCHIVO).writeText("{no es json")
        PrefsEditor.carga(); assertEquals(20f, PrefsEditor.tam); assertEquals(0, PrefsEditor.fuente)
    }

    @Test fun lasFichasYNotasSeAbrenParaLeerYSeEditanConUnClic() {
        val m = ModoPagina()
        m.elige("f1"); assertEquals("f1", m.sel); assertFalse(m.editando)
        m.edita(); assertTrue(m.editando)
        // Esc vuelve a leer; otro Esc ya no hace nada (lo deja pasar)
        assertTrue(m.esc()); assertFalse(m.editando); assertFalse(m.esc())
        // Elegir otra se abre leyendo, aunque estuvieras editando
        m.edita(); m.elige("f2"); assertEquals("f2", m.sel); assertFalse(m.editando)
        // Elegir la misma no cambia nada
        m.edita(); m.elige("f2"); assertTrue(m.editando)
        m.listo(); assertFalse(m.editando)
        // Lo nuevo se abre para rellenarlo
        m.nuevo("f3"); assertEquals("f3", m.sel); assertTrue(m.editando)
        // Si se borra, se elige otra para leer
        m.desaparece("f1"); assertEquals("f1", m.sel); assertFalse(m.editando)
        // Sin nada elegido no se edita
        m.desaparece(null); m.edita(); assertFalse(m.editando)
    }

    @Test fun losNombresDeLasFichasSeReconocenEnElTexto() {
        val e = Escritos(File(tmp.root, "f"))
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra") }
        val marta = e.nuevo(Escritos.T_FICHA, obra.id) { put("nombre", "Marta"); put("alias", "Marti, la Pelirroja") }
        e.nuevo(Escritos.T_FICHA, obra.id) { put("nombre", "Lucas") }
        val rx = regexFichas(e.fichas(obra.id))!!
        val t = "Marti y Lucas vieron a la Pelirroja. Martina no."
        val nombres = rx.findAll(t).map { it.value }.toList()
        assertEquals(listOf("Marti", "Lucas", "la Pelirroja"), nombres)
        assertTrue("la Pelirroja" in nombres)
        // «Martina» no es «Marti» (palabras enteras)
        assertFalse(nombres.any { it.startsWith("Martina") })
        assertEquals(0..4, nombreFichaEn(t, 2, rx))
        assertNull(nombreFichaEn(t, 40, rx))
        assertEquals(marta.id, fichaDeNombre(e.fichas(obra.id), "la pelirroja")?.id)
        assertNull(regexFichas(emptyList()))
    }
}
