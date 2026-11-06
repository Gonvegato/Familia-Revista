package com.gonzalo.mostaza

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Los widgets de Mostaza, dibujados en el ordenador con la biblioteca de las capturas (pósteres de mentira, hechos aquí; sin red ni
 * cuenta) y probados con distintos temas. Con -Proborazzi.test.record=true deja las vistas previas en build/outputs/widgets/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val dia = 86_400_000L

    private fun siembra(vacia: Boolean = false) {
        Skin.cargar(ctx)
        Skin.origenAtrio = false; Skin.invertido = false; Skin.fondoId = ""; Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L
        if (vacia) { Biblioteca.fijaParaPruebas(emptyList()); return }
        val e = Escaparate(ctx)
        val ahora = System.currentTimeMillis()
        val p = e.pelis; val s = e.series
        Biblioteca.fijaParaPruebas(listOf(
            // Oppenheimer se tocó como título hace un día, pero en Juego de tronos viste un episodio hace una hora: va primero Juego de tronos
            e.titulo(p[1], Estado.VIENDO, ahora - 5 * dia, ahora - dia), e.titulo(s[0], Estado.VIENDO, ahora - 60 * dia, ahora - 3 * dia),
            e.titulo(p[0], Estado.PENDIENTE, ahora - 2 * dia), e.titulo(s[2], Estado.PENDIENTE, ahora - 3 * dia), e.titulo(p[5], Estado.PENDIENTE, ahora - 4 * dia),
            e.titulo(s[3], Estado.PENDIENTE, ahora - dia), e.titulo(p[3], Estado.VISTO, ahora - 300 * dia).copy(fin = ahora - 200 * dia),
        ), listOf(
            Progreso("tt0944947:2:5", "tt0944947", 2, 5, 34 * 60_000L, 57 * 60_000L, false, 0L, ahora - 3_600_000L),
            Progreso("tt0944947:2:4", "tt0944947", 2, 4, 57 * 60_000L, 57 * 60_000L, true, ahora - dia, ahora - dia),
            Progreso("tt15398776", "tt15398776", 0, 0, 72 * 60_000L, 180 * 60_000L, false, 0L, ahora - 2 * dia),
        ))
    }

    @After fun limpia() { Biblioteca.olvidaParaPruebas(); Skin.fondoId = ""; Skin.invertido = false; Skin.display = "Atrio"; Skin.body = "Atrio" }

    private val tamanos = mapOf(WTipo.SEGUIR to (264f to 132f), WTipo.PENDIENTES to (280f to 165f))
    private val otros = listOf(330f to 165f, 160f to 160f, 150f to 260f, 330f to 80f, 400f to 200f, 110f to 40f)

    @Test fun seguirViendoEsLoUltimoQueTocaste() {
        val a = Titulo("a", "a", "movie", "A", estado = Estado.VIENDO, ultima = 100L)
        val b = Titulo("b", "b", "series", "B", estado = Estado.VIENDO, ultima = 50L)
        val c = Titulo("c", "c", "movie", "C", estado = Estado.PENDIENTE, ultima = 999L, anadido = 10L)
        val d = Titulo("d", "d", "movie", "D", estado = Estado.PENDIENTE, anadido = 20L)
        val v = Titulo("v", "v", "movie", "V", estado = Estado.VISTO, ultima = 9_999L)
        val l = listOf(a, b, c, d, v)
        assertEquals("sin vídeos, por cuándo tocaste el título", listOf("a", "b"), Biblioteca.seguirViendo(l) { 0L }.map { it.ref })
        assertEquals("un episodio visto después cuenta más", listOf("b", "a"), Biblioteca.seguirViendo(l) { if (it.ref == "b") 200L else 0L }.map { it.ref })
        assertEquals("pendientes: lo último añadido primero, y solo pendientes", listOf("d", "c"), Biblioteca.pendientes(l).map { it.ref })
        assertTrue(Biblioteca.seguirViendo(listOf(c, d, v)) { 0L }.isEmpty())
    }

    @Test fun seguirViendoAbreLaFichaDeLoMasReciente() = runBlocking {
        siembra()
        val d = WDatos(ctx)
        assertEquals("Juego de tronos", d.viendo.first().nombre)
        assertEquals("T2 · E5 · quedan 23 min", d.seguir(d.viendo.first()))
        assertEquals(34f / 57f, d.fraccion(d.viendo.first())!!, 0.001f)
        assertEquals(listOf("Separación", "Dune: Parte dos", "Shōgun", "Perfect Days"), d.pendientes.map { it.nombre })
        val pag = Widgets.componer(ctx, WTipo.SEGUIR, 1, 330f, 165f)
        assertTrue(pag.zonas.any { it.desc == "Seguir viendo Juego de tronos" && it.toque != null })
        assertTrue("con dos a medias, la esquina pasa al otro", pag.zonas.any { it.desc == "Otro título" })
        val pen = Widgets.componer(ctx, WTipo.PENDIENTES, 1, 400f, 165f)   // (cuatro en fila)
        val celdas = pen.zonas.mapNotNull { it.celdas }.single().single()
        assertEquals("cada póster abre su ficha", listOf("Abrir Separación", "Abrir Dune: Parte dos", "Abrir Shōgun", "Abrir Perfect Days"), celdas.map { it.second })
        assertTrue(celdas.all { it.first != null })
        assertTrue(pen.zonas.any { it.desc == "Abrir Pendientes en Mostaza" })
    }

    @Test fun vacioInvitaAAbrirMostaza() = runBlocking {
        siembra(vacia = true)
        val s = Widgets.componer(ctx, WTipo.SEGUIR, 1, 330f, 165f)
        assertEquals(1, s.zonas.size); assertEquals("Nada a medias", s.zonas[0].desc); assertNotNull(s.zonas[0].toque)
        val p = Widgets.componer(ctx, WTipo.PENDIENTES, 1, 330f, 165f)
        assertEquals(1, p.zonas.size); assertEquals("Nada pendiente", p.zonas[0].desc)
    }

    @Test fun dibujaTodosSinFallar() {
        for (vacia in listOf(false, true)) {
            siembra(vacia)
            for (t in WTipo.entries) for ((w, h) in otros + tamanos.values) for (noche in listOf(false, true)) {
                val pag = runBlocking { Widgets.componer(ctx, t, 1, w, h) }
                val bm = pag.bitmap(ctx, w, h, noche, 1f)
                assertTrue("$t ${w}x$h sale con tamaño", bm.width > 0 && bm.height > 0)
            }
        }
    }

    private fun pixelCentro(tipo: WTipo): Int {
        runBlocking { Widgets.cargarLetras(ctx) }
        val pag = runBlocking { Widgets.componer(ctx, tipo, 1, 264f, 132f) }
        val bm = pag.bitmap(ctx, 264f, 132f, Skin.tones.darkBg, 1f)
        return bm.getPixel(bm.width - 20, bm.height / 2)
    }

    @Test fun losWidgetsUsanLosColoresDeApariencia() {
        siembra()
        val h = Hoja(ctx, 10f, 10f, false, 1f)
        assertNotEquals("claro y oscuro tienen papeles distintos", h.papel, Hoja(ctx, 10f, 10f, true, 1f).papel)
        Skin.invertido = true
        assertEquals("invertido: el papel claro pasa a ser la tinta", h.tinta, Hoja(ctx, 10f, 10f, false, 1f).papel)
        Skin.invertido = false
        val granate = FONDOS.first { it.id == "granate" }
        Skin.fondoId = "granate"
        assertEquals("fondo de color", granate.tones.paper.toInt(), pixelCentro(WTipo.SEGUIR))
        assertEquals(granate.tones.paper.toInt(), pixelCentro(WTipo.PENDIENTES))
        assertEquals("con un fondo, un solo dibujo", false, Widgets.noche())
        Skin.fondoId = ""; Skin.fondoPropio = 0xFF123456L
        assertEquals("fondo a mano", 0xFF123456.toInt(), pixelCentro(WTipo.SEGUIR))
        Skin.fondoPropio = 0L
    }

    @Test fun conElReproductorAbiertoEsperan() {
        Widgets.reproductorAbierto = true
        Widgets.pedir(ctx)   // (no repinta: queda pedido)
        Widgets.reproductorCerrado(ctx)
        assertFalse(Widgets.reproductorAbierto)
    }

    private fun foto(nombre: String, tipo: WTipo, w: Float, h: Float, noche: Boolean, dir: String = "build/outputs/widgets") {
        runBlocking { Widgets.cargarLetras(ctx) }
        val pag = runBlocking { Widgets.componer(ctx, tipo, 1, w, h) }
        val bm = pag.bitmap(ctx, w, h, noche, 2f)
        File(dir).mkdirs()
        File("$dir/$nombre.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun dibujaLasVistasPrevias() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(kotlinx.coroutines.Dispatchers.Unconfined).allowHardware(false).build())
        for ((t, s) in tamanos) foto("w_prev_${t.name.lowercase()}", t, s.first, s.second, false)
        for ((t, s) in tamanos) foto("oscuro_${t.name.lowercase()}", t, s.first, s.second, true)
        foto("alto_seguir", WTipo.SEGUIR, 150f, 260f, false)
        foto("compacto_seguir", WTipo.SEGUIR, 160f, 160f, false)
        foto("bajo_seguir", WTipo.SEGUIR, 330f, 80f, false)
        foto("bajo_pendientes", WTipo.PENDIENTES, 330f, 110f, false)
        Skin.display = "Vitrina"
        for ((t, s) in tamanos) foto("vitrina_${t.name.lowercase()}", t, s.first, s.second, false)
        Skin.display = "Atrio"
        siembra(vacia = true)
        foto("vacio_seguir", WTipo.SEGUIR, 264f, 132f, false)
        foto("vacio_pendientes", WTipo.PENDIENTES, 280f, 165f, false)
    }
}
