package com.gonzalo.familia

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Config
import com.gonzalo.familia.nucleo.DiscoArchivos
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.RedJava
import java.io.File

/**
 * La cuenta de la familia en Android: una sola por app (sobre el núcleo, `com.gonzalo.familia.nucleo.Familia`).
 *
 *  - Se escucha en directo mientras se ve cualquier pantalla de la app; al ocultarse, lo que falte se sube con un trabajo de fondo
 *    en cuanto haya red (aunque Android congele la app).
 *  - Cada hora, con red, un trabajo de fondo baja lo nuevo (para que los widgets estén al día sin abrir la app).
 *  - [cambios] cambia cada vez que cambia algo de la cuenta: leerlo en una pantalla de Compose la repinta sola.
 */
object LaFamilia {
    /** Las apps de la familia (sus paquetes). */
    val APPS = listOf("com.gonzalo.hiedra", "com.gonzalo.marfil", "com.gonzalo.cobalto", "com.gonzalo.gamerate", "com.gonzalo.mostaza", "com.gonzalo.cobre")

    @Volatile private var f: Familia? = null
    private lateinit var appCtx: Context
    /** El contexto de la app (desde que se llamó a [inicia]). */
    val contexto: Context get() = appCtx
    private val principal = Handler(Looper.getMainLooper())

    /** Cambia con cada cambio de la cuenta (leído desde Compose, repinta). */
    var cambios by mutableIntStateOf(0)
        private set

    /** Lo último que pasó al sincronizar («Sincronizado», «Sin conexión: se reintenta solo»…). */
    var estado by mutableStateOf("")
        private set

    /** En las pruebas: otra dirección (el emulador de Firebase). */
    @Volatile var configPruebas: Config? = null

    val familia: Familia get() = f ?: error("LaFamilia.inicia(contexto) no se ha llamado")

    /** Al arrancar la app (en Application.onCreate o en la primera actividad). Se puede llamar varias veces. */
    @Synchronized
    fun inicia(ctx: Context): Familia {
        f?.let { return it }
        appCtx = ctx.applicationContext
        val dir = File(appCtx.filesDir, "familia")
        val seguro = DiscoCifrado.de(appCtx, File(appCtx.filesDir, "familia_seguro"))
        val nueva = Familia(configPruebas ?: Config(), RedJava(), DiscoArchivos(dir), seguro)
        nueva.alEncolar = { SubidaFamilia.encola(appCtx) }
        nueva.alSincronizar = { inf ->
            principal.post {
                estado = when {
                    inf.ok -> "Sincronizado"
                    inf.error == "Sin cuenta" -> ""
                    inf.error == "Sin conexión" -> "Sin conexión: se reintenta solo."
                    else -> inf.error.orEmpty()
                }
            }
            if (!inf.ok && nueva.pendientes > 0) SubidaFamilia.encola(appCtx)
        }
        nueva.alCambiarClaves = { avisa() }
        f = nueva
        principal.post { vigilaVida() }
        SubidaFamilia.periodica(appCtx)
        // Sin sesión: si una hermana ya la tiene, se entra solo
        pideSesion()
        return nueva
    }

    /** Una colección de la cuenta, avisando a Compose de sus cambios. */
    fun coleccion(nombre: String): Coleccion {
        val c = familia.coleccion(nombre)
        if (vigiladas.add(nombre)) c.alCambiar { avisa() }
        return c
    }

    /** Una colección de tu pareja (solo lectura). */
    fun ajena(uid: String, nombre: String): Coleccion {
        val c = familia.ajena(uid, nombre)
        if (vigiladas.add("$uid/$nombre")) c.alCambiar { avisa() }
        return c
    }

    private val vigiladas = java.util.Collections.synchronizedSet(HashSet<String>())

    private val oyentes = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    /** Para lo que no es Compose (los widgets): se llama en el hilo principal tras cada cambio. */
    fun alCambiar(o: () -> Unit) { oyentes += o }

    private val alEntrarOyentes = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    /** Se llama (en el hilo principal) cada vez que esta app entra en la cuenta, la pidas aquí o llegue de una hermana. */
    fun alEntrar(o: () -> Unit) { alEntrarOyentes += o }

    /** Sin sesión aquí: se pide a las hermanas (en segundo plano). Se mira al arrancar y cada vez que vuelves a la app. */
    private val pidiendo = java.util.concurrent.atomic.AtomicBoolean(false)
    fun pideSesion() {
        val fam = f ?: return
        if (fam.sesion.dentro || !pidiendo.compareAndSet(false, true)) return
        Thread { try { runCatching { Acceso.pideAHermanas(appCtx) } } finally { pidiendo.set(false) } }.start()
    }

    @Volatile private var avisoPendiente = false
    private fun avisa() {
        if (avisoPendiente) return
        avisoPendiente = true
        // Varios cambios seguidos se juntan en un solo repintado
        principal.postDelayed({ avisoPendiente = false; cambios++; oyentes.forEach { runCatching { it() } } }, 50)
    }

    private var vigilando = false
    private fun vigilaVida() {
        if (vigilando) return
        vigilando = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Si entraste en otra app mientras esta seguía abierta, entra ahora sola
                pideSesion()
                f?.escucha()
            }
            override fun onStop(owner: LifecycleOwner) {
                f?.deja()
                if ((f?.pendientes ?: 0) > 0) SubidaFamilia.encola(appCtx)
            }
        })
    }

    /** Entra con usuario y contraseña (bloquea: llámalo fuera del hilo principal). Lo recuerda para tus otras apps. */
    fun entrar(usuario: String, contrasena: String) {
        val fam = familia
        val antes = fam.sesion.uid
        fam.sesion.entrar(usuario, contrasena)
        if (antes.isNotBlank() && antes != fam.sesion.uid) fam.olvidaTodo()
        Acceso.guarda(appCtx, fam.sesion.usuario, contrasena)
        fam.programa(0)
        principal.post { cambios++; alEntrarOyentes.forEach { runCatching { it() } } }
    }

    /** Sale de la cuenta en esta app (las demás siguen dentro) y olvida lo de la cuenta en este dispositivo. */
    fun salir() {
        val fam = familia
        fam.deja()
        fam.sesion.salir()
        fam.olvidaTodo()
        Acceso.olvida(appCtx)
        principal.post { cambios++ }
    }

    /** Sincroniza ya (bloquea). */
    fun sincronizaYa() = familia.sincroniza()

    /** Solo para las pruebas: olvida la cuenta de este proceso (la siguiente [inicia] empieza de cero). */
    fun reiniciaParaPruebas() {
        f?.deja()
        f = null
        vigiladas.clear(); oyentes.clear(); alEntrarOyentes.clear()
    }
}
