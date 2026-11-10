package com.gonzalo.shai

import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoTexto

/**
 * Tu contrato, el de FcFeed (no el de FC): lo pone la app al crear la carrera según tu nivel y tu club, y lo cambia cuando renuevas, fichas por
 * otro club o te quedas libre. De él sale lo que cobras cada jornada. [hasta] es la última temporada que cubre («2028/29»).
 */
data class Contrato(
    val club: String,
    /** Euros a la semana (una jornada = una semana). */
    val sueldo: Long,
    val desde: String,
    val hasta: String,
    /** La prima de fichaje o de renovación (euros, de una vez). */
    val prima: Long = 0,
    /** La cláusula de rescisión (euros). */
    val clausula: Long = 0,
    /** Lo que cobras de más por cada gol (euros; jugador). */
    val porGol: Long = 0,
    /** Los objetivos (míster: los de la directiva; jugador: si los hay). */
    val objetivos: String = "",
    /** Qué parte de tus derechos de imagen se queda el club. */
    val imagen: String = "",
    /** «cesión», si lo es. */
    val nota: String = "",
) {
    fun aMapa(): Map<String, Any?> = mapOf("cl" to club, "su" to sueldo, "de" to desde, "ha" to hasta, "pr" to prima, "cu" to clausula, "pg" to porGol,
        "ob" to objetivos, "im" to imagen, "no" to nota)
    companion object {
        fun de(m: Map<String, Any?>): Contrato? = if (m.isEmpty() || m["cl"].comoTexto().isBlank()) null else Contrato(
            m["cl"].comoTexto(), m["su"].comoLong(), m["de"].comoTexto(), m["ha"].comoTexto(), m["pr"].comoLong(), m["cu"].comoLong(), m["pg"].comoLong(),
            m["ob"].comoTexto(), m["im"].comoTexto(), m["no"].comoTexto())
    }
}

object Contratos {
    /** El primer año de una temporada («2026/27» → 2026). */
    fun anio(t: String): Int? = Regex("""^(\d{4})""").find(t.trim())?.groupValues?.get(1)?.toInt()

    /** «2026/27» + 2 → «2028/29». */
    fun mas(t: String, n: Int): String {
        var x = t.ifBlank { "2026/27" }
        repeat(n) { x = Motor.siguienteTemporada(x) }
        return x
    }

    /** Cuántas temporadas le quedan, contando la de ahora (1 = esta es la última; 0 = ya ha acabado). */
    fun quedan(c: Contrato, temporada: String): Int {
        val h = anio(c.hasta) ?: return 1
        val a = anio(temporada) ?: return 1
        return (h - a + 1).coerceAtLeast(0)
    }

    private fun clausulaDe(n: Nivel, sueldo: Long): Long = when (n) {
        Nivel.CANTERANO -> 5_000_000L; Nivel.PROMESA -> 30_000_000L; Nivel.TITULAR -> 80_000_000L; Nivel.ESTRELLA -> 200_000_000L; Nivel.LEYENDA -> 500_000_000L
    }.coerceAtLeast(sueldo * 52 * 4)

    private fun imagenDe(n: Nivel) = when (n) {
        Nivel.CANTERANO, Nivel.PROMESA -> "El club se queda el 50 % de tus derechos de imagen."
        Nivel.TITULAR -> "El club se queda el 30 % de tus derechos de imagen."
        else -> "Tus derechos de imagen son tuyos; el club se queda el 15 % de lo que cobres por campañas con su camiseta."
    }

    private fun objetivosDe(n: Nivel, modo: Rol) = if (modo == Rol.MISTER) when (n) {
        Nivel.CANTERANO -> "La directiva pide salvar la categoría y subir a dos chavales de la cantera."
        Nivel.PROMESA -> "La directiva pide la permanencia sin sufrir y algún susto en la Copa."
        Nivel.TITULAR -> "La directiva pide pelear por Europa."
        Nivel.ESTRELLA -> "La directiva pide pelear la liga y llegar lejos en Europa."
        Nivel.LEYENDA -> "La directiva pide ganarlo todo. Todo."
    } else ""

    /** Su primer contrato con su club, según su nivel (dentro de los rangos de [Rangos]). */
    fun primero(n: Nivel, club: String, temporada: String, modo: Rol): Contrato {
        val r = Rangos.de(n).sueldo
        val sueldo = redondea(r.first + (r.last - r.first) * when (n) { Nivel.CANTERANO -> 0.15; Nivel.PROMESA -> 0.3; else -> 0.4 })
        val anios = when (n) { Nivel.CANTERANO -> 3; Nivel.PROMESA -> 4; Nivel.TITULAR -> 4; Nivel.ESTRELLA -> 5; Nivel.LEYENDA -> 3 }
        return Contrato(club, sueldo, temporada, mas(temporada, anios - 1), prima = redondea(sueldo * when (n) { Nivel.CANTERANO -> 4.0; else -> 12.0 }),
            clausula = clausulaDe(n, sueldo), porGol = if (modo == Rol.JUGADOR && n != Nivel.CANTERANO) redondea(sueldo * 0.08) else 0,
            objetivos = objetivosDe(n, modo), imagen = imagenDe(n))
    }

    /** Lo que había antes de que existieran los contratos (el sueldo y el texto libre «3 años»), pasado a contrato. */
    fun migra(u: Universo): Contrato? {
        if (u.salario <= 0 || u.club.isBlank()) return null
        val anios = Regex("""(\d+)""").find(u.contratoHasta)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 8) ?: 3
        val n = u.nivelActual
        return Contrato(u.club, u.salario, u.temporada, mas(u.temporada, anios - 1), clausula = clausulaDe(n, u.salario), imagen = imagenDe(n))
    }

    /** El contrato que trae una oferta aceptada (traspaso, cesión, banquillo o renovación). Un patrocinio no cambia el contrato. */
    fun deOferta(u: Universo, o: Oferta): Contrato? {
        val n = u.nivelActual
        val t = u.temporada.ifBlank { "2026/27" }
        val anios = o.anios.coerceIn(1, 8)
        return when (o.tipo) {
            "traspaso", "banquillo" -> Contrato(o.de, o.salario.takeIf { it > 0 } ?: u.contrato?.sueldo ?: u.salario, t, mas(t, anios - 1), prima = o.prima,
                clausula = clausulaDe(n, o.salario), porGol = if (u.modo == Rol.JUGADOR && n != Nivel.CANTERANO) redondea(o.salario * 0.08) else 0,
                objetivos = objetivosDe(n, u.modo), imagen = imagenDe(n))
            "cesion" -> Contrato(o.de, o.salario.takeIf { it > 0 } ?: u.contrato?.sueldo ?: u.salario, t, t, prima = o.prima, nota = "Cedido por ${u.contrato?.club ?: u.club}",
                imagen = imagenDe(n))
            "renovacion" -> (u.contrato ?: primero(n, u.club, t, u.modo)).let { c ->
                c.copy(sueldo = o.salario.takeIf { it > 0 } ?: c.sueldo, desde = t, hasta = mas(t, anios - 1), prima = o.prima, clausula = clausulaDe(n, o.salario),
                    porGol = if (c.porGol > 0 || (u.modo == Rol.JUGADOR && n != Nivel.CANTERANO)) redondea((o.salario.takeIf { it > 0 } ?: c.sueldo) * 0.08) else 0,
                    objetivos = objetivosDe(n, u.modo).ifBlank { c.objetivos }, imagen = imagenDe(n), nota = "")
            }
            else -> null
        }
    }

    /** Lo que se cobra en una jornada: el sueldo del contrato, los patrocinios y, si marcó, la prima por gol. */
    fun cobra(u: Universo, p: Partido?): Long = sinComision(u, (u.contrato?.sueldo ?: 0) + u.patrocinio + (if (p != null && !Competiciones.deSelecciones(p.competicion)) (u.contrato?.porGol ?: 0) * p.goles else 0))

    /** Lo que le llega después de que su agente se quede su parte ([Universo.comision]). */
    fun sinComision(u: Universo, bruto: Long): Long = if (u.comision <= 0 || u.suAgente == null) bruto else bruto - bruto * u.comision / 100

    private fun redondea(x: Double): Long = when {
        x >= 100_000 -> Math.round(x / 5_000) * 5_000
        x >= 10_000 -> Math.round(x / 500) * 500
        else -> Math.round(x / 100) * 100
    }
    private fun redondea(x: Long) = redondea(x.toDouble())

    // ---------------------------------------------------------------- las renovaciones

    /** Por qué toca hablar de renovar (o null si no toca): al acercarse el final, o porque su nivel ya está muy por encima de lo que cobra. */
    fun motivoRenovacion(u: Universo, partidosTemporada: Int): String? {
        val c = u.contrato ?: return null
        if (c.nota.isNotBlank()) return null  // (una cesión no se renueva)
        if (u.paso - u.renovacionPaso < 8 && u.renovacionPaso > 0) return null
        val q = quedan(c, u.temporada)
        return when {
            q <= 1 -> "te queda esta temporada de contrato"
            q == 2 && partidosTemporada >= 19 -> "te queda temporada y media de contrato"
            c.sueldo * 13 < Rangos.de(u.nivelActual).sueldo.first * 10 -> "tu nivel ya está muy por encima de lo que cobras"
            else -> null
        }
    }

    /**
     * Lo que le ofrece el club al renovar, con lógica: según su nivel de ahora y cómo le va (la nota media y los goles de la temporada, o
     * las victorias de míster). Si le va mal, puede ser una rebaja. Siempre dentro de [Rangos].
     */
    fun propuesta(u: Universo, ps: List<Partido>): Oferta {
        val c = u.contrato ?: primero(u.nivelActual, u.club, u.temporada, u.modo)
        val n = u.nivelActual
        val r = Rangos.de(n).sueldo
        val l = Estadisticas.linea(u.temporada, "", ps)
        val forma = when {
            u.modo == Rol.MISTER -> if (l.jugados == 0) 0.0 else (l.victorias - l.derrotas).toDouble() / l.jugados
            l.notaMedia > 0 -> (l.notaMedia - 6.5) / 1.5
            else -> 0.0
        }.coerceIn(-1.0, 1.0)
        val objetivo = r.first + (r.last - r.first) * (0.35 + 0.35 * forma).coerceIn(0.05, 0.9)
        val sueldo = redondea(maxOf(c.sueldo * (0.9 + 0.1 * forma), objetivo).let { if (forma < -0.4) c.sueldo * 0.9 else it }).coerceIn(r.first, r.last)
        val anios = if (u.personaje.edad >= 31) 2 else if (forma > 0.3) 5 else 3
        val mejora = sueldo - c.sueldo
        val texto = when {
            mejora > 0 -> "El ${u.club} quiere renovarte: ${Formato.euros(sueldo)} a la semana (${Formato.euros(c.sueldo)} ahora), $anios temporadas y una cláusula nueva. Es una mejora de ${Formato.euros(mejora)} a la semana."
            mejora < 0 -> "El ${u.club} te ofrece renovar, pero con rebaja: ${Formato.euros(sueldo)} a la semana (${Formato.euros(c.sueldo)} ahora) y $anios temporadas. Dicen que «hay que ajustarse a lo que está dando»."
            else -> "El ${u.club} te ofrece renovar en las mismas condiciones: ${Formato.euros(sueldo)} a la semana y $anios temporadas más."
        }
        return Rangos.recorta(Oferta(Universo.nuevoId("o"), u.club, "renovacion", sueldo, redondea(sueldo * if (forma > 0) 8.0 else 3.0), anios, texto), n)
    }

    /**
     * Pedir más a una renovación: el club acepta subir (entre un 10 y un 20 %) si le va bien y la afición le quiere, o se planta. Siempre
     * igual para la misma oferta (no hay que darle diez veces al botón).
     */
    fun contraoferta(u: Universo, o: Oferta): Pair<Oferta, Boolean> {
        val rnd = java.util.Random(o.id.hashCode().toLong())
        val ganas = u.valorFuera(Fuera.AFICION) / 100.0 * 0.5 + u.valorFuera(Fuera.FAMA) / 100.0 * 0.3 + rnd.nextDouble() * 0.4
        val sube = ganas > 0.55
        return if (sube) {
            val f = 1.10 + rnd.nextDouble() * 0.10
            Rangos.recorta(o.copy(id = Universo.nuevoId("o"), salario = redondea(o.salario * f), texto = "Contraoferta aceptada: el ${o.de} sube a ${Formato.euros(redondea(o.salario * f))} a la semana. «Es nuestra última palabra», dicen."), u.nivelActual) to true
        } else o.copy(id = Universo.nuevoId("o"), texto = "El ${o.de} no se mueve: «Esto es lo que hay». O lo tomas o lo dejas.") to false
    }
}
