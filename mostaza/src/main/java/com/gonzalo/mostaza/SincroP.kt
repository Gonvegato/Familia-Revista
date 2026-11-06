package com.gonzalo.mostaza

import android.content.Context
import com.gonzalo.familia.LaFamilia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * La sincronización de Mostaza es la de la cuenta de la familia ([LaFamilia]): cada cambio va a la cuenta campo a campo, se escucha en
 * directo mientras se ve la app y lo pendiente se sube solo en cuanto hay red. Aquí queda lo de Mostaza: la apariencia
 * (`ajustes/mostaza`), lo demás que eliges ([EspejoCuenta]: `ajustes/mostaza_*`) y traer una vez lo que Azabache tenía de películas y
 * series ([MigraAzabache]).
 */
object SincroP {
    @Volatile private var app: Context? = null
    @Volatile private var de: Any? = null

    val estado: String get() = LaFamilia.estado
    val ultima: Long get() { LaFamilia.cambios; return app?.getSharedPreferences("mostaza_sync", Context.MODE_PRIVATE)?.getLong("ultima", 0L) ?: 0L }

    private fun ajustes() = LaFamilia.coleccion("ajustes")

    /** Lo que eliges en Mostaza, grupo a grupo (`ajustes/<nodo>`). */
    val GRUPOS: List<EspejoCuenta.Grupo> get() = listOf(AjustesReproductor.GRUPO, AjustesElector.GRUPO, AjustesTorrents.GRUPO, OrdenGuardado.GRUPO, AjustesPortada.GRUPO, Actualizador.GRUPO)

    fun iniciar(c: Context) {
        app = c.applicationContext
        Cuenta.cargar(c)
        if (de !== LaFamilia.familia) {
            de = LaFamilia.familia
            val f = LaFamilia.familia
            val previo = f.alSincronizar
            f.alSincronizar = { inf ->
                previo?.invoke(inf)
                if (inf.ok) app?.let { a ->
                    a.getSharedPreferences("mostaza_sync", Context.MODE_PRIVATE).edit().putLong("ultima", System.currentTimeMillis()).apply()
                    MigraAzabache.siHaceFalta(a)
                }
            }
            ajustes().alCambiar { refs -> if ("mostaza" in refs) android.os.Handler(android.os.Looper.getMainLooper()).post { aplicaAjustes() } }
            aplicaAjustes()
        }
        EspejoCuenta.inicia(c, GRUPOS)
        MigraAzabache.siHaceFalta(c)
    }

    /** La apariencia que llega de otro de tus dispositivos (si es más nueva que la de aquí). */
    private fun aplicaAjustes() {
        val c = app ?: return
        val n = ajustes().nodo("mostaza") ?: return
        n.mapa("skin").takeIf { it.isNotEmpty() }?.let { m -> Skin.aplicarAjustes(c, JsonK.aJson(m) as JSONObject) }
    }

    /** Ha cambiado la apariencia aquí: va a la cuenta. */
    fun cambio(c: Context, @Suppress("UNUSED_PARAMETER") ms: Long = 0L) {
        app = c.applicationContext
        if (!Cuenta.vinculada) return
        if (Skin.ajustesAt > 0L) runCatching { ajustes().cambia("mostaza", mapOf("skin" to JsonK.aKotlin(Skin.ajustesJson()))) }
    }

    suspend fun sincronizar(c: Context): Boolean = withContext(Dispatchers.IO) {
        cambio(c)
        LaFamilia.sincronizaYa().ok.also { MigraAzabache.siHaceFalta(c) }
    }

    fun hayPendiente(): Boolean = runCatching { LaFamilia.familia.pendientes > 0 }.getOrDefault(false)
}
