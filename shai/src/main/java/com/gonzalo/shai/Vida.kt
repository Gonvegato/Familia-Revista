package com.gonzalo.shai

import kotlin.math.abs
import kotlin.math.roundToLong

/** Las cifras de una temporada en una competición (o en todas: [competicion] vacía). */
data class Linea(
    val temporada: String, val competicion: String, val jugados: Int, val victorias: Int, val empates: Int, val derrotas: Int,
    val golesFavor: Int, val golesContra: Int, val minutos: Int, val goles: Int, val asistencias: Int, val notaMedia: Double,
)

object Estadisticas {
    fun linea(temporada: String, competicion: String, ps: List<Partido>): Linea {
        val conNota = ps.filter { it.nota > 0 }
        return Linea(temporada, competicion, ps.size, ps.count { it.ganado }, ps.count { it.empatado }, ps.count { !it.ganado && !it.empatado },
            ps.sumOf { it.golesFavor }, ps.sumOf { it.golesContra }, ps.sumOf { it.minutos }, ps.sumOf { it.goles }, ps.sumOf { it.asistencias },
            if (conNota.isEmpty()) 0.0 else conNota.sumOf { it.nota } / conNota.size)
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
        Articulo(Bien.CASA, "Piso en el centro", 420_000, -150, "Tres habitaciones, a diez minutos del estadio."),
        Articulo(Bien.CASA, "Ático con terraza", 1_350_000, -600, "Vistas a toda la ciudad. La prensa rosa ya sabe dónde está."),
        Articulo(Bien.CASA, "Chalet en la sierra", 2_400_000, -900, "Piscina, jardín y vecinos que no hacen preguntas."),
        Articulo(Bien.CASA, "Villa frente al mar", 6_800_000, -2_500, "La de las fotos del verano."),
        Articulo(Bien.COCHE, "Utilitario eléctrico", 32_000, -40, "Para pasar desapercibido (o intentarlo)."),
        Articulo(Bien.COCHE, "Berlina alemana", 95_000, -120, "La que lleva medio vestuario."),
        Articulo(Bien.COCHE, "Todoterreno de lujo", 180_000, -200, "Cabe todo el entorno."),
        Articulo(Bien.COCHE, "Deportivo italiano", 340_000, -350, "Rojo. Ruidoso. Sale en todas las fotos."),
        Articulo(Bien.COCHE, "Hiperdeportivo de colección", 2_900_000, -900, "Tres en el mundo. Uno es tuyo."),
        Articulo(Bien.NEGOCIO, "Cafetería de barrio", 150_000, 900, "Un sitio pequeño con tu foto en la pared."),
        Articulo(Bien.NEGOCIO, "Restaurante de autor", 900_000, 4_200, "El chef dice que eres su musa."),
        Articulo(Bien.NEGOCIO, "Academia de fútbol", 1_200_000, 3_800, "Para que salga el siguiente como tú."),
        Articulo(Bien.NEGOCIO, "Discoteca", 2_000_000, 11_000, "Mucho dinero. Muchas fotos. Mucho riesgo."),
        Articulo(Bien.NEGOCIO, "Marca de ropa propia", 3_500_000, 16_000, "Con tu dorsal en todo."),
    )

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
        return u.copy(
            paso = paso, personaje = pj, dinero = u.dinero + u.salario + rentas,
            precios = Mercado.avanza(u.precios.ifEmpty { Mercado.inicial() }, u.id, paso),
            bienes = u.bienes.map { Mercado.revaloriza(it, u.id, paso) },
            temporada = p?.temporada?.ifBlank { null } ?: u.temporada,
        )
    }

    /**
     * Lo que dice Claude que cambia, con límites (que una fiesta no te arruine ni un patrocinio te haga rico de golpe): lo de fuera del
     * campo, de −10 a +10 cada cosa; tu gente, de −15 a +15; el dinero, hasta un 20 % de lo que tienes (y al menos 50.000 €).
     */
    fun aplica(u: Universo, e: Efectos): Universo {
        val limite = maxOf(50_000L, abs(u.patrimonio) / 5)
        val dinero = e.dinero.coerceIn(-limite, limite)
        val fuera = u.fuera.toMutableMap()
        e.fuera.forEach { (k, d) -> if (Fuera.TODOS.any { it.first == k }) fuera[k] = (u.valorFuera(k) + d.coerceIn(-10, 10)).coerceIn(0, 100) }
        var entorno = u.entorno.map { p ->
            val d = e.relaciones.entries.firstOrNull { (n, _) -> mismo(n, p.nombre) }?.value ?: 0
            if (d == 0) p else p.copy(afinidad = (p.afinidad + d.coerceIn(-15, 15)).coerceIn(0, 100))
        }
        e.nuevas.filter { n -> n.nombre.isNotBlank() && entorno.none { mismo(it.nombre, n.nombre) } }.take(3).forEach { n ->
            entorno = entorno + n.copy(id = n.id.ifBlank { Universo.nuevoId("p") }, afinidad = n.afinidad.coerceIn(0, 100))
        }
        return u.copy(dinero = u.dinero + dinero, fuera = fuera, entorno = entorno.takeLast(40))
    }

    /** Lo que de verdad se aplicó (con los límites), para enseñarlo. */
    fun recorta(u: Universo, e: Efectos): Efectos {
        val limite = maxOf(50_000L, abs(u.patrimonio) / 5)
        return e.copy(dinero = e.dinero.coerceIn(-limite, limite), fuera = e.fuera.filterKeys { k -> Fuera.TODOS.any { it.first == k } }.mapValues { it.value.coerceIn(-10, 10) },
            relaciones = e.relaciones.mapValues { it.value.coerceIn(-15, 15) })
    }

    fun mismo(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    /** Aceptar una oferta: cambia el club (o el contrato) y lo que cobras; las otras ofertas de club de esa misma vez se caen. */
    fun acepta(u: Universo, o: Oferta): Universo = when (o.tipo) {
        "traspaso", "cesion", "banquillo" -> u.copy(club = o.de, salario = if (o.salario > 0) o.salario else u.salario, dinero = u.dinero + o.prima,
            contratoHasta = if (o.anios > 0) "${o.anios} año${if (o.anios > 1) "s" else ""}" else u.contratoHasta)
        "renovacion" -> u.copy(salario = if (o.salario > 0) o.salario else u.salario, dinero = u.dinero + o.prima,
            contratoHasta = if (o.anios > 0) "${o.anios} año${if (o.anios > 1) "s" else ""} más" else u.contratoHasta)
        else -> u.copy(dinero = u.dinero + o.prima, salario = u.salario + o.salario)
    }
}
