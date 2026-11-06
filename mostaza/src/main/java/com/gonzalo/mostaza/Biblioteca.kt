package com.gonzalo.mostaza

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.PortadasFamilia
import com.gonzalo.familia.nucleo.Coleccion
import com.gonzalo.familia.nucleo.Fechas
import com.gonzalo.familia.nucleo.Nodo
import java.time.Instant
import java.time.ZoneId

/** En qué punto estás con una película o una serie (en la cuenta, los estados de siempre de la familia). */
enum class Estado(val cuenta: String, val nombre: String) {
    VIENDO("en_curso", "Viendo"), PENDIENTE("pendiente", "Pendiente"), VISTO("terminado", "Visto"), ABANDONADO("abandonado", "Abandonado");
    companion object { fun de(s: String) = entries.firstOrNull { it.cuenta == s } ?: PENDIENTE }
}

/** Una película o una serie de tu biblioteca (un nodo `titulos/<ref>` de la cuenta). */
data class Titulo(
    val ref: String, val id: String, val tipo: String, val nombre: String, val ano: String = "", val estado: Estado = Estado.PENDIENTE,
    val fav: Boolean = false, val anadido: Long = 0L, val ultima: Long = 0L, val fin: Long = 0L, val poster: String = "", val portada: String = "",
    val fondo: String = "", val minutos: Int = 0, val generos: List<String> = emptyList(), val imdb: String = "", val tmdb: Long = 0L, val tvmaze: Long = 0L,
    val mod: Long = 0L,
) {
    val esSerie: Boolean get() = tipo == "series"
    /** El id con el que se le piden cosas a los addons (el de IMDb si se sabe: es el que usan casi todos). */
    val idStremio: String get() = imdb.ifBlank { id }
    /** La imagen que se enseña: la de la cuenta si ya está aquí; si no, la dirección del póster. */
    val imagen: String get() = portada.ifBlank { poster }
    fun breve() = MetaBreve(idStremio, tipo, nombre, poster, "poster", fondo, ano, generos)
}

/** Lo visto de una película o un episodio (un nodo `progreso/<vídeo>`). */
data class Progreso(val video: String, val ref: String, val temporada: Int, val episodio: Int, val pos: Long, val dur: Long, val visto: Boolean, val cuando: Long, val ultima: Long) {
    val fraccion: Float get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
}

/**
 * Tu biblioteca de películas y series: la colección `titulos` de la cuenta (la misma en todos tus dispositivos y en Azabache, que la lee
 * para valorar), con lo visto de cada película y episodio (`progreso`) y el tiempo que ves cada día en cada dispositivo (`tiempo`).
 *
 * Las tres reglas (ver HISTORIA):
 *  1. nada entra solo hasta que llevas 10 minutos viéndolo de verdad en una misma sesión ([Visionado]); antes, no se escribe nada en
 *     la cuenta (solo un punto para seguir en este dispositivo: [Reanudar]);
 *  2. el tiempo que ves se apunta por día y dispositivo, y la posición de cada película o episodio viaja; una película o un episodio
 *     está visto al 90 %, y una serie pasa a «Visto» cuando tienes vistos todos sus episodios estrenados;
 *  3. borrar es para siempre: queda borrado en la cuenta ([quita]), lo que entra solo nunca lo devuelve ([entraSolo]), y solo «Añadir»
 *     ([anade]) lo recupera, el mismo, con lo suyo.
 */
object Biblioteca {
    val titulos = mutableStateListOf<Titulo>()
    var cargada by mutableStateOf(false)
    private var appCtx: Context? = null
    private var col: Coleccion? = null
    private var prog: Coleccion? = null
    private var tiempo: Coleccion? = null
    private val principal by lazy { Handler(Looper.getMainLooper()) }
    /** En las pruebas de pantallas: una lista fija (sin la cuenta debajo). */
    @Volatile private var congelada = false
    private val progresoFijo = HashMap<String, Progreso>()

    /** Un visto es al 90 % (regla 2). */
    const val VISTO = 0.90
    /** Lo que hay que ver de verdad, seguido en una sesión, para que algo entre solo en tu biblioteca (regla 1). */
    const val UMBRAL_MS = 10 * 60_000L

    fun cargar(c: Context) {
        if (cargada) return
        appCtx = c.applicationContext
        Cuenta.cargar(c)
        col = LaFamilia.coleccion("titulos").also { it.alCambiar { principal.post { refresca() } } }
        prog = LaFamilia.coleccion("progreso").also { it.alCambiar { principal.post { version++ } } }
        tiempo = LaFamilia.coleccion("tiempo")
        refresca()
        cargada = true
    }

    /** Sube cuando cambia lo visto (las fichas abiertas vuelven a leer). */
    var version by mutableStateOf(0)
        private set

    fun fijaParaPruebas(l: List<Titulo>, progresos: List<Progreso> = emptyList()) {
        congelada = true; cargada = true; titulos.clear(); titulos.addAll(l)
        progresoFijo.clear(); progresos.forEach { progresoFijo[it.video] = it }
    }

    /** (Para las pruebas: como si la app acabara de abrirse.) */
    fun olvidaParaPruebas() { cargada = false; congelada = false; col = null; prog = null; tiempo = null; titulos.clear(); progresoFijo.clear() }

    // ---------------------------------------------------------------- las claves

    /** El nodo de un id de Stremio («tt0111161», «kitsu:1»): el id tal cual, con lo que Firebase no admite como %XX. */
    fun ref(id: String): String {
        val sb = StringBuilder()
        for (ch in id) if (ch in ".$#[]/%" || ch.code < 32 || ch.code == 127) sb.append('%').append("%02X".format(ch.code)) else sb.append(ch)
        return sb.toString()
    }

    fun idDeRef(r: String): String = EspejoCuenta.clave(r)

    // ---------------------------------------------------------------- leer

    private fun deNodo(n: Nodo): Titulo = Titulo(
        ref = n.ref, id = n.texto("id").ifBlank { idDeRef(n.ref) }, tipo = n.texto("tipo").ifBlank { "movie" }, nombre = n.texto("t"), ano = n.texto("ano"),
        estado = Estado.de(n.texto("estado")), fav = n.si("fav"), anadido = Fechas.ms(n.entero("an")), ultima = n.entero("ul"), fin = n.entero("fin"),
        poster = n.texto("pu"), portada = appCtx?.let { PortadasFamilia.archivo(it, n.texto("portada")) }.orEmpty(), fondo = n.texto("fondo"),
        minutos = n.entero("dur").toInt(), generos = n.lista("gen").map { it.toString() }, imdb = n.texto("imdb"), tmdb = n.entero("tmdb"), tvmaze = n.entero("tvmaze"),
        mod = n.m.values.maxOrNull() ?: 0L,
    )

    /** Vuelve a montar la lista con lo que hay en la cuenta (se llama sola cuando cambia algo, venga de donde venga). */
    fun refresca() {
        if (congelada) return
        val cc = col ?: return
        val nueva = cc.vivos().filter { it.texto("t").isNotBlank() }.map { deNodo(it) }.sortedByDescending { maxOf(it.ultima, it.anadido) }
        if (nueva != titulos.toList()) { titulos.clear(); titulos.addAll(nueva) }
        version++
        appCtx?.let { c -> PortadasFamilia.baja(cc.vivos().filter { PortadasFamilia.archivo(c, it.texto("portada")).isBlank() }.map { it.texto("portada") }) { refresca() } }
    }

    /** Los nodos de un id (por su ref, su id o su código de IMDb: lo que vino de Azabache puede tener otra ref). */
    private fun nodosDe(id: String): List<Nodo> {
        val cc = col ?: return emptyList()
        val r = ref(id)
        return cc.todos().values.filter { it.ref == r || it.texto("id") == id || (id.startsWith("tt") && it.texto("imdb") == id) }
    }

    private fun nodoVivo(id: String): Nodo? = nodosDe(id).firstOrNull { !it.borrado && it.texto("t").isNotBlank() }

    /** La película o la serie de tu biblioteca con ese id de Stremio (si está y no la has borrado). */
    fun por(id: String): Titulo? = if (congelada) titulos.firstOrNull { it.id == id || it.idStremio == id || it.ref == id } else nodoVivo(id)?.let { deNodo(it) }

    fun porRef(r: String): Titulo? = if (congelada) titulos.firstOrNull { it.ref == r } else col?.nodo(r)?.takeIf { !it.borrado }?.let { deNodo(it) }

    /** ¿La borraste? (está en la cuenta como borrada y no hay otra viva con ese id) */
    fun loBorraste(id: String): Boolean = nodoVivo(id) == null && nodosDe(id).any { it.borrado }

    /** Lo que Azabache dice de este título: su nota (0–100; 0 = sin valorar) y su reseña. */
    fun valoracion(ref: String): Pair<Int, String> = col?.nodo(ref)?.let { it.entero("nota").toInt() to it.texto("resena") } ?: (0 to "")

    // ---------------------------------------------------------------- escribir

    private fun campos(m: Meta?, b: MetaBreve): Map<String, Any?> {
        val c = LinkedHashMap<String, Any?>()
        c["id"] = b.id; c["tipo"] = b.tipo.ifBlank { m?.tipo ?: "movie" }; c["t"] = b.nombre; c["ano"] = m?.ano?.ifBlank { null } ?: b.ano
        c["pu"] = m?.poster?.ifBlank { null } ?: b.poster; c["fondo"] = m?.fondo?.ifBlank { null } ?: b.fondo
        c["gen"] = (m?.generos?.takeIf { it.isNotEmpty() } ?: b.generos)
        m?.minutos?.takeIf { it > 0 }?.let { c["dur"] = it.toLong() }
        val imdb = m?.imdb?.ifBlank { null } ?: if (b.id.startsWith("tt")) b.id.substringBefore(':') else ""
        if (imdb.isNotBlank()) c["imdb"] = imdb
        m?.tmdb?.takeIf { it > 0 }?.let { c["tmdb"] = it }
        return c.filterValues { it != "" && it != null && !(it is List<*> && it.isEmpty()) }
    }

    /** La portada a la cuenta (la imagen reducida, como las de Hiedra y Marfil), sin esperar. */
    private fun ponPortada(r: String, url: String) {
        if (url.isBlank() || !url.startsWith("http")) return
        kotlin.concurrent.thread(name = "mostaza-portada") {
            runCatching {
                val bytes = java.net.URL(url).openStream().use { it.readBytes() }
                PortadasFamilia.guardar(bytes)?.let { h -> col?.cambia(r, mapOf("portada" to h)) }
            }
        }
    }

    /**
     * «Añadir» (lo pides tú): si no estaba, entra con el estado que digas; si la habías borrado, vuelve LA MISMA (el mismo nodo, con
     * su estado, sus fechas, su nota de Azabache y lo visto). Devuelve el título.
     */
    fun anade(b: MetaBreve, m: Meta? = null, estado: Estado = Estado.PENDIENTE, ahora: Long = System.currentTimeMillis()): Titulo? {
        val cc = col ?: return null
        nodoVivo(b.id)?.let { return deNodo(it) }
        val borrado = nodosDe(b.id).filter { it.borrado }.maxByOrNull { it.entero(Nodo.B) }
        if (borrado != null) {
            cc.pon(borrado.ref, mapOf(Nodo.B to 0L))
            refresca()
            return por(b.id)
        }
        val r = ref(b.id)
        cc.pon(r, campos(m, b) + mapOf("estado" to estado.cuenta, "an" to ahora, "fav" to false), ahora)
        ponPortada(r, m?.poster?.ifBlank { null } ?: b.poster)
        refresca()
        return por(b.id)
    }

    /**
     * Lo que entra solo (regla 1, tras 10 minutos viéndolo): si ya estaba, pasa a «Viendo» (si estaba pendiente o abandonado); si no
     * estaba, entra en «Viendo». Si lo borraste, NO vuelve (regla 3) y devuelve false: entonces no se escribe nada en la cuenta.
     */
    fun entraSolo(b: MetaBreve, m: Meta? = null, ahora: Long = System.currentTimeMillis()): Boolean {
        val cc = col ?: return false
        val vivo = nodoVivo(b.id)
        if (vivo != null) {
            val e = Estado.de(vivo.texto("estado"))
            val c = LinkedHashMap<String, Any?>(); c["ul"] = ahora
            if (e == Estado.PENDIENTE || e == Estado.ABANDONADO) c["estado"] = Estado.VIENDO.cuenta
            cc.cambia(vivo.ref, c)
            refresca(); return true
        }
        if (loBorraste(b.id)) return false
        val r = ref(b.id)
        cc.pon(r, campos(m, b) + mapOf("estado" to Estado.VIENDO.cuenta, "an" to ahora, "ul" to ahora, "fav" to false), ahora)
        ponPortada(r, m?.poster?.ifBlank { null } ?: b.poster)
        refresca()
        return true
    }

    fun cambiaEstado(r: String, e: Estado, ahora: Long = System.currentTimeMillis()) {
        val cc = col ?: return
        val c = LinkedHashMap<String, Any?>(); c["estado"] = e.cuenta
        if (e == Estado.VISTO && (cc.nodo(r)?.entero("fin") ?: 0L) <= 0L) c["fin"] = ahora
        cc.cambia(r, c); refresca()
    }

    fun ponFavorito(r: String, f: Boolean) { col?.cambia(r, mapOf("fav" to f)); refresca() }

    /** Quitarla de tu biblioteca: queda borrada en la cuenta (con lo visto y su tiempo, para cuando la vuelvas a añadir). */
    fun quita(r: String) { col?.borra(r); refresca() }

    /** Lo que se sabe después de abrir su ficha (la duración, los ids de fuera…), si faltaba. */
    fun completa(t: Titulo, m: Meta) {
        val cc = col ?: return
        val n = cc.nodo(t.ref) ?: return
        val c = LinkedHashMap<String, Any?>()
        if (n.entero("dur") <= 0 && m.minutos > 0) c["dur"] = m.minutos.toLong()
        if (n.texto("imdb").isBlank() && m.imdb.isNotBlank()) c["imdb"] = m.imdb
        if (n.entero("tmdb") <= 0 && m.tmdb > 0) c["tmdb"] = m.tmdb
        if (n.texto("fondo").isBlank() && m.fondo.isNotBlank()) c["fondo"] = m.fondo
        if (n.texto("pu").isBlank() && m.poster.isNotBlank()) c["pu"] = m.poster
        if (n.texto("ano").isBlank() && m.ano.isNotBlank()) c["ano"] = m.ano
        if (c.isNotEmpty()) cc.pon(t.ref, c)
        if (n.texto("portada").isBlank() && m.poster.isNotBlank()) ponPortada(t.ref, m.poster)
    }

    // ---------------------------------------------------------------- lo visto (`progreso/<vídeo>`)

    private fun progresoDe(n: Nodo) = Progreso(n.texto("v").ifBlank { idDeRef(n.ref) }, n.texto("ref"), n.entero("temp").toInt(), n.entero("ep").toInt(),
        n.entero("pos"), n.entero("dur"), n.si("visto"), n.entero("cuando"), n.entero("ul"))

    fun progreso(videoId: String): Progreso? = if (congelada) progresoFijo[videoId] else prog?.nodo(ref(videoId))?.takeIf { !it.borrado }?.let { progresoDe(it) }

    /** Lo visto de un título (sus episodios, o la película). */
    fun progresos(r: String): List<Progreso> = if (congelada) progresoFijo.values.filter { it.ref == r }
        else prog?.vivos()?.filter { it.texto("ref") == r }?.map { progresoDe(it) }.orEmpty()

    /** El último vídeo que tocaste de un título (para «Seguir viendo»). */
    fun ultimo(r: String): Progreso? = progresos(r).maxByOrNull { it.ultima }

    /** Apunta la posición de un vídeo (solo si el título está en tu biblioteca: lo decide quien llama). Al 90 %, visto. */
    fun ponPosicion(t: Titulo, video: Video?, pos: Long, dur: Long, ahora: Long = System.currentTimeMillis()) {
        val pc = prog ?: return
        val vid = video?.id ?: t.idStremio
        val c = LinkedHashMap<String, Any?>()
        c["ref"] = t.ref; c["v"] = vid; c["pos"] = pos.coerceAtLeast(0L); c["ul"] = ahora
        if (dur > 0) c["dur"] = dur
        if (video != null) { c["temp"] = video.temporada.toLong(); c["ep"] = video.episodio.toLong() }
        val previo = pc.nodo(ref(vid))
        if (dur > 0 && pos >= dur * VISTO && previo?.si("visto") != true) { c["visto"] = true; c["cuando"] = ahora }
        pc.cambia(ref(vid), c)
    }

    /** Marcar (o desmarcar) a mano un episodio o la película como visto. */
    fun marcaVisto(t: Titulo, video: Video?, visto: Boolean, ahora: Long = System.currentTimeMillis()) {
        val pc = prog ?: return
        val vid = video?.id ?: t.idStremio
        val c = LinkedHashMap<String, Any?>(); c["ref"] = t.ref; c["v"] = vid; c["visto"] = visto; c["cuando"] = ahora
        if (video != null) { c["temp"] = video.temporada.toLong(); c["ep"] = video.episodio.toLong() }
        if (!visto) c["pos"] = 0L
        pc.cambia(ref(vid), c)
        version++
    }

    /**
     * El estado de una serie según lo visto (regla 2): con todos sus episodios estrenados vistos (sin los especiales, la temporada 0), «Visto»;
     * si no, «Viendo» (también si estaba vista y ha salido un episodio nuevo). Pendiente y abandonada no cambian por un episodio suelto marcado.
     */
    fun recalculaSerie(t: Titulo, m: Meta, ahora: Long = System.currentTimeMillis(), trasVer: Boolean = true) {
        if (!m.esSerie) return
        val estrenados = m.videos.filter { it.temporada > 0 && it.estrenado(ahora) }
        if (estrenados.isEmpty()) return
        val vistos = progresos(t.ref).filter { it.visto }.map { it.video }.toSet()
        val todos = estrenados.all { it.id in vistos }
        val actual = por(t.idStremio)?.estado ?: t.estado
        when {
            todos && actual != Estado.VISTO -> cambiaEstado(t.ref, Estado.VISTO, ahora)
            !todos && actual == Estado.VISTO -> cambiaEstado(t.ref, Estado.VIENDO, ahora)
            !todos && trasVer && (actual == Estado.PENDIENTE || actual == Estado.ABANDONADO) -> cambiaEstado(t.ref, Estado.VIENDO, ahora)
        }
    }

    /** El episodio que toca: el que dejaste a medias o el siguiente al último visto (de los estrenados). */
    fun siguiente(t: Titulo, m: Meta, ahora: Long = System.currentTimeMillis()): Video? {
        val eps = m.videos.filter { it.temporada > 0 }.sortedWith(compareBy({ it.temporada }, { it.episodio }))
        if (eps.isEmpty()) return null
        val ps = progresos(t.ref).associateBy { it.video }
        ps.values.filter { !it.visto && it.pos > 60_000L }.maxByOrNull { it.ultima }?.let { p -> eps.firstOrNull { it.id == p.video }?.let { return it } }
        val ultimoVisto = eps.indexOfLast { ps[it.id]?.visto == true }
        return eps.drop(ultimoVisto + 1).firstOrNull { it.estrenado(ahora) && ps[it.id]?.visto != true } ?: if (ultimoVisto < 0) eps.first() else null
    }

    // ---------------------------------------------------------------- el tiempo (`tiempo/<ref>~<dispositivo>`)

    private fun dia(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** Suma lo visto de verdad ([porDia]: «2026-11-05» → ms) a lo de este dispositivo para ese título. */
    fun sumaTiempo(t: Titulo, porDia: Map<String, Long>, sesiones: Int = 0) {
        val tc = tiempo ?: return
        if (porDia.values.sumOf { it } <= 0L && sesiones == 0) return
        val r = "${t.ref}~${Cuenta.dispositivo}"
        val n = tc.nodo(r)
        val c = LinkedHashMap<String, Any?>()
        c["ref"] = t.ref; c["fuente"] = Cuenta.dispositivo; c["t"] = t.nombre
        c["total"] = (n?.entero("total") ?: 0L) + porDia.values.sumOf { it }
        if (sesiones > 0) c["veces"] = (n?.entero("veces") ?: 0L) + sesiones
        porDia.forEach { (d, ms) -> if (ms > 0) c["d$d"] = (n?.entero("d$d") ?: 0L) + ms }
        tc.cambia(r, c)
    }

    /** Lo visto de un título, sumando tus dispositivos (ms). */
    fun msVistos(r: String): Long = tiempo?.vivos()?.filter { it.texto("ref") == r }?.sumOf { it.entero("total") } ?: 0L

    fun hoy(ahora: Long = System.currentTimeMillis()) = dia(ahora)
}
