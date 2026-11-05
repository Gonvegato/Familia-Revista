package com.gonzalo.marfil.pc

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

/** Un libro de páginas (PDF o cómic): cuántas tiene y cada una como imagen. */
interface Paginas : Closeable {
    val total: Int
    fun pagina(i: Int, ancho: Int): ImageBitmap?
}

/** Un PDF (Apache PDFBox): cada página se pinta a la anchura que se ve. */
class Pdf(f: File) : Paginas {
    private val doc: PDDocument = Loader.loadPDF(f)
    private val pintor = PDFRenderer(doc)
    override val total: Int = doc.numberOfPages
    val titulo: String = runCatching { doc.documentInformation?.title.orEmpty() }.getOrDefault("")
    val autor: String = runCatching { doc.documentInformation?.author.orEmpty() }.getOrDefault("")
    @Synchronized override fun pagina(i: Int, ancho: Int): ImageBitmap? = runCatching {
        val p = doc.getPage(i)
        val escala = (ancho / p.mediaBox.width).coerceIn(0.5f, 4f)
        pintor.renderImage(i, escala, ImageType.RGB).toComposeImageBitmap()
    }.getOrNull()
    /** La primera página como JPEG (la portada de un PDF). */
    @Synchronized fun primeraJpeg(): ByteArray? = runCatching {
        val img = pintor.renderImage(0, (600 / doc.getPage(0).mediaBox.width).coerceIn(0.3f, 3f), ImageType.RGB)
        java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(img, "jpg", it) }.toByteArray()
    }.getOrNull()
    override fun close() = doc.close()
}

/** Un cómic (CBZ: un zip de imágenes, en orden por su nombre). */
class Comic(f: File) : Paginas {
    private val zip = ZipFile(f)
    private val imagenes = zip.entries().asSequence().filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif") }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).toList()
    override val total: Int = imagenes.size
    @Synchronized override fun pagina(i: Int, ancho: Int): ImageBitmap? = runCatching {
        org.jetbrains.skia.Image.makeFromEncoded(zip.getInputStream(imagenes[i]).use { it.readBytes() }).toComposeImageBitmap()
    }.getOrNull()
    override fun close() = zip.close()
}

/** La posición en un libro de páginas, como la guarda el lector del móvil: la página como un CFI sin capítulo (`epubcfi(/6/<2·(n+1)>)`). */
object PosicionPaginas {
    fun cfi(pagina: Int) = "epubcfi(/6/${(pagina + 1) * 2})"
    fun pagina(cfi: String): Int? = Regex("^epubcfi\\(/6/(\\d+)\\)$").find(cfi.trim())?.groupValues?.get(1)?.toIntOrNull()?.let { it / 2 - 1 }
}
