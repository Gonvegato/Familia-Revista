package com.gonzalo.familia

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FallosTest {
    private val c: Context get() = RuntimeEnvironment.getApplication()

    @Test fun unFalloQueCierraQuedaApuntadoYSigueSuCamino() {
        Fallos.olvida(c)
        var siguio = false
        val antes = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> siguio = true }
        try {
            Fallos.vigila(c)
            val h = Thread { throw IllegalStateException("roto aquí") }.apply { name = "prueba" }
            h.start(); h.join()
            val f = Fallos.ultimo(c)
            assertTrue(f, "se cerró (prueba)" in f && "roto aquí" in f)
            assertTrue("el manejador de antes también se entera", siguio)
            Fallos.olvida(c)
            assertEquals("", Fallos.ultimo(c))
        } finally { Thread.setDefaultUncaughtExceptionHandler(antes) }
    }

    @Test fun unFalloParadoSeApuntaConSuSitio() {
        Fallos.apunta(c, "widget", RuntimeException("sin datos"))
        val f = Fallos.ultimo(c)
        assertTrue(f, "widget" in f && "sin datos" in f)
    }
}
