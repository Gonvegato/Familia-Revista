package com.gonzalo.hiedra.pc

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Lo que Hiedra lee de este ordenador (Steam, Heroic, Lutris, tus ROMs) y cómo abre cada cosa, con archivos de ejemplo. */
class FuentesTest {
    /** (En el Mac las direcciones steam:// y heroic:// las abre `open`; en Linux, Steam o xdg-open.) */
    private val mac = com.gonzalo.familia.pc.Sitio.sistema == com.gonzalo.familia.pc.Sitio.Sistema.MAC

    @get:Rule val tmp = TemporaryFolder()

    @Before fun prepara() { Pc.paraPruebas(tmp.root, emptyList()); Programas.flatpaksPruebas = emptySet() }
    @After fun fuera() { Pc.paraPruebas(null, null); Programas.pathPruebas = null; Programas.flatpaksPruebas = null; Emulacion.instaladoPruebas = null }

    private fun escribe(ruta: String, texto: String): File = File(tmp.root, ruta).also { it.parentFile.mkdirs(); it.writeText(texto) }

    // ---------------------------------------------------------------- Steam

    @Test fun vdfConBloquesEscapesYComentarios() {
        val m = Vdf.lee("""
            // un comentario
            "AppState"
            {
                "appid"     "1145360"
                "name"      "Hades \"II\""
                "UserConfig" { "language" "spanish" }
                "StateFlags"    "4"
            }
        """.trimIndent())
        assertEquals("1145360", Vdf.texto(m, "appstate", "AppID"))
        assertEquals("Hades \"II\"", Vdf.texto(m, "AppState", "name"))
        assertEquals("spanish", Vdf.texto(m, "AppState", "userconfig", "language"))
    }

    @Test fun lasBibliotecasDeOtrosDiscosYLoQueNoEstaInstalado() {
        val otra = File(tmp.root, "Disco2/SteamLibrary")
        escribe("Steam/config/libraryfolders.vdf", """
            "libraryfolders"
            {
                "0" { "path" "${File(tmp.root, "Steam").path}" "apps" { "1145360" "123" } }
                "1" { "path" "${otra.path}" }
            }
        """.trimIndent())
        // El formato viejo ponía la ruta tal cual
        assertEquals(listOf("/media/juegos"), Steam.rutasDeBibliotecas("\"LibraryFolders\" { \"TimeNextStatsReport\" \"1\" \"1\" \"/media/juegos\" }"))
        escribe("Steam/steamapps/appmanifest_1145360.acf", "\"AppState\" { \"appid\" \"1145360\" \"name\" \"Hades\" \"installdir\" \"Hades\" \"StateFlags\" \"4\" }")
        escribe("Steam/steamapps/appmanifest_1086940.acf", "\"AppState\" { \"appid\" \"1086940\" \"name\" \"Baldur's Gate 3\" \"StateFlags\" \"6\" }")  // instalado, con una actualización pendiente
        escribe("Steam/steamapps/appmanifest_1245620.acf", "\"AppState\" { \"appid\" \"1245620\" \"name\" \"Elden Ring\" \"StateFlags\" \"1026\" }") // bajándose: aún no
        escribe("Steam/steamapps/appmanifest_1493710.acf", "\"AppState\" { \"appid\" \"1493710\" \"name\" \"Proton Experimental\" \"StateFlags\" \"4\" }")
        File(otra, "steamapps").mkdirs()
        escribe("Disco2/SteamLibrary/steamapps/appmanifest_367520.acf", "\"AppState\" { \"appid\" \"367520\" \"name\" \"Hollow Knight\" \"StateFlags\" \"4\" }")
        Pc.steamPruebas = listOf(File(tmp.root, "Steam"))
        val l = Steam.juegos()
        assertEquals(listOf("Baldur's Gate 3", "Hades", "Hollow Knight"), l.map { it.nombre })
        val h = l.first { it.nombre == "Hades" }
        assertEquals("steam:1145360", h.clave); assertEquals("pc:steam:1145360", h.claveCuenta)
        assertTrue(h.carpeta.endsWith("steamapps/common/Hades"))
        assertEquals("https://cdn.cloudflare.steamstatic.com/steam/apps/1145360/library_hero.jpg", h.fondo)
    }

    @Test fun steamSeAbreConSteamYSeVeQueJuegoEstaAbierto() {
        if (mac) {
            // En el Mac, el sistema abre la dirección steam:// con Steam
            assertEquals(listOf("open", "steam://rungameid/10"), Steam.orden("10", flatpak = false) { it == "steam" })
        } else {
            assertEquals(listOf("steam", "steam://rungameid/10"), Steam.orden("10", flatpak = false) { it == "steam" })
            assertEquals(listOf("flatpak", "run", "com.valvesoftware.Steam", "steam://rungameid/10"), Steam.orden("10", flatpak = true) { it == "flatpak" })
        }
        val lineas = listOf("/home/g/.local/share/Steam/ubuntu12_32/reaper SteamLaunch AppId=1145360 -- /home/g/.local/share/Steam/steamapps/common/SteamLinuxRuntime_sniper/_v2-entry-point",
            "/usr/bin/kwin_wayland", "/home/g/.local/share/Steam/ubuntu12_32/steam -srt-logger-opened")
        assertEquals(setOf("1145360"), Steam.enMarcha(lineas))
    }

    // ---------------------------------------------------------------- Heroic

    @Test fun heroicEpicGogAmazonYLoAnadidoAMano() {
        val raiz = File(tmp.root, "heroic")
        escribe("heroic/legendaryConfig/legendary/installed.json", """
            {"Fortnite": {"app_name": "Fortnite", "title": "Fortnite", "install_path": "/home/g/Games/Heroic/Fortnite", "is_dlc": false, "platform": "Windows"},
             "Expansion": {"app_name": "Expansion", "title": "Un DLC", "install_path": "/x", "is_dlc": true}}
        """)
        escribe("heroic/store_cache/legendary_library.json", """{"library": [{"app_name": "Fortnite", "title": "Fortnite", "art_square": "https://cdn1.epicgames.com/f.jpg", "art_cover": "https://cdn1.epicgames.com/ancha.jpg"}]}""")
        escribe("heroic/gog_store/installed.json", """{"installed": [{"appName": "1207658924", "install_path": "/home/g/Games/Heroic/Unreal Tournament", "is_dlc": false}]}""")
        escribe("heroic/store_cache/gog_library.json", """{"games": [{"app_name": "1207658924", "title": "Unreal Tournament GOTY", "art_square": "https://images.gog.com/ut.jpg"}]}""")
        escribe("heroic/nile_config/nile/installed.json", """[{"id": "amzn1.adg.product.abc", "version": "1", "path": "/home/g/Games/Heroic/Amazon/Juego", "size": 1}]""")
        escribe("heroic/nile_config/nile/library.json", """[{"id": "amzn1.adg.product.abc", "product": {"title": "Juego de Amazon"}}]""")
        escribe("heroic/sideload_apps/library.json", """{"games": [{"app_name": "abcd", "title": "Mi juego", "is_installed": true, "install": {"install_path": "/opt/mijuego"}}]}""")
        val l = Heroic.juegos(raiz).associateBy { it.clave }
        assertEquals(setOf("heroic:legendary:Fortnite", "heroic:gog:1207658924", "heroic:nile:amzn1.adg.product.abc", "heroic:sideload:abcd"), l.keys)
        val f = l.getValue("heroic:legendary:Fortnite")
        assertEquals("Epic", f.etiqueta); assertEquals("https://cdn1.epicgames.com/f.jpg", f.portada); assertEquals("/home/g/Games/Heroic/Fortnite", f.carpeta)
        assertEquals("Unreal Tournament GOTY", l.getValue("heroic:gog:1207658924").nombre); assertEquals("GOG", l.getValue("heroic:gog:1207658924").etiqueta)
        assertEquals("Juego de Amazon", l.getValue("heroic:nile:amzn1.adg.product.abc").nombre); assertEquals("Amazon", l.getValue("heroic:nile:amzn1.adg.product.abc").etiqueta)
        assertEquals("/opt/mijuego", l.getValue("heroic:sideload:abcd").carpeta)
        assertEquals("heroic://launch?appName=Unreal%20Tournament&runner=gog", Heroic.url("gog", "Unreal Tournament"))
        assertEquals(listOf(if (mac) "open" else "xdg-open", "heroic://launch?appName=Fortnite&runner=legendary"), Heroic.orden("legendary", "Fortnite"))
    }

    // ---------------------------------------------------------------- Lutris

    @Test fun lutrisSinLosDeSteamYConSuPortada() {
        val portada = escribe("coverart/celeste.jpg", "x")
        val salida = """
            2026-11-05 21:00:00,000: Startup complete
            [
              {"id": 3, "slug": "celeste", "name": "Celeste", "runner": "linux", "platform": "Linux", "year": 2018, "directory": "/home/g/Games/celeste", "playtime": null, "coverPath": "${portada.path}"},
              {"id": 7, "slug": "hades", "name": "Hades", "runner": "steam", "platform": "Linux", "directory": null},
              {"id": 9, "slug": "diablo", "name": "Diablo II", "runner": "wine", "platform": "Windows", "directory": "/home/g/Games/diablo-ii", "coverPath": "/no/existe.jpg"}
            ]
        """.trimIndent()
        val l = Lutris.juegosDe(salida, conSteam = true)
        assertEquals(listOf("lutris:3", "lutris:9"), l.map { it.clave })
        assertEquals(portada.toURI().toString(), l[0].portada)
        assertEquals("", l[1].portada)
        assertEquals("/home/g/Games/diablo-ii", l[1].carpeta)
        assertEquals(3, Lutris.juegosDe(salida, conSteam = false).size)
        assertEquals(listOf("lutris", "lutris:rungameid/9"), Lutris.orden("9", flatpak = false))
        assertEquals(listOf("flatpak", "run", "net.lutris.Lutris", "lutris:rungameid/9"), Lutris.orden("9", flatpak = true))
        assertTrue(Lutris.juegosDe("Lutris no está bien", conSteam = true).isEmpty())
    }

    // ---------------------------------------------------------------- emulación

    @Test fun elSistemaDeCadaRomYLaMismaClaveQueEnElMovil() {
        assertEquals("gba", Emulacion.detecta("Pokemon - Emerald Version (USA, Europe).gba")?.id)
        assertEquals("psx", Emulacion.detecta("Final Fantasy VII (Disc 1).chd", listOf("Roms", "PlayStation"))?.id)
        assertEquals("ps2", Emulacion.detecta("Shadow of the Colossus.iso", listOf("roms", "ps2"))?.id)
        assertNull("una .iso suelta puede ser de muchos sistemas", Emulacion.detecta("Juego.iso", listOf("Descargas")))
        assertEquals("switch", Emulacion.detecta("Zelda.nsp")?.id)
        // La clave de la ROM es la del móvil: rom:<sistema>:<nombre del archivo sin extensión, normalizado>
        val r = Rom("/r/gba/Legend of Zelda, The - The Minish Cap (USA).gba", "gba", "Legend of Zelda, The - The Minish Cap (USA)")
        assertEquals("rom:gba:legendofzeldathetheminishcapusa", r.clave)
        assertEquals(r.clave, Lanzador.rom("gba", "Legend of Zelda, The - The Minish Cap (USA)").clave)
        assertEquals("The Legend of Zelda - The Minish Cap", Emulacion.titulo(r.archivo))
        assertEquals("Pokémon Esmeralda", Emulacion.titulo("Pokémon Esmeralda (Spain) [!]"))
        assertEquals("https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/Legend%20of%20Zelda%2C%20The%20-%20The%20Minish%20Cap%20%28USA%29.png", Emulacion.portada(r))
    }

    @Test fun recorrerLasCarpetasDeRoms() {
        escribe("Roms/gba/Advance Wars (USA).gba", "x")
        escribe("Roms/gba/Advance Wars (USA).sav", "x")
        escribe("Roms/psx/Crash Bandicoot (USA).cue", "FILE \"Crash Bandicoot (USA).bin\" BINARY")
        escribe("Roms/psx/Crash Bandicoot (USA).bin", "x")
        escribe("Roms/psx/FF7/Final Fantasy VII.m3u", "Final Fantasy VII (Disc 1).chd\nFinal Fantasy VII (Disc 2).chd")
        escribe("Roms/psx/FF7/Final Fantasy VII (Disc 1).chd", "x")
        escribe("Roms/psx/FF7/Final Fantasy VII (Disc 2).chd", "x")
        escribe("Roms/ps3/Demon's Souls [BLUS30443]/PS3_GAME/USRDIR/EBOOT.BIN", "x")
        escribe("Roms/wiiu/Breath of the Wild/code/U-King.rpx", "x")
        escribe("Roms/varios/Leeme.txt", "x")
        val l = Emulacion.escanea(File(tmp.root, "Roms")).associateBy { it.archivo }
        assertEquals(setOf("Advance Wars (USA)", "Crash Bandicoot (USA)", "Final Fantasy VII", "Demon's Souls [BLUS30443]", "Breath of the Wild"), l.keys)
        assertEquals("psx", l.getValue("Final Fantasy VII").sistema)
        assertTrue(l.getValue("Final Fantasy VII").ruta.endsWith(".m3u"))
        assertEquals("ps3", l.getValue("Demon's Souls [BLUS30443]").sistema)
        assertTrue(l.getValue("Demon's Souls [BLUS30443]").ruta.endsWith("EBOOT.BIN"))
        assertEquals("wiiu", l.getValue("Breath of the Wild").sistema)
        // Las carpetas y lo encontrado se apuntan en este ordenador
        Emulacion.anadeCarpeta(File(tmp.root, "Roms"))
        assertEquals(5, Emulacion.escaneaTodo().size)
        assertEquals(5, Emulacion.romsGuardadas().size)
        assertEquals(5, Pc.todos().count { it.fuente == Fuente.EMULACION })
    }

    @Test fun laOrdenDeCadaEmulador() {
        val nucleos = File(tmp.root, "cores").also { it.mkdirs() }
        File(nucleos, "mgba_libretro.so").writeText("x")
        // RetroArch en el sistema (con su núcleo de GBA), Dolphin en Flatpak y nada más
        Emulacion.instaladoPruebas = { e -> when (e.id) { "retroarch" -> Instalado.Binario("/usr/bin/retroarch"); "dolphin" -> Instalado.Flatpak("org.DolphinEmu.dolphin-emu"); else -> null } }
        val gba = Emulacion.opciones("gba")
        val ra = gba.first { it.id == "retroarch:mgba" }
        assertNull("sin la carpeta de núcleos de prueba no lo ve", ra.nucleo.takeIf { it?.path?.startsWith(tmp.root.path) == true })
        val conNucleo = Opcion("retroarch:mgba", "RetroArch · mGBA", Instalado.Binario("/usr/bin/retroarch"), File(nucleos, "mgba_libretro.so"))
        assertEquals(listOf("/usr/bin/retroarch", "-f", "-L", File(nucleos, "mgba_libretro.so").path, "/r/a.gba"), Emulacion.orden(conNucleo, "/r/a.gba", "gba"))
        val gc = Emulacion.opciones("gc")
        assertEquals("primero el emulador que mejor va", "dolphin", gc.first().id)
        assertEquals(listOf("flatpak", "run", "org.DolphinEmu.dolphin-emu", "-b", "-C", "Dolphin.Display.Fullscreen=True", "-e", "/r/m.rvz"), Emulacion.orden(gc.first(), "/r/m.rvz", "gc"))
        assertEquals("dolphin", Emulacion.elegida("gc")?.id)
        assertNull("PS2 sin PCSX2", Emulacion.elegida("ps2"))
        val ps2 = Opcion("pcsx2", "PCSX2", Instalado.Binario("/usr/bin/pcsx2-qt"))
        assertEquals(listOf("/usr/bin/pcsx2-qt", "-batch", "-nogui", "-fullscreen", "--", "/r/sotc.iso"), Emulacion.orden(ps2, "/r/sotc.iso", "ps2"))
        // Tu programa, con sus opciones (las comillas se respetan)
        val prog = escribe("bin/miemu", "#!/bin/sh")
        Emulacion.ponPropio("ps2", prog.path, "--pantalla \"modo grande\" {rom}")
        assertEquals("propio", Emulacion.eleccion("ps2"))
        val p = Emulacion.elegida("ps2")!!
        assertEquals(listOf(prog.path, "--pantalla", "modo grande", "/r/sotc.iso"), Emulacion.orden(p, "/r/sotc.iso", "ps2"))
        assertEquals(listOf("-f", "a b", ""), Emulacion.partes("-f 'a b' \"\""))
    }

    // ---------------------------------------------------------------- jugando: los procesos

    @Test fun unJuegoSeReconocePorSuCarpetaTambienConWine() {
        val lineas = listOf("/usr/bin/python3 /home/g/.local/share/Steam/compatibilitytools.d/GE-Proton/proton waitforexitandrun /home/g/Games/Heroic/Hades/Hades.exe",
            "Z:\\home\\g\\Games\\Lutris\\Diablo II\\Game.exe")
        assertTrue(Procesos.hayDe("/home/g/Games/Heroic/Hades", lineas))
        assertTrue(Procesos.hayDe("/home/g/Games/Lutris/Diablo II/", lineas))
        assertFalse(Procesos.hayDe("/home/g/Games/Heroic/Hade", lineas))
        assertFalse("una carpeta demasiado corta valdría para todo", Procesos.hayDe("/", lineas))
    }

    @Test fun elTiempoDeLoQueAbreOtroProgramaSeCuentaMientrasSeVe() {
        val p = Pc.Partida("heroic:legendary:Hades", "Hades", "", 1_000L, "", cuenta = true)
        Pc.vigilaParaPruebas(p, carpeta = "/home/g/Games/Heroic/Hades")
        val con = listOf("proton waitforexitandrun /home/g/Games/Heroic/Hades/Hades.exe")
        Pc.mira(10_000L, emptyList())                 // Heroic aún lo está abriendo
        assertFalse(p.enMarcha)
        Pc.mira(20_000L, con); Pc.mira(23_000L, con)
        assertTrue(p.enMarcha); assertSame(p, Pc.ahora)
        Pc.mira(26_000L, emptyList())                 // una mirada sin él: aún no se da por cerrado
        assertSame(p, Pc.ahora)
        Pc.mira(29_000L, emptyList())
        assertNull(Pc.ahora)
        assertEquals("del primer momento en que se vio al último", 3_000L, Pc.ultimaTerminadaParaPruebas)
        // Si no llega a verse en 20 minutos, se olvida sin contar nada
        val q = Pc.Partida("lutris:3", "Celeste", "", 0L, "", cuenta = true)
        Pc.vigilaParaPruebas(q, carpeta = "/home/g/Games/celeste")
        Pc.mira(21 * 60_000L, emptyList())
        assertNull(Pc.ahora)
    }
}
