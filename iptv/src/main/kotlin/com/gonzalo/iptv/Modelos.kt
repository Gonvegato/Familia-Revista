package com.gonzalo.iptv

/*
 * Lo que maneja Cobre, sin nada de Android (lo mismo sirve en el ordenador). Las listas y los canales llevan secretos (la dirección de una
 * lista M3U suele llevar tu usuario y tu contraseña; un login de Xtream los lleva a la vista): sus `toString` los tapan, para que un
 * registro o un mensaje de error no los enseñe nunca por descuido.
 */

/** De dónde sale una lista: una dirección M3U, un archivo M3U de este dispositivo o un login de Xtream Codes. */
enum class TipoLista(val clave: String) {
    M3U("m3u"), ARCHIVO("archivo"), XTREAM("xtream");
    companion object { fun de(s: String) = entries.firstOrNull { it.clave == s } ?: M3U }
}

/**
 * Una lista tuya, tal como va en la cuenta (`/claves/<uid>/cobre_listas`, ver [DatosCuenta]). [url]: la dirección de la lista M3U o, en
 * Xtream, el servidor (`http://servidor:puerto`). [epg]: una guía XMLTV tuya (además de la que dé la lista, o en su lugar). [agente]: el
 * user-agent con el que se pide todo de esta lista ("" = el de Cobre). [dispositivo]: en las de archivo, el dispositivo donde está el
 * archivo (en los demás, la lista sale como «está en otro dispositivo»). [formato]: el de los canales en directo de Xtream (`ts` o `m3u8`).
 */
data class Lista(
    val id: String,
    val nombre: String,
    val tipo: TipoLista,
    val url: String = "",
    val usuario: String = "",
    val contrasena: String = "",
    val epg: String = "",
    val agente: String = "",
    val dispositivo: String = "",
    val formato: String = "ts",
    val creada: Long = 0L,
) {
    val esXtream: Boolean get() = tipo == TipoLista.XTREAM
    /** Lo que no se puede enseñar ni apuntar: la contraseña, el usuario y las direcciones (que suelen llevar los dos). */
    val secretos: List<String> get() = listOf(contrasena, usuario, url, epg).filter { it.length >= 3 }
    override fun toString() = "Lista($id, $nombre, ${tipo.clave})"
}

/** Cómo se ve lo ya emitido de un canal (los atributos `catchup`, `catchup-days` y `catchup-source` de las listas M3U). */
data class Catchup(val modo: String, val dias: Int, val fuente: String = "") {
    override fun toString() = "Catchup($modo, $dias)"
}

/**
 * Un canal (o cualquier entrada de una lista M3U). [clave]: la suya, estable entre descargas de la lista (con ella se apuntan tus
 * favoritos en la cuenta): `x<stream_id>` en Xtream, `c<16 hex>` en M3U (de su tvg-id y su nombre, ver [M3u.clave]). [url]: en M3U, la
 * dirección tal cual (con lo que lleve dentro); en Xtream, vacía: se construye al reproducir ([Xtream.urlDirecto]). [cabeceras]: las que
 * pide la lista para ese canal (user-agent, referer…), que se mandan solo a su servidor.
 */
data class Canal(
    val clave: String,
    val nombre: String,
    val grupo: String = "",
    val logo: String = "",
    val tvgId: String = "",
    val tvgNombre: String = "",
    val numero: Int = 0,
    val url: String = "",
    val idXtream: Long = 0L,
    val cabeceras: Map<String, String> = emptyMap(),
    val catchup: Catchup? = null,
    val radio: Boolean = false,
    /** Lo de `#KODIPROP` (para saber, por ejemplo, que va cifrado con DRM y no se puede ver aquí). */
    val propiedades: Map<String, String> = emptyMap(),
    /** El desfase de su guía en horas (`tvg-shift`). */
    val desfase: Double = 0.0,
) {
    /** ¿Pide DRM (Widevine, PlayReady…)? Cobre no sabe abrir esos. */
    val conDrm: Boolean get() = propiedades.keys.any { it.contains("license_type") || it.contains("license_key") || it.contains("drm") }
    override fun toString() = "Canal($clave, $nombre, $grupo)"
}

/** Un programa de la guía. Las horas, en milisegundos desde 1970 (UTC). */
data class Programa(
    val canal: String,
    val inicio: Long,
    val fin: Long,
    val titulo: String,
    val subtitulo: String = "",
    val descripcion: String = "",
    val categoria: String = "",
    val icono: String = "",
    val episodio: String = "",
) {
    fun emitiendose(ahora: Long) = ahora in inicio until fin
    /** Lo que va de programa (0–1). */
    fun avance(ahora: Long): Float = if (fin <= inicio) 0f else ((ahora - inicio).toFloat() / (fin - inicio)).coerceIn(0f, 1f)
}

/** Un canal de la guía (los `<channel>` de un XMLTV): su id y sus nombres. */
data class CanalGuia(val id: String, val nombres: List<String>, val icono: String = "")

/** Una categoría de Xtream (de canales, películas o series). */
data class Categoria(val id: String, val nombre: String)

/** Una película del videoclub de Xtream (`get_vod_streams`). */
data class Pelicula(
    val id: Long,
    val nombre: String,
    val categoria: String = "",
    val poster: String = "",
    val extension: String = "mp4",
    val nota: Double = 0.0,
    val anadida: Long = 0L,
    val tmdb: String = "",
    val ano: String = "",
)

/** Una serie de Xtream (`get_series`). */
data class Serie(
    val id: Long,
    val nombre: String,
    val categoria: String = "",
    val poster: String = "",
    val nota: Double = 0.0,
    val ano: String = "",
    val generos: String = "",
    val trama: String = "",
    val fondo: String = "",
    val modificada: Long = 0L,
)

/** La ficha de una película (`get_vod_info`). */
data class FichaPelicula(
    val pelicula: Pelicula,
    val descripcion: String = "",
    val duracionSeg: Long = 0L,
    val generos: String = "",
    val reparto: String = "",
    val director: String = "",
    val fondo: String = "",
    val trailer: String = "",
    val estreno: String = "",
    val pais: String = "",
)

/** Un episodio (`get_series_info`). [id]: el que va en la dirección de `/series/…`. */
data class Episodio(
    val id: Long,
    val temporada: Int,
    val numero: Int,
    val titulo: String,
    val extension: String = "mp4",
    val duracionSeg: Long = 0L,
    val trama: String = "",
    val imagen: String = "",
    val estreno: String = "",
    val tmdb: String = "",
)

/** Una temporada (puede no venir: entonces se sacan de los episodios). */
data class Temporada(val numero: Int, val nombre: String, val poster: String = "", val episodios: Int = 0)

/** La ficha de una serie con todos sus episodios, por temporada (en orden). */
data class FichaSerie(val serie: Serie, val temporadas: List<Temporada>, val episodios: Map<Int, List<Episodio>>) {
    /** Todos, en orden (los especiales, la temporada 0, al final). */
    val todos: List<Episodio> get() = episodios.toSortedMap(compareBy { if (it == 0) Int.MAX_VALUE else it }).values.flatten()
    /** El que va detrás de [e] (el siguiente de su temporada o el primero de la siguiente). */
    fun siguiente(e: Episodio): Episodio? { val l = todos; val i = l.indexOfFirst { it.id == e.id }; return if (i < 0) null else l.getOrNull(i + 1) }
}

/** Lo que dice tu servidor de Xtream de tu cuenta (`user_info` y `server_info`). */
data class Perfil(
    val activo: Boolean,
    val estado: String,
    val caduca: Long = 0L,
    val conexionesMax: Int = 0,
    val conexionesActivas: Int = 0,
    val formatos: List<String> = emptyList(),
    val zonaHoraria: String = "",
    val prueba: Boolean = false,
    val mensaje: String = "",
)
