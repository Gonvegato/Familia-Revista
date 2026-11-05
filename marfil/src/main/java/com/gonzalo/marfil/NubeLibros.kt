package com.gonzalo.marfil

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.work.*
import java.util.concurrent.TimeUnit
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Dónde está el archivo de un libro en tu kDrive: su id, su nombre y su tamaño. */
class NubeLibro(val f: Long, val n: String, val s: Long)

/**
 * Los archivos de los libros por tu kDrive. Cada libro que añades se sube solo, en cuanto hay conexión (como en Atrio), a
 * «Mi carpeta/Marfil/Libros/<título>/»; en el libro de la cuenta queda dónde está (campo `nube`: id de kDrive, nombre y tamaño; nunca
 * rutas de tu móvil) y así tus otros dispositivos lo bajan al abrirlo o, con «Tener todos mis libros aquí», solos con Wi-Fi. Los libros
 * que vienen de Atrio apuntan a su archivo de «Atrio/Libros/…»: no se duplican.
 */
object NubeLibros {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val subiendo = AtomicBoolean(false)
    private fun prefs(c: Context) = c.getSharedPreferences("marfil_nube", Context.MODE_PRIVATE)

    fun deJson(o: JSONObject?): NubeLibro? = o?.let { if (it.optLong("f") > 0) NubeLibro(it.optLong("f"), it.optString("n"), it.optLong("s")) else null }
    fun aJson(x: NubeLibro): JSONObject = JSONObject().put("f", x.f).put("n", x.n).put("s", x.s).put("u", System.currentTimeMillis())

    /** Dónde está cada libro en tu kDrive (kid → archivo): va en el propio libro de la cuenta (campo `nube`). */
    fun indice(c: Context): Map<String, NubeLibro> = Biblioteca.propios().mapNotNull { l -> Biblioteca.nube(l.id)?.let { l.id to it } }.toMap()

    fun de(c: Context, l: Libro): NubeLibro? = Biblioteca.nube(l.id)

    /** Apunta en el libro dónde está su archivo en tu kDrive (lo ven tus otros dispositivos). */
    fun registrar(c: Context, l: Libro, x: NubeLibro) = Biblioteca.ponerNube(l.id, x)

    /** (El índice llega con los libros de la cuenta: no hay nada aparte que bajar.) */
    fun leerIndice(c: Context): Boolean = Cuenta.vinculada

    private fun seguro(s: String) = s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]+"""), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(80).trim().ifBlank { "Sin nombre" }

    /** Si una subida o una bajada falla, no se vuelve a intentar sola hasta pasado este rato (a mano, siempre). */
    private const val ESPERA_FALLO = 30 * 60_000L
    private fun falloReciente(c: Context, que: String, id: String) = System.currentTimeMillis() - prefs(c).getLong("fallo-$que:$id", 0L) < ESPERA_FALLO
    private fun apuntaFallo(c: Context, que: String, id: String) = prefs(c).edit().putLong("fallo-$que:$id", System.currentTimeMillis()).apply()
    private fun quitaFallo(c: Context, que: String, id: String) = prefs(c).edit().remove("fallo-$que:$id").apply()

    /** El archivo de un libro en este dispositivo, si está (y no está vacío). */
    private fun archivo(l: Libro): File? = runCatching { File(android.net.Uri.parse(l.uri).path ?: return@runCatching null) }.getOrNull()?.takeIf { it.exists() && it.length() > 0 }

    /** Los libros que tienen aquí su archivo y aún no están en tu kDrive. */
    fun porSubir(c: Context, aMano: Boolean = false): List<Libro> =
        Biblioteca.propios().filter { l -> l.uri.isNotBlank() && de(c, l) == null && (aMano || !falloReciente(c, "s", l.id)) && archivo(l) != null }

    /** Los libros que están en tu kDrive y no aquí. */
    fun porBajar(c: Context): List<Libro> = Biblioteca.propios().filter { l -> l.enOtroDispositivo && de(c, l) != null && !falloReciente(c, "b", l.id) }

    /** Sube los libros que están aquí y aún no están en tu kDrive, con la conexión que haya (como en Atrio). Devuelve cuántos ha subido. */
    fun subirPendientes(c: Context, aMano: Boolean = false, aviso: (String) -> Unit = {}): Int {
        if (!KDrive.on(c) || !Cuenta.vinculada) return 0
        if (!subiendo.compareAndSet(false, true)) return 0
        var n = 0
        try {
            runCatching { leerIndice(c) }
            for (l in porSubir(c, aMano)) {
                val f = archivo(l) ?: continue
                val ext = (l.archivo.substringAfterLast('.', "").ifBlank { f.extension }).lowercase().take(5).ifBlank { "epub" }
                val nombre = "${seguro(l.titulo)}.$ext"
                try {
                    aviso("Subiendo $nombre…")
                    val id = KDrive.upload(c, "Marfil/Libros/${seguro(l.titulo)}", nombre, f.length()) { f.inputStream() }
                    registrar(c, l, NubeLibro(id, nombre, f.length()))
                    quitaFallo(c, "s", l.id)
                    n++
                } catch (e: Exception) { apuntaFallo(c, "s", l.id); aviso("No he podido subir ${l.titulo}: ${e.message}") }
            }
        } finally { subiendo.set(false) }
        return n
    }

    // ------------------------------------------------------------------ ajustes
    /** Tener en este dispositivo todos los libros de tu kDrive (se bajan solos con Wi-Fi). Por defecto, sí. */
    fun autoBajar(c: Context) = prefs(c).getBoolean("auto_bajar", true)
    fun ponAutoBajar(c: Context, v: Boolean) { prefs(c).edit().putBoolean("auto_bajar", v).apply(); if (v) programar(c) }

    // ------------------------------------------------------------------ todo junto, en segundo plano
    /** Baja los libros que están en tu kDrive y no tienes aquí (con Wi-Fi). Para si queda poco sitio. */
    fun bajarTodos(c: Context, aviso: (String) -> Unit = {}): Int {
        if (!KDrive.on(c)) return 0
        var n = 0
        for (l in porBajar(c)) {
            if (c.filesDir.usableSpace < 300L * 1024 * 1024) { aviso("Queda poco sitio en el móvil: dejo de bajar libros."); break }
            try { aviso("Bajando ${l.titulo}…"); bajar(c, l); quitaFallo(c, "b", l.id); n++ } catch (e: Exception) { apuntaFallo(c, "b", l.id) }
        }
        return n
    }

    /** Todo lo de kDrive que toca hacer ahora: subir los libros que faltan y (si lo has dejado activo) bajar los de tus otros dispositivos. Bloqueante (red). */
    fun todo(c: Context) {
        runCatching { subirPendientes(c) }
        if (autoBajar(c)) runCatching { bajarTodos(c) }
    }

    /**
     * Lo que haga falta, en segundo plano (aunque cierres la app): subir los libros nuevos en cuanto haya conexión y, si está activo
     * «Tener todos mis libros aquí», bajar con Wi-Fi los que falten. Se llama al añadir un libro, al abrir la app y tras cada
     * sincronización: si no hay nada que hacer, no hace nada (ni red).
     */
    fun siHaceFalta(c: Context) {
        if (!KDrive.on(c) || !Cuenta.vinculada) return
        runCatching { if (porSubir(c).isNotEmpty()) programa(c, "subir", NetworkType.CONNECTED, 3) }
        runCatching { if (autoBajar(c) && porBajar(c).isNotEmpty()) programa(c, "bajar", NetworkType.UNMETERED, 15) }
    }

    private fun programa(c: Context, que: String, red: NetworkType, espera: Long) {
        val req = OneTimeWorkRequestBuilder<SincroLibrosWorker>()
            .setInputData(workDataOf("que" to que))
            .setInitialDelay(espera, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(red).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        // KEEP: si ya está subiendo (o bajando), que siga; lo que llegue mientras se recoge en la siguiente sincronización
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork("marfil-libros-$que", ExistingWorkPolicy.KEEP, req)
    }

    /** (Lo de siempre: subir y bajar lo que falte.) */
    fun programar(c: Context) = siHaceFalta(c)

    /** En segundo plano, sin molestar (lo llama la sincronización). */
    fun subirEnSegundoPlano(c: Context) = siHaceFalta(c)

    /** Baja el archivo de un libro de tu kDrive y lo deja aquí. Bloqueante (red). Devuelve el libro ya con su archivo, o lanza el error. */
    fun bajar(c: Context, l: Libro): Libro {
        val x = de(c, l) ?: throw KDrive.Fail("Ese libro no está en tu kDrive todavía.")
        val ext = x.n.substringAfterLast('.', "epub").lowercase().take(5)
        val dest = File(Metadatos.dirLibros(c), "${l.id}.$ext")
        KDrive.download(c, x.f, dest)
        val ahora = Biblioteca.por(l.id) ?: l
        val conArchivo = ahora.copy(uri = android.net.Uri.fromFile(dest).toString(), archivo = x.n)
        return conArchivo.also { Biblioteca.poner(it) }
    }

    /** Lo que pasa al abrir un libro «en otro dispositivo»: si está en tu kDrive, se baja y se abre; si no, se dice por qué. */
    fun abrirBajando(c: Context, l: Libro, goto: String?) {
        val hilo = Handler(Looper.getMainLooper())
        fun dice(t: String) = hilo.post { Toast.makeText(c, t, Toast.LENGTH_LONG).show() }
        if (!KDrive.on(c)) { dice("Este libro está en otro dispositivo. Conecta tu kDrive (Ajustes → Avanzado) para bajarlo."); return }
        scope.launch {
            runCatching { leerIndice(c) }
            if (de(c, l) == null) { dice("Este libro aún no está en tu kDrive: se sube solo desde el dispositivo donde lo añadiste, en cuanto tiene conexión."); return@launch }
            dice("Bajando «${l.titulo}»…")
            try { val n = bajar(c, l); hilo.post { LectorActivity.abrir(c, n.id, goto) } }
            catch (e: Exception) { dice("No he podido bajarlo: ${e.message}") }
        }
    }
}

/** El trabajo en segundo plano de los libros: subir (con cualquier conexión) o bajar (con Wi-Fi). */
class SincroLibrosWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = try {
        val c = applicationContext
        Biblioteca.cargar(c)
        when (inputData.getString("que")) {
            "subir" -> NubeLibros.subirPendientes(c)
            "bajar" -> NubeLibros.bajarTodos(c)
            else -> NubeLibros.todo(c)
        }
        Result.success()
    } catch (e: Exception) { if (runAttemptCount < 4) Result.retry() else Result.failure() }
}
