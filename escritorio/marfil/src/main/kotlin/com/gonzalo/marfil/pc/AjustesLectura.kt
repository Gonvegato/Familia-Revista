package com.gonzalo.marfil.pc

import androidx.compose.ui.graphics.Color
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.LaFamilia

/**
 * Los ajustes del lector: los mismos que el del móvil (el objeto `settings` de lector.js, con sus nombres y sus valores), así que lo que
 * eliges en el móvil se ve aquí y al revés:
 *  - los de cada libro, en `lectura/<kid>` (`settings` y `settingsAt`), que mandan;
 *  - los de partida para todos (lo último que elegiste en cualquier dispositivo), en `ajustes/marfil` → `lector` (`{"at": ms, "s": {…}}`).
 * Lo que es de cada dispositivo (el brillo y el formato de los PDF) no viaja. Lo que el ordenador no usa (los botones de volumen,
 * la pantalla encendida…) se conserva tal cual.
 */
class AjustesLectura(val m: Map<String, Any?>) {
    private fun texto(k: String, d: String) = (m[k] as? String)?.takeIf { it.isNotBlank() } ?: d
    private fun bool(k: String, d: Boolean) = if (m.containsKey(k) && m[k] != null) m[k].comoBool() else d
    private fun num(k: String, d: Double) = if (m[k] is Number || (m[k] as? String)?.toDoubleOrNull() != null) m[k].comoDouble() else d

    /** papel, sepia, gris, noche, negro o app («Como la app»). */
    val tema: String get() = texto("theme", "app").let { if (it in TEMAS.map { t -> t.id }) it else "papel" }
    /** El tamaño de la letra (60–240 %). */
    val tamano: Int get() = num("size", 100.0).toInt().coerceIn(60, 240)
    /** original, literata, garamond, atrio, serif, sans, facil o mono. */
    val letra: String get() = texto("font", "original").let { if (it in LETRAS.map { l -> l.first }) it else "original" }
    val interlineado: Double get() = (Math.round(num("spacing", 1.5) * 10) / 10.0).coerceIn(1.0, 2.4)
    /** s, m o l. */
    val margen: String get() = texto("margin", "m").let { if (it in setOf("s", "m", "l")) it else "m" }
    val justificado: Boolean get() = bool("justify", true)
    /** «paginated» (páginas) o «scrolled» (desplazamiento continuo). */
    val flujo: String get() = if (texto("flow", "paginated") == "scrolled") "scrolled" else "paginated"
    /** Los párrafos: 0 como el libro, 1 con un poco de aire, 2 separados y sin sangría. */
    val parrafos: Int get() = num("paraSpace", 0.0).toInt().coerceIn(0, 2)
    /** Dos columnas cuando la pantalla es ancha (en el ordenador: dos páginas, como un libro abierto). */
    val columnas: Boolean get() = bool("columns", false)
    val animado: Boolean get() = bool("animated", true)
    val guiones: Boolean get() = bool("hyphens", true)
    /** De 21:00 a 7:00, el tema Noche. */
    val nocheAuto: Boolean get() = bool("nightAuto", false)
    /** La luz cálida (0–1). */
    val calida: Double get() = num("warm", 0.0).coerceIn(0.0, 1.0)
    /** Un poco más gruesa (Literata y Garamond). */
    val gruesa: Boolean get() = num("weight", 0.0) > 0
    /** Tu velocidad de lectura (bytes de libro por minuto; 0 = aún no se sabe). La aprende el lector del móvil (y este). */
    val velocidad: Int get() = num("speed", 0.0).toInt().coerceAtLeast(0)

    /** Con un ajuste cambiado. */
    fun con(k: String, v: Any?): AjustesLectura = AjustesLectura(LinkedHashMap(m).also { it[k] = v })

    /** El tema que toca ahora (de noche, Noche si lo pediste). */
    fun temaAhora(hora: Int = java.time.LocalTime.now().hour): String =
        if (nocheAuto && (hora >= 21 || hora < 7) && tema !in OSCUROS) "noche" else tema

    override fun equals(other: Any?) = other is AjustesLectura && other.m == m
    override fun hashCode() = m.hashCode()

    companion object {
        /** Los de lector.js cuando no hay nada elegido. */
        val DE_PARTIDA: Map<String, Any?> = linkedMapOf(
            "theme" to "app", "size" to 100L, "font" to "original", "spacing" to 1.5, "margin" to "m", "justify" to true, "flow" to "paginated",
            "hyphens" to true, "paraSpace" to 0L, "columns" to false, "animated" to true, "status" to true, "speed" to 0L, "nightAuto" to false, "warm" to 0.0,
        )
        /** Lo de cada dispositivo (no viaja con los de partida). */
        val PROPIOS = listOf("brightness", "pdfTexto", "pdfForzar")

        class Tema(val id: String, val nombre: String, val fondo: Long, val tinta: Long, val suave: Long, val superficie: Long, val oscuro: Boolean)
        /** Los temas del lector del móvil (lector.css), con sus colores. «app» toma los de la app. */
        val TEMAS = listOf(
            Tema("app", "Como la app", 0, 0, 0, 0, false),
            Tema("papel", "Papel", 0xFFFBF8F1, 0xFF1F1D1A, 0xFF857E72, 0xFFFAF6EE, false),
            Tema("sepia", "Sepia", 0xFFF1E6CF, 0xFF43341F, 0xFF8D7A5C, 0xFFF6ECD8, false),
            Tema("gris", "Gris", 0xFF2B2D31, 0xFFE3E1DC, 0xFF9A9893, 0xFF313338, true),
            Tema("noche", "Noche", 0xFF121317, 0xFFD9D6CF, 0xFF8E8A82, 0xFF17181D, true),
            Tema("negro", "Negro", 0xFF000000, 0xFFBEBBB4, 0xFF7D7A74, 0xFF0B0B0C, true),
        )
        val OSCUROS = setOf("gris", "noche", "negro")
        val LETRAS = listOf("original" to "Original", "literata" to "Literata", "garamond" to "Garamond", "atrio" to "Fraunces", "serif" to "Serif", "sans" to "Sans", "facil" to "Fácil lectura", "mono" to "Mono")

        /** Los colores de un tema: fondo, tinta, gris y superficie (los paneles). */
        fun colores(id: String, app: List<Color>): List<Color> {
            val t = TEMAS.firstOrNull { it.id == id } ?: TEMAS[1]
            return if (t.id == "app") app else listOf(Color(t.fondo), Color(t.tinta), Color(t.suave), Color(t.superficie))
        }

        /** Los ajustes de partida que viajan (`ajustes/marfil` → `lector`): su hora y sus valores (o null). */
        fun dePartidaEnLaCuenta(texto: String): Pair<Long, Map<String, Any?>>? = runCatching {
            val o = Json.lee(texto).comoMapa()
            val s = o["s"].comoMapa()
            if (s.isEmpty()) null else o["at"].comoLong() to s
        }.getOrNull()

        /** Lo que se usa al abrir un libro: lo de partida, encima lo que viaja para todos y encima lo del libro. */
        fun mezcla(dePartida: Map<String, Any?>?, delLibro: Map<String, Any?>?): AjustesLectura {
            val r = LinkedHashMap(DE_PARTIDA)
            dePartida?.forEach { (k, v) -> if (k !in PROPIOS && v != null) r[k] = v }
            delLibro?.forEach { (k, v) -> if (v != null) r[k] = v }
            return AjustesLectura(r)
        }

        /** Para `ajustes/marfil` → `lector`: los de partida de todos tus dispositivos, sin lo propio de este. */
        fun paraLaCuenta(a: AjustesLectura, at: Long): String = Json.escribe(linkedMapOf("at" to at, "s" to a.m.filterKeys { it !in PROPIOS }))

        // ---------------------------------------------------------------- en la cuenta

        /** Los de un libro (los suyos encima de los de partida). */
        fun de(kid: String): AjustesLectura {
            val partida = runCatching { LaFamilia.coleccion("ajustes").nodo("marfil")?.texto("lector") }.getOrNull()?.let { dePartidaEnLaCuenta(it)?.second }
            val libro = runCatching { Libros.lectura().nodo(kid)?.mapa("settings") }.getOrNull()?.takeIf { it.isNotEmpty() }
            return mezcla(partida, libro)
        }

        /** Has cambiado algo aquí: vale para este libro (en todos tus dispositivos) y como punto de partida para los demás. */
        fun guarda(kid: String, a: AjustesLectura) {
            val ahora = System.currentTimeMillis()
            runCatching { Libros.lectura().cambia(kid, linkedMapOf("ref" to kid, "settings" to a.m, "settingsAt" to ahora)) }
            runCatching { LaFamilia.coleccion("ajustes").cambia("marfil", mapOf("lector" to paraLaCuenta(a, ahora))) }
        }

        /** Un valor de los ajustes, como texto (para las pruebas). */
        fun valor(a: AjustesLectura, k: String): String = a.m[k].comoTexto()
    }
}
