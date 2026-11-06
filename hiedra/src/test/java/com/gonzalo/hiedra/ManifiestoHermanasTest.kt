package com.gonzalo.hiedra

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Desde Android 11 una app solo «ve» a las demás si las declara en `<queries>` del manifiesto. Sin eso,
 * `getLaunchIntentForPackage` devuelve null y los enlaces a las hermanas («En Azabache» y los lanzadores) no salen nunca en un móvil de
 * verdad. Robolectric no filtra paquetes, así que ninguna otra prueba lo nota: esta mira el manifiesto tal cual.
 */
class ManifiestoHermanasTest {
    private fun manifiesto(): File {
        var d: File? = File("").absoluteFile
        while (d != null) {
            for (r in listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")) File(d, r).takeIf { it.exists() }?.let { return it }
            d = d.parentFile
        }
        error("no encuentro el manifiesto")
    }

    @Test
    fun declaraALasHermanas() {
        val m = manifiesto().readText()
        val queries = m.substringAfter("<queries>", "").substringBefore("</queries>")
        for (p in listOf("com.gonzalo.marfil", "com.gonzalo.cobalto", "com.gonzalo.gamerate")) assertTrue("falta <package android:name=\"$p\"/> en <queries>", Regex("<package\\s+android:name=\"" + Regex.escape(p) + "\"").containsMatchIn(queries))
    }

    /** Lo mismo con los emuladores: uno que no está en <queries> sale siempre como «no instalado» aunque lo tengas. */
    @Test
    fun declaraTodosLosEmuladoresDelCatalogo() {
        val queries = manifiesto().readText().substringAfter("<queries>", "").substringBefore("</queries>")
        val declarados = Regex("<package\\s+android:name=\"([^\"]+)\"").findAll(queries).map { it.groupValues[1] }.toSet()
        val json = org.json.JSONObject(File(manifiesto().parentFile, "assets/emulators.json").readText()).getJSONObject("emus")
        val faltan = json.keys().asSequence().flatMap { k -> json.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it).substringBefore('/') } } }.toSet() - declarados
        assertTrue("faltan en <queries>: $faltan", faltan.isEmpty())
    }
}
