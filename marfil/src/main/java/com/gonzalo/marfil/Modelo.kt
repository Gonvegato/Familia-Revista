package com.gonzalo.marfil

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.UUID

/** En qué estante está un libro. */
enum class Estado(val nombre: String) { PENDIENTE("Pendiente"), LEYENDO("Leyendo"), LEIDO("Leído"), ABANDONADO("Abandonado") }

/**
 * Un libro de tu biblioteca. El archivo (EPUB, PDF…) es de cada dispositivo ([uri] vacío = el libro viene de otro de tus dispositivos y aquí
 * aún no está el archivo); lo demás viaja con la cuenta: lo que editas gana por «el cambio más reciente», lo leído se suma por dispositivo
 * y el punto por el que vas es el más lejano (como en Atrio).
 */
data class Libro(
    val id: String,
    val titulo: String,
    val autor: String = "",
    /** Lo que identifica al libro en todos tus dispositivos: se fija al crearlo (título y autor de entonces) y NO cambia si luego editas el título o el autor. */
    val clave: String = claveDe(titulo, autor),
    /** Portada elegida o sacada del libro (archivo de la app); "" = sin portada. */
    val portada: String = "",
    /** De dónde salió la portada si fue de internet (Open Library, iTunes…): viaja con la cuenta, para que Azabache enseñe la misma. */
    val portadaUrl: String = "",
    /**
     * El fondo: una imagen a lo ancho para el cartel de la portada y la cabecera de la ficha (archivo de la app, o una dirección de internet
     * mientras no ha bajado); "" = sin fondo (se usa el color de la portada). En la cuenta, como la portada: `fondo` (hash) y `fu` (de dónde salió).
     */
    val fondo: String = "",
    val fondoUrl: String = "",
    /** Dirección del archivo en este dispositivo ("" = aún no está aquí). */
    val uri: String = "",
    /** Nombre del archivo (con su extensión). */
    val archivo: String = "",
    val favorito: Boolean = false,
    val estado: Estado = Estado.PENDIENTE,
    val anadido: Long = 0L,
    /** Última vez que se abrió (milisegundos). */
    val ultima: Long = 0L,
    /** Lo leído: de 0 a 1 (el punto más lejano de todos tus dispositivos). */
    val fraccion: Float = 0f,
    /** Cuándo marcaste a mano por dónde vas (ms; 0 = nunca): esa posición manda sobre «lo más lejos», también en tus otros dispositivos. */
    val fraccionFijada: Long = 0L,
    /** Última vez que se cambió la ficha (milisegundos): gana el más reciente. */
    val mod: Long = 0L,
    /** Tiempo leído (ms): el de ESTE dispositivo y el de los demás (por dispositivo); el total es la suma. */
    val msPropios: Long = 0L,
    val ajenosMs: Map<String, Long> = emptyMap(),
    /** Páginas del libro (0 = no se sabe) y de dónde sale ese número: ver [Paginas]. */
    val paginas: Int = 0,
    val paginasOrigen: String = "",
    /** Las veces que lo has terminado (fechas en ms, de la más antigua a la más reciente). */
    val terminados: List<Long> = emptyList(),
) {
    /** La última vez que lo terminaste (0 = nunca). */
    val terminado: Long get() = terminados.lastOrNull() ?: 0L
    val msLeidos: Long get() = msPropios + ajenosMs.values.sum()
    /** El libro viene de otro dispositivo y aquí aún no tiene archivo. */
    val enOtroDispositivo: Boolean get() = uri.isBlank()
    val formato: String get() = archivo.substringAfterLast('.', "").lowercase()
}

/** Lo que identifica a un libro en todos tus dispositivos (el id de cada uno es distinto): título y autor, sin tildes ni mayúsculas. */
fun claveDe(titulo: String, autor: String) = "libro:" + norm(titulo) + ":" + norm(autor)

fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Lo que se edita de un libro y viaja con la cuenta (gana el cambio más reciente). */
fun compartidos(l: Libro) = listOf(l.titulo, l.autor, l.favorito, l.estado, l.portadaUrl)

/** El título de un libro a partir del nombre de su archivo: «El nombre de la rosa - Umberto Eco.epub» → («El nombre de la rosa», «Umberto Eco»). */
fun tituloYAutorDeArchivo(nombre: String): Pair<String, String> {
    val base = nombre.substringBeforeLast('.', nombre).replace('_', ' ').replace(Regex("\\s+"), " ").trim()
    val partes = base.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
    return if (partes.size >= 2) partes[0] to partes[1] else base to ""
}

/** Dos fechas de terminado con menos de 12 horas de diferencia son la misma. */
const val REPETIDA_MS = 12L * 3600_000L

/** Junta varias listas de fechas de terminado (de tus dispositivos) en una, ordenada y sin repetidas (a menos de 12 h, se queda la primera). */
fun juntarFechas(vararg listas: List<Long>): List<Long> {
    val out = ArrayList<Long>()
    for (f in listas.flatMap { it }.filter { it > 0 }.sorted()) if (out.isEmpty() || f - out.last() >= REPETIDA_MS) out.add(f)
    return out
}

/** El estado en la cuenta (el mismo en toda la familia: ver nube/ESQUEMA.md). */
val Estado.enCuenta: String get() = when (this) { Estado.PENDIENTE -> "pendiente"; Estado.LEYENDO -> "en_curso"; Estado.LEIDO -> "terminado"; Estado.ABANDONADO -> "abandonado" }
fun estadoDeCuenta(s: String): Estado = when (s) { "en_curso" -> Estado.LEYENDO; "terminado" -> Estado.LEIDO; "abandonado" -> Estado.ABANDONADO; else -> Estado.PENDIENTE }

/**
 * Tu biblioteca: los libros de la cuenta (la colección `libros`, la misma en todos tus dispositivos y en Azabache) más lo que es
 * solo de este móvil (dónde está el archivo de cada libro). Cada libro se llama por su `kid` (sale de su clave: el mismo en todas
 * partes). Las obras de Cobalto que se leen aquí ([esEscrito]) no son de la biblioteca: cada móvil las rehace de la cuenta (lo leído en
 * ellas sí viaja, con [LecturaNube]).
 */
object Biblioteca {
    val libros = mutableStateListOf<Libro>()
    /** Tus libros de verdad: sin las obras de Cobalto (que se leen aquí pero no son de la biblioteca). */
    fun propios(): List<Libro> = libros.filter { !it.esEscrito }
    var cargada by mutableStateOf(false)
    private var appCtx: Context? = null
    private var col: com.gonzalo.familia.nucleo.Coleccion? = null
    /** Solo para leer lo de antes (la migración y las pruebas de la mezcla vieja). */
    val borrados = HashMap<String, Long>()
    /** Lo de este móvil: dónde está el archivo de cada libro (por kid). */
    private val uris = HashMap<String, String>()
    /** Las obras de Cobalto que se leen aquí (solo en este móvil). */
    private val escritos = LinkedHashMap<String, Libro>()
    private val principal by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    fun cargar(c: Context) {
        if (cargada) return
        appCtx = c.applicationContext
        Cuenta.cargar(c)
        leeLocal(c)
        val cc = com.gonzalo.familia.LaFamilia.coleccion("libros")
        col = cc
        cc.alCambiar { principal.post { refresca() } }
        com.gonzalo.familia.LaFamilia.coleccion("tiempo").alCambiar { principal.post { refresca() } }
        refresca()
        cargada = true
        LecturaNube.inicia(c)
    }

    private fun archivoLocal(c: Context) = File(c.filesDir, "biblioteca_local.json")

    private fun leeLocal(c: Context) {
        val o = runCatching { JSONObject(archivoLocal(c).readText()) }.getOrNull() ?: return
        o.optJSONObject("uris")?.let { u -> u.keys().forEach { k -> uris[k] = u.optString(k) } }
        o.optJSONArray("escritos")?.let { a -> for (i in 0 until a.length()) aLibro(a.getJSONObject(i)).let { escritos[it.id] = it } }
    }

    private fun guardaLocal() {
        val c = appCtx ?: return
        val o = JSONObject().put("uris", JSONObject().also { u -> uris.forEach { (k, v) -> u.put(k, v) } })
            .put("escritos", JSONArray().also { a -> escritos.values.forEach { a.put(aJson(it)) } })
        runCatching { val f = archivoLocal(c); val t = File(f.path + ".tmp"); t.writeText(o.toString()); t.renameTo(f) }
    }

    /** El nombre de un libro en la cuenta (sale de su clave). */
    fun kid(clave: String) = MezclaLectura.kid(clave)

    fun nuevoId() = UUID.randomUUID().toString().replace("-", "").take(12)

    /** El archivo de la portada guardada en el móvil (la imagen de la cuenta, por su hash), o "" si aún no está. */
    fun archivoPortada(c: Context, hash: String): String = if (hash.isBlank()) "" else File(File(c.filesDir, "familia"), "img/$hash").takeIf { it.isFile }?.absolutePath.orEmpty()

    /** El tiempo leído de cada libro (kid → dispositivo → ms), del último [refresca]. */
    private var tiempos: Map<String, Map<String, Long>> = emptyMap()

    /** Un libro tal como está en la cuenta (con lo de este móvil). */
    fun deNodo(n: com.gonzalo.familia.nucleo.Nodo): Libro {
        val c = appCtx
        val hash = n.texto("portada")
        val (fondo, fondoUrl) = fondoDe(c, n)
        return Libro(
            id = n.ref, titulo = n.texto("t"), autor = n.texto("a"), clave = n.texto("clave").ifBlank { claveDe(n.texto("t"), n.texto("a")) },
            portada = c?.let { archivoPortada(it, hash) }.orEmpty(), portadaUrl = n.texto("pu"), fondo = fondo, fondoUrl = fondoUrl, uri = uris[n.ref].orEmpty(), archivo = n.texto("archivo"),
            favorito = n.si("fav"), estado = estadoDeCuenta(n.texto("estado")), anadido = com.gonzalo.familia.nucleo.Fechas.ms(n.entero("an")), ultima = com.gonzalo.familia.nucleo.Fechas.ms(n.entero("ul")),
            fraccion = n.decimal("fr").toFloat().coerceIn(0f, 1f), fraccionFijada = n.entero("ff"), mod = n.m.values.maxOrNull() ?: 0L,
            paginas = n.entero("pg").toInt(), paginasOrigen = n.texto("po"), terminados = terminadosDe(n),
            msPropios = tiempos[n.ref]?.get(Cuenta.dispositivo) ?: 0L, ajenosMs = tiempos[n.ref]?.filterKeys { it != Cuenta.dispositivo } ?: emptyMap(),
        )
    }

    /**
     * Cuándo lo terminaste. Un libro leído sin fecha (llegó así de Atrio, de otro dispositivo o de antes de la familia) cuenta desde
     * que pasó a «terminado» en la cuenta (la cuenta guarda la hora de cada campo); si eso no se sabe, desde la última vez que lo
     * abriste. Así cuenta para el reto del año. (Solo se deduce aquí: en la cuenta no se escribe nada hasta que cambies el libro.)
     */
    private fun terminadosDe(n: com.gonzalo.familia.nucleo.Nodo): List<Long> {
        val fin = n.lista("fin").map { com.gonzalo.familia.nucleo.Fechas.ms(it.comoLongSeguro()) }.filter { it > 0 }
        if (fin.isNotEmpty() || estadoDeCuenta(n.texto("estado")) != Estado.LEIDO) return fin
        return listOfNotNull((n.m["estado"] ?: 0L).takeIf { it > FECHA_MINIMA } ?: n.entero("ul").takeIf { it > FECHA_MINIMA })
    }
    private const val FECHA_MINIMA = 946_684_800_000L   // 2000-01-01: lo de antes es una hora de mentira (o ninguna)

    private fun Any?.comoLongSeguro(): Long = (this as? Number)?.toLong() ?: this?.toString()?.toDoubleOrNull()?.toLong() ?: 0L

    /**
     * El fondo de un libro: la imagen de la cuenta (`fondo`, por su hash) si ya está en el móvil; si aún no ha bajado, la dirección de la que
     * salió (`fu`). Si el libro nunca ha tenido fondo puesto aquí (no hay campo `fondo`), el que le pusieras en Jonsu (`az.hero`, si es de internet).
     */
    fun fondoDe(c: Context?, n: com.gonzalo.familia.nucleo.Nodo): Pair<String, String> {
        val fu = n.texto("fu")
        if (n["fondo"] != null) {
            val h = n.texto("fondo")
            if (h.isBlank() || h == SIN_FONDO) return "" to ""
            val f = c?.let { archivoPortada(it, h) }.orEmpty()
            return f.ifBlank { fu.takeIf { it.startsWith("http") }.orEmpty() } to fu
        }
        val hero = n.texto("az").takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it).optString("hero") }.getOrNull() }.orEmpty()
        return if (hero.startsWith("http")) hero to hero else "" to ""
    }

    /** El hash del fondo de un libro en la cuenta ("" si no tiene). */
    fun hashFondo(id: String): String = col?.nodo(id)?.texto("fondo").orEmpty().takeIf { it != SIN_FONDO }.orEmpty()

    /** «Sin fondo, a propósito» (en la cuenta un campo vacío no existe, y entonces volvería el de Jonsu). */
    const val SIN_FONDO = "-"

    /** El hash de la portada de un libro en la cuenta ("" si no tiene). */
    fun hashPortada(id: String): String = col?.nodo(id)?.texto("portada").orEmpty()

    /** En las pruebas de pantallas: una lista fija (sin la cuenta debajo). */
    @Volatile private var congelada = false
    fun fijaParaPruebas(l: List<Libro>) { congelada = true; libros.clear(); libros.addAll(l) }

    /** Vuelve a montar la lista con lo que hay en la cuenta (se llama sola cuando cambia algo, venga de donde venga). */
    /**
     * Las fechas que llegaron de Atrio como número de día (y quedaron en 1970) se corrigen en la cuenta, una vez (al quedar bien ya no
     * hay nada que corregir): así las ven igual Azabache y tus otros dispositivos.
     */
    private fun reparaFechas(cc: com.gonzalo.familia.nucleo.Coleccion, vivos: List<com.gonzalo.familia.nucleo.Nodo>) {
        for (n in vivos) {
            val c = LinkedHashMap<String, Any?>()
            val fin = n.lista("fin").map { it.comoLongSeguro() }
            if (fin.any { com.gonzalo.familia.nucleo.Fechas.esDia(it) }) c["fin"] = fin.map { com.gonzalo.familia.nucleo.Fechas.ms(it) }
            for (k in listOf("an", "ul")) n.entero(k).let { if (com.gonzalo.familia.nucleo.Fechas.esDia(it)) c[k] = com.gonzalo.familia.nucleo.Fechas.ms(it) }
            if (c.isNotEmpty()) runCatching { cc.cambia(n.ref, c) }
        }
    }

    fun refresca() {
        if (congelada) return
        val cc = col ?: return
        tiempos = runCatching { LecturaNube.tiempos() }.getOrDefault(emptyMap())
        val vivos = cc.vivos()
        reparaFechas(cc, vivos)
        val deCuenta = vivos.filter { it.texto("t").isNotBlank() }.map { deNodo(it) }
        val nueva = (deCuenta + escritos.values).sortedByDescending { maxOf(it.anadido, 0L) }
        if (nueva != libros.toList()) { libros.clear(); libros.addAll(nueva) }
        appCtx?.let { c -> Widgets.pedir(c); Portadas.bajarQueFalten(c) }
    }

    /** Lo que se guarda de un libro en la cuenta (campo → valor). */
    fun campos(l: Libro): Map<String, Any?> = mapOf(
        "clave" to l.clave, "t" to l.titulo, "a" to l.autor, "estado" to l.estado.enCuenta, "fav" to l.favorito,
        "an" to l.anadido, "ul" to l.ultima, "fr" to Math.round(l.fraccion * 10000.0) / 10000.0, "ff" to l.fraccionFijada,
        "pg" to l.paginas.toLong(), "po" to l.paginasOrigen, "fin" to l.terminados, "pu" to l.portadaUrl, "archivo" to l.archivo,
    )

    /** Guarda un libro: solo sube a la cuenta lo que ha cambiado de verdad (campo a campo). */
    fun poner(l0: Libro) {
        if (l0.esEscrito) {
            escritos[l0.id] = l0; guardaLocal(); refresca(); return
        }
        if (congelada || col == null) { val i = libros.indexOfFirst { it.id == l0.id }; if (i >= 0) libros[i] = l0 else libros.add(0, l0); col?.cambia(l0.id, campos(l0)); return }
        // Un libro se llama siempre por su kid (sale de su clave): así es el mismo en todos tus dispositivos
        val id = kid(l0.clave)
        val previo = por(id) ?: por(l0.id)
        // Una sola regla para «terminado»: si pasa a Leído (desde la ficha, desde el lector…), se apunta la fecha (salvo que ya se apuntara hace poco)
        val ahora = System.currentTimeMillis()
        val l = (if (previo != null && previo.estado != Estado.LEIDO && l0.estado == Estado.LEIDO && (l0.terminado == 0L || l0.terminado < ahora - REPETIDA_MS)) l0.copy(terminados = l0.terminados + ahora) else l0)
            .copy(id = id, anadido = l0.anadido.takeIf { it > 0 } ?: previo?.anadido?.takeIf { it > 0 } ?: ahora)
        val archivoNuevo = l.uri.isNotBlank() && uris[id] != l.uri
        if (archivoNuevo) { uris[id] = l.uri; guardaLocal() }
        val cc = col ?: return
        val cambios = campos(l).toMutableMap()
        // La portada se pone con [Portadas] (es una imagen de la cuenta); aquí solo se quita si se ha quitado
        if (l.portada.isBlank() && previo != null && previo.portada.isNotBlank()) cambios["portada"] = ""
        // El fondo, igual: se pone con [Portadas.ponerFondo]; aquí solo se quita si se ha quitado
        if (l.fondo.isBlank() && previo != null && previo.fondo.isNotBlank()) { cambios["fondo"] = SIN_FONDO; cambios["fu"] = "" }
        if (cc.nodo(id)?.borrado == true) cambios["b"] = 0L
        cc.cambia(id, cambios)
        refresca()
        // Un libro con su archivo aquí (recién añadido o con archivo nuevo): a tu kDrive, en cuanto haya conexión
        if (archivoNuevo) appCtx?.let { NubeLibros.siHaceFalta(it) }
    }

    /** Dónde está el archivo de un libro en este móvil. */
    fun ponUri(id: String, uri: String) { if (uris[id] != uri) { uris[id] = uri; guardaLocal(); appCtx?.let { NubeLibros.siHaceFalta(it) } } }

    /** La portada de un libro (el hash de una imagen ya guardada en la cuenta) y de dónde salió. */
    fun ponerPortada(id: String, hash: String, url: String) {
        col?.cambia(id, mapOf("portada" to hash, "pu" to url))
        if (congelada || col == null) { val i = libros.indexOfFirst { it.id == id }; val c = appCtx; if (i >= 0 && c != null) libros[i] = libros[i].copy(portada = archivoPortada(c, hash), portadaUrl = url) }
        refresca()
    }

    /** El fondo de un libro (el hash de una imagen ya guardada en la cuenta; "" = sin fondo) y de dónde salió. */
    fun ponerFondo(id: String, hash: String, url: String) {
        col?.cambia(id, mapOf("fondo" to hash.ifBlank { SIN_FONDO }, "fu" to url))
        if (congelada || col == null) { val i = libros.indexOfFirst { it.id == id }; val c = appCtx; if (i >= 0 && c != null) libros[i] = libros[i].copy(fondo = archivoPortada(c, hash), fondoUrl = url) }
        refresca()
    }

    /** Dónde está el libro en tu kDrive (para bajarlo en tus otros dispositivos). */
    fun ponerNube(id: String, nube: NubeLibro) {
        col?.cambia(id, mapOf("nube" to mapOf("f" to nube.f, "n" to nube.n, "s" to nube.s)))
    }

    fun nube(id: String): NubeLibro? = col?.nodo(id)?.mapa("nube")?.let { m -> NubeLibros.deJson(JSONObject(m)) }

    /** Lo que Azabache dice de este libro: su nota (0–100; 0 = sin valorar) y su reseña. */
    fun valoracion(id: String): Pair<Int, String> = col?.nodo(id)?.let { it.entero("nota").toInt() to it.texto("resena") } ?: (0 to "")

    fun cambiar(id: String, f: (Libro) -> Libro) { por(id)?.let { poner(f(it)) } }

    fun quitar(id: String) {
        val l = por(id) ?: return
        if (congelada || col == null) { libros.removeAll { it.id == id }; col?.borra(id); return }
        if (l.esEscrito) { escritos.remove(id); guardaLocal(); refresca(); return }
        col?.borra(id)
        uris.remove(id); guardaLocal()
        refresca()
    }

    fun por(id: String): Libro? = libros.firstOrNull { it.id == id }
    fun existente(titulo: String, autor: String): Libro? = claveDe(titulo, autor).let { k -> libros.firstOrNull { it.clave == k } }

    /** La copia de seguridad (Ajustes → Copia): tus libros tal cual. */
    fun aTexto(): String = JSONObject().apply {
        put("version", 1)
        put("libros", JSONArray().apply { propios().forEach { put(aJson(it)) } })
    }.toString()

    /** Importa una copia de seguridad: los libros que no tengas se añaden. */
    fun leer(texto: String, reemplazar: Boolean) {
        val js = JSONObject(texto).optJSONArray("libros") ?: JSONArray()
        val claves = libros.map { it.clave }.toSet()
        for (i in 0 until js.length()) aLibro(js.getJSONObject(i)).takeIf { reemplazar || it.clave !in claves }?.let { poner(it) }
    }

    /** (Ya no hace falta: todo se guarda al cambiar.) */
    fun guardar() { refresca() }

    fun aJson(l: Libro) = JSONObject().apply {
        put("id", l.id); put("clave", l.clave); put("titulo", l.titulo); put("autor", l.autor); put("portada", l.portada); put("pu", l.portadaUrl); put("fondo", l.fondo); put("fu", l.fondoUrl); put("uri", l.uri); put("archivo", l.archivo)
        put("favorito", l.favorito); put("estado", l.estado.name); put("anadido", l.anadido); put("ultima", l.ultima); put("fraccion", l.fraccion.toDouble()); put("ff", l.fraccionFijada)
        put("mod", l.mod); put("msp", l.msPropios); put("pg", l.paginas); put("po", l.paginasOrigen)
        put("tm", JSONArray().also { a -> l.terminados.forEach { a.put(it) } })
        put("ajms", JSONObject().also { o -> l.ajenosMs.forEach { (k, v) -> o.put(k, v) } })
    }

    fun aLibro(o: JSONObject) = Libro(
        id = o.optString("id").ifBlank { nuevoId() }, titulo = o.optString("titulo"), autor = o.optString("autor"), clave = o.optString("clave").ifBlank { claveDe(o.optString("titulo"), o.optString("autor")) }, portada = o.optString("portada"), portadaUrl = o.optString("pu"),
        fondo = o.optString("fondo"), fondoUrl = o.optString("fu"),
        uri = o.optString("uri"), archivo = o.optString("archivo"), favorito = o.optBoolean("favorito"),
        estado = Estado.entries.firstOrNull { it.name == o.optString("estado") } ?: Estado.PENDIENTE,
        anadido = o.optLong("anadido"), ultima = o.optLong("ultima"), fraccion = o.optDouble("fraccion", 0.0).toFloat(), fraccionFijada = o.optLong("ff"), mod = o.optLong("mod"),
        msPropios = o.optLong("msp"), ajenosMs = o.optJSONObject("ajms")?.let { a -> a.keys().asSequence().associateWith { k -> a.optLong(k) } } ?: emptyMap(),
        paginas = o.optInt("pg"), paginasOrigen = o.optString("po"),
        terminados = o.optJSONArray("tm")?.let { a -> (0 until a.length()).map { a.optLong(it) }.filter { it > 0 } } ?: emptyList()
    )
}

/** Horas y minutos de lectura: «3 h 20 min», «45 min». */
fun tiempoDe(ms: Long): String {
    val min = ms / 60_000
    return if (min < 60) "$min min" else "${min / 60} h" + if (min % 60 > 0) " ${min % 60} min" else ""
}

/** «Leído 37 %». */
fun porcentaje(f: Float) = "${(f * 100).toInt().coerceIn(0, 100)} %"
/** Lo que llevas de un libro: «37 %» o, si lo has terminado, «Terminado» (en vez de un 98 % o un 100 %). */
fun avance(l: Libro) = if (l.estado == Estado.LEIDO) "Terminado" else porcentaje(l.fraccion)
