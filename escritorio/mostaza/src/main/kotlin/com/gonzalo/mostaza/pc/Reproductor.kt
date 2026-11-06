package com.gonzalo.mostaza.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.Actualizador
import com.gonzalo.familia.pc.Sitio
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Lo que se le pasa al reproductor: qué se ve, con qué fuente y las demás por si hay que cambiar (como en el móvil). */
class Encargo(
    val breve: MetaBreve, val meta: Meta?, val video: Video?, val elegida: Candidata, val candidatas: List<Candidata>,
    /** Lo que ya llevaba esta sesión del reproductor con este título (al pasar al episodio siguiente o a otra fuente). */
    val previo: Long = 0L, val pendiente: Map<String, Long> = emptyMap(),
    /** La eligió el elector solo («Reproducir», el episodio siguiente), no tú: si sus pistas no traen tu idioma, se prueba otra. */
    val automatica: Boolean = false,
)

/** Qué hacer con una fuente. */
sealed class Accion {
    /** Con mpv (una dirección directa o de tu debrid, o un torrent con el motor). */
    object Reproductor : Accion()
    /** Fuera de Mostaza (YouTube, una web): con el navegador. */
    class Fuera(val url: String) : Accion()
    /** No se puede: el texto, para enseñarlo tal cual. */
    class Aviso(val texto: String) : Accion()
}

/** Lo que enseña la banda del reproductor (en la ventana de Mostaza, mientras mpv tiene la suya). */
class EstadoReproductor {
    var activo by mutableStateOf(false)
    var titulo by mutableStateOf(""); var subtitulo by mutableStateOf("")
    var reproduciendo by mutableStateOf(false); var cargando by mutableStateOf(false)
    var pos by mutableStateOf(0L); var dur by mutableStateOf(0L)
    /** Un aviso que no se va solo (no se puede reproducir…). */
    var aviso by mutableStateOf<String?>(null)
    /** Un mensaje que se va solo («Ha entrado en Viendo»). */
    var mensaje by mutableStateOf<String?>(null)
    /** Se corta: se propone la siguiente fuente. */
    var corte by mutableStateOf<String?>(null)
    /** El episodio siguiente, y en cuántos segundos empieza (−1: sin cuenta atrás). */
    var siguiente by mutableStateOf<String?>(null); var cuentaAtras by mutableStateOf(-1)
    var fuente by mutableStateOf("")
    /** Las pistas del archivo (para elegir audio y subtítulos desde aquí). */
    var pistas by mutableStateOf<List<PistaMpv>>(emptyList())
    /** Las fuentes a las que se puede cambiar, y la de ahora. */
    var candidatas by mutableStateOf<List<Candidata>>(emptyList()); var elegida by mutableStateOf<Candidata?>(null)
}

/**
 * El reproductor del ordenador: mpv ([Mpv]) con la lógica del reproductor del móvil (`ReproductorActivity`): cuenta la sesión ([Visionado]),
 * apunta la posición y el tiempo, mira las pistas de verdad (pone el mejor audio según tu orden y subtítulos en español si hacen falta; y si
 * «Reproducir» eligió solo y no trae tu primer idioma, prueba otra fuente: [Tanteo]), reconoce el vídeo de aviso del debrid ([FuentesRotas])
 * y pasa sola a otra, propone otra fuente si se corta y pone el episodio siguiente. Una sola sesión a la vez: un solo mpv, que se queda
 * abierto entre fuentes y episodios; cerrar su ventana termina la sesión (regla 1).
 *
 * Todo lo de la sesión pasa en un solo hilo ([hilo]): los sucesos de mpv, el pulso de cada segundo y lo que pides desde la ventana.
 */
object Reproductor {
    val estado = EstadoReproductor()
    private val hilo = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "mostaza-reproductor").apply { isDaemon = true } }
    @Volatile private var proceso: Process? = null
    @Volatile private var cliente: ClienteMpv? = null
    private var pulso: ScheduledFuture<*>? = null

    private var encargo: Encargo? = null
    private var visionado: Visionado? = null
    private var tanteo: Tanteo? = null
    private val tanteando: Boolean get() = tanteo?.terminado == false
    /** La entrada de la lista de mpv que es lo de ahora (los sucesos de la anterior, al cambiar, no cuentan). */
    private var entrada = -1L
    /** Ya ha empezado el archivo de ahora (lo que llega antes es del anterior, que mpv aún estaba contando, y no vale). */
    private var empezado = false
    private var url = ""
    private var torrentAbierto = ""
    private var subsPendientes: List<Subtitulo> = emptyList()
    /** Sube con cada archivo que se abre (lo de uno no se aplica a otro que se ha abierto mientras). */
    private var generacion = 0L
    /** Lo que se decidió con las pistas: ¿hacen falta subtítulos en español? */
    private var quiereSubs = false
    private var listo = false
    private var subsDecididos = false
    private var avisoDelDebrid = false
    private var avisadoEntrada = false
    private var cargandoDesde = 0L
    private val cortes = ArrayList<Long>()
    private val velocidades = ArrayList<Long>()
    private var mensajeHasta = 0L

    /** En las pruebas: el reloj de la sesión y la hora (para no esperar diez minutos de verdad). */
    @Volatile var relojPruebas: (() -> Long)? = null
    @Volatile var ahoraPruebas: (() -> Long)? = null
    /** Cada cuánto se le pregunta a mpv por dónde va (en las pruebas, más a menudo). */
    @Volatile var pulsoMs = 1_000L
    private fun reloj(): Long = relojPruebas?.invoke() ?: Reloj.ahora()
    private fun ahora(): Long = ahoraPruebas?.invoke() ?: System.currentTimeMillis()

    /** Qué se hace con [f] (sin hacerlo todavía): lo decide su tipo (`url`, `infoHash`, `ytId`, `externalUrl`, NZB o archivos). */
    fun accion(f: Fuente, torrents: Boolean = Torrents.disponible): Accion = when (f.tipo) {
        TipoFuente.DIRECTA -> Accion.Reproductor
        TipoFuente.TORRENT -> if (torrents) Accion.Reproductor else Accion.Aviso(Torrents.AVISO)
        TipoFuente.YOUTUBE -> Accion.Fuera("https://www.youtube.com/watch?v=" + java.net.URLEncoder.encode(f.ytId, "UTF-8"))
        TipoFuente.EXTERNA -> if (f.externalUrl.startsWith("http://") || f.externalUrl.startsWith("https://")) Accion.Fuera(f.externalUrl)
            else Accion.Aviso("Es un enlace para la app de Stremio («${f.externalUrl.substringBefore('/').take(20)}…»): Mostaza no lo puede abrir.")
        TipoFuente.NZB -> Accion.Aviso("Todavía no: Mostaza aún no sabe abrir fuentes de Usenet (NZB). Elige otra fuente.")
        TipoFuente.ARCHIVO -> Accion.Aviso("Todavía no: Mostaza aún no sabe abrir vídeos dentro de archivos comprimidos (RAR, ZIP, 7z…). Elige otra fuente.")
        TipoFuente.NADA -> Accion.Aviso("Esta fuente no trae nada que se pueda ver.")
    }

    /** ¿Está mpv en este ordenador? */
    val hayMpv: Boolean get() = Mpv.busca() != null

    /**
     * Pone [c] (de [todas], ya ordenadas) para [breve]. Devuelve un aviso si no se puede (para enseñarlo), o null si ya está en marcha.
     * Sin mpv, devuelve cómo instalarlo (la ventana ofrece además abrirlo con el reproductor del sistema, sin contar lo visto).
     */
    fun pon(breve: MetaBreve, meta: Meta?, video: Video?, c: Candidata, todas: List<Candidata>, previo: Long = 0L, pendiente: Map<String, Long> = emptyMap(),
            automatica: Boolean = false): String? {
        return when (val a = accion(c.fuente)) {
            is Accion.Aviso -> a.texto
            is Accion.Fuera -> if (abreEnNavegador(a.url)) null else "No se ha podido abrir el navegador."
            Accion.Reproductor -> {
                if (!hayMpv) return Mpv.comoInstalar()
                val e = Encargo(breve, meta, video, c, todas, previo, pendiente, automatica)
                hilo.execute { runCatching { empieza(e) }.onFailure { x -> estado.aviso = "No se ha podido abrir el reproductor: ${x.message ?: x.javaClass.simpleName}" } }
                null
            }
        }
    }

    /**
     * Sin mpv: la fuente con el reproductor que tenga el sistema para los vídeos. Así no se sabe por dónde vas (ni entra en tu biblioteca,
     * ni se apunta el tiempo ni lo visto), y la dirección va en la línea de órdenes del sistema (no hay otra forma de pasársela); las
     * cabeceras no se pueden pasar. Devuelve un aviso o null.
     */
    fun conElSistema(c: Candidata, nombre: String): String? {
        val f = c.fuente
        val u = when (f.tipo) {
            TipoFuente.DIRECTA -> f.url
            TipoFuente.TORRENT -> runCatching { runBlocking { Torrents.prepara(f.infoHash, f.fileIdx, f.trackers, f.archivo.ifBlank { nombre }) } }.getOrElse { return it.message }
            else -> return (accion(f) as? Accion.Aviso)?.texto
        }
        if (f.cabeceras.isNotEmpty()) return "Esta fuente pide cabeceras que el reproductor del sistema no recibe: hace falta mpv."
        return if (Sitio.abre(u)) null else "No hay ningún programa en este ordenador que abra vídeos."
    }

    /** Cierra mpv (termina la sesión). */
    fun para() { hilo.execute { cliente?.manda("quit") ?: alSalir() } }

    /** Al cerrar Mostaza: termina la sesión y la apunta (espera un poco: luego se sube lo pendiente). */
    fun alCerrarLaApp() {
        runCatching { hilo.submit { cliente?.manda("quit", espera = 1_500); alSalir() }.get(4, TimeUnit.SECONDS) }
        runCatching { proceso?.destroy() }
        Torrents.paraTodo()
    }

    fun alterna() = hilo.execute { cliente?.manda("cycle", "pause") }
    fun salta(seg: Int) = hilo.execute { cliente?.manda("seek", seg, "relative") }
    fun ponAudio(id: Int) = hilo.execute { cliente?.pon("aid", id); refrescaPistas() }
    fun ponSubtitulos(id: Int?) = hilo.execute { cliente?.pon("sid", id ?: "no"); refrescaPistas() }
    fun cambiaFuente(c: Candidata) = hilo.execute { cambiaA(c) }
    fun siguienteFuente() = hilo.execute { estado.corte = null; siguienteCandidata()?.let { cambiaA(it) } }
    fun siguienteEpisodioYa() = hilo.execute { siguienteYa() }
    fun cancelaSiguiente() = hilo.execute { estado.cuentaAtras = -1; estado.siguiente = null }
    fun quitaAviso() { estado.aviso = null }

    // ---------------------------------------------------------------- mpv

    private fun opciones() = Mpv.Opciones(
        audio = Mpv.idiomas(AjustesReproductor.audio()), subs = Mpv.idiomas(AjustesReproductor.subs()), subTam = AjustesReproductor.subTam(), subFondo = AjustesReproductor.subFondo(),
        pantallaCompleta = PantallaCompleta.si(), espera = if (torrentAbierto.isNotBlank()) com.gonzalo.torrent.MotorTorrent.ESPERA_LECTURA_MS / 1000 else 30,
        agente = "Mostaza/${Actualizador.version}",
    )

    /** Abre mpv si no está abierto (en su ventana, vacío: lo que se ve se le da por el socket). */
    private fun asegura(): ClienteMpv? {
        cliente?.takeIf { !it.cerrado && proceso?.isAlive == true }?.let { return it }
        val mpv = Mpv.busca() ?: return null
        val dir = Mpv.carpetaSocket(Local.dir)
        val socket = File(dir, "s${ProcessHandle.current().pid()}.sock").also { it.delete() }
        val p = ProcessBuilder(Mpv.orden(mpv, socket, opciones())).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        proceso = p
        val c = ClienteMpv.conecta(socket, vivo = { p.isAlive }) { s -> hilo.execute { runCatching { suceso(s) } } } ?: run { p.destroy(); proceso = null; return null }
        cliente = c
        c.alCerrar = { hilo.execute { alSalir() } }
        // Lo que se sigue sin preguntar: si está reproduciendo de verdad (sin pausa ni cargando) y si se ha quedado esperando a la red
        c.observa(1, "core-idle"); c.observa(2, "paused-for-cache")
        // (los mandos dentro de mpv, si su versión los entiende; si no, no pasa nada)
        c.pon("input-gamepad", true)
        Thread({ p.waitFor(); hilo.execute { alSalir() } }, "mostaza-mpv-fin").apply { isDaemon = true; start() }
        pulso?.cancel(false)
        pulso = hilo.scheduleAtFixedRate({ runCatching { late() } }, pulsoMs, pulsoMs, TimeUnit.MILLISECONDS)
        return c
    }

    private fun nombreDe(e: Encargo): Pair<String, String> {
        val v = e.video
        return e.breve.nombre to (if (v != null && v.temporada > 0) "T${v.temporada} · E${v.episodio}" + (if (v.titulo.isNotBlank()) " · ${v.titulo}" else "") else e.breve.ano)
    }

    /** Lo de un encargo nuevo (desde «Reproducir», una fuente que eliges, el episodio siguiente o probando otra). */
    private fun empieza(e: Encargo) {
        // Lo que se pide desde la ventana es otra sesión: la de antes (si mpv seguía abierto con otra cosa) termina y se apunta
        visionado?.let { it.termina(estado.pos, estado.dur); visionado = null }
        cierraTorrent()
        tanteo?.cancela()
        tanteo = if (e.automatica) nuevoTanteo(e) else null
        abre(e)
    }

    private fun abre(e: Encargo) {
        encargo = e
        generacion++; quiereSubs = false
        val (t, s) = nombreDe(e)
        estado.activo = true; estado.titulo = t; estado.subtitulo = s
        estado.fuente = e.elegida.analisis.resumen().ifBlank { e.elegida.addon }
        estado.cargando = true; estado.reproduciendo = false; estado.aviso = null; estado.corte = null; estado.siguiente = null; estado.cuentaAtras = -1
        estado.candidatas = e.candidatas.filter { accion(it.fuente) is Accion.Reproductor }; estado.elegida = e.elegida; estado.pistas = emptyList()
        listo = false; cortes.clear(); cargandoDesde = 0L; subsDecididos = false; avisadoEntrada = false; avisoDelDebrid = false
        val v = visionado
        visionado = if (v != null && v.breve.id == e.breve.id && e.video != null && v.videoId != e.video.id) v.siguiente(e.video, e.meta, ::reloj)
            else Visionado(e.breve, e.meta, e.video, ::reloj, ::ahora, previo = e.previo, pendiente = e.pendiente)
        val f = e.elegida.fuente
        fuenteSubs = f
        try {
            url = if (f.tipo == TipoFuente.TORRENT) {
                estado.mensaje = "Preparando el torrent…"
                // (con tope, como en el móvil: si no arranca en dos minutos y medio, a otra fuente)
                (runBlocking { kotlinx.coroutines.withTimeoutOrNull(150_000L) { Torrents.prepara(f.infoHash, f.fileIdx, f.trackers, f.archivo.ifBlank { e.breve.nombre }) } }
                    ?: run { Torrents.para(f.infoHash); throw SinTorrent("Este torrent no arranca: en 2 minutos y medio no ha llegado nada. Prueba con otra fuente.") })
                    .also { torrentAbierto = f.infoHash; estado.mensaje = null }
            } else f.url
        } catch (x: SinTorrent) { if (!fallaEnTanteo()) { estado.cargando = false; estado.aviso = x.message }; return }
        val c = asegura() ?: run { estado.cargando = false; estado.activo = false; estado.aviso = if (hayMpv) "No se ha podido abrir mpv." else Mpv.comoInstalar(); return }
        val tipo = e.breve.tipo.ifBlank { if (e.video != null) "series" else "movie" }
        subsPendientes = (runBlocking { withTimeoutOrNull(4_000) { runCatching { Addons.subtitulos(tipo, e.video?.id ?: e.breve.id, f) }.getOrDefault(emptyList()) } }.orEmpty() + f.subtitulos)
            .distinctBy { it.url }.let { ordenaSubs(it) }.take(12)
        val desde = Reanudar.desde(visionado!!.videoId)
        // Lo de este archivo, por el socket (nunca en la línea de órdenes): las cabeceras de la fuente, desde dónde, su título y, probando
        // fuentes, en pausa hasta ver sus pistas (para no ver un trozo en otro idioma)
        c.pon("http-header-fields", Mpv.cabeceras(f.cabeceras))
        c.pon("start", if (desde > 0) String.format(java.util.Locale.ROOT, "%.3f", desde / 1000.0) else "none")
        c.pon("force-media-title", listOf(t, s).filter { it.isNotBlank() }.joinToString(" · "))
        c.pon("pause", tanteando)
        c.pon("network-timeout", if (torrentAbierto.isNotBlank()) com.gonzalo.torrent.MotorTorrent.ESPERA_LECTURA_MS / 1000 else 30)
        entrada = -1; empezado = false
        val r = c.manda("loadfile", url, "replace")
        if (!r.ok) { if (!fallaEnTanteo()) { estado.cargando = false; estado.aviso = "No se ha podido abrir esta fuente." }; return }
        r.datos.comoMapa()["playlist_entry_id"]?.comoLong()?.takeIf { it > 0 }?.let { entrada = it }
    }

    /** Los subtítulos de tus addons, primero los de tu idioma de subtítulos (y luego el español). */
    private fun ordenaSubs(l: List<Subtitulo>): List<Subtitulo> {
        val pref = Mpv.idiomas(AjustesReproductor.subs()) + listOf("spa", "es")
        return l.sortedBy { s -> pref.indexOfFirst { s.idioma.lowercase().startsWith(it) }.let { if (it < 0) 99 else it } }
    }

    private fun suceso(s: MensajeMpv.Suceso) {
        val c = cliente ?: return
        // (lo que llega del archivo anterior después de pedir otro no cuenta: sus pistas, su duración o su final no son de este)
        if (s.nombre in setOf("file-loaded", "playback-restart", "seek", "end-file") && !empezado) return
        when (s.nombre) {
            "start-file" -> if (entrada < 0 || s.entrada == entrada) { entrada = s.entrada; empezado = true }
            "file-loaded" -> {
                if (esAvisoDelDebrid()) return
                val pistas = PistaMpv.deLista(c.lee("track-list"))
                estado.pistas = pistas
                if (!subsDecididos && PistasDeMpv.audios(pistas).isNotEmpty()) { subsDecididos = true; val g = generacion; alVerPistas(pistas); if (g == generacion) anadeSubtitulos() }
            }
            "playback-restart" -> {
                if (esAvisoDelDebrid()) return
                listo = true; cargandoDesde = 0L; estado.cargando = false
                estado.dur = ((c.leeDouble("duration") ?: 0.0) * 1000).toLong()
                reproduce(c.lee("core-idle")?.comoBool() == false)
                if (!subsDecididos) { val pistas = PistaMpv.deLista(c.lee("track-list")); estado.pistas = pistas; subsDecididos = true; val g = generacion; alVerPistas(pistas); if (g == generacion) anadeSubtitulos() }
            }
            "seek" -> visionado?.sesion?.salto()
            "end-file" -> {
                if (s.entrada > 0 && entrada > 0 && s.entrada != entrada) return
                when (s.razon) {
                    "eof" -> { if (esAvisoDelDebrid()) return; visionado?.guarda(estado.dur, estado.dur, forzar = true); alTerminar() }
                    "error" -> { if (fallaEnTanteo()) return; estado.cargando = false
                        estado.aviso = "No se puede ver esta fuente ahora (no responde, el enlace ha caducado o mpv no sabe abrirla). Prueba con otra." }
                }
            }
            "property-change" -> when (s.propiedad) {
                "core-idle" -> reproduce(!s.valor.comoBool())
                "paused-for-cache" -> if (s.valor.comoBool()) {
                    if (listo && c.lee("pause")?.comoBool() != true) {
                        val ahora = reloj()
                        cargandoDesde = ahora; cortes += ahora; cortes.removeAll { ahora - it > 120_000L }
                        if (cortes.size >= 3 && estado.corte == null) seCorta()
                    }
                } else cargandoDesde = 0L
            }
        }
    }

    /** mpv dice si está reproduciendo de verdad (ni en pausa, ni cargando): lo que cuenta para la sesión (como `isPlaying` en el móvil). */
    private fun reproduce(siMpv: Boolean) {
        val si = siMpv && listo && !avisoDelDebrid
        if (si == estado.reproduciendo) return
        estado.reproduciendo = si
        visionado?.sesion?.reproduciendo(si)
        if (!si) visionado?.guarda(estado.pos, estado.dur)
    }

    /** Cada segundo: por dónde va, lo que se ha visto de verdad, y lo que toca (cargando demasiado, el tanteo, la cuenta atrás). */
    private fun late() {
        val c = cliente ?: return
        if (c.cerrado) return
        c.leeDouble("time-pos")?.let { estado.pos = (it * 1000).toLong().coerceAtLeast(0L) }
        c.leeDouble("duration")?.let { if (it > 0) estado.dur = (it * 1000).toLong() }
        if (estado.reproduciendo) {
            visionado?.tick(estado.pos, estado.dur)
            c.lee("cache-speed")?.comoLong()?.takeIf { it > 0 }?.let { velocidades += it * 8 / 1000; if (velocidades.size > 600) velocidades.removeAt(0) }
        }
        visionado?.let { v -> if (v.entro && !avisadoEntrada) { avisadoEntrada = true; muestra("Ha entrado en «Viendo»") } }
        if (mensajeHasta > 0 && reloj() > mensajeHasta) { mensajeHasta = 0; estado.mensaje = null }
        if (cargandoDesde > 0 && listo && reloj() - cargandoDesde > 15_000L && estado.corte == null) seCorta()
        if (tanteando && !subsDecididos) tanteo?.let { t -> val d = t.trasTiempo(encargo!!.elegida, reloj()); if (t.terminado) haz(d) }
        if (estado.cuentaAtras >= 0) {
            if (estado.cuentaAtras == 0) siguienteYa()
            else { c.manda("show-text", "Siguiente: ${estado.siguiente.orEmpty()} en ${estado.cuentaAtras} s", 1_200); estado.cuentaAtras-- }
        }
    }

    /** mpv se ha cerrado (su ventana, «q», o Mostaza al salir): termina la sesión (regla 1) y se apunta. */
    private fun alSalir() {
        if (!estado.activo && cliente == null) return
        pulso?.cancel(false); pulso = null
        visionado?.termina(estado.pos, estado.dur)
        visionado = null
        if (listo && cortes.isEmpty() && velocidades.isNotEmpty()) Conexion.apunta(velocidades.sorted()[velocidades.size / 2])
        velocidades.clear()
        cierraTorrent()
        runCatching { cliente?.close() }; cliente = null
        runCatching { proceso?.destroy() }; proceso = null
        tanteo?.cancela(); tanteo = null
        encargo = null
        estado.activo = false; estado.reproduciendo = false; estado.cargando = false; estado.siguiente = null; estado.cuentaAtras = -1; estado.corte = null; estado.mensaje = null
    }

    private fun muestra(m: String, ms: Long = 4_000L) {
        estado.mensaje = m; mensajeHasta = reloj() + ms
        cliente?.manda("show-text", m, ms.toInt())
    }

    // ---------------------------------------------------------------- las pistas (como en el móvil)

    private fun nuevoTanteo(e: Encargo): Tanteo {
        val rotas = FuentesRotas.busca()
        return Tanteo(AjustesElector.prefs(), e.candidatas.filter { accion(it.fuente) is Accion.Reproductor && (it === e.elegida || !rotas(Elector.clave(it.fuente))) }, reloj())
    }

    /**
     * ¿Lo que se ha abierto es un vídeo de aviso de tu debrid ([FuentesRotas]) y no lo que querías ver? Entonces no cuenta para nada (ni
     * la posición, ni el tiempo, ni «Visto»): se apunta en tu cuenta que esta fuente no vale y se pasa sola a la siguiente.
     */
    private fun esAvisoDelDebrid(): Boolean {
        if (avisoDelDebrid) return true
        val e = encargo ?: return false
        val dur = ((cliente?.leeDouble("duration") ?: 0.0) * 1000).toLong()
        if (!FuentesRotas.esAviso(dur, e.meta?.minutos ?: 0)) return false
        avisoDelDebrid = true
        FuentesRotas.apunta(Elector.clave(e.elegida.fuente))
        val v = visionado
        visionado = null
        cliente?.pon("pause", true)
        estado.reproduciendo = false
        val previo = v?.sesion?.vistoMs ?: e.previo
        val pendiente = v?.takeIf { !it.enCuenta }?.pendiente() ?: e.pendiente
        val t = tanteo
        val otra = if (t != null && !t.terminado) when (val d = t.trasFallo(e.elegida, reloj())) { is Decision.Prueba -> d.candidata; is Decision.Vuelve -> d.candidata; else -> null } else null
        val sig = otra ?: siguienteCandidata()
        if (sig == null) {
            tanteo?.cancela()
            estado.cargando = false; estado.mensaje = null
            estado.aviso = "Tu debrid no da este archivo: en vez de la película, el addon manda un vídeo de aviso. No queda otra fuente que probar sola; elige otra en «Fuente»."
            return true
        }
        estado.mensaje = "Tu debrid no da este archivo: probando otra…"
        hilo.execute { cierraTorrent(); abre(Encargo(e.breve, e.meta, e.video, sig, e.candidatas, previo, pendiente, e.automatica)) }
        return true
    }

    /**
     * Ya se ven las pistas de verdad del archivo (las de mpv): se recuerda lo que trae (en este ordenador), se pone su mejor audio según tu
     * orden (y subtítulos en español si el audio no lo es y aceptas la original con subtítulos) y, si se están probando fuentes, se decide.
     */
    private fun alVerPistas(pistas: List<PistaMpv>) {
        val e = encargo ?: return
        val prefs = AjustesElector.prefs()
        val audios = PistasDeMpv.audios(pistas)
        val v = PistasDeMpv.verifica(pistas, prefs)
        if (audios.isNotEmpty()) MemoriaPistas.apunta(Elector.clave(e.elegida.fuente), v)
        decidePistas(pistas, prefs)
        val t = tanteo
        if (t == null || t.terminado || audios.isEmpty()) { cliente?.pon("pause", false); return }
        haz(t.tras(e.elegida, v, reloj()))
    }

    private fun refrescaPistas() { cliente?.let { estado.pistas = PistaMpv.deLista(it.lee("track-list")) } }

    private fun normal(l: String) = when (val x = l.lowercase().take(3)) { "spa", "esp", "es-", "es" -> "es"; "eng", "en-", "en" -> "en"; else -> x.take(2) }

    /** El audio y los subtítulos (lo mismo que decide el reproductor del móvil). */
    private fun decidePistas(pistas: List<PistaMpv>, prefs: PrefsElector) {
        val c = cliente ?: return
        val audioPref = AjustesReproductor.audio()
        val prefEspanol = audioPref.isBlank() || normal(audioPref) == "es"
        val audios = PistasDeMpv.audios(pistas)
        var audio: Idioma? = audios.firstOrNull { it.elegida }?.let { Pistas.clasifica(it.idioma, it.titulo) }
        var audioSel = audios.firstOrNull { it.elegida }?.idioma
        if (prefEspanol) PistasDeMpv.mejorAudio(pistas, prefs)?.let { id ->
            val p = audios.first { it.id == id }
            audio = Pistas.clasifica(p.idioma, p.titulo); audioSel = p.idioma
            if (!p.elegida) c.pon("aid", id)
        }
        val enMiIdioma = if (prefEspanol) audio?.espanol == true else audioSel != null && normal(audioSel!!) == normal(audioPref)
        quiereSubs = !enMiIdioma && Pistas.quiereSubtitulos(audio, prefs)
        if (enMiIdioma) {
            if (!AjustesReproductor.subsSiempre()) c.pon("sid", "no")
        } else if (Pistas.quiereSubtitulos(audio, prefs)) {
            val subs = PistasDeMpv.subtitulos(pistas)
            if (subs.none { it.elegida && Pistas.subtituloEspanol(it.idioma, it.titulo) }) PistasDeMpv.subtituloEspanol(pistas, prefs)?.let { c.pon("sid", it) }
        }
        refrescaPistas()
    }

    /**
     * Los subtítulos de tus addons, como pistas más del archivo (después de decidir el audio y si se prueba otra fuente: bajarlos tarda, y
     * no se bajan para una fuente que se va a dejar). Si hacen falta en español y el archivo no los traía, se pone uno de estos.
     */
    @Volatile private var fuenteSubs: Fuente? = null

    private fun anadeSubtitulos() {
        val c = cliente ?: return
        val l = subsPendientes.take(6); subsPendientes = emptyList()
        if (l.isEmpty()) return
        val g = generacion
        // (se bajan aquí y se le da a mpv el archivo: así las cabeceras de la fuente —tu debrid, tu token de kDrive— no van a otros servidores)
        val fuente = fuenteSubs
        l.forEach { x -> if (g == generacion) c.manda("sub-add", Subs.local(x, fuente)?.absolutePath ?: return@forEach, "auto", x.etiqueta.ifBlank { PistaMpv.nombreIdioma(x.idioma).ifBlank { "Subtítulos" } }, x.idioma, espera = 8_000) }
        if (g != generacion) return
        val pistas = PistaMpv.deLista(c.lee("track-list"))
        val subs = PistasDeMpv.subtitulos(pistas)
        when {
            quiereSubs && subs.none { it.elegida && Pistas.subtituloEspanol(it.idioma, it.titulo) } -> PistasDeMpv.subtituloEspanol(pistas, AjustesElector.prefs())?.let { c.pon("sid", it) }
            !quiereSubs && !AjustesReproductor.subsSiempre() && subs.any { it.elegida } -> c.pon("sid", "no")
        }
        refrescaPistas()
    }

    /** Lo que ha decidido el [Tanteo]. */
    private fun haz(d: Decision) {
        when (d) {
            is Decision.SeQueda -> { estado.mensaje = null; cliente?.pon("pause", false); d.mensaje?.let { muestra(it, 7_000L) } }
            is Decision.Prueba -> { estado.mensaje = d.mensaje; cliente?.manda("show-text", d.mensaje, 4_000); pruebaOtra(d.candidata) }
            is Decision.Vuelve -> { pruebaOtra(d.candidata); muestra(d.mensaje, 8_000L) }
            Decision.Nada -> { estado.mensaje = null; cliente?.pon("pause", false) }
        }
    }

    /** Falla la fuente que se está probando: ¿hay otra? (true si se encarga el tanteo y no hay que avisar) */
    private fun fallaEnTanteo(): Boolean {
        val t = tanteo ?: return false
        if (t.terminado) return false
        val e = encargo ?: return false
        val d = t.trasFallo(e.elegida, reloj())
        if (d is Decision.Nada) { estado.mensaje = null; return false }
        hilo.execute { haz(d) }
        return true
    }

    /** Otra fuente mientras se prueba: sin apuntar nada de esta (no se ha visto) y desde el mismo sitio. */
    private fun pruebaOtra(c: Candidata) {
        val e = encargo ?: return
        cierraTorrent()
        val v = visionado
        visionado = null
        abre(Encargo(e.breve, e.meta, e.video, c, e.candidatas, v?.sesion?.vistoMs ?: 0L, v?.takeIf { !it.enCuenta }?.pendiente().orEmpty(), e.automatica))
    }

    private fun cambiaA(c: Candidata) {
        val e = encargo ?: return
        // La eliges tú: se acabó probar solas
        tanteo?.cancela(); tanteo = null; estado.mensaje = null
        val pos = estado.pos
        visionado?.guarda(pos, estado.dur, forzar = true)
        cierraTorrent()
        Reanudar.pon(visionado?.videoId ?: e.breve.id, pos, estado.dur)
        val v = visionado
        visionado = null   // (la misma sesión sigue con la otra fuente: lo visto y lo que falta por apuntar pasan al nuevo)
        abre(Encargo(e.breve, e.meta, e.video, c, e.candidatas, v?.sesion?.vistoMs ?: 0L, v?.takeIf { !it.enCuenta }?.pendiente().orEmpty()))
    }

    /** Se corta una y otra vez: se apunta que la conexión da menos y se propone la siguiente. */
    private fun seCorta() {
        val kbps = (cliente?.lee("cache-speed")?.comoLong() ?: 0L) * 8 / 1000
        Conexion.apunta(kbps, corte = true)
        val sig = siguienteCandidata() ?: return
        estado.corte = "Se corta. ¿Probar con ${sig.analisis.resumen().ifBlank { sig.addon }}?"
    }

    private fun siguienteCandidata(): Candidata? {
        val e = encargo ?: return null
        val cs = e.candidatas.filter { accion(it.fuente) is Accion.Reproductor }
        val i = cs.indexOfFirst { it === e.elegida }
        val rotas = FuentesRotas.busca()
        return cs.drop(i + 1).firstOrNull { !rotas(Elector.clave(it.fuente)) }
    }

    // ---------------------------------------------------------------- el episodio siguiente

    private fun videoSiguiente(): Video? {
        val e = encargo ?: return null
        val m = e.meta ?: return null
        val v = e.video ?: return null
        val eps = m.videos.filter { it.temporada > 0 }.sortedWith(compareBy({ it.temporada }, { it.episodio }))
        val i = eps.indexOfFirst { it.id == v.id }
        return eps.getOrNull(i + 1)?.takeIf { it.estrenado() }
    }

    private fun alTerminar() {
        val sig = videoSiguiente()
        if (sig == null || !AjustesReproductor.siguiente()) { visionado?.termina(estado.dur, estado.dur); visionado = null; muestra("Fin", 3_000); return }
        estado.siguiente = "T${sig.temporada} · E${sig.episodio}" + if (sig.titulo.isNotBlank()) " · ${sig.titulo}" else ""
        estado.cuentaAtras = 8
    }

    /** El siguiente episodio: sus fuentes, y la que toca (la del mismo grupo que esta, si la hay). */
    private fun siguienteYa() {
        val e = encargo ?: return
        val sig = videoSiguiente() ?: return
        estado.cuentaAtras = -1; estado.siguiente = null; estado.cargando = true
        val grupo = e.elegida.fuente.grupo
        val cs = runBlocking { Busqueda.fuentes("series", sig.id) }
        val m = e.meta
        val el = Busqueda.elige(cs, m, grupo)
        val c = el.elegida ?: run { estado.cargando = false; estado.aviso = "No hay fuentes para el episodio siguiente."; return }
        visionado?.termina(estado.dur, estado.dur)
        cierraTorrent()
        val nuevo = Encargo(e.breve, m, sig, c, el.ordenadas, automatica = true)
        tanteo = nuevoTanteo(nuevo)
        abre(nuevo)
    }

    private fun cierraTorrent() { if (torrentAbierto.isNotBlank()) { Torrents.para(torrentAbierto); torrentAbierto = "" } }

    /** (Para las pruebas: el proceso de mpv, para mirar su línea de órdenes.) */
    fun pidParaPruebas(): Long? = proceso?.takeIf { it.isAlive }?.pid()

    /** (Para las pruebas: espera a que el hilo del reproductor haya hecho lo que tenía pendiente.) */
    fun esperaParaPruebas() { runCatching { hilo.submit {}.get(10, TimeUnit.SECONDS) } }
}

/** Abrir mpv a pantalla completa (es de este ordenador: no viaja). */
object PantallaCompleta {
    fun si(): Boolean = Local.lee("mpvPantallaCompleta")?.comoBool() ?: true
    fun pon(v: Boolean) = Local.pon("mpvPantallaCompleta", v)
}

/** Buscar las fuentes de un vídeo en tus addons y elegir la mejor (sin reproducir todavía). */
object Busqueda {
    /** En las pruebas de pantallas: fuentes fijas. */
    @Volatile var fijas: List<Candidata>? = null

    suspend fun fuentes(tipo: String, videoId: String, alLlegar: (List<Candidata>) -> Unit = {}): List<Candidata> = fijas ?: Addons.fuentes(tipo, videoId, alLlegar)

    fun elige(cs: List<Candidata>, m: Meta?, grupo: String = ""): Eleccion =
        Elector.elige(cs, AjustesElector.prefs(), Conexion.red(), Conexion.capacidades, m?.minutos ?: 0, Torrents.disponible, grupo, MemoriaPistas.busca(), FuentesRotas.busca())
}
