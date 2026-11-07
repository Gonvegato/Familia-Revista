package com.gonzalo.marfil.pc

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.appDeLaFamilia

fun main() {
    val icono = Thread.currentThread().contextClassLoader.getResourceAsStream("icono.png")?.use { it.readBytes() }
        ?.let { runCatching { BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()) }.getOrNull() }
    appDeLaFamilia("marfil", "Seshat", icono, alIniciar = {
        listOf("libros", "lectura", "tiempo", "marcas", "ajustes", "capitulos").forEach { LaFamilia.coleccion(it) }
        // Lo que tengas aquí y aún no esté en tu kDrive, se sube solo
        Thread({ runCatching { Thread.sleep(8_000); NubeLibros.subePendientes() } }, "marfil-subida").apply { isDaemon = true; start() }
    }, alCerrar = { Lectura.alCerrar() }) { AppMarfil() }
}
