package com.gonzalo.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EleccionTest {
    private val mb = 1024L * 1024
    private val temporada = listOf(
        ArchivoTorrent(0, "Serie T1/Serie.S01E01.1080p.mkv", 1200 * mb),
        ArchivoTorrent(1, "Serie T1/Serie.S01E02.1080p.mkv", 1300 * mb),
        ArchivoTorrent(2, "Serie T1/.pad/1234", 3000, relleno = true),
        ArchivoTorrent(3, "Serie T1/Sample/serie-sample.mkv", 40 * mb),
        ArchivoTorrent(4, "Serie T1/Serie.S01E01.es.srt", 50_000),
        ArchivoTorrent(5, "Serie T1/Serie.S01E03.1080p.mkv", 1250 * mb),
    )

    private fun elegido(r: Eleccion.Resultado) = (r as Eleccion.Resultado.Elegido).archivo.indice
    private fun noEsta(r: Eleccion.Resultado) = (r as Eleccion.Resultado.NoEsta).mensaje

    @Test fun conFileIdxEseArchivo() {
        assertEquals(0, elegido(Eleccion.elige(temporada, 0, null)))
        assertEquals(5, elegido(Eleccion.elige(temporada, 5, null)))
        // Aunque no sea un vídeo: el addon sabe lo que pide
        assertEquals(4, elegido(Eleccion.elige(temporada, 4, null)))
    }

    @Test fun fileIdxQueNoEsta() {
        assertTrue(noEsta(Eleccion.elige(temporada, 9, null)).contains("n.º 9"))
        // Uno de relleno no es un archivo de verdad
        assertTrue(noEsta(Eleccion.elige(temporada, 2, null)).contains("n.º 2"))
        // Si el addon dio el nombre, vale el nombre
        assertEquals(5, elegido(Eleccion.elige(temporada, 9, "Serie.S01E03.1080p.mkv")))
    }

    @Test fun fileIdxNegativoEsComoNoTenerlo() {
        assertEquals(1, elegido(Eleccion.elige(temporada, -1, null)))
    }

    @Test fun sinFileIdxElVideoMasGrande() {
        assertEquals(1, elegido(Eleccion.elige(temporada, null, null)))
    }

    @Test fun sinFileIdxPorNombre() {
        assertEquals(0, elegido(Eleccion.elige(temporada, null, "serie.s01e01.1080p.MKV")))
        assertEquals(5, elegido(Eleccion.elige(temporada, null, "carpeta/Serie.S01E03.1080p.mkv")))
        // Un nombre que no está: el más grande
        assertEquals(1, elegido(Eleccion.elige(temporada, null, "Otra cosa")))
    }

    @Test fun lasMuestrasNoSeEligenNunca() {
        val peli = listOf(
            ArchivoTorrent(0, "Peli/Peli.2024.sample.mkv", 90 * mb),
            ArchivoTorrent(1, "Peli/Peli.2024.iso", 9000 * mb),
            ArchivoTorrent(2, "Peli/Sample/otra.mp4", 60 * mb),
        )
        assertTrue(noEsta(Eleccion.elige(peli, null, null)).contains("muestra"))
        assertEquals(listOf(0, 2), peli.filter { Eleccion.esMuestra(it) }.map { it.indice })
        // Una película que se llama «Sample» no es una muestra (es grande); «Samples» tampoco
        assertEquals(false, Eleccion.esMuestra(ArchivoTorrent(0, "The.Sample.2020.1080p.mkv", 2000 * mb)))
        assertEquals(false, Eleccion.esMuestra(ArchivoTorrent(0, "Samples/clip.mkv", 10 * mb)))
        assertEquals(true, Eleccion.esMuestra(ArchivoTorrent(0, "SAMPLE.mkv", 10 * mb)))
    }

    @Test fun sinVideos() {
        val musica = listOf(ArchivoTorrent(0, "Disco/01.flac", 30 * mb), ArchivoTorrent(1, "Disco/portada.jpg", mb))
        assertTrue(noEsta(Eleccion.elige(musica, null, null)).contains("ningún vídeo"))
        assertTrue(noEsta(Eleccion.elige(emptyList(), null, null)).contains("ningún vídeo"))
    }

    @Test fun archivosVaciosNoCuentan() {
        val l = listOf(ArchivoTorrent(0, "a.mkv", 0), ArchivoTorrent(1, "b.mkv", 10))
        assertEquals(1, elegido(Eleccion.elige(l, null, null)))
        assertTrue(Eleccion.elige(l, 0, null) is Eleccion.Resultado.NoEsta)
    }

    @Test fun mime() {
        assertEquals("video/x-matroska", Mime.de("a.MKV"))
        assertEquals("video/mp4", Mime.de("a.mp4"))
        assertEquals("video/mp4", Mime.de("a.m4v"))
        assertEquals("video/x-msvideo", Mime.de("a.avi"))
        assertEquals("video/webm", Mime.de("a.webm"))
        assertEquals("video/mp2t", Mime.de("a.ts"))
        assertEquals("video/quicktime", Mime.de("a.mov"))
        assertEquals("application/x-subrip", Mime.de("a.srt"))
        assertEquals("application/octet-stream", Mime.de("a.iso"))
        assertEquals("application/octet-stream", Mime.de("sinextension"))
        assertEquals(ArchivoTorrent(0, "dir\\sub\\a.mkv", 1).nombre, "a.mkv")
    }
}
