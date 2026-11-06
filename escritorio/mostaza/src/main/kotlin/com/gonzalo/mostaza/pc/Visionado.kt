package com.gonzalo.mostaza.pc


/**
 * Lo que se ha visto DE VERDAD en una sesión del reproductor (regla 1): solo cuenta el tiempo reproduciendo. Una pausa no cuenta, saltar
 * adelante no cuenta (no es tiempo que pase) y salir del reproductor termina la sesión. Se va sumando a pasos ([tick], cada segundo
 * mientras se reproduce); un paso de más de [PASO_MAX] (el ordenador dormido, la app congelada) cuenta solo eso.
 */
class Sesion(private val reloj: () -> Long = { Reloj.ahora() }, previo: Long = 0L) {
    /** Lo visto (ms). [previo]: lo que ya llevaba esta misma sesión del reproductor con este título (al pasar solo al episodio siguiente). */
    var vistoMs = previo; private set
    private var ultimo = -1L
    var terminada = false; private set

    /** El reproductor dice si está reproduciendo ahora (empieza, se pausa, se queda cargando…). */
    fun reproduciendo(si: Boolean) {
        if (terminada) return
        if (si) { if (ultimo < 0) ultimo = reloj() } else { tick(); ultimo = -1L }
    }

    /** Suma lo que ha pasado desde el último paso (si se estaba reproduciendo). Devuelve lo sumado. */
    fun tick(): Long {
        if (terminada || ultimo < 0) return 0L
        val ahora = reloj()
        val d = (ahora - ultimo).coerceIn(0L, PASO_MAX)
        ultimo = ahora
        vistoMs += d
        return d
    }

    /** Has saltado a otro sitio: lo de antes del salto ya está sumado; el salto en sí no suma nada. */
    fun salto() { if (ultimo >= 0) { tick(); ultimo = reloj() } }

    fun termina() { tick(); ultimo = -1L; terminada = true }

    companion object { const val PASO_MAX = 5_000L }
}

/**
 * Un vídeo mientras lo ves: cuenta la [Sesion], apunta la posición y el tiempo, y decide si entra en tu biblioteca.
 *  - Si el título ya está en tu biblioteca, la posición y el tiempo van a la cuenta (cada poco y al salir).
 *  - Si no está, no se escribe NADA en la cuenta hasta que la sesión llega a 10 minutos de verdad (regla 1); entonces entra solo (en
 *    «Viendo») y se apunta todo lo de esta sesión. Si lo habías borrado, no entra (regla 3) y sigue sin escribirse nada.
 *  - Al salir, si la película llegó al 90 %, queda vista (y en «Visto»); en una serie, el episodio, y la serie según sus episodios (regla 2).
 * El punto donde lo dejaste se guarda siempre en este ordenador ([Reanudar]), esté o no en tu biblioteca. (El mismo código que en el móvil.)
 */
class Visionado(
    val breve: MetaBreve, val meta: Meta?, val video: Video?,
    reloj: () -> Long = { Reloj.ahora() },
    private val ahora: () -> Long = { System.currentTimeMillis() },
    /** Lo que ya llevaba esta sesión del reproductor con este mismo título (episodio anterior, sin salir). */
    previo: Long = 0L,
    /** Y lo que de eso aún no se había apuntado en la cuenta (por día). */
    pendiente: Map<String, Long> = emptyMap(),
) {
    val sesion = Sesion(reloj, previo)
    /** Lo visto en esta sesión y aún no apuntado en la cuenta, por día («2026-11-05» → ms). */
    private val porDia = LinkedHashMap<String, Long>(pendiente)

    /** Lo visto que aún no está en la cuenta (para pasárselo al episodio siguiente, si no ha entrado todavía). */
    fun pendiente(): Map<String, Long> = porDia.toMap()

    /** El visionado del vídeo siguiente del mismo título, en la misma sesión del reproductor (sigue contando desde aquí). */
    fun siguiente(v: Video, m: Meta? = meta, reloj: () -> Long = { Reloj.ahora() }): Visionado =
        Visionado(breve, m, v, reloj, ahora, sesion.vistoMs, if (enCuenta) emptyMap() else pendiente())
    private var sesionContada = false
    /** ¿Se escribe en la cuenta? (el título está en tu biblioteca, o ha entrado ya en esta sesión) */
    var enCuenta: Boolean = Biblioteca.por(breve.id) != null; private set
    /** Ha entrado solo en esta sesión (para decírtelo). */
    var entro = false; private set
    private var promovido = false
    private var ultimaPos = 0L; private var ultimaDur = 0L
    private var guardadoEn = 0L

    val videoId: String get() = video?.id ?: breve.id

    /** Cada segundo, mientras se reproduce: suma, y a los 10 minutos entra solo. */
    fun tick(pos: Long, dur: Long) {
        ultimaPos = pos; if (dur > 0) ultimaDur = dur
        val d = sesion.tick()
        if (d > 0) { val hoy = Biblioteca.hoy(ahora()); porDia[hoy] = (porDia[hoy] ?: 0L) + d }
        if (sesion.vistoMs >= Biblioteca.UMBRAL_MS && !promovido) {
            promovido = true
            // Regla 1: a los 10 minutos entra (o, si ya estaba pendiente o abandonado, pasa a «Viendo»); regla 3: lo borrado no vuelve
            val antes = enCuenta
            enCuenta = Biblioteca.entraSolo(breve, meta, ahora())
            if (enCuenta && !antes) entro = true
            if (enCuenta) guarda(pos, dur, forzar = true)
        }
        if (enCuenta && ahora() - guardadoEn > 15_000L) guarda(pos, dur)
        // El punto para seguir, aquí, cada poco (también si la app se cierra de golpe)
        if (ahora() - localEn > 15_000L) { localEn = ahora(); Reanudar.pon(videoId, pos, dur, ahora()) }
    }
    private var localEn = 0L

    /** Apunta dónde vas: aquí siempre; en la cuenta, solo si toca (ver arriba). */
    fun guarda(pos: Long = ultimaPos, dur: Long = ultimaDur, forzar: Boolean = false) {
        ultimaPos = pos; if (dur > 0) ultimaDur = dur
        Reanudar.pon(videoId, pos, dur, ahora())
        if (!enCuenta) return
        if (!forzar && ahora() - guardadoEn < 1_000L) return
        guardadoEn = ahora()
        val t = Biblioteca.por(breve.id) ?: run { enCuenta = false; return }
        Biblioteca.ponPosicion(t, video, pos, dur, ahora())
        val sesiones = if (!sesionContada && sesion.vistoMs > 0) 1 else 0
        if (porDia.isNotEmpty() || sesiones > 0) {
            Biblioteca.sumaTiempo(t, porDia.toMap(), sesiones)
            porDia.clear(); if (sesiones > 0) sesionContada = true
        }
    }

    /** Al salir del reproductor (o al pasar al episodio siguiente): termina la sesión. */
    fun termina(pos: Long = ultimaPos, dur: Long = ultimaDur) {
        val d = run { val antes = sesion.vistoMs; sesion.termina(); sesion.vistoMs - antes }
        if (d > 0) { val hoy = Biblioteca.hoy(ahora()); porDia[hoy] = (porDia[hoy] ?: 0L) + d }
        guarda(pos, dur, forzar = true)
        if (!enCuenta) return
        val t = Biblioteca.por(breve.id) ?: return
        val visto = dur > 0 && pos >= dur * Biblioteca.VISTO
        if (meta != null && meta.esSerie && video != null) Biblioteca.recalculaSerie(t, meta, ahora(), trasVer = promovido)
        else if (visto && t.estado != Estado.VISTO) Biblioteca.cambiaEstado(t.ref, Estado.VISTO, ahora())
    }
}

/** Un reloj que solo avanza (no cambia si cambias la hora del ordenador), en ms: el del móvil es `SystemClock.elapsedRealtime()`. */
object Reloj {
    fun ahora(): Long = System.nanoTime() / 1_000_000L
}
