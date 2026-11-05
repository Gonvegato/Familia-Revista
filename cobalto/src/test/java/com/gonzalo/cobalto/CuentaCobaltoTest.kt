package com.gonzalo.cobalto

import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Lo que Cobalto publica en la cuenta de la familia para Marfil y Azabache ([Publicar.todo]), con la cuenta de verdad (el emulador de
 * Firebase con las reglas del repositorio): las obras, los capítulos terminados y las palabras de cada día.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CuentaCobaltoTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var otro: Familia

    @Before fun prepara() {
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.configPruebas = Emulador.cfg
        Escritos.reiniciaParaPruebas(); File(ctx.filesDir, "escritos").deleteRecursively()
        Cuenta.cargar(ctx)
        runBlocking { Cuenta.entrar("gonzalo", "secreto1") }
        otro = Emulador.dispositivo("gonzalo")
        listOf("obras", "capitulos", "palabras").forEach { otro.coleccion(it); LaFamilia.coleccion(it) }
    }

    @After fun suelta() { Escritos.reiniciaParaPruebas(); LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null }

    @Test fun unCapituloTerminadoLlegaAMarfilYLasPalabrasDelDiaTambien() {
        val e = Escritos.get(ctx)
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "La casa del lago"); put("autor", "Gonzalo") }
        val cap = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "Uno"); put("hecho", 1) }
        e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", cap.id); put("texto", "Llovía sobre el lago.") }
        e.registra(obra.id, 4)
        // (lo escrito hace un momento espera a estar quieto: se publica pasado ese rato)
        Thread.sleep(50)
        var diferido = Publicar.todo(ctx)
        if (diferido) { Thread.sleep(Publicar.QUIETO + 200); diferido = Publicar.todo(ctx) }
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok)
        val c = otro.coleccion("capitulos").nodo("${obra.id}~${cap.id}")
        assertTrue("el capítulo terminado está en la cuenta", c != null)
        assertEquals("Llovía sobre el lago.", c!!.texto("x")); assertEquals(obra.id, c.texto("ref"))
        assertTrue("con su última edición (ue)", c.entero("ue") > 0)
        assertEquals("en_curso", otro.coleccion("obras").nodo(obra.id)!!.texto("estado"))
        assertEquals(4L, otro.coleccion("palabras").nodo(Cuenta.dispositivo)!!.entero("d" + java.time.LocalDate.ofEpochDay(Escritos.hoy())))
        // Quitarle la marca de terminado lo quita de la cuenta
        e.edita(cap.id) { remove("hecho") }
        Publicar.todo(ctx)
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok)
        assertNull(otro.coleccion("capitulos").nodo("${obra.id}~${cap.id}")?.takeIf { !it.borrado })
    }
}
