package com.gonzalo.mau

import kotlin.random.Random

/**
 * Cómo se mueve Mau (docs/mau/ANIMACION.md): para una postura y un instante, cuánto gira, se escala o se desplaza cada grupo del
 * dibujo. Lo que va «al azar» (parpadear, las orejas, la pausa de la cola, el zarpazo) sale de [semilla], así que dos Animadores con
 * la misma semilla se mueven igual (las pruebas).
 *
 * Los ids de contorno no se piden aquí: quien dibuja usa para «contorno-X» lo mismo que para «X», y para «contorno» lo de «cuerpo».
 */
class Animador(private val semilla: Long = System.nanoTime()) {
    private var pose: Pose? = null
    private var ultimo = -1L
    private var rnd = Random(semilla)
    private var agendas = HashMap<String, Agenda>()

    /** Empieza de nuevo (otra postura): lo del azar vuelve a contar desde 0. */
    fun reinicia() { pose = null }

    /**
     * Los movimientos de [p] cuando lleva [t] ms puesta. [dormirse]: ms desde que empezó a dormirse (tres parpadeos lentos, la cabeza
     * cae; ANIMACION.md «Dormirse»), o null.
     */
    fun mueve(p: Pose, t: Long, dormirse: Long? = null): Map<String, Mov> {
        if (p != pose || t < ultimo) { pose = p; rnd = Random(semilla xor p.ordinal.toLong()); agendas = HashMap() }
        ultimo = t
        val piv = PIVOTES.getValue(p)
        val m = HashMap<String, Mov>()
        fun de(id: String) = m.getOrPut(id) { Mov() }

        // Ojos
        when (p) {
            Pose.SENTADO, Pose.LEYENDO, Pose.ESCRIBIENDO, Pose.CELEBRANDO, Pose.JUGANDO -> {
                val ag = agenda("parpadeo", 1500L..4000L, 3000L..6000L) { if (it.nextInt(5) == 0) 1 else 0 }
                val e = ag.en(t, 380)
                val cerrado = e != null && (e.local < 130 || (e.dato == 1 && e.local in 250 until 380))
                if (cerrado) ojos(m, true)
            }
            Pose.RONRONEANDO -> {
                val e = agenda("parpadeo-lento", 1500L..3000L, 4000L..5000L).en(t, 600)
                if (e != null) ojos(m, true)
            }
            else -> {}
        }

        // Cuerpo (respirar, maullar, ronronear)
        when (p) {
            Pose.ESCONDIDO -> {}
            Pose.DURMIENDO -> de("cuerpo").apply { px = piv.cuerpo.x; py = piv.cuerpo.y; ey = 1f + 0.03f * respira(t, 4500) }
            Pose.CELEBRANDO -> de("cuerpo").apply { px = piv.cuerpo.x; py = piv.cuerpo.y; ey = maullidoCuerpo(t) }
            Pose.RONRONEANDO -> de("cuerpo").apply { dx = tramos((t % 80) / 80f, listOf(0f to 0f, .25f to .6f, .75f to -.6f, 1f to 0f), Curva.LINEAL) }
            else -> de("cuerpo").apply { px = piv.cuerpo.x; py = piv.cuerpo.y; ey = 1f + 0.015f * respira(t, 3200) }
        }

        // Cola
        val cola = piv.cola
        if (cola != null) when (p) {
            Pose.DURMIENDO -> {}
            Pose.TAPANDOSE -> {
                val l = t % 2500
                if (l < 360) de("cola").apply {
                    px = cola.x; py = cola.y
                    giro = tramos(l / 360f, listOf(0f to 0f, 1 / 6f to 1.5f, 2 / 6f to -1.5f, 3 / 6f to 1.5f, 4 / 6f to -1.5f, 5 / 6f to 1.5f, 1f to 0f), Curva.LINEAL)
                }
            }
            else -> {
                val media = p == Pose.LEYENDO || p == Pose.JUGANDO
                // 2400 ms de vaivén y una pausa al azar de 0,8–2 s
                val e = agenda("cola", 0L..600L, 3200L..4400L).en(t, 2400)
                if (e != null) de("cola").apply {
                    px = cola.x; py = cola.y
                    giro = tramos(e.local / 2400f, listOf(0f to 0f, 1 / 3f to 6f, 2 / 3f to -4f, 1f to 0f)) * (if (media) .5f else 1f)
                }
            }
        }

        // Cabeza
        when (p) {
            Pose.CELEBRANDO -> de("cabeza").apply { px = piv.cabeza.x; py = piv.cabeza.y; giro = maullidoCabeza(t) }
            Pose.RONRONEANDO -> de("cabeza").apply { px = piv.cabeza.x; py = piv.cabeza.y; giro = 3f * respira(t, 1200) }
            Pose.ESCONDIDO -> if (t < 380) de("cabeza").apply { dy = 80f * (1f - Curva.ASOMARSE(t / 380f)) }
            else -> {}
        }

        // Orejas: de vez en cuando, una sola
        if (p != Pose.DURMIENDO && p != Pose.TAPANDOSE) {
            val e = agenda("orejas", 2000L..5000L, 5000L..9000L) { it.nextInt(2) }.en(t, 230)
            if (e != null) {
                val izq = e.dato == 0
                val g = if (e.local < 90) 12f * Curva.OREJA_IDA(e.local / 90f) else 12f * (1f - Curva.OREJA_VUELTA((e.local - 90) / 140f))
                val o = if (izq) piv.orejaIzq else piv.orejaDer
                de(if (izq) "oreja-izq" else "oreja-der").apply { px = o.x; py = o.y; giro = if (izq) -g else g }
            }
        }

        // Escribir: la pata y la pluma
        if (p == Pose.ESCRIBIENDO) {
            val g = tramos((t % 700) / 700f, listOf(0f to 0f, 1 / 3f to -5f, 2 / 3f to 3f, 1f to 0f))
            for (id in listOf("pata-der", "pluma")) de(id).apply { px = 112f; py = 150f; giro = g }
        }

        // Jugar: zarpazo y el ovillo rueda cuando la zarpa golpea
        if (p == Pose.JUGANDO) {
            val e = agenda("zarpazo", 600L..1400L, 1400L..2000L).en(t, 640)
            if (e != null) {
                if (e.local < 360) de("pata-der").apply {
                    px = 114f; py = 168f
                    giro = tramos(e.local / 360f, listOf(0f to 0f, 1 / 3f to -12f, 2 / 3f to 3f, 1f to 0f))
                }
                val o = e.local - 240
                if (o in 0 until 400) {
                    val f = tramos(o / 400f, listOf(0f to 0f, .5f to 1f, 1f to 0f), Curva.OVILLO)
                    de("ovillo").apply { px = 170f; py = 197f; giro = 30f * f; dx = 4f * f }
                }
            }
        }

        // Dormirse: tres parpadeos cada vez más lentos y la cabeza que cae
        if (dormirse != null) {
            val d = dormirse
            val cerrado = d < 300 || d in 800 until 1250 || d in 1750 until 2350 || d >= 2900
            ojos(m, cerrado)
            if (d >= 2900) {
                val f = Curva.SUAVE(((d - 2900) / 900f).coerceIn(0f, 1f))
                de("cabeza").apply { px = piv.cabeza.x; py = piv.cabeza.y; giro = 10f * f; dy = 6f * f }
            }
        }
        return m
    }

    private fun ojos(m: HashMap<String, Mov>, cerrados: Boolean) {
        m.getOrPut("ojos-abiertos") { Mov() }.alfa = if (cerrados) 0f else 1f
        m.getOrPut("ojos-cerrados") { Mov() }.alfa = if (cerrados) 1f else 0f
    }

    /** 0 → 1 → 0 cada [periodo] ms, con la curva suave en cada mitad. */
    private fun respira(t: Long, periodo: Int): Float = tramos((t % periodo) / periodo.toFloat(), listOf(0f to 0f, .5f to 1f, 1f to 0f))

    // Dos maullidos seguidos (520 ms, 300 ms entre ellos) y 1500 ms de pausa
    private fun maullido(t: Long): Float? {
        val l = t % 2840
        return when {
            l < 520 -> l / 520f
            l in 820 until 1340 -> (l - 820) / 520f
            else -> null
        }
    }
    private fun maullidoCabeza(t: Long): Float = maullido(t)?.let { tramos(it, listOf(0f to 0f, 1 / 3f to -6f, 2 / 3f to -4f, 1f to 0f), Curva.MAULLIDO) } ?: 0f
    private fun maullidoCuerpo(t: Long): Float = maullido(t)?.let { tramos(it, listOf(0f to 1f, .5f to 1.02f, 1f to 1f), Curva.MAULLIDO) } ?: 1f

    private fun agenda(nombre: String, primera: LongRange, hueco: LongRange, dato: (Random) -> Int = { 0 }) =
        agendas.getOrPut(nombre) { Agenda(Random(rnd.nextLong()), primera, hueco, dato) }

    /** Cosas que pasan de vez en cuando: cada una empieza un rato al azar ([hueco], de inicio a inicio) después de la anterior. */
    private class Agenda(val rnd: Random, primera: LongRange, val hueco: LongRange, val dato: (Random) -> Int) {
        class Vez(val inicio: Long, val dato: Int) { var local = 0L }
        private val veces = ArrayDeque<Vez>()
        private var siguiente = rnd.nextLong(primera.first, primera.last + 1)

        /** La vez que está pasando en [t] (con [Vez.local]: ms desde que empezó), si alguna empezó hace menos de [dura] ms. */
        fun en(t: Long, dura: Long): Vez? {
            while (siguiente <= t) {
                veces.addLast(Vez(siguiente, dato(rnd)))
                siguiente += rnd.nextLong(hueco.first, hueco.last + 1)
            }
            while (veces.size > 1 && veces.first().inicio + dura <= t) veces.removeFirst()
            val v = veces.lastOrNull { it.inicio <= t && t < it.inicio + dura } ?: return null
            v.local = t - v.inicio
            return v
        }
    }
}

/** Lo que dura dormirse (los parpadeos y la cabeza que cae); después se funde con «durmiendo» en [FUNDIDO_DORMIR] ms. */
const val DURA_DORMIRSE = 3800L
const val FUNDIDO_DORMIR = 250L
