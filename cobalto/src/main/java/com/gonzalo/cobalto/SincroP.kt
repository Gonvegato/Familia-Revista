package com.gonzalo.cobalto

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * La sincronización de Cobalto con tu cuenta de Atrio. Lo que escribes va por [Escritos] (los mismos nodos `/escritos` y `escritosIdx`
 * que Atrio y Atrio del PC: documento a documento, con mezcla). Aquí va lo demás, como en Marfil y Carmesí:
 *  - la apariencia (tema, acento, letras…) sube a `/users/<uid>/cobalto/dev/<dispositivo>` y gana la más reciente entre tus Cobaltos;
 *  - mientras Cobalto se ve, se escucha en directo el índice de tus escritos (lo que subas desde el PC o desde otro móvil se baja al momento)
 *    y los sellos de tus otros Cobaltos (su apariencia).
 */
object SincroP {
    const val ESPERA = 600L
    private val candado = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var app: Context? = null
    private var programada: Job? = null
    private var escucha: FbStream? = null
    private var escuchaEscritos: FbStream? = null
    private val bajando = AtomicBoolean(false)
    @Volatile private var fallos = 0

    /** Qué está pasando (para Ajustes): "" sin vincular, «Sincronizado», o el último problema. */
    var estado by mutableStateOf("")
        private set
    /** Cuándo se sincronizó por última vez (milisegundos; 0 = todavía no). */
    var ultima by mutableStateOf(0L)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("cobalto_sync", Context.MODE_PRIVATE)

    fun iniciar(c: Context) {
        app = c.applicationContext
        ultima = prefs(c).getLong("ultima", 0L)
    }

    private fun vistos(c: Context): JSONObject = runCatching { JSONObject(prefs(c).getString("vistos", "{}")!!) }.getOrDefault(JSONObject())
    private fun guardaVistos(c: Context, o: JSONObject) = prefs(c).edit().putString("vistos", o.toString()).apply()
    fun olvidarVistos() { app?.let { prefs(it).edit().remove("vistos").remove("hash").apply() } }

    /** La apariencia ha cambiado aquí (Skin.guardar lo avisa): se sube dentro de [ms]. */
    fun cambio(c: Context, ms: Long = ESPERA) {
        val a = app ?: run { iniciar(c); app }
        if (a == null || !Cuenta.vinculada) return
        synchronized(this) {
            programada?.cancel()
            programada = scope.launch {
                delay(ms)
                withContext(NonCancellable) { subir(a) }
            }
        }
    }

    /** Sube la apariencia de este dispositivo si ha cambiado desde la última vez. */
    suspend fun subir(c: Context, forzar: Boolean = false): Boolean = candado.withLock {
        if (!Cuenta.vinculada) return@withLock true
        try {
            val blob = JSONObject().put("v", 1).put("dev", Cuenta.dispositivo).apply { if (Skin.ajustesAt > 0L) put("aj", Skin.ajustesJson()) }.toString()
            val hash = blob.hashCode().toString() + ":" + blob.length
            val p = prefs(c)
            if (!forzar && p.getString("hash", "") == hash) return@withLock true
            val nodo = JSONObject().put("name", Cuenta.nombreDispositivo).put("device", "cobalto").put("updated", JSONObject().put(".sv", "timestamp")).put("data", blob)
            Cuenta.db("PUT", "${Cuenta.raiz}/dev/${Cuenta.dispositivo}", nodo.toString(), "print=silent")
            val ts = Cuenta.db("GET", "${Cuenta.raiz}/dev/${Cuenta.dispositivo}/updated", null)?.toLongOrNull() ?: throw Cuenta.Fail("Firebase no ha guardado la copia.")
            Cuenta.db("PUT", "${Cuenta.raiz}/stamps/${Cuenta.dispositivo}", ts.toString(), "print=silent")
            p.edit().putString("hash", hash).apply()
            guardaVistos(c, vistos(c).put(Cuenta.dispositivo, ts))
            bien(c)
            true
        } catch (e: Exception) { mal(c, e); false }
    }

    /** Baja la apariencia más reciente de tus otros Cobaltos. Devuelve true si ha cambiado algo aquí. */
    suspend fun bajar(c: Context): Boolean = candado.withLock {
        if (!Cuenta.vinculada) return@withLock false
        try {
            val sellos = Cuenta.db("GET", "${Cuenta.raiz}/stamps", null)?.let { JSONObject(it) }
            val vistas = vistos(c)
            val ahora = System.currentTimeMillis()
            val vistosNuevos = JSONObject(vistas.toString())
            var mejor: JSONObject? = null
            if (sellos != null) for (dev in sellos.keys()) {
                val ts = sellos.optLong(dev, 0L)
                if (dev == Cuenta.dispositivo || ts <= vistas.optLong(dev, 0L) || ahora - ts > Cuenta.STALE) continue
                val raw = Cuenta.db("GET", "${Cuenta.raiz}/dev/$dev/data", null) ?: continue
                val datos = org.json.JSONTokener(raw).nextValue() as? String ?: continue
                runCatching { JSONObject(datos).optJSONObject("aj") }.getOrNull()?.let { aj -> if (mejor == null || aj.optLong("at") > mejor!!.optLong("at")) mejor = aj }
                vistosNuevos.put(dev, ts)
            }
            var cambio = false
            mejor?.let { if (Skin.aplicarAjustes(c, it)) cambio = true }
            guardaVistos(c, vistosNuevos)
            bien(c)
            cambio
        } catch (e: Exception) { mal(c, e); false }
    }

    /** Al vincular una cuenta (o a mano, «Sincronizar ahora»): lo escrito, y la apariencia de tus otros Cobaltos. */
    suspend fun sincronizar(c: Context, desdeCero: Boolean = false): Boolean {
        if (desdeCero) olvidarVistos()
        bajar(c)
        val ok = subir(c, forzar = true)
        val okEscritos = Escritos.sincroniza(c)
        if (okEscritos) runCatching { withContext(Dispatchers.IO) { Publicar.todo(c) } }
        return ok && okEscritos
    }

    /** Varios avisos de la escucha seguidos se juntan en una sola bajada. */
    fun bajarPronto() {
        val c = app ?: return
        if (!Cuenta.vinculada || !bajando.compareAndSet(false, true)) return
        scope.launch { try { delay(150); bajar(c) } finally { bajando.set(false) } }
    }

    /** Mientras Cobalto se ve: escucha tu cuenta en directo. */
    fun escuchar(c: Context) {
        iniciar(c)
        if (!Cuenta.vinculada) { parar(); return }
        if (escucha != null) return
        bajarPronto()
        Escritos.bajaPronto(c)
        val f = FbStream("cobalto", { renovar -> Cuenta.urlFlujo("${Cuenta.raiz}/stamps", renovar) }, { _, _ -> bajarPronto() }, { })
        escucha = f
        f.start(scope)
        // El índice de tus escritos: cambia en cuanto algo sube desde el PC o desde otro móvil
        val g = FbStream("escritos", { renovar -> Cuenta.urlFlujo("/users/${Cuenta.uid}/escritosIdx", renovar) }, { _, _ -> Escritos.bajaPronto(c) }, { })
        escuchaEscritos = g
        g.start(scope)
    }

    fun parar() { escucha?.stop(); escucha = null; escuchaEscritos?.stop(); escuchaEscritos = null }

    private fun bien(c: Context) {
        fallos = 0
        val ahora = System.currentTimeMillis()
        ultima = ahora; prefs(c).edit().putLong("ultima", ahora).apply()
        estado = "Sincronizado"
    }

    private fun mal(c: Context, e: Exception) {
        fallos++
        estado = (e.message ?: "sin conexión").let { if (e is java.io.IOException) "Sin conexión: se reintenta solo." else it }
        if (fallos <= 8 && Cuenta.vinculada) cambio(c, minOf(300_000L, 5_000L * Math.pow(3.0, (fallos - 1).toDouble()).toLong()))
    }
}
