package com.gonzalo.marfil

import com.gonzalo.familia.nucleo.Nodo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Lo leído en un Kobo, a la cuenta: los libros, por dónde vas, si los has terminado y el tiempo leído. Lo mismo que hace el programa
 * del Kobo (kobo/ en el repositorio, por Wi-Fi) pero desde aquí, con el Kobo enchufado por cable. Las dos partes deciden igual: lo
 * comprueban los mismos casos (kobo/testdata/casos.json, en KoboTest).
 *
 * Solo se sube lo que el Kobo sabe de más tarde que la cuenta (cada campo con su hora): si después has leído en Seshat, gana Seshat.
 * Un libro borrado en Seshat no vuelve. El tiempo del Kobo va aparte (`tiempo/<kid>~kobo`), como el de cada móvil.
 */
object Kobo {
    const val DISPOSITIVO = "kobo"

    /** Un libro tal como lo apunta el Kobo (`content`, ContentType 6, de KoboReader.sqlite). Horas en ms (0: no se sabe). */
    data class Libro(
        val titulo: String, val autor: String, val porcentaje: Double, val estado: Int, val ul: Long, val segundos: Long, val terminado: Long,
        /** El libro de la cuenta al que corresponde (lo pone [buscaKid]; vacío: el de su clave). */
        val kid: String = "",
        /** Solo en los kepub que copias tú: por dónde vas como lo apunta el Kobo («capítulo.xhtml#kobo.12.3:40») y la huella del archivo. */
        val marca: String = "",
        val huellaKepub: String = "",
        /** ContentID del Kobo (en los que copias tú, «file:///mnt/onboard/…»). */
        val contentId: String = "",
    )

    /** Unos campos de una cosa de la cuenta, todos con la misma hora. */
    data class Cambio(val coleccion: String, val ref: String, val campos: Map<String, Any?>, val hora: Long)

    data class Plan(val cambios: List<Cambio>, val resumen: List<String>)

    private const val PENDIENTE = "pendiente"
    private const val LEYENDO = "en_curso"
    private const val TERMINADO = "terminado"

    /**
     * Qué libro de la cuenta es [l]: el de su misma clave o, si no está, el único con el mismo título y las mismas palabras en el autor
     * («Brown, Pierce» es «Pierce Brown»), o con el mismo título y sin autor en uno de los dos. Si no hay ninguno (o hay dudas), el de su
     * clave: será un libro nuevo.
     */
    fun buscaKid(l: Libro, libros: Map<String, Nodo>): String {
        val k = MezclaLectura.kid(claveDe(l.titulo, l.autor))
        if (libros.containsKey(k)) return k
        val t = norm(l.titulo); val a = palabras(l.autor)
        val hallados = libros.filter { (_, n) ->
            !n.borrado && norm(n.texto("t")) == t && palabras(n.texto("a")).let { b -> a == b || a.isEmpty() || b.isEmpty() }
        }.keys
        return hallados.singleOrNull() ?: k
    }

    private fun palabras(s: String) = norm(s).split(' ').filter { it.isNotEmpty() }.sorted().joinToString(" ")

    private fun horaDe(n: Nodo?, campo: String): Long = n?.m?.get(campo) ?: 0L

    private fun Any?.num(): Double = when (this) { is Number -> toDouble(); else -> 0.0 } // (como Go: un texto no es un número)

    /**
     * Qué subir (pura: no habla con nadie). [libros] y [tiempos]: lo que hay en la cuenta (`libros/<kid>` y `tiempo/<kid>~<disp>`).
     */
    fun planea(kobo: List<Libro>, libros: Map<String, Nodo>, tiempos: Map<String, Nodo>, ahora: Long, zona: ZoneId, disp: String = DISPOSITIVO): Plan {
        val cambios = ArrayList<Cambio>()
        val resumen = ArrayList<String>()
        for (l0 in kobo) {
            val k = l0.kid.ifBlank { buscaKid(l0, libros) }
            val l = l0.copy(kid = k)
            val libro = libros[k]
            if (libro != null && libro.borrado) continue // borrado en Seshat: no se resucita
            val ul = l.ul; val fin = l.terminado
            var fr = (l.porcentaje * 100).roundToLong() / 10000.0
            var estado = ""
            when (l.estado) {
                1 -> estado = LEYENDO
                2 -> { estado = TERMINADO; fr = 1.0 }
            }
            val campos = LinkedHashMap<String, Pair<Any?, Long>>()
            fun pon(c: String, v: Any?, hora: Long) { if (hora > 0 && hora > horaDe(libro, c)) campos[c] = v to hora }
            if (libro == null) {
                val hora = if (ul > 0) ul else ahora
                pon("clave", claveDe(l.titulo, l.autor), hora)
                pon("t", l.titulo, hora)
                pon("a", l.autor, hora)
                pon("an", hora, hora)
                pon("estado", estado.ifEmpty { PENDIENTE }, hora)
                if (ul > 0) pon("ul", ul, ul)
                pon("fr", fr, hora)
                if (fin > 0 && l.estado == 2) pon("fin", listOf(fin), fin)
                if (l.marca.isNotEmpty() && l.huellaKepub.isNotEmpty() && ul > 0) { pon("kc", l.marca, ul); pon("kh", l.huellaKepub, ul) }
            } else if (ul > 0) {
                if (ul > libro.campos["ul"].num().toLong()) pon("ul", ul, ul)
                if (abs(fr - libro.campos["fr"].num()) > 0.00005) pon("fr", fr, ul)
                if (estado.isNotEmpty() && estado != libro.texto("estado")) pon("estado", estado, if (l.estado == 2 && fin > 0) fin else ul)
                // En un kepub: por dónde vas tal como lo apunta el Kobo y de qué archivo (SesKobo salta ahí si tiene ese mismo archivo)
                if (l.marca.isNotEmpty() && l.huellaKepub.isNotEmpty() && (l.marca != libro.texto("kc") || l.huellaKepub != libro.texto("kh"))) {
                    pon("kc", l.marca, ul); pon("kh", l.huellaKepub, ul)
                }
                if (fin > 0 && l.estado == 2) {
                    val lista = libro.lista("fin")
                    if (lista.none { abs(it.num() - fin) < 36 * 3600 * 1000.0 }) pon("fin", lista + fin, fin)
                }
            }
            campos.entries.groupBy { it.value.second }.forEach { (hora, cs) -> cambios += Cambio("libros", k, cs.associate { it.key to it.value.first }, hora) }

            // El tiempo leído en el Kobo: su total y lo que ha crecido, en el día de la última lectura. La primera vez, solo el total.
            val total = l.segundos * 1000
            val ref = "$k~$disp"
            val tn = tiempos[ref]
            val viejo = tn?.campos?.get("total").num().toLong()
            var tiempoCambia = false
            if (total > viejo) {
                val hora = maxOf(ahora, (tn?.m?.values?.maxOrNull() ?: 0L) + 1)
                val (t, a) = if (libro != null && libro.texto("t").isNotEmpty()) libro.texto("t") to libro.texto("a") else l.titulo to l.autor
                val tc = linkedMapOf<String, Any?>("ref" to k, "fuente" to disp, "total" to total, "t" to t, "a" to a)
                if (tn != null) {
                    val dia = Instant.ofEpochMilli(if (ul > 0) ul else ahora).atZone(zona).toLocalDate()
                    val d = "d$dia"
                    tc[d] = tn.campos[d].num().toLong() + (total - viejo)
                }
                val sube = tc.filter { (c, v) -> tn == null || c == "total" || (c.length > 1 && c[0] == 'd') || tn.campos[c]?.toString() != v.toString() }
                cambios += Cambio("tiempo", ref, sube, hora)
                tiempoCambia = true
            }
            if (campos.isNotEmpty() || tiempoCambia) resumen += "${l.titulo}: ${cuanto(l)} · ${tiempoDe(l.segundos * 1000)}"
        }
        return Plan(cambios, resumen.sorted())
    }

    /** Un libro que copiaste al Kobo en kepub (los de la tienda no: su ContentID no es un archivo). */
    fun esKepub(contentId: String) = contentId.lowercase().let { it.startsWith("file:///mnt/onboard/") && it.endsWith(".kepub.epub") }

    private fun cuanto(l: Libro) = if (l.estado == 2) "terminado" else "${l.porcentaje.roundToLong()} %"

    /** Las fechas del Kobo («2024-01-15T14:30:00Z», «2024-01-15T14:30:00.000», «2024-01-15 14:30:00.000+00:00»…), en ms. Sin zona, UTC. */
    fun fecha(s: String?): Long {
        val t = s?.trim()?.replace(' ', 'T').orEmpty()
        if (t.isEmpty()) return 0L
        return runCatching { OffsetDateTime.parse(t, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(t, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]xx")).toInstant().toEpochMilli() } // («+0000», como Go)
            .recoverCatching { LocalDateTime.parse(t, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .recoverCatching { LocalDate.parse(t).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            .getOrDefault(0L)
    }
}
