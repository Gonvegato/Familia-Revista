package com.gonzalo.shai

import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoTexto

/**
 * Un hito o un récord de tu carrera en FcFeed (con las cifras que tú metes: no son récords del mundo real). [precoz]: conseguido muy joven
 * para lo que es.
 */
data class Hito(val id: String, val titulo: String, val texto: String, val paso: Int, val edad: Int, val precoz: Boolean = false, val temporada: String = "",
                /** Si es la comparación con un récord real ([RecordsReales]): su número, y la línea con las cifras para Claude. */
                val real: Int = 0, val linea: String = "") {
    fun aMapa(): Map<String, Any?> = mapOf("id" to id, "ti" to titulo, "tx" to texto, "pa" to paso.toLong(), "ed" to edad.toLong(), "pr" to precoz, "te" to temporada,
        "rn" to real.toLong(), "li" to linea)
    companion object {
        fun de(m: Map<String, Any?>) = Hito(m["id"].comoTexto(), m["ti"].comoTexto(), m["tx"].comoTexto(), m["pa"].comoLong().toInt(), m["ed"].comoLong().toInt(), m["pr"].comoBool(), m["te"].comoTexto(),
            m["rn"].comoLong().toInt(), m["li"].comoTexto())
    }
}

/** Los récords de la carrera (de FcFeed), sacados de sus partidos. */
class Records(
    val partidos: Int, val goles: Int, val asistencias: Int, val hatTricks: Int, val masGolesPartido: Int,
    val rachaMarcando: Int, val rachaInvicto: Int, val mejorTemporada: Pair<String, Int>?, val victorias: Int, val titulos: List<String>,
)

object Hitos {
    private val GOLES = listOf(1, 10, 25, 50, 100, 150, 200, 300, 400, 500)
    private val PARTIDOS = listOf(1, 50, 100, 200, 300, 400, 500)
    private val RACHA_GOL = listOf(3, 5, 7, 10)
    private val INVICTO = listOf(10, 15, 20, 30)
    private val NOTABLES = Regex("champions|europa|conference|selecci|mundial|euro|nations|copa del rey|supercopa|fa cup|copa", RegexOption.IGNORE_CASE)

    /** ¿Es precoz a esta edad? (cuanto más gordo el hito, más tarde sigue siéndolo). */
    private fun precoz(edad: Int, hasta: Int) = edad in 1..hasta

    /** Las rachas que terminan en el último partido. */
    private fun rachaFinal(ps: List<Partido>, f: (Partido) -> Boolean) = ps.reversed().takeWhile(f).size
    private fun rachaMax(ps: List<Partido>, f: (Partido) -> Boolean): Int { var m = 0; var c = 0; ps.forEach { if (f(it)) { c++; m = maxOf(m, c) } else c = 0 }; return m }

    fun records(u: Universo, ps: List<Partido>): Records {
        val porTemporada = ps.groupBy { it.temporada }.mapValues { (_, l) -> if (u.modo == Rol.JUGADOR) l.sumOf { it.goles } else l.count { it.ganado } }
        return Records(ps.size, ps.sumOf { it.goles }, ps.sumOf { it.asistencias }, ps.count { it.goles >= 3 }, ps.maxOfOrNull { it.goles } ?: 0,
            rachaMax(ps) { it.goles > 0 }, rachaMax(ps) { it.ganado || it.empatado }, porTemporada.maxByOrNull { it.value }?.toPair()?.takeIf { it.second > 0 },
            ps.count { it.ganado }, ps.mapNotNull { it.titulo.ifBlank { null } })
    }

    /**
     * Lo que se acaba de conseguir con el último partido de [ps] (que ya incluye ese partido): primer gol, 10/25/50/100… goles, hat-tricks,
     * rachas, partidos 1/50/100…, primeras veces en competiciones grandes, récord de goles en una temporada, títulos. La [edad] es la de ahora.
     */
    fun nuevos(u: Universo, ps: List<Partido>, paso: Int): List<Hito> {
        val p = ps.lastOrNull() ?: return emptyList()
        val antes = ps.dropLast(1)
        val e = u.personaje.edad
        val t = p.temporada
        val l = mutableListOf<Hito>()
        fun h(id: String, titulo: String, texto: String, pre: Boolean = false) {
            if (u.hitos.none { it.id == id }) l += Hito(id, titulo, texto + if (pre) " Con $e años: de los más precoces de su carrera en FcFeed." else "", paso, e, pre, t)
        }
        // Partidos
        PARTIDOS.firstOrNull { it == ps.size }?.let { n ->
            if (n == 1) h("debut", "Debut en FcFeed", "Su primer partido en la carrera: ${p.rival} (${p.competicion}).", precoz(e, 19))
            else h("partidos_$n", "$n partidos", "Llega a los $n partidos en su carrera en FcFeed.", precoz(e, if (n <= 100) 21 else 24))
        }
        if (u.modo == Rol.JUGADOR) {
            val g0 = antes.sumOf { it.goles }; val g1 = g0 + p.goles
            GOLES.filter { it in (g0 + 1)..g1 }.forEach { n ->
                if (n == 1) h("gol_1", "Primer gol", "Su primer gol en la carrera, ante el ${p.rival}.", precoz(e, 19))
                else h("goles_$n", "$n goles", "Llega a los $n goles en su carrera en FcFeed.", precoz(e, when { n <= 25 -> 20; n <= 50 -> 21; n <= 100 -> 23; else -> 25 }))
            }
            if (p.goles >= 3) {
                val n = ps.count { it.goles >= 3 }
                val q = if (p.goles >= 4) "Póker" else "Hat-trick"
                h("hat_$n", if (n == 1) "Primer ${q.lowercase()}" else "$q número $n", "${p.goles} goles ante el ${p.rival}.", precoz(e, 20))
            }
            val racha = rachaFinal(ps) { it.goles > 0 }
            RACHA_GOL.firstOrNull { it == racha }?.let { n -> h("racha_gol_${n}_$paso", "$n partidos seguidos marcando", "Encadena $n partidos seguidos con gol.", precoz(e, 20)) }
            // Récord de goles en una temporada (el suyo)
            val esta = ps.filter { it.temporada == t }.sumOf { it.goles }
            val mejorOtra = ps.filter { it.temporada != t }.groupBy { it.temporada }.maxOfOrNull { (_, x) -> x.sumOf { it.goles } } ?: 0
            if (mejorOtra > 0 && esta > mejorOtra && esta - p.goles <= mejorOtra) h("record_temp_$t", "Récord de goles en una temporada", "$esta goles en la ${t}: supera los $mejorOtra de su mejor temporada.", precoz(e, 21))
        } else {
            val v = ps.count { it.ganado }
            listOf(1, 50, 100, 200).firstOrNull { it == v && p.ganado }?.let { n -> h("victorias_$n", if (n == 1) "Primera victoria" else "$n victorias", "Su victoria número $n en el banquillo.", precoz(e, if (n == 1) 35 else 40)) }
        }
        val invicto = rachaFinal(ps) { it.ganado || it.empatado }
        INVICTO.firstOrNull { it == invicto }?.let { n -> h("invicto_${n}_$paso", "$n partidos invicto", "Encadena $n partidos sin perder.") }
        // Primera vez en una competición grande
        if (p.competicion.isNotBlank() && NOTABLES.containsMatchIn(p.competicion) && antes.none { it.competicion.equals(p.competicion, true) })
            h("comp_" + p.competicion.lowercase().replace(Regex("[^a-z0-9]"), ""), "Debut en ${p.competicion}", "Su primer partido en ${p.competicion}, ante el ${p.rival}.", precoz(e, 19))
        // Títulos
        if (p.titulo.isNotBlank()) h("titulo_${p.titulo.lowercase().replace(Regex("[^a-z0-9]"), "")}_$t", "Campeón: ${p.titulo}", "Gana ${p.titulo} en la temporada $t.", precoz(e, 20))
        return l
    }

    /** Lo que da de fama un hito (pequeño; más si es precoz o gordo). */
    fun fama(h: Hito): Int = (if (h.precoz) 2 else 1) + (if (h.id.startsWith("titulo") || h.id.startsWith("goles_100") || h.id.startsWith("record")) 1 else 0)
}
