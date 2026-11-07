package com.gonzalo.mostaza

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Desde Android 11 una app solo «ve» a las demás si las declara en `<queries>` del manifiesto: sin eso no salen ni «Valorar en Azabache»,
 * ni los colores de Azabache, ni «Abrir con otro reproductor». Robolectric no filtra paquetes: esta prueba mira el manifiesto tal cual.
 */
class ManifiestoHermanasTest {
    private fun manifiesto(): File {
        var d: File? = File("").absoluteFile
        while (d != null) {
            for (r in listOf("src/main/AndroidManifest.xml", "mostaza/src/main/AndroidManifest.xml")) File(d, r).takeIf { it.exists() }?.let { return it }
            d = d.parentFile
        }
        error("no encuentro el manifiesto")
    }

    @Test fun declaraALasHermanasYALosReproductores() {
        val m = manifiesto().readText()
        val q = m.substringAfter("<queries>", "").substringBefore("</queries>")
        for (p in listOf("com.gonzalo.gamerate", "com.gonzalo.hiedra", "com.gonzalo.marfil", "com.gonzalo.cobalto", "org.videolan.vlc", "com.brouken.player"))
            assertTrue("falta <package android:name=\"$p\"/> en <queries>", Regex("<package\\s+android:name=\"" + Regex.escape(p) + "\"").containsMatchIn(q))
        assertTrue("los reproductores de vídeo, por su intent", q.contains("android:mimeType=\"video/*\""))
        assertTrue("las fuentes por http (y el motor de torrents en 127.0.0.1)", m.contains("android:usesCleartextTraffic=\"true\""))
        assertTrue("el «Install» de las páginas de los addons", m.contains("android:scheme=\"stremio\""))
    }

    @Test fun lasHermanasVenAMostaza() {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "familia/src/main/AndroidManifest.xml").exists()) d = d.parentFile
        val familia = File(d!!, "familia/src/main/AndroidManifest.xml").readText()
        assertTrue("las hermanas tienen que ver a Hathor para darle la cuenta (entrar una vez)", familia.contains("<package android:name=\"com.gonzalo.mostaza\" />"))
        assertTrue(File(d, "familia/src/main/java/com/gonzalo/familia/LaFamilia.kt").readText().contains("\"com.gonzalo.mostaza\""))
    }
}
