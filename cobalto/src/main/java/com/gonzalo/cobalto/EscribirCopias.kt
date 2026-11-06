package com.gonzalo.cobalto

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Las copias de seguridad completas de lo que escribes, en una carpeta de este dispositivo, aparte de la cuenta (una novela no se puede
 * volver a escribir): todos los documentos, también la papelera. El formato es el de Atrio del PC: `{app, hecha, docs}` en
 * `copia-AAAA-MM-DD_HHMMSS-<n>obras-<p>pal.json`; en el móvil, comprimido (`.json.gz`) para que ocupe poco. Solo se hace una si algo ha
 * cambiado desde la última (o si se pide a mano), y se guardan las [guarda] más nuevas (y, con [tope], no más de esos bytes: se borran las
 * más viejas, dejando siempre las tres últimas). De cualquiera se saca una obra como obra nueva ([recupera]): ids nuevos y los enlaces de
 * dentro intactos; nunca pisa lo que hay.
 *
 * Sin nada de Android: el MISMO archivo en el móvil (CopiasMovil.kt) y en Cobalto para Linux y Mac (pc/Cuaderno.kt, `Copias`).
 */
class CopiasEscritos(
    private val carpeta: () -> File,
    val guarda: Int = 40,
    private val comprimidas: Boolean = false,
    private val tope: Long = Long.MAX_VALUE,
) {
    /** Una copia: su archivo, cuándo se hizo y lo que tenía (sale del nombre). */
    class Copia(val archivo: File, val hecha: Long, val obras: Int, val palabras: Int) {
        val tam: Long get() = archivo.length()
    }

    fun dir(): File = carpeta().also { it.mkdirs() }

    @Volatile private var ultimaFirma: String? = null
    private fun firmaGuardada(): String = ultimaFirma ?: runCatching { File(dir(), FIRMA).readText() }.getOrDefault("").also { ultimaFirma = it }

    /**
     * Hace una copia si algo ha cambiado desde la última (con [forzar], aunque no). Devuelve el archivo, o null si no hacía falta. Se escribe
     * entera en un temporal, se guarda en el disco de verdad (fsync) y solo entonces toma su nombre: una copia a medias nunca pasa por buena.
     */
    @Synchronized
    fun haz(e: Escritos, forzar: Boolean = false, ahora: Long = System.currentTimeMillis()): File? {
        val docs = e.volcado()
        if (docs.length() == 0) return null
        val firma = firma(docs)
        if (!forzar && firma == firmaGuardada()) return null
        val obras = e.obras()
        val palabras = obras.sumOf { e.palabrasObra(it.id) }
        val nombre = "copia-" + FECHA.format(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ahora), java.time.ZoneId.systemDefault())) +
            "-${obras.size}obras-${palabras}pal.json" + if (comprimidas) ".gz" else ""
        val d = dir()
        val f = File(d, nombre)
        val tmp = File(d, "$nombre.tmp")
        val bytes = JSONObject().put("app", "cobalto").put("hecha", ahora).put("docs", docs).toString().toByteArray(Charsets.UTF_8)
        try {
            java.io.FileOutputStream(tmp).use { o ->
                if (comprimidas) { val z = GZIPOutputStream(o); z.write(bytes); z.finish(); o.flush(); o.fd.sync(); z.close() }
                else { o.write(bytes); o.fd.sync() }
            }
            if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) return null }
        } finally { tmp.delete() }
        ultimaFirma = firma
        runCatching { File(d, FIRMA).writeText(firma) }
        poda()
        return f
    }

    /** Las copias que hay, la más nueva primero. */
    fun lista(): List<Copia> = (dir().listFiles { x -> x.isFile && NOMBRE.matches(x.name) } ?: emptyArray()).sortedByDescending { it.name }.map { f ->
        val m = NOMBRE.matchEntire(f.name)!!
        val hecha = runCatching { java.time.LocalDateTime.parse(m.groupValues[1], FECHA).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrDefault(f.lastModified())
        Copia(f, hecha, m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    private fun poda() {
        val d = dir()
        // (lo que quedó a medias porque se cerró la app escribiéndolo)
        d.listFiles { x -> x.name.endsWith(".tmp") && System.currentTimeMillis() - x.lastModified() > 3_600_000L }?.forEach { it.delete() }
        val l = lista()
        l.drop(guarda).forEach { it.archivo.delete() }
        val quedan = l.take(guarda)
        var total = quedan.sumOf { it.tam }
        for (c in quedan.drop(3).reversed()) { if (total <= tope) break; total -= c.tam; c.archivo.delete() }
    }

    /** Los documentos de una copia (comprimida o no). Vacío si no se puede leer. */
    fun lee(f: File): List<JSONObject> = leeArchivo(f)

    companion object {
        /** Dónde se apunta lo que tenía la última copia (para no repetirla si no ha cambiado nada, aunque se cierre la app). */
        const val FIRMA = "ultima-copia.txt"
        private val FECHA = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
        private val NOMBRE = Regex("copia-(\\d{4}-\\d{2}-\\d{2}_\\d{6})-(\\d+)obras-(\\d+)pal\\.json(\\.gz)?")

        /** Lo que identifica lo que hay ahora (cuántos documentos y la última edición): si no cambia, no hace falta otra copia. */
        fun firma(docs: JSONArray): String {
            var u = 0L
            for (i in 0 until docs.length()) u = maxOf(u, docs.optJSONObject(i)?.optLong("u") ?: 0L)
            return "${docs.length()}:$u"
        }

        fun leeArchivo(f: File): List<JSONObject> = runCatching {
            val bytes = if (f.name.endsWith(".gz")) GZIPInputStream(f.inputStream()).use { it.readBytes() } else f.readBytes()
            val a = JSONObject(String(bytes, Charsets.UTF_8)).optJSONArray("docs") ?: JSONArray()
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        }.getOrDefault(emptyList())

        /** La copia tal cual, sin comprimir (para guardarla fuera: es el formato de las del ordenador y de Atrio). */
        fun json(f: File): ByteArray = if (f.name.endsWith(".gz")) GZIPInputStream(f.inputStream()).use { it.readBytes() } else f.readBytes()

        /** Las obras de una copia (también las que estaban en la papelera), por título. */
        fun obras(docs: List<JSONObject>): List<Doc> =
            docs.filter { it.optString("t") == Escritos.T_OBRA && !Escritos.lapida(it) }.map { Doc(it) }.sortedBy { it.s("titulo").lowercase() }

        /** Lo de una obra en una copia: palabras y escenas (para elegir cuál sacar). */
        fun resumen(docs: List<JSONObject>, pid: String): Pair<Int, Int> {
            val esc = deLaObra(docs, pid).filter { it.optString("t") == Escritos.T_ESCENA }
            return esc.sumOf { it.optInt("palabras") } to esc.size
        }

        /**
         * Lo de una obra en la copia, tal como se veía: lo vivo y, si la obra estaba en la papelera, lo que se fue con ella. Nunca las palabras
         * de cada día (ya están contadas: sacarlas otra vez las contaría dos veces) ni las marcas de borrado vacías.
         */
        private fun deLaObra(docs: List<JSONObject>, pid: String): List<JSONObject> {
            val obra = docs.firstOrNull { it.optString("id") == pid } ?: return emptyList()
            val obraBorrada = obra.optInt("d") == 1
            return docs.filter { o ->
                o.optString("pid") == pid && o.optString("t") != Escritos.T_LOG && !Escritos.lapida(o) &&
                    (o.optInt("d") != 1 || (obraBorrada && (o.optString("id") == pid || o.optString("dc") == pid)))
            }
        }

        /**
         * Saca la obra [pid] de una copia como obra NUEVA en [e]: «título (copia)», al final, con todo lo suyo (partes, capítulos, escenas,
         * fichas, notas, uniones del árbol, ramas y sucesos, borradores) con ids nuevos y los enlaces entre ellos puestos a los nuevos. No toca
         * nada de lo que hay. Devuelve la obra nueva (o null si la copia no la tiene).
         */
        fun recupera(e: Escritos, docs: List<JSONObject>, pid: String, sufijo: String = " (copia)", ahora: Long = System.currentTimeMillis()): Doc? {
            val suyos = deLaObra(docs, pid)
            if (suyos.none { it.optString("t") == Escritos.T_OBRA }) return null
            val ids = suyos.associate { it.optString("id") to Escritos.nuevoId(it.optString("t")) }
            val nuevoPid = ids.getValue(pid)
            val orden = (e.obras().maxOfOrNull { it.orden } ?: 0.0) + 1000.0
            val out = ArrayList<JSONObject>()
            for (o in suyos) {
                val c = JSONObject(o.toString())
                listOf("d", "dt", "dc", "u", "pu").forEach { c.remove(it) }
                c.put("id", ids.getValue(o.optString("id"))).put("pid", nuevoPid)
                fun enlace(k: String, obligado: Boolean = false): Boolean {
                    val v = c.optString(k)
                    if (v.isEmpty()) return !obligado
                    val n = ids[v]
                    if (n != null) { c.put(k, n); return true }
                    c.remove(k)
                    return !obligado
                }
                val vale = when (c.optString("t")) {
                    Escritos.T_OBRA -> { c.put("titulo", c.optString("titulo").ifBlank { "Sin título" } + sufijo).put("orden", orden).put("creado", ahora); c.remove("arch"); true }
                    Escritos.T_CAP -> enlace("aid")
                    Escritos.T_ESCENA -> enlace("cid", obligado = true) && enlace("pov") && enlace("lugar")
                    Escritos.T_BORRADOR -> enlace("eid", obligado = true)
                    Escritos.T_HITO -> enlace("eid") && enlace("rama")
                    Escritos.T_REL -> enlace("a", obligado = true) && enlace("b", obligado = true)
                    else -> true
                }
                if (!vale) continue
                c.optJSONArray("vin")?.let { a -> c.put("vin", JSONArray().also { n -> (0 until a.length()).mapNotNull { ids[a.optString(it)] }.forEach { n.put(it) } }) }
                out += c
            }
            e.ponVarios(out)
            return e.get(nuevoPid)
        }
    }
}
