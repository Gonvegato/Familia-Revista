package com.gonzalo.ptah

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*
 * El selector de color de los diseños: los colores de tu Apariencia (papel, tinta, acento y gris: cambian con ella), los que has usado
 * hace poco, transparente o el de serie de esa pieza; y uno a mano con el tono, la saturación y el brillo (el cuadro y la barra), su
 * opacidad y su código (#RRGGBB o #AARRGGBB).
 */

/** Lo que dice un color de un diseño: «Acento de tu Apariencia», «#12393A», «El de serie»… */
fun nombreColor(spec: String, deSerie: String = "El de serie"): String = when {
    spec.isBlank() -> deSerie
    spec == Col.TRANSPARENTE -> "Transparente"
    Col.esDelTema(spec) -> (Col.ROLES.firstOrNull { it.first == spec }?.second ?: "") + " de tu estilo"
    else -> spec.uppercase()
}

/** Un cuadradito de color (a cuadros si es transparente o tiene opacidad). */
@Composable
fun Muestra(c: Color, modifier: Modifier = Modifier, marcado: Boolean = false) {
    Box(modifier.drawBehind {
        if (c.alpha < 1f) {
            val q = size.minDimension / 4f
            for (i in 0 until 4) for (j in 0 until 4) drawRect(if ((i + j) % 2 == 0) Color(0xFFDDDDDD) else Color.White, Offset(i * q, j * q), Size(q, q))
        }
        drawRect(c)
    }.border(if (marcado) 3.dp else 1.dp, if (marcado) Acento else Charcoal))
}

/** Una fila del editor con un color: su muestra, su nombre y lo que es; un toque y se abre el selector. */
@Composable
fun FilaColor(nombre: String, spec: String, efectivo: Int, deSerie: String = "El de serie", conSerie: Boolean = true, alCambiar: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().tocable { abierto = true }.padding(vertical = 10.dp).semantics { contentDescription = "$nombre: ${nombreColor(spec, deSerie)}" },
        verticalAlignment = Alignment.CenterVertically) {
        Muestra(Color(efectivo), Modifier.size(34.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(nombre, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 22.sp, color = Charcoal)
            Text(nombreColor(spec, deSerie), color = Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text("▾", color = Muted, fontSize = 16.sp)
    }
    Filete(1.dp, Line)
    if (abierto) SelectorColorDialogo(nombre, spec, efectivo, conSerie, deSerie, alElegir = { alCambiar(it); abierto = false }, alCerrar = { abierto = false })
}

@Composable
fun SelectorColorDialogo(titulo: String, spec: String, efectivo: Int, conSerie: Boolean, deSerie: String, alElegir: (String) -> Unit, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).background(Paper).border(1.dp, Charcoal)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                ContenidoSelector(titulo, spec, efectivo, conSerie, deSerie, alElegir = { s -> if (s.startsWith("#")) Recientes.pon(ctx, s); alElegir(s) }, alCerrar = alCerrar)
            }
        }
    }
}

/** Lo de dentro del selector (también para las capturas). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ContenidoSelector(titulo: String, spec: String, efectivo: Int, conSerie: Boolean, deSerie: String, alElegir: (String) -> Unit, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(efectivo or (0xFF shl 24), it) } }
    var h by remember { mutableFloatStateOf(hsv0[0]) }
    var s by remember { mutableFloatStateOf(hsv0[1]) }
    var v by remember { mutableFloatStateOf(hsv0[2]) }
    var a by remember { mutableFloatStateOf(((efectivo ushr 24) / 255f).let { if (it == 0f) 1f else it }) }
    var elegido by remember { mutableStateOf(spec) }
    var codigo by remember { mutableStateOf(if (spec.startsWith("#")) spec else Col.hex(efectivo)) }
    fun aMano(): String { val c = Color.hsv(h, s, v, a); return Col.hex(c.toArgb()) }
    fun cambiaAMano() { elegido = aMano(); codigo = elegido }
    val actual: Int = when {
        elegido.isBlank() -> efectivo
        else -> Col.resuelve(elegido, Skin.tones, efectivo)
    }

    Antetitulo("Color", color = Acento, conCuadro = false)
    Text(titulo, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 29.sp, color = Charcoal)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Muestra(Color(actual), Modifier.size(width = 64.dp, height = 40.dp))
        Spacer(Modifier.width(12.dp))
        Text(nombreColor(elegido, deSerie), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(14.dp)); Filete(2.dp)

    // ---- Los de tu Apariencia (y transparente, y el de serie)
    Etiqueta("De tu estilo (cambian con él)", Modifier.padding(top = 12.dp, bottom = 8.dp))
    val tonos = Skin.tones
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val ops = Col.ROLES + listOf(Col.TRANSPARENTE to "Transparente") + (if (conSerie) listOf("" to deSerie) else emptyList())
        ops.forEach { (sp, nombre) ->
            Column(Modifier.widthIn(min = 56.dp).tocable { elegido = sp }, horizontalAlignment = Alignment.CenterHorizontally) {
                Muestra(Color(if (sp.isBlank()) efectivo else Col.resuelve(sp, tonos, efectivo)), Modifier.size(40.dp), marcado = elegido == sp)
                Text(nombre, color = if (elegido == sp) Acento else Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    lineHeight = 11.sp, maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    // ---- Los de hace poco
    val recientes = Recientes.lista(ctx)
    if (recientes.isNotEmpty()) {
        Etiqueta("Los de hace poco", Modifier.padding(top = 14.dp, bottom = 8.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            recientes.forEach { hx ->
                val c = Col.argb(hx) ?: return@forEach
                Muestra(Color(c), Modifier.size(36.dp).tocable {
                    elegido = hx; codigo = hx
                    FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }.let { x -> h = x[0]; s = x[1]; v = x[2] }; a = (c ushr 24) / 255f
                }, marcado = elegido.equals(hx, true))
            }
        }
    }

    // ---- A mano: el cuadro (saturación y brillo), la barra del tono, la opacidad y el código
    Etiqueta("A mano", Modifier.padding(top = 16.dp, bottom = 8.dp))
    Box(Modifier.fillMaxWidth().aspectRatio(1.6f).border(1.dp, Line)
        .pointerInput(Unit) { detectTapGestures { p -> s = (p.x / size.width).coerceIn(0f, 1f); v = 1f - (p.y / size.height).coerceIn(0f, 1f); cambiaAMano() } }
        .pointerInput(Unit) { detectDragGestures { ch, _ -> s = (ch.position.x / size.width).coerceIn(0f, 1f); v = 1f - (ch.position.y / size.height).coerceIn(0f, 1f); cambiaAMano() } }
    ) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Saturación y brillo" }) {
            drawRect(Color.hsv(h, 1f, 1f))
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val p = Offset(s * size.width, (1f - v) * size.height)
            drawCircle(Color.White, 9.dp.toPx(), p, style = Stroke(3.dp.toPx()))
            drawCircle(Color.Black, 10.5.dp.toPx(), p, style = Stroke(1.dp.toPx()))
        }
    }
    Spacer(Modifier.height(12.dp))
    BarraColor("Tono", h / 360f, Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })) { h = it * 360f; cambiaAMano() }
    BarraColor("Opacidad", a, Brush.horizontalGradient(listOf(Color.hsv(h, s, v, 0f), Color.hsv(h, s, v)))) { a = it; cambiaAMano() }
    Etiqueta("Código (#RRGGBB o #AARRGGBB)", Modifier.padding(top = 10.dp))
    CampoCodigo(codigo) { t, c ->
        codigo = t
        val argb = Col.argb(t)
        if (c != null && argb != null) {
            FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }.let { x -> h = x[0]; s = x[1]; v = x[2] }
            a = if (t.trim().removePrefix("#").length == 8) (argb ushr 24) / 255f else 1f
            elegido = Col.hex(if (t.trim().removePrefix("#").length == 8) argb else argb or (0xFF shl 24))
        }
    }

    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonSuave("Cancelar", modifier = Modifier.weight(1f)) { alCerrar() }
        BotonAcento("Ponerlo", modifier = Modifier.weight(1f)) { alElegir(elegido) }
    }
}

/** Una barra con su degradado (el tono o la opacidad), que se toca o se arrastra. */
@Composable
private fun BarraColor(nombre: String, valor: Float, fondo: Brush, alCambiar: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Etiqueta(nombre)
        Box(Modifier.fillMaxWidth().height(30.dp).padding(top = 4.dp)
            .pointerInput(Unit) { detectTapGestures { p -> alCambiar((p.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectDragGestures { ch, _ -> alCambiar((ch.position.x / size.width).coerceIn(0f, 1f)) } }) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = nombre }) {
                val q = size.height / 2
                for (i in 0 until (size.width / q).toInt() + 1) for (j in 0 until 2) drawRect(if ((i + j) % 2 == 0) Color(0xFFDDDDDD) else Color.White, Offset(i * q, j * q), Size(q, q))
                drawRect(fondo)
                drawRect(Color.Black.copy(alpha = 0.4f), style = Stroke(1.dp.toPx()))
                val x = valor.coerceIn(0f, 1f) * size.width
                drawRect(Color.White, Offset(x - 3.dp.toPx(), -2f), Size(6.dp.toPx(), size.height + 4f))
                drawRect(Color.Black, Offset(x - 3.dp.toPx(), -2f), Size(6.dp.toPx(), size.height + 4f), style = Stroke(1.dp.toPx()))
            }
        }
    }
}
