package com.gonzalo.mostaza.pc

import java.text.Normalizer

/*
 * Las pistas de verdad de una fuente. El texto de una fuente (lo que lee [Analisis]) es solo una primera idea del idioma: muchas veces no
 * dice nada fiable (una bandera, «DUAL», «MULTI», o nada). Cuando el reproductor abre el archivo, Media3 (en el ordenador, mpv: su `track-list`) da sus pistas de audio y
 * de subtítulos con su idioma (`Format.language`: «spa», «es», «es-ES», «es-419», «es-MX»…) y su nombre (`Format.label`: «Castellano»,
 * «Latino», «Español (España)», «Spanish (Latin America)»…). Aquí se entiende eso ([Pistas.clasifica]), se elige el mejor audio del
 * archivo según tu orden ([Pistas.mejorAudio]) y se decide si hay que probar otra fuente ([Tanteo]). Lo que se ve de cada fuente se
 * recuerda en ESTE dispositivo ([MemoriaPistas]: es saber de la fuente, no tuyo) y el elector lo usa la próxima vez (lo comprobado
 * manda sobre el texto). Todo es Kotlin sin Android (PistasTest), salvo lo que guarda la memoria.
 */

/** Lo que se ha visto en las pistas de un archivo. */
data class Verificacion(
    /** Los idiomas de sus pistas de audio ([Idioma.OTRO] para uno que no es de los que se distinguen). */
    val idiomas: Set<Idioma>,
    /** Trae subtítulos en español. */
    val subtitulosEsp: Boolean = false,
    /** Se sabe el idioma de todas sus pistas de audio (o ya tiene el primero de tu orden): si no, no se puede decir que le falte nada. */
    val concluyente: Boolean = true,
)

/** Una pista de audio del archivo, ya entendida. */
data class PistaAudio(val idioma: Idioma?, val soportada: Boolean = true, val elegida: Boolean = false)

object Pistas {
    private fun normal(s: String?): String = Normalizer.normalize(s.orEmpty(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

    private val LATAM = setOf("419", "mx", "ar", "co", "cl", "pe", "ve", "uy", "py", "bo", "ec", "gt", "cr", "pa", "do", "cu", "hn", "ni", "sv", "pr", "us", "la")
    private val HINT_LATINO = Regex("(?<![a-z])(latino|latina|latam|latin ?america(no|na|n)?|latinoamerica(no|na)?|hispanoamerica(no|na)?|mexic(o|ano|ana)|argentin[oa]?|419|es[-_ ]?(mx|419|la|ar|co|cl|us))(?![a-z])")
    private val HINT_LATIN = Regex("(?<![a-z])latin(?![a-z])")
    private val HINT_CASTELLANO = Regex("(?<![a-z])(castellano|castilian|espana|spain|europe(an)?|europeo|iberic[oa]?|es[-_ ]?es)(?![a-z])")
    private val HINT_ESPANOL = Regex("(?<![a-z])(espanol|spanish|esp|spa)(?![a-z])")
    private val SIN_IDIOMA = setOf("", "und", "mul", "zxx", "mis", "unknown", "qaa")

    private fun primarioOtro(p: String): Idioma? = when (p) {
        "en", "eng" -> Idioma.INGLES; "fr", "fre", "fra" -> Idioma.FRANCES; "it", "ita" -> Idioma.ITALIANO; "de", "ger", "deu" -> Idioma.ALEMAN
        "pt", "por" -> Idioma.PORTUGUES; "ja", "jpn" -> Idioma.JAPONES; "ko", "kor" -> Idioma.COREANO; "ru", "rus" -> Idioma.RUSO
        "zh", "chi", "zho", "cmn", "yue" -> Idioma.CHINO
        else -> null
    }

    private fun etiquetaOtro(l: String): Idioma? = when {
        Regex("(?<![a-z])(english|ingles)(?![a-z])").containsMatchIn(l) -> Idioma.INGLES
        Regex("(?<![a-z])(french|francais|frances)(?![a-z])").containsMatchIn(l) -> Idioma.FRANCES
        Regex("(?<![a-z])(italian|italiano)(?![a-z])").containsMatchIn(l) -> Idioma.ITALIANO
        Regex("(?<![a-z])(german|deutsch|aleman)(?![a-z])").containsMatchIn(l) -> Idioma.ALEMAN
        Regex("(?<![a-z])(portuguese|portugues)(?![a-z])").containsMatchIn(l) -> Idioma.PORTUGUES
        Regex("(?<![a-z])(japanese|japones)(?![a-z])").containsMatchIn(l) -> Idioma.JAPONES
        Regex("(?<![a-z])(korean|coreano)(?![a-z])").containsMatchIn(l) -> Idioma.COREANO
        Regex("(?<![a-z])(russian|ruso)(?![a-z])").containsMatchIn(l) -> Idioma.RUSO
        Regex("(?<![a-z])(chinese|chino|mandarin)(?![a-z])").containsMatchIn(l) -> Idioma.CHINO
        else -> null
    }

    /**
     * El idioma de una pista: [Idioma.CASTELLANO], [Idioma.LATINO], [Idioma.ESPANOL] (español sin saber cuál), otro conocido, [Idioma.OTRO]
     * (un idioma que no se distingue), o null (no lo dice: «und», nada). El nombre de la pista manda sobre el código cuando dice de dónde es
     * (los programas que juntan pistas suelen poner solo «es» o «spa», aunque sea latino).
     */
    fun clasifica(lenguaje: String?, etiqueta: String?): Idioma? {
        val code = normal(lenguaje).replace('_', '-')
        val primario = code.substringBefore('-')
        val region = code.substringAfter('-', "")
        val l = normal(etiqueta)
        val espanolPorCodigo = primario in setOf("es", "spa", "esp")
        val sinCodigo = primario in SIN_IDIOMA
        val espanolPorEtiqueta = HINT_ESPANOL.containsMatchIn(l) || HINT_CASTELLANO.containsMatchIn(l) || HINT_LATINO.containsMatchIn(l)
        if (espanolPorCodigo || (sinCodigo && espanolPorEtiqueta)) {
            return when {
                HINT_LATINO.containsMatchIn(l) || (HINT_LATIN.containsMatchIn(l) && (espanolPorCodigo || HINT_ESPANOL.containsMatchIn(l))) -> Idioma.LATINO
                HINT_CASTELLANO.containsMatchIn(l) -> Idioma.CASTELLANO
                region in LATAM -> Idioma.LATINO
                region == "es" -> Idioma.CASTELLANO
                else -> Idioma.ESPANOL
            }
        }
        if (sinCodigo) return etiquetaOtro(l)
        return primarioOtro(primario) ?: Idioma.OTRO
    }

    /** ¿Es una pista de subtítulos en español (de cualquier sitio)? */
    fun subtituloEspanol(lenguaje: String?, etiqueta: String?): Boolean = clasifica(lenguaje, etiqueta)?.let { it.espanol } == true

    /** Lo que se ve en las pistas: los audios (idioma, nombre) y los subtítulos (idioma, nombre). */
    fun verifica(audios: List<Pair<String?, String?>>, subtitulos: List<Pair<String?, String?>>, p: PrefsElector): Verificacion {
        val clas = audios.map { (l, e) -> clasifica(l, e) }
        val conocidos = clas.filterNotNull().toSet()
        val subs = subtitulos.any { (l, e) -> subtituloEspanol(l, e) }
        val base = Verificacion(conocidos, subs, true)
        val concluyente = clas.isNotEmpty() && (clas.none { it == null } || (conocidos.isNotEmpty() && puesto(base, p) < 1.0))
        return base.copy(concluyente = concluyente)
    }

    /** El puesto de un archivo comprobado en tu orden: el de su mejor pista de audio (más bajo = mejor). */
    fun puesto(v: Verificacion, p: PrefsElector): Double {
        val orden = Elector.ordenEfectivo(p)
        if (v.idiomas.isEmpty()) return orden.size.toDouble()
        return v.idiomas.minOf { i -> Elector.puestoIdioma(Analisis(idiomas = if (i == Idioma.OTRO) emptySet() else setOf(i), subtitulosEsp = v.subtitulosEsp), p) }
    }

    /**
     * La pista de audio que toca según tu orden (su índice), o null para dejar la que pone el reproductor: solo se cambia a una en
     * español (castellano, latino o español sin saber), que este dispositivo sepa reproducir; si la que ya suena es igual de buena, esa.
     */
    fun mejorAudio(pistas: List<PistaAudio>, p: PrefsElector): Int? {
        val orden = Elector.ordenEfectivo(p)
        fun puestoDe(x: PistaAudio): Double = if (x.idioma?.espanol == true) Elector.puestoIdioma(Analisis(idiomas = setOf(x.idioma)), p) else orden.size.toDouble() + 1
        val validas = pistas.withIndex().filter { it.value.soportada && it.value.idioma?.espanol == true && puestoDe(it.value) < orden.size }
        if (validas.isEmpty()) return null
        val mejor = validas.minOf { puestoDe(it.value) }
        // Si en tu orden va antes la original (VOSE u original) que cualquier español de este archivo, no se cambia
        val primerOriginal = orden.indexOfFirst { it == "vose" || it == "original" }
        if (primerOriginal >= 0 && primerOriginal < mejor && pistas.any { it.soportada && it.idioma != null && it.idioma.espanol == false }) return null
        val empatadas = validas.filter { puestoDe(it.value) == mejor }
        return (empatadas.firstOrNull { it.value.elegida } ?: empatadas.first()).index
    }

    /** ¿Hay que poner subtítulos en español? Si el audio que suena no es español y tu orden acepta la original con subtítulos. */
    fun quiereSubtitulos(audio: Idioma?, p: PrefsElector): Boolean = audio != null && !audio.espanol && "vose" in Elector.ordenEfectivo(p)

    /** «en castellano», «en latino», «en versión original con subtítulos»… (para lo que se dice). */
    fun enIdioma(id: String): String = when (id) {
        "castellano" -> "en castellano"; "latino" -> "en latino"; "vose" -> "en versión original con subtítulos"; "original" -> "en versión original"; else -> "en $id"
    }

    /** Lo que es un archivo comprobado, dicho con tu orden: «en latino», «en versión original con subtítulos», «en inglés». */
    fun describe(v: Verificacion, p: PrefsElector): String {
        val orden = Elector.ordenEfectivo(p)
        val i = puesto(v, p).toInt()
        return if (i in orden.indices) enIdioma(orden[i]) else v.idiomas.firstOrNull { it != Idioma.OTRO }?.let { "en " + it.etiqueta.lowercase() } ?: "en otro idioma"
    }
}

/** Qué hacer después de ver las pistas (o de que falle) una fuente que se eligió sola. */
sealed class Decision {
    /** Se queda con esta (y, si hace falta, se dice algo). */
    data class SeQueda(val mensaje: String? = null) : Decision()
    /** Se prueba otra. */
    data class Prueba(val candidata: Candidata, val mensaje: String) : Decision()
    /** Se vuelve a la mejor que se ha encontrado (ya probada). */
    data class Vuelve(val candidata: Candidata, val mensaje: String) : Decision()
    /** No hay nada que hacer (falló y no queda otra): lo dice el reproductor. */
    object Nada : Decision()
}

/**
 * Probar fuentes hasta dar con una en tu primer idioma, cuando «Reproducir» la eligió sola (nunca cuando tocas tú una fuente concreta):
 * como mucho [MAX_PRUEBAS] archivos abiertos (contando el primero) y [MAX_MS] en total. Solo se prueban las que podrían ser mejores (lo
 * comprobado de antes, o el texto, no dice que sean peores) y que se abren rápido (directas o de tu debrid ya bajadas; un torrent, solo si
 * tu debrid lo tiene o tiene semillas). Si ninguna es mejor, vuelve a la mejor y lo dice tal cual.
 * [candidatas]: las del elector, en su orden (la primera es la que se abrió).
 */
class Tanteo(private val p: PrefsElector, private val candidatas: List<Candidata>, private val inicio: Long) {
    companion object {
        const val MAX_PRUEBAS = 4
        const val MAX_MS = 45_000L
    }

    private val probadas = ArrayList<Pair<Candidata, Verificacion?>>()
    var terminado = false; private set
    val intentos: Int get() = probadas.size

    private val orden = Elector.ordenEfectivo(p)
    private val objetivo: String get() = orden.firstOrNull() ?: "castellano"

    private fun puestoV(v: Verificacion) = Pistas.puesto(v, p)
    private fun mejorProbada(): Pair<Candidata, Verificacion>? = probadas.mapNotNull { (c, v) -> if (v != null && v.concluyente) c to v else null }.minByOrNull { puestoV(it.second) }

    /** ¿Se abre rápido y sin gastar? */
    private fun probable(c: Candidata): Boolean {
        val a = c.analisis
        return when (c.fuente.tipo) {
            TipoFuente.DIRECTA -> a.enCache != false
            TipoFuente.TORRENT -> a.enCache == true || a.semillas >= Elector.SEMILLAS
            else -> false
        }
    }

    /** ¿Podría tener mejor idioma que [mejor]? Lo comprobado manda; si no, el texto (y si el texto no dice nada, o dice MULTI/DUAL, quizá). */
    private fun podriaMejorar(c: Candidata, mejor: Double): Boolean {
        c.verificacion?.let { return it.concluyente && puestoV(it) < mejor || !it.concluyente }
        val a = c.analisis
        val audioConocido = a.idiomas.any { it != Idioma.MULTI && it != Idioma.DUAL }
        if (!audioConocido) return true
        return Elector.puestoIdioma(a, p) < mejor
    }

    private fun siguiente(desde: Candidata, mejor: Double, ahora: Long): Candidata? {
        if (probadas.size >= MAX_PRUEBAS || ahora - inicio >= MAX_MS) return null
        val i = candidatas.indexOfFirst { it === desde }
        return candidatas.drop(i + 1).firstOrNull { c -> probadas.none { it.first === c } && probable(c) && podriaMejorar(c, mejor) }
    }

    private fun agotadoPorLimite(ahora: Long) = probadas.size >= MAX_PRUEBAS || ahora - inicio >= MAX_MS

    private fun alFinal(actual: Candidata, ahora: Long): Decision {
        terminado = true
        val mejor = mejorProbada() ?: return Decision.Nada
        val como = Pistas.describe(mejor.second, p)
        val mensaje = if (agotadoPorLimite(ahora) && candidatas.size > probadas.size)
            "No he encontrado ninguna ${Pistas.enIdioma(objetivo)} (he probado ${probadas.size}); la mejor es $como"
            else "No hay ninguna ${Pistas.enIdioma(objetivo)}; la mejor es $como"
        return if (mejor.first === actual) Decision.SeQueda(mensaje) else Decision.Vuelve(mejor.first, mensaje)
    }

    /** Ya se ven las pistas de [c] (la que está abierta). */
    fun tras(c: Candidata, v: Verificacion, ahora: Long): Decision {
        if (terminado) return Decision.SeQueda()
        probadas += c to v
        // Sin saber el idioma de sus pistas no se puede decir que le falte el tuyo: se queda
        if (!v.concluyente) { terminado = true; return Decision.SeQueda() }
        val pv = puestoV(v)
        if (pv < 1.0) { terminado = true; return Decision.SeQueda() }
        val mejor = mejorProbada()?.let { puestoV(it.second) } ?: pv
        val sig = siguiente(c, mejor, ahora) ?: return alFinal(c, ahora)
        return Decision.Prueba(sig, "Esta no tiene audio ${Pistas.enIdioma(objetivo)}: probando otra…")
    }

    /** [c] no se ha podido abrir. */
    fun trasFallo(c: Candidata, ahora: Long): Decision {
        if (terminado) return Decision.Nada
        probadas += c to null
        val mejor = mejorProbada()?.let { puestoV(it.second) } ?: Double.MAX_VALUE
        val sig = siguiente(c, mejor, ahora) ?: return alFinal(c, ahora)
        return Decision.Prueba(sig, "Esta no se abre: probando otra…")
    }

    /** Ha pasado el tiempo sin ver las pistas de [c]: si ya había una comprobada, a esa; si no, se queda con esta. */
    fun trasTiempo(c: Candidata, ahora: Long): Decision {
        if (terminado || ahora - inicio < MAX_MS) return Decision.SeQueda()
        terminado = true
        val mejor = mejorProbada() ?: return Decision.SeQueda()
        return Decision.Vuelve(mejor.first, "No he encontrado ninguna ${Pistas.enIdioma(objetivo)}; la mejor es ${Pistas.describe(mejor.second, p)}")
    }

    fun cancela() { terminado = true }
}

/**
 * Lo comprobado de cada fuente (sus idiomas de audio y si trae subtítulos en español), por su [Elector.clave]: en ESTE dispositivo (es
 * caché, saber de la fuente; no viaja con la cuenta). Como mucho [MAX] fuentes (las más recientes).
 */
object MemoriaPistas {
    const val MAX = 400

    data class Entrada(val v: Verificacion, val cuando: Long)

    fun codifica(m: Map<String, Entrada>): String = m.entries.sortedByDescending { it.value.cuando }.take(MAX).joinToString("\n") { (k, e) ->
        listOf(k.replace(Regex("[\\t\\n\\r]"), " "), e.v.idiomas.joinToString(",") { it.name }, if (e.v.subtitulosEsp) "s" else "n", e.cuando.toString()).joinToString("\t")
    }

    fun decodifica(s: String?): LinkedHashMap<String, Entrada> {
        val out = LinkedHashMap<String, Entrada>()
        s.orEmpty().lineSequence().forEach { l ->
            val x = l.split('\t'); if (x.size < 4 || x[0].isBlank()) return@forEach
            val ids = x[1].split(',').mapNotNull { n -> Idioma.values().firstOrNull { it.name == n } }.toSet()
            out[x[0]] = Entrada(Verificacion(ids, x[2] == "s", true), x[3].toLongOrNull() ?: 0L)
        }
        return out
    }

    /** Añade (o cambia) lo de [clave]. */
    fun con(m: Map<String, Entrada>, clave: String, v: Verificacion, ahora: Long): LinkedHashMap<String, Entrada> =
        LinkedHashMap(m).also { it[clave] = Entrada(v.copy(concluyente = true), ahora) }

    /** Dónde se guarda en este ordenador (en las pruebas, otra carpeta). */
    @Volatile var archivo: java.io.File? = null
    private fun f(): java.io.File = archivo ?: java.io.File(com.gonzalo.familia.pc.Sitio.de("mostaza"), "pistas.txt")
    @Volatile private var cache: LinkedHashMap<String, Entrada>? = null

    private fun todas(): LinkedHashMap<String, Entrada> = cache ?: decodifica(runCatching { f().takeIf { it.isFile }?.readText() }.getOrNull()).also { cache = it }

    /** Lo comprobado de una fuente (o null). */
    fun busca(): (String) -> Verificacion? { val m = todas(); return { k -> m[k]?.v } }

    /** Apunta lo comprobado (solo si es concluyente). */
    @Synchronized
    fun apunta(clave: String, v: Verificacion) {
        if (!v.concluyente || clave.isBlank()) return
        val m = con(todas(), clave, v, System.currentTimeMillis())
        val s = codifica(m)
        cache = decodifica(s)
        runCatching { f().writeText(s) }
    }

    /** (Para las pruebas.) */
    fun olvidaParaPruebas() { cache = null }
}
