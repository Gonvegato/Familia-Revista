package com.gonzalo.shai

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Los récords del fútbol de verdad (la lista de 200): los datos, las fechas exactas y la comparación con su carrera en Shai. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoricosTest {
    private lateinit var api: ApiDeMentira

    @Before fun prepara() { api = Ejemplo.prepara() }
    @After fun suelta() { ClaveApi.fija = null; Gasto.mesFijo = null; runCatching { Prueba.pon(RuntimeEnvironment.getApplication(), false) } }

    /** Un chaval nacido de forma que el [en] tenga [a] años y [d] días. */
    private fun nacidoPara(en: LocalDate, a: Int, d: Int) = en.minusDays(d.toLong()).minusYears(a.toLong())

    private val DIA = LocalDate.of(2026, 10, 21)

    private fun champions(goles: Int = 1, fecha: LocalDate = DIA) = Partido("2026/27", "UEFA Champions League", "Inter de Milán", true, 2, 1, 90, goles, 0, 8.0, fecha = Fechas.iso(fecha))

    private fun jugador(nac: LocalDate?) = Ejemplo.universo().let { it.copy(personaje = it.personaje.copy(edad = 17, nacimiento = nac?.let { n -> Fechas.iso(n) } ?: "")) }

    @Test fun hayDoscientosYLasCorreccionesEstan() {
        val t = RecordsReales.TODOS
        assertEquals(200, t.size)
        assertEquals((1..200).toList(), t.map { it.n })
        assertEquals("32 Copas del Rey", RecordsReales.de(127)!!.marca)
        assertEquals("13 Bundesligas", RecordsReales.de(139)!!.marca)
        assertTrue("Neuer" in RecordsReales.de(139)!!.quien)
        RecordsReales.de(178)!!.let { assertTrue("veterano en la Primera inglesa" in it.record); assertNull(it.clave); assertEquals(50, it.anios); assertEquals(5, it.dias); assertTrue("Miura" in it.nota) }
        assertTrue(RecordsReales.de(60)!!.dudoso); assertTrue(RecordsReales.de(151)!!.dudoso); assertTrue(RecordsReales.de(32)!!.dudoso)
        RecordsReales.de(85)!!.let { assertEquals(78, it.anios); assertEquals(-1, it.dias); assertEquals("78 años", it.marca) }
        listOf(58, 62, 143, 198).forEach { assertEquals(RecordReal.COMPROBADO, RecordsReales.de(it)!!.fuente) }
        RecordsReales.de(8)!!.let { assertEquals(17, it.anios); assertEquals(40, it.dias); assertEquals("gol_joven:champions", it.clave) }
    }

    @Test fun lasFechasYLaEdadExacta() {
        assertEquals(LocalDate.of(2026, 8, 12), Fechas.lee("12/08/2026"))
        assertEquals(LocalDate.of(2026, 8, 12), Fechas.lee("12-8-26"))
        assertEquals(LocalDate.of(2026, 8, 12), Fechas.lee("2026-08-12"))
        assertEquals(LocalDate.of(2026, 8, 12), Fechas.lee("12 ago 2026"))
        assertNull(Fechas.lee("mañana")); assertNull(Fechas.lee("31/02/2026"))
        assertEquals(17 to 40, Fechas.edad(LocalDate.of(2009, 3, 1), LocalDate.of(2026, 4, 10)))
        assertEquals(16 to 87, Fechas.edad(nacidoPara(DIA, 16, 87), DIA))
        assertEquals("17 años y 1 día", Fechas.textoEdad(17, 1))
    }

    @Test fun conDiecisieteAniosYTreintaDiasBateElDeAnsuFati() {
        val u = jugador(nacidoPara(DIA, 17, 30))
        val c = Historicos.nuevos(u, listOf(champions())).firstOrNull { it.r.n == 8 }
        assertNotNull(c)
        assertEquals(Historicos.Estado.BATE, c!!.estado)
        assertEquals("17 años y 30 días", c.suMarca)
        assertTrue(c.texto, "Lo bate por 10 días" in c.texto)
        assertTrue("Ansu Fati" in c.texto)
        assertTrue(c.linea.startsWith("- BATE · #8 Goleador más joven en la Champions League · récord: Ansu Fati"))
    }

    @Test fun conDiecisieteAniosYCincuentaDiasSeQuedaADiezDias() {
        val u = jugador(nacidoPara(DIA, 17, 50))
        val c = Historicos.nuevos(u, listOf(champions())).first { it.r.n == 8 }
        assertEquals(Historicos.Estado.CERCA, c.estado)
        assertTrue(c.texto, "se queda a 10 días del récord de Ansu Fati" in c.texto)
        // (y solo «a X días»: nunca «segundo más joven»)
        assertFalse("segundo" in c.texto)
    }

    @Test fun losDudososNoCuentanYSinFechaDeNacimientoNoSeComparanDias() {
        val u = jugador(null)
        val cs = Historicos.nuevos(u, listOf(champions(goles = 14)))
        assertTrue(cs.none { it.r.deEdad && it.r.dias >= 0 })
        assertTrue(cs.none { it.r.n == 26 })                  // «más de 900 goles»: dudoso
        assertNotNull(cs.firstOrNull { it.r.n == 31 && it.estado == Historicos.Estado.BATE }) // 14 goles en un partido > 13 de Archie Thompson
        assertNotNull(cs.firstOrNull { it.r.n == 53 && it.estado == Historicos.Estado.BATE }) // y más de 5 en Champions
        // Ninguno de los que no se pueden comparar o son dudosos
        assertTrue(cs.all { it.r.usable })
    }

    @Test fun elRecordBatidoSeGuardaYNoSeRepite() {
        Gasto.mesFijo = java.time.YearMonth.of(2026, 11)
        val (u0, _) = Motor.crea(jugador(nacidoPara(DIA, 17, 30)))
        Motor.partido(u0.id, champions())
        val u1 = Carreras.de(u0.id)!!
        val suyo = u1.recordsSuyos.first { it.n == 8 }
        assertEquals("17 años y 30 días", suyo.marca)
        val rec = Carreras.entradasDe(u0.id).first { it.tipo == TipoEntrada.RECORD }
        assertTrue(rec.titulo, "Récord histórico: Goleador más joven en la Champions League" in rec.titulo)
        assertTrue(u1.valorFuera(Fuera.FAMA) > u0.valorFuera(Fuera.FAMA) + 10)
        assertTrue(u1.hitos.any { it.real == 8 && it.linea.isNotBlank() })
        // A Claude le llega la línea con las cifras tal cual
        val prensa = api.mensajes().map { it.cuerpo.toString() }.last { "RÉCORDS HISTÓRICOS REALES" in it }
        assertTrue("- BATE · #8 Goleador más joven en la Champions League" in prensa)
        // El siguiente gol en Champions (sin fecha: la estima Shai, una semana después) ya no es «el primero»
        Motor.partido(u0.id, champions().copy(fecha = ""))
        val ult = Carreras.partidos(u0.id).last().second
        assertTrue(ult.fechaEstimada); assertEquals(DIA.plusDays(7), Fechas.lee(ult.fecha))
        assertEquals(1, Carreras.entradasDe(u0.id).count { it.tipo == TipoEntrada.RECORD })
    }

    @Test fun losDeCifraYSuPropioRecord() {
        val u = jugador(null)
        // 50 goles en LaLiga en una temporada (iguala a Messi) y el 51 lo bate
        val liga = (1..25).map { Partido("2026/27", "LaLiga EA Sports", "Rival $it", true, 3, 0, 90, 2, 0, 8.0) }
        val iguala = Historicos.nuevos(u, liga).firstOrNull { it.r.n == 43 }
        assertEquals(Historicos.Estado.IGUALA, iguala?.estado)
        val bate = Historicos.nuevos(u, liga + Partido("2026/27", "LaLiga", "Otro", true, 1, 0, 90, 1, 0, 7.0)).first { it.r.n == 43 }
        assertEquals(Historicos.Estado.BATE, bate.estado)
        // Ya suyo (51): la temporada siguiente hay que pasar de 51
        val conSuyo = u.copy(recordsSuyos = listOf(RecordSuyo(43, 51.0, "51 goles", 26, "2026/27")))
        val sig = (1..26).map { Partido("2027/28", "LaLiga", "R$it", true, 2, 0, 90, 2, 0, 8.0) }
        val vuelve = Historicos.nuevos(conSuyo, liga + sig).firstOrNull { it.r.n == 43 }
        assertTrue(vuelve!!.contraSuyo); assertTrue("su propio récord" in vuelve.texto)
        // A tiro: con 40 goles en la liga de esta temporada, le faltan 10 para igualar
        val tiro = Historicos.aTiro(u, liga.take(20)).first { it.r.n == 43 }
        assertTrue(tiro.texto, "le faltan 10 goles para igualar" in tiro.texto)
    }

    @Test fun enModoPruebaLaPortadaEsDelRecord() {
        Prueba.pon(RuntimeEnvironment.getApplication(), true)
        Gasto.mesFijo = java.time.YearMonth.of(2026, 11)
        val (u0, _) = Motor.crea(jugador(nacidoPara(DIA, 17, 30)))
        val (e, fallo) = Motor.partido(u0.id, champions())
        assertNull(fallo)
        val pr = Carreras.entrada(e.id)!!.prensa!!
        assertTrue(pr.titular, "historia" in pr.titular)
        assertTrue(pr.entradilla, "Ansu Fati" in pr.entradilla && "17 años y 30 días" in pr.entradilla)
        assertTrue(Carreras.entrada(e.id)!!.posts.any { "HISTÓRICO" in it.texto })
        Prueba.vacia()
    }
}
