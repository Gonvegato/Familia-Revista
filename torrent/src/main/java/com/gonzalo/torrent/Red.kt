package com.gonzalo.torrent

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** Por dónde sale el móvil ahora: una red sin límite (wifi, cable), datos móviles (o una red de pago), o ninguna. */
internal enum class TipoRed { WIFI, MOVIL, NINGUNA }

internal interface Red {
    fun actual(): TipoRed
    /** Avisa (en cualquier hilo) cada vez que cambia la red; se deja de avisar al cerrar lo que devuelve. */
    fun vigila(cambio: () -> Unit): AutoCloseable
}

/** La red de Android. Una VPN sobre wifi cuenta como wifi (no es de pago); sobre datos móviles, como datos móviles. */
internal class RedAndroid(context: Context) : Red {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun actual(): TipoRed {
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return TipoRed.NINGUNA
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return TipoRed.NINGUNA
        val sinLimite = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TipoRed.MOVIL
            sinLimite || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> TipoRed.WIFI
            else -> TipoRed.MOVIL
        }
    }

    override fun vigila(cambio: () -> Unit): AutoCloseable {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = cambio()
            override fun onLost(network: Network) = cambio()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = cambio()
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        return AutoCloseable { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
}
