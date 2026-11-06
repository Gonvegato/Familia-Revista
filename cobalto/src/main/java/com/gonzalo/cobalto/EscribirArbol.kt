package com.gonzalo.cobalto

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Escribir: el árbol de relaciones. Las fichas en óvalos sobre un lienzo libre, unidas por líneas con la palabra que tú
 * quieras en medio (enemigos, amantes, hogar, pertenencia…). Pulsar una ficha la abre; pulsar la palabra de una unión
 * enseña lo que escribiste de ella. Las fichas se colocan arrastrando (la posición viaja con la cuenta: ax, ay) y «Ordenar»
 * las reparte solas. Igual que el PC (escribir-arbol.js).
 *
 * Aquí, solo las medidas y la colocación (sin nada de Android): es el MISMO archivo que compila Cobalto para Linux y Mac. El lienzo
 * de cada uno está en EscribirArbolUi.kt (móvil) y en escritorio/cobalto/…/pc/Arbol.kt (ordenador).
 */

object ArbolEscribir {
    /** Tamaño de cada ficha (semiejes y letra, en dp): el que elijas o, si no, según el tipo. */
    val TAM = mapOf(1 to Triple(60f, 31f, 14f), 2 to Triple(80f, 41f, 16.5f), 3 to Triple(104f, 55f, 20f))
    /** Letras por línea del nombre, según el tamaño (como el PC). */
    fun letras(t: Int) = when (t) { 1 -> 11; 2 -> 13; else -> 14 }
    /** El nombre en una o dos líneas, cortando por palabras, para que quepa en el óvalo (igual que el PC). */
    fun lineasNombre(nombre: String, max: Int): List<String> {
        val out = mutableListOf("")
        for (w in nombre.ifBlank { "Sin nombre" }.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val cur = out.last()
            when {
                cur.isEmpty() -> out[out.lastIndex] = w
                (cur + " " + w).length <= max -> out[out.lastIndex] = "$cur $w"
                else -> out += w
            }
        }
        if (out.size > 2) { while (out.size > 2) out.removeAt(out.lastIndex); out[1] = out[1].take(max - 1) + "…" }
        return out.map { if (it.length > max + 2) it.take(max + 1) + "…" else it }
    }
    fun tam(f: Doc): Int = f.i("tam").takeIf { it in 1..3 } ?: when (f.s("tipo")) { "lugar", "mundo" -> 3; "personaje" -> 2; else -> 1 }

    /** Dónde va cada ficha: la suya o, si aún no tiene, en círculo (sin guardar hasta que la muevas u ordenes). */
    fun posiciones(fs: List<Doc>): Map<String, Offset> {
        fun tiene(f: Doc) = f.o.has("ax") && f.o.opt("ax")?.toString()?.toDoubleOrNull() != null
        val sin = fs.filter { !tiene(it) }
        val p = LinkedHashMap<String, Offset>()
        fs.filter { tiene(it) }.forEach { p[it.id] = Offset(it.d("ax").toFloat(), it.d("ay").toFloat()) }
        val r = 140f + sin.size * 28f
        sin.forEachIndexed { i, f -> val t = (i.toDouble() / max(1, sin.size)) * PI * 2 - PI / 2; p[f.id] = Offset((r * cos(t)).roundToInt().toFloat(), (r * sin(t) * 0.7).roundToInt().toFloat()) }
        return p
    }

    /** «Ordenar»: muelles entre lo unido y repulsión entre todo, centrado en el origen (como el PC). */
    fun ordena(ids: List<String>, rels: List<Pair<String, String>>, ini: Map<String, Offset>): Map<String, Offset> {
        val px = DoubleArray(ids.size) { (ini[ids[it]]?.x ?: 0f).toDouble() }
        val py = DoubleArray(ids.size) { (ini[ids[it]]?.y ?: 0f).toDouble() }
        val idx = ids.withIndex().associate { it.value to it.index }
        val k = 270.0
        for (it in 0 until 400) {
            val t = 30 * (1 - it / 400.0) + 1
            val dx = DoubleArray(ids.size); val dy = DoubleArray(ids.size)
            for (i in ids.indices) for (j in i + 1 until ids.size) {
                var x = px[i] - px[j]; var y = py[i] - py[j]; var d = hypot(x, y)
                if (d < 0.01) { x = (i - j) * 0.1 + 0.05; y = 0.07; d = 0.1 }
                val f = k * k / d
                dx[i] += x / d * f; dy[i] += y / d * f; dx[j] -= x / d * f; dy[j] -= y / d * f
            }
            for ((a, b) in rels) {
                val i = idx[a] ?: continue; val j = idx[b] ?: continue
                val x = px[i] - px[j]; val y = py[i] - py[j]; val d = max(0.01, hypot(x, y))
                val f = d * d / k
                dx[i] -= x / d * f; dy[i] -= y / d * f; dx[j] += x / d * f; dy[j] += y / d * f
            }
            for (i in ids.indices) {
                dx[i] -= px[i] * 0.02 * k / 10; dy[i] -= py[i] * 0.03 * k / 10
                val m = max(0.01, hypot(dx[i], dy[i]))
                px[i] += dx[i] / m * min(m, t * 3); py[i] += dy[i] / m * min(m, t * 3)
            }
        }
        val cx = px.average(); val cy = py.average()
        return ids.withIndex().associate { (i, id) -> id to Offset((px[i] - cx).roundToInt().toFloat(), (py[i] - cy).roundToInt().toFloat()) }
    }

    /** ¿El punto [m] (del mundo) está dentro del óvalo de una ficha de tamaño [t] con centro [c]? */
    fun dentro(m: Offset, c: Offset, t: Int): Boolean { val (rx, ry, _) = TAM.getValue(t); val dx = (m.x - c.x) / rx; val dy = (m.y - c.y) / ry; return dx * dx + dy * dy <= 1f }

    /**
     * La rueda del ratón o el trackpad (en el ordenador): true si acerca o aleja (la rueda de un ratón, o Ctrl/⌘ con el trackpad), false si
     * mueve el lienzo (dos dedos en el trackpad: llega a trocitos, a veces de lado). Como Atrio.
     */
    fun ruedaAcerca(dx: Float, dy: Float, conTecla: Boolean): Boolean = conTecla || (dx == 0f && dy != 0f && abs(dy) >= 1f && abs(dy - dy.roundToInt()) < 0.001f)
}
