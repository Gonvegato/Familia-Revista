package com.gonzalo.cobalto.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.pc.VentanaFamilia

/**
 * Lo que el estudio de escritura comparte entre pantallas: la concentración (solo la página, sin columna, cabecera, índice ni
 * barras), la ventana a pantalla completa y una búsqueda que se lleva a otra escena («Buscar en la obra» → abrir un resultado).
 */
object Estudio {
    /** Concentración: la app esconde todo lo que no es la página que escribes. */
    var concentracion by mutableStateOf(false)
    /** ¿La pantalla completa la pusimos al concentrarnos? (entonces se quita al salir) */
    private var puesta = false

    /** Lo que buscar al abrir la próxima escena (y en cuál). */
    var buscarAlAbrir: Pair<String, String>? = null

    /** La ventana a pantalla completa: la de toda la familia (F11; en el Mac también ⌃⌘F). */
    val pantallaCompleta: Boolean get() = VentanaFamilia.pantallaCompleta

    fun concentra(on: Boolean) {
        concentracion = on
        if (!on && puesta) { puesta = false; VentanaFamilia.pantallaCompleta = false }
    }

    /** Pone (o quita) la ventana a pantalla completa desde la concentración: al salir de ella, vuelve como estaba. */
    fun pantallaCompleta(on: Boolean) {
        puesta = on && concentracion
        VentanaFamilia.pantallaCompleta = on
    }

    fun reiniciaParaPruebas() { concentracion = false; puesta = false; buscarAlAbrir = null; VentanaFamilia.pantallaCompleta = false }
}
