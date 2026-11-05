package com.gonzalo.cobalto

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Las piezas de Atrio que usa el estudio de Escribir (Components.kt, Vitrina.kt, Form.kt, Theme.kt de Atrio), con los mismos nombres y
 * hechas con las de la revista de aquí: así los archivos Escribir*.kt son los de Atrio tal cual y se actualizan copiándolos.
 */

/** El acento (en Atrio es el color de la nota general). */
val ColGeneral: Color get() = Acento

/** Esquinas según la forma del tema. */
fun shp(r: Dp): Shape = esquina(r)
fun pillShape(): Shape = pildora()

fun largo(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

/** De dos colores, el que mejor se lee sobre [fondo]. */
fun sobre(fondo: Color, a: Color, b: Color): Color {
    val l = fondo.luminance()
    return if (kotlin.math.abs(a.luminance() - l) >= kotlin.math.abs(b.luminance() - l)) a else b
}

/** En Atrio, el hueco del dock flotante; aquí no hay dock: solo la barra de navegación de Android y un poco de aire. */
@Composable
fun dockSpace(extra: Dp = 16.dp): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + extra

/** Un toque (y, si se quiere, una pulsación larga), sin el muelle de Atrio pero con su vibración suave. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.press(haptic: Boolean = true, onLong: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val hf = LocalHapticFeedback.current
    this.combinedClickable(
        interactionSource = src, indication = null,
        onLongClick = onLong?.let { l -> { hf.performHapticFeedback(HapticFeedbackType.LongPress); l() } },
        onClick = { if (haptic) hf.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
    )
}

/** Barra de progreso fina (en la revista, todas en tinta salvo que se pida un color). */
@Composable
fun Bar(frac: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).background(Line)) { Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(color)) }
}

/** Filtro de una sola palabra, como el de la revista. */
@Composable
fun SoftChip(text: String, selected: Boolean, modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") onPanel: Boolean = false, onClick: () -> Unit) =
    FiltroRevista(text, selected, modifier, onClick)

/** Botón de icono: en la revista, solo el icono. */
@Composable
fun IconPill(icon: ImageVector, desc: String, active: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).semantics { role = Role.Button; contentDescription = desc }.press(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (active) Acento else Charcoal, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun BackButton(onBack: () -> Unit) = VolverRevista(onBack)

/** Cabecera de pantalla: «← VOLVER», la sección en acento y el título grande con filete grueso; botones a la derecha. */
@Composable
fun TopBar(back: String, onBack: () -> Unit, title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            VolverRevista(onBack); Spacer(Modifier.weight(1f)); actions()
        }
        Text(back.uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        FitTitle(title, 44.sp, 46.sp, letterSpacing = (-1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Filete(3.dp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Titular enorme con filete grueso debajo, una línea pequeña encima y botones a la derecha. */
@Composable
fun BigHeader(title: String, overline: String? = null, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (overline != null) Text(overline.uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        FitTitle(title, 48.sp, 50.sp, letterSpacing = (-1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Filete(3.dp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Con la app cerrada o sin red: una tarea que sube lo pendiente de Escribir en cuanto haya conexión (el CloudSave de Atrio). */
object NubeTarea {
    fun encolar(ctx: android.content.Context, retraso: Long = 0L) = runCatching {
        val req = androidx.work.OneTimeWorkRequestBuilder<NubeWorker>()
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .setInitialDelay(retraso, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        androidx.work.WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork("nube", androidx.work.ExistingWorkPolicy.REPLACE, req)
    }
}

class NubeWorker(ctx: android.content.Context, p: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Cuenta.cargar(c)
        if (!Cuenta.vinculada) return Result.success()
        val ok = Escritos.sincroniza(c)
        // Un capítulo que se tocó hace menos de 20 s queda diferido: se vuelve a intentar en un rato (aquí, no en la app, que puede estar congelada)
        val diferido = if (ok) runCatching { Publicar.todo(c) }.getOrDefault(false) else false
        return if ((!ok || diferido) && runAttemptCount < 5) Result.retry() else Result.success()
    }
}
