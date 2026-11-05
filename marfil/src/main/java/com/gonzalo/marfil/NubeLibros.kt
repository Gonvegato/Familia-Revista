package com.gonzalo.marfil

import android.content.Context
import android.net.ConnectivityManager
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
 * Los archivos de los libros por tu kDrive. Cada libro que añades se sube solo (con Wi-Fi) a «Mi carpeta/Marfil/Libros/<título>/»;
 * en tu cuenta queda el índice (/marfil/nube/<libro>: id de kDrive, nombre y tamaño; nunca rutas de tu móvil) y así tus otros dispositivos
 * saben qué hay y lo bajan cuando abres el libro. Los libros que vienen de Atrio apuntan a su archivo de «Atrio/Libros/…»: no se duplican.
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

    private fun sinMedir(c: Context): Boolean = runCatching { !(c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered }.getOrDefault(true)

    /** Sube los libros que están aquí y aún no están en tu kDrive. Solo con Wi-Fi (o datos sin límite) salvo que se pida a mano. Devuelve cuántos ha subido. */
    fun subirPendientes(c: Context, aMano: Boolean = false, aviso: (String) -> Unit = {}): Int {
        if (!KDrive.on(c) || !Cuenta.vinculada) return 0
        if (!aMano && !sinMedir(c)) return 0
        if (!subiendo.compareAndSet(false, true)) return 0
        var n = 0
        try {
            runCatching { leerIndice(c) }
            for (l in Biblioteca.propios()) {
                if (l.uri.isBlank() || de(c, l) != null) continue
                val f = runCatching { File(android.net.Uri.parse(l.uri).path ?: return@runCatching null) }.getOrNull()?.takeIf { it.exists() && it.length() > 0 } ?: continue
                val ext = (l.archivo.substringAfterLast('.', "").ifBlank { f.extension }).lowercase().take(5).ifBlank { "epub" }
                val nombre = "${seguro(l.titulo)}.$ext"
                try {
                    aviso("Subiendo $nombre…")
                    val id = KDrive.upload(c, "Marfil/Libros/${seguro(l.titulo)}", nombre, f.length()) { f.inputStream() }
                    registrar(c, l, NubeLibro(id, nombre, f.length()))
                    n++
                } catch (e: Exception) { aviso("No he podido subir ${l.titulo}: ${e.message}") }
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
        for (l in Biblioteca.propios()) {
            if (!l.enOtroDispositivo || de(c, l) == null) continue
            if (c.filesDir.usableSpace < 300L * 1024 * 1024) { aviso("Queda poco sitio en el móvil: dejo de bajar libros."); break }
            runCatching { aviso("Bajando ${l.titulo}…"); bajar(c, l); n++ }
        }
        return n
    }

    /** Todo lo de kDrive que toca hacer ahora: subir los libros que faltan y (si lo has dejado activo) bajar los de tus otros dispositivos. Bloqueante (red). */
    fun todo(c: Context) {
        runCatching { subirPendientes(c, aMano = true) }
        if (autoBajar(c)) runCatching { bajarTodos(c) }
    }

    /** Pide que se haga en segundo plano (aunque cierres la app) cuando haya Wi-Fi. Varias peticiones seguidas se juntan en una. */
    fun programar(c: Context) {
        if (!KDrive.on(c) || !Cuenta.vinculada) return
        val req = OneTimeWorkRequestBuilder<SincroLibrosWorker>()
            .setInitialDelay(15, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork("marfil-libros", ExistingWorkPolicy.KEEP, req)
    }

    /** En segundo plano, sin molestar (lo llama la sincronización). */
    fun subirEnSegundoPlano(c: Context) = programar(c)

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
            if (de(c, l) == null) { dice("Este libro aún no está en tu kDrive: se sube solo, con Wi-Fi, desde el dispositivo donde lo añadiste."); return@launch }
            dice("Bajando «${l.titulo}»…")
            try { val n = bajar(c, l); hilo.post { LectorActivity.abrir(c, n.id, goto) } }
            catch (e: Exception) { dice("No he podido bajarlo: ${e.message}") }
        }
    }
}

/** El trabajo en segundo plano de los libros: subir y bajar, con Wi-Fi. */
class SincroLibrosWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = try { NubeLibros.todo(applicationContext); Result.success() } catch (e: Exception) { if (runAttemptCount < 4) Result.retry() else Result.failure() }
}
