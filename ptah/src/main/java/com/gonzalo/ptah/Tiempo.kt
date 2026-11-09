package com.gonzalo.ptah

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/*
 * El tiempo, de Open-Meteo (open-meteo.com): gratis y sin clave para un uso no comercial (menos de 10.000 llamadas al día), con los datos
 * bajo la licencia CC-BY 4.0: por eso se dice «Datos: Open-Meteo» en Ajustes, en el editor y, discreto, en el propio widget. La ciudad se
 * busca con su geocoding; o, si lo permites, se usa la ubicación aproximada de este móvil (que no sale de él). Lo bajado se guarda en el
 * móvil (caché) y se vuelve a pedir cada 30–60 minutos ([Refresco]), nunca más de una vez cada 30 minutos por sitio.
 */

data class Lugar(val nombre: String, val region: String, val pais: String, val lat: Double, val lon: Double) {
    /** «León · Castilla y León, España». */
    val detalle: String get() = listOf(region, pais).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

data class DiaPrevision(val fecha: LocalDate, val codigo: Int, val max: Double, val min: Double, val lluvia: Int?)

data class Prevision(
    val temp: Double, val sensacion: Double?, val humedad: Int?, val viento: Double?, val codigo: Int, val esDia: Boolean,
    /** La hora de los datos «de ahora» (en la hora de allí). */
    val hora: LocalDateTime, val zona: String, val dias: List<DiaPrevision>,
    /** Cuándo se bajó (ms). */
    val bajada: Long,
)

object OpenMeteo {
    class Error(msg: String) : Exception(msg)
    const val ATRIBUCION = "Datos: Open-Meteo"
    const val LICENCIA = "Datos del tiempo de Open-Meteo.com, con la licencia CC-BY 4.0 (open-meteo.com/en/license)."

    fun urlBuscar(nombre: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(nombre.trim(), "UTF-8")}&count=8&language=es&format=json"

    fun urlPrevision(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${"%.4f".format(java.util.Locale.US, lat)}&longitude=${"%.4f".format(java.util.Locale.US, lon)}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=7"

    /** Los sitios que da el geocoding (vacío si no hay ninguno: Open-Meteo entonces no trae «results»). */
    fun lugares(json: String): List<Lugar> {
        val o = JSONObject(json)
        if (o.optBoolean("error")) throw Error(o.optString("reason").ifBlank { "Open-Meteo no contesta bien" })
        val a = o.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val r = a.optJSONObject(i) ?: return@mapNotNull null
            if (!r.has("latitude") || !r.has("longitude")) return@mapNotNull null
            Lugar(r.optString("name"), r.optString("admin1"), r.optString("country"), r.optDouble("latitude"), r.optDouble("longitude"))
        }
    }

    /** La previsión que da la API («current» y «daily»); [bajada]: cuándo se pidió. */
    fun prevision(json: String, bajada: Long = System.currentTimeMillis()): Prevision {
        val o = JSONObject(json)
        if (o.optBoolean("error")) throw Error(o.optString("reason").ifBlank { "Open-Meteo no contesta bien" })
        val c = o.optJSONObject("current") ?: throw Error("Open-Meteo no ha mandado el tiempo de ahora")
        val d = o.optJSONObject("daily")
        val dias = ArrayList<DiaPrevision>()
        if (d != null) {
            val t = d.optJSONArray("time")
            val cod = d.optJSONArray("weather_code"); val mx = d.optJSONArray("temperature_2m_max"); val mn = d.optJSONArray("temperature_2m_min")
            val ll = d.optJSONArray("precipitation_probability_max")
            for (i in 0 until (t?.length() ?: 0)) {
                val f = runCatching { LocalDate.parse(t!!.getString(i)) }.getOrNull() ?: continue
                if (mx == null || mn == null || mx.isNull(i) || mn.isNull(i)) continue
                dias += DiaPrevision(f, cod?.optInt(i, 0) ?: 0, mx.optDouble(i), mn.optDouble(i), ll?.takeIf { !it.isNull(i) }?.optInt(i))
            }
        }
        fun dbl(k: String) = if (c.has(k) && !c.isNull(k)) c.optDouble(k) else null
        return Prevision(
            temp = dbl("temperature_2m") ?: throw Error("Open-Meteo no ha mandado la temperatura"),
            sensacion = dbl("apparent_temperature"), humedad = dbl("relative_humidity_2m")?.toInt(), viento = dbl("wind_speed_10m"),
            codigo = c.optInt("weather_code", 0), esDia = c.optInt("is_day", 1) == 1,
            hora = runCatching { LocalDateTime.parse(c.getString("time")) }.getOrElse { LocalDateTime.now(ZoneOffset.UTC) },
            zona = o.optString("timezone"), dias = dias, bajada = bajada,
        )
    }

    /** Pide una dirección (bloquea). */
    fun pide(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Widgets (app de widgets para Android)")
        try {
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw Error(runCatching { JSONObject(txt).optString("reason") }.getOrNull()?.ifBlank { null } ?: "Open-Meteo: error $code")
            return txt
        } finally { c.disconnect() }
    }
}

/** Los iconos del tiempo de Widgets (dibujados aquí, de línea, como los de una revista). */
enum class IconoTiempo { DESPEJADO, POCAS_NUBES, NUBES, CUBIERTO, NIEBLA, LLOVIZNA, LLUVIA, CHUBASCOS, NIEVE, TORMENTA }

/** Los códigos del tiempo de la OMM (WMO) que da Open-Meteo: lo que dicen y su icono. */
object Wmo {
    data class Estado(val texto: String, val icono: IconoTiempo)

    fun de(codigo: Int): Estado = when (codigo) {
        0 -> Estado("Despejado", IconoTiempo.DESPEJADO)
        1 -> Estado("Casi despejado", IconoTiempo.POCAS_NUBES)
        2 -> Estado("Algunas nubes", IconoTiempo.NUBES)
        3 -> Estado("Cubierto", IconoTiempo.CUBIERTO)
        45 -> Estado("Niebla", IconoTiempo.NIEBLA)
        48 -> Estado("Niebla con escarcha", IconoTiempo.NIEBLA)
        51 -> Estado("Llovizna débil", IconoTiempo.LLOVIZNA)
        53 -> Estado("Llovizna", IconoTiempo.LLOVIZNA)
        55 -> Estado("Llovizna intensa", IconoTiempo.LLOVIZNA)
        56, 57 -> Estado("Llovizna helada", IconoTiempo.LLOVIZNA)
        61 -> Estado("Lluvia débil", IconoTiempo.LLUVIA)
        63 -> Estado("Lluvia", IconoTiempo.LLUVIA)
        65 -> Estado("Lluvia fuerte", IconoTiempo.LLUVIA)
        66, 67 -> Estado("Lluvia helada", IconoTiempo.LLUVIA)
        71 -> Estado("Nieve débil", IconoTiempo.NIEVE)
        73 -> Estado("Nieve", IconoTiempo.NIEVE)
        75 -> Estado("Nieve intensa", IconoTiempo.NIEVE)
        77 -> Estado("Granos de nieve", IconoTiempo.NIEVE)
        80 -> Estado("Chubascos débiles", IconoTiempo.CHUBASCOS)
        81 -> Estado("Chubascos", IconoTiempo.CHUBASCOS)
        82 -> Estado("Chubascos fuertes", IconoTiempo.CHUBASCOS)
        85, 86 -> Estado("Chubascos de nieve", IconoTiempo.NIEVE)
        95 -> Estado("Tormenta", IconoTiempo.TORMENTA)
        96, 99 -> Estado("Tormenta con granizo", IconoTiempo.TORMENTA)
        else -> Estado("Tiempo variable", IconoTiempo.NUBES)
    }
}

/** Lo bajado del tiempo, en el móvil (una previsión por sitio), y cuándo toca pedirlo otra vez. */
object CacheTiempo {
    /** Cada cuánto se vuelve a pedir (y como mucho una vez cada [MINIMO] por sitio). */
    const val CADA = 45 * 60_000L
    const val MINIMO = 30 * 60_000L

    /** En las pruebas: la red de mentira. */
    @Volatile var red: (String) -> String = { OpenMeteo.pide(it) }

    fun clave(lat: Double, lon: Double) = "%.2f_%.2f".format(java.util.Locale.US, lat, lon)
    private fun dir(ctx: Context) = File(ctx.filesDir, "tiempo").apply { mkdirs() }
    private fun archivo(ctx: Context, lat: Double, lon: Double) = File(dir(ctx), clave(lat, lon) + ".json")

    /** Lo guardado de ese sitio (sin red). */
    fun guardada(ctx: Context, lat: Double, lon: Double): Prevision? {
        val f = archivo(ctx, lat, lon)
        if (!f.isFile) return null
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return null
        return runCatching { OpenMeteo.prevision(o.getString("r"), o.optLong("t")) }.getOrNull()
    }

    /** Pide la previsión si toca (o si [forzar], respetando el mínimo). Devuelve la que haya. Bloquea (red). */
    fun refresca(ctx: Context, lat: Double, lon: Double, forzar: Boolean = false): Prevision? {
        val vieja = guardada(ctx, lat, lon)
        val edad = System.currentTimeMillis() - (vieja?.bajada ?: 0L)
        if (vieja != null && (edad < MINIMO || (!forzar && edad < CADA))) return vieja
        return try {
            val txt = red(OpenMeteo.urlPrevision(lat, lon))
            val p = OpenMeteo.prevision(txt)
            archivo(ctx, lat, lon).writeText(JSONObject().put("t", p.bajada).put("r", txt).toString())
            p
        } catch (e: Exception) { vieja }
    }

    fun busca(nombre: String): List<Lugar> = OpenMeteo.lugares(red(OpenMeteo.urlBuscar(nombre)))
}

/**
 * Dónde mirar el tiempo de un diseño: la ciudad elegida (va con el diseño: nombre y coordenadas) o, con «donde estés», la ubicación
 * aproximada de este móvil, que se guarda solo aquí ([Ubicacion]).
 */
fun sitioDe(ctx: Context, d: Diseno): Pair<Double, Double>? =
    if (d.texto("lugar") == "aqui") Ubicacion.ultima(ctx)?.let { it.first to it.second }
    else if (d.opciones.containsKey("lat") && d.opciones.containsKey("lon")) d.decimal("lat") to d.decimal("lon") else null

/** La ubicación aproximada de este móvil (con tu permiso): solo para el tiempo «donde estés». No va a la cuenta. */
object Ubicacion {
    private fun p(ctx: Context) = ctx.getSharedPreferences("ptah_ubicacion", Context.MODE_PRIVATE)

    fun ultima(ctx: Context): Triple<Double, Double, Long>? {
        val p = p(ctx)
        if (!p.contains("lat")) return null
        return Triple(p.getFloat("lat", 0f).toDouble(), p.getFloat("lon", 0f).toDouble(), p.getLong("t", 0L))
    }

    fun permitida(ctx: Context) = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Mira dónde está el móvil (lo último que sepa Android, aproximado). Con la app delante: Android no la da en segundo plano. */
    @android.annotation.SuppressLint("MissingPermission")
    fun actualiza(ctx: Context): Boolean {
        if (!permitida(ctx)) return false
        val lm = ctx.getSystemService(android.location.LocationManager::class.java) ?: return false
        val l = listOf(android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.PASSIVE_PROVIDER, android.location.LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return false
        // (redondeada a unos 1–2 km: para el tiempo sobra)
        p(ctx).edit().putFloat("lat", (Math.round(l.latitude * 50) / 50.0).toFloat()).putFloat("lon", (Math.round(l.longitude * 50) / 50.0).toFloat()).putLong("t", System.currentTimeMillis()).apply()
        return true
    }
}
