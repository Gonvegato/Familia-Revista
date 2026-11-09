package com.gonzalo.shai

import kotlin.math.abs
import kotlin.math.roundToLong

/** Las cifras de una temporada en una competición (o en todas: [competicion] vacía). */
data class Linea(
    val temporada: String, val competicion: String, val jugados: Int, val victorias: Int, val empates: Int, val derrotas: Int,
    val golesFavor: Int, val golesContra: Int, val minutos: Int, val goles: Int, val asistencias: Int, val notaMedia: Double,
    val mvps: Int = 0,
)

object Estadisticas {
    fun linea(temporada: String, competicion: String, ps: List<Partido>): Linea {
        val conNota = ps.filter { it.nota > 0 }
        return Linea(temporada, competicion, ps.size, ps.count { it.ganado }, ps.count { it.empatado }, ps.count { !it.ganado && !it.empatado },
            ps.sumOf { it.golesFavor }, ps.sumOf { it.golesContra }, ps.sumOf { it.minutos }, ps.sumOf { it.goles }, ps.sumOf { it.asistencias },
            if (conNota.isEmpty()) 0.0 else conNota.sumOf { it.nota } / conNota.size, ps.count { it.mvp })
    }

    /** Por temporada (la más nueva primero): el total y cada competición. */
    fun porTemporada(ps: List<Partido>): List<Pair<Linea, List<Linea>>> =
        ps.groupBy { it.temporada.ifBlank { "—" } }.entries.reversed().map { (t, l) ->
            linea(t, "", l) to l.groupBy { it.competicion.ifBlank { "—" } }.map { (c, x) -> linea(t, c, x) }.sortedByDescending { it.jugados }
        }

    /** La racha de los últimos partidos («VVEDV»), del más viejo al más nuevo. */
    fun racha(ps: List<Partido>, n: Int = 5): String = ps.takeLast(n).joinToString("") { it.letra }

    fun total(ps: List<Partido>) = linea("", "", ps)
}

/** Lo que se puede comprar: acciones de empresas inventadas, casas, coches y negocios (todo inventado: ni marcas ni empresas reales). */
object Mercado {
    /** símbolo, nombre, precio de salida (céntimos), cuánto se mueve (desviación por jornada) y su tendencia. */
    class Valor(val simbolo: String, val nombre: String, val inicial: Long, val vol: Double, val deriva: Double, val sector: String)

    val VALORES = listOf(
        Valor("GRD", "Grada Banca", 1_840, 0.015, 0.001, "Banca"),
        Valor("CSP", "Césped Energía", 2_310, 0.022, 0.002, "Energía"),
        Valor("BDM", "Balón Dorado Media", 940, 0.035, 0.002, "Medios"),
        Valor("TRB", "Tribuna Inmobiliaria", 3_620, 0.018, 0.0015, "Inmobiliaria"),
        Valor("VAR", "Videoarbitraje Tech", 1_210, 0.050, 0.004, "Tecnología"),
        Valor("PRT", "Portería Seguros", 2_770, 0.012, 0.0008, "Seguros"),
        Valor("CRN", "Córner Moda", 680, 0.045, 0.001, "Moda"),
        Valor("ULT", "Ultramarinos del Fondo Sur", 1_560, 0.020, 0.0012, "Alimentación"),
    )

    /** Lo que hay a la venta: tipo, nombre, precio (euros), renta por jornada (lo que deja un negocio, o lo que cuesta mantener). */
    class Articulo(val tipo: String, val nombre: String, val precio: Long, val renta: Long, val descripcion: String)

    val TIENDA = listOf(
        Articulo(Bien.CASA, "Estudio cerca de la ciudad deportiva", 140_000, -60, "Treinta y cinco metros, un sofá cama y el entrenamiento a cinco minutos andando."),
        Articulo(Bien.CASA, "Piso de dos habitaciones en el barrio", 180_000, -80, "En la calle de siempre, encima de la panadería. Tu madre a dos portales."),
        Articulo(Bien.CASA, "Piso en el centro", 420_000, -150, "Tres habitaciones, a diez minutos del estadio."),
        Articulo(Bien.CASA, "Ático con terraza", 1_350_000, -600, "Vistas a toda la ciudad. La prensa rosa ya sabe dónde está."),
        Articulo(Bien.CASA, "Chalet en la sierra", 2_400_000, -900, "Piscina, jardín y vecinos que no hacen preguntas."),
        Articulo(Bien.CASA, "Villa frente al mar", 6_800_000, -2_500, "La de las fotos del verano."),
        Articulo(Bien.CASA, "Mansión con campo de fútbol", 18_000_000, -6_000, "Doce habitaciones, cine, bodega y un campo de césped natural en el jardín."),
        Articulo(Bien.CASA, "Isla privada en el Mediterráneo", 45_000_000, -15_000, "Con su embarcadero, su helipuerto y ningún fotógrafo."),
        Articulo(Bien.COCHE, "Moto de 125", 4_000, -10, "Para llegar al entrenamiento sin buscar aparcamiento."),
        Articulo(Bien.COCHE, "Coche de segunda mano", 9_000, -25, "Ocho años, 120.000 kilómetros y una abolladura que ya es parte de la familia."),
        Articulo(Bien.COCHE, "Compacto nuevo", 22_000, -35, "El primero que te compras con tu dinero. Huele a nuevo."),
        Articulo(Bien.COCHE, "Utilitario eléctrico", 32_000, -40, "Para pasar desapercibido (o intentarlo)."),
        Articulo(Bien.COCHE, "Berlina alemana", 95_000, -120, "La que lleva medio vestuario."),
        Articulo(Bien.COCHE, "Todoterreno de lujo", 180_000, -200, "Cabe todo el entorno."),
        Articulo(Bien.COCHE, "Deportivo italiano", 340_000, -350, "Rojo. Ruidoso. Sale en todas las fotos."),
        Articulo(Bien.COCHE, "Hiperdeportivo de colección", 2_900_000, -900, "Tres en el mundo. Uno es tuyo."),
        Articulo(Bien.COCHE, "Yate de 30 metros", 12_000_000, -4_000, "Con tripulación, jacuzzi en la cubierta y el nombre de tu madre en la popa."),
        Articulo(Bien.COCHE, "Avión privado", 38_000_000, -12_000, "Para no volver a pisar una terminal."),
        Articulo(Bien.NEGOCIO, "Puesto en el mercado de abastos", 40_000, 250, "Fruta y verdura de la huerta de tu tío, con tu foto en el cartel."),
        Articulo(Bien.NEGOCIO, "Furgoneta de comida para festivales", 60_000, 400, "La de las croquetas. Va a todas las fiestas de la comarca."),
        Articulo(Bien.NEGOCIO, "Cafetería de barrio", 150_000, 900, "Un sitio pequeño con tu foto en la pared."),
        Articulo(Bien.NEGOCIO, "Restaurante de autor", 900_000, 4_200, "El chef dice que eres su musa."),
        Articulo(Bien.NEGOCIO, "Academia de fútbol", 1_200_000, 3_800, "Para que salga el siguiente como tú."),
        Articulo(Bien.NEGOCIO, "Discoteca", 2_000_000, 11_000, "Mucho dinero. Muchas fotos. Mucho riesgo."),
        Articulo(Bien.NEGOCIO, "Marca de ropa propia", 3_500_000, 16_000, "Con tu dorsal en todo."),
        Articulo(Bien.NEGOCIO, "Hotel boutique en la costa", 14_000_000, 60_000, "Veinte habitaciones, una terraza con tu nombre y lista de espera en verano."),
        Articulo(Bien.NEGOCIO, "Club de fútbol de Segunda", 40_000_000, 120_000, "Tu propio club. La grada ya ha hecho pancartas con tu cara de presidente."),
    )

    /**
     * Lo que se te enseña en «De compras»: lo que puedes pagar y lo que está cerca de poder pagar (hasta tres veces lo que puedes mover), y al
     * menos lo dos más baratos de cada cosa; lo que es demasiado poco para ti (menos de lo que ganas en dos o tres días) ya no sale.
     */
    fun catalogo(u: Universo): List<Articulo> {
        val poder = maxOf(u.dinero, u.patrimonio / 2) + u.salario * 10
        val tope = poder * 3
        val suelo = poder / 400
        return listOf(Bien.CASA, Bien.COCHE, Bien.NEGOCIO).flatMap { t ->
            val deTipo = TIENDA.filter { it.tipo == t }.sortedBy { it.precio }
            val caben = deTipo.filter { it.precio in suelo..tope }
            caben.ifEmpty { deTipo.filter { it.precio >= suelo }.take(2).ifEmpty { deTipo.takeLast(2) } }
                .let { if (it.size < 2) (it + deTipo.filter { a -> a.precio > (it.lastOrNull()?.precio ?: 0) }.take(2 - it.size)) else it }
        }
    }

    /** ¿Te llega? */
    fun llega(u: Universo, a: Articulo) = a.precio <= u.dinero

    /** Los precios de salida de una carrera nueva. */
    fun inicial(): Map<String, List<Long>> = VALORES.associate { it.simbolo to listOf(it.inicial) }

    /** Una jornada en bolsa (siempre igual para la misma carrera y la misma jornada: no depende de cuándo lo mires). */
    fun avanza(precios: Map<String, List<Long>>, semilla: String, paso: Int): Map<String, List<Long>> = VALORES.associate { v ->
        val h = (precios[v.simbolo] ?: listOf(v.inicial)).ifEmpty { listOf(v.inicial) }
        val r = java.util.Random((semilla + v.simbolo).hashCode().toLong() * 31 + paso)
        val cambio = v.deriva + v.vol * r.nextGaussian()
        val nuevo = (h.last() * (1 + cambio.coerceIn(-0.25, 0.25))).roundToLong().coerceAtLeast(5)
        v.simbolo to (h + nuevo).takeLast(24)
    }

    /** Lo que valen tus cosas tras una jornada: las casas suben poco a poco, los coches bajan, los negocios se mueven. */
    fun revaloriza(b: Bien, semilla: String, paso: Int): Bien {
        val r = java.util.Random((semilla + b.id).hashCode().toLong() * 17 + paso)
        val cambio = when (b.tipo) {
            Bien.CASA -> 0.002 + 0.006 * r.nextGaussian()
            Bien.COCHE -> -0.006 + 0.003 * r.nextGaussian()
            else -> 0.0 + 0.02 * r.nextGaussian()
        }
        return b.copy(valor = (b.valor * (1 + cambio.coerceIn(-0.1, 0.1))).roundToLong().coerceAtLeast(1))
    }

    fun valor(simbolo: String) = VALORES.firstOrNull { it.simbolo == simbolo }

    class NoLlega(falta: Long) : Exception("No te llega: te faltan ${Formato.euros(falta)}.")

    fun compraAcciones(u: Universo, simbolo: String, cuantas: Long): Universo {
        val p = u.precio(simbolo); require(cuantas > 0 && p > 0)
        val coste = (p * cuantas + 99) / 100
        if (coste > u.dinero) throw NoLlega(coste - u.dinero)
        val antes = u.cartera[simbolo] ?: 0L
        val medio = ((u.costeCartera[simbolo] ?: 0L) * antes + p * cuantas) / (antes + cuantas)
        return u.copy(dinero = u.dinero - coste, cartera = u.cartera + (simbolo to antes + cuantas), costeCartera = u.costeCartera + (simbolo to medio))
    }

    fun vendeAcciones(u: Universo, simbolo: String, cuantas: Long): Universo {
        val tiene = u.cartera[simbolo] ?: 0L
        val n = cuantas.coerceAtMost(tiene); require(n > 0)
        val ingreso = u.precio(simbolo) * n / 100
        val quedan = tiene - n
        return u.copy(dinero = u.dinero + ingreso, cartera = if (quedan > 0) u.cartera + (simbolo to quedan) else u.cartera - simbolo,
            costeCartera = if (quedan > 0) u.costeCartera else u.costeCartera - simbolo)
    }

    fun compra(u: Universo, a: Articulo): Universo {
        if (a.precio > u.dinero) throw NoLlega(a.precio - u.dinero)
        val b = Bien(Universo.nuevoId("b"), a.tipo, a.nombre, a.precio, a.precio, a.renta, u.paso)
        return u.copy(dinero = u.dinero - a.precio, bienes = u.bienes + b)
    }

    /** Vender algo tuyo, por lo que vale ahora. */
    fun vende(u: Universo, id: String): Universo {
        val b = u.bienes.firstOrNull { it.id == id } ?: return u
        return u.copy(dinero = u.dinero + b.valor, bienes = u.bienes.filter { it.id != id })
    }
}

/** Cómo se le está viendo (para el tono de la prensa y la red): sale de su edad, su nivel y cómo le van los últimos partidos. */
object Percepcion {
    /** La forma de los últimos partidos, de −1 (fatal) a +1 (de dulce). */
    fun forma(modo: Rol, ps: List<Partido>): Double {
        val ult = ps.takeLast(5)
        if (ult.isEmpty()) return 0.0
        val notas = ult.filter { it.nota > 0 }.map { it.nota }
        val n = if (modo == Rol.JUGADOR && notas.isNotEmpty()) (notas.average() - 6.8) / 1.2 else 0.0
        val r = ult.sumOf { if (it.ganado) 1.0 else if (it.empatado) 0.0 else -1.0 } / ult.size
        val g = if (modo == Rol.JUGADOR) ult.sumOf { it.goles + it.asistencias * 0.5 } / ult.size / 1.2 else 0.0
        return ((n + r + g) / (if (modo == Rol.JUGADOR) 2.2 else 1.0)).coerceIn(-1.0, 1.0)
    }

    /** «sorpresa: …», «confirmación: …», «declive: …». */
    fun de(u: Universo, ps: List<Partido>): String {
        val e = u.personaje.edad
        val f = forma(u.modo, ps)
        val bajo = u.nivelActual.ordinal <= Nivel.PROMESA.ordinal
        return when {
            ps.isEmpty() -> if (e <= 21) "expectación: un chaval de $e años del que todavía no se sabe lo que dará" else "a la espera: su nivel se conoce y se espera que lo confirme"
            e <= 21 && f > 0.25 -> "sorpresa: un chaval de $e años rindiendo muy por encima de lo esperado" + (if (bajo) " (nadie le esperaba)" else "") +
                ": nuevo fenómeno, un terremoto, comparaciones con leyendas, ofertas de grandes antes de tiempo y mucha presión"
            e <= 21 && f < -0.25 -> "dudas: un chaval de $e años en un mal tramo; se le pide paciencia y algunos ya dicen que se le ha subido a la cabeza"
            e <= 21 -> "crecimiento: un chaval de $e años que se va haciendo un sitio; se le mira con curiosidad"
            e <= 27 && f > 0.25 -> "confirmación: está en su mejor momento y lo demuestra; se habla de dar el salto"
            e <= 27 && f < -0.25 -> "crítica: un mal tramo que la grada y la prensa no le perdonan"
            e <= 27 -> "normalidad: cumple; se espera de él lo que ya ha enseñado"
            f > 0.25 -> "buena racha: a los $e años su nivel ya se conoce; se celebra el tramo, pero nadie se vuelve loco"
            f < -0.25 -> "declive: a los $e años se habla de si está acabado, de su físico y de su contrato"
            else -> "veteranía: a los $e años es lo que es; se le valora por la experiencia y se vigila el declive"
        }
    }
}

/** Lo que cambia con cada jornada y con lo que pasa. */
object Vida {
    /**
     * Una jornada (cada partido que metes): cobras el sueldo de la semana, tus negocios dan lo suyo (y tus casas y coches cuestan lo suyo),
     * la bolsa se mueve y tus cosas cambian de valor. Si el partido trae la media nueva del juego, se apunta.
     */
    fun jornada(u: Universo, p: Partido?): Universo {
        val paso = u.paso + 1
        val rentas = u.bienes.sumOf { it.renta }
        var pj = u.personaje
        if (p != null && p.media > 0 && p.media != pj.media) pj = pj.copy(media = p.media, historial = (pj.historial + (paso to p.media)).takeLast(60))
        // La fama que se gana jugando: poca, y cuanto más famoso, más cuesta subir (empieza la jornada con su tope a cero)
        val f0 = u.valorFuera(Fuera.FAMA)
        val gana = if (p != null) famaDelPartido(u.modo, p, f0, u.personaje.edad) else 0
        val f1 = (f0 + gana).coerceIn(0, 100)
        return u.copy(
            fuera = u.fuera + (Fuera.FAMA to f1), famaJornada = maxOf(0, gana), famaHist = (u.famaHist.ifEmpty { listOf(u.paso to f0) } + (paso to f1)).takeLast(80),
            paso = paso, personaje = pj, dinero = u.dinero + Contratos.cobra(u, p) + rentas,
            precios = Mercado.avanza(u.precios.ifEmpty { Mercado.inicial() }, u.id, paso),
            bienes = u.bienes.map { Mercado.revaloriza(it, u.id, paso) },
            temporada = p?.temporada?.ifBlank { null } ?: u.temporada,
        )
    }

    /**
     * Lo que dice Claude que cambia, con límites (que una fiesta no te arruine ni un patrocinio te haga rico de golpe): lo de fuera del
     * campo, de −10 a +10 cada cosa; tu gente, de −15 a +15; el dinero, hasta un 20 % de lo que tienes (y al menos 50.000 €).
     */
    /** Lo más que puede subir la fama en una jornada (sumando el partido, lo que pasa y lo que decide), a una edad normal. */
    const val TOPE_FAMA_JORNADA = 5

    /** El tope según la edad: un chaval que lo rompe es un terremoto (sube más deprisa); a los 28 o más, su nivel ya se conoce. */
    fun topeFama(edad: Int): Int = when { edad <= 21 -> 7; edad <= 27 -> TOPE_FAMA_JORNADA; else -> 4 }

    /** Cuánto pesa lo bueno (y lo malo) según la edad: a los 18, un pico es noticia; a los 29, «una buena racha»; mayor, lo malo pesa más. */
    fun pesoEdad(edad: Int, bueno: Boolean): Double = if (bueno) when {
        edad <= 19 -> 1.8; edad <= 21 -> 1.4; edad <= 24 -> 1.1; edad <= 27 -> 1.0; edad <= 30 -> 0.6; else -> 0.45
    } else when { edad <= 21 -> 0.8; edad <= 27 -> 1.0; edad <= 30 -> 1.2; else -> 1.5 }

    /**
     * La fama que da un partido: la nota, los goles y las asistencias (o, de míster, ganar o perder); cuanto más famoso es, menos sube; y
     * la edad multiplica (un chaval que rinde por encima de lo esperado sube mucho más deprisa; uno de 29, poco). De −3 a +5.
     */
    fun famaDelPartido(modo: Rol, p: Partido, fama: Int, edad: Int = 24): Int {
        var puntos = 0.0
        if (modo == Rol.JUGADOR) {
            if (p.minutos <= 0) return 0
            puntos += when { p.nota >= 8.5 -> 2.0; p.nota >= 7.5 -> 1.2; p.nota >= 6.5 -> 0.5; p.nota in 0.1..5.5 -> -1.0; else -> 0.0 }
            puntos += minOf(2, p.goles) * 0.9 + minOf(2, p.asistencias) * 0.5
            if (p.ganado) puntos += 0.4 else if (!p.empatado) puntos -= 0.3
            // Ser el MVP se nota (sube algo más)
            if (p.mvp) puntos += 0.8
        } else puntos += if (p.ganado) 1.2 else if (p.empatado) 0.2 else -0.8
        val freno = 1.0 - fama.coerceIn(0, 100) / 115.0
        val g = if (puntos > 0) puntos * freno * pesoEdad(edad, true) else puntos * pesoEdad(edad, false)
        val tope = when { edad <= 21 -> 4; edad <= 27 -> 3; else -> 2 }
        return Math.round(g).toInt().coerceIn(-3, tope)
    }

    fun aplica(u: Universo, e: Efectos): Universo {
        val limite = maxOf(50_000L, abs(u.patrimonio) / 5)
        val dinero = e.dinero.coerceIn(-limite, limite)
        val fuera = u.fuera.toMutableMap()
        var famaJornada = u.famaJornada
        e.fuera.forEach { (k, d0) ->
            if (Fuera.TODOS.none { it.first == k }) return@forEach
            var d = d0.coerceIn(-10, 10)
            // La fama sube poco a poco: lo que queda del tope de esta jornada
            if (k == Fuera.FAMA && d > 0) { d = minOf(d, maxOf(0, topeFama(u.personaje.edad) - famaJornada)); famaJornada += d }
            fuera[k] = (u.valorFuera(k) + d).coerceIn(0, 100)
        }
        val entorno = u.entorno.map { p ->
            val d = e.relaciones.entries.firstOrNull { (n, _) -> Gente.quien(listOf(p), n) != null && Gente.quien(u.entorno, n)?.id == p.id }?.value ?: 0
            if (d == 0) p else p.copy(afinidad = (p.afinidad + d.coerceIn(-15, 15)).coerceIn(0, 100))
        }
        val f = fuera[Fuera.FAMA] ?: u.valorFuera(Fuera.FAMA)
        val hist = if (f != u.valorFuera(Fuera.FAMA)) (u.famaHist.ifEmpty { listOf(u.paso to u.valorFuera(Fuera.FAMA)) }.filter { it.first != u.paso } + (u.paso to f)).takeLast(80) else u.famaHist
        // La gente nueva entra si hay sitio (y no es nadie que ya esté o que se fue): [Gente.anade]
        val r = Gente.anade(u.copy(dinero = u.dinero + dinero, fuera = fuera, entorno = entorno, famaJornada = famaJornada, famaHist = hist), e.nuevas)
        return if (e.rivales.isEmpty()) r else Rivales.aplica(r, e.rivales)
    }

    /** Lo que de verdad se aplicó (con los límites), para enseñarlo. */
    fun recorta(u: Universo, e: Efectos): Efectos {
        val limite = maxOf(50_000L, abs(u.patrimonio) / 5)
        val queda = maxOf(0, topeFama(u.personaje.edad) - u.famaJornada)
        return e.copy(dinero = e.dinero.coerceIn(-limite, limite), fuera = e.fuera.filterKeys { k -> Fuera.TODOS.any { it.first == k } }
            .mapValues { (k, v) -> v.coerceIn(-10, 10).let { if (k == Fuera.FAMA && it > 0) minOf(it, queda) else it } }.filterValues { it != 0 },
            relaciones = e.relaciones.mapValues { it.value.coerceIn(-15, 15) }, rivales = Rivales.filtra(u, e.rivales))
    }

    fun mismo(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    /** Aceptar una oferta: cambia el club (o el contrato) y lo que cobras; las otras ofertas de club de esa misma vez se caen. */
    fun acepta(u: Universo, o: Oferta): Universo {
        val c = Contratos.deOferta(u, o)
        return if (c != null) u.copy(club = c.club, contrato = c, salario = c.sueldo + u.patrocinio, dinero = u.dinero + o.prima, contratoHasta = c.hasta,
            renovacionPaso = if (o.tipo == "renovacion") u.paso else u.renovacionPaso)
        else u.copy(dinero = u.dinero + o.prima, patrocinio = u.patrocinio + o.salario, salario = (u.contrato?.sueldo ?: 0) + u.patrocinio + o.salario)
    }
}
