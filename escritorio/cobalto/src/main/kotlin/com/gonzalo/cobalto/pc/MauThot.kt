package com.gonzalo.cobalto.pc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.MauEnElEditor
import com.gonzalo.cobalto.MauEnThot
import com.gonzalo.cobalto.MauEscribir
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.MauLugar
import com.gonzalo.mau.Lugar

/*
 * Mau en Thot del ordenador: lo pone la ventana de la familia; lo que es igual en el móvil (la meta, el atasco, el sprint) está en
 * MauEscribir.kt (el mismo archivo, copiado al compilar). Aquí, lo del ordenador: la meta del día de la cuenta y si la ventana está delante.
 * El teclado ya cuenta como «haces algo» (lo mira la ventana).
 */

/**
 * La meta de palabras al día: la de la cuenta (`ajustes/cobalto_meta`, campo `diaria`, la que se elige en Thot del móvil) o, si no hay,
 * la de serie del móvil (MetaDiaria.DE_SERIE, 500). En el ordenador no se cambia: la pone el móvil.
 */
object MetaDiariaPc {
    const val DE_SERIE = 500
    const val NODO = "cobalto_meta"

    fun de(): Int = (if (!LaFamilia.iniciada) null else runCatching { LaFamilia.coleccion("ajustes").nodo(NODO)?.entero("diaria")?.toInt() }.getOrNull())
        ?.takeIf { it > 0 } ?: DE_SERIE
}

/** Lo de Mau para toda la ventana (en [AppCobalto]): el sprint en su menú, la meta del día, «hoy aún no has escrito» y el sprint. */
@Composable
fun MauEnThotPc(e: Escritos) {
    LaunchedEffect(Unit) {
        // (lo que el editor aún no ha guardado cuenta en el sprint)
        MauEscribir.guardaAntes = { Editor.guardaYa() }
        MauEscribir.pon()
    }
    val cambios = LaFamilia.cambios
    val meta = remember(cambios) { MetaDiariaPc.de() }
    MauEnThot(e, meta, LocalWindowInfo.current.isWindowFocused)
}

/** Lo de Mau en el editor de [pid]: escribiendo (con su pluma), el sprint con él en su menú y, si te atascas, una idea. */
@Composable
fun MauEnElEditorPc(e: Escritos, pid: String, escrito: Long) {
    MauLugar(Lugar.ESCRIBIENDO)
    MauEnElEditor(e, pid, escrito, LocalWindowInfo.current.isWindowFocused)
}
