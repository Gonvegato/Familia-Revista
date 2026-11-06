package com.gonzalo.hiedra.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.Sitio
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/** De dónde es un juego de este ordenador. [id]: lo que lee el móvil en «Tu PC» (`s`: lo mismo que mandaba Atrio del PC). */
enum class Fuente(val id: String, val nombre: String) {
    STEAM("steam", "Steam"), HEROIC("heroic", "Heroic"), LUTRIS("lutris", "Lutris"), EMULACION("emulators", "Emulación"), PROGRAMA("local", "Programas");
    companion object {
        fun de(clave: String) = when {
            clave.startsWith("steam:") -> STEAM; clave.startsWith("heroic:") -> HEROIC; clave.startsWith("lutris:") -> LUTRIS
            clave.startsWith("rom:") -> EMULACION; else -> PROGRAMA
        }
    }
}

/**
 * Un juego de este ordenador: de Steam (`steam:<appid>`, la misma clave que usaba Atrio del PC), de Heroic (`heroic:<tienda>:<app>`), de
 * Lutris (`lutris:<id>`), una ROM de tus carpetas (`rom:<sistema>:<archivo>`, la misma clave que en el móvil) o un programa tuyo (`local:<id>`).
 * Dónde está es de este ordenador: no va a la cuenta.
 */
class JuegoPc(
    val clave: String, val nombre: String, val portada: String, val ruta: String = "",
    val fuente: Fuente = Fuente.de(clave),
    /** De dónde, como se enseña: «Steam», «Epic», «GOG», «Amazon», «Lutris», «GBA», «Programa»… */
    val etiqueta: String = if (fuente == Fuente.PROGRAMA) "Programa" else fuente.nombre,
    /** Una imagen grande y apaisada (el fondo de su página en la sala), si la hay. */
    val fondo: String = "",
    /** La carpeta donde está instalado (para saber, por sus procesos, cuándo se juega). */
    val carpeta: String = "",
    /** Las ROMs: su sistema y el nombre del archivo sin extensión (el de la cuenta). */
    val sistema: String = "", val archivo: String = "",
    /** Lo que hace falta para abrirlo (la tienda de Heroic, el id de Lutris, si es de Flatpak…). */
    val extra: Map<String, String> = emptyMap(),
) {
    val steamId: String? get() = clave.removePrefix("steam:").takeIf { clave.startsWith("steam:") }
    /** La clave de su lanzador en la cuenta: «pc:<clave>»; la de una ROM es la suya, como en el móvil. */
    val claveCuenta: String get() = if (fuente == Fuente.EMULACION) clave else "pc:$clave"
    fun lanzador(dev: String): Lanzador = if (fuente == Fuente.EMULACION) Lanzador.rom(sistema, archivo) else Lanzador.pc(clave, dev)
    fun con(mas: Map<String, String>) = JuegoPc(clave, nombre, portada, ruta, fuente, etiqueta, fondo, carpeta, sistema, archivo, extra + mas)
}

/**
 * Lo que hay en este ordenador para jugar: Steam, Heroic (Epic, GOG, Amazon), Lutris, las ROMs de tus carpetas y tus programas (los añades a
 * mano: un ejecutable, un .AppImage, una app del Mac…). Se mira en segundo plano y se guarda la lista ([lista]); [version] cambia cuando
 * cambia (leído desde Compose, repinta).
 */
object Pc {
    @Volatile var dirPruebas: File? = null
    private fun archivo() = File(dirPruebas ?: Sitio.de("hiedra"), "programas.json")

    /** (En las pruebas, otras carpetas de Steam.) */
    var steamPruebas: List<File>?
        get() = Steam.raicesPruebas
        set(v) { Steam.raicesPruebas = v; cache = null }

    /** En las pruebas: todo en [dir] y nada de lo de este ordenador (ni Heroic, ni Lutris, ni procesos), salvo el Steam que se diga. */
    fun paraPruebas(dir: File?, steam: List<File>?) {
        dirPruebas = dir; Steam.raicesPruebas = steam; Emulacion.dirPruebas = dir
        Heroic.raicesPruebas = if (dir == null) null else emptyList(); Lutris.salidaPruebas = if (dir == null) null else ""
        Procesos.lineasPruebas = if (dir == null) null else ({ emptyList() })
        cache = null
    }

    /** Las carpetas de Steam que se miran (sus bibliotecas). */
    fun carpetasSteam(): List<File> = Steam.raices().flatMap { Steam.bibliotecas(it) }

    fun steam(): List<JuegoPc> = Steam.juegos()

    /** Tus programas (los que has añadido aquí). */
    fun programas(): List<JuegoPc> = runCatching { Json.lee(archivo().readText()).comoLista() }.getOrDefault(emptyList()).map { it.comoMapa() }
        .map { JuegoPc(it["k"].comoTexto(), it["n"].comoTexto(), "", it["r"].comoTexto(), Fuente.PROGRAMA) }.filter { it.clave.isNotBlank() }

    fun anadePrograma(nombre: String, ruta: File): JuegoPc {
        val clave = "local:" + java.security.MessageDigest.getInstance("SHA-1").digest(ruta.absolutePath.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
        val l = programas().filter { it.clave != clave } + JuegoPc(clave, nombre, "", ruta.absolutePath, Fuente.PROGRAMA)
        archivo().writeText(Json.escribe(l.map { mapOf("k" to it.clave, "n" to it.nombre, "r" to it.ruta) }))
        cache = null
        return JuegoPc(clave, nombre, "", ruta.absolutePath, Fuente.PROGRAMA)
    }

    fun quitaPrograma(clave: String) {
        archivo().writeText(Json.escribe(programas().filter { it.clave != clave }.map { mapOf("k" to it.clave, "n" to it.nombre, "r" to it.ruta) }))
        cache = null
    }

    /** Una ROM como juego de este ordenador. */
    fun deRom(r: Rom): JuegoPc = JuegoPc(r.clave, Emulacion.titulo(r.archivo), Emulacion.portada(r), r.ruta, Fuente.EMULACION,
        Emulacion.sistema(r.sistema)?.corto ?: r.sistema.uppercase(), sistema = r.sistema, archivo = r.archivo)

    /** Todo lo de este ordenador, mirado ahora (bloquea: Lutris tarda unos segundos). [escanear]: recorre otra vez tus carpetas de ROMs. */
    fun todos(escanear: Boolean = false): List<JuegoPc> {
        val steam = runCatching { Steam.juegos() }.getOrDefault(emptyList())
        val heroic = runCatching { Heroic.juegos() }.getOrDefault(emptyList())
        val lutris = runCatching { Lutris.juegos(conSteam = steam.isNotEmpty()) }.getOrDefault(emptyList())
        val roms = runCatching { if (escanear) Emulacion.escaneaTodo() else Emulacion.romsGuardadas() }.getOrDefault(emptyList()).map(::deRom).distinctBy { it.clave }
        return (steam + heroic + lutris + roms + programas()).distinctBy { it.clave }
    }

    @Volatile private var cache: List<JuegoPc>? = null
        set(v) { field = v; indice = v.orEmpty().associateBy { it.claveCuenta } }
    @Volatile private var indice: Map<String, JuegoPc> = emptyMap()
    /** Cambia cada vez que cambia lo de este ordenador. */
    var version by mutableIntStateOf(0)
        private set
    @Volatile var mirando = false
        private set

    /** Lo de este ordenador (lo último que se miró; la primera vez, se mira ahora). */
    fun lista(): List<JuegoPc> = cache ?: todos().also { cache = it }
    /** Lo que hay, sin esperar (vacío si aún no se ha mirado). */
    fun yaMirado(): List<JuegoPc>? = cache

    /** Al terminar de mirar (para «Tu PC» del móvil). */
    @Volatile var alMirar: (() -> Unit)? = null

    /** Vuelve a mirar en segundo plano (y avisa al terminar). */
    fun refresca(escanear: Boolean = false, alTerminar: (() -> Unit)? = null) {
        if (mirando) return
        mirando = true; enPantalla { version++ }
        Thread({
            try { cache = todos(escanear) } finally { mirando = false }
            runCatching { alMirar?.invoke() }
            enPantalla { version++; alTerminar?.invoke() }
        }, "hiedra-mirar").apply { isDaemon = true; start() }
    }

    fun por(clave: String): JuegoPc? = lista().firstOrNull { it.clave == clave }
    /** El de este ordenador para un lanzador de la cuenta (por su clave: «pc:steam:…», «rom:gba:…»). */
    fun porClaveCuenta(clave: String): JuegoPc? { lista(); return indice[clave] }

    private fun enPantalla(que: () -> Unit) { if (SwingUtilities.isEventDispatchThread()) que() else SwingUtilities.invokeLater(que) }

    // ---------------------------------------------------------------- jugar

    /**
     * Lo que se está jugando ahora (desde Hiedra, o un juego de Steam abierto desde fuera). [juego]: el de la cuenta ("" si no está).
     * [cuenta]: Hiedra cuenta su tiempo (los de Steam no: sus horas las cuenta Steam).
     */
    class Partida(val clave: String, val nombre: String, val juego: String, val desde: Long, val portada: String, val cuenta: Boolean = false, val deHiedra: Boolean = true) {
        @Volatile internal var visto = 0L
        @Volatile internal var ultima = 0L
        @Volatile internal var fallos = 0
        @Volatile internal var acumulado = 0L
        @Volatile internal var carpeta = ""
        @Volatile internal var appSteam = ""
        @Volatile internal var proceso = false
        /** ¿Ya se ha visto el juego abierto? (para los que abre otro programa: Steam, Heroic, Lutris) */
        val enMarcha: Boolean get() = proceso || visto > 0
    }
    private val partidas = CopyOnWriteArrayList<Partida>()
    /** La partida de ahora (la última que empezó; antes las de Hiedra que las de fuera). */
    val ahora: Partida? get() = partidas.lastOrNull { it.deHiedra } ?: partidas.lastOrNull()
    @Volatile var alCambiarPartida: (() -> Unit)? = null
    /** Al terminar una partida que abrió Hiedra (para volver a poner Hiedra delante). */
    val alTerminar = CopyOnWriteArrayList<(Partida) -> Unit>()

    /** Cambia cuando empieza o acaba una partida (leído desde Compose, repinta). */
    var partidasVersion by mutableIntStateOf(0)
        private set
    private fun cambio() { alCambiarPartida?.invoke(); enPantalla { partidasVersion++ } }
    /** (En las pruebas: lo que contó la última partida que terminó.) */
    @Volatile var ultimaTerminadaParaPruebas = -1L
        private set
    /** (En las pruebas: vigilar una partida como si la hubiera abierto Heroic o Lutris, sin abrir nada.) */
    fun vigilaParaPruebas(p: Partida, carpeta: String) { p.carpeta = carpeta; partidas.clear(); partidas += p; ultimaMirada = 0L }

    private fun termina(p: Partida, ms: Long) {
        if (!partidas.remove(p)) return
        ultimaTerminadaParaPruebas = ms
        if (p.cuenta && p.juego.isNotBlank()) runCatching { Juegos.sumaPartida(p.juego, ms.coerceIn(0L, 12 * 3600_000L)) }
        cambio()
        // (si nunca se vio abierto —en el Mac no se ve cuándo Steam abre un juego—, no se toca la ventana)
        if (p.deHiedra && p.enMarcha) alTerminar.forEach { f -> enPantalla { runCatching { f(p) } } }
    }

    /**
     * Abre un juego. Los de Steam, con Steam (sus horas las cuenta Steam y llegan por su API); los de Heroic y Lutris, con ellos (Hiedra cuenta
     * el tiempo mientras ve el juego abierto); las ROMs, con su emulador, y tus programas, aquí mismo (Hiedra cuenta hasta que se cierran).
     * El tiempo va a las horas de este ordenador. Devuelve un mensaje para enseñar.
     */
    fun juega(j: JuegoPc, juegoId: String): String {
        val portada = j.portada.takeIf { it.startsWith("http") }.orEmpty()
        fun empieza(p: Partida): Partida { partidas.removeAll { it.clave == p.clave && !it.deHiedra }; partidas += p; cambio(); vigila(); return p }
        when (j.fuente) {
            Fuente.STEAM -> {
                val id = j.steamId ?: return "No sé abrir ${j.nombre}."
                runCatching { Programas.abre(Steam.orden(id, j.extra["flatpak"] == "1")) }.getOrElse { return "No he podido abrir Steam." }
                empieza(Partida(j.clave, j.nombre, juegoId, System.currentTimeMillis(), portada).also { it.appSteam = id })
                return "Abriendo ${j.nombre} en Steam"
            }
            Fuente.HEROIC, Fuente.LUTRIS -> {
                val orden = if (j.fuente == Fuente.HEROIC) Heroic.orden(j.extra["runner"].orEmpty(), j.extra["app"].orEmpty()) else Lutris.orden(j.extra["id"].orEmpty(), j.extra["flatpak"] == "1")
                runCatching { Programas.abre(orden) }.getOrElse { return "No he podido abrir ${j.fuente.nombre}." }
                val p = empieza(Partida(j.clave, j.nombre, juegoId, System.currentTimeMillis(), portada, cuenta = j.carpeta.isNotBlank()).also { it.carpeta = j.carpeta })
                val donde = if (j.fuente == Fuente.HEROIC) "Heroic" else "Lutris"
                return if (p.cuenta) "Abriendo ${j.nombre} con $donde" else "Abriendo ${j.nombre} con $donde (sin su carpeta no puedo contar el tiempo)"
            }
            Fuente.EMULACION -> {
                val f = File(j.ruta)
                if (!f.exists()) return "No encuentro ${f.name} en este ordenador."
                val rom = Rom(j.ruta, j.sistema, j.archivo)
                val orden = Emulacion.ordenPara(rom) ?: return "No hay emulador para ${Emulacion.sistema(j.sistema)?.nombre ?: j.sistema}: elígelo en Ajustes → Emulación."
                val proc = runCatching { Programas.abre(orden, f.parentFile) }.getOrElse { return "No se ha podido abrir el emulador: ${it.message}" }
                espera(proc, empieza(Partida(j.clave, j.nombre, juegoId, System.currentTimeMillis(), portada, cuenta = true).also { it.proceso = true }))
                return "A jugar: ${j.nombre}"
            }
            Fuente.PROGRAMA -> {
                val f = File(j.ruta)
                if (!f.exists()) return "No encuentro ${f.path} en este ordenador."
                val orden = when {
                    Sitio.sistema == Sitio.Sistema.MAC && f.name.endsWith(".app") -> listOf("open", "-W", f.absolutePath)
                    f.name.endsWith(".sh") -> listOf("bash", f.absolutePath)
                    else -> listOf(f.absolutePath)
                }
                if (!f.canExecute() && !f.name.endsWith(".sh") && !f.isDirectory) runCatching { f.setExecutable(true) }
                val proc = runCatching { Programas.abre(orden, f.parentFile) }.getOrElse { return "No se ha podido abrir: ${it.message}" }
                espera(proc, empieza(Partida(j.clave, j.nombre, juegoId, System.currentTimeMillis(), portada, cuenta = true).also { it.proceso = true }))
                return "A jugar: ${j.nombre}"
            }
        }
    }

    private fun espera(proc: Process, p: Partida) {
        Thread({ runCatching { proc.waitFor() }; termina(p, System.currentTimeMillis() - p.desde) }, "hiedra-partida").apply { isDaemon = true; start() }
    }

    // ---------------------------------------------------------------- vigilar (lo que abre otro programa, y Steam desde fuera)

    private const val ESPERA_MAX = 20 * 60_000L   // lo que se espera a ver el juego abierto (Steam o Heroic pueden actualizarlo antes)
    private const val HUECO = 2 * 60_000L         // un hueco así entre dos miradas: el ordenador estaba suspendido
    @Volatile private var vigilando = false
    @Volatile private var ultimaMirada = 0L

    /** Mira los procesos de vez en cuando: rápido mientras hay algo que esperar, despacio para ver si abres un juego de Steam desde fuera. */
    fun vigila() {
        if (vigilando) return
        vigilando = true
        Thread({
            while (vigilando) {
                runCatching { mira(System.currentTimeMillis(), Procesos.lineas()) }
                val rapido = partidas.any { !it.proceso }
                try { Thread.sleep(if (rapido) 3_000 else 10_000) } catch (_: InterruptedException) { break }
            }
        }, "hiedra-procesos").apply { isDaemon = true; start() }
    }

    /** Una mirada a los procesos (aparte, para las pruebas). */
    fun mira(ahora: Long, lineas: List<String>) {
        val hueco = ultimaMirada > 0 && ahora - ultimaMirada > HUECO
        ultimaMirada = ahora
        val steam = Steam.enMarcha(lineas)
        for (p in partidas.toList()) {
            if (p.proceso) continue
            val visto = if (p.appSteam.isNotBlank()) p.appSteam in steam else Procesos.hayDe(p.carpeta, lineas)
            when {
                visto && hueco && p.visto > 0 -> { p.acumulado += p.ultima - p.visto; p.visto = ahora; p.ultima = ahora; p.fallos = 0 }
                visto -> { if (p.visto == 0L) { p.visto = ahora; cambio() }; p.ultima = ahora; p.fallos = 0 }
                p.visto > 0 -> if (++p.fallos >= 2) termina(p, p.acumulado + p.ultima - p.visto)
                ahora - p.desde > ESPERA_MAX -> termina(p, 0)
            }
        }
        // Un juego de Steam abierto desde fuera de Hiedra: sale en «Tu PC» (a qué juegas) mientras está abierto; su tiempo lo cuenta Steam
        val locales = yaMirado().orEmpty().filter { it.fuente == Fuente.STEAM }.associateBy { it.steamId }
        for (id in steam) {
            if (partidas.any { it.appSteam == id }) continue
            val j = locales[id] ?: continue
            partidas += Partida(j.clave, j.nombre, Juegos.porClave(j.claveCuenta)?.id.orEmpty(), ahora, j.portada, deHiedra = false).also { it.appSteam = id; it.visto = ahora; it.ultima = ahora }
            cambio()
        }
    }
}
