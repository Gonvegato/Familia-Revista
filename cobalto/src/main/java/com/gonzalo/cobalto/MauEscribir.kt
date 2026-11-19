package com.gonzalo.cobalto

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.gonzalo.mau.Frases
import com.gonzalo.mau.ui.AccionMenu
import com.gonzalo.mau.ui.Mau
import kotlinx.coroutines.delay

/**
 * Mau en Thot: anima a escribir sin agobiar. Una vez al día, si a partir de las 12 aún no has escrito nada, te lo recuerda; con la meta
 * de palabras del día te avisa a mitad, cuando falta poco (≤ 10 %) y al cumplirla (cada cosa una vez al día); si en el editor llevas
 * [ATASCO] sin escribir (después de haber escrito algo), te da una idea; y hace los sprints contigo: los de Thot (los empieces desde su
 * menú, desde el de Thot o en otro dispositivo) los anuncia, vigila el reloj y celebra el final con las palabras escritas.
 *
 * Sin nada de Android: es el MISMO archivo que compila Thot para Linux y Mac (Mau, el sprint y las palabras de hoy son lo mismo en los
 * dos). Lo propio de cada uno (dónde está Mau en la pantalla, la meta del día, si la app está delante) va en MauThot.kt del móvil y en
 * escritorio/cobalto/…/pc/MauThot.kt.
 */
object MauEscribir {
    /** El sprint que se empieza desde el menú de Mau. */
    const val SPRINT = "Sprint de 25 min conmigo"
    const val MIN_SPRINT = 25
    /** Sin escribir, con el editor delante, tanto tiempo: atasco. */
    const val ATASCO = 3 * 60_000L
    /** Lo de «hoy aún no has escrito», a partir de esta hora. */
    const val HORA_ANIMAR = 12

    /** Lo que se dice de la meta del día. */
    enum class Meta(val clave: String) { MITAD("meta.mitad"), CASI("meta.casi"), CUMPLIDA("meta.cumplida") }

    /**
     * Lo que toca decir de la meta [meta] al pasar de [antes] a [ahora] palabras escritas hoy: lo más alto que se ha cruzado (cumplirla,
     * quedarse a ≤ 10 % o pasar la mitad), o null.
     */
    fun cruce(antes: Int, ahora: Int, meta: Int): Meta? {
        if (meta <= 0 || ahora <= antes) return null
        val casi = meta - meta / 10
        val mitad = (meta + 1) / 2
        return when {
            ahora >= meta -> if (antes < meta) Meta.CUMPLIDA else null
            ahora >= casi -> if (antes < casi) Meta.CASI else null
            ahora >= mitad -> if (antes < mitad) Meta.MITAD else null
            else -> null
        }
    }

    // ---------------- La meta del día ----------------
    private var diaVisto = -1L
    private var hechasAntes = -1

    /**
     * Las palabras de hoy son [hechas] y la meta [meta] (en el hilo de la pantalla, cada vez que cambian). La primera vez (y al cambiar de
     * día) solo apunta; luego, al cruzar la mitad, el ≤ 10 % o la meta, Mau lo dice (cada cosa una vez al día).
     */
    fun miraMeta(hechas: Int, meta: Int, dia: Long = Escritos.hoy()) {
        val antes = if (dia == diaVisto) hechasAntes else -1
        diaVisto = dia; hechasAntes = hechas
        if (antes < 0 || !Mau.iniciado) return
        val m = cruce(antes, hechas, meta) ?: return
        if (!Mau.cerebro.primeraVezHoy(m.clave, Mau.ahora())) return
        // (la meta es de lo importante: lo dice aunque haya hablado hace poco)
        when (m) {
            Meta.MITAD -> Mau.di(Frases.mitadMeta(hechas, meta), importante = true)
            Meta.CASI -> Mau.di(Frases.casiMeta(meta - hechas), importante = true)
            Meta.CUMPLIDA -> Mau.celebra(Frases.metaCumplida(meta))
        }
    }

    // ---------------- Hoy aún no has escrito ----------------
    /** Al abrir la app (o volver a ella): si hoy aún no has escrito nada ([hoy] palabras) y es por la tarde, te anima (una vez al día). */
    fun animaSiNoHasEscrito(hoy: Int, hora: Int) {
        if (!Mau.iniciado || hoy > 0 || hora < HORA_ANIMAR) return
        val c = Mau.cerebro; val t = Mau.ahora()
        // (solo si puede decirlo ahora: si no, se gastaría lo de una vez al día sin decir nada; ya lo intentará al volver)
        if (!c.habla || c.callado(t) || c.escondido || c.dormido || c.bocadillo != null) return
        if (!c.primeraVezHoy("sinEscribirHoy", t)) return
        Mau.di(c.elige(Frases.sinEscribirHoy))
    }

    /** Llevas [ATASCO] sin escribir en el editor. */
    fun atascada() { if (Mau.iniciado) Mau.di(Mau.cerebro.elige(Frases.atascada)) }

    // ---------------- El sprint ----------------
    /** El editor abierto ahora (lo escrito y la obra), o null fuera de él: el sprint de Mau es de esa obra. */
    @Volatile var editor: Pair<Escritos, String>? = null
    /** Lo que hay que guardar antes de contar las palabras del sprint (en el ordenador, lo que el editor aún no ha guardado). */
    @Volatile var guardaAntes: () -> Unit = {}
    /** El sprint que sigue Mau: su obra y cuándo empezó. */
    private var sprintDe: Pair<String, Long>? = null

    private val accionSprint = AccionMenu(SPRINT, visible = { editor != null && Mau.cerebro.sprintHasta == null && !SprintEscribir.activo() }) {
        editor?.let { (e, pid) ->
            runCatching { guardaAntes() }
            SprintEscribir.empieza(e, pid, MIN_SPRINT, 0)
            sigueSprint(e)
        }
    }

    /** Pone en el menú de Mau el sprint con él (una vez; después de arrancar Mau). */
    fun pon() { if (Mau.iniciado) Mau.acciones.addIfAbsent(accionSprint) }

    /**
     * Sigue el sprint de Thot ([SprintEscribir], en el hilo de la pantalla, cada vez que cambia su fin). Si empieza uno, Mau lo anuncia
     * y vigila el reloj (no se duerme); si era uno que ya estaba en marcha (al volver a abrir, o de otro dispositivo), lo sigue sin
     * anunciarlo. Al cumplirse la hora lo celebra él mismo con las palabras escritas; si se termina antes, a mano, dice cuántas fueron.
     */
    fun sigueSprint(e: Escritos) {
        if (!Mau.iniciado) return
        val c = Mau.cerebro
        val t = Mau.ahora()
        val hasta = SprintEscribir.hasta
        if (hasta > t) {
            if (c.sprintHasta == hasta) return
            val desde = if (SprintEscribir.minutos > 0) hasta - SprintEscribir.minutos * 60_000L else t
            val pid = SprintEscribir.pid
            val antes = c.bocadillo?.id
            sprintDe = pid to desde
            // (desde que empezó: así dice «25 minutos» y su reloj acaba a la vez que el de Thot)
            c.empiezaSprint(hasta - desde, desde) { Frases.sprintAcabado(palabrasDe(e, pid, desde)) }
            if (t - desde > 15_000L && c.bocadillo?.id != antes) c.callaBocadillo()
        } else {
            val sh = c.sprintHasta ?: return
            // Se acabó a su hora: lo celebra Mau al ver que ha llegado la suya (con las palabras de [palabrasDe])
            if (t >= sh - 2_000L) return
            // Se terminó antes, a mano (aquí o en otro dispositivo)
            val s = SprintEscribir.terminado?.takeIf { (it.pid to it.desde) == sprintDe }
            c.cortaSprint(); sprintDe = null
            if (s != null) Mau.responde(Frases.sprintAcabado(s.palabras))
        }
    }

    /** Las palabras del sprint de [pid] que empezó en [desde] al acabar (ya terminado en Thot, o aún por terminar), o null si no se sabe. */
    private fun palabrasDe(e: Escritos, pid: String, desde: Long): Int? {
        runCatching { guardaAntes() }
        SprintEscribir.terminado?.let { if (it.pid == pid && it.desde == desde) return it.palabras }
        return if (SprintEscribir.hasta > 0L && SprintEscribir.pid == pid) SprintEscribir.llevas(e) else null
    }

    fun reiniciaParaPruebas() { diaVisto = -1L; hechasAntes = -1; editor = null; sprintDe = null; guardaAntes = {} }
}

/**
 * Lo de Mau para toda la app (va en la raíz, para que siga fuera del editor): la meta del día ([meta], 0 si no hay), «hoy aún no has
 * escrito» al abrir o volver a la app ([delante]: si se ve) y el sprint.
 */
@Composable
fun MauEnThot(e: Escritos, meta: Int, delante: Boolean) {
    val ver = e.version
    val hoy = remember(ver) { e.escritas(Escritos.hoy()) }
    LaunchedEffect(hoy, meta) { MauEscribir.miraMeta(hoy, meta) }
    LaunchedEffect(delante) {
        if (!delante) return@LaunchedEffect
        // (un momento después de abrir: que llegue lo de la cuenta y que Mau haya saludado)
        delay(10_000)
        MauEscribir.animaSiNoHasEscrito(e.escritas(Escritos.hoy()), java.time.LocalTime.now().hour)
    }
    val hasta = SprintEscribir.hasta
    LaunchedEffect(hasta) { MauEscribir.sigueSprint(e) }
}

/**
 * Lo de Mau en el editor de [pid]: el sprint con él sale en su menú y, si llevas [MauEscribir.ATASCO] sin escribir con la app delante
 * ([delante]) después de haber escrito algo ([escrito]: cuándo cambió el texto por última vez; 0, aún nada), te anima. Dónde está Mau
 * (ESCRIBIENDO) lo dice cada editor con su MauLugar.
 */
@Composable
fun MauEnElEditor(e: Escritos, pid: String, escrito: Long, delante: Boolean) {
    DisposableEffect(e, pid) {
        val yo = e to pid
        MauEscribir.editor = yo
        onDispose { if (MauEscribir.editor === yo) MauEscribir.editor = null }
    }
    LaunchedEffect(escrito, delante) {
        if (escrito == 0L || !delante) return@LaunchedEffect
        delay(MauEscribir.ATASCO)
        MauEscribir.atascada()
    }
}
