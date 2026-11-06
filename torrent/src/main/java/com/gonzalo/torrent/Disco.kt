package com.gonzalo.torrent

import android.content.Context
import android.os.storage.StorageManager
import java.io.File

/** El sitio del disco: cuánto se puede usar y, antes de empezar a bajar, pedirlo. */
internal interface Disco {
    fun libre(dir: File): Long
    /** Pide [bytes] para la app en el disco de [dir] (Android borra cachés de otras apps si hace falta). */
    fun pide(dir: File, bytes: Long) {}
}

/** Fuera de Android: lo que queda libre, sin más. */
internal object DiscoSimple : Disco {
    override fun libre(dir: File): Long = dir.usableSpace
}

/**
 * En Android: cuenta también lo que el sistema puede liberar de las cachés de otras apps (getAllocatableBytes), y al empezar un
 * vídeo lo pide (allocateBytes), para que ese sitio esté de verdad cuando libtorrent escriba.
 */
internal class DiscoAndroid(context: Context) : Disco {
    private val sm = context.applicationContext.getSystemService(StorageManager::class.java)

    override fun libre(dir: File): Long =
        runCatching { sm.getAllocatableBytes(sm.getUuidForPath(dir)) }.getOrNull()?.coerceAtLeast(dir.usableSpace) ?: dir.usableSpace

    override fun pide(dir: File, bytes: Long) {
        if (dir.usableSpace >= bytes) return
        runCatching { sm.allocateBytes(sm.getUuidForPath(dir), bytes) }
    }
}
