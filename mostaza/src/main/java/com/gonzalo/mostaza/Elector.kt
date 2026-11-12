package com.gonzalo.mostaza

import java.text.Normalizer
import kotlin.math.floor
import java.util.Locale

/*
 * El elector de fuentes: de todas las que dan tus addons para una película o un episodio (directas o de tu debrid, y torrents), cuál
 * se pone al pulsar «Reproducir». El protocolo no dice ni el idioma, ni la calidad, ni las semillas de una fuente: solo hay texto libre
 * (`name`, `description`), el nombre del archivo y su tamaño (`behaviorHints.filename`, `videoSize`) y el «grupo» para el episodio
 * siguiente (`bingeGroup`). Así que aquí se LEE ese texto ([Analisis.de]) y se ordena ([Elector.ordena]):
 *
 *  1. el idioma primero: una fuente en un idioma que prefieres gana a cualquier calidad (tu orden: castellano, latino, original con
 *     subtítulos…). Lo que el reproductor ya ha visto en las pistas de verdad de una fuente ([Pistas], [MemoriaPistas]) manda sobre su
 *     texto; «MULTI» o «DUAL» sin más es «puede tener español»: detrás de las que lo tienen seguro y delante de las que no;
 *  2. luego la mejor calidad que cabe en tu conexión (lo que pesa por minuto frente a lo que mide este dispositivo) y que este
 *     dispositivo sabe reproducir (el códec, el HDR), sin pasar de la que hayas puesto como máxima; en un torrent, con semillas;
 *  3. luego las directas (o de tu debrid) antes que los torrents;
 *  4. y por último, el orden de tus addons y el de cada addon.
 * Para el episodio siguiente se prefiere el mismo `bingeGroup` que el anterior (lo que hace Stremio). Todo es Kotlin sin Android: se
 * prueba con títulos de verdad (ElectorTest).
 */

/** Un idioma de audio que se reconoce en el texto de una fuente. */
enum class Idioma(val etiqueta: String) {
    CASTELLANO("Castellano"), LATINO("Latino"), ESPANOL("Español"), INGLES("Inglés"), FRANCES("Francés"), ITALIANO("Italiano"),
    ALEMAN("Alemán"), PORTUGUES("Portugués"), JAPONES("Japonés"), COREANO("Coreano"), RUSO("Ruso"), CHINO("Chino"), MULTI("Multi"), DUAL("Dual"),
    /** Una pista de audio en un idioma que no se distingue (solo sale de las pistas de verdad, no del texto). */
    OTRO("Otro");

    val espanol: Boolean get() = this == CASTELLANO || this == LATINO || this == ESPANOL
}

/**
 * Lo seguro que es el idioma español que se lee del texto: [FUERTE] (lo dice con letras, «[Esp]», «Castellano», o solo trae 🇪🇸), [MEDIA]
 * (🇪🇸 entre pocas banderas, «Dual Audio», «Spanish English Subs»), [DEBIL] (🇪🇸 en una lista larga de banderas: casi siempre son los
 * subtítulos) o [COMPROBADA] (visto en las pistas de verdad del archivo, [Pistas]).
 */
enum class Confianza { DEBIL, MEDIA, FUERTE, COMPROBADA }

/** Lo que se entiende del texto de una fuente. */
data class Analisis(
    /** Líneas de la imagen: 2160, 1440, 1080, 720, 576, 480, 360; 0 = no se sabe. */
    val resolucion: Int = 0,
    val hdr: Boolean = false, val dv: Boolean = false,
    /** `hevc`, `av1`, `avc`, `vp9` o "" (no se sabe). */
    val codec: String = "",
    val idiomas: Set<Idioma> = emptySet(),
    /** Lleva subtítulos en español (VOSE, «subs esp»…). */
    val subtitulosEsp: Boolean = false,
    /** Bytes (0 = no se sabe). */
    val tamano: Long = 0L,
    /** Semillas de un torrent (-1 = no se sabe). */
    val semillas: Int = -1,
    /** Lo marca el addon como ya en la caché de tu debrid (true), como «por bajar» (false), o no dice nada (null). */
    val enCache: Boolean? = null,
    /** Grabada en el cine o de una emisión de mala calidad (CAM, TS…). */
    val mala: Boolean = false,
    val tresD: Boolean = false,
    /** Lo seguro que es el idioma español leído (solo cuenta si [tieneEspanol]). */
    val confianza: Confianza = Confianza.FUERTE,
    /** Reescalada (por IA o no): no es la calidad que dice. */
    val reescalada: Boolean = false,
    /** De 10 bits («10bit», «Hi10P»). En H.264 casi ningún decodificador de Android lo lee. */
    val diezBits: Boolean = false,
) {
    val tieneEspanol: Boolean get() = Idioma.CASTELLANO in idiomas || Idioma.LATINO in idiomas || Idioma.ESPANOL in idiomas
    /** «MULTI» o «DUAL» sin decir cuáles: puede tener español (o no). */
    val puedeTenerEspanol: Boolean get() = !tieneEspanol && (Idioma.MULTI in idiomas || Idioma.DUAL in idiomas)
    /** Lo que se enseña: «1080p · Castellano · 2,1 GB». */
    fun resumen(): String = listOfNotNull(
        etiquetaResolucion(resolucion).takeIf { it.isNotBlank() },
        if (hdr || dv) (if (dv) "Dolby Vision" else "HDR") else null,
        idiomaPrincipal()?.let { i ->
            when { !i.espanol -> i.etiqueta; confianza == Confianza.DEBIL -> "¿${i.etiqueta}?"; confianza == Confianza.COMPROBADA -> "${i.etiqueta} ✓"; else -> i.etiqueta }
        } ?: if (subtitulosEsp) "VOSE" else null,
        if (tamano > 0) tamanoTexto(tamano) else null,
    ).joinToString(" · ")

    fun idiomaPrincipal(): Idioma? = listOf(Idioma.CASTELLANO, Idioma.LATINO, Idioma.ESPANOL, Idioma.INGLES, Idioma.FRANCES, Idioma.ITALIANO, Idioma.ALEMAN,
        Idioma.PORTUGUES, Idioma.JAPONES, Idioma.COREANO, Idioma.RUSO, Idioma.CHINO, Idioma.MULTI, Idioma.DUAL).firstOrNull { it in idiomas }

    companion object {
        fun etiquetaResolucion(r: Int) = when { r >= 2160 -> "4K"; r >= 1440 -> "1440p"; r >= 1080 -> "1080p"; r >= 720 -> "720p"; r > 0 -> "${r}p"; else -> "" }

        /** «2,1 GB», «700 MB» (con coma: en español). */
        fun tamanoTexto(b: Long): String = when {
            b >= 1_000_000_000L -> String.format(Locale("es", "ES"), "%.1f GB", b / 1e9)
            b >= 1_000_000L -> "${b / 1_000_000L} MB"
            else -> "${(b / 1000L).coerceAtLeast(1)} KB"
        }

        private fun normal(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

        /** Las banderas (dos letras regionales seguidas) del texto, como códigos de país («ES», «MX»…). */
        fun banderas(s: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            val cps = s.codePoints().toArray()
            while (i < cps.size - 1) {
                val a = cps[i]; val b = cps[i + 1]
                if (a in 0x1F1E6..0x1F1FF && b in 0x1F1E6..0x1F1FF) { out += "" + ('A' + (a - 0x1F1E6)) + ('A' + (b - 0x1F1E6)); i += 2 } else i++
            }
            return out
        }

        private val LATAM = setOf("MX", "AR", "CO", "CL", "PE", "VE", "UY", "PY", "BO", "EC", "GT", "CR", "PA", "DO", "CU", "HN", "NI", "SV", "PR")
        private val INGLESES = setOf("GB", "US", "AU", "CA", "IE", "NZ", "UK")

        private fun idiomaDePais(p: String): Idioma? = when (p) {
            "ES" -> Idioma.CASTELLANO; in LATAM -> Idioma.LATINO; in INGLESES -> Idioma.INGLES; "FR" -> Idioma.FRANCES; "IT" -> Idioma.ITALIANO
            "DE", "AT" -> Idioma.ALEMAN; "PT", "BR" -> Idioma.PORTUGUES; "JP" -> Idioma.JAPONES; "KR" -> Idioma.COREANO; "RU" -> Idioma.RUSO; "CN", "TW", "HK" -> Idioma.CHINO
            else -> null
        }

        private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)
        // Los separadores de un nombre de archivo (puntos, guiones bajos, corchetes) cuentan como espacios
        private const val B = "(?<![a-z0-9])"
        private const val E = "(?![a-z0-9])"
        private val SUBS_ESP = listOf(
            r("${B}(vose|vosE|v\\.o\\.s\\.e)$E"),
            r("${B}(sub(s|titulos?|titles|titulado|titulada)?|vos)[ ._\\-]*(esp(anol)?|spa(nish)?|castellano|es|cast)$E"),
            r("${B}(esp(anol)?|spa(nish)?|castellano)[ ._\\-]*(sub(s|titulos?|titles|titulado)?|subbed)$E"),
            r("${B}subtitulad[oa]$E"),
        )
        private val CASTELLANO = r("${B}(castellano|castilian|spain|es[-_]es|spa[-_]es|cast|espana)$E")
        private val LATINO = r("${B}(latino|latina|lat|latam|latin ?america(n)?|es[-_](mx|419|la)|spa[-_]la|mexicano)$E")
        private val ESPANOL = r("${B}(espanol|spanish|esp|spa)$E")
        private val INGLES = r("${B}(english|eng|ingles|en[-_](us|gb))$E")
        private val OTROS = listOf(
            Idioma.FRANCES to r("${B}(french|francais|frances|vff|vfi|truefrench)$E"), Idioma.ITALIANO to r("${B}(italian|italiano|ita)$E"),
            Idioma.ALEMAN to r("${B}(german|deutsch|aleman|ger)$E"), Idioma.PORTUGUES to r("${B}(portuguese|portugues|pt[-_]br|dublado)$E"),
            Idioma.JAPONES to r("${B}(japanese|japones|jpn)$E"), Idioma.COREANO to r("${B}(korean|coreano|kor)$E"), Idioma.RUSO to r("${B}(russian|ruso|rus)$E"),
            Idioma.CHINO to r("${B}(chinese|chino|mandarin)$E"),
        )

        /** Lo que dice el texto de una fuente (y su tamaño, si el addon lo da). */
        fun de(f: Fuente): Analisis = de(f.texto, f.tamano)

        fun de(texto: String, tamanoHint: Long = 0L): Analisis {
            val t = normal(texto)
            // ---- calidad
            val res = when {
                r("${B}(4320p|8k|2160p|4k|uhd|2160)$E").containsMatchIn(t) -> 2160
                r("${B}(1440p|2k|qhd)$E").containsMatchIn(t) -> 1440
                r("${B}(1080p|1080i|fhd|full ?hd|1080)$E").containsMatchIn(t) -> 1080
                r("${B}(720p|hd ?ready|720)$E").containsMatchIn(t) -> 720
                r("${B}(576p|576)$E").containsMatchIn(t) -> 576
                r("${B}(480p|sd|dvdrip|dvd|480)$E").containsMatchIn(t) -> 480
                r("${B}(360p|360|240p)$E").containsMatchIn(t) -> 360
                else -> 0
            }
            // (sin «ts» ni «tc» sueltos: un archivo .ts no es una grabación de cine)
            val mala = r("${B}(cam|camrip|hdcam|telesync|hdts|hdtc|telecine|screener|dvdscr)$E").containsMatchIn(t)
            val dv = r("${B}(dv|dovi|dolby ?vision)$E").containsMatchIn(t)
            val hdr = r("${B}(hdr|hdr10|hdr10\\+|hdr10plus|hlg)$E").containsMatchIn(t) || t.contains("hdr10+")
            val codec = when {
                r("${B}(x265|h ?\\.?265|hevc)$E").containsMatchIn(t) -> "hevc"
                r("${B}(av1)$E").containsMatchIn(t) -> "av1"
                r("${B}(vp9)$E").containsMatchIn(t) -> "vp9"
                r("${B}(x264|h ?\\.?264|avc)$E").containsMatchIn(t) -> "avc"
                else -> ""
            }
            // ---- idiomas (los subtítulos primero, y se quitan: «Spanish subs» no es audio en español)
            var resto = t
            var subs = false
            for (p in SUBS_ESP) if (p.containsMatchIn(resto)) { subs = true; resto = p.replace(resto, " ") }
            val idiomas = LinkedHashSet<Idioma>()
            val paises = banderas(texto).distinct()
            val dePaises = paises.mapNotNull { idiomaDePais(it) }.toSet()
            val txtCastellano = CASTELLANO.containsMatchIn(resto)
            val txtLatino = LATINO.containsMatchIn(resto)
            val txtEspanol = ESPANOL.containsMatchIn(resto)
            val txtOtros = INGLES.containsMatchIn(resto) || OTROS.any { it.second.containsMatchIn(resto) }
            idiomas += dePaises
            // Torrentio pone 🇪🇸 para cualquier español: con una bandera latinoamericana (o «latino» en el texto), esa 🇪🇸 no es castellano
            if ((Idioma.LATINO in dePaises || txtLatino) && !txtCastellano) idiomas -= Idioma.CASTELLANO
            if (txtCastellano) idiomas += Idioma.CASTELLANO
            if (txtLatino) idiomas += Idioma.LATINO
            if (txtEspanol && Idioma.CASTELLANO !in idiomas && Idioma.LATINO !in idiomas) idiomas += Idioma.ESPANOL
            if (INGLES.containsMatchIn(resto)) idiomas += Idioma.INGLES
            for ((i, p) in OTROS) if (p.containsMatchIn(resto)) idiomas += i
            if (r("${B}(multi|multi[ -]?audio|multilang|multidub|multi[ -]?subs?)$E").containsMatchIn(resto)) idiomas += Idioma.MULTI
            if (r("${B}(dual|dual[ -]?audio)$E").containsMatchIn(resto)) idiomas += Idioma.DUAL
            // ---- lo seguro que es el español que se ha leído
            val confianza = when {
                txtCastellano || txtLatino -> Confianza.FUERTE                                // lo dice con todas las letras
                txtEspanol && !txtOtros && paises.size <= 1 -> Confianza.FUERTE               // «[Esp]» (y como mucho 🇪🇸)
                txtEspanol -> Confianza.MEDIA                                                 // «Spanish English Subs»: ¿cuál es el audio?
                paises.size <= 1 -> Confianza.FUERTE                                          // solo 🇪🇸
                paises.size <= 4 -> Confianza.MEDIA                                           // 🇬🇧 / 🇪🇸 / 🇫🇷
                else -> Confianza.DEBIL                                                       // una lista larga de banderas: casi siempre son subtítulos
            }
            val reescalada = r("${B}(ai[ ._-]?upscal(e|ed)?|upscal(e|ed)|ai[ ._-]?enhanced)$E").containsMatchIn(t)
            // ---- tamaño, semillas y caché del debrid
            val tam = tamanoHint.takeIf { it > 0 } ?: tamanoDe(texto)
            val sem = Regex("(?:👤|👥|seeders?|seeds?|peers?)\\s*:?\\s*(\\d+)", RegexOption.IGNORE_CASE).find(texto)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            val cache = when {
                Regex("\\[[A-Za-z]{2,4}\\+\\]|\\b(RD|AD|PM|DL|TB|ED|OC|PKP|EN)\\+|⚡|\\binstant\\b|\\bcached\\b", RegexOption.IGNORE_CASE).containsMatchIn(texto) -> true
                Regex("\\[[A-Za-z]{2,4}\\s*(download|dl|⏳)\\]|⏳|\\bnot cached\\b|\\buncached\\b", RegexOption.IGNORE_CASE).containsMatchIn(texto) -> false
                else -> null
            }
            return Analisis(if (mala && res == 0) 360 else res, hdr, dv, codec, idiomas, subs, tam, sem, cache, mala, r("${B}(3d|sbs|half[ -]?sbs)$E").containsMatchIn(t),
                confianza, reescalada, r("${B}(10 ?bits?|hi10p?)$E").containsMatchIn(t))
        }

        /** «2.1 GB», «1,5 GB», «700 MB», «4.37 GiB». 0 si no lo dice. */
        fun tamanoDe(s: String): Long {
            val m = Regex("(\\d+(?:[.,]\\d+)?)\\s*(TB|TiB|GB|GiB|MB|MiB|KB|KiB)\\b", RegexOption.IGNORE_CASE).find(s) ?: return 0L
            val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
            val u = m.groupValues[2].lowercase()
            val f = when { u.startsWith("t") -> 1e12; u.startsWith("g") -> 1e9; u.startsWith("m") -> 1e6; else -> 1e3 } * (if (u.contains('i')) 1.024 * 1.024 else 1.0)
            return (n * f).toLong()
        }
    }
}

/** Lo que prefieres al elegir fuente (viaja con la cuenta: `ajustes/mostaza_elector`). */
data class PrefsElector(
    /** El orden de los idiomas: `castellano`, `latino`, `vose` (versión original con subtítulos en español) y `original`. */
    val idiomas: List<String> = listOf("castellano", "latino", "vose"),
    /** ¿Vale una fuente solo con subtítulos (sin audio en español)? */
    val subtitulados: Boolean = true,
    /** La calidad máxima (líneas: 2160, 1080, 720, 480). */
    val maxima: Int = 2160,
    /** Las directas (o de tu debrid) antes que los torrents. */
    val directasPrimero: Boolean = true,
    /** Con datos del móvil: como mucho 720p y lo que menos pese. */
    val ahorroDatos: Boolean = true,
) {
    companion object {
        val IDIOMAS = listOf("castellano" to "Castellano", "latino" to "Latino", "vose" to "Original con subtítulos", "original" to "Original")
        fun nombre(id: String) = IDIOMAS.firstOrNull { it.first == id }?.second ?: id
    }
}

/** Cómo es la conexión ahora mismo (en este dispositivo): [kbps] lo medido aquí (o lo que estima Android; 0 = no se sabe). */
data class Red(val kbps: Long = 0L, val movil: Boolean = false, val medida: Boolean = false)

/** Lo que sabe reproducir este dispositivo (de `MediaCodecList`). */
data class Capacidades(val hevc: Boolean = true, val av1: Boolean = false, val vp9: Boolean = true, val hdr: Boolean = false, val dv: Boolean = false, val maxima: Int = 2160,
    /** Algún decodificador lee H.264 de 10 bits («Hi10P»; casi ninguno de Android). */
    val avc10: Boolean = true) {
    fun reproduce(a: Analisis): Boolean = when {
        a.codec == "hevc" && !hevc -> false
        a.codec == "av1" && !av1 -> false
        a.codec == "vp9" && !vp9 -> false
        a.dv && !dv && !a.hdr && !hdr -> false     // Dolby Vision sin capa HDR10 en un dispositivo que no lo entiende: colores verdes y morados
        a.resolucion > maxima -> false
        else -> true
    }
}

/**
 * Una fuente con su addon y lo que se ha entendido de ella. [verificacion]: lo que se vio en sus pistas de verdad la última vez que se abrió
 * en este dispositivo ([MemoriaPistas]); si está, manda sobre lo que dice el texto (y [analisis] ya lleva esos idiomas).
 */
data class Candidata(val fuente: Fuente, val addon: String, val ordenAddon: Int, val ordenEnAddon: Int, val analisis: Analisis = Analisis.de(fuente), val verificacion: Verificacion? = null)

/** La fuente elegida y por qué. */
data class Eleccion(val elegida: Candidata?, val ordenadas: List<Candidata>, val fueraDeIdioma: Boolean, val porGrupo: Boolean) {
    val resumen: String get() = elegida?.analisis?.resumen().orEmpty()
}

object Elector {
    /** Lo que suele pesar por segundo cada calidad (kbit/s) cuando el addon no dice el tamaño. */
    fun tipicoKbps(res: Int): Long = when { res >= 2160 -> 25_000L; res >= 1440 -> 14_000L; res >= 1080 -> 9_000L; res >= 720 -> 4_500L; res > 0 -> 1_800L; else -> 6_000L }

    /** Lo que pesa por segundo (kbit/s): tamaño / duración; sin uno de los dos, lo típico de su calidad. */
    fun kbps(a: Analisis, minutos: Int): Long = if (a.tamano > 0 && minutos > 0) a.tamano * 8 / (minutos * 60L) / 1000L else tipicoKbps(a.resolucion)

    /** El margen sobre lo que pesa por segundo que tiene que dejar la conexión (para que no se corte). */
    const val MARGEN = 1.35
    /** Semillas mínimas de un torrent para darlo por bueno (si no lo tiene ya tu debrid). */
    const val SEMILLAS = 4

    /** Tu orden de idiomas tal como cuenta (sin «vose» si no aceptas subtítulos). */
    fun ordenEfectivo(p: PrefsElector): List<String> = p.idiomas.filter { it != "vose" || p.subtitulados }

    /**
     * La clave de una fuente para recordar lo comprobado de ella ([MemoriaPistas]), sin secretos: el `infoHash` y el archivo (también si
     * va dentro de la dirección de tu debrid), o el nombre del archivo y su tamaño, o una huella (SHA-256) de la dirección sin lo que va
     * detrás de «?» (las claves de tu debrid nunca se guardan tal cual).
     */
    fun clave(f: Fuente): String {
        if (f.infoHash.isNotBlank()) return "bt:${f.infoHash.lowercase()}:${f.fileIdx ?: ""}"
        if (f.url.isBlank()) return ""
        val hash = Regex("(?<![0-9a-fA-F])[0-9a-fA-F]{40}(?![0-9a-fA-F])").find(f.url)?.value?.lowercase()
        if (hash != null) return "bt:$hash:${f.fileIdx?.toString() ?: f.archivo.lowercase()}"
        if (f.archivo.isNotBlank() && f.tamano > 0) return "f:${f.archivo.lowercase()}|${f.tamano}"
        val sin = f.url.substringBefore('#').substringBefore('?')
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(sin.toByteArray())
        return "u:" + d.take(16).joinToString("") { "%02x".format(it) }
    }

    /** El puesto de una candidata: lo comprobado en sus pistas si se sabe ([Pistas.puesto]); si no, lo que dice su texto. */
    fun puesto(c: Candidata, p: PrefsElector): Double = c.verificacion?.let { Pistas.puesto(it, p) } ?: puestoIdioma(c.analisis, p)

    /** Las candidatas con lo que se recuerda de ellas ([verificados]: clave → lo comprobado). */
    fun conVerificados(cs: List<Candidata>, verificados: (String) -> Verificacion?): List<Candidata> = cs.map { c ->
        val v = verificados(clave(c.fuente)) ?: return@map c
        c.copy(verificacion = v, analisis = c.analisis.copy(idiomas = v.idiomas - Idioma.OTRO, subtitulosEsp = v.subtitulosEsp || c.analisis.subtitulosEsp, confianza = Confianza.COMPROBADA))
    }

    /** ¿Qué puesto tiene en tu orden de idiomas? (más bajo = mejor; `orden.size` = ninguno de los tuyos). */
    fun puestoIdioma(a: Analisis, p: PrefsElector): Double {
        // Un español poco seguro (🇪🇸 entre muchas banderas) cuenta como «MULTI»: puede tenerlo, o no
        if (a.tieneEspanol && a.confianza == Confianza.DEBIL)
            return puestoIdioma(a.copy(idiomas = a.idiomas.filterNot { it.espanol }.toSet() + Idioma.MULTI, confianza = Confianza.FUERTE), p)
        // Uno a medias va detrás del seguro del mismo idioma (pero delante del siguiente de tu orden)
        val extra = if (a.tieneEspanol && a.confianza == Confianza.MEDIA) 0.2 else 0.0
        val orden = ordenEfectivo(p)
        var mejor = orden.size.toDouble()
        orden.forEachIndexed { i, id ->
            val v: Double? = when (id) {
                "castellano" -> if (Idioma.CASTELLANO in a.idiomas) extra else if (Idioma.ESPANOL in a.idiomas) 0.5 + extra else null
                "latino" -> if (Idioma.LATINO in a.idiomas) extra else if (Idioma.ESPANOL in a.idiomas) 0.5 + extra else null
                // Con subtítulos: la original (sin audio en español) con subtítulos dentro, o sin ellos (los pondrán tus addons de subtítulos)
                "vose" -> if (!a.tieneEspanol) (if (a.subtitulosEsp) 0.0 else 0.25) else null
                "original" -> if (!a.tieneEspanol) 0.0 else null
                else -> null
            }
            if (v != null) mejor = minOf(mejor, i + v)
        }
        // «MULTI» o «DUAL» sin decir cuáles: puede tener español. Detrás de las que lo tienen seguro (los españoles que pones primero en tu
        // orden), delante de las que seguro que no
        val bloque = orden.takeWhile { it == "castellano" || it == "latino" }.size
        if (a.puedeTenerEspanol && bloque > 0) mejor = minOf(mejor, bloque - 1 + 0.75)
        return mejor
    }

    /** ¿Cabe en la conexión, la sabe reproducir el dispositivo y no pasa de lo que has puesto? (y un torrent, con semillas) */
    fun cabe(c: Candidata, p: PrefsElector, red: Red, cap: Capacidades, minutos: Int): Boolean {
        val a = c.analisis
        val maxima = if (red.movil && p.ahorroDatos) minOf(p.maxima, 720) else p.maxima
        if (a.resolucion > maxima) return false
        if (!cap.reproduce(a)) return false
        if (red.kbps > 0 && kbps(a, minutos) * MARGEN > red.kbps) return false
        val f = c.fuente
        if (f.tipo == TipoFuente.TORRENT && a.enCache != true && a.semillas in 0 until SEMILLAS) return false
        if (f.tipo == TipoFuente.DIRECTA && a.enCache == false) return false   // de tu debrid, pero aún por bajar: no se puede ver ya
        return true
    }

    /** Solo se elige sola una fuente que se puede poner aquí (directa o, con el motor de torrents, un torrent). */
    private fun reproducible(c: Candidata, torrents: Boolean) = when (c.fuente.tipo) { TipoFuente.DIRECTA -> true; TipoFuente.TORRENT -> torrents; else -> false }

    /**
     * Todas las fuentes, de mejor a peor, y la elegida. [grupoAnterior]: el `bingeGroup` de la fuente del episodio anterior (se prefiere
     * el mismo). [torrents]: si hay motor de torrents (sin él, los torrents nunca se eligen solos).
     */
    fun elige(cs: List<Candidata>, p: PrefsElector, red: Red, cap: Capacidades, minutos: Int, torrents: Boolean, grupoAnterior: String = "",
              verificados: (String) -> Verificacion? = { null }, rotas: (String) -> Boolean = { false }): Eleccion {
        // (las que dan un vídeo de aviso en vez de la película, [FuentesRotas]: al final, y no se eligen solas)
        val ord = ordena(conVerificados(cs, verificados), p, red, cap, minutos, torrents).let { o -> o.partition { !rotas(clave(it.fuente)) }.let { (b, r) -> b + r } }
        val jugables = ord.filter { reproducible(it, torrents) && !rotas(clave(it.fuente)) }
        val delGrupo = if (grupoAnterior.isNotBlank()) jugables.firstOrNull { it.fuente.grupo == grupoAnterior && cap.reproduce(it.analisis) } else null
        val e = delGrupo ?: jugables.firstOrNull()
        val fuera = e != null && puesto(e, p) >= ordenEfectivo(p).size
        return Eleccion(e, ord, fuera, delGrupo != null)
    }

    fun ordena(cs: List<Candidata>, p: PrefsElector, red: Red, cap: Capacidades, minutos: Int, torrents: Boolean = true): List<Candidata> {
        val maxima = if (red.movil && p.ahorroDatos) minOf(p.maxima, 720) else p.maxima
        return cs.sortedWith(
            compareBy<Candidata> { if (reproducible(it, torrents)) 0 else 1 }
                // Lo de tu kDrive, lo primero: es lo que tú has puesto ahí
                .thenBy { if (it.ordenAddon < 0) 0 else 1 }
                // Tu idioma (el puesto en tu orden); dentro del mismo, nunca una grabada en el cine (CAM, TS…) ni una reescalada si hay otra
                .thenBy { floor(puesto(it, p)) }
                .thenBy { if (it.analisis.mala) 2 else if (it.analisis.reescalada) 1 else 0 }
                // Y lo seguro que es (lo comprobado, «[Esp]» o solo 🇪🇸, antes que 🇪🇸 entre otras banderas)
                .thenBy { puesto(it, p) }
                // (a igual idioma, la que ya se ha comprobado en este dispositivo: se sabe que es así)
                .thenBy { if (it.verificacion != null) 0 else 1 }
                .thenBy { if (cabe(it, p, red, cap, minutos)) 0 else 1 }
                // Dentro de lo que cabe, la mejor calidad (y en lo que no cabe, lo que menos pesa: es lo que más fácil se verá)
                .thenByDescending { if (cabe(it, p, red, cap, minutos)) it.analisis.resolucion.coerceAtMost(maxima) else -kbps(it.analisis, minutos).toInt() }
                .thenBy { if (red.movil && p.ahorroDatos) kbps(it.analisis, minutos) else 0L }
                .thenBy { if (p.directasPrimero && it.fuente.tipo != TipoFuente.DIRECTA) 1 else 0 }
                .thenBy { it.ordenAddon }
                .thenBy { it.ordenEnAddon }
        )
    }
}
