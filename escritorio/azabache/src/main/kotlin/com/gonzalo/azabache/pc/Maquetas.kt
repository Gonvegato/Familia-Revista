package com.gonzalo.azabache.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto

/*
 * Las maquetas de las fichas (lo que va en cada casilla de cada página), las que diseñas en el Azabache del móvil con «Editar»: viajan en
 * la cuenta (`azabache/maquetas~<id>`, con `k` = la clave de la página —`v:<categoría>:<ficha>` la de lo terminado, `p:…` la de lo que llevas
 * entre manos, `tpl:v:<categoría>` la de toda la categoría— y `j` = la maqueta tal cual, el mismo JSON que guarda el móvil). Aquí se leen
 * y la página sale igual que en el móvil; se cambian allí.
 */

/** Una casilla: qué enseña (`nota`, `opinion`, `horas`…, o `texto` / `imagen` / `vacio`) y lo suyo. */
data class CasillaMaqueta(
    val tipo: String, val titulo: String = "", val texto: String = "", val imagen: String = "", val entera: Boolean = false,
    val zoom: Float = 1f, val ox: Float = 0f, val oy: Float = 0f, val forma: String = "", val proporcion: Float = 0f
)

/** Una fila: una o dos casillas; [split], lo que ocupa la de la izquierda (0–1); [alto], el alto fijo en dp (0: el que pida). */
data class FilaMaqueta(val casillas: List<CasillaMaqueta>, val split: Float = 0.5f, val alto: Float = 0f)

object Maquetas {
    /** Lee una maqueta (el JSON del móvil); null si no se entiende. */
    fun lee(j: String): List<FilaMaqueta>? = runCatching {
        Json.lee(j).comoLista().map { r ->
            val o = r.comoMapa()
            FilaMaqueta(o["c"].comoLista().map { c ->
                val m = c.comoMapa()
                CasillaMaqueta(m["t"].comoTexto().ifBlank { "vacio" }, m["h"].comoTexto(), m["x"].comoTexto(), m["i"].comoTexto(), m["f"] == true,
                    (m["z"] ?: 1.0).comoDouble().toFloat(), m["ox"].comoDouble().toFloat(), m["oy"].comoDouble().toFloat(), m["sh"].comoTexto(),
                    m["as"].comoDouble().toFloat())
            }.take(2), (o["s"] ?: 0.5).comoDouble().toFloat(), o["a"].comoDouble().toFloat())
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** La maqueta de una página: la suya; si no, la de su categoría; si no, null (la de siempre). [enCurso]: la página de lo que llevas entre manos. */
    fun de(kind: Int, id: Long, enCurso: Boolean): List<FilaMaqueta>? {
        val pre = if (enCurso) "p" else "v"
        val todas = Cuenta.lista("maquetas").associate { it["k"].comoTexto() to it["j"].comoTexto() }
        return todas["$pre:$kind:$id"]?.let { lee(it) } ?: todas["tpl:$pre:$kind"]?.let { lee(it) }
    }

    /** Las de siempre (las mismas que en el móvil). */
    fun deSiempre(k: Kind, enCurso: Boolean): List<FilaMaqueta> {
        fun filas(vararg l: List<String>) = l.map { r -> FilaMaqueta(r.map { CasillaMaqueta(it) }) }
        return if (enCurso) when {
            k.isGame -> filas(listOf("estado", "portada"), listOf("jugar"), listOf("horas", "sesiones"), listOf("apuntes"), listOf("diario"), listOf("enlaces"))
            k.isMovie || k.isTv -> filas(listOf("critica", "estado"), listOf("donde"), listOf("progreso"), listOf("apuntes"), listOf("diario"), listOf("enlaces"))
            k.isBook -> filas(listOf("estado", "portada"), listOf("lectura", "sesiones"), listOf("apuntes"), listOf("diario"), listOf("enlaces"))
            else -> filas(listOf("estado"), listOf("apuntes"), listOf("diario"), listOf("enlaces"))
        } else when {
            k.isGame -> filas(listOf("nota", "portada"), listOf("opinion"), listOf("horas", "sesiones"), listOf("diario"), listOf("datos", "enlaces"))
            k.isMovie || k.isTv -> filas(listOf("critica", "nota"), listOf("opinion"), listOf("diario"), listOf("datos", "enlaces"), listOf("donde"))
            k.isBook -> filas(listOf("nota", "portada"), listOf("opinion"), listOf("lectura", "sesiones"), listOf("diario"), listOf("datos", "enlaces"))
            else -> filas(listOf("datos", "nota"), listOf("opinion"), listOf("diario"), listOf("enlaces"))
        }
    }
}
