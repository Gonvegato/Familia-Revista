package com.gonzalo.shai

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Lo de Shai 0.2.0, con el modo prueba (sin IA, sin red, sin gasto): lo que pasa no se repite, las decisiones caducan, Shai recuerda,
 * la temporada se cierra, el alcance va con el nivel y sube poco a poco, las ofertas y la tienda van con tu nivel y tu dinero, y los
 * contratos son de Shai.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContenidoTest {
    private val ctx get() = RuntimeEnvironment.getApplication()

    @Before fun prepara() {
        Carreras.almacen = AlmacenMemoria()
        ClaveApi.fija = ""
        Gasto.mesFijo = java.time.YearMonth.of(2026, 11)
        Motor.reloj = { System.currentTimeMillis() }
        Prueba.pon(ctx, true); Prueba.vacia()
    }
    @After fun suelta() { Prueba.vacia(); Prueba.pon(ctx, false); ClaveApi.fija = null; Gasto.mesFijo = null }

    private fun nueva(n: Nivel = Nivel.CANTERANO, temporada: String = "2026/27"): Universo {
        val c = Contratos.primero(n, "Real Betis", temporada, Rol.JUGADOR)
        val u0 = Ejemplo.universo().copy(temporada = temporada, salario = c.sueldo, dinero = n.dinero, contrato = c, nivel = n.name,
            fuera = Fuera.INICIAL + mapOf(Fuera.FAMA to n.fama, Fuera.AFICION to n.aficion, Fuera.CARISMA to n.carisma))
        return Motor.crea(u0).first
    }

    private fun partido(gf: Int, gc: Int, i: Int, goles: Int = 1, nota: Double = 7.5, t: String = "2026/27") =
        Partido(t, "LaLiga", listOf("Sevilla FC", "Valencia CF", "Girona FC", "Athletic Club", "Getafe CF")[i % 5], i % 2 == 0, gf, gc, 90, goles, 1, nota)

    @Test fun elModoPruebaNoRepiteEventosEnDiezJornadas() {
        val u = nueva()
        repeat(10) { i -> Motor.partido(u.id, partido(listOf(2, 0, 1)[i % 3], listOf(0, 1, 1)[i % 3], i)) }
        // (los del banco: lo del contrato, que monta Shai con sus cuentas, va aparte)
        val eventos = Carreras.entradasDe(u.id, todas = true).filter { it.tipo == TipoEntrada.EVENTO && it.titulo.isNotBlank() && it.decision?.accion.isNullOrBlank() && it.ofertas.none { o -> o.tipo == "renovacion" } }
        assertTrue("eventos: ${eventos.size}", eventos.size >= 18)
        assertEquals("se repite: " + eventos.groupBy { it.titulo }.filter { it.value.size > 1 }.keys, eventos.size, eventos.map { it.titulo }.toSet().size)
        // Y cada uno con su contexto de verdad (no una frase suelta)
        assertTrue(eventos.all { it.texto.length > 250 })
        // Las portadas de cada partido, con su noticia
        val portadas = Carreras.partidos(u.id).mapNotNull { it.first.prensa }
        assertEquals(10, portadas.size)
        assertTrue(portadas.all { it.articulo.split("\n\n").size >= 3 && it.entradillas.isNotEmpty() })
        // La red: largos, con respuestas, citas y tendencia
        val e = Carreras.partidos(u.id).last().first
        assertTrue(e.posts.size >= 9); assertTrue(e.tendencia.startsWith("#")); assertTrue(e.tendenciaPosts > 0)
        assertTrue(e.posts.count { it.respuestas.size >= 2 } >= 2); assertTrue(e.posts.any { it.cita != null })
        assertTrue(e.posts.map { it.texto.length }.average() > 90)
        // Mensajes: conversaciones de varios mensajes
        assertTrue(e.dms.groupBy { it.conversacion }.values.all { it.size >= 2 })
        assertEquals(0.0, Gasto.mes(), 0.0)
    }

    @Test fun lasDecisionesCaducanYNoContestarTieneConsecuencias() {
        val u = nueva()
        Motor.partido(u.id, partido(2, 0, 0))
        val abiertas = Carreras.entradasDe(u.id).filter { it.decision?.abierta == true }
        assertTrue(abiertas.isNotEmpty())
        assertTrue("nunca más de ${Narrador.MAX_ABIERTAS}", abiertas.size <= Narrador.MAX_ABIERTAS)
        Motor.partido(u.id, partido(1, 1, 1))
        abiertas.forEach { a ->
            val d = Carreras.entrada(a.id)!!.decision!!
            assertTrue(d.caducada); assertTrue(d.consecuencia.length > 40)
        }
        assertTrue(Carreras.entradasDe(u.id).any { it.tipo == TipoEntrada.SINCONTESTAR })
        assertTrue(Carreras.entradasDe(u.id).count { it.decision?.abierta == true } <= Narrador.MAX_ABIERTAS)
    }

    @Test fun shaiRecuerdaCadaTresJornadasYSeCierraLaTemporada() {
        val u = nueva()
        repeat(3) { i -> Motor.partido(u.id, partido(2, 1, i)) }
        val m = Carreras.de(u.id)!!
        assertTrue(m.memoria.contains("Su gente:")); assertEquals(3, m.memoriaPaso)
        assertTrue(Narrador.contexto(m, Carreras.entradasDe(u.id), emptyList()).contains("LO QUE SHAI RECUERDA"))
        assertNull(Motor.cierraTemporada(u.id))
        val c = Carreras.de(u.id)!!
        assertEquals("2027/28", c.temporada); assertEquals(listOf("2026/27"), c.cerradas); assertEquals(20, c.personaje.edad)
        val t = Carreras.entradasDe(u.id).first { it.tipo == TipoEntrada.TEMPORADA }
        assertTrue(t.premios.size >= 2); assertTrue(t.texto.split("\n\n").size >= 3); assertTrue(t.ofertas.isNotEmpty())
        assertTrue(Carreras.entradasDe(u.id).any { it.tipo == TipoEntrada.EVENTO && it.texto.length > 200 && it.paso == t.paso })
        assertEquals("La temporada 2026/27 ya está cerrada.", run { Carreras.guarda(c.copy(temporada = "2026/27")); Motor.cierraTemporada(u.id) })
    }

    @Test fun unaEstrellaTieneMuchoMasAlcanceQueUnCanterano() {
        val cant = nueva(Nivel.CANTERANO); val estr = nueva(Nivel.ESTRELLA)
        assertTrue(Alcance.seguidores(estr) > Alcance.seguidores(cant) * 500)
        Motor.partido(cant.id, partido(2, 0, 0)); Motor.partido(estr.id, partido(2, 0, 0))
        fun likes(id: String) = Carreras.partidos(id).last().first.posts.map { it.megusta.toLong() }.average()
        assertTrue("estrella ${likes(estr.id)} vs canterano ${likes(cant.id)}", likes(estr.id) > likes(cant.id) * 100)
        assertTrue(Narrador.contexto(Carreras.de(estr.id)!!, emptyList(), emptyList()).contains("Nivel de AHORA: Estrella"))
        assertTrue(Narrador.contexto(Carreras.de(cant.id)!!, emptyList(), emptyList()).contains("la prensa local"))
    }

    @Test fun elCanteranoSubePocoAPoco() {
        val u = nueva(Nivel.CANTERANO)
        Motor.partido(u.id, partido(4, 0, 0, goles = 3, nota = 9.8))
        val tras1 = Carreras.de(u.id)!!
        assertTrue("fama ${tras1.valorFuera(Fuera.FAMA)}", tras1.valorFuera(Fuera.FAMA) <= Nivel.CANTERANO.fama + Vida.TOPE_FAMA_JORNADA * 2)
        assertEquals(Nivel.CANTERANO, tras1.nivelActual)
        assertTrue(Alcance.seguidores(tras1) * 100 < Alcance.seguidores(Nivel.ESTRELLA.fama))
        repeat(24) { i -> Motor.partido(u.id, partido(3, 1, i + 1, goles = 2, nota = 8.6)) }
        val luego = Carreras.de(u.id)!!
        assertTrue("nivel ${luego.nivelActual} con fama ${luego.valorFuera(Fuera.FAMA)}", luego.nivelActual.ordinal >= Nivel.PROMESA.ordinal)
        assertTrue(luego.famaHist.size > 10)
        // Y nunca más de lo que da el tope en una jornada (el partido y lo demás juntos)
        luego.famaHist.zipWithNext().forEach { (a, b) -> if (b.first == a.first + 1) assertTrue("${a} → ${b}", b.second - a.second <= Vida.TOPE_FAMA_JORNADA + 3) }
    }

    @Test fun aLos18UnBuenTramoEsUnTerremotoYALos29NoTanto() {
        val joven = nueva(Nivel.PROMESA).let { Carreras.guarda(it.copy(personaje = it.personaje.copy(edad = 18))) }
        val veterano = nueva(Nivel.PROMESA).let { Carreras.guarda(it.copy(personaje = it.personaje.copy(edad = 29))) }
        repeat(5) { i -> Motor.partido(joven.id, partido(3, 0, i, goles = 2, nota = 8.8)); Motor.partido(veterano.id, partido(3, 0, i, goles = 2, nota = 8.8)) }
        val j = Carreras.de(joven.id)!!.valorFuera(Fuera.FAMA) - Nivel.PROMESA.fama
        val v = Carreras.de(veterano.id)!!.valorFuera(Fuera.FAMA) - Nivel.PROMESA.fama
        assertTrue("18 años: +$j; 29 años: +$v", j - v >= 10 && j >= v * 1.5)
        assertTrue(Narrador.contexto(Carreras.de(joven.id)!!, emptyList(), Carreras.partidos(joven.id).map { it.second }).contains("sorpresa"))
        assertTrue(Narrador.contexto(Carreras.de(veterano.id)!!, emptyList(), Carreras.partidos(veterano.id).map { it.second }).contains("buena racha"))
        assertTrue(Carreras.partidos(joven.id).last().first.tendencia.startsWith("#Fenómeno"))
        // Y los malos tramos pesan más con la edad
        val malo = partido(0, 3, 0, goles = 0, nota = 4.5).copy(asistencias = 0)
        assertTrue(Vida.famaDelPartido(Rol.JUGADOR, malo, 50, 32) < Vida.famaDelPartido(Rol.JUGADOR, malo, 50, 19))
    }

    @Test fun losHitosYRecords() {
        val u = nueva(Nivel.CANTERANO)   // 19 años
        Motor.partido(u.id, partido(1, 0, 0, goles = 0))
        Motor.partido(u.id, partido(2, 0, 1, goles = 1))
        var c = Carreras.de(u.id)!!
        val gol = c.hitos.single { it.id == "gol_1" }
        assertTrue(gol.precoz); assertEquals(19, gol.edad); assertTrue(c.hitos.any { it.id == "debut" })
        Motor.partido(u.id, partido(4, 1, 2, goles = 3))
        Motor.partido(u.id, partido(2, 1, 3, goles = 1))
        c = Carreras.de(u.id)!!
        assertTrue(c.hitos.any { it.id == "hat_1" && it.titulo == "Primer hat-trick" })
        assertTrue(c.hitos.any { it.id.startsWith("racha_gol_3") })
        assertTrue(c.hitos.any { it.id == "goles_5" }.not())   // (5 no es un número de los que cuentan)
        // Se cuentan: una entrada del hito, la red lo celebra y la prensa lo sabe (sin récords del mundo real)
        assertTrue(Carreras.entradasDe(u.id).any { it.tipo == TipoEntrada.HITO && it.titulo.contains("hat-trick") })
        // (de récords reales, solo los que le pasa la app con sus cifras: HistoricosTest)
        assertTrue(Narrador.REGLAS.contains("solo puedes citar los que vienen en «RÉCORDS HISTÓRICOS REALES»"))
        val r = Hitos.records(c, Carreras.partidos(u.id).map { it.second })
        assertEquals(5, r.goles); assertEquals(1, r.hatTricks); assertEquals(3, r.rachaMarcando); assertEquals(4, r.rachaInvicto)
        // Un título y el veterano: no es precoz
        val vet = nueva(Nivel.TITULAR).let { Carreras.guarda(it.copy(personaje = it.personaje.copy(edad = 30))) }
        Motor.partido(vet.id, partido(1, 0, 0, goles = 1).copy(titulo = "Copa del Rey"))
        val v = Carreras.de(vet.id)!!
        assertTrue(v.hitos.any { it.titulo == "Campeón: Copa del Rey" }); assertTrue(v.hitos.none { it.precoz })
    }

    @Test fun lasOfertasVanConTuNivel() {
        val cant = Ejemplo.universo().copy(fuera = Fuera.INICIAL + (Fuera.FAMA to 5))
        val r = Narrador.leePrensa(mapOf("ofertas" to listOf(mapOf("de" to "Manchester City", "tipo" to "traspaso", "salario_semanal" to 400_000L, "prima" to 50_000_000L, "anios" to 5L, "texto" to "x"))), cant.nivelActual)
        val o = r.ofertas.single()
        assertEquals(Rangos.de(Nivel.CANTERANO).sueldo.last, o.salario); assertEquals(Rangos.de(Nivel.CANTERANO).primaMax, o.prima)
        assertTrue(Narrador.prensa(cant, Ejemplo.partido(), "ctx").cuerpo().toString().contains("OFERTAS PARA SU NIVEL (canterano)"))
        // En el modo prueba, las del banco del nivel (y dentro del rango)
        val u = nueva(Nivel.ESTRELLA)
        Motor.partido(u.id, partido(1, 0, 0))
        assertNull(Motor.cierraTemporada(u.id))
        val ofertas = Carreras.entradasDe(u.id).flatMap { it.ofertas }.filter { it.tipo != "renovacion" }
        assertTrue(ofertas.isNotEmpty())
        val rg = Rangos.de(Carreras.de(u.id)!!.nivelActual)
        ofertas.filter { it.tipo != "patrocinio" }.forEach { assertTrue("${it.de} ${it.salario}", it.salario in rg.sueldo) }
    }

    @Test fun laTiendaVaConTuDinero() {
        val pobre = Ejemplo.universo().copy(dinero = 15_000, salario = 3_000)
        val tienda = Mercado.catalogo(pobre)
        assertTrue(tienda.any { it.nombre == "Piso de dos habitaciones en el barrio" }); assertTrue(tienda.any { it.nombre == "Coche de segunda mano" })
        assertFalse(tienda.any { it.nombre == "Villa frente al mar" || it.nombre == "Avión privado" })
        assertFalse(Mercado.llega(pobre, tienda.first { it.nombre == "Piso de dos habitaciones en el barrio" }))
        val rico = Ejemplo.universo().copy(dinero = 12_000_000, salario = 180_000)
        val lujo = Mercado.catalogo(rico)
        assertTrue(lujo.any { it.nombre == "Villa frente al mar" }); assertTrue(lujo.any { it.nombre == "Hiperdeportivo de colección" })
        assertFalse(lujo.any { it.nombre == "Moto de 125" || it.nombre == "Coche de segunda mano" })
        assertTrue(lujo.size >= tienda.size)
    }

    @Test fun elPrimerContratoVaConElNivel() {
        val c = Contratos.primero(Nivel.CANTERANO, "Real Betis", "2026/27", Rol.JUGADOR)
        val e = Contratos.primero(Nivel.ESTRELLA, "Real Betis", "2026/27", Rol.JUGADOR)
        assertTrue(c.sueldo in Rangos.de(Nivel.CANTERANO).sueldo); assertTrue(e.sueldo in Rangos.de(Nivel.ESTRELLA).sueldo)
        assertTrue(e.sueldo > c.sueldo * 10); assertTrue(e.clausula > c.clausula)
        assertEquals("2028/29", c.hasta); assertEquals("2030/31", e.hasta)
        assertTrue(Contratos.primero(Nivel.TITULAR, "Real Betis", "2026/27", Rol.MISTER).objetivos.isNotBlank())
        // Lo de antes (un sueldo y «3 años») pasa a contrato al leerlo
        val viejo = Universo.deJson(Ejemplo.universo().copy(contrato = null, contratoHasta = "3 años").aJson().replace("\"ct\":{}", "\"x\":1"))!!
        assertEquals(18_000L, viejo.contrato!!.sueldo); assertEquals("2028/29", viejo.contrato!!.hasta)
    }

    @Test fun elSueldoDeLaJornadaEsElDelContrato() {
        val u = nueva()
        val antes = Carreras.de(u.id)!!
        Motor.partido(u.id, partido(1, 0, 0, goles = 0))
        val d = Carreras.de(u.id)!!
        val efectos = Carreras.entradasDe(u.id).sumOf { it.efectos.dinero }
        assertEquals(antes.dinero + antes.contrato!!.sueldo + efectos, d.dinero)
    }

    @Test fun laRenovacionLlegaCuandoSeAcercaElFinal() {
        val u = nueva()
        val c = Carreras.de(u.id)!!
        Carreras.guarda(c.copy(contrato = c.contrato!!.copy(hasta = "2026/27")))
        Motor.partido(u.id, partido(2, 0, 0))
        val o = Carreras.entradasDe(u.id).flatMap { it.ofertas }.single { it.tipo == "renovacion" }
        assertTrue(o.salario in Rangos.de(Carreras.de(u.id)!!.nivelActual).sueldo)
        // Pedir más una vez, aceptar y que cambie el contrato
        val e = Carreras.entradasDe(u.id).first { it.ofertas.any { x -> x.id == o.id } }
        assertNotNull(Motor.contraoferta(e.id, o.id))
        val nueva = Carreras.entrada(e.id)!!.ofertas.single { it.estado == Oferta.ABIERTA }
        assertTrue(nueva.negociada); assertNull(Motor.contraoferta(e.id, nueva.id))
        Motor.oferta(e.id, nueva.id, true)
        val r = Carreras.de(u.id)!!
        assertEquals(nueva.salario, r.contrato!!.sueldo); assertTrue(Contratos.quedan(r.contrato!!, r.temporada) >= 2)
    }

    @Test fun unTraspasoSustituyeElContratoYSinRenovarQuedasLibre() {
        val u = nueva(Nivel.TITULAR)
        val e = Carreras.guarda(Entrada("e1", u.id, TipoEntrada.EVENTO, 1, 0, "t", "x", ofertas = listOf(Oferta("o1", "Sevilla FC", "traspaso", 60_000, 1_000_000, 4, "x"))))
        Motor.oferta(e.id, "o1", true)
        val f = Carreras.de(u.id)!!
        assertEquals("Sevilla FC", f.club); assertEquals("Sevilla FC", f.contrato!!.club); assertEquals(60_000L, f.contrato!!.sueldo); assertEquals("2029/30", f.contrato!!.hasta)
        // Se acaba sin renovar: libre
        Carreras.guarda(f.copy(contrato = f.contrato!!.copy(hasta = "2026/27")))
        Motor.partido(u.id, partido(1, 0, 0))
        Carreras.entradasDe(u.id).forEach { x -> if (x.ofertas.any { it.tipo == "renovacion" }) Carreras.guarda(x.copy(ofertas = x.ofertas.map { it.copy(estado = Oferta.RECHAZADA) })) }
        assertNull(Motor.cierraTemporada(u.id))
        val l = Carreras.de(u.id)!!
        assertTrue(l.libre); assertTrue(Carreras.entradasDe(u.id).any { it.tipo == TipoEntrada.OFICIAL && it.titulo == "Eres libre" })
        assertTrue(Narrador.contexto(l, emptyList(), emptyList()).contains("SIN CONTRATO"))
    }
}
