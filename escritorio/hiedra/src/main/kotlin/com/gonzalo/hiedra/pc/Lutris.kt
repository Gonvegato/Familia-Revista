package com.gonzalo.hiedra.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.Sitio
import java.io.File

/**
 * Los juegos instalados en Lutris (solo en Linux). Se le piden a Lutris (`lutris -l -o -j`: la lista de lo instalado en JSON, con su id, su
 * nombre, su «runner», su carpeta y su portada), al de Flatpak si es ese el que hay. Los de Steam no: esos ya salen de Steam. Se abren con
 * `lutris lutris:rungameid/<id>`; sus claves son las de Atrio del PC: `lutris:<id>`.
 */
object Lutris {
    private const val FLATPAK = "net.lutris.Lutris"
    /** (En las pruebas: lo que «contesta» Lutris.) */
    @Volatile var salidaPruebas: String? = null

    /** El primer arreglo JSON de lo que escribe Lutris (a veces escribe avisos antes). */
    fun juegosDe(salida: String, conSteam: Boolean): List<JuegoPc> {
        val a = salida.indexOf('['); val b = salida.lastIndexOf(']')
        if (a < 0 || b <= a) return emptyList()
        val lista = runCatching { Json.lee(salida.substring(a, b + 1)) }.getOrNull().comoLista()
        return lista.map { it.comoMapa() }.mapNotNull { g ->
            val id = g["id"].comoTexto().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Los de Steam salen ya de Steam (con sus horas)
            if (conSteam && g["runner"].comoTexto().equals("steam", true)) return@mapNotNull null
            val nombre = g["name"].comoTexto().ifBlank { g["slug"].comoTexto() }.ifBlank { "Lutris $id" }
            val portada = g["coverPath"].comoTexto().takeIf { it.isNotBlank() && File(it).isFile }?.let { File(it).toURI().toString() }.orEmpty()
            JuegoPc("lutris:$id", nombre, portada, fuente = Fuente.LUTRIS, etiqueta = "Lutris", carpeta = g["directory"].comoTexto(),
                extra = mapOf("id" to id, "runner" to g["runner"].comoTexto(), "plataforma" to g["platform"].comoTexto()))
        }
    }

    /** ¿Cuál hay? («lutris», el de Flatpak o ninguno.) */
    private fun cual(): String? = when {
        Sitio.sistema != Sitio.Sistema.LINUX -> null
        Programas.enPath("lutris") -> "lutris"
        FLATPAK in Programas.flatpaks() -> "flatpak"
        else -> null
    }

    fun juegos(conSteam: Boolean): List<JuegoPc> {
        salidaPruebas?.let { return juegosDe(it, conSteam) }
        val orden = when (cual()) { "lutris" -> listOf("lutris", "-l", "-o", "-j"); "flatpak" -> listOf("flatpak", "run", FLATPAK, "-l", "-o", "-j"); else -> return emptyList() }
        return juegosDe(Programas.salida(orden, 40) ?: return emptyList(), conSteam).map { if (cual() == "flatpak") it.con(mapOf("flatpak" to "1")) else it }
    }

    fun orden(id: String, flatpak: Boolean): List<String> =
        if (flatpak) listOf("flatpak", "run", FLATPAK, "lutris:rungameid/$id") else listOf("lutris", "lutris:rungameid/$id")
}
