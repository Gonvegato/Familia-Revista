package com.gonzalo.hiedra.pc

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.appDeLaFamilia

fun main() {
    val icono = Thread.currentThread().contextClassLoader.getResourceAsStream("icono.png")?.use { it.readBytes() }
        ?.let { runCatching { BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()) }.getOrNull() }
    appDeLaFamilia("hiedra", "Hiedra", icono, alIniciar = { listOf("juegos", "tiempo", "ajustes").forEach { LaFamilia.coleccion(it) }; TuPc.inicia() }, alCerrar = { TuPc.alCerrar() }) { AppHiedra() }
}
