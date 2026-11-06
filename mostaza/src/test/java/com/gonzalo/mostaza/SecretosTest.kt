package com.gonzalo.mostaza

import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Json
import kotlinx.coroutines.runBlocking
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
import java.io.File

/**
 * Las direcciones de los addons llevan secretos (la clave de tu debrid va dentro): solo pueden estar en `/claves/<uid>` (lo único que no lee
 * ni tu pareja). Ni en `/users/<uid>` (que tu pareja sí puede leer), ni en los registros, ni en los mensajes de error, ni en la caché de
 * manifiestos del dispositivo. Y viajan: tu otro dispositivo las tiene, en el mismo orden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecretosTest : ConCuenta() {
    private val secreto = "RDKEY-9f8e7d6c5b4a"

    @Before fun prepara() { ShadowLog.clear(); preparaCuenta() }
    @After fun fin() { suelta() }

    @Test fun lasDireccionesDeLosAddonsSoloVanAClaves() {
        AddonDeMentira().use { a ->
            val m = """{"id":"org.prueba.fuentes","version":"1.0.0","name":"Fuentes de prueba","description":"x","types":["movie"],"resources":["stream"],"catalogs":[],"idPrefixes":["tt"],"behaviorHints":{"configurable":true}}"""
            a.respuestas["/$secreto/manifest.json"] = m
            a.respuestas["/otra-$secreto/manifest.json"] = m.replace("Fuentes de prueba", "Fuentes configuradas")
            a.respuestas["/$secreto/stream/movie/tt0111161.json"] = """{"streams":[{"name":"x","url":"https://d/1.mkv"}]}"""
            val url = a.base + "/$secreto/manifest.json"
            runBlocking { Addons.instala(url) }
            assertEquals(listOf(Addons.CINEMETA, url), Addons.lista().map { it.url })
            // Al configurarlo, la dirección nueva sustituye a la vieja en su sitio
            val nueva = a.base + "/otra-$secreto/manifest.json"
            runBlocking { Addons.reemplaza(url, nueva) }
            assertEquals(listOf(Addons.CINEMETA, nueva), Addons.lista().map { it.url })
            Addons.guarda(listOf(AddonInstalado(url, "Fuentes de prueba"), AddonInstalado(Addons.CINEMETA, "Cinemeta")))
            // Lo de verdad: pedir fuentes con el addon (y un error de ese addon)
            runBlocking { Addons.fuentes("movie", "tt0111161") }
            runBlocking { runCatching { Addons.instala(a.base + "/$secreto-mal/manifest.json") } }.exceptionOrNull()?.let { e -> assertFalse("el error no lleva la clave", e.message.orEmpty().contains(secreto)) }
            sincroniza()
            // En /claves sí (y en orden)
            val claves = Emulador.lee("/claves/$uid")
            assertTrue(Json.escribe(claves).contains(secreto))
            assertEquals(listOf(url, Addons.CINEMETA), Addons.deTexto(LaFamilia.familia.clave(Addons.CLAVE)).map { it.url })
            // En /users/<uid> (lo que lee tu pareja), en ninguna parte
            val users = Json.escribe(Emulador.lee("/users/$uid"))
            assertFalse("la clave del debrid no está en /users", users.contains(secreto))
            // Tu otro dispositivo las tiene (de /claves)
            espera("llegan al otro dispositivo") { otro.sincroniza(); Addons.deTexto(otro.clave(Addons.CLAVE)).map { it.url } == listOf(url, Addons.CINEMETA) }
            // Ni en los registros, ni en la caché del dispositivo (fuera del archivo cifrado de las claves)
            assertFalse(ShadowLog.getLogs().any { it.msg.orEmpty().contains(secreto) || it.throwable?.message.orEmpty().contains(secreto) })
            val enClaro = File(ctx.filesDir, "addons_manifiestos.json").takeIf { it.exists() }?.readText().orEmpty()
            assertFalse("la caché de manifiestos va por el hash de la dirección", enClaro.contains(secreto))
            val prefs = File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty().joinToString("\n") { it.readText() }
            assertFalse("ni en las preferencias", prefs.contains(secreto))
        }
    }

    @Test fun tuParejaNoLasPuedeLeer() {
        AddonDeMentira().use { a ->
            a.respuestas["/$secreto/manifest.json"] = """{"id":"x","version":"1","name":"X","description":"","types":["movie"],"resources":["stream"],"catalogs":[]}"""
            runBlocking { Addons.instala(a.base + "/$secreto/manifest.json") }
            sincroniza()
            // Tu pareja: puede leer tu cuenta (`partner`), pero no tus claves
            val suUid = Emulador.cuenta("lucia")
            Emulador.escribe("/users/$uid/partner", suUid)
            val lucia = Emulador.dispositivo("lucia")
            val r = com.gonzalo.familia.nucleo.RedJava().pedir("GET", lucia.sesion.url("/claves/$uid", conPase = true))
            assertFalse("tu pareja no lee /claves", r.ok)
            val suya = com.gonzalo.familia.nucleo.RedJava().pedir("GET", lucia.sesion.url("/users/$uid", conPase = true))
            assertTrue("lo demás sí", suya.ok)
            assertFalse(suya.cuerpo.contains(secreto))
        }
    }
}
