package com.gonzalo.cobre.pc

import com.gonzalo.familia.pc.LlaveroMemoria
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.Respuesta
import com.gonzalo.iptv.TipoLista
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * La tele en el ordenador: el [com.gonzalo.iptv.Almacen] de `iptv` (el mismo que en el móvil) con el cifrado del ordenador ([Cifrado], la clave
 * en el llavero), los grupos y el orden de los canales, lo que se emite ahora y lo siguiente (emparejando la guía), y lo de las listas de
 * archivo. Sin la cuenta (lo de la cuenta está en `CuentaTest`).
 */
class TeleTest {
    private lateinit var dir: File
    private val clave = "Cl4veDeLaLista"

    private fun xmltv(t: Long) = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.Instant.ofEpochMilli(t).atOffset(ZoneOffset.UTC)) + " +0000"

    private val ahora = System.currentTimeMillis() / 60_000 * 60_000
    private val red = Red { url, _ ->
        when {
            url.startsWith("http://lista.ejemplo.test/") -> Respuesta(200, ByteArrayInputStream("""
                #EXTM3U url-tvg="http://epg.ejemplo.test/guia.xml"
                #EXTINF:-1 tvg-id="La1.es" tvg-chno="1" group-title="Nacionales",La 1 HD
                http://tv.ejemplo.test/live/pepe/$clave/101.ts
                #EXTINF:-1 tvg-id="" tvg-chno="3" group-title="Nacionales",ES: Antena 3 FHD
                http://tv.ejemplo.test/live/pepe/$clave/103.ts
                #EXTINF:-1 tvg-chno="2" group-title="Deportes",Deportes 1
                http://tv.ejemplo.test/live/pepe/$clave/201.ts
            """.trimIndent().toByteArray()))
            url.startsWith("http://epg.ejemplo.test/") -> Respuesta(200, ByteArrayInputStream("""
                <?xml version="1.0" encoding="UTF-8"?>
                <tv>
                  <channel id="La1.es"><display-name>La 1</display-name></channel>
                  <channel id="Antena3.es"><display-name>Antena 3</display-name></channel>
                  <programme start="${xmltv(ahora - 1_800_000)}" stop="${xmltv(ahora + 1_800_000)}" channel="La1.es"><title>Telediario</title></programme>
                  <programme start="${xmltv(ahora + 1_800_000)}" stop="${xmltv(ahora + 5_400_000)}" channel="La1.es"><title>Saber y ganar</title></programme>
                  <programme start="${xmltv(ahora - 600_000)}" stop="${xmltv(ahora + 600_000)}" channel="Antena3.es"><title>Noticias</title></programme>
                </tv>
            """.trimIndent().toByteArray()))
            else -> Respuesta(404, ByteArrayInputStream(ByteArray(0)))
        }
    }
    private val lista = Lista("lm3u0000001", "Mi lista", TipoLista.M3U, "http://lista.ejemplo.test/lista.m3u")

    @Before fun prepara() {
        dir = kotlin.io.path.createTempDirectory("cobre-tele").toFile()
        Local.dirPruebas = dir; Local.olvidaParaPruebas(); Local.dispositivoPruebas = "cobre-linux-pruebas"
        Tele.olvidaParaPruebas()
        com.gonzalo.familia.pc.LaFamilia.llaveroPruebas = LlaveroMemoria()
        Tele.red = red
        AjustesCuenta.enMemoria = HashMap()
        MiCuenta.fijo = MiCuenta.Fijo()
    }

    @After fun fin() {
        Tele.olvidaParaPruebas(); AjustesCuenta.enMemoria = null; MiCuenta.fijo = null; com.gonzalo.familia.pc.LaFamilia.llaveroPruebas = null
        Local.dirPruebas = null; Local.olvidaParaPruebas(); Local.dispositivoPruebas = null
        dir.deleteRecursively()
    }

    @Test fun losCanalesSuGuiaYSuOrden() {
        assertEquals(3, Tele.almacen.actualizaCanales(lista))
        assertTrue(Tele.almacen.actualizaGuia(lista) >= 3)
        val cg = Tele.cargada(lista)!!
        assertEquals(listOf("Nacionales", "Deportes"), cg.todosLosGrupos)
        // Lo que se emite y lo siguiente: por su tvg-id (La 1) y, sin él, por su nombre (sin «ES:» ni «FHD»)
        val (a, d) = Tele.ahoraYDespuesRapido(cg, cg.canales[0], ahora)
        assertEquals("Telediario", a?.titulo); assertEquals("Saber y ganar", d?.titulo)
        assertEquals("Noticias", Tele.ahoraYDespuesRapido(cg, cg.canales[1], ahora).first?.titulo)
        assertNull(Tele.ahoraYDespuesRapido(cg, cg.canales[2], ahora).first)
        // El orden de los canales (`ajustes/cobre_directo` → `orden`) y los grupos escondidos
        assertEquals(listOf("La 1 HD", "ES: Antena 3 FHD", "Deportes 1"), Tele.visibles(cg).map { it.nombre })
        AjustesDirecto.ponOrden(AjustesDirecto.Orden.NUMERO)
        assertEquals(listOf(1, 2, 3), Tele.visibles(cg).map { it.numero })
        AjustesDirecto.ponOrden(AjustesDirecto.Orden.NOMBRE)
        assertEquals(listOf("Deportes 1", "ES: Antena 3 FHD", "La 1 HD"), Tele.visibles(cg).map { it.nombre })
        MiCuenta.fijo = MiCuenta.Fijo(ocultos = mapOf(lista.id to setOf("Deportes")))
        assertEquals(2, Tele.visibles(cg).size)
        assertEquals("solo los de un grupo", listOf("ES: Antena 3 FHD", "La 1 HD"), Tele.visibles(cg, "Nacionales").map { it.nombre })
        assertTrue("el grupo escondido no sale", Tele.visibles(cg, "Deportes").isEmpty())
        // Las cabeceras: las del canal y el user-agent de la lista
        val conAgente = lista.copy(agente = "Agente/1.0")
        assertEquals("Agente/1.0", Tele.cabeceras(conAgente, cg.canales[0])["User-Agent"])
        assertEquals("http://tv.ejemplo.test/live/pepe/$clave/101.ts", Tele.url(lista, cg.canales[0]))
    }

    @Test fun enElDiscoCifradoYConLaClaveDelLlavero() {
        Tele.almacen.actualizaCanales(lista)
        val enClaro = dir.walkTopDown().filter { it.isFile }.joinToString("\n") { String(it.readBytes(), Charsets.ISO_8859_1) }
        assertFalse("las direcciones de los canales, cifradas", enClaro.contains(clave))
        assertFalse(enClaro.contains("tv.ejemplo.test"))
        // La clave está en el disco de Cobre cifrada con la del llavero: otro llavero no la abre (y la caché se vuelve a bajar)
        assertNotNull(Tele.cargada(lista))
        Tele.olvidaParaPruebas(); Tele.red = red
        com.gonzalo.familia.pc.LaFamilia.llaveroPruebas = LlaveroMemoria()
        assertNull(Tele.cargada(lista))
    }

    @Test fun lasListasDeArchivoSonDeSuOrdenador() {
        val aqui = Lista("larchivo001", "La de aquí", TipoLista.ARCHIVO, dispositivo = "cobre-linux-pruebas")
        val fuera = Lista("larchivo002", "La del móvil", TipoLista.ARCHIVO, dispositivo = "cobre-1a2b3c4d")
        Listas.fijas = listOf(aqui, fuera)
        try {
            assertEquals(listOf("larchivo001"), Listas.usables().map { it.id })
            Tele.almacen.guardaArchivo(aqui, ByteArrayInputStream("#EXTM3U\n#EXTINF:-1,Uno\nhttp://tv.ejemplo.test/$clave/1.ts\n".toByteArray()))
            assertEquals(1, Tele.almacen.actualizaCanales(aqui))
            assertFalse("el archivo, cifrado", dir.walkTopDown().filter { it.isFile }.any { String(it.readBytes(), Charsets.ISO_8859_1).contains(clave) })
            val e = runCatching { Tele.almacen.actualizaCanales(fuera) }.exceptionOrNull()
            assertEquals("Esta lista es un archivo de otro dispositivo: aquí no está.", e?.message)
        } finally { Listas.fijas = null }
    }
}
