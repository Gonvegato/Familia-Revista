package com.gonzalo.shai

/**
 * Las proezas: un partido o una temporada fuera de lo normal (siete goles, una final con cuatro, cincuenta goles en una temporada).
 * Lo de cada jornada sube poco a poco y con topes; una proeza los rompe: la fama salta de golpe, lo que vale se dispara (con un suelo
 * según su edad y el escenario) y la prensa, la red, su gente y los clubes reaccionan como lo harían de verdad: que el mundo pierda la
 * cabeza. Sin IA: solo cuentas con lo que él mete.
 */
enum class Proeza(val nombre: String, val explica: String, val salto: Int, val factorMax: Double, val suelo: Long) {
    NINGUNA("", "", 0, 2.0, 0),
    ENORME("Partido enorme", "un partido enorme, de los que se recuerdan en su club", 0, 2.5, 0),
    HISTORICA("Partido histórico", "un partido histórico, de los que salen en los libros: lo hacen muy pocos en la historia", 10, 3.5, 60_000_000),
    SURREAL("Partido de otro planeta", "algo que no se había visto nunca o casi nunca en el fútbol: surrealista", 20, 6.0, 150_000_000);

    val mayor get() = ordinal >= HISTORICA.ordinal
}

object Proezas {
    /** Un escenario grande: una eliminatoria avanzada, un torneo de selecciones o la Champions. */
    fun escenarioGrande(p: Partido): Boolean {
        val f = Competiciones.familia(p.competicion)
        return Competiciones.pesoFase(p.fase) >= 1.4 || f == Competiciones.Familia.CHAMPIONS ||
            (f == Competiciones.Familia.TORNEO_SELECCIONES && !Competiciones.norm(p.competicion).contains("amistoso"))
    }

    /** Lo que es este partido. Un amistoso, un escalón menos. */
    fun dePartido(modo: Rol, p: Partido): Proeza {
        val grande = escenarioGrande(p)
        val g = if (modo == Rol.MISTER) {
            val dif = p.golesFavor - p.golesContra
            when {
                dif >= 6 && p.esFinal -> Proeza.SURREAL
                dif >= 7 || (dif >= 5 && grande) || (dif >= 4 && p.esFinal) -> Proeza.HISTORICA
                dif >= 5 || (dif >= 3 && p.esFinal) -> Proeza.ENORME
                else -> Proeza.NINGUNA
            }
        } else {
            if (p.minutos <= 0) return Proeza.NINGUNA
            val gol = p.goles; val total = p.goles + p.asistencias
            when {
                gol >= 6 || total >= 9 || (gol >= 5 && p.esFinal) -> Proeza.SURREAL
                gol >= 5 || total >= 7 || (gol >= 4 && grande) || (gol >= 3 && p.esFinal) -> Proeza.HISTORICA
                gol >= 4 || total >= 5 || (gol >= 3 && grande) -> Proeza.ENORME
                else -> Proeza.NINGUNA
            }
        }
        return if (g != Proeza.NINGUNA && Competiciones.familia(p.competicion) == Competiciones.Familia.AMISTOSO) Proeza.entries[g.ordinal - 1] else g
    }

    /**
     * Lo que es una temporada (sus partidos jugados): de jugador, por los goles (50 o más, histórica; 70 o más, de otro planeta) o el
     * ritmo (más de 1,2 por partido en 12 o más; 1,6 en 15 o más). De míster, sin perder en 20 o más y ganando casi todo.
     */
    fun deTemporada(modo: Rol, partidos: List<Partido>): Proeza {
        if (modo == Rol.MISTER) {
            val n = partidos.size; val v = partidos.count { it.ganado }
            return when {
                n >= 25 && partidos.none { !it.ganado && !it.empatado } && v >= n * 0.9 -> Proeza.SURREAL
                n >= 20 && partidos.none { !it.ganado && !it.empatado } -> Proeza.HISTORICA
                else -> Proeza.NINGUNA
            }
        }
        val jugados = partidos.filter { it.minutos > 0 }
        val n = jugados.size; val g = jugados.sumOf { it.goles }
        val ritmo = if (n > 0) g.toDouble() / n else 0.0
        return when {
            g >= 70 || (n >= 15 && ritmo >= 1.6) -> Proeza.SURREAL
            g >= 50 || (n >= 12 && ritmo >= 1.2) -> Proeza.HISTORICA
            else -> Proeza.NINGUNA
        }
    }

    /** Lo que hay que saber de una jornada: el partido, la temporada y si la temporada acaba de cruzar a otro escalón con este partido. */
    class Lectura(val partido: Proeza, val temporada: Proeza, val temporadaNueva: Boolean) {
        /** Lo más gordo que dispara esta jornada (la temporada solo si acaba de cruzar). */
        val dispara: Proeza get() = if (temporadaNueva && temporada.ordinal > partido.ordinal) temporada else partido
    }

    /** El partido [p] (ya guardado o no) con los de su temporada. */
    fun lee(u: Universo, p: Partido, partidos: List<Partido>): Lectura {
        val deTemp = partidos.filter { it.temporada == p.temporada }
        val i = deTemp.indexOfLast { it == p }
        val antes = if (i >= 0) deTemp.filterIndexed { j, _ -> j != i } else deTemp
        val con = antes + p
        val t0 = deTemporada(u.modo, antes); val t1 = deTemporada(u.modo, con)
        return Lectura(dePartido(u.modo, p), t1, t1.ordinal > t0.ordinal)
    }

    /** La fama tras una proeza: salta (sin el tope de la jornada) y, si es de otro planeta, al menos a estrella. */
    fun fama(u: Universo, p: Partido, pr: Proeza, f0: Int): Int {
        if (!pr.mayor) return f0
        val salto = Math.round(pr.salto * (if (escenarioGrande(p)) 1.3 else 1.0) * Vida.pesoEdad(u.personaje.edad, true).coerceIn(0.7, 1.4)).toInt()
        val minimo = if (pr == Proeza.SURREAL) Nivel.ESTRELLA.desde + 3 else Nivel.TITULAR.desde + 3
        return maxOf(f0 + salto, minimo, f0).coerceAtMost(100)
    }

    /** El suelo de lo que vale tras una proeza: más cuanto más joven y más grande el escenario (un chaval que lo hace en un Mundial, ~300 M€). */
    fun sueloValor(u: Universo, p: Partido, pr: Proeza): Long {
        if (!pr.mayor) return 0
        val edad = u.personaje.edad
        val porEdad = when { edad <= 19 -> 1.6; edad <= 23 -> 1.3; edad <= 28 -> 1.0; else -> 0.6 }
        val esc = if (escenarioGrande(p)) 1.3 else 1.0
        return (pr.suelo * porEdad * esc).toLong()
    }

    /** Para Claude: lo que significa esta jornada (null si no hay nada fuera de lo normal). */
    fun paraClaude(u: Universo, l: Lectura): String? {
        val partes = ArrayList<String>()
        if (l.partido != Proeza.NINGUNA) partes += when (l.partido) {
            Proeza.SURREAL -> "ESTE PARTIDO ES DE OTRO PLANETA (${l.partido.explica}). La reacción tiene que ser la de la vida real ante algo así: que TODO EL MUNDO PIERDA LA CABEZA. Olvida el alcance normal de su nivel: esto da la vuelta al planeta. Portadas en varios países, la prensa internacional, programas especiales, la tendencia mundial número uno, memes, gente que no sabe nada de fútbol hablando de él, la afición rival que se rinde, los escépticos que no se lo creen, comparaciones con los más grandes de la historia, su club y su selección desbordados, su gente que no da crédito, el teléfono de su agente echando humo. Los «me gusta» y los reposts de los posts gordos, muchísimas veces lo normal (millones si hace falta). La sensación de fondo: «¿qué cojones acabamos de ver?»."
            Proeza.HISTORICA -> "ESTE PARTIDO ES HISTÓRICO (${l.partido.explica}). Que la reacción esté a la altura, como en la vida real: portada a toda página, la prensa nacional entera (y la internacional), tertulias que no hablan de otra cosa, tendencia en todo el país, la red desbordada (los posts gordos, varias veces lo normal), comparaciones con los grandes, su gente eufórica."
            Proeza.ENORME -> "ESTE PARTIDO ES ENORME (${l.partido.explica}): que se note más que un buen partido normal (portada, la red encendida, se habla de él en todo su país)."
            else -> ""
        }
        if (l.temporada.mayor) partes += (if (l.temporadaNueva) "CON ESTE PARTIDO, SU TEMPORADA PASA A SER " else "SU TEMPORADA ES ") +
            (if (l.temporada == Proeza.SURREAL) "DE OTRO PLANETA: unas cifras que no se han visto nunca o casi nunca en la historia. Se habla de él como de algo que no tiene explicación; cada partido suyo es un acontecimiento mundial."
            else "HISTÓRICA: unas cifras al alcance de muy pocos en la historia. Se le trata como a uno de los grandes; cada partido suyo, una cita.")
        if (partes.isEmpty()) return null
        if (l.dispara.mayor) partes += "Los clubes más grandes del mundo se pelean por él: se habla abiertamente de pagar su cláusula o cifras de récord (de récords de traspaso cita solo el que viene en DATOS AL DÍA). No inventes récords del fútbol de verdad (regla 10): lo surrealista se cuenta con sus cifras de este partido y de esta temporada."
        return partes.joinToString("\n")
    }
}
