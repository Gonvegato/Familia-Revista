package com.gonzalo.azabache.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto

/*
 * Lo de Azabache, con la forma de siempre (la misma que en el móvil): las categorías, las fichas (valoradas y en curso), las notas
 * del diario y las sagas. Las fichas se guardan como mapas tal cual (con lo que no se usa aquí incluido): así lo que el móvil
 * guarda en una ficha y aquí no se enseña no se pierde nunca.
 */

typealias Obj = Map<String, Any?>

const val SRC_MOVIE = "movie"
const val SRC_TV = "tv"

/** Una categoría: Videojuegos (0), Libros (1) o una tuya (películas, series, discos…). */
class Kind(
    val id: Int, val title: String, val labels: List<String>, val statuses: List<String>, val isBook: Boolean,
    val custom: Boolean = false, val color: Int = 0, val source: String = "",
) {
    val crit: Int get() = (labels.size - 1).coerceIn(1, 4)
    val isMovie: Boolean get() = source == SRC_MOVIE
    val isTv: Boolean get() = source == SRC_TV
    val isGame: Boolean get() = id == 0
    val creatorWord: String get() = if (isMovie) "Director" else if (custom) "Creador" else if (isBook) "Autor" else "Estudio"
    val platformWord: String get() = if (isMovie || isTv) "Dónde" else if (isBook || custom) "Formato" else "Plataforma"
    val unit: String get() = if (isMovie) "película" else if (isTv) "serie" else if (custom) "ficha" else if (isBook) "libro" else "juego"
    val units: String get() = if (isMovie) "películas" else if (isTv) "series" else if (custom) "fichas" else if (isBook) "libros" else "juegos"
    val doneWord: String get() = if (isMovie || isTv) "Vistas" else if (custom) "Terminados" else if (isBook) "Leídos" else "Pasados"
    val platforms: List<String> get() = when {
        isMovie -> listOf("Cine", "Netflix", "Prime Video", "Disney+", "HBO Max", "Apple TV+", "Filmin")
        isTv -> listOf("Netflix", "Prime Video", "Disney+", "HBO Max", "Apple TV+", "Filmin", "Tele")
        custom -> emptyList()
        isBook -> listOf("Papel", "Ebook", "Kindle", "Audiolibro")
        else -> listOf("PC", "PS5", "PS4", "Switch", "Switch 2", "Xbox", "Steam Deck", "Móvil")
    }
}

val GAMES = Kind(0, "Videojuegos", listOf("Valoración", "Gráficos", "Historia", "Música", "Jugabilidad"), listOf("Pendiente", "Jugando", "Abandonado"), false)
val BOOKS = Kind(1, "Libros", listOf("Valoración", "Personajes", "Historia", "Escritura", "Ritmo"), listOf("Pendiente", "Leyendo", "Abandonado"), true)

fun customKind(id: Int, title: String, criteria: List<String>, progress: String, color: Int, source: String = "") = Kind(
    id, title.trim(), listOf("Valoración") + criteria.map { it.trim() }.filter { it.isNotEmpty() }.take(4).ifEmpty { listOf("Disfrute") },
    listOf("Pendiente", progress.trim().ifEmpty { "En curso" }, "Abandonado"), false, true, color,
    source.takeIf { it == SRC_MOVIE || it == SRC_TV } ?: ""
)

fun kindAMapa(k: Kind): Obj = linkedMapOf("id" to k.id.toLong(), "title" to k.title, "criteria" to k.labels.drop(1), "progress" to k.statuses[1], "color" to k.color.toLong(), "source" to k.source)

fun kindDeMapa(o: Obj): Kind? = runCatching {
    customKind(o["id"].comoLong().toInt(), o["title"].comoTexto(), o["criteria"].comoLista().map { it.comoTexto() },
        o["progress"].comoTexto().ifBlank { "En curso" }, o["color"].comoLong().toInt(), o["source"].comoTexto())
}.getOrNull()?.takeIf { it.title.isNotBlank() && it.id > 1 }

/** Las cinco notas: la general y los cuatro criterios. */
val NOTAS = listOf("general", "gfx", "story", "music", "gameplay")

/**
 * Una ficha de Azabache: [o] es la ficha tal cual (la misma forma que en el móvil: `id`, `name`, `cover`, las notas, `status`…).
 * [valorada]: está terminada (en la lista de valoradas). En los juegos y libros, [ref] es su cosa de la cuenta y [portada] su imagen.
 */
class Ficha(val kind: Int, val o: Obj, val valorada: Boolean, val ref: String? = null, val portada: String = "", val portadaUrl: String = "", val sinNota: Boolean = false) {
    val id: Long get() = o["id"].comoLong()
    val nombre: String get() = o["name"].comoTexto()
    val creador: String get() = o["creator"].comoTexto()
    val plataforma: String get() = o["platform"].comoTexto()
    /** 0 pendiente, 1 en curso, 2 abandonado (las valoradas, terminadas). */
    val estado: Int get() = if (valorada) 3 else o["status"].comoLong().toInt().coerceIn(0, 2)
    val notas: List<Int> get() = NOTAS.map { o[it].comoLong().toInt() }
    val general: Int get() = o["general"].comoLong().toInt()
    /** Sin valorar: nunca un 0 (en la cuenta, sin el campo `nota`; en las tuyas, sin ninguna nota puesta). */
    val sinValorar: Boolean get() = !valorada || sinNota || (ref == null && notas.all { it == 0 })
    val resena: String get() = o["review"].comoTexto()
    val fav: Boolean get() = o["fav"] == true
    val minutos: Int get() = o["minutes"].comoLong().toInt()
    val paginas: Int get() = o["pages"].comoLong().toInt()
    /** Días desde 1970 (0 = sin fecha). */
    val empezado: Long get() = o["start"].comoLong()
    val terminado: Long get() = o["end"].comoLong()
    val actualizado: Long get() = o["updated"].comoLong()
    val ttb: Triple<Int, Int, Int> get() = Triple(o["ttbMain"].comoLong().toInt(), o["ttbExtra"].comoLong().toInt(), o["ttbFull"].comoLong().toInt())
    /** La portada que se ve: la imagen de la cuenta (hash) o una dirección de internet. */
    val portadaWeb: String get() = portadaUrl.ifBlank { o["cover"].comoTexto().takeIf { it.startsWith("http") }.orEmpty() }
    val actividad: Long get() = maxOf(actualizado, terminado * 86_400_000L, empezado * 86_400_000L)

    fun con(cambios: Map<String, Any?>): Map<String, Any?> = LinkedHashMap(o).also { it.putAll(cambios) }
}

/** Una nota del diario (puede haber varias el mismo día). [sobre]: las fichas de las que habla («categoría:id»). */
data class Nota(val id: Long, val dia: Long, val texto: String, val sobre: List<String> = emptyList(), val foto: String = "", val comparte: Boolean = false, val updated: Long = 0L, val resto: Obj = emptyMap())

fun notaAMapa(n: Nota): Obj = LinkedHashMap(n.resto).apply {
    put("id", n.id); put("dia", n.dia); put("x", n.texto); put("updated", n.updated)
    if (n.sobre.isNotEmpty()) put("sobre", n.sobre) else remove("sobre")
    if (n.foto.isNotBlank()) put("foto", n.foto) else remove("foto")
    if (n.comparte) put("comp", true) else remove("comp")
}

fun notaDeMapa(o: Obj): Nota? {
    val id = o["id"].comoLong()
    if (id == 0L || !o.containsKey("dia")) return null
    return Nota(id, o["dia"].comoLong(), o["x"].comoTexto(), o["sobre"].comoLista().map { it.comoTexto() }.filter { it.isNotBlank() },
        o["foto"].comoTexto(), o["comp"] == true, o["updated"].comoLong(), o)
}

/** Una saga: [items], los ids de sus fichas en orden. */
data class Saga(val id: Long, val name: String, val items: List<Long>, val cover: String = "", val kind: Int = 1, val note: String = "", val updated: Long = 0L, val resto: Obj = emptyMap())

fun sagaAMapa(s: Saga): Obj = LinkedHashMap(s.resto).apply {
    put("id", s.id); put("name", s.name); put("kind", s.kind.toLong()); put("items", s.items); put("updated", s.updated)
    if (s.cover.isNotBlank()) put("cover", s.cover) else remove("cover")
    if (s.note.isNotBlank()) put("note", s.note) else remove("note")
}

fun sagaDeMapa(o: Obj): Saga? = runCatching {
    Saga(o["id"].comoLong(), o["name"].comoTexto(), o["items"].comoLista().map { it.comoLong() }.filter { it != 0L }.distinct(),
        o["cover"].comoTexto(), (o["kind"] ?: 1L).comoLong().toInt(), o["note"].comoTexto(), o["updated"].comoLong(), o)
}.getOrNull()?.takeIf { it.id != 0L }

/**
 * Para comparar dos fichas (o dos valores) sin que cuente el orden de los campos ni si un número es entero o no: la misma regla
 * que en el móvil.
 */
fun canon(v: Any?): String = when (v) {
    null -> "null"
    is Map<*, *> -> v.keys.map { it.toString() }.filter { it != "sv" }.sorted().joinToString(",", "{", "}") { k -> "$k:${canon(v[k])}" }
    is List<*> -> v.joinToString(",", "[", "]") { canon(it) }
    is Double, is Float -> (v as Number).toDouble().let { d -> if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString() else d.toString() }
    is Number -> v.toLong().toString()
    is Boolean -> v.toString()
    else -> Json.escribe(v.toString())
}

/** Un nombre para comparar (sin tildes, sin mayúsculas y solo letras y números). */
fun normName(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().filter { it.isLetterOrDigit() }

/** El número de una ficha de categoría propia: sale del nombre (así coincide en tu móvil y en el de tu pareja). */
fun customItemId(name: String): Long {
    var h = -0x340d631b7bdddcdbL
    for (c in normName(name)) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
    return (h and Long.MAX_VALUE) % 9_000_000_000_000L + 100_000_000_000L
}

/** Lee un texto JSON de un objeto (o un mapa vacío). */
fun objDe(texto: String): Obj = if (texto.startsWith("{")) runCatching { Json.lee(texto).comoMapa() }.getOrDefault(emptyMap()) else emptyMap()
