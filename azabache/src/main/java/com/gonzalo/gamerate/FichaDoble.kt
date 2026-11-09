package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/*
 * La ficha a doble página (como en Seshat, Bastet y Thot): la foto de apertura (el fondo de la ficha o, si no tiene, su portada ampliada),
 * sin texto pintado encima, con un velo arriba para «← Volver» y los botones; la portada con su paspartú colgando sobre el borde; la
 * etiqueta del estado, el título enorme, el botón principal y, debajo, las secciones numeradas de la página de siempre.
 */

/**
 * Qué parte del fondo se ve en la apertura: es de CADA dispositivo (un móvil y una tableta tienen otra forma), no de la cuenta. La imagen
 * (el `hero` de la ficha, el que leen Seshat y Bastet) no cambia. Se guarda por ficha y por imagen: si la imagen cambia, vuelve al centro.
 */
object EncuadreFondo {
    private fun p(c: Context) = c.getSharedPreferences("encuadres_dispositivo", Context.MODE_PRIVATE)
    /** Sube al cambiar un encuadre (la ficha se vuelve a pintar). */
    var version by mutableIntStateOf(0)
        private set

    /** La clave de un encuadre: la ficha ([item], «categoría:número»), que es el fondo, y la imagen (su huella). */
    fun clave(item: String, imagen: String): String = "$item|fondo|${huella(imagen)}"
    fun huella(imagen: String): String = java.security.MessageDigest.getInstance("SHA-1").digest(imagen.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

    /** El encuadre (desplazamiento horizontal y vertical, de -1 a 1; 0 es el centro). */
    fun de(c: Context, item: String, imagen: String): Pair<Float, Float> {
        val v = p(c).getString(clave(item, imagen), null) ?: return 0f to 0f
        val a = v.split(',')
        return (a.getOrNull(0)?.toFloatOrNull() ?: 0f).coerceIn(-1f, 1f) to (a.getOrNull(1)?.toFloatOrNull() ?: 0f).coerceIn(-1f, 1f)
    }

    fun pon(c: Context, item: String, imagen: String, x: Float, y: Float) {
        val e = p(c).edit()
        if (x == 0f && y == 0f) e.remove(clave(item, imagen)) else e.putString(clave(item, imagen), "${x.coerceIn(-1f, 1f)},${y.coerceIn(-1f, 1f)}")
        e.apply()
        version++
    }
}

/** Alto de la foto de apertura. */
val ALTO_APERTURA = 290.dp

/** La imagen de apertura encuadrada como se eligió en este dispositivo. */
@Composable
fun FondoEncuadrado(imagen: String, item: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val v = EncuadreFondo.version
    val (x, y) = remember(imagen, item, v) { EncuadreFondo.de(ctx, item, imagen) }
    AsyncImage(imagen, null, modifier, contentScale = ContentScale.Crop, alignment = BiasAlignment(x, y), colorFilter = Ediciones.filtro)
}

/**
 * La cabecera de la ficha a doble página. [item]: «categoría:número» (para el encuadre); [acciones]: los botones de arriba a la derecha
 * («IMÁGENES», «EDITAR»…); [principal]: el botón principal (texto y qué hace), o nada.
 */
@Composable
fun AperturaFicha(
    item: String, portada: String, fondo: String, titulo: String, etiqueta: String?, linea: String,
    onBack: () -> Unit, acciones: List<Pair<String, () -> Unit>>, principal: Pair<String, () -> Unit>?, alEncuadrar: (() -> Unit)?,
) {
    val apertura = fondo.ifBlank { portada }
    val ancho = minOf(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp, 1080)
    val tapaW = if (ancho >= 700) 170.dp else 132.dp
    val tapaH = tapaW * 1.5f
    val colgada = tapaH * 0.75f
    Column(Modifier.fillMaxWidth().testTag("apertura")) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().height(ALTO_APERTURA)) {
            Box(Modifier.matchParentSize().clipToBounds().background(Brush.verticalGradient(listOf(lerpCo(AcentoBloque, Color.White, 0.08f), lerpCo(AcentoBloque, Color.Black, 0.6f))))) {
                if (apertura.isNotBlank()) FondoEncuadrado(apertura, item, Modifier.matchParentSize())
            }
            // El velo de arriba: «← Volver» y los botones se leen sobre cualquier foto
            Box(Modifier.fillMaxWidth().height(110.dp).background(veloArriba()))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Text("← VOLVER", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.5.sp, letterSpacing = 1.4.sp, maxLines = 1,
                    modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp, horizontal = 4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End)) {
                    (acciones + listOfNotNull(if (alEncuadrar != null && apertura.isNotBlank()) "Encuadrar" to alEncuadrar else null)).forEach { (t, f) ->
                        Text(t.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.5.sp, letterSpacing = 1.4.sp, maxLines = 1,
                            modifier = Modifier.clickable(onClick = f).padding(vertical = 8.dp, horizontal = 6.dp))
                    }
                }
            }
            // La portada con su paspartú, colgando sobre el borde de la foto
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp).offset(y = colgada)) {
                PortadaConPaspartu(portada, titulo, tapaW, tapaH, color = AcentoBloque, sombra = 18.dp)
            }
        }
        // Lo de la izquierda de la portada: la etiqueta, el título y su línea (al menos tan alto como lo que cuelga la portada)
        Column(Modifier.fillMaxWidth().heightIn(min = colgada + 14.dp).padding(start = 18.dp, end = tapaW + 34.dp)) {
            if (etiqueta != null) Text(etiqueta.uppercase(), color = SobreBloque, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(y = (-14).dp).background(AcentoBloque).padding(horizontal = 10.dp, vertical = 6.dp))
            FitTitle(titulo.uppercase(), 32.sp, 32.sp, color = Charcoal, family = Grotesca, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp, minSize = 18f, conEdicion = false,
                modifier = Modifier.fillMaxWidth().padding(top = if (etiqueta != null) 0.dp else 14.dp))
            if (linea.isNotBlank()) Text(linea, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (principal != null) BotonC(principal.first, Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp), fondo = Acento, letra = SobreAcento, onClick = principal.second)
        Spacer(Modifier.height(10.dp))
    }
}

private fun lerpCo(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** El botón principal de una ficha según su categoría: jugar en Bastet, leer en Seshat, ver en Hathor (o nada). */
fun principalDe(ctx: Context, kind: Kind, id: Long): Pair<String, () -> Unit>? = when {
    kind.isGame && Hermanas.instalada(ctx, Hermanas.PAQ_HIEDRA) -> "Jugar en Bastet" to { Hermanas.jugarEnHiedra(ctx, id); Unit }
    kind.isBook && Hermanas.instalada(ctx, Hermanas.PAQ_MARFIL) -> "Leer en Seshat" to { Hermanas.leerEnMarfil(ctx, id); Unit }
    (kind.isMovie || kind.isTv) && Hermanas.instalada(ctx, Hermanas.PAQ_MOSTAZA) -> "Ver en Hathor" to { Hermanas.verEnMostaza(ctx, CosasMostaza.refDe(ctx, kind, id)); Unit }
    else -> null
}

/**
 * El encuadre del fondo de apertura, solo en este dispositivo: se arrastra la foto dentro del marco (de la misma forma que la apertura en
 * esta pantalla) y se guarda. «Centrar» lo deja como estaba.
 */
@Composable
fun EditorEncuadreFondo(imagen: String, item: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val inicio = remember(imagen, item) { EncuadreFondo.de(ctx, item, imagen) }
    var x by remember { mutableFloatStateOf(inicio.first) }
    var y by remember { mutableFloatStateOf(inicio.second) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().background(Paper).padding(vertical = 18.dp)) {
            Text("Encuadrar el fondo", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 18.dp))
            Text("Arrastra la foto para elegir qué parte se ve. Solo para este dispositivo: en los demás eliges el suyo (tienen otra forma). La imagen es la misma en todos.",
                color = Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(ALTO_APERTURA).clipToBounds().background(Sand).semantics { contentDescription = "Marco del fondo" }
                .pointerInput(Unit) {
                    detectDragGestures { ch, d ->
                        ch.consume()
                        x = (x - d.x / size.width * 2f).coerceIn(-1f, 1f)
                        y = (y - d.y / size.height * 2f).coerceIn(-1f, 1f)
                    }
                }) {
                AsyncImage(imagen, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = BiasAlignment(x, y), colorFilter = Ediciones.filtro)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                EnlaceRevista("Centrar") { x = 0f; y = 0f }
                Spacer(Modifier.weight(1f))
                EnlaceRevista("Cancelar", onClick = onDismiss)
                EnlaceRevista("Guardar", acento = true) { EncuadreFondo.pon(ctx, item, imagen, x, y); onDismiss() }
            }
        }
    }
}
