package com.gonzalo.cobalto.pc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.AspectoTexto
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.EdicionTexto
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.FormatoMarkdown
import com.gonzalo.cobalto.HistorialTexto
import com.gonzalo.cobalto.Tipografia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.delay

/**
 * Lo que estás escribiendo y aún no se ha guardado (se guarda a los 0,8 s de dejar de escribir, al cambiar de escena y al cerrar):
 * así cada tecla no es un cambio en la cuenta, y no se pierde nada.
 */
object Editor {
    @Volatile private var pendiente: Pair<String, String>? = null

    fun apunta(eid: String, texto: String) { pendiente = eid to texto }

    /** Guarda ya lo que hubiera pendiente. */
    fun guardaYa() {
        val (eid, texto) = pendiente ?: return
        pendiente = null
        val e = Cuaderno.escritos
        e.escribe(eid, texto)
        e.instantanea(eid, texto)
    }
}

/**
 * El editor de una escena: el Markdown ligero de «Escribir» pintado mientras escribes (los mismos colores y reglas que en el móvil,
 * EscribirTexto.kt), la tipografía automática («—», «…», comillas latinas) y deshacer y rehacer (⌘Z / Ctrl+Z; ⌘⇧Z / Ctrl+Y).
 * ⌘B / Ctrl+B negrita, ⌘I / Ctrl+I cursiva.
 */
@Composable
fun EditorEscena(esc: Doc, fichas: List<Doc>, modifier: Modifier = Modifier) {
    val e = Cuaderno.escritos
    var v by remember(esc.id) { mutableStateOf(TextFieldValue(esc.s("texto"), TextRange(esc.s("texto").length))) }
    val historial = remember(esc.id) { HistorialTexto(v) }
    var escribiendo by remember(esc.id) { mutableStateOf(0L) }
    // Lo que llega de fuera (otro dispositivo) mientras no escribes aquí, se ve
    LaunchedEffect(esc.s("texto")) {
        if (System.currentTimeMillis() - escribiendo > 3_000 && esc.s("texto") != v.text) v = TextFieldValue(esc.s("texto"), TextRange(minOf(v.selection.start, esc.s("texto").length)))
    }
    LaunchedEffect(escribiendo) { if (escribiendo > 0) { delay(800); Editor.guardaYa() } }
    DisposableEffect(esc.id) { onDispose { Editor.guardaYa() } }
    val nombres = fichas.flatMap { f -> listOf(f.s("nombre")) + f.s("alias").split(',') }.map { it.trim() }.filter { it.length >= 3 }
    val rxFichas = remember(nombres) { if (nombres.isEmpty()) null else Regex("\\b(" + nombres.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")\\b") }
    val aspecto = AspectoTexto(Charcoal, Muted.copy(alpha = 0.6f), Acento, Display, 20f)
    val foco = remember { FocusRequester() }
    fun cambia(n: TextFieldValue, apuntar: Boolean = true) {
        var nv = n
        // La tipografía automática, cuando se acaba de escribir un signo
        if (n.text.length == v.text.length + 1 && n.selection.collapsed) {
            val c = n.selection.start
            Tipografia.aplica(n.text, c, n.text[c - 1])?.let { (t, cur) -> historial.apunta(n); historial.corte(); nv = TextFieldValue(t, TextRange(cur)) }
        }
        if (apuntar) historial.apunta(nv)
        v = nv
        if (nv.text != esc.s("texto")) { Editor.apunta(esc.id, nv.text); escribiendo = System.currentTimeMillis() }
    }
    val scroll = rememberScrollState()
    Column(modifier.verticalScroll(scroll)) {
        BasicTextField(
            v, { cambia(it) },
            Modifier.fillMaxWidth().heightIn(min = 520.dp).focusRequester(foco).onPreviewKeyEvent { k ->
                if (k.type != KeyEventType.KeyDown || !(k.isCtrlPressed || k.isMetaPressed)) return@onPreviewKeyEvent false
                when {
                    k.key == Key.Z && !k.isShiftPressed -> { historial.deshace()?.let { cambia(it, false) }; true }
                    (k.key == Key.Z && k.isShiftPressed) || k.key == Key.Y -> { historial.rehace()?.let { cambia(it, false) }; true }
                    k.key == Key.B -> { cambia(EdicionTexto.envuelve(v, "**")); true }
                    k.key == Key.I -> { cambia(EdicionTexto.envuelve(v, "*")); true }
                    k.key == Key.S -> { Editor.guardaYa(); true }
                    else -> false
                }
            },
            textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontSize = 20.sp, lineHeight = 32.sp),
            cursorBrush = SolidColor(Acento),
            visualTransformation = FormatoMarkdown(aspecto, rxFichas),
            decorationBox = { inner ->
                if (v.text.isEmpty()) Text("Empieza aquí… (# título, > cita, *cursiva*, **negrita**, *** salto de escena)", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp)
                inner()
            }
        )
        Spacer(Modifier.height(80.dp))
    }
    LaunchedEffect(esc.id) { runCatching { foco.requestFocus() } }
}

/** Las palabras escritas hoy en esta escena (para enseñarlo mientras escribes). */
fun palabras(t: String) = Escritos.palabras(t)
