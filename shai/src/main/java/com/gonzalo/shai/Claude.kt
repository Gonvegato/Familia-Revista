package com.gonzalo.shai

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/*
 * La API de Claude, por HTTP directo (la del «cURL / raw HTTP» de la documentación: `POST /v1/messages`, `POST /v1/messages/batches`,
 * `GET /v1/messages/batches/<id>` y sus resultados en JSONL), con tu clave en la cabecera `x-api-key` (nunca en la dirección ni en un
 * registro). Versión de la API: 2026-10-21 (sin `temperature`/`top_p`/`top_k`, que en ella son un error).
 *
 * Por qué no el SDK de Java: compila y pasa R8 en Android, pero lleva Jackson (reflexión) y OpenTelemetry, y aquí no se puede probar en un
 * Android de verdad; así, lo que prueban los tests es exactamente lo que corre en el móvil.
 */

/** Pedir por la red. Se cambia en las pruebas (una API de mentira: nada sale, nada se gasta). */
interface Transporte {
    class Respuesta(val codigo: Int, val cuerpo: String)
    fun post(url: String, cabeceras: Map<String, String>, cuerpo: String): Respuesta
    fun get(url: String, cabeceras: Map<String, String>): Respuesta
}

class TransporteHttp(private val espera: Int = 30_000, private val lectura: Int = 180_000) : Transporte {
    override fun post(url: String, cabeceras: Map<String, String>, cuerpo: String) = pide(url, "POST", cabeceras, cuerpo)
    override fun get(url: String, cabeceras: Map<String, String>) = pide(url, "GET", cabeceras, null)

    private fun pide(url: String, metodo: String, cabeceras: Map<String, String>, cuerpo: String?): Transporte.Respuesta {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = espera; c.readTimeout = lectura; c.requestMethod = metodo
        c.setRequestProperty("User-Agent", "Shai/$VERSION (Android)")
        cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            if (cuerpo != null) {
                c.doOutput = true
                val b = cuerpo.toByteArray(Charsets.UTF_8)
                c.setFixedLengthStreamingMode(b.size)
                c.outputStream.use { it.write(b) }
            }
            val codigo = try { c.responseCode } catch (e: IOException) { throw IOException("Sin conexión con la API de Claude") }
            val s = (if (codigo in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return Transporte.Respuesta(codigo, s)
        } finally { c.disconnect() }
    }
}

/** Lo que gasta una respuesta (tokens), tal cual lo dice la API en `usage`. */
data class Uso(val entrada: Long = 0, val salida: Long = 0, val cacheEscrita: Long = 0, val cacheLeida: Long = 0) {
    operator fun plus(o: Uso) = Uso(entrada + o.entrada, salida + o.salida, cacheEscrita + o.cacheEscrita, cacheLeida + o.cacheLeida)
    /** Todo lo que entró (para saber si un prompt de Haiku pasa de los 100.000 tokens). */
    val prompt: Long get() = entrada + cacheEscrita + cacheLeida
    companion object {
        fun de(m: Map<String, Any?>) = Uso(m["input_tokens"].comoLong(), m["output_tokens"].comoLong(), m["cache_creation_input_tokens"].comoLong(), m["cache_read_input_tokens"].comoLong())
    }
}

/** Una petición a `/v1/messages`. El [sistema] va en bloques; los marcados con [Bloque.cache] llevan `cache_control` (caché de prompts). */
data class Peticion(
    val modelo: String,
    val sistema: List<Bloque>,
    val usuario: List<Map<String, Any?>>,
    val maxTokens: Int,
    /**
     * El esquema JSON de la respuesta. NO va como salida estructurada (`output_config.format`): con esquemas como los de Shai la API
     * contesta 400 «The compiled grammar is too large». Va en el mensaje, como instrucción, y lo que vuelve se lee con tolerancia
     * ([Respuesta.json]); si no se puede leer, [Motor.pide] lo pide otra vez.
     */
    val esquema: Map<String, Any?>? = null,
    val esfuerzo: String = "low",
    /** La caché de una hora (para los lotes, que pueden tardar más de cinco minutos). */
    val cacheHora: Boolean = false,
) {
    class Bloque(val texto: String, val cache: Boolean = false)

    fun cuerpo(): Map<String, Any?> {
        val ttl = if (cacheHora) mapOf("type" to "ephemeral", "ttl" to "1h") else mapOf("type" to "ephemeral")
        val m = LinkedHashMap<String, Any?>()
        m["model"] = modelo
        m["max_tokens"] = maxTokens.toLong()
        m["system"] = sistema.map { b -> if (b.cache) mapOf("type" to "text", "text" to b.texto, "cache_control" to ttl) else mapOf("type" to "text", "text" to b.texto) }
        m["messages"] = listOf(mapOf("role" to "user", "content" to (if (esquema == null) usuario else usuario + texto(instruccion(esquema)))))
        m["output_config"] = mapOf("effort" to esfuerzo)
        // Sin pensar más de la cuenta: Haiku 5.5 acepta apagarlo (con esfuerzo bajo); Sonnet 5.5 no («disabled» es un 400 en él), y con
        // esfuerzo bajo piensa poco o nada en lo sencillo
        if (modelo.startsWith("claude-haiku")) m["thinking"] = mapOf("type" to "disabled")
        return m
    }

    /** Lo que costaría, como mucho (para no pasarse del tope): la entrada a ojo (un token cada ~3 letras) y la salida entera. */
    fun usoMaximo(): Uso {
        val letras = sistema.sumOf { it.texto.length } + usuario.sumOf { b -> b["text"].comoTexto().length + (b["source"].comoMapa()["data"].comoTexto().length / 600) }
        return Uso(entrada = (letras / 3L) + 50, salida = maxTokens.toLong())
    }

    companion object {
        private const val ABRE = "<<<ESQUEMA\n"
        private const val CIERRA = "\nESQUEMA>>>"

        fun instruccion(esquema: Map<String, Any?>) = "FORMATO DE LA RESPUESTA: contesta SOLO con un objeto JSON válido, sin nada antes ni después y sin ```, " +
            "con TODAS las claves de este esquema JSON (los textos, en español; si algo no toca, cadena vacía, 0, false o lista vacía):\n" + ABRE + Json.escribe(esquema) + CIERRA

        /** El esquema que va en el mensaje de una petición (para las API de mentira y las pruebas). */
        fun esquemaDe(cuerpo: Map<String, Any?>): Map<String, Any?>? {
            val t = cuerpo["messages"].comoLista().flatMap { it.comoMapa()["content"].comoLista() }.joinToString("\n") { it.comoMapa()["text"].comoTexto() }
            val i = t.indexOf(ABRE).takeIf { it >= 0 } ?: return null
            val j = t.indexOf(CIERRA, i).takeIf { it >= 0 } ?: return null
            return runCatching { Json.lee(t.substring(i + ABRE.length, j)).comoMapa() }.getOrNull()
        }

        fun texto(t: String): Map<String, Any?> = mapOf("type" to "text", "text" to t)
        fun imagen(base64: String, tipo: String = "image/jpeg"): Map<String, Any?> = mapOf("type" to "image", "source" to mapOf("type" to "base64", "media_type" to tipo, "data" to base64))
    }
}

/** Lo que contesta Claude: el texto (en salida estructurada, el JSON) y lo que gastó. */
data class Respuesta(val texto: String, val uso: Uso, val parada: String, val modelo: String) {
    /** El JSON de la respuesta, aunque venga entre ``` o con algo de texto alrededor. */
    fun json(): Map<String, Any?> {
        val t = texto.trim()
        val i = t.indexOf('{'); val j = t.lastIndexOf('}')
        val m = runCatching { Json.lee(t).comoMapa() }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: (if (i >= 0 && j > i) runCatching { Json.lee(t.substring(i, j + 1)).comoMapa() }.getOrNull()?.takeIf { it.isNotEmpty() } else null)
        return m ?: throw Claude.Fallo(if (parada == "max_tokens") "La respuesta se cortó (era demasiado larga)." else "La respuesta no se pudo leer (no venía en JSON).", true)
    }
}

class Claude(private val clave: () -> String, private val red: Transporte = TransporteHttp(), private val base: String = "https://api.anthropic.com") {
    open class Fallo(msg: String, val reintentable: Boolean = false) : Exception(msg)

    private fun cabeceras(): Map<String, String> {
        val k = clave().trim()
        if (k.isBlank()) throw Fallo("Falta tu clave de la API de Claude (Ajustes → IA).")
        return mapOf("x-api-key" to k, "anthropic-version" to VERSION_API, "content-type" to "application/json")
    }

    /** Una petición y su respuesta (bloquea: llámalo fuera del hilo principal). */
    fun mensaje(p: Peticion): Respuesta {
        val r = try { red.post("$base/v1/messages", cabeceras(), Json.escribe(p.cuerpo())) } catch (e: IOException) { throw Fallo("Sin conexión con la API de Claude.", true) }
        if (r.codigo !in 200..299) throw error(r)
        return deMensaje(Json.lee(r.cuerpo).comoMapa())
    }

    /**
     * Una petición tal cual (el cuerpo ya hecho) y la respuesta entera, con todos sus bloques: para las herramientas de servidor (la
     * búsqueda web, [Clubes]). Si la búsqueda web está apagada en la organización, [Clubes.Desactivada].
     */
    fun crudo(cuerpo: Map<String, Any?>): Map<String, Any?> {
        val r = try { red.post("$base/v1/messages", cabeceras(), Json.escribe(cuerpo)) } catch (e: IOException) { throw Fallo("Sin conexión con la API de Claude.", true) }
        if (r.codigo !in 200..299) {
            val msg = runCatching { Json.lee(r.cuerpo).comoMapa()["error"].comoMapa()["message"].comoTexto() }.getOrDefault("").lowercase()
            if (r.codigo == 400 && "web search" in msg && ("not enabled" in msg || "disabled" in msg)) throw Clubes.Desactivada()
            throw error(r)
        }
        return Json.lee(r.cuerpo).comoMapa()
    }

    /** Manda un lote (a mitad de precio; vuelve en minutos, como mucho en 24 horas). Devuelve su id. */
    fun creaLote(peticiones: List<Pair<String, Peticion>>): String {
        val cuerpo = mapOf("requests" to peticiones.map { (id, p) -> mapOf("custom_id" to id, "params" to p.cuerpo()) })
        val r = try { red.post("$base/v1/messages/batches", cabeceras(), Json.escribe(cuerpo)) } catch (e: IOException) { throw Fallo("Sin conexión con la API de Claude.", true) }
        if (r.codigo !in 200..299) throw error(r)
        return Json.lee(r.cuerpo).comoMapa()["id"].comoTexto().ifBlank { throw Fallo("La API no devolvió el lote.") }
    }

    /** Cómo va un lote: `in_progress`, `canceling` o `ended` (y entonces, dónde están sus resultados). */
    class EstadoLote(val estado: String, val resultados: String)

    fun lote(id: String): EstadoLote {
        val r = try { red.get("$base/v1/messages/batches/$id", cabeceras()) } catch (e: IOException) { throw Fallo("Sin conexión con la API de Claude.", true) }
        if (r.codigo !in 200..299) throw error(r)
        val m = Json.lee(r.cuerpo).comoMapa()
        return EstadoLote(m["processing_status"].comoTexto(), m["results_url"].comoTexto())
    }

    /** Lo que volvió de cada petición de un lote terminado: su id y su respuesta, o por qué no hay (`errored`, `canceled`, `expired`). */
    class ResultadoLote(val id: String, val respuesta: Respuesta?, val fallo: String)

    fun resultados(id: String, url: String = ""): List<ResultadoLote> {
        // (la dirección de los resultados es la que da la API; si no, la de siempre)
        val u = url.takeIf { it.startsWith("$base/") } ?: "$base/v1/messages/batches/$id/results"
        val r = try { red.get(u, cabeceras()) } catch (e: IOException) { throw Fallo("Sin conexión con la API de Claude.", true) }
        if (r.codigo !in 200..299) throw error(r)
        return r.cuerpo.lineSequence().filter { it.isNotBlank() }.mapNotNull { l ->
            val m = runCatching { Json.lee(l).comoMapa() }.getOrNull() ?: return@mapNotNull null
            val res = m["result"].comoMapa()
            val tipo = res["type"].comoTexto()
            if (tipo == "succeeded") ResultadoLote(m["custom_id"].comoTexto(), runCatching { deMensaje(res["message"].comoMapa()) }.getOrNull(), "")
            else ResultadoLote(m["custom_id"].comoTexto(), null, tipo.ifBlank { "errored" })
        }.toList()
    }

    private fun deMensaje(m: Map<String, Any?>): Respuesta {
        val parada = m["stop_reason"].comoTexto()
        val uso = Uso.de(m["usage"].comoMapa())
        if (parada == "refusal") throw Rechazo(uso, m["model"].comoTexto())
        // (lo que vale es el texto: los bloques de «thinking», si los hay, se dejan)
        val texto = m["content"].comoLista().map { it.comoMapa() }.filter { it["type"] == "text" }.joinToString("") { it["text"].comoTexto() }
        return Respuesta(texto, uso, parada, m["model"].comoTexto())
    }

    /** Claude no quiso escribir eso (el filtro de seguridad). Lo gastado, gastado está. */
    class Rechazo(val uso: Uso, val modelo: String) : Exception("Claude no ha querido escribir esto. Prueba a contarlo de otra manera.")

    private fun error(r: Transporte.Respuesta): Fallo {
        val msg = runCatching { Json.lee(r.cuerpo).comoMapa()["error"].comoMapa()["message"].comoTexto() }.getOrDefault("")
        return when (r.codigo) {
            401 -> Fallo("La clave de la API no vale (¿la copiaste entera?).")
            403 -> Fallo("La API no deja usar esa clave para esto." + (if (msg.isNotBlank()) " ($msg)" else ""))
            429 -> Fallo("Demasiadas peticiones seguidas: espera un poco.", true)
            529, 500, 502, 503 -> Fallo("La API de Claude está saturada: prueba en un rato.", true)
            402 -> Fallo("Problema de facturación en tu cuenta de la API (¿se han acabado los créditos del mes?)." + (if (msg.isNotBlank()) " ($msg)" else ""))
            413 -> Fallo("La petición es demasiado grande (¿una foto enorme?).")
            400 -> Fallo("La API no aceptó la petición: ${msg.take(200)}")
            else -> Fallo("La API respondió ${r.codigo}" + (if (msg.isNotBlank()) ": ${msg.take(200)}" else "."), r.codigo >= 500)
        }
    }

    companion object {
        const val VERSION_API = "2026-10-21"
        const val HAIKU = "claude-haiku-5-5"
        const val SONNET = "claude-sonnet-5-5"
    }
}
