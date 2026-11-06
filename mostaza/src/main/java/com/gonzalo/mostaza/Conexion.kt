package com.gonzalo.mostaza

import android.content.Context
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * La conexión de ESTE dispositivo (es suya: no viaja). Para el elector cuenta, por este orden, lo que se ha medido de verdad aquí al ver
 * cosas por este tipo de red (wifi, datos o cable: una media que se va corrigiendo, y que baja en cuanto algo se corta) y, si aún no hay
 * nada medido, lo que estima Android (con un margen: suele quedarse corto con lo que de verdad da).
 */
object Conexion {
    private fun p(c: Context) = c.getSharedPreferences("mostaza_red", Context.MODE_PRIVATE)

    enum class Tipo { WIFI, MOVIL, CABLE, OTRA, NINGUNA }

    fun tipo(c: Context): Tipo {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return Tipo.NINGUNA
        val cap = cm.getNetworkCapabilities(cm.activeNetwork ?: return Tipo.NINGUNA) ?: return Tipo.NINGUNA
        return when {
            cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Tipo.CABLE
            cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Tipo.WIFI
            cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Tipo.MOVIL
            else -> Tipo.OTRA
        }
    }

    /** ¿Se paga por lo que se baja? (datos del móvil, o una wifi marcada como «con límite») */
    fun medida(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val cap = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return !cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Lo que estima Android de la bajada (kbit/s; 0 = no lo sabe). */
    fun estimaAndroid(c: Context): Long {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return 0L
        val cap = cm.getNetworkCapabilities(cm.activeNetwork ?: return 0L) ?: return 0L
        return cap.linkDownstreamBandwidthKbps.toLong().coerceAtLeast(0L)
    }

    /** Lo medido aquí con este tipo de red (kbit/s; 0 = nada todavía). */
    fun medido(c: Context, t: Tipo = tipo(c)): Long = p(c).getLong("kbps_${t.name}", 0L)

    /**
     * Apunta lo medido al ver algo ([kbps]): la media se mueve hacia lo nuevo; si se ha cortado ([corte]), se queda con lo más bajo (y
     * un poco menos), para que la próxima vez se elija algo que quepa.
     */
    fun apunta(c: Context, kbps: Long, corte: Boolean = false, t: Tipo = tipo(c)) {
        if (kbps <= 0 || t == Tipo.NINGUNA) return
        val antes = medido(c, t)
        val nuevo = when {
            corte -> minOf(if (antes > 0) antes else kbps, kbps) * 85 / 100
            antes <= 0 -> kbps
            else -> (antes * 7 + kbps * 3) / 10
        }
        p(c).edit().putLong("kbps_${t.name}", nuevo.coerceAtLeast(300L)).apply()
    }

    /** La red de ahora para el elector. */
    fun red(c: Context): Red {
        val t = tipo(c)
        val m = medido(c, t)
        val kbps = if (m > 0) m else estimaAndroid(c) * 6 / 10
        return Red(kbps, t == Tipo.MOVIL || (t != Tipo.WIFI && t != Tipo.CABLE && medida(c)), m > 0)
    }

    /** Lo que sabe reproducir este dispositivo (los decodificadores de vídeo de Android y la pantalla). */
    @Volatile private var capacidades: Capacidades? = null
    fun capacidades(c: Context): Capacidades = capacidades ?: calcula(c).also { capacidades = it }

    private fun calcula(c: Context): Capacidades {
        val codecs = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder } }.getOrDefault(emptyList())
        fun tiene(mime: String) = codecs.any { ci -> ci.supportedTypes.any { it.equals(mime, true) } }
        fun alto(mime: String): Int = codecs.filter { ci -> ci.supportedTypes.any { it.equals(mime, true) } }.maxOfOrNull { ci ->
            runCatching { ci.getCapabilitiesForType(mime).videoCapabilities.supportedHeights.upper }.getOrDefault(0)
        } ?: 0
        val dv = tiene("video/dolby-vision")
        val pantalla = runCatching { c.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY) }.getOrNull()
        @Suppress("DEPRECATION")
        val hdr = runCatching { pantalla?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true }.getOrDefault(false)
        val maxAvc = alto("video/avc"); val maxHevc = alto("video/hevc")
        val maxima = maxOf(maxAvc, maxHevc, 1080).let { if (it >= 2160) 2160 else if (it >= 1440) 1440 else 1080 }
        return Capacidades(hevc = tiene("video/hevc") || codecs.isEmpty(), av1 = tiene("video/av01"), vp9 = tiene("video/x-vnd.on2.vp9") || codecs.isEmpty(), hdr = hdr, dv = dv, maxima = maxima)
    }
}
