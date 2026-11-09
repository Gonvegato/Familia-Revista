package com.gonzalo.ptah

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * El minutero: los widgets de reloj llevan la hora dibujada con la letra de verdad del diseño (un `TextClock` en un widget no respeta la
 * letra en todos los lanzadores: en ColorOS sale la del sistema), así que hay que volver a pintarlos cada minuto.
 *
 * Cómo: una alarma **exacta** al empezar cada minuto (`setExact`, del tipo `RTC`: no despierta al móvil). Con la pantalla apagada no salta
 * (no gasta nada); al encenderla, la que estaba pendiente salta enseguida y el reloj se pone en hora. Solo se repintan los widgets de reloj.
 * Coste: un dibujo y un envío al lanzador por minuto y por reloj con la pantalla encendida (`MinuteroTest` mide el dibujo).
 *
 * El permiso: `USE_EXACT_ALARM` (Android 13 o más; se concede al instalar, sin preguntar: Google Play lo limita a las apps de reloj y
 * alarmas, pero Widgets se instala desde tu kDrive, no desde Play) y `SCHEDULE_EXACT_ALARM` en Android 12 (concedido de serie, se puede
 * quitar en Ajustes del móvil). En Android 10 y 11 no hace falta. Si no se pueden poner alarmas exactas (o eliges en Ajustes «con la letra
 * del móvil»), la hora va como `TextClock`/`AnalogClock` ([Vivo]): siempre en hora, pero con la letra del sistema.
 */
object Minutero {
    const val ACCION = "com.gonzalo.ptah.MINUTO"

    fun esReloj(d: Diseno) = d.tipo == RelojDigital.id || d.tipo == RelojAgujas.id

    private fun p(ctx: Context) = ctx.getSharedPreferences("ptah_reloj", Context.MODE_PRIVATE)

    /** «La hora con la letra del móvil» (Ajustes → Avanzado): es de este móvil, no viaja. */
    fun letraDelMovil(ctx: Context) = p(ctx).getBoolean("letra_movil", false)
    fun ponLetraDelMovil(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("letra_movil", v).apply(); Widgets.pedir(ctx) }

    /** ¿Deja Android poner alarmas exactas? */
    fun puedeExactas(ctx: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        runCatching { ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true }.getOrDefault(false)

    /** ¿La hora va como `TextClock`/`AnalogClock` (con la letra del sistema) en vez de dibujada? */
    fun relojVivo(ctx: Context) = letraDelMovil(ctx) || !puedeExactas(ctx)

    /** Los widgets que llevan un reloj. */
    fun relojes(ctx: Context): List<Int> = Widgets.ids(ctx).filter { id -> Widgets.disenoDe(ctx, id)?.let { Disenos.de(ctx, it) }?.let(::esReloj) == true }

    /** El próximo minuto en punto (ms). */
    fun proximo(ahora: Long = System.currentTimeMillis()): Long = (ahora / 60_000L + 1) * 60_000L + 150L

    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 11, Intent(ctx, WPtah::class.java).setAction(ACCION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Pone la alarma del próximo minuto si hay relojes dibujados (y la quita si no). Devuelve si la ha puesto. */
    fun programa(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        val pi = pi(ctx)
        if (relojVivo(ctx) || relojes(ctx).isEmpty()) { am.cancel(pi); return false }
        return try { am.setExact(AlarmManager.RTC, proximo(), pi); true }
        catch (e: SecurityException) { am.cancel(pi); false }
    }

    /** Por qué la hora va ahora con la letra del móvil (null: va dibujada, con la letra del diseño). */
    fun motivo(ctx: Context): String? = when {
        letraDelMovil(ctx) -> "lo has elegido en Ajustes → General («con la letra del móvil»)"
        !puedeExactas(ctx) -> "Android no deja a Widgets poner alarmas exactas (canScheduleExactAlarms = no): falta el permiso «Alarmas y recordatorios»"
        else -> null
    }

    /** ¿Widgets tiene limitada la actividad en segundo plano? (entonces el minutero puede no llegar) */
    fun restringida(ctx: Context): Boolean = runCatching { ctx.getSystemService(android.app.ActivityManager::class.java)?.isBackgroundRestricted == true }.getOrDefault(false)
    fun sinOptimizar(ctx: Context): Boolean = runCatching { ctx.getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true }.getOrDefault(false)

    /**
     * ¿El minutero se ha quedado atrás? (hay relojes dibujados y la última vez que se pusieron en hora fue hace más de 10 minutos, con la
     * pantalla encendida: el ahorro de batería no deja llegar las alarmas). Se mira al abrir Widgets.
     */
    fun atrasado(ctx: Context): Boolean {
        if (relojVivo(ctx) || relojes(ctx).isEmpty()) return false
        val u = ultimo(ctx)
        return u > 0 && System.currentTimeMillis() - u > 10 * 60_000L
    }

    /**
     * Las pantallas de ColorOS (OPPO, Realme, OnePlus) para dejar a una app arrancar sola y trabajar en segundo plano (cambian con cada
     * versión: se prueban por orden); si ninguna está, el detalle de la app en Ajustes.
     */
    val COLOROS = listOf(
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.coloros.oppoguardelf" to "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
        "com.coloros.oppoguardelf" to "com.coloros.powermanager.fuelgaue.PowerSaverModeActivity",
    )

    fun abreInicioAutomatico(ctx: Context): Boolean {
        for ((p, c) in COLOROS) {
            val i = Intent().setClassName(p, c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { ctx.startActivity(i) }.isSuccess) return true
        }
        return runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    fun abreAlarmas(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 31 &&
        runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    /** Pide que la batería no limite a Widgets (el cuadro de Android; si no sale, la lista de apps de la batería). */
    @android.annotation.SuppressLint("BatteryLife")
    fun abreBateria(ctx: Context): Boolean =
        runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess ||
            runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    /** Cuándo se repintó la hora por última vez (para Ajustes: si se queda atrás, algo lo frena). */
    fun apunta(ctx: Context) { p(ctx).edit().putLong("ultimo", System.currentTimeMillis()).apply() }
    fun ultimo(ctx: Context) = p(ctx).getLong("ultimo", 0L)
}
