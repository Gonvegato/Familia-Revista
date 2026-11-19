package com.gonzalo.mostaza.pc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.pc.HermanasPc
import com.gonzalo.familia.pc.MauPc
import com.gonzalo.mau.Frases
import com.gonzalo.mau.ui.Mau
import kotlinx.coroutines.delay
import com.gonzalo.mau.Accion as AccionMau

/**
 * Mau en Hathor del ordenador (lo pone la ventana de la familia): mientras mpv tiene algo puesto, de espaldas mirando (lo dice la banda del
 * reproductor con `MauLugar(Lugar.VIENDO)`); al acabar de ver algo (llegó al 90 %: visto), al volver a la ventana pregunta qué tal
 * ([MauTrasVer]); si está Jonsu, con «Valorar en Jonsu». El vídeo se ve en la ventana de mpv (también a pantalla completa): ahí Mau no está.
 */
object MauHathor {
    /** Lo último que se acabó de ver: la ref del título y cuándo (ms). */
    class Acabado(val ref: String, val cuando: Long)

    /** Lo acabado y aún sin comentar (lo pone [Visionado.termina]; lo quita [MauTrasVer] al decirlo). */
    var acabado by mutableStateOf<Acabado?>(null)

    /** Lo de hace más de esto ya no se comenta. */
    const val FRESCO_MS = 30 * 60_000L

    /** Lo que dura la vuelta: Mau tiene que estar ya en casa (viendo algo, no habla). */
    const val ESPERA_MS = 1_500L

    fun visto(ref: String, ahora: Long = System.currentTimeMillis()) { acabado = Acabado(ref, ahora) }
}

/** Con mpv ya cerrado ([Reproductor] sin nada puesto): si se acaba de ver algo, Mau pregunta qué te ha parecido. */
@Composable
fun MauTrasVer() {
    val a = MauHathor.acabado ?: return
    if (Reproductor.estado.activo) return
    LaunchedEffect(a) {
        delay(MauHathor.ESPERA_MS)
        MauHathor.acabado = null
        if (!Mau.iniciado || Mau.ahora() - a.cuando > MauHathor.FRESCO_MS) return@LaunchedEffect
        val jonsu = if (HermanasPc.esta("azabache")) AccionMau("Valorar en Jonsu") { HermanasPc.abre("azabache", listOf(MauPc.ARG_LLEGA)) } else null
        // (acabar una peli o un episodio es de lo importante, como acabar un libro: no espera a la pausa entre dos cosas que dice)
        Mau.di(Mau.cerebro.elige(Frases.pelisVista), jonsu, importante = true)
    }
}
