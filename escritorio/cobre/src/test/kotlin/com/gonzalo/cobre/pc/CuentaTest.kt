package com.gonzalo.cobre.pc

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.IptvError
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Progreso
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.Respuesta
import com.gonzalo.iptv.TipoLista
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Lo de Cobre del ordenador en la cuenta, con la cuenta de verdad (el emulador de Firebase con las reglas del repositorio) y «el móvil» (otro
 * dispositivo de la cuenta) escribiendo y leyendo con el formato de Android (`nube/ESQUEMA.md`): los mismos nodos y campos, uno a uno, que
 * prueban `CuentaTest`, `SecretosTest` y `AjustesCuentaTest` de Cobre del móvil. Las listas, solo en `/claves`; nada secreto en `/users` ni en
 * claro en el disco de este ordenador.
 */
class CuentaTest : ConCuenta() {
    @Before fun prepara() { preparaCuenta() }
    @After fun fin() { suelta() }

    private val l1 = Canal("x101", "La 1 HD", "Nacionales", idXtream = 101)
    private val l2 = Canal("c0123456789abcdef", "Antena 3", "Nacionales", url = "http://tv.ejemplo.test/live/pepe/S3cr3t/2.ts")
    private val l3 = Canal("x205", "DAZN LaLiga", "Deportes", idXtream = 205)

    @Test fun losFavoritosYSuOrdenViajan() {
        MiCuenta.alternaFavorito("lprueba001", l1); MiCuenta.alternaFavorito("lprueba001", l2); MiCuenta.alternaFavorito("lprueba001", l3)
        assertEquals(listOf("x101", "c0123456789abcdef", "x205"), MiCuenta.favoritos().map { it.canal })
        sincroniza()
        val col = otro.coleccion(DatosCuenta.FAVORITOS)
        val n = col.nodo("lprueba001~x101")!!
        assertEquals("lprueba001", n.texto("lista")); assertEquals("x101", n.texto("canal")); assertEquals("La 1 HD", n.texto("n")); assertEquals(1L, n.entero("o"))
        // El orden: DAZN arriba del todo
        MiCuenta.mueveFavorito("lprueba001~x205", -1); MiCuenta.mueveFavorito("lprueba001~x205", -1)
        assertEquals(listOf("x205", "x101", "c0123456789abcdef"), MiCuenta.favoritos().map { it.canal })
        sincroniza()
        assertEquals(listOf("x205", "x101", "c0123456789abcdef"), col.vivos().sortedBy { it.entero("o") }.map { it.texto("canal") })
        // Quitar es «b» (el nodo se queda); y en el otro dispositivo lo vuelves a poner: vuelve aquí
        MiCuenta.alternaFavorito("lprueba001", l1)
        sincroniza()
        assertTrue(col.nodo("lprueba001~x101")!!.borrado)
        otro.coleccion(DatosCuenta.FAVORITOS).pon("lprueba001~x101", mapOf("b" to 0L, "o" to 9L))
        sincroniza()
        espera("vuelve") { MiCuenta.esFavorito("lprueba001", "x101") }
        // En la cuenta, nada de direcciones (la de Antena 3 lleva la contraseña)
        assertFalse(Json.escribe(Emulador.lee("/users/$uid")).contains("S3cr3t"))
    }

    @Test fun losGruposEscondidosYElUltimoCanal() {
        MiCuenta.ocultaGrupo("lprueba001", "ES | Deportes / Fútbol.", true)
        MiCuenta.ponUltimo("lprueba001", l3)
        sincroniza()
        val g = otro.coleccion(DatosCuenta.GRUPOS).vivos().single()
        assertEquals("ES | Deportes / Fútbol.", g.texto("g")); assertTrue(g.si("oculto")); assertEquals("lprueba001", g.texto("lista"))
        assertEquals("el mismo nodo que el móvil", DatosCuenta.refGrupo("lprueba001", "ES | Deportes / Fútbol."), g.ref)
        val u = otro.coleccion(DatosCuenta.ESTADO).nodo("ultimo")!!
        assertEquals("x205", u.texto("canal")); assertEquals("DAZN LaLiga", u.texto("n")); assertTrue(u.entero("ul") > 0)
        // Volver a enseñarlo, desde el otro dispositivo
        otro.coleccion(DatosCuenta.GRUPOS).pon(g.ref, mapOf("oculto" to false))
        sincroniza()
        espera("se ve otra vez") { MiCuenta.ocultos("lprueba001").isEmpty() }
        // El último canal que ves en el otro dispositivo, aquí
        otro.coleccion(DatosCuenta.ESTADO).pon("ultimo", DatosCuenta.camposUltimo("lprueba001", l1, System.currentTimeMillis() + 1000))
        sincroniza()
        espera("el último, el del otro") { MiCuenta.ultimo()?.canal == "x101" }
    }

    @Test fun loVistoYSeguirViendo() {
        Listas.pon(Lista("lprueba001", "Mi servidor", TipoLista.XTREAM, "http://tv.ejemplo.test:8080", "pepe", "S3cr3t"))
        val ahora = System.currentTimeMillis()
        MiCuenta.ponProgreso(Progreso("lprueba001", "p5001", 5001, "mkv", "Dune: Parte dos", pos = 40 * 60_000L, dur = 166 * 60_000L, ultima = ahora - 10_000))
        MiCuenta.ponProgreso(Progreso("lprueba001", "e9011", 9011, "mkv", "Se acerca el invierno", 3001, "Juego de tronos", 1, 1, 57 * 60_000L, 57 * 60_000L, true, ahora - 5_000))
        sincroniza()
        val p = otro.coleccion(DatosCuenta.PROGRESO).nodo("lprueba001~e9011")!!
        assertEquals("episodio", p.texto("tipo")); assertEquals(3001L, p.entero("serie")); assertEquals("Juego de tronos", p.texto("st")); assertTrue(p.si("visto")); assertEquals(1L, p.entero("temp"))
        assertEquals(listOf("e9011", "p5001"), MiCuenta.seguir().map { it.video })
        // En la tablet sigues la película hasta el minuto 120: aquí se sigue desde ahí (lo más reciente gana entre lo de la cuenta y lo de aquí)
        Reanudar.pon("lprueba001", "p5001", 50 * 60_000L, 166 * 60_000L, ahora - 1_000)
        otro.coleccion(DatosCuenta.PROGRESO).pon("lprueba001~p5001", mapOf("pos" to 120 * 60_000L, "ul" to ahora + 60_000))
        sincroniza()
        espera("llega") { MiCuenta.progreso("lprueba001", "p5001")?.pos == 120 * 60_000L }
        assertEquals(120 * 60_000L, Reanudar.desde("lprueba001", "p5001"))
        assertEquals("la película, ahora la primera", "p5001", MiCuenta.seguir().first().video)
        // Vista (al 90 %, o a mano): sale de «Seguir viendo»
        MiCuenta.marcaVisto(MiCuenta.progreso("lprueba001", "p5001")!!, true)
        assertEquals(listOf("e9011"), MiCuenta.seguir().map { it.video })
        // Lo de una lista que ya no tienes, no sale
        Listas.quita("lprueba001")
        assertTrue(MiCuenta.seguir().isEmpty())
    }

    private val clave = "Pw-9f8e7d6c5b"
    private val token = "TOKEN-a1b2c3d4"
    private val xtream = Lista("lxtream0001", "Mi servidor", TipoLista.XTREAM, "http://tv.ejemplo.test:8080", "usuarioPepe", clave)
    private val m3u = Lista("lm3u0000001", "Mi lista", TipoLista.M3U, "http://lista.ejemplo.test/get.php?token=$token", epg = "http://epg.ejemplo.test/x.xml?token=$token")

    /** Una lista M3U cuyas direcciones llevan el usuario y la contraseña (como las de los servidores de Xtream). */
    private val red = object : Red {
        override fun abre(url: String, cabeceras: Map<String, String>): Respuesta {
            if (url.startsWith("http://lista.ejemplo.test/")) return Respuesta(200, ByteArrayInputStream("""
                #EXTM3U url-tvg="http://tv.ejemplo.test:8080/xmltv.php?username=usuarioPepe&password=$clave"
                #EXTINF:-1 tvg-id="La1.es" group-title="Nacionales",La 1
                http://tv.ejemplo.test:8080/live/usuarioPepe/$clave/101.ts
                #EXTINF:-1 tvg-id="La2.es" group-title="Nacionales",La 2
                http://tv.ejemplo.test:8080/live/usuarioPepe/$clave/102.ts
            """.trimIndent().toByteArray()))
            // Un servidor que contesta con la dirección en el mensaje: no debe llegar a ningún texto
            throw IptvError("403 en $url")
        }
    }

    @Test fun lasListasSoloVanAClavesYLlegan() {
        Listas.pon(xtream); Listas.pon(m3u)
        sincroniza()
        val claves = Json.escribe(Emulador.lee("/claves/$uid"))
        assertTrue(claves.contains(clave)); assertTrue(claves.contains(token))
        assertEquals(listOf(xtream, m3u), DatosCuenta.listasDeTexto(LaFamilia.familia.clave(DatosCuenta.CLAVE)))
        // Usar las listas (favoritos, el último canal) no pone nada secreto en /users
        Tele.red = red
        Tele.almacen.actualizaCanales(m3u)
        val c = Tele.cargada(m3u)!!.canales.first()
        MiCuenta.alternaFavorito(m3u.id, c); MiCuenta.ponUltimo(m3u.id, c)
        sincroniza()
        val users = Json.escribe(Emulador.lee("/users/$uid"))
        assertFalse("ni la contraseña", users.contains(clave)); assertFalse("ni el token", users.contains(token)); assertFalse("ni el usuario", users.contains("usuarioPepe"))
        assertTrue("(lo demás sí está)", users.contains("La 1"))
        // Tu otro dispositivo las tiene (de /claves), en el mismo orden
        espera("llegan al otro dispositivo") { otro.sincroniza(); DatosCuenta.listasDeTexto(otro.clave(DatosCuenta.CLAVE)).map { it.id } == listOf(xtream.id, m3u.id) }
        // Y una que pones en el móvil llega aquí
        val delMovil = Lista("lmovil00001", "La del móvil", TipoLista.ARCHIVO, dispositivo = "cobre-1a2b3c4d")
        otro.ponClave(DatosCuenta.CLAVE, DatosCuenta.listasATexto(DatosCuenta.listasDeTexto(otro.clave(DatosCuenta.CLAVE)) + delMovil))
        sincroniza()
        espera("llega del móvil") { Listas.lista().any { it.id == delMovil.id } }
        assertFalse("la de archivo de otro dispositivo, aquí no se usa", Listas.usables().any { it.id == delMovil.id })
        assertEquals(" · en otro dispositivo", descripcion(delMovil).substringAfter("Archivo M3U"))
    }

    @Test fun nadaEnClaroEnElOrdenadorNiEnLosErrores() {
        Listas.pon(m3u)
        Tele.red = red
        assertEquals(2, Tele.almacen.actualizaCanales(m3u))
        assertEquals(2, Tele.cargada(m3u)!!.canales.size)
        assertTrue("la guía de la cabecera, guardada (cifrada)", Tele.almacen.fuentesGuia(m3u).any { it.contains("xmltv.php") })
        // Un error del servidor (con la dirección dentro): el texto que se enseña, sin secretos
        val e = runCatching { Tele.almacen.actualizaGuia(m3u) }.exceptionOrNull()!!
        assertFalse(e.message!!.contains(clave)); assertFalse(e.message!!.contains(token)); assertFalse(e.message!!.contains("usuarioPepe"))
        sincroniza()
        // En el ordenador: ni en las cachés (cifradas con la clave del llavero), ni en lo de Cobre, ni en la copia de la cuenta
        val enClaro = dir.walkTopDown().filter { it.isFile }.joinToString("\n") { String(it.readBytes(), Charsets.ISO_8859_1) }
        assertFalse("la contraseña no está en claro", enClaro.contains(clave))
        assertFalse("el token no está en claro", enClaro.contains(token))
        // Ni en el texto de una lista ni en cómo se enseña (lo que saldría en un registro o en Ajustes por descuido)
        assertFalse(m3u.toString().contains(token)); assertFalse(xtream.toString().contains(clave))
        assertFalse(descripcion(m3u).contains(token)); assertFalse(descripcion(xtream).contains(clave)); assertFalse(descripcion(xtream).contains("usuarioPepe"))
        // Con otro llavero (otro usuario, otro ordenador), la caché no se lee
        Tele.olvidaParaPruebas(); LaFamilia.llaveroPruebas = com.gonzalo.familia.pc.LlaveroMemoria()
        assertTrue("sin la clave del llavero, nada", Tele.almacen.canales(m3u).second.isEmpty())
    }

    @Test fun tuParejaNoLasPuedeLeer() {
        Listas.pon(xtream)
        sincroniza()
        val suUid = Emulador.cuenta("lucia")
        Emulador.escribe("/users/$uid/partner", suUid)
        val lucia = Emulador.dispositivo("lucia")
        val r = com.gonzalo.familia.nucleo.RedJava().pedir("GET", lucia.sesion.url("/claves/$uid", conPase = true))
        assertFalse("tu pareja no lee /claves", r.ok)
        val suya = com.gonzalo.familia.nucleo.RedJava().pedir("GET", lucia.sesion.url("/users/$uid", conPase = true))
        assertTrue("lo demás sí", suya.ok)
        assertFalse(suya.cuerpo.contains(clave))
    }

    @Test fun loQueEligesViajaConLosCamposDelMovil() {
        AjustesReproductor.ponAspecto(AjustesReproductor.Aspecto.ZOOM); AjustesReproductor.ponSubs("spa"); AjustesReproductor.ponInvertirCanales(true); AjustesReproductor.ponSalto(30)
        AjustesGuia.ponHoras(24)
        AjustesDirecto.ponOrden(AjustesDirecto.Orden.NUMERO); AjustesDirecto.ponDonde("lprueba001", "ES | Deportes"); AjustesDirecto.ponAlAbrir(true)
        sincroniza()
        val aj = otro.coleccion("ajustes")
        val re = aj.nodo("cobre_reproductor")!!
        assertEquals("ZOOM", re.texto("aspecto")); assertEquals("spa", re.texto("subs")); assertEquals(true, re["invertirCanales"]); assertEquals("un número entero, como en Android", 30L, re["salto"])
        assertEquals(24L, aj.nodo("cobre_guia")!!["horas"])
        val di = aj.nodo("cobre_directo")!!
        assertEquals("NUMERO", di.texto("orden")); assertEquals("lprueba001", di.texto("lista")); assertEquals("ES | Deportes", di.texto("grupo")); assertEquals(true, di["alAbrir"])
        // En el móvil lo cambias (con lo que es solo del móvil también): llega aquí, y lo del móvil no se toca
        aj.pon("cobre_reproductor", mapOf("aspecto" to "ANCHO", "ahorro" to false, "externo" to "org.videolan.vlc"))
        aj.pon("cobre_guia", mapOf("dias" to 7L, "soloWifi" to false))
        sincroniza()
        espera("llega lo del otro dispositivo") { AjustesReproductor.aspecto() == AjustesReproductor.Aspecto.ANCHO }
        espera("y la guía") { AjustesGuia.dias() == 7 }
        assertEquals("lo que no ha cambiado allí, igual", 24, AjustesGuia.horas())
        AjustesReproductor.ponReconectar(false)
        sincroniza()
        assertEquals("org.videolan.vlc", aj.nodo("cobre_reproductor")!!.texto("externo"))
        assertEquals(false, aj.nodo("cobre_reproductor")!!["reconectar"])
        // Lo de por defecto, el mismo que en el móvil
        aj.pon("cobre_guia", mapOf("horas" to "")); sincroniza()
        espera("quitado: el de por defecto") { AjustesGuia.horas() == 12 }
    }

    @Test fun sinCuentaNoSeEscribeNada() {
        LaFamilia.salir()
        espera("fuera") { !LaFamilia.familia.sesion.dentro }
        MiCuenta.alternaFavorito("lprueba001", l1); MiCuenta.ponUltimo("lprueba001", l1); MiCuenta.ocultaGrupo("lprueba001", "x", true)
        Listas.pon(xtream)
        assertEquals(0, LaFamilia.familia.pendientes)
        assertTrue(Listas.lista().isEmpty())
    }
}
