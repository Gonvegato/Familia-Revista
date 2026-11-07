package com.gonzalo.mostaza

import android.content.Context
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Un episodio que está por estrenarse de una serie de tu biblioteca (de TMDB: `next_episode_to_air`). [fecha]: «2026-11-14». */
data class Proximo(val ref: String, val serie: String, val temporada: Int, val episodio: Int, val titulo: String, val fecha: String) {
    val dia: LocalDate? get() = runCatching { LocalDate.parse(fecha) }.getOrNull()
}

/**
 * «Próximos capítulos» (el widget): el siguiente episodio que se estrena de cada serie de tu biblioteca. Se le pregunta a TMDB (los
 * detalles de la serie, `tv/<id>`, traen `next_episode_to_air`) con tu clave, como el resto del catálogo (en la cabecera; nunca en un
 * registro), y se guarda aquí, en este dispositivo, para que el widget lo lea sin red. Se refresca al abrir la app, al sincronizar y
 * cuando se repinta el widget, como mucho cada [VIDA] (o antes si cambian las series de tu biblioteca).
 */
object Proximos {
    /** Cada cuánto se vuelve a preguntar a TMDB. */
    const val VIDA = 6 * 3600_000L
    private val ES = Locale("es", "ES")
    private val cerrojo = Mutex()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** Lo mismo que [refresca], sin esperar (al abrir la app, al sincronizar). Solo si toca. */
    fun alDia(c: Context) {
        val app = c.applicationContext
        scope.launch { runCatching { refresca(app) } }
    }

    /** En las pruebas: la lista fija (sin caché ni red). */
    @Volatile var fijos: List<Proximo>? = null

    private fun p(c: Context) = c.getSharedPreferences("mostaza_proximos", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- leer (sin red)

    /** El episodio que dice TMDB en los detalles de una serie (null si no hay ninguno anunciado o no trae fecha). */
    fun deTmdb(json: String, ref: String, serie: String): Proximo? {
        val m = runCatching { Json.lee(json) }.getOrNull().comoMapa()
        val e = m["next_episode_to_air"].comoMapa().takeIf { it.isNotEmpty() } ?: return null
        val fecha = e["air_date"].comoTexto().takeIf { runCatching { LocalDate.parse(it) }.isSuccess } ?: return null
        val nombre = serie.ifBlank { m["name"].comoTexto() }
        return Proximo(ref, nombre, e["season_number"].comoLong().toInt(), e["episode_number"].comoLong().toInt(), e["name"].comoTexto(), fecha)
    }

    /** Los que quedan por estrenar (de hoy en adelante), por fecha; el mismo día, por serie. */
    fun ordena(l: List<Proximo>, hoy: LocalDate = LocalDate.now()): List<Proximo> =
        l.filter { it.dia?.let { d -> !d.isBefore(hoy) } == true }.sortedWith(compareBy<Proximo>({ it.fecha }, { it.serie.lowercase(ES) }, { it.temporada }, { it.episodio }))

    /** «hoy», «mañana», «sáb 14 nov» (y con el año si no es este). */
    fun cuando(d: LocalDate, hoy: LocalDate = LocalDate.now()): String = when (d) {
        hoy -> "hoy"
        hoy.plusDays(1) -> "mañana"
        else -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')}" +
            if (d.year != hoy.year) " ${d.year}" else ""
    }

    /** «T3 · E2» (y su título si lo tiene y no es el genérico «Episodio 2»). */
    fun episodio(x: Proximo): String = listOf("T${x.temporada} · E${x.episodio}", x.titulo.takeIf { it.isNotBlank() && !it.matches(Regex("(?i)(episodio|episode|cap[ií]tulo) \\d+")) }.orEmpty())
        .filter { it.isNotBlank() }.joinToString(" · ")

    /** Lo guardado, ya ordenado (sin lo que ya se ha estrenado) y solo de series que siguen en tu biblioteca. */
    fun lista(c: Context, hoy: LocalDate = LocalDate.now()): List<Proximo> {
        fijos?.let { return ordena(it, hoy) }
        val vivas = Biblioteca.titulos.toList().filter { it.esSerie }.map { it.ref }.toSet()
        return ordena(desdeTexto(p(c).getString("lista", null).orEmpty()), hoy).filter { vivas.isEmpty() || it.ref in vivas }
    }

    fun aTexto(l: List<Proximo>): String = Json.escribe(l.map { mapOf("ref" to it.ref, "s" to it.serie, "t" to it.temporada.toLong(), "e" to it.episodio.toLong(), "n" to it.titulo, "f" to it.fecha) })

    fun desdeTexto(s: String): List<Proximo> = if (s.isBlank()) emptyList() else runCatching {
        Json.lee(s).comoLista().map { it.comoMapa() }.map { m ->
            Proximo(m["ref"].comoTexto(), m["s"].comoTexto(), m["t"].comoLong().toInt(), m["e"].comoLong().toInt(), m["n"].comoTexto(), m["f"].comoTexto())
        }
    }.getOrDefault(emptyList())

    // ---------------------------------------------------------------- preguntar a TMDB

    /** Las series de tu biblioteca que se miran (las abandonadas, no). */
    private fun series(): List<Titulo> = Biblioteca.titulos.toList().filter { it.esSerie && it.estado != Estado.ABANDONADO }

    private fun firma(l: List<Titulo>) = l.map { it.ref }.sorted().joinToString(",").hashCode().toString()

    /** ¿Toca preguntar? (pasó [VIDA] o han cambiado las series) */
    fun toca(c: Context, ahora: Long = System.currentTimeMillis()): Boolean {
        val pr = p(c)
        return ahora - pr.getLong("en", 0L) > VIDA || pr.getString("firma", "") != firma(series())
    }

    /** El id de TMDB de una serie: el que guarda la biblioteca o, si no, buscado por su código de IMDb. */
    private fun idTmdb(t: Titulo): Long {
        if (t.tmdb > 0) return t.tmdb
        TmdbDatos.deId(t.id)?.takeIf { it.tv }?.let { return it.id }
        val imdb = t.imdb.ifBlank { t.idStremio.substringBefore(':') }.takeIf { it.startsWith("tt") } ?: return 0L
        return runCatching { TmdbDatos.encontrado(Tmdb.pide("find/$imdb", listOf("external_source" to "imdb_id"), vida = 7 * 24 * 3600_000L)) }.getOrNull()
            ?.takeIf { it.tv }?.id ?: 0L
    }

    /**
     * Pregunta a TMDB (si hay clave y toca, o con [forzar]) y lo guarda; luego repinta los widgets. Sin red o sin clave no hace nada (se
     * queda lo de antes). Devuelve true si ha guardado algo nuevo.
     */
    suspend fun refresca(c: Context, forzar: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (fijos != null || !Biblioteca.cargada) return@withContext false
        val app = c.applicationContext
        Tmdb.cargar(app)
        if (!Tmdb.activo) return@withContext false
        if (!cerrojo.tryLock()) return@withContext false
        try {
            if (!forzar && !toca(app)) return@withContext false
            val ss = series()
            val fallos = java.util.concurrent.atomic.AtomicInteger()
            val r = coroutineScope {
                ss.chunked(6).flatMap { trozo ->
                    trozo.map { t ->
                        async {
                            try {
                                val id = idTmdb(t).takeIf { it > 0 } ?: return@async null
                                deTmdb(Tmdb.pide("tv/$id", vida = VIDA), t.ref, t.nombre)
                            } catch (e: CancellationException) { throw e } catch (e: Exception) { fallos.incrementAndGet(); null }
                        }
                    }.awaitAll()
                }.filterNotNull()
            }
            // Si no ha contestado nada (sin red), se queda lo de antes
            if (ss.isNotEmpty() && fallos.get() == ss.size) return@withContext false
            p(app).edit().putString("lista", aTexto(r)).putLong("en", System.currentTimeMillis()).putString("firma", firma(ss)).apply()
            Widgets.pedir(app)
            true
        } finally { cerrojo.unlock() }
    }
}
