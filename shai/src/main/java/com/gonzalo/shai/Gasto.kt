package com.gonzalo.shai

import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoLong
import java.time.YearMonth
import java.time.ZoneId

/**
 * Lo que cuesta cada respuesta, con los precios de la API (dólares por millón de tokens):
 *  - Haiku 5.5: 0,10 entrada / 0,50 salida con prompts de hasta 100.000 tokens; 0,50 / 2,50 si pasa de ahí.
 *  - Sonnet 5.5: 2 / 10.
 *  - La caché: escribirla cuesta 1,25 veces la entrada (5 minutos) o 2 veces (1 hora); leerla, 0,1 veces.
 *  - Los lotes (Batch), la mitad de todo.
 */
object Precios {
    class Tarifa(val entrada: Double, val salida: Double)

    fun tarifa(modelo: String, uso: Uso): Tarifa = when {
        modelo.startsWith("claude-haiku-5-5") -> if (uso.prompt > 100_000) Tarifa(0.50, 2.50) else Tarifa(0.10, 0.50)
        modelo.startsWith("claude-sonnet-5-5") -> Tarifa(2.0, 10.0)
        // (por si la API contesta con otro modelo: lo más caro de los dos, para no quedarse corto)
        else -> Tarifa(2.0, 10.0)
    }

    /** Dólares. */
    fun coste(modelo: String, uso: Uso, lote: Boolean = false, cacheHora: Boolean = false): Double {
        val t = tarifa(modelo, uso)
        val m = 1_000_000.0
        val escritura = if (cacheHora) 2.0 else 1.25
        val total = uso.entrada * t.entrada / m + uso.salida * t.salida / m + uso.cacheEscrita * t.entrada * escritura / m + uso.cacheLeida * t.entrada * 0.1 / m
        return if (lote) total / 2 else total
    }
}

/**
 * El gasto del mes y el tope. Va en tu cuenta (`shai/gasto`): un campo por mes, dispositivo y modelo (`<AAAAMM>_<dispositivo>_<h|s>`,
 * en millonésimas de dólar), para que dos móviles no se pisen: el del mes es la suma. El tope (`shai/ajustes`, `tope`, en dólares; de
 * serie 100, lo que da el plan Max al mes) para la app: al llegar a él, no se pide nada más hasta el mes siguiente.
 */
object Gasto {
    const val TOPE_SERIE = 100.0
    @Volatile var dispositivo: String = "movil"
    /** En las pruebas: otro reloj. */
    @Volatile var mesFijo: YearMonth? = null

    class Tope(val gastado: Double, val tope: Double) : Exception("Has llegado al tope de gasto de este mes (${Formato.dolares(gastado)} de ${Formato.dolares(tope)}). Súbelo en Ajustes → IA o espera al mes que viene.")

    fun mesActual(): YearMonth = mesFijo ?: YearMonth.now(ZoneId.systemDefault())
    private fun clave(mes: YearMonth) = "%04d%02d".format(mes.year, mes.monthValue)

    private fun nodo(): Map<String, Any?> = Carreras.almacen.nodo(NODO_GASTO).orEmpty()

    /** Lo gastado en un mes (dólares), y por modelo: `h` Haiku, `s` Sonnet. */
    fun mes(mes: YearMonth = mesActual(), modelo: String? = null): Double {
        val pre = clave(mes) + "_"
        return nodo().filterKeys { k -> k.startsWith(pre) && (modelo == null || k.endsWith("_$modelo")) }.values.sumOf { it.comoLong() } / 1_000_000.0
    }

    fun tope(): Double = Carreras.almacen.nodo(NODO_AJUSTES)?.get("tope")?.comoLong()?.takeIf { it > 0 }?.toDouble() ?: TOPE_SERIE
    fun ponTope(d: Double) = Carreras.almacen.pon(NODO_AJUSTES, mapOf("tope" to Math.round(d.coerceIn(1.0, 10_000.0))))

    /** ¿Eventos de entre partidos automáticos? (de serie, sí) */
    fun eventosSolos(): Boolean = Carreras.almacen.nodo(NODO_AJUSTES)?.let { n -> if (n.containsKey("auto")) n["auto"].comoBool() else true } ?: true
    fun ponEventosSolos(on: Boolean) = Carreras.almacen.pon(NODO_AJUSTES, mapOf("auto" to on))

    /** Antes de pedir: si con lo que podría costar ([maximo], dólares) se pasa del tope, no se pide. */
    fun compruebaAntes(maximo: Double) {
        val g = mes(); val t = tope()
        if (g >= t || g + maximo > t) throw Tope(g, t)
    }

    /** Apunta lo gastado. */
    @Synchronized
    fun apunta(modelo: String, dolares: Double) {
        if (dolares <= 0) return
        val k = clave(mesActual()) + "_" + dispositivo.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "movil" } + "_" + (if (modelo.contains("haiku")) "h" else "s")
        val antes = nodo()[k].comoLong()
        Carreras.almacen.pon(NODO_GASTO, mapOf(k to antes + Math.round(dolares * 1_000_000)))
    }

    const val NODO_GASTO = "gasto"
    const val NODO_AJUSTES = "ajustes"
}
