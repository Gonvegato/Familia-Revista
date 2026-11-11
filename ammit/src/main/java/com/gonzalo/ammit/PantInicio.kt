package com.gonzalo.ammit

import android.appwidget.AppWidgetHostView
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as FormatoFecha
import java.util.Locale
import kotlin.math.roundToInt

private val ES = Locale("es", "ES")
private val SOMBRA = Shadow(Color(0x99000000), Offset(0f, 2f), 6f)
private val GRIS = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** La pantalla de inicio de Ammit (sobre tu fondo de pantalla). */
@Composable
fun PantInicio(a: InicioActivity) {
    val horizontal = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { a.colocando = true }) }) {
        // Un velo suave arriba y abajo para que se lea sobre cualquier fondo
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x44000000), Color.Transparent, Color(0x33000000)))))
        if (horizontal) Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 36.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(0.42f).fillMaxSize().verticalScroll(rememberScrollState())) { Reloj(); Spacer(Modifier.height(18.dp)); Widgets(a) }
            Box(Modifier.weight(0.58f).fillMaxSize()) { Rejilla(a) }
        } else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Reloj(); Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth()) { Widgets(a) }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { Rejilla(a) }
        }
        if (a.colocando) BarraColocar(a, Modifier.align(Alignment.BottomCenter))
        if (a.eligiendoWidget) ElegirWidget(a)
    }
}

@Composable
private fun Reloj() {
    var ahora by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000L * (60 - LocalDateTime.now().second).coerceAtLeast(1)); ahora = LocalDateTime.now() } }
    Column {
        BasicText(ahora.format(DateTimeFormatter.ofPattern("HH:mm")), style = TextStyle(Color.White, 78.sp, FontWeight.SemiBold, fontFamily = Diseno.Grotesca, shadow = SOMBRA, letterSpacing = (-2).sp))
        BasicText("${ahora.dayOfWeek.getDisplayName(FormatoFecha.FULL, ES)} ${ahora.dayOfMonth} de ${ahora.month.getDisplayName(FormatoFecha.FULL, ES)}",
            style = TextStyle(Color.White, 17.sp, FontWeight.Medium, fontFamily = Diseno.Texto, shadow = SOMBRA))
        BasicText("AMMIT · SOLO LA FAMILIA", style = TextStyle(Color(Ammit.ACENTO_NOCHE), 11.sp, FontWeight.Bold, fontFamily = Diseno.Texto, letterSpacing = 2.sp, shadow = SOMBRA), modifier = Modifier.padding(top = 6.dp))
    }
}

/** Los widgets de la familia que has puesto, uno debajo de otro. */
@Composable
private fun Widgets(a: InicioActivity) {
    a.widgets.forEach { w ->
        val info = remember(w.id) { a.infoDe(w.id) } ?: return@forEach
        BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            val ancho = maxWidth
            AndroidView(
                factory = { ctx -> a.host.createView(ctx, w.id, info).apply { setPadding(0, 0, 0, 0) } },
                update = { v -> (v as? AppWidgetHostView)?.let { hv -> tamano(hv, ancho.value, w.alto.toFloat()) } },
                modifier = Modifier.fillMaxWidth().height(w.alto.dp)
            )
            if (a.colocando) Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chapa("↑") { a.subirWidget(w.id) }; Chapa("−") { a.cambiarAlto(w.id, -30) }; Chapa("+") { a.cambiarAlto(w.id, 30) }; Chapa("Quitar") { a.quitarWidget(w.id) }
            }
        }
    }
}

/** Le dice al widget su tamaño (en dp), para que se dibuje a la medida. */
private fun tamano(v: AppWidgetHostView, w: Float, h: Float) {
    val ultimo = v.tag as? Pair<*, *>
    if (ultimo == (w to h)) return
    v.tag = w to h
    if (android.os.Build.VERSION.SDK_INT >= 31) v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
    else @Suppress("DEPRECATION") v.updateAppWidgetSize(null, w.roundToInt(), h.roundToInt(), w.roundToInt(), h.roundToInt())
}

@Composable
private fun Chapa(t: String, al: () -> Unit) {
    BasicText(t, style = TextStyle(Diseno.PAPEL, 13.sp, FontWeight.Bold, fontFamily = Diseno.Texto),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC000000)).clickable(onClick = al).padding(horizontal = 12.dp, vertical = 6.dp))
}

/** Tus apps, colocables: mantener pulsada una y arrastrarla la cambia de sitio. */
@Composable
private fun Rejilla(a: InicioActivity) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val celda = 96.dp
        val columnas = (maxWidth / celda).toInt().coerceIn(3, 8)
        val sitios = remember { mutableStateMapOf<String, Rect>() }
        var arrastrando by remember { mutableStateOf<String?>(null) }
        var desplazado by remember { mutableStateOf(Offset.Zero) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = arrastrando == null), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            a.apps.chunked(columnas).forEach { fila ->
                Row(Modifier.fillMaxWidth()) {
                    fila.forEach { app ->
                        val esta = arrastrando == app.clave
                        Box(Modifier.weight(1f)
                            .onGloballyPositioned { sitios[app.clave] = it.boundsInRoot() }
                            .then(if (esta) Modifier.offset { IntOffset(desplazado.x.roundToInt(), desplazado.y.roundToInt()) }.graphicsLayer { scaleX = 1.12f; scaleY = 1.12f } else Modifier)
                            .pointerInput(app.clave) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { arrastrando = app.clave; desplazado = Offset.Zero; a.colocando = true },
                                    onDragEnd = { arrastrando = null; desplazado = Offset.Zero; a.guardarOrden() },
                                    onDragCancel = { arrastrando = null; desplazado = Offset.Zero },
                                ) { cambio, d ->
                                    cambio.consume()
                                    desplazado += d
                                    val yo = sitios[app.clave] ?: return@detectDragGesturesAfterLongPress
                                    val punto = yo.center + desplazado
                                    val destino = sitios.entries.firstOrNull { (k, r) -> k != app.clave && r.contains(punto) }?.key
                                    if (destino != null) {
                                        // Al cambiar de sitio, el arrastre sigue desde el sitio nuevo
                                        val nuevo = sitios[destino]!!
                                        a.mover(app.clave, destino)
                                        desplazado = punto - nuevo.center
                                    }
                                }
                            }
                            .clickable(enabled = arrastrando == null) { Apps.abrir(a, app) }, contentAlignment = Alignment.TopCenter) {
                            Icono(app, a.colocando)
                        }
                    }
                    repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun Icono(app: AppDeInicio, colocando: Boolean) {
    val fuera = Ammit.encendida && !app.deLaFamilia
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
        Box(Modifier.size(60.dp)) {
            app.icono?.let { Image(it, app.nombre, Modifier.fillMaxSize().graphicsLayer { alpha = if (fuera) 0.75f else 1f }, colorFilter = if (fuera) GRIS else null) }
                ?: Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Diseno.PAPEL))
            if (fuera) {
                // Las cadenas encima del icono, y el candado en la esquina
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) { Cadenas(Diseno.PAPEL, alfa = 0.85f, escala = 0.55f) }
                Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(22.dp).clip(CircleShape).background(Diseno.NEGRO).border(1.dp, Diseno.PAPEL.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) { Candado() }
            }
            if (colocando && !fuera) Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(10.dp).clip(CircleShape).background(Color(Ammit.ACENTO_NOCHE)))
        }
        BasicText(app.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(if (fuera) Color.White.copy(alpha = 0.6f) else Color.White, 12.5.sp, FontWeight.Medium, fontFamily = Diseno.Texto, shadow = SOMBRA, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Candado() {
    Canvas(Modifier.size(12.dp)) {
        val c = Diseno.PAPEL
        val w = size.width; val h = size.height
        drawRoundRect(c, Offset(w * 0.12f, h * 0.45f), androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.55f), androidx.compose.ui.geometry.CornerRadius(w * 0.12f))
        drawArc(c, 180f, 180f, false, Offset(w * 0.25f, h * 0.05f), androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.75f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.14f))
    }
}

/** Abajo, mientras colocas: poner un widget, los ajustes de Ammit y «Hecho». */
@Composable
private fun BarraColocar(a: InicioActivity, m: Modifier) {
    val ctx = LocalContext.current
    Row(m.navigationBarsPadding().padding(bottom = 18.dp).shadow(12.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Diseno.NEGRO)
        .padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Boton("+ WIDGET") { a.eligiendoWidget = true }
        Boton("AJUSTES DE AMMIT") { ctx.startActivity(Intent(ctx, AjustesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Boton("HECHO", acento = true) { a.colocando = false }
    }
}

@Composable
private fun Boton(t: String, acento: Boolean = false, al: () -> Unit) {
    BasicText(t, style = TextStyle(if (acento) Diseno.NEGRO else Diseno.PAPEL, 12.sp, FontWeight.Bold, fontFamily = Diseno.Texto, letterSpacing = 1.4.sp),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (acento) Color(Ammit.ACENTO_NOCHE) else Color.Transparent).clickable(onClick = al).padding(horizontal = 16.dp, vertical = 11.dp))
}

/** La lista de widgets de la familia para poner (con su vista previa). */
@Composable
private fun ElegirWidget(a: InicioActivity) {
    val t = tonos()
    val ctx = LocalContext.current
    val lista = remember { a.widgetsDisponibles() }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { a.eligiendoWidget = false }, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).width(520.dp).clip(RoundedCornerShape(20.dp)).background(t.fondo).clickable(enabled = false) {}.padding(22.dp)) {
            BasicText("WIDGETS DE LA FAMILIA", style = TextStyle(t.acento, 11.sp, FontWeight.Bold, fontFamily = Diseno.Texto, letterSpacing = 2.sp))
            BasicText("¿Cuál pongo?", style = TextStyle(t.tinta, 30.sp, FontWeight.ExtraBold, fontFamily = Diseno.Grotesca), modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            if (lista.isEmpty()) BasicText("No hay ninguna app de la familia con widgets instalada.", style = TextStyle(t.gris, 15.sp, fontFamily = Diseno.Texto))
            Column(Modifier.verticalScroll(rememberScrollState()).height(460.dp)) {
                lista.forEach { info ->
                    val app = remember(info) { Ammit.FAMILIA.firstOrNull { it.paquete == info.provider.packageName }?.nombre.orEmpty() }
                    val prev = remember(info) { runCatching { info.loadPreviewImage(ctx, 0) }.getOrNull() }
                    Row(Modifier.fillMaxWidth().clickable { a.ponerWidget(info) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 120.dp, height = 64.dp), contentAlignment = Alignment.Center) {
                            prev?.let { d -> AndroidView({ c -> android.widget.ImageView(c).apply { setImageDrawable(d); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } }, Modifier.fillMaxSize()) }
                        }
                        Column(Modifier.padding(start = 14.dp)) {
                            BasicText(info.loadLabel(ctx.packageManager), style = TextStyle(t.tinta, 18.sp, FontWeight.ExtraBold, fontFamily = Diseno.Grotesca))
                            BasicText(app.uppercase(), style = TextStyle(t.gris, 11.sp, FontWeight.Bold, fontFamily = Diseno.Texto, letterSpacing = 1.6.sp))
                        }
                    }
                }
            }
        }
    }
}
