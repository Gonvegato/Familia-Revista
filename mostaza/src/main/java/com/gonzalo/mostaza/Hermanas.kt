package com.gonzalo.mostaza

import android.content.Context
import android.content.Intent

/** Las hermanas de la familia: abrirlas, y llevar a Azabache a la ficha de lo que estás viendo (para valorarlo). */
object Hermanas {
    const val AZABACHE = "com.gonzalo.gamerate"
    val TODAS = listOf("Jonsu" to AZABACHE, "Bastet" to "com.gonzalo.hiedra", "Seshat" to "com.gonzalo.marfil", "Thot" to "com.gonzalo.cobalto")

    /** Para abrir [paquete]: con Seshat, la que haya en el móvil (la normal o SesKobo, que hace de Seshat). */
    fun lanzador(c: Context, paquete: String): android.content.Intent? = runCatching { c.packageManager.getLaunchIntentForPackage(com.gonzalo.familia.LaFamilia.instalada(c, paquete)) }.getOrNull()
    fun instalada(c: Context, paquete: String) = lanzador(c, paquete) != null
    fun azabache(c: Context) = instalada(c, AZABACHE)

    fun abre(c: Context, paquete: String): Boolean {
        val i = lanzador(c, paquete) ?: return false
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }

    /** Azabache, en la ficha de ese título (Azabache 1.6.3 entiende «go» = «titulo:<ref>»). [conMau]: Mau se va con ella (desde su bocadillo). */
    fun abreEnAzabache(c: Context, ref: String, conMau: Boolean = false): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(AZABACHE) ?: return false
        i.putExtra("go", "titulo:$ref")
        if (conMau) i.putExtra(com.gonzalo.familia.MauMovil.EXTRA_LLEGA, true)
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }
}
