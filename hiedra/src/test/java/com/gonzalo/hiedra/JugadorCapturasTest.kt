package com.gonzalo.hiedra

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers

/**
 * Capturas del jugador interno: el mando táctil (en apaisado y en vertical) y el menú. El núcleo es código nativo de ARM y no corre en
 * el ordenador, así que el juego se ve como un fondo verde liso (JuegoActivity.sinMotor). Solo al grabar capturas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class JugadorCapturasTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun preparar() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Catalogo.cargar(ctx); Skin.cargar(ctx); Ajustes.cargar(ctx)
        Biblioteca.cargada = true; Biblioteca.juegos.clear(); Archivos.limpiarParaPruebas()
        val l = Lanzador.rom("gba", "Pokémon Esmeralda"); Archivos.poner(l.clave, "content://x/1", "")
        Biblioteca.juegos.add(Juego("r1", "Pokémon Esmeralda", lanzadores = listOf(l)))
        // Uno de cada mando (los de Lemuroid): SNES, PlayStation, N64, DS, Mega Drive y arcade
        for ((id, s, t) in listOf(Triple("s1", "snes", "Chrono Trigger"), Triple("p1", "psx", "Final Fantasy VII"), Triple("n1", "n64", "Super Mario 64"),
                Triple("d1", "nds", "Pokémon Platino"), Triple("m1", "megadrive", "Sonic the Hedgehog 2"), Triple("a1", "neogeo", "Metal Slug"))) {
            val x = Lanzador.rom(s, t); Archivos.poner(x.clave, "content://x/$id", "")
            Biblioteca.juegos.add(Juego(id, t, lanzadores = listOf(x)))
        }
        JuegoActivity.sinMotor = true
    }

    @After fun quitar() { JuegoActivity.sinMotor = false }

    private fun foto(nombre: String) {
        repeat(3) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(150) }
        captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    @Test fun apaisado() {
        RuntimeEnvironment.setQualifiers("+land")
        val a = Robolectric.buildActivity(JuegoActivity::class.java, JuegoActivity.intent(ctx, "r1")).setup().get()
        foto("40_jugador_tactil")
        a.abrirMenu(); foto("41_jugador_menu")
        a.mandoConectado = true; foto("41b_jugador_menu_con_mando"); a.mandoConectado = false
        a.pantallaMenu = "avance"; foto("42_jugador_avance")
        a.pantallaMenu = "mando"; foto("43_jugador_mando")
        a.pantallaMenu = "guardar"; foto("44_jugador_guardar")
        a.pantallaMenu = "imagen"; foto("45_jugador_imagen")
        a.pantallaMenu = "consola"; foto("45b_jugador_consola")
        a.cerrarMenu()
        // Con mando: el táctil se oculta y quedan las píldoras (MENÚ y ▶▶)
        a.mandoConectado = true; foto("46_jugador_con_mando")
    }

    @Test fun cadaConsolaConSuMando() {
        RuntimeEnvironment.setQualifiers("+land")
        for ((id, n) in listOf("s1" to "48_jugador_snes", "p1" to "49_jugador_psx", "n1" to "50_jugador_n64", "m1" to "51_jugador_megadrive", "a1" to "52_jugador_neogeo")) {
            val c = Robolectric.buildActivity(JuegoActivity::class.java, JuegoActivity.intent(ctx, id)).setup()
            foto(n)
            // La pantalla de carga y la de la BIOS que falta
            if (id == "s1") { c.get().fase = FaseJuego.Preparando("Bajando el núcleo Snes9x…", 0.4f); foto("48b_jugador_cargando") }
            if (id == "a1") { c.get().fase = FaseJuego.Error("FinalBurn Neo necesita la BIOS neogeo.zip (Neo Geo). Elige el archivo: Hiedra lo reconoce y lo deja donde lo busca.", "Elegir la BIOS" to {}); foto("52b_jugador_falta_bios") }
            c.pause().stop().destroy()
        }
    }

    @Test fun dsDePie() {
        RuntimeEnvironment.setQualifiers("+port")
        Robolectric.buildActivity(JuegoActivity::class.java, JuegoActivity.intent(ctx, "d1")).setup().get()
        foto("53_jugador_nds_vertical")
    }

    @Test fun vertical() {
        RuntimeEnvironment.setQualifiers("+port")
        Robolectric.buildActivity(JuegoActivity::class.java, JuegoActivity.intent(ctx, "r1")).setup().get()
        foto("47_jugador_vertical")
    }
}

/** El menú en pausa en la tablet tumbada (la OPPO Pad Mini: 1260 × 840 dp). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class JugadorCapturasTabletTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun preparar() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Catalogo.cargar(ctx); Skin.cargar(ctx); Ajustes.cargar(ctx)
        Biblioteca.cargada = true; Biblioteca.juegos.clear(); Archivos.limpiarParaPruebas()
        val l = Lanzador.rom("psx", "Final Fantasy VII"); Archivos.poner(l.clave, "content://x/1", "")
        Biblioteca.juegos.add(Juego("p1", "Final Fantasy VII", lanzadores = listOf(l)))
        JuegoActivity.sinMotor = true
    }

    @After fun quitar() { JuegoActivity.sinMotor = false }

    private fun foto(nombre: String) {
        repeat(3) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(150) }
        captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    @Test fun tablet() {
        val a = Robolectric.buildActivity(JuegoActivity::class.java, JuegoActivity.intent(ctx, "p1")).setup().get()
        foto("oppo_h_t6_jugador")
        a.abrirMenu(); a.mandoConectado = true; foto("oppo_h_t7_jugador_menu")
    }
}
