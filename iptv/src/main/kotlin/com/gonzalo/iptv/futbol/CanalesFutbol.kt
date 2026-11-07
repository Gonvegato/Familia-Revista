package com.gonzalo.iptv.futbol

import com.gonzalo.iptv.JsonFlujo
import com.gonzalo.iptv.lista
import com.gonzalo.iptv.mapa
import com.gonzalo.iptv.txt

/**
 * Un canal de tus listas, apuntado sin nada secreto: el id de la lista, la clave del canal ([com.gonzalo.iptv.Canal.clave]: `x<id>` en
 * Xtream, `c<16 hex>` en M3U; nunca su dirección) y su nombre (para enseñarlo y para encontrarlo si la clave cambia).
 */
data class RefCanal(val lista: String, val canal: String, val nombre: String)

/** Las calidades de un mismo canal (en las listas suelen venir como canales distintos: «… UHD», «… HD», «… SD»). */
enum class Calidad(val texto: String, val clave: String) {
    UHD("Ultra HD", "u"), HD("HD", "h"), SD("SD", "s");
    companion object { fun de(s: String?): Calidad? = entries.firstOrNull { it.name.equals(s, true) || it.clave == s } }
}

/** Una entrada de un grupo: el mismo canal en hasta tres calidades (cualquiera puede faltar). */
data class Entrada(val uhd: RefCanal? = null, val hd: RefCanal? = null, val sd: RefCanal? = null) {
    fun en(c: Calidad): RefCanal? = when (c) { Calidad.UHD -> uhd; Calidad.HD -> hd; Calidad.SD -> sd }
    fun con(c: Calidad, r: RefCanal?): Entrada = when (c) { Calidad.UHD -> copy(uhd = r); Calidad.HD -> copy(hd = r); Calidad.SD -> copy(sd = r) }
    /** Las calidades que tiene, de la mejor a la peor. */
    val calidades: List<Calidad> get() = Calidad.entries.filter { en(it) != null }
    val todas: List<RefCanal> get() = listOfNotNull(uhd, hd, sd)
    val vacia: Boolean get() = todas.isEmpty()
    /** El nombre que se enseña: el del canal sin la calidad («ES: DAZN LA LIGA»). */
    val nombre: String get() = todas.firstOrNull()?.let { Calidades.base(it.nombre) }.orEmpty()
}

/**
 * Un grupo de canales tuyo para el fútbol («Movistar LaLiga», «DAZN LaLiga»…): su nombre y sus entradas en tu orden (cada una, el mismo
 * canal en sus calidades). En cada partido hay un botón por cada grupo de su competición: un toque y se pone la primera entrada (en la
 * calidad que toque, [Calidades.elige]); las demás son el 2, el 3…
 */
data class GrupoCanales(val id: String, val nombre: String, val canales: List<Entrada> = emptyList()) {
    /** Lo que pone el botón: el nombre sin «LaLiga» («Movistar», «DAZN»); si no queda nada, el nombre entero. */
    val boton: String get() = nombre.replace(Regex("(?i)\\b(la ?liga)\\b"), "").replace(Regex("\\s+"), " ").trim().ifBlank { nombre }
}

/** Cómo está la conexión ahora (de Android y del reproductor): con ella se elige la calidad. */
data class Conexion(
    /** Wifi o cable sin límite (no medida). */
    val sinLimite: Boolean,
    /** El ahorro de datos del sistema (Data Saver) encendido para Nut. */
    val ahorro: Boolean = false,
    /** Lo que dice Android que da el enlace (kb/s; 0 = no lo sabe). */
    val kbpsEnlace: Int = 0,
    /** Lo que ha medido de verdad el reproductor hace poco (kb/s; 0 = nada). Manda sobre lo de Android. */
    val kbpsMedidos: Long = 0,
)

/**
 * Qué calidad poner. Las reglas (en este orden):
 *  1. Si la has elegido tú (Ajustes → Fútbol, o en el reproductor), esa.
 *  2. Con el ahorro de datos del sistema encendido: SD.
 *  3. Si la conexión da menos de [MUY_LENTA] (3 Mb/s): SD.
 *  4. Con datos del móvil o una red medida: HD.
 *  5. Si da menos de [UHD_KBPS] (25 Mb/s; un canal en 4K pide unos 15–25): HD.
 *  6. Si no (wifi o cable sin límite, rápida o sin saber cuánto): Ultra HD.
 * La velocidad es la que haya medido el reproductor (si la hay) o, si no, la que dice Android del enlace. Si la elegida no está puesta en
 * esa entrada, se prueba la siguiente hacia abajo y luego hacia arriba ([orden]).
 */
object Calidades {
    const val UHD_KBPS = 25_000L
    const val HD_KBPS = 6_000L
    const val MUY_LENTA = 3_000L

    fun elige(c: Conexion, forzada: Calidad? = null): Calidad {
        if (forzada != null) return forzada
        if (c.ahorro) return Calidad.SD
        val kbps = if (c.kbpsMedidos > 0) c.kbpsMedidos else c.kbpsEnlace.toLong()
        if (kbps in 1 until MUY_LENTA) return Calidad.SD
        if (!c.sinLimite) return Calidad.HD
        if (kbps in 1 until UHD_KBPS) return Calidad.HD
        return Calidad.UHD
    }

    /** En qué orden se prueban las calidades si se quiere [q]: ella, las de debajo y luego las de encima. */
    fun orden(q: Calidad): List<Calidad> = listOf(q) + Calidad.entries.filter { it.ordinal > q.ordinal } + Calidad.entries.filter { it.ordinal < q.ordinal }.reversed()

    /** La calidad de una entrada que se pone queriendo [q] (la primera que tenga, en [orden]). */
    fun de(e: Entrada, q: Calidad): Calidad? = orden(q).firstOrNull { e.en(it) != null }

    /**
     * Qué probar si falla lo de ahora ([entrada] en [calidad]): otra calidad de la misma entrada (en el [orden] de [quiero]) que no se haya
     * probado; luego la siguiente entrada del grupo (en su mejor calidad para [quiero]), dando la vuelta, hasta que no quede nada sin
     * probar. [probadas]: (entrada, calidad) ya probadas. Null: no queda nada.
     */
    fun siguienteIntento(entradas: List<Entrada>, entrada: Int, calidad: Calidad, quiero: Calidad, probadas: Set<Pair<Int, Calidad>>): Pair<Int, Calidad>? {
        if (entradas.isEmpty()) return null
        val hechas = probadas + (entrada to calidad)
        for (k in 0 until entradas.size) {
            val i = (entrada + k).mod(entradas.size)
            val q = orden(quiero).firstOrNull { entradas[i].en(it) != null && (i to it) !in hechas }
            if (q != null) return i to q
        }
        return null
    }

    private val MARCAS = Regex("(?i)(?<![a-z0-9])(uhd|4k|2160p?|fhd|full ?hd|1080[pi]?|hd|720p?|sd|576[pi]?|480p?|hevc|h\\.?265)(?![a-z0-9])")

    /** La calidad que dice el nombre de un canal («… UHD» o «… 4K»: Ultra HD; «… SD»: SD; lo demás, HD). */
    fun deNombre(n: String): Calidad {
        val t = n.lowercase()
        return when {
            Regex("(?<![a-z0-9])(uhd|4k|2160p?)(?![a-z0-9])").containsMatchIn(t) -> Calidad.UHD
            Regex("(?<![a-z0-9])(sd|576[pi]?|480p?)(?![a-z0-9])").containsMatchIn(t) -> Calidad.SD
            else -> Calidad.HD
        }
    }

    /** El nombre sin la calidad: «ES: DAZN LA LIGA UHD» → «ES: DAZN LA LIGA». */
    fun base(n: String): String = MARCAS.replace(n, " ").replace(Regex("[\\s|\\-_]+$"), "").replace(Regex("\\s+"), " ").trim().ifBlank { n.trim() }

    /** ¿Son el mismo canal en otra calidad? (el mismo nombre sin la calidad, sin mirar mayúsculas). */
    fun mismoCanal(a: String, b: String) = base(a).equals(base(b), true)
}

/** Los grupos de canales, qué grupos tiene cada competición y tus equipos: cómo son por defecto, cómo se eligen y cómo se guardan. */
object CanalesFutbol {
    /** Los de serie, vacíos: los rellenas tú (Ajustes → Fútbol). */
    val GRUPOS = listOf(
        GrupoCanales("movistar", "Movistar LaLiga"), GrupoCanales("dazn", "DAZN LaLiga"), GrupoCanales("champions", "Champions"),
        GrupoCanales("copa", "Copa"), GrupoCanales("europa", "Europa League"), GrupoCanales(SELECCION, "Selección"),
    )

    /** Los grupos de serie que llegaron después (si no los tienes, aparecen; si los borras, no vuelven: queda su marca `x`). */
    const val SELECCION = "seleccion"
    private val NUEVOS = listOf(SELECCION)

    /** Qué grupos tiene cada competición, de serie: LaLiga, dos (Movistar y DAZN); las demás, uno. */
    val ASIGNACION: Map<String, List<String>> = mapOf(
        Competiciones.LALIGA.slug to listOf("movistar", "dazn"), Competiciones.CHAMPIONS.slug to listOf("champions"),
        Competiciones.COPA.slug to listOf("copa"), Competiciones.SUPERCOPA.slug to listOf("copa"), Competiciones.EUROPA.slug to listOf("europa"),
    ) + Competiciones.SELECCIONES.associate { it.slug to listOf(SELECCION) }

    /** Tu equipo de serie (sugerido: lo eliges tú). */
    val SUGERIDO = Equipo("83", "Barcelona", "Barcelona", "BAR", "https://a.espncdn.com/i/teamlogos/soccer/500/83.png")

    /** Los grupos de una competición, en el orden de la asignación (los que no existen, fuera). */
    fun gruposDe(liga: String, grupos: List<GrupoCanales>, asignacion: Map<String, List<String>>): List<GrupoCanales> =
        (asignacion[liga] ?: ASIGNACION[liga].orEmpty()).distinct().mapNotNull { id -> grupos.firstOrNull { it.id == id } }

    /** El canal que va después del [i] en el grupo (vuelve al primero tras el último). */
    fun siguiente(g: GrupoCanales, i: Int): Int = if (g.canales.isEmpty()) 0 else (i + 1).mod(g.canales.size)

    /** La entrada [i] del grupo (dando la vuelta), o null si está vacío. */
    fun canal(g: GrupoCanales, i: Int): Entrada? = if (g.canales.isEmpty()) null else g.canales[i.mod(g.canales.size)]

    /** ¿Vale como clave de canal? Nunca una dirección (por si una lista rara diera la suya como clave): esas no se apuntan. */
    fun claveValida(c: String) = c.isNotBlank() && c.length <= 64 && "://" !in c && c.none { it == '/' || it == '?' || it == '&' || it == '=' || it == '@' || it.isWhitespace() }

    /** ¿Está ya [r] en el grupo (en cualquier entrada y calidad)? */
    fun tiene(g: GrupoCanales, r: RefCanal) = g.canales.any { e -> e.todas.any { it.lista == r.lista && it.canal == r.canal } }

    /** Añade una entrada al final del grupo con [r] en su calidad (la que diga su nombre, o [q]), si no está ya y su clave vale. */
    fun anade(g: GrupoCanales, r: RefCanal, q: Calidad = Calidades.deNombre(r.nombre)): GrupoCanales =
        if (!claveValida(r.canal) || tiene(g, r)) g else g.copy(canales = g.canales + Entrada().con(q, r))

    /** Añade una entrada con lo que ya trae (las tres calidades que se hayan encontrado). */
    fun anade(g: GrupoCanales, e: Entrada): GrupoCanales {
        val limpia = Calidad.entries.fold(Entrada()) { x, q -> e.en(q)?.takeIf { claveValida(it.canal) && !tiene(g, it) }?.let { x.con(q, it) } ?: x }
        return if (limpia.vacia) g else g.copy(canales = g.canales + limpia)
    }

    /** Pone (o quita, con null) el canal de una calidad en la entrada [i]; si la entrada se queda vacía, fuera. */
    fun pon(g: GrupoCanales, i: Int, q: Calidad, r: RefCanal?): GrupoCanales {
        if (i !in g.canales.indices || (r != null && !claveValida(r.canal))) return g
        val l = g.canales.toMutableList(); l[i] = l[i].con(q, r)
        return g.copy(canales = l.filter { !it.vacia })
    }

    /**
     * Una entrada con [r] y, de [candidatos] (los canales de su misma lista), los que son el mismo canal en las otras calidades
     * («… UHD», «… HD», «… SD»): al elegir uno, se rellenan solos los demás.
     */
    fun entradaCon(r: RefCanal, candidatos: List<RefCanal>): Entrada {
        var e = Entrada().con(Calidades.deNombre(r.nombre), r)
        for (c in candidatos) {
            if (c.lista != r.lista || c.canal == r.canal || !Calidades.mismoCanal(c.nombre, r.nombre)) continue
            val q = Calidades.deNombre(c.nombre)
            if (e.en(q) == null) e = e.con(q, c)
        }
        return e
    }

    /** Mueve el canal [i] [d] sitios (−1 sube, +1 baja). */
    fun mueve(g: GrupoCanales, i: Int, d: Int): GrupoCanales {
        val j = i + d
        if (i !in g.canales.indices || j !in g.canales.indices) return g
        val l = g.canales.toMutableList(); l.add(j, l.removeAt(i)); return g.copy(canales = l)
    }

    fun quita(g: GrupoCanales, i: Int): GrupoCanales = if (i !in g.canales.indices) g else g.copy(canales = g.canales.filterIndexed { k, _ -> k != i })

    /** El id de un grupo nuevo, a partir de su nombre (sin repetir). */
    fun idNuevo(nombre: String, existentes: Collection<String>): String {
        val base = "g" + java.text.Normalizer.normalize(nombre, java.text.Normalizer.Form.NFD).lowercase().replace(Regex("[^a-z0-9]+"), "").take(20)
        var id = base; var n = 2
        while (id in existentes) { id = base + n; n++ }
        return id
    }

    // ---------------------------------------------------------------- cómo se guardan (textos JSON, en tu cuenta)

    private fun ref(r: RefCanal) = linkedMapOf("l" to r.lista, "k" to r.canal, "n" to r.nombre)
    private fun refDe(x: Any?): RefCanal? = x.mapa().takeIf { it.isNotEmpty() }?.let { RefCanal(it["l"].txt(), it["k"].txt(), it["n"].txt()) }?.takeIf { it.lista.isNotBlank() && claveValida(it.canal) }

    /** `[{"id","n","c":[{"u":{"l","k","n"},"h":{…},"s":{…}}]}]` (cada entrada, sus calidades: `u` Ultra HD, `h` HD, `s` SD). */
    fun gruposATexto(l: List<GrupoCanales>): String = JsonFlujo.escribe(l.map { g ->
        linkedMapOf<String, Any?>("id" to g.id, "n" to g.nombre, "c" to g.canales.map { e -> Calidad.entries.mapNotNull { q -> e.en(q)?.takeIf { claveValida(it.canal) }?.let { q.clave to ref(it) } }.toMap(LinkedHashMap()) }.filter { it.isNotEmpty() })
    } + NUEVOS.filter { id -> l.none { it.id == id } }.map { linkedMapOf("id" to it, "x" to true) })

    /** Los grupos de un texto (sin texto o si no se entiende, los de serie). */
    fun gruposDeTexto(t: String?): List<GrupoCanales> {
        if (t.isNullOrBlank()) return GRUPOS
        val v = runCatching { JsonFlujo.lee(t) }.getOrNull() as? List<*> ?: return GRUPOS
        val ids = v.lista().map { it.mapa()["id"].txt() }.toSet()
        // (los de serie que llegaron después y aún no tenías: al final)
        val faltan = GRUPOS.filter { it.id in NUEVOS && it.id !in ids }
        return v.lista().map { it.mapa() }.mapNotNull { m ->
            val id = m["id"].txt(); if (id.isBlank() || m["x"] == true) return@mapNotNull null
            GrupoCanales(id, m["n"].txt().ifBlank { id }, m["c"].lista().map { it.mapa() }.map { x ->
                // (una entrada de antes, con un solo canal: como HD)
                if (x.containsKey("k")) Entrada(hd = refDe(x)) else Entrada(refDe(x["u"]), refDe(x["h"]), refDe(x["s"]))
            }.filter { !it.vacia })
        }.distinctBy { it.id } + faltan
    }

    /** `{"esp.1":["movistar","dazn"],…}`. */
    fun asignacionATexto(a: Map<String, List<String>>): String = JsonFlujo.escribe(a.mapValues { it.value.distinct() })

    /** La asignación de un texto, encima de la de serie (lo que no diga, como de serie). */
    fun asignacionDeTexto(t: String?): Map<String, List<String>> {
        val m = if (t.isNullOrBlank()) emptyMap() else runCatching { JsonFlujo.lee(t) }.getOrNull().mapa()
        return ASIGNACION + m.mapValues { (_, v) -> v.lista().map { it.txt() }.filter { it.isNotBlank() }.distinct() }
    }

    /** Tus equipos: `[{"id","n","c","s","e","l"}]` (`l`: la competición donde se buscó, su liga). */
    fun equiposATexto(l: List<Pair<Equipo, String>>): String = JsonFlujo.escribe(l.distinctBy { it.first.id }.map { (e, liga) ->
        linkedMapOf("id" to e.id, "n" to e.nombre, "c" to e.corto, "s" to e.siglas, "e" to e.escudo, "l" to liga)
    })

    /**
     * Un equipo, a tus equipos ([rival] = false) o a tus rivales (true): si ya estaba en esa lista, sale; si estaba en la otra, se
     * cambia (un equipo no puede ser tuyo y rival a la vez). Devuelve (tus equipos, tus rivales).
     */
    fun alterna(mios: List<Pair<Equipo, String>>, rivales: List<Pair<Equipo, String>>, e: Equipo, liga: String, rival: Boolean): Pair<List<Pair<Equipo, String>>, List<Pair<Equipo, String>>> {
        val enMios = mios.any { it.first.id == e.id }; val enRivales = rivales.any { it.first.id == e.id }
        val m = mios.filter { it.first.id != e.id }; val r = rivales.filter { it.first.id != e.id }
        return if (rival) (m to if (enRivales) r else r + (e to liga)) else ((if (enMios) m else m + (e to liga)) to r)
    }

    fun equiposDeTexto(t: String?): List<Pair<Equipo, String>> {
        if (t.isNullOrBlank()) return emptyList()
        return runCatching { JsonFlujo.lee(t) }.getOrNull().lista().map { it.mapa() }.mapNotNull { m ->
            val id = m["id"].txt(); if (id.isBlank()) null
            else Equipo(id, m["n"].txt(), m["c"].txt().ifBlank { m["n"].txt() }, m["s"].txt(), m["e"].txt()) to m["l"].txt().ifBlank { Competiciones.LALIGA.slug }
        }.distinctBy { it.first.id }
    }
}
