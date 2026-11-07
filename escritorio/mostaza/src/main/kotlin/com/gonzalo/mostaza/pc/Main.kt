package com.gonzalo.mostaza.pc

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.gonzalo.familia.pc.appDeLaFamilia

fun main(args: Array<String>) {
    val icono = Thread.currentThread().contextClassLoader.getResourceAsStream("icono.png")?.use { it.readBytes() }
        ?.let { runCatching { BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()) }.getOrNull() }
    appDeLaFamilia("mostaza", "Hathor", icono, args = args,
        alIniciar = {
            Mando.inicia()
            // (el motor de torrents se mira ya, por detrás: así «Reproducir» sabe enseguida si puede elegir un torrent)
            Thread({ runCatching { Torrents.disponible } }, "mostaza-torrents").apply { isDaemon = true; start() }
        },
        // Al cerrar: lo que se estaba viendo termina y se apunta (antes de subir lo pendiente a la cuenta)
        alCerrar = { Reproductor.alCerrarLaApp(); Mando.para() }) { AppMostaza() }
}
