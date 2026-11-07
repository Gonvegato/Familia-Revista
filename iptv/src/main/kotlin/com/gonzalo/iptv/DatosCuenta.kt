package com.gonzalo.iptv

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Lo de Cobre en la cuenta de la familia (`nube/ESQUEMA.md`), en funciones puras: lo usan Cobre del móvil y, luego, el del ordenador,
 * así que los dos escriben lo mismo con los mismos nombres.
 *
 *  - Tus listas, **solo en `/claves/<uid>/cobre_listas`** ([CLAVE]): un texto JSON con cada lista (sus direcciones y tus logins son
 *    secretos; `/claves` es lo único de la cuenta que no lee ni tu pareja). Nunca en `/users/<uid>`.
 *  - En `/users/<uid>/familia/…` (lo demás de la cuenta, campo a campo), nada secreto: ni direcciones, ni usuarios, ni contraseñas, ni
 *    imágenes del servidor (pueden ir por él). Solo ids, nombres y lo visto:
 *    `cobre_favoritos/<lista>~<canal>`, `cobre_grupos/<lista>~<g…>`, `cobre_progreso/<lista>~<p…|e…>` y `cobre_estado/ultimo`.
 */
object DatosCuenta {
    const val CLAVE = "cobre_listas"
    const val FAVORITOS = "cobre_favoritos"
    const val GRUPOS = "cobre_grupos"
    const val PROGRESO = "cobre_progreso"
    const val ESTADO = "cobre_estado"
    const val ULTIMO = "ultimo"

    // ---------------------------------------------------------------- las listas (en /claves)

    /** `[{"id","n","t","u","us","pw","epg","ua","dev","f","c"}]` → listas (lo que no se entiende, fuera). */
    fun listasDeTexto(t: String): List<Lista> = runCatching { JsonFlujo.lee(t) }.getOrNull().lista().mapNotNull { x ->
        val m = x.mapa()
        val id = m["id"].txt()
        if (id.isBlank() || !refValida(id)) null
        else Lista(id, m["n"].txt().ifBlank { "Lista" }, TipoLista.de(m["t"].txt()), m["u"].txt(), m["us"].txt(), m["pw"].txt(), m["epg"].txt(), m["ua"].txt(), m["dev"].txt(),
            m["f"].txt().ifBlank { "ts" }, m["c"].largo())
    }.distinctBy { it.id }

    fun listasATexto(l: List<Lista>): String = JsonFlujo.escribe(l.distinctBy { it.id }.map {
        linkedMapOf("id" to it.id, "n" to it.nombre, "t" to it.tipo.clave, "u" to it.url, "us" to it.usuario, "pw" to it.contrasena, "epg" to it.epg, "ua" to it.agente,
            "dev" to it.dispositivo, "f" to it.formato, "c" to it.creada)
    })

    private val azar = SecureRandom()
    /** El id de una lista nueva: `l` + 10 hexadecimales al azar (no sale de nada tuyo). */
    fun idNuevo(): String = "l" + ByteArray(5).also { azar.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun refValida(r: String) = r.isNotBlank() && r.length <= 700 && r.none { it in ".$#[]/" || it.code < 32 || it.code == 127 }

    private fun h(s: String, n: Int = 16) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(n)

    // ---------------------------------------------------------------- favoritos, grupos y el último canal

    /** El nodo del favorito: `<lista>~<clave del canal>` (las claves de canal ya valen como nombre de nodo). */
    fun refFavorito(lista: String, canal: String) = "$lista~${seguro(canal)}"

    /** Lo que Firebase no admite en un nombre, como %XX (como en el resto de la familia). */
    fun seguro(s: String): String {
        val sb = StringBuilder()
        for (ch in s) if (ch in ".$#[]/%" || ch.code < 32 || ch.code == 127) sb.append('%').append("%02X".format(ch.code)) else sb.append(ch)
        return sb.toString()
    }

    /** Un favorito: `lista`, `canal`, `n` (el nombre, para enseñarlo sin la lista) y `o` (su sitio en tus favoritos). Quitarlo es `b`. */
    fun camposFavorito(lista: String, c: Canal, orden: Long) = mapOf("lista" to lista, "canal" to c.clave, "n" to c.nombre, "o" to orden)

    /** El nodo de un grupo: `<lista>~g<16 hex del nombre>` (el nombre del grupo puede llevar de todo). */
    fun refGrupo(lista: String, grupo: String) = "$lista~g${h(grupo)}"

    /** Un grupo escondido: `lista`, `g` (el nombre) y `oculto` (sí/no: volver a enseñarlo es `false`, nunca se borra). */
    fun camposGrupo(lista: String, grupo: String, oculto: Boolean) = mapOf("lista" to lista, "g" to grupo, "oculto" to oculto)

    /** El último canal que viste (`cobre_estado/ultimo`): `lista`, `canal`, `n` y `ul` (cuándo). */
    fun camposUltimo(lista: String, c: Canal, cuando: Long) = mapOf("lista" to lista, "canal" to c.clave, "n" to c.nombre, "ul" to cuando)

    // ---------------------------------------------------------------- lo visto del videoclub

    /** El vídeo de una película (`p<id>`) o de un episodio (`e<id>`). */
    fun videoPelicula(p: Pelicula) = "p${p.id}"
    fun videoEpisodio(e: Episodio) = "e${e.id}"
    fun refProgreso(lista: String, video: String) = "$lista~$video"

    /**
     * Lo visto de una película o un episodio: `lista`, `v` (el vídeo), `tipo` (`pelicula` / `episodio`), `id`, `ext` (su extensión, para
     * ponerlo desde «Seguir viendo» sin pedir la ficha), `t` (el título), y en los episodios `serie` (su id), `st` (el nombre de la serie),
     * `temp` y `ep`; `pos` y `dur` (ms), `visto` (al 90 %) y `ul` (la última vez).
     */
    fun camposProgreso(p: Progreso): Map<String, Any?> = linkedMapOf(
        "lista" to p.lista, "v" to p.video, "tipo" to (if (p.serie > 0) "episodio" else "pelicula"), "id" to p.id, "ext" to p.extension, "t" to p.titulo,
        "serie" to p.serie, "st" to p.tituloSerie, "temp" to p.temporada.toLong(), "ep" to p.episodio.toLong(), "pos" to p.pos, "dur" to p.dur, "visto" to p.visto, "ul" to p.ultima,
    )

    fun progresoDe(m: Map<String, Any?>): Progreso? {
        val lista = m["lista"].txt(); val v = m["v"].txt()
        if (lista.isBlank() || v.isBlank()) return null
        return Progreso(lista, v, m["id"].largo(), m["ext"].txt(), m["t"].txt(), m["serie"].largo(), m["st"].txt(), m["temp"].largo().toInt(), m["ep"].largo().toInt(),
            m["pos"].largo(), m["dur"].largo(), m["visto"] == true || m["visto"].largo() == 1L, m["ul"].largo())
    }
}

/** Lo visto de una película o un episodio (un nodo `cobre_progreso`). [serie] = 0 en las películas. */
data class Progreso(
    val lista: String, val video: String, val id: Long, val extension: String, val titulo: String,
    val serie: Long = 0L, val tituloSerie: String = "", val temporada: Int = 0, val episodio: Int = 0,
    val pos: Long = 0L, val dur: Long = 0L, val visto: Boolean = false, val ultima: Long = 0L,
) {
    val esEpisodio: Boolean get() = serie > 0
    val fraccion: Float get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
}

/** Las reglas de lo visto (las de Mostaza, para que se comporten igual): visto al 90 %, y por dónde seguir. */
object Visto {
    const val VISTO = 0.90
    /** Visto al 90 %. */
    fun visto(pos: Long, dur: Long) = dur > 0 && pos >= dur * VISTO
    /** Desde dónde seguir: desde el principio si no llevas ni 30 s o si ya está casi al final (97 %). */
    fun desde(pos: Long, dur: Long): Long = if (pos < 30_000L || (dur > 0 && pos >= dur * 0.97)) 0L else pos

    /**
     * «Seguir viendo»: lo empezado y sin terminar, lo más reciente primero; de cada serie, solo lo último que viste de ella (si ese
     * episodio ya está visto, la serie sale igual: toca el siguiente). Solo de las listas que tienes ([listas]).
     */
    fun seguir(todos: Collection<Progreso>, listas: Set<String>, tope: Int = 30): List<Progreso> {
        val vivos = todos.filter { it.lista in listas && it.ultima > 0 && it.pos > 0 }
        val series = vivos.filter { it.esEpisodio }.groupBy { it.lista to it.serie }.values.map { l -> l.maxBy { it.ultima } }
        val pelis = vivos.filter { !it.esEpisodio && !it.visto }
        return (series + pelis).sortedByDescending { it.ultima }.take(tope)
    }
}
