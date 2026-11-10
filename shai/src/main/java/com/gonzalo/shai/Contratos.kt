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

    fun clausula(n: Nivel, sueldo: Long): Long = clausulaDe(n, sueldo)
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
                clausula = o.clausula.takeIf { it > 0 } ?: clausulaDe(n, o.salario), porGol = if (u.modo == Rol.JUGADOR && n != Nivel.CANTERANO) redondea(o.salario * 0.08) else 0,
                objetivos = objetivosDe(n, u.modo), imagen = imagenDe(n))
            "cesion" -> Contrato(o.de, o.salario.takeIf { it > 0 } ?: u.contrato?.sueldo ?: u.salario, t, t, prima = o.prima, nota = "Cedido por ${u.contrato?.club ?: u.club}",
                imagen = imagenDe(n))
            "renovacion" -> (u.contrato ?: primero(n, u.club, t, u.modo)).let { c ->
                c.copy(sueldo = o.salario.takeIf { it > 0 } ?: c.sueldo, desde = t, hasta = mas(t, anios - 1), prima = o.prima, clausula = o.clausula.takeIf { it > 0 } ?: clausulaDe(n, o.salario),
                    porGol = if (c.porGol > 0 || (u.modo == Rol.JUGADOR && n != Nivel.CANTERANO)) redondea((o.salario.takeIf { it > 0 } ?: c.sueldo) * 0.08) else 0,
                    objetivos = objetivosDe(n, u.modo).ifBlank { c.objetivos }, imagen = imagenDe(n), nota = "")
            }
            else -> null
        }
    }

    /**
     * Lo que vale en el mercado ahora (lo que pediría su club por él): lo de su nivel, más si está en racha o acaba de pasar algo gordo,
     * y más cuanto más joven. Si su cláusula está muy por debajo, es una ganga ([ganga]) y los clubes lo saben.
     */
    fun valorMercado(u: Universo): Long = if (u.valor > 0) u.valor else valorBase(u)

    /**
     * Lo que vale tras un partido: sube (o baja) con lo que hace (goles, asistencias, nota, MVP), multiplicado por el escenario (la fase:
     * una final pesa el doble; un torneo de selecciones o la Champions, más) y por su edad (un chaval que rompe un Mundial se dispara).
     * Encadenado partido a partido, en un Mundial crece de forma exponencial. Nunca por debajo de un suelo de su nivel ni por encima de
     * [VALOR_MAX].
     */
    fun revalora(u: Universo, p: Partido): Long {
        val antes = valorMercado(u)
        val impacto = if (u.modo == Rol.MISTER) (if (p.ganado) 0.06 else if (p.empatado) 0.0 else -0.05)
        else {
            if (p.minutos <= 0) return antes
            0.06 * minOf(p.goles, 5) + 0.035 * minOf(p.asistencias, 5) + (if (p.mvp) 0.05 else 0.0) +
                (when { p.nota >= 8.5 -> 0.06; p.nota >= 7.5 -> 0.03; p.nota in 0.1..5.5 -> -0.05; else -> 0.0 }) + (if (p.ganado) 0.01 else if (!p.empatado) -0.02 else 0.0)
        }
        val f = Competiciones.familia(p.competicion)
        val escenario = Competiciones.pesoFase(p.fase) * when {
            f == Competiciones.Familia.TORNEO_SELECCIONES && !Competiciones.norm(p.competicion).contains("amistoso") -> 1.4
            f == Competiciones.Familia.CHAMPIONS -> 1.3
            f == Competiciones.Familia.SELECCION -> 1.2
            f == Competiciones.Familia.AMISTOSO -> 0.3
            else -> 1.0
        }
        val edad = u.personaje.edad
        val joven = when { edad <= 19 -> 1.5; edad <= 23 -> 1.2; edad <= 28 -> 1.0; else -> 0.7 }
        val factor = (1.0 + impacto * (if (impacto > 0) escenario * joven else 1.0)).coerceIn(0.85, 2.0)
        val v = (antes * factor).toLong()
        return (if (v >= 10_000_000) Math.round(v / 100_000.0) * 100_000 else Math.round(v / 10_000.0) * 10_000).coerceIn(valorBase(u) * 2 / 5, VALOR_MAX)
    }

    /**
     * Las ofertas de clubes que tiene abiertas, puestas al día con lo que vale ahora (si ha subido): el sueldo sube algo menos que su valor,
     * la prima y la cláusula lo mismo, sin pasar de lo que pide el nivel de lo que vale. Devuelve las que cambian (oferta de antes → nueva).
     */
    fun subenOfertas(u: Universo, entradas: List<Entrada>, ratio: Double): List<Pair<Oferta, Oferta>> {
        if (ratio < 1.05) return emptyList()
        val n = nivelOfertas(u)
        val ocultas = entradas.flatMap { it.ofertas }.mapNotNull { it.antes.ifBlank { null } }.toSet()
        return entradas.flatMap { it.ofertas }.filter { it.estado == Oferta.ABIERTA && it.tipo in setOf("traspaso", "renovacion", "cesion", "banquillo") && it.id !in ocultas }
            .mapNotNull { o ->
                // (el sueldo sube bastante menos que su valor; la prima y la cláusula, lo mismo)
                val sube = 1.0 + (ratio - 1.0) * 0.3
                val nueva = recortaPara(u, o.copy(id = Universo.nuevoId("o"), salario = redondea(o.salario * sube), prima = redondea(o.prima * ratio),
                    clausula = if (o.clausula > 0) redondeaCl((o.clausula * ratio).toLong()) else 0, antes = o.id, negociada = false,
                    texto = "Mejoran su oferta tras tu último partido: ahora ${Formato.euros(redondea(o.salario * sube))} a la semana. " + o.texto.substringAfter("Mejoran su oferta tras tu último partido: ").substringAfter(". ", o.texto)))
                if (nueva.salario > o.salario || nueva.prima > o.prima || nueva.clausula > o.clausula) o to nueva else null
            }
    }

    /**
     * Lo que puede cobrar a la semana un chaval, por su edad y lo que lleva jugado (por encima de 21 años, sin tope: manda su nivel).
     * Referencia de la realidad: un chaval de 18 que costó 60-70 M€ llegó cobrando unos 80.000 € a la semana; con 16-17 años, aunque valga
     * una fortuna, cobra mucho menos. Lo que se dispara es lo que pagarían por él (su valor, su cláusula), no su sueldo.
     */
    fun topeSueldo(u: Universo): Long {
        val edad = u.personaje.edad
        val base = when { edad <= 17 -> 60_000L; edad <= 19 -> 120_000L; edad <= 21 -> 250_000L; else -> return Long.MAX_VALUE }
        val jugados = Carreras.partidos(u.id).count { it.second.minutos > 0 }
        // Un fenómeno sin precedentes (vale una fortuna) cobra varios millones aunque sea un chaval: hasta el triple, según lo que vale
        val fenomeno = (valorMercado(u) / 30_000_000.0).coerceIn(1.0, 3.0)
        return redondea(base * (1.0 + minOf(jugados, 60) / 40.0) * fenomeno)
    }

    /** Es una superestrella: vale 100 M€ o más, o el triple de lo que vale alguien de su nivel. */
    fun superestrella(u: Universo): Boolean = u.valor >= 100_000_000 || (u.valor > 0 && u.valor >= valorBase(u) * 3)

    /** La apuesta que tiene abierta (si la hay): la de un club que paga muy por encima de lo normal. */
    fun apuesta(u: Universo): Oferta? = Carreras.entradasDe(u.id).flatMap { it.ofertas }.firstOrNull { it.apuesta && it.estado == Oferta.ABIERTA }

    /** Lo que puede pagar una apuesta: hasta el triple de su tope de sueldo (y el nivel de una leyenda). */
    fun topeApuesta(u: Universo): Long = topeSueldo(u).let { if (it == Long.MAX_VALUE) it else it * 3 }

    /**
     * El tope de una oferta normal: el de su edad y, si hay una apuesta abierta, los demás pueden acercarse a ella (hasta el 75 %) para
     * no quedarse atrás. Nunca la igualan: la apuesta es la excepción.
     */
    fun topeNormal(u: Universo): Long {
        val t = topeSueldo(u)
        val a = apuesta(u) ?: return t
        return if (t == Long.MAX_VALUE) t else maxOf(t, a.salario * 3 / 4)
    }

    /**
     * Una oferta de club, en su sitio: el nivel con el que se le hacen ofertas y lo que puede cobrar por su edad. Una apuesta solo vale si
     * es una superestrella y no tiene otra abierta (si no, es una oferta normal); entonces puede llegar al triple de su tope.
     */
    fun recortaPara(u: Universo, o0: Oferta): Oferta {
        val otra = apuesta(u)
        val o = if (o0.apuesta && (!superestrella(u) || o0.tipo == "patrocinio" || (otra != null && otra.id != o0.id && otra.id != o0.antes))) o0.copy(apuesta = false) else o0
        val tope = if (o.apuesta) topeApuesta(u) else topeNormal(u)
        return Rangos.recorta(o, nivelOfertas(u), tope).let { r ->
            // (la apuesta, y las que se acercan a ella, pueden pasar del máximo de su nivel: hasta lo que pidan, sin pasar de su tope)
            if (o.tipo != "patrocinio" && (o.apuesta || otra != null)) maxOf(r.salario, minOf(o.salario, tope)).let { sal -> r.copy(salario = sal, texto = Rangos.cuadraTexto(o.texto, sal, r.prima)) } else r
        }.let { x ->
        // Un club que le ficha o le renueva le blinda: su cláusula, nunca por debajo de 1,5 veces lo que vale (y como mucho 1.000 M€)
        if (x.tipo == "traspaso" || x.tipo == "renovacion" || x.tipo == "banquillo")
            x.copy(clausula = maxOf(x.clausula, clausulaMinima(u)).coerceAtMost(1_000_000_000L)) else x
        }
    }

    /** La cláusula mínima con la que un club le ficha o le renueva: 1,5 veces lo que vale. */
    fun clausulaMinima(u: Universo): Long = redondeaCl(valorMercado(u) * 3 / 2)

    /** El tope de lo que puede valer (por encima del fichaje más caro de la historia, para que haya sitio). */
    const val VALOR_MAX = 350_000_000L

    /** El nivel que pide el mercado por lo que vale (si vale más de lo que dice su fama, las ofertas van por lo que vale). */
    fun nivelPorValor(valor: Long): Nivel = when {
        valor >= 150_000_000 -> Nivel.LEYENDA; valor >= 60_000_000 -> Nivel.ESTRELLA; valor >= 20_000_000 -> Nivel.TITULAR; valor >= 5_000_000 -> Nivel.PROMESA; else -> Nivel.CANTERANO
    }
    /**
     * El nivel con el que se le hacen las ofertas: el de su fama, y uno más por cada vez que lo que vale dobla lo de su nivel (un chaval
     * que vale cuatro veces lo de un titular recibe ofertas de estrella).
     */
    fun nivelOfertas(u: Universo): Nivel {
        if (u.valor <= 0) return u.nivelActual
        val veces = u.valor.toDouble() / maxOf(1L, valorBase(u))
        // (como mucho uno: lo que vale dispara lo que pagarían por él, no su categoría de golpe)
        val pasos = if (veces < 2) 0 else 1
        return Nivel.entries[(u.nivelActual.ordinal + pasos).coerceAtMost(Nivel.entries.size - 1)]
    }

    private fun valorBase(u: Universo): Long {
        val n = u.nivelActual
        val base = clausulaDe(n, u.contrato?.sueldo ?: u.salario) / 2
        val edad = u.personaje.edad
        val joven = when { edad <= 18 -> 1.5; edad <= 21 -> 1.25; edad <= 27 -> 1.0; edad <= 30 -> 0.7; else -> 0.4 }
        return redondea(base * joven)
    }

    /** Su cláusula es una ganga: está por debajo de la mitad de lo que vale (con un mínimo de nivel: un canterano normal no lo es). */
    fun ganga(u: Universo): Boolean {
        val c = u.contrato ?: return false
        if (c.clausula <= 0 || u.nivelActual == Nivel.CANTERANO) return false
        return c.clausula < valorMercado(u) / 2
    }

    /** Lo que se cobra en una jornada: el sueldo del contrato, los patrocinios y, si marcó, la prima por gol. */
    fun cobra(u: Universo, p: Partido?): Long = sinComision(u, (u.contrato?.sueldo ?: 0) + u.patrocinio + (if (p != null && !Competiciones.deSelecciones(p.competicion)) (u.contrato?.porGol ?: 0) * p.goles else 0))

    /** Lo que le llega después de que su agente se quede su parte ([Universo.comision]). */
    fun sinComision(u: Universo, bruto: Long): Long = if (u.comision <= 0 || u.suAgente == null) bruto else bruto - bruto * u.comision / 100

    private fun redondeaCl(x: Long): Long = if (x >= 10_000_000) Math.round(x / 5_000_000.0) * 5_000_000 else Math.round(x / 500_000.0) * 500_000
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
            ganga(u) -> "tu cláusula (${Formato.eurosCorto(c.clausula)}) es una ganga para lo que vales ahora y el club quiere blindarte"
            else -> null
        }
    }

    /**
     * Lo que le ofrece el club al renovar, con lógica: según su nivel de ahora y cómo le va (la nota media y los goles de la temporada, o
     * las victorias de míster). Si le va mal, puede ser una rebaja. Siempre dentro de [Rangos].
     */
    fun propuesta(u: Universo, ps: List<Partido>): Oferta {
        val c = u.contrato ?: primero(u.nivelActual, u.club, u.temporada, u.modo)
        val n = nivelOfertas(u)
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
        // La cláusula nueva: la de su nivel (y si la de ahora es una ganga, el club la sube para blindarle)
        val cl = maxOf(clausulaDe(n, sueldo), if (ganga(u)) valorMercado(u) * 3 / 2 else 0L)
        return recortaPara(u, Oferta(Universo.nuevoId("o"), u.club, "renovacion", sueldo, redondea(sueldo * if (forma > 0) 8.0 else 3.0), anios,
            texto.replace("y una cláusula nueva", "y una cláusula de ${Formato.eurosCorto(redondeaCl(cl))}") + (c.clausula.takeIf { it > 0 }?.let { " Tu cláusula ahora: ${Formato.eurosCorto(it)}." } ?: ""),
            clausula = redondeaCl(cl)))
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
            recortaPara(u, o.copy(id = Universo.nuevoId("o"), salario = redondea(o.salario * f), texto = "Contraoferta aceptada: el ${o.de} sube a ${Formato.euros(redondea(o.salario * f))} a la semana. «Es nuestra última palabra», dicen.")) to true
        } else o.copy(id = Universo.nuevoId("o"), texto = "El ${o.de} no se mueve: «Esto es lo que hay». O lo tomas o lo dejas.") to false
    }
}
