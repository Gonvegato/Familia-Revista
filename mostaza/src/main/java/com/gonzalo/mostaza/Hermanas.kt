package com.gonzalo.mostaza

import android.content.Context
import android.content.Intent

/** Las hermanas de la familia: abrirlas, y llevar a Azabache a la ficha de lo que estás viendo (para valorarlo). */
object Hermanas {
    const val AZABACHE = "com.gonzalo.gamerate"
    val TODAS = listOf("Azabache" to AZABACHE, "Hiedra" to "com.gonzalo.hiedra", "Marfil" to "com.gonzalo.marfil", "Cobalto" to "com.gonzalo.cobalto")

    fun instalada(c: Context, paquete: String) = runCatching { c.packageManager.getLaunchIntentForPackage(paquete) != null }.getOrDefault(false)
    fun azabache(c: Context) = instalada(c, AZABACHE)

    fun abre(c: Context, paquete: String): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(paquete) ?: return false
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }

    /** Azabache, en la ficha de ese título (Azabache 1.6.3 entiende «go» = «titulo:<ref>»). */
    fun abreEnAzabache(c: Context, ref: String): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(AZABACHE) ?: return false
        i.putExtra("go", "titulo:$ref")
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }
}
