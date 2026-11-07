package com.gonzalo.familia.nucleo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * La sincronización de verdad: varios dispositivos (y varias apps) con la misma cuenta, contra el emulador oficial de Firebase
 * con las reglas de `nube/reglas.json`. Lo que pasa aquí es lo que pasará con tu cuenta.
 */
class SincroTest {
    private lateinit var uid: String

    @Before fun prepara() {
        Emulador.limpia()
        uid = Emulador.cuenta("gonzalo")
    }

    @Test fun loQueCambiaUnMovilLlegaAlOtro() {
        val a = Emulador.dispositivo("gonzalo")
        val b = Emulador.dispositivo("gonzalo")
        b.coleccion("libros")
        a.coleccion("libros").pon("l1", "t" to "El nombre del viento", "estado" to "en_curso")
        assertEquals("en_curso", a.coleccion("libros").nodo("l1")!!.texto("estado"))   // se ve al momento, antes de subir
        assertTrue(a.sincroniza().ok)
        assertTrue(b.sincroniza().ok)
        val l = b.coleccion("libros").nodo("l1")!!
        assertEquals("El nombre del viento", l.texto("t"))
        assertEquals("en_curso", l.texto("estado"))
        assertEquals(0, a.pendientes)
    }

    @Test fun apagarUnAjusteQueNuncaSeHabiaGuardadoLlega() {
        // Un ajuste encendido de serie («el episodio siguiente, solo») que nunca se había escrito: apagarlo tiene que llegar al otro
        val a = Emulador.dispositivo("gonzalo")
        val b = Emulador.dispositivo("gonzalo")
        b.coleccion("ajustes")
        a.coleccion("ajustes").pon("app_reproductor", "subs" to "spa")
        a.coleccion("ajustes").cambia("app_reproductor", mapOf("siguiente" to false, "subs" to "spa"))
        assertTrue(a.sincroniza().ok); assertTrue(b.sincroniza().ok)
        val n = b.coleccion("ajustes").nodo("app_reproductor")!!
        assertTrue("el campo está, apagado", "siguiente" in n.campos)
        assertEquals(false, n.si("siguiente"))
        // (y quitar algo que no está no sube nada)
        a.coleccion("ajustes").cambia("app_reproductor", mapOf("audio" to ""))
        assertEquals(0, a.pendientes)
    }

    @Test fun quienEscuchaUnaColeccionLaSigueOyendoTrasSalirYVolverAEntrar() {
        val a = Emulador.dispositivo("gonzalo")
        val b = Emulador.dispositivo("gonzalo")
        val libros = a.coleccion("libros")
        var oidos = 0
        libros.alCambiar { oidos++ }
        b.coleccion("libros").pon("l1", "t" to "Dune")
        assertTrue(b.sincroniza().ok); assertTrue(a.sincroniza().ok)
        assertEquals("Dune", libros.nodo("l1")?.texto("t"))
        // Sales de la cuenta (y vuelves a entrar): lo de antes se olvida, pero es la misma colección y quien la escuchaba la sigue oyendo
        a.olvidaTodo()
        assertNull("olvidado", libros.nodo("l1"))
        assertSame(libros, a.coleccion("libros"))
        oidos = 0
        b.coleccion("libros").pon("l2", "t" to "Hyperion")
        assertTrue(b.sincroniza().ok); assertTrue(a.sincroniza().ok)
        assertTrue("se oye lo que llega después", oidos > 0)
        assertEquals("Hyperion", libros.nodo("l2")?.texto("t")); assertEquals("y lo de antes vuelve de la cuenta", "Dune", libros.nodo("l1")?.texto("t"))
    }

    @Test fun camposDistintosDeLaMismaCosaNoSePisan() {
        val hiedra = Emulador.dispositivo("gonzalo")      // el estado lo cambia una app…
        val azabache = Emulador.dispositivo("gonzalo")    // …y la nota otra, a la vez y sin haberse visto
        hiedra.coleccion("juegos").pon("j1", "t" to "Hades", "estado" to "terminado")
        azabache.coleccion("juegos").pon("j1", "nota" to 92L)
        assertTrue(azabache.sincroniza().ok)
        assertTrue(hiedra.sincroniza().ok)
        assertTrue(azabache.sincroniza().ok)
        for (d in listOf(hiedra, azabache)) {
            val j = d.coleccion("juegos").nodo("j1")!!
            assertEquals("terminado", j.texto("estado"))
            assertEquals(92L, j.entero("nota"))
            assertEquals("Hades", j.texto("t"))
        }
    }

    @Test fun ganaElCambioMasRecienteAunqueLlegueAntes() {
        val viejo = Emulador.dispositivo("gonzalo")
        val nuevo = Emulador.dispositivo("gonzalo")
        // El viejo cambia el estado sin red (hace una hora) y no lo sube…
        viejo.reloj.desfase = -3_600_000L
        viejo.coleccion("libros").pon("l1", "estado" to "terminado", "fav" to true)
        // …el nuevo lo cambia ahora y lo sube
        nuevo.coleccion("libros").pon("l1", "estado" to "en_curso")
        assertTrue(nuevo.sincroniza().ok)
        // Al volver la red, el viejo sube lo suyo: el estado se rechaza (es más viejo), el favorito sí entra
        val inf = viejo.sincroniza()
        assertTrue(inf.toString(), inf.ok)
        assertEquals(1, inf.rechazados)
        assertEquals("en_curso", viejo.coleccion("libros").nodo("l1")!!.texto("estado"))
        assertTrue(viejo.coleccion("libros").nodo("l1")!!.si("fav"))
        assertTrue(nuevo.sincroniza().ok)
        assertEquals("en_curso", nuevo.coleccion("libros").nodo("l1")!!.texto("estado"))
        assertTrue(nuevo.coleccion("libros").nodo("l1")!!.si("fav"))
        assertEquals(0, viejo.pendientes)
    }

    @Test fun lasReglasNoDejanEscribirSinHoraOConUnaVieja() {
        val a = Emulador.dispositivo("gonzalo")
        a.coleccion("libros").pon("l1", "estado" to "en_curso")
        assertTrue(a.sincroniza().ok)
        val pase = a.sesion.paseValido()
        val url = "${Emulador.db}/users/$uid/familia.json?${Emulador.ns}&auth=$pase"
        assertNotEquals(200, Emulador.pide("PATCH", url, """{"libros/l1/estado":"terminado"}""").first)
        assertNotEquals(200, Emulador.pide("PATCH", url, """{"libros/l1/estado":"terminado","libros/l1/m/estado":5}""").first)
        assertEquals("en_curso", (Emulador.lee("/users/$uid/familia/libros/l1/estado")))
    }

    @Test fun loPendienteSobreviveACerrarLaApp() {
        val disco = DiscoMemoria(); val seguro = DiscoMemoria()
        val antes = Emulador.dispositivo("gonzalo", disco, seguro)
        antes.coleccion("obras").pon("p1", "t" to "La novela", "palabras" to 1200L)
        assertEquals(1, antes.pendientes)
        // Se cierra la app sin subir; al abrirla, todo sigue ahí (sin volver a entrar en la cuenta)
        val despues = Familia(Emulador.cfg, RedJava(), disco, seguro).also { it.automatico = false }
        assertTrue(despues.sesion.dentro)
        assertEquals(1, despues.pendientes)
        assertEquals("La novela", despues.coleccion("obras").nodo("p1")!!.texto("t"))
        assertTrue(despues.sincroniza().ok)
        assertEquals(1200L, Emulador.lee("/users/$uid/familia/obras/p1/palabras").comoLong())
        // Y lo bajado se queda también: sin red se ve todo
        val sinRed = Familia(Emulador.cfg.copy(baseDatos = "http://127.0.0.1:9"), RedJava(), disco, seguro).also { it.automatico = false }
        assertEquals("La novela", sinRed.coleccion("obras").nodo("p1")!!.texto("t"))
        assertFalse(sinRed.sincroniza().ok)
    }

    @Test fun soloSeBajaLoQueCambio() {
        val a = Emulador.dispositivo("gonzalo")
        val b = Emulador.dispositivo("gonzalo")
        val libros = a.coleccion("libros"); b.coleccion("libros")
        for (i in 1..40) libros.pon("l$i", "t" to "Libro $i")
        assertTrue(a.sincroniza().ok)
        assertEquals(40, b.sincroniza().let { b.coleccion("libros").vivos().size })
        a.coleccion("libros").pon("l7", "estado" to "terminado")
        assertTrue(a.sincroniza().ok)
        val inf = b.sincroniza()
        assertTrue(inf.ok)
        assertTrue("bajó ${inf.bajados}, debería ser poco", inf.bajados in 1..5)
        assertEquals("terminado", b.coleccion("libros").nodo("l7")!!.texto("estado"))
        // Sin cambios: no baja nada
        assertEquals(0, b.sincroniza().bajados)
    }

    @Test fun borrarSeQuedaBorradoYSePuedeRecuperar() {
        val a = Emulador.dispositivo("gonzalo")
        val b = Emulador.dispositivo("gonzalo")
        a.coleccion("juegos").pon("j1", "t" to "Baldur's Gate 3")
        a.sincroniza(); b.sincroniza()
        b.coleccion("juegos").borra("j1")
        b.sincroniza(); a.sincroniza()
        assertTrue(a.coleccion("juegos").nodo("j1")!!.borrado)
        assertTrue(a.coleccion("juegos").vivos().isEmpty())
        a.coleccion("juegos").recupera("j1")
        a.sincroniza(); b.sincroniza()
        assertFalse(b.coleccion("juegos").nodo("j1")!!.borrado)
    }

    @Test fun lasPortadasSonLasMismasEnTodas() {
        val a = Emulador.dispositivo("gonzalo")
        val b = Emulador.dispositivo("gonzalo")
        b.coleccion("libros")
        val img = ByteArray(50_000) { (it * 31 % 251).toByte() }
        val h = a.imagenes.guarda(img, "image/jpeg")
        a.coleccion("libros").pon("l1", "portada" to h)
        assertTrue(a.sincroniza().ok)
        assertTrue(b.sincroniza().ok)
        val hb = b.coleccion("libros").nodo("l1")!!.texto("portada")
        assertEquals(h, hb)
        assertNull(b.imagenes.local(hb))
        assertArrayEquals(img, b.imagenes.baja(hb))
        assertArrayEquals(img, b.imagenes.local(hb))
        // La misma imagen otra vez no da error (ya existe y no cambia)
        a.imagenes.guarda(img, "image/jpeg")
        assertTrue(a.sincroniza().ok)
        assertEquals(0, a.pendientes)
    }

    @Test fun lasClavesSeGuardanUnaVezYTuParejaNoLasVe() {
        val azabache = Emulador.dispositivo("gonzalo")
        azabache.ponClave("kdrive_token", "tok-123")
        azabache.ponClave("steamgriddb", "sgdb-456")
        assertTrue(azabache.sincroniza().ok)
        val hiedra = Emulador.dispositivo("gonzalo")
        assertTrue(hiedra.sincroniza().ok)
        assertEquals("tok-123", hiedra.clave("kdrive_token"))
        assertEquals("sgdb-456", hiedra.clave("steamgriddb"))
        // La pareja lee tu cuenta…
        val parejaUid = Emulador.cuenta("pareja")
        Emulador.escribe("/users/$uid/partner", parejaUid)
        val pareja = Emulador.dispositivo("pareja")
        val pase = pareja.sesion.paseValido()
        assertEquals(200, Emulador.pide("GET", "${Emulador.db}/users/$uid/familia.json?${Emulador.ns}&auth=$pase").first)
        // …pero no tus claves
        assertNotEquals(200, Emulador.pide("GET", "${Emulador.db}/claves/$uid.json?${Emulador.ns}&auth=$pase").first)
    }

    @Test fun laParejaSeLeeSinPoderTocarla() {
        val yo = Emulador.dispositivo("gonzalo")
        yo.coleccion("libros").pon("l1", "t" to "Rayuela", "nota" to 88L)
        yo.sincroniza()
        val parejaUid = Emulador.cuenta("pareja")
        Emulador.escribe("/users/$uid/partner", parejaUid)
        val pareja = Emulador.dispositivo("pareja")
        val mios = pareja.ajena(uid, "libros")
        assertTrue(pareja.sincroniza().ok)
        assertEquals("Rayuela", mios.nodo("l1")!!.texto("t"))
        assertEquals(88L, mios.nodo("l1")!!.entero("nota"))
        try { mios.pon("l1", "nota" to 1L); error("debería ser de solo lectura") } catch (e: IllegalStateException) { }
    }

    @Test fun alMomentoPorElFlujoDeSellos() {
        val a = Emulador.dispositivo("gonzalo")
        val b = Emulador.dispositivo("gonzalo")
        b.coleccion("libros")   // abierta: la escucha la tiene en cuenta
        b.escucha()
        try {
            Thread.sleep(1500)
            a.coleccion("libros").pon("l9", "t" to "Ficciones")
            assertTrue(a.sincroniza().ok)
            val hasta = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < hasta && b.coleccion("libros").nodo("l9") == null) Thread.sleep(200)
            assertEquals("Ficciones", b.coleccion("libros").nodo("l9")?.texto("t"))
        } finally { b.deja() }
    }

    @Test fun muchosCambiosDeGolpe() {
        val a = Emulador.dispositivo("gonzalo")
        val t = a.coleccion("tiempo")
        for (i in 1..250) t.pon("l$i~dev1", "d2026-11-0${i % 9 + 1}" to i * 1000L)
        val inf = a.sincroniza()
        assertTrue(inf.toString(), inf.ok)
        assertEquals(250, inf.subidos)
        assertEquals(250, Emulador.lee("/users/$uid/familia/tiempo").comoMapa().size)
    }

    @Test fun elPaseCaducadoSeRenuevaSolo() {
        val a = Emulador.dispositivo("gonzalo")
        a.sesion.paseCaducado()
        a.coleccion("libros").pon("l1", "t" to "x")
        assertTrue(a.sincroniza().ok)
        assertEquals("x", Emulador.lee("/users/$uid/familia/libros/l1/t"))
    }

    @Test fun contrasenaMalaNoEntra() {
        val f = Familia(Emulador.cfg, RedJava(), DiscoMemoria())
        try { f.sesion.entrar("gonzalo", "otra"); error("no debería entrar") } catch (e: Sesion.Error) { assertTrue(e.message!!, e.message!!.contains("incorrect")) }
        assertFalse(f.sesion.dentro)
    }
}
