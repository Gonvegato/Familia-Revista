package com.gonzalo.familia.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Tu kDrive (la API de Infomaniak, la misma que usan las apps de Android): los archivos grandes (libros, ROMs, partidas) y las
 * versiones de las apps, en «Mi carpeta». El token y el número del kDrive son claves de la cuenta (`kdrive_token`, `kdrive_id`):
 * se ponen una vez en Azabache y los tienen todas.
 */
class KDrive(private val token: () -> String = { LaFamilia.clave("kdrive_token") }, private val drive: () -> String = { LaFamilia.clave("kdrive_id") }) {
    class Fallo(msg: String) : IOException(msg)
    class Archivo(val nombre: String, val id: Long, val carpeta: Boolean, val visibilidad: String, val tamano: Long)

    /** En las pruebas, otro servidor. */
    var api = System.getProperty("familia.kdrive") ?: "https://api.infomaniak.com"

    val activo: Boolean get() = token().isNotBlank() && drive().isNotBlank()

    @Volatile private var casa = 0L
    @Volatile private var carpetaVersiones = 0L

    private fun abre(url: String): HttpURLConnection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000; readTimeout = 60_000
        setRequestProperty("Authorization", "Bearer ${token()}")
    }

    private fun llama(metodo: String, ruta: String, cuerpo: String? = null): Map<String, Any?> {
        val c = abre(api + ruta)
        c.requestMethod = metodo
        if (cuerpo != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(cuerpo.toByteArray()) }
        }
        return respuesta(c)
    }

    /** {"result":"success","data":…} o {"result":"error","error":{"description":…}} */
    private fun respuesta(c: HttpURLConnection): Map<String, Any?> {
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val o = runCatching { Json.lee(txt) }.getOrNull().comoMapa()
        if (code !in 200..299 || o["result"].comoTexto() == "error") {
            val e = o["error"].comoMapa()
            val por = e["description"].comoTexto().ifBlank { e["code"].comoTexto() }
            throw Fallo(when (code) {
                401 -> "kDrive no acepta el token (revísalo o crea otro)"
                403 -> "El token no tiene permiso para este kDrive"
                404 -> "No encuentro ese kDrive (revisa su número)"
                else -> "kDrive: ${por.ifBlank { "error $code" }}"
            })
        }
        return o
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    /** Lo que hay dentro de una carpeta. */
    fun dentro(carpeta: Long): List<Archivo> {
        val out = ArrayList<Archivo>()
        var cursor = ""
        do {
            val q = if (cursor.isNotEmpty()) "?cursor=${enc(cursor)}" else ""
            val o = llama("GET", "/3/drive/${drive()}/files/$carpeta/files$q")
            o["data"].comoLista().forEach { x -> val m = x.comoMapa(); out += Archivo(m["name"].comoTexto(), m["id"].comoLong(), m["type"].comoTexto() == "dir", m["visibility"].comoTexto(), m["size"].comoLong()) }
            cursor = if (o["has_more"].comoBool()) o["cursor"].comoTexto() else ""
        } while (cursor.isNotEmpty() && out.size < 5000)
        return out
    }

    /** Tu carpeta privada («Mi carpeta»): en la raíz del kDrive no se puede escribir. Es la única con visibilidad «is_private_space». */
    fun miCarpeta(): Long {
        if (casa > 0) return casa
        val raiz = dentro(1)
        val h = raiz.firstOrNull { it.carpeta && it.visibilidad == "is_private_space" }
            ?: throw Fallo("No encuentro tu carpeta privada en el kDrive (hay: ${raiz.joinToString { it.nombre }.ifBlank { "nada" }})")
        casa = h.id
        return h.id
    }

    /** Comprueba el token y el kDrive. */
    fun prueba(): String { casa = 0; carpetaVersiones = 0; miCarpeta(); return "Conectado a tu kDrive" }

    /** Los archivos de «Mi carpeta/versiones» (nombre → id): ahí está cada versión de las apps. */
    fun versiones(carpeta: String = System.getenv("KDRIVE_CARPETA")?.takeIf { it.isNotBlank() } ?: "versiones"): List<Archivo> {
        if (carpetaVersiones > 0) runCatching { return dentro(carpetaVersiones).filter { !it.carpeta } }.onFailure { carpetaVersiones = 0 }
        val d = dentro(miCarpeta()).firstOrNull { it.carpeta && it.nombre == carpeta } ?: return emptyList()
        carpetaVersiones = d.id
        return dentro(d.id).filter { !it.carpeta }
    }

    /**
     * Sube un archivo a «Mi carpeta/[ruta]/[nombre]» (kDrive crea las carpetas que falten). Si ya hay uno con ese nombre, queda como
     * versión nueva del mismo. Devuelve su id. Los de más de 5 MB, a trozos (como la app oficial).
     */
    fun sube(ruta: String, nombre: String, tamano: Long, progreso: (Long) -> Unit = {}, entrada: () -> InputStream): Long {
        val d = drive(); val h = miCarpeta()
        if (tamano <= TROZOS_DESDE) {
            val q = "directory_id=$h&directory_path=${enc(ruta)}&file_name=${enc(nombre)}&total_size=$tamano&conflict=version"
            val c = abre("$api/3/drive/$d/upload?$q")
            c.requestMethod = "POST"; c.readTimeout = 300_000; c.doOutput = true
            c.setFixedLengthStreamingMode(tamano)
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.outputStream.use { o -> entrada().use { it.copyTo(o) } }
            val id = respuesta(c)["data"].comoMapa()["id"].comoLong()
            progreso(tamano)
            return id.takeIf { it > 0 } ?: throw Fallo("kDrive no ha devuelto el archivo subido")
        }
        val trozo = maxOf(1L * 1024 * 1024, (tamano + 199) / 200)
        val total = ((tamano + trozo - 1) / trozo).toInt()
        val inicio = llama("POST", "/3/drive/$d/upload/session/start", Json.escribe(mapOf(
            "conflict" to "version", "directory_id" to h, "directory_path" to ruta, "file_name" to nombre,
            "last_modified_at" to System.currentTimeMillis() / 1000, "total_chunks" to total, "total_size" to tamano)))["data"].comoMapa()
        val tok = inicio["token"].comoTexto().ifBlank { throw Fallo("kDrive no ha abierto la subida") }
        val host = inicio["upload_url"].comoTexto().ifBlank { api }.trimEnd('/')
        var enviado = 0L
        entrada().use { inp ->
            for (n in 1..total) {
                val len = if (n == total) (tamano - trozo * (total - 1)).toInt() else trozo.toInt()
                val buf = ByteArray(len)
                var r = 0
                while (r < len) { val k = inp.read(buf, r, len - r); if (k < 0) throw Fallo("El archivo se ha acabado antes de tiempo"); r += k }
                var intentos = 0
                while (true) {
                    try {
                        val c = abre("$host/3/drive/$d/upload/session/$tok/chunk?chunk_number=$n&chunk_size=$len")
                        c.requestMethod = "POST"; c.readTimeout = 300_000; c.doOutput = true
                        c.setFixedLengthStreamingMode(len)
                        c.setRequestProperty("Content-Type", "application/octet-stream")
                        c.outputStream.use { it.write(buf) }
                        respuesta(c)
                        break
                    } catch (e: IOException) {
                        if (e is Fallo || ++intentos >= 3) throw e
                        Thread.sleep(1500L * intentos)
                    }
                }
                enviado += len; progreso(enviado)
            }
        }
        val fin = llama("POST", "/3/drive/$d/upload/session/$tok/finish")["data"].comoMapa()
        return fin["file"].comoMapa()["id"].comoLong().takeIf { it > 0 } ?: throw Fallo("kDrive no ha cerrado la subida")
    }

    /** Baja un archivo a [destino] (primero a un temporal: un corte no deja un archivo a medias). */
    fun baja(id: Long, destino: File, progreso: (Long, Long) -> Unit = { _, _ -> }) {
        val c = abre("$api/2/drive/${drive()}/files/$id/download")
        c.readTimeout = 300_000
        if (c.responseCode !in 200..299) { respuesta(c); throw Fallo("kDrive: error ${c.responseCode}") }
        val total = c.contentLengthLong
        destino.parentFile?.mkdirs()
        val tmp = File(destino.path + ".part")
        c.inputStream.use { inp -> tmp.outputStream().use { o ->
            val buf = ByteArray(64 * 1024); var n = 0L
            while (true) { val k = inp.read(buf); if (k < 0) break; o.write(buf, 0, k); n += k; progreso(n, total) }
        } }
        if (total > 0 && tmp.length() != total) { tmp.delete(); throw Fallo("kDrive: la descarga ha quedado incompleta") }
        if (!tmp.renameTo(destino)) { tmp.copyTo(destino, true); tmp.delete() }
    }

    /** Borra un archivo (a la papelera del kDrive). Uno que ya no está no es un fallo. */
    fun borra(id: Long) {
        try { llama("DELETE", "/2/drive/${drive()}/files/$id") } catch (e: Fallo) { if (e.message?.startsWith("No encuentro") != true) throw e }
    }

    companion object {
        const val TROZOS_DESDE = 5L * 1024 * 1024
    }
}
