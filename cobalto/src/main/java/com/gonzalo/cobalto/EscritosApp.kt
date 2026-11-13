package com.gonzalo.cobalto

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Los escritos en la app de Android: los de esta app ([get]), con la cuenta de la familia ([CuentaEscritos]) y la subida programada
 * (en cuanto cambias algo, con reintentos sin conexión y una tarea de fondo por si cierras la app). El núcleo ([Escritos], sin nada de
 * Android) es el mismo que compila Cobalto para Linux y Mac.
 */
object CuentaEscritos : NubeEscritos {
    override val vinculada: Boolean get() = Cuenta.vinculada
    override val uid: String get() = Cuenta.uid
    override fun db(metodo: String, ruta: String, cuerpo: String?, consulta: String): String? = Cuenta.db(metodo, ruta, cuerpo, consulta)
}

private object EscritosApp {
    @Volatile var inst: Escritos? = null
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.gonzalo.familia.Fallos.paraCorrutinas("escritos"))
    var programada: Job? = null
    var fallos = 0
    @Volatile var ordenada = 0L
    val escuchando = java.util.concurrent.atomic.AtomicBoolean(false)
}

/** Los escritos de esta app. */
fun Escritos.Companion.get(ctx: Context): Escritos {
    val a = ctx.applicationContext
    return EscritosApp.inst ?: synchronized(EscritosApp) {
        EscritosApp.inst ?: Escritos(File(a.filesDir, "escritos"), CuentaEscritos) { Cuenta.dispositivo.ifBlank { "movil" } }.also { it.alCambiar = { pronto(a) }; EscritosApp.inst = it }
    }
}

/** Solo para las pruebas: empezar de cero. */
fun Escritos.Companion.reiniciaParaPruebas() {
    synchronized(EscritosApp) { EscritosApp.inst?.parar(); EscritosApp.inst = null; EscritosApp.programada?.cancel(); EscritosApp.programada = null; EscritosApp.fallos = 0; EscritosApp.ordenada = 0L }
}

/**
 * Programa subir lo pendiente (y bajar lo nuevo) dentro de [ms]. Sin conexión se reintenta solo (5 s, 15 s, 45 s… hasta 5 min) y,
 * por si se cierra la app, queda una tarea para cuando haya red.
 */
fun Escritos.Companion.pronto(ctx: Context, ms: Long = ESPERA) {
    val c = ctx.applicationContext
    if (!Cuenta.vinculada) return
    synchronized(EscritosApp) {
        EscritosApp.programada?.cancel()
        EscritosApp.programada = EscritosApp.scope.launch {
            delay(ms)
            withContext(NonCancellable) { sincroniza(c) }
        }
    }
}

/** Sube y baja ahora. Devuelve false si no ha podido (se reintenta solo). */
suspend fun Escritos.Companion.sincroniza(ctx: Context): Boolean {
    val c = ctx.applicationContext
    if (!Cuenta.vinculada) return true
    val e = get(c)
    return try {
        // Lo que llega de otro dispositivo (el PC, tu pareja) también se ve en los widgets al momento
        if (e.sincroniza()) Widgets.pedir(c)
        EscritosApp.fallos = 0
        // Y lo que leen las hermanas (los capítulos terminados y las palabras del día), sin esperar al trabajo de fondo ni a «Sincronizar ahora»
        val diferido = runCatching { withContext(Dispatchers.IO) { Publicar.todo(c) } }.getOrDefault(false)
        if (System.currentTimeMillis() - EscritosApp.ordenada > 6 * 3_600_000L) { EscritosApp.ordenada = System.currentTimeMillis(); e.purga(); runCatching { e.limpia() } }
        if (e.pendientes().isNotEmpty()) pronto(c) else if (diferido) pronto(c, Publicar.QUIETO)
        true
    } catch (ex: Exception) {
        EscritosApp.fallos++
        e.error = ex.message ?: "sin conexión"
        if (EscritosApp.fallos <= 8) pronto(c, minOf(300_000L, 5_000L * Math.pow(3.0, (EscritosApp.fallos - 1).toDouble()).toLong()))
        NubeTarea.encolar(c)
        false
    }
}

/** Baja lo nuevo de tus otros dispositivos (varias peticiones seguidas se juntan en una). */
fun Escritos.Companion.bajaPronto(ctx: Context) {
    val c = ctx.applicationContext
    if (!Cuenta.vinculada || !EscritosApp.escuchando.compareAndSet(false, true)) return
    EscritosApp.scope.launch {
        delay(150)
        EscritosApp.escuchando.set(false)
        runCatching { sincroniza(c) }
    }
}
