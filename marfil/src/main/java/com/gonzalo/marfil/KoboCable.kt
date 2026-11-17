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
                "${col("TimeSpentReading")}, ${col("LastTimeFinishedReading")} FROM content WHERE ContentType = 6"
            val r = ArrayList<Kobo.Libro>()
            db.rawQuery(q, null).use { c ->
                while (c.moveToNext()) {
                    val t = c.getString(1)?.trim().orEmpty()
                    val pct = if (c.isNull(3)) 0.0 else c.getDouble(3)
                    val est = if (c.isNull(4)) 0 else c.getInt(4)
                    val seg = if (c.isNull(6)) 0L else c.getLong(6)
                    if (t.isEmpty() || (est == 0 && pct <= 0 && seg <= 0)) continue
                    r += Kobo.Libro(t, c.getString(2)?.trim().orEmpty(), pct.coerceIn(0.0, 100.0), est, Kobo.fecha(c.getString(5)), seg, Kobo.fecha(c.getString(7)))
                }
            }
            return r
        }
    }

    /** Lee el Kobo y sube a la cuenta lo que sepa de más tarde. */
    fun lee(ctx: Context, arbol: Uri): Lectura {
        val kobo = libros(ctx, arbol)
        val p = Kobo.planea(kobo, LaFamilia.coleccion("libros").todos(), LaFamilia.coleccion("tiempo").todos(), System.currentTimeMillis(), ZoneId.systemDefault())
        for (c in p.cambios) LaFamilia.coleccion(c.coleccion).pon(c.ref, c.campos, c.hora)
        apunta(ctx, "cable")
        return Lectura(kobo.size, p.resumen)
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
        hijo(dir, nombre)?.delete()
        val f = dir.createFile("application/octet-stream", nombre) ?: throw Error("No se pudo escribir $nombre en el Kobo.")
        // (algunos selectores añaden una extensión al crear el archivo: se le devuelve su nombre)
        if (f.name != nombre) f.renameTo(nombre)
        ctx.contentResolver.openOutputStream(f.uri, "wt")!!.use { it.write(datos) }
    }

    private fun asset(ctx: Context, nombre: String) = ctx.assets.open("kobo/$nombre").use { it.readBytes() }

    /**
     * Deja el Kobo listo para mandar solo por Wi-Fi: el programa (`.adds/seshat`), con qué cuenta hablar (`cuenta.json`: el pase que se
     * renueva, nunca la contraseña), las entradas del menú (`.adds/nm/seshat`) y, si el Kobo aún no lo tiene, NickelMenu (`.kobo/Kobo.tgz`,
     * que el Kobo instala al desconectarlo). Devuelve si ha puesto NickelMenu.
     */
    fun prepara(ctx: Context, arbol: Uri): Boolean {
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
        for (n in listOf("seshat-kobo", "seshat.sh", "LICENSE-NickelMenu")) escribe(ctx, ds, n, asset(ctx, n))
        escribe(ctx, ds, "cuenta.json", Json.escribe(cuenta).toByteArray())
        val nm = carpeta(adds, "nm")
        hijo(nm, "uninstall")?.delete() // (si alguna vez se desinstaló NickelMenu, si no, no volvería)
        escribe(ctx, nm, "seshat", asset(ctx, "nm-seshat"))
        // NickelMenu crea su carpeta doc al arrancar la primera vez: si está, ya lo tiene
        val ponerNm = hijo(nm, "doc") == null
        if (ponerNm) escribe(ctx, dk, "Kobo.tgz", asset(ctx, "NickelMenu-Kobo.tgz"))
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("preparado", true).apply()
        return ponerNm
    }

    /** Quita del Kobo lo de Seshat (el programa, la cuenta y sus entradas del menú). NickelMenu se queda. */
    fun quita(ctx: Context, arbol: Uri) {
        val r = raiz(ctx, arbol)
        kobo(r)
        hijo(r, ".adds")?.let { a ->
            hijo(a, "seshat")?.delete()
            hijo(a, "nm")?.let { hijo(it, "seshat")?.delete() }
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("preparado", false).apply()
    }
}
