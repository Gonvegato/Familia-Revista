package com.gonzalo.familia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los estilos de los widgets: los colores que sacan y que todo se siga leyendo. */
class EstiloWidgetTest {
    private val papel = 0xFFF8F0D4.toInt()
    private val negro = 0xFF000000.toInt()
    private val acentos = mapOf("Seshat" to 0xFFB0791E, "Jonsu" to 0xFF8E5A78, "Bastet" to 0xFF7A8A3A, "Thot" to 0xFF5A7896, "Hathor" to 0xFFB56576, "Nut" to 0xFF5D1414)
    private fun dia(a: Long) = TonosWidget(papel, negro, 0xFF6B6352.toInt(), 0xFFCFC6AD.toInt(), 0xFFEDE3C3.toInt(), 0xFFF2E9CC.toInt(), a.toInt())
    private fun noche(a: Long) = TonosWidget(negro, papel, 0xFFA59C86.toInt(), 0xFF3A352C.toInt(), 0xFF1E1B16.toInt(), 0xFF141210.toInt(), a.toInt())

    @Test fun laClaveVaYViene() {
        for (e in EstiloWidget.entries) assertEquals(e, EstiloWidget.de(e.clave))
        assertEquals(EstiloWidget.COMO_AHORA, EstiloWidget.de(null))
        assertEquals(EstiloWidget.COMO_AHORA, EstiloWidget.de("otro"))
    }

    @Test fun comoAhoraCabeceraYMarcoNoTocanLosColores() {
        val t = dia(0xFFB0791E)
        for (e in listOf(EstiloWidget.COMO_AHORA, EstiloWidget.CABECERA, EstiloWidget.MARCO)) assertEquals(t, EstilosWidget.aplicar(e, false, t))
    }

    @Test fun todoAcentoSeLeeEnLasSeisDeDiaYDeNoche() {
        for ((app, a) in acentos) for (t in listOf(dia(a), noche(a))) {
            val r = EstilosWidget.aplicar(EstiloWidget.ACENTO, t.papel == negro, t)
            assertEquals(a.toInt(), r.papel)
            assertTrue("$app: el texto sobre el acento se lee (${EstilosWidget.contraste(r.tinta, r.papel)})", EstilosWidget.contraste(r.tinta, r.papel) >= 4.5)
        }
    }

    @Test fun elPapelTenidoLlevaAcentoPeroElTextoSeSigueLeyendo() {
        for ((app, a) in acentos) for (n in listOf(false, true)) {
            val t = if (n) noche(a) else dia(a)
            val r = EstilosWidget.aplicar(EstiloWidget.TENIDO, n, t)
            assertNotEquals(app, t.papel, r.papel)
            assertTrue("$app: texto sobre papel teñido", EstilosWidget.contraste(r.tinta, r.papel) >= 7.0)
        }
    }

    @Test fun laFranjaYElMarcoSeVenSobreElPapelTambienElGranateDeNocheYSuTextoSeLee() {
        for ((app, a) in acentos) for (t in listOf(dia(a), noche(a))) {
            val b = EstilosWidget.colorBorde(t)
            assertTrue("$app: franja visible", EstilosWidget.contraste(b, t.papel) >= 2.0)
            assertTrue("$app: texto en la franja", EstilosWidget.contraste(EstilosWidget.sobre(b, t.papel, t.tinta), b) >= 4.5)
        }
        // El granate de Nut de noche se aclara; de día queda tal cual
        assertNotEquals(0xFF5D1414.toInt(), EstilosWidget.colorBorde(noche(0xFF5D1414)))
        assertEquals(0xFF5D1414.toInt(), EstilosWidget.colorBorde(dia(0xFF5D1414)))
    }
}
