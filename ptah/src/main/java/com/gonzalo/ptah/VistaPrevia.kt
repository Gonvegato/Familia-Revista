package com.gonzalo.ptah

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Lo que dibujan las pantallas (la hora, el tiempo…): el de las pruebas, si lo hay; si no, el de este móvil. */
fun datosApp(ctx: android.content.Context): Datos = Widgets.datos?.invoke(ctx) ?: DatosAndroid(ctx)

/** Un fondo de pantalla de mentira (para ver cómo queda un widget transparente o con opacidad): un degradado sobrio. */
val FondoDePantalla = Brush.linearGradient(listOf(Color(0xFF4F6B68), Color(0xFF1E2B33), Color(0xFF3A2A33)))

/**
 * La vista previa de un diseño a [w] × [h] dp, con el mismo dibujo que el widget ([Pintor]). [vivo]: la hora se mueve (cada segundo con
 * segundero; si no, cada 20 s). Lee la apariencia: cambia con ella.
 */
@Composable
fun VistaPrevia(d: Diseno, w: Float, h: Float, noche: Boolean, modifier: Modifier = Modifier, vivo: Boolean = true, mostrar: Float = 1f) {
    val ctx = LocalContext.current
    // (se dibuja a su tamaño de verdad y se enseña a [mostrar] veces: con los píxeles justos, la letra queda como en el widget)
    val dens = LocalDensity.current.density * mostrar
    // La apariencia (si cambia, se vuelve a dibujar)
    val tema = listOf(Skin.palette.id, Skin.accentId, Skin.fondoId, Skin.fondoPropio, Skin.tintaPropia, Skin.acentoPropio, Skin.invertido, Skin.origenAtrio, Skin.display, Skin.body, TemaAtrio.tonos)
    var letras by remember { mutableIntStateOf(0) }
    LaunchedEffect(d.estilo.letraTitulos, d.estilo.letraTexto, Skin.display, Skin.body) { withContext(Dispatchers.IO) { Letras.prepara(ctx, listOf(d)) }; letras++ }
    // El tiempo: si no hay nada guardado de ese sitio, se pide
    var tiempo by remember { mutableIntStateOf(0) }
    if (d.tipo == Tiempo.id) LaunchedEffect(d.texto("lugar"), d.decimal("lat"), d.decimal("lon")) {
        withContext(Dispatchers.IO) { if (d.texto("lugar") == "aqui") Ubicacion.actualiza(ctx); Refresco.tiempo(ctx, listOf(d)) }; tiempo++
    }
    val reloj = d.tipo == RelojDigital.id || d.tipo == RelojAgujas.id
    var tic by remember { mutableIntStateOf(0) }
    if (vivo && reloj) LaunchedEffect(d.si("segundero")) { while (true) { delay(if (d.si("segundero")) 1_000 else 20_000); tic++ } }
    val bm = remember(d, w, h, noche, dens, tema, letras, tiempo, tic) {
        runCatching { Pintor.pinta(ctx, d, w, h, noche, dens, datosApp(ctx)).bm.asImageBitmap() }.getOrNull()
    }
    if (bm != null) Image(bm, d.nombre, modifier.size((w * mostrar).dp, (h * mostrar).dp), contentScale = ContentScale.FillBounds)
    else Spacer(modifier.size((w * mostrar).dp, (h * mostrar).dp))
}

/** Una miniatura: la vista previa encogida para caber en su sitio, sobre el fondo de pantalla de mentira. */
@Composable
fun Miniatura(d: Diseno, celdas: Tamanos.Celdas, noche: Boolean, modifier: Modifier = Modifier, alto: Float = 120f) {
    BoxWithConstraints(modifier.fillMaxWidth().height(alto.dp).background(FondoDePantalla).semantics { contentDescription = "Así se ve ${d.nombre}" }, contentAlignment = Alignment.Center) {
        val (w, h) = Tamanos.dp(celdas)
        val k = minOf(1f, (maxWidth.value - 16f) / w, (alto - 16f) / h)
        VistaPrevia(d, w, h, noche, vivo = false, mostrar = k)
    }
}
