package com.gonzalo.marfil.pc

import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Fechas
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.Nodo
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Sitio
import java.io.File
import java.text.Normalizer

enum class Estado(val nombre: String, val cuenta: String) {
    PENDIENTE("Pendiente", "pendiente"), LEYENDO("Leyendo", "en_curso"), LEIDO("Leído", "terminado"), ABANDONADO("Abandonado", "abandonado");
    companion object { fun de(s: String) = entries.firstOrNull { it.cuenta == s } ?: PENDIENTE }
}

/** Dónde está el archivo de un libro en tu kDrive (campo `nube`): su id, su nombre y su tamaño. */
class EnNube(val f: Long, val n: String, val s: Long)

/** Un libro de la cuenta (`libros/<kid>`), tal como lo ve Marfil (los mismos campos que en el móvil). */
class Libro(val n: Nodo, val minutos: Long) {
    val id: String get() = n.ref
    val titulo: String get() = n.texto("t")
    val autor: String get() = n.texto("a")
    val clave: String get() = n.texto("clave").ifBlank { Libros.clave(titulo, autor) }
    val estado: Estado get() = Estado.de(n.texto("estado"))
    val fav: Boolean get() = n.si("fav")
    val fraccion: Float get() = n.decimal("fr").toFloat().coerceIn(0f, 1f)
    val anadido: Long get() = Fechas.ms(n.entero("an"))
    val ultima: Long get() = Fechas.ms(n.entero("ul"))
    val paginas: Int get() = n.entero("pg").toInt()
    val portada: String get() = n.texto("portada")
    val portadaUrl: String get() = n.texto("pu")
    val archivo: String get() = n.texto("archivo")
    val formato: String get() = archivo.substringAfterLast('.', "").lowercase()
    val nube: EnNube? get() = n.mapa("nube").let { m -> m["f"].comoLong().takeIf { it > 0 }?.let { EnNube(it, (m["n"] as? String).orEmpty(), m["s"].comoLong()) } }
    /** Cuándo lo terminaste (la última vez; como Marfil: si no tiene fecha, desde que pasó a terminado). */
    val terminados: List<Long> get() {
        val fin = n.lista("fin").map { Fechas.ms(it.comoLong()) }.filter { it > 0 }
        if (fin.isNotEmpty() || estado != Estado.LEIDO) return fin
        return listOfNotNull((n.m["estado"] ?: 0L).takeIf { it > 946_684_800_000L } ?: n.entero("ul").takeIf { it > 946_684_800_000L })
    }
    /** La nota que le has puesto en Azabache (0–100; null = sin valorar). */
    val nota: Int? get() = n["nota"]?.let { n.entero("nota").toInt() }
}

/**
 * Tus libros: los de la cuenta (`libros/<kid>`, sin copia), lo leído de cada uno (`lectura/<kid>`), el tiempo (`tiempo/<kid>~<dispositivo>`)
 * y dónde está cada archivo en este ordenador (eso no va a la cuenta: es de este ordenador).
 */
object Libros {
    private fun col(): Coleccion = LaFamilia.coleccion("libros")
    fun lectura(): Coleccion = LaFamilia.coleccion("lectura")
    fun tiempo(): Coleccion = LaFamilia.coleccion("tiempo")

    /** Este ordenador para el tiempo leído (`tiempo/<kid>~<dispositivo>`), como `Cuenta.dispositivo` en Android. */
    @Volatile var dispositivo: () -> String = { Sitio.dispositivo("marfil") }
    @Volatile var dirPruebas: File? = null
    private fun dir() = dirPruebas ?: Sitio.de("marfil")

    /** La clave de un libro, como la pone Marfil (`libro:<título>:<autor>`, en minúsculas, sin tildes y solo letras y números). */
    fun clave(titulo: String, autor: String) = "libro:" + norm(titulo) + ":" + norm(autor)
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    /** El nodo de un libro: los 20 primeros hexadecimales del SHA-1 de su clave (el mismo libro, el mismo nodo en todos tus dispositivos). */
    fun kid(clave: String): String = java.security.MessageDigest.getInstance("SHA-1").digest(clave.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    fun todos(): List<Libro> {
        val min = tiempo().vivos().groupBy { it.texto("ref") }.mapValues { (_, ns) -> ns.sumOf { it.entero("total") } / 60_000L }
        return col().vivos().filter { it.texto("t").isNotBlank() }.map { Libro(it, min[it.ref] ?: 0L) }.sortedByDescending { maxOf(it.ultima, it.anadido) }
    }
    fun por(id: String): Libro? = col().nodo(id)?.takeIf { !it.borrado }?.let { n -> Libro(n, tiempo().vivos().filter { it.texto("ref") == id }.sumOf { it.entero("total") } / 60_000L) }

    /** Cambia campos de un libro (solo lo que cambia de verdad va a la cuenta). */
    fun cambia(id: String, campos: Map<String, Any?>) = col().cambia(id, campos)

    /** El estado: «Leído» apunta la fecha (salvo que se apuntara hace poco), como en el móvil. */
    fun ponEstado(l: Libro, e: Estado) {
        val c = LinkedHashMap<String, Any?>()
        c["estado"] = e.cuenta
        val ahora = System.currentTimeMillis()
        if (e == Estado.LEIDO && l.estado != Estado.LEIDO && (l.terminados.lastOrNull() ?: 0L) < ahora - 12L * 3600_000L) c["fin"] = l.n.lista("fin").map { Fechas.ms(it.comoLong()) }.filter { it > 0 } + ahora
        cambia(l.id, c)
    }

    /** Añade un libro (o, si ya está, lo enlaza a este archivo). Devuelve su id. */
    fun anade(titulo: String, autor: String, archivoNombre: String, paginas: Int = 0): String {
        val k = clave(titulo, autor)
        val id = kid(k)
        val n = col().nodo(id)
        val campos = LinkedHashMap<String, Any?>()
        if (n == null || n.borrado) {
            campos["clave"] = k; campos["t"] = titulo; campos["a"] = autor; campos["estado"] = "pendiente"; campos["an"] = System.currentTimeMillis()
            campos["archivo"] = archivoNombre; if (paginas > 0) campos["pg"] = paginas.toLong()
            if (n?.borrado == true) campos["b"] = 0L
        } else if (n.texto("archivo").isBlank()) campos["archivo"] = archivoNombre
        if (campos.isNotEmpty()) col().cambia(id, campos)
        return id
    }

    fun quita(id: String) { col().borra(id); archivos.remove(id); guardaArchivos() }

    // ---------------------------------------------------------------- los archivos, en este ordenador

    private val archivos = HashMap<String, String>().also { m ->
        runCatching { Json.lee(File(dir(), "archivos.json").readText()).comoMapa().forEach { (k, v) -> (v as? String)?.let { m[k] = it } } }
    }
    private fun guardaArchivos() = runCatching { File(dir(), "archivos.json").writeText(Json.escribe(archivos.toSortedMap())) }

    /** La carpeta donde se guardan aquí los libros (los que añades y los que bajan de tu kDrive). */
    fun carpeta(): File = File(dir(), "libros").also { it.mkdirs() }

    /** El archivo de un libro en este ordenador (si está). */
    fun archivo(id: String): File? = (archivos[id]?.let { File(it) } ?: por(id)?.let { l -> File(carpeta(), "$id.${l.formato.ifBlank { "epub" }}") })?.takeIf { it.isFile && it.length() > 0 }

    fun ponArchivo(id: String, f: File) { archivos[id] = f.absolutePath; guardaArchivos() }

    /** Copia un archivo a la carpeta de Marfil y lo apunta (así no depende de dónde estaba). */
    fun guardaCopia(id: String, origen: File): File {
        val destino = File(carpeta(), "$id.${origen.extension.lowercase().ifBlank { "epub" }}")
        if (origen.canonicalPath != destino.canonicalPath) origen.copyTo(destino, overwrite = true)
        ponArchivo(id, destino)
        return destino
    }

    // ---------------------------------------------------------------- por dónde vas

    /** Lo leído de un libro (`lectura/<kid>`): la posición (CFI), el porcentaje, el nombre del capítulo, cuándo y lo más lejos que llegaste. */
    class Posicion(val cfi: String, val fraccion: Double, val etiqueta: String, val cuando: Long, val mejorFraccion: Double, val mejorCfi: String = "", val mejorEtiqueta: String = "", val fijada: Long = 0L)
    fun posicion(id: String): Posicion? = lectura().nodo(id)?.takeIf { !it.borrado }?.let { n ->
        Posicion(n.texto("cfi"), n.decimal("fraction"), n.texto("label"), n.entero("read"), n.decimal("bestFrac"), n.texto("bestCfi"), n.texto("bestLabel"), n.entero("bestSet"))
    }

    /**
     * Apunta por dónde vas ([cfi], [fr] 0–1, el capítulo [etiqueta]): en `lectura/<kid>` (lo que abre el móvil en el mismo sitio) y en el
     * libro lo leído (lo más lejos que has llegado), la última vez y, si toca, «Leyendo» o «Leído» (las mismas reglas que en el móvil).
     * Un [salto] largo (la barra, el índice…) apunta dónde estás pero no cuenta como leído hasta que digas que vas por ahí ([fija]).
     */
    fun leido(id: String, cfi: String, fr: Double, etiqueta: String, msHoy: Long = 0L, salto: Boolean = false) {
        val l = por(id) ?: return
        val ahora = System.currentTimeMillis()
        val pos = LinkedHashMap<String, Any?>()
        pos["ref"] = id; pos["cfi"] = cfi; pos["fraction"] = Math.round(fr * 10000.0) / 10000.0; pos["label"] = etiqueta; pos["read"] = ahora
        val antes = posicion(id)
        if (!salto && fr > (antes?.mejorFraccion ?: 0.0)) { pos["bestCfi"] = cfi; pos["bestFrac"] = pos["fraction"]; pos["bestLabel"] = etiqueta }
        lectura().cambia(id, pos)
        val c = LinkedHashMap<String, Any?>()
        c["ul"] = ahora
        if (salto) { cambia(id, c); return }
        val mejor = maxOf(fr, antes?.mejorFraccion ?: 0.0).toFloat()
        if (mejor > l.fraccion + 0.0005f) c["fr"] = Math.round(mejor * 10000.0) / 10000.0
        val estado = when {
            mejor >= 0.985f -> Estado.LEIDO
            l.estado == Estado.PENDIENTE && (mejor > 0.003f || msHoy >= 60_000L) -> Estado.LEYENDO
            l.estado == Estado.ABANDONADO && mejor > l.fraccion + 0.003f -> Estado.LEYENDO
            else -> l.estado
        }
        cambia(id, c)
        if (estado != l.estado) ponEstado(por(id) ?: l, estado)
    }

    /**
     * «Voy por aquí» (tras un salto largo): esta es tu posición de verdad, aunque sea menos de lo que llegaste, en todos tus dispositivos
     * (como `fijarPosicion` del móvil: `bestSet` con la hora). Lo leído del libro pasa a ser esto.
     */
    fun fija(id: String, cfi: String, fr: Double, etiqueta: String) {
        val l = por(id) ?: return
        val ahora = System.currentTimeMillis()
        val f = Math.round(fr * 10000.0) / 10000.0
        lectura().cambia(id, linkedMapOf("ref" to id, "cfi" to cfi, "fraction" to f, "label" to etiqueta, "read" to ahora, "bestCfi" to cfi, "bestFrac" to f, "bestLabel" to etiqueta, "bestSet" to ahora))
        cambia(id, mapOf("fr" to f, "ul" to ahora))
        val estado = when {
            fr >= 0.985 -> Estado.LEIDO
            l.estado == Estado.LEIDO -> Estado.LEYENDO
            fr <= 0.003 && l.estado == Estado.LEYENDO -> Estado.PENDIENTE
            l.estado == Estado.PENDIENTE && fr > 0.003 -> Estado.LEYENDO
            else -> l.estado
        }
        if (estado != l.estado) ponEstado(por(id) ?: l, estado)
    }

    /** Tu tiempo con un libro (todos tus dispositivos): lo de hoy y el total (ms) y los días seguidos que lo has leído (hasta hoy o ayer). */
    fun tiempoDe(id: String): Triple<Long, Long, Int> {
        val ns = tiempo().vivos().filter { it.texto("ref") == id }
        val hoy = java.time.LocalDate.now()
        val dias = HashMap<String, Long>()
        ns.forEach { n -> n.campos.forEach { (k, v) -> if (k.length == 11 && k[0] == 'd' && k[5] == '-') dias[k.substring(1)] = (dias[k.substring(1)] ?: 0L) + ((v as? Number)?.toLong() ?: 0L) } }
        var d = if ((dias[hoy.toString()] ?: 0L) > 0) hoy else hoy.minusDays(1)
        var racha = 0
        while ((dias[d.toString()] ?: 0L) >= 60_000L) { racha++; d = d.minusDays(1) }
        return Triple(dias[hoy.toString()] ?: 0L, ns.sumOf { it.entero("total") }, racha)
    }

    /** Suma tiempo leído en este ordenador (`tiempo/<kid>~<dispositivo>`: `total` y el del día). */
    fun sumaTiempo(id: String, ms: Long) {
        if (ms <= 0) return
        val l = por(id)
        val dev = dispositivo()
        val ref = "$id~$dev"
        val n = tiempo().nodo(ref)
        val dia = "d" + java.time.LocalDate.now()
        tiempo().cambia(ref, linkedMapOf("ref" to id, "fuente" to dev, "total" to (n?.entero("total") ?: 0L) + ms, dia to (n?.entero(dia) ?: 0L) + ms,
            "t" to (l?.titulo ?: ""), "a" to (l?.autor ?: "")))
    }

    /** El objetivo de lectura (lo fija Marfil en `ajustes/lectura`: `dia` minutos al día, `ano` libros del año, `anio`). */
    fun objetivo(): Triple<Int, Int, Int> = LaFamilia.coleccion("ajustes").nodo("lectura")?.let { Triple(it.entero("dia").toInt(), it.entero("ano").toInt(), it.entero("anio").toInt()) } ?: Triple(0, 0, 0)

    /** Los minutos leídos hoy (todos tus dispositivos). */
    fun minutosHoy(): Long { val d = "d" + java.time.LocalDate.now(); val libros = col().vivos().map { it.ref }.toSet(); return tiempo().vivos().filter { it.texto("ref") in libros }.sumOf { it.entero(d) } / 60_000L }
}
