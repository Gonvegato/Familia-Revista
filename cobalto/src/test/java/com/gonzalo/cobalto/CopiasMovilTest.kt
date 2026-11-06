package com.gonzalo.cobalto

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Las copias de seguridad del móvil: en la memoria de la app, comprimidas, al salir de la app si algo ha cambiado (y no si no). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopiasMovilTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun prepara() {
        Escritos.reiniciaParaPruebas(); CopiasMovil.reiniciaParaPruebas()
        File(ctx.filesDir, "escritos").deleteRecursively(); CopiasMovil.carpeta(ctx).deleteRecursively()
    }
    @After fun suelta() { Escritos.reiniciaParaPruebas(); CopiasMovil.reiniciaParaPruebas() }

    private fun espera(n: Int) {
        val hasta = System.currentTimeMillis() + 10_000
        while (CopiasMovil.de(ctx).lista().size < n && System.currentTimeMillis() < hasta) Thread.sleep(50)
    }

    @Test fun alSalirDeLaAppSeHaceUnaCopiaSiAlgoHaCambiado() {
        val e = Escritos.get(ctx)
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "La casa del lago") }
        CopiasMovil.alOcultarse(ctx)
        espera(1)
        val l = CopiasMovil.de(ctx).lista()
        assertEquals(1, l.size)
        // Dentro de la app (no en los archivos del móvil) y comprimida
        assertTrue(l[0].archivo.canonicalPath.startsWith(ctx.filesDir.canonicalPath))
        assertTrue(l[0].archivo.name.endsWith(".json.gz"))
        assertTrue(CopiasMovil.de(ctx).lee(l[0].archivo).any { it.optString("id") == o.id })
        // Sin cambios, al salir otra vez no hay otra
        CopiasMovil.alOcultarse(ctx)
        Thread.sleep(1500)
        assertEquals(1, CopiasMovil.de(ctx).lista().size)
        // Con un cambio, sí (en otro segundo: el nombre lleva la hora)
        Thread.sleep(1000)
        e.edita(o.id) { put("sinopsis", "Vuelve.") }
        CopiasMovil.alOcultarse(ctx)
        espera(2)
        assertEquals(2, CopiasMovil.de(ctx).lista().size)
        assertEquals(40, CopiasMovil.GUARDA)
    }
}
