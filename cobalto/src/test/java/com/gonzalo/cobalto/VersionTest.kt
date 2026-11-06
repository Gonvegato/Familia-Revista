package com.gonzalo.cobalto

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** La versión que se enseña (en la portada y en Ajustes) es la que se instala: la de build.gradle.kts. Antes se quedaba atrás (0.3.3 decía 0.3.1). */
class VersionTest {
    @Test fun laVersionQueSeVeEsLaInstalada() {
        val gradle = listOf(File("build.gradle.kts"), File("cobalto/build.gradle.kts")).first { it.exists() }.readText()
        val nombre = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(gradle)!!.groupValues[1]
        assertEquals(nombre, VERSION)
    }
}
