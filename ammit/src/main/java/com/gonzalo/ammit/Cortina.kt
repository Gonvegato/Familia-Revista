package com.gonzalo.ammit

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowManager

/**
 * La cortina de cadenas: una capa del vigilante (servicio de accesibilidad) que se pinta por encima de TODO, también mientras Android
 * cambia de una pantalla a otra. Así, al encender y al apagar, lo que pasa debajo (abrirse un inicio u otro) no se ve nunca: ni
 * parpadeos ni destellos.
 *
 * - Encender: las cadenas tapan lo que tengas delante (tu inicio de siempre, una app…); tapado del todo, debajo se abre la pantalla de
 *   inicio de Ammit, que tiene ese mismo campo de fondo; cuando está pintada, la cortina se quita (no se nota: es igual) y salen las apps.
 * - Apagar: la cortina aparece con el campo entero (igual que el fondo de Ammit), debajo se abre tu inicio de siempre, las cadenas dan
 *   un tirón y se van una a una, por donde vinieron.
 */
object Cortina {
    /** La pantalla de inicio de Ammit ya está pintada debajo (la cortina se puede quitar). */
    @Volatile var inicioListo = false
    /** Lo que hace la pantalla de inicio cuando se quita la cortina (sacar las apps). */
    @Volatile var alQuitar: (() -> Unit)? = null

    /** Enciende o apaga Ammit con la cortina. Devuelve false si el vigilante no está (entonces se hace a la antigua). */
    fun cambiar(c: Context, on: Boolean, abrir: (Intent) -> Unit): Boolean {
        val v = Vigia.vivo ?: return false
        if (on) {
            Ammit.encender(c, true, animar = false)
            BotonAmmit.avisar(c)
            if (!v.cortina("entrar")) return false
            // Plegar la barra de arriba sin abrir nada (lo de debajo se queda; las cadenas lo tapan)
            abrir(Intent(c, PlegarActivity::class.java))
        } else {
            val inicio = Ammit.inicioNormal(c)
            Ammit.encender(c, false, animar = false)
            BotonAmmit.avisar(c)
            if (!v.cortina("salir")) return false
            abrir(if (inicio != null) Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(inicio).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                  else Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        }
        return true
    }

    /** Pone la capa (desde el vigilante). */
    fun poner(s: AccessibilityService, modo: String): Boolean = runCatching {
        val wm = s.getSystemService(WindowManager::class.java)
        inicioListo = false
        val vista = VistaCortina(s, modo)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT)
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        lp.windowAnimations = 0
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        if (Build.VERSION.SDK_INT >= 30) lp.fitInsetsTypes = 0
        // (del tamaño de la pantalla entera, como el campo)
        val (w, h) = Campo.pantalla(s); lp.width = w; lp.height = h
        vista.quitar = { runCatching { wm.removeView(vista) } }
        wm.addView(vista, lp)
        true
    }.getOrDefault(false)
}

/** Pliega la barra de arriba y se va: no enseña nada. */
class PlegarActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}

/** Lo que se ve en la cortina, fotograma a fotograma. */
private class VistaCortina(private val c: Context, private val modo: String) : View(c) {
    var quitar: () -> Unit = {}
    private var t0 = -1L
    private var hecho = false
    private var pedido = false
    private val capa = Paint()

    private fun fin() {
        if (hecho) return
        hecho = true
        post {
            quitar()
            Cortina.alQuitar?.invoke()
        }
    }

    override fun onDraw(cv: Canvas) {
        if (hecho) return
        val (w, h) = Campo.pantalla(c)
        val campo = Campo.de(w, h)
        if (t0 < 0) t0 = SystemClock.uptimeMillis()
        val ms = (SystemClock.uptimeMillis() - t0).toFloat()
        val n = campo.n
        if (modo == "entrar") {
            val (dx, dy) = Tiempos.temblor(ms, Tiempos.golpesEntrar(n))
            cv.save(); cv.translate(dx * campo.u, dy * campo.u)
            campo.pinta(cv, Tiempos.entrarOscuro(ms), Tiempos.entrarBase(ms)) { k -> Tiempos.entrar(ms, k) }
            cv.restore()
            // Tapado del todo: debajo, la pantalla de inicio de Ammit (con el mismo campo de fondo)
            if (ms >= 1480 && !pedido) {
                pedido = true
                Ammit.ponAnimacion(c, "cortina")
                runCatching { c.startActivity(Intent(c, InicioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)) }
            }
            // Se quita cuando ya está apagada como el fondo y la pantalla de inicio está pintada debajo (o, por si acaso, a los 5 s)
            if ((ms >= 1950 && Cortina.inicioListo) || ms > 5000) { fin(); return }
        } else {
            // 0–150: aparece el campo entero (sobre la pantalla de Ammit no se nota: es su fondo); 200–420: el tirón; luego se van
            val a = Tiempos.clamp(ms / 150)
            val tiron = if (ms in 200f..420f) 14f * kotlin.math.sin((ms - 200) / 220 * Math.PI.toFloat()) else 0f
            val r = ms - 420
            val (dx, dy) = if (r > 0) Tiempos.temblor(r, Tiempos.golpesSalir(n)) else (tiron * 0.5f * kotlin.math.sin(ms * 0.9f)) to (tiron * 0.5f * kotlin.math.sin(ms * 1.13f))
            if (a < 1f) { capa.alpha = (255 * a).toInt(); cv.saveLayer(null, capa) } else cv.save()
            cv.translate(dx * campo.u, dy * campo.u)
            val fondo = campo.fondoSiHay()
            if (r <= 0f && fondo != null) cv.drawBitmap(fondo, 0f, 0f, null)
            else campo.pinta(cv, Campo.OSCURO, Tiempos.salirBase(r)) { k -> Tiempos.salir(r, k, n) }
            cv.restore()
            if (r >= Tiempos.salidaFin(n)) { fin(); return }
        }
        postInvalidateOnAnimation()
    }
}
