package com.gonzalo.ptah

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Las piezas de los editores (las de la revista): una casilla de texto con su etiqueta encima (nunca cortada, también con la letra
 * grande), elegir una de pocas opciones, un sí/no y una barra con su valor.
 */

/** Una casilla de texto: la etiqueta en versalitas encima y lo escrito en serif. */
@Composable
fun Campo(etiqueta: String, valor: String, pista: String, modifier: Modifier = Modifier, lineas: Int = 1, teclado: KeyboardType = KeyboardType.Text, alCambiar: (String) -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Etiqueta(etiqueta)
        Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
            if (valor.isEmpty()) Text(pista, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 22.sp)
            BasicTextField(valor, alCambiar, singleLine = lineas == 1, maxLines = lineas, minLines = 1,
                textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(Acento), keyboardOptions = KeyboardOptions(keyboardType = teclado), modifier = Modifier.fillMaxWidth())
        }
        Filete(1.5.dp)
    }
}

/** Elegir una de pocas opciones ([opciones]: valor → nombre). */
@Composable
fun Elige(etiqueta: String, opciones: List<Pair<String, String>>, valor: String, alCambiar: (String) -> Unit) {
    MagChoice(opciones.map { it.second }, opciones.indexOfFirst { it.first == valor }.coerceAtLeast(0), etiqueta) { i -> alCambiar(opciones[i].first) }
}

/** Un sí/no con su explicación. */
@Composable
fun SiNo(titulo: String, descripcion: String? = null, valor: Boolean, alCambiar: (Boolean) -> Unit) {
    Fila(titulo, descripcion, alHacer = { alCambiar(!valor) }) { Interruptor(valor, alCambiar) }
}

/** Una barra con su valor escrito al lado (el que se ve se cambia al momento). */
@Composable
fun Barra(etiqueta: String, valor: Float, rango: ClosedFloatingPointRange<Float>, texto: (Float) -> String, pasos: Int = 0, alCambiar: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Etiqueta(etiqueta, Modifier.weight(1f))
            Text(texto(valor), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
        Slider(valor, alCambiar, valueRange = rango, steps = pasos, modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(thumbColor = Acento, activeTrackColor = Acento, inactiveTrackColor = Line))
    }
}

/** Un bloque del editor: el filete y su antetítulo. */
@Composable
fun BloqueEditor(titulo: String, primero: Boolean = false) {
    if (!primero) { Spacer(Modifier.height(16.dp)); Filete(2.dp) }
    Spacer(Modifier.height(10.dp))
    Antetitulo(titulo, Modifier.padding(bottom = 2.dp))
}
