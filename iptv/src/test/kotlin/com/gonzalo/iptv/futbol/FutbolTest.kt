package com.gonzalo.iptv.futbol

import com.gonzalo.iptv.JsonFlujo
import com.gonzalo.iptv.RedDeMentira
import com.gonzalo.iptv.recurso
import com.gonzalo.iptv.recursoTexto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.zip.GZIPOutputStream

/** Las respuestas de ESPN (de verdad, recortadas, en `resources/espn`), leídas y traducidas; las horas de España; el orden de los partidos. */
class FutbolTest {
    private fun json(n: String) = JsonFlujo.lee(recursoTexto("espn/$n"))
    private val ahora = Hora.lee("2026-11-07T18:28:00Z")

    @Test fun losProximosDeUnEquipo() {
        val l = Espn.partidos(json("calendario_proximos.json"), "esp.1")
        assertEquals(5, l.size)
        val p = l.first()
        assertEquals("401882816", p.id); assertEquals("esp.1", p.liga)
        assertEquals("Atlético Madrid", p.local.nombre); assertEquals("Barcelona", p.visitante.nombre); assertEquals("83", p.visitante.id)
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500/83.png", p.visitante.escudo)
        assertEquals(Estado.POR_JUGAR, p.estado); assertNull("sin empezar, sin goles", p.golesLocal); assertEquals("–", p.marcador)
        assertEquals("Riyadh Air Metropolitano", p.estadio)
        // 20:00 UTC de noviembre son las 21:00 en España
        assertEquals("21:00", Hora.reloj(p.inicio)); assertEquals("Mañana · 21:00", p.cuando(ahora))
    }

    @Test fun losJugadosConSuMarcador() {
        val l = Espn.partidos(json("calendario_jugados.json"), "esp.1")
        val p = l.first { it.id == "401882822" }
        assertEquals(Estado.FINAL, p.estado); assertEquals(6, p.golesLocal); assertEquals(1, p.golesVisitante); assertEquals("6-1", p.marcador)
        assertEquals("Final", p.cuando(ahora))
        assertEquals("los jugados del Barça, el más reciente primero", listOf("401882822", "401882832", "401882846", "401882853"), Orden.jugados(l, "83").map { it.id })
    }

    @Test fun losMarcadoresDeUnDiaConUnoEnElDescanso() {
        val l = Espn.partidos(json("marcadores.json"), "esp.1")
        assertEquals(4, l.size)
        val fin = l.first { it.id == "401882812" }
        assertEquals("Málaga", fin.local.nombre); assertEquals("1-2", fin.marcador); assertEquals(Estado.FINAL, fin.estado)
        val ht = l.first { it.id == "401882811" }
        assertEquals(Estado.DESCANSO, ht.estado); assertTrue(ht.estado.enDirecto); assertEquals("0-0", ht.marcador); assertEquals("Descanso", ht.cuando(ahora))
        val luego = l.first { it.id == "401882808" }
        assertEquals(Estado.POR_JUGAR, luego.estado); assertEquals("Hoy · 21:00", luego.cuando(ahora))
        // En directo primero; luego lo que viene; luego lo jugado (lo más reciente antes)
        assertEquals(listOf("401882811", "401882808", "401882813", "401882812"), Orden.ordena(l, ahora).map { it.id })
    }

    @Test fun laClasificacion() {
        val c = Espn.clasificacion(json("clasificacion.json"), "esp.1")
        assertEquals(1, c.tablas.size); assertEquals("una sola tabla, sin título", "", c.tablas[0].first)
        val f = c.tablas[0].second
        assertEquals(6, f.size)
        val bar = f[0]
        assertEquals(1, bar.puesto); assertEquals("Barcelona", bar.equipo.nombre); assertEquals(30, bar.puntos); assertEquals(11, bar.jugados)
        assertEquals(10, bar.ganados); assertEquals(0, bar.empatados); assertEquals(1, bar.perdidos); assertEquals(34, bar.diferencia); assertEquals(46, bar.aFavor)
        assertEquals("Liga de Campeones", bar.nota)
        assertEquals(listOf(30, 28, 27, 23, 19, 18), f.map { it.puntos })
    }

    @Test fun goleadoresYAsistentes() {
        val l = Espn.lideres(json("goleadores.json"), "esp.1")
        assertEquals("Raphinha", l.goles[0].nombre); assertEquals(15, l.goles[0].cifra); assertEquals(10, l.goles[0].partidos); assertEquals("Barcelona", l.goles[0].equipo.nombre)
        assertEquals("Kylian Mbappé", l.goles[1].nombre)
        assertEquals("Lamine Yamal", l.asistencias[0].nombre); assertEquals(7, l.asistencias[0].cifra)
        // (pedido en español, «Partidos: 10, Goles: 15» o «Juegos: 11, Asistencias: 7»)
        val es = Espn.lideres(JsonFlujo.lee("""{"stats":[{"name":"assistsLeaders","leaders":[{"displayValue":"Juegos: 11, Asistencias: 7","value":7.0,"athlete":{"displayName":"Lamine Yamal","team":{"id":"83","displayName":"Barcelona"}}}]}]}"""), "esp.1")
        assertEquals(11, es.asistencias[0].partidos)
    }

    @Test fun losEquiposParaElegir() {
        val l = Espn.equipos(json("equipos.json"))
        assertEquals(20, l.size)
        val b = l.first { it.id == "83" }
        assertEquals("Barcelona", b.nombre); assertEquals("BAR", b.siglas); assertTrue(b.escudo.endsWith("/83.png"))
        assertEquals("por orden alfabético (sin mirar acentos)", "Alavés", l.first().nombre)
    }

    @Test fun laFichaDeUnPartidoTerminado() {
        val f = Espn.ficha(json("ficha_final.json"), "esp.1")
        assertEquals("6-1", f.partido.marcador); assertEquals(Estado.FINAL, f.partido.estado); assertEquals("Barcelona", f.partido.local.nombre)
        val goles = f.eventos.filter { it.tipo.esGol }
        assertEquals(7, goles.size)
        assertEquals("8' Gol de Raphinha (asistencia de Fermín López)", goles[0].frase())
        assertEquals("84' Gol de Mariano Díaz", goles.last().frase())
        val am = f.eventos.first { it.tipo == TipoEvento.AMARILLA }
        assertEquals("27' Tarjeta amarilla para Carlos Protesoni", am.frase())
        val cambio = f.eventos.first { it.tipo == TipoEvento.CAMBIO }
        assertEquals("45' Cambio: entra Marc Bernal, sale Pedri", cambio.frase())
        assertTrue("los retrasos y demás, fuera", f.eventos.none { it.tipo == TipoEvento.OTRO })
        assertEquals("un solo descanso", 1, f.eventos.count { it.tipo == TipoEvento.DESCANSO })
        assertTrue("ni una palabra en inglés", f.eventos.none { e -> listOf("Goal", "Yellow", "Substitution", "replaces").any { it in e.frase() } })
        // Alineaciones: el local primero, once titulares
        assertEquals(2, f.alineaciones.size)
        val a = f.alineaciones[0]
        assertEquals("83", a.equipo.id); assertEquals(11, a.titulares.size); assertTrue(a.suplentes.isNotEmpty()); assertEquals("Joan García", a.titulares[0].nombre); assertEquals("1", a.titulares[0].dorsal)
        assertTrue(a.formacion.startsWith("4-"))
        assertTrue("el que entró, marcado", a.suplentes.any { it.nombre == "Gabriel Jesus" && it.entra })
        // Las cifras, en español y con su %
        val pos = f.cifras.first { it.nombre == "Posesión" }
        assertEquals("77 %", pos.local); assertEquals("23 %", pos.visitante)
        assertEquals("80 %", f.cifras.first { it.nombre == "Pases buenos" }.visitante)
        assertEquals("13", f.cifras.first { it.nombre == "Tiros" }.local)
    }

    @Test fun laFichaEnElDescanso() {
        val f = Espn.ficha(json("ficha_descanso.json"), "esp.1")
        assertEquals(Estado.DESCANSO, f.partido.estado); assertEquals("0-0", f.partido.marcador)
        assertEquals(TipoEvento.INICIO, f.eventos.first().tipo); assertEquals("Empieza el partido", f.eventos.first().frase())
        assertEquals(TipoEvento.DESCANSO, f.eventos.last().tipo)
    }

    @Test fun lasHorasSonLasDeEspana() {
        // Invierno (UTC+1) y verano (UTC+2), y el cambio de día a medianoche de España
        assertEquals("21:00", Hora.reloj(Hora.lee("2026-11-08T20:00Z")))
        assertEquals("21:00", Hora.reloj(Hora.lee("2026-07-01T19:00Z")))
        assertEquals("00:30", Hora.reloj(Hora.lee("2026-07-01T22:30Z")))
        assertEquals("Jue 2 jul", Hora.dia(Hora.lee("2026-07-01T22:30Z")))
        assertEquals(LocalDate.of(2026, 7, 2), Hora.diaDe(Hora.lee("2026-07-01T22:30Z")))
        assertEquals("con segundos también", Hora.lee("2026-11-08T20:00Z"), Hora.lee("2026-11-08T20:00:00Z"))
        assertEquals(0L, Hora.lee("mañana"))
        val hoy = Hora.lee("2026-11-07T10:00:00Z")
        assertEquals("Hoy · 21:00", Hora.diaYHora(Hora.lee("2026-11-07T20:00Z"), hoy))
        assertEquals("Ayer · 21:00", Hora.diaYHora(Hora.lee("2026-11-06T20:00Z"), hoy))
        assertEquals("Mié 11 nov · 20:45", Hora.diaYHora(Hora.lee("2026-11-11T19:45Z"), hoy))
    }

    @Test fun losEventosYLosEstadosEnEspanol() {
        assertEquals(TipoEvento.GOL, Textos.tipo("70", "Goal")); assertEquals(TipoEvento.GOL_PENALTI, Textos.tipo("98", "Penalty - Scored"))
        assertEquals(TipoEvento.GOL_PROPIA, Textos.tipo("97", "Own Goal")); assertEquals(TipoEvento.GOL_PROPIA, Textos.tipo("", "Own Goal"))
        assertEquals(TipoEvento.GOL_CABEZA, Textos.tipo("137", "Goal - Header")); assertEquals(TipoEvento.ROJA, Textos.tipo("93", "Red Card"))
        assertEquals(TipoEvento.AMARILLA, Textos.tipo("", "Tarjeta amarilla")); assertEquals(TipoEvento.SEGUNDA_AMARILLA, Textos.tipo("", "Second Yellow Card"))
        assertEquals(TipoEvento.PENALTI_FALLADO, Textos.tipo("", "Penalty - Missed")); assertEquals(TipoEvento.PENALTI_PARADO, Textos.tipo("", "Penalty - Saved"))
        assertEquals(TipoEvento.VAR, Textos.tipo("", "VAR - Goal Disallowed")); assertEquals(TipoEvento.CAMBIO, Textos.tipo("", "Substitution"))
        assertEquals(TipoEvento.OTRO, Textos.tipo("129", "Start Delay")); assertEquals(TipoEvento.OTRO, Textos.tipo("", "Varnish"))
        assertEquals("14' Gol de Raphinha (penalti)", Evento("14'", TipoEvento.GOL_PENALTI, "83", "Raphinha").frase())
        assertEquals("33' Gol en propia puerta de Koundé", Evento("33'", TipoEvento.GOL_PROPIA, "83", "Koundé").frase())
        assertEquals("90'+2' Tarjeta roja para Gavi", Evento("90'+2'", TipoEvento.ROJA, "83", "Gavi").frase())
        assertEquals("70' Revisión del VAR", Evento("70'", TipoEvento.VAR).frase())
        assertEquals(Estado.POR_JUGAR, Textos.estado("STATUS_SCHEDULED", "pre")); assertEquals(Estado.DESCANSO, Textos.estado("STATUS_HALFTIME", "in"))
        assertEquals(Estado.EN_JUEGO, Textos.estado("STATUS_SECOND_HALF")); assertEquals(Estado.FINAL, Textos.estado("STATUS_FULL_TIME"))
        assertEquals(Estado.FINAL_PENALTIS, Textos.estado("STATUS_FINAL_PEN")); assertEquals(Estado.APLAZADO, Textos.estado("STATUS_POSTPONED"))
        assertEquals("lo desconocido, por su fase", Estado.EN_JUEGO, Textos.estado("STATUS_ALGO_NUEVO", "in"))
        assertEquals("Por jugar", Estado.POR_JUGAR.texto); assertEquals("Descanso", Estado.DESCANSO.texto); assertEquals("Final", Estado.FINAL.texto)
    }

    @Test fun tusPartidosElQueSeJuegaYElSiguiente() {
        val bar = Equipo("83", "Barcelona"); val rma = Equipo("86", "Real Madrid"); val otro = Equipo("1", "Otro")
        val h = 3_600_000L
        val l = listOf(
            Partido("a", "esp.1", ahora + 48 * h, bar, otro),
            Partido("b", "esp.1", ahora - 30 * 60_000L, otro, rma, Estado.EN_JUEGO, 1, 0, "30'"),
            Partido("c", "uefa.champions", ahora + 24 * h, otro, bar),
            Partido("d", "esp.1", ahora - 72 * h, bar, rma, Estado.FINAL, 2, 1),
            Partido("e", "esp.1", ahora + 96 * h, rma, otro),
        )
        assertEquals("el Barça: el de mañana (Champions) antes que el de pasado", listOf("c", "a"), Orden.proximos(l, "83", ahora).map { it.id })
        assertEquals("de cada uno, uno; el que se juega, primero", listOf("b", "c"), Orden.tusPartidos(l, listOf("83", "86"), ahora).map { it.id })
        // Si juegan entre ellos, sale una vez
        val clasico = Partido("x", "esp.1", ahora + h, bar, rma)
        assertEquals(listOf("x"), Orden.tusPartidos(listOf(clasico), listOf("83", "86"), ahora).map { it.id })
        assertTrue(Orden.quizaEnDirecto(Partido("y", "esp.1", ahora - 20 * 60_000L, bar, rma), ahora))
        assertFalse(Orden.quizaEnDirecto(Partido("z", "esp.1", ahora + 5 * h, bar, rma), ahora))
    }

    @Test fun losGruposDeCadaCompeticionYElSiguienteCanal() {
        val grupos = CanalesFutbol.GRUPOS.map { if (it.id == "movistar") it.copy(canales = listOf(Entrada(hd = RefCanal("l1", "x1", "M+ LaLiga")), Entrada(hd = RefCanal("l1", "x2", "M+ LaLiga 2")), Entrada(hd = RefCanal("l1", "x3", "M+ LaLiga 3")))) else it }
        val a = CanalesFutbol.ASIGNACION
        assertEquals("LaLiga: Movistar y DAZN", listOf("movistar", "dazn"), CanalesFutbol.gruposDe("esp.1", grupos, a).map { it.id })
        assertEquals(listOf("Movistar", "DAZN"), CanalesFutbol.gruposDe("esp.1", grupos, a).map { it.boton })
        assertEquals(listOf("champions"), CanalesFutbol.gruposDe("uefa.champions", grupos, a).map { it.id })
        assertEquals(listOf("copa"), CanalesFutbol.gruposDe("esp.copa_del_rey", grupos, a).map { it.id })
        assertEquals("Champions", CanalesFutbol.gruposDe("uefa.champions", grupos, a)[0].boton)
        // Lo que tú cambias manda; un grupo que ya no existe no sale
        val tuya = CanalesFutbol.asignacionDeTexto("""{"uefa.champions":["champions","movistar","borrado"]}""")
        assertEquals(listOf("champions", "movistar"), CanalesFutbol.gruposDe("uefa.champions", grupos, tuya).map { it.id })
        assertEquals("lo que no dices, como de serie", listOf("movistar", "dazn"), tuya["esp.1"])
        // El siguiente canal del grupo (y vuelta al primero)
        val m = grupos[0]
        assertEquals(1, CanalesFutbol.siguiente(m, 0)); assertEquals(2, CanalesFutbol.siguiente(m, 1)); assertEquals(0, CanalesFutbol.siguiente(m, 2))
        assertEquals("x1", CanalesFutbol.canal(m, 3)?.hd?.canal)
        assertNull("un grupo vacío no tiene canal", CanalesFutbol.canal(grupos[1], 0)); assertEquals(0, CanalesFutbol.siguiente(grupos[1], 0))
        assertEquals(listOf("x2", "x1", "x3"), CanalesFutbol.mueve(m, 1, -1).canales.map { it.hd?.canal })
        assertEquals(listOf("x1", "x3"), CanalesFutbol.quita(m, 1).canales.map { it.hd?.canal })
    }

    @Test fun losGruposSeGuardanSinDirecciones() {
        val g = CanalesFutbol.anade(CanalesFutbol.anade(GrupoCanales("movistar", "Movistar LaLiga"), RefCanal("l1", "x101", "M+ LaLiga")),
            RefCanal("l1", "http://tv.ejemplo.test:8080/live/pepe/s3cr3t0/1.ts", "Uno raro"))
        assertEquals("una dirección no se apunta nunca", listOf("x101"), g.canales.flatMap { it.todas }.map { it.canal })
        val t = CanalesFutbol.gruposATexto(listOf(g, GrupoCanales("gotro", "Otro")))
        assertFalse(t.contains("http")); assertFalse(t.contains("s3cr3t0"))
        assertEquals("(sin «Selección»: se guardó sin ella, es que la borraste)", listOf(g, GrupoCanales("gotro", "Otro")), CanalesFutbol.gruposDeTexto(t))
        assertEquals("sin nada, los de serie", CanalesFutbol.GRUPOS, CanalesFutbol.gruposDeTexto(null))
        // Las tres calidades de una entrada viajan; y lo guardado antes (un canal por entrada) se lee como HD
        val tres = GrupoCanales("dazn", "DAZN LaLiga", listOf(Entrada(RefCanal("l1", "x1", "ES: DAZN LA LIGA UHD"), RefCanal("l1", "x2", "ES: DAZN LA LIGA HD"), null)))
        assertEquals(listOf(tres), CanalesFutbol.gruposDeTexto(CanalesFutbol.gruposATexto(listOf(tres))))
        assertEquals(Entrada(hd = RefCanal("l1", "x9", "Viejo")), CanalesFutbol.gruposDeTexto("""[{"id":"a","n":"A","c":[{"l":"l1","k":"x9","n":"Viejo"}]}]""")[0].canales[0])
        assertEquals(listOf("Movistar LaLiga", "DAZN LaLiga", "Champions", "Copa", "Europa League", "Selección"), CanalesFutbol.GRUPOS.map { it.nombre })
        assertTrue(CanalesFutbol.GRUPOS.all { it.canales.isEmpty() })
        assertEquals("gmovistarliga2", CanalesFutbol.idNuevo("Movistar Liga", listOf("gmovistarliga")))
        val eq = listOf(CanalesFutbol.SUGERIDO to "esp.1")
        assertEquals(eq, CanalesFutbol.equiposDeTexto(CanalesFutbol.equiposATexto(eq)))
    }

    @Test fun laCacheNoPideDeMasYSirveSinRed() {
        val dir = Files.createTempDirectory("futbol").toFile()
        var t = 1_000_000L
        var pedidas = 0; var hayRed = true
        val c = CacheFutbol(dir, { u -> pedidas++; if (!hayRed) throw java.io.IOException("sin red"); "{\"u\":\"$u\",\"n\":$pedidas}" }, { t })
        assertEquals("{\"u\":\"a\",\"n\":1}", c.texto("a", 60_000))
        t += 30_000; c.texto("a", 60_000)
        assertEquals("aún vale: no se pide", 1, pedidas)
        t += 60_000; assertEquals("{\"u\":\"a\",\"n\":2}", c.texto("a", 60_000))
        hayRed = false; t += 120_000
        assertEquals("sin red, lo de antes aunque sea viejo", "{\"u\":\"a\",\"n\":2}", c.texto("a", 60_000))
        assertTrue(runCatching { c.texto("b", 60_000) }.isFailure)
        dir.deleteRecursively()
    }

    @Test fun pideAEspnYDescomprime() {
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(recurso("espn/calendario_proximos.json")) } }.toByteArray()
        val red = RedDeMentira { u -> if (u == Espn.urlCalendario("esp.1", "83", true)) gz else if (u.contains("/teams/83/schedule")) recurso("espn/calendario_jugados.json") else null }
        val e = Espn(red)
        assertEquals(5, e.calendario("esp.1", "83", true).size)
        assertEquals(4, e.calendario("esp.1", "83", false).size)
        assertEquals("https://site.api.espn.com/apis/site/v2/sports/soccer/esp.1/teams/83/schedule?fixture=true&lang=es&region=es", red.pedidas[0].first)
        assertTrue("un 404 es un fallo", runCatching { e.clasificacion("esp.1") }.isFailure)
        assertEquals("https://site.api.espn.com/apis/site/v2/sports/soccer/esp.1/scoreboard?dates=20261107&lang=es&region=es", Espn.urlMarcadores("esp.1", LocalDate.of(2026, 11, 7)))
        assertNotNull(Competiciones.de("esp.1").nombre)
        assertEquals("LaLiga", Competiciones.de("esp.1").corto)
    }

    // ---------------------------------------------------------------- las calidades

    @Test fun laCalidadSegunLaConexion() {
        val casos = listOf(
            // (conexión, forzada) → calidad
            Triple(Conexion(sinLimite = true, kbpsEnlace = 100_000), null, Calidad.UHD),
            Triple(Conexion(sinLimite = true), null, Calidad.UHD),                                     // wifi sin saber la velocidad
            Triple(Conexion(sinLimite = true, kbpsEnlace = 12_000), null, Calidad.HD),                 // wifi lenta para 4K
            Triple(Conexion(sinLimite = true, kbpsEnlace = 2_000), null, Calidad.SD),                  // muy lenta
            Triple(Conexion(sinLimite = false, kbpsEnlace = 150_000), null, Calidad.HD),               // datos del móvil, aunque vayan rápidos
            Triple(Conexion(sinLimite = false, kbpsEnlace = 1_500), null, Calidad.SD),                 // datos del móvil y muy lentos
            Triple(Conexion(sinLimite = true, ahorro = true, kbpsEnlace = 300_000), null, Calidad.SD), // ahorro de datos
            Triple(Conexion(sinLimite = true, kbpsEnlace = 300_000, kbpsMedidos = 8_000), null, Calidad.HD), // lo medido manda
            Triple(Conexion(sinLimite = true, kbpsEnlace = 2_000, kbpsMedidos = 40_000), null, Calidad.UHD),
            Triple(Conexion(sinLimite = false), Calidad.UHD, Calidad.UHD),                             // elegida a mano
            Triple(Conexion(sinLimite = true, kbpsEnlace = 100_000), Calidad.SD, Calidad.SD),
        )
        casos.forEachIndexed { i, (c, f, q) -> assertEquals("caso $i: $c", q, Calidades.elige(c, f)) }
    }

    @Test fun siLaCalidadNoEstaLaSiguienteYSiFallaOtraYLuegoOtroCanal() {
        fun r(n: String) = RefCanal("l1", "x" + n.hashCode().toUInt(), n)
        val dazn = Entrada(r("DAZN UHD"), r("DAZN HD"), r("DAZN SD"))
        val sinUhd = Entrada(hd = r("M+ HD"), sd = r("M+ SD"))
        val soloSd = Entrada(sd = r("Otro SD"))
        assertEquals(listOf(Calidad.UHD, Calidad.HD, Calidad.SD), Calidades.orden(Calidad.UHD))
        assertEquals(listOf(Calidad.HD, Calidad.SD, Calidad.UHD), Calidades.orden(Calidad.HD))
        assertEquals(listOf(Calidad.SD, Calidad.HD, Calidad.UHD), Calidades.orden(Calidad.SD))
        assertEquals(Calidad.HD, Calidades.de(sinUhd, Calidad.UHD)); assertEquals(Calidad.SD, Calidades.de(soloSd, Calidad.HD)); assertEquals(Calidad.UHD, Calidades.de(dazn, Calidad.UHD))
        val l = listOf(dazn, sinUhd, soloSd)
        // Falla DAZN en UHD: DAZN en HD; luego en SD; luego el siguiente canal (en su mejor para UHD: HD); …; y al final, nada
        var p = setOf<Pair<Int, Calidad>>()
        val pasos = mutableListOf<Pair<Int, Calidad>>()
        var ahora = 0 to Calidad.UHD
        while (true) {
            val s = Calidades.siguienteIntento(l, ahora.first, ahora.second, Calidad.UHD, p) ?: break
            p = p + ahora; pasos += s; ahora = s
        }
        assertEquals(listOf(0 to Calidad.HD, 0 to Calidad.SD, 1 to Calidad.HD, 1 to Calidad.SD, 2 to Calidad.SD), pasos)
        // Empezando por el segundo, se da la vuelta hasta el primero
        assertEquals(2 to Calidad.SD, Calidades.siguienteIntento(l, 1, Calidad.SD, Calidad.SD, setOf(1 to Calidad.HD)))
        assertEquals(0 to Calidad.SD, Calidades.siguienteIntento(l, 2, Calidad.SD, Calidad.SD, setOf(1 to Calidad.HD, 1 to Calidad.SD)))
        assertNull(Calidades.siguienteIntento(emptyList(), 0, Calidad.HD, Calidad.HD, emptySet()))
    }

    @Test fun lasCalidadesPorElNombreYElMismoCanal() {
        assertEquals(Calidad.UHD, Calidades.deNombre("ES: DAZN LA LIGA UHD")); assertEquals(Calidad.UHD, Calidades.deNombre("M+ LaLiga 4K"))
        assertEquals(Calidad.SD, Calidades.deNombre("ES: DAZN LA LIGA SD")); assertEquals(Calidad.HD, Calidades.deNombre("ES: DAZN LA LIGA HD"))
        assertEquals(Calidad.HD, Calidades.deNombre("M+ LaLiga")); assertEquals("no se confunde con palabras", Calidad.HD, Calidades.deNombre("Sdad Deportiva"))
        assertEquals("ES: DAZN LA LIGA", Calidades.base("ES: DAZN LA LIGA UHD")); assertEquals("M+ LaLiga", Calidades.base("M+ LaLiga FHD"))
        assertTrue(Calidades.mismoCanal("ES: DAZN LA LIGA UHD", "es: dazn la liga sd")); assertFalse(Calidades.mismoCanal("ES: DAZN LA LIGA", "ES: DAZN LA LIGA 2"))
        val uhd = RefCanal("l1", "x1", "ES: DAZN LA LIGA UHD")
        val cands = listOf(RefCanal("l1", "x2", "ES: DAZN LA LIGA HD"), RefCanal("l1", "x3", "ES: DAZN LA LIGA SD"), RefCanal("l1", "x4", "ES: DAZN LA LIGA 2 HD"), RefCanal("l2", "x5", "ES: DAZN LA LIGA SD"))
        val e = CanalesFutbol.entradaCon(uhd, cands)
        assertEquals(Entrada(uhd, cands[0], cands[1]), e)
        assertEquals("ES: DAZN LA LIGA", e.nombre)
        val g = CanalesFutbol.anade(GrupoCanales("dazn", "DAZN LaLiga"), e)
        assertEquals(1, g.canales.size)
        assertEquals("quitar la UHD", Entrada(null, cands[0], cands[1]), CanalesFutbol.pon(g, 0, Calidad.UHD, null).canales[0])
        assertTrue("sin nada, la entrada se va", CanalesFutbol.pon(CanalesFutbol.anade(GrupoCanales("a", "A"), uhd), 0, Calidad.UHD, null).canales.isEmpty())
        assertEquals("añadir por el nombre: en su calidad", Entrada(sd = cands[1]), CanalesFutbol.anade(GrupoCanales("a", "A"), cands[1]).canales[0])
    }

    // ---------------------------------------------------------------- rivales y selecciones

    @Test fun lasSelecciones() {
        val prox = Espn.partidos(json("selecciones_proximos.json"), "uefa.nations")
        assertEquals(2, prox.size)
        val p = prox[0]
        assertEquals("uefa.nations", p.liga); assertEquals("Liga de Naciones", p.competicion.nombre)
        assertEquals("Czechia", p.local.nombre); assertEquals("164", p.visitante.id); assertEquals("Spain", p.visitante.nombre)
        assertEquals("20:45", Hora.reloj(p.inicio))
        val mundial = Espn.partidos(json("selecciones_mundial.json"), "fifa.world")
        val final = mundial.first()
        assertEquals("la final, tras la prórroga", Estado.FINAL_PRORROGA, final.estado); assertEquals("1-0", final.marcador); assertEquals("Spain", final.local.nombre)
        assertEquals("Mundial", final.competicion.nombre)
        val eq = Espn.equipos(json("selecciones_equipos.json"))
        assertEquals(54, eq.size); assertTrue(eq.any { it.id == "164" && it.nombre == "Spain" })
        assertTrue(Competiciones.esDeSelecciones("fifa.friendly")); assertFalse(Competiciones.esDeSelecciones("esp.1"))
        assertEquals(Competiciones.SELECCIONES, Competiciones.para("uefa.nations"))
        assertEquals("de un club: su liga, las copas y las europeas", listOf("esp.1", "uefa.champions", "uefa.europa", "uefa.europa.conf", "esp.copa_del_rey", "esp.super_cup"), Competiciones.para("esp.1").map { it.slug })
        assertEquals("eng.1", Competiciones.para("eng.1").first().slug)
        // Sus partidos van con el grupo «Selección»
        assertEquals(listOf("seleccion"), CanalesFutbol.gruposDe("fifa.world", CanalesFutbol.GRUPOS, CanalesFutbol.ASIGNACION).map { it.id })
        assertEquals(listOf("seleccion"), CanalesFutbol.gruposDe("uefa.nations", CanalesFutbol.GRUPOS, CanalesFutbol.ASIGNACION).map { it.id })
    }

    @Test fun borrarSeleccionNoLaHaceVolver() {
        val sin = CanalesFutbol.GRUPOS.filter { it.id != "seleccion" }
        val t = CanalesFutbol.gruposATexto(sin)
        assertEquals(sin, CanalesFutbol.gruposDeTexto(t))
        // Lo guardado antes de que existiera (sin su marca): aparece
        assertTrue(CanalesFutbol.gruposDeTexto("""[{"id":"movistar","n":"Movistar LaLiga","c":[]}]""").any { it.id == "seleccion" })
    }

    @Test fun tusRivales() {
        val bar = Equipo("83", "Barcelona"); val rma = Equipo("86", "Real Madrid"); val atm = Equipo("1068", "Atlético"); val otro = Equipo("1", "Otro")
        // Un equipo no puede estar en las dos listas
        var (m, r) = CanalesFutbol.alterna(emptyList(), emptyList(), bar, "esp.1", rival = false)
        assertEquals(listOf("83"), m.map { it.first.id })
        CanalesFutbol.alterna(m, r, rma, "esp.1", rival = true).let { m = it.first; r = it.second }
        assertEquals(listOf("86"), r.map { it.first.id })
        CanalesFutbol.alterna(m, r, rma, "esp.1", rival = false).let { m = it.first; r = it.second }
        assertEquals("al hacerlo tuyo, deja de ser rival", listOf("83", "86"), m.map { it.first.id }); assertTrue(r.isEmpty())
        CanalesFutbol.alterna(m, r, bar, "esp.1", rival = true).let { m = it.first; r = it.second }
        assertEquals(listOf("86"), m.map { it.first.id }); assertEquals(listOf("83"), r.map { it.first.id })
        CanalesFutbol.alterna(m, r, bar, "esp.1", rival = true).let { m = it.first; r = it.second }
        assertTrue("otra vez: fuera", r.isEmpty())
        // Sus partidos: el de uno tuyo contra un rival sale como tuyo; en la portada, los de rivales solo si se juegan hoy
        val h = 3_600_000L
        val ahoraMad = Hora.lee("2026-11-07T10:00:00Z")
        val l = listOf(
            Partido("clasico", "esp.1", ahoraMad + 48 * h, bar, rma),
            Partido("hoy", "esp.1", ahoraMad + 8 * h, rma, otro),
            Partido("vivo", "esp.1", ahoraMad - 30 * 60_000L, atm, otro, Estado.EN_JUEGO, 0, 0, "30'"),
            Partido("luego", "esp.1", ahoraMad + 72 * h, atm, otro),
        )
        assertTrue(Orden.contraRival(l[0], listOf("83"), listOf("86"))); assertFalse(Orden.contraRival(l[1], listOf("83"), listOf("86")))
        assertEquals(listOf("vivo", "hoy"), Orden.deRivales(l, listOf("86", "1068"), listOf("83"), ahoraMad).map { it.id })
        assertEquals(listOf("vivo", "hoy"), Orden.deRivales(l, listOf("86", "1068"), listOf("83"), ahoraMad, soloHoy = true).map { it.id })
        assertEquals("el del Atlético que no es hoy, fuera de la portada", listOf<String>(), Orden.deRivales(l.filter { it.id == "luego" }, listOf("1068"), listOf("83"), ahoraMad, soloHoy = true).map { it.id })
        assertEquals("el clásico es tuyo, no del rival", listOf("clasico"), Orden.tusPartidos(l, listOf("83"), ahoraMad).map { it.id })
        assertEquals(listOf<String>(), Orden.deRivales(l.take(1), listOf("86"), listOf("83"), ahoraMad).map { it.id })
    }

    // ---------------------------------------------------------------- los partidos del día

    @Test fun losMarcadoresDeCadaCompeticion() {
        val laliga = Espn.partidos(json("dia_laliga_vivo.json"), "esp.1")
        val vivo = laliga.first { it.id == "401882808" }
        assertEquals("Valencia", vivo.local.nombre); assertEquals(Estado.EN_JUEGO, vivo.estado); assertEquals("0-2", vivo.marcador); assertEquals("65'", vivo.reloj)
        assertEquals("65'", Dia.cuando(vivo)); assertEquals("FINAL", Dia.cuando(laliga.first { it.id == "401882812" }))
        assertEquals("Deportivo La Coruña", laliga.first { it.id == "401882813" }.visitante.nombre)
        val premier = Espn.partidos(json("dia_premier.json"), "eng.1")
        assertEquals(5, premier.size); assertEquals("eng.1", premier[0].liga); assertEquals("15:00", Dia.cuando(premier[0]))
        assertEquals("Crystal Palace", premier.first { it.id == "401879243" }.local.nombre)
        val ucl = Espn.partidos(json("dia_champions.json"), "uefa.champions")
        assertEquals(4, ucl.size); assertTrue(ucl.any { it.visitante.id == "83" || it.local.id == "83" })
        val unl = Espn.partidos(json("dia_naciones.json"), "uefa.nations")
        assertTrue("España en español", unl.any { it.local.nombre == "Chequia" && it.visitante.nombre == "España" })
        val hyper = Espn.partidos(json("dia_hypermotion.json"), "esp.2")
        assertEquals(4, hyper.size); assertEquals("LaLiga Hypermotion", Competiciones.de("esp.2").nombre)
        assertEquals("https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard?dates=20261108&lang=es&region=es", Espn.urlMarcadores("eng.1", LocalDate.of(2026, 11, 8)))
    }

    @Test fun elDiaAgrupadoPorCompeticion() {
        val laliga = Espn.partidos(json("dia_laliga_vivo.json"), "esp.1")
        val premier = Espn.partidos(json("dia_premier.json"), "eng.1")
        val hoy = LocalDate.of(2026, 11, 7)
        val bar = Equipo("83", "Barcelona"); val otro = Equipo("1", "Otro")
        val nuestro = Partido("x", "uefa.europa.conf", Hora.lee("2026-11-07T18:00Z"), bar, otro)
        val todo = laliga + premier + nuestro
        val s = Dia.agrupa(todo, hoy, listOf("83", "86"), Competiciones.DEL_DIA)
        assertEquals("primero tus equipos (el del Madrid y el del Barça, por hora), luego LaLiga; la Premier es de mañana", listOf("tus", "esp.1"), s.map { it.clave })
        assertEquals(listOf("x", "401882808"), s[0].partidos.map { it.id })
        assertEquals("los tuyos no se repiten en su competición", 3, s[1].partidos.size)
        assertEquals(listOf("401882812", "401882813", "401882811"), s[1].partidos.map { it.id })
        val manana = Dia.agrupa(todo, hoy.plusDays(1), emptyList(), Competiciones.DEL_DIA)
        assertEquals(listOf("eng.1"), manana.map { it.clave })
        // Escondida, no sale (salvo lo de tus equipos); tu orden manda
        assertTrue(Dia.agrupa(todo, hoy.plusDays(1), emptyList(), Competiciones.DEL_DIA, setOf("eng.1")).isEmpty())
        assertEquals(listOf("tus"), Dia.agrupa(todo, hoy, listOf("83", "86"), Competiciones.DEL_DIA, setOf("esp.1")).map { it.clave })
        val orden = Competiciones.ordenadas(listOf("eng.1", "nada", "esp.1"))
        assertEquals(listOf("eng.1", "esp.1", "uefa.champions"), orden.take(3).map { it.slug })
        assertEquals("todas, sin repetir", Competiciones.DEL_DIA.size, orden.size)
        assertEquals("esp.2", Competiciones.DEL_DIA.last().slug)
    }

    @Test fun laTiraDeDiasYLoQueDuraLoGuardado() {
        val ahora = Hora.lee("2026-11-07T10:00:00Z")
        val t = Hora.tira(ahora, 1, 3)
        assertEquals(listOf("Ayer", "Hoy", "Mañana", "Lun 9", "Mar 10"), t.map { Hora.nombreDia(it, ahora) })
        assertEquals("Sábado 7 de noviembre", Hora.largo(LocalDate.of(2026, 11, 7)))
        val hoy = LocalDate.of(2026, 11, 7)
        assertTrue(Dia.edad(hoy.minusDays(5), ahora, false) > 86_400_000L)
        assertEquals(60_000L, Dia.edad(hoy, ahora, true)); assertEquals(600_000L, Dia.edad(hoy, ahora, false))
        assertEquals(3_600_000L, Dia.edad(hoy.plusDays(2), ahora, false))
    }

    @Test fun caraACaraUltimosYPlantilla() {
        val f = Espn.ficha(json("ficha_cara_a_cara.json"), "esp.1")
        assertEquals(5, f.caraACara.size)
        assertTrue(f.caraACara.all { it.juega("83") && it.juega("96") && it.estado.terminado && it.golesLocal != null })
        assertTrue("el más reciente primero", f.caraACara.zipWithNext().all { (a, b) -> a.inicio >= b.inicio })
        assertEquals(setOf("83", "96"), f.ultimos.keys)
        val ult = f.ultimos["83"]!!
        assertTrue(ult.isNotEmpty()); assertTrue(ult.all { it.juega("83") })
        val get = ult.first { it.id == "401882853" }
        assertEquals("Barcelona", get.local.nombre); assertEquals("Getafe", get.visitante.nombre); assertEquals("3-0", get.marcador); assertEquals("esp.1", get.liga)
        val pl = Espn.plantilla(json("plantilla.json"))
        assertEquals(34, pl.size)
        assertEquals("los porteros primero, por dorsal", "Joan García", pl.first().nombre); assertEquals("POR", pl.first().posicion)
        assertEquals(listOf("POR", "DEF", "MED", "DEL"), pl.map { it.posicion }.distinct())
    }

    @Test fun dondeVerloEnLosTresCasos() {
        val dazn = GrupoCanales("dazn", "DAZN LaLiga", listOf(Entrada(hd = RefCanal("l1", "x1", "DAZN LaLiga HD"))))
        val grupos = CanalesFutbol.GRUPOS.map { if (it.id == "dazn") dazn else it }
        val a = CanalesFutbol.ASIGNACION
        // LaLiga: Movistar sin canal (elegir) y DAZN con canal (ver)
        assertEquals(listOf(Donde.Elegir(grupos[0]), Donde.Ver(dazn)), DondeVer.opciones("esp.1", grupos, a))
        // La Premier no tiene grupo: crear el suyo
        assertEquals(listOf(Donde.Crear("eng.1", "Premier League")), DondeVer.opciones("eng.1", grupos, a))
        val e = Entrada(hd = RefCanal("l1", "x9", "M+ Premier HD"))
        val (g2, a2, id) = DondeVer.crea("eng.1", "Premier League", e, grupos, a)
        assertEquals("gpremierleague", id)
        assertEquals(listOf(Donde.Ver(GrupoCanales(id, "Premier League", listOf(e)))), DondeVer.opciones("eng.1", g2, a2))
        assertEquals("lo demás, igual", a["esp.1"], a2["esp.1"])
        // Otra vez (ya existe un grupo con ese nombre): se usa ese, no se duplica
        val (g3, _, id3) = DondeVer.crea("eng.1", "Premier League", null, g2, a2)
        assertEquals(id, id3); assertEquals(g2.size, g3.size)
        // Elegir para un grupo vacío lo guarda en él
        val g4 = DondeVer.guarda("movistar", Entrada(hd = RefCanal("l1", "x2", "M+ LaLiga HD")), grupos)
        assertTrue(DondeVer.opciones("esp.1", g4, a).all { it is Donde.Ver })
        assertEquals("Tus canales para LaLiga", DondeVer.titulo("esp.1"))
    }

    @Test fun lasHorasVanEnLaZonaDelMovilEnIrlandaUnaMenosQueEnEspana() {
        val antes = Hora.zona
        try {
            // Un partido a las 21:00 de España, en invierno (13 nov) y en verano (15 ago)
            val invierno = java.time.LocalDateTime.of(2026, 11, 13, 21, 0).atZone(Hora.ESPANA).toInstant().toEpochMilli()
            val verano = java.time.LocalDateTime.of(2026, 8, 15, 21, 0).atZone(Hora.ESPANA).toInstant().toEpochMilli()
            Hora.zona = Hora.ESPANA
            assertEquals("21:00", Hora.reloj(invierno)); assertEquals("21:00", Hora.reloj(verano))
            Hora.zona = java.time.ZoneId.of("Europe/Dublin")
            assertEquals("20:00", Hora.reloj(invierno)); assertEquals("20:00", Hora.reloj(verano))
            // Uno a las 00:30 de España es aún el día anterior en Irlanda
            val tarde = java.time.LocalDateTime.of(2026, 11, 14, 0, 30).atZone(Hora.ESPANA).toInstant().toEpochMilli()
            assertEquals(LocalDate.of(2026, 11, 13), Hora.diaDe(tarde))
        } finally { Hora.zona = antes }
    }
}
