package com.gonzalo.ammit

import android.appwidget.AppWidgetHostView
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as FormatoFecha
import java.util.Locale
import kotlin.math.roundToInt

private val ES = Locale("es", "ES")
private val SOMBRA = Shadow(Color(0x99000000), Offset(0f, 2f), 6f)

/**
 * Lo que dura la animación en curso y cuándo aparece cada cosa. Es siempre el mismo objeto y solo se lee dentro de las capas
 * (graphicsLayer) y del dibujo: al empezar o cambiar una animación no se rehace la pantalla (eso daba un tirón justo cuando salían las
 * apps tras el juicio), solo se mueven las capas.
 */
class Aparicion(private val a: InicioActivity) {
    private val ms get() = a.ms.floatValue
    private val anim get() = if (a.tapada) "cortina" else a.animacion
    private val paso get() = minOf(22f, 520f / a.apps.size.coerceAtLeast(1))
    /** La hora, los widgets: 0 sin ver, 1 del todo. */
    fun resto(): Float = when (anim) {
        "entrar" -> Tiempos.clamp((ms - Tiempos.ENTRAR_APPS) / 300)
        "aparecer" -> Tiempos.clamp(ms / 300)
        "salir" -> 1 - Tiempos.clamp(ms / 250)
        "cortina" -> 0f
        else -> 1f
    }
    /** La app número [i]: salen una tras otra. */
    fun icono(i: Int): Float = when (anim) {
        "entrar" -> Tiempos.clamp((ms - Tiempos.ENTRAR_APPS - 50 - i * paso) / 260)
        "aparecer" -> Tiempos.clamp((ms - 50 - i * paso) / 260)
        "salir" -> 1 - Tiempos.clamp(ms / 250)
        "cortina" -> 0f
        else -> 1f
    }
    companion object {
        fun dura(anim: String?, cuantas: Int) = when (anim) {
            "entrar" -> Tiempos.ENTRAR_MS
            "aparecer" -> (50 + minOf(22f, 520f / cuantas.coerceAtLeast(1)) * cuantas + 280).toInt()
            "salir" -> 300
            else -> 0
        }
    }
}

val LocalAparicion = androidx.compose.runtime.compositionLocalOf<Aparicion?> { null }

/** El campo de la pantalla entera (o, si lo que hay que tapar es más grande, de ese tamaño). */
fun paraTapar(pantalla: Pair<Int, Int>, w: Float, h: Float): Campo =
    if (pantalla.first + 1 < w || pantalla.second + 1 < h) Campo.de(w.toInt(), h.toInt()) else Campo.de(pantalla.first, pantalla.second)

/** Dónde empieza esta vista en la pantalla (para pintar el campo anclado a la pantalla). */
fun enPantalla(v: android.view.View): Pair<Float, Float> { val l = IntArray(2); v.getLocationOnScreen(l); return l[0].toFloat() to l[1].toFloat() }

/** Cómo sale cada cosa: de pequeña a su tamaño, con un pequeño rebote. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.sale(q: Float) {
    alpha = q
    val e = if (q < 1f) 0.55f + 0.45f * Tiempos.ease(q) + 0.14f * kotlin.math.sin(q * Math.PI.toFloat()) else 1f
    scaleX = e; scaleY = e
}

/** La pantalla de inicio de Ammit: el sinfín de cadenas de fondo, y encima la hora, los widgets de la familia y tus apps. */
@Composable
fun PantInicio(a: InicioActivity) {
    val horizontal = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    // El reloj de la animación, a cada fotograma (a.ms vuelve a 0 en cuanto cambia la animación, así el primer fotograma nunca sale
    // con el final de la anterior)
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { a.animacion }.collectLatest { anim ->
            if (anim == null) return@collectLatest
            if (anim == "casa") {
                // Debajo de la cortina, al volver al inicio: con las apps cargadas y esto pintado, la cortina se funde (todo ya a la vista)
                val hasta = android.os.SystemClock.uptimeMillis() + 1500
                while (!a.cargadas && android.os.SystemClock.uptimeMillis() < hasta) delay(16)
                repeat(3) { androidx.compose.runtime.withFrameNanos { } }
                Cortina.inicioListo = true
                a.animacion = null
                return@collectLatest
            }
            if (anim == "cortina") {
                // Debajo de la cortina de cadenas: con las apps cargadas y esto pintado, la cortina se puede quitar (y entonces salen)
                val hasta = android.os.SystemClock.uptimeMillis() + 1500
                while (!a.cargadas && android.os.SystemClock.uptimeMillis() < hasta) delay(16)
                repeat(3) { androidx.compose.runtime.withFrameNanos { } }
                Cortina.inicioListo = true
                delay(5000)
                if (a.animacion == "cortina") a.animacion = "aparecer"
                return@collectLatest
            }
            val d = Aparicion.dura(anim, a.apps.size).toFloat()
            val t0 = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val m = (androidx.compose.runtime.withFrameNanos { it } - t0) / 1_000_000f
                a.ms.floatValue = minOf(m, d)
                if (m >= d) break
            }
            a.alAcabarAnimacion()
        }
    }
    // (lo que cambia la pantalla de verdad se deriva aparte: así pasar de «tapada» a «salen las apps» no la rehace)
    val oculta by remember { androidx.compose.runtime.derivedStateOf { !Ammit.encendida && a.animacion == null && !a.tapada } }
    val conTemblor by remember { androidx.compose.runtime.derivedStateOf { a.animacion == "entrar" || a.animacion == "salir" } }
    val cadena by remember { androidx.compose.runtime.derivedStateOf { if (Ammit.encendida || a.animacion == "salir" || a.tapada) 1f else 0f } }
    // Apagada y sin nada que animar, esta pantalla solo pasa a tu inicio de siempre: no se pinta nada (si no, al darle a casa se veían
    // un instante la hora y las apps de Ammit antes de tu inicio)
    if (oculta) { Box(Modifier.fillMaxSize()); return }
    val ap = remember { Aparicion(a) }
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    // (el tamaño de la pantalla, una vez por giro, no a cada fotograma)
    val pantalla = remember(config.orientation, config.screenWidthDp, config.screenHeightDp) { Campo.pantalla(ctx) }
    val vista = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.CompositionLocalProvider(LocalCadena provides cadena, LocalAparicion provides ap) {
    // (la capa del temblor, solo mientras hay golpes: envolviendo siempre toda la pantalla, las capturas salían en blanco)
    Box(Modifier.fillMaxSize().then(if (!conTemblor) Modifier else Modifier.graphicsLayer {
        // El temblor: los golpes de las cadenas al llegar; al apagar, un tirón
        val ms = a.ms.floatValue
        val c = paraTapar(pantalla, size.width, size.height)
        val (dx, dy) = when (a.animacion) {
            "entrar" -> Tiempos.temblor(ms, Tiempos.golpesEntrar(c.n))
            "salir" -> if (ms in 50f..250f) { val f = 14f * kotlin.math.sin((ms - 50) / 200 * Math.PI.toFloat()); f * 0.5f * kotlin.math.sin(ms * 0.9f) to f * 0.5f * kotlin.math.sin(ms * 1.13f) } else 0f to 0f
            else -> 0f to 0f
        }
        translationX = dx * c.u; translationY = dy * c.u
    }).pointerInput(Unit) { detectTapGestures(onLongPress = { a.colocando = true }) }) {
        // El fondo: el sinfín de cadenas (al encender sin cortina, llegando una tras otra sobre tu fondo de pantalla)
        Canvas(Modifier.fillMaxSize()) {
            // (del tamaño de la pantalla entera y anclado a ella, no a la ventana: igual al píxel que el de la cortina)
            val c = paraTapar(pantalla, size.width, size.height)
            val (ox, oy) = enPantalla(vista)
            translate(-ox, -oy) {
                if (a.animacion == "entrar") {
                    val ms = a.ms.floatValue
                    drawIntoCanvas { cv -> runCatching { c.pinta(cv.nativeCanvas, Tiempos.entrarOscuro(ms), Tiempos.entrarBase(ms)) { k -> Tiempos.entrar(ms, k) } }.onFailure { Fallos.apunta(ctx, "inicio", it) } }
                } else if (cadena > 0f) {
                    // (pintado igual que en la cortina, con las mismas listas de dibujo: así al fundirse no se nota nada)
                    drawIntoCanvas { cv -> runCatching { c.pinta(cv.nativeCanvas, Campo.OSCURO, 1f) { 1f } }.onFailure { Fallos.apunta(ctx, "inicio", it) } }
                }
            }
        }
        if (horizontal) Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 36.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(0.42f).fillMaxSize().graphicsLayer { alpha = ap.resto() }.verticalScroll(rememberScrollState())) { Reloj(); Spacer(Modifier.height(18.dp)); Widgets(a) }
            Box(Modifier.weight(0.58f).fillMaxSize()) { Rejilla(a) }
        } else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = ap.resto() }) {
                Reloj(); Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth()) { Widgets(a) }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { Rejilla(a) }
        }
        if (a.colocando) BarraColocar(a, Modifier.align(Alignment.BottomCenter))
        if (a.eligiendoWidget) ElegirWidget(a)
    }
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
        // Cuánto falta para poder soltarla (se recalcula cuando cambia algo en la cuenta)
        val ctx = LocalContext.current
        val cambios = runCatching { com.gonzalo.familia.LaFamilia.cambios }.getOrDefault(0)
        val p = remember(cambios, ahora) { runCatching { Candado.progreso(ctx) }.getOrNull() }
        if (p != null && p.conCuenta && p.listo) BasicText(
            if (p.puede) "YA PUEDES SOLTARLA" else "SE SUELTA CON ${Candado.PALABRAS} PALABRAS (LLEVAS ${p.palabras}) O UN CAPÍTULO",
            style = TextStyle(Color.White.copy(alpha = 0.75f), 11.sp, FontWeight.Bold, fontFamily = Diseno.Texto, letterSpacing = 1.6.sp, shadow = SOMBRA),
            modifier = Modifier.padding(top = 4.dp))
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
            // (aire arriba: el candado de la esquina de los iconos de la primera fila sobresale y se cortaba)
            Spacer(Modifier.height(10.dp))
            val ap = LocalAparicion.current
            a.apps.chunked(columnas).forEachIndexed { nf, fila ->
                Row(Modifier.fillMaxWidth()) {
                    fila.forEachIndexed { nc, app ->
                        val i = nf * columnas + nc
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
                            .clickable(enabled = arrastrando == null) { Apps.abrir(a, app) }
                            .then(if (Diseno.sinCapas) Modifier else Modifier.graphicsLayer { sale(ap?.icono(i) ?: 1f) }), contentAlignment = Alignment.TopCenter) {
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
    // (k: cuánto encadenada; durante la animación va de 0 a 1 al encender y de 1 a 0 al apagar)
    val k = if (app.deLaFamilia) 0f else LocalCadena.current
    val fuera = k > 0.001f
    val gris = remember(k) { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - k) }) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
        Box(Modifier.size(60.dp)) {
            app.icono?.let { Image(it, app.nombre, Modifier.fillMaxSize().graphicsLayer { alpha = 1f - 0.25f * k }, colorFilter = if (fuera) gris else null) }
                ?: Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Diseno.PAPEL))
            if (fuera) {
                // Las cadenas encima del icono (al soltarse, caen) y el candado en la esquina
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).graphicsLayer { translationY = (1f - k) * 26.dp.toPx(); alpha = k }) { Cadenas(Diseno.PAPEL, alfa = 0.85f, escala = 0.55f) }
                Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(22.dp).graphicsLayer { scaleX = k; scaleY = k; alpha = k }.clip(CircleShape).background(Diseno.NEGRO).border(1.dp, Diseno.PAPEL.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) { Candado() }
            }
            if (colocando && !fuera) Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(10.dp).clip(CircleShape).background(Color(Ammit.ACENTO_NOCHE)))
        }
        BasicText(app.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(Color.White.copy(alpha = 1f - 0.4f * k), 12.5.sp, FontWeight.Medium, fontFamily = Diseno.Texto, shadow = SOMBRA, textAlign = TextAlign.Center),
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
