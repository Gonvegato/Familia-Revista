package com.gonzalo.familia.pc.revista

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Los colores que tienes puestos en Azabache, leídos de la cuenta (`ajustes/azabache`: `at` y `skin`, en el formato de Azabache:
 * `theme31`, `skin_fondo`, `skin_accent_id`, `skin_fondo_propio`…). En Android las hermanas se los piden a Azabache en el móvil; en el
 * ordenador salen de la cuenta, con las mismas reglas: tema claro u oscuro, fondo de color, acento y colores a mano.
 */
object TemaAzabache {
    /** Los colores de Azabache ya resueltos (null: la cuenta aún no los tiene). */
    var tonos by mutableStateOf<Tones?>(null)

    /** Lo que guarda Azabache, tal cual (para «Copiar de Azabache»). */
    var skin: Map<String, Any?> = emptyMap(); private set

    private fun Map<String, Any?>.texto(k: String) = (this[k] as? String).orEmpty()
    private fun Map<String, Any?>.largo(k: String) = (this[k] as? Number)?.toLong() ?: 0L

    /** El modo de Azabache: 1 claro, 2 oscuro, otro: como el sistema. */
    fun oscuroDe(s: Map<String, Any?>, sistema: Boolean): Boolean {
        FONDOS.firstOrNull { it.id == s.texto("skin_fondo") }?.let { return it.oscuro }
        return when ((s["theme31"] as? Number)?.toInt() ?: 2) { 1 -> false; 2 -> true; else -> sistema }
    }

    /** Los tonos que se ven en Azabache con esa apariencia (el mismo cálculo que hacen Marfil, Hiedra y Cobalto al copiarla). */
    fun tonosDe(s: Map<String, Any?>, sistema: Boolean = Modo.oscuro): Tones {
        val fondo = FONDOS.firstOrNull { it.id == s.texto("skin_fondo") }
        val oscuro = oscuroDe(s, sistema)
        // Sin nada elegido en Apariencia de Jonsu (ni fondo, ni acento, ni colores a mano): los colores de la familia con su ciruela, como en Android
        if (s.texto("skin_fondo").isEmpty() && s.texto("skin_accent_id").ifBlank { "edicion" } == "edicion" &&
            s.largo("skin_fondo_propio") == 0L && s.largo("skin_tinta_propia") == 0L && s.largo("skin_acento_propio") == 0L)
            return paletaFamilia("azabache").let { if (oscuro) it.dark else it.light }
        val paleta = PALETTES.first { it.id == "revista" }
        val base = fondo?.tones ?: if (oscuro) paleta.dark else paleta.light
        val acento = s.texto("skin_accent_id").ifBlank { "edicion" }
        val conAcento = when {
            fondo != null && (acento == "edicion" || acento == "rojo") -> base
            else -> ACCENT_COLORS.firstOrNull { it.id == acento }?.let { x -> base.copy(general = (if (oscuro) x.dark else x.light) or 0xFF000000) } ?: base
        }
        return Skin.conPropios(conAcento, s.largo("skin_fondo_propio"), s.largo("skin_tinta_propia"), s.largo("skin_acento_propio"))
    }

    /** Llega la apariencia de Azabache de la cuenta. */
    fun pon(s: Map<String, Any?>) {
        skin = s
        tonos = if (s.isEmpty()) null else tonosDe(s)
    }
}
