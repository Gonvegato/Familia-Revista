package com.gonzalo.hiedra

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** La versión que se enseña (en la portada y en Ajustes) es la que se instala: la de build.gradle.kts. (En Marfil y Cobalto se quedaba atrás: la 0.13.5 de Marfil decía 0.13.3.) */
class VersionTest {
    @Test fun laVersionQueSeVeEsLaInstalada() {
        val gradle = listOf(File("build.gradle.kts"), File("hiedra/build.gradle.kts")).first { it.exists() }.readText()
        val nombre = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(gradle)!!.groupValues[1]
        assertEquals(nombre, VERSION)
    }
}
