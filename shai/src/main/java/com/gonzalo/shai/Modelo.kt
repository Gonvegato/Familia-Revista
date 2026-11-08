package com.gonzalo.shai

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto

/*
 * Lo de Shai: tus carreras de EA Sports FC (cada una, un universo aparte) y lo que pasa en ellas. Kotlin puro (sin Android), para
 * probarlo todo sin móvil. Lo del campo (resultados, minutos, goles, la media y las cifras del juego) lo pones tú; Shai no se inventa
 * nada del campo: ni lesiones, ni suplencias, ni resultados. Lo de fuera (la prensa, la red, los mensajes, las ofertas, el dinero, la
 * fama, tu gente) lo escribe Claude a partir de lo que pones, y tus decisiones lo cambian.
 */

enum class Rol(val nombre: String) { JUGADOR("Jugador"), MISTER("Míster") }

/** Lo de fuera del campo que lleva Shai (0–100). Lo del juego (la media y sus cifras) lo pones tú. */
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
    /** Para el míster no hay carta en FC: las cifras de Shai, para que tengan algo que contar. */
    val MISTER = listOf("TAC" to "Táctica", "MOT" to "Motivación", "CAN" to "Cantera", "MER" to "Mercado")
}

/** Una persona de tu entorno (siempre inventada: las personas reales solo salen de pasada en la prensa y la red). */
data class Persona(val id: String, val nombre: String, val rol: String, val afinidad: Int = 50, val nota: String = "") {
    fun aMapa(): Map<String, Any?> = mapOf("id" to id, "n" to nombre, "r" to rol, "a" to afinidad.toLong(), "t" to nota)
    companion object {
        fun de(m: Map<String, Any?>) = Persona(m["id"].comoTexto(), m["n"].comoTexto(), m["r"].comoTexto(), m["a"].comoLong().toInt().coerceIn(0, 100), m["t"].comoTexto())
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
) {
    val nombreCorto: String get() = apodo.ifBlank { nombre }
    fun aMapa(): Map<String, Any?> = mapOf(
        "n" to nombre, "ap" to apodo, "ed" to edad.toLong(), "na" to nacionalidad, "po" to posicion, "do" to dorsal.toLong(), "me" to media.toLong(),
        "at" to atributos.mapValues { it.value.toLong() }, "ra" to rasgos, "hi" to historial.map { listOf(it.first.toLong(), it.second.toLong()) },
    )
    companion object {
        fun de(m: Map<String, Any?>) = Personaje(
            m["n"].comoTexto(), m["ap"].comoTexto(), m["ed"].comoLong().toInt(), m["na"].comoTexto(), m["po"].comoTexto(), m["do"].comoLong().toInt(),
            m["me"].comoLong().toInt(), m["at"].comoMapa().mapValues { it.value.comoLong().toInt() }, m["ra"].comoTexto(),
            m["hi"].comoLista().mapNotNull { x -> x.comoLista().takeIf { it.size == 2 }?.let { it[0].comoLong().toInt() to it[1].comoLong().toInt() } },
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
) {
    val nombre: String get() = personaje.nombreCorto
    fun valorFuera(k: String) = fuera[k] ?: Fuera.INICIAL[k] ?: 50
    fun precio(simbolo: String): Long = precios[simbolo]?.lastOrNull() ?: 0L
    /** Lo que tienes en total: dinero, bienes y acciones (euros). */
    val patrimonio: Long get() = dinero + bienes.sumOf { it.valor } + cartera.entries.sumOf { (s, n) -> n * precio(s) / 100 }

    fun aMapa(): Map<String, Any?> = mapOf(
        "v" to 1L, "id" to id, "mo" to modo.name, "pj" to personaje.aMapa(), "cl" to club, "li" to liga, "te" to temporada, "sa" to salario, "ch" to contratoHasta,
        "di" to dinero, "fu" to fuera.mapValues { it.value.toLong() }, "en" to entorno.map { it.aMapa() }, "ca" to cartera, "cc" to costeCartera,
        "bi" to bienes.map { it.aMapa() }, "pr" to precios, "pa" to paso.toLong(), "lo" to lotes.map { it.aMapa() }, "ae" to autoEventos,
        "c" to creado, "cm" to cambiado,
    )
    fun aJson(): String = Json.escribe(aMapa())

    companion object {
        fun nuevoId(prefijo: String = "u") = prefijo + java.util.UUID.randomUUID().toString().replace("-", "").take(10)
        fun deJson(t: String?): Universo? = if (t.isNullOrBlank()) null else runCatching { de(Json.lee(t).comoMapa()) }.getOrNull()
        fun de(m: Map<String, Any?>): Universo? {
            val id = m["id"].comoTexto().ifBlank { return null }
            return Universo(
                id, runCatching { Rol.valueOf(m["mo"].comoTexto()) }.getOrDefault(Rol.JUGADOR), Personaje.de(m["pj"].comoMapa()),
                m["cl"].comoTexto(), m["li"].comoTexto(), m["te"].comoTexto(), m["sa"].comoLong(), m["ch"].comoTexto(), m["di"].comoLong(),
                Fuera.INICIAL + m["fu"].comoMapa().mapValues { it.value.comoLong().toInt().coerceIn(0, 100) },
                m["en"].comoLista().map { Persona.de(it.comoMapa()) }.filter { it.nombre.isNotBlank() },
                m["ca"].comoMapa().mapValues { it.value.comoLong() }.filterValues { it > 0 }, m["cc"].comoMapa().mapValues { it.value.comoLong() },
                m["bi"].comoLista().map { Bien.de(it.comoMapa()) }, m["pr"].comoMapa().mapValues { (_, v) -> v.comoLista().map { it.comoLong() } },
                m["pa"].comoLong().toInt(), m["lo"].comoLista().map { Lote.de(it.comoMapa()) }.filter { it.id.isNotBlank() },
                if (m.containsKey("ae")) m["ae"].comoBool() else true, m["c"].comoLong(), m["cm"].comoLong(),
            )
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
) {
    val resultado: String get() = "$golesFavor-$golesContra"
    val ganado: Boolean get() = golesFavor > golesContra
    val empatado: Boolean get() = golesFavor == golesContra
    val letra: String get() = if (ganado) "V" else if (empatado) "E" else "D"

    fun aMapa(): Map<String, Any?> = mapOf(
        "te" to temporada, "co" to competicion, "ri" to rival, "lo" to local, "gf" to golesFavor.toLong(), "gc" to golesContra.toLong(), "mi" to minutos.toLong(),
        "go" to goles.toLong(), "as" to asistencias.toLong(), "no" to nota, "gs" to goleadores, "eq" to equipo, "re" to rendimiento, "mo" to momentos,
        "me" to media.toLong(), "fe" to fecha, "cm" to como,
    )

    /** Una línea para contárselo a Claude. */
    fun resumen(modo: Rol): String = buildString {
        append(if (local) "En casa contra $rival" else "En el campo del $rival"); append(" ($competicion"); if (fecha.isNotBlank()) append(", $fecha"); append("): ")
        append(resultado); append(if (ganado) " (victoria)" else if (empatado) " (empate)" else " (derrota)")
        if (modo == Rol.JUGADOR) {
            append(". Jugó $minutos min"); if (goles > 0) append(", $goles gol${if (goles > 1) "es" else ""}"); if (asistencias > 0) append(", $asistencias asistencia${if (asistencias > 1) "s" else ""}")
            if (nota > 0) append(", nota ${Formato.decimal(nota)}")
        } else if (goleadores.isNotBlank()) append(". Goles: $goleadores")
        if (equipo.isNotEmpty()) append(". Datos: " + equipo.entries.joinToString(", ") { "${it.key} ${it.value}" })
        if (rendimiento.isNotBlank()) append(". Rendimiento: $rendimiento")
        if (momentos.isNotBlank()) append(". Lo que pasó: $momentos")
        if (media > 0) append(". Su media en el juego es ahora $media")
    }

    companion object {
        /** Los datos del partido que da FC 26 en «Datos del partido» (los que se pueden meter en la plantilla completa). */
        val DATOS_EQUIPO = listOf("Posesión", "Tiros", "Tiros a puerta", "Precisión de pase", "Faltas", "Córners")
        fun de(m: Map<String, Any?>) = Partido(
            m["te"].comoTexto(), m["co"].comoTexto(), m["ri"].comoTexto(), m["lo"].comoBool(), m["gf"].comoLong().toInt(), m["gc"].comoLong().toInt(),
            m["mi"].comoLong().toInt(), m["go"].comoLong().toInt(), m["as"].comoLong().toInt(), m["no"].comoDouble(), m["gs"].comoTexto(),
            m["eq"].comoMapa().mapValues { it.value.comoTexto() }, m["re"].comoTexto(), m["mo"].comoTexto(), m["me"].comoLong().toInt(), m["fe"].comoTexto(),
            m["cm"].comoTexto().ifBlank { "rapida" },
        )
    }
}

/** Un post de la red (como X). [tipo]: `aficion`, `prensa`, `rival`, `famoso` (una persona pública, de pasada), `entorno`. */
data class Post(val autor: String, val usuario: String, val texto: String, val megusta: Int = 0, val reposts: Int = 0, val tipo: String = "aficion") {
    fun aMapa(): Map<String, Any?> = mapOf("a" to autor, "u" to usuario, "t" to texto, "l" to megusta.toLong(), "r" to reposts.toLong(), "k" to tipo)
    companion object { fun de(m: Map<String, Any?>) = Post(m["a"].comoTexto(), m["u"].comoTexto(), m["t"].comoTexto(), m["l"].comoLong().toInt(), m["r"].comoLong().toInt(), m["k"].comoTexto()) }
}

/** Un mensaje privado. [mio]: lo que contestaste tú. */
data class Dm(val de: String, val texto: String, val mio: Boolean = false) {
    fun aMapa(): Map<String, Any?> = mapOf("d" to de, "t" to texto, "m" to mio)
    companion object { fun de(m: Map<String, Any?>) = Dm(m["d"].comoTexto(), m["t"].comoTexto(), m["m"].comoBool()) }
}

/** La portada del periódico. */
data class Prensa(val cabecera: String, val titular: String, val entradilla: String, val otros: List<String> = emptyList()) {
    fun aMapa(): Map<String, Any?> = mapOf("c" to cabecera, "t" to titular, "e" to entradilla, "o" to otros)
    companion object { fun de(m: Map<String, Any?>) = Prensa(m["c"].comoTexto(), m["t"].comoTexto(), m["e"].comoTexto(), m["o"].comoLista().map { it.comoTexto() }) }
}

/**
 * Una oferta («la app manda»: si la aceptas aquí, la haces en FC). [tipo]: `traspaso`, `cesion`, `renovacion`, `patrocinio` o `banquillo`
 * (míster). [salario] en euros a la semana; [prima], de una vez.
 */
data class Oferta(val id: String, val de: String, val tipo: String, val salario: Long, val prima: Long, val anios: Int, val texto: String, val estado: String = ABIERTA) {
    fun aMapa(): Map<String, Any?> = mapOf("id" to id, "d" to de, "t" to tipo, "s" to salario, "p" to prima, "a" to anios.toLong(), "x" to texto, "e" to estado)
    val cambiaDeClub: Boolean get() = tipo == "traspaso" || tipo == "cesion" || tipo == "banquillo"
    companion object {
        const val ABIERTA = "abierta"; const val ACEPTADA = "aceptada"; const val RECHAZADA = "rechazada"
        val TIPOS = mapOf("traspaso" to "Traspaso", "cesion" to "Cesión", "renovacion" to "Renovación", "patrocinio" to "Patrocinio", "banquillo" to "Banquillo")
        fun de(m: Map<String, Any?>) = Oferta(m["id"].comoTexto(), m["d"].comoTexto(), m["t"].comoTexto(), m["s"].comoLong(), m["p"].comoLong(), m["a"].comoLong().toInt(), m["x"].comoTexto(), m["e"].comoTexto().ifBlank { ABIERTA })
    }
}

/** Una decisión (con consecuencias): de 2 a 3 opciones, o lo que quieras contestar. */
data class Decision(val id: String, val de: String, val texto: String, val opciones: List<String>, val elegida: String = "", val consecuencia: String = "", val estado: String = ABIERTA) {
    fun aMapa(): Map<String, Any?> = mapOf("id" to id, "d" to de, "t" to texto, "o" to opciones, "el" to elegida, "cq" to consecuencia, "e" to estado)
    val abierta: Boolean get() = estado == ABIERTA
    companion object {
        const val ABIERTA = "abierta"; const val RESUELTA = "resuelta"
        fun de(m: Map<String, Any?>) = Decision(m["id"].comoTexto(), m["d"].comoTexto(), m["t"].comoTexto(), m["o"].comoLista().map { it.comoTexto() }, m["el"].comoTexto(), m["cq"].comoTexto(), m["e"].comoTexto().ifBlank { ABIERTA })
    }
}

/** Lo que cambia algo que pasa: dinero (euros), lo de fuera del campo (+/−) y la relación con tu gente (por nombre, +/−); y gente nueva. */
data class Efectos(val dinero: Long = 0, val fuera: Map<String, Int> = emptyMap(), val relaciones: Map<String, Int> = emptyMap(), val nuevas: List<Persona> = emptyList()) {
    val vacio: Boolean get() = dinero == 0L && fuera.values.all { it == 0 } && relaciones.values.all { it == 0 } && nuevas.isEmpty()
    fun aMapa(): Map<String, Any?> = mapOf("d" to dinero, "f" to fuera.mapValues { it.value.toLong() }, "r" to relaciones.mapValues { it.value.toLong() }, "n" to nuevas.map { it.aMapa() })
    companion object {
        fun de(m: Map<String, Any?>) = Efectos(m["d"].comoLong(), m["f"].comoMapa().mapValues { it.value.comoLong().toInt() }, m["r"].comoMapa().mapValues { it.value.comoLong().toInt() },
            m["n"].comoLista().map { Persona.de(it.comoMapa()) })
    }
}

enum class TipoEntrada(val nombre: String) { PARTIDO("Partido"), EVENTO("Mientras tanto"), RESPUESTA("Lo que pasó después"), OFICIAL("Oficial"), INICIO("El principio") }

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
) {
    fun visible(ahora: Long) = visibleDesde <= ahora
    fun aMapa(): Map<String, Any?> = mapOf(
        "v" to 1L, "id" to id, "un" to universo, "tp" to tipo.name, "c" to creado, "pa" to paso.toLong(), "ti" to titulo, "tx" to texto,
        "pt" to partido?.aMapa(), "po" to posts.map { it.aMapa() }, "dm" to dms.map { it.aMapa() }, "pr" to prensa?.aMapa(), "of" to ofertas.map { it.aMapa() },
        "de" to decision?.aMapa(), "ef" to efectos.aMapa(), "vd" to visibleDesde, "ap" to aplicada, "pe" to pendiente,
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
            )
        }
    }
}
