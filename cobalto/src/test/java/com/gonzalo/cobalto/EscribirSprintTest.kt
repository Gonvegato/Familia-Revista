package com.gonzalo.cobalto

import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Config
import com.gonzalo.familia.nucleo.DiscoMemoria
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.RedJava
import com.gonzalo.familia.nucleo.comoMapa
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as ConfigRobolectric
import java.io.File

/**
 * Los sprints en la cuenta (EscribirSprint.kt: el mismo archivo en el móvil y en el ordenador, y esta prueba la pasan los dos). Cada
 * «dispositivo» es su propio [MotorSprints] con su propia cuenta; las que hablan entre sí van contra el emulador de Firebase con las reglas
 * de verdad (nube/reglas.json).
 */
@RunWith(RobolectricTestRunner::class)
@ConfigRobolectric(sdk = [34])
class EscribirSprintTest {
    @get:Rule val tmp = TemporaryFolder()
    private var ahora = 1_790_000_000_000L
    private val MIN = 60_000L

    private class CuentaDe(val f: Familia, override val dispositivo: String) : CuentaSprints {
        override fun coleccion(): Coleccion = f.coleccion(SprintEscribir.COLECCION)
        override val dentro: Boolean get() = f.sesion.dentro
    }
    /** Una cuenta sin sesión (lo hecho espera en su cola), para lo que no necesita red. */
    private fun sinRed() = Familia(Config(), RedJava(), DiscoMemoria()).also { it.automatico = false }
    private fun motor(f: Familia, dev: String) = MotorSprints().also { it.cuenta = CuentaDe(f, dev); it.reloj = { ahora } }

    private class Obra(val e: Escritos, val pid: String, val a: String, val b: String)
    private fun obra(nombre: String): Obra {
        val e = Escritos(File(tmp.root, nombre))
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra") }
        val c = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        return Obra(e, o.id, e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "") }.id, e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "") }.id)
    }
    private fun viejo(pid: String, desde: Long, min: Int, pal: Int, meta: Int) = JSONObject().put("pid", pid).put("desde", desde).put("min", min).put("pal", pal).put("meta", meta)

    // ------------------------------------------------------------------ Sin red

    @Test fun cuentaLoNuevoDeTodaLaObraUnaSolaVezYSeGuardaEnLaCuenta() {
        val f = sinRed(); val s = motor(f, "cobalto-movil"); val o = obra("e")
        // Lo escrito antes no cuenta
        o.e.escribe(o.a, "Antes de empezar había cinco.")
        s.empieza(o.e, o.pid, 25, 10)
        val desde = ahora
        assertTrue(s.activo(o.pid)); assertFalse(s.activo("otra"))
        assertEquals(0, s.llevas(o.e))
        // Ya está en la cuenta, en marcha
        val enMarcha = f.coleccion("sprints").nodo(s.ref(o.pid, desde))!!
        assertEquals(0L, enMarcha.entero("fin")); assertEquals(desde + 25 * MIN, enMarcha.entero("hasta")); assertEquals("cobalto-movil", enMarcha.texto("dev"))
        o.e.escribe(o.a, "Antes de empezar había cinco. Y luego tres más.")
        o.e.escribe(o.b, "Otra escena con cuatro.")
        // Guardar lo mismo otra vez no suma
        o.e.escribe(o.b, "Otra escena con cuatro.")
        assertEquals(8, s.llevas(o.e))
        // Terminar antes de tiempo, a los 12 minutos: 12 min, 8 palabras, 0 por minuto (8/12) y la meta sin cumplir
        ahora += 12 * MIN
        s.termina(o.e, ya = true)
        assertFalse(s.activo())
        val t = s.terminado!!
        assertEquals(12, t.minutos); assertEquals(8, t.palabras); assertEquals(10, t.meta); assertEquals(0, t.porMinuto)
        val h = s.historial(o.pid)
        assertEquals(1, h.size); assertEquals(8, h[0].palabras)
        assertTrue(s.historial("otra").isEmpty())
        // En la cuenta, con el formato de nube/ESQUEMA.md (ningún campo vacío como null)
        val n = f.coleccion("sprints").nodo(s.ref(o.pid, desde))!!
        assertEquals(setOf("ref", "desde", "hasta", "min", "meta", "pal", "dia", "base", "dev", "fin"), n.campos.keys)
        assertEquals(o.pid, n.texto("ref")); assertEquals(desde, n.entero("desde")); assertEquals(12L, n.entero("min")); assertEquals(8L, n.entero("pal"))
        assertEquals(10L, n.entero("meta")); assertEquals(ahora, n.entero("fin")); assertEquals(Escritos.hoy(), n.entero("dia")); assertEquals(5L, n.entero("base"))
        assertTrue(n.campos.values.none { it == null })
    }

    @Test fun sigueTrasCerrarLaAppYLoTerminaElDispositivoDondeEmpezo() {
        val f = sinRed(); var s = motor(f, "cobalto-movil"); val o = obra("e")
        s.empieza(o.e, o.pid, 10, 0)
        // «Se cierra la app»: lo de memoria se olvida y, al volver antes de la hora, sigue en marcha (sale de la cuenta)
        ahora += 5 * MIN
        s = motor(f, "cobalto-movil"); s.mira(o.e)
        assertTrue(s.activo(o.pid)); assertEquals(10, s.minutos)
        o.e.escribe(o.a, "Uno dos tres cuatro cinco seis siete ocho nueve diez once doce.")
        // Pasa su hora con todo cerrado: otro dispositivo que se abre no lo termina (no sabe lo que se escribió allí)…
        ahora += 20 * MIN
        val otro = motor(f, "cobalto-tableta"); otro.mira(o.e)
        assertFalse(otro.activo()); assertNull(otro.terminado); assertTrue(otro.historial().isEmpty())
        // …y el suyo sí, al abrirse: con sus 10 minutos
        s = motor(f, "cobalto-movil"); s.mira(o.e)
        assertFalse(s.activo())
        val t = s.terminado!!
        assertEquals(10, t.minutos); assertEquals(12, t.palabras); assertEquals(1, t.porMinuto)
        assertEquals(1, s.historial().size); assertEquals(1, otro.historial().size)
    }

    @Test fun unSprintDeMenosDeUnMinutoNoQuedaEnElHistorial() {
        val f = sinRed(); val s = motor(f, "cobalto-movil"); val o = obra("e")
        s.empieza(o.e, o.pid, 25, 0)
        val desde = ahora
        ahora += 10_000L
        s.termina(o.e, ya = true)
        assertNull(s.terminado)
        assertTrue(s.historial().isEmpty())
        // (en la cuenta queda quitado, con su hora en «b»)
        assertTrue(f.coleccion("sprints").nodo(s.ref(o.pid, desde))!!.borrado)
        // Terminar sin uno en marcha no hace nada
        s.termina(o.e, ya = true)
        assertTrue(s.historial().isEmpty())
    }

    @Test fun elHistorialYaNoSeRecortaATodosLosDeLaCuenta() {
        val f = sinRed(); val s = motor(f, "cobalto-movil"); val o = obra("e")
        repeat(205) { i ->
            s.empieza(o.e, o.pid, 5, 0)
            ahora += 5 * MIN + 1
            s.termina(o.e)
            ahora += i.toLong()
        }
        assertEquals(205, s.historial().size)
    }

    // ------------------------------------------------------------------ Con la cuenta de verdad (el emulador)

    @Test fun losDeAntesDeDosDispositivosSeJuntanEnLaCuentaSinPerderNiRepetirNinguno() {
        Emulador.limpia(); Emulador.cuenta("gonzalo")
        val movil = Emulador.dispositivo("gonzalo"); val pc = Emulador.dispositivo("gonzalo")
        val m = motor(movil, "cobalto-movil"); val p = motor(pc, "cobalto-pc")
        // Hasta que no se ha bajado lo que ya hay en la cuenta, no se pasa nada (para no mandar lo que ya está)
        assertFalse(m.listoParaMigrar())
        movil.coleccion("sprints"); pc.coleccion("sprints")
        assertTrue(movil.sincroniza().ok); assertTrue(pc.sincroniza().ok)
        assertTrue(m.listoParaMigrar()); assertTrue(p.listoParaMigrar())
        val d = ahora - 10 * 86_400_000L
        val comun = d + 9 * 3_600_000L
        // Lo que cada uno guardaba aparte (el formato de antes, el mismo en los dos); uno está en los dos (una copia de la carpeta, por ejemplo)
        val delMovil = JSONArray().put(viejo("p_a", d, 25, 600, 500)).put(viejo("p_a", d + 3_600_000L, 10, 150, 0)).put(viejo("p_b", d + 7_200_000L, 45, 900, 1000))
            .put(viejo("p_a", comun, 20, 300, 0))
        val delPc = JSONArray().put(viejo("p_a", d + 86_400_000L, 15, 200, 0)).put(viejo("p_b", d + 2 * 86_400_000L, 30, 480, 0)).put(viejo("p_a", comun, 20, 300, 0))
            // (uno de menos de un minuto no estaba en el historial: no se pasa)
            .put(viejo("p_a", d + 3 * 86_400_000L, 0, 0, 0))
        // Y el ordenador tenía uno en marcha
        val enMarcha = JSONObject().put("pid", "p_b").put("desde", ahora - 5 * MIN).put("hasta", ahora + 20 * MIN).put("base", 40).put("dia", 20_000).put("meta", 0).put("min", 25)
        assertEquals(4, m.migra(delMovil, null))
        assertTrue(movil.sincroniza().ok); assertTrue(pc.sincroniza().ok)
        // El ordenador ya ve los del móvil: el común no se repite
        assertEquals(3, p.migra(delPc, enMarcha))
        assertTrue(pc.sincroniza().ok); assertTrue(movil.sincroniza().ok)
        // Volver a abrir no pasa nada otra vez
        assertEquals(0, m.migra(delMovil, null)); assertEquals(0, p.migra(delPc, enMarcha))
        // Los dos (y un tercero que se estrena) ven los mismos seis terminados, cada uno una vez y con lo suyo
        val tableta = Emulador.dispositivo("gonzalo").also { it.coleccion("sprints"); assertTrue(it.sincroniza().ok) }
        for (x in listOf(m, p, motor(tableta, "cobalto-tableta"))) {
            val h = x.historial()
            assertEquals(6, h.size)
            assertEquals(6, h.map { it.pid to it.desde }.toSet().size)
            assertEquals(600 + 150 + 900 + 300 + 200 + 480, h.sumOf { it.palabras })
            assertEquals(25 + 10 + 45 + 20 + 15 + 30, h.sumOf { it.minutos })
            assertEquals(4, x.historial("p_a").size)
        }
        // En la cuenta: siete (el que estaba en marcha, también), cada uno con la obra y su hora de empezar
        val uid = movil.sesion.uid
        val todo = Emulador.lee("/users/$uid/familia/sprints").comoMapa()
        assertEquals(7, todo.size)
        val n = todo["p_a~$d"].comoMapa()
        assertEquals("p_a", n["ref"]); assertEquals(25L, (n["min"] as Number).toLong()); assertEquals(600L, (n["pal"] as Number).toLong()); assertEquals(500L, (n["meta"] as Number).toLong())
        assertEquals(d + 25 * MIN, (n["fin"] as Number).toLong()); assertEquals("cobalto-movil", n["dev"])
        assertTrue("cada campo con su hora (m) y la del servidor (u)", n["m"].comoMapa().keys.containsAll(listOf("ref", "desde", "min", "pal", "meta", "fin")) && n["u"] is Number)
        // (el que estaba en los dos, una vez: el primero que lo pasó)
        assertEquals("cobalto-movil", todo["p_a~$comun"].comoMapa()["dev"])
        assertEquals("cobalto-pc", todo["p_b~${d + 2 * 86_400_000L}"].comoMapa()["dev"])
        // El que estaba en marcha en el ordenador se ve en marcha en el móvil (y en el ordenador)
        val o = obra("e")
        m.mira(o.e); p.mira(o.e)
        assertTrue(m.activo("p_b")); assertTrue(p.activo("p_b")); assertEquals(ahora + 20 * MIN, m.hasta)
    }

    @Test fun unSprintEmpezadoEnUnSitioSeVeEnElOtroYAcabaDondeLoTermines() {
        Emulador.limpia(); Emulador.cuenta("gonzalo")
        val movil = Emulador.dispositivo("gonzalo"); val pc = Emulador.dispositivo("gonzalo")
        val m = motor(movil, "cobalto-movil"); val p = motor(pc, "cobalto-pc")
        val om = obra("movil"); val op = obra("pc")
        m.empieza(om.e, om.pid, 25, 300)
        assertTrue(movil.sincroniza().ok); pc.coleccion("sprints"); assertTrue(pc.sincroniza().ok)
        // El ordenador lo ve en marcha: la misma obra, la misma hora de acabar, la misma meta
        p.mira(op.e)
        assertTrue(p.activo(om.pid)); assertEquals(m.hasta, p.hasta); assertEquals(300, p.meta)
        // Se termina en el ordenador, a los 10 minutos
        ahora += 10 * MIN
        p.termina(op.e, ya = true)
        assertEquals(10, p.terminado!!.minutos)
        assertTrue(pc.sincroniza().ok); assertTrue(movil.sincroniza().ok)
        // El móvil, que seguía con él, al ponerse al día lo deja (sin enseñar otra vez el resultado)
        m.mira(om.e)
        assertFalse(m.activo()); assertNull(m.terminado)
        assertEquals(listOf(10), m.historial().map { it.minutos })
    }

    @Test fun aSuHoraGanaElPrimeroQueLlegaYTerminarloAManoGanaSiempre() {
        Emulador.limpia(); Emulador.cuenta("gonzalo")
        val movil = Emulador.dispositivo("gonzalo"); val pc = Emulador.dispositivo("gonzalo")
        val m = motor(movil, "cobalto-movil"); val p = motor(pc, "cobalto-pc")
        val om = obra("movil"); val op = obra("pc")
        movil.coleccion("sprints"); pc.coleccion("sprints")
        // Los dos abiertos cuando se cumple la hora: cada uno lo termina con lo que ha visto escribir
        m.empieza(om.e, om.pid, 15, 0)
        val primero = ahora
        assertTrue(movil.sincroniza().ok); assertTrue(pc.sincroniza().ok); p.mira(op.e)
        om.e.escribe(om.a, "Cinco palabras en el móvil.")
        ahora += 15 * MIN
        m.termina(om.e); p.termina(op.e)
        assertEquals(5, m.terminado!!.palabras); assertEquals(0, p.terminado!!.palabras)
        // Llega antes el móvil: lo del ordenador (con la misma hora) no cambia nada, y los dos se quedan con lo del móvil
        assertTrue(movil.sincroniza().ok); assertTrue(pc.sincroniza().ok); assertTrue(movil.sincroniza().ok)
        for (x in listOf(m, p)) assertEquals(listOf(5), x.historial().filter { it.desde == primero }.map { it.palabras })
        // Otro: el ordenador lo termina a mano a los 5 minutos, y el móvil, sin red, a su hora. Aunque llegue antes lo del móvil, gana lo hecho a mano
        ahora += MIN
        m.empieza(om.e, om.pid, 20, 0)
        val segundo = ahora
        assertTrue(movil.sincroniza().ok); assertTrue(pc.sincroniza().ok); p.mira(op.e)
        ahora += 5 * MIN; p.termina(op.e, ya = true)
        ahora += 15 * MIN; m.termina(om.e)
        assertTrue(movil.sincroniza().ok); assertTrue(pc.sincroniza().ok); assertTrue(movil.sincroniza().ok)
        for (x in listOf(m, p)) assertEquals(listOf(5), x.historial().filter { it.desde == segundo }.map { it.minutos })
    }
}
