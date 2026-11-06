package com.gonzalo.hiedra.pc

import com.gonzalo.familia.pc.Sitio
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Los programas del sistema: dónde está uno en el PATH, qué apps de Flatpak hay instaladas, abrir un programa sin esperarlo y leer lo que
 * escribe otro (con un tope de tiempo: nada de esto puede colgar la app).
 */
object Programas {
    /** (En las pruebas: qué programas «hay».) */
    @Volatile var pathPruebas: Set<String>? = null
    @Volatile var flatpaksPruebas: Set<String>? = null

    private fun dirsPath(): List<File> {
        val p = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }
        val extra = if (Sitio.sistema == Sitio.Sistema.MAC) listOf("/opt/homebrew/bin", "/usr/local/bin") else listOf("/usr/bin", "/usr/local/bin", System.getProperty("user.home") + "/.local/bin")
        return (p + extra).distinct().map(::File)
    }

    /** El programa [nombre] del PATH, o null. */
    fun donde(nombre: String): File? {
        pathPruebas?.let { return if (nombre in it) File("/usr/bin/$nombre") else null }
        return dirsPath().map { File(it, nombre) }.firstOrNull { it.isFile && it.canExecute() }
    }
    fun enPath(nombre: String): Boolean = donde(nombre) != null

    @Volatile private var flatpaks: Pair<Long, Set<String>>? = null

    /**
     * Las apps de Flatpak instaladas (`flatpak list --app --columns=application`). Se miran al mirar lo de este ordenador ([otraVez]) y,
     * si no, como mucho cada diez minutos (así no se espera al pintar los ajustes).
     */
    fun flatpaks(otraVez: Boolean = false): Set<String> {
        flatpaksPruebas?.let { return it }
        if (Sitio.sistema != Sitio.Sistema.LINUX) return emptySet()
        if (!otraVez) flatpaks?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.let { return it.second }
        val s = if (!enPath("flatpak")) emptySet() else salida(listOf("flatpak", "list", "--app", "--columns=application"), 10)
            ?.lines()?.map { it.trim() }?.filter { it.isNotBlank() && it.contains('.') }?.toSet().orEmpty()
        flatpaks = System.currentTimeMillis() to s
        return s
    }

    /** Lo que escribe una orden (o null si falla o pasa de [segundos]). */
    fun salida(orden: List<String>, segundos: Long = 20): String? = runCatching {
        val p = ProcessBuilder(orden).redirectErrorStream(false).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val texto = StringBuilder()
        val lector = Thread { runCatching { p.inputStream.bufferedReader().use { r -> texto.append(r.readText()) } } }.apply { isDaemon = true; start() }
        if (!p.waitFor(segundos, TimeUnit.SECONDS)) { p.destroyForcibly(); return null }
        lector.join(2_000)
        texto.toString()
    }.getOrNull()

    /** Abre un programa (sin esperar a que termine). Lo que escribe se tira. */
    fun abre(orden: List<String>, dir: File? = null): Process =
        ProcessBuilder(orden).directory(dir?.takeIf { it.isDirectory } ?: File(System.getProperty("user.home")))
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
}
