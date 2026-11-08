package com.gonzalo.ptah

import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

/*
 * Un diseño de widget: su tipo (un reloj, el tiempo, una foto…: [Tipos]), lo propio de ese tipo ([Diseno.opciones]) y su estilo
 * ([Estilo]: el fondo, los colores, el borde, las esquinas, las letras…). Es lo que viaja con la cuenta (`ajustes/ptah_disenos`, un
 * campo por diseño con este JSON: [Disenos]). Nada secreto ni nada del móvil: ni la foto (se queda en el móvil, [Fotos]), ni la
 * ubicación (si la eliges, se mira en cada móvil), ni qué widget de la pantalla de inicio lleva cada diseño.
 */

/**
 * Un color de un diseño, como texto:
 *  - `@papel`, `@tinta`, `@acento`, `@gris`: el de tu Apariencia (cambia con ella y, si la tienes «como el móvil», con el claro y el oscuro);
 *  - `#RRGGBB` o `#AARRGGBB`: uno fijo;
 *  - `@transparente`;
 *  - `""`: el de por defecto de esa pieza (lo dice cada tipo).
 */
object Col {
    const val PAPEL = "@papel"
    const val TINTA = "@tinta"
    const val ACENTO = "@acento"
    const val GRIS = "@gris"
    const val TRANSPARENTE = "@transparente"
    val ROLES = listOf(PAPEL to "Papel", TINTA to "Tinta", ACENTO to "Acento", GRIS to "Gris")

    /** El color que es [spec] con estos [tonos]; si [spec] es "" o no se entiende, [porDefecto]. */
    fun resuelve(spec: String, tonos: Tones, porDefecto: Int): Int = when {
        spec.isBlank() -> porDefecto
        spec == PAPEL -> tonos.paper.toInt()
        spec == TINTA -> tonos.ink.toInt()
        spec == ACENTO -> tonos.general.toInt()
        spec == GRIS -> tonos.cMuted.toArgb()
        spec == TRANSPARENTE -> 0
        spec.startsWith("#") -> argb(spec) ?: porDefecto
        else -> porDefecto
    }

    /** «#RRGGBB» o «#AARRGGBB» → ARGB (null si no es un color). */
    fun argb(t: String): Int? {
        val h = t.trim().removePrefix("#")
        if (h.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
        return when (h.length) {
            6 -> (0xFF000000L or h.toLong(16)).toInt()
            8 -> h.toLong(16).toInt()
            3 -> (0xFF000000L or h.map { "$it$it" }.joinToString("").toLong(16)).toInt()
            else -> null
        }
    }

    /** ARGB → «#RRGGBB» (o «#AARRGGBB» si no es opaco). */
    fun hex(c: Int): String = if ((c ushr 24) == 0xFF) "#%06X".format(c and 0xFFFFFF) else "#%08X".format(c)

    /** ¿Depende de tu Apariencia (y por tanto del claro y el oscuro)? */
    fun esDelTema(spec: String) = spec in setOf(PAPEL, TINTA, ACENTO, GRIS)
}

enum class TipoFondo(val nombre: String) { PAPEL("Papel"), COLOR("Color"), DEGRADADO("Degradado"), TRANSPARENTE("Transparente") }

enum class Alineacion(val nombre: String) { IZQUIERDA("Izquierda"), CENTRO("Centro"), DERECHA("Derecha") }

/**
 * El estilo de un diseño. Los colores son textos de [Col]. [piezas]: el color de cada pieza por separado (las agujas, los números, las
 * rayas, el segundero…; las que tiene cada tipo las dice [TipoWidget.piezas]); la que no está, la de por defecto. [ocultas]: las partes
 * que no se ven ([TipoWidget.partes]).
 */
data class Estilo(
    val fondo: TipoFondo = TipoFondo.PAPEL,
    val colorFondo: String = Col.PAPEL,
    val colorFondo2: String = Col.ACENTO,
    /** El degradado: 0 de arriba abajo, 90 de izquierda a derecha, 45 en diagonal… */
    val angulo: Int = 0,
    /** Lo que se ve del fondo (0 a 100: con 0, solo lo de encima, como sin fondo). */
    val opacidad: Int = 100,
    val tinta: String = Col.TINTA,
    val acento: String = Col.ACENTO,
    val secundario: String = Col.GRIS,
    val piezas: Map<String, String> = emptyMap(),
    val borde: Boolean = true,
    val grosorBorde: Float = 1.1f,
    val colorBorde: String = "",
    /** El radio de las esquinas, en dp. */
    val radio: Float = 22f,
    /** El marco interior de revista: un filete fino un poco por dentro del borde. */
    val marco: Boolean = false,
    /** "" = la de tu Apariencia; si no, «Atrio», «Vitrina» o una de Google Fonts. */
    val letraTitulos: String = "",
    val letraTexto: String = "",
    /** El tamaño del texto, relativo (1 = el de serie). */
    val escala: Float = 1f,
    val alineacion: Alineacion = Alineacion.IZQUIERDA,
    val ocultas: Set<String> = emptySet(),
) {
    fun pieza(id: String): String = piezas[id].orEmpty()
    fun ve(parte: String) = parte !in ocultas
    /** ¿Algún color sale de tu Apariencia? (entonces el widget cambia con el claro y el oscuro) */
    val delTema: Boolean get() {
        if (fondo == TipoFondo.PAPEL) return true
        val fondos = listOfNotNull(colorFondo.takeIf { fondo != TipoFondo.TRANSPARENTE }, colorFondo2.takeIf { fondo == TipoFondo.DEGRADADO })
        // (una pieza o el borde sin color propio toman el de la tinta o el acento: cuentan si esos son del tema)
        return (fondos + piezas.values + colorBorde).any { Col.esDelTema(it) } || listOf(tinta, acento, secundario).any { it.isBlank() || Col.esDelTema(it) }
    }

    fun aJson(): JSONObject = JSONObject()
        .put("fondo", fondo.name).put("cf", colorFondo).put("cf2", colorFondo2).put("ang", angulo).put("op", opacidad)
        .put("tinta", tinta).put("acento", acento).put("sec", secundario)
        .put("piezas", JSONObject().also { o -> piezas.toSortedMap().forEach { (k, v) -> o.put(k, v) } })
        .put("borde", borde).put("gb", grosorBorde.toDouble()).put("cb", colorBorde).put("radio", radio.toDouble()).put("marco", marco)
        .put("lt", letraTitulos).put("lx", letraTexto).put("esc", escala.toDouble()).put("al", alineacion.name)
        .put("ocultas", JSONArray(ocultas.sorted()))

    companion object {
        fun deJson(o: JSONObject?): Estilo {
            if (o == null) return Estilo()
            val d = Estilo()
            fun t(k: String, def: String) = if (o.has(k)) o.optString(k, def) else def
            return Estilo(
                fondo = TipoFondo.entries.firstOrNull { it.name == o.optString("fondo") } ?: d.fondo,
                colorFondo = t("cf", d.colorFondo), colorFondo2 = t("cf2", d.colorFondo2),
                angulo = o.optInt("ang", d.angulo), opacidad = o.optInt("op", d.opacidad).coerceIn(0, 100),
                tinta = t("tinta", d.tinta), acento = t("acento", d.acento), secundario = t("sec", d.secundario),
                piezas = o.optJSONObject("piezas")?.let { p -> p.keys().asSequence().associateWith { p.optString(it) } }.orEmpty(),
                borde = o.optBoolean("borde", d.borde), grosorBorde = o.optDouble("gb", d.grosorBorde.toDouble()).toFloat().coerceIn(0.3f, 8f),
                colorBorde = t("cb", d.colorBorde), radio = o.optDouble("radio", d.radio.toDouble()).toFloat().coerceIn(0f, 80f),
                marco = o.optBoolean("marco", d.marco), letraTitulos = t("lt", ""), letraTexto = t("lx", ""),
                escala = o.optDouble("esc", 1.0).toFloat().coerceIn(0.5f, 2f),
                alineacion = Alineacion.entries.firstOrNull { it.name == o.optString("al") } ?: d.alineacion,
                ocultas = o.optJSONArray("ocultas")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty(),
            )
        }
    }
}

/**
 * Un diseño guardado. [opciones]: lo propio de su tipo (la cabecera del reloj, la ciudad del tiempo, las apps de los accesos…), valores
 * sueltos (sí/no, números, textos) o listas de textos.
 */
data class Diseno(
    val id: String,
    val nombre: String,
    val tipo: String,
    val estilo: Estilo = Estilo(),
    val opciones: Map<String, Any?> = emptyMap(),
    /** Cuándo se cambió (ms). */
    val cambiado: Long = 0L,
    /** Cuándo se creó (ms): el orden de «Tus widgets». */
    val creado: Long = 0L,
) {
    fun texto(k: String, def: String = ""): String = (opciones[k] as? String) ?: opciones[k]?.toString() ?: def
    fun entero(k: String, def: Int = 0): Int = when (val v = opciones[k]) { is Number -> v.toInt(); is String -> v.toIntOrNull() ?: def; else -> def }
    fun largo(k: String, def: Long = 0L): Long = when (val v = opciones[k]) { is Number -> v.toLong(); is String -> v.toLongOrNull() ?: def; else -> def }
    fun decimal(k: String, def: Double = 0.0): Double = when (val v = opciones[k]) { is Number -> v.toDouble(); is String -> v.toDoubleOrNull() ?: def; else -> def }
    fun si(k: String, def: Boolean = false): Boolean = when (val v = opciones[k]) { is Boolean -> v; is String -> v == "true"; else -> def }
    @Suppress("UNCHECKED_CAST")
    fun lista(k: String): List<String> = (opciones[k] as? List<*>)?.map { it.toString() }.orEmpty()

    fun con(k: String, v: Any?): Diseno = copy(opciones = opciones + (k to v))
    fun conEstilo(e: Estilo): Diseno = copy(estilo = e)

    /** El JSON que va a la cuenta. */
    fun aJson(): String = JSONObject().put("v", 1).put("id", id).put("n", nombre).put("tipo", tipo).put("estilo", estilo.aJson())
        .put("op", JSONObject().also { o -> opciones.toSortedMap().forEach { (k, v) -> o.put(k, aJsonValor(v)) } })
        .put("c", cambiado).put("cr", creado).toString()

    companion object {
        /** Un id nuevo: «d» y 10 cifras hexadecimales al azar. */
        fun nuevoId(): String = "d" + (1..10).map { "0123456789abcdef"[java.util.concurrent.ThreadLocalRandom.current().nextInt(16)] }.joinToString("")

        /** Lee un diseño; null si no es uno (un texto roto no tumba la lista). */
        fun deJson(s: String?): Diseno? {
            if (s.isNullOrBlank()) return null
            val o = runCatching { JSONObject(s) }.getOrNull() ?: return null
            val id = o.optString("id"); val tipo = o.optString("tipo")
            if (id.isBlank() || tipo.isBlank()) return null
            val op = o.optJSONObject("op")
            return Diseno(id, o.optString("n"), tipo, Estilo.deJson(o.optJSONObject("estilo")),
                op?.keys()?.asSequence()?.associateWith { k -> deJsonValor(op.opt(k)) }.orEmpty(), o.optLong("c"), o.optLong("cr"))
        }

        private fun aJsonValor(v: Any?): Any = when (v) {
            null -> JSONObject.NULL
            is List<*> -> JSONArray(v.map { it?.toString() })
            is Float -> v.toDouble()
            else -> v
        }

        private fun deJsonValor(v: Any?): Any? = when (v) {
            JSONObject.NULL, null -> null
            is JSONArray -> (0 until v.length()).map { v.optString(it) }
            else -> v
        }
    }
}

/**
 * El tamaño de un widget. Android (12 o más, en vertical) da 57 × 102 dp a 1×1, 130 × 102 a 2×1, 276 × 220 a 4×2 y 276 × 454 a 4×4 (developer.android.com, «Determine a size for your widget»). En apaisado las celdas son más anchas y más bajas.
 */
object Tamanos {
    data class Celdas(val col: Int, val fil: Int) { override fun toString() = "$col×$fil" }

    val PRESETS = listOf(Celdas(1, 1), Celdas(2, 1), Celdas(2, 2), Celdas(3, 1), Celdas(3, 2), Celdas(4, 1), Celdas(4, 2), Celdas(4, 3), Celdas(4, 4), Celdas(5, 2))

    // Los de la tabla de Android (en vertical y en apaisado); de 4 en adelante, cada celda más lo mismo
    private val ANCHO_V = listOf(57f, 130f, 203f, 276f); private val ALTO_V = listOf(102f, 220f, 337f, 454f)
    private val ANCHO_H = listOf(127f, 269f, 412f, 554f); private val ALTO_H = listOf(51f, 117f, 184f, 250f)
    private fun de(t: List<Float>, n: Int) = if (n <= t.size) t[n - 1] else t.last() + (t.last() - t[t.size - 2]) * (n - t.size)

    /** Las celdas → dp. */
    fun dp(c: Celdas, apaisado: Boolean = false): Pair<Float, Float> =
        if (!apaisado) de(ANCHO_V, c.col) to de(ALTO_V, c.fil) else de(ANCHO_H, c.col) to de(ALTO_H, c.fil)

    /** Los dp → las celdas que ocupa (las que caben enteras, al menos 1). */
    fun celdas(w: Float, h: Float): Celdas =
        Celdas((1..8).lastOrNull { de(ANCHO_V, it) <= w + 0.5f } ?: 1, (1..8).lastOrNull { de(ALTO_V, it) <= h + 0.5f } ?: 1)

    /** Lo más pequeño que se deja (y lo más grande). */
    const val MIN = 40f
    const val MAX = 600f
    fun acota(w: Float, h: Float) = w.coerceIn(MIN, MAX) to h.coerceIn(MIN, MAX)

    /** Lo que se usa al dibujar un tamaño: «pequeño» (1×1 y así), «bajo» (una fila), «estrecho» (una columna)… */
    fun pequeno(w: Float, h: Float) = w < 120f && h < 120f
    fun bajo(h: Float) = h < 90f
    fun estrecho(w: Float) = w < 100f
}
