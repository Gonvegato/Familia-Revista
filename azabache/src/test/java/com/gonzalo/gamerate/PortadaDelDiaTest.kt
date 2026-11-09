package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Jonsu 1.8: la portada del día, los retos de todas, la semana apilada, las páginas, el paspartú, el encuadre por dispositivo y los colores. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PortadaDelDiaTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val hoy = LocalDate.of(2026, 11, 9).toEpochDay()
    private val min = 60_000L

    private fun dias(juego: Map<Long, Long> = emptyMap(), libro: Map<Long, Long> = emptyMap(), serie: Map<Long, Long> = emptyMap(), palabras: Map<Long, Int> = emptyMap()) = ratosPorDia(
        juego.mapValues { (_, ms) -> mapOf(JuegoPc(201L, "Deadlock") to ms) },
        libro.mapValues { (_, ms) -> mapOf(301L to ms) },
        serie.mapValues { (_, ms) -> mapOf("tv_sev" to ms) },
        palabras, { if (it == 301L) "El nombre del viento" else "" }, { if (it == "tv_sev") "Severance" else "" }, "obra1" to "La casa del faro"
    )

    @Test fun loDeMasTiempoEsLaPortada() {
        val d = dias(juego = mapOf(hoy to 95 * min), libro = mapOf(hoy to 35 * min), palabras = mapOf(hoy to 812))
        val p = portadaDelDia(d, hoy)!!
        assertEquals(Hermana.BASTET, p.principal.hermana)
        assertEquals(201L, p.principal.id)
        assertEquals("HOY · 1 H 35 EN BASTET", antetituloPortada(p, hoy))
        assertEquals("y 35′ leyendo, 812 palabras", restoDelDia(p))
    }

    @Test fun libroYSerie() {
        assertEquals(Hermana.SESHAT, portadaDelDia(dias(juego = mapOf(hoy to 10 * min), libro = mapOf(hoy to 80 * min)), hoy)!!.principal.hermana)
        val s = portadaDelDia(dias(libro = mapOf(hoy to 20 * min), serie = mapOf(hoy to 120 * min)), hoy)!!
        assertEquals(Hermana.HATHOR, s.principal.hermana)
        assertEquals("tv_sev", s.principal.ref)
        assertEquals("Severance", s.principal.titulo)
        assertEquals("HOY · 2 H EN HATHOR", antetituloPortada(s, hoy))
    }

    @Test fun loEscritoSoloSiNoHayNadaConTiempo() {
        val p = portadaDelDia(dias(palabras = mapOf(hoy to 900)), hoy)!!
        assertEquals(Hermana.THOT, p.principal.hermana)
        assertEquals("obra1", p.principal.ref)
        assertEquals("HOY · 900 PALABRAS EN THOT", antetituloPortada(p, hoy))
        assertEquals(Hermana.SESHAT, portadaDelDia(dias(libro = mapOf(hoy to 5 * min), palabras = mapOf(hoy to 5000)), hoy)!!.principal.hermana)
    }

    @Test fun sinNadaHoyLoDeAyerOLaSemana() {
        val ayer = portadaDelDia(dias(juego = mapOf(hoy - 1 to 40 * min)), hoy)!!
        assertEquals(hoy - 1, ayer.dia)
        assertTrue(antetituloPortada(ayer, hoy).startsWith("AYER · 40′"))
        val antes = portadaDelDia(dias(libro = mapOf(hoy - 4 to 30 * min)), hoy)!!
        assertTrue(antetituloPortada(antes, hoy).startsWith("EL "))
        assertNull(portadaDelDia(dias(libro = mapOf(hoy - 30 to 30 * min)), hoy))
        assertNull(portadaDelDia(emptyMap(), hoy))
    }

    @Test fun duraciones() {
        assertEquals("52′", duracionCorta(52 * min))
        assertEquals("1 h", duracionCorta(60 * min))
        assertEquals("1 h 05", duracionCorta(65 * min))
        assertEquals("1 H 40", duracionCorta(100 * min, mayus = true))
    }

    @Test fun retosDeLasHermanas() {
        val d = LocalDate.of(2026, 11, 9)
        assertEquals("SIN RETO ESTE AÑO", ritmoAnual(0, 3, d))
        assertEquals("¡RETO CUMPLIDO!", ritmoAnual(20, 20, d))
        assertEquals("¡RETO CUMPLIDO! Y 2 DE PROPINA", ritmoAnual(20, 22, d))
        // 30 al año el 9 de noviembre (día 313 de 365): tocarían 25; con 2, 23 por detrás y quedan 28 en 53 días
        assertEquals("VAS 23 POR DETRÁS · TE QUEDAN 28 EN 53 DÍAS", ritmoAnual(30, 2, d))
        assertEquals("TE FALTAN 188", ritmoPalabras(500, 312))
        assertEquals("¡META CUMPLIDA!", ritmoPalabras(500, 812))
        assertEquals("AÚN NADA HOY", ritmoPalabras(500, 0))
        val gs = listOf(Game(1, "a", "", end = LocalDate.of(2026, 3, 1).toEpochDay()), Game(2, "b", "", end = LocalDate.of(2025, 12, 31).toEpochDay()), Game(3, "c", ""))
        assertEquals(1, terminadosEnAno(gs, 2026))
        val r = retosDeTodas(MetasCuenta(30, 20, 40, 500), 2, 3, 0, 812, d)
        assertEquals(listOf(Hermana.SESHAT, Hermana.BASTET, Hermana.HATHOR, Hermana.THOT), r.map { it.hermana })
        assertEquals(30, r[0].meta); assertEquals(812, r[3].hechos)
    }

    @Test fun metasSinCuenta() {
        // Sin sesión de la familia: sin retos (0) y la meta de palabras de serie de Thot
        val m = metasDeLaCuenta()
        assertEquals(0, m.libros); assertEquals(0, m.juegos); assertEquals(0, m.cine)
        assertEquals(META_PALABRAS_DE_SERIE, m.palabras)
    }

    @Test fun semanaApiladaYReparto() {
        val d = dias(juego = mapOf(hoy to 100 * min, hoy - 2 to 30 * min), libro = mapOf(hoy to 52 * min), palabras = mapOf(hoy to 812, hoy - 10 to 50))
        val s = semanaApilada(d, hoy)
        assertEquals(7, s.size)
        assertEquals(hoy, s.last().dia)
        assertEquals(152 * min, s.last().total)
        assertEquals(812, s.last().palabras)
        assertTrue(repartoDelDia(s.last()).endsWith("1 h 40 jugando · 52′ leyendo · 812 palabras"))
        assertTrue(repartoDelDia(s[0]).endsWith("nada"))
        assertEquals(1, diasSeguidosFamilia(d, hoy))
        val seguidos = dias(libro = (0L..4L).associate { hoy - it to 10 * min })
        assertEquals(5, diasSeguidosFamilia(seguidos, hoy))
    }

    @Test fun loVistoEnHathorDeLaCuenta() {
        // Lo que guarda CuentaFamilia de `tiempo/<ref>~<dispositivo>` de los títulos de Hathor
        val o = JSONObject().put("tv_sev", JSONObject().put("t", "Severance").put("tipo", "tv").put("days", JSONObject().put("2026-11-09", 3_600_000L).put("2026-11-08", 0L)))
        val d = Hermanas.diasVistos(o)
        assertEquals(mapOf("tv_sev" to 3_600_000L), d[hoy])
        assertNull(d[hoy - 1])
    }

    @Test fun lasPaginas() {
        val cine = Kind(7, "Películas", listOf("General", "a"), listOf("En lista", "Viendo", "Abandonada"), false, custom = true, source = SRC_MOVIE)
        val discos = Kind(8, "Discos", listOf("General", "a"), listOf("En lista", "Escuchando", "Abandonado"), false, custom = true)
        val p = paginasJonsu(listOf(GAMES, BOOKS, cine, discos), { 3 }, conEscritos = true)
        assertEquals(listOf("Portada", "Diario", GAMES.title, BOOKS.title, "Cine", "Discos", "Escritos"), p.map { it.nombre })
        assertEquals(listOf(0, 4, 1, 2, 3, 108, 5), p.map { it.destino })
        assertEquals(listOf(null, null, 3, 3, 3, 3, null), p.map { it.cuenta })
        assertEquals(listOf(0, 4, 1, 2), paginasJonsu(listOf(GAMES, BOOKS), { 0 }, conEscritos = false).map { it.destino })
    }

    @Test fun paspartu() {
        assertEquals(PASPARTU_CREMA, colorPaspartu(0.2f))
        assertEquals(PASPARTU_CREMA, colorPaspartu(null))
        assertEquals(PASPARTU_OSCURO, colorPaspartu(0.9f))
        assertEquals(PASPARTU_OSCURO, colorPaspartu(UMBRAL_PASPARTU))
    }

    @Test fun encuadrePorDispositivo() {
        assertEquals(0f to 0f, EncuadreFondo.de(ctx, "0:201", "fondo-a"))
        EncuadreFondo.pon(ctx, "0:201", "fondo-a", 0.4f, -0.7f)
        assertEquals(0.4f to -0.7f, EncuadreFondo.de(ctx, "0:201", "fondo-a"))
        // Otra imagen (o la misma en otra ficha): al centro
        assertEquals(0f to 0f, EncuadreFondo.de(ctx, "0:201", "fondo-b"))
        assertEquals(0f to 0f, EncuadreFondo.de(ctx, "0:202", "fondo-a"))
        // No va a la cuenta: es un archivo propio, fuera de los que suben (y no cambia la ficha ni su `hero`)
        assertTrue(ctx.getSharedPreferences("encuadres_dispositivo", Context.MODE_PRIVATE).all.isNotEmpty())
        EncuadreFondo.pon(ctx, "0:201", "fondo-a", 0f, 0f)
        assertTrue(ctx.getSharedPreferences("encuadres_dispositivo", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test fun coloresDeJonsuYAcentoLegible() {
        // El granate sobre el casi negro no se lee; el legible sí, y se queda en la familia del granate (más rojo que verde)
        assertTrue(contrasteWcag(Color(JONSU_ACENTO), Color(JONSU_FONDO)) < 4.5f)
        val l = acentoLegible(Color(JONSU_ACENTO), Color(JONSU_FONDO), Color(JONSU_TINTA))
        assertTrue(contrasteWcag(l, Color(JONSU_FONDO)) >= 4.5f)
        assertTrue(l.red > l.green)
        // Sobre la cabecera crema, el acento oscurecido también se lee
        assertTrue(contrasteWcag(acentoLegible(Color(JONSU_ACENTO), Color(JONSU_TINTA), Color(JONSU_FONDO)), Color(JONSU_TINTA)) >= 4.5f)
        // Sin nada elegido, los colores de Jonsu; con algo elegido, lo tuyo
        assertTrue(Skin.deSerieJonsu("", "edicion", 0L, 0L, 0L))
        assertTrue(!Skin.deSerieJonsu("", "azul", 0L, 0L, 0L))
        assertTrue(!Skin.deSerieJonsu("arena", "edicion", 0L, 0L, 0L))
        assertTrue(!Skin.deSerieJonsu("", "edicion", 0xFF101010L, 0L, 0L))
        val t = Skin.conPropios(PALETTES.first { it.id == "revista" }.light, JONSU_FONDO, JONSU_TINTA, JONSU_ACENTO)
        assertEquals(Color(JONSU_FONDO), t.cPaper); assertEquals(Color(JONSU_TINTA), t.cInk); assertEquals(Color(JONSU_ACENTO), t.cGeneral())
    }
}
