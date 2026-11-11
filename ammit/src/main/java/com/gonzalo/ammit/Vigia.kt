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

    companion object {
        /** El vigilante, si está activado (para poner la cortina de cadenas por encima de todo). */
        @Volatile var vivo: Vigia? = null
    }

    override fun onServiceConnected() {
        Ammit.cargar(this); vivo = this
        // Las cadenas, grabadas ya, para que la primera animación vaya fluida
        android.os.Handler(mainLooper).post { runCatching { Campo.dePantalla(this).precalentar() } }
    }
    override fun onUnbind(intent: Intent?): Boolean { vivo = null; Cortina.quitarYa(); return super.onUnbind(intent) }
    override fun onDestroy() { vivo = null; Cortina.quitarYa(); super.onDestroy() }

    /** Pone la cortina de cadenas ([Cortina]): «entrar» o «salir». */
    fun cortina(modo: String): Boolean = Cortina.poner(this, modo)

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        // Apagada, no hace nada más que esta comprobación (el estado está en memoria: encender y apagar pasan por este mismo proceso)
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !Ammit.encendida) return
        // (con una cortina puesta, lo que pase debajo es cosa de Ammit)
        if (Cortina.ocupada) return
        val paquete = e.packageName?.toString() ?: return
        // Tu pantalla de inicio de siempre (Ammit no tiene por qué serlo): encendida, en su lugar, la de Ammit; la cortina de cadenas tapa
        // el cambio (es igual que su fondo), así no se ve tu inicio ni un instante
        val casa = inicioActual()
        if (casa != null && casa != Ammit.PROPIO && paquete == casa) {
            if (!Cortina.ocupada && !Cortina.poner(this, "casa")) irAAmmit()
            return
        }
        if (Ammit.pasa(paquete, Ammit.teclados(this))) return
        // Solo las pantallas de verdad (actividades): no los avisos, menús o paneles que una app pinta por encima
        val clase = e.className?.toString() ?: return
        val esPantalla = runCatching { packageManager.getActivityInfo(ComponentName(paquete, clase), 0); true }.getOrDefault(false)
        if (!esPantalla) return
        // (la misma app dos veces seguidas, en un momento: un solo juicio)
        val ahora = SystemClock.elapsedRealtime()
        if (paquete == ultimo && ahora - cuando < 2_000) return
        ultimo = paquete; cuando = ahora
        val nombre = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(paquete, 0)).toString() }.getOrDefault(paquete)
        // El juicio, en la cortina por encima de todo (si no se pudiera, en su pantalla, a la antigua)
        if (!Cortina.poner(this, "juicio", nombre)) JuicioActivity.abrir(this, nombre)
    }

    private fun irAAmmit() = runCatching {
        startActivity(Intent(this, InicioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    /** La pantalla de inicio que tiene puesta la tablet (la de siempre, o Ammit si la elegiste). */
    private fun inicioActual(): String? = runCatching {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    override fun onInterrupt() {}
}
