package com.gonzalo.gamerate

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/*
 * Tu nube (kDrive de Infomaniak): los libros, las fotos de portada y las capturas, en una carpeta «Atrio» de tu kDrive
 * que también ves y usas tú. El token está en tu cuenta, en `/claves` (que solo tú puedes leer; tu pareja, que lee tus datos,
 * no): lo usan las cuatro apps. También viaja con la cuenta la lista de lo subido ([NubeFile]): así tus otros dispositivos
 * saben qué bajar y tu pareja puede abrirlo con su enlace.
 */

/** Algo tuyo en la nube. [k]: qué es («libro:123», «foto:foto_1.jpg», «shot:abc»); [f]: su id en kDrive (antiguo); [gd]: su id en Google Drive; [l]: su enlace. */
data class NubeFile(
    val k: String, val f: Long, val n: String, val s: Long, val l: String = "",
    /** Datos de una captura (juego, id de SteamGridDB, fecha, tamaño): para listarla sin bajarla. */
    val m: JSONObject? = null, val updated: Long = System.currentTimeMillis(),
    /** ID del archivo en Google Drive (vacío en archivos antiguos que solo estaban en kDrive). */
    val gd: String = "",
) {
    /** Id estable a partir de la clave (las listas de la cuenta se mezclan por id). */
    val id: Long get() = nubeId(k)
}

fun nubeId(k: String): Long {
    var h = -0x340d631b7bdddcdbL // FNV-1a de 64 bits
    for (b in k.toByteArray()) { h = h xor (b.toLong() and 0xff); h *= 0x100000001b3L }
    // 53 bits: el escritorio (JavaScript) lo lee como número sin perder precisión
    return (h and 0x1FFFFFFFFFFFFFL).coerceAtLeast(1L)
}

fun nubeToJson(x: NubeFile): JSONObject = JSONObject().put("id", x.id).put("k", x.k).put("f", x.f).put("n", x.n).put("s", x.s)
    .put("updated", x.updated).apply {
        if (x.gd.isNotBlank()) put("gd", x.gd)
        if (x.l.isNotBlank()) put("l", x.l)
        if (x.m != null) put("m", x.m)
    }

fun nubeFromJson(o: JSONObject): NubeFile? {
    val k = o.optString("k").ifBlank { return null }
    return NubeFile(k, o.optLong("f"), o.optString("n"), o.optLong("s"), o.optString("l", ""), o.optJSONObject("m"), o.optLong("updated"), o.optString("gd", ""))
}

/** Tu nube en Jonsu: delega en [com.gonzalo.familia.NubeFamilia] (Google Drive principal, kDrive como respaldo durante la transición). */
object KDrive {
    var api: String
        get() = com.gonzalo.familia.NubeFamilia.api
        set(v) { com.gonzalo.familia.NubeFamilia.api = v }

    var share: String
        get() = com.gonzalo.familia.NubeFamilia.share
        set(v) { com.gonzalo.familia.NubeFamilia.share = v }

    private fun prefs(ctx: Context) = EspejoCuenta.prefs(ctx, AjustesEnLaCuenta.NUBE)

    fun gdriveOn(ctx: Context) = com.gonzalo.familia.NubeFamilia.gdriveOn(ctx)
    fun kdriveOn(ctx: Context) = com.gonzalo.familia.NubeFamilia.kdriveOn(ctx)
    fun token(ctx: Context) = com.gonzalo.familia.NubeFamilia.token(ctx)
    fun drive(ctx: Context) = com.gonzalo.familia.NubeFamilia.drive(ctx)
    fun on(ctx: Context) = com.gonzalo.familia.NubeFamilia.on(ctx)
    fun nombreNube(ctx: Context) = com.gonzalo.familia.NubeFamilia.nombreNube(ctx)

    fun save(ctx: Context, token: String, drive: String) = com.gonzalo.familia.NubeFamilia.save(ctx, token, drive)
    fun confirma(ctx: Context) = com.gonzalo.familia.NubeFamilia.confirma(ctx)
    fun descarta(ctx: Context) = com.gonzalo.familia.NubeFamilia.descarta(ctx)
    fun forget(ctx: Context) {
        prefs(ctx).edit().remove("shots").apply()
        com.gonzalo.familia.NubeFamilia.forget(ctx)
    }
    fun migraALaCuenta(ctx: Context) = com.gonzalo.familia.NubeFamilia.migraALaCuenta(ctx)

    /** Subir también las capturas que lleguen del PC (las sube el PC; esto vale si no lo hace). */
    fun shots(ctx: Context) = prefs(ctx).getBoolean("shots", true)
    fun setShots(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("shots", v).apply()
    var lastMsg: String = ""

    suspend fun test(ctx: Context): String = withContext(Dispatchers.IO) {
        com.gonzalo.familia.NubeFamilia.testKDrive(ctx)
        "Conectado: Polaris guardará todo en «Mi carpeta/Polaris» de tu kDrive (la misma carpeta que Polaris del PC)"
    }

    fun home(ctx: Context): Long = com.gonzalo.familia.NubeFamilia.homeKDrive(ctx)
    fun upload(ctx: Context, path: String, name: String, size: Long, onProgress: (Long) -> Unit = {}, input: () -> InputStream): Long =
        com.gonzalo.familia.NubeFamilia.uploadKDrive(ctx, path, name, size, onProgress, input)
    fun subeDual(ctx: Context, path: String, name: String, size: Long, onProgress: (Long) -> Unit = {}, input: () -> InputStream) =
        com.gonzalo.familia.NubeFamilia.subeDual(ctx, path, name, size, onProgress, input)

    /** Una carpeta o un archivo de tu nube, para el explorador ([path]: dónde está, en las búsquedas). */
    class Entry(val id: Long, val name: String, val dir: Boolean, val image: Boolean, val path: String = "", val gdId: String = "", val thumbUrl: String = "") {
        val esGoogleDrive: Boolean get() = gdId.isNotBlank()
        val claveUi: String get() = if (esGoogleDrive) "gd:$gdId" else "kd:$id"
    }

    private val IMG = Regex("""(?i)\.(jpe?g|png|webp|gif|heic|heif|avif|bmp)$""")

    /** Lo que hay en una carpeta de tu kDrive: primero las carpetas y luego las imágenes. */
    fun list(ctx: Context, dir: Long): List<Entry> {
        val out = com.gonzalo.familia.NubeFamilia.childrenKDrive(ctx, dir).mapNotNull {
            val img = !it.dir && IMG.containsMatchIn(it.name)
            if (it.dir || img) Entry(it.id, it.name, it.dir, img) else null
        }
        return out.sortedWith(compareByDescending<Entry> { it.dir }.thenBy { it.name.lowercase() })
    }

    /** Lo que hay en una carpeta de Google Drive: primero las carpetas y luego las imágenes. */
    fun listGDrive(ctx: Context, dirId: String): List<Entry> {
        val g = com.gonzalo.familia.NubeFamilia.gdrive(ctx)
        val out = g.dentro(dirId).mapNotNull {
            val img = !it.carpeta && (it.mime.startsWith("image/") || IMG.containsMatchIn(it.nombre))
            if (it.carpeta || img) Entry(0L, it.nombre, it.carpeta, img, "", it.id, it.miniatura.ifBlank { g.urlDescarga(it.id) }) else null
        }
        return out.sortedWith(compareByDescending<Entry> { it.dir }.thenBy { it.name.lowercase() })
    }

    /** Dónde empieza el explorador en kDrive: «Mi carpeta/Polaris» (o «Atrio») si ya existe; si no, «Mi carpeta». */
    fun start(ctx: Context): Long {
        val h = home(ctx)
        val dirs = list(ctx, h).filter { it.dir }
        return dirs.firstOrNull { it.name == "Polaris" }?.id
            ?: dirs.firstOrNull { it.name == "Atrio" }?.id
            ?: h
    }

    /** Dónde empieza el explorador en Google Drive: «Polaris» (o «Jonsu»/«Atrio» si aún existe) dentro de la raíz; si no, la raíz. */
    fun startGDrive(ctx: Context): Pair<String, String> {
        val g = com.gonzalo.familia.NubeFamilia.gdrive(ctx)
        for (nombre in listOf("Polaris", "Jonsu", "Atrio")) {
            val id = g.carpeta(nombre)
            if (id != null) return id to nombre
        }
        return g.carpetaRaiz() to "Google Drive"
    }

    /** Busca por nombre en kDrive (o en Google Drive si está activo y [soloKDrive] es false). */
    fun busca(ctx: Context, texto: String, tipo: String = "image", max: Int = 300): List<Entry> {
        val q = texto.trim()
        if (q.isEmpty()) return emptyList()
        if (gdriveOn(ctx)) {
            val g = com.gonzalo.familia.NubeFamilia.gdrive(ctx)
            return g.busca(q, tipo, max).mapNotNull {
                val img = !it.carpeta && (it.mime.startsWith("image/") || IMG.containsMatchIn(it.nombre))
                if (it.carpeta || img) Entry(0L, it.nombre, it.carpeta, img, "Google Drive", it.id, it.miniatura.ifBlank { g.urlDescarga(it.id) }) else null
            }
        }
        val enc = { v: String -> URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
        val out = mutableListOf<Entry>()
        var cursor = ""
        do {
            val c = if (cursor.isNotEmpty()) "&cursor=${enc(cursor)}" else ""
            val conn = URL("$api/3/drive/${drive(ctx)}/files/search?query=${enc(q)}&types[]=$tipo&with=path$c").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"; conn.connectTimeout = 20000; conn.readTimeout = 60000
            conn.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
            val code = conn.responseCode
            val txt = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            if (code !in 200..299) throw com.gonzalo.familia.NubeFamilia.Fail("kDrive: error $code")
            val o = runCatching { JSONObject(txt) }.getOrNull() ?: JSONObject()
            val a = o.optJSONArray("data") ?: JSONArray()
            for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                val dirx = it.optString("type") == "dir"
                val img = !dirx && (it.optString("extension_type") == "image" || IMG.containsMatchIn(it.optString("name")))
                if (dirx || img) out += Entry(it.optLong("id"), it.optString("name"), dirx, img, rutaLegible(it.optString("path")))
            }
            cursor = if (o.optBoolean("has_more")) o.optString("cursor") else ""
        } while (cursor.isNotEmpty() && out.size < max)
        return out.take(max)
    }

    /** «/Private/Atrio/Juegos/Hades/Portadas/x.jpg» → «Mi carpeta/Atrio/Juegos/Hades/Portadas» (la carpeta). */
    fun rutaLegible(p: String): String = p.trim('/').split('/').dropLast(1).let { ps ->
        if (ps.firstOrNull() == "Private") (listOf("Mi carpeta") + ps.drop(1)).joinToString("/") else ps.joinToString("/")
    }

    /** La carpeta de una ficha en kDrive («Mi carpeta/Atrio/<Categoría>/<Ficha>») si ya existe, o null. */
    fun carpetaDeFicha(ctx: Context, nombre: String): Pair<Long, String>? {
        val n = NubeSync.safe(cleanTitle(nombre))
        if (nombre.isBlank()) return null
        return runCatching { busca(ctx, n, tipo = "dir", max = 60) }.getOrDefault(emptyList())
            .firstOrNull { !it.esGoogleDrive && it.dir && it.name == n && (it.path.startsWith("Mi carpeta/Polaris/") || it.path.startsWith("Mi carpeta/Atrio/")) }?.let { it.id to "${it.path}/${it.name}" }
    }

    /** La carpeta de una ficha en Google Drive si ya existe, o null. */
    fun carpetaDeFichaGDrive(ctx: Context, nombre: String): Pair<String, String>? {
        val n = NubeSync.safe(cleanTitle(nombre))
        if (nombre.isBlank() || !gdriveOn(ctx)) return null
        return runCatching { com.gonzalo.familia.NubeFamilia.gdrive(ctx).busca(n, tipo = "dir", max = 40) }.getOrDefault(emptyList())
            .firstOrNull { it.carpeta && it.nombre == n }?.let { it.id to it.nombre }
    }

    /** La miniatura de una imagen. */
    fun thumb(ctx: Context, file: Long) = "$api/2/drive/${drive(ctx)}/files/$file/thumbnail"
    fun thumbEntry(ctx: Context, e: Entry): String = if (e.esGoogleDrive) e.thumbUrl.ifBlank { com.gonzalo.familia.NubeFamilia.gdrive(ctx).urlDescarga(e.gdId) } else thumb(ctx, e.id)
    fun auth(ctx: Context) = "Bearer ${token(ctx)}"
    fun authEntry(ctx: Context, e: Entry): String = if (e.esGoogleDrive) runCatching { "Bearer ${com.gonzalo.familia.NubeFamilia.gdrive(ctx).paseValido()}" }.getOrDefault("") else auth(ctx)

    fun downloadShared(link: String, file: Long, dest: File) = com.gonzalo.familia.NubeFamilia.descargaEnlace(link, file, dest)
    fun link(ctx: Context, file: Long): String = com.gonzalo.familia.NubeFamilia.linkKDrive(ctx, file)
    fun versiones(ctx: Context): List<Pair<String, Long>> = com.gonzalo.familia.NubeFamilia.versionesKDrive(ctx)
    fun versionesDual(ctx: Context) = com.gonzalo.familia.NubeFamilia.versionesDual(ctx)
    fun bajaVersion(ctx: Context, v: com.gonzalo.familia.NubeFamilia.VersionRemota, dest: File) = com.gonzalo.familia.NubeFamilia.bajaVersion(ctx, v, dest)
    fun download(ctx: Context, file: Long, dest: File) = com.gonzalo.familia.NubeFamilia.downloadKDrive(ctx, file, dest)
    fun exists(ctx: Context, file: Long): Boolean? = com.gonzalo.familia.NubeFamilia.existsKDrive(ctx, file)
    fun reach(ctx: Context) = com.gonzalo.familia.NubeFamilia.reachKDrive(ctx)
    fun delete(ctx: Context, file: Long) = com.gonzalo.familia.NubeFamilia.deleteKDrive(ctx, file)
}

/** Qué subir y qué bajar: lo hace en segundo plano ([NubeWorker]) y a mano desde Ajustes. */
object NubeSync {
    var running by mutableStateOf(false)
    var status by mutableStateOf("")

    fun enqueue(ctx: Context, verify: Boolean = false) {
        // Sin kDrive aquí, solo si hay algo tuyo que bajar por su enlace (tus portadas)
        if (!KDrive.on(ctx) && Store(ctx).loadNube().none { it.k.startsWith("foto:") && it.l.isNotBlank() }) return
        val req = OneTimeWorkRequestBuilder<NubeWorker>()
            .setInputData(androidx.work.workDataOf("verify" to verify))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        // KEEP: si ya está subiendo, que siga (antes se cortaba al abrir o cerrar la app y volvía a empezar)
        WorkManager.getInstance(ctx).enqueueUniqueWork("kdrive", ExistingWorkPolicy.KEEP, req)
    }

    /** Un nombre que vale para una carpeta o un archivo en cualquier sistema. */
    fun safe(s: String) = s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]+"""), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(80).trim().ifBlank { "Sin nombre" }

    /**
     * Dónde va cada cosa en tu kDrive, todo bajo «Mi carpeta/Atrio»:
     * Atrio/<Categoría>/<Ficha>/Libro · Capturas · Portadas, y Atrio/<Categoría>/Sagas/<Saga>/Portadas.
     */
    fun folder(kind: Kind, name: String, what: String) = "Polaris/${safe(kind.title)}/${safe(cleanTitle(name))}/$what"

    /** Las fotos tuyas que usan tus fichas y tus sagas (portadas y fondos), con la carpeta de su ficha. */
    /** Tus fotos (portadas, fondos, imágenes de casillas) que usan tus fichas y faltan aquí: se bajan. Cuántas. */
    suspend fun bajarFotos(ctx: Context, store: Store = Store(ctx), errs: MutableList<String> = mutableListOf()): Int = withContext(Dispatchers.IO) {
        val have = store.loadNube().associateBy { it.k }
        var n0 = 0
        for (n in photosInUse(ctx, store).keys) {
            if (Photos.best(ctx, n) != null) continue
            val x = have["foto:$n"] ?: continue
            try {
                status = "Bajando la foto ${x.n}…"
                val tmp = File(ctx.cacheDir, "nube_${x.f}")
                bajar(ctx, x, tmp)
                Photos.saveArrived(ctx, n, tmp.readBytes(), Photos.mimeOf(File(x.n)))
                tmp.delete()
                n0++
            } catch (e: Exception) { errs += "${x.n}: ${e.message}" }
        }
        if (n0 > 0) Photos.llegaron()
        n0
    }

    /**
     * Una imagen que eliges de tu nube para una portada: se baja y se usa, y se apunta ese mismo archivo de tu
     * nube para ella (no se vuelve a subir otra copia). Devuelve su dirección.
     */
    suspend fun usarDeLaNube(ctx: Context, file: Long, name: String, gdId: String = ""): String = withContext(Dispatchers.IO) {
        val tag = gdId.ifBlank { file.toString() }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val tmp = File(ctx.cacheDir, "nube_pick_${tag}_${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
        com.gonzalo.familia.NubeFamilia.bajaDual(ctx, gdId, file, tmp)
        val size = tmp.length()
        val u = Photos.import(ctx, Uri.fromFile(tmp))
        tmp.delete()
        val enlace = com.gonzalo.familia.NubeFamilia.enlacePublico(ctx, gdId, file)
        Photos.nameOf(u)?.let { n -> Store(ctx).addNube(listOf(NubeFile("foto:$n", file, name, size, enlace, gd = gdId))) }
        u
    }

    /** ¿Hay fotos tuyas en uso aquí que aún no están en tu nube? (para subirlas enseguida) */
    fun fotosPorSubir(ctx: Context, store: Store = Store(ctx)): Boolean {
        if (!KDrive.on(ctx)) return false
        val have = store.loadNube().map { it.k }.toSet()
        return photosInUse(ctx, store).keys.any { "foto:$it" !in have && Photos.best(ctx, it) != null }
    }

    private fun photosInUse(ctx: Context, store: Store): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val kinds = listOf(GAMES, BOOKS) + store.kinds
        val names = HashMap<Pair<Int, Long>, String>()
        for (k in kinds) {
            // Portada, fondo y logo de cada ficha
            val items = store.load(k.id).map { listOf(it.name, it.cover, it.hero, it.logo) to it.id } + store.loadPlays(k.id).map { listOf(it.name, it.cover, it.hero, it.logo) to it.id }
            for ((v, id) in items) {
                names[k.id to id] = v[0]
                for (u in v.drop(1)) Photos.nameOf(u)?.let { out.putIfAbsent(it, folder(k, v[0], "Portadas")) }
            }
        }
        // Las fotos que pusiste en las casillas de la revista de una ficha (se guardan en este dispositivo)
        for ((key, raw) in ctx.getSharedPreferences("maqueta", Context.MODE_PRIVATE).all) {
            val m = Regex("""^[pv]:(\d+):(\d+)$""").find(key) ?: continue
            val k = kinds.find { it.id == m.groupValues[1].toInt() } ?: continue
            val name = names[k.id to m.groupValues[2].toLong()] ?: continue
            Regex(""""i"\s*:\s*"(file:[^"]+)"""").findAll(raw as? String ?: continue).forEach { r ->
                Photos.nameOf(r.groupValues[1].replace("\\/", "/"))?.let { out.putIfAbsent(it, folder(k, name, "Imágenes")) }
            }
        }
        store.loadSagas().forEach { sg ->
            val k = kinds.find { it.id == sg.kind } ?: BOOKS
            Photos.nameOf(sg.cover)?.let { out.putIfAbsent(it, "Polaris/${safe(k.title)}/Sagas/${safe(sg.name)}/Portadas") }
        }
        // Las fotos de tu diario
        store.loadDiario().forEach { n -> Photos.nameOf(n.foto)?.let { out.putIfAbsent(it, "Polaris/Diario/${java.time.LocalDate.ofEpochDay(n.dia).year}") } }
        return out
    }

    /** El nombre de la ficha de un juego (por su id de SteamGridDB), para que sus capturas vayan a su carpeta. */
    fun gameName(store: Store, sg: Long, fallback: String): String =
        (store.loadPlays(0).find { it.id == sg }?.name ?: store.load(0).find { it.id == sg }?.name)?.takeIf { sg > 0 } ?: fallback.ifBlank { "Escritorio" }

    /** Los libros con su archivo en este móvil: (id, dirección). */
    private fun localBooks(ctx: Context): List<Pair<Long, String>> =
        ctx.getSharedPreferences("lector", Context.MODE_PRIVATE).all.keys.mapNotNull { it.toLongOrNull() }
            .mapNotNull { id -> BookFiles.get(ctx, id)?.let { id to it } }

    private fun sizeOf(ctx: Context, uri: Uri): Long {
        if (uri.scheme == "file") return File(uri.path ?: "").length()
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0) }
        return ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    }

    /** Sube lo que falte y baja lo que falte aquí. Devuelve un resumen. */
    suspend fun run(ctx: Context, verify: Boolean = false, foreground: (suspend (String, Int) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        val on = KDrive.on(ctx)
        val store = Store(ctx)
        if (!on && store.loadNube().none { it.l.isNotBlank() }) return@withContext "Sin nube"
        running = true
        var up = 0; var down = 0; val errs = mutableListOf<String>()
        try {
            val have = store.loadNube().associateBy { it.k }
            fun progress(what: String, size: Long): (Long) -> Unit {
                var last = -1
                return { done ->
                    val pct = if (size > 0) (done * 100 / size).toInt() else 100
                    if (pct != last) {
                        last = pct
                        status = "Subiendo $what… $pct %"
                        if (foreground != null) kotlinx.coroutines.runBlocking { runCatching { foreground(status, pct) } }
                    }
                }
            }
            val added = mutableListOf<NubeFile>()
            if (on) {
            val titles = (store.load(1).map { it.id to it.name } + store.loadPlays(1).map { it.id to it.name }).toMap()
            val np = ctx.getSharedPreferences("nube", Context.MODE_PRIVATE)
            val comprobar = verify || System.currentTimeMillis() - np.getLong("verificadoEn", 0L) > 6 * 3600_000L
            if (comprobar) np.edit().putLong("verificadoEn", System.currentTimeMillis()).apply()
            for ((id, u) in localBooks(ctx)) {
                val rec = have["libro:$id"]
                if (rec != null) {
                    val tam = runCatching { sizeOf(ctx, Uri.parse(u)) }.getOrDefault(0L)
                    val sobra = (tam > 0 && tam != rec.s) || (comprobar && com.gonzalo.familia.NubeFamilia.existeDual(ctx, rec.gd, rec.f) == false)
                    if (!sobra) continue
                    store.removeNube("libro:$id")
                }
                try {
                    val uri = Uri.parse(u)
                    val size = sizeOf(ctx, uri)
                    if (size <= 0) continue
                    val file = BookFiles.name(ctx, id).ifBlank { "libro.epub" }
                    val ext = file.substringAfterLast('.', "epub").take(5)
                    val title = titles[id] ?: file.substringBeforeLast('.')
                    val name = "${safe(cleanTitle(title))}.$ext"
                    status = "Subiendo $name…"
                    foreground?.let { runCatching { it(status, 0) } }
                    val sub = KDrive.subeDual(ctx, folder(BOOKS, title, "Libro"), name, size, progress(name, size)) { ctx.contentResolver.openInputStream(uri) ?: throw IOException("no se puede leer") }
                    val link = com.gonzalo.familia.NubeFamilia.enlacePublico(ctx, sub.gdId, sub.kdId)
                    added += NubeFile("libro:$id", sub.kdId, name, size, link, gd = sub.gdId)
                    up++
                } catch (e: Exception) { errs += "${titles[id] ?: "un libro"}: ${e.message}" }
            }
            // Fotos de portada: el original si está aquí
            for ((n, where) in photosInUse(ctx, store)) {
                if (have.containsKey("foto:$n")) continue
                val (f0, _) = Photos.best(ctx, n) ?: continue
                try {
                    status = "Subiendo la foto ${f0.name}…"
                    val sub = KDrive.subeDual(ctx, where, f0.name, f0.length(), progress(f0.name, f0.length())) { f0.inputStream() }
                    val link = com.gonzalo.familia.NubeFamilia.enlacePublico(ctx, sub.gdId, sub.kdId)
                    added += NubeFile("foto:$n", sub.kdId, f0.name, f0.length(), link, gd = sub.gdId)
                    up++
                } catch (e: Exception) { errs += "${f0.name}: ${e.message}" }
            }
            // Capturas que tengas aquí y no estén (normalmente las sube el PC)
            if (KDrive.shots(ctx)) for (s in Suite.localShots) {
                if (have.containsKey("shot:${s.id}") || store.shotNoCloud(s.id)) continue
                val f0 = File(Uri.parse(s.uri).path ?: continue)
                if (!f0.exists()) continue
                try {
                    val g = gameName(store, s.sg, s.game)
                    val name = "${safe(cleanTitle(g))} ${java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.ROOT).format(java.util.Date(s.time))}.${f0.extension}"
                    status = "Subiendo la captura $name…"
                    val sub = KDrive.subeDual(ctx, folder(GAMES, g, "Capturas"), name, f0.length(), progress(name, f0.length())) { f0.inputStream() }
                    val link = com.gonzalo.familia.NubeFamilia.enlacePublico(ctx, sub.gdId, sub.kdId)
                    added += NubeFile("shot:${s.id}", sub.kdId, name, f0.length(), link, shotMeta(s), gd = sub.gdId)
                    up++
                } catch (e: Exception) { errs += "${s.game}: ${e.message}" }
            }
            }
            if (added.isNotEmpty()) store.addNube(added)
            // Bajar las fotos de portada que falten aquí (las elegiste en otro dispositivo)
            down += bajarFotos(ctx, store, errs)
        } catch (e: Exception) {
            errs += e.message ?: "error"
        } finally { running = false }
        val msg = listOfNotNull(
            if (up > 0) "$up subido${if (up == 1) "" else "s"}" else null,
            if (down > 0) "$down bajado${if (down == 1) "" else "s"}" else null,
            errs.firstOrNull()?.let { "falló: $it" + if (errs.size > 1) " (y ${errs.size - 1} más)" else "" }
        ).joinToString(" · ").ifBlank { "Todo al día" }
        status = msg
        KDrive.lastMsg = msg
        if (up > 0) Couple.pushIfPending(store)
        msg
    }

    fun shotMeta(s: Shot): JSONObject = JSONObject().put("game", s.game).put("sg", s.sg).put("t", s.time).put("w", s.w).put("h", s.h)
        .put("mime", s.mime).put("pc", s.pc).put("by", s.by)

    /**
     * Baja un archivo de tu nube (Google Drive o kDrive). Primero con tu cuenta; si falla, por su enlace público.
     */
    fun bajar(ctx: Context, x: NubeFile, dest: File) {
        val first = if (KDrive.on(ctx)) try { com.gonzalo.familia.NubeFamilia.bajaDual(ctx, x.gd, x.f, dest); return } catch (e: Exception) { e } else null
        var shared: Exception? = null
        if (x.l.isNotBlank()) try { KDrive.downloadShared(x.l, x.f, dest); return } catch (e: Exception) { shared = e }
        throw com.gonzalo.familia.NubeFamilia.Fail(if (first != null) porQue(ctx, x, first) else "No se pudo bajar por su enlace (${shared?.message ?: "no tiene"}); conecta tu Google Drive en Ajustes → Tu nube")
    }

    /** Por qué no se pudo bajar: otro kDrive en este dispositivo, el archivo ya no está, o el error tal cual. */
    internal fun porQue(ctx: Context, x: NubeFile, e: Exception): String {
        val suyo = Regex("""/app/share/(\d+)/""").find(x.l)?.groupValues?.get(1)
        val aqui = KDrive.drive(ctx)
        if (suyo != null && aqui.isNotBlank() && suyo != aqui)
            return "Este dispositivo está conectado a otro kDrive (nº $aqui) y el archivo está en el nº $suyo: cámbialo en Ajustes → Cuenta → Tu nube"
        if ((e is com.gonzalo.familia.NubeFamilia.Fail || e is com.gonzalo.familia.nucleo.GoogleDrive.Fallo) && e.message?.startsWith("No encuentro") == true) {
            if (x.gd.isNotBlank()) return "Ese archivo ya no está en tu Google Drive (¿lo borraste o lo moviste?): vuelve a adjuntarlo desde el dispositivo donde lo tengas"
            return if (runCatching { KDrive.reach(ctx) }.isSuccess) "Ese archivo ya no está en tu kDrive (¿lo borraste o lo moviste?): vuelve a adjuntarlo desde el dispositivo donde lo tengas"
            else "No encuentro tu kDrive (nº $aqui) con este token: revísalo en Ajustes → Cuenta → Tu nube"
        }
        return e.message ?: "error"
    }

    /** Baja un libro de la nube y lo deja adjunto. */
    suspend fun fetchBook(ctx: Context, id: Long): Boolean = withContext(Dispatchers.IO) {
        val x = Store(ctx).loadNube().find { it.k == "libro:$id" } ?: return@withContext false
        val dest = File(File(ctx.filesDir, "libros"), "$id.${x.n.substringAfterLast('.', "epub")}")
        try { bajar(ctx, x, dest) } catch (e: Exception) {
            if (e.message?.contains("ya no está") == true) Store(ctx).removeNube("libro:$id")
            throw e
        }
        BookFiles.attachFile(ctx, id, dest, x.n)
        true
    }

    /** Un libro de tu pareja (desde su enlace) a este móvil, adjunto a tu ficha de ese libro. */
    suspend fun copyFromPartner(ctx: Context, x: NubeFile, id: Long) = withContext(Dispatchers.IO) {
        val dest = File(File(ctx.filesDir, "libros"), "$id.${x.n.substringAfterLast('.', "epub")}")
        KDrive.downloadShared(x.l, x.f, dest)
        BookFiles.attachFile(ctx, id, dest, x.n)
    }

    /** Las capturas que están en tu nube y no en este móvil. */
    fun missingShots(store: Store): List<NubeFile> =
        store.loadNube().filter { it.k.startsWith("shot:") && Suite.shotById(it.k.removePrefix("shot:")) == null }

    /** Baja de la nube las capturas que falten en este móvil. */
    suspend fun fetchShots(ctx: Context, list: List<NubeFile>): Int = withContext(Dispatchers.IO) {
        var n = 0
        for (x in list) {
            try {
                status = "Bajando ${x.n}…"
                val tmp = File(ctx.cacheDir, "nube_${x.gd.ifBlank { x.f.toString() }}")
                bajar(ctx, x, tmp)
                Suite.addFromCloud(x, tmp.readBytes())
                tmp.delete()
                n++
            } catch (e: Exception) { status = "No se pudo bajar ${x.n}: ${e.message}" }
        }
        n
    }

    /** Quita una captura de la nube (y no se vuelve a subir). */
    suspend fun removeShot(ctx: Context, shotId: String) = withContext(Dispatchers.IO) {
        val store = Store(ctx)
        store.setShotNoCloud(shotId)
        store.loadNube().find { it.k == "shot:$shotId" }?.let { x ->
            com.gonzalo.familia.NubeFamilia.borraDual(ctx, x.gd, x.f)
            store.removeNube(x.k)
        }
    }
}

/** En la app de tu pareja: su nube (la pone la app al ver la suya). */
object NubeHub {
    /**
     * Elegir una imagen de tu nube (lo pone la app; null sin nube): la devuelve ya como foto tuya de este móvil. El
     * primer dato es el nombre de la ficha o la saga (para abrir su carpeta); puede ir vacío.
     */
    var pickFromCloud: ((String, (String) -> Unit) -> Unit)? = null
    var partner by mutableStateOf<List<NubeFile>>(emptyList())
    /** Copiar a tu Atrio un libro de tu pareja (lo pone la app). */
    var copyBook: ((Long) -> Unit)? = null
    var copying by mutableStateOf(0L)
}

/** En una ficha de tu pareja: su libro, para abrirlo (y bajarlo) desde su enlace. */
@Composable
fun PartnerFileLink(itemId: Long, kind: Kind, always: Boolean = false) {
    if ((SagaHub.on && !always) || !kind.isBook) return
    val x = NubeHub.partner.find { it.k == "libro:$itemId" && it.l.isNotBlank() } ?: return
    val ctx = LocalContext.current
    val copy = NubeHub.copyBook
    if (copy != null) MagLink(if (NubeHub.copying == itemId) "Copiando…" else "Copiar a mi Polaris", "${x.n} · con su archivo, para leerlo aquí", accent = true) {
        if (NubeHub.copying == 0L) copy(itemId)
    }
    val donde = if (com.gonzalo.familia.nucleo.GoogleDrive.esEnlaceGoogleDrive(x.l)) "Google Drive" else "kDrive"
    MagLink("Abrir en su $donde", "En el navegador") {
        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(x.l))) }
    }
}

class NubeWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    private suspend fun aviso(text: String, pct: Int) {
        val nm = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("nube") == null)
            nm.createNotificationChannel(android.app.NotificationChannel("nube", "Subidas a tu nube", android.app.NotificationManager.IMPORTANCE_LOW))
        val n = androidx.core.app.NotificationCompat.Builder(applicationContext, "nube")
            .setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Subiendo a tu nube").setContentText(text)
            .setProgress(100, pct, false).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).build()
        val info = if (android.os.Build.VERSION.SDK_INT >= 29) androidx.work.ForegroundInfo(4711, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else androidx.work.ForegroundInfo(4711, n)
        setForeground(info)
    }

    override suspend fun doWork(): Result {
        var fg = true
        var msg = NubeSync.run(applicationContext, inputData.getBoolean("verify", false)) { t, p -> if (fg) try { aviso(t, p) } catch (e: Exception) { fg = false } }
        repeat(2) { if (NubeSync.fotosPorSubir(applicationContext)) msg = NubeSync.run(applicationContext) { t, p -> if (fg) try { aviso(t, p) } catch (e: Exception) { fg = false } } }
        Couple.pushIfPending(Store(applicationContext))
        return if (msg.startsWith("falló") && runAttemptCount < 3) Result.retry() else Result.success()
    }
}

/** Ajustes → Tu nube: Google Drive (principal) y kDrive (respaldo durante la transición). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NubeSettings(say: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var gdOn by remember { mutableStateOf(KDrive.gdriveOn(ctx)) }
    var gdCid by remember { mutableStateOf(com.gonzalo.familia.NubeFamilia.gdriveClientId(ctx)) }
    var gdCsec by remember { mutableStateOf("") }
    var gdRtok by remember { mutableStateOf("") }
    var gdDir by remember { mutableStateOf(com.gonzalo.familia.NubeFamilia.gdriveCarpeta(ctx)) }
    var kdOn by remember { mutableStateOf(KDrive.kdriveOn(ctx)) }
    var token by remember { mutableStateOf("") }
    var drive by remember { mutableStateOf(KDrive.drive(ctx)) }
    var shots by remember { mutableStateOf(KDrive.shots(ctx)) }
    var mostrarKDrive by remember { mutableStateOf(kdOn && !gdOn) }

    var autorizando by remember { mutableStateOf(false) }
    var mostrarManual by remember { mutableStateOf(false) }

    Text("Google Drive", fontWeight = FontWeight.Bold, fontSize = 15.sp)
    if (!gdOn) {
        Text(
            "Tus libros, ROMs, guardados, fotos, capturas, películas y actualizaciones de todas las apps de la familia, en tu Google Drive.",
            color = UiSoft, fontSize = 13.sp
        )
        Plegado(
            "Solo tienes que pegar tu ID de cliente y tu Secreto de cliente (de Google Cloud → Aplicación de escritorio) y pulsar «Iniciar sesión con Google». " +
                "Se abrirá el navegador, aceptas el permiso y Polaris guarda todo solo para las 6 apps (en el móvil y en el ordenador)."
        )
        CampoRevista("ID de cliente", gdCid, "…apps.googleusercontent.com", ascii = true) { gdCid = it }
        CampoRevista("Secreto de cliente", gdCsec, "GOCSPX-…", secreto = true, ascii = true) { gdCsec = it }
        CampoRevista("Carpeta raíz (opcional)", gdDir, "vacío: «Familia»", ascii = true) { gdDir = it }
        MagLink(
            if (autorizando) "Esperando a Google en el navegador…" else "Iniciar sesión con Google",
            "Abre el navegador y conecta Google Drive automáticamente (sin terminal)",
            accent = true
        ) {
            if (autorizando) return@MagLink
            if (gdCid.isBlank() || gdCsec.isBlank()) { say("Pega antes tu ID de cliente y tu Secreto de cliente"); return@MagLink }
            autorizando = true
            scope.launch {
                try {
                    val rtok = withContext(Dispatchers.IO) {
                        com.gonzalo.familia.nucleo.GoogleDrive.autorizarEnNavegador(
                            clientId = gdCid,
                            clientSecret = gdCsec,
                            abrirUrl = { url ->
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    runCatching {
                                        ctx.startActivity(
                                            android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            },
                            esperarPrimerPlano = {
                                // Android/ColorOS corta DNS y red mientras el navegador sigue delante: esperamos a que el usuario vuelva a Jonsu
                                val fin = System.currentTimeMillis() + 90_000L
                                while (!Actualizador.seVe() && System.currentTimeMillis() < fin) {
                                    runCatching { Thread.sleep(150) }
                                }
                                runCatching { Thread.sleep(500) }
                            },
                        )
                    }
                    com.gonzalo.familia.NubeFamilia.saveGDrive(ctx, gdCid, gdCsec, rtok, gdDir)
                    say(com.gonzalo.familia.NubeFamilia.testGDrive(ctx))
                    com.gonzalo.familia.NubeFamilia.confirmaGDrive(ctx)
                    gdOn = true
                    NubeSync.enqueue(ctx)
                } catch (e: Exception) {
                    com.gonzalo.familia.NubeFamilia.descartaGDrive(ctx)
                    say(e.message ?: "No se pudo conectar con Google Drive")
                } finally {
                    autorizando = false
                }
            }
        }
        MagLink(if (mostrarManual) "Ocultar Refresh Token manual" else "Ya tengo un Refresh Token") {
            mostrarManual = !mostrarManual
        }
        if (mostrarManual) {
            CampoRevista("Refresh Token", gdRtok, "token OAuth", secreto = true, ascii = true) { gdRtok = it }
            MagLink("Conectar con Refresh Token", "Comprueba las credenciales y busca/crea la carpeta") {
                if (gdCid.isBlank() || gdCsec.isBlank() || gdRtok.isBlank()) { say("Falta Client ID, Client Secret o Refresh Token"); return@MagLink }
                com.gonzalo.familia.NubeFamilia.saveGDrive(ctx, gdCid, gdCsec, gdRtok, gdDir)
                scope.launch {
                    try {
                        say(com.gonzalo.familia.NubeFamilia.testGDrive(ctx))
                        com.gonzalo.familia.NubeFamilia.confirmaGDrive(ctx)
                        gdOn = true
                        NubeSync.enqueue(ctx)
                    } catch (e: Exception) {
                        com.gonzalo.familia.NubeFamilia.descartaGDrive(ctx)
                        say(e.message ?: "No se pudo conectar con Google Drive")
                    }
                }
            }
        }
    } else {
        Text("Conectada a tu Google Drive", fontWeight = FontWeight.SemiBold)
        val st = NubeSync.status.ifBlank { KDrive.lastMsg }
        if (st.isNotBlank()) Text(st, color = UiSoft, fontSize = 13.sp)
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Subir las capturas", modifier = Modifier.weight(1f))
            Interruptor(shots) { shots = it; KDrive.setShots(ctx, it) }
        }
        MagLink(if (NubeSync.running) "Sincronizando…" else "Sincronizar ahora", accent = !NubeSync.running) {
            if (!NubeSync.running) { NubeSync.enqueue(ctx, verify = true); say("Sincronizando con tu nube en segundo plano") }
        }
        if (kdOn) {
            Spacer(Modifier.height(6.dp))
            MagLink(
                if (MigradorGDrive.enMarcha) "Migrando de kDrive a Google Drive…" else "Migrar todo de kDrive a Google Drive",
                "Copia tus fotos, libros, ROMs, partidas, películas y versiones sin borrar nada en kDrive y los enlaza en tu cuenta",
                accent = !MigradorGDrive.enMarcha
            ) {
                if (!MigradorGDrive.enMarcha) MigradorGDrive.iniciar(ctx)
            }
            if (MigradorGDrive.progreso.isNotBlank()) Text(MigradorGDrive.progreso, color = UiSoft, fontSize = 12.sp)
            if (MigradorGDrive.resumen.isNotBlank()) Text(MigradorGDrive.resumen, color = UiSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            MigradorGDrive.error?.let { Text("Error al migrar: $it", color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
        MagLink("Desconectar Google Drive", "Lo subido sigue en tu Google Drive") {
            com.gonzalo.familia.NubeFamilia.forgetGDrive(ctx); gdOn = false; gdCsec = ""; gdRtok = ""
            say("Google Drive desconectado en todas las apps")
        }
    }

    Spacer(Modifier.height(14.dp))
    MagLink(if (mostrarKDrive) "Ocultar kDrive (respaldo)" else "kDrive (respaldo de transición)", if (kdOn) "Conectado (${KDrive.drive(ctx)})" else "Sin configurar") {
        mostrarKDrive = !mostrarKDrive
    }
    if (mostrarKDrive) {
        if (!kdOn) {
            Text("Solo necesario si aún te quedan archivos antiguos en kDrive pendientes de pasar a Google Drive.", color = UiSoft, fontSize = 13.sp)
            CampoRevista("Token", token, "token de API de kDrive", secreto = true, ascii = true) { token = it }
            CampoRevista("kDrive", drive, "número o dirección", ascii = true) { drive = it }
            MagLink("Conectar kDrive", "Comprueba el token y busca tu carpeta", accent = !gdOn) {
                if (token.isBlank() || drive.isBlank()) { say("Falta el token o el número de tu kDrive"); return@MagLink }
                KDrive.save(ctx, token, drive)
                scope.launch {
                    try { say(KDrive.test(ctx)); KDrive.confirma(ctx); kdOn = true; NubeSync.enqueue(ctx) }
                    catch (e: Exception) { KDrive.descarta(ctx); say(e.message ?: "No se pudo conectar") }
                }
            }
        } else {
            Text("kDrive de respaldo: ${KDrive.drive(ctx)}", fontWeight = FontWeight.SemiBold)
            if (!gdOn) {
                val st = NubeSync.status.ifBlank { KDrive.lastMsg }
                if (st.isNotBlank()) Text(st, color = UiSoft, fontSize = 13.sp)
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Subir las capturas", modifier = Modifier.weight(1f))
                    Interruptor(shots) { shots = it; KDrive.setShots(ctx, it) }
                }
                MagLink(if (NubeSync.running) "Sincronizando…" else "Sincronizar ahora", accent = !NubeSync.running) {
                    if (!NubeSync.running) { NubeSync.enqueue(ctx, verify = true); say("Sincronizando con tu nube en segundo plano") }
                }
            }
            MagLink("Desconectar kDrive", "Lo subido sigue en tu kDrive") {
                KDrive.forget(ctx); kdOn = false; token = ""
                say("kDrive desconectado en todas las apps (lo subido sigue en tu kDrive)")
            }
        }
    }
}
