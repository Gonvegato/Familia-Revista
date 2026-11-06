package com.gonzalo.marfil

import android.content.Context
import com.gonzalo.familia.LaFamilia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * La sincronización de Marfil: ya no es suya, es la de la cuenta de la familia ([LaFamilia]): cada cambio va a la cuenta campo a
 * campo, se escucha en directo mientras se ve la app y lo pendiente se sube solo en cuanto hay red. Aquí queda lo que es de Marfil:
 * la apariencia y los ajustes del lector (`ajustes/marfil`), los demás ajustes ([EspejoCuenta]: `ajustes/marfil_*`), lo leído de cada libro
 * ([LecturaNube]) y la migración de lo de antes.
 */
object SincroP {
    @Volatile private var app: Context? = null
    @Volatile private var de: Any? = null

    /** Qué está pasando (para Ajustes): "" sin cuenta, «Sincronizado» o el último problema. */
    val estado: String get() = LaFamilia.estado
    /** Cuándo se sincronizó bien por última vez (ms; 0 = todavía no). */
    val ultima: Long get() { LaFamilia.cambios; return app?.getSharedPreferences("marfil_sync", Context.MODE_PRIVATE)?.getLong("ultima", 0L) ?: 0L }

    private fun ajustes() = LaFamilia.coleccion("ajustes")

    fun iniciar(c: Context) {
        app = c.applicationContext
        Cuenta.cargar(c)
        if (de !== LaFamilia.familia) LaFamilia.familia.let { f ->
            val previo = f.alSincronizar
            f.alSincronizar = { inf ->
                previo?.invoke(inf)
                if (inf.ok) app?.getSharedPreferences("marfil_sync", Context.MODE_PRIVATE)?.edit()?.putLong("ultima", System.currentTimeMillis())?.apply()
                // Los libros por tu kDrive: subir los nuevos y bajar (si lo tienes activo) los de tus otros dispositivos
                if (inf.ok) app?.let { runCatching { NubeLibros.siHaceFalta(it) } }
            }
        }
        if (de !== LaFamilia.familia) {
            de = LaFamilia.familia
            ajustes().alCambiar { refs -> if ("marfil" in refs) aplicaAjustes() }
            aplicaAjustes()
        }
        // Lo demás que eliges (el orden de cada estante, tener aquí los libros de tu kDrive…): también en la cuenta
        EspejoCuenta.inicia(c, GRUPOS)
        // Los Escritos de Cobalto se rehacen solos al cambiar sus capítulos en la cuenta
        runCatching { Escritos.escucha(c) }
        // La primera vez con la cuenta en este móvil: lo de antes pasa a la cuenta
        Migracion.siHaceFalta(c)
        // Lo que quedara por subir a tu kDrive (o por bajar)
        runCatching { NubeLibros.siHaceFalta(c) }
    }

    /** Las preferencias de Marfil que son tuyas (no de este móvil) y viajan con la cuenta, cada una en `ajustes/<nodo>` ([EspejoCuenta]). */
    val GRUPOS: List<EspejoCuenta.Grupo> get() = listOf(OrdenGuardado.GRUPO, NubeLibros.GRUPO, Internet.GRUPO, Actualizador.GRUPO)

    /** La apariencia y los ajustes del lector que llegan de otro de tus Marfil (si son más nuevos que los de aquí). */
    private fun aplicaAjustes() {
        val c = app ?: return
        val n = ajustes().nodo("marfil") ?: return
        var cambio = false
        n.mapa("skin").takeIf { it.isNotEmpty() }?.let { m -> if (Skin.aplicarAjustes(c, LecturaNube.aJson(m) as JSONObject)) cambio = true }
        n.texto("lector").takeIf { it.isNotBlank() }?.let { if (AjustesLector.aplicar(c, it)) cambio = true }
        if (cambio) Widgets.pedir(c)
    }

    /** Ha cambiado la apariencia o los ajustes del lector aquí: van a la cuenta (con la hora en que cambiaron). */
    fun cambio(c: Context, @Suppress("UNUSED_PARAMETER") ms: Long = 0L) {
        app = c.applicationContext
        if (!Cuenta.vinculada) return
        val campos = LinkedHashMap<String, Any?>()
        if (Skin.ajustesAt > 0L) campos["skin"] = LecturaNube.aKotlin(Skin.ajustesJson())
        AjustesLector.paraSubir(c).takeIf { it.isNotBlank() }?.let { campos["lector"] = it }
        if (campos.isNotEmpty()) runCatching { ajustes().cambia("marfil", campos) }
    }

    /** Ha cambiado lo leído de un libro. */
    fun cambioLectura(c: Context, id: String, ms: Long = 20_000L) = LecturaNube.cambio(c, id, ms)

    /** Al abrir un libro: lo último leído en otros dispositivos, antes de que el lector lo pida (con un tope de tiempo). */
    suspend fun alAbrir(c: Context, id: String, tope: Long = 2500L) {
        if (!Cuenta.vinculada) return
        kotlinx.coroutines.withTimeoutOrNull(tope) { withContext(Dispatchers.IO) { runCatching { LaFamilia.sincronizaYa(); LecturaNube.baja(c, setOf(id)) } } }
    }

    /** «Sincronizar ahora» (y al entrar en la cuenta). */
    suspend fun sincronizar(c: Context, @Suppress("UNUSED_PARAMETER") desdeCero: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        Migracion.siHaceFalta(c, esperar = true)
        cambio(c)
        LaFamilia.sincronizaYa().ok
    }

    suspend fun subir(c: Context, @Suppress("UNUSED_PARAMETER") forzar: Boolean = false): Boolean = sincronizar(c)

    suspend fun subirLectura(c: Context, id: String): Boolean = withContext(Dispatchers.IO) { runCatching { LecturaNube.sube(c, id, EstadoLectura.estado(c, id)) }.isSuccess }

    /** La escucha en directo la lleva la cuenta (mientras se ve cualquier pantalla de la app). */
    fun escuchar(c: Context) { iniciar(c) }
    fun parar() {}

    fun hayPendiente(): Boolean = runCatching { LaFamilia.familia.pendientes > 0 }.getOrDefault(false)
}
