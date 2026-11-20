package com.gonzalo.cobre

import android.content.Context
import android.content.Intent

/** Las hermanas de la familia: saber si están y abrirlas (desde Ajustes → Cuenta). */
object Hermanas {
    const val AZABACHE = "com.gonzalo.gamerate"
    val TODAS = listOf("Polaris" to AZABACHE, "Sirius" to "com.gonzalo.mostaza", "Orion" to "com.gonzalo.hiedra", "Lyra" to "com.gonzalo.marfil", "Nova" to "com.gonzalo.cobalto")

    /** Para abrir [paquete]: con Seshat, la que haya en el móvil (la normal o SesKobo, que hace de Seshat). */
    fun lanzador(c: Context, paquete: String): android.content.Intent? = runCatching { c.packageManager.getLaunchIntentForPackage(com.gonzalo.familia.LaFamilia.instalada(c, paquete)) }.getOrNull()
    fun instalada(c: Context, paquete: String) = lanzador(c, paquete) != null

    fun abre(c: Context, paquete: String): Boolean {
        val i = lanzador(c, paquete) ?: return false
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }
}
