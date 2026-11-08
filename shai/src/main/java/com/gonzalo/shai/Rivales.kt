package com.gonzalo.shai

import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto

/** Un choque con un rival: lo que pasó y cuánto subió (o bajó) la cosa. [tipo]: `deportivo` (en el campo, en las cifras) o `personal` (piques, pullas, mal rollo). */
data class Choque(val paso: Int, val texto: String, val cambio: Int, val tipo: String = DEPORTIVO) {
    fun aMapa(): Map<String, Any?> = mapOf("pa" to paso.toLong(), "t" to texto, "c" to cambio.toLong(), "k" to tipo)
    companion object {
        const val DEPORTIVO = "deportivo"
        const val PERSONAL = "personal"
        fun de(m: Map<String, Any?>) = Choque(m["pa"].comoLong().toInt(), m["t"].comoTexto(), m["c"].comoLong().toInt(), m["k"].comoTexto().ifBlank { DEPORTIVO })
    }
}

/**
 * Un rival: surge solo por lo que pasa (o lo metes tú). [real]: un futbolista de verdad (solo lo metes tú): con él, solo rivalidad
 * deportiva vista por la prensa, con cifras; nada de piques ni frases. Los que inventa Shai (o Claude) son siempre ficticios, de clubes reales.
 */
data class Rival(
    val id: String, val nombre: String, val club: String, val posicion: String = "", val origen: String = "", val intensidad: Int = 30,
    val real: Boolean = false, val desde: Int = 0, val choques: List<Choque> = emptyList(), val ultimo: Int = 0,
) {
    val grado: String get() = when { intensidad < 25 -> "tibia"; intensidad < 50 -> "viva"; intensidad < 75 -> "caliente"; else -> "guerra abierta" }
    fun aMapa(): Map<String, Any?> = mapOf("id" to id, "n" to nombre, "cl" to club, "po" to posicion, "or" to origen, "in" to intensidad.toLong(), "re" to real,
        "de" to desde.toLong(), "ch" to choques.map { it.aMapa() }, "ul" to ultimo.toLong())
    companion object {
        fun de(m: Map<String, Any?>) = Rival(m["id"].comoTexto(), m["n"].comoTexto(), m["cl"].comoTexto(), m["po"].comoTexto(), m["or"].comoTexto(),
            m["in"].comoLong().toInt().coerceIn(0, 100), m["re"].comoBool(), m["de"].comoLong().toInt(), m["ch"].comoLista().map { Choque.de(it.comoMapa()) }, m["ul"].comoLong().toInt())
    }
}

/** Lo que dice Claude (o el modo prueba) de un rival: un choque, cuánto sube y, si [nuevo], que acaba de nacer (siempre inventado). */
data class CambioRival(val nombre: String, val club: String = "", val posicion: String = "", val origen: String = "", val cambio: Int = 0, val choque: String = "",
                       val tipo: String = Choque.DEPORTIVO, val nuevo: Boolean = false) {
    fun aMapa(): Map<String, Any?> = mapOf("n" to nombre, "cl" to club, "po" to posicion, "or" to origen, "c" to cambio.toLong(), "ch" to choque, "k" to tipo, "nu" to nuevo)
    companion object {
        fun de(m: Map<String, Any?>) = CambioRival(m["n"].comoTexto(), m["cl"].comoTexto(), m["po"].comoTexto(), m["or"].comoTexto(), m["c"].comoLong().toInt(),
            m["ch"].comoTexto(), m["k"].comoTexto().ifBlank { Choque.DEPORTIVO }, m["nu"].comoBool())
        /** Lo que viene de Claude (`efectos.rivales`). */
        fun deClaude(m: Map<String, Any?>) = CambioRival(m["nombre"].comoTexto(), m["club"].comoTexto(), m["posicion"].comoTexto(), m["origen"].comoTexto(),
            m["cambio"].comoLong().toInt(), m["choque"].comoTexto(), if (m["tipo"].comoTexto() == Choque.PERSONAL) Choque.PERSONAL else Choque.DEPORTIVO, m["nuevo"].comoBool())
    }
}

object Rivales {
    /** Cuántos rivales vivos como mucho (si nace otro, se apaga el más tibio). */
    const val MAX = 6

    // Nombres inventados (combinaciones corrientes que no son de nadie conocido)
    private val NOMBRES = listOf("Iker", "Bruno", "Marcos", "Adrián", "Thiago", "Luca", "Mateo", "Hugo", "Óscar", "Rubén", "Enzo", "Nico", "Dario", "Yannick", "Kevin", "Samu", "Joel", "Teo", "Malik", "Andrés")
    private val APELLIDOS = listOf("Lafuente", "Correa Vidal", "Montaner", "Arrieta", "Bravo Soler", "Quintela", "Ferrandis", "Galán", "Oyarzábal Ruiz", "Dacosta", "Maresca Lugo",
        "Ibarguren", "Peñalver", "Soto Mairena", "Valcárcel", "Nkemelu", "Rossetti", "Duarte Lins", "Cabañero", "Ostrowski")

    private fun inventa(semilla: String, usados: Collection<String>): String {
        val h = semilla.hashCode().let { if (it < 0) -it else it }
        for (k in 0 until 400) {
            val n = NOMBRES[(h + k * 7) % NOMBRES.size] + " " + APELLIDOS[(h / 3 + k * 11) % APELLIDOS.size]
            if (usados.none { Vida.mismo(it, n) }) return n
        }
        return "Jugador ${h % 1000}"
    }

    private fun nuevoRival(u: Universo, nombre: String, club: String, posicion: String, origen: String, intensidad: Int, real: Boolean = false) =
        Rival(Universo.nuevoId("r"), nombre, club, posicion, origen, intensidad.coerceIn(0, 100), real, u.paso, ultimo = u.paso)

    private fun mete(l: List<Rival>, r: Rival): List<Rival> {
        val todos = l + r
        return if (todos.size <= MAX) todos else todos.sortedByDescending { if (it.id == r.id) 1000 else it.intensidad }.take(MAX)
    }

    private fun goleaLiga(ps: List<Partido>, t: String) = ps.filter { it.temporada == t && Historicos.comp(it.competicion) in Historicos.TOP5 + "laliga" }.sumOf { it.goles }

    class TrasPartido(val rivales: List<Rival>, val nacidos: List<Rival>, val choques: List<Pair<Rival, Choque>>)

    /**
     * Tras un partido ([ps] ya lo incluye): cómo cambian los rivales que hay (los duelos directos con su club, la pelea por el puesto) y si
     * nace uno solo (como mucho uno por jornada): el delantero del club que siempre te marca, el central que te anuló, el compañero que
     * compite por tu puesto, el que te disputa el trofeo de goleador. Los que nacen aquí son inventados.
     */
    fun trasPartido(u: Universo, ps: List<Partido>): TrasPartido {
        val p = ps.lastOrNull() ?: return TrasPartido(u.rivales, emptyList(), emptyList())
        val jugador = u.modo == Rol.JUGADOR
        val choques = mutableListOf<Pair<Rival, Choque>>()
        var l = u.rivales.map { r ->
            val duelo = r.club.isNotBlank() && Vida.mismo(r.club, p.rival)
            val puesto = r.origen.startsWith("Compite por tu puesto") && Vida.mismo(r.club, u.club)
            when {
                duelo -> {
                    val c = 8 + (if (jugador && p.goles > 0) 4 else 0) + (if (!p.ganado && !p.empatado) 6 else 0) + (if (p.ganado) 2 else 0)
                    val t = "Duelo directo en la jornada ${u.paso}: ${u.club} ${p.resultado} ${p.rival}" + when {
                        !jugador -> if (p.ganado) ". Le ganas el pulso." else if (p.empatado) ". Tablas." else ". Esta vez gana él."
                        p.goles > 0 -> ". Tú marcaste (${p.goles}); la prensa ya cuenta los goles de cada uno."
                        p.minutos == 0 -> ". No jugaste: él se llevó los focos."
                        else -> ". Te quedaste sin marcar delante de él."
                    }
                    val ch = Choque(u.paso, t, c)
                    choques += r to ch
                    r.copy(intensidad = (r.intensidad + c).coerceIn(0, 100), choques = (r.choques + ch).takeLast(12), ultimo = u.paso)
                }
                puesto && jugador && p.minutos in 1..59 -> {
                    val ch = Choque(u.paso, "Jornada ${u.paso}: solo jugaste ${p.minutos} minutos y la grada ya pregunta si el puesto es suyo.", 6)
                    choques += r to ch
                    r.copy(intensidad = (r.intensidad + 6).coerceIn(0, 100), choques = (r.choques + ch).takeLast(12), ultimo = u.paso)
                }
                puesto && jugador && p.minutos >= 85 && (p.goles > 0 || p.nota >= 7.5) -> {
                    val ch = Choque(u.paso, "Jornada ${u.paso}: partido entero y ${if (p.goles > 0) "gol" else "buena nota"}; el puesto, de momento, es tuyo.", -3)
                    choques += r to ch
                    r.copy(intensidad = (r.intensidad - 3).coerceIn(5, 100), choques = (r.choques + ch).takeLast(12), ultimo = u.paso)
                }
                // Sin contacto, se enfría poco a poco
                u.paso - r.ultimo >= 2 -> r.copy(intensidad = (r.intensidad - 1).coerceAtLeast(5))
                else -> r
            }
        }
        // ¿Nace una? (una por jornada como mucho, la primera que toque)
        val usados = l.map { it.nombre } + u.entorno.map { it.nombre } + u.personaje.nombre
        val conClub = { c: String -> l.any { Vida.mismo(it.club, c) && !it.origen.startsWith("Compite por tu puesto") } }
        val antesContra = ps.dropLast(1).filter { Vida.mismo(it.rival, p.rival) }
        val t = p.temporada
        val pos = u.personaje.posicion.uppercase()
        val defensa = Regex("^(DFC|CB|LD|LI|LB|RB|CAD|CAI|POR|GK)").containsMatchIn(pos)
        val nacido: Rival? = when {
            p.rival.isBlank() -> null
            // El del club que siempre te marca: segundo duelo (o más) con ese club y te marcan
            antesContra.isNotEmpty() && p.golesContra > 0 && !conClub(p.rival) -> {
                val n = antesContra.size + 1
                val puesto = if (!jugador) "entrenador" else if (defensa) "delantero" else "central"
                nuevoRival(u, inventa(p.rival + "dueloA", usados), p.rival, puesto,
                    "Duelos con el ${p.rival}: ya os habéis visto $n veces y su $puesto siempre aparece (${p.golesFavor}-${p.golesContra} esta vez)", 35 + (if (!p.ganado) 10 else 0))
            }
            // El central que te anuló
            jugador && !defensa && p.minutos >= 70 && p.goles == 0 && p.nota in 0.1..5.9 && !conClub(p.rival) ->
                nuevoRival(u, inventa(p.rival + "central", usados), p.rival, "central",
                    "Te anuló en la jornada ${u.paso} (${u.club} ${p.resultado} ${p.rival}): ni una ocasión clara y una nota de ${Formato.decimal(p.nota)}", 30)
            // El compañero que compite por tu puesto
            jugador && ps.size >= 3 && p.minutos in 1..45 && l.none { it.origen.startsWith("Compite por tu puesto") } ->
                nuevoRival(u, inventa(u.club + "puesto", usados), u.club, u.personaje.posicion.ifBlank { "tu puesto" },
                    "Compite por tu puesto en el ${u.club}: tiene tu edad (más o menos) y el entrenador le está dando minutos (tú, ${p.minutos} en la jornada ${u.paso})", 30)
            // El que te disputa el trofeo de máximo goleador
            jugador && goleaLiga(ps, t) >= 10 && goleaLiga(ps.dropLast(1), t) < 10 && l.none { it.origen.startsWith("Te disputa el trofeo") } -> {
                val club = ps.filter { it.temporada == t }.groupBy { it.rival }.maxByOrNull { (_, x) -> x.sumOf { it.golesContra } }?.key ?: p.rival
                nuevoRival(u, inventa(club + "goleador", usados), club, "delantero",
                    "Te disputa el trofeo de máximo goleador de la ${u.liga.ifBlank { "liga" }} ${t}: tú llevas ${goleaLiga(ps, t)} y él va a tu ritmo", 40)
            }
            else -> null
        }
        if (nacido != null) l = mete(l, nacido)
        return TrasPartido(l, listOfNotNull(nacido), choques)
    }

    /** Al irse a otro club: el compañero con el que no acabó bien (inventado) se queda en el viejo y la rivalidad empieza. */
    fun excompanero(u: Universo, viejo: String): Universo {
        if (viejo.isBlank() || u.rivales.any { Vida.mismo(it.club, viejo) && it.origen.startsWith("Excompañero") }) return u
        val n = inventa(viejo + "ex", u.rivales.map { it.nombre } + u.entorno.map { it.nombre })
        val r = nuevoRival(u, n, viejo, if (u.modo == Rol.MISTER) "el que era su segundo" else u.personaje.posicion.ifBlank { "compañero" },
            "Excompañero en el $viejo: se quedó allí y no le ha sentado nada bien cómo te fuiste", 35)
        return u.copy(rivales = mete(u.rivales, r))
    }

    /** Un rival que metes tú. Si es un futbolista de verdad ([real]), la rivalidad solo es deportiva. */
    fun anade(u: Universo, nombre: String, club: String, real: Boolean, origen: String = ""): Universo {
        if (nombre.isBlank() || u.rivales.any { Vida.mismo(it.nombre, nombre) }) return u
        return u.copy(rivales = mete(u.rivales, nuevoRival(u, nombre.trim(), club.trim(), "", origen.ifBlank { if (real) "Lo has elegido tú (futbolista real: rivalidad solo deportiva)" else "Lo has elegido tú" }, 30, real)))
    }

    fun quita(u: Universo, id: String) = u.copy(rivales = u.rivales.filter { it.id != id })

    /**
     * Lo que cuenta Claude de los rivales: choques con los que hay y alguno nuevo (siempre inventado). Con los rivales reales, solo lo
     * deportivo (lo personal se tira) y sin pasarse; a los nuevos que no conocemos se les da por inventados.
     */
    fun filtra(u: Universo, cs: List<CambioRival>): List<CambioRival> = cs.filter { it.nombre.isNotBlank() }.mapNotNull { c ->
        val r = u.rivales.firstOrNull { Vida.mismo(it.nombre, c.nombre) }
        when {
            r?.real == true -> if (c.tipo == Choque.PERSONAL) null else c.copy(cambio = c.cambio.coerceIn(-5, 5), nuevo = false)
            r == null && !c.nuevo -> null
            else -> c.copy(cambio = c.cambio.coerceIn(-15, 15))
        }
    }.take(3)

    fun aplica(u: Universo, cs: List<CambioRival>): Universo {
        var l = u.rivales
        for (c in filtra(u, cs)) {
            val i = l.indexOfFirst { Vida.mismo(it.nombre, c.nombre) }
            if (i >= 0) {
                val r = l[i]
                val ch = Choque(u.paso, c.choque, c.cambio, c.tipo)
                l = l.toMutableList().also { it[i] = r.copy(intensidad = (r.intensidad + c.cambio).coerceIn(0, 100), choques = if (ch.texto.isBlank()) r.choques else (r.choques + ch).takeLast(12), ultimo = u.paso) }
            } else if (c.nuevo && l.count { !it.real } < MAX) {
                val r = nuevoRival(u, c.nombre, c.club, c.posicion, c.origen.ifBlank { c.choque }, (25 + c.cambio).coerceIn(15, 60))
                l = mete(l, if (c.choque.isNotBlank()) r.copy(choques = listOf(Choque(u.paso, c.choque, c.cambio, c.tipo))) else r)
            }
        }
        return u.copy(rivales = l)
    }

    /** Las líneas para Claude: quién es cada uno, por qué, cómo de caliente y si es real (solo deportivo) o inventado (vale todo). */
    fun contexto(u: Universo): String = u.rivales.sortedByDescending { it.intensidad }.joinToString("\n") { r ->
        "- RIVAL · ${r.nombre} · ${r.club.ifBlank { "sin club" }} · ${r.posicion.ifBlank { "—" }} · ${r.origen} · intensidad ${r.intensidad}/100 (${r.grado}) · " +
            (if (r.real) "REAL: solo rivalidad deportiva vista por la prensa con cifras; sin frases inventadas, sin piques ni mal rollo" else "INVENTADO: vale todo (pullas en redes, declaraciones, chicas de por medio)") +
            (r.choques.takeLast(2).takeIf { it.isNotEmpty() }?.joinToString("; ", " · últimos choques: ") { "J${it.paso} ${it.texto}" } ?: "")
    }
}
