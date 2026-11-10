package com.gonzalo.shai

import kotlin.random.Random

/**
 * Las pujas (solas, sin botón): entre jornadas, si tienes abiertas dos o más ofertas del mismo tipo (dos marcas que te quieren de imagen,
 * dos clubes que te quieren fichar), la que va por detrás mueve ficha: mejora la suya (más sueldo, más prima o un año más), se mantiene
 * o se retira. Tú solo eliges. Como mucho [MAX_RONDAS] rondas por oferta y nunca por encima de lo que pide tu nivel ([Rangos]). Sin IA:
 * no cuesta nada y sale también en el modo prueba.
 */
object Pujas {
    const val MAX_RONDAS = 3
    private val TIPOS = setOf("patrocinio", "traspaso", "cesion")

    /** Las que compiten: abiertas, de un tipo que puja, agrupadas por tipo, con al menos dos de quien las hace distinto. */
    fun grupos(entradas: List<Entrada>): List<List<Pair<Entrada, Oferta>>> {
        val ocultas = entradas.flatMap { it.ofertas }.mapNotNull { it.antes.ifBlank { null } }.toSet()
        return entradas.flatMap { e -> e.ofertas.map { e to it } }
            .filter { (_, o) -> o.estado == Oferta.ABIERTA && o.tipo in TIPOS && o.id !in ocultas }
            .groupBy { it.second.tipo }.values
            .map { g -> g.distinctBy { Rangos.limpiaNombre(it.second.de).lowercase() } }
            .filter { it.size >= 2 }
    }

    /**
     * La puja de esta jornada (o null si no hay o nadie se mueve): una entrada «Puja» que sale entre jornadas ([visible]) con la oferta
     * nueva de quien mejora o la retirada de quien se va. Las de antes cambian al salir ([aplica]).
     */
    fun calcula(u: Universo, entradas: List<Entrada>, ahora: Long, visible: Long, azar: Random = Random(u.id.hashCode() * 31 + u.paso)): Entrada? {
        val g = grupos(entradas).firstOrNull() ?: return null
        val ofertas = g.map { it.second }.sortedByDescending { it.valor }
        val lider = ofertas.first()
        val r = Rangos.de(u.nivelActual)
        // (en un club, lo de su edad; si hay una apuesta, pueden acercarse a ella, pero la apuesta no puja: ya es la excepción)
        val topeSemana = if (lider.tipo == "patrocinio") r.patroSemana else minOf(r.sueldo.last.let { if (Contratos.apuesta(u) != null) maxOf(it, Contratos.topeNormal(u)) else it }, Contratos.topeNormal(u))
        val topePrima = if (lider.tipo == "patrocinio") r.patroPrima else r.primaMax
        // Se mueve la que va por detrás (la segunda); si ya ha pujado todas sus rondas, nadie
        val otra = ofertas.drop(1).firstOrNull { it.ronda < MAX_RONDAS && !it.apuesta } ?: return null
        val quien = Rangos.limpiaNombre(otra.de); val delLider = Rangos.limpiaNombre(lider.de)
        val tiro = azar.nextDouble()
        val enElTope = otra.salario >= topeSemana && otra.prima >= topePrima
        return when {
            // Se retira (más fácil cuanto más ha pujado ya o si ya no puede subir)
            tiro < 0.18 + 0.12 * otra.ronda || enElTope -> {
                val ret = otra.copy(id = Universo.nuevoId("o"), estado = Oferta.RETIRADA, antes = otra.id, ronda = otra.ronda + 1,
                    texto = elige(azar, RETIRADAS).replace("{Q}", quien).replace("{L}", delLider))
                Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.PUJA, ahora, u.paso, "$quien se retira de la puja",
                    "$quien no sigue: ${ret.texto} $delLider se queda sola y lo sabe.", ofertas = listOf(ret), visibleDesde = visible, aplicada = false)
            }
            // Se mantiene (esta jornada, nada)
            tiro < 0.38 + 0.08 * otra.ronda -> null
            // Mejora: pasa al líder (o se acerca), con sueldo, prima o años
            else -> {
                val sube = 1.06 + azar.nextDouble() * 0.16
                val semana = redondea(maxOf(otra.salario.toDouble(), lider.salario * sube)).coerceAtMost(topeSemana)
                val prima = redondea(maxOf(otra.prima.toDouble(), lider.prima * (1.0 + azar.nextDouble() * 0.2))).coerceAtMost(topePrima)
                val anios = if (azar.nextDouble() < 0.25 && otra.anios in 1..4) otra.anios + 1 else otra.anios
                if (semana <= otra.salario && prima <= otra.prima && anios == otra.anios) return null
                val cambios = buildList {
                    if (semana > otra.salario) add("${Formato.euros(semana)} a la semana")
                    if (prima > otra.prima) add("${Formato.euros(prima)} de prima")
                    if (anios > otra.anios) add("un año más")
                }
                val nueva = otra.copy(id = Universo.nuevoId("o"), salario = semana, prima = prima, anios = anios, antes = otra.id, ronda = otra.ronda + 1, estado = Oferta.ABIERTA,
                    texto = "Mejora su oferta tras saber lo de $delLider: ${cambios.joinToString(", ").replaceFirstChar { it.lowercase() }}. " + elige(azar, FRASES).replace("{Q}", quien))
                val titulo = elige(azar, TITULOS).replace("{Q}", quien).replace("{L}", delLider)
                Entrada(Universo.nuevoId("e"), u.id, TipoEntrada.PUJA, ahora, u.paso, titulo,
                    "Al enterarse de la oferta de $delLider, $quien ha mejorado la suya sin que nadie se lo pida: ${cambios.joinToString(", ")}. " +
                        (if (nueva.valor > lider.valor) "Ahora es la mejor sobre la mesa." else "Aún no llega a la de $delLider, pero se acerca."),
                    ofertas = listOf(nueva), visibleDesde = visible, aplicada = false)
            }
        }
    }

    /** Al salir la puja: la oferta de antes queda superada (la nueva ocupa su sitio) o retirada. Si ya la habías contestado, la puja no cuenta. */
    fun aplica(e: Entrada, todas: List<Entrada>): Pair<Entrada, Entrada?>? {
        if (e.tipo != TipoEntrada.PUJA) return null
        val o = e.ofertas.firstOrNull { it.antes.isNotBlank() } ?: return null
        val vieja = todas.firstOrNull { x -> x.ofertas.any { it.id == o.antes } } ?: return null
        val antes = vieja.ofertas.first { it.id == o.antes }
        if (antes.estado != Oferta.ABIERTA) return e.copy(ofertas = e.ofertas.map { if (it.estado == Oferta.ABIERTA) it.copy(estado = Oferta.MEJORADA) else it }) to null
        return e to vieja.copy(ofertas = vieja.ofertas.map { if (it.id == o.antes) it.copy(estado = Oferta.MEJORADA) else it })
    }

    private fun redondea(x: Double): Long = when {
        x >= 1_000_000 -> Math.round(x / 50_000) * 50_000
        x >= 100_000 -> Math.round(x / 5_000) * 5_000
        x >= 10_000 -> Math.round(x / 500) * 500
        else -> Math.round(x / 100) * 100
    }
    private fun elige(azar: Random, l: List<String>) = l[azar.nextInt(l.size)]

    private val TITULOS = listOf("{Q} contraataca", "{Q} sube la apuesta", "{Q} no se rinde", "Puja: {Q} responde a {L}", "{Q} mueve ficha")
    private val FRASES = listOf("«No vamos a perderle por dinero», dicen en {Q}.", "En {Q} lo tienen claro: le quieren a él.",
        "«Es nuestra prioridad», repiten en {Q}.", "En {Q} saben que hay otra puja y no quieren quedarse atrás.", "«Que lo piense, pero que no tarde», avisan desde {Q}.")
    private val RETIRADAS = listOf("«No vamos a entrar en una guerra», dicen en {Q}.", "En {Q} creen que el precio ya no tiene sentido.",
        "«Le deseamos lo mejor, pero hasta aquí», explica {Q}.", "{Q} ha encontrado otra opción y retira su oferta.")
}
