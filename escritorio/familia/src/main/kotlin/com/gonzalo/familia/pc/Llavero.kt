package com.gonzalo.familia.pc

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * El llavero del sistema: lo delicado de la familia en este ordenador (la sesión que comparten las apps de la familia y la clave con la
 * que se cifra lo de cada una). Se guarda como texto; nunca la contraseña.
 *
 *  - Mac: el Llavero (Keychain), con la orden `security` del sistema. Los elementos son de servicio «Familia».
 *  - Linux: el Secret Service (GNOME Keyring, KWallet…), con `secret-tool` (paquete `libsecret-tools`).
 *  - Si no hay ninguno: un archivo que solo puede leer tu usuario (como `~/.ssh`), en la carpeta común de la familia.
 */
interface Llavero {
    fun lee(nombre: String): String?
    fun guarda(nombre: String, valor: String): Boolean
    fun borra(nombre: String)
    /** Qué llavero es (para Ajustes). */
    val nombre: String

    companion object {
        const val SERVICIO = "Familia"

        /** El de este sistema (si responde), o el archivo de repuesto. */
        val delSistema: Llavero by lazy {
            System.getProperty("familia.llavero")?.let { if (it == "archivo") return@lazy LlaveroArchivo(File(Sitio.comun, "llavero.json")) }
            val candidato: Llavero? = when (Sitio.sistema) {
                Sitio.Sistema.MAC -> LlaveroMac().takeIf { it.funciona() }
                Sitio.Sistema.LINUX -> LlaveroLinux().takeIf { it.funciona() }
                else -> null
            }
            candidato ?: LlaveroArchivo(File(Sitio.comun, "llavero.json"))
        }

        /** Para guardar cualquier texto como algo sin espacios ni comillas (lo que piden las órdenes del sistema). */
        fun codifica(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
        fun decodifica(s: String): String? = runCatching { String(Base64.getDecoder().decode(s.trim()), Charsets.UTF_8) }.getOrNull()
    }
}

/** Ejecuta una orden del sistema y devuelve (código, salida). [entrada] va por la entrada estándar (no se ve en la lista de procesos). */
internal fun orden(cmd: List<String>, entrada: String? = null, espera: Long = 15): Pair<Int, String> = runCatching {
    val p = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    p.outputStream.use { o -> if (entrada != null) o.write(entrada.toByteArray(Charsets.UTF_8)) }
    val salida = p.inputStream.bufferedReader().use { it.readText() }
    if (!p.waitFor(espera, TimeUnit.SECONDS)) { p.destroyForcibly(); -1 to "" } else p.exitValue() to salida
}.getOrDefault(-1 to "")

/** El Llavero del Mac (orden `security`). */
class LlaveroMac(private val servicio: String = Llavero.SERVICIO) : Llavero {
    override val nombre = "el Llavero del Mac"

    fun funciona(): Boolean = File("/usr/bin/security").canExecute() &&
        guarda("prueba", "ok") && lee("prueba") == "ok" && run { borra("prueba"); true }

    override fun lee(nombre: String): String? {
        val (c, s) = orden(listOf("/usr/bin/security", "find-generic-password", "-s", servicio, "-a", nombre, "-w"))
        return if (c == 0) Llavero.decodifica(s) else null
    }

    override fun guarda(nombre: String, valor: String): Boolean {
        // Por la entrada estándar (modo interactivo de `security`): el valor no aparece en la lista de procesos
        val (c, _) = orden(listOf("/usr/bin/security", "-i"), "add-generic-password -U -s $servicio -a $nombre -l $servicio -w ${Llavero.codifica(valor)}\n")
        return c == 0 && lee(nombre) == valor
    }

    override fun borra(nombre: String) { orden(listOf("/usr/bin/security", "delete-generic-password", "-s", servicio, "-a", nombre)) }
}

/** El Secret Service de Linux (orden `secret-tool`). */
class LlaveroLinux(private val servicio: String = Llavero.SERVICIO.lowercase()) : Llavero {
    override val nombre = "el llavero del sistema (Secret Service)"

    fun funciona(): Boolean = orden(listOf("sh", "-c", "command -v secret-tool")).first == 0 &&
        guarda("prueba", "ok") && lee("prueba") == "ok" && run { borra("prueba"); true }

    override fun lee(nombre: String): String? {
        val (c, s) = orden(listOf("secret-tool", "lookup", "servicio", servicio, "nombre", nombre))
        return if (c == 0 && s.isNotBlank()) Llavero.decodifica(s) else null
    }

    override fun guarda(nombre: String, valor: String): Boolean {
        val (c, _) = orden(listOf("secret-tool", "store", "--label=Familia ($nombre)", "servicio", servicio, "nombre", nombre), Llavero.codifica(valor))
        return c == 0
    }

    override fun borra(nombre: String) { orden(listOf("secret-tool", "clear", "servicio", servicio, "nombre", nombre)) }
}

/** El de repuesto: un archivo que solo puede leer tu usuario (permisos 600). */
class LlaveroArchivo(private val archivo: File) : Llavero {
    override val nombre = "un archivo privado de tu usuario (no hay llavero del sistema)"
    private val cerrojo = Any()

    private fun todo(): MutableMap<String, String> = synchronized(cerrojo) {
        val t = archivo.takeIf { it.isFile }?.readText() ?: return HashMap()
        com.gonzalo.familia.nucleo.Json.lee(t).let { m -> (m as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }?.toMutableMap() } ?: HashMap()
    }

    private fun escribe(m: Map<String, String>) = synchronized(cerrojo) {
        archivo.parentFile?.mkdirs()
        val tmp = File(archivo.parentFile, archivo.name + ".tmp")
        tmp.writeText("")
        runCatching { Files.setPosixFilePermissions(tmp.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
        tmp.writeText(com.gonzalo.familia.nucleo.Json.escribe(m))
        if (!tmp.renameTo(archivo)) { archivo.delete(); tmp.renameTo(archivo) }
    }

    override fun lee(nombre: String): String? = todo()[nombre]?.let { Llavero.decodifica(it) }
    override fun guarda(nombre: String, valor: String): Boolean { synchronized(cerrojo) { escribe(todo().also { it[nombre] = Llavero.codifica(valor) }) }; return true }
    override fun borra(nombre: String) { synchronized(cerrojo) { val m = todo(); if (m.remove(nombre) != null) escribe(m) } }
}

/** En memoria (para las pruebas). */
class LlaveroMemoria : Llavero {
    override val nombre = "Memoria"
    val cosas = java.util.concurrent.ConcurrentHashMap<String, String>()
    override fun lee(nombre: String) = cosas[nombre]
    override fun guarda(nombre: String, valor: String): Boolean { cosas[nombre] = valor; return true }
    override fun borra(nombre: String) { cosas.remove(nombre) }
}
