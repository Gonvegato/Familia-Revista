package com.gonzalo.hiedra

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Los juegos que son más de un archivo: un .cue y sus pistas (.bin), un .m3u con los discos de un juego (y cada disco con las suyas), un
 * .ccd con su .img y su .sub. El núcleo abre el primero y busca los demás a su lado, así que hay que llevárselos todos juntos (a la
 * caché, o como archivos «virtuales» si el núcleo los abre donde están). Lo puro se prueba en el ordenador ([pistasCue], [listaM3u]…).
 */
object Discos {
    /** Extensiones que apuntan a otros archivos. */
    val INDICES = setOf("cue", "m3u", "ccd", "toc", "gdi")

    /** Los archivos de un .cue: las líneas `FILE "Juego (Track 1).bin" BINARY` (con o sin comillas). */
    fun pistasCue(texto: String): List<String> = texto.lineSequence().mapNotNull { l ->
        val t = l.trim()
        if (!t.startsWith("FILE ", ignoreCase = true)) return@mapNotNull null
        val resto = t.substring(5).trim()
        val nombre = if (resto.startsWith("\"")) resto.drop(1).substringBefore('"') else resto.substringBeforeLast(' ').trim()
        nombre.replace('\\', '/').substringAfterLast('/').takeIf { it.isNotBlank() }
    }.distinct().toList()

    /** Los discos de un .m3u: cada línea que no esté vacía ni sea un comentario (#). */
    fun listaM3u(texto: String): List<String> = texto.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.replace('\\', '/').substringAfterLast('/') }.distinct().toList()

    /** Lo que acompaña a un .ccd (CloneCD): la imagen y los subcanales, con el mismo nombre. */
    fun deCcd(nombre: String): List<String> = nombre.substringBeforeLast('.').let { listOf("$it.img", "$it.sub") }

    /**
     * Todos los archivos que necesita [principal] además de él, siguiendo un .m3u hasta sus .cue y estos hasta sus pistas. [leer] da el
     * texto de un archivo de la misma carpeta (null si no está); [existe], si está (los .sub de un .ccd no siempre existen).
     */
    fun necesarios(principal: String, leer: (String) -> String?, existe: (String) -> Boolean = { true }): List<String> {
        val out = LinkedHashSet<String>()
        fun sigue(n: String, prof: Int) {
            if (prof > 3) return
            when (n.substringAfterLast('.', "").lowercase()) {
                "m3u" -> leer(n)?.let { t -> listaM3u(t).forEach { d -> if (out.add(d)) sigue(d, prof + 1) } }
                "cue" -> leer(n)?.let { t -> out.addAll(pistasCue(t)) }
                "ccd" -> out.addAll(deCcd(n).filter(existe))
                "toc" -> leer(n)?.let { t -> Regex("FILE\\s+\"([^\"]+)\"", RegexOption.IGNORE_CASE).findAll(t).forEach { out.add(it.groupValues[1].substringAfterLast('/')) } }
            }
        }
        sigue(principal, 0)
        out.remove(principal)
        return out.toList()
    }

    /**
     * Cuál de los archivos de un .zip es el juego: un .m3u manda (es la lista de discos), luego un .cue, y si no, el primero que el núcleo
     * sepa abrir. null = ninguno.
     */
    fun principal(nombres: List<String>, extensiones: Set<String>): String? {
        val ext = { n: String -> n.substringAfterLast('.', "").lowercase() }
        return nombres.firstOrNull { ext(it) == "m3u" && "m3u" in extensiones }
            ?: nombres.firstOrNull { ext(it) == "cue" && "cue" in extensiones }
            ?: nombres.firstOrNull { ext(it) in extensiones }
    }

    /**
     * La carpeta de un documento del almacenamiento del móvil: «primary:Roms/psx/Juego.cue» → «primary:Roms/psx». En la raíz del
     * volumen, «primary:». null si el id no es una ruta (otros proveedores usan números).
     */
    fun padre(docId: String): String? {
        val i = docId.indexOf(':'); if (i <= 0) return null
        // Solo los del almacenamiento del móvil y de las tarjetas («primary», «home», «1234-ABCD»): los ids de los demás no son rutas
        val vol = docId.substring(0, i)
        if (!(vol.equals("primary", true) || vol.equals("home", true) || vol.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")))) return null
        val ruta = docId.substring(i + 1)
        if (ruta.isEmpty()) return null
        return if ('/' in ruta) docId.substringBeforeLast('/') else docId.substring(0, i + 1)
    }

    /**
     * Los archivos que están junto a una ROM elegida desde una carpeta (nombre en minúsculas → documento). Vacío si la ROM se eligió
     * suelta (Android no deja ver lo que tiene al lado) o si no se puede mirar.
     */
    fun hermanos(c: Context, uri: Uri): Map<String, Pair<String, Uri>> = runCatching {
        if (!DocumentsContract.isTreeUri(uri)) return emptyMap()
        val arbol = DocumentsContract.buildTreeDocumentUri(uri.authority, DocumentsContract.getTreeDocumentId(uri))
        val carpeta = padre(DocumentsContract.getDocumentId(uri)) ?: return emptyMap()
        val hijos = DocumentsContract.buildChildDocumentsUriUsingTree(arbol, carpeta)
        val out = HashMap<String, Pair<String, Uri>>()
        c.contentResolver.query(hijos, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { q ->
            while (q.moveToNext()) {
                val id = q.getString(0) ?: continue; val n = q.getString(1) ?: continue
                out[n.lowercase()] = n to DocumentsContract.buildDocumentUriUsingTree(arbol, id)
            }
        }
        out
    }.getOrDefault(emptyMap())

    /** El texto de un documento pequeño (un .cue o un .m3u: como mucho 256 KB). */
    fun texto(c: Context, uri: Uri): String? = runCatching {
        c.contentResolver.openInputStream(uri)?.use { i -> String(i.readNBytesCompat(256 * 1024), Charsets.UTF_8) }
    }.getOrNull()

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val b = ByteArray(8192)
        while (out.size() < max) { val n = read(b, 0, minOf(b.size, max - out.size())); if (n < 0) break; out.write(b, 0, n) }
        return out.toByteArray()
    }
}
