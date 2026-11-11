package com.gonzalo.gamerate

import com.gonzalo.familia.nucleo.Nodo
import org.junit.Assert.assertEquals
import org.junit.Test

/** El fondo que pones en una hermana (aquí, Hathor: `fimg`) es el que sale en Jonsu; el que guardaba Jonsu para sí solo vale si no hay ninguno. */
class FondoDeLaCuentaTest {
    private fun peli(ref: String, vararg campos: Pair<String, Any?>) =
        Nodo(ref, mapOf("t" to "Peli $ref", "tipo" to "movie", "estado" to "en_curso", "an" to 1_700_000_000_000L) + campos.toMap())
    private fun hero(n: Nodo, portada: (String) -> String = { h -> "/img/$h" }) =
        CosasMostaza.construye(listOf(n), emptyList(), emptyList(), "movie", 4242, portada = portada).plays.single().hero

    @Test fun elFondoDeHathorMandaSobreElDeJonsu() {
        val az = """{"hero":"/viejo/de-jonsu.jpg"}"""
        assertEquals("el que elegiste en Hathor", "/img/abc", hero(peli("a", "fimg" to "abc", "az" to az, "fondo" to "https://tmdb/x.jpg")))
        assertEquals("aún sin bajar: el de internet del que salió", "https://de/donde.jpg", hero(peli("b", "fimg" to "abc", "fu" to "https://de/donde.jpg"), portada = { "" }))
        assertEquals("quitado a propósito en Hathor", "", hero(peli("c", "fimg" to "-", "az" to az, "fondo" to "https://tmdb/x.jpg")))
        assertEquals("sin elegir en Hathor: el que tenía Jonsu", "/viejo/de-jonsu.jpg", hero(peli("d", "az" to az, "fondo" to "https://tmdb/x.jpg")))
        assertEquals("y si tampoco, el de TMDB", "https://tmdb/x.jpg", hero(peli("e", "fondo" to "https://tmdb/x.jpg")))
    }
}
