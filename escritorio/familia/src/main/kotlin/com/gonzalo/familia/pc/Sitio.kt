package com.gonzalo.familia.pc

import java.io.File
import java.util.Locale

/**
 * Dónde vive cada cosa en el ordenador. En la cuenta está todo; aquí solo hay la caché para funcionar sin conexión, lo pendiente
 * de subir y lo que es de este ordenador (dónde está un archivo, la ventana…).
 *
 *  - Mac: `~/Library/Application Support/Familia/<App>`
 *  - Linux: `$XDG_DATA_HOME/familia/<app>` (o `~/.local/share/familia/<app>`)
 *
 * En las pruebas, la propiedad `familia.dir` (o la variable `FAMILIA_DIR`) lo cambia todo de sitio.
 */
object Sitio {
    enum class Sistema { MAC, LINUX, OTRO }

    val sistema: Sistema = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).let {
        when {
            it.contains("mac") || it.contains("darwin") -> Sistema.MAC
            it.contains("linux") -> Sistema.LINUX
            else -> Sistema.OTRO
        }
    }

    private val casa = File(System.getProperty("user.home"))

    /** La carpeta de toda la familia en este ordenador (la de cada app cuelga de aquí). */
    val raiz: File get() {
        (System.getProperty("familia.dir") ?: System.getenv("FAMILIA_DIR"))?.takeIf { it.isNotBlank() }?.let { return File(it) }
        return when (sistema) {
            Sistema.MAC -> File(casa, "Library/Application Support/Familia")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: File(casa, ".local/share").path, "familia")
        }
    }

    /** La carpeta de una app («azabache», «cobalto», «hiedra», «marfil»). */
    fun de(app: String): File = File(raiz, if (sistema == Sistema.MAC) app.replaceFirstChar { it.uppercase() } else app).also { it.mkdirs() }

    /** Lo común a las apps de la familia de este ordenador (el llavero de repuesto). */
    val comun: File get() = File(raiz, if (sistema == Sistema.MAC) "Comun" else "comun").also { it.mkdirs() }

    /** El nombre del equipo, para enseñarlo («portátil de Gonzalo»). */
    val nombreEquipo: String by lazy {
        runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull().orEmpty().substringBefore('.').ifBlank {
            if (sistema == Sistema.MAC) "Mac" else "Linux"
        }
    }

    /**
     * El número de esta app en este ordenador para lo que se apunta por dispositivo (`tiempo/<cosa>~<dispositivo>`,
     * `palabras/<dispositivo>`), como en Android («marfil-1a2b3c4d»): se crea una vez y no cambia aunque cambie el nombre del equipo.
     */
    fun dispositivo(app: String): String {
        val f = File(de(app), "dispositivo.txt")
        f.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val so = if (sistema == Sistema.MAC) "mac" else "linux"
        val id = "$app-$so-" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)
        runCatching { f.writeText(id) }
        return id
    }

    /** Abre un archivo o una dirección con el programa que toque en este sistema. */
    fun abre(que: String): Boolean = runCatching {
        val orden = when (sistema) { Sistema.MAC -> listOf("open", que); else -> listOf("xdg-open", que) }
        ProcessBuilder(orden).redirectErrorStream(true).start(); true
    }.getOrDefault(false)
}
