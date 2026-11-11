package com.gonzalo.mostaza

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * Hathor 0.7.0 (la dirección C, como Seshat y Bastet): los colores «Palomitas» y su paso desde los de antes, el acento legible, el reto
 * de películas y series terminadas, la semana, el encuadre de cada dispositivo, el paspartú, los órdenes de cada página y la etiqueta del estado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DireccionCTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun ms(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 3_600_000L

    @After fun limpia() { Biblioteca.olvidaParaPruebas(); Encuadres.almacen = "encuadres" }

    // ---------------------------------------------------------------- los colores

    @Test fun familiaEsLaDeSerie() {
        val p = PALETTES.first { it.id == "familia" }
        assertEquals(0xFFF8F0D4L, p.light.paper); assertEquals(0xFF000000L, p.light.ink); assertEquals(0xFFB56576L, p.light.general)
        ctx.getSharedPreferences("mostaza_tema", Context.MODE_PRIVATE).edit().clear().commit()
        Skin.cargar(ctx)
        assertEquals("familia", Skin.palette.id); assertEquals("paleta", Skin.accentId)
        assertEquals(0L, Skin.fondoPropio); assertEquals(0L, Skin.acentoPropio)
    }

    @Test fun losColoresDeAntesPasanAPalomitas() {
        Skin.palette = PALETTES.first { it.id == "revista" }; Skin.accentId = "mostaza"
        Skin.fondoPropio = 0xFF715021; Skin.tintaPropia = 0xFFFFFFFF; Skin.acentoPropio = 0xFFD4C1C0
        assertTrue(Skin.aPalomitas())
        assertEquals("familia", Skin.palette.id); assertEquals("paleta", Skin.accentId)
        assertEquals(0L, Skin.fondoPropio); assertEquals(0L, Skin.tintaPropia); assertEquals(0L, Skin.acentoPropio)
        assertFalse("la segunda vez no cambia nada", Skin.aPalomitas())
    }

    @Test fun elBotonPoneLaFamilia() {
        Skin.palette = PALETTES.first { it.id == "vitrina" }; Skin.accentId = "azul"; Skin.fondoPropio = 0xFF123456; Skin.acentoPropio = 0xFF00AA00
        Skin.aColoresDeSerie()
        assertEquals("familia", Skin.palette.id); assertEquals("paleta", Skin.accentId); assertEquals(0L, Skin.fondoPropio); assertEquals(0L, Skin.acentoPropio)
    }

    @Test fun loElegidoAManoSeQueda() {
        Skin.palette = PALETTES.first { it.id == "vitrina" }; Skin.accentId = "azul"
        Skin.fondoPropio = 0xFF123456; Skin.tintaPropia = 0xFFFFFFFF; Skin.acentoPropio = 0xFF00AA00
        assertFalse(Skin.aPalomitas())
        assertEquals(0xFF123456L, Skin.fondoPropio); assertEquals(0xFFFFFFFFL, Skin.tintaPropia); assertEquals(0xFF00AA00L, Skin.acentoPropio); assertEquals("vitrina", Skin.palette.id)
        Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L
    }

    @Test fun elRojoSeLeeSobreElPapel() {
        val papel = Color(0xFFF4ECDD); val tinta = Color(0xFF1B1A1F); val rojo = Color(0xFFD7263D)
        // El rojo tal cual, sobre el crema, se queda algo por debajo del 4,5:1 (AA para letra pequeña): para texto, se oscurece lo justo
        assertTrue(contraste(rojo, papel) < 4.5f)
        val legible = acentoLegible(rojo, papel, tinta)
        assertTrue(contraste(legible, papel) >= 4.5f)
        assertTrue("casi el mismo rojo", kotlin.math.abs(legible.red - rojo.red) < 0.2f)
        // De noche, sobre el negro de la paleta oscura, se aclara
        assertTrue(contraste(acentoLegible(rojo, Color(0xFF16131A), Color(0xFFF3ECE0)), Color(0xFF16131A)) >= 4.5f)
        // Y el texto blanco sobre un bloque rojo se lee
        assertTrue(contraste(Color.White, rojo) >= 4.5f)
    }

    // ---------------------------------------------------------------- el reto

    @Test fun elRetoCuentaPeliculasYSeriesTerminadasEsteAno() {
        val ano = LocalDate.now().year
        val l = listOf(
            Titulo("a", "a", "movie", "Peli vista", estado = Estado.VISTO, fin = ms(LocalDate.of(ano, 2, 1))),
            Titulo("b", "b", "series", "Serie terminada", estado = Estado.VISTO, fin = ms(LocalDate.of(ano, 3, 1))),
            Titulo("c", "c", "series", "Serie a medias", estado = Estado.VIENDO),
            Titulo("d", "d", "movie", "Peli del año pasado", estado = Estado.VISTO, fin = ms(LocalDate.of(ano - 1, 12, 30))),
            Titulo("e", "e", "movie", "Pendiente", estado = Estado.PENDIENTE),
            Titulo("f", "f", "movie", "Abandonada", estado = Estado.ABANDONADO, fin = ms(LocalDate.of(ano, 4, 1))),
        )
        val t = terminadasEn(l, ano)
        assertEquals(listOf("b", "a"), t.map { it.ref })
    }

    @Test fun elRitmoDelReto() {
        // 24 al año: a 1 de julio (día 182 de 365) tocarían 11
        val r = ritmoReto(24, 9, LocalDate.of(2026, 7, 1))
        assertEquals(-2, r.diferencia); assertEquals(15, r.quedan); assertEquals(184, r.dias)
        assertEquals("VAS 2 POR DETRÁS · TE QUEDAN 15 EN 184 DÍAS", r.texto())
        assertEquals("¡RETO CUMPLIDO!", ritmoReto(5, 5, LocalDate.of(2026, 7, 1)).texto())
    }

    @Test fun elRetoSeGuardaYSeLee() {
        RetoCine.poner(ctx, 36)
        assertEquals(36, RetoCine.meta(ctx))
        RetoCine.poner(ctx, 0)
        assertEquals(0, RetoCine.meta(ctx))
    }

    // ---------------------------------------------------------------- la semana

    @Test fun laSemanaSumaLoVistoCadaDiaYLaRacha() {
        val hoy = LocalDate.of(2026, 11, 9)
        val ratos = listOf(
            Rato("a", "A", hoy.toString(), 30 * 60_000L), Rato("b", "B", hoy.toString(), 15 * 60_000L),
            Rato("a", "A", hoy.minusDays(1).toString(), 60 * 60_000L), Rato("a", "A", hoy.minusDays(2).toString(), 30_000L),
            Rato("a", "A", hoy.minusDays(10).toString(), 99 * 60_000L),
        )
        val s = datosCine(ratos, hoy)
        assertEquals(45L, s.hoyMin)
        assertEquals(7, s.semana.size); assertEquals(hoy, s.semana.last().first); assertEquals(60L, s.semana[5].second)
        // (medio minuto no cuenta para la racha: la regla común de la familia es un minuto)
        assertEquals(2, s.racha)
        assertEquals("3 H 01", minutosCorto(181)); assertEquals("52′", minutosCorto(52)); assertEquals("2 h 10", minutosDia(130))
    }

    // ---------------------------------------------------------------- el encuadre, de cada dispositivo

    @Test fun elEncuadreEsDeCadaDispositivoYVuelveAlCentroSiCambiaLaImagen() {
        val f1 = java.io.File(ctx.cacheDir, "aaaa1111"); f1.writeBytes(byteArrayOf(1))
        val f2 = java.io.File(ctx.cacheDir, "bbbb2222"); f2.writeBytes(byteArrayOf(2))
        Encuadres.almacen = "encuadres_movil"
        Encuadres.poner(ctx, "tt1", false, f1.path, Encuadre(2f, 0.3f, 0.6f))
        assertEquals(Encuadre(2f, 0.3f, 0.6f), Encuadres.de(ctx, "tt1", false, f1.path))
        // Otro dispositivo (otro almacén): la misma imagen, centrada, hasta que la encuadres allí
        Encuadres.almacen = "encuadres_tablet"
        assertTrue(Encuadres.de(ctx, "tt1", false, f1.path).neutro)
        Encuadres.almacen = "encuadres_movil"
        // La imagen cambia (otro hash): el encuadre de antes ya no casa
        assertTrue(Encuadres.de(ctx, "tt1", false, f2.path).neutro)
        // El del fondo va aparte del de la portada
        assertTrue(Encuadres.de(ctx, "tt1", true, f1.path).neutro)
        // Uno de internet no se encuadra
        Encuadres.poner(ctx, "tt1", false, "https://x/y.jpg", Encuadre(3f))
        assertTrue(Encuadres.de(ctx, "tt1", false, "https://x/y.jpg").neutro)
    }

    @Test fun elEncuadreSiempreCubreElHueco() {
        val e = Encuadre(1f, 0f, 0f).ajustado(300f, 450f, 600f, 900f)
        assertEquals(0.5f, e.cx, 0.001f); assertEquals(0.5f, e.cy, 0.001f)
        val c = colocarEncuadre(300f, 450f, 1200f, 900f, Encuadre(1f, 0f, 0.5f))
        assertTrue(c[0] <= 0f && c[0] + c[2] >= 300f)
    }

    // ---------------------------------------------------------------- el paspartú

    @Test fun elPaspartuEsCremaYOscuroSoloConPortadasMuyClaras() {
        assertEquals(PASPARTU_CREMA, colorPaspartu(0.1f))
        assertEquals(PASPARTU_CREMA, colorPaspartu(0.77f))
        assertEquals(PASPARTU_OSCURO, colorPaspartu(0.78f))
        assertEquals(Color(0xFFF4EBD6), PASPARTU_CREMA)
    }

    // ---------------------------------------------------------------- las páginas

    @Test fun cadaPaginaTieneSusOrdenesSinRepetir() {
        Estado.entries.forEach { e -> val o = ordenesDe(e); assertEquals(o.size, o.distinct().size) }
        assertFalse(OrdenGuardado.Orden.ANADIDOS in ordenesDe(Estado.VIENDO))
        assertEquals(OrdenGuardado.Orden.ANADIDOS, ordenesDe(Estado.PENDIENTE).first())
        assertEquals("Terminadas", nombreOrden(Estado.VISTO, OrdenGuardado.Orden.RECIENTES))
        // Un orden guardado que no es de la página (de la sala, por ejemplo) no se usa: va el primero de los suyos
        OrdenGuardado.pon(ctx, Estado.VIENDO, OrdenGuardado.Orden.ANO)
        assertEquals(OrdenGuardado.Orden.RECIENTES, ordenDePagina(ctx, Estado.VIENDO))
    }

    @Test fun enViendoLoUltimoQueVisteVaPrimero() {
        val ahora = System.currentTimeMillis()
        val a = Titulo("a", "a", "series", "A", estado = Estado.VIENDO, ultima = ahora - 10_000)
        val b = Titulo("b", "b", "series", "B", estado = Estado.VIENDO, ultima = ahora - 90_000)
        Biblioteca.fijaParaPruebas(listOf(a, b), listOf(Progreso("b:1:1", "b", 1, 1, 1, 2, false, 0, ahora)))
        assertEquals(listOf("b", "a"), ordenarPagina(Estado.VIENDO, OrdenGuardado.Orden.RECIENTES, listOf(a, b)).map { it.ref })
        assertEquals(listOf("a", "b"), ordenarPagina(Estado.VIENDO, OrdenGuardado.Orden.TITULO, listOf(b, a)).map { it.ref })
    }

    @Test fun lasPaginasYLosEnlacesDeFuera() {
        assertEquals(listOf(Destino.Portada, Destino.Viendo, Destino.Pendiente, Destino.Visto, Destino.Abandonado, Destino.Proximos), PAGINAS)
        assertEquals(-1, paginaDe(Destino.Ajustes)); assertEquals(-1, paginaDe(Destino.Explorar))
        assertEquals(Destino.Pendiente, destinoDe("Pendiente")); assertEquals(Destino.Pendiente, destinoDe("pendientes"))
        assertEquals(Destino.Visto, destinoDe("Vistas")); assertEquals(Destino.Proximos, destinoDe("Próximos")); assertNull(destinoDe("nada"))
    }

    // ---------------------------------------------------------------- la etiqueta del estado

    @Test fun laEtiquetaDelEstadoNuncaDiceTerminadoEnViendo() {
        val ahora = System.currentTimeMillis()
        val t = Titulo("s", "s", "series", "S", estado = Estado.VIENDO)
        Biblioteca.fijaParaPruebas(listOf(t), listOf(Progreso("s:2:5", "s", 2, 5, 57, 57, true, ahora, ahora)))
        assertEquals("VIENDO · T2 · E5", etiquetaEstado(t, "series"))
        assertEquals("PENDIENTE", etiquetaEstado(t.copy(estado = Estado.PENDIENTE), "series"))
        assertTrue(etiquetaEstado(t.copy(estado = Estado.VISTO, fin = ahora), "series").startsWith("VISTA · "))
        assertEquals("PELÍCULA", etiquetaEstado(null, "movie"))
    }

    @Test fun elFondoQueSeVe() {
        val t = Titulo("x", "x", "movie", "X", fondo = "https://tmdb/backdrop.jpg")
        assertEquals("https://tmdb/backdrop.jpg", t.fondoVer)
        assertEquals("/f/elegido.jpg", t.copy(fondoImg = "/f/elegido.jpg").fondoVer)
        assertEquals("", t.copy(sinFondo = true).fondoVer)
    }
}
