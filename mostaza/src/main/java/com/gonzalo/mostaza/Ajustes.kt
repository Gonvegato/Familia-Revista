package com.gonzalo.mostaza

import android.content.Context
import com.gonzalo.mostaza.EspejoCuenta.Tipo

/*
 * Todo lo que eliges en Mostaza es tuyo, no de este dispositivo: viaja con la cuenta, en `ajustes/mostaza_<grupo>`, un campo por ajuste
 * ([EspejoCuenta], como en Hiedra, Marfil y Azabache). Lo que es de verdad de este dispositivo (el tamaño de la caché de torrents, lo que
 * mide su conexión, su id) se queda en él: está aparte, en preferencias que no viajan.
 */

/** El reproductor: subtítulos, audio, el episodio siguiente y el reproductor de fuera. */
object AjustesReproductor {
    val GRUPO = EspejoCuenta.Grupo("mostaza_reproductor", "mostaza_reproductor", { k ->
        when (k) { "subs", "audio", "externo" -> Tipo.TEXTO; "subTam", "salto" -> Tipo.ENTERO; "subFondo", "siguiente", "subsSiempre" -> Tipo.SI_NO; else -> null }
    })
    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)
    /** El idioma de los subtítulos (ISO 639: «spa», «eng»…; "" = sin subtítulos salvo que el audio no sea el tuyo). */
    fun subs(c: Context) = p(c).getString("subs", "spa").orEmpty()
    fun ponSubs(c: Context, v: String) = p(c).edit().putString("subs", v).apply()
    /** Poner subtítulos siempre (aunque el audio ya sea en tu idioma). */
    fun subsSiempre(c: Context) = p(c).getBoolean("subsSiempre", false)
    fun ponSubsSiempre(c: Context, v: Boolean) = p(c).edit().putBoolean("subsSiempre", v).apply()
    /** El tamaño de los subtítulos (por ciento). */
    fun subTam(c: Context) = p(c).getInt("subTam", 100).coerceIn(50, 250)
    fun ponSubTam(c: Context, v: Int) = p(c).edit().putInt("subTam", v).apply()
    /** Subtítulos sobre una banda oscura (si no, con borde). */
    fun subFondo(c: Context) = p(c).getBoolean("subFondo", false)
    fun ponSubFondo(c: Context, v: Boolean) = p(c).edit().putBoolean("subFondo", v).apply()
    /** El idioma de audio preferido cuando una fuente trae varios. */
    fun audio(c: Context) = p(c).getString("audio", "spa").orEmpty()
    fun ponAudio(c: Context, v: String) = p(c).edit().putString("audio", v).apply()
    /** Al terminar un episodio, el siguiente solo. */
    fun siguiente(c: Context) = p(c).getBoolean("siguiente", true)
    fun ponSiguiente(c: Context, v: Boolean) = p(c).edit().putBoolean("siguiente", v).apply()
    /** «Abrir con otro reproductor»: el paquete del que usas siempre ("" = preguntar cada vez). */
    fun externo(c: Context) = p(c).getString("externo", "").orEmpty()
    fun ponExterno(c: Context, v: String) = p(c).edit().putString("externo", v).apply()
    /** Cuánto salta adelante o atrás (segundos). */
    fun salto(c: Context) = p(c).getInt("salto", 10).coerceIn(5, 60)
    fun ponSalto(c: Context, v: Int) = p(c).edit().putInt("salto", v).apply()
}

/** El elector de fuentes ([Elector]). */
object AjustesElector {
    val GRUPO = EspejoCuenta.Grupo("mostaza_elector", "mostaza_elector", { k ->
        when (k) { "idiomas" -> Tipo.TEXTO; "maxima" -> Tipo.ENTERO; "subtitulados", "directas", "ahorro", "auto" -> Tipo.SI_NO; else -> null }
    })
    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)
    fun prefs(c: Context): PrefsElector {
        val x = p(c)
        val d = PrefsElector()
        return PrefsElector(
            x.getString("idiomas", null)?.split(',')?.map { it.trim() }?.filter { id -> PrefsElector.IDIOMAS.any { it.first == id } }?.distinct()?.takeIf { it.isNotEmpty() } ?: d.idiomas,
            x.getBoolean("subtitulados", d.subtitulados), x.getInt("maxima", d.maxima), x.getBoolean("directas", d.directasPrimero), x.getBoolean("ahorro", d.ahorroDatos),
        )
    }
    fun pon(c: Context, n: PrefsElector) = p(c).edit().putString("idiomas", n.idiomas.joinToString(",")).putBoolean("subtitulados", n.subtitulados)
        .putInt("maxima", n.maxima).putBoolean("directas", n.directasPrimero).putBoolean("ahorro", n.ahorroDatos).apply()
    /** «Reproducir» pone sola la mejor fuente (si no, enseña la lista). */
    fun auto(c: Context) = p(c).getBoolean("auto", true)
    fun ponAuto(c: Context, v: Boolean) = p(c).edit().putBoolean("auto", v).apply()
}

/** Lo de los torrents que es tuyo (viaja); el tamaño de la caché es de cada dispositivo. */
object AjustesTorrents {
    val GRUPO = EspejoCuenta.Grupo("mostaza_torrent", "mostaza_torrent", { k ->
        when (k) { "soloWifi", "compartir" -> Tipo.SI_NO; "subida", "bajada" -> Tipo.ENTERO; else -> null }
    }, alLlegar = { c -> Torrents.ajustesCambiados(c) })
    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)
    private fun local(c: Context) = c.getSharedPreferences("mostaza_torrent_local", Context.MODE_PRIVATE)
    fun ajustes(c: Context): AjustesTorrent = p(c).let { x ->
        AjustesTorrent(x.getBoolean("soloWifi", true), x.getInt("subida", SUBIDA_POR_DEFECTO_KBS).coerceAtLeast(0), x.getInt("bajada", 0).coerceAtLeast(0), x.getBoolean("compartir", true),
            local(c).getInt("cacheMB", 4096).coerceIn(512, 65536))
    }
    fun pon(c: Context, a: AjustesTorrent) {
        p(c).edit().putBoolean("soloWifi", a.soloWifi).putInt("subida", a.subidaKBs).putInt("bajada", a.bajadaKBs).putBoolean("compartir", a.compartir).apply()
        local(c).edit().putInt("cacheMB", a.cacheMB).apply()
        Torrents.ajustesCambiados(c)
    }
}

/** El orden de cada sección y lo que se ve (películas, series o todo). */
object OrdenGuardado {
    enum class Orden(val nombre: String) { RECIENTES("Recientes"), ANADIDOS("Añadidos"), TITULO("Título"), ANO("Año") }
    enum class Filtro(val nombre: String) { TODO("Todo"), PELICULAS("Películas"), SERIES("Series") }
    val GRUPO = EspejoCuenta.Grupo("mostaza_orden", "mostaza_orden", { k -> if (k in Estado.entries.map { it.name } || k.startsWith("filtro_")) Tipo.TEXTO else null })
    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)
    fun de(c: Context, e: Estado): Orden = Orden.entries.firstOrNull { it.name == p(c).getString(e.name, null) } ?: Orden.RECIENTES
    fun pon(c: Context, e: Estado, o: Orden) = p(c).edit().putString(e.name, o.name).apply()
    fun filtro(c: Context, e: Estado): Filtro = Filtro.entries.firstOrNull { it.name == p(c).getString("filtro_${e.name}", null) } ?: Filtro.TODO
    fun ponFiltro(c: Context, e: Estado, f: Filtro) = p(c).edit().putString("filtro_${e.name}", f.name).apply()
}

/** La portada (qué catálogos salen y cuántos) y lo último que elegiste en «Explorar» (catálogo, género o año). */
object AjustesPortada {
    val GRUPO = EspejoCuenta.Grupo("mostaza_portada", "mostaza_portada", { k ->
        when (k) { "filas", "explorarCatalogo", "explorarGenero", "explorarTipo" -> Tipo.TEXTO; "cuantas" -> Tipo.ENTERO; else -> null }
    })
    private fun p(c: Context) = EspejoCuenta.prefs(c, GRUPO)
    /** Las filas de catálogos de la portada («<addon>|<tipo>|<catálogo>», separadas por comas; "" = las primeras de tus addons). */
    fun filas(c: Context): List<String> = p(c).getString("filas", "").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun ponFilas(c: Context, l: List<String>) = p(c).edit().putString("filas", l.joinToString(",")).apply()
    /** Cuántas filas de catálogos (si no las eliges una a una). */
    fun cuantas(c: Context) = p(c).getInt("cuantas", 4).coerceIn(0, 12)
    fun ponCuantas(c: Context, n: Int) = p(c).edit().putInt("cuantas", n).apply()
    fun explorar(c: Context): Triple<String, String, String> = p(c).let { Triple(it.getString("explorarTipo", "movie").orEmpty(), it.getString("explorarCatalogo", "").orEmpty(), it.getString("explorarGenero", "").orEmpty()) }
    fun ponExplorar(c: Context, tipo: String, catalogo: String, genero: String) = p(c).edit().putString("explorarTipo", tipo).putString("explorarCatalogo", catalogo).putString("explorarGenero", genero).apply()
}
