package com.gonzalo.mau.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.gonzalo.mau.Accion
import com.gonzalo.mau.Cerebro
import com.gonzalo.mau.Frases
import com.gonzalo.mau.Guardado
import com.gonzalo.mau.Lugar

/** Lo que Mau necesita de cada sitio (el móvil o el ordenador). */
interface MauCasa {
    /** Esta app: «marfil» (Seshat), «hiedra» (Bastet), «cobalto» (Thot), «azabache» (Jonsu), «mostaza» (Hathor) o «cobre» (Nut). */
    val app: String
    /** En el ordenador (ratón y ventana) o en el móvil (dedos). */
    val ordenador: Boolean
    /** Dónde guarda lo suyo (escondido, callado, sus ajustes). */
    val guardado: Guardado
    /** Si el sistema pide quitar las animaciones. */
    val sinAnimaciones: Boolean
    /** ¿Está [app] en este dispositivo? */
    fun instalada(app: String): Boolean
    /** Abre [app] (y Mau va con ella). false si no está. */
    fun abre(app: String): Boolean
    /** Una vibración suave, como un ronroneo (en el ordenador, nada). */
    fun ronronea()
    /** Un toque de vibración al pulsar. */
    fun tic()
    /** Hace [f] en el hilo de la pantalla. */
    fun enPrincipal(f: () -> Unit)
}

/** Las seis apps de la suite, como salen en el menú de Mau (con los colores de su icono). */
class AppSuite(
    val app: String, val nombre: String, val que: String, val letra: String,
    val fondo: Color, val tinta: Color, val punto: Color, val sobrePunto: Color, val acento: Color
)

val APPS_SUITE = listOf(
    AppSuite("marfil", "Seshat", "LIBROS", "S", Color(0xFFF8F0D4), Color(0xFF050404), Color(0xFFC68B00), Color(0xFF050404), Color(0xFFC68B00)),
    AppSuite("hiedra", "Bastet", "JUEGOS", "B", Color(0xFF26301E), Color(0xFFF2EEDC), Color(0xFF8C9A48), Color(0xFF141A0E), Color(0xFF8C9A48)),
    AppSuite("cobalto", "Thot", "ESCRITOS", "T", Color(0xFF19202E), Color(0xFFF2EEDC), Color(0xFFB5B83A), Color(0xFF19202E), Color(0xFFB5B83A)),
    AppSuite("azabache", "Jonsu", "DIARIO", "J", Color(0xFF171515), Color(0xFFF2EEDC), Color(0xFFB3362E), Color(0xFFFFFFFF), Color(0xFFB3362E)),
    AppSuite("mostaza", "Hathor", "CINE", "H", Color(0xFF6B4A22), Color(0xFFF8F0D4), Color(0xFFE9D9C0), Color(0xFF3E2A12), Color(0xFFD98A9B)),
    AppSuite("cobre", "Nut", "TELE", "N", Color(0xFF4A1018), Color(0xFFF8F0D4), Color(0xFFE89A5A), Color(0xFF3A0C12), Color(0xFFE89A5A))
)

/** Una acción del menú de Mau propia de una app (p. ej. el sprint de Thot). Solo sale si [visible]. */
class AccionMenu(val texto: String, val visible: () -> Boolean = { true }, val hacer: () -> Unit)

/**
 * Mau en esta app: uno por proceso (todas las pantallas de la app comparten el mismo gato). Se arranca con [inicia] y desde
 * cualquier parte de la app se le habla: [di], [celebra], [lugar]… (desde cualquier hilo: lo pasa al de la pantalla).
 */
object Mau {
    @Volatile private var casaInt: MauCasa? = null
    @Volatile private var cerebroInt: Cerebro? = null
    val iniciado: Boolean get() = casaInt != null
    val casa: MauCasa get() = casaInt ?: error("Mau.inicia no se ha llamado")
    val cerebro: Cerebro get() = cerebroInt ?: error("Mau.inicia no se ha llamado")

    /** Cambia cuando cambia algo de Mau (leído desde Compose, repinta). */
    var version by mutableLongStateOf(0L)
        private set

    /** Llegó desde otra app de la familia (por el menú de Mau): al verse, sube desde el borde y saluda. */
    var llega by androidx.compose.runtime.mutableStateOf(false)

    /** Las acciones de esta app en el menú de Mau. */
    val acciones = java.util.concurrent.CopyOnWriteArrayList<AccionMenu>()

    @Synchronized
    fun inicia(casa: MauCasa) {
        if (casaInt != null) return
        casaInt = casa
        val c = Cerebro(casa.guardado)
        c.alCambiar = { version++ }
        cerebroInt = c
    }

    val acento: Color get() = APPS_SUITE.firstOrNull { it.app == casaInt?.app }?.acento ?: Color(0xFFC68B00)

    fun ahora() = System.currentTimeMillis()

    // ---- Dónde está (según la pantalla) ----
    private class Entrada(val lugar: Lugar)
    private val pilas = HashMap<Any, ArrayList<Entrada>>()
    private val bases = HashMap<Any, Lugar>()

    /** El lugar de la ventana o actividad [clave]: lo último que pidió una pantalla suya, o el de la ventana. */
    fun lugarDe(clave: Any): Lugar = synchronized(pilas) { pilas[clave]?.lastOrNull()?.lugar ?: bases[clave] ?: Lugar.CASA }

    /** El lugar de una ventana o actividad entera (el lector: LEYENDO). */
    fun base(clave: Any, lugar: Lugar) { synchronized(pilas) { bases[clave] = lugar }; version++ }

    /** Una pantalla pide [lugar] mientras se ve. Devuelve con qué quitarlo ([quita]). */
    fun empuja(clave: Any, lugar: Lugar): Any {
        val e = Entrada(lugar)
        synchronized(pilas) { pilas.getOrPut(clave) { ArrayList() } += e }
        version++
        return e
    }

    fun quita(clave: Any, ficha: Any) {
        synchronized(pilas) { pilas[clave]?.remove(ficha); if (pilas[clave]?.isEmpty() == true) pilas.remove(clave) }
        version++
    }

    private val margenes = HashMap<Any, ArrayList<Pair<Any, Float>>>()

    /** Lo que ocupan abajo las pantallas de [clave] que se ven ahora (en dp; el mayor), para que Mau no lo tape. */
    fun margenDe(clave: Any): Float = synchronized(pilas) { margenes[clave]?.maxOfOrNull { it.second } ?: 0f }

    /** Una pantalla ocupa [dp] abajo (una banda de reproductor, una barra) mientras se ve. Se quita con [quitaMargen]. */
    fun ponMargen(clave: Any, dp: Float): Any {
        val f = Any()
        synchronized(pilas) { margenes.getOrPut(clave) { ArrayList() } += f to dp }
        version++
        return f
    }
    fun quitaMargen(clave: Any, ficha: Any) {
        synchronized(pilas) { margenes[clave]?.removeAll { it.first === ficha }; if (margenes[clave]?.isEmpty() == true) margenes.remove(clave) }
        version++
    }

    private val fueras = HashMap<Any, Int>()

    /** ¿Hay alguna pantalla de [clave] que pide que Mau no salga (un juego, la sala de la tele)? */
    fun fueraDe(clave: Any): Boolean = synchronized(pilas) { (fueras[clave] ?: 0) > 0 }

    /** Una pantalla pide que Mau no salga mientras se ve. Se deshace con [vuelveA]. */
    fun sal(clave: Any) { synchronized(pilas) { fueras[clave] = (fueras[clave] ?: 0) + 1 }; version++ }
    fun vuelveA(clave: Any) { synchronized(pilas) { val n = (fueras[clave] ?: 0) - 1; if (n <= 0) fueras.remove(clave) else fueras[clave] = n }; version++ }

    /** La ventana o actividad [clave] se ha cerrado. */
    fun olvida(clave: Any) { synchronized(pilas) { pilas.remove(clave); bases.remove(clave); fueras.remove(clave); margenes.remove(clave) }; version++ }

    private fun enPrincipal(f: (Cerebro) -> Unit) { val c = cerebroInt ?: return; casa.enPrincipal { f(c) } }

    /** Que diga [texto] por su cuenta (si toca: no si está callado, dormido, escondido o habló hace poco; salvo [importante]). */
    fun di(texto: String, accion: Accion? = null, importante: Boolean = false) = enPrincipal { it.di(texto, ahora(), accion, espontaneo = true, importante = importante) }

    /** Que diga [texto] porque se lo has pedido tú (siempre, salvo que le hayas quitado el habla). */
    fun responde(texto: String, accion: Accion? = null) = enPrincipal { it.di(texto, ahora(), accion, espontaneo = false) }

    /** Algo que celebrar: salta y lo dice. */
    fun celebra(texto: String?, accion: Accion? = null) = enPrincipal { it.celebra(texto, ahora(), accion) }

    @Volatile private var ultimoToque = 0L

    /** Has hecho algo (tocar, teclear, pasar página). Si dormía, se despierta. */
    fun toque() {
        val c = cerebroInt ?: return
        // (llega con cada movimiento del dedo: basta con uno cada medio segundo)
        val t = ahora()
        if (t - ultimoToque < 500 && c.fase == Cerebro.Fase.DESPIERTO) return
        ultimoToque = t
        casa.enPrincipal {
            val dormia = c.fase == Cerebro.Fase.DURMIENDO
            c.toque(ahora())
            if (dormia) c.di(c.elige(Frases.despertar), ahora())
        }
    }
}
