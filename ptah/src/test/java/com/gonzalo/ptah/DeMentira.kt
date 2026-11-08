package com.gonzalo.ptah

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Lo de mentira de las pruebas: la hora (sábado 7 de noviembre de 2026, 10:08:30, en Madrid), el tiempo de León (con la forma de la
 * respuesta de Open-Meteo), la batería, y una «foto» (un degradado con un sol, hecha aquí). Sin red.
 */
class DatosDeMentira(private val ctx: Context, var cargando: Boolean = true, var nivel: Int = 76, var conTiempo: Boolean = true, var conFoto: Boolean = true) : Datos {
    override fun ahora(): ZonedDateTime = AHORA
    override fun tiempo(d: Diseno): Prevision? = if (!conTiempo) null else OpenMeteo.prevision(prevision(), AHORA.toInstant().toEpochMilli()).let { p ->
        p.copy(hora = AHORA.toLocalDateTime(), esDia = true, codigo = 2, temp = 12.4)
    }
    override fun bateria() = Bateria(nivel, cargando)
    override fun iconoApp(paquete: String): Bitmap? = null
    override fun nombreApp(paquete: String): String? = mapOf("com.android.chrome" to "Chrome", "com.whatsapp" to "WhatsApp", "com.google.android.gm" to "Gmail")[paquete]
    override fun foto(id: String): Bitmap? = if (conFoto) foto else null

    companion object {
        val AHORA: ZonedDateTime = ZonedDateTime.of(2026, 11, 7, 10, 8, 30, 0, ZoneId.of("Europe/Madrid"))

        fun prevision(): String = listOf(File("src/test/resources/openmeteo/prevision_leon.json"), File("ptah/src/test/resources/openmeteo/prevision_leon.json")).first { it.exists() }.readText()

        /** Una foto de mentira: un atardecer (degradado) con el sol y el horizonte del mar. */
        val foto: Bitmap by lazy {
            val b = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.drawRect(0f, 0f, 640f, 300f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, 300f, 0xFF2B4A6F.toInt(), 0xFFF2A65A.toInt(), Shader.TileMode.CLAMP) })
            c.drawCircle(420f, 280f, 70f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE3A1.toInt() })
            c.drawRect(0f, 300f, 640f, 480f, Paint().apply { shader = LinearGradient(0f, 300f, 0f, 480f, 0xFF1F5C73.toInt(), 0xFF0E2A36.toInt(), Shader.TileMode.CLAMP) })
            for (i in 0 until 6) c.drawRect(330f + i * 8f, 312f + i * 22f, 510f - i * 8f, 316f + i * 22f, Paint().apply { color = 0x66FFE3A1 })
            b
        }

        /** Unos diseños de ejemplo, uno por tipo (las plantillas, y alguno más). */
        fun disenos(): List<Diseno> = Plantillas.TODAS.mapIndexed { i, (p, c) -> p.copy(id = "d%010d".format(i), creado = 1000L - i).con("celdas", "${c.col}x${c.fil}") }
    }
}
