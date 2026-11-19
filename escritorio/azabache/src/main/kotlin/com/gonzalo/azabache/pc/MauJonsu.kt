package com.gonzalo.azabache.pc

import com.gonzalo.mau.Frases
import com.gonzalo.mau.ui.Mau
import java.time.LocalDate
import java.time.LocalTime

/*
 * Mau en Jonsu del ordenador (lo pone la ventana, encima de todo): el saludo del día, con lo de ayer entre las hermanas (lo mismo que cuenta
 * «Hoy» en la portada: leído, jugado y escrito).
 */
object MauJonsu {
    /** Al abrir la ventana, una vez al día: un saludo según la hora y, si ayer hubo algo, lo leído, jugado y escrito. */
    fun saluda() {
        if (!Mau.iniciado || !Mau.cerebro.primeraVezHoy("saludo", Mau.ahora())) return
        // (si viene de otra hermana, que acabe antes su «¡Ya estoy aquí!»)
        val llega = Mau.llega
        Thread({
            val ayer = LocalDate.now().toEpochDay() - 1
            val resumen = runCatching { diasDeLaCuenta()[ayer]?.let { resumenDeAyer(it) } }.getOrNull()
            if (llega) runCatching { Thread.sleep(6_000) }
            Mau.casa.enPrincipal {
                val saludo = Mau.cerebro.elige(Frases.saludo(LocalTime.now().hour))
                Mau.di(if (resumen == null) saludo else "$saludo $resumen", importante = true)
            }
        }, "mau-saludo").apply { isDaemon = true }.start()
    }

    /** «Ayer: 45 min leyendo, 1 h 10 min jugando y 600 palabras.» (null si ayer no hubo nada: menos de un minuto y ninguna palabra). */
    fun resumenDeAyer(d: DiaHoy): String? {
        val partes = listOfNotNull(
            if (d.leidoMs >= 60_000L) "${duracion((d.leidoMs + 30_000L) / 60_000L)} leyendo" else null,
            if (d.jugadoMs >= 60_000L) "${duracion((d.jugadoMs + 30_000L) / 60_000L)} jugando" else null,
            if (d.palabras > 0) "${Formato.cifra(d.palabras)} ${if (d.palabras == 1) "palabra" else "palabras"}" else null,
        )
        if (partes.isEmpty()) return null
        val y = if (partes.size > 1) partes.dropLast(1).joinToString(", ") + " y " + partes.last() else partes.first()
        return "Ayer: $y."
    }
}
