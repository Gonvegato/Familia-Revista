package com.gonzalo.mostaza.pc

import com.gonzalo.familia.pc.Sitio
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.SwingUtilities
import org.lwjgl.sdl.SDLEvents.*
import org.lwjgl.sdl.SDLGamepad.*
import org.lwjgl.sdl.SDLHints.*
import org.lwjgl.sdl.SDLInit.*
import org.lwjgl.sdl.SDL_Event

/**
 * El mando en la ventana de Mostaza (el de Xbox, el de PlayStation, el de Switch y cualquiera que SDL conozca), con SDL 3 por LWJGL como en
 * Hiedra. Aquí no hace falta más que el teclado: cada botón es una tecla, la misma que harías tú (así el mando y el teclado van siempre
 * igual): la cruceta o el stick izquierdo son las flechas (mueven el foco), A es Intro (pulsa), B es Esc (vuelve), Y busca, los gatillos de
 * arriba cambian de sección y Start pone o quita la pausa de lo que se está viendo. Los botones van por su sitio, no por su letra.
 * Mientras la ventana de Mostaza no está delante (estás viendo algo en mpv, que lee el mando él mismo), el mando no la toca.
 * Si SDL no se puede cargar, Mostaza sigue con el teclado y el ratón.
 */
object Mando {
    @Volatile private var activo = false
    @Volatile var estado = "Mirando…"; private set

    /** Lo que se hace con cada botón: la tecla y si va con Ctrl (⌘ en el Mac). */
    enum class Tecla(val codigo: Int, val conMod: Boolean = false) {
        ARRIBA(KeyEvent.VK_UP), ABAJO(KeyEvent.VK_DOWN), IZQUIERDA(KeyEvent.VK_LEFT), DERECHA(KeyEvent.VK_RIGHT),
        PULSAR(KeyEvent.VK_ENTER), VOLVER(KeyEvent.VK_ESCAPE), BUSCAR(KeyEvent.VK_F, true),
        ANTERIOR(KeyEvent.VK_PAGE_UP, true), SIGUIENTE(KeyEvent.VK_PAGE_DOWN, true), PAUSA(KeyEvent.VK_SPACE),
    }

    fun inicia() {
        if (activo) return
        activo = true
        Thread({ runCatching { bucle() }.onFailure { e -> estado = "No se ha podido usar el mando: ${e.message ?: e.javaClass.simpleName}" } }, "mostaza-mando")
            .apply { isDaemon = true; start() }
    }

    fun para() { activo = false }

    /** Pulsa la tecla en lo que tiene el foco de la ventana de Mostaza (si está delante). */
    fun pulsa(t: Tecla) = SwingUtilities.invokeLater {
        if (t == Tecla.PAUSA) { Reproductor.alterna(); return@invokeLater }
        val c = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return@invokeLater
        val mod = if (!t.conMod) 0 else if (Sitio.sistema == Sitio.Sistema.MAC) KeyEvent.META_DOWN_MASK else KeyEvent.CTRL_DOWN_MASK
        val ahora = System.currentTimeMillis()
        val ch = if (t == Tecla.PULSAR) '\n' else KeyEvent.CHAR_UNDEFINED
        c.dispatchEvent(KeyEvent(c, KeyEvent.KEY_PRESSED, ahora, mod, t.codigo, ch))
        if (t == Tecla.PULSAR) c.dispatchEvent(KeyEvent(c, KeyEvent.KEY_TYPED, ahora, mod, KeyEvent.VK_UNDEFINED, '\n'))
        c.dispatchEvent(KeyEvent(c, KeyEvent.KEY_RELEASED, ahora + 30, mod, t.codigo, ch))
    }

    private fun ventanaActiva(): Boolean = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow != null

    /** Repetir una dirección al mantenerla (como el teclado): la primera al momento, luego tras 380 ms, y después cada 110 ms. */
    private class Repeticion { var desde = 0L; var ultima = 0L
        fun paso(si: Boolean, ahora: Long): Boolean {
            if (!si) { desde = 0L; return false }
            if (desde == 0L) { desde = ahora; ultima = ahora; return true }
            if (ahora - desde > 380 && ahora - ultima > 110) { ultima = ahora; return true }
            return false
        }
    }

    private fun bucle() {
        // Las bibliotecas nativas de LWJGL, en la carpeta de Mostaza (no en /tmp, que puede no dejar ejecutar)
        runCatching { org.lwjgl.system.Configuration.SHARED_LIBRARY_EXTRACT_PATH.set(File(Local.dir, "nativas").absolutePath) }
        SDL_SetHint(SDL_HINT_APP_NAME, "Mostaza")
        SDL_SetHint(SDL_HINT_NO_SIGNAL_HANDLERS, "1")
        SDL_SetHint(SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1")
        if (!SDL_Init(SDL_INIT_GAMEPAD)) { estado = "SDL no ha arrancado"; return }
        estado = "Listo"
        val abiertos = LinkedHashMap<Int, Long>()
        val ejes = HashMap<Int, IntArray>()
        val pulsados = HashMap<Int, MutableSet<Int>>()
        val repes = listOf(Tecla.ARRIBA, Tecla.ABAJO, Tecla.IZQUIERDA, Tecla.DERECHA).associateWith { Repeticion() }
        val botones = mapOf(SDL_GAMEPAD_BUTTON_SOUTH to Tecla.PULSAR, SDL_GAMEPAD_BUTTON_EAST to Tecla.VOLVER, SDL_GAMEPAD_BUTTON_NORTH to Tecla.BUSCAR,
            SDL_GAMEPAD_BUTTON_LEFT_SHOULDER to Tecla.ANTERIOR, SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER to Tecla.SIGUIENTE, SDL_GAMEPAD_BUTTON_START to Tecla.PAUSA)
        val e = SDL_Event.malloc()
        var sinMandosDesde = System.currentTimeMillis()
        try {
            while (activo) {
                while (SDL_PollEvent(e)) {
                    when (e.type()) {
                        SDL_EVENT_GAMEPAD_ADDED -> { val id = e.gdevice().which(); if (id !in abiertos) { val h = SDL_OpenGamepad(id); if (h != 0L) { abiertos[id] = h; ejes[id] = IntArray(8); pulsados[id] = HashSet() } } }
                        SDL_EVENT_GAMEPAD_REMOVED -> { val id = e.gdevice().which(); abiertos.remove(id)?.let { SDL_CloseGamepad(it) }; ejes.remove(id); pulsados.remove(id) }
                        SDL_EVENT_GAMEPAD_BUTTON_DOWN -> {
                            val id = e.gbutton().which(); val b = e.gbutton().button().toInt()
                            pulsados[id]?.add(b)
                            if (ventanaActiva()) botones[b]?.let { pulsa(it) }
                        }
                        SDL_EVENT_GAMEPAD_BUTTON_UP -> { pulsados[e.gbutton().which()]?.remove(e.gbutton().button().toInt()) }
                        SDL_EVENT_GAMEPAD_AXIS_MOTION -> { val id = e.gaxis().which(); val a = e.gaxis().axis().toInt(); ejes[id]?.let { if (a in it.indices) it[a] = e.gaxis().value().toInt() } }
                    }
                }
                val ahora = System.currentTimeMillis()
                fun hacia(boton: Int, eje: Int, signo: Int) = abiertos.keys.any { id ->
                    (pulsados[id]?.contains(boton) == true) || (ejes[id]?.getOrNull(eje)?.let { v -> v * signo > 18000 } == true)
                }
                val dirs = mapOf(Tecla.ARRIBA to hacia(SDL_GAMEPAD_BUTTON_DPAD_UP, SDL_GAMEPAD_AXIS_LEFTY, -1), Tecla.ABAJO to hacia(SDL_GAMEPAD_BUTTON_DPAD_DOWN, SDL_GAMEPAD_AXIS_LEFTY, 1),
                    Tecla.IZQUIERDA to hacia(SDL_GAMEPAD_BUTTON_DPAD_LEFT, SDL_GAMEPAD_AXIS_LEFTX, -1), Tecla.DERECHA to hacia(SDL_GAMEPAD_BUTTON_DPAD_RIGHT, SDL_GAMEPAD_AXIS_LEFTX, 1))
                for ((t, si) in dirs) if (repes.getValue(t).paso(si && ventanaActiva(), ahora)) pulsa(t)
                // En el Mac, sin ventana de SDL, los mandos conectados antes de arrancar a veces no se ven: se vuelve a mirar de vez en cuando
                if (abiertos.isEmpty()) {
                    if (Sitio.sistema == Sitio.Sistema.MAC && ahora - sinMandosDesde > 15_000) { sinMandosDesde = ahora; SDL_QuitSubSystem(SDL_INIT_GAMEPAD); SDL_InitSubSystem(SDL_INIT_GAMEPAD) }
                } else sinMandosDesde = ahora
                Thread.sleep(if (ventanaActiva()) 8 else 60)
            }
        } finally {
            e.free()
            abiertos.values.forEach { SDL_CloseGamepad(it) }
            SDL_Quit()
        }
    }
}
