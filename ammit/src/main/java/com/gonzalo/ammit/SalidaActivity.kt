package com.gonzalo.ammit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.core.view.WindowCompat

/**
 * Al apagar Ammit: una capa transparente encima de tu pantalla de inicio de siempre (que ya está abierta debajo). Empieza tapada con el
 * mismo sinfín de cadenas del fondo de Ammit y las cadenas se van una tras otra, por donde vinieron, hasta que se ve tu inicio. Luego se
 * cierra sola.
 */
class SalidaActivity : Activity() {
    companion object {
        /** Abre tu inicio de siempre y, encima, esta capa (las dos a la vez, para que no asome tu inicio antes de las cadenas). */
        fun abrir(a: Activity, inicio: android.content.ComponentName): Boolean = runCatching {
            val casa = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(inicio)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            val capa = Intent(a, SalidaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            a.startActivities(arrayOf(casa, capa))
            @Suppress("DEPRECATION") a.overridePendingTransition(0, 0)
            true
        }.getOrDefault(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContentView(VistaSalida(this) { cerrar() })
    }

    private var cerrada = false
    private fun cerrar() {
        if (cerrada) return
        cerrada = true
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    @Deprecated("Mientras se van las cadenas, atrás no hace nada")
    override fun onBackPressed() {}

    // Si te vas a otra cosa a medias, se quita sin más
    override fun onStop() { super.onStop(); cerrar() }
}

/** El campo de cadenas yéndose, dibujado a cada fotograma de la pantalla. */
private class VistaSalida(c: Context, val alAcabar: () -> Unit) : View(c) {
    private var t0 = -1L
    private var hecho = false

    override fun onDraw(cv: Canvas) {
        if (width == 0 || height == 0) return
        val campo = Campo.dePantalla(context)
        if (t0 < 0) t0 = SystemClock.uptimeMillis()
        val ms = (SystemClock.uptimeMillis() - t0).toFloat()
        val n = campo.n
        val (dx, dy) = Tiempos.temblor(ms, Tiempos.golpesSalir(n))
        cv.save(); cv.translate(dx * campo.u, dy * campo.u)
        campo.pinta(cv, Campo.OSCURO, Tiempos.salirBase(ms)) { k -> Tiempos.salir(ms, k, n) }
        cv.restore()
        if (ms < Tiempos.salidaFin(n)) postInvalidateOnAnimation()
        else if (!hecho) { hecho = true; post(alAcabar) }
    }
}
