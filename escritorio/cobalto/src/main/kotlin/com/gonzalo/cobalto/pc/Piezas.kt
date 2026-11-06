@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.gonzalo.cobalto.pc

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gonzalo.cobalto.Doc
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.familia.pc.revista.*
import org.json.JSONArray
import org.json.JSONObject

/*
 * Las piezas de Cobalto del ordenador que no están en la revista común (escritorio/familia): los colores y los tipos de las
 * fichas y las notas (los mismos que en el móvil, EscribirDialogos.kt), las muestras de color, las opciones que se encienden y
 * se apagan, los desplegables, las teclas de la barra del editor y cómo se escriben los atajos en cada sistema.
 */

/** Los colores con los que se marcan obras, fichas, notas y uniones (los de COLORES_ESCRITO del móvil). */
val COLORES_ESCRITO = listOf("#B0102F", "#D9822B", "#C9A227", "#5B8C5A", "#3E8E9E", "#4A6FA5", "#7B5EA7", "#A8577E", "#6B6B6B")

/** Un color «#rrggbb» guardado en un escrito, o [def] si no hay (o no se entiende). */
fun colorEscrito(hex: String, def: Color = Acento): Color {
    val h = hex.trim().removePrefix("#")
    if (h.length != 6) return def
    return h.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: def
}

/** Los tipos de ficha y de nota (clave, plural) y su nombre en singular: como en el móvil. */
val TIPOS_FICHA = listOf("personaje" to "Personajes", "lugar" to "Lugares", "objeto" to "Objetos", "mundo" to "Mundo", "otro" to "Otros")
val TIPOS_NOTA = listOf("nota" to "Notas", "idea" to "Ideas", "resumen" to "Resúmenes", "esquema" to "Esquemas", "investigacion" to "Investigación", "plantilla" to "Plantillas")
fun singularFicha(t: String) = when (t) { "personaje" -> "Personaje"; "lugar" -> "Lugar"; "objeto" -> "Objeto"; "mundo" -> "Mundo"; else -> "Otro" }
fun singularNota(t: String) = when (t) { "idea" -> "Idea"; "resumen" -> "Resumen"; "esquema" -> "Esquema"; "investigacion" -> "Investigación"; "plantilla" -> "Plantilla"; else -> "Nota" }

val ESTADOS_ESCENA = listOf("Borrador", "Revisión", "Final")
fun colorEstado(e: Int): Color = when (e) { 0 -> Color(0xFFB8B2A6); 1 -> Color(0xFFD9A441); else -> Color(0xFF5B8C5A) }
/** El verde de «meta conseguida» (el mismo que en el móvil). */
val Conseguido = Color(0xFF5B8C5A)

/** Los ids a los que está vinculada una ficha o una nota (partes y capítulos). */
fun Doc.vin(): List<String> = lista("vin")
fun JSONObject.ponVin(l: List<String>) { put("vin", JSONArray().also { a -> l.forEach { a.put(it) } }) }

/** Los datos de una ficha ([{k, v}]), sin los vacíos. */
fun camposDe(f: Doc): List<Pair<String, String>> = f.o.optJSONArray("campos")?.let { a ->
    (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { it.optString("k") to it.optString("v") }.filter { it.first.isNotBlank() || it.second.isNotBlank() }
}.orEmpty()

/** Los atajos de teclado se escriben como en cada sistema (⌘ en el Mac): «Ctrl+B» → «⌘B». */
object Atajo {
    val mac: Boolean get() = Sitio.sistema == Sitio.Sistema.MAC
    fun texto(t: String): String = if (mac) t.replace("Ctrl+Mayús+", "⇧⌘").replace("Ctrl+Alt+", "⌥⌘").replace("Ctrl+", "⌘").replace("Mayús+", "⇧") else t
    /** La tecla de los atajos de este sistema (⌘ en el Mac, Ctrl en los demás). */
    fun principal(k: KeyEvent): Boolean = if (mac) k.isMetaPressed else k.isCtrlPressed
}

/** Un clic que no se lleva el foco (el cursor sigue en el texto). */
fun Modifier.clickableSinFoco(onClick: () -> Unit): Modifier = this.focusProperties { canFocus = false }.clickable(onClick = onClick)

/** Una opción que se enciende y se apaga (versalitas; encendida en acento y subrayada): FiltroRevista con su margen. */
@Composable
fun Opcion(texto: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) = FiltroRevista(texto, on, modifier.pointerHoverIcon(PointerIcon.Hand), onClick)

/** Una fila de opciones (de una sola elección o varias), que sigue en la línea de abajo si no cabe. */
@Composable
fun FilaOpciones(modifier: Modifier = Modifier, contenido: @Composable FlowRowScope.() -> Unit) =
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), content = contenido)

/** Un antetítulo pequeño de campo («TIPO», «COLOR»…). */
@Composable
fun EtiquetaCampo(t: String, modifier: Modifier = Modifier) =
    Text(t.uppercase(), modifier.padding(top = 10.dp, bottom = 2.dp), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)

/** Las muestras de color: un círculo por color; el elegido, con un aro de tinta. [conNinguno]: también «sin color». */
@Composable
fun Muestras(elegido: String, conNinguno: Boolean = false, onElige: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (conNinguno) {
            val on = elegido.isBlank()
            Box(Modifier.size(28.dp).clip(CircleShape).border(if (on) 3.dp else 1.dp, if (on) Charcoal else Line, CircleShape).pointerHoverIcon(PointerIcon.Hand).clickable { onElige("") },
                contentAlignment = Alignment.Center) { Text("—", color = Muted, fontSize = 12.sp) }
        }
        COLORES_ESCRITO.forEach { c ->
            val on = c.equals(elegido, true)
            Box(Modifier.size(28.dp).clip(CircleShape).background(colorEscrito(c)).border(if (on) 3.dp else 0.dp, Charcoal, CircleShape).pointerHoverIcon(PointerIcon.Hand).clickable { onElige(c) })
        }
    }
}

/** Un desplegable de la revista: la etiqueta pequeña y lo elegido; al pulsar, la lista. */
@Composable
fun Desplegable(etiqueta: String, valor: String, opciones: List<Pair<String, String>>, modifier: Modifier = Modifier, onElige: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(Modifier.fillMaxWidth().clip(esquina(6.dp)).border(1.dp, Line, esquina(6.dp)).pointerHoverIcon(PointerIcon.Hand).clickable { abierto = true }.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(etiqueta.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(opciones.find { it.first == valor }?.second ?: "—", Modifier.weight(1f), color = Charcoal, fontFamily = Body, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("▾", color = Muted, fontSize = 12.sp)
            }
        }
        DropdownMenu(abierto, { abierto = false }, containerColor = Paper) {
            opciones.forEach { (k, n) -> DropdownMenuItem({ Text(n, fontFamily = Body, fontWeight = if (k == valor) FontWeight.Bold else FontWeight.Normal) }, { abierto = false; onElige(k) }) }
        }
    }
}

/**
 * Un campo de texto de la revista: la etiqueta en versalitas encima y una línea debajo (de acento al escribir). Sin cajas, como
 * los formularios de Atrio. [lineas] > 1: un texto largo. [estilo]: la letra (por defecto, la del texto).
 */
@Composable
fun CampoRevista(
    valor: String, onCambia: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier, lineas: Int = 1, pista: String = "",
    estilo: TextStyle = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp), foco: FocusRequester? = null, alTeclear: ((KeyEvent) -> Boolean)? = null,
) {
    var enfocado by remember { mutableStateOf(false) }
    Column(modifier.padding(vertical = 4.dp)) {
        if (etiqueta.isNotEmpty()) Text(etiqueta.uppercase(), color = if (enfocado) Acento else Muted, fontFamily = Body, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        val linea = if (enfocado) Acento else Line
        BasicTextField(
            valor, onCambia,
            Modifier.fillMaxWidth().padding(top = 3.dp).let { if (foco != null) it.focusRequester(foco) else it }
                .onFocusChanged { enfocado = it.isFocused }
                .let { m -> if (alTeclear != null) m.onPreviewKeyEvent(alTeclear) else m }
                .drawBehind { drawRect(linea, Offset(0f, size.height - (if (enfocado) 2.dp else 1.dp).toPx()), Size(size.width, (if (enfocado) 2.dp else 1.dp).toPx())) }
                .padding(bottom = 6.dp),
            singleLine = lineas == 1, minLines = lineas,
            textStyle = estilo.copy(color = Charcoal), cursorBrush = SolidColor(Acento),
            decorationBox = { inner -> Box { if (valor.isEmpty() && pista.isNotEmpty()) Text(pista, style = estilo.copy(color = Muted.copy(alpha = 0.7f), fontStyle = FontStyle.Italic)); inner() } }
        )
    }
}

/**
 * Una tecla de la barra del editor: el texto (o el signo), encendida en tinta cuando está activa, el acento al pasar por encima y
 * su explicación (con el atajo) al dejar el ratón quieto. No se queda el foco: el cursor sigue en el texto.
 */
@Composable
fun Tecla(texto: String, ayuda: String, modifier: Modifier = Modifier, activa: Boolean = false, familia: FontFamily = Body, tam: TextUnit = 13.sp, peso: FontWeight = FontWeight.Bold,
          estilo: FontStyle = FontStyle.Normal, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val encima by hover.collectIsHoveredAsState()
    Ayuda(ayuda) {
        Box(
            modifier.heightIn(min = 32.dp).widthIn(min = 32.dp).background(if (activa) Charcoal else Color.Transparent)
                .hoverable(hover).pointerHoverIcon(PointerIcon.Hand).focusProperties { canFocus = false }.clickable(interactionSource = hover, indication = null, onClick = onClick)
                .padding(horizontal = 9.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(texto, color = if (activa) Paper else if (encima) Acento else Charcoal, fontFamily = familia, fontSize = tam, fontWeight = peso, fontStyle = estilo, letterSpacing = 0.3.sp, maxLines = 1)
        }
    }
}

/** La explicación de algo al dejar el ratón encima (como el «title» de Atrio). */
@Composable
fun Ayuda(texto: String, contenido: @Composable () -> Unit) {
    if (texto.isBlank()) { contenido(); return }
    TooltipArea(
        tooltip = {
            Text(texto, Modifier.clip(esquina(4.dp)).background(Charcoal).padding(horizontal = 10.dp, vertical = 6.dp), color = Paper, fontFamily = Body, fontSize = 12.sp)
        },
        delayMillis = 550,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp)),
        content = contenido
    )
}

/** Un separador vertical en una barra. */
@Composable
fun SeparadorBarra() = Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(20.dp).background(Line))

/**
 * Una hoja encima de la app, más grande que los diálogos de la revista (para leer una ficha, comparar dos versiones…): el ancho
 * que se pida (sin pasar del 92 % de la ventana) y casi todo el alto. Sin titular propio: lo pone quien la usa.
 */
@Composable
fun Hoja(onCerrar: () -> Unit, ancho: Dp = 900.dp, alCerrarConEsc: () -> Boolean = { false }, contenido: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val forma = esquina(10.dp)
        val foco = remember { FocusRequester() }
        // (antes que lo de dentro: si un campo pide el foco, se lo queda él)
        LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
        val ventana = LocalWindowInfo.current.containerSize
        val d = LocalDensity.current
        val w = if (ventana.width > 0) with(d) { minOf(ancho, (ventana.width * 0.92f).toDp()) } else ancho
        val h = if (ventana.height > 0) with(d) { (ventana.height * 0.9f).toDp() } else 760.dp
        Column(
            Modifier.width(w).heightIn(max = h).clip(forma).background(Paper).border(1.dp, Line, forma)
                .focusRequester(foco).focusable()
                .onPreviewKeyEvent { k -> if (k.type == KeyEventType.KeyDown && k.key == Key.Escape) { if (!alCerrarConEsc()) onCerrar(); true } else false },
            content = contenido
        )
    }
}

/** Un botón de texto pequeño (versalitas) para dentro de las tarjetas: «PONER EL NOMBRE», «EDITAR»… */
@Composable
fun AccionPeque(texto: String, modifier: Modifier = Modifier, acento: Boolean = false, apagada: Boolean = false, onClick: () -> Unit) =
    Text(texto.uppercase(), modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(vertical = 6.dp),
        color = if (apagada) Muted else if (acento) Acento else Charcoal, fontFamily = Body, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1)

/** Un punto de color (el de una ficha o una nota). */
@Composable
fun Punto(c: Color, tam: Dp = 11.dp, modifier: Modifier = Modifier) = Box(modifier.size(tam).clip(CircleShape).background(c))

/** Una barra de progreso fina (la meta del día, la de la obra). */
@Composable
fun Progreso(f: Float, color: Color = Acento, alto: Dp = 3.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(alto).background(Sand)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(color)) }
}
