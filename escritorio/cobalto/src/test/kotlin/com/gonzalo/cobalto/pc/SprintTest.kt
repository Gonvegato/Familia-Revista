package com.gonzalo.cobalto.pc

import com.gonzalo.cobalto.Escritos
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Los sprints: cuentan las palabras nuevas de la obra (las que apunta Escritos.escribe, una vez) y se guardan como en el móvil. */
class SprintTest {
    @get:Rule val tmp = TemporaryFolder()
    private var ahora = 1_000_000L
    private lateinit var e: Escritos
    private lateinit var pid: String
    private lateinit var a: String
    private lateinit var b: String

    @Before fun prepara() {
        Cuaderno.reiniciaParaPruebas(); Cuaderno.dirPruebas = tmp.root
        SprintEscribir.reiniciaParaPruebas()
        SprintEscribir.reloj = { ahora }
        SprintEscribir.hoy = { 20_000L }
        e = Escritos(File(tmp.root, "e"))
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra") }
        pid = obra.id
        val cap = e.nuevo(Escritos.T_CAP, pid) { put("titulo", "Uno") }
        a = e.nuevo(Escritos.T_ESCENA, pid) { put("cid", cap.id); put("texto", "") }.id
        b = e.nuevo(Escritos.T_ESCENA, pid) { put("cid", cap.id); put("texto", "") }.id
    }
    @After fun fuera() { SprintEscribir.reiniciaParaPruebas(); Cuaderno.reiniciaParaPruebas(); Cuaderno.dirPruebas = null }

    @Test fun cuentaLoNuevoDeTodaLaObraUnaSolaVez() {
        // Lo escrito antes no cuenta
        e.escribe(a, "Antes de empezar había cinco.")
        SprintEscribir.hoy = { Escritos.hoy() }
        SprintEscribir.empieza(e, pid, 25, 10)
        assertTrue(SprintEscribir.activo(pid)); assertFalse(SprintEscribir.activo("otra"))
        assertEquals(0, SprintEscribir.llevas(e))
        e.escribe(a, "Antes de empezar había cinco. Y luego tres más.")
        e.escribe(b, "Otra escena con cuatro.")
        // Guardar lo mismo otra vez no suma
        e.escribe(b, "Otra escena con cuatro.")
        assertEquals(8, SprintEscribir.llevas(e))
        // Terminar antes de tiempo, a los 12 minutos: 12 min, 8 palabras, 0 por minuto (8/12) y la meta sin cumplir
        ahora += 12 * 60_000L
        SprintEscribir.termina(e, ya = true)
        assertFalse(SprintEscribir.activo())
        val s = SprintEscribir.terminado!!
        assertEquals(12, s.minutos); assertEquals(8, s.palabras); assertEquals(10, s.meta); assertEquals(0, s.porMinuto)
        val h = SprintEscribir.historial(pid)
        assertEquals(1, h.size); assertEquals(8, h[0].palabras)
        assertTrue(SprintEscribir.historial("otra").isEmpty())
    }

    @Test fun seGuardaComoEnElMovilYSigueTrasCerrarLaApp() {
        SprintEscribir.hoy = { Escritos.hoy() }
        SprintEscribir.empieza(e, pid, 10, 0)
        val o = JSONObject(File(tmp.root, SprintEscribir.ARCHIVO).readText()).getJSONObject("sprint")
        assertEquals(pid, o.getString("pid")); assertEquals(10, o.getInt("min")); assertEquals(ahora + 600_000L, o.getLong("hasta"))
        assertTrue(o.has("base") && o.has("dia") && o.has("desde") && o.has("meta"))
        // «Se cierra la app»: se olvida lo de memoria y, al volver antes de la hora, sigue en marcha
        SprintEscribir.reiniciaParaPruebas(); SprintEscribir.reloj = { ahora }; SprintEscribir.hoy = { Escritos.hoy() }
        ahora += 5 * 60_000L
        SprintEscribir.recupera(e)
        assertTrue(SprintEscribir.activo(pid))
        e.escribe(a, "Uno dos tres cuatro cinco seis siete ocho nueve diez once doce.")
        // Pasa su hora con la app cerrada: al abrir se da por terminado con sus 10 minutos
        SprintEscribir.reiniciaParaPruebas(); SprintEscribir.reloj = { ahora }; SprintEscribir.hoy = { Escritos.hoy() }
        ahora += 20 * 60_000L
        SprintEscribir.recupera(e)
        assertFalse(SprintEscribir.activo())
        val s = SprintEscribir.terminado!!
        assertEquals(10, s.minutos); assertEquals(12, s.palabras); assertEquals(1, s.porMinuto)
        val x = JSONObject(File(tmp.root, SprintEscribir.ARCHIVO).readText())
        assertFalse(x.has("sprint"))
        val t = x.getJSONArray("sprints").getJSONObject(0)
        assertEquals(setOf("pid", "desde", "min", "pal", "meta"), t.keys().asSequence().toSet())
    }

    @Test fun unSprintDeMenosDeUnMinutoNoQuedaEnElHistorial() {
        SprintEscribir.empieza(e, pid, 25, 0)
        ahora += 10_000L
        SprintEscribir.termina(e, ya = true)
        assertNull(SprintEscribir.terminado)
        assertTrue(SprintEscribir.historial().isEmpty())
        // Terminar sin uno en marcha no hace nada
        SprintEscribir.termina(e, ya = true)
        assertTrue(SprintEscribir.historial().isEmpty())
    }

    @Test fun elHistorialGuardaLosUltimos200() {
        repeat(205) { i ->
            SprintEscribir.empieza(e, pid, 5, 0)
            ahora += 5 * 60_000L + 1
            SprintEscribir.termina(e)
            ahora += i.toLong()
        }
        assertEquals(200, SprintEscribir.historial().size)
    }
}
