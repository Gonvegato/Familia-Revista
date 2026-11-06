package com.gonzalo.azabache.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto

/*
 * Las maquetas de las fichas (lo que va en cada casilla de cada página), las mismas que diseñas en el Azabache del móvil con «Editar»:
 * viajan en la cuenta (`azabache/maquetas~<id>`, con `o` = {id, k, j, updated}: `k` = la clave de la página —`v:<categoría>:<ficha>` la de lo
 * terminado, `p:…` la de lo que llevas entre manos, `tpl:v:<categoría>` / `tpl:p:<categoría>` la de toda la categoría—, `j` = la maqueta,
 * el mismo JSON que guarda el móvil, e `id` = el FNV-1a de 53 bits de la clave, como `nubeId` del móvil). Desde la 1.2.0 se diseñan
 * también aquí («Diseñar»): se escriben igual que las escribe el móvil (Maqueta.kt y Store.saveMaqueta de Android).
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

    /** La maqueta como la escribe el móvil (`MagLayouts.toJson`: las mismas claves, en el mismo orden, y los decimales del Float). */
    fun aJson(filas: List<FilaMaqueta>): String = Json.escribe(filas.map { r ->
        linkedMapOf("s" to r.split.toDouble(), "a" to r.alto.toDouble(), "c" to r.casillas.map { c ->
            linkedMapOf("t" to c.tipo, "h" to c.titulo, "x" to c.texto, "i" to c.imagen, "f" to c.entera, "z" to c.zoom.toDouble(),
                "ox" to c.ox.toDouble(), "oy" to c.oy.toDouble(), "sh" to c.forma, "as" to c.proporcion.toDouble())
        })
    })

    /** El número de una maqueta en la cuenta: el FNV-1a de 64 bits de su clave, en 53 bits (el `nubeId` del móvil). */
    fun idDe(clave: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (b in clave.toByteArray()) { h = h xor (b.toLong() and 0xff); h *= 0x100000001b3L }
        return (h and 0x1FFFFFFFFFFFFFL).coerceAtLeast(1L)
    }

    /** Las claves que guarda el móvil: `v:0:12`, `p:1:7`, `tpl:v:2`… */
    fun claveValida(k: String): Boolean = Regex("""^(tpl:)?[pv]:\d+(:\d+)?$""").matches(k)

    fun clave(kind: Int, id: Long, enCurso: Boolean) = "${if (enCurso) "p" else "v"}:$kind:$id"
    fun plantilla(kind: Int, enCurso: Boolean) = "tpl:${if (enCurso) "p" else "v"}:$kind"

    private fun todas(): Map<String, String> = Cuenta.lista("maquetas").associate { it["k"].comoTexto() to it["j"].comoTexto() }

    /** La maqueta de una página: la suya; si no, la de su categoría; si no, null (la de siempre). [enCurso]: la página de lo que llevas entre manos. */
    fun de(kind: Int, id: Long, enCurso: Boolean): List<FilaMaqueta>? {
        val t = todas()
        return t[clave(kind, id, enCurso)]?.let { lee(it) } ?: t[plantilla(kind, enCurso)]?.let { lee(it) }
    }

    /** ¿Tiene esta página una maqueta suya (no la de la categoría)? */
    fun tienePropia(kind: Int, id: Long, enCurso: Boolean): Boolean = todas()[clave(kind, id, enCurso)]?.let { lee(it) } != null
    fun hayPlantilla(kind: Int, enCurso: Boolean): Boolean = todas()[plantilla(kind, enCurso)]?.let { lee(it) } != null

    /**
     * La que se ve: la guardada con lo que hace el móvil al leerla (la portada va pequeña, en la primera fila junto a otra casilla:
     * nunca una fila entera para ella sola, `MagLayouts.portadaPequena`), o la de siempre.
     */
    fun paraVer(k: Kind, id: Long, enCurso: Boolean): List<FilaMaqueta> =
        de(k.id, id, enCurso)?.let { if (k.isGame || k.isBook) portadaPequena(it) else it } ?: deSiempre(k, enCurso)

    /** La portada, pequeña y en la primera fila junto a otra casilla (la misma regla que en el móvil). */
    fun portadaPequena(filas: List<FilaMaqueta>): List<FilaMaqueta> {
        if (filas.isEmpty()) return filas
        val out = filas.toMutableList()
        val sola = out.indexOfFirst { r -> r.casillas.size == 1 && r.casillas[0].tipo == "portada" }
        if (sola >= 0) out.removeAt(sola)
        if (out.none { r -> r.casillas.any { it.tipo == "portada" } }) {
            val i = out.indexOfFirst { it.casillas.size == 1 }
            if (i >= 0) out[i] = out[i].copy(casillas = out[i].casillas + CasillaMaqueta("portada")) else out.add(0, FilaMaqueta(listOf(CasillaMaqueta("portada"))))
        }
        return out
    }

    /** Guarda la maqueta de una página en la cuenta (la ven tus otros dispositivos al momento). Bloquea poco: escribe en la caché. */
    fun guarda(clave: String, filas: List<FilaMaqueta>) { if (claveValida(clave)) Cuenta.guardaMaqueta(clave, aJson(filas)) }

    /** La usa toda la categoría: sin los textos ni las imágenes de esta ficha (solo dónde va cada cosa), como en el móvil. */
    fun guardaPlantilla(plantilla: String, filas: List<FilaMaqueta>) {
        guarda(plantilla, filas.map { r -> r.copy(casillas = r.casillas.map { c -> if (c.tipo == "texto" || c.tipo == "imagen") CasillaMaqueta("vacio") else c }) })
    }

    /** «Restablecer el diseño de siempre»: se quitan la de la ficha y la de la categoría (como en el móvil: en los demás, también). */
    fun restablece(clave: String, plantilla: String) = Cuenta.borraMaquetas(listOf(clave, plantilla))

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

    /** Lo que puede ir en una casilla de una página (las casillas del móvil, en su orden). [aqui]: se pinta en el ordenador (si no, solo en el móvil). */
    class Modulo(val tipo: String, val nombre: String, val aqui: Boolean = true)

    fun modulos(k: Kind, enCurso: Boolean): List<Modulo> {
        val cine = k.isMovie || k.isTv
        return if (enCurso) listOfNotNull(
            if (cine) Modulo("critica", "Crítica", false) else null,
            if (cine) Modulo("donde", "Dónde verla", false) else null,
            if (k.isBook) Modulo("lectura", "Lectura") else null,
            if (k.isGame || k.isBook) Modulo("portada", "Portada") else null,
            Modulo("estado", "Estado y nota"),
            if (k.isGame) Modulo("jugar", "Jugar") else null,
            if (cine) Modulo("progreso", "Progreso") else null,
            Modulo("apuntes", "Apuntes"),
            if (k.isGame) Modulo("horas", "Horas") else null,
            Modulo("sesiones", "Sesiones"),
            Modulo("diario", "En tu diario"),
            Modulo("enlaces", "Enlaces"),
        ) else listOfNotNull(
            if (k.isGame || k.isBook) Modulo("portada", "Portada") else null,
            Modulo("nota", "Nota"),
            if (cine) Modulo("critica", "Crítica", false) else null,
            if (cine) Modulo("donde", "Dónde verla", false) else null,
            if (k.isBook) Modulo("lectura", "Lectura") else null,
            Modulo("opinion", "Opinión"),
            if (k.isGame) Modulo("horas", "Horas") else null,
            Modulo("sesiones", "Sesiones"),
            Modulo("diario", "En tu diario"),
            Modulo("enlaces", "Enlaces"),
            Modulo("datos", "Datos"),
        )
    }

    /** El nombre de lo que va en una casilla (para el editor). */
    fun nombreDe(tipo: String, k: Kind, enCurso: Boolean): String = when (tipo) {
        "texto" -> "Texto libre"; "imagen" -> "Imagen"; "vacio" -> "Vacía"
        else -> (modulos(k, enCurso) + modulos(k, !enCurso)).firstOrNull { it.tipo == tipo }?.nombre ?: tipo.replaceFirstChar { it.uppercase() }
    }

    // ---------------------------------------------------------------- editar (lo que hace cada herramienta del editor; puro)

    fun sube(f: List<FilaMaqueta>, i: Int) = if (i <= 0 || i >= f.size) f else f.toMutableList().also { val t = it[i]; it[i] = it[i - 1]; it[i - 1] = t }
    fun baja(f: List<FilaMaqueta>, i: Int) = if (i < 0 || i >= f.size - 1) f else f.toMutableList().also { val t = it[i]; it[i] = it[i + 1]; it[i + 1] = t }
    fun divide(f: List<FilaMaqueta>, i: Int) = f.mapIndexed { k, x -> if (k == i && x.casillas.size == 1) x.copy(casillas = x.casillas + CasillaMaqueta("vacio"), split = 0.5f) else x }
    /** Unir: se queda la primera casilla que no esté vacía. */
    fun une(f: List<FilaMaqueta>, i: Int) = f.mapIndexed { k, x -> if (k == i && x.casillas.size == 2) x.copy(casillas = listOf(x.casillas.firstOrNull { it.tipo != "vacio" } ?: x.casillas[0])) else x }
    fun quita(f: List<FilaMaqueta>, i: Int) = f.filterIndexed { k, _ -> k != i }
    fun anade(f: List<FilaMaqueta>) = f + FilaMaqueta(listOf(CasillaMaqueta("vacio")))
    fun ponCasilla(f: List<FilaMaqueta>, i: Int, j: Int, c: CasillaMaqueta) = f.mapIndexed { k, r -> if (k != i) r else r.copy(casillas = r.casillas.mapIndexed { n, x -> if (n == j) c else x }) }
    /** La línea de en medio, entre el 30 y el 70 % (como en el móvil). */
    fun ponSplit(f: List<FilaMaqueta>, i: Int, s: Float) = f.mapIndexed { k, x -> if (k == i) x.copy(split = s.coerceIn(0.3f, 0.7f)) else x }
    /** El alto fijo de una fila: entre 56 y 1400 dp; 0, el que pida. */
    fun ponAlto(f: List<FilaMaqueta>, i: Int, a: Float) = f.mapIndexed { k, x -> if (k == i) x.copy(alto = if (a <= 0f) 0f else a.coerceIn(56f, 1400f)) else x }
}
