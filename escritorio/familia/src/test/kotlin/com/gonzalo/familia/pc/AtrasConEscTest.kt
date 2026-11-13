package com.gonzalo.familia.pc

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.gonzalo.familia.pc.mov.atrasConEsc
import org.junit.Assert.assertEquals
import org.junit.Test

/** «Atrás» de Thot, Bastet y Jonsu: Esc vuelve cuando nada de dentro lo usa, y no le quita el Esc a lo de dentro (el diseñador, el editor). */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class AtrasConEscTest {
    private fun tecla(k: Key, meta: Boolean = false) = KeyEvent(k, KeyEventType.KeyDown, isMetaPressed = meta)

    private fun escena(dentroUsaEsc: Boolean, alAtras: () -> Boolean): ImageComposeScene = ImageComposeScene(400, 300) {
        val dentro = remember { FocusRequester() }
        Box(Modifier.fillMaxSize().atrasConEsc(alAtras)) {
            if (dentroUsaEsc) {
                // como el diseñador de Jonsu o el editor de Thot: con el foco, y el Esc es suyo
                Box(Modifier.size(50.dp).focusRequester(dentro).onPreviewKeyEvent { it.type == KeyEventType.KeyDown && it.key == Key.Escape }.focusable())
                LaunchedEffect(Unit) { dentro.requestFocus() }
            }
        }
    }

    @Test fun escVuelveSiNadieLoUsa() {
        var veces = 0
        val s = escena(false) { veces++; true }
        repeat(3) { s.render(it * 16_000_000L) }
        s.sendKeyEvent(tecla(Key.Escape))
        assertEquals(1, veces)
        s.sendKeyEvent(tecla(Key.LeftBracket, meta = true))
        assertEquals("⌘[ también vuelve", 2, veces)
        s.close()
    }

    @Test fun noLeQuitaElEscALoDeDentro() {
        var veces = 0
        val s = escena(true) { veces++; true }
        repeat(3) { s.render(it * 16_000_000L) }
        s.sendKeyEvent(tecla(Key.Escape))
        assertEquals(0, veces)
        s.close()
    }
}
