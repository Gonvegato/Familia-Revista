// Copiado de familia/ (Android) por herramientas: lo mismo en el Mac. Lo de Android (vibrar, el gesto de atrás, las preferencias) cambia por lo del ordenador.
package com.gonzalo.familia.pc.mov

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Lo último que has buscado en cada app (en este dispositivo). */
object BusquedasRecientes {
    private val p by lazy { java.util.prefs.Preferences.userRoot().node("com/gonzalo/familia/busquedas") }
    fun de(c: Any?): List<String> = runCatching { p.get("r", "") }.getOrDefault("").split('\n').filter { it.isNotBlank() }
    fun apunta(c: Any?, q: String) {
        val t = q.trim(); if (t.length < 2) return
        runCatching { p.put("r", (listOf(t) + de(c).filterNot { it.equals(t, true) }).take(6).joinToString("\n")) }
    }
}

/**
 * El buscador de la familia: crece desde la palabra BUSCAR ([desde]) hasta ocupar la pantalla, con el campo arriba sobre la franja de
 * tinta y el teclado listo; antes de escribir, lo último que buscaste; al escribir, lo que pinta [resultados] (los de cada app). «Cerrar» o
 * Atrás lo recogen hacia la palabra, fundiéndose al llegar. [bloqueado]: si hay algo abierto encima (una ficha), Atrás es para eso.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapaBuscar(
    desde: () -> Rect?, apertura: Int, pista: String,
    papel: Color, tinta: Color, acento: Color, gris: Color, letra: FontFamily,
    bloqueado: Boolean = false, alCerrado: () -> Unit,
    resultados: @Composable (String) -> Unit,
) {
    val ctx = Unit
    val vista = Unit
    val foco = remember { FocusRequester() }
    val teclado = LocalFocusManager.current
    var texto by rememberSaveable(apertura) { mutableStateOf("") }
    val t = remember { Animatable(0f) }
    var cerrando by remember { mutableStateOf(false) }
    val origen = remember(apertura) { desde()?.let { Rect(it.left - 10f, it.top - 6f, it.right + 10f, it.bottom + 6f) } }
    LaunchedEffect(apertura) { t.snapTo(0f); t.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 260f)) }
    LaunchedEffect(apertura) { delay(140); runCatching { foco.requestFocus() } }
    LaunchedEffect(cerrando) {
        if (!cerrando) return@LaunchedEffect
        teclado.clearFocus(); Vibra.tic(vista)
        if (texto.isNotBlank()) BusquedasRecientes.apunta(ctx, texto)
        t.animateTo(0f, spring(dampingRatio = 1f, stiffness = 340f))
        alCerrado()
    }
    Atras(enabled = !cerrando && !bloqueado) { cerrando = true }
    Column(Modifier.fillMaxSize().graphicsLayer {
        clip = true
        shape = FormaEntre(origen, t.value)
        alpha = if (origen == null) t.value.coerceIn(0f, 1f) else fundidoBorde(t.value)
    }.background(papel)) {
        Row(Modifier.fillMaxWidth().background(tinta).statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).height(48.dp).background(papel).border(2.dp, acento).padding(horizontal = 12.dp)
                .graphicsLayer { val e = 0.6f + 0.4f * t.value.coerceIn(0f, 1f); scaleX = e; transformOrigin = TransformOrigin(0f, 0.5f); alpha = t.value.coerceIn(0f, 1f) },
                verticalAlignment = Alignment.CenterVertically) {
                BasicText("⌕", style = TextStyle(tinta, 20.sp, FontWeight.Black, fontFamily = letra), modifier = Modifier.padding(end = 8.dp))
                Box(Modifier.weight(1f)) {
                    if (texto.isEmpty()) BasicText(pista, style = TextStyle(gris, 15.sp, fontFamily = letra), maxLines = 1)
                    BasicTextField(texto, { texto = it }, Modifier.fillMaxWidth().focusRequester(foco), singleLine = true,
                        textStyle = TextStyle(tinta, 15.sp, FontWeight.SemiBold, fontFamily = letra), cursorBrush = SolidColor(acento),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { BusquedasRecientes.apunta(ctx, texto); teclado.clearFocus() }))
                }
                if (texto.isNotEmpty()) BasicText("✕", style = TextStyle(gris, 15.sp, FontWeight.Black, fontFamily = letra),
                    modifier = Modifier.pulsable(vibra = Vibra::tic) { texto = ""; runCatching { foco.requestFocus() } }.padding(6.dp))
            }
            BasicText("CERRAR", style = TextStyle(acento, 11.sp, FontWeight.Black, fontFamily = letra, letterSpacing = 1.4.sp), maxLines = 1,
                modifier = Modifier.padding(start = 12.dp).pulsable(vibra = null) { cerrando = true }.padding(vertical = 10.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).imePadding().graphicsLayer { alpha = if (cerrando) ((t.value - 0.5f) / 0.5f).coerceIn(0f, 1f) else 1f }) {
            if (texto.isBlank()) {
                val recientes = remember(apertura) { BusquedasRecientes.de(ctx) }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    if (recientes.isNotEmpty()) {
                        BasicText("RECIENTES", style = TextStyle(acento, 11.sp, FontWeight.Black, fontFamily = letra, letterSpacing = 2.sp), modifier = Modifier.padding(top = 22.dp, bottom = 10.dp).entrada("busca:$apertura:r", 0))
                        FlowRow(Modifier.fillMaxWidth().entrada("busca:$apertura:rc", 1), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recientes.forEach { s ->
                                BasicText(s.uppercase(), style = TextStyle(tinta, 11.sp, FontWeight.Black, fontFamily = letra, letterSpacing = 1.2.sp), maxLines = 1,
                                    modifier = Modifier.pulsable(vibra = Vibra::tic) { texto = s }.border(2.dp, tinta).padding(horizontal = 12.dp, vertical = 9.dp))
                            }
                        }
                    } else BasicText("Escribe para buscar.", style = TextStyle(gris, 17.sp, fontFamily = letra), modifier = Modifier.padding(top = 26.dp))
                }
            } else resultados(texto)
        }
    }
}
