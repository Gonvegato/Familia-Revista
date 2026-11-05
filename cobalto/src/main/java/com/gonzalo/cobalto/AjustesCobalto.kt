package com.gonzalo.cobalto

import android.content.Context

/** Lo de Cobalto que se guarda en el móvil y no viaja: por ahora, nada más que el último estado del estudio. */
object AjustesCobalto {
    private fun p(c: Context) = c.getSharedPreferences("cobalto_ajustes", Context.MODE_PRIVATE)
    /** La última obra abierta (para «Sigue escribiendo» cuando no hay escenas). */
    fun ultimaObra(c: Context): String = p(c).getString("ultimaObra", "").orEmpty()
    fun ponUltimaObra(c: Context, id: String) = p(c).edit().putString("ultimaObra", id).apply()
}
