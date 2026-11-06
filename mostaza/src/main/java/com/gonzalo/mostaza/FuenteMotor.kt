package com.gonzalo.mostaza

import android.content.Context
import com.gonzalo.torrent.ErrorTorrent
import com.gonzalo.torrent.MotorTorrent
import com.gonzalo.torrent.AjustesTorrent as AjustesMotor

/**
 * El motor de torrents de la familia (`:torrent`) detrás de [FuenteTorrent]: uno solo para toda la app, con tus ajustes de la cuenta
 * (y los cambios que lleguen de este o de otro dispositivo, en marcha).
 */
class FuenteMotor private constructor(c: Context) : FuenteTorrent {
    private val motor = MotorTorrent(c.applicationContext, paraMotor(Torrents.ajustes(c)))

    override suspend fun prepara(infoHash: String, fileIdx: Int?, fuentes: List<String>, nombre: String?): String =
        try { motor.prepara(infoHash, fileIdx, fuentes, nombre).url }
        catch (e: ErrorTorrent) { throw SinTorrent(e.message ?: "No se ha podido abrir el torrent.") }

    override fun para(infoHash: String) = motor.para(infoHash)

    override fun bajada(infoHash: String): Long? = runCatching { motor.estado(infoHash)?.bajadaBs }.getOrNull()

    override fun estado(infoHash: String): String? = runCatching { motor.estado(infoHash) }.getOrNull()?.let { e ->
        e.error ?: resumenTorrent(e.pares, e.bajadaBs, e.progreso, e.fase == com.gonzalo.torrent.EstadoTorrent.Fase.BUSCANDO, e.conocidos, e.semillasEnjambre)
    }

    fun ajusta(a: AjustesTorrent) = motor.ajusta(paraMotor(a))

    companion object {
        /** Para el motor, «sin límite» de subida es esto (unos 1 GB/s): el motor nunca sube sin límite. */
        const val SIN_LIMITE_KBS = 1_000_000

        /**
         * Lo de tu cuenta, en lo que entiende el motor. «Compartir mientras ves» apagado deja la subida en lo mínimo (sin subir nada los
         * demás dejan de mandarte trozos); al cerrar el vídeo se deja siempre de compartir y se borra (no se queda compartiendo).
         */
        fun paraMotor(a: AjustesTorrent) = AjustesMotor(
            soloWifi = a.soloWifi,
            subidaMaxKBs = when { !a.compartir -> 0; a.subidaKBs <= 0 -> SIN_LIMITE_KBS; else -> a.subidaKBs },
            bajadaMaxKBs = a.bajadaKBs.coerceAtLeast(0),
            cacheMaxMB = a.cacheMB,
            seguirCompartiendoAlParar = false,
        )

        /** Pone el motor (una vez por proceso). */
        @Synchronized fun conecta(c: Context) {
            if (Torrents.disponible) return
            val f = runCatching { FuenteMotor(c) }.getOrNull() ?: return
            Torrents.fuente = f
            Torrents.alCambiarAjustes = { f.ajusta(it) }
        }
    }
}
