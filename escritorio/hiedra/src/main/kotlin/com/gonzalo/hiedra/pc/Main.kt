package com.gonzalo.hiedra.pc

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.appDeLaFamilia

/**
 * Hiedra del ordenador. Con `--sala` se abre ya en la sala (a pantalla completa, para la tele y el mando): es la que va en el inicio de sesión
 * del PC de jugar (`instalar.sh --al-iniciar`). También se abre en la sala si lo eliges en Ajustes → Sala y mando.
 */
fun main(args: Array<String>) {
    val sala = "--sala" in args
    val icono = Thread.currentThread().contextClassLoader.getResourceAsStream("icono.png")?.use { it.readBytes() }
        ?.let { runCatching { BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()) }.getOrNull() }
    appDeLaFamilia("hiedra", "Hiedra", icono, alIniciar = {
        listOf("juegos", "tiempo", "ajustes").forEach { LaFamilia.coleccion(it) }
        TuPc.inicia()
        // Lo de este ordenador: primero lo que ya se sabe (rápido) y enseguida otra vuelta por tus carpetas de ROMs
        Pc.refresca { Pc.refresca(escanear = true) }
        Pc.vigila()
        Mando.inicia()
    }, alCerrar = { VentanaHiedra.alCerrar(); TuPc.alCerrar(); Mando.para() }) { AppHiedra(sala = sala || AjustesSala.alAbrir) }
}
