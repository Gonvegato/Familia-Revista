package com.gonzalo.familia.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Actualizarse sola desde tu kDrive, como en Android: cada versión de escritorio se sube a «Mi carpeta/versiones» con su sistema en
 * el nombre (`Azabache-1.2.0-mac.dmg`, `Azabache-1.2.0-linux.tar.gz`, `Azabache-1.2.0-linux.deb`). Los actualizadores de Android solo
 * cogen `^<App>-<versión>\.apk$`, así que nunca se confunden.
 *
 * Sola del todo ([automatico]): al abrir la app (y cada pocas horas) mira tu kDrive; si hay una versión nueva, la baja y la deja
 * preparada, y se pone sola **al cerrar la app** (o al pulsar «Reiniciar ahora»). Así:
 *  - En el Mac, el .dmg se monta sin enseñarlo, la app nueva se copia encima de la de ahora (sin la marca de cuarentena: es la misma
 *    app que ya abriste, así que no vuelve a pedir «Abrir igualmente») y se desmonta.
 *  - En Linux con el .tar.gz (CachyOS, Arch y cualquier otro), se descomprime y su `instalar.sh` la pone en su sitio (y al día el menú).
 *  - Con el .deb (instalada en /opt, del sistema) no se puede sola: se baja a Descargas y se abre el instalador, como antes.
 */
object Actualizador {
    class Nueva(val version: String, val archivo: KDrive.Archivo)
    /** Una versión nueva ya bajada (y descomprimida en Linux), lista para ponerse. */
    class Preparada(val version: String, val archivo: File, val carpeta: File?)

    /** La versión de esta app (la pone el empaquetado; en desarrollo, «0»). */
    val version: String get() = System.getProperty("familia.version")?.takeIf { it.isNotBlank() } ?: "0"

    private fun partes(v: String) = v.split('.').map { it.toIntOrNull() ?: 0 }
    fun mayor(a: String, b: String): Boolean {
        val x = partes(a); val y = partes(b)
        for (i in 0 until maxOf(x.size, y.size)) { val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }; if (p != q) return p > q }
        return false
    }

    /**
     * Dónde está instalada esta app (la carpeta `<App>/` con bin/ y lib/ en Linux; el `<App>.app` en el Mac), a partir de la carpeta de
     * recursos que pone el empaquetado: `<App>/lib/app/resources` o `<App>.app/Contents/app/resources`. null en desarrollo.
     */
    fun instalada(recursos: String? = System.getProperty("compose.application.resources.dir")): File? =
        recursos?.takeIf { it.isNotBlank() }?.let { File(it).absoluteFile.parentFile?.parentFile?.parentFile }

    /** Instalada con el .deb (en /opt, del sistema): esa la actualiza su instalador. */
    fun conDeb(sitio: File? = instalada(), sistema: Sitio.Sistema = Sitio.sistema): Boolean =
        sistema != Sitio.Sistema.MAC && sitio?.path?.startsWith("/opt/") == true

    /** ¿Puede ponerse sola la versión nueva? Si está instalada en una carpeta tuya (no la del .deb) y se puede escribir en ella. */
    fun sePoneSola(sitio: File? = instalada()): Boolean =
        sitio != null && sitio.isDirectory && !conDeb(sitio) && sitio.canWrite() && sitio.parentFile?.canWrite() == true

    /** Lo que vale para este sistema: `<App>-<versión>-mac.dmg`; en Linux el `.tar.gz` (o el `.deb` si se instaló con él). */
    fun patron(app: String, sistema: Sitio.Sistema = Sitio.sistema, deb: Boolean = conDeb(sistema = sistema)): Regex {
        val nombre = Regex.escape(app.replaceFirstChar { it.uppercase() })
        val fin = when { sistema == Sitio.Sistema.MAC -> "mac\\.dmg"; deb -> "linux\\.deb"; else -> "linux\\.tar\\.gz" }
        return Regex("^$nombre-(\\d+(?:\\.\\d+)*)-$fin$")
    }

    /** La versión más nueva que hay en tu kDrive para esta app y este sistema, si es más nueva que la de ahora. Bloquea (red). */
    fun busca(app: String, kdrive: KDrive = KDrive()): Nueva? {
        if (!kdrive.activo) return null
        val p = patron(app)
        return kdrive.versiones().mapNotNull { a -> p.find(a.nombre)?.groupValues?.get(1)?.let { Nueva(it, a) } }
            .filter { mayor(it.version, version) }.maxWithOrNull { a, b -> if (mayor(a.version, b.version)) 1 else if (mayor(b.version, a.version)) -1 else 0 }
    }

    // ---------------------------------------------------------------- sola del todo

    /** La versión nueva que ya está bajada y se pondrá al cerrar (la pantalla la enseña). */
    var preparada by mutableStateOf<Preparada?>(null)
        private set
    /** Lo último que ha pasado al buscar o poner (para Ajustes → Versión). */
    var estado by mutableStateOf("")
        private set

    private fun carpetaDe(app: String) = File(Sitio.de(app), "actualizacion")

    /**
     * Mira si hay versión nueva y, si se puede poner sola, la baja y la deja preparada (sin tocar la de ahora). Bloquea (red): llámalo
     * fuera de la pantalla. Devuelve la preparada, o null si no hay nada nuevo o no se puede sola.
     */
    fun automatico(app: String, kdrive: KDrive = KDrive()): Preparada? {
        preparada?.let { return it }
        if (!sePoneSola()) return null
        val n = runCatching { busca(app, kdrive) }.getOrElse { estado = "No he podido mirar si hay versión nueva: ${it.message}"; return null } ?: return null
        val dir = carpetaDe(app).also { it.mkdirs() }
        // (lo de versiones anteriores ya no sirve)
        dir.listFiles()?.filter { !it.name.contains(n.version) }?.forEach { it.deleteRecursively() }
        val archivo = File(dir, n.archivo.nombre)
        if (!archivo.isFile || (n.archivo.tamano > 0 && archivo.length() != n.archivo.tamano)) {
            estado = "Bajando la ${n.version}…"
            val tmp = File(dir, n.archivo.nombre + ".parcial")
            kdrive.baja(n.archivo.id, tmp)
            if (!tmp.renameTo(archivo)) { tmp.copyTo(archivo, overwrite = true); tmp.delete() }
        }
        val carpeta = if (Sitio.sistema == Sitio.Sistema.MAC) null else descomprime(archivo, File(dir, "v${n.version}"))
        return Preparada(n.version, archivo, carpeta).also { preparada = it; estado = "La ${n.version} está lista: se pone sola al cerrar." }
    }

    /** Descomprime el .tar.gz de Linux en [destino] (vacía antes). Devuelve la carpeta. */
    fun descomprime(targz: File, destino: File): File {
        destino.deleteRecursively(); destino.mkdirs()
        corre(listOf("tar", "-xzf", targz.absolutePath, "-C", destino.absolutePath), "descomprimir la versión nueva")
        return destino
    }

    /**
     * Pone la versión preparada encima de la de ahora. Al cerrar la app (o antes de reiniciar). Bloquea unos segundos. Devuelve el
     * programa de la versión nueva (para volver a abrirla) o null si no había nada o no se pudo (lo de ahora sigue intacto).
     */
    fun ponLaPreparada(app: String, sitio: File? = instalada()): File? {
        val p = preparada ?: return null
        val donde = sitio ?: return null
        return runCatching {
            val programa = if (Sitio.sistema == Sitio.Sistema.MAC) ponMac(p.archivo, donde) else ponLinux(p.carpeta ?: descomprime(p.archivo, File(carpetaDe(app), "v${p.version}")), app, donde)
            preparada = null
            carpetaDe(app).deleteRecursively()
            programa
        }.getOrElse { estado = "No se ha podido poner la ${p.version}: ${it.message}"; null }
    }

    /** Linux: el `instalar.sh` de la versión nueva la pone en [donde] (y pone al día la entrada del menú). */
    fun ponLinux(carpeta: File, app: String, donde: File, entorno: Map<String, String> = emptyMap()): File {
        val nombre = app.replaceFirstChar { it.uppercase() }
        val script = File(carpeta, "instalar.sh")
        require(script.isFile) { "la versión nueva no trae instalar.sh" }
        corre(listOf("sh", script.absolutePath, "--destino=${donde.absolutePath}", "--callada"), "instalar la versión nueva", entorno)
        return File(donde, "bin/$nombre")
    }

    /** Mac: monta el .dmg sin enseñarlo, copia la app encima de [donde] (el `<App>.app` de ahora) y lo desmonta. */
    fun ponMac(dmg: File, donde: File): File {
        val montaje = File(dmg.parentFile, "montaje").also { it.deleteRecursively(); it.mkdirs() }
        corre(listOf("hdiutil", "attach", "-nobrowse", "-noautoopen", "-noverify", "-quiet", "-mountpoint", montaje.absolutePath, dmg.absolutePath), "abrir el .dmg")
        try {
            val nueva = montaje.listFiles()?.firstOrNull { it.name.endsWith(".app") && it.isDirectory } ?: error("el .dmg no trae ninguna app")
            val lado = File(donde.parentFile, donde.name + ".nueva"); val vieja = File(donde.parentFile, donde.name + ".vieja")
            lado.deleteRecursively(); vieja.deleteRecursively()
            corre(listOf("ditto", nueva.absolutePath, lado.absolutePath), "copiar la app nueva")
            // Sin la marca de «descargado de internet» (por si la tuviera): es la misma app que ya abriste, en su versión nueva
            runCatching { corre(listOf("xattr", "-dr", "com.apple.quarantine", lado.absolutePath), "quitar la cuarentena") }
            // El cambio, de golpe: si algo falla aquí, la de antes sigue entera
            if (!donde.renameTo(vieja)) error("no puedo apartar la versión de ahora")
            if (!lado.renameTo(donde)) { vieja.renameTo(donde); error("no puedo poner la versión nueva") }
            vieja.deleteRecursively()
        } finally {
            runCatching { corre(listOf("hdiutil", "detach", "-quiet", montaje.absolutePath), "cerrar el .dmg") }
            montaje.delete()
        }
        return donde
    }

    /** Vuelve a abrir la app (la versión que haya ya puesta) un momento después de cerrarse esta. */
    fun abreOtraVez(programa: File) {
        runCatching {
            val orden = if (Sitio.sistema == Sitio.Sistema.MAC) listOf("open", "-n", programa.absolutePath) else listOf(programa.absolutePath)
            ProcessBuilder(orden).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        }
    }

    /** Sin sola: la baja a Descargas y la abre con el sistema (el instalador del .deb, o el .dmg en el Mac). Bloquea (red). */
    fun instala(n: Nueva, kdrive: KDrive = KDrive(), progreso: (Long, Long) -> Unit = { _, _ -> }): File {
        val casa = File(System.getProperty("user.home"))
        val descargas = listOf("Descargas", "Downloads").map { File(casa, it) }.firstOrNull { it.isDirectory } ?: casa
        val destino = File(descargas, n.archivo.nombre)
        kdrive.baja(n.archivo.id, destino, progreso)
        Sitio.abre(destino.absolutePath)
        return destino
    }

    private fun corre(orden: List<String>, para: String, entorno: Map<String, String> = emptyMap()) {
        val p = ProcessBuilder(orden).redirectErrorStream(true).also { it.environment().putAll(entorno) }.start()
        val salida = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(5, TimeUnit.MINUTES)) { p.destroyForcibly(); error("no he podido $para (tarda demasiado)") }
        if (p.exitValue() != 0) error("no he podido $para: ${salida.trim().take(300)}")
    }

    /** (Para las pruebas.) */
    fun preparaParaPruebas(p: Preparada?) { preparada = p }
}
