package com.gonzalo.cobalto

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo nuevo de la página del editor: el párrafo del cursor, subir y bajar párrafos, la sangría, apagar lo demás y comparar por palabras. */
class EscribirPaginaTest {
    private fun v(t: String, a: Int = t.length, b: Int = a) = TextFieldValue(t, TextRange(a, b))
    private val aspecto = AspectoTexto(Color.Black, Color.Gray, Color.Red, FontFamily.Serif, 18f)

    @Test fun elParrafoDelCursor() {
        val t = "Uno dos.\n\nTres cuatro.\nCinco."
        assertEquals(0 until 8, EdicionTexto.parrafo(t, 3))
        assertEquals(0 until 8, EdicionTexto.parrafo(t, 8))
        assertEquals("la línea en blanco", 9 until 9, EdicionTexto.parrafo(t, 9))
        assertEquals(10 until 22, EdicionTexto.parrafo(t, 10))
        assertEquals(23 until 29, EdicionTexto.parrafo(t, t.length))
        assertEquals(0 until 0, EdicionTexto.parrafo("", 0))
    }

    @Test fun subirYBajarUnParrafo() {
        val t = "Primero.\n\nSegundo.\n\nTercero."
        // Bajar el primero: pasa detrás del segundo, con el cursor dentro de él y en la misma columna
        val b = EdicionTexto.mueveParrafo(v(t, 3), 1)
        assertEquals("Segundo.\n\nPrimero.\n\nTercero.", b.text)
        assertEquals(10 + 3, b.selection.start)
        // Subir el tercero
        val s = EdicionTexto.mueveParrafo(v(t, t.length), -1)
        assertEquals("Primero.\n\nTercero.\n\nSegundo.", s.text)
        assertEquals("Tercero.", s.text.substring(s.selection.start - 8, s.selection.start))
        // Sin a dónde ir, o desde una línea en blanco: nada
        assertEquals(t, EdicionTexto.mueveParrafo(v(t, 2), -1).text)
        assertEquals(t, EdicionTexto.mueveParrafo(v(t, t.length), 1).text)
        assertEquals(t, EdicionTexto.mueveParrafo(v(t, 9), 1).text)
    }

    @Test fun laSangriaSoloEnLosParrafos() {
        val t = "# Título\n\nUn párrafo.\n\n***\n\n> Una cita\nOtro párrafo."
        val a = FormatoPagina(FormatoMarkdown(aspecto), sangria = 24.sp).estilo(t)
        assertEquals("no cambia el texto", t, a.text)
        val sangrados = a.paragraphStyles.filter { it.item.textIndent != null }.map { t.substring(it.start, it.end).trimEnd('\n') }
        assertEquals(listOf("Un párrafo.", "Otro párrafo."), sangrados)
        // Los estilos de párrafo no se pisan (Compose no lo admite)
        val rangos = a.paragraphStyles.sortedBy { it.start }
        rangos.zipWithNext().forEach { (x, y) -> assertTrue("se pisan: $x $y", x.end <= y.start) }
    }

    @Test fun enConcentracionSoloSeVeElParrafoVivo() {
        val t = "Antes.\n\nAhora escribo.\n\nDespués."
        val vivo = EdicionTexto.parrafo(t, 10)
        val apagado = Color(0x55000000)
        val a = FormatoPagina(FormatoMarkdown(aspecto), vivo = vivo, apagado = apagado).estilo(t)
        val apagados = a.spanStyles.filter { it.item.color == apagado }.map { t.substring(it.start, it.end) }
        assertEquals(listOf("Antes.\n\n", "\n\nDespués."), apagados)
        // Sin concentración, nada apagado
        assertTrue(FormatoPagina(FormatoMarkdown(aspecto)).estilo(t).spanStyles.none { it.item.color == apagado })
    }

    @Test fun compararPorPalabras() {
        val d = EscribirDiff.porPalabras("La casa era blanca y grande.", "La casa era azul y grande, muy grande.")
        assertEquals("La casa era blanca y grande.", d.filter { it.tipo != TrozoDiff.OTRO }.joinToString("") { it.texto })
        assertEquals("La casa era azul y grande, muy grande.", d.filter { it.tipo != TrozoDiff.AQUI }.joinToString("") { it.texto })
        assertEquals(listOf("blanca"), d.filter { it.tipo == TrozoDiff.AQUI }.map { it.texto.trim() }.filter { it.isNotEmpty() }.take(1))
        assertTrue(d.any { it.tipo == TrozoDiff.OTRO && it.texto.contains("azul") })
        // Iguales: un solo trozo
        assertEquals(1, EscribirDiff.porPalabras("igual", "igual").size)
    }
}
