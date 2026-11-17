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
        /**
         * El tiempo leído como lo lleva el programa del Kobo ([porDias]): el total en ms y todo lo leído cada día que ha visto. Por cable,
         * sin ellos (el total es [segundos] y lo nuevo va al día de la última lectura).
         */
        val porDias: Boolean = false,
        val total: Long = 0,
        val dias: Map<String, Long> = emptyMap(),
    )

    /**
     * Junta los libros del Kobo que son el mismo para la cuenta (la muestra y el comprado, el EPUB y el kepub): queda el leído más tarde,
     * con el tiempo de todos sumado y la fecha de terminado más reciente. Como `agrupa` de kobo/kobo.go.
     */
    fun agrupa(libros: List<Libro>, clave: (Libro) -> String): List<Libro> {
        val m = LinkedHashMap<String, Libro>()
        for (l in libros) {
            val k = clave(l)
            val a = m[k] ?: run { m[k] = l; null } ?: continue
            val dias = HashMap<String, Long>().apply { for (x in listOf(a.dias, l.dias)) for ((d, v) in x) merge(d, v, Long::plus) }
            val base = if (l.ul > a.ul) l else a
            m[k] = base.copy(segundos = a.segundos + l.segundos, total = a.total + l.total, dias = dias, terminado = maxOf(a.terminado, l.terminado),
                porDias = a.porDias || l.porDias)
        }
        return m.values.toList()
    }

    /**
     * Un subrayado o una nota del Kobo (tabla `Bookmark` de KoboReader.sqlite): de qué libro ([volumen], su ContentID), en qué capítulo
     * ([contenido]) y dónde, tal como lo apunta el Kobo: en un kepub, la frase («span#kobo\\.12\\.3») y las letras dentro; en un EPUB, el
     * punto de Adobe («capítulo.xhtml#point(/1/4/10/1:14)»).
     */
    data class Subrayado(
        val id: String, val volumen: String, val contenido: String, val ini: String, val ioff: Int, val fin: String, val foff: Int,
        val texto: String, val nota: String, val creado: Long, val cambiado: Long,
    )

    /** El capítulo de un subrayado desde lo que apunta el Kobo (kepub: «…kepub.epub!OEBPS!text/c.xhtml»; EPUB: «…epub#(1)OEBPS/text/c.xhtml»). */
    fun capituloDe(s: Subrayado): String {
        val c = s.contenido
        if (c.contains('!')) return c.split('!').drop(1).joinToString("/")
        Regex("""#\(\d+\)(.+)$""").find(c)?.let { return it.groupValues[1] }
        return s.ini.substringBefore('#').ifBlank { s.fin.substringBefore('#') }
    }

    /**
     * Los subrayados del Kobo que faltan en la cuenta, como los guarda Seshat (`marcas/<kid>~<id>`, tipo «notes»). Llegan con la posición
     * del Kobo («kobo:…» y `kobo`): el lector de Seshat los pone en su sitio al abrir el libro (lector.js, subrayadosDelKobo) y desde ahí
     * son subrayados normales. Los que ya estuvieron en la cuenta (aunque luego se quitaran o se pusieran en su sitio) no vuelven.
     */
    fun planeaSubrayados(subs: List<Subrayado>, kobo: List<Libro>, libros: Map<String, Nodo>, yaEstan: (String) -> Boolean): List<Cambio> {
        val porVolumen = kobo.filter { it.contentId.isNotBlank() }.associateBy { it.contentId }
        val out = ArrayList<Cambio>()
        for (s in subs) {
            val l = porVolumen[s.volumen] ?: continue
            if (s.texto.isBlank() && s.nota.isBlank()) continue
            val kid = buscaKid(l, libros)
            val valor = "kobo:" + s.id
            val creado = if (s.creado > 0) s.creado else s.cambiado
            // (el mismo id que le daría Seshat: el de su valor y su hora de creación, MezclaLectura.clave)
            val id = kid + "~" + java.security.MessageDigest.getInstance("SHA-1").digest("$valor|$creado".toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
            if (yaEstan(id)) continue
            val n = linkedMapOf<String, Any?>("value" to valor, "color" to "subrayado", "text" to s.texto.trim(), "note" to s.nota.trim(), "created" to creado,
                "kobo" to mapOf("cap" to capituloDe(s), "ini" to s.ini, "ioff" to s.ioff, "fin" to s.fin, "foff" to s.foff))
            out += Cambio("marcas", id, mapOf("ref" to kid, "tipo" to "notes", "n" to n, "b" to 0L), maxOf(creado, s.cambiado, 1L))
        }
        return out
    }

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
    fun planea(kobo: List<Libro>, libros: Map<String, Nodo>, tiempos: Map<String, Nodo>, ahora: Long, zona: ZoneId, disp: String = DISPOSITIVO,
               conTiempo: Boolean = true): Plan {
        val cambios = ArrayList<Cambio>()
        val resumen = ArrayList<String>()
        // Dos libros del Kobo que son el mismo de la cuenta, uno solo (si no, el segundo pisaría al primero y el tiempo contaría dos veces)
        val juntos = agrupa(kobo.map { it.copy(kid = it.kid.ifBlank { buscaKid(it, libros) }) }) { it.kid }
        for (l in juntos) {
            val k = l.kid
            val libro = libros[k]
            if (libro != null && libro.borrado) continue // borrado en Seshat: no se resucita
            // (nunca en el futuro: con el reloj del Kobo adelantado, la cuenta no aceptaría cambios de Seshat hasta esa hora)
            val ul = minOf(l.ul, ahora); val fin = minOf(l.terminado, ahora)
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

            // El tiempo leído en el Kobo (como `planea` de kobo/plan.go). Sin [conTiempo] (por cable, con el programa del Kobo puesto: lo
            // lleva él, día a día), nada
            var total = if (l.total > 0 || l.porDias) l.total else l.segundos * 1000
            val ref = "$k~$disp"
            val tn = tiempos[ref]
            val viejo = tn?.campos?.get("total").num().toLong()
            val dias = HashMap<String, Long>()
            val diaUl = { Instant.ofEpochMilli(if (ul > 0) ul else ahora).atZone(zona).toLocalDate().toString() }
            if (l.porDias) {
                // Lo que apunta día a día el programa del Kobo: `k<día>` dice cuánto de `d<día>` es suyo, y se pone al día solo su parte
                var explicado = 0L
                for ((dia, v) in l.dias) { val kd = tn?.campos?.get("k$dia").num().toLong(); if (v > kd) { dias[dia] = v - kd; explicado += v - kd } }
                // Lo que el total ha crecido sin estar en ningún día: al de la última lectura (la primera vez, solo el total)
                if (tn != null && total - viejo > explicado) dias.merge(diaUl(), total - viejo - explicado, Long::plus)
            } else if (total > viejo && tn != null) dias.merge(diaUl(), total - viejo, Long::plus)
            var tiempoCambia = false
            if (conTiempo && (total > viejo || dias.isNotEmpty())) {
                val hora = maxOf(ahora, (tn?.m?.values?.maxOrNull() ?: 0L) + 1)
                val t = libro?.texto("t")?.takeIf { it.isNotEmpty() } ?: l.titulo
                val a = libro?.texto("a")?.takeIf { it.isNotEmpty() && libro.texto("t").isNotEmpty() } ?: l.autor
                val tc = linkedMapOf<String, Any?>("ref" to k, "fuente" to disp, "t" to t, "a" to a)
                if (total > viejo) tc["total"] = total
                for ((dia, v) in dias) tc["d$dia"] = tn?.campos?.get("d$dia").num().toLong() + v
                if (l.porDias) for ((dia, v) in l.dias) if (v > tn?.campos?.get("k$dia").num().toLong()) tc["k$dia"] = v
                val sube = tc.filter { (c, v) -> tn == null || c !in setOf("ref", "fuente", "t", "a") || tn.campos[c]?.toString() != v.toString() }
                cambios += Cambio("tiempo", ref, sube, hora)
                tiempoCambia = true
            }
            if (total < viejo) total = viejo
            if (campos.isNotEmpty() || tiempoCambia) resumen += "${l.titulo}: ${cuanto(l)} · ${tiempoDe(total)}"
        }
        return Plan(cambios, resumen.sorted())
    }

    private val CON_ZONA_SIN_DOS_PUNTOS = java.time.format.DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .optionalStart().appendFraction(java.time.temporal.ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd().appendOffset("+HHMM", "+0000").toFormatter()

    /** Un libro que copiaste al Kobo en kepub (los de la tienda no: su ContentID no es un archivo). */
    fun esKepub(contentId: String) = contentId.lowercase().let { it.startsWith("file:///mnt/onboard/") && it.endsWith(".kepub.epub") }

    private fun cuanto(l: Libro) = if (l.estado == 2) "terminado" else "${l.porcentaje.roundToLong()} %"

    /** Las fechas del Kobo («2024-01-15T14:30:00Z», «2024-01-15T14:30:00.000», «2024-01-15 14:30:00.000+00:00»…), en ms. Sin zona, UTC. */
    fun fecha(s: String?): Long {
        val t = s?.trim()?.replace(' ', 'T').orEmpty()
        if (t.isEmpty()) return 0L
        return runCatching { OffsetDateTime.parse(t, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(t, CON_ZONA_SIN_DOS_PUNTOS).toInstant().toEpochMilli() } // («+0000», como Go)
            .recoverCatching { LocalDateTime.parse(t, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .recoverCatching { LocalDate.parse(t).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            .getOrDefault(0L)
    }
}
