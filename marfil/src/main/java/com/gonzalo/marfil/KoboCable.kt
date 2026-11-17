package com.gonzalo.marfil

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Json
import java.io.File
import java.time.ZoneId

/**
 * El Kobo enchufado al móvil (su memoria, «KOBOeReader», elegida con el selector de carpetas de Android): leerlo y subir lo leído
 * ([lee]), dejarlo preparado para que mande solo por Wi-Fi ([prepara]) o quitar eso ([quita]).
 */
object KoboCable {
    private const val PREFS = "kobo"

    class Error(msg: String) : Exception(msg)

    /** Lo que ha pasado al leer el Kobo: una línea por libro que ha cambiado (vacía: nada nuevo). */
    data class Lectura(val libros: Int, val resumen: List<String>)

    private fun hijo(d: DocumentFile, nombre: String): DocumentFile? = d.findFile(nombre)

    private fun raiz(ctx: Context, arbol: Uri): DocumentFile =
        DocumentFile.fromTreeUri(ctx, arbol)?.takeIf { it.isDirectory } ?: throw Error("No se puede abrir esa carpeta.")

    /** Comprueba que [d] es la memoria de un Kobo (tiene `.kobo`). */
    private fun kobo(d: DocumentFile): DocumentFile = hijo(d, ".kobo")?.takeIf { it.isDirectory }
        ?: throw Error("Esa carpeta no es la memoria de un Kobo: elige la que se llama KOBOeReader (donde está la carpeta .kobo).")

    /** Lee los libros del Kobo (copia su base de datos al móvil; en el Kobo no se toca nada). */
    fun libros(ctx: Context, arbol: Uri): List<Kobo.Libro> {
        val dk = kobo(raiz(ctx, arbol))
        val bd = hijo(dk, "KoboReader.sqlite") ?: throw Error("No está la base de datos del Kobo (.kobo/KoboReader.sqlite).")
        val dir = File(ctx.cacheDir, "kobo").apply { deleteRecursively(); mkdirs() }
        val copia = File(dir, "KoboReader.sqlite")
        fun copiar(de: DocumentFile, a: File) { ctx.contentResolver.openInputStream(de.uri)!!.use { i -> a.outputStream().use { i.copyTo(it) } } }
        copiar(bd, copia)
        for (ext in listOf("-wal", "-shm")) hijo(dk, "KoboReader.sqlite$ext")?.let { runCatching { copiar(it, File(dir, "KoboReader.sqlite$ext")) } }
        try {
            return leeBase(copia)
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Los libros de una copia de KoboReader.sqlite. Kobo ha cambiado las columnas con los años: se pregunta cuáles hay y las que falten
     * cuentan como vacías (como hace el programa del Kobo).
     */
    fun leeBase(archivo: File): List<Kobo.Libro> {
        val db = SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READWRITE)
        db.use {
            val cols = HashSet<String>()
            db.rawQuery("PRAGMA table_info(content)", null).use { c -> while (c.moveToNext()) cols += c.getString(1).lowercase() }
            if ("contentid" !in cols || "contenttype" !in cols) throw Error("La base de datos del Kobo no es como se esperaba.")
            fun col(n: String) = if (n.lowercase() in cols) n else "NULL"
            val titulo = when {
                "title" in cols && "booktitle" in cols -> "COALESCE(NULLIF(Title, ''), BookTitle)"
                "title" in cols -> "Title"
                "booktitle" in cols -> "BookTitle"
                else -> "NULL"
            }
            val q = "SELECT ContentID, $titulo, ${col("Attribution")}, ${col("___PercentRead")}, ${col("ReadStatus")}, ${col("DateLastRead")}, " +
                "${col("TimeSpentReading")}, ${col("LastTimeFinishedReading")}, ${col("ChapterIDBookmarked")} FROM content WHERE ContentType = 6"
            val r = ArrayList<Kobo.Libro>()
            db.rawQuery(q, null).use { c ->
                while (c.moveToNext()) {
                    val t = c.getString(1)?.trim().orEmpty()
                    val pct = if (c.isNull(3)) 0.0 else c.getDouble(3)
                    val est = if (c.isNull(4)) 0 else c.getInt(4)
                    val seg = if (c.isNull(6)) 0L else c.getLong(6)
                    if (t.isEmpty() || (est == 0 && pct <= 0 && seg <= 0)) continue
                    val id = c.getString(0).orEmpty()
                    r += Kobo.Libro(t, c.getString(2)?.trim().orEmpty(), pct.coerceIn(0.0, 100.0), est, Kobo.fecha(c.getString(5)), seg, Kobo.fecha(c.getString(7)),
                        marca = if (Kobo.esKepub(id)) c.getString(8)?.trim().orEmpty() else "", contentId = id)
                }
            }
            return r
        }
    }

    /** Lee el Kobo y sube a la cuenta lo que sepa de más tarde. */
    fun lee(ctx: Context, arbol: Uri): Lectura {
        if (!LaFamilia.familia.sesion.dentro) throw Error("Entra en tu cuenta (Ajustes → Cuenta) antes de leer el Kobo.")
        val kobo = libros(ctx, arbol).let { ls -> conHuellasKepub(ctx, raiz(ctx, arbol), ls) }
        // Con los libros de la cuenta al día (si no, un libro que aún no ha bajado a este móvil se tomaría por nuevo)
        val inf = LaFamilia.sincronizaYa()
        if (!LaFamilia.coleccion("libros").completa && !inf.ok) throw Error("Sin conexión con la cuenta: hace falta para saber qué libros tienes ya.")
        val p = Kobo.planea(kobo, LaFamilia.coleccion("libros").todos(), LaFamilia.coleccion("tiempo").todos(), System.currentTimeMillis(), ZoneId.systemDefault())
        for (c in p.cambios) LaFamilia.coleccion(c.coleccion).pon(c.ref, c.campos, c.hora)
        apunta(ctx, "cable")
        return Lectura(kobo.size, p.resumen)
    }

    /** La huella de cada kepub copiado al Kobo (para que SesKobo sepa que es el mismo archivo que tiene); sin archivo, sin marca. */
    private fun conHuellasKepub(ctx: Context, r: DocumentFile, libros: List<Kobo.Libro>): List<Kobo.Libro> {
        val listados = HashMap<String, Map<String, DocumentFile>>()
        return libros.map { l ->
            if (l.marca.isEmpty()) return@map l
            var f: DocumentFile? = r
            for (p in l.contentId.removePrefix("file:///mnt/onboard/").split('/')) f = f?.let { d -> listados.getOrPut(d.uri.toString()) { d.listFiles().associateBy { it.name.orEmpty() } }[p] }
            val h = f?.takeIf { it.isFile }?.let { d -> runCatching { ctx.contentResolver.openInputStream(d.uri)?.use { KoSync.huellaDe(it) } }.getOrNull() }
            if (h == null) l.copy(marca = "") else l.copy(huellaKepub = h)
        }
    }

    /** Lo último que llegó del Kobo por aquí: (hora, «cable»), o null. (Lo que manda el Kobo por Wi-Fi se ve en los libros.) */
    fun ultima(ctx: Context): Pair<Long, String>? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val t = p.getLong("ultima", 0L)
        return if (t > 0) t to p.getString("como", "").orEmpty() else null
    }

    private fun apunta(ctx: Context, como: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("ultima", System.currentTimeMillis()).putString("como", como).apply()

    fun preparado(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("preparado", false)

    private fun carpeta(d: DocumentFile, nombre: String): DocumentFile =
        hijo(d, nombre)?.takeIf { it.isDirectory } ?: d.createDirectory(nombre) ?: throw Error("No se pudo crear la carpeta $nombre en el Kobo.")

    private fun escribe(ctx: Context, dir: DocumentFile, nombre: String, datos: ByteArray) {
        // Encima del que ya hay (truncándolo); si no está, se crea. Borrar y crear puede dejar «nombre (1)» si el borrado falla.
        val f = hijo(dir, nombre)?.takeIf { it.isFile } ?: dir.createFile("application/octet-stream", nombre)?.also {
            // (algunos selectores añaden una extensión al crear el archivo: se le devuelve su nombre)
            if (it.name != nombre) it.renameTo(nombre)
        } ?: throw Error("No se pudo escribir $nombre en el Kobo.")
        ctx.contentResolver.openOutputStream(f.uri, "wt")!!.use { it.write(datos) }
    }

    private fun asset(ctx: Context, nombre: String) = ctx.assets.open("kobo/$nombre").use { it.readBytes() }

    /** Lo que ha hecho [prepara]: si pone los plugins (el Kobo se reinicia al desconectarlo) y cuántos libros van con posición. */
    data class Preparado(val plugins: Boolean, val conPosicion: Int)

    /**
     * Deja el Kobo listo para mandar solo por Wi-Fi: el programa (`.adds/seshat`), con qué cuenta hablar (`cuenta.json`: el pase que se
     * renueva, nunca la contraseña), las entradas del menú (`.adds/nm/seshat`) y, si hay usuario de posiciones (KOSync), NickelInkSync
     * (`.adds/NickelInkSync`: su programa, su configuración y el índice de los libros del Kobo). Si al Kobo le falta alguno de los dos
     * plugins (NickelMenu, NickelInkSync), `.kobo/Kobo.tgz` con los dos: el Kobo los instala al desconectarlo.
     */
    fun prepara(ctx: Context, arbol: Uri): Preparado {
        val r = raiz(ctx, arbol)
        val dk = kobo(r)
        val ses = LaFamilia.familia.sesion.comparte()
        val cfg = LaFamilia.familia.cfg
        if (ses.isEmpty() || (ses["claveWeb"] as? String).isNullOrBlank()) throw Error("Entra en tu cuenta (Ajustes → Cuenta) antes de preparar el Kobo.")
        val cuenta = linkedMapOf(
            "uid" to ses["uid"], "refresco" to ses["refresco"], "claveWeb" to ses["claveWeb"], "baseDatos" to cfg.baseDatos, "baseToken" to cfg.baseToken,
            "extra" to cfg.extra, "zona" to ZoneId.systemDefault().id, "dispositivo" to Kobo.DISPOSITIVO,
        )
        val adds = carpeta(r, ".adds")
        val ds = carpeta(adds, "seshat")
        for (n in listOf("seshat-kobo", "seshat.sh", "LICENSE-NickelMenu", "LICENSE-NickelInkSync")) escribe(ctx, ds, n, asset(ctx, n))
        escribe(ctx, ds, "cuenta.json", Json.escribe(cuenta).toByteArray())
        val nm = carpeta(adds, "nm")
        hijo(nm, "uninstall")?.delete() // (si alguna vez se desinstaló NickelMenu, si no, no volvería)
        escribe(ctx, nm, "seshat", asset(ctx, "nm-seshat"))
        var plugins = hijo(nm, "doc") == null // (NickelMenu crea su carpeta doc al arrancar la primera vez)
        var conPosicion = 0
        KoSync.datosParaElKobo(ctx)?.let { (servidor, usuario, clave) ->
            val dn = carpeta(adds, "NickelInkSync")
            hijo(dn, "uninstall")?.delete()
            escribe(ctx, dn, "nickelinksync", asset(ctx, "nickelinksync"))
            escribe(ctx, dn, "config.ini", configNis(servidor, usuario, clave).toByteArray())
            val libros = epubsDelKobo(ctx, r, dk)
            conPosicion = libros.size
            // Lo que ya hubiera en el índice (si lo preparó NickelInkSync desde un ordenador) se queda
            val antes = hijo(dn, "library.json")?.let { f -> runCatching { ctx.contentResolver.openInputStream(f.uri)!!.use { it.readBytes().decodeToString() } }.getOrNull() }
            escribe(ctx, dn, "library.json", indiceNis(libros + librosDeIndice(antes)).toByteArray())
            if (hijo(dn, "NickelInkSync.log") == null) plugins = true // (el plugin apunta ahí en cuanto arranca)
        }
        if (plugins) escribe(ctx, dk, "Kobo.tgz", asset(ctx, "Kobo.tgz"))
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("preparado", true).apply()
        return Preparado(plugins, conPosicion)
    }

    /** La configuración de NickelInkSync: el servidor y el usuario de SesKobo; la contraseña, solo su MD5 (lo que pide KOSync). */
    fun configNis(servidor: String, usuario: String, clave: String) = """
        |# Lo pone SesKobo al preparar el Kobo: la posición de cada libro en el servidor KOSync, la misma que ve SesKobo.
        |server = $servidor
        |user = $usuario
        |key = $clave
        |# Gana la posición más reciente (el plugin aún no sabe preguntar)
        |confirm = never
        |# Avisos en la pantalla al abrir y dejar un libro (SesKobo los ha traducido al español)
        |notify = all
        |position = paragraph
        |""".trimMargin()

    /** Un libro del Kobo para el índice de NickelInkSync: su huella de KOReader, título y autor. */
    data class LibroNis(val huella: String, val titulo: String, val autor: String)

    /** `library.json` de NickelInkSync (formato 1): los libros cuya posición se sincroniza. */
    fun indiceNis(libros: List<LibroNis>): String = Json.escribe(linkedMapOf(
        "format" to 1L, "created" to java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString(),
        "books" to libros.distinctBy { it.huella }.sortedBy { it.huella }.map { l ->
            linkedMapOf<String, Any?>("hash" to l.huella, "title" to l.titulo).apply { if (l.autor.isNotBlank()) put("authors", listOf(l.autor)) }
        },
    ))

    /** Los libros de un `library.json` de NickelInkSync (vacío si no hay o no se entiende). */
    fun librosDeIndice(texto: String?): List<LibroNis> = runCatching {
        val a = org.json.JSONObject(texto!!).getJSONArray("books")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> LibroNis(o.getString("hash"), o.optString("title"), o.optJSONArray("authors")?.optString(0).orEmpty()) } }
    }.getOrDefault(emptyList())

    /** Los EPUB copiados al Kobo (no los kepub: su archivo no es el de Seshat), con su huella. Los saca de la base de datos del Kobo. */
    private fun epubsDelKobo(ctx: Context, r: DocumentFile, dk: DocumentFile): List<LibroNis> {
        val bd = hijo(dk, "KoboReader.sqlite") ?: return emptyList()
        val dir = File(ctx.cacheDir, "kobo-nis").apply { deleteRecursively(); mkdirs() }
        val copia = File(dir, "KoboReader.sqlite")
        fun copiar(de: DocumentFile, a: File) { ctx.contentResolver.openInputStream(de.uri)!!.use { i -> a.outputStream().use { i.copyTo(it) } } }
        try {
            copiar(bd, copia)
            for (ext in listOf("-wal", "-shm")) hijo(dk, "KoboReader.sqlite$ext")?.let { runCatching { copiar(it, File(dir, "KoboReader.sqlite$ext")) } }
            val filas = ArrayList<Triple<String, String, String>>()
            SQLiteDatabase.openDatabase(copia.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("SELECT ContentID, COALESCE(Title, ''), COALESCE(Attribution, '') FROM content WHERE ContentType = 6 AND ContentID LIKE 'file:///mnt/onboard/%'", null).use { c ->
                    while (c.moveToNext()) filas += Triple(c.getString(0), c.getString(1), c.getString(2))
                }
            }
            val listados = HashMap<String, Map<String, DocumentFile>>()
            return filas.mapNotNull { (id, t, a) ->
                val ruta = id.removePrefix("file:///mnt/onboard/")
                if (!ruta.lowercase().endsWith(".epub") || ruta.lowercase().endsWith(".kepub.epub")) return@mapNotNull null
                var f: DocumentFile? = r
                for (p in ruta.split('/')) f = f?.let { d -> listados.getOrPut(d.uri.toString()) { d.listFiles().associateBy { it.name.orEmpty() } }[p] }
                val h = f?.takeIf { it.isFile }?.let { d -> runCatching { ctx.contentResolver.openInputStream(d.uri)?.use { KoSync.huellaDe(it) } }.getOrNull() }
                h?.let { LibroNis(it, t.trim(), a.trim()) }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    // ---- Vincular kepub con EPUB: el kepub del Kobo con el EPUB del mismo libro en SesKobo, para llevar la posición entre los dos

    /** Un kepub del Kobo y el libro de SesKobo que parece ser el mismo ([libro] null: ninguno con EPUB). [seguro]: sale marcado. */
    data class Pareja(val kepub: String, val archivoKobo: String, val tituloKobo: String, val autorKobo: String, val libro: Libro?, val seguro: Boolean)

    /** Un libro de SesKobo con su EPUB (no un kepub ni otro formato): lo que se puede vincular. */
    private fun conEpub(l: Libro) = l.uri.isNotBlank() && !l.esEscrito &&
        l.archivo.ifBlank { Uri.decode(l.uri) }.lowercase().let { it.endsWith(".epub") && !it.endsWith(".kepub.epub") }

    /**
     * Las parejas posibles entre los [kepubs] del Kobo (ContentID, título, autor) y [libros] de SesKobo: mismo título; si sale más de
     * uno, o los autores no coinciden, la pareja queda sin marcar (puede no ser el mismo). Pura (así se prueba).
     */
    fun parejas(kepubs: List<Triple<String, String, String>>, libros: List<Libro>): List<Pareja> = kepubs.map { (id, t, a) ->
        val nt = norm(t)
        val mismos = libros.filter { conEpub(it) && norm(it.titulo) == nt }
        val pa = palabras(a)
        val conAutor = mismos.filter { pa.isEmpty() || palabras(it.autor).isEmpty() || palabras(it.autor) == pa }
        val l = conAutor.singleOrNull() ?: mismos.firstOrNull()
        Pareja(id, id.substringAfterLast('/'), t, a, l, seguro = l != null && conAutor.size == 1 && mismos.size == 1)
    }

    private fun palabras(s: String) = norm(s).split(' ').filter { it.isNotEmpty() }.sorted().joinToString(" ")

    /** Los vínculos ya hechos en el Kobo (`.adds/seshat/vinculos.json`): ContentID del kepub → archivo del original. */
    private fun vinculos(ctx: Context, ds: DocumentFile?): LinkedHashMap<String, org.json.JSONObject> {
        val r = LinkedHashMap<String, org.json.JSONObject>()
        val f = ds?.let { hijo(it, "vinculos.json") } ?: return r
        runCatching {
            val a = org.json.JSONObject(ctx.contentResolver.openInputStream(f.uri)!!.use { it.readBytes().decodeToString() }).getJSONArray("vinculos")
            for (i in 0 until a.length()) a.getJSONObject(i).let { r[it.getString("kepub")] = it }
        }
        return r
    }

    /** Los kepub del Kobo aún sin vincular, con el libro de SesKobo que parece el mismo. */
    fun buscaParejas(ctx: Context, arbol: Uri): List<Pareja> {
        if (!KoSync.activo(ctx)) throw Error("Pon antes tu usuario de posiciones (más abajo, en «Posición en el Kobo») y prepara el Kobo.")
        val r = raiz(ctx, arbol)
        val dk = kobo(r)
        if (hijo(r, ".adds")?.let { hijo(it, "NickelInkSync") }?.let { hijo(it, "nickelinksync") } == null) throw Error("Prepara antes el Kobo para Wi-Fi (con tu usuario de posiciones): hace falta NickelInkSync.")
        val ya = vinculos(ctx, hijo(r, ".adds")?.let { hijo(it, "seshat") })
        val bd = hijo(dk, "KoboReader.sqlite") ?: throw Error("No está la base de datos del Kobo (.kobo/KoboReader.sqlite).")
        val dir = File(ctx.cacheDir, "kobo-vincular").apply { deleteRecursively(); mkdirs() }
        val kepubs = ArrayList<Triple<String, String, String>>()
        try {
            val copia = File(dir, "KoboReader.sqlite")
            ctx.contentResolver.openInputStream(bd.uri)!!.use { i -> copia.outputStream().use { i.copyTo(it) } }
            for (ext in listOf("-wal", "-shm")) hijo(dk, "KoboReader.sqlite$ext")?.let { f -> runCatching { ctx.contentResolver.openInputStream(f.uri)!!.use { i -> File(dir, "KoboReader.sqlite$ext").outputStream().use { i.copyTo(it) } } } }
            SQLiteDatabase.openDatabase(copia.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("SELECT ContentID, COALESCE(Title, ''), COALESCE(Attribution, '') FROM content WHERE ContentType = 6", null).use { c ->
                    while (c.moveToNext()) { val id = c.getString(0); if (Kobo.esKepub(id) && id !in ya) kepubs += Triple(id, c.getString(1).trim(), c.getString(2).trim()) }
                }
            }
        } finally { dir.deleteRecursively() }
        return parejas(kepubs, Biblioteca.propios()).sortedBy { it.tituloKobo.lowercase() }
    }

    /**
     * Vincula [elegidas]: copia el EPUB de cada una al Kobo (`.adds/seshat/originales/<huella>.orig`: con esa extensión el Kobo no lo
     * toma por un libro) y lo apunta en `vinculos.json`. El Kobo termina solo (empareja y prepara) la próxima vez que mande con Wi-Fi.
     */
    fun vincula(ctx: Context, arbol: Uri, elegidas: List<Pareja>): Int {
        val r = raiz(ctx, arbol)
        kobo(r)
        val ds = carpeta(carpeta(r, ".adds"), "seshat")
        val orig = carpeta(ds, "originales")
        val lista = vinculos(ctx, ds)
        var n = 0
        for (p in elegidas) {
            val l = p.libro ?: continue
            val u = Uri.parse(l.uri)
            val h = KoSync.huella(ctx, u) ?: throw Error("No se puede leer el EPUB de «${l.titulo}».")
            val nombre = "$h.orig"
            if (hijo(orig, nombre) == null) escribe(ctx, orig, nombre, ctx.contentResolver.openInputStream(u)!!.use { it.readBytes() })
            lista[p.kepub] = org.json.JSONObject().put("kepub", p.kepub).put("original", nombre).put("titulo", l.titulo)
            n++
        }
        escribe(ctx, ds, "vinculos.json", org.json.JSONObject().put("vinculos", org.json.JSONArray(lista.values.toList())).toString(1).toByteArray())
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("vinculados", lista.size).apply()
        return n
    }

    fun vinculados(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("vinculados", 0)

    /** Quita del Kobo lo de Seshat (el programa, la cuenta, sus entradas del menú y NickelInkSync). NickelMenu se queda. */
    fun quita(ctx: Context, arbol: Uri) {
        val r = raiz(ctx, arbol)
        kobo(r)
        hijo(r, ".adds")?.let { a ->
            hijo(a, "seshat")?.delete() // (con los originales de los vinculados)
            hijo(a, "nm")?.let { hijo(it, "seshat")?.delete() }
            // NickelInkSync: fuera su configuración y su programa, y el aviso de desinstalarse (lo hace al reiniciar el Kobo)
            hijo(a, "NickelInkSync")?.let { d ->
                for (n in listOf("config.ini", "library.json", "nickelinksync")) hijo(d, n)?.delete()
                if (hijo(d, "uninstall") == null) d.createFile("application/octet-stream", "uninstall")?.let { f -> if (f.name != "uninstall") f.renameTo("uninstall") }
            }
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("preparado", false).putInt("vinculados", 0).apply()
    }
}
