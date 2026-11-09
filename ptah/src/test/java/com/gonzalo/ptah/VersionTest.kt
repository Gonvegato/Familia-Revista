package com.gonzalo.ptah

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La versión que se enseña (en la portada y en Ajustes) es la que se instala: la de build.gradle.kts. El actualizador solo coge
 * «Widgets-<versión>.apk» de su carpeta «widgets». Y no es de la familia: ni en sus listas, ni con su módulo, ni con sus nombres.
 */
class VersionTest {
    private fun archivo(n: String) = listOf(File(n), File("ptah/$n")).first { it.exists() }
    private fun raiz() = listOf(File(".."), File(".")).first { File(it, "settings.gradle.kts").exists() }

    @Test fun laVersionQueSeVeEsLaInstalada() {
        val g = archivo("build.gradle.kts").readText()
        assertEquals(Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(g)!!.groupValues[1], VERSION)
        assertTrue(Regex("versionCode = \\d+").containsMatchIn(g))
    }

    @Test fun elActualizadorSoloCogeLasDeWidgets() {
        assertEquals(listOf(0, 6, 0), Actualizador.versionDe("Widgets-0.6.0.apk"))
        assertEquals(null, Actualizador.versionDe("Ptah-0.6.0.apk"))
        assertEquals(null, Actualizador.versionDe("FcFeed-0.1.0.apk"))
        assertEquals(null, Actualizador.versionDe("Widgets-0.1.0.apk.part"))
        val ult = Actualizador.ultima(listOf("Widgets-0.6.0.apk" to 1L, "Widgets-0.10.0.apk" to 2L, "Widgets-0.9.1.apk" to 3L, "FcFeed-9.0.0.apk" to 4L))!!
        assertEquals("Widgets-0.10.0.apk", ult.first)
        assertEquals("widgets", KDrive.CARPETA)
    }

    @Test fun noEsDeLaFamilia() {
        val fam = File(raiz(), "familia/src/main/java/com/gonzalo/familia/LaFamilia.kt").readText()
        assertFalse(fam.contains("\"com.gonzalo.ptah\""))
        assertFalse(File(raiz(), "familia/src/main/AndroidManifest.xml").readText().contains("com.gonzalo.ptah"))
        assertFalse(archivo("build.gradle.kts").readText().contains("project(\":familia\")"))
        val man = archivo("src/main/AndroidManifest.xml").readText()
        assertTrue(man.contains("android:label=\"Widgets\"")); assertFalse(man.contains("com.gonzalo.gamerate"))
        // El widget de la pantalla de inicio sigue siendo el mismo (los que tienes puestos no se pierden)
        assertTrue(man.contains("android:name=\".WPtah\""))
        // Lo que se ve: ni rastro de los nombres de antes ni de la familia
        val main = File(raiz(), "ptah/src/main/java/com/gonzalo/ptah").listFiles()!!.filter { it.extension == "kt" && it.name != "Migracion.kt" && it.name != "Caja.kt" }
        val cadenas = Regex("\"([^\"\\n]*)\"")
        val malas = main.flatMap { f -> cadenas.findAll(f.readText()).map { it.groupValues[1] }.filter { t -> listOf("Ptah", "Jonsu", "Shai", "Hathor").any { t.contains(it) } || (t.contains(' ') && listOf("familia", "revista", "tu cuenta").any { t.lowercase().contains(it) }) }.map { "${f.name}: $it" } }
        assertTrue(malas.joinToString("\n"), malas.isEmpty())
        assertFalse(File(raiz(), "ptah/src/main/res/values/widgets.xml").readText().contains("Ptah"))
        // La firma: la misma de siempre (para instalarse encima de la que hay), con su nombre nuevo
        val firmar = File(raiz(), "herramientas/firmar.py").readText()
        assertTrue(firmar.contains("\"ptah\": None") && firmar.contains("\"ptah\": \"Widgets\""))
    }
}
