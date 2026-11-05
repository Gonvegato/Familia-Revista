package com.gonzalo.hiedra.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.Sitio
import java.io.File

/** Un juego de este ordenador: de Steam (`steam:<appid>`, la misma clave que usaba Atrio del PC) o un programa tuyo (`local:<id>`). */
class JuegoPc(val clave: String, val nombre: String, val portada: String, val ruta: String = "") {
    val steamId: String? get() = clave.removePrefix("steam:").takeIf { clave.startsWith("steam:") }
}

/**
 * Lo que hay en este ordenador para jugar: los juegos instalados de Steam (se leen de su carpeta: `steamapps/appmanifest_*.acf`) y tus
 * programas (los añades a mano: un ejecutable, un .AppImage, una app del Mac…). Dónde está cada uno es de este ordenador: no va a la cuenta.
 */
object Pc {
    @Volatile var dirPruebas: File? = null
    private fun archivo() = File(dirPruebas ?: Sitio.de("hiedra"), "programas.json")

    /** (En las pruebas, otras carpetas de Steam.) */
    @Volatile var steamPruebas: List<File>? = null

    /** Las carpetas de Steam que se miran (la de siempre de cada sistema; o la que digas). */
    fun carpetasSteam(): List<File> {
        steamPruebas?.let { return it }
        val casa = File(System.getProperty("user.home"))
        val base = when (Sitio.sistema) {
            Sitio.Sistema.MAC -> listOf(File(casa, "Library/Application Support/Steam"))
            else -> listOf(File(casa, ".steam/steam"), File(casa, ".local/share/Steam"), File(casa, ".var/app/com.valvesoftware.Steam/.local/share/Steam"))
        }.filter { File(it, "steamapps").isDirectory }.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }
        // Las bibliotecas de otros discos (libraryfolders.vdf)
        val otras = base.flatMap { b -> File(b, "steamapps/libraryfolders.vdf").takeIf { it.isFile }?.readText()?.let { t -> Regex("\"path\"\\s+\"([^\"]+)\"").findAll(t).map { File(it.groupValues[1].replace("\\\\", "\\")) }.toList() }.orEmpty() }
        return (base + otras).filter { File(it, "steamapps").isDirectory }.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }
    }

    /** Los juegos de Steam instalados aquí (sin las herramientas: Proton, los runtimes, Steamworks…). */
    fun steam(): List<JuegoPc> = carpetasSteam().flatMap { c ->
        File(c, "steamapps").listFiles { f -> f.name.startsWith("appmanifest_") && f.name.endsWith(".acf") }.orEmpty().mapNotNull { f ->
            val t = runCatching { f.readText() }.getOrNull() ?: return@mapNotNull null
            val id = Regex("\"appid\"\\s+\"(\\d+)\"").find(t)?.groupValues?.get(1) ?: return@mapNotNull null
            val nombre = Regex("\"name\"\\s+\"([^\"]+)\"").find(t)?.groupValues?.get(1) ?: return@mapNotNull null
            if (Regex("(?i)proton|steam linux runtime|steamworks|redistributable|steamvr").containsMatchIn(nombre)) return@mapNotNull null
            JuegoPc("steam:$id", nombre, "https://cdn.cloudflare.steamstatic.com/steam/apps/$id/library_600x900.jpg")
        }
    }.distinctBy { it.clave }.sortedBy { it.nombre.lowercase() }

    /** Tus programas (los que has añadido aquí). */
    fun programas(): List<JuegoPc> = runCatching { Json.lee(archivo().readText()).comoLista() }.getOrDefault(emptyList()).map { it.comoMapa() }
        .map { JuegoPc(it["k"].comoTexto(), it["n"].comoTexto(), "", it["r"].comoTexto()) }.filter { it.clave.isNotBlank() }

    fun anadePrograma(nombre: String, ruta: File): JuegoPc {
        val clave = "local:" + java.security.MessageDigest.getInstance("SHA-1").digest(ruta.absolutePath.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
        val l = programas().filter { it.clave != clave } + JuegoPc(clave, nombre, "", ruta.absolutePath)
        archivo().writeText(Json.escribe(l.map { mapOf("k" to it.clave, "n" to it.nombre, "r" to it.ruta) }))
        return JuegoPc(clave, nombre, "", ruta.absolutePath)
    }

    fun quitaPrograma(clave: String) { archivo().writeText(Json.escribe(programas().filter { it.clave != clave }.map { mapOf("k" to it.clave, "n" to it.nombre, "r" to it.ruta) })) }

    /** Todo lo de este ordenador. */
    fun todos(): List<JuegoPc> = steam() + programas()
    fun por(clave: String): JuegoPc? = todos().firstOrNull { it.clave == clave }

    // ---------------------------------------------------------------- jugar

    /** Lo que se está jugando ahora desde Hiedra (para «Tu PC» del móvil y para contar el tiempo). */
    class Partida(val clave: String, val nombre: String, val juego: String, val desde: Long, val portada: String)
    @Volatile var ahora: Partida? = null; private set
    @Volatile var alCambiarPartida: (() -> Unit)? = null

    /**
     * Abre un juego. Los de Steam, con Steam (`steam://rungameid/<id>`: sus horas las cuenta Steam y llegan por su API); tus programas, aquí
     * mismo, y el tiempo hasta que se cierran va a las horas de este ordenador. Devuelve un mensaje para enseñar.
     */
    fun juega(j: JuegoPc, juegoId: String): String {
        j.steamId?.let { id ->
            if (!Sitio.abre("steam://rungameid/$id")) return "No he podido abrir Steam."
            ahora = Partida(j.clave, j.nombre, juegoId, System.currentTimeMillis(), j.portada); alCambiarPartida?.invoke()
            Thread({ runCatching { Thread.sleep(4 * 3600_000L) }; if (ahora?.clave == j.clave) { ahora = null; alCambiarPartida?.invoke() } }, "hiedra-steam").apply { isDaemon = true; start() }
            return "Abriendo ${j.nombre} en Steam"
        }
        val f = File(j.ruta)
        if (!f.exists()) return "No encuentro ${f.path} en este ordenador."
        val orden = when {
            Sitio.sistema == Sitio.Sistema.MAC && f.name.endsWith(".app") -> listOf("open", "-W", f.absolutePath)
            f.name.endsWith(".sh") -> listOf("bash", f.absolutePath)
            else -> listOf(f.absolutePath)
        }
        val p = runCatching { ProcessBuilder(orden).directory(f.parentFile).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start() }.getOrElse { return "No se ha podido abrir: ${it.message}" }
        val partida = Partida(j.clave, j.nombre, juegoId, System.currentTimeMillis(), j.portada)
        ahora = partida; alCambiarPartida?.invoke()
        Thread({
            runCatching { p.waitFor() }
            val ms = System.currentTimeMillis() - partida.desde
            runCatching { Juegos.sumaPartida(juegoId, ms.coerceAtMost(12 * 3600_000L)) }
            if (ahora === partida) { ahora = null; alCambiarPartida?.invoke() }
        }, "hiedra-partida").apply { isDaemon = true; start() }
        return "A jugar: ${j.nombre}"
    }
}
