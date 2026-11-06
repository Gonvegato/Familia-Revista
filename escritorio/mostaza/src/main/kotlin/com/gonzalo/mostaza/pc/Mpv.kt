package com.gonzalo.mostaza.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoBool
import com.gonzalo.familia.nucleo.comoDouble
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.Sitio
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/*
 * El reproductor del ordenador: mpv (GPL/LGPL, el que hay en CachyOS y Arch con `pacman -S mpv` y en el Mac con `brew install mpv`). En el
 * ordenador no hay ExoPlayer: Mostaza abre mpv en su propia ventana y habla con él por su IPC de JSON (`--input-ipc-server`, un socket
 * local de este usuario), igual que con el reproductor del móvil: le dice qué poner y desde dónde, y le pregunta cada segundo por dónde
 * va, si está reproduciendo y qué pistas tiene. Así las tres reglas (los 10 minutos, el 90 %, el tiempo de verdad), seguir donde lo
 * dejaste, las pistas de verdad y el vídeo de aviso del debrid funcionan como en el móvil.
 *
 * Lo secreto (la dirección de la fuente, que puede llevar la clave de tu debrid, y sus cabeceras) NUNCA va en la línea de órdenes de mpv
 * (cualquier programa del ordenador puede leer la línea de órdenes de otro): se le da por el socket, que solo puede abrir tu usuario
 * (en una carpeta 0700). Y mpv no guarda nada de ello: sin «seguir donde lo dejé» propio (lo lleva Mostaza) y sin yt-dlp (que recibiría
 * la dirección en su línea de órdenes).
 */
object Mpv {
    /** Lo que se le pide a mpv para empezar (lo demás va por el socket, archivo a archivo). */
    data class Opciones(
        val audio: List<String> = listOf("spa", "es"), val subs: List<String> = listOf("spa", "es"), val subTam: Int = 100, val subFondo: Boolean = false,
        val pantallaCompleta: Boolean = true, val espera: Int = 30, val agente: String = "Mostaza",
    )

    /** Dónde puede estar mpv: lo que diga el PATH y, como las apps del Mac no ven el PATH de la terminal, los sitios de Homebrew y del .app. */
    fun candidatos(sistema: Sitio.Sistema = Sitio.sistema, path: String = System.getenv("PATH").orEmpty(), casa: File = File(System.getProperty("user.home"))): List<File> {
        val delPath = path.split(File.pathSeparatorChar).filter { it.isNotBlank() }.map { File(it, "mpv") }
        val fijos = if (sistema == Sitio.Sistema.MAC) listOf(File("/opt/homebrew/bin/mpv"), File("/usr/local/bin/mpv"), File("/Applications/mpv.app/Contents/MacOS/mpv"),
            File(casa, "Applications/mpv.app/Contents/MacOS/mpv"), File("/opt/local/bin/mpv"))
            else listOf(File("/usr/bin/mpv"), File("/usr/local/bin/mpv"), File(casa, ".local/bin/mpv"))
        return (delPath + fijos).distinctBy { it.absolutePath }
    }

    /** En las pruebas: otro mpv (o ninguno). */
    @Volatile var fijo: File? = null
    @Volatile var ninguno = false

    fun busca(): File? { if (ninguno) return null; fijo?.let { return it }; return candidatos().firstOrNull { it.isFile && it.canExecute() } }

    /** Cómo se instala aquí (lo que se enseña si no está). */
    fun comoInstalar(sistema: Sitio.Sistema = Sitio.sistema): String = if (sistema == Sitio.Sistema.MAC)
        "Mostaza reproduce con mpv. En el Mac se instala con Homebrew: abre la Terminal y escribe «brew install mpv» (si no tienes Homebrew, primero brew.sh). Después vuelve a pulsar «Reproducir»."
        else "Mostaza reproduce con mpv. En CachyOS y Arch: «sudo pacman -S mpv» en una terminal (en Ubuntu, Debian o Mint: «sudo apt install mpv»; en Fedora: «sudo dnf install mpv»). Después vuelve a pulsar «Reproducir»."

    /**
     * La línea de órdenes de mpv. Sin la dirección ni las cabeceras (van por el socket). [socket]: dónde escucha su IPC. Lo de los
     * idiomas, los subtítulos y el título no es secreto.
     */
    fun orden(mpv: File, socket: File, o: Opciones): List<String> = buildList {
        add(mpv.absolutePath)
        add("--idle=yes")                     // se queda abierto entre una fuente y otra (y entre episodios)
        add("--force-window=yes")             // con su ventana desde el principio (mientras carga)
        add("--keep-open=no")
        add("--input-ipc-server=${socket.absolutePath}")
        add("--no-terminal")                  // nada por la salida (ni la dirección, que mpv escribiría al abrirla)
        add("--msg-level=all=no")
        add("--ytdl=no")                      // sin yt-dlp: le daría la dirección en su línea de órdenes
        add("--save-position-on-quit=no")     // «seguir donde lo dejaste» lo lleva Mostaza (en la cuenta)
        add("--resume-playback=no")
        add("--hwdec=auto-safe")
        add("--network-timeout=${o.espera}")
        add("--user-agent=${o.agente}")
        add("--title=\${media-title} · Mostaza")
        if (o.audio.isNotEmpty()) add("--alang=" + o.audio.joinToString(","))
        if (o.subs.isNotEmpty()) add("--slang=" + o.subs.joinToString(","))
        if (o.subTam != 100) add("--sub-scale=" + String.format(java.util.Locale.ROOT, "%.2f", o.subTam / 100.0))
        if (o.subFondo) add("--sub-back-color=#B0000000")
        if (o.pantallaCompleta) add("--fs")
    }

    /** Los idiomas de audio para mpv a partir de tu preferencia (ISO 639: «spa» → «spa,es»; «eng» → «eng,en»). */
    fun idiomas(pref: String): List<String> {
        val p = pref.trim().lowercase()
        if (p.isBlank()) return emptyList()
        val dos = when (p.take(3)) { "spa", "esp" -> "es"; "eng" -> "en"; "fre", "fra" -> "fr"; "ita" -> "it"; "ger", "deu" -> "de"; "por" -> "pt"; "jpn" -> "ja"; else -> if (p.length == 2) p else "" }
        val tres = when (p.take(2)) { "es" -> "spa"; "en" -> "eng"; "fr" -> "fre"; "it" -> "ita"; "de" -> "ger"; "pt" -> "por"; "ja" -> "jpn"; else -> p }
        return listOf(if (p.length == 2) tres else p, dos).filter { it.isNotBlank() }.distinct()
    }

    /** Las cabeceras de una fuente como las quiere mpv (`http-header-fields`: «Nombre: valor»). Sin saltos de línea (no se cuela otra). */
    fun cabeceras(c: Map<String, String>): List<String> = c.filter { (k, v) -> k.isNotBlank() && k.none { it == ':' || it == '\n' || it == '\r' } && v.none { it == '\n' || it == '\r' } }
        .map { (k, v) -> "$k: $v" }

    /** Una orden para el socket (una línea de JSON). */
    fun linea(id: Long, vararg orden: Any?): String = Json.escribe(mapOf("command" to orden.toList(), "request_id" to id))

    /** Una carpeta solo para tu usuario para el socket (en el Mac la ruta de un socket no puede pasar de 104 letras). */
    fun carpetaSocket(base: File): File {
        val d = File(base, "mpv")
        d.mkdirs()
        runCatching { Files.setPosixFilePermissions(d.toPath(), PosixFilePermissions.fromString("rwx------")) }
        return d
    }
}

/** Lo que dice mpv por su socket: una respuesta a una orden (con su `request_id`) o un suceso (`event`). */
sealed class MensajeMpv {
    data class Respuesta(val id: Long, val ok: Boolean, val datos: Any?, val error: String) : MensajeMpv()
    data class Suceso(val nombre: String, val campos: Map<String, Any?>) : MensajeMpv() {
        val entrada: Long get() = campos["playlist_entry_id"].comoLong()
        /** En `end-file`: `eof`, `stop`, `quit`, `error`, `redirect`… */
        val razon: String get() = campos["reason"].comoTexto()
        /** En `property-change`: qué propiedad y su valor. */
        val propiedad: String get() = campos["name"].comoTexto()
        val valor: Any? get() = campos["data"]
    }

    companion object {
        fun lee(linea: String): MensajeMpv? {
            val m = runCatching { Json.lee(linea) }.getOrNull().comoMapa()
            if (m.isEmpty()) return null
            if (m.containsKey("event")) return Suceso(m["event"].comoTexto(), m)
            if (m.containsKey("error")) return Respuesta(m["request_id"].comoLong(), m["error"].comoTexto() == "success", m["data"], m["error"].comoTexto())
            return null
        }
    }
}

/** Una pista del archivo, como la da mpv en `track-list`. */
data class PistaMpv(val id: Int, val tipo: String, val idioma: String, val titulo: String, val elegida: Boolean, val externa: Boolean, val codec: String, val canales: Int = 0) {
    val etiqueta: String get() = listOf(titulo, nombreIdioma(idioma), codec.uppercase(), if (canales > 0) "$canales canales" else "").filter { it.isNotBlank() }.distinct().joinToString(" · ").ifBlank { "Pista $id" }

    companion object {
        /** `track-list`: las pistas de vídeo, audio y subtítulos (`type`: `video`, `audio`, `sub`). */
        fun deLista(v: Any?): List<PistaMpv> = v.comoLista().mapNotNull { x ->
            val m = x.comoMapa(); val id = m["id"].comoLong().toInt()
            if (id <= 0) null else PistaMpv(id, m["type"].comoTexto(), m["lang"].comoTexto(), m["title"].comoTexto(), m["selected"].comoBool(), m["external"].comoBool(),
                m["codec"].comoTexto(), (m["demux-channel-count"] ?: m["audio-channels"]).comoLong().toInt())
        }

        fun nombreIdioma(l: String): String = when (l.lowercase().take(3).removeSuffix("-")) {
            "spa", "esp", "es" -> "Español"; "eng", "en" -> "Inglés"; "fre", "fra", "fr" -> "Francés"; "ita", "it" -> "Italiano"; "ger", "deu", "de" -> "Alemán"
            "por", "pt" -> "Portugués"; "jpn", "ja" -> "Japonés"; "kor", "ko" -> "Coreano"; else -> l
        }
    }
}

/**
 * Lo que se ve en las pistas de verdad, dicho con lo de [Pistas] (lo mismo que hace el reproductor del móvil con las de Media3): los audios y
 * los subtítulos con su idioma y su nombre, y la pista de audio que toca según tu orden.
 */
object PistasDeMpv {
    fun audios(l: List<PistaMpv>) = l.filter { it.tipo == "audio" }
    fun subtitulos(l: List<PistaMpv>) = l.filter { it.tipo == "sub" }

    fun verifica(l: List<PistaMpv>, p: PrefsElector): Verificacion =
        Pistas.verifica(audios(l).map { it.idioma.ifBlank { null } to it.titulo.ifBlank { null } }, subtitulos(l).filter { !it.externa }.map { it.idioma.ifBlank { null } to it.titulo.ifBlank { null } }, p)

    /** El id de mpv del audio que toca (o null: se deja el que ha puesto mpv). mpv decodifica todos los audios: todos se pueden poner. */
    fun mejorAudio(l: List<PistaMpv>, p: PrefsElector): Int? {
        val a = audios(l)
        val k = Pistas.mejorAudio(a.map { PistaAudio(Pistas.clasifica(it.idioma, it.titulo), true, it.elegida) }, p) ?: return null
        return a[k].id
    }

    /** Los subtítulos en español que tocan (los de latino después, salvo que en tu orden vaya antes el latino), o null si no hay. */
    fun subtituloEspanol(l: List<PistaMpv>, p: PrefsElector): Int? {
        val esp = subtitulos(l).filter { Pistas.subtituloEspanol(it.idioma, it.titulo) }
        if (esp.isEmpty()) return null
        val latinoPrimero = p.idiomas.indexOf("latino").let { li -> li >= 0 && (p.idiomas.indexOf("castellano").let { c -> c < 0 || li < c }) }
        return esp.sortedWith(compareBy({ (Pistas.clasifica(it.idioma, it.titulo) == Idioma.LATINO) != latinoPrimero }, { it.externa })).first().id
    }
}

/**
 * Hablar con un mpv ya abierto por su socket. Las órdenes esperan su respuesta (con un tope); los sucesos van a [alSuceso] en el hilo que
 * lee el socket. Nunca se escribe en ningún registro lo que se manda (lleva la dirección y las cabeceras).
 */
class ClienteMpv private constructor(private val canal: SocketChannel, private val alSuceso: (MensajeMpv.Suceso) -> Unit) : AutoCloseable {
    private val salida = Channels.newOutputStream(canal)
    private val ids = AtomicLong(1)
    private val esperando = ConcurrentHashMap<Long, CompletableFuture<MensajeMpv.Respuesta>>()
    @Volatile var cerrado = false; private set
    /** Se llama una vez cuando el socket se cierra (mpv se ha cerrado). */
    @Volatile var alCerrar: () -> Unit = {}

    private val lector = Thread({
        try {
            Channels.newInputStream(canal).bufferedReader(Charsets.UTF_8).useLines { lineas ->
                for (l in lineas) when (val m = MensajeMpv.lee(l)) {
                    is MensajeMpv.Respuesta -> esperando.remove(m.id)?.complete(m)
                    is MensajeMpv.Suceso -> runCatching { alSuceso(m) }
                    null -> {}
                }
            }
        } catch (e: Exception) { }
        cierra()
    }, "mostaza-mpv").apply { isDaemon = true }

    /** Manda una orden y espera su respuesta (como mucho [espera] ms). Si mpv ya no está, una respuesta de error. */
    fun manda(vararg orden: Any?, espera: Long = 5_000): MensajeMpv.Respuesta {
        if (cerrado) return MensajeMpv.Respuesta(0, false, null, "cerrado")
        val id = ids.getAndIncrement()
        val f = CompletableFuture<MensajeMpv.Respuesta>()
        esperando[id] = f
        try {
            synchronized(salida) { salida.write((Mpv.linea(id, *orden) + "\n").toByteArray(Charsets.UTF_8)); salida.flush() }
        } catch (e: IOException) { esperando.remove(id); cierra(); return MensajeMpv.Respuesta(id, false, null, "cerrado") }
        return runCatching { f.get(espera, TimeUnit.MILLISECONDS) }.getOrElse { esperando.remove(id); MensajeMpv.Respuesta(id, false, null, "sin respuesta") }
    }

    fun pon(propiedad: String, valor: Any?): Boolean = manda("set_property", propiedad, valor).ok
    fun lee(propiedad: String): Any? = manda("get_property", propiedad).let { if (it.ok) it.datos else null }
    fun leeDouble(propiedad: String): Double? = lee(propiedad)?.comoDouble()
    fun observa(id: Int, propiedad: String) = manda("observe_property", id, propiedad)

    @Synchronized private fun cierra() {
        if (cerrado) return
        cerrado = true
        runCatching { canal.close() }
        esperando.values.forEach { it.complete(MensajeMpv.Respuesta(0, false, null, "cerrado")) }
        esperando.clear()
        runCatching { alCerrar() }
    }

    override fun close() = cierra()

    companion object {
        /** Se conecta al socket de un mpv que acaba de abrirse (lo intenta durante [espera] ms: mpv tarda un poco en crearlo). */
        fun conecta(socket: File, espera: Long = 8_000, vivo: () -> Boolean = { true }, alSuceso: (MensajeMpv.Suceso) -> Unit): ClienteMpv? {
            val hasta = System.currentTimeMillis() + espera
            while (System.currentTimeMillis() < hasta && vivo()) {
                if (socket.exists()) runCatching {
                    val c = SocketChannel.open(StandardProtocolFamily.UNIX)
                    c.connect(UnixDomainSocketAddress.of(socket.toPath()))
                    return ClienteMpv(c, alSuceso).also { it.lector.start() }
                }
                Thread.sleep(100)
            }
            return null
        }
    }
}
