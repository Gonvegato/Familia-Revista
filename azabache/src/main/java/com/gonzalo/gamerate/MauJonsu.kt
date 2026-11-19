package com.gonzalo.gamerate

import android.content.Context
import com.gonzalo.mau.Frases
import com.gonzalo.mau.Guardado
import com.gonzalo.mau.ui.Mau
import java.time.LocalDate
import java.time.LocalTime

/*
 * Mau en Jonsu: el saludo del día (con lo de ayer entre las hermanas) y los retos de todas cuando se cumplen. Lo que se calcula va aparte, sin
 * Android, para poder probarlo.
 */
object MauJonsu {
    /**
     * Al abrir Jonsu, una vez al día: un saludo según la hora y, si ayer hubo algo, lo leído, jugado, visto y escrito entre las hermanas
     * (lo mismo que cuenta «Esta semana» de la portada). [llega]: Mau viene de otra hermana (primero dice que ha llegado).
     */
    fun saluda(ctx: Context, llega: Boolean = false) {
        if (!Mau.iniciado || !Mau.cerebro.primeraVezHoy("saludo", Mau.ahora())) return
        val app = ctx.applicationContext
        kotlin.concurrent.thread(isDaemon = true, name = "mau-saludo") {
            val ayer = LocalDate.now().toEpochDay() - 1
            val resumen = runCatching {
                val ratos = ratosPorDia(Hermanas.jugadoPorDia(app), Hermanas.leidoPorDia(app), Hermanas.vistoPorDia(app), Hermanas.palabras(app), { "" }, { "" }, null)
                resumenDeAyer(ratos[ayer].orEmpty())
            }.getOrNull()
            // (si viene de otra hermana, que acabe antes su «¡Ya estoy aquí!»)
            if (llega) runCatching { Thread.sleep(6_000) }
            Mau.casa.enPrincipal {
                val saludo = Mau.cerebro.elige(Frases.saludo(LocalTime.now().hour))
                Mau.di(if (resumen == null) saludo else "$saludo $resumen", importante = true)
            }
        }
    }

    /** «Ayer: 45 min leyendo, 1 h 10 min jugando y 600 palabras.» (null si ayer no hubo nada: menos de un minuto y ninguna palabra). */
    fun resumenDeAyer(ratos: List<Rato>): String? {
        val partes = ArrayList<String>()
        for (h in listOf(Hermana.SESHAT, Hermana.BASTET, Hermana.HATHOR)) {
            val ms = ratos.filter { it.hermana == h }.sumOf { it.ms }
            if (ms >= 60_000L) partes += "${readTime((ms + 30_000L) / 60_000L)} ${h.verbo}"
        }
        val palabras = ratos.filter { it.hermana == Hermana.THOT }.sumOf { it.palabras }
        if (palabras > 0) partes += palabrasTexto(palabras)
        if (partes.isEmpty()) return null
        return "Ayer: " + conY(partes) + "."
    }

    /** «a», «a y b», «a, b y c». */
    fun conY(l: List<String>): String = if (l.size > 1) l.dropLast(1).joinToString(", ") + " y " + l.last() else l.firstOrNull().orEmpty()

    /** Mira los retos de la portada: los que se acaban de cumplir, Mau los celebra (una vez cada uno). */
    fun miraRetos(retos: List<RetoHermana>, hoy: LocalDate = LocalDate.now()) {
        if (!Mau.iniciado) return
        val casa = Mau.casa
        casa.enPrincipal {
            val texto = retosCumplidos(retos, casa.guardado, hoy)
            if (texto != null) Mau.celebra(texto)
        }
    }

    /**
     * Lo que hay que celebrar de [retos] (null: nada), apuntando en [g] cómo va cada uno. Solo cuenta el paso de «pendiente» a «cumplido»
     * dentro del mismo tramo (el año; en Thot, el día): uno que ya estaba cumplido la primera vez que se mira no se celebra, y uno celebrado
     * no se vuelve a celebrar en ese tramo aunque luego parezca pendiente (mientras se carga la cuenta, o si subes la meta).
     */
    fun retosCumplidos(retos: List<RetoHermana>, g: Guardado, hoy: LocalDate = LocalDate.now()): String? {
        val nuevos = ArrayList<String>()
        for (r in retos) {
            if (r.meta <= 0) continue
            val clave = "jonsu.reto.${r.hermana.name}"
            val tramo = if (r.hermana == Hermana.THOT) hoy.toString() else hoy.year.toString()
            val antes = g.lee(clave)?.takeIf { it.substringBefore('|') == tramo }?.substringAfter('|')
            if (antes == "cumplido") continue
            val cumplido = r.hechos >= r.meta
            if (cumplido && antes == "pendiente") nuevos += if (r.hermana == Hermana.THOT) "${palabrasTexto(r.meta)} hoy" else r.titulo
            val ahora = if (cumplido) "cumplido" else "pendiente"
            if (ahora != antes) g.guarda(clave, "$tramo|$ahora")
        }
        return when (nuevos.size) {
            0 -> null
            1 -> "¡Reto cumplido: ${nuevos[0]}!"
            else -> "¡Retos cumplidos: ${conY(nuevos)}!"
        }
    }
}
