package com.gonzalo.torrent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Lo mismo que MotorLocalTest, pero en un móvil (o el emulador): la biblioteca nativa de su arquitectura, la caché privada de la
 * app, la red de Android y la dirección http://127.0.0.1 sin cifrar (con res/xml/torrent_red_local.xml, como en la app).
 */
@RunWith(AndroidJUnit4::class)
class MotorEnElMovilTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun seVeUnTorrentSembradoEnElPropioMovil() = runBlocking {
        hayLibtorrent()
        val base = File(ctx.cacheDir, "prueba-semilla").apply { deleteRecursively(); mkdirs() }
        val dir = File(base, "Peli")
        val video = creaVideo(File(dir, "Peli.mkv"), 3 * 1024 * 1024 + 777)
        File(dir, "Peli.nfo").writeText("nfo")
        val ti = creaTorrent(dir, 64 * 1024, relleno = true)
        val cache = File(ctx.cacheDir, "prueba-torrent").apply { deleteRecursively() }
        Sembrador(subidaBs = 1024 * 1024).use { s ->
            s.siembra(ti, base)
            val m = MotorTorrent(
                cache, AjustesTorrent(), RedFija(TipoRed.WIFI),
                ConfigSesion(interfaces = "127.0.0.1:0", dht = false, lsd = false, pares = listOf("127.0.0.1" to s.puerto)),
                Tiempos(sinParesMs = 10_000, metadatosMs = 20_000, esperaPiezaMs = 30_000, apagadoMs = 500, borradoMs = 3_000),
            )
            try {
                val f = m.prepara(ti.hashV1, null, listOf("dht:local"), null)
                assertEquals("Peli.mkv", f.archivo)
                assertArrayEquals(video.copyOfRange(2_000_000, 2_100_000), pide(f.url, "bytes=2000000-2099999").cuerpo)
                assertArrayEquals(video.copyOfRange(video.size - 500, video.size), pide(f.url, "bytes=-500").cuerpo)
                assertArrayEquals(video, pide(f.url).cuerpo)
                m.para(ti.hashV1)
                assertNull(m.estado(ti.hashV1))
                assertTrue(hastaQue { !File(cache, ti.hashV1).exists() })
            } finally {
                m.cierra()
            }
        }
        base.deleteRecursively()
    }

    @Test fun laRedDeAndroidYElMotorDeLaApp() {
        // Lo que ve la app: wifi, datos móviles o nada (aquí no se sabe cuál, pero no falla)
        assertTrue(RedAndroid(ctx).actual() in TipoRed.entries)
        // El motor tal cual lo crea la app: no arranca nada hasta el primer prepara
        val m = MotorTorrent(ctx)
        assertNull(m.estado("0123456789abcdef0123456789abcdef01234567"))
        m.paraTodo()
    }
}
