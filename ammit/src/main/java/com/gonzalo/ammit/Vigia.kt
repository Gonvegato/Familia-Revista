package com.gonzalo.ammit

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * El vigilante (servicio de accesibilidad). Con Ammit encendida, cada vez que pasa a estar delante una pantalla de una app que no es de
 * la familia, la tapa con el juicio ([JuicioActivity]) y vuelve a la pantalla de inicio. Solo mira qué app y qué pantalla se abre (los
 * cambios de ventana), nunca lo que hay dentro. Pasa también la pantalla de inicio que tengas puesta (si no fuera Ammit, para no
 * entrar en un bucle) y los teclados.
 */
class Vigia : AccessibilityService() {
    private var ultimo = ""
    private var cuando = 0L

    override fun onServiceConnected() { Ammit.cargar(this) }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        Ammit.cargar(this)
        if (!Ammit.encendida) return
        val paquete = e.packageName?.toString() ?: return
        if (Ammit.pasa(paquete, Ammit.teclados(this)) || paquete == inicioActual()) return
        // Solo las pantallas de verdad (actividades): no los avisos, menús o paneles que una app pinta por encima
        val clase = e.className?.toString() ?: return
        val esPantalla = runCatching { packageManager.getActivityInfo(ComponentName(paquete, clase), 0); true }.getOrDefault(false)
        if (!esPantalla) return
        // (la misma app dos veces seguidas, en un momento: un solo juicio)
        val ahora = SystemClock.elapsedRealtime()
        if (paquete == ultimo && ahora - cuando < 2_000) return
        ultimo = paquete; cuando = ahora
        val nombre = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(paquete, 0)).toString() }.getOrDefault(paquete)
        JuicioActivity.abrir(this, nombre)
    }

    /** La pantalla de inicio que tiene puesta la tablet (normalmente Ammit). */
    private fun inicioActual(): String? = runCatching {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    override fun onInterrupt() {}
}
