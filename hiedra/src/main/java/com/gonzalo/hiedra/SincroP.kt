package com.gonzalo.hiedra

import android.content.Context
import com.gonzalo.familia.LaFamilia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * La sincronización de Hiedra: es la de la cuenta de la familia ([LaFamilia]): cada cambio va a la cuenta campo a campo, se escucha
 * en directo mientras se ve la app y lo pendiente se sube solo en cuanto hay red. Aquí queda lo que es de Hiedra: la apariencia y la
 * portada de la app (`ajustes/hiedra`), las ROMs a tu kDrive y la migración de lo de antes.
 */
object SincroP {
    @Volatile private var app: Context? = null
    @Volatile private var de: Any? = null

    /** Qué está pasando (para Ajustes): "" sin cuenta, «Sincronizado» o el último problema. */
    val estado: String get() = LaFamilia.estado
    /** Cuándo se sincronizó bien por última vez (ms; 0 = todavía no). */
    val ultima: Long get() { LaFamilia.cambios; return app?.getSharedPreferences("hiedra_sync", Context.MODE_PRIVATE)?.getLong("ultima", 0L) ?: 0L }

    private fun ajustes() = LaFamilia.coleccion("ajustes")

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
                    a.getSharedPreferences("hiedra_sync", Context.MODE_PRIVATE).edit().putLong("ultima", System.currentTimeMillis()).apply()
                    if (KDrive.on(a)) runCatching { SubirRomsWorker.programar(a) }
                }
            }
            ajustes().alCambiar { refs -> if ("hiedra" in refs) android.os.Handler(android.os.Looper.getMainLooper()).post { aplicaAjustes() } }
            aplicaAjustes()
        }
        Migracion.siHaceFalta(c)
    }

    /** La apariencia y la portada de la app que llegan de otro de tus Hiedra (si son más nuevas que las de aquí). */
    private fun aplicaAjustes() {
        val c = app ?: return
        val n = ajustes().nodo("hiedra") ?: return
        var cambio = false
        n.mapa("skin").takeIf { it.isNotEmpty() }?.let { m -> if (Skin.aplicarAjustes(c, JSONObject(m))) cambio = true }
        n.mapa("ini").takeIf { it.isNotEmpty() }?.let { m -> if (Inicio.aplicarRemoto(JSONObject(m).toString())) cambio = true }
        if (cambio) Widgets.pedir(c)
    }

    /** Ha cambiado la apariencia o la portada de la app aquí: van a la cuenta. */
    fun cambio(c: Context, @Suppress("UNUSED_PARAMETER") ms: Long = 0L) {
        app = c.applicationContext
        if (!Cuenta.vinculada) return
        val campos = LinkedHashMap<String, Any?>()
        if (Skin.ajustesAt > 0L) campos["skin"] = com.gonzalo.familia.nucleo.Json.lee(Skin.ajustesJson().toString())
        if (Inicio.config.mod > 0L) campos["ini"] = com.gonzalo.familia.nucleo.Json.lee(Inicio.config.aJson().toString())
        if (campos.isNotEmpty()) runCatching { ajustes().cambia("hiedra", campos) }
    }

    /** «Sincronizar ahora» (y al entrar en la cuenta). */
    suspend fun sincronizar(c: Context, @Suppress("UNUSED_PARAMETER") desdeCero: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        Migracion.siHaceFalta(c, esperar = true)
        cambio(c)
        LaFamilia.sincronizaYa().ok
    }

    suspend fun subir(c: Context, @Suppress("UNUSED_PARAMETER") forzar: Boolean = false): Boolean = sincronizar(c)

    /** La escucha en directo la lleva la cuenta (mientras se ve cualquier pantalla de la app). */
    fun escuchar(c: Context) { iniciar(c) }
    fun parar() {}
    fun olvidarVistos() {}

    fun hayPendiente(): Boolean = runCatching { LaFamilia.familia.pendientes > 0 }.getOrDefault(false)
}
