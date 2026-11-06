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

    @Test fun losSprintsQueGuardabaEsteMovilPasanALaCuentaUnaVez() {
        // Lo que guardaba Cobalto 0.4.1 en sus preferencias («escribir»: «sprints», los terminados)
        val ahora = System.currentTimeMillis()
        val p = ctx.getSharedPreferences("escribir", android.content.Context.MODE_PRIVATE)
        p.edit().putString("sprints", org.json.JSONArray()
            .put(org.json.JSONObject().put("pid", "p_a").put("desde", ahora - 86_400_000L).put("min", 25).put("pal", 640).put("meta", 500))
            .put(org.json.JSONObject().put("pid", "p_b").put("desde", ahora - 2 * 86_400_000L).put("min", 10).put("pal", 120).put("meta", 0)).toString()).commit()
        SprintEscribir.reiniciaParaPruebas(); SprintsDeAntes.reiniciaParaPruebas(); SprintEscribir.cuenta = CuentaSprintsMovil
        try {
            LaFamilia.coleccion("sprints"); assertTrue(LaFamilia.sincronizaYa().ok)
            assertEquals(2, SprintsDeAntes.migra(ctx))
            assertEquals(0, SprintsDeAntes.migra(ctx))
            assertTrue(LaFamilia.sincronizaYa().ok)
            // El ordenador (el otro dispositivo de la cuenta) los tiene, con este móvil como su dispositivo
            otro.coleccion("sprints"); assertTrue(otro.sincroniza().ok)
            val n = otro.coleccion("sprints").nodo("p_a~${ahora - 86_400_000L}")!!
            assertEquals(640L, n.entero("pal")); assertEquals(25L, n.entero("min")); assertEquals(500L, n.entero("meta")); assertEquals(Cuenta.dispositivo, n.texto("dev"))
            assertEquals(2, otro.coleccion("sprints").vivos().size)
            // Y aquí se ven en Cifras (de la cuenta)
            assertEquals(listOf(640), SprintEscribir.historial("p_a").map { it.palabras })
            // Lo de antes se queda como copia; al volver a abrir la app no se repite nada
            assertEquals(Cuenta.uid, p.getString("sprints_en_cuenta", ""))
            assertTrue(p.contains("sprints"))
            SprintsDeAntes.reiniciaParaPruebas()
            assertEquals(0, SprintsDeAntes.migra(ctx))
        } finally { SprintEscribir.reiniciaParaPruebas(); SprintEscribir.cuenta = CuentaSprints.Ninguna }
    }

    @Test fun tuParejaEsLaDeLaCuentaEnTodosTusDispositivos() {
        val yo = Cuenta.uid
        val ella = Emulador.cuenta("lucia")
        // (Lucía te deja leer su cuenta y tiene su nombre puesto en Azabache; su usuario es «lucia»)
        Emulador.escribe("/users/$ella/partner", yo); Emulador.escribe("/users/$ella/name", "Lucía"); Emulador.escribe("/usernames/lucia", ella)
        // Hasta la 0.4.1 se guardaba solo en este móvil: si la cuenta no tenía ninguna, pasa a ella (una vez)
        ctx.getSharedPreferences("cobalto_cuenta", android.content.Context.MODE_PRIVATE).edit()
            .putString("parejaUid", ella).putString("parejaUsuario", "lucia").remove("parejaEnCuenta").commit()
        Cuenta.cargar(ctx)
        assertEquals(ella, Cuenta.parejaUid)
        Cuenta.sincronizaPareja()
        assertEquals(ella, Emulador.lee("/users/$yo/partner"))
        // Si se quita en otro sitio (Azabache, otro móvil), aquí también
        Emulador.escribe("/users/$yo/partner", "")
        Cuenta.sincronizaPareja()
        assertEquals("", Cuenta.parejaUid)
        // Si se pone en otro sitio, aquí aparece, con su nombre
        Emulador.escribe("/users/$yo/partner", ella)
        Cuenta.sincronizaPareja()
        assertEquals(ella, Cuenta.parejaUid); assertEquals("Lucía", Cuenta.parejaUsuario)
        // Quitarla aquí la quita de la cuenta (vacío, nunca null)…
        runBlocking { Cuenta.quitarPareja() }
        assertEquals("", Emulador.lee("/users/$yo/partner")); assertEquals("", Cuenta.parejaUid)
        // …y ponerla aquí, la pone
        runBlocking { Cuenta.ponPareja("lucia") }
        assertEquals(ella, Emulador.lee("/users/$yo/partner")); assertEquals(ella, Cuenta.parejaUid)
        // Salir de la cuenta la olvida en este móvil, pero no la quita de la cuenta
        Cuenta.salir()
        assertEquals("", Cuenta.parejaUid); assertEquals(ella, Emulador.lee("/users/$yo/partner"))
    }
}
