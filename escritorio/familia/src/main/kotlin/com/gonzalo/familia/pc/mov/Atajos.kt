package com.gonzalo.familia.pc.mov

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable

/**
 * El teclado de la ventana: «atrás» (Esc, o ⌘[ en el Mac y Ctrl+[ en Linux) cierra lo de más arriba (el buscador, una ficha encima de
 * otra…) y los atajos de cada pantalla (⌘1…⌘9, ⌘F). Lo que se registra después (lo que está encima) se atiende antes.
 */
object Atajos {
    private val atras = ArrayList<() -> Boolean>()
    private val teclas = ArrayList<(KeyEvent) -> Boolean>()

    /** Lo llama la ventana con cada tecla; true si alguien la ha usado. */
    fun maneja(k: KeyEvent): Boolean {
        if (k.type != KeyEventType.KeyDown) return false
        val esAtras = k.key == Key.Escape || ((k.isMetaPressed || k.isCtrlPressed) && k.key == Key.LeftBracket)
        if (esAtras) { for (i in atras.indices.reversed()) if (atras[i]()) return true; return false }
        for (i in teclas.indices.reversed()) if (teclas[i](k)) return true
        return false
    }

    internal fun ponAtras(f: () -> Boolean) = atras.add(f)
    internal fun quitaAtras(f: () -> Boolean) = atras.remove(f)
    internal fun ponTeclas(f: (KeyEvent) -> Boolean) = teclas.add(f)
    internal fun quitaTeclas(f: (KeyEvent) -> Boolean) = teclas.remove(f)

    /** ⌘ en el Mac, Ctrl en Linux. */
    fun mando(k: KeyEvent): Boolean = if (System.getProperty("os.name").orEmpty().lowercase().contains("mac")) k.isMetaPressed else k.isCtrlPressed
}

/** «Atrás» mientras [enabled]: Esc (o ⌘[) llama a [alAtras]. */
@Composable
fun Atras(enabled: Boolean = true, alAtras: () -> Unit) {
    val f by rememberUpdatedState(alAtras)
    val e by rememberUpdatedState(enabled)
    DisposableEffect(Unit) {
        val h: () -> Boolean = { if (e) { f(); true } else false }
        Atajos.ponAtras(h)
        onDispose { Atajos.quitaAtras(h) }
    }
}

/** Los atajos de una pantalla mientras está: [maneja] devuelve true si la tecla era suya. */
@Composable
fun Teclas(maneja: (KeyEvent) -> Boolean) {
    val f by rememberUpdatedState(maneja)
    DisposableEffect(Unit) {
        val h: (KeyEvent) -> Boolean = { f(it) }
        Atajos.ponTeclas(h)
        onDispose { Atajos.quitaTeclas(h) }
    }
}

/**
 * «Atrás» para la raíz de una app que lleva su propia navegación (Thot, Bastet, Jonsu): Esc, o ⌘[ / Ctrl+[, cuando nada de dentro lo ha
 * usado antes (un campo, el editor, un diálogo, el diseñador). Por eso va en la fase normal del teclado, de dentro hacia fuera, y no en
 * la de la ventana: así no le quita el Esc a nadie. [alAtras] devuelve true si ha vuelto a algún sitio.
 */
@Composable
fun androidx.compose.ui.Modifier.atrasConEsc(alAtras: () -> Boolean): androidx.compose.ui.Modifier {
    val raiz = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val f by rememberUpdatedState(alAtras)
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { raiz.requestFocus() } }
    return this.then(androidx.compose.ui.Modifier.focusRequester(raiz).focusable().onKeyEvent { k ->
        k.type == KeyEventType.KeyDown && (k.key == Key.Escape || ((k.isMetaPressed || k.isCtrlPressed) && k.key == Key.LeftBracket)) && f()
    })
}
