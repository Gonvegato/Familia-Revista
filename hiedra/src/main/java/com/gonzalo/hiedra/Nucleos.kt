package com.gonzalo.hiedra

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

/**
 * Los núcleos libretro del jugador interno («Hiedra» en el selector de emulador). No van dentro del APK: se bajan del buildbot de
 * libretro (el mismo sitio del que los baja RetroArch), uno por sistema, la primera vez que se juega o desde Sistemas.
 *
 * Qué núcleo lleva cada consola, con qué opciones arranca, qué BIOS busca y cómo son sus mandos es lo mismo que hace Lemuroid (el
 * jugador del autor de LibretroDroid y RadialGamePad, que son los que usa Hiedra), comprobado en su código y en los archivos .info de
 * libretro. Las opciones se han comprobado una a una en el código de cada núcleo (las del buildbot pueden llamarse distinto que las de
 * Lemuroid, que compila los suyos: melonDS, por ejemplo).
 *
 * Un núcleo NO se actualiza solo: un estado guardado (el «guardado rápido») solo lo entiende la misma versión del núcleo que lo hizo,
 * así que cambiarlo por debajo podría dejar sin cargar la partida de otro móvil. Se actualiza a mano, avisando, y la versión anterior
 * se guarda hasta que se haya jugado con la nueva. La partida de verdad (la SRAM, .sav/.srm) no depende de la versión.
 *
 * En cada carpeta `filesDir/nucleos/<id>/`: el `.so` y `nucleo.json` con la fecha del buildbot, el tamaño y el SHA-1 (la «versión»
 * que viaja con cada estado guardado). Las BIOS y los archivos que algún núcleo necesita aparte (los de PPSSPP) van en `filesDir/system`.
 */
object Nucleos {
    /**
     * Una BIOS que busca un núcleo. [ruta]: dónde, dentro de la carpeta del sistema (`fbneo/neogeo.zip`). [md5]: los que se conocen
     * (sirven para reconocerla aunque se llame de otra manera). [grupo]: de las de un mismo grupo basta con una (las tres regiones
     * del Mega-CD).
     */
    class Bios(val ruta: String, val descripcion: String, val md5: Set<String> = emptySet(), val obligatoria: Boolean = false, val grupo: String? = null) {
        val archivo: String get() = ruta.substringAfterLast('/')
    }

    /** Lo que un núcleo necesita además de su .so (PPSSPP: sus letras, su firmware y sus traducciones). Se baja una vez a la carpeta del sistema. */
    class Recursos(val url: String, val prueba: String, val pesoAprox: Long)

    /** Una opción del núcleo que se cambia desde el menú del juego: su clave en el núcleo y sus valores (valor del núcleo → cómo se dice). */
    class Opcion(val clave: String, val nombre: String, val valores: List<Pair<String, String>>)

    /**
     * Un núcleo para un sistema. Dos sistemas pueden compartir el mismo [id] (Game Boy y Game Boy Color, Gambatte) con distintas
     * [variables]: se baja una vez.
     *
     *  - [extensiones]: lo que abre tal cual (dentro de un .zip se busca una de estas).
     *  - [variables]: las opciones con las que arranca (las de Lemuroid); [opciones]: las que se pueden cambiar desde el menú.
     *  - [pruebas]: no se ha podido probar en un móvil de verdad y es de los delicados (3D, discos, arcade): se dice «en pruebas» y,
     *    en «Automático», manda el emulador de fuera que tengas instalado para ese sistema.
     *  - [alFrente]: «Automático» usa Hiedra aunque tengas otro emulador (solo GBA, como hasta ahora).
     *  - [tactil]: la consola tiene pantalla táctil (DS): lo que tocas en la imagen del juego le llega al núcleo.
     *  - [saltarRepetidos]: LibretroDroid no vuelve a dibujar una imagen igual a la anterior (Lemuroid lo apaga en N64 y PlayStation).
     *  - [directo]: el núcleo abre el archivo donde está, sin copiarlo (el «VFS» de libretro; PlayStation y PSP, que son discos enormes).
     *  - [zipEntero]: el .zip es la ROM (FBNeo: ni se descomprime ni se le cambia el nombre, que es lo que dice qué juego es).
     */
    class Nucleo(
        val id: String, val nombre: String, val extensiones: Set<String>, val pad: Mandos.Pad,
        val variables: Map<String, String> = emptyMap(), val opciones: List<Opcion> = emptyList(), val bios: List<Bios> = emptyList(),
        val pruebas: Boolean = false, val alFrente: Boolean = false, val tactil: Boolean = false, val saltarRepetidos: Boolean = true,
        val directo: Boolean = false, val zipEntero: Boolean = false, val recursos: Recursos? = null,
        /** Lo que pesa la descarga (aprox.), para decirlo antes de bajarlo. */
        val pesoAprox: Long = 0L,
        /** Imágenes por segundo de la consola (para medir la velocidad real del avance rápido). */
        val fps: Float = 60f,
    )

    /** Lo instalado de un núcleo. [version] es su SHA-1 (corto): dos móviles con la misma versión pueden compartir estados. */
    data class Instalado(val id: String, val fecha: Long, val tamano: Long, val version: String)

    private const val KB = 1024L
    private const val MB = 1024L * 1024L

    // ------------------------------------------------------------------ las BIOS que se conocen (MD5 de libretro y de Lemuroid)

    private val BIOS_LYNX = Bios("lynxboot.img", "Arranque de la Lynx (hace falta en algunos juegos)", setOf("fcd403db69f54290b51035d82f835e7b"))
    private val BIOS_MEGACD = listOf(
        Bios("bios_CD_E.bin", "Mega-CD (Europa)", setOf("e66fa1dc5820d254611fdcdba0662372"), obligatoria = true, grupo = "megacd"),
        Bios("bios_CD_U.bin", "Sega CD (América)", setOf("2efd74e3232ff260e371b99f84024f7f"), obligatoria = true, grupo = "megacd"),
        Bios("bios_CD_J.bin", "Mega-CD (Japón)", setOf("278a9397d192149e84e820ac621a8edd"), obligatoria = true, grupo = "megacd"),
    )
    private val BIOS_PSX = listOf(
        Bios("scph5501.bin", "PlayStation 3.0 (América)", setOf("490f666e1afb15b7362b406ed1cea246")),
        Bios("scph5500.bin", "PlayStation 3.0 (Japón)", setOf("8dd7d5296a650fac7319bce665a6a53c")),
        Bios("scph5502.bin", "PlayStation 3.0 (Europa)", setOf("32736f17079d0b2b7024407c39bd3050")),
        Bios("scph1001.bin", "PlayStation 2.2 (América)", setOf("924e392ed05558ffdb115408c263dccf")),
        Bios("scph7001.bin", "PlayStation 4.1 (América)", setOf("1e68c231d0896b7eadcad1d7d8e76129")),
        Bios("scph101.bin", "PS one 4.5 (América)", setOf("6e3735ff4c7dc899ee98981385f6f3d0")),
    )
    private val BIOS_NDS = listOf(
        Bios("bios7.bin", "DS ARM7", setOf("df692a80a5b1bc90728bc3dfc76cd948")),
        Bios("bios9.bin", "DS ARM9", setOf("a392174eb3e572fed6447e956bde4b25")),
        Bios("firmware.bin", "Firmware de la DS", setOf("e45033d9b0fa6b0de071292bba7c9d13")),
    )
    private val BIOS_FDS = Bios("disksys.rom", "Famicom Disk System (para los juegos .fds)", setOf("ca30b50f880eb660a320674ed365ef7a"))
    private val BIOS_ARCADE = listOf(
        Bios("fbneo/neogeo.zip", "Neo Geo (para todos sus juegos)"),
        Bios("fbneo/pgm.zip", "PolyGame Master"),
        Bios("fbneo/skns.zip", "Super Kaneko Nova System"),
    )

    // ------------------------------------------------------------------ los núcleos (los de Lemuroid, uno por consola)

    private val MGBA = Nucleo("mgba", "mGBA", setOf("gba"), Mandos.Pad.GBA, alFrente = true, pesoAprox = 420 * KB, fps = 59.73f,
        opciones = listOf(
            Opcion("mgba_color_correction", "Color", listOf("OFF" to "Tal cual", "GBA" to "Como en la consola")),
            Opcion("mgba_interframe_blending", "Estela de la pantalla", listOf("OFF" to "No", "mix_smart" to "Transparencias", "lcd_ghosting" to "Como un LCD")),
        ))
    private fun gambatte(color: Boolean) = Nucleo("gambatte", "Gambatte", setOf("gb", "gbc", "dmg"), Mandos.Pad.GB, pesoAprox = 440 * KB, fps = 59.73f,
        variables = if (color) mapOf("gambatte_gbc_color_correction" to "disabled")
            else mapOf("gambatte_gb_colorization" to "internal", "gambatte_gb_internal_palette" to "GB - Pocket"),
        opciones = if (color) listOf(Opcion("gambatte_gbc_color_correction", "Color", listOf("disabled" to "Tal cual", "always" to "Como en la consola")))
            else listOf(Opcion("gambatte_gb_colorization", "Colores", listOf("internal" to "Game Boy Pocket", "disabled" to "Verdes", "auto" to "Coloreado", "SGB" to "Super Game Boy"))))
    private fun fceumm(disco: Boolean = false) = Nucleo("fceumm", "FCEUmm", setOf("nes", "fds", "unf", "unif"), Mandos.Pad.NES,
        bios = listOf(if (disco) Bios(BIOS_FDS.ruta, BIOS_FDS.descripcion, BIOS_FDS.md5, obligatoria = true) else BIOS_FDS), pesoAprox = 460 * KB, fps = 60.1f)
    private val SNES9X = Nucleo("snes9x", "Snes9x", setOf("smc", "sfc", "swc", "fig", "bs", "st"), Mandos.Pad.SNES, pesoAprox = 890 * KB, fps = 60.1f)
    private fun genesis(pad: Mandos.Pad, ext: Set<String>, bios: List<Bios> = emptyList(), pruebas: Boolean = false) =
        Nucleo("genesis_plus_gx", "Genesis Plus GX", ext, pad, bios = bios, pruebas = pruebas, pesoAprox = 1_140 * KB, fps = 59.92f)
    private val PCE_FAST = Nucleo("mednafen_pce_fast", "Beetle PCE Fast", setOf("pce"), Mandos.Pad.PCENGINE, pesoAprox = 380 * KB, fps = 59.83f)
    private val NGP = Nucleo("mednafen_ngp", "Beetle NeoPop", setOf("ngp", "ngc", "ngpc", "npc"), Mandos.Pad.NEOGEOPOCKET, pesoAprox = 160 * KB, fps = 60.25f)
    private fun wswan(color: Boolean) = Nucleo("mednafen_wswan", "Beetle Cygne", if (color) setOf("ws", "wsc", "pc2") else setOf("ws", "pc2"), Mandos.Pad.WONDERSWAN, pesoAprox = 100 * KB, fps = 75.47f,
        variables = if (color) mapOf("wswan_rotate_display" to "landscape") else mapOf("wswan_rotate_display" to "landscape", "wswan_mono_palette" to "wonderswan"),
        opciones = listOf(Opcion("wswan_rotate_display", "Pantalla", listOf("landscape" to "Apaisada", "portrait" to "Vertical"))))
    private val HANDY = Nucleo("handy", "Handy", setOf("lnx", "lyx", "o"), Mandos.Pad.LYNX, bios = listOf(BIOS_LYNX), pesoAprox = 150 * KB,
        variables = mapOf("handy_rot" to "None", "handy_refresh_rate" to "60"),
        opciones = listOf(Opcion("handy_rot", "Pantalla", listOf("None" to "Normal", "270" to "Girada (derecha)", "90" to "Girada (izquierda)"))))
    private val STELLA = Nucleo("stella", "Stella", setOf("a26", "bin"), Mandos.Pad.ATARI2600, pesoAprox = 1_480 * KB)
    private val PROSYSTEM = Nucleo("prosystem", "ProSystem", setOf("a78", "bin"), Mandos.Pad.ATARI7800, pesoAprox = 50 * KB)
    /** FinalBurn Neo. La BIOS de Neo Geo la busca en la carpeta del sistema y en la del juego: si está junto a tus ROMs, Hiedra la copia sola. */
    private fun fbneo(pad: Mandos.Pad, neogeo: Boolean = false) = Nucleo("fbneo", "FinalBurn Neo", setOf("zip"), pad, pruebas = true, zipEntero = true, pesoAprox = 16_700 * KB,
        bios = if (neogeo) listOf(Bios(BIOS_ARCADE[0].ruta, BIOS_ARCADE[0].descripcion, obligatoria = true)) + BIOS_ARCADE.drop(1) else BIOS_ARCADE)
    private val MUPEN = Nucleo("mupen64plus_next_gles3", "Mupen64Plus-Next", setOf("n64", "z64", "v64", "u1", "ndd"), Mandos.Pad.N64, pruebas = true,
        saltarRepetidos = false, pesoAprox = 2_210 * KB,
        bios = listOf(Bios("Mupen64plus/IPL.n64", "64DD (solo para los juegos de disco)", setOf("8d3d9f294b6e174bc7b1d2fd1c727530"))),
        variables = mapOf("mupen64plus-43screensize" to "320x240", "mupen64plus-FrameDuping" to "True"),
        opciones = listOf(Opcion("mupen64plus-43screensize", "Resolución", listOf("320x240" to "Original", "640x480" to "×2", "960x720" to "×3"))))
    private val PCSX = Nucleo("pcsx_rearmed", "PCSX ReARMed", setOf("cue", "chd", "pbp", "m3u", "iso", "img", "bin", "ccd", "toc", "mdf", "cbn", "exe"), Mandos.Pad.PSX,
        pruebas = true, saltarRepetidos = false, directo = true, bios = BIOS_PSX, pesoAprox = 540 * KB, fps = 59.94f,
        variables = mapOf("pcsx_rearmed_drc" to "disabled"),
        opciones = listOf(Opcion("pcsx_rearmed_drc", "Procesador", listOf("disabled" to "Intérprete (seguro)", "enabled" to "Recompilador (más rápido)"))))
    private val PPSSPP = Nucleo("ppsspp", "PPSSPP", setOf("iso", "cso", "pbp", "chd", "elf", "prx"), Mandos.Pad.PSP, pruebas = true, directo = true,
        recursos = Recursos("https://buildbot.libretro.com/assets/system/PPSSPP.zip", "PPSSPP/ppge_atlas.zim", 9_900 * KB), pesoAprox = 8_200 * KB, fps = 59.94f,
        variables = mapOf("ppsspp_frame_duplication" to "enabled"),
        opciones = listOf(Opcion("ppsspp_internal_resolution", "Resolución", listOf("480x272" to "Original", "960x544" to "×2", "1440x816" to "×3"))))
    private val MELONDS = Nucleo("melonds", "melonDS", setOf("nds", "dsi", "ids"), Mandos.Pad.NDS, pruebas = true, tactil = true, bios = BIOS_NDS,
        pesoAprox = 700 * KB, fps = 59.83f,
        variables = mapOf("melonds_touch_mode" to "Touch", "melonds_threaded_renderer" to "enabled"),
        opciones = listOf(Opcion("melonds_screen_layout", "Pantallas", listOf("Top/Bottom" to "Una encima de otra", "Left/Right" to "Una al lado de otra", "Hybrid Top" to "Grande y pequeña"))))

    /**
     * Qué núcleo usa cada sistema (por el id de ES-DE: los nombres de sus carpetas de ROMs). Todos se pueden elegir; los [Nucleo.pruebas]
     * se dicen «en pruebas».
     */
    val catalogo: Map<String, Nucleo> = linkedMapOf(
        "gba" to MGBA,
        "gb" to gambatte(color = false), "gbc" to gambatte(color = true),
        "nes" to fceumm(), "famicom" to fceumm(), "fds" to fceumm(disco = true),
        "snes" to SNES9X, "sfc" to SNES9X, "snesna" to SNES9X,
        "megadrive" to genesis(Mandos.Pad.MEGADRIVE, setOf("md", "smd", "gen", "bin", "mdx", "68k", "sgd")),
        "genesis" to genesis(Mandos.Pad.MEGADRIVE, setOf("md", "smd", "gen", "bin", "mdx", "68k", "sgd")),
        "megadrivejp" to genesis(Mandos.Pad.MEGADRIVE, setOf("md", "smd", "gen", "bin", "mdx", "68k", "sgd")),
        "mastersystem" to genesis(Mandos.Pad.MASTERSYSTEM, setOf("sms", "bin")),
        "mark3" to genesis(Mandos.Pad.MASTERSYSTEM, setOf("sms", "bin")),
        "gamegear" to genesis(Mandos.Pad.GAMEGEAR, setOf("gg")),
        "segacd" to genesis(Mandos.Pad.MEGADRIVE, setOf("cue", "chd", "iso", "m3u"), BIOS_MEGACD, pruebas = true),
        "megacd" to genesis(Mandos.Pad.MEGADRIVE, setOf("cue", "chd", "iso", "m3u"), BIOS_MEGACD, pruebas = true),
        "megacdjp" to genesis(Mandos.Pad.MEGADRIVE, setOf("cue", "chd", "iso", "m3u"), BIOS_MEGACD, pruebas = true),
        "pcengine" to PCE_FAST, "tg16" to PCE_FAST,
        "ngp" to NGP, "ngpc" to NGP,
        "wonderswan" to wswan(color = false), "wonderswancolor" to wswan(color = true),
        "atarilynx" to HANDY, "atari2600" to STELLA, "atari7800" to PROSYSTEM,
        "fbneo" to fbneo(Mandos.Pad.ARCADE6), "arcade" to fbneo(Mandos.Pad.ARCADE6), "neogeo" to fbneo(Mandos.Pad.ARCADE4, neogeo = true),
        "cps1" to fbneo(Mandos.Pad.ARCADE6), "cps2" to fbneo(Mandos.Pad.ARCADE6), "cps3" to fbneo(Mandos.Pad.ARCADE6),
        "n64" to MUPEN,
        "psx" to PCSX,
        "psp" to PPSSPP,
        "nds" to MELONDS,
    )
    /** Los sistemas que se juegan con Hiedra. */
    val activos: Set<String> get() = catalogo.keys

    /**
     * Los que se enseñan en «Sistemas» como consolas de Hiedra (uno por consola: «sfc», «genesis» o «tg16» son la misma con otro nombre
     * de carpeta), en este orden: las portátiles y las de sobremesa de Nintendo, Sega, el resto de 2D, arcade y las que están en pruebas.
     */
    val principales: List<String> = listOf(
        "gba", "gb", "gbc", "nes", "snes", "fds", "megadrive", "mastersystem", "gamegear", "pcengine", "ngp", "ngpc",
        "wonderswan", "wonderswancolor", "atarilynx", "atari2600", "atari7800", "nds", "n64", "psx", "psp", "segacd", "neogeo", "arcade",
    )

    fun para(sistema: String): Nucleo? = catalogo[sistema]

    /** Todos los núcleos distintos (por id), en el orden del catálogo. */
    val nucleos: List<Nucleo> get() = catalogo.values.distinctBy { it.id }

    const val BUILDBOT = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a"

    // ------------------------------------------------------------------ puro (se prueba sin móvil)

    fun nombreSo(id: String) = "${id}_libretro_android.so"
    fun url(id: String) = "$BUILDBOT/${nombreSo(id)}.zip"

    /** «Wed, 04 Nov 2026 07:22:14 GMT» → ms (0 si no se entiende). */
    fun fechaHttp(cabecera: String?): Long = runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(cabecera!!)!!.time
    }.getOrDefault(0L)

    /** ¿Hay en el buildbot uno más nuevo que el instalado? (con un día de margen: el buildbot recompila cada noche aunque no cambie nada) */
    fun hayNueva(instalado: Instalado?, enBuildbot: Long): Boolean =
        instalado != null && instalado.fecha > 0 && enBuildbot > instalado.fecha + 24 * 3600_000L

    fun aJson(i: Instalado): JSONObject = JSONObject().put("id", i.id).put("fecha", i.fecha).put("tamano", i.tamano).put("version", i.version)
    fun deJson(o: JSONObject): Instalado? = o.optString("id").takeIf { it.isNotBlank() }?.let { Instalado(it, o.optLong("fecha"), o.optLong("tamano"), o.optString("version")) }

    /** «mGBA · 4 nov 2026 · 2,4 MB» */
    fun describir(n: Nucleo, i: Instalado?): String =
        if (i == null) "${n.nombre} · se descarga al jugar" + (if (n.pesoAprox > 0) " (${megas(n.pesoAprox + (n.recursos?.pesoAprox ?: 0L))})" else "")
        else listOf(n.nombre, if (i.fecha > 0) Formato.fecha(i.fecha) else null, megas(i.tamano)).filterNotNull().joinToString(" · ")

    fun megas(b: Long): String = if (b < MB) "${(b + KB / 2) / KB} KB" else String.format(Locale("es", "ES"), "%.1f MB", b / 1_048_576.0)

    /** Las opciones con las que arranca: las del núcleo y, encima, las que hayas cambiado tú (solo las que se pueden cambiar). */
    fun variables(n: Nucleo, propias: Map<String, String>): Map<String, String> {
        val validas = n.opciones.associate { o -> o.clave to o.valores.map { it.first }.toSet() }
        return n.variables + propias.filter { (k, v) -> validas[k]?.contains(v) == true }
    }

    /**
     * Las BIOS obligatorias que faltan ([existe]: si un archivo de la carpeta del sistema está). De un grupo basta con una: si falta,
     * se devuelven todas las del grupo (para decir «una de estas»).
     */
    fun faltan(n: Nucleo, existe: (String) -> Boolean): List<Bios> {
        val obligatorias = n.bios.filter { it.obligatoria }
        val sueltas = obligatorias.filter { it.grupo == null && !existe(it.ruta) }
        val grupos = obligatorias.filter { it.grupo != null }.groupBy { it.grupo }.values.filter { g -> g.none { existe(it.ruta) } }.flatten()
        return sueltas + grupos
    }

    /** Todas las BIOS que se conocen (de todos los núcleos), sin repetir. */
    val todasLasBios: List<Bios> get() = catalogo.values.flatMap { it.bios }.distinctBy { it.ruta }

    /** Qué BIOS es un archivo: por su MD5 (aunque se llame de otra manera) o, si no se conoce el MD5, por su nombre. null = ninguna. */
    fun identificarBios(nombre: String, md5: String?, entre: List<Bios> = todasLasBios): Bios? =
        md5?.lowercase()?.let { m -> entre.firstOrNull { m in it.md5 } }
            ?: entre.firstOrNull { it.md5.isEmpty() && it.archivo.equals(nombre, ignoreCase = true) }
            ?: entre.firstOrNull { it.archivo.equals(nombre, ignoreCase = true) }

    /** Una ruta de dentro de un .zip que se puede escribir en [raiz] sin salirse de ella (nada de «../»). */
    fun dentroDe(raiz: File, nombre: String): File? {
        val f = File(raiz, nombre)
        return f.takeIf { it.canonicalPath.startsWith(raiz.canonicalPath + File.separator) }
    }

    // ------------------------------------------------------------------ en el móvil

    /** Cambia cuando se instala o se quita un núcleo, una BIOS o unos recursos (las pantallas lo observan). */
    var version by mutableIntStateOf(0)
        private set

    private fun dir(c: Context, id: String) = File(c.filesDir, "nucleos/$id")
    fun archivo(c: Context, id: String) = File(dir(c, id), nombreSo(id))
    fun instalado(c: Context, id: String): Instalado? =
        runCatching { File(dir(c, id), "nucleo.json").takeIf { it.exists() && archivo(c, id).exists() }?.readText()?.let { deJson(JSONObject(it)) } }.getOrNull()
    fun anterior(c: Context, id: String): Instalado? =
        runCatching { File(c.filesDir, "nucleos/$id.anterior/nucleo.json").takeIf { it.exists() }?.readText()?.let { deJson(JSONObject(it)) } }.getOrNull()

    /** La carpeta del sistema de libretro (las BIOS que pida un núcleo y los recursos de PPSSPP). */
    fun sistema(c: Context) = File(c.filesDir, "system").apply { mkdirs() }

    fun biosEsta(c: Context, b: Bios) = File(sistema(c), b.ruta).let { it.exists() && it.length() > 0 }
    fun faltan(c: Context, n: Nucleo): List<Bios> = faltan(n) { File(sistema(c), it).let { f -> f.exists() && f.length() > 0 } }

    /** ¿Están los recursos que necesita el núcleo (si necesita alguno)? */
    fun recursosListos(c: Context, n: Nucleo) = n.recursos?.let { File(sistema(c), it.prueba).exists() } ?: true

    /** Abre una conexión al buildbot con lo de siempre. */
    private fun conexion(u: String, metodo: String = "GET"): HttpURLConnection = (URL(u).openConnection() as HttpURLConnection).apply {
        requestMethod = metodo; connectTimeout = 20_000; readTimeout = 60_000
        setRequestProperty("User-Agent", "Hiedra/${VERSION} (Android)")
    }

    /** La fecha del núcleo que hay ahora en el buildbot (0 si no se sabe). Bloqueante (red). */
    fun fechaEnBuildbot(id: String): Long = runCatching {
        val con = conexion(url(id), "HEAD")
        try { if (con.responseCode in 200..299) fechaHttp(con.getHeaderField("Last-Modified")) else 0L } finally { con.disconnect() }
    }.getOrDefault(0L)

    /** Baja [u] a [destino] avisando de cuánto lleva. Devuelve la fecha de la cabecera Last-Modified. */
    private fun bajar(u: String, destino: File, progreso: (Float) -> Unit): Long {
        val con = conexion(u)
        try {
            if (con.responseCode !in 200..299) throw IOException("el buildbot de libretro respondió ${con.responseCode}")
            val total = con.contentLengthLong
            con.inputStream.use { i -> destino.outputStream().use { o ->
                val buf = ByteArray(64 * 1024); var leido = 0L
                while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); leido += n; if (total > 0) progreso(leido.toFloat() / total) }
            } }
            return fechaHttp(con.getHeaderField("Last-Modified"))
        } finally { con.disconnect() }
    }

    /**
     * Baja el núcleo del buildbot y lo deja listo. Si ya había uno, se guarda como «anterior» (por si los estados de la versión vieja
     * hacen falta). Bloqueante (red): fuera del hilo principal. [progreso] recibe la fracción (0–1).
     */
    fun instalar(c: Context, id: String, progreso: (Float) -> Unit = {}): Instalado {
        val tmp = File(c.cacheDir, "nucleo-$id.zip")
        try {
            val fecha = bajar(url(id), tmp, progreso)
            val nuevo = File(c.cacheDir, "nucleo-$id.so")
            var hallado = false
            ZipInputStream(tmp.inputStream()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory && e.name.endsWith(".so")) { nuevo.outputStream().use { z.copyTo(it) }; hallado = true; break }
                }
            }
            if (!hallado || nuevo.length() < 10_000) throw IOException("el archivo del núcleo ha llegado roto")
            val sha = MessageDigest.getInstance("SHA-1").let { md -> nuevo.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }; md.digest() }
            val inst = Instalado(id, fecha, nuevo.length(), sha.joinToString("") { "%02x".format(it) }.take(12))
            val d = dir(c, id)
            if (d.exists()) {
                val ant = File(c.filesDir, "nucleos/$id.anterior")
                ant.deleteRecursively(); d.renameTo(ant)
            }
            d.mkdirs()
            if (!nuevo.renameTo(archivo(c, id))) { nuevo.copyTo(archivo(c, id), overwrite = true); nuevo.delete() }
            File(d, "nucleo.json").writeText(aJson(inst).toString())
            version++
            return inst
        } finally { tmp.delete() }
    }

    /** Baja y descomprime en la carpeta del sistema lo que el núcleo necesita aparte (PPSSPP: unos 10 MB, una vez). Bloqueante (red). */
    fun instalarRecursos(c: Context, n: Nucleo, progreso: (Float) -> Unit = {}) {
        val r = n.recursos ?: return
        val tmp = File(c.cacheDir, "recursos-${n.id}.zip")
        try {
            bajar(r.url, tmp, progreso)
            val raiz = sistema(c)
            ZipInputStream(tmp.inputStream()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val f = dentroDe(raiz, e.name) ?: continue
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                }
            }
            if (!File(raiz, r.prueba).exists()) throw IOException("los archivos de ${n.nombre} han llegado incompletos")
            version++
        } finally { tmp.delete() }
    }

    /** Quita el núcleo (y la copia anterior). Los estados guardados se quedan: sirven si vuelves a bajar la misma versión. */
    fun quitar(c: Context, id: String) {
        dir(c, id).deleteRecursively(); File(c.filesDir, "nucleos/$id.anterior").deleteRecursively(); version++
    }

    /**
     * Copia una BIOS que has elegido a la carpeta del sistema, con el nombre que busca el núcleo (reconocida por su MD5 o por su nombre).
     * Devuelve cuál era, o null si no es ninguna de las que se conocen. Bloqueante (disco).
     */
    fun instalarBios(c: Context, uri: Uri, nombre: String, entre: List<Bios> = todasLasBios): Bios? {
        val tmp = File(c.cacheDir, "bios.tmp")
        try {
            val md = MessageDigest.getInstance("MD5")
            c.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o ->
                val b = ByteArray(64 * 1024); var total = 0L
                while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n); o.write(b, 0, n); total += n; if (total > 128 * MB) throw IOException("ese archivo es demasiado grande para ser una BIOS") }
            } } ?: return null
            val md5 = md.digest().joinToString("") { "%02x".format(it) }
            val b = identificarBios(nombre, md5, entre) ?: return null
            val destino = File(sistema(c), b.ruta).apply { parentFile?.mkdirs() }
            tmp.copyTo(destino, overwrite = true)
            version++
            return b
        } finally { tmp.delete() }
    }

    /** Lo que has cambiado de las opciones de un sistema: aquí y en tu cuenta (`ajustes/hiedra_nucleos`, un campo `op:<sistema>:<opción>`). */
    val GRUPO: EspejoCuenta.Grupo = EspejoCuenta.Grupo("hiedra_nucleos", "hiedra_nucleos", { k -> if (k.startsWith("op:")) EspejoCuenta.Tipo.TEXTO else null }, alLlegar = { version++ })
    private fun prefs(c: Context) = EspejoCuenta.prefs(c, GRUPO)
    fun opcionesPropias(c: Context, sistema: String): Map<String, String> =
        prefs(c).all.mapNotNull { (k, v) -> if (k.startsWith("op:$sistema:") && v is String) k.removePrefix("op:$sistema:") to v else null }.toMap()
    fun ponOpcion(c: Context, sistema: String, clave: String, valor: String) { prefs(c).edit().putString("op:$sistema:$clave", valor).apply() }
}
