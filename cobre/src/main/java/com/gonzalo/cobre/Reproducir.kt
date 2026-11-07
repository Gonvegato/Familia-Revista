package com.gonzalo.cobre

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.Episodio
import com.gonzalo.iptv.FichaSerie
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Pelicula
import com.gonzalo.iptv.Serie

/** Lo que se le pasa al reproductor: un canal (con los de su grupo, para cambiar con el mando), una película o un episodio. */
sealed class Encargo {
    abstract val lista: Lista
    /** [plan]: si viene de un grupo del fútbol, sus entradas en todas sus calidades (para cambiar de calidad y para probar otra si falla). */
    class Directo(override val lista: Lista, val canales: List<Canal>, val indice: Int, val plan: PlanFutbol? = null) : Encargo()
    class DePelicula(override val lista: Lista, val pelicula: Pelicula, val duracionSeg: Long = 0L) : Encargo()
    class DeEpisodio(override val lista: Lista, val serie: Serie, val ficha: FichaSerie?, val episodio: Episodio) : Encargo()
}

/**
 * Un grupo de canales del fútbol, ya en el reproductor: sus entradas (las de esta lista) con cada calidad resuelta a su canal, la calidad
 * que se quiere ([quiero], elegida por la conexión o a mano) y el rótulo (el grupo, «DAZN LaLiga»). Los canales del encargo son, de cada
 * entrada, el de la calidad que toca.
 */
class PlanFutbol(val grupo: String, val entradas: List<com.gonzalo.iptv.futbol.Entrada>, val canales: List<Map<com.gonzalo.iptv.futbol.Calidad, Canal>>, var quiero: com.gonzalo.iptv.futbol.Calidad) {
    /** La calidad del canal [c] en la entrada [i]. */
    fun calidadDe(i: Int, c: Canal): com.gonzalo.iptv.futbol.Calidad? = canales.getOrNull(i)?.entries?.firstOrNull { it.value.clave == c.clave }?.key
}

object Reproducir {
    /** El encargo para el reproductor (se recoge una vez al abrirlo). */
    @Volatile private var encargo: Encargo? = null
    fun toma(): Encargo? = encargo.also { encargo = null }

    fun pon(ctx: Context, e: Encargo) {
        encargo = e
        ctx.startActivity(Intent(ctx, ReproductorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun canal(ctx: Context, l: Lista, canales: List<Canal>, c: Canal) {
        val i = canales.indexOfFirst { it.clave == c.clave }.coerceAtLeast(0)
        pon(ctx, Encargo.Directo(l, canales.ifEmpty { listOf(c) }, if (canales.isEmpty()) 0 else i))
    }

    /**
     * «Abrir con otro reproductor» (VLC, Just Player, mpv, MX…), para lo que este no sepa: **se le pasa la dirección del canal o de la
     * película, que lleva tu usuario y tu contraseña** (por eso se pregunta antes), con el título, por dónde ibas y las cabeceras que pida
     * (las que entienden). Devuelve un aviso si no hay ninguno.
     */
    fun conOtro(ctx: Context, url: String, titulo: String, pos: Long, cabeceras: Map<String, String>, paquete: String = AjustesReproductor.externo(ctx)): String? {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
            .putExtra("title", titulo).putExtra(Intent.EXTRA_TITLE, titulo)
            .putExtra("position", pos.toInt()).putExtra("from_start", false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (cabeceras.isNotEmpty()) i.putExtra("headers", cabeceras.flatMap { listOf(it.key, it.value) }.toTypedArray())
        if (paquete.isNotBlank()) {
            val conPaquete = Intent(i).setPackage(paquete)
            if (conPaquete.resolveActivity(ctx.packageManager) != null) { ctx.startActivity(conPaquete); return null }
        }
        return try { ctx.startActivity(Intent.createChooser(i, "Abrir con…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); null }
        catch (e: ActivityNotFoundException) { "No hay otro reproductor de vídeo en este dispositivo (VLC o Just Player, por ejemplo)." }
    }

    /** Los reproductores de fuera que hay en este dispositivo (paquete → nombre). */
    fun otros(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("http://example.org/v.ts"), "video/*")
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.filter { it.first != ctx.packageName }.distinctBy { it.first }
    }
}
