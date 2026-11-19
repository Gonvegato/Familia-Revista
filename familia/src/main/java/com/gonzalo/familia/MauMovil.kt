package com.gonzalo.familia

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gonzalo.mau.Guardado
import com.gonzalo.mau.Lugar
import com.gonzalo.mau.ui.Mau
import com.gonzalo.mau.ui.MauCapa
import com.gonzalo.mau.ui.MauCasa
import java.lang.ref.WeakReference

/**
 * Mau en el móvil: se pone encima de una actividad ([pon]) y desde ahí acompaña. Cada app de la suite lo pone en sus pantallas (la
 * principal, el lector, el reproductor…); las pantallas dicen dónde está con [MauLugar].
 */
object MauMovil {
    /** El extra con el que una hermana abre esta app desde el menú de Mau: Mau llega con ella. */
    const val EXTRA_LLEGA = "com.gonzalo.familia.mau.llega"

    /** Las apps de la suite y su paquete (Seshat: la que esté, la normal o SesKobo). */
    private fun paquete(ctx: Context, app: String): String? = when (app) {
        "marfil" -> LaFamilia.instalada(ctx, LaFamilia.SESHAT)
        "hiedra" -> "com.gonzalo.hiedra"
        "cobalto" -> "com.gonzalo.cobalto"
        "azabache" -> "com.gonzalo.gamerate"
        "mostaza" -> "com.gonzalo.mostaza"
        "cobre" -> "com.gonzalo.cobre"
        else -> null
    }

    private var vista: WeakReference<View>? = null
    private val principal = Handler(Looper.getMainLooper())

    private class Casa(private val ctx: Context, override val app: String) : MauCasa {
        override val ordenador = false
        override val guardado: Guardado = object : Guardado {
            private val p = ctx.getSharedPreferences("mau", Context.MODE_PRIVATE)
            override fun lee(clave: String): String? = p.getString(clave, null)
            override fun guarda(clave: String, valor: String?) { p.edit().apply { if (valor == null) remove(clave) else putString(clave, valor) }.apply() }
        }
        override val sinAnimaciones: Boolean
            get() = runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

        override fun instalada(app: String): Boolean {
            if (app == this.app) return true
            val p = paquete(ctx, app) ?: return false
            return runCatching { ctx.packageManager.getLaunchIntentForPackage(p) != null }.getOrDefault(false)
        }

        override fun abre(app: String): Boolean {
            val p = paquete(ctx, app) ?: return false
            val i = runCatching { ctx.packageManager.getLaunchIntentForPackage(p) }.getOrNull() ?: return false
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.putExtra(EXTRA_LLEGA, true)
            return runCatching { ctx.startActivity(i); true }.getOrDefault(false)
        }

        // Un ronroneo: toques suaves y seguidos durante un segundo y medio (sin permiso de vibrar: los de la pantalla)
        override fun ronronea() {
            val v = vista?.get() ?: return
            for (i in 0 until 16) principal.postDelayed({ v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, i * 95L)
        }

        override fun tic() { vista?.get()?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }

        override fun enPrincipal(f: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) f() else principal.post(f) }
    }

    /** Arranca Mau en esta app (una vez por proceso). [app]: «marfil», «hiedra», «cobalto», «azabache», «mostaza» o «cobre». */
    fun inicia(ctx: Context, app: String) = Mau.inicia(Casa(ctx.applicationContext, app))

    /**
     * Pone a Mau encima de [actividad] (después de su setContent / setContentView). [lugar]: el de la pantalla entera (el lector:
     * LEYENDO). [margenAbajo]: lo que ocupa abajo la app y Mau no debe tapar.
     */
    fun pon(actividad: ComponentActivity, app: String, lugar: Lugar = Lugar.CASA, margenAbajo: Dp = 0.dp) {
        inicia(actividad, app)
        Mau.base(actividad, lugar)
        miraSiLlega(actividad.intent)
        // Cada toque o tecla en la pantalla cuenta como «haces algo» (sin quitárselo a la app)
        val orig = actividad.window.callback
        if (orig !is Oyente) actividad.window.callback = Oyente(orig)
        val cv = ComposeView(actividad).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                var activa by remember { mutableStateOf(actividad.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
                DisposableEffect(Unit) {
                    val o = LifecycleEventObserver { _, e ->
                        when (e) {
                            Lifecycle.Event.ON_RESUME -> { activa = true; vista = WeakReference(this@apply) }
                            Lifecycle.Event.ON_PAUSE -> activa = false
                            Lifecycle.Event.ON_DESTROY -> Mau.olvida(actividad)
                            else -> {}
                        }
                    }
                    actividad.lifecycle.addObserver(o)
                    onDispose { actividad.lifecycle.removeObserver(o) }
                }
                MauCapa(actividad, activa = activa, margenFijo = margenAbajo, atras = { on, f -> BackHandler(on, f) })
            }
            // Mau no coge nunca el foco (el mando de la tele, el teclado): se le toca con el dedo, y lo demás sigue igual
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isFocusable = false
        }
        actividad.addContentView(cv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        vista = WeakReference(cv)
    }

    /** Llámalo también en onNewIntent: si la abrió una hermana desde el menú de Mau, Mau llega con ella. */
    fun miraSiLlega(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_LLEGA, false) == true) {
            i.removeExtra(EXTRA_LLEGA)
            Mau.llega = true
        }
    }

    private class Oyente(private val o: Window.Callback) : Window.Callback by o {
        override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
            if (event?.actionMasked == MotionEvent.ACTION_DOWN) Mau.toque()
            return o.dispatchTouchEvent(event)
        }
        override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
            if (event?.action == KeyEvent.ACTION_DOWN) Mau.toque()
            return o.dispatchKeyEvent(event)
        }
    }
}

/** La actividad de un contexto de Compose. */
private fun Context.actividad(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Mientras esta pantalla se ve, Mau está en [lugar] (leyendo, escribiendo, viendo…). */
@Composable
fun MauLugar(lugar: Lugar) {
    val a = LocalContext.current.actividad() ?: return
    DisposableEffect(a, lugar) {
        val f = Mau.empuja(a, lugar)
        onDispose { Mau.quita(a, f) }
    }
}

/** Mientras esta pantalla se ve, Mau no sale (un juego, un mando, la sala de la tele). */
@Composable
fun MauFuera() {
    val a = LocalContext.current.actividad() ?: return
    DisposableEffect(a) {
        Mau.sal(a)
        onDispose { Mau.vuelveA(a) }
    }
}

/** Mientras esta pantalla se ve, ocupa [alto] abajo (una banda, unos controles): Mau se sienta encima, sin taparlo. */
@Composable
fun MauMargenAbajo(alto: Dp) {
    val a = LocalContext.current.actividad() ?: return
    DisposableEffect(a, alto) {
        val f = Mau.ponMargen(a, alto.value)
        onDispose { Mau.quitaMargen(a, f) }
    }
}
