package com.gonzalo.ptah

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * «Los colores fijados»: hasta la 0.5.0 la app podía seguir en directo los colores de otra app; desde la 0.6.0 ya no se leen de fuera,
 * pero se guardan los últimos que se leyeron para que los widgets que los usaban se sigan viendo igual (se pueden quitar en Estilo).
 */
object TemaAtrio {
    private val COLUMNAS = listOf("paper", "crudo", "ink", "acento", "sand", "line", "edge", "muted", "oro")

    /** Los colores fijados (null: ninguno). */
    var tonos by mutableStateOf<Tones?>(null)

    /** Los tonos de Widgets a partir de los nueve colores que da Atrio (ARGB), en este orden: [COLUMNAS]. */
    fun tonosDe(v: List<Int>): Tones {
        fun l(i: Int) = v[i].toLong() and 0xFFFFFFFFL
        // criterios (gráficos, historia…): Widgets no los usa; se rellenan con tinta, gris y acento
        return Tones(paper = l(0), crudo = l(1), ink = l(2), general = l(3), gfx = l(2), story = l(7), music = l(7), gameplay = l(3), gold = l(8),
            sand = l(4), line = l(5), edge = l(6), muted = l(7))
    }

    private fun guardado(c: Context) = c.getSharedPreferences("ptah_tema", Context.MODE_PRIVATE)

    /** Los colores fijados (al arrancar). */
    fun cargarGuardado(c: Context) {
        val j = guardado(c).getString("atrio", null) ?: return
        runCatching { val o = JSONObject(j); tonos = tonosDe(COLUMNAS.map { o.getInt(it) }) }
    }
}
