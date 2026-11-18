package com.gonzalo.mau

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Dónde está Mau, según lo que hagas: de eso sale su postura y cuándo se duerme si no haces nada (null: no se duerme). */
enum class Lugar(val pose: Pose, val dormirTras: Long?) {
    /** En cualquier pantalla: sentado, esperando. */
    CASA(Pose.SENTADO, 3 * 60_000L),
    /** Leyendo un libro (Seshat): con gafas y su libro. Pasar página cuenta como hacer algo. */
    LEYENDO(Pose.LEYENDO, 12 * 60_000L),
    /** Escribiendo (Thot): con su pluma. */
    ESCRIBIENDO(Pose.ESCRIBIENDO, 8 * 60_000L),
    /** Viendo una peli, una serie o un partido: de espaldas, mira contigo. No habla ni se duerme. */
    VIENDO(Pose.VIENDO, null),
    /** En Bastet: con su ovillo. */
    JUGANDO(Pose.JUGANDO, 3 * 60_000L)
}

/** Dónde se esconde (solo enseña las orejas). */
enum class Borde { ABAJO, IZQUIERDA, DERECHA, ARRIBA }

/** Lo que se guarda de Mau en cada app (en Android, sus preferencias; en el ordenador, un archivo de la app). */
interface Guardado {
    fun lee(clave: String): String?
    fun guarda(clave: String, valor: String?)
}

/** Para las pruebas y por si no hay dónde guardar. */
class GuardadoEnMemoria : Guardado {
    private val m = HashMap<String, String>()
    override fun lee(clave: String) = m[clave]
    override fun guarda(clave: String, valor: String?) { if (valor == null) m.remove(clave) else m[clave] = valor }
}

/** Un botón del bocadillo («Empezar», «Abrir Jonsu»…). */
class Accion(val texto: String, val hacer: () -> Unit)

/** Lo que dice Mau ahora. */
class Bocadillo(val texto: String, val accion: Accion?, val hasta: Long, val id: Long)

/**
 * Lo que piensa Mau: si está despierto o dormido, escondido, callado, qué dice. Kotlin puro: el tiempo se le pasa ([ahora]) y lo que
 * se ve lo pinta quien lo use (mau/compose). Se usa desde un solo hilo (el de la pantalla).
 */
class Cerebro(
    private val guardado: Guardado = GuardadoEnMemoria(),
    private val zona: ZoneId = ZoneId.systemDefault(),
    semilla: Long = System.nanoTime()
) {
    private val rnd = kotlin.random.Random(semilla)
    /** Se llama cada vez que cambia algo que se ve. */
    var alCambiar: () -> Unit = {}
    private fun cambia() { version++; runCatching { alCambiar() } }
    var version = 0L
        private set

    // ---------------- Ajustes (se guardan) ----------------
    /** Si dice cosas (bocadillos) por su cuenta. */
    var habla: Boolean = guardado.lee("habla") != "no"
        set(v) { field = v; guardado.guarda("habla", if (v) null else "no"); if (!v) bocadillo = null; cambia() }
    /** Si se mueve (respirar, la cola, parpadear…). Si no, se queda quieto en su postura. */
    var mueve: Boolean = guardado.lee("mueve") != "no"
        set(v) { field = v; guardado.guarda("mueve", if (v) null else "no"); cambia() }
    /** Si se duerme cuando no haces nada. */
    var duerme: Boolean = guardado.lee("duerme") != "no"
        set(v) { field = v; guardado.guarda("duerme", if (v) null else "no"); if (!v) despierta(); cambia() }
    /** Tamaño: 0 pequeño, 1 normal, 2 grande. */
    var tamano: Int = guardado.lee("tamano")?.toIntOrNull()?.coerceIn(0, 2) ?: 1
        set(v) { field = v.coerceIn(0, 2); guardado.guarda("tamano", field.toString()); cambia() }

    /** Escondido: solo se le ven las orejas en [borde]. */
    var escondido: Boolean = guardado.lee("escondido") == "si"
        private set
    /** El borde donde se esconde. */
    var borde: Borde = guardado.lee("borde")?.let { b -> Borde.entries.firstOrNull { it.name == b } } ?: Borde.ABAJO
        private set
    /** Por dónde de ese borde (0: al principio —a la izquierda o arriba—; 1: al final). */
    var sitioBorde: Float = guardado.lee("sitioBorde")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        private set
    /** Dónde se sienta (abajo), de 0 (a la izquierda) a 1 (a la derecha). */
    var sitio: Float = guardado.lee("sitio")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        private set
    /** Callado hasta (ms; 0: no). */
    var calladoHasta: Long = guardado.lee("calladoHasta")?.toLongOrNull() ?: 0L
        private set

    fun callado(ahora: Long) = ahora < calladoHasta

    fun ponSitio(f: Float) { sitio = f.coerceIn(0f, 1f); guardado.guarda("sitio", sitio.toString()); cambia() }
    fun ponBorde(b: Borde, f: Float) {
        borde = b; sitioBorde = f.coerceIn(0f, 1f)
        guardado.guarda("borde", b.name); guardado.guarda("sitioBorde", sitioBorde.toString()); cambia()
    }

    // ---------------- Despierto, dormido ----------------
    enum class Fase { DESPIERTO, BOSTEZANDO, DURMIENDOSE, DURMIENDO }
    var fase = Fase.DESPIERTO
        private set
    /** Desde cuándo está en [fase] (ms). */
    var faseDesde = 0L
        private set
    private var ultimoToque = Long.MIN_VALUE
    private var temporal: Pose? = null
    private var temporalHasta = 0L

    /** Lo que dura el bostezo antes de dormirse. */
    val DURA_BOSTEZO = 2600L

    /** Has hecho algo (tocar, teclear, pasar página): si estaba dormido, se despierta. */
    fun toque(ahora: Long) {
        ultimoToque = ahora
        if (fase != Fase.DESPIERTO) despierta(ahora)
    }

    private fun despierta(ahora: Long = faseDesde) { fase = Fase.DESPIERTO; faseDesde = ahora; cambia() }

    /** Cuánto tarda en dormirse en [lugar] a esta hora (de noche, en casa, mucho antes). null: no se duerme. */
    fun dormirTras(lugar: Lugar, ahora: Long): Long? {
        if (!duerme) return null
        val t = lugar.dormirTras ?: return null
        val h = Instant.ofEpochMilli(ahora).atZone(zona).hour
        return if (h < 6 && lugar == Lugar.CASA) 60_000L else t
    }

    /** Dónde estaba en el último [tick] (viendo algo, no habla por su cuenta). */
    var lugarActual: Lugar = Lugar.CASA
        private set

    /** Cada poco (un segundo basta): pasa el tiempo. */
    fun tick(ahora: Long, lugar: Lugar) {
        lugarActual = lugar
        if (ultimoToque == Long.MIN_VALUE) ultimoToque = ahora
        if (temporal != null && ahora >= temporalHasta) { temporal = null; cambia() }
        bocadillo?.let { if (ahora >= it.hasta) { bocadillo = null; cambia() } }
        sprintHasta?.let { if (ahora >= it) acabaSprint(ahora) }
        val tras = dormirTras(lugar, ahora)
        when (fase) {
            Fase.DESPIERTO -> if (tras != null && !escondido && temporal == null && sprintHasta == null && ahora - ultimoToque >= tras) {
                fase = Fase.BOSTEZANDO; faseDesde = ahora; cambia()
            }
            Fase.BOSTEZANDO -> if (tras == null) despierta(ahora) else if (ahora - faseDesde >= DURA_BOSTEZO) { fase = Fase.DURMIENDOSE; faseDesde = ahora; cambia() }
            Fase.DURMIENDOSE -> if (tras == null) despierta(ahora) else if (ahora - faseDesde >= DURA_DORMIRSE + FUNDIDO_DORMIR) { fase = Fase.DURMIENDO; faseDesde = ahora; cambia() }
            Fase.DURMIENDO -> if (tras == null) despierta(ahora)
        }
    }

    val dormido: Boolean get() = fase == Fase.DURMIENDO || fase == Fase.DURMIENDOSE

    /** La postura que toca ahora en [lugar] (sin contar lo de escondido, que lo pinta quien lo use). */
    fun pose(lugar: Lugar, ahora: Long): Pose {
        temporal?.let { if (ahora < temporalHasta) return it }
        return when (fase) {
            Fase.DESPIERTO -> lugar.pose
            Fase.BOSTEZANDO -> Pose.BOSTEZANDO
            // (se duerme desde «sentado»: los parpadeos, la cabeza que cae y el fundido a «durmiendo»)
            Fase.DURMIENDOSE -> Pose.SENTADO
            Fase.DURMIENDO -> Pose.DURMIENDO
        }
    }

    /** Si está dormiéndose: ms desde que empezó (para [Animador.mueve]). */
    fun dormirse(ahora: Long): Long? = if (fase == Fase.DURMIENDOSE && temporal == null) ahora - faseDesde else null

    /** Pone [p] durante [ms] (ronronear, celebrar, taparse). */
    fun pon(p: Pose, ms: Long, ahora: Long) {
        temporal = p; temporalHasta = ahora + ms
        ultimoToque = ahora
        if (fase != Fase.DESPIERTO) { fase = Fase.DESPIERTO; faseDesde = ahora }
        cambia()
    }

    /** Lo acaricias: ronronea un rato. */
    fun acaricia(ahora: Long) = pon(Pose.RONRONEANDO, 3_500, ahora)

    /** Algo que celebrar (un libro acabado, la meta de palabras): salta un rato y lo dice. */
    fun celebra(texto: String?, ahora: Long, accion: Accion? = null) {
        pon(Pose.CELEBRANDO, 4_000, ahora)
        if (texto != null) di(texto, ahora, accion, espontaneo = true, importante = true)
    }

    fun esconde() { escondido = true; guardado.guarda("escondido", "si"); bocadillo = null; cambia() }
    fun vuelve(ahora: Long) { escondido = false; guardado.guarda("escondido", null); toque(ahora); cambia() }

    /** Una hora callado (se tapa los ojos un momento). */
    fun calla(ahora: Long, ms: Long = 3_600_000L) {
        calladoHasta = ahora + ms; guardado.guarda("calladoHasta", calladoHasta.toString())
        bocadillo = null
        pon(Pose.TAPANDOSE, 2_500, ahora)
    }
    fun habla(ahora: Long) { calladoHasta = 0; guardado.guarda("calladoHasta", null); toque(ahora); cambia() }

    // ---------------- Lo que dice ----------------
    var bocadillo: Bocadillo? = null
        private set
    private var ultimoEspontaneo = Long.MIN_VALUE / 2
    private var nBocadillo = 0L

    /** Entre dos cosas que dice por su cuenta, como poco. */
    val PAUSA_HABLAR = 4 * 60_000L

    /**
     * Que diga [texto]. [espontaneo]: lo dice por su cuenta (no porque se lo hayas pedido): entonces no lo dice si está callado,
     * escondido, dormido, si no habla, si ya está diciendo otra cosa o si habló hace poco (salvo [importante]). true si lo dice.
     */
    fun di(texto: String, ahora: Long, accion: Accion? = null, espontaneo: Boolean = true, importante: Boolean = false): Boolean {
        if (!habla) return false
        if (espontaneo) {
            if (callado(ahora) || escondido || dormido || lugarActual == Lugar.VIENDO) return false
            if (!importante && (bocadillo != null || ahora - ultimoEspontaneo < PAUSA_HABLAR)) return false
            ultimoEspontaneo = ahora
        }
        val dura = (4_500L + texto.length * 55L).coerceAtMost(12_000L) + (if (accion != null) 4_000L else 0L)
        bocadillo = Bocadillo(texto, accion, ahora + dura, ++nBocadillo)
        cambia()
        return true
    }

    fun callaBocadillo() { if (bocadillo != null) { bocadillo = null; cambia() } }

    /** true la primera vez de cada día que se pregunta por [clave] (en esta app). Para lo que se dice una vez al día. */
    fun primeraVezHoy(clave: String, ahora: Long): Boolean {
        val hoy = LocalDate.ofInstant(Instant.ofEpochMilli(ahora), zona).toString()
        if (guardado.lee("dia.$clave") == hoy) return false
        guardado.guarda("dia.$clave", hoy)
        return true
    }

    /** Una de [frases] al azar (no la misma que la última vez que se eligió de esa lista). */
    fun elige(frases: List<String>): String {
        if (frases.size <= 1) return frases.firstOrNull().orEmpty()
        val ultima = ultimas[frases]
        var f: String
        do { f = frases[rnd.nextInt(frases.size)] } while (f == ultima)
        ultimas[frases] = f
        return f
    }
    private val ultimas = HashMap<List<String>, String>()

    // ---------------- Sprint ----------------
    /** Un sprint con Mau (escribir sin parar): hasta cuándo (ms), o null. */
    var sprintHasta: Long? = null
        private set
    var sprintDesde: Long = 0L
        private set
    private var alAcabarSprint: (() -> String?)? = null

    /** Empieza un sprint de [ms]. Al acabar, celebra con lo que devuelva [alAcabar] (p. ej. «412 palabras»). */
    fun empiezaSprint(ms: Long, ahora: Long, alAcabar: () -> String?) {
        sprintDesde = ahora; sprintHasta = ahora + ms; alAcabarSprint = alAcabar
        toque(ahora)
        di("¡Vamos! ${ms / 60_000} minutos sin parar. Yo vigilo el reloj.", ahora, espontaneo = false)
    }

    fun cortaSprint() { sprintHasta = null; alAcabarSprint = null; cambia() }

    private fun acabaSprint(ahora: Long) {
        val f = alAcabarSprint
        sprintHasta = null; alAcabarSprint = null
        val texto = runCatching { f?.invoke() }.getOrNull()
        pon(Pose.CELEBRANDO, 4_000, ahora)
        di(texto ?: "¡Sprint terminado!", ahora, espontaneo = false)
    }
}
