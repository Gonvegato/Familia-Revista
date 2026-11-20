package com.gonzalo.marfil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Seshat y SesKobo se actualizan desde la misma carpeta «versiones»: cada una coge solo las suyas. */
class ActualizadorVersionesTest {
    private val carpeta = listOf(
        "Seshat-0.22.6.apk" to 1L, "Seshat-0.23.0.apk" to 2L, "SesKobo-0.22.6-kobo.3.apk" to 3L, "SesKobo-0.22.6-kobo.10.apk" to 4L,
        "SesKobo-0.23.0-kobo.1.apk" to 5L, "Jonsu-1.9.0.apk" to 6L, "SesKobo-0.24.0.apk" to 7L, "Marfil-0.9.0.apk" to 8L,
    )

    @Test fun seshatSoloLasSuyas() {
        assertEquals(listOf(0, 22, 6), Actualizador.versionDe("Seshat-0.22.6.apk", sesKobo = false))
        assertEquals(listOf(0, 23, 0), Actualizador.versionDe("Lyra-0.23.0.apk", sesKobo = false))
        assertNull(Actualizador.versionDe("SesKobo-0.22.6-kobo.3.apk", sesKobo = false))
        assertNull(Actualizador.versionDe("LyraKobo-0.23.0-kobo.1.apk", sesKobo = false))
        assertEquals("Seshat-0.23.0.apk", Actualizador.ultima(carpeta, sesKobo = false)?.first)
    }

    @Test fun sesKoboSoloLasSuyas() {
        assertEquals(listOf(0, 22, 6, 3), Actualizador.versionDe("SesKobo-0.22.6-kobo.3.apk", sesKobo = true))
        assertEquals(listOf(0, 23, 0, 1), Actualizador.versionDe("LyraKobo-0.23.0-kobo.1.apk", sesKobo = true))
        assertNull(Actualizador.versionDe("Seshat-0.23.0.apk", sesKobo = true))
        assertNull(Actualizador.versionDe("Lyra-0.23.0.apk", sesKobo = true))
        assertNull(Actualizador.versionDe("SesKobo-0.24.0.apk", sesKobo = true)) // (sin «-kobo»: no es un nombre de LyraKobo)
        // 0.22.6-kobo.10 es más nueva que 0.22.6-kobo.3 (como números), y 0.23.0-kobo.1 más que las dos
        assertEquals("SesKobo-0.23.0-kobo.1.apk", Actualizador.ultima(carpeta, sesKobo = true)?.first)
        assertEquals("SesKobo-0.22.6-kobo.10.apk", Actualizador.ultima(carpeta.filter { !it.first.contains("0.23") }, sesKobo = true)?.first)
    }

    @Test fun laInstaladaComoNumeros() {
        // (lo que se compara con la del kDrive: la versión de la app instalada)
        assertEquals(listOf(0, 22, 6, 3), Actualizador.numeros("0.22.6-kobo.3"))
        assertEquals(listOf(0, 22, 6), Actualizador.numeros("0.22.6"))
    }
}
