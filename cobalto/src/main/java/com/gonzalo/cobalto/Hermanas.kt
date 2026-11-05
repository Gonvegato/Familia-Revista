package com.gonzalo.cobalto

import android.content.Context
import android.content.Intent

/** Las apps hermanas que están en este móvil: Marfil lee los capítulos terminados (los que Cobalto publica en tu cuenta). */
object Hermanas {
    const val PAQ_MARFIL = "com.gonzalo.marfil"

    fun instalada(c: Context, paq: String) = runCatching { c.packageManager.getLaunchIntentForPackage(paq) != null }.getOrDefault(false)
    fun hayMarfil(c: Context) = instalada(c, PAQ_MARFIL)

    /** Abre Marfil en «Escritos» (y con [obra], leyendo esa obra: Marfil 0.12.3; 0.12.0 abre la lista; una más vieja, su portada). */
    fun leerEnMarfil(c: Context, obra: String = "") {
        val i = c.packageManager.getLaunchIntentForPackage(PAQ_MARFIL) ?: return
        i.putExtra("go", "escritos"); if (obra.isNotBlank()) i.putExtra("obra", obra)
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Cuántos capítulos de la obra están terminados (los que Marfil puede leer). */
    fun capitulosTerminados(e: Escritos, obraId: String): Int = e.capitulos(obraId).count { Compartir.hecho(e, it) }
}
