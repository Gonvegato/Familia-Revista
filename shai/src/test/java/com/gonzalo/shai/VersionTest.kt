package com.gonzalo.shai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La versión que se enseña (en la portada y en Ajustes) es la que se instala: la de build.gradle.kts. El actualizador solo coge
 * «Shai-<versión>.apk» de la carpeta «versiones» (no las de las hermanas). Y Shai no sale en Jonsu: Jonsu no la nombra.
 */
class VersionTest {
    private fun archivo(n: String) = listOf(File(n), File("shai/$n")).first { it.exists() }
    private fun raiz() = listOf(File(".."), File(".")).first { File(it, "settings.gradle.kts").exists() }

    @Test fun laVersionQueSeVeEsLaInstalada() {
        val g = archivo("build.gradle.kts").readText()
        assertEquals(Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(g)!!.groupValues[1], VERSION)
        assertTrue(Regex("versionCode = \\d+").containsMatchIn(g))
    }

    @Test fun elActualizadorSoloCogeLasDeShai() {
        assertEquals(listOf(0, 1, 0), Actualizador.versionDe("Shai-0.1.0.apk"))
        assertEquals(null, Actualizador.versionDe("Nut-0.1.0.apk"))
        assertEquals(null, Actualizador.versionDe("Cobre-0.1.0.apk"))
        assertEquals(null, Actualizador.versionDe("Shai-0.1.0.apk.part"))
        assertEquals(null, Actualizador.versionDe("Shai-0.1.0-linux.tar.gz"))
        val ult = Actualizador.ultima(listOf("Shai-0.1.0.apk" to 1L, "Shai-0.10.0.apk" to 2L, "Shai-0.9.1.apk" to 3L, "Hathor-9.0.0.apk" to 4L))!!
        assertEquals("Shai-0.10.0.apk", ult.first)
    }

    @Test fun esDeLaFamiliaPeroNoSaleEnJonsu() {
        // Entra con la cuenta (es una de las apps de la familia, con su proveedor de «entrar una vez»)...
        val fam = File(raiz(), "familia/src/main/java/com/gonzalo/familia/LaFamilia.kt").readText()
        assertTrue(fam.contains("\"com.gonzalo.shai\""))
        assertTrue(File(raiz(), "familia/src/main/AndroidManifest.xml").readText().contains("com.gonzalo.shai"))
        // ...pero Jonsu no la lee ni la enseña
        val jonsu = File(raiz(), "azabache/src/main").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
        assertFalse(jonsu.any { f -> f.readText().let { it.contains("com.gonzalo.shai") || it.contains("shai_") || it.contains("\"Shai\"") } })
        // La firma: la de la familia, sin linaje (nace con ella)
        val firmar = File(raiz(), "herramientas/firmar.py").readText()
        assertTrue(firmar.contains("\"shai\": None") && firmar.contains("\"shai\": \"Shai\""))
    }
}
