package com.gonzalo.cobalto

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gonzalo.mau.ui.Mau

/*
 * Mau en Thot del móvil: lo que es igual en el ordenador (la meta, el atasco, el sprint) está en MauEscribir.kt; aquí, lo de Android (la
 * meta del día de [MetaDiaria], si la app está delante y el hueco que deja Mau en el pie del editor).
 */

/** ¿Se ve la app (la actividad, al menos RESUMED)? */
@Composable
fun appDelante(): Boolean {
    @Suppress("DEPRECATION") val ciclo = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var delante by remember(ciclo) { mutableStateOf(ciclo.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(ciclo) {
        val o = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_RESUME -> delante = true
                Lifecycle.Event.ON_PAUSE -> delante = false
                else -> {}
            }
        }
        ciclo.lifecycle.addObserver(o)
        onDispose { ciclo.lifecycle.removeObserver(o) }
    }
    return delante
}

/** Lo de Mau para toda la app (en [CobaltoApp]): la meta de palabras del día ([MetaDiaria]), «hoy aún no has escrito» y el sprint. */
@Composable
fun MauEnThotMovil(e: Escritos) {
    val ctx = LocalContext.current
    val mv = MetaDiaria.version
    val meta = remember(e.version, mv) { MetaDiaria.de(ctx) }
    MauEnThot(e, meta, appDelante())
}

/**
 * El hueco de Mau en el pie del editor (las cuentas y la barra de formato): sentado abajo, a la derecha o a la izquierda, taparía sus
 * botones. Lo que mide es el ancho de Mau según su tamaño (el de mau/compose/…/Capa.kt en el móvil). Escondido, ninguno.
 */
@Composable
fun huecoMau(): PaddingValues {
    if (!Mau.iniciado) return PaddingValues()
    @Suppress("UNUSED_VARIABLE") val v = Mau.version
    val c = Mau.cerebro
    if (c.escondido) return PaddingValues()
    val ancho = Mau.anchoDp.dp + 4.dp
    return if (c.sitio >= 0.5f) PaddingValues(end = ancho) else PaddingValues(start = ancho)
}
