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
 * Lo de FcFeed 0.7.0: las competiciones (desplegable, agrupar variantes, la búsqueda y Haiku para lo suelto), el MVP, la gente que sale con
 * varios nombres en un solo chat, hablar con cualquiera, darle puerta y la gente nueva con la fama. Todo con la API de mentira.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GenteYCompeticionesTest {
    private lateinit var api: ApiDeMentira

    @Before fun prepara() { api = Ejemplo.prepara() }
    @After fun suelta() { ClaveApi.fija = null; Gasto.mesFijo = null }

    private fun pj(comp: String, rival: String = "Sevilla FC", mvp: Boolean = false) = Ejemplo.partido(2, 1, rival).copy(competicion = comp, mvp = mvp)

    // ---------------------------------------------------------------- competiciones

    @Test fun lasVariantesVanALaCompeticionDeLaLista() {
        val l = listOf("LaLiga EA Sports", "Copa del Rey", "Supercopa de España", "UEFA Europa League")
        listOf("LaLiga", "La Liga", "liga", "LALIGA EA SPORTS", "laliga ea sports").forEach { assertEquals(it, "LaLiga EA Sports", Competiciones.canonica(it, l)) }
        listOf("copa", "Copa del rey", "COPA").forEach { assertEquals(it, "Copa del Rey", Competiciones.canonica(it, l)) }
        listOf("amistoso", "Amistosos", "pretemporada", "Trofeo Joan Gamper").forEach { assertEquals(it, Competiciones.AMISTOSO, Competiciones.canonica(it, l)) }
        assertEquals("UEFA Europa League", Competiciones.canonica("europa league", l))
        assertEquals("Supercopa de España", Competiciones.canonica("supercopa", l))
        assertEquals(Competiciones.SELECCION, Competiciones.canonica("Selección", l))
        assertNull(Competiciones.canonica("Torneo de verano del pueblo", l))
    }

    @Test fun elDesplegableTraeLasDelClubAmistosoYSeleccion() {
        val u = Carreras.guarda(Ejemplo.universo().copy(comps = listOf("LaLiga EA Sports", "Copa del Rey"), compsDe = "Real Betis"))
        val l = Competiciones.lista(u)
        // (y desde la 0.12.0, las de selecciones entre las del club y «Amistoso»)
        assertEquals(listOf("LaLiga EA Sports", "Copa del Rey") + Competiciones.DE_SELECCIONES + listOf(Competiciones.AMISTOSO, Competiciones.SELECCION), l)
        // «Otra…»: se añade a la lista (y no se repite)
        assertEquals("Trofeo de la ciudad", Motor.anadeCompeticion(u.id, "trofeo de la ciudad"))
        assertTrue("Trofeo de la ciudad" in Competiciones.lista(Carreras.de(u.id)!!))
        assertEquals("LaLiga EA Sports", Motor.anadeCompeticion(u.id, "laliga ea sports"))
    }

    @Test fun losPartidosViejosSeAgrupanYLoSueltoLoEmparejaHaiku() {
        val (u0, _) = Motor.crea(Ejemplo.universo())
        // Antes de la 0.7.0: cada partido con su forma de escribirlo, y sin la lista del club
        listOf("LaLiga", "liga", "La Liga", "amistosos", "Copa", "Trofeo Gamper").forEach { Motor.partido(u0.id, pj(it)) }
        // Sin la lista del club, las variantes ya se juntan entre ellas
        Motor.normalizaCompeticiones(u0.id, conIa = false)
        val sinLista = Carreras.partidos(u0.id).map { it.second.competicion }.distinct()
        assertTrue(sinLista.toString(), sinLista.containsAll(listOf("LaLiga", Competiciones.AMISTOSO, "Copa")) && "liga" !in sinLista && "La Liga" !in sinLista)
        // Se buscan sus competiciones (búsqueda web de mentira) → la lista del club, y los partidos se agrupan con ella
        assertNull(Motor.buscaCompeticiones(u0.id))
        val u = Carreras.de(u0.id)!!
        assertEquals(listOf("LaLiga EA Sports", "Copa del Rey", "UEFA Europa League"), u.comps)
        val cs = Carreras.partidos(u.id).map { it.second }
        assertEquals(3, cs.count { it.competicion == "LaLiga EA Sports" })
        assertEquals(1, cs.count { it.competicion == "Copa del Rey" })
        // «Trofeo Gamper» es un amistoso por las reglas (trofeo); lo que se metió se guarda
        assertEquals(2, cs.count { it.competicion == Competiciones.AMISTOSO })
        assertTrue(cs.any { it.competicionOriginal == "liga" })
        // Las cifras van por competición de verdad
        val porComp = Estadisticas.porTemporada(cs).first().second.map { it.competicion }
        assertEquals(setOf("LaLiga EA Sports", "Copa del Rey", Competiciones.AMISTOSO), porComp.toSet())
    }

    @Test fun loQueNoEncajaPorReglasLoPreguntaHaikuUnaVez() {
        val (u0, _) = Motor.crea(Ejemplo.universo().copy(comps = listOf("LaLiga EA Sports", "UEFA Europa League"), compsDe = "Real Betis"))
        Motor.partido(u0.id, pj("la orejona del jueves"))
        Motor.partido(u0.id, pj("torneo raro"))
        val antes = api.mensajes().size
        Motor.normalizaCompeticiones(u0.id, conIa = true)
        val pedidas = api.mensajes().drop(antes).filter { Peticion.esquemaDe(it.cuerpo)?.get("properties").toString().contains("grupos") }
        assertEquals(1, pedidas.size)
        assertEquals("claude-haiku-5-5", pedidas.single().cuerpo["model"])
        val cs = Carreras.partidos(u0.id).map { it.second.competicion }
        assertTrue(cs.toString(), "UEFA Europa League" in cs && Competiciones.OTRA in cs)
        // Ya preguntado: no se vuelve a preguntar
        val n = api.mensajes().size
        Motor.normalizaCompeticiones(u0.id, conIa = true)
        assertEquals(n, api.mensajes().size)
    }

    @Test fun seCorrigeLaCompeticionDeUnPartidoGuardado() {
        val (u, _) = Motor.crea(Ejemplo.universo())
        val (e, _) = Motor.partido(u.id, pj("LaLiga"))
        Motor.corrigePartido(e.id, "Copa del Rey", mvp = true)
        val p = Carreras.entrada(e.id)!!.partido!!
        assertEquals("Copa del Rey", p.competicion); assertEquals("LaLiga", p.competicionOriginal); assertTrue(p.mvp)
    }

    // ---------------------------------------------------------------- MVP

    @Test fun elMvpSeGuardaCuentaYSeCelebra() {
        val p = pj("LaLiga", mvp = true)
        assertTrue(Partido.de(p.aMapa()).mvp)
        assertFalse(Partido.de(p.aMapa() - "mv").mvp)
        assertTrue(p.resumen(Rol.JUGADOR).contains("MVP"))
        assertTrue(Vida.famaDelPartido(Rol.JUGADOR, p, 20, 24) >= Vida.famaDelPartido(Rol.JUGADOR, p.copy(mvp = false), 20, 24))
        val (u, _) = Motor.crea(Ejemplo.universo())
        Motor.partido(u.id, p)
        Motor.partido(u.id, pj("LaLiga", "Girona FC", mvp = false))
        val nu = Carreras.de(u.id)!!
        assertTrue(nu.hitos.any { it.id == "mvp_1" && it.titulo == "Primer MVP" })
        assertEquals(1, Estadisticas.linea("", "", Carreras.partidos(u.id).map { it.second }).mvps)
        // Va a Claude
        assertTrue(api.mensajes().last { "ACABA DE JUGAR" in it.cuerpo.toString() && "FUE EL MVP" in it.cuerpo.toString() } != null)
    }

    // ---------------------------------------------------------------- la misma persona, un solo chat

    @Test fun marisaMarisaBloiseYMamaSonLaMismaPersonaYUnSoloChat() {
        val madre = Persona("p0madre001", "Marisa Bloise", "madre", 85)
        val kike = Persona("p0kike0001", "Kike Lozano", "amigo de siempre", 70)
        val u = Carreras.guarda(Ejemplo.universo().copy(entorno = listOf(madre, kike)))
        assertEquals(madre, Gente.quien(u.entorno, "Marisa"))
        assertEquals(madre, Gente.quien(u.entorno, "mamá"))
        assertEquals(madre, Gente.quien(u.entorno, "Mi madre"))
        assertEquals(madre, Gente.quien(u.entorno, "MARISA BLOISE"))
        assertEquals(kike, Gente.quien(u.entorno, "Kike"))
        assertNull(Gente.quien(u.entorno, "Vera Salinas"))
        // Lo que había guardado: tres chats para la misma persona
        listOf(Dm("Marisa", "¡Ese es mi niño!"), Dm("Marisa Bloise", "Abrígate"), Dm("mamá", "¿Vienes el domingo?", chat = "mamá"), Dm("Kike", "¿Salimos?"))
            .forEachIndexed { i, d -> Carreras.guarda(Entrada("e$i", u.id, TipoEntrada.EVENTO, 1000L + i, 1, "x", dms = listOf(d))) }
        val convs = conversaciones(u, Carreras.entradasDe(u.id))
        assertEquals(listOf("Kike Lozano", "Marisa Bloise").sorted(), convs.map { it.nombre }.sorted())
        val suya = convs.first { it.nombre == "Marisa Bloise" }
        assertEquals(3, suya.recibidos); assertFalse(suya.grupo); assertEquals(madre.id, suya.persona?.id)
        // «Separar»: «mamá» no es ella (por ejemplo, era otra persona) → su propio chat; y «Es la misma persona que…» lo vuelve a juntar
        Motor.separa(u.id, "mamá", madre.id)
        val u2 = Carreras.de(u.id)!!
        assertEquals(3, conversaciones(u2, Carreras.entradasDe(u.id)).size)
        Motor.junta(u.id, "mamá", madre.id)
        assertEquals(2, conversaciones(Carreras.de(u.id)!!, Carreras.entradasDe(u.id)).size)
        // Lo que manda Claude con su id va a su persona aunque el nombre venga raro
        Carreras.guarda(Entrada("e9", u.id, TipoEntrada.EVENTO, 2000L, 2, "y", dms = listOf(Dm("La señora Bloise", "Te he dejado tuppers", pid = madre.corto))))
        assertEquals(4, conversaciones(Carreras.de(u.id)!!, Carreras.entradasDe(u.id)).first { it.nombre == "Marisa Bloise" }.recibidos)
    }

    @Test fun aClaudeSeLePasaLaGenteConSuIdYSeLePideElNombreExacto() {
        val u = Ejemplo.universo().copy(entorno = listOf(Persona("p0madre001", "Marisa Bloise", "madre", 85)))
        val ctx = Narrador.contexto(u, emptyList(), emptyList())
        assertTrue(ctx, ctx.contains("[p0madr] Marisa Bloise, madre, afinidad 85"))
        assertTrue(Narrador.REGLAS.contains("nombre EXACTO"))
        assertTrue(Peticion.esquemaDe(Narrador.red(u, Ejemplo.partido(), ctx).cuerpo()).toString().contains("id"))
    }

    // ---------------------------------------------------------------- hablar con cualquiera y darle puerta

    @Test fun hablarConAlguienLeCambiaYQuedaEnSuChat() {
        val agente = Persona("p0agente01", "Toni Vidal", "representante", 50, "Siempre al teléfono", actitud = "Presiona para que cambie de club")
        val (u, _) = Motor.crea(Ejemplo.universo().copy(entorno = listOf(agente)))
        val gasto = Gasto.mes()
        assertNull(Motor.charla(u.id, agente.id, "Toni, entiendo que quieres lo mejor para mí, pero quiero quedarme aquí y triunfar. Te pido que lo respetes."))
        val p = Carreras.de(u.id)!!.entorno.first { it.id == agente.id }
        assertTrue(p.afinidad > agente.afinidad)
        assertEquals("Ha dejado de presionarle con el cambio de club", p.actitud)
        assertTrue(Gasto.mes() > gasto)
        val pedida = api.mensajes().last()
        assertEquals("claude-sonnet-5-5", pedida.cuerpo["model"])
        assertTrue(pedida.cuerpo.toString().contains("quiero quedarme aquí"))
        val c = conversaciones(Carreras.de(u.id)!!, Carreras.entradasDe(u.id)).first { it.nombre == "Toni Vidal" }
        assertTrue(c.dms.any { it.mio && "quedarme" in it.texto })
        assertTrue(c.dms.any { !it.mio && "escuchado" in it.texto })
    }

    @Test fun darlePuertaAlRepresentanteTraeCandidatosYYaNoEscribe() {
        val agente = Persona("p0agente01", "Toni Vidal", "representante", 40)
        val (u, _) = Motor.crea(Ejemplo.universo().copy(entorno = listOf(agente, Persona("p0madre001", "Loli Cortés", "madre", 85))))
        assertNull(Motor.puerta(u.id, agente.id, "Me tienes harto con lo del cambio de club"))
        val nu = Carreras.de(u.id)!!
        assertTrue(nu.entorno.none { it.id == agente.id })
        val fuera = nu.exEntorno.single()
        assertEquals("Me tienes harto con lo del cambio de club", fuera.porque); assertTrue(fuera.salio > 0)
        assertTrue(nu.entorno.any { it.nombre == "Elena Sanz" && it.desde > 0 })
        assertTrue(Carreras.entradasDe(u.id).any { it.tipo == TipoEntrada.PUERTA && it.dms.any { d -> d.de == "Toni Vidal" } })
        // Ya no escribe: lo nuevo que trae su nombre se queda fuera
        assertTrue(Gente.sinExpulsados(nu, listOf(Dm("Toni Vidal", "¿Hablamos?"), Dm("Loli Cortés", "Bien hecho"))).map { it.de } == listOf("Loli Cortés"))
        assertTrue(Narrador.contexto(nu, emptyList(), emptyList()).contains("YA NO ESTÁN EN SU VIDA"))
        // Y puede volver
        Motor.recupera(u.id, agente.id)
        assertTrue(Carreras.de(u.id)!!.entorno.any { it.id == agente.id && it.afinidad == 20 })
    }

    // ---------------------------------------------------------------- gente nueva con la fama

    @Test fun alSubirDeNivelEntraGenteNuevaUnaVez() {
        // Era promesa y ya tiene fama de titular (45): con el siguiente partido entra gente nueva
        val (u0, _) = Motor.crea(Ejemplo.universo().copy(fuera = Fuera.INICIAL + (Fuera.FAMA to 30)))
        assertEquals(Nivel.PROMESA.ordinal, Carreras.de(u0.id)!!.nivelGente)
        Carreras.guarda(Carreras.de(u0.id)!!.copy(fuera = Fuera.INICIAL + (Fuera.FAMA to 45)))
        val antes = Carreras.de(u0.id)!!.entorno.size
        val (e, _) = Motor.partido(u0.id, Ejemplo.partido())
        val u = Carreras.de(u0.id)!!
        val nuevas = u.entorno.drop(antes)
        assertTrue(nuevas.toString(), nuevas.size == Gente.cuantas(Nivel.TITULAR) && nuevas.all { it.desde == u.paso && Gente.nueva(u, it) })
        assertEquals(Nivel.TITULAR.ordinal, u.nivelGente)
        assertTrue(Carreras.entradasDe(u.id).any { it.tipo == TipoEntrada.GENTE })
        // Se presentan por mensaje (en su propio chat)
        val convs = conversaciones(u, Carreras.entradasDe(u.id))
        nuevas.forEach { p -> assertTrue(p.nombre, convs.any { it.nombre == p.nombre && it.recibidos > 0 }) }
        assertTrue(Carreras.entrada(e.id)!!.dms.any { d -> nuevas.any { it.id == d.pid } })
        // Con el siguiente partido (mismo nivel), nadie más
        val n = u.entorno.size
        Motor.partido(u0.id, Ejemplo.partido(1, 1, "Girona FC"))
        assertEquals(n, Carreras.de(u0.id)!!.entorno.size)
    }

    @Test fun laGenteNoPasaDelTope() {
        val muchos = (1..Gente.MAX).map { Persona("p$it" + "xxxxxxxx", "Persona $it", "amigo") }
        val u = Ejemplo.universo().copy(entorno = muchos)
        assertEquals(Gente.MAX, Gente.anade(u, listOf(Persona("", "Otra Más", "influencer"))).entorno.size)
    }

    // ---------------------------------------------------------------- modo prueba

    @Test fun enModoPruebaLasCharlasYLaPuertaSonRicasYGratis() {
        Prueba.pon(RuntimeEnvironment.getApplication(), true)
        try {
            ClaveApi.fija = ""
            val amigo = Persona("p0kike0001", "Kike Lozano", "amigo de siempre", 60, "Vive de noche", actitud = "Insiste en salir de fiesta cada semana")
            val (u, _) = Motor.crea(Ejemplo.universo().copy(entorno = listOf(amigo)))
            assertNull(Motor.charla(u.id, amigo.id, "Kike, te quiero mucho, pero necesito que entiendas que no voy a salir más entre semana. Te pido que lo respetes de verdad."))
            val p = Carreras.de(u.id)!!.entorno.first()
            assertTrue(p.afinidad > 60); assertTrue(p.actitud, p.actitud.contains("arrastrar"))
            val disc = Carreras.de(u.id)!!.valorFuera(Fuera.DISCIPLINA)
            assertNull(Motor.puerta(u.id, amigo.id, ""))
            val nu = Carreras.de(u.id)!!
            assertTrue(nu.valorFuera(Fuera.DISCIPLINA) > disc)
            assertTrue(Carreras.entradasDe(u.id).first { it.tipo == TipoEntrada.PUERTA }.texto.length > 120)
            assertEquals(0.0, Gasto.mes(), 0.0)
        } finally { Prueba.pon(RuntimeEnvironment.getApplication(), false) }
    }
}

/** FcFeed 0.7.1: el orden (cronológico, como un chat) y las conversaciones del modo prueba sin repetir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrdenYSinRepetirTest {
    @Before fun prepara() { Ejemplo.prepara() }
    @After fun suelta() { ClaveApi.fija = null; Gasto.mesFijo = null }

    @Test fun todoVaPorJornadaYDentroPorMomento() {
        val madre = Persona("p0madre001", "Marisa Bloise", "madre", 85)
        val u = Carreras.guarda(Ejemplo.universo().copy(entorno = listOf(madre), paso = 5))
        // Creadas en desorden: lo de la jornada 2 se guardó antes que lo de la 1; el evento de la jornada 2 llegó (por lotes) después del partido 3
        Carreras.guarda(Entrada("eB", u.id, TipoEntrada.EVENTO, 1_000, 2, "La entrevista", dms = listOf(Dm("Marisa", "Me han llamado de una revista"))))
        Carreras.guarda(Entrada("eA", u.id, TipoEntrada.EVENTO, 5_000, 1, "Tu coche", dms = listOf(Dm("mamá", "Te ha llegado una multa"))))
        Carreras.guarda(Entrada("eP2", u.id, TipoEntrada.PARTIDO, 500, 2, partido = Ejemplo.partido(), dms = listOf(Dm("Marisa Bloise", "¡Ese es mi niño!"))))
        Carreras.guarda(Entrada("eP3", u.id, TipoEntrada.PARTIDO, 6_000, 3, partido = Ejemplo.partido(), dms = listOf(Dm("Marisa", "Bien jugado"))))
        Carreras.guarda(Entrada("eC", u.id, TipoEntrada.EVENTO, 9_000, 2, "Llega tarde", dms = listOf(Dm("Marisa", "Lo de la cena"))))
        val es = Carreras.entradasDe(u.id)
        // Lo más nuevo primero (Red, Inicio): jornada 3, luego la 2 (lo de después del partido, encima del partido), luego la 1
        assertEquals(listOf("eP3", "eC", "eB", "eP2", "eA"), es.map { it.id })
        // El chat, de lo más viejo a lo más nuevo
        val c = conversaciones(u, es).single()
        assertEquals(listOf("Te ha llegado una multa", "¡Ese es mi niño!", "Me han llamado de una revista", "Lo de la cena", "Bien jugado"), c.dms.map { it.texto })
        assertEquals("eP3", c.ultimaEntrada?.id)
    }

    @Test fun losChatsVanPorLoUltimoDeCadaUno() {
        val u = Carreras.guarda(Ejemplo.universo().copy(entorno = listOf(Persona("p0madre001", "Marisa Bloise", "madre"), Persona("p0kike0001", "Kike Lozano", "amigo")), paso = 3))
        Carreras.guarda(Entrada("e1", u.id, TipoEntrada.EVENTO, 9_000, 1, "x", dms = listOf(Dm("Kike", "Hola"))))
        Carreras.guarda(Entrada("e2", u.id, TipoEntrada.EVENTO, 1_000, 3, "y", dms = listOf(Dm("Marisa", "Hola, hijo"))))
        assertEquals(listOf("Marisa Bloise", "Kike Lozano"), conversaciones(u, Carreras.entradasDe(u.id)).map { it.nombre })
    }

    @Test fun enModoPruebaLasConversacionesNoSeRepitenEnLaCarrera() {
        val ctx = RuntimeEnvironment.getApplication()
        Prueba.pon(ctx, true)
        try {
            ClaveApi.fija = ""
            val (u, _) = Motor.crea(Ejemplo.universo())
            repeat(14) { i -> Motor.partido(u.id, Ejemplo.partido(2 + i % 2, 1, "Rival $i")) }
            val textos = Carreras.entradasDe(u.id, todas = true).filter { it.tipo == TipoEntrada.PARTIDO }.flatMap { e -> e.dms.map { e.id to it.texto } }
            assertTrue(textos.size > 20)
            val repetidos = textos.groupBy { it.second }.filterValues { l -> l.map { it.first }.distinct().size > 1 }.keys
            assertTrue(repetidos.joinToString("\n"), repetidos.isEmpty())
        } finally { Prueba.pon(ctx, false) }
    }
}
