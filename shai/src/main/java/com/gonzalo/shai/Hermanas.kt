package com.gonzalo.shai

import android.content.Context
import android.content.Intent

/** Las hermanas de la familia: saber si están y abrirlas (desde Ajustes → Cuenta). */
object Hermanas {
    const val AZABACHE = "com.gonzalo.gamerate"
    // (Shai no sale en Jonsu: Jonsu no lee nada de Shai; aquí solo se abren las hermanas)
    val TODAS = listOf("Jonsu" to AZABACHE, "Hathor" to "com.gonzalo.mostaza", "Nut" to "com.gonzalo.cobre", "Bastet" to "com.gonzalo.hiedra", "Seshat" to "com.gonzalo.marfil", "Thot" to "com.gonzalo.cobalto", "Ptah" to "com.gonzalo.ptah")

    fun instalada(c: Context, paquete: String) = runCatching { c.packageManager.getLaunchIntentForPackage(paquete) != null }.getOrDefault(false)

    fun abre(c: Context, paquete: String): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(paquete) ?: return false
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }
}
