package com.gonzalo.hiedra.pc

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.familia.pc.VentanaFamilia
import java.awt.Frame
import java.awt.Rectangle
import java.io.File
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * La ventana de Hiedra para la sala: a pantalla completa (con el interruptor común de la familia, [VentanaFamilia.pantallaCompleta]: el
 * mismo que el de F11), apartarse al abrir un juego y volver delante al cerrarlo. La ventana la crea lo común (`appDeLaFamilia`); aquí se
 * maneja la misma, la que se ve desde dentro de la app.
 */
object VentanaHiedra {
    @Volatile var ventana: ComposeWindow? = null
    /** Dónde estaba la ventana antes de la sala (para guardarla así al cerrar, y no a pantalla completa). */
    @Volatile private var antes: Pair<Boolean, Rectangle>? = null
    /** Hiedra se ha apartado para dejar delante un juego. */
    @Volatile var apartada = false
        private set

    private fun enPantalla(que: () -> Unit) { if (SwingUtilities.isEventDispatchThread()) que() else SwingUtilities.invokeLater(que) }
    private fun luego(ms: Int, que: () -> Unit) = Timer(ms) { que() }.apply { isRepeats = false; start() }

    /** Entrar en la sala (pantalla completa) o salir de ella (como estaba). */
    fun pantallaCompleta(si: Boolean) = enPantalla {
        val w = ventana
        if (si && w != null && w.placement != WindowPlacement.Fullscreen) antes = (w.placement == WindowPlacement.Maximized) to Rectangle(w.bounds)
        VentanaFamilia.pantallaCompleta = si
        if (si && w != null) luego(200) { w.toFront(); w.requestFocus() }
    }

    /** Al abrir un juego desde la sala: Hiedra se aparta (se minimiza) para que el juego quede delante. */
    fun apartate() = enPantalla {
        val w = ventana ?: return@enPantalla
        apartada = true
        // (en el Mac, una ventana a pantalla completa no se puede minimizar: primero se sale)
        if (Sitio.sistema == Sitio.Sistema.MAC && VentanaFamilia.pantallaCompleta) { VentanaFamilia.pantallaCompleta = false; luego(900) { w.extendedState = w.extendedState or Frame.ICONIFIED } }
        else w.extendedState = w.extendedState or Frame.ICONIFIED
    }

    /** Al cerrar el juego: Hiedra vuelve delante (y a pantalla completa si estaba en la sala). */
    fun vuelve(enSala: Boolean) = enPantalla {
        val w = ventana ?: return@enPantalla
        if (!apartada) return@enPantalla
        w.extendedState = w.extendedState and Frame.ICONIFIED.inv()
        w.isVisible = true
        fun delante() { w.toFront(); w.requestFocus(); apartada = false }
        luego(300) {
            when {
                // (si al minimizarla dejó de estar a pantalla completa —según el sistema—, se vuelve a poner)
                enSala && VentanaFamilia.pantallaCompleta && w.placement != WindowPlacement.Fullscreen -> {
                    VentanaFamilia.pantallaCompleta = false
                    luego(150) { VentanaFamilia.pantallaCompleta = true; luego(300) { delante() } }
                }
                enSala && !VentanaFamilia.pantallaCompleta -> { VentanaFamilia.pantallaCompleta = true; luego(300) { delante() } }
                else -> delante()
            }
        }
    }

    /** Cierra Hiedra como si se cerrara la ventana (y así se guarda y se sube lo que falte). */
    fun cerrar() = enPantalla { ventana?.let { w -> w.dispatchEvent(java.awt.event.WindowEvent(w, java.awt.event.WindowEvent.WINDOW_CLOSING)) } }

    /**
     * Al cerrar Hiedra a pantalla completa: lo común de la familia ya ha guardado la ventana tal cual (del tamaño de la pantalla); se guarda
     * como estaba antes, para que la próxima vez se abra en su sitio (el mismo archivo y el mismo formato de `appDeLaFamilia`).
     */
    fun alCerrar() {
        val a = antes ?: return
        if (!VentanaFamilia.pantallaCompleta) return
        val m = linkedMapOf<String, Any?>("max" to a.first, "x" to a.second.x.toLong(), "y" to a.second.y.toLong(), "w" to a.second.width.toLong(), "h" to a.second.height.toLong())
        runCatching { File(Sitio.de("hiedra"), "ventana.json").writeText(Json.escribe(m)) }
    }
}
