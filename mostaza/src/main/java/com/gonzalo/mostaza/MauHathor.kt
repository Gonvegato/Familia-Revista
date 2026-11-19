package com.gonzalo.mostaza

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.gonzalo.mau.Accion
import com.gonzalo.mau.Frases
import com.gonzalo.mau.ui.Mau
import kotlinx.coroutines.delay

/**
 * Mau en Hathor: lo que no es ponerlo (eso, en cada actividad con `MauMovil.pon`). Al acabar de ver algo (el reproductor lo deja visto:
 * llegó al 90 %), al volver a la app Mau pregunta qué tal ([MauTrasVer]); si está Jonsu, con «Valorar en Jonsu».
 */
object MauHathor {
    /** Lo último que se acabó de ver: la ref del título y cuándo (ms). */
    class Acabado(val ref: String, val cuando: Long)

    /** Lo acabado y aún sin comentar (lo pone [Visionado.termina]; lo quita [MauTrasVer] al decirlo). */
    var acabado by mutableStateOf<Acabado?>(null)

    /** Lo de hace más de esto ya no se comenta (saliste del reproductor y volviste a la app mucho después). */
    const val FRESCO_MS = 30 * 60_000L

    /** Lo que dura la vuelta: Mau tiene que estar ya en casa (viendo algo, no habla). */
    const val ESPERA_MS = 1_500L

    fun visto(ref: String, ahora: Long = System.currentTimeMillis()) { acabado = Acabado(ref, ahora) }
}

/** En la pantalla normal (no en la sala ni en el reproductor): si se acaba de ver algo, Mau pregunta qué te ha parecido. */
@Composable
fun MauTrasVer() {
    val a = MauHathor.acabado ?: return
    val ctx = LocalContext.current
    LaunchedEffect(a) {
        delay(MauHathor.ESPERA_MS)
        MauHathor.acabado = null
        if (!Mau.iniciado || Mau.ahora() - a.cuando > MauHathor.FRESCO_MS) return@LaunchedEffect
        val jonsu = if (Hermanas.azabache(ctx)) Accion("Valorar en Jonsu") { Hermanas.abreEnAzabache(ctx, a.ref, conMau = true) } else null
        // (acabar una peli o un episodio es de lo importante, como acabar un libro: no espera a la pausa entre dos cosas que dice)
        Mau.di(Mau.cerebro.elige(Frases.pelisVista), jonsu, importante = true)
    }
}
