package com.gonzalo.ptah

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Lo que no es pantalla: el modelo de un diseño (escribirlo y leerlo), el registro de tipos, los tamaños, la cuenta atrás, el calendario,
 * los formatos de la hora y de la fecha, los accesos, y Open-Meteo (su respuesta, de verdad o con su forma de verdad, y los códigos WMO).
 */
class ModeloTest {
    private fun recurso(n: String): String = listOf(File("src/test/resources/openmeteo/$n"), File("ptah/src/test/resources/openmeteo/$n")).first { it.exists() }.readText()

    // ---------------------------------------------------------------- el diseño

    @Test fun unDisenoSeEscribeYSeLeeIgual() {
        val e = Estilo(fondo = TipoFondo.DEGRADADO, colorFondo = "#12393A", colorFondo2 = Col.ACENTO, angulo = 45, opacidad = 80, tinta = "#F2E6CF", acento = "#D98A3D",
            secundario = Col.GRIS, piezas = mapOf("horaria" to "#FF0000", "segundero" to Col.ACENTO), borde = false, grosorBorde = 2.5f, colorBorde = "#80FFFFFF",
            radio = 30f, marco = true, letraTitulos = "Playfair Display", letraTexto = "Vitrina", escala = 1.3f, alineacion = Alineacion.CENTRO, ocultas = setOf("fecha", "rayas"))
        val d = Diseno("d0123456789", "Mi reloj «bonito»", "agujas", e, mapOf("numeros" to "romanos", "segundero" to true, "dias" to 4, "lat" to 42.6, "items" to listOf("app:a.b", "web:x.com|X")), 123L, 100L)
        val leido = Diseno.deJson(d.aJson())!!
        assertEquals(d.id, leido.id); assertEquals(d.nombre, leido.nombre); assertEquals(d.tipo, leido.tipo)
        assertEquals(e, leido.estilo)
        assertEquals("romanos", leido.texto("numeros")); assertTrue(leido.si("segundero")); assertEquals(4, leido.entero("dias"))
        assertEquals(42.6, leido.decimal("lat"), 1e-9); assertEquals(listOf("app:a.b", "web:x.com|X"), leido.lista("items"))
        assertEquals(123L, leido.cambiado); assertEquals(100L, leido.creado)
        // Y otra vuelta: el mismo texto
        assertEquals(leido.aJson(), Diseno.deJson(leido.aJson())!!.aJson())
    }

    @Test fun unTextoRotoNoEsUnDiseno() {
        assertNull(Diseno.deJson(null)); assertNull(Diseno.deJson("")); assertNull(Diseno.deJson("{roto")); assertNull(Diseno.deJson("""{"id":"d1"}"""))
        // Lo que falte, de serie; lo que no se entienda, también
        val d = Diseno.deJson("""{"id":"d1","tipo":"nota","estilo":{"fondo":"RARO","radio":999,"esc":9}}""")!!
        assertEquals(TipoFondo.PAPEL, d.estilo.fondo); assertEquals(80f, d.estilo.radio); assertEquals(2f, d.estilo.escala)
        // Un tipo de una versión más nueva se lee (y se conserva tal cual)
        val nuevo = Diseno.deJson("""{"id":"d2","tipo":"holograma","op":{"x":1}}""")!!
        assertEquals("holograma", nuevo.tipo); assertNull(Tipos.de(nuevo.tipo)); assertEquals(1, nuevo.entero("x"))
    }

    @Test fun losIdsSonDeLaCuenta() {
        val ids = (1..200).map { Diseno.nuevoId() }.toSet()
        assertEquals(200, ids.size)
        assertTrue(ids.all { it.matches(Regex("d[0-9a-f]{10}")) })
        // (un campo de la cuenta: sin . $ # [ ] /)
        assertTrue(ids.all { EspejoCuenta.campo(it) == it })
    }

    @Test fun losColores() {
        val t = PALETTES.first { it.id == "revista" }.light
        assertEquals(0xFF12393A.toInt(), Col.resuelve("#12393A", t, 0))
        assertEquals(0x8012393A.toInt(), Col.resuelve("#8012393A", t, 0))
        assertEquals(t.paper.toInt(), Col.resuelve(Col.PAPEL, t, 0))
        assertEquals(t.general.toInt(), Col.resuelve(Col.ACENTO, t, 0))
        assertEquals(0, Col.resuelve(Col.TRANSPARENTE, t, 7))
        assertEquals(7, Col.resuelve("", t, 7)); assertEquals(7, Col.resuelve("#xyz", t, 7))
        assertEquals("#12393A", Col.hex(0xFF12393A.toInt())); assertEquals("#8012393A", Col.hex(0x8012393A.toInt()))
        // ¿Cambia con el claro y el oscuro?
        assertTrue(Estilo().delTema)
        assertFalse(Estilo(fondo = TipoFondo.COLOR, colorFondo = "#000000", tinta = "#FFFFFF", acento = "#FF0000", secundario = "#888888").delTema)
        assertTrue(Estilo(fondo = TipoFondo.COLOR, colorFondo = "#000000", tinta = "#FFFFFF", acento = "#FF0000", secundario = "#888888", piezas = mapOf("x" to Col.ACENTO)).delTema)
    }

    // ---------------------------------------------------------------- el registro de tipos

    @Test fun cadaTipoEstaUnaVezYSabeHacerseNuevo() {
        val ids = Tipos.TODOS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (t in Tipos.TODOS) {
            assertSame(t, Tipos.de(t.id))
            assertTrue("${t.id}: grupo conocido", t.grupo in Tipos.GRUPOS)
            assertTrue("${t.id}: con descripción", t.descripcion.length > 20)
            val d = t.nuevo()
            assertEquals(t.id, d.tipo)
            // Lo que trae de serie sobrevive a la cuenta
            assertEquals(d.opciones.keys, Diseno.deJson(d.aJson())!!.opciones.keys)
            assertEquals((t.piezas + t.partes).map { it.first }.size, (t.piezas.map { it.first }.toSet().size + t.partes.map { it.first }.toSet().size))
        }
        // Cada grupo tiene alguno; y los de la primera versión están todos
        assertTrue(Tipos.GRUPOS.all { g -> Tipos.TODOS.any { it.grupo == g } })
        assertEquals(setOf("digital", "agujas", "calendario", "buscador", "accesos", "tiempo", "bateria", "nota", "cuentaatras", "foto"), ids.toSet())
        assertEquals(setOf(Necesidad.TIEMPO), Tiempo.necesita(Tiempo.nuevo()))
        assertEquals(setOf(Necesidad.MEDIANOCHE), Calendario.necesita(Calendario.nuevo()))
        assertTrue(RelojDigital.necesita(RelojDigital.nuevo()).isEmpty())
    }

    @Test fun lasPlantillasSonDeTiposQueHay() {
        assertTrue(Plantillas.TODAS.size >= 10)
        for ((p, c) in Plantillas.TODAS) {
            assertNotNull(p.nombre, Tipos.de(p.tipo)); assertTrue(p.id.startsWith("p")); assertTrue(c.col in 1..5 && c.fil in 1..4)
            val copia = Plantillas.copia(p)
            assertNotEquals(p.id, copia.id); assertEquals(p.estilo, copia.estilo); assertEquals(p.opciones, copia.opciones - "celdas"); assertEquals(c, Plantillas.celdasDe(copia)); assertTrue(copia.id.startsWith("d"))
        }
        assertEquals("todos los tipos tienen al menos una plantilla", Tipos.TODOS.map { it.id }.toSet(), Plantillas.TODAS.map { it.first.tipo }.toSet())
    }

    // ---------------------------------------------------------------- los tamaños

    @Test fun lasCeldasYLosDp() {
        // Celdas casi cuadradas, como las de los lanzadores de ahora (76 dp menos el hueco)
        assertEquals(70f to 70f, Tamanos.dp(Tamanos.Celdas(1, 1)))
        assertEquals(70f to 146f, Tamanos.dp(Tamanos.Celdas(1, 2)))
        assertEquals(298f to 146f, Tamanos.dp(Tamanos.Celdas(4, 2)))
        assertEquals(298f to 298f, Tamanos.dp(Tamanos.Celdas(4, 4)))
        for (c in Tamanos.PRESETS) { val (w, h) = Tamanos.dp(c); assertEquals(c, Tamanos.celdas(w, h)) }
        assertTrue("1×1 y 1×2 de pie, entre los tamaños del editor", Tamanos.Celdas(1, 1) in Tamanos.PRESETS && Tamanos.Celdas(1, 2) in Tamanos.PRESETS)
        assertEquals(Tamanos.Celdas(1, 1), Tamanos.celdas(40f, 40f))
        assertEquals("4×2", Tamanos.Celdas(4, 2).toString())
        assertEquals(Tamanos.MIN to Tamanos.MAX, Tamanos.acota(1f, 9999f))
    }

    // ---------------------------------------------------------------- la cuenta atrás y el calendario

    @Test fun laCuentaAtras() {
        val hoy = LocalDate.of(2026, 11, 7)
        assertEquals(CuentaAtras.Cuenta(48, "Faltan", "días", false), CuentaAtras.cuenta(hoy, LocalDate.of(2026, 12, 25)))
        assertEquals(CuentaAtras.Cuenta(1, "Falta", "día", false), CuentaAtras.cuenta(hoy, hoy.plusDays(1)))
        assertEquals(CuentaAtras.Cuenta(0, "Es hoy", "", false), CuentaAtras.cuenta(hoy, hoy))
        assertEquals(CuentaAtras.Cuenta(1, "Hace", "día", true), CuentaAtras.cuenta(hoy, hoy.minusDays(1)))
        assertEquals(CuentaAtras.Cuenta(365, "Hace", "días", true), CuentaAtras.cuenta(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 1, 1)))
        // (con años bisiestos y el cambio de hora de por medio: días de calendario)
        assertEquals(366L, CuentaAtras.cuenta(LocalDate.of(2027, 3, 1), LocalDate.of(2028, 3, 1)).dias)
        assertEquals(1L, CuentaAtras.cuenta(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 29)).dias)
        // La de serie: la próxima Navidad
        val n = CuentaAtras.fecha(CuentaAtras.nuevo())
        assertEquals(12, n.monthValue); assertEquals(25, n.dayOfMonth); assertFalse(n.isBefore(LocalDate.now()))
    }

    @Test fun elMesEnSemanasEnteras() {
        val nov = Calendario.rejilla(LocalDate.of(2026, 11, 7))
        // Noviembre de 2026 empieza en domingo: la primera semana es la del lunes 26 de octubre
        assertEquals(LocalDate.of(2026, 10, 26), nov.first()); assertEquals(DayOfWeek.MONDAY, nov.first().dayOfWeek)
        assertEquals(0, nov.size % 7); assertEquals(LocalDate.of(2026, 12, 6), nov.last())
        assertTrue(nov.contains(LocalDate.of(2026, 11, 30)))
        val dom = Calendario.rejilla(LocalDate.of(2026, 11, 7), lunes = false)
        assertEquals(LocalDate.of(2026, 11, 1), dom.first()); assertEquals(DayOfWeek.SUNDAY, dom.first().dayOfWeek)
        // Febrero de 2027 (lunes 1, 28 días): cuatro semanas justas
        assertEquals(28, Calendario.rejilla(LocalDate.of(2027, 2, 10)).size)
    }

    // ---------------------------------------------------------------- la hora y la fecha

    @Test fun losFormatosComoElTextClock() {
        val t = ZonedDateTime.of(2026, 9, 30, 9, 5, 7, 0, ZoneId.of("Europe/Madrid"))
        assertEquals("9:05", Formatos.formatea(Formatos.HORA, t))
        assertEquals("09:05", Formatos.formatea(Formatos.HORA_CERO, t))
        assertEquals("21:05", Formatos.formatea(Formatos.HORA, t.withHour(21)))
        assertEquals("9:05", Formatos.formatea(Formatos.HORA_12, t.withHour(21)))
        assertEquals("12:00", Formatos.formatea(Formatos.HORA_12, t.withHour(0).withMinute(0)))
        assertEquals("miércoles 30 de septiembre", Formatos.formatea(Formatos.FECHA_LARGA, t))
        assertEquals("mié 30 sept", Formatos.formatea(Formatos.FECHA_CORTA, t))
        assertEquals("de septiembre", Formatos.formatea("'de' MMMM", t))
        // Las muestras: lo más ancho que puede salir
        assertTrue(Formatos.muestras(Formatos.FECHA_LARGA).contains("miércoles 30 de septiembre"))
        assertTrue(Formatos.muestras(Formatos.HORA).contains("23:59"))
    }

    // ---------------------------------------------------------------- los accesos

    @Test fun losAccesos() {
        assertEquals(Acceso(false, "com.android.chrome", ""), Acceso.de("app:com.android.chrome"))
        assertEquals(Acceso(true, "elpais.com", "El País"), Acceso.de("web:elpais.com|El País"))
        assertEquals(Acceso(true, "x.com", ""), Acceso.de("web:x.com"))
        assertNull(Acceso.de("otra:cosa"))
        val a = Acceso(true, "es.wikipedia.org/wiki/Ptah", "Ptah")
        assertEquals(a, Acceso.de(a.aTexto()))
        assertEquals("https://elpais.com", Acceso.direccion(" elpais.com "))
        assertEquals("http://intra.net", Acceso.direccion("http://intra.net"))
    }

    // ---------------------------------------------------------------- Open-Meteo

    @Test fun elGeocodingDeVerdad() {
        // (la respuesta de verdad de geocoding-api.open-meteo.com para «León», sin los códigos postales)
        val l = OpenMeteo.lugares(recurso("geocoding_leon.json"))
        assertEquals(5, l.size)
        val esp = l.first { it.pais == "España" }
        assertEquals("León", esp.nombre); assertEquals("Comunidad Autónoma de Castilla y León", esp.region)
        assertEquals(42.60003, esp.lat, 1e-6); assertEquals(-5.57032, esp.lon, 1e-6)
        assertEquals("Comunidad Autónoma de Castilla y León, España", esp.detalle)
        // Sin resultados, Open-Meteo no manda «results»: es una lista vacía
        assertTrue(OpenMeteo.lugares(recurso("geocoding_nada.json")).isEmpty())
    }

    @Test fun laPrevision() {
        val p = OpenMeteo.prevision(recurso("prevision_leon.json"), 5L)
        assertEquals(4.1, p.temp, 1e-9); assertEquals(1.2, p.sensacion!!, 1e-9); assertEquals(87, p.humedad); assertEquals(3, p.codigo)
        assertFalse(p.esDia); assertEquals("Europe/Madrid", p.zona); assertEquals(5L, p.bajada)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 7, 23, 45), p.hora)
        assertEquals(7, p.dias.size)
        assertEquals(DiaPrevision(LocalDate.of(2026, 11, 8), 61, 10.4, 5.1, 90), p.dias[1])
        assertEquals(-2.1, p.dias.last().min, 1e-9)
        // El límite de llamadas (la respuesta de verdad de cuando se pasa): un error con lo que dice
        val e = assertThrows(OpenMeteo.Error::class.java) { OpenMeteo.prevision(recurso("error_limite.json")) }
        assertTrue(e.message!!.contains("limit"))
        assertThrows(OpenMeteo.Error::class.java) { OpenMeteo.lugares(recurso("error_limite.json")) }
    }

    @Test fun lasDireccionesDeOpenMeteo() {
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=San+Sebasti%C3%A1n&count=8&language=es&format=json", OpenMeteo.urlBuscar(" San Sebastián "))
        val u = OpenMeteo.urlPrevision(42.6, -5.57)
        assertTrue(u.startsWith("https://api.open-meteo.com/v1/forecast?latitude=42.6000&longitude=-5.5700&"))
        assertTrue(u.contains("current=temperature_2m,") && u.contains("weather_code") && u.contains("daily=weather_code,temperature_2m_max,temperature_2m_min") && u.contains("timezone=auto"))
        // (sin clave: es la API gratuita, de uso no comercial)
        assertFalse(u.contains("apikey"))
    }

    @Test fun losCodigosWmo() {
        assertEquals(Wmo.Estado("Despejado", IconoTiempo.DESPEJADO), Wmo.de(0))
        assertEquals(IconoTiempo.POCAS_NUBES, Wmo.de(1).icono); assertEquals(IconoTiempo.NUBES, Wmo.de(2).icono); assertEquals(IconoTiempo.CUBIERTO, Wmo.de(3).icono)
        assertEquals(IconoTiempo.NIEBLA, Wmo.de(45).icono); assertEquals(IconoTiempo.NIEBLA, Wmo.de(48).icono)
        for (c in listOf(51, 53, 55, 56, 57)) assertEquals(IconoTiempo.LLOVIZNA, Wmo.de(c).icono)
        for (c in listOf(61, 63, 65, 66, 67)) assertEquals(IconoTiempo.LLUVIA, Wmo.de(c).icono)
        for (c in listOf(71, 73, 75, 77, 85, 86)) assertEquals(IconoTiempo.NIEVE, Wmo.de(c).icono)
        for (c in listOf(80, 81, 82)) assertEquals(IconoTiempo.CHUBASCOS, Wmo.de(c).icono)
        for (c in listOf(95, 96, 99)) assertEquals(IconoTiempo.TORMENTA, Wmo.de(c).icono)
        assertEquals("Tormenta con granizo", Wmo.de(99).texto); assertEquals("Lluvia fuerte", Wmo.de(65).texto)
        // Uno que no existe no rompe nada
        assertEquals(IconoTiempo.NUBES, Wmo.de(42).icono)
        // Todos los iconos se usan
        assertEquals(IconoTiempo.entries.toSet(), (0..99).map { Wmo.de(it).icono }.toSet())
        assertEquals("12°", grados(12.4)); assertEquals("−2°", grados(-2.1)); assertEquals("0°", grados(-0.3))
    }
}
