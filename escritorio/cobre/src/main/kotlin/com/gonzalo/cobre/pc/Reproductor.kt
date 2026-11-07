package com.gonzalo.cobre.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.Actualizador
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.Episodio
import com.gonzalo.iptv.FichaSerie
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Pelicula
import com.gonzalo.iptv.Programa
import com.gonzalo.iptv.Progreso
import com.gonzalo.iptv.Serie
import com.gonzalo.iptv.Visto
import com.gonzalo.iptv.Xtream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Lo que se le pasa al reproductor: un canal (con los de su grupo, para cambiar de canal), una película o un episodio (como en el móvil). */
sealed class Encargo {
    abstract val lista: Lista
    class Directo(override val lista: Lista, val canales: List<Canal>, val indice: Int) : Encargo() { val canal: Canal get() = canales[indice] }
    class DePelicula(override val lista: Lista, val pelicula: Pelicula, val duracionSeg: Long = 0L) : Encargo()
    class DeEpisodio(override val lista: Lista, val serie: Serie, val ficha: FichaSerie?, val episodio: Episodio) : Encargo()
}

/** Lo que enseña la banda del reproductor (en la ventana de Cobre, mientras mpv tiene la suya). */
class EstadoReproductor {
    var activo by mutableStateOf(false)
    var directo by mutableStateOf(false)
    var titulo by mutableStateOf(""); var subtitulo by mutableStateOf(""); var numero by mutableStateOf(0); var logo by mutableStateOf("")
    var reproduciendo by mutableStateOf(false); var cargando by mutableStateOf(false)
    var pos by mutableStateOf(0L); var dur by mutableStateOf(0L)
    /** Lo que se emite ahora y lo siguiente (en directo). */
    var ahora by mutableStateOf<Programa?>(null); var despues by mutableStateOf<Programa?>(null)
    /** Un aviso que no se va solo (no se puede ver…). */
    var aviso by mutableStateOf<String?>(null)
    /** Un mensaje que se va solo («Se ha cortado: volviendo a conectar…»). */
    var mensaje by mutableStateOf<String?>(null)
    /** El episodio siguiente, y en cuántos segundos empieza (−1: sin cuenta atrás). */
    var siguiente by mutableStateOf<String?>(null); var cuentaAtras by mutableStateOf(-1)
    /** Las pistas (para elegir audio y subtítulos desde aquí). */
    var pistas by mutableStateOf<List<PistaMpv>>(emptyList())
    /** La lista de canales encima (la rápida), con el que está marcado; null = cerrada. */
    var lista by mutableStateOf<List<Canal>?>(null); var enLista by mutableStateOf(0)
    /** Lo que llevas tecleado de un número de canal. */
    var tecleado by mutableStateOf("")
}

/**
 * El reproductor del ordenador: mpv ([Mpv], como en Mostaza del ordenador) con la lógica del reproductor de Cobre del móvil
 * (`ReproductorActivity`). En directo: arriba y abajo cambian de canal (en la ventana de mpv, en la de Cobre y con el mando; se puede dar la
 * vuelta: arriba el anterior), los números van al canal por su número, Intro abre la lista de canales encima del vídeo (con lo que echan en
 * cada uno), al cambiar de canal se ve lo que echan ahora y lo que viene después, y si se corta vuelve a conectar solo (hasta seis veces,
 * esperando cada vez un poco más). En las películas y las series: sigue donde lo dejaste (aquí o en otro dispositivo), apunta por dónde vas
 * en la cuenta, visto al 90 % y el episodio siguiente en 8 segundos. Un solo mpv, que se queda abierto entre canales y episodios.
 *
 * Todo lo de la sesión pasa en un solo hilo ([hilo]): los sucesos de mpv, el pulso de cada segundo y lo que pides desde la ventana.
 */
object Reproductor {
    val estado = EstadoReproductor()
    private val hilo = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cobre-reproductor").apply { isDaemon = true } }
    @Volatile private var proceso: Process? = null
    @Volatile private var cliente: ClienteMpv? = null
    private var pulso: ScheduledFuture<*>? = null

    @Volatile private var encargo: Encargo? = null
    /** La entrada de la lista de mpv que es lo de ahora (los sucesos de la anterior, al cambiar, no cuentan). */
    private var entrada = -1L
    private var empezado = false
    private var listo = false
    private var reintentos = 0
    private var reintento: ScheduledFuture<*>? = null
    private var tecleo: ScheduledFuture<*>? = null
    private var guardadoEn = 0L
    private var mensajeHasta = 0L
    private var listaHasta = 0L

    /** Cada cuánto se le pregunta a mpv por dónde va (en las pruebas, más a menudo). */
    @Volatile var pulsoMs = 1_000L
    /** Cuánto espera antes de volver a conectar (la primera vez; luego el doble cada vez). En las pruebas, menos. */
    @Volatile var esperaReintento = 1_000L

    /** ¿Está mpv en este ordenador? */
    val hayMpv: Boolean get() = Mpv.busca() != null

    /** Pone un encargo. Devuelve un aviso si no se puede (sin mpv: cómo instalarlo), o null si ya está en marcha. */
    fun pon(e: Encargo): String? {
        if (!hayMpv) return Mpv.comoInstalar()
        hilo.execute { runCatching { empieza(e) }.onFailure { x -> estado.aviso = "No se ha podido abrir el reproductor: ${x.javaClass.simpleName}" } }
        return null
    }

    /** Un canal, con los de su grupo para cambiar de canal. */
    fun canal(l: Lista, canales: List<Canal>, c: Canal): String? {
        val i = canales.indexOfFirst { it.clave == c.clave }
        return pon(Encargo.Directo(l, if (i < 0) listOf(c) else canales, i.coerceAtLeast(0)))
    }

    /** Cierra mpv (termina lo que se veía y se apunta). */
    fun para() { hilo.execute { if (cliente != null) { guarda(); cliente?.manda("quit") } else alSalir() } }

    /** Al cerrar Cobre: lo que se veía se apunta (espera un poco: luego se sube lo pendiente). */
    fun alCerrarLaApp() {
        runCatching { hilo.submit { guarda(); cliente?.manda("quit", espera = 1_500); alSalir() }.get(4, TimeUnit.SECONDS) }
        runCatching { proceso?.destroy() }
    }

    fun alterna() = hilo.execute { cliente?.manda("cycle", "pause") }
    fun salta(seg: Int) = hilo.execute { if (encargo !is Encargo.Directo) cliente?.manda("seek", seg, "relative") }
    fun ponAudio(id: Int) = hilo.execute { cliente?.pon("aid", id); refrescaPistas() }
    fun ponSubtitulos(id: Int?) = hilo.execute { cliente?.pon("sid", id ?: "no"); refrescaPistas() }
    fun siguienteEpisodioYa() = hilo.execute { siguienteYa() }
    fun cancelaSiguiente() = hilo.execute { estado.cuentaAtras = -1; estado.siguiente = null }
    fun quitaAviso() { estado.aviso = null }
    /** Al canal de al lado (+1 el siguiente, −1 el anterior), en el grupo con el que se abrió. */
    fun zapea(d: Int) = hilo.execute { cambiaCanal(d) }
    /** «Arriba» (con lo de dar la vuelta: arriba, el anterior). */
    fun arriba() = zapea(if (AjustesReproductor.invertirCanales()) -1 else 1)
    fun abajo() = zapea(if (AjustesReproductor.invertirCanales()) 1 else -1)
    fun vaACanal(i: Int) = hilo.execute { irA(i) }
    fun abreLista() = hilo.execute { listaEncima() }
    fun cierraLista() = hilo.execute { estado.lista = null; listaHasta = 0; cliente?.manda("show-text", "", 1) }
    fun numero(n: Int) = hilo.execute { teclea(n) }
    fun reintentaYa() = hilo.execute { reintenta(manual = true) }

    // ---------------------------------------------------------------- mpv

    private fun opciones() = Mpv.Opciones(audio = Mpv.idiomas(AjustesReproductor.audio()), subs = Mpv.idiomas(AjustesReproductor.subs()), pantallaCompleta = PantallaCompleta.si(),
        agente = agentePorDefecto())

    private fun agentePorDefecto() = "Cobre/${Actualizador.version}"

    /** Abre mpv si no está abierto (en su ventana, vacío: lo que se ve se le da por el socket). */
    private fun asegura(): ClienteMpv? {
        cliente?.takeIf { !it.cerrado && proceso?.isAlive == true }?.let { return it }
        val mpv = Mpv.busca() ?: return null
        val dir = Mpv.carpetaSocket(Local.dir)
        val socket = File(dir, "s${ProcessHandle.current().pid()}.sock").also { it.delete() }
        val teclas = File(dir, "teclas.conf").also { runCatching { it.writeText(Mpv.teclas()) } }
        val p = ProcessBuilder(Mpv.orden(mpv, socket, opciones().copy(teclas = teclas.takeIf { it.isFile })))
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        proceso = p
        val c = ClienteMpv.conecta(socket, vivo = { p.isAlive }) { s -> hilo.execute { runCatching { suceso(s) } } } ?: run { p.destroy(); proceso = null; return null }
        cliente = c
        c.alCerrar = { hilo.execute { alSalir() } }
        c.observa(1, "core-idle")
        Thread({ p.waitFor(); hilo.execute { alSalir() } }, "cobre-mpv-fin").apply { isDaemon = true; start() }
        pulso?.cancel(false)
        pulso = hilo.scheduleAtFixedRate({ runCatching { late() } }, pulsoMs, pulsoMs, TimeUnit.MILLISECONDS)
        return c
    }

    /** Lo de un encargo nuevo: lo de antes se apunta (si era una película) y se abre lo nuevo. */
    private fun empieza(e: Encargo) {
        guarda()
        encargo = e
        reintentos = 0
        abre()
    }

    private fun abre() {
        val e = encargo ?: return
        reintento?.cancel(false); reintento = null
        estado.activo = true; estado.aviso = null; estado.siguiente = null; estado.cuentaAtras = -1; estado.cargando = true; estado.reproduciendo = false
        estado.pistas = emptyList(); estado.pos = 0L
        listo = false; guardadoEn = 0L
        val l = e.lista
        val url: String; val cab: Map<String, String>; val desde: Long
        when (e) {
            is Encargo.Directo -> {
                val c = e.canal
                estado.directo = true; estado.titulo = c.nombre; estado.numero = if (c.numero > 0) c.numero else e.indice + 1; estado.logo = c.logo; estado.subtitulo = c.grupo
                estado.dur = 0L; estado.ahora = null; estado.despues = null
                if (c.conDrm) { estado.cargando = false; estado.aviso = "Este canal va cifrado con DRM (Widevine o parecido): Cobre no lo puede abrir."; cliente?.manda("stop"); return }
                url = Tele.url(l, c); cab = Tele.cabeceras(l, c); desde = 0L
                MiCuenta.ponUltimo(l.id, c)
                refrescaGuia(e)
            }
            is Encargo.DePelicula -> {
                estado.directo = false; estado.titulo = e.pelicula.nombre; estado.subtitulo = e.pelicula.ano; estado.logo = ""; estado.numero = 0
                url = Xtream(l, Tele.red).urlPelicula(e.pelicula); cab = Tele.cabecerasLista(l)
                estado.dur = e.duracionSeg * 1000L
                desde = Reanudar.desde(l.id, DatosCuenta.videoPelicula(e.pelicula))
            }
            is Encargo.DeEpisodio -> {
                val ep = e.episodio
                estado.directo = false; estado.titulo = e.serie.nombre; estado.subtitulo = "T${ep.temporada} · E${ep.numero}" + if (ep.titulo.isNotBlank()) " · ${tituloEpisodio(ep, e.serie.nombre)}" else ""
                estado.logo = ""; estado.numero = 0
                url = Xtream(l, Tele.red).urlEpisodio(ep); cab = Tele.cabecerasLista(l)
                estado.dur = ep.duracionSeg * 1000L
                desde = Reanudar.desde(l.id, DatosCuenta.videoEpisodio(ep))
            }
        }
        val c = asegura() ?: run { estado.cargando = false; estado.activo = false; estado.aviso = if (hayMpv) "No se ha podido abrir mpv." else Mpv.comoInstalar(); return }
        // Lo de este canal o vídeo, por el socket (nunca en la línea de órdenes): sus cabeceras, su user-agent, desde dónde, su título y la imagen
        c.pon("http-header-fields", Mpv.cabeceras(cab))
        c.pon("user-agent", Mpv.agente(cab, agentePorDefecto()))
        c.pon("start", if (desde > 0) String.format(java.util.Locale.ROOT, "%.3f", desde / 1000.0) else "none")
        c.pon("force-media-title", titulo())
        aspecto(c)
        c.pon("pause", false)
        entrada = -1; empezado = false
        val r = c.manda("loadfile", url, "replace")
        if (!r.ok) { estado.cargando = false; estado.aviso = "No se ha podido abrir esto."; return }
        r.datos.comoMapa()["playlist_entry_id"]?.comoLong()?.takeIf { it > 0 }?.let { entrada = it }
        if (e is Encargo.Directo) muestraInfo()
    }

    private fun titulo(): String = when (val e = encargo) {
        is Encargo.Directo -> "${estado.numero} · ${e.canal.nombre}"
        else -> listOf(estado.titulo, estado.subtitulo).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** La imagen como la elegiste (`aspecto`: ajustar, llenar, zoom, 16:9, 4:3). */
    private fun aspecto(c: ClienteMpv) {
        val a = AjustesReproductor.aspecto()
        c.pon("keepaspect", a != AjustesReproductor.Aspecto.LLENAR)
        c.pon("panscan", if (a == AjustesReproductor.Aspecto.ZOOM) 1.0 else 0.0)
        c.pon("video-aspect-override", when (a) { AjustesReproductor.Aspecto.ANCHO -> "16:9"; AjustesReproductor.Aspecto.ESTRECHO -> "4:3"; else -> "-1" })
    }

    private fun suceso(s: MensajeMpv.Suceso) {
        val c = cliente ?: return
        if (s.nombre == "client-message") { mensajeDeMpv(s.palabras); return }
        // (lo que llega del archivo anterior después de pedir otro no cuenta)
        if (s.nombre in setOf("file-loaded", "playback-restart", "end-file") && !empezado) return
        when (s.nombre) {
            "start-file" -> if (entrada < 0 || s.entrada == entrada) { entrada = s.entrada; empezado = true }
            "file-loaded" -> estado.pistas = PistaMpv.deLista(c.lee("track-list"))
            "playback-restart" -> {
                listo = true; reintentos = 0; estado.cargando = false
                if (estado.mensaje?.startsWith("Se ha cortado") == true) estado.mensaje = null
                if (!estado.directo) ((c.leeDouble("duration") ?: 0.0) * 1000).toLong().takeIf { it > 0 }?.let { estado.dur = it }
                estado.reproduciendo = c.lee("core-idle")?.comoBool() == false
                estado.pistas = PistaMpv.deLista(c.lee("track-list"))
            }
            "end-file" -> {
                if (s.entrada > 0 && entrada > 0 && s.entrada != entrada) return
                val e = encargo ?: return
                when (s.razon) {
                    // Un canal en directo no se acaba: si el servidor corta (o falla), se vuelve a conectar
                    "eof", "error" -> if (e is Encargo.Directo) {
                        val tope = if (listo) 6 else 2
                        if (AjustesReproductor.reconectar() && reintentos < tope) reintenta()
                        else { estado.cargando = false; estado.mensaje = null; estado.aviso = NO_SE_PUEDE }
                    } else if (s.razon == "eof") { guarda(fin = true); alTerminar() }
                    else { estado.cargando = false; estado.aviso = NO_SE_PUEDE }
                }
            }
            "property-change" -> if (s.propiedad == "core-idle") {
                val si = !s.valor.comoBool() && listo
                if (si != estado.reproduciendo) { estado.reproduciendo = si; if (!si) guarda() }
            }
        }
    }

    const val NO_SE_PUEDE = "No se puede ver ahora: el servidor no responde, no deja verlo (¿ha caducado tu cuenta o estás viendo en demasiados sitios a la vez?) o mpv no sabe abrirlo."

    /** Se ha cortado: se vuelve a conectar, esperando cada vez un poco más (1, 2, 4, 8… segundos). */
    private fun reintenta(manual: Boolean = false) {
        if (encargo == null) return
        if (manual) reintentos = 0
        reintentos++
        val espera = if (manual) 0L else (esperaReintento shl (reintentos - 1).coerceAtMost(4))
        estado.aviso = null; estado.cargando = true
        estado.mensaje = "Se ha cortado: volviendo a conectar…"
        cliente?.manda("show-text", "Se ha cortado: volviendo a conectar…", 3_000)
        reintento?.cancel(false)
        reintento = hilo.schedule({ if (encargo != null) abre() }, espera, TimeUnit.MILLISECONDS)
    }

    /** Lo que pulsas en la ventana de mpv (sus teclas: [Mpv.teclas]). */
    private fun mensajeDeMpv(p: List<String>) {
        if (p.firstOrNull() != "cobre") return
        val e = encargo
        val enLista = estado.lista != null
        when (p.getOrNull(1)) {
            "numero" -> p.getOrNull(2)?.toIntOrNull()?.let { if (e is Encargo.Directo) teclea(it) }
            "tecla" -> when (p.getOrNull(2)) {
                "arriba" -> when { enLista -> mueveCursor(-1); e is Encargo.Directo -> cambiaCanal(if (AjustesReproductor.invertirCanales()) -1 else 1); else -> cliente?.manda("seek", 60, "relative") }
                "abajo" -> when { enLista -> mueveCursor(1); e is Encargo.Directo -> cambiaCanal(if (AjustesReproductor.invertirCanales()) 1 else -1); else -> cliente?.manda("seek", -60, "relative") }
                "lista" -> when { enLista -> irA(estado.enLista); e is Encargo.Directo -> listaEncima(); e != null -> cliente?.manda("cycle", "pause") }
                "volver" -> if (enLista) { estado.lista = null; listaHasta = 0; cliente?.manda("show-text", "", 1) } else cliente?.pon("fullscreen", false)
                "info" -> if (e is Encargo.Directo) muestraInfo() else cliente?.manda("show-progress")
            }
        }
    }

    /** Cada segundo: por dónde va, lo visto, la cuenta atrás del episodio siguiente y lo que se enseña. */
    private fun late() {
        val c = cliente ?: return
        if (c.cerrado) return
        val e = encargo
        if (e != null && e !is Encargo.Directo) {
            c.leeDouble("time-pos")?.let { estado.pos = (it * 1000).toLong().coerceAtLeast(0L) }
            c.leeDouble("duration")?.let { if (it > 0) estado.dur = (it * 1000).toLong() }
            if (estado.reproduciendo && System.currentTimeMillis() - guardadoEn > 10_000L) guarda()
        }
        if (e is Encargo.Directo && listo && System.currentTimeMillis() > (estado.ahora?.fin ?: Long.MAX_VALUE)) refrescaGuia(e)
        if (mensajeHasta > 0 && System.currentTimeMillis() > mensajeHasta) { mensajeHasta = 0; estado.mensaje = null }
        if (listaHasta > 0 && System.currentTimeMillis() > listaHasta) { listaHasta = 0; estado.lista = null }
        if (estado.cuentaAtras >= 0) {
            if (estado.cuentaAtras == 0) siguienteYa()
            else { c.manda("show-text", "Siguiente: ${estado.siguiente.orEmpty()} en ${estado.cuentaAtras} s", 1_200); estado.cuentaAtras-- }
        }
    }

    /** mpv se ha cerrado (su ventana, «q», o Cobre al salir): se apunta lo que se veía. */
    private fun alSalir() {
        if (!estado.activo && cliente == null) return
        guarda()
        pulso?.cancel(false); pulso = null
        reintento?.cancel(false); reintento = null
        runCatching { cliente?.close() }; cliente = null
        runCatching { proceso?.destroy() }; proceso = null
        encargo = null
        estado.activo = false; estado.reproduciendo = false; estado.cargando = false; estado.siguiente = null; estado.cuentaAtras = -1; estado.mensaje = null
        estado.lista = null; estado.tecleado = ""
    }

    private fun muestra(m: String, ms: Long = 4_000L) {
        estado.mensaje = m; mensajeHasta = System.currentTimeMillis() + ms
        cliente?.manda("show-text", m, ms.toInt())
    }

    private fun refrescaPistas() { cliente?.let { estado.pistas = PistaMpv.deLista(it.lee("track-list")) } }

    // ---------------------------------------------------------------- canales

    private fun cambiaCanal(d: Int) {
        val e = encargo as? Encargo.Directo ?: return
        if (e.canales.size <= 1) return
        val i = ((e.indice + d) % e.canales.size + e.canales.size) % e.canales.size
        encargo = Encargo.Directo(e.lista, e.canales, i); reintentos = 0
        abre()
    }

    private fun irA(i: Int) {
        val e = encargo as? Encargo.Directo ?: return
        estado.lista = null; listaHasta = 0
        val j = i.coerceIn(0, e.canales.size - 1)
        if (j == e.indice) { cliente?.manda("show-text", "", 1); return }
        encargo = Encargo.Directo(e.lista, e.canales, j); reintentos = 0
        abre()
    }

    private fun hora(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }

    private val guias = Executors.newSingleThreadExecutor { r -> Thread(r, "cobre-guia-canal").apply { isDaemon = true } }

    /**
     * Lo que se emite y lo siguiente de este canal: al momento, lo de la guía ya leída; y luego (aparte, que puede pedirlo a la red: la guía
     * corta de Xtream), lo de [Tele.programas]. Si cambia, se vuelve a enseñar.
     */
    private fun refrescaGuia(e: Encargo.Directo) {
        val c = e.canal
        val ahora = System.currentTimeMillis()
        Tele.enMemoria(e.lista.id)?.let { cg -> Tele.ahoraYDespuesRapido(cg, c, ahora).let { (a, d) -> estado.ahora = a; estado.despues = d } }
        guias.execute {
            val prog = runCatching { Tele.programas(e.lista, c) }.getOrDefault(emptyList())
            hilo.execute {
                if ((encargo as? Encargo.Directo)?.canal?.clave != c.clave) return@execute
                val t = System.currentTimeMillis()
                val a = prog.firstOrNull { it.emitiendose(t) }; val d = prog.firstOrNull { it.inicio >= (a?.fin ?: t) }
                val cambia = a != estado.ahora || d != estado.despues
                estado.ahora = a; estado.despues = d
                if (cambia && a != null && estado.lista == null) muestraInfo()
            }
        }
    }

    /** La banda de mpv al cambiar de canal: su número y su nombre, lo que echan ahora (hasta cuándo) y lo que viene después. */
    private fun muestraInfo() {
        val l = buildList {
            add(titulo())
            estado.ahora?.let { add("Ahora: ${it.titulo} (hasta las ${hora(it.fin)})") }
            estado.despues?.let { add("Después, a las ${hora(it.inicio)}: ${it.titulo}") }
        }
        cliente?.manda("show-text", l.joinToString("\n"), 5_000)
    }

    /** La lista de canales encima del vídeo (también en la ventana de mpv): el de ahora marcado, y con lo que echan en cada uno. */
    private fun listaEncima() {
        val e = encargo as? Encargo.Directo ?: return
        estado.lista = e.canales; estado.enLista = e.indice
        pintaLista()
    }

    private fun mueveCursor(d: Int) {
        val l = estado.lista ?: return
        estado.enLista = ((estado.enLista + d) % l.size + l.size) % l.size
        pintaLista()
    }

    private fun pintaLista() {
        val e = encargo as? Encargo.Directo ?: return
        val l = estado.lista ?: return
        listaHasta = System.currentTimeMillis() + 10_000L
        val cg = Tele.enMemoria(e.lista.id)
        val ahora = System.currentTimeMillis()
        val desde = (estado.enLista - 4).coerceIn(0, (l.size - 9).coerceAtLeast(0))
        val lineas = (desde until minOf(l.size, desde + 9)).map { i ->
            val c = l[i]
            val prog = cg?.let { Tele.ahoraYDespuesRapido(it, c, ahora).first }?.titulo
            (if (i == estado.enLista) "▶ " else "   ") + "${if (c.numero > 0) c.numero else i + 1}  ${c.nombre}" + (prog?.let { " · $it" } ?: "")
        }
        cliente?.manda("show-text", (listOf("Canales (↑ ↓, Intro para ver, Esc para cerrar)") + lineas).joinToString("\n"), 10_000)
    }

    /** Un número tecleado (con el teclado o los números del mando): se espera un poco a la siguiente cifra y se va al canal. */
    private fun teclea(n: Int) {
        if (encargo !is Encargo.Directo) return
        estado.tecleado = (estado.tecleado + n).takeLast(4)
        cliente?.manda("show-text", "Canal ${estado.tecleado}…", 1_600)
        tecleo?.cancel(false)
        tecleo = hilo.schedule({ val t = estado.tecleado.toIntOrNull(); estado.tecleado = ""; if (t != null) alNumero(t) }, 1_500, TimeUnit.MILLISECONDS)
    }

    /** Ir a un canal por su número (el suyo de la lista o, si la lista no tiene números, su puesto). */
    private fun alNumero(n: Int) {
        val e = encargo as? Encargo.Directo ?: return
        val i = e.canales.indexOfFirst { it.numero == n }.takeIf { it >= 0 } ?: (n - 1).takeIf { it in e.canales.indices && e.canales.none { c -> c.numero > 0 } }
        if (i == null) { muestra("No hay canal $n", 2_000L); return }
        irA(i)
    }

    // ---------------------------------------------------------------- lo visto (videoclub)

    private fun videoActual(): String? = when (val e = encargo) { is Encargo.DePelicula -> DatosCuenta.videoPelicula(e.pelicula); is Encargo.DeEpisodio -> DatosCuenta.videoEpisodio(e.episodio); else -> null }

    /** Apunta por dónde vas (aquí y en la cuenta), con lo visto al 90 % (como en el móvil). */
    private fun guarda(fin: Boolean = false) {
        val e = encargo ?: return
        val v = videoActual() ?: return
        guardadoEn = System.currentTimeMillis()
        val pos = if (fin) estado.dur else estado.pos
        val dur = estado.dur
        if (pos <= 0 || dur <= 0) return
        Reanudar.pon(e.lista.id, v, pos, dur)
        val antes = MiCuenta.progreso(e.lista.id, v)
        val visto = (antes?.visto == true) || Visto.visto(pos, dur)
        val p = when (e) {
            is Encargo.DePelicula -> Progreso(e.lista.id, v, e.pelicula.id, e.pelicula.extension, e.pelicula.nombre, pos = pos, dur = dur, visto = visto, ultima = System.currentTimeMillis())
            is Encargo.DeEpisodio -> Progreso(e.lista.id, v, e.episodio.id, e.episodio.extension, tituloEpisodio(e.episodio, e.serie.nombre), e.serie.id, e.serie.nombre,
                e.episodio.temporada, e.episodio.numero, pos, dur, visto, System.currentTimeMillis())
            else -> return
        }
        MiCuenta.ponProgreso(p)
    }

    private fun episodioSiguiente(): Episodio? = (encargo as? Encargo.DeEpisodio)?.let { e -> e.ficha?.siguiente(e.episodio) }

    private fun alTerminar() {
        val sig = episodioSiguiente()
        if (sig == null || !AjustesReproductor.siguiente()) { muestra("Fin", 3_000); return }
        estado.siguiente = "T${sig.temporada} · E${sig.numero}" + if (sig.titulo.isNotBlank()) " · ${tituloEpisodio(sig, (encargo as Encargo.DeEpisodio).serie.nombre)}" else ""
        estado.cuentaAtras = 8
    }

    private fun siguienteYa() {
        val e = encargo as? Encargo.DeEpisodio ?: return
        val sig = episodioSiguiente() ?: return
        estado.cuentaAtras = -1; estado.siguiente = null
        encargo = Encargo.DeEpisodio(e.lista, e.serie, e.ficha, sig)
        abre()
    }

    /** (Para las pruebas: el proceso de mpv, para mirar su línea de órdenes.) */
    fun pidParaPruebas(): Long? = proceso?.takeIf { it.isAlive }?.pid()

    /** (Para las pruebas: lo que pulsarías en la ventana de mpv.) */
    fun teclaParaPruebas(tecla: String): Boolean = runCatching { hilo.submit<Boolean> { cliente?.manda("keypress", tecla)?.ok == true }.get(5, TimeUnit.SECONDS) }.getOrDefault(false)

    /** (Para las pruebas: lo que está viendo ahora.) */
    fun encargoParaPruebas(): Encargo? = encargo

    /** (Para las pruebas: espera a que el hilo del reproductor haya hecho lo que tenía pendiente.) */
    fun esperaParaPruebas() { runCatching { hilo.submit {}.get(10, TimeUnit.SECONDS) } }
}

/** «Juego de tronos - S01E02 - El camino real» → «El camino real» (muchos servidores repiten la serie y el número en el título). Como en el móvil. */
fun tituloEpisodio(e: Episodio, serie: String): String {
    val t = e.titulo.trim()
    val sin = t.replace(Regex("^" + Regex.escape(serie) + "\\s*[-–:|]\\s*", RegexOption.IGNORE_CASE), "").replace(Regex("^S\\d{1,2}\\s*E\\d{1,3}\\s*[-–:|]?\\s*", RegexOption.IGNORE_CASE), "").trim()
    return sin.ifBlank { "Episodio ${e.numero}" }
}
