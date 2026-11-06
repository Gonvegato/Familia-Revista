package com.gonzalo.cobalto

import android.content.Context
import com.gonzalo.familia.LaFamilia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * La sincronización de Cobalto. Lo que escribes va por [Escritos]: los documentos `/escritos` y `escritosIdx`, documento a documento y
 * con mezcla a tres bandas, los mismos que Atrio del PC (se escucha en directo el índice). Lo demás es de la cuenta de la familia
 * ([LaFamilia]): la apariencia (`ajustes/cobalto`) y lo que leen Azabache y Marfil ([Publicar]: obras, capítulos y palabras).
 */
object SincroP {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var app: Context? = null
    @Volatile private var de: Any? = null
    private var escuchaEscritos: FbStream? = null
    private val parejaMirando = java.util.concurrent.atomic.AtomicBoolean(false)

    val estado: String get() = LaFamilia.estado
    val ultima: Long get() { LaFamilia.cambios; return app?.getSharedPreferences("cobalto_sync", Context.MODE_PRIVATE)?.getLong("ultima", 0L) ?: 0L }

    private fun ajustes() = LaFamilia.coleccion("ajustes")

    fun iniciar(c: Context) {
        app = c.applicationContext
        Cuenta.cargar(c)
        if (de !== LaFamilia.familia) {
            de = LaFamilia.familia
            val f = LaFamilia.familia
            val previo = f.alSincronizar
            f.alSincronizar = { inf -> previo?.invoke(inf); if (inf.ok) app?.getSharedPreferences("cobalto_sync", Context.MODE_PRIVATE)?.edit()?.putLong("ultima", System.currentTimeMillis())?.apply() }
            ajustes().alCambiar { refs -> if ("cobalto" in refs) android.os.Handler(android.os.Looper.getMainLooper()).post { aplicaAjustes() } }
            aplicaAjustes()
            // Lo que leen Azabache y Marfil: que estén abiertas para que se suban y se bajen solas
            listOf("obras", "capitulos", "palabras").forEach { LaFamilia.coleccion(it) }
            // Los sprints, en la cuenta (los de este móvil de antes pasan a ella en cuanto se ha bajado lo que ya había)
            SprintEscribir.cuenta = CuentaSprintsMovil
            LaFamilia.coleccion(SprintEscribir.COLECCION).alCambiar { app?.let { SprintsDeAntes.pronto(it) } }
        }
        SprintsDeAntes.pronto(c)
        // Tu pareja, la de la cuenta (la de este móvil, si la cuenta no tenía, pasa a ella una vez)
        if (Cuenta.vinculada && parejaMirando.compareAndSet(false, true)) kotlin.concurrent.thread(name = "cobalto-pareja") { try { runCatching { Cuenta.sincronizaPareja() } } finally { parejaMirando.set(false) } }
        Migracion.siHaceFalta(c)
    }

    private fun aplicaAjustes() {
        val c = app ?: return
        val n = ajustes().nodo("cobalto") ?: return
        n.mapa("skin").takeIf { it.isNotEmpty() }?.let { m -> if (Skin.aplicarAjustes(c, JSONObject(m))) Widgets.pedir(c) }
    }

    /** La apariencia ha cambiado aquí: va a la cuenta. */
    fun cambio(c: Context, @Suppress("UNUSED_PARAMETER") ms: Long = 0L) {
        app = c.applicationContext
        if (!Cuenta.vinculada || Skin.ajustesAt <= 0L) return
        runCatching { ajustes().cambia("cobalto", mapOf("skin" to com.gonzalo.familia.nucleo.Json.lee(Skin.ajustesJson().toString()))) }
    }

    suspend fun subir(c: Context, @Suppress("UNUSED_PARAMETER") forzar: Boolean = false): Boolean = sincronizar(c)

    /** Al entrar en la cuenta (o a mano, «Sincronizar ahora»): lo escrito, la apariencia y lo que leen tus otras apps. */
    suspend fun sincronizar(c: Context, @Suppress("UNUSED_PARAMETER") desdeCero: Boolean = false): Boolean {
        Migracion.siHaceFalta(c)
        cambio(c)
        val okEscritos = Escritos.sincroniza(c)
        if (okEscritos) runCatching { withContext(Dispatchers.IO) { Publicar.todo(c) } }
        val ok = withContext(Dispatchers.IO) { LaFamilia.sincronizaYa().ok }
        return ok && okEscritos
    }

    /** Mientras Cobalto se ve: lo escrito se escucha en directo (el índice cambia en cuanto algo sube desde el PC o desde otro móvil). */
    fun escuchar(c: Context) {
        iniciar(c)
        if (!Cuenta.vinculada) { parar(); return }
        if (escuchaEscritos != null) return
        Escritos.bajaPronto(c)
        val g = FbStream("escritos", { renovar -> Cuenta.urlFlujo("/users/${Cuenta.uid}/escritosIdx", renovar) }, { _, _ -> Escritos.bajaPronto(c) }, { })
        escuchaEscritos = g
        g.start(scope)
    }

    fun parar() { escuchaEscritos?.stop(); escuchaEscritos = null }
    fun olvidarVistos() {}
}
