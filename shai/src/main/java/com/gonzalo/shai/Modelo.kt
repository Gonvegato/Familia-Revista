package com.gonzalo.shai

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto

/*
 * Lo de FcFeed: tus carreras de EA Sports FC (cada una, un universo aparte) y lo que pasa en ellas. Kotlin puro (sin Android), para
 * probarlo todo sin móvil. Lo del campo (resultados, minutos, goles, la media y las cifras del juego) lo pones tú; FcFeed no se inventa
 * nada del campo: ni lesiones, ni suplencias, ni resultados. Lo de fuera (la prensa, la red, los mensajes, las ofertas, el dinero, la
 * fama, tu gente) lo escribe Claude a partir de lo que pones, y tus decisiones lo cambian.
 */

enum class Rol(val nombre: String) { JUGADOR("Jugador"), MISTER("Míster") }

/** Lo de fuera del campo que lleva FcFeed (0–100). Lo del juego (la media y sus cifras) lo pones tú. */
object Fuera {
    const val FAMA = "fama"
    const val AFICION = "aficion"
    const val DISCIPLINA = "disciplina"
    const val CARISMA = "carisma"
    val TODOS = listOf(FAMA to "Fama", AFICION to "Afición", DISCIPLINA to "Disciplina", CARISMA to "Carisma")
    val INICIAL = mapOf(FAMA to 20, AFICION to 50, DISCIPLINA to 60, CARISMA to 50)
}

/** Las cifras de la carta de FC del jugador (las pones tú, de tu partida) y las del míster. */
object Cifras {
    val JUGADOR = listOf("RIT" to "Ritmo", "TIR" to "Tiro", "PAS" to "Pase", "REG" to "Regate", "DEF" to "Defensa", "FIS" to "Físico")
    /** Para el míster no hay carta en FC: las cifras de FcFeed, para que tengan algo que contar. */
    val MISTER = listOf("TAC" to "Táctica", "MOT" to "Motivación", "CAN" to "Cantera", "MER" to "Mercado")
}

/**
 * Cómo de conocido es. Al crear la carrera se elige el de salida, que fija la fama, la afición y el carisma de partida y el sueldo y el
 * dinero que se sugieren; luego el nivel ACTUAL sale de la fama, que sube poco a poco con lo que hace (partidos, goles, notas, decisiones,
 * escándalos), con topes por jornada ([Vida.jornada], [Vida.aplica]): un canterano no se vuelve estrella por un partidazo.
 */
enum class Nivel(
    val nombre: String, val explica: String, val fama: Int, val aficion: Int, val carisma: Int, val salario: Long, val dinero: Long,
    /** Desde qué fama se está en este nivel. */
    val desde: Int,
    /** Qué medios le siguen. */
    val medios: String,
) {
    CANTERANO("Canterano", "Debuta o casi: le conocen en su barrio y en la grada del filial", 8, 55, 40, 3_000, 15_000, 0,
        "la prensa local y las cuentas de la cantera de su club; los nacionales, de pasada"),
    PROMESA("Promesa", "Ya suena: se habla de él en su liga y en las tertulias", 25, 55, 50, 15_000, 120_000, 18,
        "la prensa deportiva nacional cuando hace algo, y la local siempre"),
    TITULAR("Titular", "Fijo en su equipo y conocido en todo el país", 45, 55, 55, 45_000, 900_000, 38,
        "toda la prensa deportiva nacional; la internacional, si hace algo gordo"),
    ESTRELLA("Estrella", "De los mejores de su liga: portadas, marcas y fichajes", 75, 60, 65, 180_000, 12_000_000, 62,
        "la prensa nacional e internacional a diario, la prensa rosa y los programas de televisión"),
    LEYENDA("Leyenda mundial", "De los mejores del mundo: cada gesto da la vuelta al planeta", 92, 65, 75, 450_000, 60_000_000, 85,
        "los medios de todo el mundo, la prensa rosa internacional y los programas de televisión de varios países");

    companion object {
        fun de(t: String): Nivel? = entries.firstOrNull { it.name == t }
        /** El nivel que da una fama. */
        fun deFama(f: Int): Nivel = entries.last { f >= it.desde }
    }
}

/**
 * Lo que puede pedir cada nivel en una oferta (euros): el sueldo a la semana, la prima, y en un patrocinio lo que da a la semana y de una vez.
 * Se le pasa a Claude y lo que devuelva fuera de aquí se recorta ([Rangos.recorta]).
 */
object Rangos {
    class Rango(val sueldo: LongRange, val primaMax: Long, val patroSemana: Long, val patroPrima: Long, val quien: String)
    fun de(n: Nivel): Rango = when (n) {
        Nivel.CANTERANO -> Rango(1_500L..12_000L, 60_000, 2_000, 20_000,
            "cesiones a clubes de Segunda o a filiales, renovaciones modestas con su club y patrocinios de comercios y marcas locales (inventadas)")
        Nivel.PROMESA -> Rango(8_000L..40_000L, 400_000, 8_000, 150_000,
            "clubes medianos de su liga o de Segunda que le den minutos, renovaciones con mejora y marcas nacionales pequeñas (inventadas)")
        Nivel.TITULAR -> Rango(25_000L..110_000L, 2_000_000, 25_000, 600_000,
            "clubes de la parte alta de su liga o medianos de otras grandes ligas, renovaciones buenas y marcas nacionales (inventadas)")
        Nivel.ESTRELLA -> Rango(90_000L..350_000L, 10_000_000, 80_000, 3_000_000,
            "los grandes de Europa, renovaciones de estrella y marcas grandes internacionales (inventadas)")
        Nivel.LEYENDA -> Rango(250_000L..900_000L, 30_000_000, 200_000, 10_000_000,
            "los más grandes del mundo y contratos históricos, y las marcas más grandes del planeta (inventadas)")
    }
    fun texto(n: Nivel): String { val r = de(n)
        return "OFERTAS PARA SU NIVEL (${n.nombre.lowercase()}): ${r.quien}. Sueldo entre ${Formato.euros(r.sueldo.first)} y ${Formato.euros(r.sueldo.last)} a la semana; prima de hasta ${Formato.euros(r.primaMax)}; un patrocinio, hasta ${Formato.euros(r.patroSemana)} a la semana y ${Formato.euros(r.patroPrima)} de una vez." }
    /** Lo que se sale del rango de su nivel, a su sitio. */
    fun recorta(o: Oferta, n: Nivel): Oferta { val r = de(n)
        return if (o.tipo == "patrocinio") o.copy(salario = o.salario.coerceIn(0, r.patroSemana), prima = o.prima.coerceIn(0, r.patroPrima))
        else o.copy(salario = if (o.salario <= 0) 0 else o.salario.coerceIn(r.sueldo.first, r.sueldo.last), prima = o.prima.coerceIn(0, r.primaMax))
    }
}

/** El alcance en la red: cuántos le siguen y cuánto se mueve lo suyo (sale de su fama de ahora). */
object Alcance {
    /** Seguidores: de unos mil (fama 0) a unos doscientos millones (fama 100), en escala logarítmica. */
    fun seguidores(fama: Int): Long = Math.round(1000.0 * Math.pow(200_000.0, fama.coerceIn(0, 100) / 100.0))
    fun seguidores(u: Universo): Long = seguidores(u.valorFuera(Fuera.FAMA))
    /** Los «me gusta» de un post normal suyo o sobre él (uno muy gordo, varias veces más). */
    fun megustaTipico(u: Universo): Long = (seguidores(u) / 60).coerceAtLeast(12)
    /** «48 M», «12 mil», «950». */
    fun texto(n: Long): String = when {
        n >= 1_000_000 -> Formato.decimal(n / 1_000_000.0) + " M"
        n >= 1_000 -> Formato.cifra(Math.round(n / 1000.0)) + " mil"
        else -> Formato.cifra(n)
    }
}

/**
 * Una persona de tu entorno (siempre inventada: las personas reales solo salen de pasada en la prensa y la red). [alias]: otros nombres con
 * los que sale («Marisa Bloise», «mamá»: es la misma persona, [Gente]); [noEs]: nombres que NO son ella aunque se parezcan (lo separaste a
 * mano); [actitud]: cómo está contigo ahora y qué te insiste («presiona para que cambies de club»), que cambia con lo que habláis; [desde]:
 * la jornada en que entró en tu vida; [salio]: la jornada en que le diste puerta (0: sigue) y [porque], lo que dijiste.
 */
data class Persona(val id: String, val nombre: String, val rol: String, val afinidad: Int = 50, val nota: String = "",
                   val alias: List<String> = emptyList(), val noEs: List<String> = emptyList(), val actitud: String = "", val desde: Int = 0,
                   val salio: Int = 0, val porque: String = "") {
    fun aMapa(): Map<String, Any?> = mapOf("id" to id, "n" to nombre, "r" to rol, "a" to afinidad.toLong(), "t" to nota, "al" to alias, "ne" to noEs, "ac" to actitud,
        "ds" to desde.toLong(), "sa" to salio.toLong(), "pq" to porque)
    /** El id corto que se le pasa a Claude (para que diga quién escribe sin equivocarse de nombre). */
    val corto: String get() = id.take(6)
    companion object {
        fun de(m: Map<String, Any?>) = Persona(m["id"].comoTexto(), m["n"].comoTexto(), m["r"].comoTexto(), m["a"].comoLong().toInt().coerceIn(0, 100), m["t"].comoTexto(),
            m["al"].comoLista().map { it.comoTexto() }.filter { it.isNotBlank() }, m["ne"].comoLista().map { it.comoTexto() }.filter { it.isNotBlank() }, m["ac"].comoTexto(),
            m["ds"].comoLong().toInt(), m["sa"].comoLong().toInt(), m["pq"].comoTexto())
    }
}

/** Tu personaje. [atributos] y [media] son los de tu partida de FC (los pones tú); [historial] es cómo ha ido cambiando la media. */
data class Personaje(
    val nombre: String,
    val apodo: String = "",
    val edad: Int = 18,
    val nacionalidad: String = "",
    val posicion: String = "",
    val dorsal: Int = 0,
    val media: Int = 0,
    val atributos: Map<String, Int> = emptyMap(),
    val rasgos: String = "",
    val historial: List<Pair<Int, Int>> = emptyList(),
    /** Su fecha de nacimiento en el juego (ISO, opcional): con ella y las fechas de los partidos, la edad exacta para los récords. */
    val nacimiento: String = "",
) {
    val nombreCorto: String get() = apodo.ifBlank { nombre }
    fun aMapa(): Map<String, Any?> = mapOf(
        "n" to nombre, "ap" to apodo, "ed" to edad.toLong(), "na" to nacionalidad, "po" to posicion, "do" to dorsal.toLong(), "me" to media.toLong(),
        "at" to atributos.mapValues { it.value.toLong() }, "ra" to rasgos, "hi" to historial.map { listOf(it.first.toLong(), it.second.toLong()) }, "nc" to nacimiento,
    )
    companion object {
        fun de(m: Map<String, Any?>) = Personaje(
            m["n"].comoTexto(), m["ap"].comoTexto(), m["ed"].comoLong().toInt(), m["na"].comoTexto(), m["po"].comoTexto(), m["do"].comoLong().toInt(),
            m["me"].comoLong().toInt(), m["at"].comoMapa().mapValues { it.value.comoLong().toInt() }, m["ra"].comoTexto(),
            m["hi"].comoLista().mapNotNull { x -> x.comoLista().takeIf { it.size == 2 }?.let { it[0].comoLong().toInt() to it[1].comoLong().toInt() } },
            m["nc"].comoTexto(),
        )
    }
}

/** Algo tuyo: una casa, un coche, un negocio. [valor] cambia con el tiempo; [renta] es lo que deja (o cuesta, si es negativa) cada jornada. */
data class Bien(val id: String, val tipo: String, val nombre: String, val compra: Long, val valor: Long, val renta: Long, val desde: Int) {
    fun aMapa(): Map<String, Any?> = mapOf("id" to id, "t" to tipo, "n" to nombre, "c" to compra, "v" to valor, "r" to renta, "d" to desde.toLong())
    companion object {
        const val CASA = "casa"; const val COCHE = "coche"; const val NEGOCIO = "negocio"
        fun de(m: Map<String, Any?>) = Bien(m["id"].comoTexto(), m["t"].comoTexto(), m["n"].comoTexto(), m["c"].comoLong(), m["v"].comoLong(), m["r"].comoLong(), m["d"].comoLong().toInt())
    }
}

/** Un lote de la API de lotes (Batch) que aún no ha vuelto: los dos eventos de entre partidos. */
data class Lote(val id: String, val creado: Long, val paso: Int) {
    fun aMapa(): Map<String, Any?> = mapOf("id" to id, "c" to creado, "p" to paso.toLong())
    companion object { fun de(m: Map<String, Any?>) = Lote(m["id"].comoTexto(), m["c"].comoLong(), m["p"].comoLong().toInt()) }
}

/** Una carrera: un universo con su personaje, su dinero, su gente y su mercado. */
data class Universo(
    val id: String,
    val modo: Rol,
    val personaje: Personaje,
    val club: String,
    val liga: String = "",
    val temporada: String = "",
    /** Euros a la semana (una jornada = una semana). */
    val salario: Long = 0,
    val contratoHasta: String = "",
    val dinero: Long = 0,
    val fuera: Map<String, Int> = Fuera.INICIAL,
    val entorno: List<Persona> = emptyList(),
    /** Acciones que tienes: símbolo → cuántas. */
    val cartera: Map<String, Long> = emptyMap(),
    /** Lo que pagaste de media por cada acción (céntimos). */
    val costeCartera: Map<String, Long> = emptyMap(),
    val bienes: List<Bien> = emptyList(),
    /** El precio de cada acción ahora (céntimos) y los últimos (para la gráfica). */
    val precios: Map<String, List<Long>> = emptyMap(),
    /** Las jornadas que han pasado (cada partido que metes, una). */
    val paso: Int = 0,
    val lotes: List<Lote> = emptyList(),
    val autoEventos: Boolean = true,
    val creado: Long = 0,
    val cambiado: Long = 0,
    /** Lo que FcFeed recuerda de la carrera (tramas abiertas, relaciones, hechos clave): lo resume Claude cada 3 jornadas y al cerrar temporada. */
    val memoria: String = "",
    /** La jornada en que se escribió [memoria]. */
    val memoriaPaso: Int = 0,
    /** Las temporadas cerradas («2026/27»…). */
    val cerradas: List<String> = emptyList(),
    /** Cómo de conocido empezó ([Nivel], por su nombre). El de ahora es [nivelActual]. */
    val nivel: String = "",
    /** Quién es, con sus palabras: su historia, su momento, su palmarés («23 años, Balón de Oro»…). Claude lo toma como su pasado. */
    val trayectoria: String = "",
    /** Lo que ha subido la fama en esta jornada (para el tope). */
    val famaJornada: Int = 0,
    /** Cómo ha ido la fama: (jornada, fama). */
    val famaHist: List<Pair<Int, Int>> = emptyList(),
    /** Su contrato (el de FcFeed). Null: es agente libre. */
    val contrato: Contrato? = null,
    /** Lo que le dan sus patrocinios a la semana (euros). */
    val patrocinio: Long = 0,
    /** La jornada en que se habló de renovar por última vez. */
    val renovacionPaso: Int = 0,
    /** Sus hitos y récords (de FcFeed: con sus cifras). */
    val hitos: List<Hito> = emptyList(),
    /** Los récords reales que ha batido o igualado en su carrera de FcFeed: desde entonces, la marca a batir es la suya ([Historicos]). */
    val recordsSuyos: List<RecordSuyo> = emptyList(),
    /** Sus rivales (surgen solos por lo que pasa, o los mete él): [Rivales]. */
    val rivales: List<Rival> = emptyList(),
    /** Lo que se sabe de su club de verdad (búsqueda web, [Clubes]): si ya no es el de [club] (un traspaso), hay que identificar el nuevo. */
    val clubInfo: InfoClub? = null,
    /** La gente a la que le diste puerta («Ya no está en tu vida»): no vuelven a escribir salvo que los recuperes. */
    val exEntorno: List<Persona> = emptyList(),
    /** Las competiciones que juega su club (de la búsqueda) y las que ha añadido él: la lista del desplegable ([Competiciones]). */
    val comps: List<String> = emptyList(),
    /** De qué club son [comps] (si cambia de club, hay que volver a buscarlas). */
    val compsDe: String = "",
    /** Cómo se agrupan los nombres viejos de competiciones (el nombre tal cual, en minúsculas → la de la lista). */
    val compAlias: Map<String, String> = emptyMap(),
    /** Ya se le preguntó a Haiku por los nombres que no encajaban (una sola vez por nombre: los que ya están en [compAlias]). */
    val compsPreguntadas: List<String> = emptyList(),
    /** El nivel (ordinal de [Nivel]) con el que entró la última gente nueva: al subir de nivel, entra gente nueva ([Gente]). -1: aún no. */
    val nivelGente: Int = -1,
) {
    /** El club de ahora está identificado y confirmado. */
    val clubIdentificado: Boolean get() = clubInfo?.let { it.confirmado && it.es(club) } == true
    /** Hay una identificación del club de ahora esperando a que digas «Sí, es este». */
    val clubPorConfirmar: InfoClub? get() = clubInfo?.takeIf { !it.confirmado && it.es(club) }
    /** Sin contrato: libre (si ya ha empezado). */
    val libre: Boolean get() = contrato == null
    val nivelDe: Nivel? get() = Nivel.de(nivel)
    /** El nivel de ahora (sale de la fama): es el que se le cuenta a Claude. */
    val nivelActual: Nivel get() = Nivel.deFama(valorFuera(Fuera.FAMA))
    val nombre: String get() = personaje.nombreCorto
    fun valorFuera(k: String) = fuera[k] ?: Fuera.INICIAL[k] ?: 50
    fun precio(simbolo: String): Long = precios[simbolo]?.lastOrNull() ?: 0L
    /** Lo que tienes en total: dinero, bienes y acciones (euros). */
    val patrimonio: Long get() = dinero + bienes.sumOf { it.valor } + cartera.entries.sumOf { (s, n) -> n * precio(s) / 100 }

    fun aMapa(): Map<String, Any?> = mapOf(
        "v" to 1L, "id" to id, "mo" to modo.name, "pj" to personaje.aMapa(), "cl" to club, "li" to liga, "te" to temporada, "sa" to salario, "ch" to contratoHasta,
        "di" to dinero, "fu" to fuera.mapValues { it.value.toLong() }, "en" to entorno.map { it.aMapa() }, "ca" to cartera, "cc" to costeCartera,
        "bi" to bienes.map { it.aMapa() }, "pr" to precios, "pa" to paso.toLong(), "lo" to lotes.map { it.aMapa() }, "ae" to autoEventos,
        "c" to creado, "cm" to cambiado, "mm" to memoria, "mp" to memoriaPaso.toLong(), "tc" to cerradas, "nv" to nivel, "ty" to trayectoria, "fj" to famaJornada.toLong(),
        "fh" to famaHist.map { listOf(it.first.toLong(), it.second.toLong()) }, "ct" to (contrato?.aMapa() ?: emptyMap<String, Any?>()), "pt" to patrocinio,
        "rp" to renovacionPaso.toLong(), "ht" to hitos.map { it.aMapa() }, "rs" to recordsSuyos.map { it.aMapa() }, "rv" to rivales.map { it.aMapa() },
        "ic" to (clubInfo?.aMapa() ?: emptyMap<String, Any?>()), "ex" to exEntorno.map { it.aMapa() }, "cps" to comps, "cpd" to compsDe, "cpa" to compAlias,
        "cpq" to compsPreguntadas, "nvg" to nivelGente.toLong(),
    )
    fun aJson(): String = Json.escribe(aMapa())

    companion object {
        fun nuevoId(prefijo: String = "u") = prefijo + java.util.UUID.randomUUID().toString().replace("-", "").take(10)
        fun deJson(t: String?): Universo? = if (t.isNullOrBlank()) null else runCatching { de(Json.lee(t).comoMapa()) }.getOrNull()
        fun de(m: Map<String, Any?>): Universo? {
            val id = m["id"].comoTexto().ifBlank { return null }
            val u = Universo(
                id, runCatching { Rol.valueOf(m["mo"].comoTexto()) }.getOrDefault(Rol.JUGADOR), Personaje.de(m["pj"].comoMapa()),
                m["cl"].comoTexto(), m["li"].comoTexto(), m["te"].comoTexto(), m["sa"].comoLong(), m["ch"].comoTexto(), m["di"].comoLong(),
                Fuera.INICIAL + m["fu"].comoMapa().mapValues { it.value.comoLong().toInt().coerceIn(0, 100) },
                m["en"].comoLista().map { Persona.de(it.comoMapa()) }.filter { it.nombre.isNotBlank() },
                m["ca"].comoMapa().mapValues { it.value.comoLong() }.filterValues { it > 0 }, m["cc"].comoMapa().mapValues { it.value.comoLong() },
                m["bi"].comoLista().map { Bien.de(it.comoMapa()) }, m["pr"].comoMapa().mapValues { (_, v) -> v.comoLista().map { it.comoLong() } },
                m["pa"].comoLong().toInt(), m["lo"].comoLista().map { Lote.de(it.comoMapa()) }.filter { it.id.isNotBlank() },
                if (m.containsKey("ae")) m["ae"].comoBool() else true, m["c"].comoLong(), m["cm"].comoLong(),
                m["mm"].comoTexto(), m["mp"].comoLong().toInt(), m["tc"].comoLista().map { it.comoTexto() }.filter { it.isNotBlank() },
                m["nv"].comoTexto(), m["ty"].comoTexto(), m["fj"].comoLong().toInt(),
                m["fh"].comoLista().mapNotNull { x -> x.comoLista().takeIf { it.size == 2 }?.let { it[0].comoLong().toInt() to it[1].comoLong().toInt() } },
                Contrato.de(m["ct"].comoMapa()), m["pt"].comoLong(), m["rp"].comoLong().toInt(), m["ht"].comoLista().map { Hito.de(it.comoMapa()) }.filter { it.id.isNotBlank() },
                m["rs"].comoLista().map { RecordSuyo.de(it.comoMapa()) }.filter { it.n > 0 },
                m["rv"].comoLista().map { Rival.de(it.comoMapa()) }.filter { it.nombre.isNotBlank() },
                InfoClub.de(m["ic"].comoMapa()),
                m["ex"].comoLista().map { Persona.de(it.comoMapa()) }.filter { it.nombre.isNotBlank() },
                m["cps"].comoLista().map { it.comoTexto() }.filter { it.isNotBlank() }, m["cpd"].comoTexto(),
                m["cpa"].comoMapa().mapValues { it.value.comoTexto() }.filterValues { it.isNotBlank() }, m["cpq"].comoLista().map { it.comoTexto() },
                if (m.containsKey("nvg")) m["nvg"].comoLong().toInt() else -1,
            )
            // (las carreras de antes de los contratos: su sueldo y su «3 años» pasan a ser su contrato)
            return if (!m.containsKey("ct") && u.contrato == null) u.copy(contrato = Contratos.migra(u)) else u
        }
    }
}

/** Un partido, tal cual lo metes (rápido, completo o leído de una foto del juego). */
data class Partido(
    val temporada: String,
    val competicion: String,
    val rival: String,
    val local: Boolean,
    val golesFavor: Int,
    val golesContra: Int,
    val minutos: Int = 90,
    val goles: Int = 0,
    val asistencias: Int = 0,
    val nota: Double = 0.0,
    /** Míster: quién marcó (texto libre). */
    val goleadores: String = "",
    /** Los datos del partido de los dos equipos («Posesión» → «58 / 42»…): solo los que metes. */
    val equipo: Map<String, String> = emptyMap(),
    /** Lo demás del rendimiento, con tus palabras. */
    val rendimiento: String = "",
    /** Lo que pasó (el golazo, la expulsión del rival, la remontada): tal cual lo cuentas. */
    val momentos: String = "",
    /** La media que tiene ahora tu jugador en FC (0 = no ha cambiado). */
    val media: Int = 0,
    val fecha: String = "",
    /** Cómo se metió: `rapida`, `completa` o `foto`. */
    val como: String = "rapida",
    /** Si con este partido ganó un título (lo que tú pongas: «LaLiga», «Copa del Rey»…). */
    val titulo: String = "",
    /** Si [fecha] la puso FcFeed (la del partido anterior + 7 días) y no tú. */
    val fechaEstimada: Boolean = false,
    /** Fue el MVP (el jugador del partido). */
    val mvp: Boolean = false,
    /** La competición tal cual se metió, si se agrupó en otra de la lista ([Competiciones]). */
    val competicionOriginal: String = "",
) {
    val resultado: String get() = "$golesFavor-$golesContra"
    val ganado: Boolean get() = golesFavor > golesContra
    val empatado: Boolean get() = golesFavor == golesContra
    val letra: String get() = if (ganado) "V" else if (empatado) "E" else "D"

    fun aMapa(): Map<String, Any?> = mapOf(
        "te" to temporada, "co" to competicion, "ri" to rival, "lo" to local, "gf" to golesFavor.toLong(), "gc" to golesContra.toLong(), "mi" to minutos.toLong(),
        "go" to goles.toLong(), "as" to asistencias.toLong(), "no" to nota, "gs" to goleadores, "eq" to equipo, "re" to rendimiento, "mo" to momentos,
        "me" to media.toLong(), "fe" to fecha, "cm" to como, "ti" to titulo, "fx" to fechaEstimada, "mv" to mvp, "co0" to competicionOriginal,
    )

    /** Una línea para contárselo a Claude. */
    fun resumen(modo: Rol): String = buildString {
        append(if (local) "En casa contra $rival" else "En el campo del $rival"); append(" ($competicion"); if (fecha.isNotBlank()) append(", ${Fechas.bonita(fecha)}"); append("): ")
        append(resultado); append(if (ganado) " (victoria)" else if (empatado) " (empate)" else " (derrota)")
        if (modo == Rol.JUGADOR) {
            append(". Jugó $minutos min"); if (goles > 0) append(", $goles gol${if (goles > 1) "es" else ""}"); if (asistencias > 0) append(", $asistencias asistencia${if (asistencias > 1) "s" else ""}")
            if (nota > 0) append(", nota ${Formato.decimal(nota)}")
            if (mvp) append(". FUE EL MVP DEL PARTIDO")
        } else if (goleadores.isNotBlank()) append(". Goles: $goleadores")
        if (equipo.isNotEmpty()) append(". Datos: " + equipo.entries.joinToString(", ") { "${it.key} ${it.value}" })
        if (rendimiento.isNotBlank()) append(". Rendimiento: $rendimiento")
        if (momentos.isNotBlank()) append(". Lo que pasó: $momentos")
        if (media > 0) append(". Su media en el juego es ahora $media")
        if (titulo.isNotBlank()) append(". CON ESTE PARTIDO GANA UN TÍTULO: $titulo")
    }

    companion object {
        /** Los datos del partido que da FC 26 en «Datos del partido» (los que se pueden meter en la plantilla completa). */
        val DATOS_EQUIPO = listOf("Posesión", "Tiros", "Tiros a puerta", "Precisión de pase", "Faltas", "Córners")
        fun de(m: Map<String, Any?>) = Partido(
            m["te"].comoTexto(), m["co"].comoTexto(), m["ri"].comoTexto(), m["lo"].comoBool(), m["gf"].comoLong().toInt(), m["gc"].comoLong().toInt(),
            m["mi"].comoLong().toInt(), m["go"].comoLong().toInt(), m["as"].comoLong().toInt(), m["no"].comoDouble(), m["gs"].comoTexto(),
            m["eq"].comoMapa().mapValues { it.value.comoTexto() }, m["re"].comoTexto(), m["mo"].comoTexto(), m["me"].comoLong().toInt(), m["fe"].comoTexto(),
            m["cm"].comoTexto().ifBlank { "rapida" }, m["ti"].comoTexto(), m["fx"].comoBool(), m["mv"].comoBool(), m["co0"].comoTexto(),
        )
    }
}

/** Un post de la red (como X). [tipo]: `aficion`, `prensa`, `rival`, `famoso` (una persona pública, de pasada), `entorno`. */
data class Post(
    val autor: String, val usuario: String, val texto: String, val megusta: Int = 0, val reposts: Int = 0, val tipo: String = "aficion",
    /** Las respuestas de debajo (en los más gordos). */
    val respuestas: List<Post> = emptyList(),
    /** Lo que cita (un retuit con comentario): el post citado. */
    val cita: Post? = null,
) {
    fun aMapa(): Map<String, Any?> = mapOf("a" to autor, "u" to usuario, "t" to texto, "l" to megusta.toLong(), "r" to reposts.toLong(), "k" to tipo,
        "rs" to respuestas.map { it.aMapa() }, "ci" to cita?.aMapa()).filterValues { it != null && it != emptyList<Any>() }
    companion object {
        fun de(m: Map<String, Any?>): Post = Post(m["a"].comoTexto(), m["u"].comoTexto(), m["t"].comoTexto(), m["l"].comoLong().toInt(), m["r"].comoLong().toInt(), m["k"].comoTexto(),
            m["rs"].comoLista().map { de(it.comoMapa()) }, m["ci"]?.comoMapa()?.takeIf { it.isNotEmpty() }?.let { de(it) })
    }
}

/** Un mensaje privado. [mio]: lo que contestaste tú. */
data class Dm(val de: String, val texto: String, val mio: Boolean = false, val chat: String = "",
              /** El id de la persona de su gente que lo escribe (si se sabe): así no se separa en chats distintos ([Gente]). */
              val pid: String = "") {
    /** La conversación a la que va (una persona o un grupo, «Los del barrio»): si no se dice, la de quien lo manda. */
    val conversacion: String get() = chat.ifBlank { de }
    fun aMapa(): Map<String, Any?> = mapOf("d" to de, "t" to texto, "m" to mio, "c" to chat, "p" to pid).filterValues { it != "" }
    companion object { fun de(m: Map<String, Any?>) = Dm(m["d"].comoTexto(), m["t"].comoTexto(), m["m"].comoBool(), m["c"].comoTexto(), m["p"].comoTexto()) }
}

/** La portada del periódico. */
data class Prensa(
    val cabecera: String, val titular: String, val entradilla: String, val otros: List<String> = emptyList(),
    /** La noticia entera (párrafos separados por una línea en blanco): se lee al tocar la portada. */
    val articulo: String = "",
    /** La entradilla de cada uno de [otros] (en el mismo orden). */
    val entradillas: List<String> = emptyList(),
) {
    fun aMapa(): Map<String, Any?> = mapOf("c" to cabecera, "t" to titular, "e" to entradilla, "o" to otros, "ar" to articulo, "oe" to entradillas)
    companion object {
        fun de(m: Map<String, Any?>) = Prensa(m["c"].comoTexto(), m["t"].comoTexto(), m["e"].comoTexto(), m["o"].comoLista().map { it.comoTexto() },
            m["ar"].comoTexto(), m["oe"].comoLista().map { it.comoTexto() })
    }
}

/**
 * Una oferta («la app manda»: si la aceptas aquí, la haces en FC). [tipo]: `traspaso`, `cesion`, `renovacion`, `patrocinio` o `banquillo`
 * (míster). [salario] en euros a la semana; [prima], de una vez.
 */
data class Oferta(val id: String, val de: String, val tipo: String, val salario: Long, val prima: Long, val anios: Int, val texto: String, val estado: String = ABIERTA,
                  /** Ya se pidió más una vez (no se puede volver a pedir). */
                  val negociada: Boolean = false) {
    fun aMapa(): Map<String, Any?> = mapOf("id" to id, "d" to de, "t" to tipo, "s" to salario, "p" to prima, "a" to anios.toLong(), "x" to texto, "e" to estado, "ng" to negociada)
    val cambiaDeClub: Boolean get() = tipo == "traspaso" || tipo == "cesion" || tipo == "banquillo"
    companion object {
        const val ABIERTA = "abierta"; const val ACEPTADA = "aceptada"; const val RECHAZADA = "rechazada"
        val TIPOS = mapOf("traspaso" to "Traspaso", "cesion" to "Cesión", "renovacion" to "Renovación", "patrocinio" to "Patrocinio", "banquillo" to "Banquillo")
        const val NEGOCIADA = "negociada"
        fun de(m: Map<String, Any?>) = Oferta(m["id"].comoTexto(), m["d"].comoTexto(), m["t"].comoTexto(), m["s"].comoLong(), m["p"].comoLong(), m["a"].comoLong().toInt(), m["x"].comoTexto(), m["e"].comoTexto().ifBlank { ABIERTA }, m["ng"].comoBool())
    }
}

/** Una decisión (con consecuencias): de 2 a 3 opciones, o lo que quieras contestar. */
data class Decision(
    val id: String, val de: String,
    /** La pregunta («¿Qué le contestas?»). */
    val texto: String,
    val opciones: List<String>, val elegida: String = "", val consecuencia: String = "", val estado: String = ABIERTA,
    /** De qué va, en corto («La entrevista de tu madre»). */
    val titulo: String = "",
    /** Lo que ha pasado, entero (quién, dónde, qué se dijo, por qué importa): si está vacío, el texto de su entrada. */
    val contexto: String = "",
    /** Qué te juegas. */
    val enJuego: String = "",
    /** Lo que hace FcFeed si eliges la primera opción («renegociar»: el club te hace una oferta de renovación). */
    val accion: String = "",
) {
    fun aMapa(): Map<String, Any?> = mapOf("id" to id, "d" to de, "t" to texto, "o" to opciones, "el" to elegida, "cq" to consecuencia, "e" to estado,
        "ti" to titulo, "cx" to contexto, "ej" to enJuego, "ac" to accion)
    val abierta: Boolean get() = estado == ABIERTA
    /** No la contestaste antes del partido siguiente: caducó (y eso también tuvo consecuencias). */
    val caducada: Boolean get() = estado == CADUCADA
    companion object {
        const val ABIERTA = "abierta"; const val RESUELTA = "resuelta"; const val CADUCADA = "caducada"
        fun de(m: Map<String, Any?>) = Decision(m["id"].comoTexto(), m["d"].comoTexto(), m["t"].comoTexto(), m["o"].comoLista().map { it.comoTexto() }, m["el"].comoTexto(),
            m["cq"].comoTexto(), m["e"].comoTexto().ifBlank { ABIERTA }, m["ti"].comoTexto(), m["cx"].comoTexto(), m["ej"].comoTexto(), m["ac"].comoTexto())
    }
}

/** Un premio de fin de temporada (siempre de medios inventados). */
data class Premio(val nombre: String, val medio: String, val ganado: Boolean, val texto: String) {
    fun aMapa(): Map<String, Any?> = mapOf("n" to nombre, "m" to medio, "g" to ganado, "t" to texto)
    companion object { fun de(m: Map<String, Any?>) = Premio(m["n"].comoTexto(), m["m"].comoTexto(), m["g"].comoBool(), m["t"].comoTexto()) }
}

/** Lo que cambia algo que pasa: dinero (euros), lo de fuera del campo (+/−) y la relación con tu gente (por nombre, +/−); y gente nueva. */
data class Efectos(val dinero: Long = 0, val fuera: Map<String, Int> = emptyMap(), val relaciones: Map<String, Int> = emptyMap(), val nuevas: List<Persona> = emptyList(),
                   /** Lo que cambia con sus rivales (choques y alguno nuevo). */
                   val rivales: List<CambioRival> = emptyList()) {
    val vacio: Boolean get() = dinero == 0L && fuera.values.all { it == 0 } && relaciones.values.all { it == 0 } && nuevas.isEmpty() && rivales.isEmpty()
    fun aMapa(): Map<String, Any?> = mapOf("d" to dinero, "f" to fuera.mapValues { it.value.toLong() }, "r" to relaciones.mapValues { it.value.toLong() }, "n" to nuevas.map { it.aMapa() },
        "rv" to rivales.map { it.aMapa() })
    companion object {
        fun de(m: Map<String, Any?>) = Efectos(m["d"].comoLong(), m["f"].comoMapa().mapValues { it.value.comoLong().toInt() }, m["r"].comoMapa().mapValues { it.value.comoLong().toInt() },
            m["n"].comoLista().map { Persona.de(it.comoMapa()) }, m["rv"].comoLista().map { CambioRival.de(it.comoMapa()) }.filter { it.nombre.isNotBlank() })
    }
}

enum class TipoEntrada(val nombre: String) {
    PARTIDO("Partido"), EVENTO("Mientras tanto"), RESPUESTA("Lo que pasó después"), OFICIAL("Oficial"), INICIO("El principio"),
    TEMPORADA("Fin de temporada"), SINCONTESTAR("No contestaste"), HITO("Hito"), RECORD("Récord histórico"), RIVAL("Rivalidad"),
    CHARLA("Charla"), PUERTA("Fuera de tu vida"), GENTE("Gente nueva"), HISTORICO("Hecho histórico"),
}

/**
 * Lo que pasa en tu carrera, en orden: un partido (con su prensa, la red y los mensajes de después), un evento de entre partidos, lo que
 * pasa después de una decisión, un fichaje oficial. [visibleDesde]: los eventos de entre partidos salen a su hora; [aplicada]: sus
 * efectos ya cuentan (al salir).
 */
data class Entrada(
    val id: String,
    val universo: String,
    val tipo: TipoEntrada,
    val creado: Long,
    val paso: Int,
    val titulo: String = "",
    val texto: String = "",
    val partido: Partido? = null,
    val posts: List<Post> = emptyList(),
    val dms: List<Dm> = emptyList(),
    val prensa: Prensa? = null,
    val ofertas: List<Oferta> = emptyList(),
    val decision: Decision? = null,
    val efectos: Efectos = Efectos(),
    val visibleDesde: Long = 0,
    val aplicada: Boolean = true,
    /** Lo que falta por escribir (la prensa no llegó: sin conexión, sin saldo…); se puede reintentar. */
    val pendiente: String = "",
    /** La tendencia del día en la red («#ElNiñoSeSale») y cuántos posts lleva. */
    val tendencia: String = "",
    val tendenciaPosts: Int = 0,
    /** Los premios (fin de temporada). */
    val premios: List<Premio> = emptyList(),
    /** Si es un hecho histórico ([TipoEntrada.HISTORICO]): lo que contaste, lo que entendió la app, hasta dónde llegó y lo que se comprobó. */
    val hecho: Hecho? = null,
) {
    fun visible(ahora: Long) = visibleDesde <= ahora
    fun aMapa(): Map<String, Any?> = mapOf(
        "v" to 1L, "id" to id, "un" to universo, "tp" to tipo.name, "c" to creado, "pa" to paso.toLong(), "ti" to titulo, "tx" to texto,
        "pt" to partido?.aMapa(), "po" to posts.map { it.aMapa() }, "dm" to dms.map { it.aMapa() }, "pr" to prensa?.aMapa(), "of" to ofertas.map { it.aMapa() },
        "de" to decision?.aMapa(), "ef" to efectos.aMapa(), "vd" to visibleDesde, "ap" to aplicada, "pe" to pendiente,
        "tn" to tendencia, "tp2" to tendenciaPosts.toLong(), "pm" to premios.map { it.aMapa() }, "hh" to hecho?.aMapa(),
    ).filterValues { it != null }
    fun aJson(): String = Json.escribe(aMapa())

    companion object {
        fun deJson(t: String?): Entrada? = if (t.isNullOrBlank()) null else runCatching { de(Json.lee(t).comoMapa()) }.getOrNull()
        fun de(m: Map<String, Any?>): Entrada? {
            val id = m["id"].comoTexto().ifBlank { return null }
            return Entrada(
                id, m["un"].comoTexto(), runCatching { TipoEntrada.valueOf(m["tp"].comoTexto()) }.getOrDefault(TipoEntrada.EVENTO), m["c"].comoLong(), m["pa"].comoLong().toInt(),
                m["ti"].comoTexto(), m["tx"].comoTexto(), m["pt"]?.comoMapa()?.takeIf { it.isNotEmpty() }?.let { Partido.de(it) },
                m["po"].comoLista().map { Post.de(it.comoMapa()) }, m["dm"].comoLista().map { Dm.de(it.comoMapa()) },
                m["pr"]?.comoMapa()?.takeIf { it.isNotEmpty() }?.let { Prensa.de(it) }, m["of"].comoLista().map { Oferta.de(it.comoMapa()) },
                m["de"]?.comoMapa()?.takeIf { it.isNotEmpty() }?.let { Decision.de(it) }, Efectos.de(m["ef"].comoMapa()), m["vd"].comoLong(),
                if (m.containsKey("ap")) m["ap"].comoBool() else true, m["pe"].comoTexto(),
                m["tn"].comoTexto(), m["tp2"].comoLong().toInt(), m["pm"].comoLista().map { Premio.de(it.comoMapa()) },
                m["hh"]?.comoMapa()?.takeIf { it.isNotEmpty() }?.let { Hecho.de(it) },
            )
        }
    }
}
