package com.gonzalo.shai

import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import java.text.Normalizer

/**
 * Las competiciones de una carrera: la lista del desplegable (las que juega su club según la búsqueda web, las que haya añadido él,
 * «Amistoso» y «Selección») y cómo se agrupan los nombres que se metieron a mano («LaLiga», «La Liga», «liga», «LaLiga EA Sports» → una
 * sola; «amistoso», «amistosos», «pretemporada» → «Amistoso»). Así las cifras, los récords y lo que se le cuenta a Claude van por
 * competición de verdad y no se juntan ni se parten.
 *
 * Primero, reglas (sin gastar nada); lo que no encaja por reglas lo empareja Haiku una sola vez por nombre ([Motor.normalizaCompeticiones]);
 * lo que sigue sin encajar va a «Otra». Él puede corregir la competición de cualquier partido ya guardado.
 */
object Competiciones {
    const val AMISTOSO = "Amistoso"
    const val SELECCION = "Selección"
    const val OTRA = "Otra"

    /** Qué tipo de competición es un nombre (para emparejar variantes). */
    enum class Familia { AMISTOSO, SELECCION, MUNDIAL_CLUBES, CONFERENCE, EUROPA, CHAMPIONS, SUPERCOPA, COPA, LIGA }

    /** «LaLiga EA Sports» → «laliga ea sports»; sin tildes, sin signos. */
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9ñ]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun hay(n: String, vararg claves: String): Boolean {
        val palabras = n.split(' ').toSet()
        return claves.any { c -> if (' ' in c || c.length > 4) n.contains(c) else c in palabras }
    }

    fun familia(s: String): Familia? {
        val n = norm(s)
        if (n.isBlank()) return null
        return when {
            hay(n, "amistos", "pretemporada", "friendly", "friendlies", "trofeo", "gira de") -> Familia.AMISTOSO
            n in setOf("seleccion", "seleccion nacional", "internacional", "partido internacional", "con la seleccion", "selecciones") || n.startsWith("seleccion ") -> Familia.SELECCION
            hay(n, "mundial de clubes", "club world cup", "intercontinental") -> Familia.MUNDIAL_CLUBES
            hay(n, "conference", "uecl") -> Familia.CONFERENCE
            hay(n, "europa league", "liga europa", "uel", "uefa europa", "copa de la uefa", "copa uefa") -> Familia.EUROPA
            hay(n, "champions", "liga de campeones", "ucl", "copa de europa") -> Familia.CHAMPIONS
            hay(n, "supercopa", "super cup", "supercup", "supercoppa", "supercoupe", "community shield", "trophee des champions", "supertaca", "supercopa de espana") -> Familia.SUPERCOPA
            hay(n, "copa", "cup", "coppa", "pokal", "coupe", "taca") -> Familia.COPA
            hay(n, "liga", "league", "serie a", "serie b", "bundesliga", "ligue", "premier", "eredivisie", "primeira", "championship", "laliga", "segunda", "primera",
                "hypermotion", "division", "liga mx", "mls", "brasileirao", "superliga", "super lig") -> Familia.LIGA
            else -> null
        }
    }

    /** Una competición «bonita» a partir de lo que escribió él («liga» → «Liga»). */
    fun bonita(s: String): String = s.trim().replace(Regex("\\s+"), " ").replaceFirstChar { it.uppercase() }

    /**
     * La lista del desplegable: las de su club ([Universo.comps], si son del club de ahora; si no, las de la identificación del club), las
     * que ya ha usado agrupadas por tipo (la más usada de cada una), y siempre «Amistoso» y «Selección» al final.
     */
    fun lista(u: Universo, partidos: List<Partido> = emptyList()): List<String> {
        val delClub = (if (u.comps.isNotEmpty() && (u.compsDe.isBlank() || Vida.mismo(u.compsDe, u.club))) u.comps
            else u.clubInfo?.takeIf { it.es(u.club) }?.competiciones.orEmpty())
        val propias = u.comps.filter { c -> delClub.none { norm(it) == norm(c) } && c !in setOf(AMISTOSO, SELECCION, OTRA) }
        val base = (delClub + propias).filter { it.isNotBlank() && it !in setOf(AMISTOSO, SELECCION, OTRA) }.distinctBy { norm(it) }
        // Las que ya ha usado y no encajan en la base: agrupadas por tipo, la forma más usada
        val usadas = partidos.map { it.competicion.trim() }.filter { it.isNotBlank() && it != OTRA }
        val extra = usadas.filter { canonica(it, base, u.compAlias) == null && familia(it) != Familia.AMISTOSO && familia(it) != Familia.SELECCION }
            .groupBy { familia(it)?.name ?: norm(it) }.values.map { g -> g.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key }
        return (base + extra).distinctBy { norm(it) } + AMISTOSO + SELECCION
    }

    /**
     * La de la lista que es [raw] (o null si no se sabe): un alias ya decidido, el mismo nombre, el mismo tipo (si en la lista solo hay
     * una de ese tipo, o la que más palabras comparte), o una que contiene a la otra.
     */
    fun canonica(raw: String, lista: List<String>, alias: Map<String, String> = emptyMap()): String? {
        val n = norm(raw)
        if (n.isBlank()) return null
        alias[n]?.let { a -> return lista.firstOrNull { norm(it) == norm(a) } ?: a }
        lista.firstOrNull { norm(it) == n }?.let { return it }
        val f = familia(raw)
        if (f == Familia.AMISTOSO) return AMISTOSO
        if (f == Familia.SELECCION) return SELECCION
        if (f != null) {
            val mismas = lista.filter { familia(it) == f }
            if (mismas.size == 1) return mismas.first()
            if (mismas.size > 1) {
                val ps = n.split(' ').filter { it.length >= 3 }.toSet()
                mismas.maxByOrNull { c -> norm(c).split(' ').count { it in ps } }?.takeIf { c -> norm(c).split(' ').any { it in ps } }?.let { return it }
            }
        }
        if (n.length >= 4) lista.firstOrNull { val c = norm(it); c.length >= 4 && (c.contains(n) || n.contains(c)) }?.let { return it }
        return null
    }

    /** ¿Se puede agrupar sin preguntar a nadie? (si es de un tipo conocido, aunque no esté en la lista, va con su nombre). */
    fun sinPreguntar(raw: String, lista: List<String>, alias: Map<String, String>): String? =
        canonica(raw, lista, alias) ?: familia(raw)?.let { bonita(raw) }

    // ---------------------------------------------------------------- Haiku: lo que no encaja por reglas

    val ESQUEMA_AGRUPA: Map<String, Any?> = mapOf("type" to "object", "properties" to mapOf(
        "grupos" to mapOf("type" to "array", "items" to mapOf("type" to "object", "properties" to mapOf("nombre" to mapOf("type" to "string"), "competicion" to mapOf("type" to "string")),
            "required" to listOf("nombre", "competicion"), "additionalProperties" to false))),
        "required" to listOf("grupos"), "additionalProperties" to false)

    /** La petición (Haiku, barata) para emparejar nombres sueltos con la lista. */
    fun peticionAgrupa(club: String, lista: List<String>, sueltos: List<String>) = Peticion(Claude.HAIKU,
        listOf(Peticion.Bloque("Agrupas nombres de competiciones de fútbol que alguien ha escrito a mano (con abreviaturas, faltas o el nombre del videojuego EA Sports FC) con la competición oficial que les corresponde de una lista. Si un nombre no es ninguna de la lista, «Otra». No inventes competiciones.")),
        listOf(Peticion.texto("Club: $club\nLa lista: ${lista.joinToString(" | ")}\nLos nombres que hay que agrupar: ${sueltos.joinToString(" | ")}\n\nEn «grupos», por cada nombre (tal cual), la competición de la lista que es (escrita EXACTAMENTE como en la lista) u «Otra».")),
        600, ESQUEMA_AGRUPA)

    /** Lo que contesta: nombre suelto → la de la lista (o «Otra»). */
    fun leeAgrupa(r: Map<String, Any?>, lista: List<String>): Map<String, String> =
        r["grupos"].comoLista().map { it.comoMapa() }.mapNotNull { g ->
            val n = g["nombre"].comoTexto().trim(); val c = g["competicion"].comoTexto().trim()
            if (n.isBlank()) null else norm(n) to (lista.firstOrNull { norm(it) == norm(c) } ?: OTRA)
        }.toMap()
}
