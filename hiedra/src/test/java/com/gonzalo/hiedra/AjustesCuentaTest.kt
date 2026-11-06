package com.gonzalo.hiedra

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Lo que eliges en Hiedra (el orden de cada sección, cómo se juega, el emulador de cada consola, las opciones de los núcleos, el PC, las
 * ROMs a kDrive, la web de imágenes, «actualizar sola») vive en tu cuenta, `ajustes/hiedra_*`, y llega a tus otros dispositivos ([otro]),
 * con la cuenta de verdad (el emulador de Firebase con las reglas de `nube/reglas.json`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AjustesCuentaTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var otro: Familia

    private fun espera(que: String, cond: () -> Boolean) {
        val hasta = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < hasta) { shadowOf(Looper.getMainLooper()).idle(); if (cond()) return; Thread.sleep(50) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(que, cond())
    }

    private fun entra() {
        Emulador.cuenta("gonzalo")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.configPruebas = Emulador.cfg
        EspejoCuenta.olvidaParaPruebas()
        Cuenta.cargar(ctx)
        runBlocking { Cuenta.entrar("gonzalo", "secreto1") }
        otro = Emulador.dispositivo("gonzalo")
        otro.coleccion("ajustes")
    }

    /** Arranca el espejo y espera a que lo de antes de este dispositivo esté en la cuenta. */
    private fun arranca() {
        Ajustes.cargar(ctx); AjustesJuego.cargar(ctx)
        EspejoCuenta.inicia(ctx, SincroP.GRUPOS)
        val hasta = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < hasta) {
            LaFamilia.sincronizaYa()
            if (EspejoCuenta.intenta(ctx)) break
            Thread.sleep(100)
        }
        assertTrue("lo de antes pasa a la cuenta", EspejoCuenta.intenta(ctx))
        sincroniza()
    }

    @Before fun prepara() { Emulador.limpia() }

    @After fun suelta() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null; EspejoCuenta.olvidaParaPruebas() }

    private fun sincroniza() { assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok); shadowOf(Looper.getMainLooper()).idle() }

    private fun enLaCuenta(nodo: String) = otro.coleccion("ajustes").nodo(nodo)

    @Test fun loQueEligesAquiLlegaAlOtroYAlReves() {
        entra(); arranca()
        // Aquí: el orden de Pendiente, cómo se juega (y un mando), el emulador de la GBA, una opción de su núcleo, el PC, la galería…
        OrdenGuardado.pon(ctx, Estado.PENDIENTE, Orden.Nombre)
        AjustesJuego.opacidad = 0.3f; AjustesJuego.filtro = AjustesJuego.Filtro.CRT; AjustesJuego.vibracion = false; AjustesJuego.guardar(ctx)
        AjustesJuego.ponMapa(ctx, "mando1", mapOf(96 to Mandos.Destino.Nada))
        Ajustes.emuladorPorSistema["gba"] = "mGBA"; Ajustes.portadasLibretro = false; Ajustes.guardar(ctx)
        Nucleos.ponOpcion(ctx, "gba", "mgba_solar_sensor_level", "5")
        EspejoCuenta.prefs(ctx, Suite.GRUPO).edit().putString("pc", "pc-salon").putBoolean("galeria", false).putString("shots", "[]").apply()
        NubeP.ponRomsSolas(ctx, false)
        EspejoCuenta.prefs(ctx, Internet.GRUPO).edit().putString("fuente", "pinterest").apply()
        Actualizador.ponActivo(ctx, false)
        sincroniza()
        assertEquals("Nombre", enLaCuenta("hiedra_orden")!!.texto("PENDIENTE"))
        enLaCuenta("hiedra_juego")!!.let { n ->
            assertEquals(0.3, n.decimal("opacidad"), 0.0001); assertEquals("CRT", n.texto("filtro")); assertEquals(false, n["vibracion"])
            assertTrue("lo que no has tocado no viaja", n["tamano"] == null)
            assertTrue("el mando, por su descriptor", n.texto("mapa:mando1").isNotBlank())
        }
        enLaCuenta("hiedra_ajustes")!!.let { n -> assertEquals("mGBA", n.texto("emu:gba")); assertEquals(false, n["libretro"]) }
        assertEquals("5", enLaCuenta("hiedra_nucleos")!!.texto("op:gba:mgba_solar_sensor_level"))
        enLaCuenta("hiedra_suite")!!.let { n -> assertEquals("pc-salon", n.texto("pc")); assertEquals(false, n["galeria"]); assertTrue("las capturas de aquí no viajan", n["shots"] == null) }
        assertEquals(false, enLaCuenta("hiedra_nube_p")!!["roms_solas"])
        assertEquals("pinterest", enLaCuenta("hiedra_internet")!!.texto("fuente"))
        assertEquals(false, enLaCuenta("hiedra_actualizador")!!["on"])

        // Otro dispositivo (otro móvil, o Hiedra del ordenador) cambia cosas: llegan aquí
        val a = otro.coleccion("ajustes")
        a.pon("hiedra_orden", mapOf("JUGANDO" to "Jugados"))
        a.pon("hiedra_juego", mapOf("tamano" to 1.4, "avVel" to 8L, "mapa:mando1" to ""))
        a.pon("hiedra_ajustes", mapOf("emu:gba" to "", "emu:snes" to "Snes9x", "libretro" to true))
        a.pon("hiedra_nucleos", mapOf("op:snes:snes9x_overclock" to "Light"))
        a.pon("hiedra_suite", mapOf("pc" to "pc-despacho"))
        a.pon("hiedra_nube_p", mapOf("roms_solas" to true))
        a.pon("hiedra_actualizador", mapOf("on" to true))
        sincroniza()
        espera("llega el orden") { OrdenGuardado.de(ctx, Estado.JUGANDO) == Orden.Jugados }
        assertEquals(Orden.Nombre, OrdenGuardado.de(ctx, Estado.PENDIENTE))
        espera("llega cómo se juega (y se vuelve a leer)") { AjustesJuego.tamano == 1.4f && AjustesJuego.avanceVelocidad == 8 }
        assertEquals(0.3f, AjustesJuego.opacidad); assertEquals(AjustesJuego.Filtro.CRT, AjustesJuego.filtro)
        assertTrue("el mando que se restableció allí, también aquí", AjustesJuego.mapa(ctx, "mando1").isEmpty())
        espera("llega el emulador de cada consola") { Ajustes.emuladorPorSistema == mapOf("snes" to "Snes9x") && Ajustes.portadasLibretro }
        assertEquals(mapOf("mgba_solar_sensor_level" to "5"), Nucleos.opcionesPropias(ctx, "gba"))
        assertEquals(mapOf("snes9x_overclock" to "Light"), Nucleos.opcionesPropias(ctx, "snes"))
        assertEquals("pc-despacho", EspejoCuenta.base(ctx, Suite.GRUPO).getString("pc", ""))
        assertTrue(NubeP.romsSolas(ctx)); assertTrue(Actualizador.activo(ctx))
    }

    @Test fun cambiosALaVezGanaElMasRecienteDeCadaAjuste() {
        entra(); arranca()
        val a = otro.coleccion("ajustes")
        val ahora = System.currentTimeMillis()
        // Los dos a la vez, sin verse: aquí la opacidad (ahora); allí la opacidad (hace un minuto) y la vibración
        AjustesJuego.opacidad = 0.4f; AjustesJuego.guardar(ctx)
        a.pon("hiedra_juego", mapOf("opacidad" to 0.9, "vibracion" to false), ahora - 60_000L)
        sincroniza()
        espera("de cada ajuste, el cambio más reciente") { AjustesJuego.vibracion == false }
        assertEquals(0.4f, AjustesJuego.opacidad)
        assertEquals(0.4, enLaCuenta("hiedra_juego")!!.decimal("opacidad"), 0.0001)
        // Y al revés: el de allí es más nuevo que el de aquí, aunque aquí se subiera antes
        AjustesJuego.opacidad = 0.7f; AjustesJuego.guardar(ctx)
        assertTrue(LaFamilia.sincronizaYa().ok)
        a.pon("hiedra_juego", mapOf("opacidad" to 0.2), System.currentTimeMillis() + 60_000L)
        sincroniza()
        espera("gana lo de allí, y aquí se ve") { AjustesJuego.opacidad == 0.2f }
        assertEquals(0.2, enLaCuenta("hiedra_juego")!!.decimal("opacidad"), 0.0001)
        assertEquals(false, AjustesJuego.vibracion)
    }

    @Test fun loDeAntesDeLosDosDispositivosSeJuntaSinPerderNada() {
        Emulador.cuenta("gonzalo")
        otro = Emulador.dispositivo("gonzalo")
        val antesAqui = System.currentTimeMillis() - 3_600_000L
        // El otro móvil ya pasó lo suyo: orden de Pendiente (hace dos horas) y de Pasado (hace un rato, después que este), su emulador de la
        // SNES y una opción de su núcleo
        val o = otro.coleccion("ajustes")
        o.pon("hiedra_orden", mapOf("PENDIENTE" to "Anadidos"), antesAqui - 3_600_000L)
        assertTrue(otro.sincroniza().ok)   // (si no, la cola junta los dos cambios del mismo nodo con la hora del último)
        o.pon("hiedra_orden", mapOf("PASADO" to "Nombre"), antesAqui + 600_000L)
        o.pon("hiedra_ajustes", mapOf("emu:snes" to "Snes9x"), antesAqui - 3_600_000L)
        o.pon("hiedra_nucleos", mapOf("op:snes:snes9x_overclock" to "Light"), antesAqui - 3_600_000L)
        assertTrue(otro.sincroniza().ok)
        // Este móvil tenía lo suyo de antes (con la versión que aún no lo subía): Pendiente y Pasado por otro orden, su emulador de la GBA
        // (en el formato de antes, todos juntos) y una opción del núcleo de la GBA
        ctx.getSharedPreferences("hiedra_orden", Context.MODE_PRIVATE).edit().putString("PENDIENTE", "Jugados").putString("PASADO", "Recientes").putString("JUGANDO", "Nombre").commit()
        ctx.getSharedPreferences("hiedra_ajustes", Context.MODE_PRIVATE).edit().putString("emus", JSONObject().put("gba", "mGBA").toString()).putBoolean("libretro", false).commit()
        ctx.getSharedPreferences("hiedra_nucleos", Context.MODE_PRIVATE).edit().putString("op:gba:mgba_solar_sensor_level", "3").commit()
        ctx.getSharedPreferences("espejo_cuenta", Context.MODE_PRIVATE).edit().apply { listOf("hiedra_orden", "hiedra_ajustes", "hiedra_nucleos").forEach { putLong("hora:$it", antesAqui) } }.commit()
        LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = Emulador.cfg; EspejoCuenta.olvidaParaPruebas()
        Cuenta.cargar(ctx)
        runBlocking { Cuenta.entrar("gonzalo", "secreto1") }
        arranca()
        // En la cuenta, lo de los dos: lo que solo tenía uno, tal cual; lo que tenían los dos, lo que se cambió más tarde
        val orden = enLaCuenta("hiedra_orden")!!
        assertEquals("de aquí, más nuevo que lo de allí", "Jugados", orden.texto("PENDIENTE"))
        assertEquals("de allí, más nuevo que lo de aquí", "Nombre", orden.texto("PASADO"))
        assertEquals("solo lo tenía este", "Nombre", orden.texto("JUGANDO"))
        enLaCuenta("hiedra_ajustes")!!.let { n -> assertEquals("mGBA", n.texto("emu:gba")); assertEquals("Snes9x", n.texto("emu:snes")); assertEquals(false, n["libretro"]) }
        enLaCuenta("hiedra_nucleos")!!.let { n -> assertEquals("3", n.texto("op:gba:mgba_solar_sensor_level")); assertEquals("Light", n.texto("op:snes:snes9x_overclock")) }
        // Y aquí también lo de los dos
        espera("aquí, lo del otro") { OrdenGuardado.de(ctx, Estado.PASADO) == Orden.Nombre && Ajustes.emuladorPorSistema == mapOf("gba" to "mGBA", "snes" to "Snes9x") }
        assertEquals(Orden.Jugados, OrdenGuardado.de(ctx, Estado.PENDIENTE))
        assertEquals(mapOf("snes9x_overclock" to "Light"), Nucleos.opcionesPropias(ctx, "snes"))
        assertTrue("el formato de antes ya no está", !EspejoCuenta.base(ctx, Ajustes.GRUPO).contains("emus"))
        // Lo que se cambia sin cuenta (o antes de abrirla) también llega, con su hora
        LaFamilia.reiniciaParaPruebas(); EspejoCuenta.olvidaParaPruebas()
        OrdenGuardado.pon(ctx, Estado.ABANDONADO, Orden.Nombre)
        LaFamilia.configPruebas = Emulador.cfg
        Cuenta.cargar(ctx)
        runBlocking { Cuenta.entrar("gonzalo", "secreto1") }
        arranca()
        assertEquals("Nombre", enLaCuenta("hiedra_orden")!!.texto("ABANDONADO"))
    }

    @Test fun laListaNegraSeLeeDeLaDeAzabacheEnLaCuenta() {
        entra()
        assertEquals(null, Negra.bajaDeCuenta())
        val az = otro.coleccion("azabache")
        az.pon("negra~1", mapOf("o" to JSONObject().put("id", 1).put("n", "Wallpaper Engine").put("updated", 1).toString(), "b" to 0L))
        az.pon("negra~2", mapOf("o" to JSONObject().put("id", 2).put("n", "Quitado").put("updated", 1).toString()))
        az.pon("diario~5", mapOf("o" to "{}"))
        assertTrue(otro.sincroniza().ok)
        az.borra("negra~2")
        assertTrue(otro.sincroniza().ok)
        assertEquals("lo que se quitó en Azabache ya no está vetado", listOf("Wallpaper Engine"), Negra.bajaDeCuenta())
    }

    @Test fun losNombresDeLosCamposSonLosDeLasPreferencias() {
        assertEquals("mapa:abc", EspejoCuenta.campo("mapa:abc"))
        assertEquals("op:gba:a%2Eb", EspejoCuenta.campo("op:gba:a.b"))
        assertEquals("op:gba:a.b", EspejoCuenta.clave(EspejoCuenta.campo("op:gba:a.b")))
        for (k in listOf("m", "u", "b", "a/b", "x%y", "\$#[]")) assertEquals(k, EspejoCuenta.clave(EspejoCuenta.campo(k)))
        assertEquals("a,b,c,d", EspejoCuenta.juntaComas("a,b,c", "c,d"))
        assertEquals("[1,2,3]", EspejoCuenta.juntaLista("[1,2]", "[2,3]"))
    }
}
