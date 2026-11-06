package com.gonzalo.hiedra.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.event.KeyEvent.*

/**
 * Lo que se puede hacer con el mando (y con el teclado, que hace lo mismo): moverse, pulsar (A), volver (B), buscar (X), opciones (Y), la
 * sección anterior y la siguiente (LB, RB), saltar de letra (LT, RT), el menú (☰) y cambiar de modo (⧉).
 */
enum class Accion { ARRIBA, ABAJO, IZQUIERDA, DERECHA, A, B, X, Y, LB, RB, LT, RT, MENU, VISTA;
    val direccion: Boolean get() = this == ARRIBA || this == ABAJO || this == IZQUIERDA || this == DERECHA
}

/** Con qué se está manejando Hiedra ahora: el marco del foco se ve con el mando y el teclado y se va con el ratón. */
enum class ConQue { RATON, TECLADO, MANDO }

/**
 * La entrada de Hiedra: el mando, el teclado y el ratón llegan aquí y se convierten en [Accion]es para la pantalla de ahora ([manejador]).
 *
 *  - Teclado: flechas · Intro (A) · Esc o Retroceso (B) · / o F (X: buscar) · Espacio (Y: opciones) · RePág/AvPág o Q/E (LB/RB) ·
 *    Inicio/Fin (LT/RT: letra anterior/siguiente) · S o F10 (☰) · F11 (⧉: salir de la sala).
 *  - Steam abierto con su «configuración de escritorio» convierte el mando en teclas: entonces llegan a la vez el botón y su tecla. La misma
 *    acción dos veces en menos de [DOBLE] ms, una del mando y otra del teclado, cuenta una sola vez.
 */
object Entrada {
    var con by mutableStateOf(ConQue.RATON)
    /** Lo que hace cada acción en la pantalla de ahora (devuelve true si la ha usado). */
    @Volatile var manejador: ((Accion) -> Boolean)? = null

    const val DOBLE = 140L
    private var ultima: Pair<Accion, Long>? = null
    private var ultimaDeMando = false

    /** ¿Es repetida (la misma acción del otro origen, hace nada)? Aparte, para las pruebas. */
    fun repetida(a: Accion, deMando: Boolean, ahora: Long): Boolean {
        val u = ultima
        val rep = u != null && u.first == a && ultimaDeMando != deMando && ahora - u.second < DOBLE
        if (!rep) { ultima = a to ahora; ultimaDeMando = deMando }
        return rep
    }

    /** Llega una acción (en el hilo de la pantalla). */
    fun despacha(a: Accion, deMando: Boolean, ahora: Long = System.currentTimeMillis()): Boolean {
        if (repetida(a, deMando, ahora)) return true
        con = if (deMando) ConQue.MANDO else ConQue.TECLADO
        return manejador?.invoke(a) ?: false
    }

    /** La acción de una tecla (por su código de AWT, sin modificadores; [escribiendo]: buscando, las letras, el espacio y el retroceso son del texto). */
    fun deTecla(k: Int, escribiendo: Boolean): Accion? = when (k) {
        VK_UP, VK_KP_UP -> Accion.ARRIBA; VK_DOWN, VK_KP_DOWN -> Accion.ABAJO
        VK_LEFT, VK_KP_LEFT -> Accion.IZQUIERDA; VK_RIGHT, VK_KP_RIGHT -> Accion.DERECHA
        VK_ENTER -> Accion.A
        VK_ESCAPE -> Accion.B
        VK_BACK_SPACE -> if (escribiendo) null else Accion.B
        VK_PAGE_UP -> Accion.LB; VK_PAGE_DOWN -> Accion.RB
        VK_HOME -> Accion.LT; VK_END -> Accion.RT
        VK_F10 -> Accion.MENU; VK_F11 -> Accion.VISTA
        VK_SLASH, VK_F -> if (escribiendo) null else Accion.X
        VK_SPACE -> if (escribiendo) null else Accion.Y
        VK_Q -> if (escribiendo) null else Accion.LB
        VK_E -> if (escribiendo) null else Accion.RB
        VK_S -> if (escribiendo) null else Accion.MENU
        else -> null
    }

    /**
     * Una tecla de la ventana (de AWT, antes de que la vea Compose): si es de las nuestras, se despacha y no sigue. Escribiendo, lo que se
     * teclea va a [alEscribir] (un carácter, o «\b» para borrar). Las flechas mantenidas se repiten solas (lo hace el sistema).
     */
    fun teclaAwt(e: java.awt.event.KeyEvent, escribiendo: Boolean, alEscribir: (Char) -> Unit = {}): Boolean {
        if (e.isControlDown || e.isAltDown || e.isMetaDown) return false
        if (e.id == java.awt.event.KeyEvent.KEY_TYPED) {
            val c = e.keyChar
            // (la letra de una tecla que ya ha hecho algo —la F que abre la búsqueda— no se escribe)
            if (tragar) { tragar = false; return true }
            if (!escribiendo || c == java.awt.event.KeyEvent.CHAR_UNDEFINED) return false
            if (c == '\b') { alEscribir('\b'); return true }
            if (c.isLetterOrDigit() || c == ' ' || c in ":'-.&!?") { alEscribir(c); return true }
            return false
        }
        if (e.id == java.awt.event.KeyEvent.KEY_PRESSED) tragar = false
        val a = deTecla(e.keyCode, escribiendo) ?: return escribiendo && e.keyCode != VK_TAB
        if (e.id == java.awt.event.KeyEvent.KEY_PRESSED) { tragar = true; despacha(a, deMando = false) }
        return true
    }
    private var tragar = false
}

/**
 * Repetir una dirección mientras se mantiene (la cruceta o el stick): la primera al pulsar, después de [ESPERA] ms una cada [PASO] ms y,
 * pasado [RAPIDO] ms, más deprisa. Aparte, para las pruebas.
 */
class Repeticion {
    companion object { const val ESPERA = 380L; const val PASO = 95L; const val RAPIDO = 1500L; const val PASO_RAPIDO = 50L }
    private var desde = 0L
    private var ultima = 0L
    private var activa = false

    /** Con la dirección pulsada (o no) en [ahora]: ¿toca moverse? */
    fun paso(pulsada: Boolean, ahora: Long): Boolean {
        if (!pulsada) { activa = false; return false }
        if (!activa) { activa = true; desde = ahora; ultima = ahora; return true }
        val t = ahora - desde
        if (t < ESPERA) return false
        val cada = if (t > RAPIDO) PASO_RAPIDO else PASO
        if (ahora - ultima >= cada) { ultima = ahora; return true }
        return false
    }
}
