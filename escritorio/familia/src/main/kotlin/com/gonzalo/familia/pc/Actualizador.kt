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
    class Nueva(
        val version: String,
        val nombreArchivo: String,
        val tamano: Long,
        val archivoId: String,
        val deGoogleDrive: Boolean,
        val sha256Id: String = "",
        val archivo: KDrive.Archivo = KDrive.Archivo(nombreArchivo, archivoId.toLongOrNull() ?: 0L, false, "", tamano),
    ) {
        constructor(version: String, archivo: KDrive.Archivo) : this(version, archivo.nombre, archivo.tamano, archivo.id.toString(), false, "", archivo)
    }

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
        val nombre = NombresApp.ambos(app).joinToString("|") { Regex.escape(it) }
        val fin = when { sistema == Sitio.Sistema.MAC -> "mac\\.dmg"; deb -> "linux\\.deb"; else -> "linux\\.tar\\.gz" }
        return Regex("^(?:$nombre)-(\\d+(?:\\.\\d+)*)-$fin$")
    }

    /** La versión más nueva que hay en Google Drive (o kDrive como respaldo) para esta app y este sistema. Bloquea (red). */
    fun busca(app: String, kdrive: KDrive? = null, gdrive: com.gonzalo.familia.nucleo.GoogleDrive? = null): Nueva? {
        val p = patron(app)
        val archivos: List<Nube.Archivo> = when {
            gdrive != null -> if (!gdrive.activo) emptyList() else gdrive.versiones().map { Nube.Archivo(it.id, it.nombre, it.carpeta, it.tamano, true) }
            kdrive != null -> if (!kdrive.activo) emptyList() else kdrive.versiones().map { Nube.Archivo(it.id.toString(), it.nombre, it.carpeta, it.tamano, false) }
            else -> Nube.versiones()
        }
        val porNombre = archivos.associateBy { it.nombre }
        return archivos.mapNotNull { a ->
            p.find(a.nombre)?.groupValues?.get(1)?.let { v ->
                val shaId = porNombre["${a.nombre}.sha256"]?.id.orEmpty()
                Nueva(v, a.nombre, a.tamano, a.id, a.deGoogleDrive, shaId)
            }
        }.filter { mayor(it.version, version) }.maxWithOrNull { a, b -> if (mayor(a.version, b.version)) 1 else if (mayor(b.version, a.version)) -1 else 0 }
    }

    /** Compatibilidad con llamadas existentes que pasan solo `kdrive`. */
    fun busca(app: String, kdrive: KDrive): Nueva? = busca(app, kdrive, null)

    // ---------------------------------------------------------------- sola del todo

    /** La versión nueva que ya está bajada y se pondrá al cerrar (la pantalla la enseña). */
    var preparada by mutableStateOf<Preparada?>(null)
        private set
    /** Lo último que ha pasado al buscar o poner (para Ajustes → Versión). */
    var estado by mutableStateOf("")
        private set

    private fun carpetaDe(app: String) = File(Sitio.de(app), "actualizacion")

    private fun descarga(n: Nueva, destino: File, kdrive: KDrive?, gdrive: com.gonzalo.familia.nucleo.GoogleDrive?, progreso: (Long, Long) -> Unit = { _, _ -> }) {
        if (n.deGoogleDrive) {
            (gdrive ?: Nube.gdrive()).baja(n.archivoId, destino, progreso)
        } else {
            (kdrive ?: Nube.kdrive()).baja(n.archivoId.toLong(), destino, progreso)
        }
    }

    /** Si la carpeta `versiones` trae `<archivo>.sha256`, comprueba que el archivo bajado coincide exactamente. */
    fun verificaSha256(archivo: File, esperadoTexto: String): Boolean {
        val esperado = esperadoTexto.trim().split(Regex("\\s+")).firstOrNull()?.lowercase().orEmpty()
        if (esperado.length != 64 || !esperado.all { it in '0'..'9' || it in 'a'..'f' }) return false
        val md = java.security.MessageDigest.getInstance("SHA-256")
        archivo.inputStream().use { inp ->
            val buf = ByteArray(64 * 1024)
            while (true) { val k = inp.read(buf); if (k < 0) break; md.update(buf, 0, k) }
        }
        val real = md.digest().joinToString("") { "%02x".format(it) }
        return real == esperado
    }

    private fun compruebaIntegridad(n: Nueva, archivo: File, dir: File, kdrive: KDrive?, gdrive: com.gonzalo.familia.nucleo.GoogleDrive?) {
        if (n.sha256Id.isBlank()) return
        val shaFile = File(dir, "${n.nombreArchivo}.sha256")
        try {
            if (n.deGoogleDrive) (gdrive ?: Nube.gdrive()).baja(n.sha256Id, shaFile)
            else (kdrive ?: Nube.kdrive()).baja(n.sha256Id.toLong(), shaFile)
            if (!verificaSha256(archivo, shaFile.readText())) {
                archivo.delete()
                error("el archivo bajado no coincide con su huella SHA-256")
            }
        } finally {
            shaFile.delete()
        }
    }

    /**
     * Mira si hay versión nueva y, si se puede poner sola, la baja y la deja preparada (sin tocar la de ahora). Bloquea (red): llámalo
     * fuera de la pantalla. Devuelve la preparada, o null si no hay nada nuevo o no se puede sola.
     */
    fun automatico(app: String, kdrive: KDrive? = null, gdrive: com.gonzalo.familia.nucleo.GoogleDrive? = null): Preparada? {
        preparada?.let { return it }
        if (!sePoneSola()) return null
        val n = runCatching { busca(app, kdrive, gdrive) }.getOrElse { estado = "No he podido mirar si hay versión nueva: ${it.message}"; return null } ?: return null
        val dir = carpetaDe(app).also { it.mkdirs() }
        // (lo de versiones anteriores ya no sirve)
        dir.listFiles()?.filter { !it.name.contains(n.version) }?.forEach { it.deleteRecursively() }
        val archivo = File(dir, n.nombreArchivo)
        if (!archivo.isFile || (n.tamano > 0 && archivo.length() != n.tamano)) {
            estado = "Bajando la ${n.version}…"
            val tmp = File(dir, n.nombreArchivo + ".parcial")
            descarga(n, tmp, kdrive, gdrive)
            compruebaIntegridad(n, tmp, dir, kdrive, gdrive)
            if (!tmp.renameTo(archivo)) { tmp.copyTo(archivo, overwrite = true); tmp.delete() }
        }
        val carpeta = if (Sitio.sistema == Sitio.Sistema.MAC) null else descomprime(archivo, File(dir, "v${n.version}"))
        return Preparada(n.version, archivo, carpeta).also { preparada = it; estado = "La ${n.version} está lista: se pone sola al cerrar." }
    }

    fun automatico(app: String, kdrive: KDrive): Preparada? = automatico(app, kdrive, null)

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
            val programa = if (Sitio.sistema == Sitio.Sistema.MAC) ponMac(p.archivo, donde) else ponLinux(p.carpeta ?: descomprime(p.archivo, File(carpetaDe(app), "v${p.version}")), app, donde, entornoDeInstalar)
            preparada = null
            carpetaDe(app).deleteRecursively()
            programa
        }.getOrElse { estado = "No se ha podido poner la ${p.version}: ${it.message}"; null }
    }

    /** Linux: el `instalar.sh` de la versión nueva la pone en [donde] (y pone al día la entrada del menú). */
    fun ponLinux(carpeta: File, app: String, donde: File, entorno: Map<String, String> = emptyMap()): File {
        val nombre = NombresApp.de(app)
        val script = File(carpeta, "instalar.sh")
        require(script.isFile) { "la versión nueva no trae instalar.sh" }
        corre(listOf("sh", script.absolutePath, "--destino=${donde.absolutePath}", "--callada"), "instalar la versión nueva", entorno)
        // Si estaba con un nombre de antes, instalar.sh la ha puesto al lado con el nuevo (y deja en la vieja solo un atajo)
        val nueva = if (donde.name in NombresApp.ambos(app) && NombresApp.de(app) != donde.name) File(donde.parentFile, NombresApp.de(app)) else donde
        return File(nueva, "bin/$nombre")
    }

    /**
     * Al abrirse con el nombre nuevo, recoge lo del viejo: en Linux quita la carpeta de antes (ya solo tiene el atajo que dejó
     * instalar.sh para que la versión vieja pudiera volver a abrirla); en el Mac, si la versión de antes se puso encima de su
     * `<Viejo>.app`, le pone el nombre nuevo. Nunca toca los datos (están en la carpeta de datos, que no cambia de nombre).
     */
    fun recogeNombreViejo(app: String, aqui: File? = instalada(), sistema: Sitio.Sistema = Sitio.sistema) {
        val nuevo = NombresApp.de(app); val viejos = NombresApp.ambos(app).filter { it != nuevo }
        if (aqui == null || viejos.isEmpty()) return
        runCatching {
            if (sistema == Sitio.Sistema.MAC) {
                val destino = File(aqui.parentFile, "$nuevo.app")
                if (viejos.any { aqui.name == "$it.app" } && !destino.exists()) aqui.renameTo(destino)
            } else if (aqui.name == nuevo) {
                for (viejo in viejos) {
                    val antes = File(aqui.parentFile, viejo)
                    // Solo si es el atajo (bin/<Viejo> y nada más) o una instalación entera de antes; nunca otra cosa
                    if (antes.isDirectory && File(antes, "bin/$viejo").exists()) antes.deleteRecursively()
                }
            }
        }
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
    fun instala(n: Nueva, kdrive: KDrive? = null, gdrive: com.gonzalo.familia.nucleo.GoogleDrive? = null, progreso: (Long, Long) -> Unit = { _, _ -> }): File {
        val casa = File(System.getProperty("user.home"))
        val descargas = listOf("Descargas", "Downloads").map { File(casa, it) }.firstOrNull { it.isDirectory } ?: casa
        val destino = File(descargas, n.nombreArchivo)
        descarga(n, destino, kdrive, gdrive, progreso)
        compruebaIntegridad(n, destino, descargas, kdrive, gdrive)
        Sitio.abre(destino.absolutePath)
        return destino
    }

    fun instala(n: Nueva, kdrive: KDrive, progreso: (Long, Long) -> Unit = { _, _ -> }): File = instala(n, kdrive, null, progreso)

    private fun corre(orden: List<String>, para: String, entorno: Map<String, String> = emptyMap()) {
        val p = ProcessBuilder(orden).redirectErrorStream(true).also { it.environment().putAll(entorno) }.start()
        val salida = java.util.concurrent.atomic.AtomicReference("")
        val lector = Thread { salida.set(runCatching { p.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")) }.apply { isDaemon = true; start() }
        if (!p.waitFor(5, TimeUnit.MINUTES)) { p.destroyForcibly(); lector.join(500); error("no he podido $para (tarda demasiado)") }
        lector.join(2_000)
        if (p.exitValue() != 0) error("no he podido $para: ${salida.get().trim().take(300)}")
    }

    /** (Para las pruebas.) */
    fun preparaParaPruebas(p: Preparada?) { preparada = p }
    /** (Para las pruebas: dónde deja instalar.sh la entrada del menú, en vez de las carpetas de tu usuario.) */
    @Volatile var entornoDeInstalar: Map<String, String> = emptyMap()
}
