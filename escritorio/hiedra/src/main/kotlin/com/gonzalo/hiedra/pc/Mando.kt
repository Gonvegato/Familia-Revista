package com.gonzalo.hiedra.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.pc.Sitio
import java.awt.KeyboardFocusManager
import java.io.File
import javax.swing.SwingUtilities
import org.lwjgl.sdl.SDLError.SDL_GetError
import org.lwjgl.sdl.SDLEvents.*
import org.lwjgl.sdl.SDLGamepad.*
import org.lwjgl.sdl.SDLHints.*
import org.lwjgl.sdl.SDLInit.*
import org.lwjgl.sdl.SDL_Event

/**
 * Los mandos, con SDL 3 (por LWJGL): el de Xbox, el de PlayStation, el de Switch y cualquiera que SDL conozca, enchufados o por Bluetooth,
 * también si se conectan con Hiedra ya abierta. Se leen en un hilo aparte (solo el subsistema de mandos de SDL, sin ventanas: así vale
 * también en el Mac, donde las ventanas de SDL tendrían que ir en el hilo principal) y cada pulsación llega a la pantalla como una
 * [Accion]. Mientras la ventana de Hiedra no está delante (estás jugando), el mando no la toca.
 *
 * Los botones van por su sitio, no por su letra: el de abajo pulsa, el de la derecha vuelve (en el de Switch, «B» pulsa y «A» vuelve, como
 * en Steam). Lo que se enseña en pantalla son los dibujos del mando que estás usando.
 *
 * Si SDL no se puede cargar (otro procesador, un sistema muy viejo), Hiedra sigue con el teclado y el ratón.
 */
object Mando {
    enum class Tipo { XBOX, PLAYSTATION, NINTENDO, GENERICO }
    class Conectado(val id: Int, val nombre: String, val tipo: Tipo)

    /** Los mandos conectados ahora. */
    var mandos by mutableStateOf<List<Conectado>>(emptyList())
        private set
    /** El tipo del último mando que se ha usado (para los dibujos de los botones). */
    var tipo by mutableStateOf(Tipo.XBOX)
        private set
    /** Cómo va («Listo», o por qué no hay mando). */
    var estado by mutableStateOf("Mirando…")
        private set
    /** El stick derecho: desplazar (px por paso, con su signo); se llama en el hilo de la pantalla. */
    @Volatile var alDesplazar: ((Float) -> Unit)? = null

    @Volatile private var activo = false

    /** ¿Está delante alguna ventana de Hiedra? (si no, el mando no hace nada aquí) */
    fun ventanaActiva(): Boolean = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow != null

    fun inicia() {
        if (activo) return
        activo = true
        Thread({ runCatching { bucle() }.onFailure { e -> estado = "No se ha podido usar el mando: ${e.message ?: e.javaClass.simpleName}" } }, "hiedra-mando")
            .apply { isDaemon = true; start() }
    }

    fun para() { activo = false }

    private fun enPantalla(que: () -> Unit) = SwingUtilities.invokeLater(que)

    /** El tipo de un mando, por lo que dice SDL. */
    private fun tipoDe(t: Int): Tipo = when (t) {
        SDL_GAMEPAD_TYPE_XBOX360, SDL_GAMEPAD_TYPE_XBOXONE -> Tipo.XBOX
        SDL_GAMEPAD_TYPE_PS3, SDL_GAMEPAD_TYPE_PS4, SDL_GAMEPAD_TYPE_PS5 -> Tipo.PLAYSTATION
        SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_PRO, SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_LEFT, SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_RIGHT,
        SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_PAIR, SDL_GAMEPAD_TYPE_GAMECUBE -> Tipo.NINTENDO
        else -> Tipo.GENERICO
    }

    private fun bucle() {
        // Las bibliotecas nativas de LWJGL, en la carpeta de Hiedra (no en /tmp, que puede no dejar ejecutar)
        runCatching { org.lwjgl.system.Configuration.SHARED_LIBRARY_EXTRACT_PATH.set(File(Sitio.de("hiedra"), "nativas").absolutePath) }
        SDL_SetHint(SDL_HINT_APP_NAME, "Hiedra")
        // Que SDL no se quede con las señales del proceso (las de la JVM) y que lea los mandos aunque no tenga ventana delante
        SDL_SetHint(SDL_HINT_NO_SIGNAL_HANDLERS, "1")
        SDL_SetHint(SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1")
        if (!SDL_Init(SDL_INIT_GAMEPAD)) {
            estado = "SDL no ha arrancado: " + (SDL_GetError() ?: "")
            return
        }
        estado = "Listo"
        val abiertos = LinkedHashMap<Int, Long>()                       // id → el mando abierto
        val ejes = HashMap<Int, IntArray>()                            // id → sus ejes (los seis de SDL)
        val pulsados = HashMap<Int, MutableSet<Int>>()                 // id → botones pulsados
        val repes = Accion.entries.filter { it.direccion }.associateWith { Repeticion() }
        val gatillos = HashMap<Int, BooleanArray>()
        val ARR = SDL_GAMEPAD_BUTTON_DPAD_UP; val ABA = SDL_GAMEPAD_BUTTON_DPAD_DOWN; val IZQ = SDL_GAMEPAD_BUTTON_DPAD_LEFT; val DER = SDL_GAMEPAD_BUTTON_DPAD_RIGHT
        val LX = SDL_GAMEPAD_AXIS_LEFTX; val LY = SDL_GAMEPAD_AXIS_LEFTY; val RY = SDL_GAMEPAD_AXIS_RIGHTY
        val LT = SDL_GAMEPAD_AXIS_LEFT_TRIGGER; val RT = SDL_GAMEPAD_AXIS_RIGHT_TRIGGER
        val botones = mapOf(SDL_GAMEPAD_BUTTON_SOUTH to Accion.A, SDL_GAMEPAD_BUTTON_EAST to Accion.B, SDL_GAMEPAD_BUTTON_WEST to Accion.X, SDL_GAMEPAD_BUTTON_NORTH to Accion.Y,
            SDL_GAMEPAD_BUTTON_LEFT_SHOULDER to Accion.LB, SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER to Accion.RB, SDL_GAMEPAD_BUTTON_START to Accion.MENU, SDL_GAMEPAD_BUTTON_BACK to Accion.VISTA)

        fun publica() {
            val l = abiertos.map { (id, h) -> Conectado(id, SDL_GetGamepadName(h) ?: "Mando", tipoDe(SDL_GetGamepadType(h))) }
            enPantalla { mandos = l; if (l.isNotEmpty() && tipo == Tipo.XBOX) tipo = l.last().tipo }
        }
        fun usa(id: Int) { val h = abiertos[id] ?: return; val t = tipoDe(SDL_GetGamepadType(h)); if (t != tipo) enPantalla { tipo = t } }
        fun manda(a: Accion) = enPantalla { if (ventanaActiva()) Entrada.despacha(a, deMando = true) }

        val e = SDL_Event.malloc()
        var sinMandosDesde = System.currentTimeMillis()
        try {
            while (activo) {
                while (SDL_PollEvent(e)) {
                    when (e.type()) {
                        SDL_EVENT_GAMEPAD_ADDED -> { val id = e.gdevice().which(); if (id !in abiertos) { val h = SDL_OpenGamepad(id); if (h != 0L) { abiertos[id] = h; ejes[id] = IntArray(8); pulsados[id] = HashSet(); gatillos[id] = BooleanArray(2); publica() } } }
                        SDL_EVENT_GAMEPAD_REMOVED -> { val id = e.gdevice().which(); abiertos.remove(id)?.let { SDL_CloseGamepad(it) }; ejes.remove(id); pulsados.remove(id); publica() }
                        SDL_EVENT_GAMEPAD_BUTTON_DOWN -> {
                            val id = e.gbutton().which(); val b = e.gbutton().button().toInt()
                            pulsados[id]?.add(b); usa(id)
                            botones[b]?.let { manda(it) }
                        }
                        SDL_EVENT_GAMEPAD_BUTTON_UP -> { pulsados[e.gbutton().which()]?.remove(e.gbutton().button().toInt()) }
                        SDL_EVENT_GAMEPAD_AXIS_MOTION -> { val id = e.gaxis().which(); val a = e.gaxis().axis().toInt(); ejes[id]?.let { if (a in it.indices) it[a] = e.gaxis().value().toInt() } }
                    }
                }
                val ahora = System.currentTimeMillis()
                // Direcciones: la cruceta o el stick izquierdo (con un margen para que no se mueva solo), repetidas al mantenerlas
                fun hacia(boton: Int, eje: Int, signo: Int) = abiertos.keys.any { id ->
                    (pulsados[id]?.contains(boton) == true) || (ejes[id]?.getOrNull(eje)?.let { v -> v * signo > 18000 } == true)
                }
                val dirs = mapOf(Accion.ARRIBA to hacia(ARR, LY, -1), Accion.ABAJO to hacia(ABA, LY, 1), Accion.IZQUIERDA to hacia(IZQ, LX, -1), Accion.DERECHA to hacia(DER, LX, 1))
                for ((a, si) in dirs) if (repes.getValue(a).paso(si, ahora)) manda(a)
                // Gatillos: letra anterior / siguiente
                for ((id, g) in gatillos) {
                    val l = (ejes[id]?.getOrNull(LT) ?: 0) > 16000; val r = (ejes[id]?.getOrNull(RT) ?: 0) > 16000
                    if (l && !g[0]) manda(Accion.LT); if (r && !g[1]) manda(Accion.RT)
                    g[0] = l; g[1] = r
                }
                // Stick derecho: desplazar la página, más deprisa cuanto más se empuja
                val ry = abiertos.keys.mapNotNull { ejes[it]?.getOrNull(RY) }.maxByOrNull { kotlin.math.abs(it) } ?: 0
                if (kotlin.math.abs(ry) > 8000) {
                    val v = (kotlin.math.abs(ry) - 8000) / 24767f
                    val px = Math.signum(ry.toFloat()) * Math.pow(v.toDouble().coerceIn(0.0, 1.0), 1.6).toFloat() * 34f
                    enPantalla { if (ventanaActiva()) { Entrada.con = ConQue.MANDO; alDesplazar?.invoke(px) } }
                }
                // En el Mac, sin ventana de SDL, los mandos conectados antes de arrancar a veces no se ven: se vuelve a mirar de vez en cuando
                if (abiertos.isEmpty()) {
                    if (Sitio.sistema == Sitio.Sistema.MAC && ahora - sinMandosDesde > 15_000) {
                        sinMandosDesde = ahora
                        SDL_QuitSubSystem(SDL_INIT_GAMEPAD)
                        SDL_InitSubSystem(SDL_INIT_GAMEPAD)
                    }
                } else sinMandosDesde = ahora
                // Más despacio con la ventana detrás (jugando)
                Thread.sleep(if (ventanaActiva()) 8 else 60)
            }
        } finally {
            e.free()
            abiertos.values.forEach { SDL_CloseGamepad(it) }
            SDL_Quit()
        }
    }
}
