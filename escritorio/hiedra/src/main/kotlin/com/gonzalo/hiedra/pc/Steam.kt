package com.gonzalo.hiedra.pc

import com.gonzalo.familia.pc.Sitio
import java.io.File

/**
 * Los juegos de Steam instalados en este ordenador: se leen de sus carpetas (no hace falta que Steam esté abierto). La carpeta de Steam
 * (la normal, `~/.local/share/Steam`, su enlace `~/.steam/root`, la de Flatpak o la del Mac), sus otras bibliotecas
 * (`config/libraryfolders.vdf`) y en cada una los `steamapps/appmanifest_<appid>.acf`. Se abren con Steam (`steam://rungameid/<appid>`):
 * sus horas las cuenta Steam y llegan por su API.
 */
object Steam {
    /** (En las pruebas, otras carpetas de Steam.) */
    @Volatile var raicesPruebas: List<File>? = null

    class Raiz(val dir: File, val flatpak: Boolean)

    private val casa get() = File(System.getProperty("user.home"))
    private const val FLATPAK = "com.valvesoftware.Steam"

    /** Las carpetas de Steam de este ordenador (sin repetir la misma por sus enlaces). */
    fun raices(): List<Raiz> {
        raicesPruebas?.let { l -> return l.map { Raiz(it, false) } }
        val l = when (Sitio.sistema) {
            Sitio.Sistema.MAC -> listOf(File(casa, "Library/Application Support/Steam"))
            else -> listOf(File(casa, ".local/share/Steam"), File(casa, ".steam/root"), File(casa, ".steam/steam"), File(casa, ".steam/debian-installation"),
                File(casa, ".var/app/$FLATPAK/.local/share/Steam"), File(casa, ".var/app/$FLATPAK/data/Steam"))
        }
        return l.filter { appsDe(it) != null }.map { Raiz(it, it.path.contains("/.var/app/$FLATPAK/")) }.distinctBy { real(it.dir) }
    }

    private fun real(f: File) = runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)
    /** La carpeta `steamapps` de una biblioteca (las instalaciones antiguas usaban «SteamApps»). */
    private fun appsDe(lib: File): File? = listOf("steamapps", "SteamApps").map { File(lib, it) }.firstOrNull { it.isDirectory }

    /** Las rutas de las bibliotecas que dice un `libraryfolders.vdf` (`libraryfolders` → "0".."n" → `path`; el formato viejo ponía la ruta tal cual). */
    fun rutasDeBibliotecas(texto: String): List<String> {
        val raiz = Vdf.de(Vdf.lee(texto), "libraryfolders") as? Map<*, *> ?: return emptyList()
        return raiz.entries.filter { (it.key as? String)?.all { c -> c.isDigit() } == true }.mapNotNull { (_, v) ->
            when (v) { is String -> v; is Map<*, *> -> Vdf.texto(v, "path"); else -> null }?.takeIf { it.isNotBlank() }?.replace("\\\\", "\\")
        }
    }

    /** Todas las bibliotecas de una carpeta de Steam: la suya y las de otros discos. */
    fun bibliotecas(r: Raiz): List<File> {
        val vdfs = listOf(File(r.dir, "config/libraryfolders.vdf"), File(appsDe(r.dir) ?: File(r.dir, "steamapps"), "libraryfolders.vdf"))
        val otras = vdfs.filter { it.isFile }.flatMap { f -> runCatching { rutasDeBibliotecas(f.readText()) }.getOrDefault(emptyList()) }.map(::File)
        return (listOf(r.dir) + otras).filter { appsDe(it) != null }.distinctBy(::real)
    }

    /** Lo que dice un `appmanifest_<appid>.acf`. [instalado]: StateFlags con el bit 4 («FullyInstalled»), o sin StateFlags. */
    class Manifiesto(val appid: String, val nombre: String, val carpeta: String, val instalado: Boolean)

    fun manifiesto(texto: String): Manifiesto? {
        val a = Vdf.de(Vdf.lee(texto), "AppState") ?: return null
        val id = Vdf.texto(a, "appid").takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: return null
        val nombre = Vdf.texto(a, "name").ifBlank { return null }
        val flags = Vdf.texto(a, "StateFlags").toLongOrNull()
        return Manifiesto(id, nombre, Vdf.texto(a, "installdir"), flags == null || flags and 4L != 0L)
    }

    /** Lo que Steam instala como si fueran juegos y no lo son (Proton, los runtimes, Steamworks…). */
    fun esHerramienta(nombre: String) = Regex("(?i)^proton\\b|proton easyanticheat|proton battleye|steam linux runtime|steamworks common|redistributable|^steamvr$|^steam audio").containsMatchIn(nombre)

    fun portada(appid: String) = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appid/library_600x900.jpg"
    fun fondo(appid: String) = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appid/library_hero.jpg"

    /** Los juegos de Steam instalados aquí (sin las herramientas), por nombre. */
    fun juegos(): List<JuegoPc> = raices().flatMap { r -> bibliotecas(r).map { r to it } }.flatMap { (r, lib) ->
        val apps = appsDe(lib) ?: return@flatMap emptyList()
        apps.listFiles { f -> f.name.startsWith("appmanifest_") && f.name.endsWith(".acf") }.orEmpty().mapNotNull { f ->
            val m = runCatching { manifiesto(f.readText()) }.getOrNull() ?: return@mapNotNull null
            if (!m.instalado || esHerramienta(m.nombre)) return@mapNotNull null
            JuegoPc("steam:${m.appid}", m.nombre, portada(m.appid), fuente = Fuente.STEAM, fondo = fondo(m.appid),
                carpeta = if (m.carpeta.isNotBlank()) File(apps, "common/${m.carpeta}").path else "", extra = if (r.flatpak) mapOf("flatpak" to "1") else emptyMap())
        }
    }.distinctBy { it.clave }.sortedBy { it.nombre.lowercase() }

    /** La orden para abrir un juego: `steam steam://rungameid/<id>` (o el Steam de Flatpak); si no se encuentra, lo abre el sistema. */
    fun orden(appid: String, flatpak: Boolean, enPath: (String) -> Boolean = Programas::enPath): List<String> {
        val url = "steam://rungameid/$appid"
        return when {
            Sitio.sistema == Sitio.Sistema.MAC -> listOf("open", url)
            flatpak && enPath("flatpak") -> listOf("flatpak", "run", FLATPAK, url)
            enPath("steam") -> listOf("steam", url)
            File(casa, ".var/app/$FLATPAK").isDirectory && enPath("flatpak") -> listOf("flatpak", "run", FLATPAK, url)
            else -> listOf("xdg-open", url)
        }
    }

    /**
     * Los juegos de Steam que se están ejecutando: Steam los abre con «reaper SteamLaunch AppId=<id> …» (en Linux). En el Mac no hay manera
     * igual de fiable: no se sabe.
     */
    fun enMarcha(lineas: List<String>): Set<String> = lineas.filter { it.contains("SteamLaunch") }
        .mapNotNull { Regex("(?:^|[\\u0000 ])AppId=(\\d+)(?:[\\u0000 ]|$)").find(it)?.groupValues?.get(1) }.toSet()
}
