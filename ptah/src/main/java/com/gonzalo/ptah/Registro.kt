package com.gonzalo.ptah

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lo que pasa por dentro, para el diagnóstico («Qué ve tu móvil», en Ajustes → Avanzado): las últimas operaciones de guardado y de la
 * cuenta (en un archivo pequeño de este móvil, las 40 últimas) y, de cada widget, lo último que se pintó (su tamaño, su composición, si
 * falló). Nada secreto: ids de diseños, horas, tamaños y nombres de errores.
 */
object Registro {
    private const val MAX = 40
    private val hora = SimpleDateFormat("dd/MM HH:mm:ss", Locale("es", "ES"))
    private fun archivo(ctx: Context) = File(ctx.filesDir, "registro.txt")

    @Synchronized fun apunta(ctx: Context, t: String) = runCatching {
        val f = archivo(ctx)
        val l = (if (f.isFile) f.readLines() else emptyList()) + "${hora.format(Date())}  $t"
        f.writeText(l.takeLast(MAX).joinToString("\n") + "\n")
    }

    @Synchronized fun ultimas(ctx: Context, n: Int = 20): List<String> = runCatching { archivo(ctx).readLines().filter { it.isNotBlank() }.takeLast(n).reversed() }.getOrDefault(emptyList())

    /** Un diseño, en corto: «d1a2… c=08/11 10:21:03». */
    fun corto(json: Any?): String {
        val d = Diseno.deJson(json as? String) ?: return if (json == null || json == "") "(borrado)" else "(no es un diseño)"
        return "${d.id} «${d.nombre}» c=${if (d.cambiado > 0) hora.format(Date(d.cambiado)) else "?"}"
    }

    // ---------------------------------------------------------------- de dónde viene cada diseño

    private fun origenes(ctx: Context) = ctx.getSharedPreferences("ptah_origen", Context.MODE_PRIVATE)
    fun origen(ctx: Context, id: String, de: String) { origenes(ctx).edit().putString(id, de).apply() }
    fun origenDe(ctx: Context, id: String): String = origenes(ctx).getString(id, null) ?: "local (de antes del registro)"

    // ---------------------------------------------------------------- lo último que se pintó en cada widget

    private fun pintados(ctx: Context) = ctx.getSharedPreferences("ptah_diag", Context.MODE_PRIVATE)

    fun pintado(ctx: Context, id: Int, variantes: List<String>, error: Throwable? = null) {
        val o = JSONObject().put("t", System.currentTimeMillis()).put("v", variantes.joinToString(" | "))
        if (error != null) o.put("e", "${error.javaClass.simpleName}: ${error.message.orEmpty().take(200)}")
        else pintados(ctx).getString("w$id", null)?.let { runCatching { JSONObject(it).optString("e") }.getOrNull() }?.takeIf { it.isNotBlank() }?.let { o.put("e_antes", it) }
        pintados(ctx).edit().putString("w$id", o.toString()).apply()
    }

    fun ultimoPintado(ctx: Context, id: Int): JSONObject? = pintados(ctx).getString("w$id", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    fun horaDe(ms: Long): String = if (ms <= 0) "nunca" else hora.format(Date(ms))
}
