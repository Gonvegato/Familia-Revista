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

    @Test fun elActualizadorSoloCogeLasDeFcFeed() {
        assertEquals(listOf(0, 6, 0), Actualizador.versionDe("FcFeed-0.6.0.apk"))
        assertEquals(null, Actualizador.versionDe("Shai-0.6.0.apk"))
        assertEquals(null, Actualizador.versionDe("Nut-0.1.0.apk"))
        assertEquals(null, Actualizador.versionDe("FcFeed-0.1.0.apk.part"))
        val ult = Actualizador.ultima(listOf("FcFeed-0.6.0.apk" to 1L, "FcFeed-0.10.0.apk" to 2L, "FcFeed-0.9.1.apk" to 3L, "Widgets-9.0.0.apk" to 4L))!!
        assertEquals("FcFeed-0.10.0.apk", ult.first)
        assertEquals("fcfeed", KDrive.CARPETA)
    }

    @Test fun noEsDeLaFamilia() {
        // Ni en la lista de la familia, ni con su proveedor de «entrar una vez», ni dependiendo de su módulo
        val fam = File(raiz(), "familia/src/main/java/com/gonzalo/familia/LaFamilia.kt").readText()
        assertFalse(fam.contains("\"com.gonzalo.shai\""))
        assertFalse(File(raiz(), "familia/src/main/AndroidManifest.xml").readText().contains("com.gonzalo.shai"))
        assertFalse(archivo("build.gradle.kts").readText().contains("project(\":familia\")"))
        val man = archivo("src/main/AndroidManifest.xml").readText()
        assertTrue(man.contains("android:label=\"FcFeed\"")); assertFalse(man.contains("queries"))
        // Lo que se ve: ni rastro de los nombres de antes ni de la familia
        val main = File(raiz(), "shai/src/main/java/com/gonzalo/shai").listFiles()!!.filter { it.extension == "kt" && it.name != "Migracion.kt" && it.name != "Caja.kt" }
        val cadenas = Regex("\"([^\"\\n]*)\"")
        val malas = main.flatMap { f -> cadenas.findAll(f.readText()).map { it.groupValues[1] }.filter { t -> listOf("Shai", "Jonsu", "Ptah", "Hathor", "la familia de", "cuenta de la familia").any { t.contains(it) } }.map { "${f.name}: $it" } }
        assertTrue(malas.joinToString("\n"), malas.isEmpty())
        // La firma: la misma de siempre (para instalarse encima de la que hay), con su nombre nuevo
        val firmar = File(raiz(), "herramientas/firmar.py").readText()
        assertTrue(firmar.contains("\"shai\": None") && firmar.contains("\"shai\": \"FcFeed\""))
    }
}
