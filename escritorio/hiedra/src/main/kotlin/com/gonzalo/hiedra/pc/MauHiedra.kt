package com.gonzalo.hiedra.pc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import com.gonzalo.mau.Frases
import com.gonzalo.mau.ui.Mau
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay

/**
 * Mau en Bastet del ordenador: al acabar una partida de un rato ([MINUTOS] o más), lo comenta. No en el momento (el juego suele estar
 * delante y su bocadillo dura unos segundos), sino cuando la ventana de Bastet vuelve a estar delante ([MauDiceLoJugado]).
 */
object MauHiedra {
    /** Lo jugado que Mau aún no ha comentado: el juego, cuántos minutos y cuándo acabó. */
    class Jugado(val nombre: String, val minutos: Int, val fin: Long)
    /** Desde cuántos minutos jugados lo comenta. */
    const val MINUTOS = 5
    /** Lo que acabó hace más de esto ya no se comenta (vuelves a Bastet mucho después). */
    const val CADUCA = 3L * 3_600_000L

    /** Lo que tiene por decir (leído desde Compose). */
    var porDecir by mutableStateOf<Jugado?>(null)

    /** Lo que se apuntaría al acabar una partida de [nombre] de [ms] (null si fue poco). Aparte, para las pruebas. */
    fun jugado(nombre: String, ms: Long, fin: Long): Jugado? = (ms / 60_000L).toInt().takeIf { it >= MINUTOS }?.let { Jugado(nombre, it, fin) }

    /** ¿Aún se dice? */
    fun decirlo(j: Jugado, ahora: Long): Boolean = ahora - j.fin <= CADUCA

    /** Al acabar una partida (desde [Pc]: cualquier hilo). */
    fun alTerminar(p: Pc.Partida, ms: Long, ahora: Long = System.currentTimeMillis()) {
        val j = jugado(p.nombre, ms, ahora) ?: return
        if (SwingUtilities.isEventDispatchThread()) porDecir = j else SwingUtilities.invokeLater { porDecir = j }
    }
}

/** En la pantalla principal (no en la sala, donde Mau no sale): cuando la ventana está delante, Mau dice lo que se ha jugado. */
@Composable
fun MauDiceLoJugado() {
    val delante = LocalWindowInfo.current.isWindowFocused
    val d = MauHiedra.porDecir
    LaunchedEffect(d, delante) {
        if (d == null || !delante) return@LaunchedEffect
        delay(800)   // (lo que tarda la ventana en volver delante y asentarse)
        MauHiedra.porDecir = null
        if (!MauHiedra.decirlo(d, System.currentTimeMillis())) return@LaunchedEffect
        Mau.toque()   // (volver de jugar es hacer algo: si se había dormido, se despierta)
        Mau.di(Frases.hasJugado(d.minutos, d.nombre), importante = true)
    }
}
