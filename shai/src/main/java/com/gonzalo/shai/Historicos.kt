package com.gonzalo.shai

import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoTexto
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** «Goleador más joven en la Champions League» → «goleador más joven en la Champions League» (sin tocar los nombres propios). */
fun String.inicialMinuscula(): String = if (isEmpty()) this else this[0].lowercase() + substring(1)

/** Las fechas del juego (las del calendario de FC): se meten como «12/08/2026», «12-8-26», «2026-08-12» o «12 ago 2026»; se guardan en ISO. */
object Fechas {
    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    fun lee(s: String?): LocalDate? {
        val t = s?.trim()?.lowercase().orEmpty().ifBlank { return null }
        runCatching { return LocalDate.parse(t) }
        Regex("""^(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})$""").find(t)?.let { m ->
            val (d, mo, a) = m.destructured
            val anio = a.toInt().let { if (a.length == 2) 2000 + it else it }
            return runCatching { LocalDate.of(anio, mo.toInt(), d.toInt()) }.getOrNull()
        }
        Regex("""^(\d{1,2})\s*(?:de\s+)?(\p{L}{3})\p{L}*\.?\s*(?:de\s+)?(\d{4})$""").find(t)?.let { m ->
            val (d, mes, a) = m.destructured
            val i = MESES.indexOf(mes.take(3)).takeIf { it >= 0 } ?: return null
            return runCatching { LocalDate.of(a.toInt(), i + 1, d.toInt()) }.getOrNull()
        }
        return null
    }

    fun iso(d: LocalDate): String = d.toString()
    fun texto(d: LocalDate): String = "%02d/%02d/%04d".format(d.dayOfMonth, d.monthValue, d.year)
    /** «12/08/2026» de lo que haya guardado (o lo guardado tal cual, si no se entiende). */
    fun bonita(s: String): String = lee(s)?.let { texto(it) } ?: s

    /** La edad exacta: años cumplidos y días desde el último cumpleaños. */
    fun edad(nacimiento: LocalDate, en: LocalDate): Pair<Int, Int> {
        var a = en.year - nacimiento.year
        if (nacimiento.plusYears(a.toLong()).isAfter(en)) a--
        return a to ChronoUnit.DAYS.between(nacimiento.plusYears(a.toLong()), en).toInt()
    }

    fun textoEdad(a: Int, d: Int): String = if (d < 0) "$a años" else "$a años y $d día${if (d == 1) "" else "s"}"
    fun dias(n: Long): String = if (n == 1L) "1 día" else "${Formato.cifra(n)} días"
}

/**
 * Un récord del fútbol de verdad que ha batido (o igualado) en su carrera de Shai: desde entonces, en su universo, la marca a batir es la
 * suya ([valor]). [marca]: cómo se cuenta («17 años y 30 días», «51 goles»).
 */
data class RecordSuyo(val n: Int, val valor: Double, val marca: String, val paso: Int, val temporada: String, val iguala: Boolean = false) {
    fun aMapa(): Map<String, Any?> = mapOf("n" to n.toLong(), "v" to valor, "m" to marca, "pa" to paso.toLong(), "te" to temporada, "ig" to iguala)
    companion object {
        fun de(m: Map<String, Any?>) = RecordSuyo(m["n"].comoLong().toInt(), m["v"].comoDouble(), m["m"].comoTexto(), m["pa"].comoLong().toInt(), m["te"].comoTexto(), m["ig"].comoBool())
    }
}

/**
 * Las comparaciones de su carrera en Shai con los récords reales de [RecordsReales] (solo los no dudosos, y solo con lo que se puede sacar
 * de lo que él mete: sus partidos, la competición de cada uno, sus fechas y su fecha de nacimiento). Lo de edad necesita las dos fechas:
 * sin fecha de nacimiento no se compara nada de días.
 */
object Historicos {
    enum class Estado { BATE, IGUALA, CERCA, A_TIRO, ESCAPA }

    /** Hasta cuántos días por encima de la edad del récord se cuenta como «rozarlo». */
    const val CERCA_DIAS = 90L

    class Comparacion(
        val r: RecordReal, val estado: Estado,
        /** Su marca, contada («17 años y 30 días», «38 goles»). */
        val suMarca: String, val suValor: Double,
        /** La frase entera (lo que sale en la carrera y se le pasa a Claude). */
        val texto: String,
        /** Si lo que bate es su propio récord (ya lo tenía). */
        val contraSuyo: Boolean = false,
        val fechaEstimada: Boolean = false,
        /** Lo que tiene a tiro por edad: los días que le quedan (desde su último partido) y hasta cuándo; -1 si no va por días. */
        val quedanDias: Long = -1, val plazo: String = "",
        /** Lo que tiene a tiro por cifra: lo que le falta para batirlo (en [RecordReal.unidad]) y lo que lleva; -1 si no va por cifra. */
        val falta: Double = -1.0,
    ) {
        /** La línea para Claude (y para el modo prueba): con las cifras tal cual. */
        val linea: String get() = "- ${estado.name.replace('_', ' ')} · #${r.n} ${r.record} · récord: ${r.quien}, ${r.marca} · él: $suMarca · $texto"
    }

    // ---------------------------------------------------------------- las competiciones

    val TOP5 = setOf("laliga", "premier", "seriea", "bundesliga", "ligue1")
    private val SELECCION = setOf("mundial", "eurocopa", "copaamerica", "clasificacion", "seleccion")

    /** La competición, normalizada (lo que él escribe: «LaLiga EA Sports», «UEFA Champions League», «Mundial 2030»…). */
    fun comp(s: String): String? {
        val t = s.lowercase()
        return when {
            t.isBlank() -> null
            Regex("club").containsMatchIn(t) && Regex("mundial|world cup|copa del mundo").containsMatchIn(t) -> "mundialclubes"
            Regex("clasif|eliminator|qualif|preliminar").containsMatchIn(t) && Regex("mundial|world|copa del mundo|euro|am[eé]rica").containsMatchIn(t) -> "clasificacion"
            Regex("champions|liga de campeones|\\bucl\\b").containsMatchIn(t) -> "champions"
            Regex("mundial|world cup|copa del mundo").containsMatchIn(t) -> "mundial"
            Regex("eurocopa|\\beuro\\b|\\beuro ?\\d{2,4}").containsMatchIn(t) -> "eurocopa"
            Regex("am[eé]rica").containsMatchIn(t) && Regex("copa").containsMatchIn(t) -> "copaamerica"
            Regex("libertadores").containsMatchIn(t) -> "libertadores"
            Regex("hypermotion|segunda|\\b2\\.|smartbank").containsMatchIn(t) -> null
            Regex("laliga|la liga|liga ea|primera divisi").containsMatchIn(t) -> "laliga"
            Regex("premier").containsMatchIn(t) -> "premier"
            Regex("serie a\\b").containsMatchIn(t) -> "seriea"
            Regex("bundesliga").containsMatchIn(t) -> "bundesliga"
            Regex("ligue 1|ligue1").containsMatchIn(t) -> "ligue1"
            Regex("nations|selecci|amistoso internacional").containsMatchIn(t) -> "seleccion"
            else -> null
        }
    }

    private fun en(p: Partido, c: String): Boolean = when (c) {
        "top5" -> comp(p.competicion) in TOP5
        "club" -> comp(p.competicion) !in SELECCION
        else -> comp(p.competicion) == c
    }

    private val NOMBRE_COMP = mapOf("champions" to "la Champions League", "mundial" to "el Mundial", "eurocopa" to "la Eurocopa", "copaamerica" to "la Copa América",
        "libertadores" to "la Libertadores", "laliga" to "LaLiga", "premier" to "la Premier League", "seriea" to "la Serie A", "bundesliga" to "la Bundesliga", "top5" to "una de las cinco grandes ligas")

    private fun nombreComp(c: String, p: Partido?) = NOMBRE_COMP[c] ?: p?.competicion ?: c

    fun portero(u: Universo) = Regex("^(por|pt|gk|portero)", RegexOption.IGNORE_CASE).containsMatchIn(u.personaje.posicion.trim())

    // ---------------------------------------------------------------- de quién es cada récord (familias de claves)

    private fun familia(clave: String) = clave.substringBefore(':')
    private fun compDe(clave: String) = clave.substringAfter(':', "")

    private val SOLO_MISTER = setOf("titulos_mister", "partidos_mister", "veterano_mister")
    private val DE_EQUIPO = setOf("goleada", "partido_goles", "victorias_seguidas", "invicto_temporada", "invicto_liga", "puntos_liga")
    private val DE_PORTERO = setOf("goles_portero", "cero_temporada", "cero_total", "hat_portero", "campeon_veterano_portero")

    private fun aplica(u: Universo, r: RecordReal): Boolean {
        val f = familia(r.clave ?: return false)
        return when {
            f in SOLO_MISTER -> u.modo == Rol.MISTER
            f in DE_EQUIPO -> true
            f in DE_PORTERO -> u.modo == Rol.JUGADOR && portero(u)
            f == "debut_joven" && compDe(r.clave) == "pro" || f == "cien_joven" -> u.modo == Rol.JUGADOR && u.nivelDe == Nivel.CANTERANO
            else -> u.modo == Rol.JUGADOR
        }
    }

    // ---------------------------------------------------------------- las fechas de cada partido

    /** La fecha de cada partido (la que metió o la que estimó Shai). */
    private fun fecha(p: Partido): LocalDate? = Fechas.lee(p.fecha)

    private fun nacimiento(u: Universo): LocalDate? = Fechas.lee(u.personaje.nacimiento)

    // ---------------------------------------------------------------- los de edad

    /** Lo que tiene que pasar en un partido para cada familia de edad. */
    private fun cumple(u: Universo, f: String, c: String, p: Partido, i: Int): Boolean = when (f) {
        "gol_joven", "gol_veterano" -> en(p, c) && p.goles > 0
        "debut_joven" -> if (c == "pro") i == 0 else en(p, c) && p.minutos > 0
        "asist_joven" -> en(p, c) && p.asistencias > 0
        "hat_joven" -> en(p, c) && p.goles >= 3
        "campeon_joven", "campeon_veterano_portero" -> p.titulo.isNotBlank() && comp(p.titulo) == c
        "jugar_veterano" -> en(p, c) && p.minutos > 0
        "veterano_mister" -> en(p, c)
        else -> false
    }

    private fun accion(f: String, c: String, p: Partido): String = when (f) {
        "gol_joven", "gol_veterano" -> "marca en ${nombreComp(c, p)} ante el ${p.rival}"
        "debut_joven" -> if (c == "pro") "debuta como profesional ante el ${p.rival}" else "debuta en ${nombreComp(c, p)} ante el ${p.rival}"
        "asist_joven" -> "da una asistencia en ${nombreComp(c, p)} ante el ${p.rival}"
        "hat_joven" -> "le hace un hat-trick al ${p.rival} en ${nombreComp(c, p)}"
        "campeon_joven", "campeon_veterano_portero" -> "gana ${p.titulo}"
        "cien_joven" -> "llega a los 100 partidos ante el ${p.rival}"
        "jugar_veterano" -> "juega en ${nombreComp(c, p)} ante el ${p.rival}"
        "veterano_mister" -> "dirige en ${nombreComp(c, p)} ante el ${p.rival}"
        else -> "lo consigue ante el ${p.rival}"
    }

    /** El partido de [ps] en que se cumple por primera vez (JOVEN) o la última (VETERANO). */
    private fun indice(u: Universo, r: RecordReal, ps: List<Partido>): Int? {
        val f = familia(r.clave!!); val c = compDe(r.clave)
        if (f == "cien_joven") { var n = 0; ps.forEachIndexed { i, p -> if (p.minutos > 0 && ++n == 100) return i }; return null }
        val ids = ps.indices.filter { cumple(u, f, c, ps[it], it) }
        return if (r.tipo == TipoRecord.JOVEN) ids.firstOrNull() else ids.lastOrNull()
    }

    /** Su edad en ese partido: exacta (años y días) si hay las dos fechas; solo años si el récord va por años. */
    private fun edadEn(u: Universo, r: RecordReal, p: Partido): Pair<Int, Int>? {
        val nac = nacimiento(u); val f = fecha(p)
        if (nac != null && f != null) return Fechas.edad(nac, f).let { if (r.dias < 0) it.first to -1 else it }
        return if (r.dias < 0) u.personaje.edad to -1 else null
    }

    private fun deEdad(u: Universo, r: RecordReal, ps: List<Partido>, suyo: RecordSuyo?): Comparacion? {
        val i = indice(u, r, ps) ?: return null
        if (i != ps.lastIndex) return null
        val p = ps[i]
        val f = familia(r.clave!!); val c = compDe(r.clave)
        // (lo de primera vez solo cuenta una vez; si ya lo tenía, nada)
        if (r.tipo == TipoRecord.JOVEN && suyo != null) return null
        val (a, d) = edadEn(u, r, p) ?: return null
        val su = Fechas.textoEdad(a, d)
        val est = Fechas.lee(p.fecha) != null && p.fechaEstimada
        val nombre = u.personaje.nombreCorto
        val cola = if (est) " (la fecha del partido la estimó Shai)" else ""
        if (r.dias < 0) {
            // Por años (Advocaat): lo bate si tiene más años cumplidos
            val objetivo = suyo?.valor?.toInt()?.div(1000) ?: r.anios
            return if (a > objetivo) Comparacion(r, Estado.BATE, su, a * 1000.0, "Nuevo récord histórico: ${r.record.inicialMinuscula()}. Con $su, $nombre ${accion(f, c, p)}. El récord era de ${r.quien}: ${r.marca}.$cola", suyo != null, est)
            else null
        }
        val nac = nacimiento(u)!!; val fp = fecha(p)!!
        // El día en que tendría la edad exacta del récord
        val diaRecord = nac.plusYears(r.anios.toLong()).plusDays(r.dias.toLong())
        val diferencia = ChronoUnit.DAYS.between(diaRecord, fp)   // > 0: es más mayor que el del récord
        return when (r.tipo) {
            TipoRecord.JOVEN -> when {
                diferencia < 0 -> Comparacion(r, Estado.BATE, su, a * 1000.0 + d,
                    "Nuevo récord histórico: ${r.record.inicialMinuscula()}. Con $su, $nombre ${accion(f, c, p)} y supera a ${r.quien}, que lo hizo con ${r.marca}. Lo bate por ${Fechas.dias(-diferencia)}.$cola", fechaEstimada = est)
                diferencia == 0L -> Comparacion(r, Estado.IGUALA, su, a * 1000.0 + d,
                    "Iguala un récord histórico: ${r.record.inicialMinuscula()}. Con $su, exactamente la edad de ${r.quien}, $nombre ${accion(f, c, p)}.$cola", fechaEstimada = est)
                diferencia <= CERCA_DIAS -> Comparacion(r, Estado.CERCA, su, a * 1000.0 + d,
                    "Con $su, $nombre ${accion(f, c, p)} y se queda a ${Fechas.dias(diferencia)} del récord de ${r.quien} (${r.marca}): ${r.record.inicialMinuscula()}.$cola", fechaEstimada = est)
                else -> null
            }
            else -> {
                val objetivo = suyo?.valor ?: (r.anios * 1000.0 + r.dias)
                val suyoV = a * 1000.0 + d
                if (suyoV > objetivo) {
                    if (suyo != null) Comparacion(r, Estado.BATE, su, suyoV, "", true, est)
                    else Comparacion(r, Estado.BATE, su, suyoV, "Nuevo récord histórico: ${r.record.inicialMinuscula()}. Con $su, $nombre ${accion(f, c, p)} y supera a ${r.quien}, que lo hizo con ${r.marca}. Lo bate por ${Fechas.dias(diferencia)}.$cola", fechaEstimada = est)
                } else null
            }
        }
    }

    // ---------------------------------------------------------------- los de cifra

    private fun rachaMax(ps: List<Partido>, f: (Partido) -> Boolean): Int { var m = 0; var c = 0; ps.forEach { if (f(it)) { c++; m = maxOf(m, c) } else c = 0 }; return m }
    private fun rachaFinal(ps: List<Partido>, f: (Partido) -> Boolean) = ps.reversed().takeWhile(f).size
    private fun temporadas(ps: List<Partido>) = ps.map { it.temporada }.distinct()
    private fun puntos(l: List<Partido>) = l.sumOf { if (it.ganado) 3 else if (it.empatado) 1 else 0 }

    /** Lo que lleva en cada familia de cifra con estos partidos (lo máximo, para comparar con el récord). */
    fun valor(clave: String, ps: List<Partido>): Double {
        val f = familia(clave); val c = compDe(clave).ifBlank { "" }
        fun de(p: Partido) = c.isBlank() || en(p, c)
        val q = ps.filter { de(it) }
        return when (f) {
            "goles_temporada" -> q.groupBy { it.temporada }.maxOfOrNull { (_, l) -> l.sumOf { it.goles } } ?: 0
            "asist_temporada" -> q.groupBy { it.temporada }.maxOfOrNull { (_, l) -> l.sumOf { it.asistencias } } ?: 0
            "goles_ano" -> q.mapNotNull { p -> fecha(p)?.let { it.year to p.goles } }.groupBy { it.first }.maxOfOrNull { (_, l) -> l.sumOf { it.second } } ?: 0
            "asist_ano" -> q.mapNotNull { p -> fecha(p)?.let { it.year to p.asistencias } }.groupBy { it.first }.maxOfOrNull { (_, l) -> l.sumOf { it.second } } ?: 0
            "goles_partido" -> q.maxOfOrNull { it.goles } ?: 0
            "goles_edicion" -> q.groupBy { it.temporada }.maxOfOrNull { (_, l) -> l.sumOf { it.goles } } ?: 0
            "goles_total", "goles_portero" -> q.sumOf { it.goles }
            "asist_total" -> q.sumOf { it.asistencias }
            "partidos_total" -> q.count { it.minutos > 0 }
            "hats_total" -> q.count { it.goles >= 3 }
            "pokers" -> q.count { it.goles >= 4 }
            "titulos", "titulos_mister" -> ps.count { it.titulo.isNotBlank() && (c.isBlank() || comp(it.titulo) == c) }
            "titulos_total" -> ps.count { it.titulo.isNotBlank() }
            "partidos_mister" -> ps.size
            "racha_gol" -> rachaMax(q) { it.goles > 0 }
            "racha_partidos" -> rachaMax(q) { it.minutos > 0 }
            "temporadas50" -> { var m = 0; var k = 0; temporadas(ps).forEach { t -> if (ps.filter { it.temporada == t }.sumOf { it.goles } >= 50) { k++; m = maxOf(m, k) } else k = 0 }; m }
            "temporadas_marcando" -> { var m = 0; var k = 0; temporadas(ps).forEach { t -> if (q.any { it.temporada == t && it.goles > 0 }) { k++; m = maxOf(m, k) } else k = 0 }; m }
            "mundiales_marcando" -> q.filter { it.goles > 0 }.map { it.temporada }.distinct().size
            "rivales" -> q.filter { it.goles > 0 }.map { it.rival.trim().lowercase() }.distinct().size
            "goleada" -> q.maxOfOrNull { it.golesFavor - it.golesContra } ?: 0
            "partido_goles" -> q.maxOfOrNull { it.golesFavor + it.golesContra } ?: 0
            "victorias_seguidas" -> rachaMax(q) { it.ganado }
            "invicto_liga" -> rachaMax(q) { it.ganado || it.empatado }
            "invicto_temporada" -> temporadas(ps).maxOfOrNull { t -> rachaMax(ps.filter { it.temporada == t }) { it.ganado || it.empatado } } ?: 0
            "puntos_liga" -> q.groupBy { it.temporada to comp(it.competicion) }.maxOfOrNull { (_, l) -> puntos(l) } ?: 0
            "cero_temporada" -> q.filter { it.golesContra == 0 && it.minutos >= 90 }.groupBy { it.temporada }.maxOfOrNull { it.value.size } ?: 0
            "cero_total" -> q.count { it.golesContra == 0 && it.minutos >= 90 }
            "hat_portero" -> q.maxOfOrNull { it.goles } ?: 0
            else -> 0
        }.toDouble()
    }

    /** Lo que lleva AHORA en la cifra que se está jugando (la temporada de ahora, la racha abierta…), para «a tiro». */
    private fun enCurso(clave: String, ps: List<Partido>): Double {
        val f = familia(clave); val c = compDe(clave)
        val t = ps.lastOrNull()?.temporada ?: return 0.0
        val q = ps.filter { c.isBlank() || en(it, c) }
        return when (f) {
            "goles_temporada", "goles_edicion" -> q.filter { it.temporada == t }.sumOf { it.goles }.toDouble()
            "asist_temporada" -> q.filter { it.temporada == t }.sumOf { it.asistencias }.toDouble()
            "goles_ano", "asist_ano" -> { val y = ps.lastOrNull()?.let { fecha(it)?.year } ?: return 0.0
                q.filter { fecha(it)?.year == y }.sumOf { if (f == "goles_ano") it.goles else it.asistencias }.toDouble() }
            "racha_gol" -> rachaFinal(q) { it.goles > 0 }.toDouble()
            "racha_partidos" -> rachaFinal(q) { it.minutos > 0 }.toDouble()
            "victorias_seguidas" -> rachaFinal(q) { it.ganado }.toDouble()
            "invicto_liga" -> rachaFinal(q) { it.ganado || it.empatado }.toDouble()
            "invicto_temporada" -> rachaFinal(ps.filter { it.temporada == t }) { it.ganado || it.empatado }.toDouble()
            "puntos_liga" -> ps.filter { it.temporada == t && comp(it.competicion) in TOP5 }.groupBy { comp(it.competicion) }.maxOfOrNull { puntos(it.value) }?.toDouble() ?: 0.0
            "cero_temporada" -> q.count { it.temporada == t && it.golesContra == 0 && it.minutos >= 90 }.toDouble()
            "goles_partido", "partido_goles", "goleada", "hat_portero" -> 0.0
            else -> valor(clave, ps)
        }
    }

    private fun cuenta(v: Double, unidad: String) = Formato.cifra(Math.round(v)) + " " + unidad

    private fun deCifra(u: Universo, r: RecordReal, ps: List<Partido>, suyo: RecordSuyo?): Comparacion? {
        val v = r.valor ?: return null
        val ahora = valor(r.clave!!, ps)
        val antes = valor(r.clave, ps.dropLast(1))
        val p = ps.last()
        val nombre = u.personaje.nombreCorto
        val quien = if (familia(r.clave) in DE_EQUIPO) "su equipo, el ${u.club}," else nombre
        val su = cuenta(ahora, r.unidad)
        if (suyo != null) {
            // Ya es suyo: lo mejora (en silencio en los acumulados; en lo de temporada, partido o edición, se cuenta)
            if (ahora > suyo.valor && antes <= suyo.valor) {
                val f = familia(r.clave)
                val cuenta = f in setOf("goles_temporada", "asist_temporada", "goles_ano", "asist_ano", "goles_partido", "goles_edicion", "goleada", "partido_goles", "puntos_liga", "cero_temporada")
                return Comparacion(r, Estado.BATE, su, ahora, if (cuenta) "Vuelve a batir su propio récord: ${r.record.inicialMinuscula()}. Ahora, $su (lo tenía en ${suyo.marca})." else "", true)
            }
            return null
        }
        return when {
            ahora > v && antes <= v -> Comparacion(r, Estado.BATE, su, ahora,
                "Nuevo récord histórico: ${r.record.inicialMinuscula()}. Con ${p.competicion.ifBlank { "este partido" }} ante el ${p.rival}, $quien llega a $su y supera a ${r.quien} (${r.marca}).")
            ahora == v && antes < v -> Comparacion(r, Estado.IGUALA, su, ahora,
                "Iguala un récord histórico: ${r.record.inicialMinuscula()}. Con $su, $quien se pone a la altura de ${r.quien} (${r.marca}).")
            else -> null
        }
    }

    // ---------------------------------------------------------------- lo de este partido y lo que tiene a tiro

    /** Lo que acaba de batir, igualar o rozar con el último partido de [ps] (los récords reales no dudosos que se pueden comparar). */
    fun nuevos(u: Universo, ps: List<Partido>): List<Comparacion> {
        if (ps.isEmpty()) return emptyList()
        return RecordsReales.TODOS.filter { it.usable && aplica(u, it) }.mapNotNull { r ->
            val suyo = u.recordsSuyos.firstOrNull { it.n == r.n }
            if (r.deEdad) deEdad(u, r, ps, suyo) else deCifra(u, r, ps, suyo)
        }
    }

    /** Los que tiene a tiro (cuánto le falta) y los que ya son suyos, para Cifras y para el contexto de Claude. */
    fun aTiro(u: Universo, ps: List<Partido>): List<Comparacion> {
        val nombre = u.personaje.nombreCorto
        val ultima = ps.lastOrNull()?.let { fecha(it) }
        val nac = nacimiento(u)
        val l = mutableListOf<Comparacion>()
        for (r in RecordsReales.TODOS.filter { it.usable && aplica(u, it) }) {
            if (u.recordsSuyos.any { it.n == r.n }) continue
            if (r.deEdad) {
                if (r.dias < 0 || nac == null || ultima == null) continue
                val f = familia(r.clave!!); val c = compDe(r.clave)
                val dia = nac.plusYears(r.anios.toLong()).plusDays(r.dias.toLong())
                if (r.tipo == TipoRecord.JOVEN) {
                    if (indice(u, r, ps) != null || !dia.isAfter(ultima)) continue
                    // Solo lo que tiene que ver con lo que juega (o lo general)
                    if (c !in setOf("top5", "champions", "pro", "") && ps.none { en(it, c) } && f != "cien_joven") continue
                    val n = ChronoUnit.DAYS.between(ultima, dia)
                    l += Comparacion(r, Estado.A_TIRO, "aún no", 0.0, "Tiene hasta el ${Fechas.texto(dia)} (${Fechas.dias(n)}) para ser el más joven: ${r.record.inicialMinuscula()} (${r.quien}, ${r.marca}).",
                        quedanDias = n, plazo = Fechas.texto(dia))
                } else {
                    val (a, _) = Fechas.edad(nac, ultima)
                    if (a < r.anios - 3 || ps.none { cumple(u, f, c, it, 0) }) continue
                    val n = ChronoUnit.DAYS.between(ultima, dia)
                    if (n > 0) l += Comparacion(r, Estado.A_TIRO, Fechas.textoEdad(a, -1), 0.0, "Si lo sigue haciendo después del ${Fechas.texto(dia)} (dentro de ${Fechas.dias(n)}), será el más veterano: ${r.record.inicialMinuscula()} (${r.quien}, ${r.marca}).")
                }
            } else {
                val v = r.valor ?: continue
                val f = familia(r.clave!!)
                val cur = enCurso(r.clave, ps)
                val umbral = when (f) { "racha_gol", "racha_partidos", "victorias_seguidas", "invicto_liga", "invicto_temporada" -> 0.5; "goles_total", "asist_total", "partidos_total", "titulos", "titulos_total", "titulos_mister", "partidos_mister", "goles_portero", "cero_total", "hats_total", "pokers" -> 0.75; else -> 0.6 }
                if (cur <= 0 || cur >= v || cur < v * umbral) continue
                val falta = v - cur
                l += Comparacion(r, Estado.A_TIRO, cuenta(cur, r.unidad), cur, "Lleva ${cuenta(cur, r.unidad)}: le falta${if (falta == 1.0) "" else "n"} ${cuenta(falta, r.unidad)} para igualar a ${r.quien} (${r.marca}) y ${cuenta(falta + 1, r.unidad)} para batir el récord: ${r.record.inicialMinuscula()}.",
                    falta = falta + 1)
            }
        }
        return l.sortedWith(compareBy<Comparacion> { urgencia(u, it).ordinal }.thenBy { if (it.r.deEdad) 0 else 1 }.thenBy { if (it.quedanDias >= 0) it.quedanDias.toDouble() else it.falta })
    }

    // ---------------------------------------------------------------- la cuenta atrás

    enum class Urgencia(val nombre: String) { ALTA("ALTA"), MEDIA("MEDIA"), BAJA("BAJA") }

    /**
     * Lo que pesa lo que tiene a tiro: cuanto menos le falta, más; y más cuanto más joven y más nivel tiene (un chaval que va a por un récord
     * de precocidad es portada; un veterano que se acerca a una cifra, un dato). ALTA: la prensa lo convierte en cuenta atrás.
     */
    fun urgencia(u: Universo, c: Comparacion): Urgencia {
        val joven = u.personaje.edad <= 21
        val nivel = u.nivelActual.ordinal
        val base = when {
            c.quedanDias in 0..30 -> 0
            c.quedanDias in 31..90 -> 1
            c.quedanDias > 90 -> 2
            c.falta in 0.0..3.0 -> 0
            c.falta in 3.0..8.0 -> 1
            else -> 2
        }
        val sube = (if (joven) 1 else 0) + (if (nivel >= 3) 1 else 0)
        return Urgencia.entries[(base - (if (base == 2 && sube >= 2) 1 else 0)).coerceIn(0, 2)]
    }

    /** «23 días» o «4 goles»: lo que le queda. */
    fun queda(c: Comparacion): String = when {
        c.quedanDias >= 0 -> Fechas.dias(c.quedanDias)
        c.falta >= 0 -> cuenta(c.falta, c.r.unidad)
        else -> ""
    }

    /** La línea de la cuenta atrás para Claude (y para el modo prueba). */
    fun lineaCuenta(u: Universo, c: Comparacion): String = "- CUENTA ATRÁS · #${c.r.n} ${c.r.record} · récord: ${c.r.quien}, ${c.r.marca} · " +
        (if (c.quedanDias >= 0) "quedan ${Fechas.dias(c.quedanDias)} (hasta el ${c.plazo})" else "le faltan ${queda(c)} para batirlo (lleva ${c.suMarca})") +
        " · urgencia ${urgencia(u, c).nombre}"

    /**
     * Los récords de precocidad que tenía a tiro antes del último partido y que ya no puede batir (el plazo ha pasado sin conseguirlo):
     * «se le escapa». Si le quedaban 7 días o menos, se quedó a nada (drama).
     */
    fun escapados(u: Universo, ps: List<Partido>): List<Comparacion> {
        if (ps.size < 2) return emptyList()
        val antes = aTiro(u, ps.dropLast(1)).filter { it.quedanDias >= 0 && it.r.tipo == TipoRecord.JOVEN }
        if (antes.isEmpty()) return emptyList()
        val ahora = aTiro(u, ps).map { it.r.n }.toSet()
        val conseguidos = nuevos(u, ps).map { it.r.n }.toSet()
        val nombre = u.personaje.nombreCorto
        return antes.filter { it.r.n !in ahora && it.r.n !in conseguidos }.map { c ->
            val nada = c.quedanDias <= 7
            Comparacion(c.r, Estado.ESCAPA, "aún no", 0.0,
                "Se le escapa un récord histórico: ${c.r.record.inicialMinuscula()}. El plazo para superar a ${c.r.quien} (${c.r.marca}) acabó el ${c.plazo} y $nombre ya no puede ser el más joven." +
                    if (nada) " Se queda a nada: en su último partido antes del plazo le quedaban solo ${Fechas.dias(c.quedanDias)}." else "",
                quedanDias = c.quedanDias, plazo = c.plazo)
        }
    }

    /** La fama que da (la repercusión: lo más gordo que le puede pasar; más cuanto más joven). No respeta el tope de la jornada. */
    fun fama(u: Universo, c: Comparacion): Int {
        if (c.contraSuyo) return if (c.texto.isBlank()) 0 else 2
        val e = u.personaje.edad
        val porEdad = when { e <= 19 -> 6; e <= 21 -> 4; e <= 25 -> 2; else -> 0 }
        return when (c.estado) {
            Estado.BATE -> 6 + porEdad + (if (c.r.deEdad) 2 else 0)
            Estado.IGUALA -> 4 + porEdad / 2
            Estado.CERCA -> 1 + porEdad / 3
            Estado.A_TIRO, Estado.ESCAPA -> 0
        }
    }
}
