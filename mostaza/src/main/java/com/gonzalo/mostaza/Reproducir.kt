package com.gonzalo.mostaza

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Lo que se le pasa al reproductor: qué se ve, con qué fuente y las demás por si hay que cambiar. */
class Encargo(
    val breve: MetaBreve, val meta: Meta?, val video: Video?, val elegida: Candidata, val candidatas: List<Candidata>,
    /** Lo que ya llevaba esta sesión del reproductor con este título (al pasar al episodio siguiente). */
    val previo: Long = 0L, val pendiente: Map<String, Long> = emptyMap(),
    /** La eligió el elector solo («Reproducir», el episodio siguiente), no tú: si sus pistas no traen tu idioma, se prueba otra. */
    val automatica: Boolean = false,
)

/** Qué hacer con una fuente. */
sealed class Accion {
    /** Al reproductor de Mostaza (una dirección directa, o un torrent con el motor de torrents). */
    object Reproductor : Accion()
    /** Fuera de Mostaza (YouTube, una web). */
    class Fuera(val intent: Intent) : Accion()
    /** No se puede: el texto, para enseñarlo tal cual. */
    class Aviso(val texto: String) : Accion()
}

object Reproducir {
    /** El encargo para el reproductor (se recoge una vez al abrirlo). */
    @Volatile private var encargo: Encargo? = null
    fun toma(): Encargo? = encargo.also { encargo = null }

    /** Qué se hace con [f] (sin hacerlo todavía): lo decide su tipo (`url`, `infoHash`, `ytId`, `externalUrl`, NZB o archivos). */
    fun accion(f: Fuente): Accion = when (f.tipo) {
        TipoFuente.DIRECTA -> Accion.Reproductor
        TipoFuente.TORRENT -> if (Torrents.disponible) Accion.Reproductor else Accion.Aviso(SinTorrents.AVISO)
        TipoFuente.YOUTUBE -> Accion.Fuera(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + Uri.encode(f.ytId))))
        TipoFuente.EXTERNA -> if (f.externalUrl.startsWith("http://") || f.externalUrl.startsWith("https://")) Accion.Fuera(Intent(Intent.ACTION_VIEW, Uri.parse(f.externalUrl)))
            else Accion.Aviso("Es un enlace para la app de Stremio («${f.externalUrl.substringBefore('/').take(20)}…»): Mostaza no lo puede abrir.")
        TipoFuente.NZB -> Accion.Aviso("Todavía no: Mostaza aún no sabe abrir fuentes de Usenet (NZB). Elige otra fuente.")
        TipoFuente.ARCHIVO -> Accion.Aviso("Todavía no: Mostaza aún no sabe abrir vídeos dentro de archivos comprimidos (RAR, ZIP, 7z…). Elige otra fuente.")
        TipoFuente.NADA -> Accion.Aviso("Esta fuente no trae nada que se pueda ver.")
    }

    /**
     * Pone [c] (de [todas], ya ordenadas) para [breve]. Devuelve un aviso si no se puede (para enseñarlo), o null si ya está en marcha.
     */
    fun pon(ctx: Context, breve: MetaBreve, meta: Meta?, video: Video?, c: Candidata, todas: List<Candidata>, previo: Long = 0L, pendiente: Map<String, Long> = emptyMap(),
            automatica: Boolean = false): String? {
        return when (val a = accion(c.fuente)) {
            is Accion.Aviso -> a.texto
            is Accion.Fuera -> {
                // YouTube: la app si está; si no, la web
                val i = if (c.fuente.tipo == TipoFuente.YOUTUBE) Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + c.fuente.ytId)).takeIf { it.resolveActivity(ctx.packageManager) != null } ?: a.intent else a.intent
                try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); null } catch (e: ActivityNotFoundException) { "No hay ninguna app en este dispositivo que lo abra." }
            }
            Accion.Reproductor -> {
                encargo = Encargo(breve, meta, video, c, todas, previo, pendiente, automatica)
                ctx.startActivity(Intent(ctx, ReproductorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                null
            }
        }
    }

    /**
     * «Abrir con otro reproductor» (VLC, Just Player, mpv, MX…), para lo que este dispositivo no sabe decodificar: la misma dirección,
     * con el título, por dónde ibas y las cabeceras que pida la fuente (las que entienden). Devuelve un aviso si no hay ninguno.
     */
    fun conOtro(ctx: Context, url: String, titulo: String, pos: Long, cabeceras: Map<String, String>, paquete: String = AjustesReproductor.externo(ctx)): String? {
        val uri = Uri.parse(url)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
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
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://example.org/v.mp4"), "video/*")
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.filter { it.first != ctx.packageName }.distinctBy { it.first }
    }
}
