package com.gonzalo.shai

import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lo de Shai sin pantallas: el modelo, las cuentas del dinero y de la bolsa, lo que se manda a la API (y cómo), lo que cuesta y el tope,
 * y el recorrido entero de una carrera (con la API de mentira: no sale nada a la red ni se gasta nada).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaiTest {
    private lateinit var api: ApiDeMentira

    @Before fun prepara() { api = Ejemplo.prepara() }
    @After fun suelta() { ClaveApi.fija = null; Gasto.mesFijo = null }

    // ---------------------------------------------------------------- el modelo

    @Test fun laCarreraYLoQuePasaSeGuardanYSeLeenIgual() {
        val u = Ejemplo.universo().copy(entorno = listOf(Persona("p1", "Loli", "madre", 80, "lo ve todo")), cartera = mapOf("GRD" to 10L),
            bienes = listOf(Bien("b1", Bien.COCHE, "Berlina", 95_000, 90_000, -120, 3)), precios = Mercado.inicial(), lotes = listOf(Lote("msgbatch_1", 5, 2)), autoEventos = false)
        assertEquals(u, Universo.deJson(u.aJson()))
        val e = Entrada("e1", u.id, TipoEntrada.PARTIDO, 10, 1, "t", "x", Ejemplo.partido().copy(equipo = mapOf("Posesión" to "58 / 42")),
            listOf(Post("A", "@a", "hola", 3, 1, "prensa")), listOf(Dm("Loli", "¡Bien!")), Prensa("Diario", "Titular", "Entradilla", listOf("Otro")),
            listOf(Oferta("o1", "Villarreal CF", "traspaso", 40_000, 1_000, 4, "texto")), Decision("d1", "Toni", "¿Vas?", listOf("Sí", "No")),
            Efectos(100, mapOf(Fuera.FAMA to 3), mapOf("Loli" to -2), listOf(Persona("p2", "Irene", "ligue"))), 99, false, "red")
        assertEquals(e, Entrada.deJson(e.aJson()))
        assertNull(Universo.deJson("{no es json")); assertNull(Entrada.deJson(""))
    }

    @Test fun lasCifrasPorTemporadaYCompeticion() {
        val ps = listOf(Ejemplo.partido(2, 0), Ejemplo.partido(1, 1).copy(competicion = "Copa del Rey", goles = 0, asistencias = 0, nota = 6.5), Ejemplo.partido(0, 1).copy(temporada = "2027/28", nota = 0.0))
        val t = Estadisticas.porTemporada(ps)
        assertEquals("2027/28", t.first().first.temporada)
        val s = t.last()
        assertEquals(2, s.first.jugados); assertEquals(1, s.first.victorias); assertEquals(1, s.first.empates); assertEquals(2, s.first.goles)
        assertEquals((8.7 + 6.5) / 2, s.first.notaMedia, 0.001)
        assertEquals(listOf("LaLiga", "Copa del Rey"), s.second.map { it.competicion }.sorted().reversed())
        assertEquals("VED", Estadisticas.racha(ps))
        assertEquals("1-1", ps[1].resultado)
    }

    @Test fun unaJornadaPagaElSueldoYMueveLaBolsaSiempreIgual() {
        val u = Ejemplo.universo().copy(precios = Mercado.inicial(), bienes = listOf(Bien("b1", Bien.NEGOCIO, "Cafetería", 150_000, 150_000, 900, 0)))
        val a = Vida.jornada(u, Ejemplo.partido().copy(media = 76))
        assertEquals(1, a.paso)
        assertEquals(250_000 + 18_000 + 900L, a.dinero)
        assertEquals(76, a.personaje.media); assertEquals(listOf(0 to 74, 1 to 76), a.personaje.historial)
        assertTrue(a.precios.values.all { it.size == 2 })
        // (la misma carrera y la misma jornada, los mismos precios: no depende de cuándo lo mires)
        assertEquals(a.precios, Vida.jornada(u, null).precios)
    }

    @Test fun losEfectosTienenLimites() {
        val u = Ejemplo.universo().copy(entorno = listOf(Persona("p1", "Loli Cortés", "madre", 50)))
        val r = Vida.aplica(u, Efectos(-10_000_000, mapOf(Fuera.FAMA to 40, Fuera.DISCIPLINA to -3, "inventado" to 9), mapOf("loli cortés" to -40), listOf(Persona("", "Irene", "ligue"))))
        assertEquals("como mucho, el 20 % de lo que tienes (y al menos 50.000 €)", 250_000 - 50_000L, r.dinero)
        // (la fama sube como mucho lo del tope de la jornada; a los 19 años, 7: 20 + 7)
        assertEquals(27, r.valorFuera(Fuera.FAMA)); assertEquals(57, r.valorFuera(Fuera.DISCIPLINA)); assertFalse(r.fuera.containsKey("inventado"))
        assertEquals(35, r.entorno.first().afinidad)
        assertEquals("Irene", r.entorno.last().nombre); assertTrue(r.entorno.last().id.isNotBlank())
    }

    @Test fun comprarYVender() {
        var u = Ejemplo.universo().copy(precios = Mercado.inicial())
        u = Mercado.compraAcciones(u, "GRD", 100)
        assertEquals(250_000 - 1_840L, u.dinero); assertEquals(100L, u.cartera["GRD"])
        u = Mercado.vendeAcciones(u, "GRD", 100)
        assertEquals(250_000L, u.dinero); assertFalse(u.cartera.containsKey("GRD"))
        val chalet = Mercado.TIENDA.first { it.nombre == "Chalet en la sierra" }
        try { Mercado.compra(u, chalet); fail() } catch (e: Mercado.NoLlega) { assertTrue(e.message!!.contains("2.150.000 €")) }
        u = Mercado.compra(u, Mercado.TIENDA.first { it.nombre == "Berlina alemana" })
        assertEquals(155_000L, u.dinero); assertEquals(1, u.bienes.size)
        u = Mercado.vende(u, u.bienes.first().id)
        assertEquals(250_000L, u.dinero)
    }

    @Test fun aceptarUnaOfertaCambiaElClub() {
        val u = Vida.acepta(Ejemplo.universo(), Oferta("o", "Villarreal CF", "traspaso", 42_000, 500_000, 4, ""))
        assertEquals("Villarreal CF", u.club); assertEquals(42_000L, u.salario); assertEquals(750_000L, u.dinero); assertEquals("2029/30", u.contrato!!.hasta); assertEquals("Villarreal CF", u.contrato!!.club)
        val p = Vida.acepta(Ejemplo.universo(), Oferta("o", "Relojes Cénit", "patrocinio", 2_000, 100_000, 2, ""))
        assertEquals("Real Betis", p.club); assertEquals(20_000L, p.salario); assertEquals(350_000L, p.dinero)
    }

    // ---------------------------------------------------------------- lo que se manda a la API

    @Test fun elJsonSeLeeAunqueVengaEnvueltoYSiNoSePideOtraVez() {
        assertEquals("b", Respuesta("```json\n{\"a\":\"b\"}\n```", Uso(), "end_turn", "m").json()["a"])
        assertEquals("b", Respuesta("Aquí lo tienes: {\"a\":\"b\"} ¡Suerte!", Uso(), "end_turn", "m").json()["a"])
        // La primera respuesta no trae JSON: se pide otra vez y vale la segunda
        api.siguiente = Transporte.Respuesta(200, """{"id":"x","type":"message","role":"assistant","model":"claude-haiku-5-5","content":[{"type":"text","text":"perdón, no sé"}],"stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":5}}""")
        val r = Motor.pide(Narrador.red(Ejemplo.universo(), Ejemplo.partido(), "ctx"))
        assertTrue(r["posts"].comoLista().isNotEmpty())
        assertEquals(2, api.mensajes().size)
    }

    @Test fun laPeticionEsComoPideLaApi() {
        val u = Ejemplo.universo()
        val h = Narrador.red(u, Ejemplo.partido(), "ctx").cuerpo()
        assertEquals("claude-haiku-5-5", h["model"])
        assertEquals(mapOf("type" to "disabled"), h["thinking"])
        // Ni temperature ni top_p ni top_k: en la versión 2026-10-21 son un 400
        assertFalse(h.keys.any { it in setOf("temperature", "top_p", "top_k") })
        val oc = h["output_config"].comoMapa()
        // Sin salida estructurada (la API la rechaza por grande: «compiled grammar is too large»): el esquema va en el mensaje
        assertEquals("low", oc["effort"]); assertNull(oc["format"])
        val sch = Peticion.esquemaDe(h)!!
        assertEquals(false, sch["additionalProperties"]); assertEquals(listOf("tendencia", "posts", "mensajes", "gente_nueva"), sch["required"])
        val sistema = h["system"].comoLista().first().comoMapa()
        assertEquals(mapOf("type" to "ephemeral"), sistema["cache_control"])
        assertTrue(sistema["text"].comoTexto().contains("Personas reales"))
        assertEquals("user", h["messages"].comoLista().single().comoMapa()["role"])
        // Sonnet: sin «thinking» (disabled es un 400 en Sonnet 5.5) y con esfuerzo bajo
        val s = Narrador.prensa(u, Ejemplo.partido(), "ctx").cuerpo()
        assertEquals("claude-sonnet-5-5", s["model"]); assertFalse(s.containsKey("thinking")); assertEquals("low", s["output_config"].comoMapa()["effort"])
        // Los de lote, con la caché de una hora
        assertEquals("1h", Narrador.evento(u, "ctx", 1, enLote = true).cuerpo()["system"].comoLista().first().comoMapa()["cache_control"].comoMapa()["ttl"])
        // La foto, en base64 dentro del mensaje (Haiku lee imágenes)
        val f = Narrador.foto(u, "QUJD", "image/jpeg").cuerpo()["messages"].comoLista().single().comoMapa()["content"].comoLista().first().comoMapa()
        assertEquals("image", f["type"]); assertEquals(mapOf("type" to "base64", "media_type" to "image/jpeg", "data" to "QUJD"), f["source"])
    }

    @Test fun lasReglasDelNarrador() {
        val r = Narrador.REGLAS
        assertTrue(r.contains("No inventes lesiones, suplencias"))
        assertTrue(r.contains("Todo lo íntimo, lo escandaloso y lo de pareja es SIEMPRE con personajes inventados"))
        assertTrue(r.contains("Nada de contenido sexual explícito"))
        // El contexto lleva lo de la carrera, no lo del sistema (para que el sistema vaya siempre en caché)
        val ctx = Narrador.contexto(Ejemplo.universo(), emptyList(), listOf(Ejemplo.partido()))
        assertTrue(ctx.contains("Álex Cortés")); assertTrue(ctx.contains("Real Betis")); assertTrue(ctx.contains("18.000 €"))
    }

    @Test fun laClaveVaEnSuCabeceraYNuncaEnLaDireccion() {
        Motor.pide(Narrador.red(Ejemplo.universo(), Ejemplo.partido(), "ctx"))
        val l = api.mensajes().single()
        assertEquals("https://api.anthropic.com/v1/messages", l.url)
        assertEquals("sk-ant-api03-de-mentira-0000", l.cabeceras["x-api-key"])
        assertEquals("2026-10-21", l.cabeceras["anthropic-version"])
        assertFalse(api.llamadas.any { it.url.contains("sk-ant") })
    }

    @Test fun losErroresDeLaApiSeEntienden() {
        val c = Claude({ "k" }, api)
        val p = Narrador.red(Ejemplo.universo(), Ejemplo.partido(), "ctx")
        fun falla(codigo: Int, tipo: String): Claude.Fallo {
            api.siguiente = Transporte.Respuesta(codigo, """{"type":"error","error":{"type":"$tipo","message":"algo"}}""")
            return try { c.mensaje(p); throw AssertionError("tenía que fallar") } catch (e: Claude.Fallo) { e }
        }
        assertTrue(falla(401, "authentication_error").message!!.contains("clave"))
        assertTrue(falla(429, "rate_limit_error").reintentable)
        assertTrue(falla(529, "overloaded_error").reintentable)
        assertTrue(falla(402, "billing_error").message!!.contains("facturación"))
        assertFalse(falla(400, "invalid_request_error").reintentable)
        try { Claude({ "" }, api).mensaje(p); fail() } catch (e: Claude.Fallo) { assertTrue(e.message!!.contains("Falta tu clave")) }
        api.siguiente = Transporte.Respuesta(200, """{"model":"claude-sonnet-5-5","content":[],"stop_reason":"refusal","usage":{"input_tokens":10,"output_tokens":0}}""")
        try { c.mensaje(p); fail() } catch (e: Claude.Rechazo) { assertEquals(10L, e.uso.entrada) }
    }

    // ---------------------------------------------------------------- lo que cuesta

    @Test fun losPrecios() {
        // Haiku 5.5: 0,10 / 0,50 $ por millón (hasta 100.000 tokens de prompt); la caché leída, la décima parte
        assertEquals(0.10 * 0.1 + 0.50 * 0.1, Precios.coste("claude-haiku-5-5", Uso(100_000, 100_000)), 1e-9)
        assertEquals(0.0002 + 0.0005, Precios.coste("claude-haiku-5-5", Uso(2_000, 1_000)), 1e-9)
        assertEquals(50_000 * 0.10 * 0.1 / 1e6, Precios.coste("claude-haiku-5-5", Uso(cacheLeida = 50_000)), 1e-12)
        // Más de 100.000 tokens de prompt: 0,50 / 2,50
        assertEquals(0.50 * 0.2 + 2.50 * 0.001, Precios.coste("claude-haiku-5-5", Uso(200_000, 1_000)), 1e-9)
        // Sonnet 5.5: 2 / 10; escribir caché, 1,25 veces (5 min) o 2 (1 hora); los lotes, la mitad
        assertEquals(2.0 + 10.0, Precios.coste("claude-sonnet-5-5", Uso(1_000_000, 1_000_000)), 1e-9)
        assertEquals(2.5, Precios.coste("claude-sonnet-5-5", Uso(cacheEscrita = 1_000_000)), 1e-9)
        assertEquals(4.0 / 2, Precios.coste("claude-sonnet-5-5", Uso(cacheEscrita = 1_000_000), lote = true, cacheHora = true), 1e-9)
    }

    @Test fun elGastoDelMesYElTope() {
        Gasto.dispositivo = "movil-a"; Gasto.apunta("claude-sonnet-5-5", 1.5)
        Gasto.dispositivo = "movil-b"; Gasto.apunta("claude-haiku-5-5", 0.25)
        assertEquals(1.75, Gasto.mes(), 1e-9); assertEquals(0.25, Gasto.mes(modelo = "h"), 1e-9)
        assertEquals(0.0, Gasto.mes(java.time.YearMonth.of(2026, 10)), 1e-9)
        assertEquals(100.0, Gasto.tope(), 0.0)
        Gasto.ponTope(2.0)
        Gasto.compruebaAntes(0.2)
        try { Gasto.compruebaAntes(0.3); fail("se pasaría") } catch (e: Gasto.Tope) { assertTrue(e.message!!.contains("tope")) }
        // Al llegar al tope, no se pide nada y el partido se guarda igual (con lo que falta, para pedirlo luego)
        Gasto.apunta("claude-sonnet-5-5", 0.5)
        val (u, fallo) = Motor.crea(Ejemplo.universo())
        assertNotNull(fallo); assertEquals(0, api.mensajes().size)
        val (e, f2) = Motor.partido(u.id, Ejemplo.partido())
        assertTrue(f2!!.contains("tope")); assertEquals("red,prensa", e.pendiente); assertEquals(1, Carreras.partidos(u.id).size)
        Gasto.ponTope(100.0)
        assertNull(Motor.reintenta(e.id))
        assertEquals("", Carreras.entrada(e.id)!!.pendiente)
        assertTrue(Gasto.mes() > 2.0)
    }

    // ---------------------------------------------------------------- una carrera entera

    @Test fun unaCarreraDePrincipioAFin() {
        var ahora = 1_800_000_000_000L
        Motor.reloj = { ahora }
        val (u0, f0) = Motor.crea(Ejemplo.universo())
        assertNull(f0)
        // (sin el representante que propone el principio: a su agente lo elige él entre tres)
        assertEquals(listOf("Loli Cortés", "Vera Salinas"), Carreras.de(u0.id)!!.entorno.map { it.nombre })
        assertEquals(TipoEntrada.INICIO, Carreras.entradasDe(u0.id).single().tipo)

        // Un partido: la red (Haiku), la prensa (Sonnet) y, por lotes, los dos eventos
        val (e, f) = Motor.partido(u0.id, Ejemplo.partido())
        assertNull(f)
        assertEquals(9, e.posts.size); assertEquals(2, e.dms.size)
        assertEquals("El Niño se sale", e.prensa!!.titular); assertEquals(1, e.ofertas.size); assertEquals(3, e.decision!!.opciones.size)
        var u = Carreras.de(u0.id)!!
        assertEquals(1, u.paso); assertEquals(250_000 + 18_000L, u.dinero)
        // (a los 19 años el partido da +4 de fama y la prensa pide +4, pero el tope de la jornada es 7: 20 + 7)
        assertEquals(27, u.valorFuera(Fuera.FAMA))
        assertEquals(listOf("claude-sonnet-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-sonnet-5-5"), api.mensajes().map { it.cuerpo["model"] })  // el principio, los candidatos a agente, la red y la prensa
        val lote = api.llamadas.single { it.url.endsWith("/batches") }
        assertEquals(listOf("${u.id}_1_1", "${u.id}_1_2"), lote.cuerpo["requests"].comoLista().map { it.comoMapa()["custom_id"] })
        assertEquals(1, u.lotes.size)

        // Mientras el lote no acaba, nada; al acabar, dos eventos: el primero sale ya, el segundo seis horas después
        assertEquals(0, Motor.miraLotes())
        api.estadoLote = "ended"
        assertEquals(2, Motor.miraLotes())
        assertTrue(Carreras.de(u.id)!!.lotes.isEmpty())
        assertEquals(1, Motor.revela().size)
        val visibles = Carreras.entradasDe(u.id, ahora = ahora).filter { it.tipo == TipoEntrada.EVENTO }
        assertEquals(1, visibles.size); assertEquals("Una invitación a cenar", visibles.single().titulo)
        ahora += 6 * 3600_000L
        assertEquals("Una marca de relojes quiere tu muñeca", Motor.revela().single().titulo)
        assertTrue(Carreras.entradasDe(u.id, todas = true).all { it.aplicada })

        // Una decisión: lo que pasa después, con sus efectos (y su dinero)
        u = Carreras.de(u.id)!!
        assertNull(Motor.decide(e.id, "Voy, y que se entere todo el mundo"))
        val d = Carreras.entrada(e.id)!!.decision!!
        assertFalse(d.abierta); assertEquals("Voy, y que se entere todo el mundo", d.elegida)
        val despues = Carreras.entradasDe(u.id).first { it.tipo == TipoEntrada.RESPUESTA }
        assertEquals("Noche de reservado", despues.titulo); assertTrue(despues.dms.first().mio)
        val u2 = Carreras.de(u.id)!!
        assertEquals(u.dinero - 3000, u2.dinero); assertTrue(u2.entorno.any { it.nombre == "Irene Mora" })
        assertEquals(u.valorFuera(Fuera.DISCIPLINA) - 5, u2.valorFuera(Fuera.DISCIPLINA))
        // (y no se contesta dos veces)
        assertNull(Motor.decide(e.id, "otra"))

        // La oferta: aceptarla cambia el club y la red reacciona
        val o = Carreras.entrada(e.id)!!.ofertas.single()
        assertNull(Motor.oferta(e.id, o.id, true))
        assertEquals("Villarreal CF", Carreras.de(u.id)!!.club)
        assertEquals(Oferta.ACEPTADA, Carreras.entrada(e.id)!!.ofertas.single().estado)
        val oficial = Carreras.entradasDe(u.id).first { it.tipo == TipoEntrada.OFICIAL }
        assertEquals("Oficial: ficha por Villarreal CF", oficial.titulo); assertTrue(oficial.posts.isNotEmpty())
        assertTrue(oficial.texto.contains("FC"))

        // Pedir un evento ahora (sin lote)
        val (ev, fe) = Motor.eventoAhora(u.id)
        assertNull(fe); assertTrue(ev!!.aplicada)

        // Otro partido: lo que esperaba sale ya, y se manda otro lote
        val (e2, _) = Motor.partido(u.id, Ejemplo.partido(0, 1, "Real Madrid"))
        assertEquals(2, e2.paso)
        assertEquals(2, api.llamadas.count { it.url.endsWith("/batches") })
        assertTrue(Gasto.mes() > 0)
    }

    @Test fun laFotoRellenaLaPlantilla() {
        val (u, _) = Motor.crea(Ejemplo.universo())
        val f = Motor.foto(u.id, "QUJD", "image/jpeg")
        assertTrue(f.seVe); assertEquals(3, f.golesFavor); assertEquals("Sevilla FC", f.rival); assertEquals(8.7, f.nota, 0.0)
        assertEquals("58 / 42", f.equipo["Posesión"])
        assertEquals("claude-haiku-5-5", api.mensajes().last().cuerpo["model"])
    }

    @Test fun sinEventosSolosNoHayLotes() {
        Gasto.ponEventosSolos(false)
        val (u, _) = Motor.crea(Ejemplo.universo())
        Motor.partido(u.id, Ejemplo.partido())
        assertTrue(api.llamadas.none { it.url.endsWith("/batches") })
        Gasto.ponEventosSolos(true)
    }

    @Test fun borrarUnaCarreraLoBorraTodo() {
        val u = Ejemplo.completa(api)
        assertTrue(Carreras.entradasDe(u.id, todas = true).isNotEmpty())
        Carreras.borra(u.id)
        assertNull(Carreras.de(u.id)); assertTrue(Carreras.entradasDe(u.id, todas = true).isEmpty())
    }
}
