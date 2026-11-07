package com.gonzalo.cobre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La versión que se enseña (en la portada y en Ajustes) es la que se instala: la de build.gradle.kts. Y el actualizador solo coge
 * «Cobre-<versión>.apk» de la carpeta «versiones» (no las de las hermanas ni las del ordenador). Y el manifiesto es el de una app de tele.
 */
class VersionTest {
    private fun archivo(n: String) = listOf(File(n), File("cobre/$n")).first { it.exists() }

    @Test fun laVersionQueSeVeEsLaInstalada() {
        val nombre = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(archivo("build.gradle.kts").readText())!!.groupValues[1]
        assertEquals(nombre, VERSION)
        assertEquals("0.1.0", VERSION)
    }

    @Test fun elActualizadorSoloCogeLasDeCobre() {
        assertEquals(listOf(0, 1, 0), Actualizador.versionDe("Cobre-0.1.0.apk"))
        assertEquals(null, Actualizador.versionDe("Mostaza-0.3.3.apk"))
        assertEquals(null, Actualizador.versionDe("Cobre-0.1.0-linux.tar.gz"))
        assertEquals(null, Actualizador.versionDe("Cobre-0.1.0.apk.part"))
        val ult = Actualizador.ultima(listOf("Cobre-0.1.0.apk" to 1L, "Cobre-0.10.0.apk" to 2L, "Cobre-0.9.1.apk" to 3L, "Hiedra-9.0.0.apk" to 4L))!!
        assertEquals("Cobre-0.10.0.apk", ult.first)
    }

    @Test fun esUnaAppDeTele() {
        val m = archivo("src/main/AndroidManifest.xml").readText()
        assertTrue("en el menú de Android TV", m.contains("android.intent.category.LEANBACK_LAUNCHER"))
        assertTrue("con su cartel", m.contains("android:banner=\"@drawable/banner_cobre\""))
        assertTrue("sin pantalla táctil obligatoria", Regex("android.hardware.touchscreen\"\\s+android:required=\"false\"").containsMatchIn(m))
        assertTrue(Regex("android.software.leanback\"\\s+android:required=\"false\"").containsMatchIn(m))
        assertTrue("sin copia de seguridad (la caché lleva tus listas)", m.contains("android:allowBackup=\"false\""))
    }
}
