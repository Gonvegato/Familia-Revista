package com.gonzalo.mostaza

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La versión que se enseña (en la portada y en Ajustes) es la que se instala: la de build.gradle.kts. Y el actualizador solo coge
 * «Mostaza-<versión>.apk» de la carpeta «versiones» (no las de las hermanas ni las del ordenador).
 */
class VersionTest {
    @Test fun laVersionQueSeVeEsLaInstalada() {
        val gradle = listOf(File("build.gradle.kts"), File("mostaza/build.gradle.kts")).first { it.exists() }.readText()
        val nombre = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(gradle)!!.groupValues[1]
        assertEquals(nombre, VERSION)
        assertEquals("0.1.0", VERSION)
    }

    @Test fun elActualizadorSoloCogeLasDeMostaza() {
        assertEquals(listOf(0, 1, 0), Actualizador.versionDe("Mostaza-0.1.0.apk"))
        assertEquals(null, Actualizador.versionDe("Marfil-0.14.2.apk"))
        assertEquals(null, Actualizador.versionDe("Mostaza-0.1.0-linux.tar.gz"))
        assertEquals(null, Actualizador.versionDe("Mostaza-0.1.0.apk.part"))
        val ult = Actualizador.ultima(listOf("Mostaza-0.1.0.apk" to 1L, "Mostaza-0.10.0.apk" to 2L, "Mostaza-0.9.1.apk" to 3L, "Hiedra-9.0.0.apk" to 4L))!!
        assertEquals("Mostaza-0.10.0.apk", ult.first)
        assertTrue(Actualizador.mayor(listOf(0, 10, 0), listOf(0, 9, 9)))
    }
}
