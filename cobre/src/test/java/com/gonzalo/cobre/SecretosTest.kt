package com.gonzalo.cobre

import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.IptvError
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.Respuesta
import com.gonzalo.iptv.TipoLista
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Las direcciones de las listas y los logins de Xtream son secretos: solo pueden estar en `/claves/<uid>/cobre_listas` (lo único que no
 * lee ni tu pareja). Ni en `/users/<uid>` (que tu pareja sí puede leer), ni en los registros, ni en los mensajes de error, ni en claro en
 * el dispositivo (los canales de una lista M3U llevan las direcciones: se guardan cifrados). Y viajan: tu otro dispositivo las tiene.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecretosTest : ConCuenta() {
    private val clave = "Pw-9f8e7d6c5b"
    private val token = "TOKEN-a1b2c3d4"

    @Before fun prepara() { ShadowLog.clear(); preparaCuenta() }
    @After fun fin() { suelta() }

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
            // Un servidor que contesta 403 con la dirección en el mensaje: no debe llegar a ningún texto
            throw IptvError("403 en $url")
        }
    }

    @Test fun lasListasSoloVanAClaves() {
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
    }

    @Test fun nadaEnClaroEnElDispositivoNiEnLosRegistrosNiEnLosErrores() {
        Listas.pon(m3u)
        Tele.red = red
        assertEquals(2, Tele.almacen.actualizaCanales(m3u))
        assertEquals(2, Tele.cargada(m3u)!!.canales.size)
        assertTrue("la guía de la cabecera, guardada (cifrada)", Tele.almacen.fuentesGuia(m3u).any { it.contains("xmltv.php") })
        // Un error del servidor (con la dirección dentro): el texto que se enseña, sin secretos
        val e = runCatching { Tele.almacen.actualizaGuia(m3u) }.exceptionOrNull()!!
        assertFalse(e.message!!.contains(clave)); assertFalse(e.message!!.contains(token)); assertFalse(e.message!!.contains("usuarioPepe"))
        sincroniza()
        // En el dispositivo: ni en las cachés (cifradas), ni en las preferencias, ni en la copia de la cuenta (fuera de lo cifrado de la familia)
        val dir = File(ctx.applicationInfo.dataDir)
        val enClaro = dir.walkTopDown().filter { it.isFile && !it.path.contains("familia_seguro") }.joinToString("\n") { String(it.readBytes(), Charsets.ISO_8859_1) }
        assertFalse("la contraseña no está en claro", enClaro.contains(clave))
        assertFalse("el token no está en claro", enClaro.contains(token))
        // Ni en los registros
        assertFalse(ShadowLog.getLogs().any { it.msg.orEmpty().contains(clave) || it.throwable?.message.orEmpty().contains(clave) || it.msg.orEmpty().contains(token) })
        // Ni en el texto de una lista (lo que saldría en un registro por descuido)
        assertFalse(m3u.toString().contains(token)); assertFalse(xtream.toString().contains(clave))
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
}
