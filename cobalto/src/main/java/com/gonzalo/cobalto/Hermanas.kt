package com.gonzalo.cobalto

import android.content.Context
import android.content.Intent

/** Las apps hermanas que están en este móvil: Marfil lee los capítulos terminados (los que Cobalto publica en tu cuenta). */
object Hermanas {
    const val PAQ_MARFIL = "com.gonzalo.marfil"

    /** Para abrir [paq]: con Seshat, la que haya en el móvil (la normal o SesKobo, que hace de Seshat). */
    fun lanzador(c: Context, paq: String): android.content.Intent? = runCatching { c.packageManager.getLaunchIntentForPackage(com.gonzalo.familia.LaFamilia.instalada(c, paq)) }.getOrNull()
    fun instalada(c: Context, paq: String) = lanzador(c, paq) != null
    fun hayMarfil(c: Context) = instalada(c, PAQ_MARFIL)

    /** Abre Marfil en «Escritos» (y con [obra], leyendo esa obra: Marfil 0.12.3; 0.12.0 abre la lista; una más vieja, su portada). */
    fun leerEnMarfil(c: Context, obra: String = "") {
        val i = lanzador(c, PAQ_MARFIL) ?: return
        i.putExtra("go", "escritos"); if (obra.isNotBlank()) i.putExtra("obra", obra)
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Cuántos capítulos de la obra están terminados (los que Marfil puede leer). */
    fun capitulosTerminados(e: Escritos, obraId: String): Int = e.capitulos(obraId).count { Compartir.hecho(e, it) }
}
