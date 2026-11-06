package com.gonzalo.cobalto.pc

import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.NubeEscritos
import com.gonzalo.cobalto.SprintEscribir
import org.json.JSONObject
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.Red
import com.gonzalo.familia.nucleo.RedJava
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Cobalto del ordenador con la cuenta de verdad (el emulador de Firebase con las reglas del repositorio). «El móvil» es otro [Escritos]
 * con su propia sesión: es el mismo código que compila Cobalto de Android (Escritos.kt y EscribirMezcla.kt se comparten tal cual).
 */
class CuadernoTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var otra: Familia
    private lateinit var movil: Escritos

    /** La cuenta de otro dispositivo, para su [Escritos] (como CuentaEscritos en Android). */
    private class NubeDe(val f: Familia) : NubeEscritos {
        val red = RedJava()
        override val vinculada get() = f.sesion.dentro
        override val uid get() = f.sesion.uid
        override fun db(metodo: String, ruta: String, cuerpo: String?, consulta: String): String? {
            val r = red.pedir(metodo, f.sesion.url(ruta, consulta.removePrefix("&"), conPase = true), cuerpo)
            if (r.codigo !in 200..299) throw java.io.IOException("La cuenta respondió ${r.codigo}: ${r.cuerpo}")
            return r.cuerpo.trim().takeIf { it.isNotEmpty() && it != "null" }
        }
    }

    @Before fun prepara() {
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        otra = Emulador.dispositivo("gonzalo")
        movil = Escritos(File(tmp.root, "movil"), NubeDe(otra)) { "cobalto-movil1" }
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg
        LaFamilia.dirPruebas = File(tmp.root, "pc")
        LaFamilia.llaveroPruebas = LlaveroMemoria()
        LaFamilia.inicia("cobalto").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Cuaderno.reiniciaParaPruebas()
        Cuaderno.dirPruebas = File(tmp.root, "pc")
        Cuaderno.dispositivo = { "cobalto-pc1" }
    }

    @After fun fuera() { Cuaderno.reiniciaParaPruebas(); Cuaderno.dirPruebas = null; LaFamilia.reiniciaParaPruebas() }

    private val pc get() = Cuaderno.escritos
    private fun sincroniza() = runBlocking { pc.sincroniza(); movil.sincroniza(); pc.sincroniza() }

    @Test fun loQueEscribesAquiEstaEnElMovilYAlReves() {
        val obra = pc.nuevo(Escritos.T_OBRA, "") { put("titulo", "La casa del lago"); put("autor", "Gonzalo") }
        val cap = pc.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "Uno") }
        val esc = pc.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", cap.id); put("titulo", "Llegada"); put("texto", "Llovía sobre el lago.") }
        sincroniza()
        // En la cuenta, con el formato de siempre
        val uid = otra.sesion.uid
        val doc = Emulador.lee("/users/$uid/escritos/${esc.id}").comoMapa()
        assertEquals("e", doc["t"]); assertEquals(cap.id, doc["cid"]); assertEquals("Llovía sobre el lago.", doc["texto"])
        assertNotNull(Emulador.lee("/users/$uid/escritosIdx/${esc.id}"))
        // El móvil lo tiene
        assertEquals("Llovía sobre el lago.", movil.get(esc.id)!!.s("texto"))
        assertEquals("La casa del lago", movil.obras().single().s("titulo"))
        // Y lo que se escribe en el móvil llega aquí
        movil.escribe(esc.id, "Llovía sobre el lago.\n\nElla bajó del coche.")
        sincroniza()
        assertEquals("Llovía sobre el lago.\n\nElla bajó del coche.", pc.get(esc.id)!!.s("texto"))
    }

    @Test fun escribirALaVezParrafosDistintosNoPierdeNada() {
        val obra = pc.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra") }
        val cap = pc.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "Uno") }
        val esc = pc.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", cap.id); put("texto", "Primero.\n\nSegundo.\n\nTercero.") }
        sincroniza()
        // Sin conexión entre ellos: el ordenador cambia el primer párrafo y el móvil el tercero
        pc.escribe(esc.id, "Primero, en el ordenador.\n\nSegundo.\n\nTercero.")
        movil.escribe(esc.id, "Primero.\n\nSegundo.\n\nTercero, en el móvil.")
        runBlocking { pc.sincroniza(); movil.sincroniza(); pc.sincroniza(); movil.sincroniza() }
        val esperado = "Primero, en el ordenador.\n\nSegundo.\n\nTercero, en el móvil."
        assertEquals(esperado, pc.get(esc.id)!!.s("texto"))
        assertEquals(esperado, movil.get(esc.id)!!.s("texto"))
        // Lo mismo frase contra frase: el más nuevo se queda y el otro texto se guarda como borrador (no se pierde)
        pc.escribe(esc.id, "Primero, en el despacho.\n\nSegundo.\n\nTercero, en el móvil.")
        Thread.sleep(5)
        movil.escribe(esc.id, "Primero, en la cocina.\n\nSegundo.\n\nTercero, en el móvil.")
        runBlocking { pc.sincroniza(); movil.sincroniza(); pc.sincroniza(); movil.sincroniza() }
        assertEquals(pc.get(esc.id)!!.s("texto"), movil.get(esc.id)!!.s("texto"))
        val borradores = pc.borradores(esc.id) + movil.borradores(esc.id)
        assertTrue("el texto que no se queda, como borrador", borradores.any { it.b("conflicto") })
    }

    @Test fun borrarAquiLoMandaALaPapeleraDelMovilYSeRecupera() {
        val obra = pc.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra") }
        val cap = pc.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "Uno") }
        val esc = pc.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", cap.id); put("texto", "Algo.") }
        sincroniza()
        pc.borra(cap.id)
        sincroniza()
        assertNull(movil.get(cap.id)); assertNull(movil.get(esc.id))
        assertTrue(movil.papelera().any { it.id == cap.id })
        movil.restaura(cap.id)
        sincroniza()
        assertEquals("Algo.", pc.get(esc.id)!!.s("texto"))
    }

    @Test fun loTerminadoSePublicaParaMarfilYAzabache() {
        val obra = pc.nuevo(Escritos.T_OBRA, "") { put("titulo", "La casa del lago"); put("autor", "Gonzalo") }
        val cap = pc.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "Uno") }
        pc.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", cap.id); put("texto", "Llovía sobre el lago.") }
        pc.registra(obra.id, 4)
        listOf("obras", "capitulos", "palabras").forEach { LaFamilia.coleccion(it); otra.coleccion(it) }
        // Aún sin terminar: la obra sí, el capítulo no
        Publica.todo(pc, "cobalto-pc1", ahora = System.currentTimeMillis() + 60_000)
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otra.sincroniza().ok)
        val o = otra.coleccion("obras").nodo(obra.id)!!
        assertEquals("La casa del lago", o.texto("t")); assertEquals("en_curso", o.texto("estado")); assertEquals(4L, o.entero("palabras"))
        assertNull(otra.coleccion("capitulos").nodo("${obra.id}~${cap.id}"))
        assertEquals(4L, otra.coleccion("palabras").nodo("cobalto-pc1")!!.entero("d" + java.time.LocalDate.ofEpochDay(Escritos.hoy())))
        // Lo terminas: Marfil tiene su copia de solo lectura
        pc.edita(cap.id) { put("hecho", 1) }
        Publica.todo(pc, "cobalto-pc1", ahora = System.currentTimeMillis() + 60_000)
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otra.sincroniza().ok)
        val c = otra.coleccion("capitulos").nodo("${obra.id}~${cap.id}")!!
        assertEquals("Llovía sobre el lago.", c.texto("x")); assertEquals(obra.id, c.texto("ref")); assertEquals(1L, c.entero("n")); assertEquals(4L, c.entero("p"))
    }

    @Test fun losSprintsQueGuardabaEsteOrdenadorPasanALaCuentaUnaVez() {
        // Lo que guardaba Cobalto 1.1.0 en sprints.json (el formato del móvil): dos terminados y uno en marcha
        val ahora = System.currentTimeMillis()
        Local.graba(SprintsDeAntes.ARCHIVO, JSONObject()
            .put("sprints", org.json.JSONArray().put(JSONObject().put("pid", "p_a").put("desde", ahora - 86_400_000L).put("min", 25).put("pal", 640).put("meta", 500))
                .put(JSONObject().put("pid", "p_a").put("desde", ahora - 3 * 86_400_000L).put("min", 15).put("pal", 310).put("meta", 0)))
            .put("sprint", JSONObject().put("pid", "p_a").put("desde", ahora - 60_000L).put("hasta", ahora + 600_000L).put("base", 0).put("dia", Escritos.hoy()).put("meta", 0).put("min", 11)))
        SprintEscribir.reiniciaParaPruebas(); SprintsDeAntes.reiniciaParaPruebas(); SprintEscribir.cuenta = CuentaSprintsPC
        try {
            // Hasta que no se ha bajado lo que ya había en la cuenta, se espera
            assertEquals(0, SprintsDeAntes.migra())
            LaFamilia.coleccion("sprints"); assertTrue(LaFamilia.sincronizaYa().ok)
            assertEquals(3, SprintsDeAntes.migra())
            assertEquals(0, SprintsDeAntes.migra())
            assertTrue(LaFamilia.sincronizaYa().ok)
            // El móvil (el otro dispositivo de la cuenta) los tiene: los dos terminados en su historial y el que está en marcha
            otra.coleccion("sprints"); assertTrue(otra.sincroniza().ok)
            val enLaCuenta = otra.coleccion("sprints").vivos()
            assertEquals(3, enLaCuenta.size)
            assertEquals(setOf(640L, 310L, 0L), enLaCuenta.map { it.entero("pal") }.toSet())
            assertTrue(enLaCuenta.all { it.texto("dev") == "cobalto-pc1" && it.texto("ref") == "p_a" })
            // Y aquí sigue en marcha el de antes, ahora desde la cuenta
            SprintEscribir.mira(pc)
            assertTrue(SprintEscribir.activo("p_a")); assertEquals(11, SprintEscribir.minutos)
            assertEquals(2, SprintEscribir.historial("p_a").size)
            // El archivo se queda como copia, apuntando a qué cuenta pasó: al volver a abrir la app no se repite nada
            assertEquals(LaFamilia.uid, Local.lee(SprintsDeAntes.ARCHIVO).optString("enCuenta"))
            assertTrue(Local.lee(SprintsDeAntes.ARCHIVO).has("sprints"))
            SprintsDeAntes.reiniciaParaPruebas()
            assertEquals(0, SprintsDeAntes.migra())
        } finally { SprintEscribir.reiniciaParaPruebas(); SprintEscribir.cuenta = com.gonzalo.cobalto.CuentaSprints.Ninguna }
    }

    @Test fun lasCopiasDeSeguridadLlevanTodo() {
        Cuaderno.dirPruebas = File(tmp.root, "pc")
        val obra = pc.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra") }
        val f = Copias.haz(pc, "prueba")!!
        assertTrue(f.readText().contains(obra.id))
    }
}
