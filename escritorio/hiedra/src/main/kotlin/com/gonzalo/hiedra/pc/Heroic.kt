package com.gonzalo.hiedra.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.Sitio
import java.io.File
import java.net.URLEncoder

/**
 * Los juegos de Heroic (Epic, GOG y Amazon, y lo que hayas añadido a mano en Heroic). Se leen de sus archivos, sin abrir Heroic:
 *
 *  - Epic: `legendaryConfig/legendary/installed.json` (lo instalado, por `app_name`); los títulos y las portadas, en
 *    `store_cache/legendary_library.json` (`library`).
 *  - GOG: `gog_store/installed.json` (`installed`); los títulos, en `store_cache/gog_library.json` (`games`) o en el `gog_store/library.json` de antes.
 *  - Amazon: `nile_config/nile/installed.json`; los títulos, en `store_cache/nile_library.json` (`library`) o en `nile_config/nile/library.json`.
 *  - Los añadidos a mano: `sideload_apps/library.json` (`games`).
 *
 * Se abren con Heroic (`heroic://launch?appName=…&runner=…`). Sus claves son las de Atrio del PC: `heroic:<runner>:<appName>`.
 */
object Heroic {
    @Volatile var raicesPruebas: List<File>? = null

    private val casa get() = File(System.getProperty("user.home"))
    private fun xdgConfig() = File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: File(casa, ".config").path)

    fun raices(): List<File> = (raicesPruebas ?: when (Sitio.sistema) {
        Sitio.Sistema.MAC -> listOf(File(casa, "Library/Application Support/heroic"))
        else -> listOf(File(xdgConfig(), "heroic"), File(casa, ".var/app/com.heroicgameslauncher.hgl/config/heroic"))
    }).filter { it.isDirectory }

    /** El nombre de cada tienda, como se enseña. */
    fun tienda(runner: String) = when (runner) { "legendary" -> "Epic"; "gog" -> "GOG"; "nile" -> "Amazon"; else -> "Heroic" }

    private fun json(f: File): Any? = f.takeIf { it.isFile }?.let { runCatching { Json.lee(it.readText()) }.getOrNull() }

    /** Lo que se sabe de cada juego por las cachés de la tienda de Heroic: título, portada vertical y fondo. */
    private class Info(val titulo: String, val portada: String, val fondo: String, val carpeta: String, val instalado: Boolean)

    private fun infos(lista: List<Any?>): Map<String, Info> = lista.map { it.comoMapa() }.filter { it["app_name"].comoTexto().isNotBlank() }.associate { g ->
        fun url(k: String) = g[k].comoTexto().takeIf { it.startsWith("http") }.orEmpty()
        g["app_name"].comoTexto() to Info(g["title"].comoTexto(), url("art_square"), url("art_background").ifBlank { url("art_cover") },
            g["install"].comoMapa()["install_path"].comoTexto(), g["is_installed"].comoBool())
    }

    /** Los juegos instalados en una carpeta de Heroic. */
    fun juegos(raiz: File): List<JuegoPc> {
        val out = ArrayList<JuegoPc>()
        fun mete(runner: String, app: String, titulo: String, i: Info?, carpeta: String) {
            if (app.isBlank()) return
            out += JuegoPc("heroic:$runner:$app", titulo.ifBlank { i?.titulo.orEmpty() }.ifBlank { app }, i?.portada.orEmpty(), fuente = Fuente.HEROIC,
                etiqueta = tienda(runner), fondo = i?.fondo.orEmpty(), carpeta = carpeta.ifBlank { i?.carpeta.orEmpty() }, extra = mapOf("runner" to runner, "app" to app))
        }
        // Epic (Legendary)
        val epic = infos(json(File(raiz, "store_cache/legendary_library.json")).comoMapa()["library"].comoLista())
        listOf(File(raiz, "legendaryConfig/legendary/installed.json"), File(raiz.parentFile, "legendary/installed.json")).firstOrNull { it.isFile }?.let { f ->
            json(f).comoMapa().forEach { (app, v) ->
                val g = v.comoMapa()
                if (g["is_dlc"].comoBool()) return@forEach
                mete("legendary", g["app_name"].comoTexto().ifBlank { app }, g["title"].comoTexto(), epic[app], g["install_path"].comoTexto())
            }
        }
        // GOG
        // (la caché nueva manda sobre la de antes)
        val gog = infos(json(File(raiz, "gog_store/library.json")).comoMapa()["games"].comoLista()) +
            infos(json(File(raiz, "store_cache/gog_library.json")).comoMapa()["games"].comoLista())
        json(File(raiz, "gog_store/installed.json")).comoMapa()["installed"].comoLista().map { it.comoMapa() }.forEach { g ->
            if (g["is_dlc"].comoBool()) return@forEach
            val app = g["appName"].comoTexto()
            mete("gog", app, "", gog[app], g["install_path"].comoTexto())
        }
        // Amazon (Nile)
        val nile = infos(json(File(raiz, "store_cache/nile_library.json")).comoMapa()["library"].comoLista())
        val nileTitulos = json(File(raiz, "nile_config/nile/library.json")).comoLista().map { it.comoMapa() }
            .associate { it["id"].comoTexto() to it["product"].comoMapa()["title"].comoTexto() }
        json(File(raiz, "nile_config/nile/installed.json")).comoLista().map { it.comoMapa() }.forEach { g ->
            val app = g["id"].comoTexto()
            mete("nile", app, nileTitulos[app].orEmpty(), nile[app], g["path"].comoTexto())
        }
        // Lo añadido a mano en Heroic
        infos(json(File(raiz, "sideload_apps/library.json")).comoMapa()["games"].comoLista()).forEach { (app, i) ->
            if (i.instalado) mete("sideload", app, i.titulo, i, i.carpeta)
        }
        return out
    }

    fun juegos(): List<JuegoPc> = raices().flatMap { juegos(it) }.distinctBy { it.clave }.sortedBy { it.nombre.lowercase() }

    /** La dirección con la que Heroic abre un juego. */
    fun url(runner: String, app: String) = "heroic://launch?appName=${URLEncoder.encode(app, "UTF-8").replace("+", "%20")}&runner=$runner"

    fun orden(runner: String, app: String): List<String> =
        if (Sitio.sistema == Sitio.Sistema.MAC) listOf("open", url(runner, app)) else listOf("xdg-open", url(runner, app))
}
