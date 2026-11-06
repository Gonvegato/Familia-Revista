package com.gonzalo.hiedra.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import com.gonzalo.familia.pc.Sitio
import java.io.File
import java.text.Normalizer

/** Un sistema (una consola): su id (el mismo que en Hiedra del móvil), su nombre, el corto, sus extensiones y los nombres de carpeta que lo delatan. */
class SistemaEmu(val id: String, val nombre: String, val corto: String, val libretro: String?, val extensiones: Set<String>, alias: List<String> = emptyList()) {
    val alias: Set<String> = (listOf(id, nombre, corto) + alias).map(Emulacion::norm).toSet()
}

/**
 * Un emulador de los que Hiedra sabe abrir: el programa (en el PATH, en Flatpak o como app del Mac) y cómo se le pasa un juego para que
 * arranque directamente y a pantalla completa (las opciones de cada uno, comprobadas en su documentación).
 */
class Emulador(val id: String, val nombre: String, val sistemas: Set<String>, val binarios: List<String>, val flatpak: String, val apps: List<String>,
               val args: (rom: String) -> List<String>)

/** Cómo está instalado un programa: en el sistema (una ruta), en Flatpak (su id) o dentro de una app del Mac (su ejecutable). */
sealed class Instalado {
    abstract val como: String
    data class Binario(val ruta: String) : Instalado() { override val como get() = "instalado" }
    data class Flatpak(val id: String) : Instalado() { override val como get() = "Flatpak" }
    data class AppMac(val ejecutable: String) : Instalado() { override val como get() = "app" }
    fun orden(): List<String> = when (this) { is Binario -> listOf(ruta); is Flatpak -> listOf("flatpak", "run", id); is AppMac -> listOf(ejecutable) }
}

/** Una manera de jugar a un sistema: un emulador, o RetroArch con uno de sus núcleos, o un programa tuyo. [id]: «mgba», «retroarch:mgba», «propio». */
class Opcion(val id: String, val nombre: String, val instalado: Instalado?, val nucleo: File? = null) {
    val listo: Boolean get() = instalado != null
}

/** Una ROM encontrada en tus carpetas: dónde está (de este ordenador), su sistema y el nombre del archivo sin extensión (el de la cuenta). */
class Rom(val ruta: String, val sistema: String, val archivo: String) {
    val clave: String get() = "rom:$sistema:${Emulacion.norm(archivo)}"
}

/**
 * La emulación en este ordenador: tus carpetas de ROMs (de este ordenador: no viajan, como en el móvil), los sistemas, qué emulador usa cada
 * sistema (lo eliges tú; si no, el mejor que haya instalado) y la orden para abrir cada juego.
 */
object Emulacion {
    fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().filter { it.isLetterOrDigit() }

    private fun s(id: String, nombre: String, corto: String, libretro: String?, ext: String, vararg alias: String) =
        SistemaEmu(id, nombre, corto, libretro, ext.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(), alias.toList())

    /** Los sistemas, con los mismos ids que el catálogo de Hiedra del móvil (las ROMs tienen así la misma clave en los dos). */
    val SISTEMAS: List<SistemaEmu> = listOf(
        s("gba", "Game Boy Advance", "GBA", "Nintendo - Game Boy Advance", "gba,agb,zip,7z", "gameboyadvance", "advance"),
        s("nds", "Nintendo DS", "NDS", "Nintendo - Nintendo DS", "nds,dsi,ids,zip,7z", "nintendods", "ds"),
        s("gb", "Game Boy", "GB", "Nintendo - Game Boy", "gb,sgb,dmg,zip,7z", "gameboy"),
        s("gbc", "Game Boy Color", "GBC", "Nintendo - Game Boy Color", "gbc,cgb,zip,7z", "gameboycolor", "gameboycolour"),
        s("nes", "NES", "NES", "Nintendo - Nintendo Entertainment System", "nes,fds,unf,unif,zip,7z", "famicom", "nintendo"),
        s("snes", "Super Nintendo", "SNES", "Nintendo - Super Nintendo Entertainment System", "sfc,smc,fig,swc,bs,st,zip,7z", "supernintendo", "superfamicom", "sfc"),
        s("n64", "Nintendo 64", "N64", "Nintendo - Nintendo 64", "n64,z64,v64,ndd,zip,7z", "nintendo64"),
        s("n3ds", "Nintendo 3DS", "3DS", "Nintendo - Nintendo 3DS", "3ds,3dsx,cci,cxi,cia,app,zip,7z", "3ds", "nintendo3ds"),
        s("gc", "GameCube", "GC", "Nintendo - GameCube", "iso,gcm,gcz,rvz,ciso,wia,dol,elf,m3u", "gamecube", "ngc"),
        s("wii", "Wii", "Wii", "Nintendo - Wii", "iso,wbfs,rvz,gcz,ciso,wia,wad,dol,elf,m3u", "nintendowii"),
        s("wiiu", "Wii U", "Wii U", "Nintendo - Wii U", "wud,wux,wua,rpx", "nintendowiiu"),
        s("switch", "Nintendo Switch", "Switch", null, "nsp,xci,nca,nro,nso", "nintendoswitch"),
        s("megadrive", "Mega Drive", "MD", "Sega - Mega Drive - Genesis", "md,gen,smd,bin,68k,sgd,zip,7z", "genesis", "segagenesis", "md", "segamegadrive"),
        s("mastersystem", "Master System", "SMS", "Sega - Master System - Mark III", "sms,bin,zip,7z", "sms", "segamastersystem"),
        s("gamegear", "Game Gear", "GG", "Sega - Game Gear", "gg,bin,zip,7z", "gg", "segagamegear"),
        s("segacd", "Mega-CD", "MCD", "Sega - Mega-CD - Sega CD", "cue,chd,iso,m3u", "megacd", "segacd", "segamegacd"),
        s("sega32x", "32X", "32X", "Sega - 32X", "32x,bin,zip,7z", "32x", "segamegadrive32x"),
        s("saturn", "Saturn", "SAT", "Sega - Saturn", "cue,chd,iso,ccd,m3u", "segasaturn"),
        s("dreamcast", "Dreamcast", "DC", "Sega - Dreamcast", "gdi,cdi,chd,cue,m3u", "segadreamcast", "dc"),
        s("psx", "PlayStation", "PS1", "Sony - PlayStation", "cue,chd,pbp,m3u,iso,bin,img,ecm,mds", "playstation", "ps1", "psone", "playstation1"),
        s("ps2", "PlayStation 2", "PS2", "Sony - PlayStation 2", "iso,chd,cso,gz,m3u,bin,elf", "playstation2"),
        s("ps3", "PlayStation 3", "PS3", "Sony - PlayStation 3", "iso,pkg", "playstation3"),
        s("psp", "PSP", "PSP", "Sony - PlayStation Portable", "iso,cso,pbp,chd,elf", "playstationportable"),
        s("pcengine", "PC Engine", "PCE", "NEC - PC Engine - TurboGrafx 16", "pce,sgx,cue,chd,ccd,m3u", "turbografx16", "tg16", "pce", "turbografx"),
        s("arcade", "Arcade", "Arcade", "MAME", "zip,7z,chd", "mame", "fbneo", "fba", "finalburn", "neogeo"),
    )
    private val porId = SISTEMAS.associateBy { it.id }
    fun sistema(id: String): SistemaEmu? = porId[id]

    // Las extensiones que en la práctica son de un solo sistema (las mismas que en el móvil) y las que valen para muchos
    private val PROPIAS = mapOf(
        "gba" to "gba", "agb" to "gba", "gb" to "gb", "gbc" to "gbc", "sgb" to "gb", "nds" to "nds", "dsi" to "nds",
        "nes" to "nes", "sfc" to "snes", "smc" to "snes", "n64" to "n64", "z64" to "n64", "v64" to "n64",
        "md" to "megadrive", "gen" to "megadrive", "smd" to "megadrive", "sms" to "mastersystem", "gg" to "gamegear", "32x" to "sega32x",
        "3ds" to "n3ds", "cci" to "n3ds", "cxi" to "n3ds", "cia" to "n3ds", "gcm" to "gc", "rvz" to "gc", "gcz" to "gc", "wbfs" to "wii",
        "pce" to "pcengine", "cso" to "psp", "pbp" to "psp", "cue" to "psx", "gdi" to "dreamcast", "cdi" to "dreamcast",
        "nsp" to "switch", "xci" to "switch", "wud" to "wiiu", "wux" to "wiiu", "wua" to "wiiu", "rpx" to "wiiu",
    )
    val AMBIGUAS = setOf("zip", "7z", "bin", "iso", "chd", "rom", "img", "m3u", "elf")
    private val porExt: Map<String, List<SistemaEmu>> = SISTEMAS.flatMap { s -> s.extensiones.map { it to s } }.groupBy({ it.first }, { it.second })

    /** El sistema de un archivo: lo dice la carpeta donde está (Roms/gba/…, ROMs/Nintendo DS/…), si no su extensión; null = no se sabe. */
    fun detecta(nombre: String, carpetas: List<String> = emptyList()): SistemaEmu? {
        val ext = nombre.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        for (f in carpetas.asReversed()) { val n = norm(f); SISTEMAS.firstOrNull { n in it.alias && ext in it.extensiones }?.let { return it } }
        PROPIAS[ext]?.let { id -> porId[id]?.let { return it } }
        val cand = porExt[ext].orEmpty()
        if (ext !in AMBIGUAS && cand.size == 1) return cand[0]
        return null
    }

    fun esRom(nombre: String): Boolean = nombre.substringAfterLast('.', "").lowercase() in porExt

    /** Nombre bonito a partir del de archivo: sin extensión ni etiquetas como (USA, Europe) o [!] (el mismo que en el móvil). */
    fun titulo(archivo: String): String {
        val sin = archivo.replace(Regex("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]"), "").replace('_', ' ').trim()
        // «Legend of Zelda, The - …» → «The Legend of Zelda - …»
        val m = Regex("^(.+?), (The|A|An|El|La|Los|Las)( - .*)?$").find(sin)
        return (if (m != null) "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}" else sin).ifBlank { archivo }
    }

    /** La portada de libretro de una ROM (la caja), si su sistema tiene: los caracteres prohibidos (& * / : ` < > ? \ |) pasan a «_». */
    fun portada(r: Rom): String {
        val s = sistema(r.sistema)?.libretro ?: return ""
        fun enc(x: String) = java.net.URLEncoder.encode(x, "UTF-8").replace("+", "%20")
        return "https://thumbnails.libretro.com/${enc(s)}/Named_Boxarts/${enc(r.archivo.replace(Regex("[&*/:`<>?\\\\|]"), "_"))}.png"
    }

    // ---------------------------------------------------------------- recorrer las carpetas

    private const val MAX_PROFUNDIDAD = 8
    private const val MAX_ARCHIVOS = 30000

    /** En una carpeta con .cue (o .gdi), el .bin y las pistas son del mismo juego; con un .m3u, los discos que nombra también. */
    fun quitaPistas(nombres: List<String>, leeM3u: (String) -> List<String> = { emptyList() }): List<String> {
        val cues = nombres.filter { it.endsWith(".cue", true) || it.endsWith(".gdi", true) }.map { it.substringBeforeLast('.').lowercase() }
        val enM3u = nombres.filter { it.endsWith(".m3u", true) }.flatMap(leeM3u).map { it.substringAfterLast('/').lowercase() }.toSet()
        return nombres.filterNot { n ->
            val ext = n.substringAfterLast('.', "").lowercase()
            n.lowercase() in enM3u || (ext in setOf("bin", "img", "wav", "ccd", "sub", "raw", "iso") && cues.any { n.lowercase().startsWith(it) } && !n.endsWith(".cue", true))
        }
    }

    /** Las ROMs de una carpeta (y las de dentro). */
    fun escanea(raiz: File): List<Rom> {
        val salida = ArrayList<Rom>()
        fun rec(d: File, ruta: List<String>, prof: Int) {
            if (prof > MAX_PROFUNDIDAD || salida.size >= MAX_ARCHIVOS) return
            val hijos = d.listFiles().orEmpty().filter { !it.name.startsWith(".") }
            // Un juego de PS3 es una carpeta (PS3_GAME/USRDIR/EBOOT.BIN); uno de Wii U, una carpeta con code/*.rpx
            if (File(d, "PS3_GAME/USRDIR/EBOOT.BIN").isFile) { salida += Rom(File(d, "PS3_GAME/USRDIR/EBOOT.BIN").path, "ps3", d.name); return }
            if (d.name.equals("code", true)) {
                d.listFiles { f -> f.name.endsWith(".rpx", true) }?.firstOrNull()?.let { salida += Rom(it.path, "wiiu", d.parentFile?.name ?: it.nameWithoutExtension) }
                return
            }
            val archivos = hijos.filter { it.isFile }
            val validos = quitaPistas(archivos.map { it.name }) { m3u -> runCatching { File(d, m3u).readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") } }.getOrDefault(emptyList()) }.toSet()
            for (f in archivos) {
                if (f.name !in validos || !esRom(f.name)) continue
                val sis = detecta(f.name, ruta) ?: continue
                salida += Rom(f.path, sis.id, f.name.substringBeforeLast('.', f.name))
            }
            hijos.filter { it.isDirectory }.sortedBy { it.name.lowercase() }.forEach { rec(it, ruta + it.name, prof + 1) }
        }
        rec(raiz, listOf(raiz.name), 0)
        return salida
    }

    // ---------------------------------------------------------------- lo de este ordenador (no va a la cuenta)

    @Volatile var dirPruebas: File? = null
    private fun archivo() = File(dirPruebas ?: Sitio.de("hiedra"), "emulacion.json")
    private val cerrojo = Any()

    private fun lee(): Map<String, Any?> = synchronized(cerrojo) { runCatching { Json.lee(archivo().readText()).comoMapa() }.getOrDefault(emptyMap()) }
    private fun guarda(m: Map<String, Any?>) = synchronized(cerrojo) { runCatching { archivo().writeText(Json.escribe(m)) } }
    private fun cambia(f: (MutableMap<String, Any?>) -> Unit) = synchronized(cerrojo) { val m = LinkedHashMap(lee()); f(m); guarda(m) }

    /** Tus carpetas de ROMs en este ordenador. */
    fun carpetas(): List<File> = lee()["carpetas"].comoLista().map { File(it.comoTexto()) }
    fun anadeCarpeta(f: File) = cambia { m -> m["carpetas"] = (carpetas().map { it.path } + f.absolutePath).distinct() }
    fun quitaCarpeta(f: File) = cambia { m -> m["carpetas"] = carpetas().map { it.path }.filter { it != f.path } }

    /** El emulador que has elegido para un sistema ("" = el automático). */
    fun eleccion(sistema: String): String = lee()["eleccion"].comoMapa()[sistema].comoTexto()
    fun elige(sistema: String, opcion: String) = cambia { m -> m["eleccion"] = lee()["eleccion"].comoMapa() + (sistema to opcion) }

    /** Tu programa para un sistema (un emulador que Hiedra no conoce): la ruta y sus opciones, con {rom} donde va el juego. */
    fun propio(sistema: String): Pair<String, String>? = lee()["propios"].comoMapa()[sistema].comoMapa().let { p ->
        p["programa"].comoTexto().takeIf { it.isNotBlank() }?.let { it to p["args"].comoTexto().ifBlank { "{rom}" } }
    }
    fun ponPropio(sistema: String, programa: String, args: String) = cambia { m ->
        m["propios"] = lee()["propios"].comoMapa() + (sistema to mapOf("programa" to programa, "args" to args.ifBlank { "{rom}" }))
        m["eleccion"] = lee()["eleccion"].comoMapa() + (sistema to "propio")
    }

    /** Las ROMs de la última vez que se recorrieron tus carpetas (para no esperar al abrir). */
    fun romsGuardadas(): List<Rom> = lee()["roms"].comoLista().map { it.comoMapa() }.map { Rom(it["r"].comoTexto(), it["s"].comoTexto(), it["a"].comoTexto()) }.filter { it.ruta.isNotBlank() }

    /** Recorre tus carpetas otra vez (y lo apunta). */
    fun escaneaTodo(): List<Rom> {
        val l = carpetas().filter { it.isDirectory }.flatMap { escanea(it) }.distinctBy { it.ruta }
        cambia { m -> m["roms"] = l.map { mapOf("r" to it.ruta, "s" to it.sistema, "a" to it.archivo) } }
        return l
    }

    // ---------------------------------------------------------------- los emuladores

    val EMULADORES: List<Emulador> = listOf(
        Emulador("dolphin", "Dolphin", setOf("gc", "wii"), listOf("dolphin-emu"), "org.DolphinEmu.dolphin-emu", listOf("Dolphin")) { listOf("-b", "-C", "Dolphin.Display.Fullscreen=True", "-e", it) },
        Emulador("pcsx2", "PCSX2", setOf("ps2"), listOf("pcsx2-qt", "pcsx2"), "net.pcsx2.PCSX2", listOf("PCSX2")) { listOf("-batch", "-nogui", "-fullscreen", "--", it) },
        Emulador("duckstation", "DuckStation", setOf("psx"), listOf("duckstation-qt", "duckstation"), "org.duckstation.DuckStation", listOf("DuckStation")) { listOf("-batch", "-nogui", "-fullscreen", "--", it) },
        Emulador("ppsspp", "PPSSPP", setOf("psp"), listOf("PPSSPPSDL", "ppsspp"), "org.ppsspp.PPSSPP", listOf("PPSSPPSDL", "PPSSPP")) { listOf("--fullscreen", "--pause-menu-exit", it) },
        Emulador("rpcs3", "RPCS3", setOf("ps3"), listOf("rpcs3"), "net.rpcs3.RPCS3", listOf("RPCS3", "rpcs3")) { listOf("--no-gui", "--fullscreen", it) },
        Emulador("ryujinx", "Ryujinx", setOf("switch"), listOf("ryujinx", "Ryujinx"), "io.github.ryubing.Ryujinx", listOf("Ryujinx")) { listOf("--fullscreen", it) },
        Emulador("cemu", "Cemu", setOf("wiiu"), listOf("Cemu", "cemu"), "info.cemu.Cemu", listOf("Cemu")) { listOf("-f", "-g", it) },
        Emulador("melonds", "melonDS", setOf("nds"), listOf("melonDS"), "net.kuribo64.melonDS", listOf("melonDS")) { listOf("-f", it) },
        Emulador("mgba", "mGBA", setOf("gba", "gb", "gbc"), listOf("mgba-qt"), "io.mgba.mGBA", listOf("mGBA")) { listOf("-f", it) },
        Emulador("flycast", "Flycast", setOf("dreamcast"), listOf("flycast"), "org.flycast.Flycast", listOf("Flycast")) { listOf("-config", "window:fullscreen=yes", it) },
    )
    private val RETROARCH = Emulador("retroarch", "RetroArch", emptySet(), listOf("retroarch"), "org.libretro.RetroArch", listOf("RetroArch")) { listOf(it) }

    /** Los núcleos de RetroArch que valen para cada sistema, del mejor al peor (los nombres de sus archivos: `<núcleo>_libretro.so`). */
    val NUCLEOS: Map<String, List<String>> = mapOf(
        "gba" to listOf("mgba", "gpsp", "vbam", "vba_next", "mednafen_gba"),
        "gb" to listOf("gambatte", "sameboy", "mgba", "gearboy", "tgbdual"),
        "gbc" to listOf("gambatte", "sameboy", "mgba", "gearboy", "tgbdual"),
        "nes" to listOf("mesen", "nestopia", "fceumm", "quicknes"),
        "snes" to listOf("snes9x", "bsnes", "bsnes_hd_beta", "mednafen_supafaust"),
        "n64" to listOf("mupen64plus_next", "parallel_n64"),
        "nds" to listOf("melondsds", "melonds", "desmume"),
        "n3ds" to listOf("azahar", "citra"),
        "gc" to listOf("dolphin"), "wii" to listOf("dolphin"),
        "megadrive" to listOf("genesis_plus_gx", "picodrive", "blastem", "clownmdemu"),
        "mastersystem" to listOf("genesis_plus_gx", "picodrive", "gearsystem", "smsplus"),
        "gamegear" to listOf("genesis_plus_gx", "picodrive", "gearsystem"),
        "segacd" to listOf("genesis_plus_gx", "picodrive"),
        "sega32x" to listOf("picodrive"),
        "saturn" to listOf("mednafen_saturn", "kronos", "yabause"),
        "dreamcast" to listOf("flycast"),
        "psx" to listOf("swanstation", "mednafen_psx_hw", "mednafen_psx", "pcsx_rearmed"),
        "ps2" to listOf("pcsx2", "play"),
        "psp" to listOf("ppsspp"),
        "pcengine" to listOf("mednafen_pce_fast", "mednafen_pce", "mednafen_supergrafx", "geargrafx"),
        "arcade" to listOf("fbneo", "mame", "mame2003_plus"),
    )

    /** El orden automático: para cada sistema, primero el emulador que mejor va (si lo hay) y después RetroArch. */
    private val PREFERIDOS = mapOf("nds" to "melonds", "psx" to "duckstation", "ps2" to "pcsx2", "ps3" to "rpcs3", "psp" to "ppsspp", "gc" to "dolphin",
        "wii" to "dolphin", "wiiu" to "cemu", "switch" to "ryujinx", "dreamcast" to "flycast")

    private val casa get() = File(System.getProperty("user.home"))

    /** Dónde busca los núcleos cada RetroArch. */
    fun carpetasNucleos(i: Instalado): List<File> = when (i) {
        is Instalado.Flatpak -> listOf(File(casa, ".var/app/org.libretro.RetroArch/config/retroarch/cores"))
        is Instalado.AppMac -> listOf(File(casa, "Library/Application Support/RetroArch/cores"))
        is Instalado.Binario -> listOf(File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: File(casa, ".config").path, "retroarch/cores"),
            File("/usr/lib/libretro"), File("/usr/lib64/libretro"), File("/usr/lib/x86_64-linux-gnu/libretro"), File("/usr/local/lib/libretro"))
    }
    private fun extNucleo() = if (Sitio.sistema == Sitio.Sistema.MAC) "dylib" else "so"

    /** (En las pruebas: dónde «está» cada programa.) */
    @Volatile var instaladoPruebas: ((Emulador) -> Instalado?)? = null

    /** Dónde está un emulador en este ordenador: en el sistema antes que en Flatpak. */
    fun instalado(e: Emulador): Instalado? {
        instaladoPruebas?.let { return it(e) }
        if (Sitio.sistema == Sitio.Sistema.MAC) {
            val dirs = listOf(File("/Applications"), File(casa, "Applications"))
            for (a in e.apps) for (d in dirs) {
                val mac = File(d, "$a.app/Contents/MacOS")
                val exe = mac.listFiles()?.filter { it.isFile && it.canExecute() }?.let { l -> l.firstOrNull { it.name.equals(a, true) } ?: l.singleOrNull() }
                if (exe != null) return Instalado.AppMac(exe.path)
            }
            return null
        }
        e.binarios.firstNotNullOfOrNull { Programas.donde(it) }?.let { return Instalado.Binario(it.path) }
        if (e.flatpak in Programas.flatpaks()) return Instalado.Flatpak(e.flatpak)
        return null
    }

    /** Las maneras de jugar a un sistema que hay en este ordenador, en el orden en que se eligen solas. */
    fun opciones(sistema: String, conPropio: Boolean = true): List<Opcion> {
        val out = ArrayList<Opcion>()
        EMULADORES.filter { sistema in it.sistemas }.forEach { e -> out += Opcion(e.id, e.nombre, instalado(e)) }
        val ra = instalado(RETROARCH)
        NUCLEOS[sistema].orEmpty().forEach { n ->
            val f = ra?.let { i -> carpetasNucleos(i).map { File(it, "${n}_libretro.${extNucleo()}") }.firstOrNull { it.isFile } }
            out += Opcion("retroarch:$n", "RetroArch · ${nombreNucleo(n)}", if (f != null) ra else null, f)
        }
        if (conPropio) propio(sistema)?.let { (p, _) -> out += Opcion("propio", File(p).name, if (File(p).isFile) Instalado.Binario(p) else null) }
        val pref = PREFERIDOS[sistema]
        return out.sortedBy { if (it.id == pref) 0 else if (it.id.startsWith("retroarch:")) 1 else 2 }
    }

    fun nombreNucleo(n: String) = when (n) {
        "mgba" -> "mGBA"; "gpsp" -> "gpSP"; "vbam" -> "VBA-M"; "vba_next" -> "VBA Next"; "gambatte" -> "Gambatte"; "sameboy" -> "SameBoy"; "mesen" -> "Mesen"
        "nestopia" -> "Nestopia"; "fceumm" -> "FCEUmm"; "snes9x" -> "Snes9x"; "bsnes" -> "bsnes"; "mupen64plus_next" -> "Mupen64Plus-Next"; "parallel_n64" -> "ParaLLEl N64"
        "melondsds" -> "melonDS DS"; "melonds" -> "melonDS"; "desmume" -> "DeSmuME"; "dolphin" -> "Dolphin"; "genesis_plus_gx" -> "Genesis Plus GX"; "picodrive" -> "PicoDrive"
        "mednafen_saturn" -> "Beetle Saturn"; "flycast" -> "Flycast"; "swanstation" -> "SwanStation"; "mednafen_psx_hw" -> "Beetle PSX HW"; "mednafen_psx" -> "Beetle PSX"
        "pcsx_rearmed" -> "PCSX ReARMed"; "ppsspp" -> "PPSSPP"; "mednafen_pce_fast" -> "Beetle PCE Fast"; "fbneo" -> "FinalBurn Neo"; "mame" -> "MAME"; "pcsx2" -> "LRPS2"
        else -> n.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /** Lo que se usa para un sistema: lo que elegiste, si está; si no, lo primero que esté instalado. */
    fun elegida(sistema: String): Opcion? {
        val l = opciones(sistema)
        val e = eleccion(sistema)
        return l.firstOrNull { it.id == e && it.listo } ?: l.firstOrNull { it.listo }
    }

    /** La orden para abrir una ROM con una opción (null si no está instalada). */
    fun orden(o: Opcion, rom: String, sistema: String): List<String>? {
        val i = o.instalado ?: return null
        return when {
            o.id.startsWith("retroarch:") -> i.orden() + listOf("-f", "-L", (o.nucleo ?: return null).path, rom)
            o.id == "propio" -> i.orden() + partes(propio(sistema)?.second ?: "{rom}").map { it.replace("{rom}", rom) }
            else -> i.orden() + (EMULADORES.firstOrNull { it.id == o.id } ?: return null).args(rom)
        }
    }

    /** Las opciones de tu programa, separadas como en la terminal (respetando las comillas). */
    fun partes(s: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var comilla: Char? = null; var hay = false
        for (c in s) {
            when {
                comilla != null && c == comilla -> comilla = null
                comilla == null && (c == '"' || c == '\'') -> { comilla = c; hay = true }
                comilla == null && c.isWhitespace() -> { if (sb.isNotEmpty() || hay) { out += sb.toString(); sb.clear(); hay = false } }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty() || hay) out += sb.toString()
        return out
    }

    /** La orden para abrir una ROM con lo elegido para su sistema. */
    fun ordenPara(r: Rom): List<String>? = elegida(r.sistema)?.let { orden(it, r.ruta, r.sistema) }
}
