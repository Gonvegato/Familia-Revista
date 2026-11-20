package com.gonzalo.familia

import android.content.Context
import com.gonzalo.familia.nucleo.GoogleDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * La nube común de todas las apps de la familia en Android:
 *  - **Google Drive** (principal, API v3 vía [GoogleDrive] de `:nucleo`): libros, ROMs, guardados, fotos, películas y actualizaciones.
 *  - **kDrive** (respaldo durante la transición): lo antiguo que aún no se haya movido sigue bajándose y funcionando sin romper nada.
 *
 * Las credenciales viven una sola vez en `/claves/<uid>` (gestionadas desde Jonsu).
 */
object NubeFamilia {
    class Fail(msg: String) : IOException(msg)
    class Item(val name: String, val id: Long, val dir: Boolean, val visibility: String, val size: Long = 0L)

    /** Un archivo de `versiones` (en Google Drive o en kDrive). */
    class VersionRemota(val nombre: String, val id: String, val deGoogleDrive: Boolean)

    /** Resultado de una subida: lleva `gdId` si subió a Google Drive o `kdId` si subió a kDrive. */
    class Subida(val gdId: String = "", val kdId: Long = 0L) {
        val esGoogleDrive: Boolean get() = gdId.isNotBlank()
    }

    /** Se cambia en las pruebas por un servidor simulado. */
    @Volatile var api = "https://api.infomaniak.com"
    @Volatile var share = "https://kdrive.infomaniak.com"
    @Volatile var gdrivePrueba: GoogleDrive? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nube", Context.MODE_PRIVATE)
    private fun clave(n: String) = runCatching { LaFamilia.familia.clave(n) }.getOrDefault("")

    // ---------- Google Drive (principal) ----------

    private fun probandoGd(ctx: Context) = prefs(ctx).getBoolean("gd_probando", false)

    fun gdriveClientId(ctx: Context): String =
        if (probandoGd(ctx)) prefs(ctx).getString("gd_cid", "").orEmpty()
        else clave(GoogleDrive.CLAVE_CLIENT_ID).ifBlank { prefs(ctx).getString("gd_cid", "").orEmpty() }

    fun gdriveClientSecret(ctx: Context): String =
        if (probandoGd(ctx)) prefs(ctx).getString("gd_csec", "").orEmpty()
        else clave(GoogleDrive.CLAVE_CLIENT_SECRET).ifBlank { prefs(ctx).getString("gd_csec", "").orEmpty() }

    fun gdriveRefreshToken(ctx: Context): String =
        if (probandoGd(ctx)) prefs(ctx).getString("gd_rtok", "").orEmpty()
        else clave(GoogleDrive.CLAVE_REFRESH_TOKEN).ifBlank { prefs(ctx).getString("gd_rtok", "").orEmpty() }

    fun gdriveCarpeta(ctx: Context): String =
        if (probandoGd(ctx)) prefs(ctx).getString("gd_dir", "").orEmpty()
        else clave(GoogleDrive.CLAVE_CARPETA).ifBlank { prefs(ctx).getString("gd_dir", "").orEmpty() }

    fun gdriveOn(ctx: Context): Boolean =
        gdrivePrueba?.activo ?: (gdriveClientId(ctx).isNotBlank() && gdriveClientSecret(ctx).isNotBlank() && gdriveRefreshToken(ctx).isNotBlank())

    fun gdrive(ctx: Context): GoogleDrive = gdrivePrueba ?: GoogleDrive(
        clientId = { gdriveClientId(ctx) },
        clientSecret = { gdriveClientSecret(ctx) },
        refreshToken = { gdriveRefreshToken(ctx) },
        carpetaConfig = { gdriveCarpeta(ctx) },
    )

    fun saveGDrive(ctx: Context, clientId: String, clientSecret: String, refreshToken: String, carpeta: String = "") {
        prefs(ctx).edit()
            .putString("gd_cid", clientId.trim())
            .putString("gd_csec", clientSecret.trim())
            .putString("gd_rtok", refreshToken.trim())
            .putString("gd_dir", GoogleDrive.extraeIdCarpeta(carpeta))
            .putBoolean("gd_probando", true)
            .apply()
    }

    fun confirmaGDrive(ctx: Context) {
        val p = prefs(ctx)
        val cid = p.getString("gd_cid", "").orEmpty()
        val csec = p.getString("gd_csec", "").orEmpty()
        val rtok = p.getString("gd_rtok", "").orEmpty()
        val dir = p.getString("gd_dir", "").orEmpty()
        p.edit().remove("gd_probando").apply()
        if (cid.isNotBlank() && csec.isNotBlank() && rtok.isNotBlank()) runCatching {
            LaFamilia.familia.ponClave(GoogleDrive.CLAVE_CLIENT_ID, cid)
            LaFamilia.familia.ponClave(GoogleDrive.CLAVE_CLIENT_SECRET, csec)
            LaFamilia.familia.ponClave(GoogleDrive.CLAVE_REFRESH_TOKEN, rtok)
            if (dir.isNotBlank()) LaFamilia.familia.ponClave(GoogleDrive.CLAVE_CARPETA, dir)
        }
    }

    fun descartaGDrive(ctx: Context) {
        prefs(ctx).edit().remove("gd_probando").remove("gd_cid").remove("gd_csec").remove("gd_rtok").remove("gd_dir").apply()
    }

    fun forgetGDrive(ctx: Context) {
        descartaGDrive(ctx)
        runCatching {
            LaFamilia.familia.ponClave(GoogleDrive.CLAVE_CLIENT_ID, "")
            LaFamilia.familia.ponClave(GoogleDrive.CLAVE_CLIENT_SECRET, "")
            LaFamilia.familia.ponClave(GoogleDrive.CLAVE_REFRESH_TOKEN, "")
            LaFamilia.familia.ponClave(GoogleDrive.CLAVE_CARPETA, "")
        }
    }

    suspend fun testGDrive(ctx: Context): String = withContext(Dispatchers.IO) {
        gdrive(ctx).prueba()
    }

    // ---------- kDrive (respaldo durante la transición) ----------

    private fun probando(ctx: Context) = prefs(ctx).getBoolean("probando", false)
    fun token(ctx: Context) = if (probando(ctx)) prefs(ctx).getString("token", "").orEmpty() else clave("kdrive_token").ifBlank { prefs(ctx).getString("token", "").orEmpty() }
    fun drive(ctx: Context) = if (probando(ctx)) prefs(ctx).getString("drive", "").orEmpty() else clave("kdrive_id").ifBlank { prefs(ctx).getString("drive", "").orEmpty() }
    fun kdriveOn(ctx: Context) = token(ctx).isNotBlank() && drive(ctx).isNotBlank()

    /** ¿Hay alguna nube conectada (Google Drive o kDrive)? */
    fun on(ctx: Context) = gdriveOn(ctx) || kdriveOn(ctx)
    fun nombreNube(ctx: Context) = if (gdriveOn(ctx)) "Google Drive" else "kDrive"

    fun save(ctx: Context, token: String, drive: String) {
        val d = Regex("""drive/(\d+)""").find(drive)?.groupValues?.get(1) ?: drive.filter { it.isDigit() }
        prefs(ctx).edit().putString("token", token.trim()).putString("drive", d).putBoolean("probando", true).remove("dirs").remove("home").remove("versDir").apply()
    }

    fun confirma(ctx: Context) {
        val t = prefs(ctx).getString("token", "").orEmpty(); val d = prefs(ctx).getString("drive", "").orEmpty()
        prefs(ctx).edit().remove("probando").apply()
        if (t.isNotBlank() && d.isNotBlank()) runCatching { LaFamilia.familia.ponClave("kdrive_token", t); LaFamilia.familia.ponClave("kdrive_id", d) }
    }

    fun descarta(ctx: Context) { prefs(ctx).edit().remove("probando").remove("token").remove("drive").remove("dirs").remove("home").remove("versDir").apply() }

    fun forget(ctx: Context) {
        prefs(ctx).edit().remove("probando").remove("token").remove("drive").remove("dirs").remove("home").remove("versDir").apply()
        runCatching { LaFamilia.familia.ponClave("kdrive_token", ""); LaFamilia.familia.ponClave("kdrive_id", "") }
    }

    fun migraALaCuenta(ctx: Context) {
        val t = prefs(ctx).getString("token", "").orEmpty(); val d = prefs(ctx).getString("drive", "").orEmpty()
        if (probando(ctx) || t.isBlank() || d.isBlank() || clave("kdrive_token").isNotBlank()) return
        runCatching { LaFamilia.familia.ponClave("kdrive_token", t); LaFamilia.familia.ponClave("kdrive_id", d) }
    }

    // ---------- Operaciones duales (Google Drive primero, kDrive de respaldo) ----------

    /** Versiones disponibles en `versiones` (Google Drive primero; si no está o está vacío, kDrive). */
    fun versionesDual(ctx: Context): List<VersionRemota> {
        if (gdriveOn(ctx)) {
            val gd = runCatching { gdrive(ctx).versiones().map { VersionRemota(it.nombre, it.id, true) } }.getOrDefault(emptyList())
            if (gd.isNotEmpty()) return gd
        }
        if (kdriveOn(ctx)) {
            return versionesKDrive(ctx).map { (n, id) -> VersionRemota(n, id.toString(), false) }
        }
        return emptyList()
    }

    fun bajaVersion(ctx: Context, v: VersionRemota, dest: File) {
        if (v.deGoogleDrive) gdrive(ctx).baja(v.id, dest)
        else downloadKDrive(ctx, v.id.toLong(), dest)
    }

    /** Sube a Google Drive (si está activo) o a kDrive. */
    fun subeDual(ctx: Context, path: String, name: String, size: Long, onProgress: (Long) -> Unit = {}, input: () -> InputStream): Subida {
        if (gdriveOn(ctx)) {
            val gdId = gdrive(ctx).sube(path, name, size, onProgress, input)
            return Subida(gdId = gdId)
        }
        if (kdriveOn(ctx)) {
            val kdId = uploadKDrive(ctx, path, name, size, onProgress, input)
            return Subida(kdId = kdId)
        }
        throw Fail("No hay ninguna nube conectada (configura Google Drive en Polaris → Ajustes)")
    }

    /** Baja de Google Drive si tiene [gdId], o de kDrive si tiene [kdId]. */
    fun bajaDual(ctx: Context, gdId: String, kdId: Long, dest: File) {
        if (gdId.isNotBlank() && gdriveOn(ctx)) {
            gdrive(ctx).baja(gdId, dest)
            return
        }
        if (kdId > 0L && kdriveOn(ctx)) {
            downloadKDrive(ctx, kdId, dest)
            return
        }
        throw Fail(if (gdId.isNotBlank()) "Conecta tu Google Drive en Polaris para bajar este archivo" else "Este archivo está en tu kDrive antiguo; conéctalo o migra tus archivos a Google Drive")
    }

    /** Enlace público para tu pareja (Google Drive si tiene `gdId`, kDrive si solo tiene `kdId`). */
    fun enlacePublico(ctx: Context, gdId: String, kdId: Long): String {
        if (gdId.isNotBlank() && gdriveOn(ctx)) return gdrive(ctx).enlacePublico(gdId)
        if (kdId > 0L && kdriveOn(ctx)) return linkKDrive(ctx, kdId)
        return ""
    }

    /** Baja por enlace público (Google Drive o kDrive), sin cuenta. */
    fun descargaEnlace(enlace: String, kdId: Long, dest: File) {
        if (GoogleDrive.esEnlaceGoogleDrive(enlace)) {
            GoogleDrive({ "" }, { "" }, { "" }).descargaEnlace(enlace, dest)
            return
        }
        downloadSharedKDrive(enlace, kdId, dest)
    }

    fun existeDual(ctx: Context, gdId: String, kdId: Long): Boolean? {
        if (gdId.isNotBlank() && gdriveOn(ctx)) return gdrive(ctx).existe(gdId)
        if (kdId > 0L && kdriveOn(ctx)) return existsKDrive(ctx, kdId)
        return null
    }

    fun borraDual(ctx: Context, gdId: String, kdId: Long) {
        if (gdId.isNotBlank() && gdriveOn(ctx)) runCatching { gdrive(ctx).borra(gdId) }
        if (kdId > 0L && kdriveOn(ctx)) runCatching { deleteKDrive(ctx, kdId) }
    }

    // ---------- Implementación kDrive (única para toda la familia) ----------

    private fun call(ctx: Context, method: String, path: String, body: String? = null): JSONObject {
        val c = URL(api + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20000; c.readTimeout = 60000
        c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        return answer(c)
    }

    private fun answer(c: HttpURLConnection): JSONObject {
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val o = runCatching { JSONObject(txt) }.getOrNull()
        if (code !in 200..299 || o?.optString("result") == "error") {
            val why = o?.optJSONObject("error")?.let { it.optString("description").ifBlank { it.optString("code") } }
            throw Fail(when (code) {
                401 -> "kDrive no acepta el token (revísalo o crea otro)"
                403 -> "El token no tiene permiso para este kDrive"
                404 -> "No encuentro ese kDrive (revisa su número)"
                else -> "kDrive: ${why ?: "error $code"}"
            })
        }
        return o ?: JSONObject()
    }

    suspend fun testKDrive(ctx: Context): String = withContext(Dispatchers.IO) {
        prefs(ctx).edit().remove("home").remove("versDir").apply()
        homeKDrive(ctx)
        "Conectado a tu kDrive"
    }

    fun childrenKDrive(ctx: Context, dir: Long): List<Item> {
        val out = mutableListOf<Item>()
        var cursor = ""
        do {
            val q = if (cursor.isNotEmpty()) "?cursor=${URLEncoder.encode(cursor, "UTF-8")}" else ""
            val o = call(ctx, "GET", "/3/drive/${drive(ctx)}/files/$dir/files$q")
            val a = o.optJSONArray("data") ?: JSONArray()
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { out += Item(it.optString("name"), it.optLong("id"), it.optString("type") == "dir", it.optString("visibility"), it.optLong("size")) }
            cursor = if (o.optBoolean("has_more")) o.optString("cursor") else ""
        } while (cursor.isNotEmpty() && out.size < 5000)
        return out
    }

    fun homeKDrive(ctx: Context): Long {
        prefs(ctx).getLong("home", 0L).takeIf { it > 0 }?.let { return it }
        val root = childrenKDrive(ctx, 1)
        val h = root.firstOrNull { it.dir && it.visibility == "is_private_space" }
            ?: throw Fail("No encuentro tu carpeta privada en el kDrive (hay: ${root.joinToString { it.name }.ifBlank { "nada" }})")
        prefs(ctx).edit().putLong("home", h.id).apply()
        return h.id
    }

    fun carpetaKDrive(ctx: Context, vararg ruta: String): Long? {
        var dir = homeKDrive(ctx)
        for (n in ruta) dir = childrenKDrive(ctx, dir).firstOrNull { it.dir && it.name.equals(n, ignoreCase = true) }?.id ?: return null
        return dir
    }

    fun direccionKDrive(ctx: Context, file: Long) = "$api/2/drive/${drive(ctx)}/files/$file/download"
    fun cabecerasKDrive(ctx: Context) = mapOf("Authorization" to "Bearer ${token(ctx)}")

    private const val CHUNKED = 5L * 1024 * 1024
    private const val PARALLEL = 4

    fun uploadKDrive(ctx: Context, path: String, name: String, size: Long, onProgress: (Long) -> Unit = {}, input: () -> InputStream): Long {
        val d = drive(ctx)
        val home = homeKDrive(ctx)
        val enc = { v: String -> URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
        if (size <= CHUNKED) {
            val q = "directory_id=$home&directory_path=${enc(path)}&file_name=${enc(name)}&total_size=$size&conflict=version"
            val c = URL("$api/3/drive/$d/upload?$q").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 20000; c.readTimeout = 300000
            c.doOutput = true
            c.setFixedLengthStreamingMode(size)
            c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.outputStream.use { out -> input().use { it.copyTo(out) } }
            return answer(c).optJSONObject("data")?.optLong("id")?.takeIf { it > 0 }.also { onProgress(size) } ?: throw Fail("kDrive no ha devuelto el archivo subido")
        }
        val chunk = maxOf(1L * 1024 * 1024, (size + 199) / 200)
        val total = ((size + chunk - 1) / chunk).toInt()
        val start = call(ctx, "POST", "/3/drive/$d/upload/session/start", JSONObject()
            .put("conflict", "version").put("directory_id", home).put("directory_path", path).put("file_name", name)
            .put("last_modified_at", System.currentTimeMillis() / 1000).put("total_chunks", total).put("total_size", size).toString())
            .optJSONObject("data") ?: throw Fail("kDrive no ha abierto la subida")
        val tok = start.optString("token").ifBlank { throw Fail("kDrive no ha abierto la subida") }
        val host = start.optString("upload_url").ifBlank { api }.trimEnd('/')
        val pool = java.util.concurrent.Executors.newFixedThreadPool(PARALLEL)
        val slots = java.util.concurrent.Semaphore(PARALLEL)
        val sent = java.util.concurrent.atomic.AtomicLong(0)
        val failed = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        try {
            input().use { inp ->
                for (n in 1..total) {
                    val len = if (n == total) (size - chunk * (total - 1)).toInt() else chunk.toInt()
                    slots.acquire()
                    failed.get()?.let { throw it }
                    val buf = ByteArray(len)
                    var r = 0
                    while (r < len) { val k = inp.read(buf, r, len - r); if (k < 0) throw Fail("El archivo se ha acabado antes de tiempo"); r += k }
                    pool.execute {
                        try {
                            var tries = 0
                            while (true) {
                                try {
                                    val c = URL("$host/3/drive/$d/upload/session/$tok/chunk?chunk_number=$n&chunk_size=$len").openConnection() as HttpURLConnection
                                    c.requestMethod = "POST"
                                    c.connectTimeout = 20000; c.readTimeout = 300000
                                    c.doOutput = true
                                    c.setFixedLengthStreamingMode(len)
                                    c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
                                    c.setRequestProperty("Content-Type", "application/octet-stream")
                                    c.outputStream.use { it.write(buf) }
                                    answer(c)
                                    break
                                } catch (e: IOException) {
                                    if (e is Fail || ++tries >= 3) throw e
                                    Thread.sleep(1500L * tries)
                                }
                            }
                            onProgress(sent.addAndGet(len.toLong()))
                        } catch (e: Throwable) { failed.compareAndSet(null, e) } finally { slots.release() }
                    }
                }
            }
            slots.acquire(PARALLEL)
            failed.get()?.let { throw it }
        } finally { pool.shutdownNow() }
        val fin = call(ctx, "POST", "/3/drive/$d/upload/session/$tok/finish").optJSONObject("data")
        return fin?.optJSONObject("file")?.optLong("id")?.takeIf { it > 0 } ?: throw Fail("kDrive no ha cerrado la subida")
    }

    fun versionesKDrive(ctx: Context): List<Pair<String, Long>> {
        prefs(ctx).getLong("versDir", 0L).takeIf { it > 0 }?.let { id ->
            runCatching { return childrenKDrive(ctx, id).filter { !it.dir }.map { it.name to it.id } }
            prefs(ctx).edit().remove("versDir").apply()
        }
        val dir = childrenKDrive(ctx, homeKDrive(ctx)).firstOrNull { it.dir && it.name == "versiones" } ?: return emptyList()
        prefs(ctx).edit().putLong("versDir", dir.id).apply()
        return childrenKDrive(ctx, dir.id).filter { !it.dir }.map { it.name to it.id }
    }

    private const val TROZOS = 4
    private const val MINIMO_EN_TROZOS = 4L * 1024 * 1024

    fun downloadKDrive(ctx: Context, file: Long, dest: File) {
        if (runCatching { descargaEnTrozos(ctx, file, dest) }.getOrDefault(false)) return
        descargaSimple(ctx, file, dest)
    }

    private fun descargaEnTrozos(ctx: Context, file: Long, dest: File): Boolean {
        val url = "$api/2/drive/${drive(ctx)}/files/$file/download"
        fun abre(desde: Long, hasta: Long): HttpURLConnection {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 20000; c.readTimeout = 300000
            c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
            c.setRequestProperty("Range", "bytes=$desde-$hasta")
            return c
        }
        val sonda = abre(0, 0)
        if (sonda.responseCode != 206) { sonda.disconnect(); return false }
        val total = sonda.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: run { sonda.disconnect(); return false }
        sonda.inputStream.use { it.readBytes() }
        if (total < MINIMO_EN_TROZOS) return false
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        java.io.RandomAccessFile(tmp, "rw").use { it.setLength(total) }
        val trozo = (total + TROZOS - 1) / TROZOS
        val fallo = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val hilos = (0 until TROZOS).map { i ->
            Thread {
                try {
                    val a = i * trozo
                    val b = minOf(total - 1, a + trozo - 1)
                    if (a > b) return@Thread
                    var intentos = 0
                    while (true) {
                        try {
                            val c = abre(a, b)
                            if (c.responseCode != 206) throw Fail("kDrive: error ${c.responseCode}")
                            c.inputStream.use { inp ->
                                java.io.RandomAccessFile(tmp, "rw").use { f ->
                                    f.seek(a)
                                    val buf = ByteArray(64 * 1024); var pos = a
                                    while (true) { val n = inp.read(buf); if (n < 0) break; f.write(buf, 0, n); pos += n }
                                    if (pos != b + 1) throw IOException("trozo incompleto")
                                }
                            }
                            break
                        } catch (e: IOException) {
                            if (e is Fail || ++intentos >= 3) throw e
                            Thread.sleep(1000L * intentos)
                        }
                    }
                } catch (e: Throwable) { fallo.compareAndSet(null, e) }
            }.also { it.start() }
        }
        hilos.forEach { it.join() }
        fallo.get()?.let { tmp.delete(); throw it }
        if (tmp.length() != total) { tmp.delete(); throw Fail("kDrive: la descarga ha quedado incompleta") }
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, true); tmp.delete() }
        return true
    }

    private fun descargaSimple(ctx: Context, file: Long, dest: File) {
        val c = URL("$api/2/drive/${drive(ctx)}/files/$file/download").openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 300000
        c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
        if (c.responseCode !in 200..299) { answer(c); throw Fail("kDrive: error ${c.responseCode}") }
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, true); tmp.delete() }
    }

    fun linkKDrive(ctx: Context, file: Long): String = runCatching {
        val d = call(ctx, "POST", "/2/drive/${drive(ctx)}/files/$file/link", JSONObject().put("right", "public").put("can_download", true).toString()).opt("data")
        (d as? JSONObject)?.optString("url").orEmpty().ifBlank { (d as? String).orEmpty() }
    }.getOrDefault("")

    fun downloadSharedKDrive(link: String, file: Long, dest: File) {
        val m = Regex("""/app/share/(\d+)/([a-z0-9-]+)""").find(link) ?: throw Fail("Ese enlace no es de kDrive")
        val c = URL("$share/2/app/${m.groupValues[1]}/share/${m.groupValues[2]}/files/$file/download").openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 300000
        if (c.responseCode !in 200..299) throw Fail("El enlace ya no funciona (error ${c.responseCode})")
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, true); tmp.delete() }
    }

    fun existsKDrive(ctx: Context, file: Long): Boolean? = try {
        val d = call(ctx, "GET", "/3/drive/${drive(ctx)}/files/$file").optJSONObject("data")
        val parent = d?.optLong("parent_id", 0L) ?: 0L
        if (d == null) null else if (parent <= 0L) true else childrenKDrive(ctx, parent).any { it.id == file }
    } catch (e: Fail) {
        if (e.message?.startsWith("No encuentro") == true || e.message?.contains("not_found", true) == true) false else null
    } catch (_: Exception) { null }

    fun reachKDrive(ctx: Context) { childrenKDrive(ctx, 1) }

    fun deleteKDrive(ctx: Context, file: Long) {
        try { call(ctx, "DELETE", "/2/drive/${drive(ctx)}/files/$file") } catch (e: Fail) { if (e.message?.startsWith("No encuentro") != true) throw e }
    }
}
