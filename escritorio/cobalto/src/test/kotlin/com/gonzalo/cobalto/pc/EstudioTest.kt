package com.gonzalo.cobalto.pc

import com.gonzalo.cobalto.Sinonimos
import com.gonzalo.cobalto.Tipografia
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Lo del estudio que no es pantalla: los sinónimos (leídos de la app) y la tipografía (con los casos compartidos de herramientas/casos, los
 * mismos que prueban el móvil y Atrio) y el texto de las páginas de lectura. El árbol y la guía, en EscribirArbolGuiaTest (la del móvil).
 */
class EstudioTest {
    private fun raiz(): File {
        var dir = File("").absoluteFile
        while (!File(dir, "herramientas/casos").exists()) dir = dir.parentFile ?: error("no encuentro herramientas/casos")
        return dir
    }

    // ---- Sinónimos y tipografía (herramientas/casos/sinonimos.json y tipografia.json)

    @Test fun sinonimosConLosCasosCompartidos() {
        // El diccionario va dentro de la app (se copia de los assets del móvil al compilar)
        Sinonimos.carga()
        assertTrue("el tesauro va en la app", Sinonimos.cargado)
        assertTrue(Sinonimos.cargaTexto(String(File(raiz(), "cobalto/src/main/assets/sinonimos/th_es_v2.dat").readBytes(), Charsets.ISO_8859_1)) > 20_000)
        val a = JSONObject(File(raiz(), "herramientas/casos/sinonimos.json").readText()).getJSONArray("casos")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            val esperado = c.getJSONArray("grupos").let { g -> (0 until g.length()).map { j -> g.getJSONArray(j).let { x -> (0 until x.length()).map { x.getString(it) } } } }
            val r = Sinonimos.busca(c.getString("palabra"))
            assertEquals(c.getString("palabra"), esperado, r.take(esperado.size))
            if (esperado.isEmpty()) assertTrue(r.isEmpty())
        }
        assertEquals("miró", Sinonimos.palabraEn("Ella miró la casa.", 7, 7)?.let { "Ella miró la casa.".substring(it.first, it.last + 1) })
        assertEquals("casa", Sinonimos.palabraEn("Ella miró la casa.", 13, 17)?.let { "Ella miró la casa.".substring(it.first, it.last + 1) })
        assertNull(Sinonimos.palabraEn("Ella  miró", 5, 5))
    }

    @Test fun tipografiaConLosCasosCompartidos() {
        val a = JSONObject(File(raiz(), "herramientas/casos/tipografia.json").readText()).getJSONArray("casos")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            val r = Tipografia.aplica(c.getString("texto"), c.getInt("cursor"), c.getString("ch")[0])
            if (c.isNull("esperado")) assertNull(c.getString("nombre"), r)
            else { assertEquals(c.getString("nombre"), c.getString("esperado"), r?.first); assertEquals(c.getString("nombre"), c.getInt("cursorEsperado"), r?.second) }
        }
    }

    // ---- El texto de las páginas de lectura

    @Test fun elTextoDeUnaFichaEnBloques() {
        val b = bloquesDe("# Su historia\n\nNació en el lago.\r\nCreció en Madrid.\n\n> Nadie vuelve igual.\n- Quiere: volver\n***\n### Final")
        assertEquals(listOf(Bloque.Titulo(1, "Su historia"), Bloque.Parrafo("Nació en el lago."), Bloque.Parrafo("Creció en Madrid."), Bloque.Cita("Nadie vuelve igual."),
            Bloque.Punto("Quiere: volver"), Bloque.Salto, Bloque.Titulo(3, "Final")), b)
        assertTrue(bloquesDe("  \n\n").isEmpty())
        val f = conFormato("Era **muy** alta y *nada* tímida.")
        assertEquals("Era muy alta y nada tímida.", f.text)
        assertEquals(2, f.spanStyles.size)
        assertEquals("sin cerrar *aquí", conFormato("sin cerrar *aquí").text)
    }

    @Test fun losColoresDeLosEscritos() {
        assertEquals(0xFFB0102F.toInt(), colorEscrito("#B0102F").value.shr(32).toInt())
        assertEquals(androidx.compose.ui.graphics.Color.Red, colorEscrito("", androidx.compose.ui.graphics.Color.Red))
        assertEquals(androidx.compose.ui.graphics.Color.Red, colorEscrito("#12", androidx.compose.ui.graphics.Color.Red))
        assertEquals(androidx.compose.ui.graphics.Color.Red, colorEscrito("#zzzzzz", androidx.compose.ui.graphics.Color.Red))
    }
}
