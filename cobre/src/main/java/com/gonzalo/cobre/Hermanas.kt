package com.gonzalo.cobre

import android.content.Context
import android.content.Intent

/** Las hermanas de la familia: saber si están y abrirlas (desde Ajustes → Cuenta). */
object Hermanas {
    const val AZABACHE = "com.gonzalo.gamerate"
    val TODAS = listOf("Azabache" to AZABACHE, "Mostaza" to "com.gonzalo.mostaza", "Hiedra" to "com.gonzalo.hiedra", "Marfil" to "com.gonzalo.marfil", "Cobalto" to "com.gonzalo.cobalto")

    fun instalada(c: Context, paquete: String) = runCatching { c.packageManager.getLaunchIntentForPackage(paquete) != null }.getOrDefault(false)

    fun abre(c: Context, paquete: String): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(paquete) ?: return false
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }
}
