package com.gonzalo.torrent

/** Un archivo del torrent, en el orden de libtorrent (el mismo índice que el `fileIdx` de Stremio). [relleno]: los de relleno (BEP 47). */
internal data class ArchivoTorrent(val indice: Int, val ruta: String, val tamano: Long, val relleno: Boolean = false) {
    val nombre: String get() = ruta.substringAfterLast('/').substringAfterLast('\\')
}

/** Qué archivo del torrent se ve. */
internal object Eleccion {
    sealed interface Resultado {
        data class Elegido(val archivo: ArchivoTorrent) : Resultado
        data class NoEsta(val mensaje: String) : Resultado
    }

    private val muestra = Regex("(^|[^a-z0-9])(sample|muestra)([^a-z0-9]|$)", RegexOption.IGNORE_CASE)

    /**
     * Con [fileIdx] (el del addon), ese archivo, sin más. Sin él (o si no existe y el addon dio [nombre]), el que se llame como
     * [nombre] (el `behaviorHints.filename` del stream). Si no, el vídeo más grande que no sea una muestra («sample»): lo que hace
     * Stremio con un torrent sin `fileIdx`, salvo que aquí solo cuentan los vídeos (y una muestra no se elige nunca: no se puede ver
     * la película en ella).
     */
    fun elige(archivos: List<ArchivoTorrent>, fileIdx: Int?, nombre: String?): Resultado {
        val reales = archivos.filter { !it.relleno && it.tamano > 0 }
        val porNombre = nombre?.trim()?.takeIf { it.isNotEmpty() }?.let { n ->
            val base = n.substringAfterLast('/').substringAfterLast('\\')
            reales.firstOrNull { it.nombre.equals(base, ignoreCase = true) }
        }
        if (fileIdx != null && fileIdx >= 0) {
            reales.firstOrNull { it.indice == fileIdx }?.let { return Resultado.Elegido(it) }
            porNombre?.let { return Resultado.Elegido(it) }
            return Resultado.NoEsta("El archivo n.º $fileIdx no está en este torrent (tiene ${archivos.size}). Prueba con otra fuente.")
        }
        porNombre?.let { return Resultado.Elegido(it) }
        val videos = reales.filter { Mime.esVideo(it.nombre) }
        val elegido = videos.filterNot { esMuestra(it) }.maxByOrNull { it.tamano }
            ?: return Resultado.NoEsta(
                if (videos.isEmpty()) "En este torrent no hay ningún vídeo. Prueba con otra fuente."
                else "En este torrent solo hay una muestra del vídeo. Prueba con otra fuente."
            )
        return Resultado.Elegido(elegido)
    }

    /**
     * Una muestra: «sample» en el nombre o en una carpeta del camino, como palabra suelta («Sample/», «peli-sample.mkv»; «Samples»
     * no), y pequeña (menos de [MUESTRA_MAX] bytes: una película que se llame así no es una muestra).
     */
    fun esMuestra(a: ArchivoTorrent): Boolean =
        a.tamano < MUESTRA_MAX && a.ruta.split('/', '\\').any { muestra.containsMatchIn(it.substringBeforeLast('.')) }

    const val MUESTRA_MAX = 512L * 1024 * 1024
}

/** El tipo de cada archivo por su extensión, para el Content-Type del servidor local. */
internal object Mime {
    private val tipos = mapOf(
        "mkv" to "video/x-matroska", "mk3d" to "video/x-matroska", "webm" to "video/webm",
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mov" to "video/quicktime", "3gp" to "video/3gpp",
        "avi" to "video/x-msvideo", "divx" to "video/x-msvideo", "wmv" to "video/x-ms-wmv", "asf" to "video/x-ms-asf",
        "flv" to "video/x-flv", "ts" to "video/mp2t", "m2ts" to "video/mp2t", "mts" to "video/mp2t",
        "mpg" to "video/mpeg", "mpeg" to "video/mpeg", "vob" to "video/mpeg", "ogv" to "video/ogg",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "flac" to "audio/flac", "ogg" to "audio/ogg", "opus" to "audio/ogg",
        "srt" to "application/x-subrip", "vtt" to "text/vtt", "ass" to "text/x-ssa", "ssa" to "text/x-ssa",
    )

    fun de(nombre: String): String = tipos[nombre.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"
    fun esVideo(nombre: String): Boolean = de(nombre).startsWith("video/")
}
