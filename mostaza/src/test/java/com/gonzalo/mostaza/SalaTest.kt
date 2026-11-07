package com.gonzalo.mostaza

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La tele de las pruebas: 1920 × 1080 (960 × 540 dp a xhdpi, como una tele de verdad), en modo de pantalla de tele. */
const val TELE = "w960dp-h540dp-land-television-xhdpi"

var modoDePrueba: Any? = null
/** Como assertIsFocused, sin depender de si el nodo está fusionado con el de dentro. */
fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsFocusedSeguro() {
    val n = fetchSemanticsNode()
    val enfocado = n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Focused) { null } == true ||
        n.children.any { it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Focused) { null } == true }
    if (!enfocado) throw AssertionError("sin foco")
}

/** La biblioteca, los addons y las fuentes de mentira de las capturas (sin red ni cuenta), para la sala. */
fun siembraSala(ctx: Context): Escaparate {
    iniciar(ctx)
    val e = Escaparate(ctx)
    Addons.http = e
    Addons.guarda(e.addons)
    Tmdb.claveFija = null
    val ahora = System.currentTimeMillis(); val dia = 86_400_000L
    val p = e.pelis; val s = e.series
    Biblioteca.fijaParaPruebas(listOf(
        e.titulo(s[0], Estado.VIENDO, ahora - 60 * dia, ahora - 3_600_000L), e.titulo(p[1], Estado.VIENDO, ahora - 5 * dia, ahora - dia),
        e.titulo(p[0], Estado.PENDIENTE, ahora - 2 * dia), e.titulo(s[2], Estado.PENDIENTE, ahora - 3 * dia), e.titulo(p[5], Estado.PENDIENTE, ahora - 4 * dia),
        e.titulo(p[3], Estado.VISTO, ahora - 300 * dia).copy(fin = ahora - 200 * dia), e.titulo(p[4], Estado.VISTO, ahora - 280 * dia).copy(fin = ahora - 100 * dia),
        e.titulo(s[1], Estado.VISTO, ahora - 90 * dia).copy(fin = ahora - 20 * dia), e.titulo(s[4], Estado.ABANDONADO, ahora - 400 * dia),
    ), listOf(
        Progreso("tt0944947:2:5", "tt0944947", 2, 5, 34 * 60_000L, 57 * 60_000L, false, 0L, ahora - 3_600_000L),
        Progreso("tt0944947:2:4", "tt0944947", 2, 4, 57 * 60_000L, 57 * 60_000L, true, ahora - 86_400_000L, ahora - 86_400_000L),
        Progreso("tt0944947:1:1", "tt0944947", 1, 1, 57 * 60_000L, 57 * 60_000L, true, ahora - 30 * dia, ahora - 30 * dia),
        Progreso("tt15398776", "tt15398776", 0, 0, 72 * 60_000L, 180 * 60_000L, false, 0L, ahora - dia),
    ))
    coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
    return e
}

/**
 * La sala con el mando a distancia, de verdad (las teclas pasan por Compose como en la tele): se entra con el foco en lo primero de
 * «Seguir viendo», la cruceta va al siguiente, OK abre su ficha (con el foco en «Reproducir»), ATRÁS vuelve al mismo póster, arriba llega
 * a las secciones y OK en una la abre.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = TELE)
class SalaTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @After fun limpia() { Biblioteca.olvidaParaPruebas() }

    private fun espera(que: String, cond: () -> Boolean) {
        runCatching { rule.waitUntil(10_000) { cond() } }
        rule.waitForIdle()
        assertTrue(que, cond())
    }
    private fun tecla(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Test fun esUnaTele() {
        assertTrue(Sala.esTele(ctx))
        assertTrue(Sala.activa(ctx))
    }

    @Test fun conElMandoADistancia() {
        val e = siembraSala(ctx)
        val est = EstadoSala(); val foco = FocoSala()
        rule.setContent { MostazaTheme { SalaApp(est = est, foco = foco) } }
        val jdt = Biblioteca.titulos.first { it.id == e.series[0].id }.breve()
        val opp = Biblioteca.titulos.first { it.id == e.pelis[1].id }.breve()
        // Al entrar: lo primero de «Seguir viendo» (Juego de tronos: el episodio de hace una hora)
        espera("el foco empieza en Seguir viendo") { foco.actual == idCarta("viendo", jdt) }
        assertEquals("Juego de tronos", est.destacada?.nombre)
        // Derecha: Oppenheimer (y pasa al cartel de arriba)
        tecla(Key.DirectionRight)
        espera("la cruceta va al siguiente") { foco.actual == idCarta("viendo", opp) }
        assertEquals("Oppenheimer", est.destacada?.nombre)
        // OK: su ficha, con el foco en «Reproducir»
        tecla(Key.DirectionCenter)
        espera("OK abre la ficha") { est.pila.lastOrNull()?.id == opp.id }
        espera("en la ficha, el foco en Reproducir") { foco.actual == "f:reproducir" }
        assertTrue(rule.onAllNodesWithText("SEGUIR VIENDO", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        // En la ficha, la cruceta no se sale a lo de debajo
        repeat(3) { tecla(Key.DirectionUp) }
        assertTrue("el foco sigue en la ficha", foco.actual?.startsWith("f:") == true)
        // ATRÁS: vuelve al mismo póster
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        espera("ATRÁS cierra la ficha") { est.pila.isEmpty() }
        espera("y el foco vuelve a Oppenheimer") { foco.actual == idCarta("viendo", opp) }
        // Arriba: las secciones; a la derecha, Biblioteca; OK la abre
        repeat(2) { tecla(Key.DirectionUp) }
        espera("arriba están las secciones") { foco.actual?.startsWith("tab:") == true }
        while (foco.actual != "tab:BIBLIOTECA" && foco.actual != "tab:menu") tecla(if (foco.actual == "tab:INICIO") Key.DirectionRight else Key.DirectionLeft)
        assertEquals("tab:BIBLIOTECA", foco.actual)
        tecla(Key.DirectionCenter)
        espera("OK en Biblioteca la abre") { est.seccion == SeccionSala.BIBLIOTECA }
        // ATRÁS desde una sección: a Inicio
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        espera("ATRÁS vuelve a Inicio") { est.seccion == SeccionSala.INICIO }
    }

    /**
     * Una serie con el mando: en su ficha, bajando con la cruceta se llega a los episodios (la página se desplaza sola) y OK en uno lo
     * pone con la fuente que elige Mostaza (el mismo elector que en el móvil): se abre el reproductor.
     */
    @Test fun unEpisodioConElMando() {
        val e = siembraSala(ctx)
        val est = EstadoSala(); val foco = FocoSala()
        rule.setContent { MostazaTheme { SalaApp(est = est, foco = foco) } }
        espera("la sala arranca") { foco.actual != null }
        val jdt = Biblioteca.titulos.first { it.id == e.series[0].id }.breve()
        rule.runOnUiThread { ControlSala(est, foco).abreFicha(jdt) }
        espera("en la ficha, el foco en Reproducir") { foco.actual == "f:reproducir" }
        espera("con sus temporadas") { rule.onAllNodesWithText("TEMPORADA 2", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        var n = 0
        while (foco.actual?.startsWith("f:ep:") != true && n++ < 20) tecla(Key.DirectionDown)
        assertTrue("bajando se llega a un episodio (está en ${foco.actual})", foco.actual?.startsWith("f:ep:") == true)
        val app = org.robolectric.Shadows.shadowOf(ctx as android.app.Application)
        while (app.nextStartedActivity != null) { /* (lo de antes, fuera) */ }
        tecla(Key.DirectionCenter)
        var abierta: android.content.Intent? = null
        espera("OK en el episodio abre el reproductor") { abierta = abierta ?: app.nextStartedActivity; abierta != null }
        assertEquals(ReproductorActivity::class.java.name, abierta?.component?.className)
    }

    /**
     * El reproductor con el mando a distancia: con la primera tecla, el foco va a reproducir/pausa; bajando, a la barra, donde la cruceta
     * salta; ATRÁS cierra primero la lista de subtítulos.
     */
    @Test fun elReproductorConElMando() {
        val rep = EstadoReproductor().apply { titulo = "Juego de tronos"; reproduciendo = true; cargando = false; pos = 34 * 60_000L; dur = 57 * 60_000L }
        val saltos = ArrayList<Int>()
        var pausas = 0
        rule.setContent { modoDePrueba = androidx.compose.ui.platform.LocalInputModeManager.current; MostazaTheme { ControlesReproductor(rep, {}, { pausas++ }, { saltos += it }, {}, {}, {}, {}, {}, {}, {}, {}, {}, tele = true, salto = 15) } }
        rule.waitForIdle()
        // (en la tele no hay pantalla táctil: Android arranca sin el modo táctil, y así se ve el foco desde el principio. En las pruebas, las
        // teclas no cambian el modo solas, así que se pone a mano)
        @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
        rule.runOnUiThread { (modoDePrueba as androidx.compose.ui.input.InputModeManager).requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
        rule.waitForIdle()
        espera("el foco, en reproducir/pausa") { runCatching { rule.onNode(androidx.compose.ui.test.hasContentDescription("Pausa")).assertIsFocusedSeguro(); true }.getOrDefault(false) }
        tecla(Key.DirectionCenter)
        assertEquals(1, pausas)
        var n = 0
        while (n++ < 4 && runCatching { rule.onNode(androidx.compose.ui.test.hasContentDescription("Barra de tiempo"), useUnmergedTree = true).assertIsFocusedSeguro(); true }.getOrDefault(false).not()) tecla(Key.DirectionDown)
        tecla(Key.DirectionRight); tecla(Key.DirectionLeft); tecla(Key.DirectionLeft)
        assertEquals(listOf(15, -15, -15), saltos)
    }
}

/**
 * Capturas de la sala en la tele (solo con -Proborazzi.test.record=true): 1920 × 1080, en modo de tele. Se dejan en la carpeta de
 * `-Dcapturas` (o en build/outputs/roborazzi) como `mostaza-tele-<pantalla>.png`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = TELE)
class CapturasTeleTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val dir: String get() = System.getProperty("capturas")?.takeIf { it.isNotBlank() } ?: "build/outputs/roborazzi"

    @After fun limpia() { Biblioteca.olvidaParaPruebas(); Tmdb.claveFija = null }

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(900); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$dir/mostaza-tele-$nombre.png")
    }
    private fun espera(cond: () -> Boolean) { runCatching { rule.waitUntil(10_000) { cond() } }; rule.waitForIdle() }
    private fun esperaTexto(t: String) = espera { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tecla(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Test fun pantallas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        val e = siembraSala(ctx)
        Modo.eleccion = 2
        val est = EstadoSala(); val foco = FocoSala()
        val ctl = ControlSala(est, foco)
        val rep = EstadoReproductor().apply { titulo = "Juego de tronos"; subtitulo = "T2 · E5 · Sin vuelta"; fuente = "1080p · Castellano · 4,4 GB · Fuentes de casa"; reproduciendo = true; cargando = false; pos = 34 * 60_000L; dur = 57 * 60_000L }
        var cual by mutableIntStateOf(0)
        rule.setContent {
            MostazaTheme {
                if (cual == 0) SalaApp(est = est, foco = foco)
                else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A3F48), Color(0xFF0E1014))))) {
                    ControlesReproductor(rep, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, tele = true)
                }
            }
        }
        esperaTexto("Populares"); espera { foco.actual != null }
        foto("1-inicio")
        tecla(Key.DirectionDown); foto("2-inicio-pendientes")
        rule.runOnUiThread { ctl.seccion(SeccionSala.BIBLIOTECA) }; esperaTexto("Juego de tronos"); foto("3-biblioteca")
        rule.runOnUiThread { est.estadoBib = Estado.VISTO }; foto("4-biblioteca-visto")
        rule.runOnUiThread { ctl.seccion(SeccionSala.EXPLORAR) }; esperaTexto("Oppenheimer"); foto("5-explorar")
        rule.runOnUiThread { ctl.seccion(SeccionSala.BUSCAR); est.consulta = "du"; foco.pedido = "q:buscar" }; espera { foco.actual == "q:buscar" }
        tecla(Key.DirectionCenter); esperaTexto("Películas · Cinemeta"); foto("6-buscar")
        rule.runOnUiThread { ctl.seccion(SeccionSala.INICIO) }; esperaTexto("Seguir viendo")
        val dune = Biblioteca.titulos.first { it.id == e.pelis[0].id }.breve()
        rule.runOnUiThread { ctl.abreFicha(dune) }; esperaTexto("Paul Atreides"); foto("7-ficha-pelicula")
        rule.runOnUiThread { ctl.abreFuentes(PideFuentes(dune, null, null)) }; esperaTexto("Castellano"); foto("8-fuentes")
        rule.runOnUiThread { ctl.cierra("fuentes"); ctl.atras() }; espera { est.pila.isEmpty() }
        val jdt = Biblioteca.titulos.first { it.id == e.series[0].id }.breve()
        rule.runOnUiThread { ctl.abreFicha(jdt) }; esperaTexto("Temporada 2"); foto("9-ficha-serie")
        rule.runOnUiThread { ctl.abreMenu() }; esperaTexto("Addons"); foto("10-menu")
        rule.runOnUiThread { ctl.cierra("menu"); ctl.confirma(Confirmacion("¿Quitar «Juego de tronos»?", "Se quita de tu biblioteca en todos tus dispositivos y en Azabache. Es para siempre.", "Quitar") {}) }
        foto("11-quitar")
        rule.runOnUiThread { ctl.cierra("confirmar"); ctl.atras() }; espera { est.pila.isEmpty() }
        // Con el catálogo de TMDB (como si tuvieras tu clave puesta)
        TmdbDatos.imagen = { path, _ -> path }
        Tmdb.http = e; Tmdb.claveFija = "prueba"; Tmdb.guarda("prueba")
        esperaTexto("Tendencias"); foto("12-inicio-tmdb")
        rule.runOnUiThread { ctl.seccion(SeccionSala.EXPLORAR) }; esperaTexto("Plataforma"); foto("13-explorar-tmdb")
        Tmdb.claveFija = null
        // El reproductor en la tele
        cual = 1; foto("14-reproductor")
        rep.eleccion = Triple("Subtítulos", listOf("Sin subtítulos" to false, "Español" to true, "Inglés" to false, "English [CC]" to false)) { _ -> }
        foto("15-reproductor-subtitulos")
        rep.eleccion = null; rep.visibles = false; rep.siguiente = "T2 · E6 · La noche"; rep.cuentaAtras = 6
        foto("16-reproductor-siguiente")
    }
}
