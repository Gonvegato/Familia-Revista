package com.gonzalo.marfil

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * SesKobo: la posición de cada libro en un servidor KOSync (el protocolo de sincronización de KOReader), el mismo que usa NickelInkSync
 * en el Kobo. Así lo que lees en el Kobo se abre aquí por donde ibas, y al revés. El servidor conoce cada libro por la huella de su
 * archivo ([huella]): tiene que ser el mismo archivo en los dos sitios. La contraseña nunca se guarda: solo su MD5, que es lo que pide
 * el protocolo (y lo que se manda en cada petición).
 */
object KoSync {
    const val SERVIDOR = "https://kosync.eu"
    private const val PREFS = "kosync"
    const val NOMBRE = "SesKobo"

    class Error(msg: String) : Exception(msg)

    data class Posicion(val xp: String, val fraccion: Double, val dispositivo: String, val idDispositivo: String, val hora: Long)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun servidor(ctx: Context): String = prefs(ctx).getString("servidor", SERVIDOR).orEmpty().ifBlank { SERVIDOR }.trimEnd('/')
    fun usuario(ctx: Context): String = prefs(ctx).getString("usuario", "").orEmpty()
    private fun clave(ctx: Context): String = prefs(ctx).getString("clave", "").orEmpty()
    fun activo(ctx: Context) = BuildConfig.SESKOBO && usuario(ctx).isNotBlank() && clave(ctx).isNotBlank()
    /** Para el Kobo (config.ini de NickelInkSync): servidor, usuario y la clave (MD5 de la contraseña). */
    fun datosParaElKobo(ctx: Context): Triple<String, String, String>? = if (activo(ctx)) Triple(servidor(ctx), usuario(ctx), clave(ctx)) else null

    /** Este móvil para el servidor (una vez por instalación). */
    fun idDispositivo(ctx: Context): String = prefs(ctx).getString("id", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").uppercase().also {
        prefs(ctx).edit().putString("id", it).apply()
    }

    fun md5(s: String): String = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private class Respuesta(val codigo: Int, val cuerpo: String)

    private fun pide(metodo: String, url: String, usuario: String?, clave: String?, cuerpo: String? = null): Respuesta {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = metodo
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.koreader.v1+json")
            if (usuario != null && clave != null) { c.setRequestProperty("x-auth-user", usuario); c.setRequestProperty("x-auth-key", clave) }
            if (cuerpo != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(cuerpo.toByteArray()) }
            }
            val cod = c.responseCode
            val txt = runCatching { (if (cod in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            return Respuesta(cod, txt)
        } catch (e: java.io.IOException) {
            throw Error("No hay conexión con el servidor de posiciones.")
        } finally { c.disconnect() }
    }

    /**
     * Entra con tu usuario del servidor (o lo crea si no existe, como hace KOReader). Guarda el usuario y el MD5 de la contraseña.
     * Devuelve true si la cuenta se ha creado ahora.
     */
    fun entra(ctx: Context, servidor: String, usuario: String, contrasena: String): Boolean {
        val s = servidor.trim().trimEnd('/').ifBlank { SERVIDOR }
        // (Android no deja hablar con un servidor sin cifrar: http:// no funcionaría y parecería que no hay conexión)
        val local = Regex("^http://(127\\.0\\.0\\.1|localhost)(:\\d+)?$").matches(s) // (solo en las pruebas)
        if (!(s.startsWith("https://") || local) || runCatching { URL(s) }.isFailure) throw Error("El servidor tiene que ser una dirección https:// (como ${SERVIDOR}).")
        val u = usuario.trim()
        if (u.isBlank() || contrasena.isBlank()) throw Error("Escribe un usuario y una contraseña.")
        val k = md5(contrasena)
        var creada = false
        val a = pide("GET", "$s/users/auth", u, k)
        if (a.codigo != 200) {
            val r = pide("POST", "$s/users/create", null, null, JSONObject().put("username", u).put("password", k).toString())
            when (r.codigo) {
                201 -> creada = true
                402 -> throw Error("Ese usuario ya existe con otra contraseña.")
                else -> throw Error("El servidor no deja entrar (${r.codigo}).")
            }
        }
        prefs(ctx).edit().putString("servidor", s).putString("usuario", u).putString("clave", k).apply()
        return creada
    }

    fun sal(ctx: Context) = prefs(ctx).edit().remove("usuario").remove("clave").apply()

    /** La posición guardada en el servidor para el libro con huella [documento] (null si no hay). */
    fun baja(ctx: Context, documento: String): Posicion? {
        if (!activo(ctx)) return null
        val r = pide("GET", servidor(ctx) + "/syncs/progress/" + URLEncoder.encode(documento, "UTF-8"), usuario(ctx), clave(ctx))
        if (r.codigo == 401) throw Error("El servidor de posiciones no reconoce tu usuario: vuelve a entrar en Ajustes → Lectura.")
        if (r.codigo != 200) return null
        val o = runCatching { JSONObject(r.cuerpo) }.getOrNull() ?: return null
        val xp = o.optString("progress"); if (xp.isBlank()) return null
        return Posicion(xp, o.optDouble("percentage", 0.0), o.optString("device"), o.optString("device_id"), o.optLong("timestamp", 0L) * 1000)
    }

    /** Sube la posición de este móvil para el libro [documento]. */
    fun sube(ctx: Context, documento: String, xp: String, fraccion: Double) {
        if (!activo(ctx)) return
        val cuerpo = JSONObject().put("document", documento).put("progress", xp).put("percentage", Math.round(fraccion * 10000) / 10000.0)
            .put("device", NOMBRE).put("device_id", idDispositivo(ctx)).toString()
        val r = pide("PUT", servidor(ctx) + "/syncs/progress", usuario(ctx), clave(ctx), cuerpo)
        if (r.codigo == 401) throw Error("El servidor de posiciones no reconoce tu usuario: vuelve a entrar en Ajustes → Lectura.")
        if (r.codigo !in 200..299) throw Error("El servidor de posiciones no ha guardado la posición (${r.codigo}).")
        prefs(ctx).edit().putLong("subido_$documento", System.currentTimeMillis()).putString("xp_$documento", xp).apply()
    }

    /** Cuándo subió este móvil la posición de [documento] por última vez, y cuál (para no saltar a la propia). */
    fun ultimaSubida(ctx: Context, documento: String): Pair<Long, String> =
        prefs(ctx).getLong("subido_$documento", 0L) to prefs(ctx).getString("xp_$documento", "").orEmpty()

    /** Tramos de 1 KiB que mira KOReader: en 0, 1 KiB, 4 KiB, 16 KiB… (cada uno cuatro veces el anterior, hasta 1 GiB). */
    val TRAMOS: List<Long> = listOf(0L) + (0..10).map { 1024L shl (2 * it) }

    /**
     * La huella con la que KOSync (y KOReader, y NickelInkSync) conoce un libro: el MD5 de esos tramos del archivo, parando en el
     * primero que pase del final. Se guarda por archivo y tamaño (no se vuelve a leer el libro cada vez).
     */
    fun huella(ctx: Context, uri: Uri): String? {
        val tam = runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L
        // (con la hora del archivo: otro libro en el mismo sitio y del mismo tamaño no se toma por el de antes)
        val hora = runCatching { if (uri.scheme == "file") java.io.File(uri.path!!).lastModified() else androidx.documentfile.provider.DocumentFile.fromSingleUri(ctx, uri)?.lastModified() }
            .getOrNull() ?: 0L
        val k = "h_" + md5("$uri|$tam|$hora")
        if (tam >= 0) prefs(ctx).getString(k, null)?.let { return it }
        val h = runCatching { ctx.contentResolver.openInputStream(uri)?.use { huellaDe(it) } }.getOrNull() ?: return null
        if (tam >= 0) prefs(ctx).edit().putString(k, h).apply()
        return h
    }

    /** La huella de KOReader de un archivo leído de principio a fin (salta hasta cada tramo). */
    fun huellaDe(input: java.io.InputStream): String {
        val md = MessageDigest.getInstance("MD5")
        var pos = 0L
        val buf = ByteArray(1024)
        for (t in TRAMOS) {
            while (pos < t) { val s = input.skip(t - pos); if (s <= 0) { if (input.read() < 0) return hex(md.digest()); pos++ } else pos += s }
            var n = 0
            while (n < 1024) { val r = input.read(buf, n, 1024 - n); if (r < 0) break; n += r }
            pos += n
            if (n == 0) break
            md.update(buf, 0, n)
            if (n < 1024) break
        }
        return hex(md.digest())
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
