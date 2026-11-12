package com.gonzalo.ammit

import android.content.Context

/**
 * Los fallos de Ammit, apuntados en el dispositivo para verlos en sus ajustes: si se cierra por un error (y Android da el vigilante por
 * «no funcionando»), queda dicho qué fue y dónde. Solo en este dispositivo; no va a ningún sitio.
 */
object Fallos {
    private fun p(c: Context) = c.getSharedPreferences("ammit_fallos", Context.MODE_PRIVATE)

    /** Un fallo que se ha podido parar (no ha cerrado Ammit). */
    fun apunta(c: Context, donde: String, e: Throwable) = runCatching {
        p(c).edit().putString("ultimo", "${java.text.SimpleDateFormat("d/M HH:mm", java.util.Locale("es", "ES")).format(java.util.Date())} · $donde · " +
            (e.stackTraceToString().take(1500))).apply()
    }

    /** Los que cierran Ammit: se apuntan (de golpe, que el proceso se va) y siguen su camino. */
    fun vigila(c: Context) {
        val app = c.applicationContext
        val antes = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                p(app).edit().putString("ultimo", "${java.text.SimpleDateFormat("d/M HH:mm", java.util.Locale("es", "ES")).format(java.util.Date())} · se cerró (${t.name}) · " +
                    e.stackTraceToString().take(1500)).commit()
            }
            antes?.uncaughtException(t, e)
        }
    }

    fun ultimo(c: Context): String = p(c).getString("ultimo", "").orEmpty()
    fun olvida(c: Context) { p(c).edit().clear().apply() }
}
