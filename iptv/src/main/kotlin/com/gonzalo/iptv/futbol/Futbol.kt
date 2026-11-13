package com.gonzalo.iptv.futbol

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/*
 * El fútbol de Nut: los partidos de tus equipos, las clasificaciones, los goleadores y la ficha de cada partido. Esto es lo de siempre,
 * sin Android y sin saber de dónde salen los datos (hoy, la API pública de ESPN: [Espn]); si la fuente cambia, cambia solo [FuenteFutbol].
 * Todo lo que se enseña está en español y las horas, siempre en la de España ([Hora]).
 */

/** Una competición que Nut sabe seguir: su clave en la fuente ([slug]), su nombre y el corto (para los botones y las etiquetas). */
data class Competicion(val slug: String, val nombre: String, val corto: String = nombre)

object Competiciones {
    val LALIGA = Competicion("esp.1", "LaLiga", "LaLiga")
    val CHAMPIONS = Competicion("uefa.champions", "Liga de Campeones", "Champions")
    val COPA = Competicion("esp.copa_del_rey", "Copa del Rey", "Copa")
    val SUPERCOPA = Competicion("esp.super_cup", "Supercopa de España", "Supercopa")
    val EUROPA = Competicion("uefa.europa", "Europa League", "Europa League")
    val CONFERENCE = Competicion("uefa.europa.conf", "Conference League", "Conference")
    val PREMIER = Competicion("eng.1", "Premier League", "Premier")
    val SERIE_A = Competicion("ita.1", "Serie A", "Serie A")
    val BUNDESLIGA = Competicion("ger.1", "Bundesliga", "Bundesliga")
    val LIGUE1 = Competicion("fra.1", "Ligue 1", "Ligue 1")
    val HYPERMOTION = Competicion("esp.2", "LaLiga Hypermotion", "Hypermotion")
    // Las de selecciones (en ESPN, el mismo id de equipo en todas: España es 164)
    val NACIONES = Competicion("uefa.nations", "Liga de Naciones", "Nations League")
    val MUNDIAL = Competicion("fifa.world", "Mundial", "Mundial")
    val CLASIF_MUNDIAL = Competicion("fifa.worldq.uefa", "Clasificación del Mundial", "Clasif. Mundial")
    val EURO = Competicion("uefa.euro", "Eurocopa", "Eurocopa")
    val CLASIF_EURO = Competicion("uefa.euroq", "Clasificación de la Eurocopa", "Clasif. Eurocopa")
    val AMISTOSOS = Competicion("fifa.friendly", "Amistosos", "Amistoso")

    /** Las de clubes, en este orden se enseñan (las clasificaciones, los goleadores, los equipos para elegir). */
    val CLUBES = listOf(LALIGA, CHAMPIONS, EUROPA, CONFERENCE, PREMIER, SERIE_A, BUNDESLIGA, LIGUE1, COPA, SUPERCOPA, HYPERMOTION)
    /** Las ligas (de cada equipo de club se pide la suya y, además, las copas y las europeas: [para]). */
    val LIGAS = listOf(LALIGA, PREMIER, SERIE_A, BUNDESLIGA, LIGUE1, HYPERMOTION)
    private val COPAS_Y_EUROPA = listOf(CHAMPIONS, EUROPA, CONFERENCE, COPA, SUPERCOPA)
    /** Las de selecciones: de un equipo de selecciones se piden estas. */
    val SELECCIONES = listOf(NACIONES, MUNDIAL, CLASIF_MUNDIAL, EURO, CLASIF_EURO, AMISTOSOS)
    val TODAS = CLUBES + SELECCIONES
    /**
     * El orden de serie de los partidos del día (todas las que se piden, comprobadas en ESPN): LaLiga, las europeas, las grandes ligas,
     * las copas de aquí, las de selecciones y, al final, LaLiga Hypermotion. Tú lo cambias y escondes las que quieras (Ajustes → Fútbol).
     */
    val DEL_DIA = listOf(LALIGA, CHAMPIONS, EUROPA, CONFERENCE, PREMIER, SERIE_A, BUNDESLIGA, LIGUE1, COPA, SUPERCOPA, NACIONES, CLASIF_MUNDIAL, CLASIF_EURO, MUNDIAL, EURO, AMISTOSOS, HYPERMOTION)
    /** Las que tienen clasificación (las de eliminatorias, no). */
    val CON_TABLA = listOf(LALIGA, CHAMPIONS, EUROPA, CONFERENCE, PREMIER, SERIE_A, BUNDESLIGA, LIGUE1, HYPERMOTION, NACIONES)
    fun tieneTabla(slug: String) = CON_TABLA.any { it.slug == slug }
    fun de(slug: String): Competicion = TODAS.firstOrNull { it.slug == slug } ?: Competicion(slug, slug)
    fun esDeSelecciones(slug: String) = SELECCIONES.any { it.slug == slug }
    /** Las competiciones donde se buscan los partidos de un equipo elegido en [liga] (de clubes o de selecciones). */
    fun para(equipoElegidoEn: String): List<Competicion> = if (esDeSelecciones(equipoElegidoEn)) SELECCIONES
        else (listOf(de(equipoElegidoEn)).filter { c -> LIGAS.any { it.slug == c.slug } }.ifEmpty { listOf(LALIGA) } + COPAS_Y_EUROPA).distinct()

    /** Tu orden de las competiciones del día ([guardado], slugs) con las que falten detrás, en el de serie. */
    fun ordenadas(guardado: List<String>): List<Competicion> = (guardado.mapNotNull { g -> DEL_DIA.firstOrNull { it.slug == g } } + DEL_DIA).distinct()
}

/** Un equipo: su id en la fuente, su nombre, el corto (para lo estrecho), las siglas y su escudo (una imagen pública). */
data class Equipo(val id: String, val nombre: String, val corto: String = nombre, val siglas: String = "", val escudo: String = "")

/** Cómo va un partido. */
enum class Estado(val texto: String) {
    POR_JUGAR("Por jugar"), EN_JUEGO("En juego"), DESCANSO("Descanso"), PRORROGA("Prórroga"), PENALTIS("Penaltis"),
    FINAL("Final"), FINAL_PRORROGA("Final (prórroga)"), FINAL_PENALTIS("Final (penaltis)"),
    APLAZADO("Aplazado"), SUSPENDIDO("Suspendido"), CANCELADO("Cancelado");

    val enDirecto: Boolean get() = this == EN_JUEGO || this == DESCANSO || this == PRORROGA || this == PENALTIS
    val terminado: Boolean get() = this == FINAL || this == FINAL_PRORROGA || this == FINAL_PENALTIS
}

/**
 * Un partido. [inicio]: en ms desde 1970 (UTC; se enseña en la hora de España). [golesLocal]/[golesVisitante]: null si aún no ha
 * empezado. [reloj]: el minuto que va («67'», «45'+2'»), solo en directo. [penaltis]: la tanda, si la hubo («4-3»).
 */
data class Partido(
    val id: String,
    val liga: String,
    val inicio: Long,
    val local: Equipo,
    val visitante: Equipo,
    val estado: Estado = Estado.POR_JUGAR,
    val golesLocal: Int? = null,
    val golesVisitante: Int? = null,
    val reloj: String = "",
    val penaltis: String = "",
    val estadio: String = "",
) {
    val competicion: Competicion get() = Competiciones.de(liga)
    fun juega(equipo: String) = local.id == equipo || visitante.id == equipo
    /** «2-1» (o «–» si aún no ha empezado). */
    val marcador: String get() = if (golesLocal == null || golesVisitante == null) "–" else "$golesLocal-$golesVisitante"
    /** Lo que va del partido, en una línea: «Descanso», «67'», «Final», «Dom 8 nov · 21:00». */
    fun cuando(ahora: Long): String = when {
        estado == Estado.EN_JUEGO || estado == Estado.PRORROGA -> reloj.ifBlank { estado.texto }
        estado == Estado.POR_JUGAR -> Hora.diaYHora(inicio, ahora)
        else -> estado.texto
    }
}

/** Lo que pasa en un partido (lo que se enseña en la ficha). */
enum class TipoEvento(val texto: String) {
    GOL("Gol"), GOL_CABEZA("Gol de cabeza"), GOL_FALTA("Gol de falta"), GOL_PENALTI("Gol de penalti"), GOL_PROPIA("Gol en propia puerta"),
    PENALTI_FALLADO("Penalti fallado"), PENALTI_PARADO("Penalti parado"),
    AMARILLA("Tarjeta amarilla"), SEGUNDA_AMARILLA("Segunda amarilla"), ROJA("Tarjeta roja"),
    CAMBIO("Cambio"), VAR("VAR"),
    INICIO("Empieza el partido"), DESCANSO("Descanso"), SEGUNDA_PARTE("Empieza la segunda parte"), FINAL("Final"),
    OTRO("");

    val esGol: Boolean get() = this == GOL || this == GOL_CABEZA || this == GOL_FALTA || this == GOL_PENALTI || this == GOL_PROPIA
    val esTarjeta: Boolean get() = this == AMARILLA || this == SEGUNDA_AMARILLA || this == ROJA
    /** Lo que es un hito del partido (empieza, descanso…), no una jugada. */
    val esHito: Boolean get() = this == INICIO || this == DESCANSO || this == SEGUNDA_PARTE || this == FINAL
}

/** Un evento: el minuto («14'», «45'+1'»), qué es, de qué equipo (su id) y quién ([jugador]; en un cambio, el que entra) y [otro] (el que sale, o quien da la asistencia). */
data class Evento(val minuto: String, val tipo: TipoEvento, val equipo: String = "", val jugador: String = "", val otro: String = "") {
    /** La frase que se enseña, hecha aquí (nunca el texto en inglés de la fuente): «14' Gol de Raphinha (penalti)». */
    fun frase(): String = Textos.frase(this)
}

/** Un jugador de una alineación. [entra]/[sale]: si entró o se fue en un cambio. */
data class Jugador(val nombre: String, val dorsal: String = "", val posicion: String = "", val titular: Boolean = true, val entra: Boolean = false, val sale: Boolean = false)

/** La alineación de un equipo: su dibujo («4-3-3»), los titulares y los suplentes. */
data class Alineacion(val equipo: Equipo, val formacion: String, val titulares: List<Jugador>, val suplentes: List<Jugador>)

/** Una cifra del partido para los dos equipos (ya con su nombre en español y como se enseña: «58 %», «12»). */
data class Cifra(val nombre: String, val local: String, val visitante: String)

/** La ficha de un partido: el partido (con el marcador al día), lo que ha pasado, las alineaciones y las cifras. */
data class FichaPartido(
    val partido: Partido, val eventos: List<Evento>, val alineaciones: List<Alineacion>, val cifras: List<Cifra>,
    /** Los últimos enfrentamientos entre los dos (el más reciente primero). */
    val caraACara: List<Partido> = emptyList(),
    /** Los últimos partidos de cada uno (id del equipo → partidos, el más reciente primero). */
    val ultimos: Map<String, List<Partido>> = emptyMap(),
)

/** Una fila de la clasificación. [nota]: lo que da ese puesto («Liga de Campeones», «Descenso»), si lo dice la fuente. */
data class Fila(val puesto: Int, val equipo: Equipo, val jugados: Int, val ganados: Int, val empatados: Int, val perdidos: Int, val aFavor: Int, val enContra: Int, val diferencia: Int, val puntos: Int, val nota: String = "")

/** La clasificación de una competición (una tabla por grupo; en LaLiga, una). */
data class Clasificacion(val liga: String, val tablas: List<Pair<String, List<Fila>>>)

/** Un goleador o un asistente: su nombre, su equipo, la cifra (goles o asistencias) y los partidos jugados (0 si no se sabe). */
data class Lider(val nombre: String, val equipo: Equipo, val cifra: Int, val partidos: Int = 0)

data class Lideres(val liga: String, val goles: List<Lider>, val asistencias: List<Lider>)

/**
 * De dónde salen los datos (hoy, ESPN). Cada función bloquea y lanza una excepción si no se puede. [edad]: lo viejo que puede ser lo
 * que ya se tenga guardado (ms) para no volver a pedirlo ([CacheFutbol]); 0 = pedirlo ya.
 */
interface FuenteFutbol {
    val nombre: String
    fun calendario(liga: String, equipo: String, proximos: Boolean, edad: Long = 0L): List<Partido>
    fun marcadores(liga: String, dia: LocalDate, edad: Long = 0L): List<Partido>
    fun clasificacion(liga: String, edad: Long = 0L): Clasificacion
    fun lideres(liga: String, edad: Long = 0L): Lideres
    fun ficha(liga: String, partido: String, edad: Long = 0L): FichaPartido
    fun equipos(liga: String, edad: Long = 0L): List<Equipo>
    fun plantilla(liga: String, equipo: String, edad: Long = 0L): List<Jugador>
}

/**
 * Las horas de los partidos: «Hoy · 21:00», «Mañana · 18:30», «Dom 8 nov · 21:00». En la zona de [zona]: la app la pone a la del móvil
 * (en Irlanda, una hora menos que en España); sin decir nada, la de España.
 */
object Hora {
    val ESPANA: ZoneId = ZoneId.of("Europe/Madrid")
    /** La zona en la que se dicen las horas y se cuentan los días (la del móvil, en la app). */
    @Volatile var zona: ZoneId = ESPANA
    private val DIAS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

    fun enEspana(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zona)

    /** «2026-11-08T20:00Z» (o con segundos, o con desfase): ms desde 1970; 0 si no se entiende. */
    fun lee(s: String): Long = runCatching { Instant.parse(if (Regex("T\\d\\d:\\d\\dZ$").containsMatchIn(s)) s.dropLast(1) + ":00Z" else s).toEpochMilli() }
        .recoverCatching { ZonedDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }.getOrDefault(0L)

    fun reloj(ms: Long): String = "%02d:%02d".format(enEspana(ms).hour, enEspana(ms).minute)

    /** «Dom 8 nov». */
    fun dia(ms: Long): String { val d = enEspana(ms); return "${DIAS[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MESES[d.monthValue - 1]}" }

    /** «Hoy», «Mañana», «Ayer» o «Dom 8 nov» (el día en España de [ms], visto desde [ahora]). */
    fun diaRelativo(ms: Long, ahora: Long): String {
        val d = enEspana(ms).toLocalDate(); val hoy = enEspana(ahora).toLocalDate()
        return when (d) { hoy -> "Hoy"; hoy.plusDays(1) -> "Mañana"; hoy.minusDays(1) -> "Ayer"; else -> dia(ms) }
    }

    fun diaYHora(ms: Long, ahora: Long): String = "${diaRelativo(ms, ahora)} · ${reloj(ms)}"

    /** La tira de días de la portada: desde [antes] días antes de hoy hasta [despues] después (en España). */
    fun tira(ahora: Long, antes: Int = 2, despues: Int = 7): List<LocalDate> { val hoy = diaDe(ahora); return (-antes..despues).map { hoy.plusDays(it.toLong()) } }

    /** «Ayer», «Hoy», «Mañana» o «Dom 8» (para la tira de días). */
    fun nombreDia(d: LocalDate, ahora: Long): String {
        val hoy = diaDe(ahora)
        return when (d) { hoy -> "Hoy"; hoy.plusDays(1) -> "Mañana"; hoy.minusDays(1) -> "Ayer"; else -> "${DIAS[d.dayOfWeek.value - 1]} ${d.dayOfMonth}" }
    }

    /** «Sábado 7 de noviembre». */
    fun largo(d: LocalDate): String {
        val dias = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
        return "${dias[d.dayOfWeek.value - 1]} ${d.dayOfMonth} de ${meses[d.monthValue - 1]}"
    }

    /** El día de España de [ms] (para pedir los marcadores de ese día). */
    fun diaDe(ms: Long): LocalDate = enEspana(ms).toLocalDate()
}

/** Los textos de la fuente (estados y tipos de evento, en inglés) traducidos, y las frases de los eventos. */
object Textos {
    /** El estado de un partido a partir del de la fuente (`STATUS_…`) y de su fase (`pre`, `in`, `post`). */
    fun estado(nombre: String, fase: String = ""): Estado = when (nombre.uppercase().removePrefix("STATUS_")) {
        "SCHEDULED", "TBD" -> Estado.POR_JUGAR
        "HALFTIME", "HALF_TIME", "END_OF_REGULATION", "END_OF_EXTRATIME", "HALFTIME_ET" -> Estado.DESCANSO
        "FIRST_HALF", "SECOND_HALF", "IN_PROGRESS", "LIVE" -> Estado.EN_JUEGO
        "OVERTIME", "EXTRA_TIME", "FIRST_HALF_EXTRA_TIME", "SECOND_HALF_EXTRA_TIME" -> Estado.PRORROGA
        "SHOOTOUT", "PENALTY_SHOOTOUT" -> Estado.PENALTIS
        "FULL_TIME", "FINAL", "FINAL_PEN_UNKNOWN" -> Estado.FINAL
        "FINAL_AET", "FINAL_ET" -> Estado.FINAL_PRORROGA
        "FINAL_PEN" -> Estado.FINAL_PENALTIS
        "POSTPONED", "DELAYED" -> Estado.APLAZADO
        "SUSPENDED", "ABANDONED", "RAIN_DELAY" -> Estado.SUSPENDIDO
        "CANCELED", "CANCELLED", "FORFEIT" -> Estado.CANCELADO
        else -> when (fase) { "in" -> Estado.EN_JUEGO; "post" -> Estado.FINAL; else -> Estado.POR_JUGAR }
    }

    /**
     * El tipo de un evento, por su id en ESPN (el que no cambia con el idioma) o, si no se conoce, por su texto (en inglés o en
     * español, por si se pide con `lang=es`).
     */
    fun tipo(id: String, texto: String): TipoEvento {
        when (id) {
            "70", "173", "174", "175", "176", "177" -> return TipoEvento.GOL
            "137" -> return TipoEvento.GOL_CABEZA
            "138" -> return TipoEvento.GOL_FALTA
            "98" -> return TipoEvento.GOL_PENALTI
            "97" -> return TipoEvento.GOL_PROPIA
            "93" -> return TipoEvento.ROJA
            "94" -> return TipoEvento.AMARILLA
            "76" -> return TipoEvento.CAMBIO
            "80" -> return TipoEvento.INICIO
            "81" -> return TipoEvento.DESCANSO
            "82" -> return TipoEvento.SEGUNDA_PARTE
            "83", "84" -> return TipoEvento.FINAL
        }
        val t = texto.lowercase()
        return when {
            "own goal" in t || "propia" in t -> TipoEvento.GOL_PROPIA
            ("penalty" in t || "penalti" in t) && ("miss" in t || "fall" in t) -> TipoEvento.PENALTI_FALLADO
            ("penalty" in t || "penalti" in t) && ("saved" in t || "parad" in t) -> TipoEvento.PENALTI_PARADO
            ("penalty" in t || "penalti" in t) && ("scored" in t || "gol" in t || "goal" in t) -> TipoEvento.GOL_PENALTI
            "var" in t.split(Regex("[^a-z]+")) -> TipoEvento.VAR
            ("goal" in t || "gol" in t) && ("header" in t || "cabeza" in t) -> TipoEvento.GOL_CABEZA
            ("goal" in t || "gol" in t) && ("free" in t || "falta" in t) -> TipoEvento.GOL_FALTA
            "goal" in t || t.startsWith("gol") -> TipoEvento.GOL
            "second yellow" in t || "segunda amarilla" in t -> TipoEvento.SEGUNDA_AMARILLA
            "yellow" in t || "amarilla" in t -> TipoEvento.AMARILLA
            "red" in t.split(Regex("[^a-z]+")) || "roja" in t -> TipoEvento.ROJA
            "substitution" in t || "cambio" in t || "sustitución" in t -> TipoEvento.CAMBIO
            "kickoff" in t || "kick off" in t -> TipoEvento.INICIO
            "halftime" in t || "half time" in t || "descanso" in t -> TipoEvento.DESCANSO
            "start 2nd half" in t || "second half" in t -> TipoEvento.SEGUNDA_PARTE
            "end regular time" in t || "full time" in t || "end of game" in t -> TipoEvento.FINAL
            else -> TipoEvento.OTRO
        }
    }

    /** «14' Gol de Raphinha (penalti)», «62' Cambio: entra Balde, sale Cancelo», «27' Tarjeta amarilla para Pedri». */
    fun frase(e: Evento): String {
        val m = if (e.minuto.isNotBlank()) "${e.minuto} " else ""
        val j = e.jugador; val o = e.otro
        val cuerpo = when (e.tipo) {
            TipoEvento.GOL -> if (j.isBlank()) "Gol" else "Gol de $j" + if (o.isNotBlank()) " (asistencia de $o)" else ""
            TipoEvento.GOL_CABEZA -> if (j.isBlank()) "Gol de cabeza" else "Gol de $j (de cabeza)"
            TipoEvento.GOL_FALTA -> if (j.isBlank()) "Gol de falta" else "Gol de $j (de falta)"
            TipoEvento.GOL_PENALTI -> if (j.isBlank()) "Gol de penalti" else "Gol de $j (penalti)"
            TipoEvento.GOL_PROPIA -> if (j.isBlank()) "Gol en propia puerta" else "Gol en propia puerta de $j"
            TipoEvento.PENALTI_FALLADO -> if (j.isBlank()) "Penalti fallado" else "$j falla un penalti"
            TipoEvento.PENALTI_PARADO -> if (j.isBlank()) "Penalti parado" else "Le paran un penalti a $j"
            TipoEvento.AMARILLA, TipoEvento.SEGUNDA_AMARILLA, TipoEvento.ROJA -> if (j.isBlank()) e.tipo.texto else "${e.tipo.texto} para $j"
            TipoEvento.CAMBIO -> when { j.isNotBlank() && o.isNotBlank() -> "Cambio: entra $j, sale $o"; j.isNotBlank() -> "Cambio: entra $j"; else -> "Cambio" }
            TipoEvento.VAR -> if (j.isBlank()) "Revisión del VAR" else "Revisión del VAR ($j)"
            else -> e.tipo.texto
        }
        return if (e.tipo.esHito) cuerpo else m + cuerpo
    }

    /** Las cifras de un partido que se enseñan, en este orden, con su nombre en español (las demás, fuera). */
    val CIFRAS = linkedMapOf(
        "possessionPct" to "Posesión", "totalShots" to "Tiros", "shotsOnTarget" to "Tiros a puerta", "wonCorners" to "Córneres",
        "foulsCommitted" to "Faltas", "offsides" to "Fueras de juego", "yellowCards" to "Amarillas", "redCards" to "Rojas", "saves" to "Paradas",
        "totalPasses" to "Pases", "passPct" to "Pases buenos",
    )

    /** Una cifra como se enseña: los porcentajes, con «%» (la posesión viene en 0–100; los demás, en 0–1). */
    fun cifra(nombre: String, valor: String): String {
        val d = valor.replace(',', '.').toDoubleOrNull() ?: return valor
        return when {
            nombre == "possessionPct" -> "${Math.round(d)} %"
            nombre.endsWith("Pct") -> "${Math.round(if (d <= 1.0) d * 100 else d)} %"
            d == Math.floor(d) -> d.toLong().toString()
            else -> valor
        }
    }

    /** Las notas de la clasificación («Champions League», «Relegation»…) en español. */
    fun nota(t: String): String {
        val x = t.lowercase()
        return when {
            x.isBlank() -> ""
            "champions league" in x && ("qualif" in x || "playoff" in x || "play-off" in x) -> "Previa de la Liga de Campeones"
            "champions league" in x -> "Liga de Campeones"
            "europa league" in x -> "Europa League"
            "conference league" in x -> "Conference League"
            "relegation" in x -> "Descenso"
            "promotion" in x -> "Ascenso"
            "round of 16" in x || "last 16" in x -> "A octavos de final"
            "playoff" in x || "play-off" in x -> "Eliminatoria previa"
            "eliminated" in x -> "Eliminado"
            else -> ""
        }
    }

    /** Los nombres de los grupos de las tablas («League Phase», «Group A»). */
    fun tabla(t: String): String = when {
        t.contains("League Phase", true) -> "Fase de liga"
        Regex("(?i)^group ([A-Z0-9]+)$").matches(t.trim()) -> "Grupo " + t.trim().substringAfterLast(' ')
        Regex("(?i)laliga|liga", RegexOption.IGNORE_CASE).containsMatchIn(t) -> ""
        else -> t
    }
}

/** Cómo se ordenan y se eligen los partidos. */
object Orden {
    /** Lo que dura un partido como mucho (con prórroga y penaltis): si la fuente no dice nada, a partir de aquí ya no se da por «en directo». */
    const val DURACION = 150 * 60_000L

    /** ¿Puede estar jugándose ahora? (por la hora, aunque la fuente aún diga «por jugar»: así se pide más a menudo). */
    fun quizaEnDirecto(p: Partido, ahora: Long) = p.estado.enDirecto || (p.estado == Estado.POR_JUGAR && ahora >= p.inicio - 10 * 60_000L && ahora < p.inicio + DURACION)

    /** Los que se están jugando primero; luego los que vienen (el más cercano antes); luego los jugados (el más reciente antes). */
    fun ordena(l: Collection<Partido>, ahora: Long): List<Partido> {
        val unicos = l.groupBy { it.id }.map { (_, v) -> v.maxBy { calidad(it) } }
        val vivos = unicos.filter { it.estado.enDirecto }.sortedBy { it.inicio }
        val vienen = unicos.filter { !it.estado.enDirecto && !it.estado.terminado && it.estado != Estado.CANCELADO && (it.inicio >= ahora - DURACION || it.estado == Estado.APLAZADO) }.sortedBy { it.inicio }
        val resto = (unicos - vivos.toSet() - vienen.toSet()).sortedByDescending { it.inicio }
        return vivos + vienen + resto
    }

    /** Del mismo partido repetido (de dos calendarios, o de un calendario y un marcador), el que sabe más. */
    private fun calidad(p: Partido): Int = when { p.estado.enDirecto -> 3; p.estado.terminado -> 2; p.golesLocal != null -> 1; else -> 0 }

    /** Los que vienen (o se juegan) de un equipo, el más cercano primero. */
    fun proximos(l: Collection<Partido>, equipo: String, ahora: Long): List<Partido> =
        ordena(l.filter { it.juega(equipo) }, ahora).filter { it.estado.enDirecto || (!it.estado.terminado && it.estado != Estado.CANCELADO && it.inicio >= ahora - DURACION) }

    /** Los jugados de un equipo, el más reciente primero. */
    fun jugados(l: Collection<Partido>, equipo: String): List<Partido> = l.filter { it.juega(equipo) && it.estado.terminado }.distinctBy { it.id }.sortedByDescending { it.inicio }

    /**
     * «Tus partidos»: de cada equipo tuyo, el que se juega ahora o, si no, el siguiente; sin repetir (si juegan dos de los tuyos entre
     * ellos, sale una vez). Los que están en directo, primero; luego por hora.
     */
    fun tusPartidos(l: Collection<Partido>, equipos: List<String>, ahora: Long): List<Partido> {
        val r = equipos.mapNotNull { e -> proximos(l, e, ahora).firstOrNull() }.distinctBy { it.id }
        return r.sortedWith(compareBy({ !it.estado.enDirecto }, { it.inicio }))
    }

    /**
     * Los partidos de tus rivales que no son contra uno de los tuyos (esos ya salen como tuyos, «contra tu rival»): de cada rival, el
     * que se juega o el siguiente. Con [soloHoy] (la portada), solo los que se juegan ahora o hoy (en España).
     */
    fun deRivales(l: Collection<Partido>, rivales: List<String>, mios: List<String>, ahora: Long, soloHoy: Boolean = false): List<Partido> {
        val hoy = Hora.diaDe(ahora)
        return tusPartidos(l.filter { p -> mios.none { p.juega(it) } }, rivales, ahora)
            .filter { !soloHoy || it.estado.enDirecto || (Hora.diaDe(it.inicio) == hoy && !it.estado.terminado) }
    }

    /** ¿Es un partido de uno de los tuyos contra uno de tus rivales? */
    fun contraRival(p: Partido, mios: Collection<String>, rivales: Collection<String>) =
        (p.local.id in mios && p.visitante.id in rivales) || (p.visitante.id in mios && p.local.id in rivales)
}

/** Una sección de los partidos del día: «Tus equipos» ([competicion] null) o una competición. */
data class Seccion(val competicion: Competicion?, val partidos: List<Partido>) {
    val titulo: String get() = competicion?.nombre ?: "Tus equipos"
    val clave: String get() = competicion?.slug ?: "tus"
}

/** Los partidos de un día, como se enseñan. */
object Dia {
    /**
     * Agrupa los partidos de un día: primero «Tus equipos» (los de tus equipos y tus selecciones, aunque sean de una competición que
     * escondes), luego cada competición en tu [orden] (sin las [ocultas] ni las vacías); dentro, por hora. Un partido sale una vez: si es
     * de los tuyos, en «Tus equipos». [dia]: solo los de ese día (en España); null, todos (los de un marcador del día de ESPN, que
     * cuenta los días a su manera: un amistoso a las 2 de la madrugada de aquí sale en el día de antes, y no se pierde).
     */
    fun agrupa(l: Collection<Partido>, dia: LocalDate?, mios: Collection<String>, orden: List<Competicion>, ocultas: Set<String> = emptySet()): List<Seccion> {
        val delDia = l.filter { (dia == null || Hora.diaDe(it.inicio) == dia) && it.estado != Estado.CANCELADO }.groupBy { it.id }.map { (_, v) -> v.maxBy { if (it.estado.enDirecto) 3 else if (it.estado.terminado) 2 else 0 } }
        val tuyos = delDia.filter { p -> mios.any { p.juega(it) } }.sortedBy { it.inicio }
        val resto = delDia - tuyos.toSet()
        val r = ArrayList<Seccion>()
        if (tuyos.isNotEmpty()) r += Seccion(null, tuyos)
        for (c in orden) {
            if (c.slug in ocultas) continue
            val ps = resto.filter { it.liga == c.slug }.sortedWith(compareBy({ it.inicio }, { it.local.nombre }))
            if (ps.isNotEmpty()) r += Seccion(c, ps)
        }
        return r
    }

    /** Lo que se pone en la columna de la hora: «21:00», el minuto («67'»), «DESC», «FINAL», «APLAZ.». */
    fun cuando(p: Partido): String = when {
        p.estado == Estado.DESCANSO -> "DESC"
        p.estado.enDirecto -> p.reloj.ifBlank { "EN JUEGO" }
        p.estado.terminado -> "FINAL"
        p.estado == Estado.APLAZADO -> "APLAZ."
        p.estado == Estado.SUSPENDIDO -> "SUSP."
        else -> Hora.reloj(p.inicio)
    }

    /** Cuánto puede tener lo guardado de un día: lo pasado casi no cambia; hoy, al minuto si hay algo en juego; lo que viene, cada hora. */
    fun edad(dia: LocalDate, ahora: Long, hayEnJuego: Boolean): Long {
        val hoy = Hora.diaDe(ahora)
        return when {
            dia.isBefore(hoy.minusDays(1)) -> 30L * 86_400_000L
            dia.isBefore(hoy) -> 6L * 3_600_000L
            dia == hoy && hayEnJuego -> 60_000L
            dia == hoy -> 10L * 60_000L
            else -> 3_600_000L
        }
    }
}
