package com.gonzalo.mostaza.pc

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Las fichas (como en el móvil): de TMDB si tienes tu clave puesta (en castellano, con reparto, tráiler, dónde verla y parecidas); si no, o
 * si TMDB no la tiene, la de tus addons. En una serie, los episodios son los de tus addons (es la numeración que usan para las fuentes, la
 * de IMDb), con el título, la imagen y el resumen de TMDB en castellano cuando coinciden temporada y episodio; si tus addons no los
 * tienen, los de TMDB.
 */
object Fichas {
    /** En las pruebas de pantallas: fichas fijas (sin red). */
    @Volatile var fijas: Map<String, Pair<Meta, Extras?>> = emptyMap()

    private val cache = ConcurrentHashMap<String, Pair<Meta, Extras?>>()
    fun deCache(id: String): Pair<Meta, Extras?>? = fijas[id] ?: cache[id]

    suspend fun carga(tipo: String, id: String): Pair<Meta, Extras?>? {
        deCache(id)?.let { return it }
        val r = coroutineScope {
            val t = async { runCatching { Tmdb.meta(id, tipo) }.getOrNull() }
            val a = async { if (TmdbDatos.deId(id) != null) null else runCatching { Addons.meta(tipo, id) }.getOrNull() }
            junta(t.await(), a.await())
        } ?: return null
        cache[id] = r
        if (r.first.id != id) cache[r.first.id] = r
        return r
    }

    /** Junta la de TMDB [t] y la de tus addons [a] (ver arriba). */
    fun junta(t: Pair<Meta, Extras>?, a: Meta?): Pair<Meta, Extras?>? {
        if (t == null) return a?.let { it to null }
        if (a == null || a.videos.isEmpty()) return t
        val deTmdb = t.first.videos.associateBy { it.temporada to it.episodio }
        val eps = a.videos.map { v ->
            deTmdb[v.temporada to v.episodio]?.let { e -> v.copy(titulo = e.titulo.ifBlank { v.titulo }, resumen = e.resumen.ifBlank { v.resumen }, miniatura = e.miniatura.ifBlank { v.miniatura }, estreno = if (v.estreno > 0) v.estreno else e.estreno) } ?: v
        }
        return t.first.copy(id = a.id.ifBlank { t.first.id }, videos = eps) to t.second
    }

    fun olvidaParaPruebas() { cache.clear(); fijas = emptyMap() }
}
