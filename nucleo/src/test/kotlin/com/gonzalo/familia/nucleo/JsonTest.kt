package com.gonzalo.familia.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {
    @Test fun idaYVuelta() {
        val v = mapOf("t" to "El nombre del «viento» \"ñ\"\n\t\u0001", "n" to 42L, "d" to 3.5, "b" to true, "l" to listOf(1L, "x", null), "o" to mapOf("a" to emptyMap<String, Any?>()))
        val s = Json.escribe(v)
        assertEquals(v, Json.lee(s))
    }

    @Test fun numerosDeFirebase() {
        assertEquals(1793889379206L, Json.lee("1793889379206"))
        assertEquals(-2.5, Json.lee("-2.5"))
        assertEquals(1.0E21, Json.lee("1e21"))
        assertEquals(7L, Json.lee("7.0").comoLong())
        assertEquals("12", Json.escribe(12.0))
    }

    @Test fun escapesUnicode() {
        assertEquals("é😀", Json.lee("\"\\u00e9\\ud83d\\ude00\""))
        assertTrue(Json.escribe(" ").contains("\\u2028"))
    }

    @Test(expected = Json.Error::class) fun rotoFalla() { Json.lee("{\"a\":1,}") }
}
