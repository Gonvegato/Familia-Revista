package com.gonzalo.familia.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Config
import com.gonzalo.familia.nucleo.DiscoArchivos
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.Red
import com.gonzalo.familia.nucleo.RedJava
import com.gonzalo.familia.nucleo.Sesion
import com.gonzalo.familia.nucleo.comoMapa
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/**
 * La cuenta de la familia en el ordenador: una sola por app (sobre el núcleo, [Familia]), como `LaFamilia` en Android.
 *
 *  - Mientras la app está abierta se escucha la cuenta en directo (solo los sellos: unos bytes) y lo que cambies sube al momento.
 *  - Al cerrar la ventana se sube lo que falte (la cola está en disco: si no hay red, sube la próxima vez que abras cualquier día).
 *  - Entrar una vez: la sesión (nunca la contraseña) se guarda en el llavero del sistema y la leen las otras tres apps de este
 *    usuario, que entran solas.
 *  - [cambios] cambia con cada cambio de la cuenta: leerlo desde Compose repinta.
 */
object LaFamilia {
    /** Las cuatro apps de la familia en el ordenador. */
    val APPS = listOf("azabache", "cobalto", "hiedra", "marfil")
    private const val SESION_COMUN = "sesion"

    @Volatile private var f: Familia? = null
    @Volatile var app: String = ""; private set
    @Volatile private var llavero: Llavero = Llavero.delSistema

    /** Cambia con cada cambio de la cuenta (leído desde Compose, repinta). */
    var cambios by mutableIntStateOf(0)
        private set

    /** Lo último que pasó al sincronizar («Sincronizado», «Sin conexión: se reintenta solo»…). */
    var estado by mutableStateOf("")
        private set

    /** Cuándo se sincronizó bien por última vez (ms; 0 = aún no en esta sesión). */
    var ultima by mutableStateOf(0L)
        private set

    val familia: Familia get() = f ?: error("LaFamilia.inicia(app) no se ha llamado")
    val iniciada: Boolean get() = f != null

    /** En las pruebas: otra dirección (el emulador de Firebase), otra carpeta y otro llavero. */
    @Volatile var configPruebas: Config? = null
    @Volatile var dirPruebas: File? = null
    @Volatile var llaveroPruebas: Llavero? = null
    /** En las pruebas, lo que va «al hilo de la pantalla» se hace al momento. */
    @Volatile var alMomento = false

    /** Al arrancar la app. Se puede llamar varias veces. */
    @Synchronized
    fun inicia(nombreApp: String): Familia {
        f?.let { return it }
        app = nombreApp
        llavero = llaveroPruebas ?: Llavero.delSistema
        val dir = dirPruebas ?: Sitio.de(nombreApp)
        val seguro = DiscoSeguro(File(dir, "seguro"), llavero)
        val nueva = Familia(configPruebas ?: Config(), RedJava(), DiscoArchivos(File(dir, "familia")), seguro)
        nueva.alSincronizar = { inf ->
            enPantalla {
                estado = when {
                    inf.ok -> "Sincronizado"
                    inf.error == "Sin cuenta" -> ""
                    inf.error == "Sin conexión" -> "Sin conexión: se reintenta solo."
                    else -> inf.error.orEmpty()
                }
                if (inf.ok) ultima = System.currentTimeMillis()
            }
        }
        nueva.alCambiarClaves = { avisa() }
        f = nueva
        // Sin sesión: si otra app de la familia de este ordenador ya ha entrado, se entra sola
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
    private val oyentes = CopyOnWriteArrayList<() -> Unit>()
    private val alEntrarOyentes = CopyOnWriteArrayList<() -> Unit>()

    /** Para lo que no es Compose: se llama en el hilo de la pantalla tras cada cambio. */
    fun alCambiar(o: () -> Unit) { oyentes += o }

    /** Cada vez que esta app entra en la cuenta (la pidas aquí o llegue de una hermana). */
    fun alEntrar(o: () -> Unit) { alEntrarOyentes += o }

    @Volatile private var avisoPendiente = false
    private fun avisa() {
        if (avisoPendiente) return
        avisoPendiente = true
        // Varios cambios seguidos se juntan en un solo repintado
        enPantalla(50) { avisoPendiente = false; cambios++; oyentes.forEach { runCatching { it() } } }
    }

    private fun enPantalla(retraso: Long = 0, que: () -> Unit) {
        if (alMomento) { que(); return }
        if (retraso <= 0) SwingUtilities.invokeLater(que)
        else javax.swing.Timer(retraso.toInt()) { que() }.apply { isRepeats = false; start() }
    }

    // ---------------------------------------------------------------- entrar una vez

    private val cerrojoSesion = java.util.concurrent.locks.ReentrantLock()

    /**
     * Sin sesión aquí: se mira si otra app de la familia de este ordenador ya ha entrado (en segundo plano; con [esperar], aquí mismo,
     * esperando a lo que ya se estuviera mirando).
     */
    fun pideSesion(esperar: Boolean = false) {
        val fam = f ?: return
        if (fam.sesion.dentro) return
        val hazlo = {
            if (esperar) cerrojoSesion.lock()
            if (esperar || cerrojoSesion.tryLock()) try { if (!fam.sesion.dentro) runCatching { adoptaComun(fam) } } finally { cerrojoSesion.unlock() }
        }
        if (esperar) hazlo() else Thread({ hazlo() }, "familia-sesion").apply { isDaemon = true }.start()
    }

    private fun adoptaComun(fam: Familia): Boolean {
        val m = llavero.lee(SESION_COMUN)?.let { runCatching { Json.lee(it) }.getOrNull() }.comoMapa()
        if (m.isEmpty()) return false
        try { fam.sesion.adopta(m) } catch (e: Sesion.Error) {
            // La cuenta ya no acepta esa sesión (contraseña cambiada…): se quita para que nadie la vuelva a probar
            if (!e.sinRed) llavero.borra(SESION_COMUN)
            return false
        }
        fam.programa(0)
        enPantalla { cambios++; alEntrarOyentes.forEach { runCatching { it() } } }
        return true
    }

    /** Entra con usuario y contraseña (bloquea: llámalo fuera del hilo de la pantalla). Las otras tres apps entran solas. */
    fun entrar(usuario: String, contrasena: String) {
        val fam = familia
        val antes = fam.sesion.uid
        fam.sesion.entrar(usuario, contrasena)
        if (antes.isNotBlank() && antes != fam.sesion.uid) fam.olvidaTodo()
        llavero.guarda(SESION_COMUN, Json.escribe(fam.sesion.comparte()))
        fam.programa(0)
        enPantalla { cambios++; alEntrarOyentes.forEach { runCatching { it() } } }
    }

    /** Sale de la cuenta en esta app (las demás siguen dentro) y olvida lo de la cuenta en este ordenador. */
    fun salir() {
        val fam = familia
        fam.deja()
        fam.sesion.salir()
        fam.olvidaTodo()
        llavero.borra(SESION_COMUN)
        enPantalla { cambios++; estado = "" }
    }

    val dentro: Boolean get() { cambios; return f?.sesion?.dentro == true }
    val usuario: String get() { cambios; return f?.sesion?.usuario.orEmpty() }
    val uid: String get() = f?.sesion?.uid.orEmpty()
    val nombreLlavero: String get() = llavero.nombre

    // ---------------------------------------------------------------- la vida de la ventana

    /** Al abrirse la ventana (y al volver a ella): se escucha en directo y, sin sesión, se mira si una hermana ya entró. */
    fun alVerse() {
        pideSesion()
        f?.escucha()
    }

    /** Al cerrar la ventana: deja de escuchar y sube lo pendiente (como mucho [tope] ms; lo que no suba, sube la próxima vez). */
    fun alCerrar(tope: Long = 6_000) {
        val fam = f ?: return
        fam.deja()
        if (fam.pendientes == 0 || !fam.sesion.dentro) return
        val t = Thread({ runCatching { fam.sincroniza() } }, "familia-al-cerrar").apply { isDaemon = true; start() }
        t.join(tope)
    }

    /** Sincroniza ya (bloquea). */
    fun sincronizaYa(): Familia.Informe = familia.sincroniza()

    /** Una clave de servicios de la cuenta («kdrive_token», «steamgriddb»…). */
    fun clave(nombre: String): String = runCatching { familia.clave(nombre) }.getOrDefault("")

    /** ¿Hay red con la cuenta? (Para enseñar «sin conexión».) */
    fun sinRed(e: Throwable): Boolean = e is Red.SinRed || (e is Sesion.Error && e.sinRed)

    /** Solo para las pruebas: olvida la cuenta de este proceso (la siguiente [inicia] empieza de cero). */
    fun reiniciaParaPruebas() {
        f?.deja()
        f = null
        vigiladas.clear(); oyentes.clear(); alEntrarOyentes.clear()
    }
}
