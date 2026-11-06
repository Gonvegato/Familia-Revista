package com.gonzalo.hiedra

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * El catálogo de núcleos del jugador interno: que cada consola tenga el núcleo que usa Lemuroid con el nombre que tiene en el buildbot
 * de libretro, que abra las extensiones que encuentra el escaneo, que sus mandos usen botones que el núcleo conoce, las BIOS, los discos
 * de varios archivos y el emulador automático. Sin móvil (el núcleo es código de ARM y no corre en el ordenador).
 */
class NucleosTest {
    @Before fun cargar() { if (Catalogo.sistemas.isEmpty()) Catalogo.analizar(File("src/main/assets/emulators.json").readText()) }

    /**
     * Los núcleos que había en https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/ el 5 de noviembre de 2026 (comprobado
     * en su lista; cada uno como `<id>_libretro_android.so.zip`). Si alguno cambia de nombre allí, esta prueba lo dice.
     */
    private val enElBuildbot = setOf(
        "mgba", "gambatte", "fceumm", "snes9x", "genesis_plus_gx", "mednafen_pce_fast", "mednafen_ngp", "mednafen_wswan", "handy", "stella",
        "prosystem", "fbneo", "mupen64plus_next_gles3", "pcsx_rearmed", "ppsspp", "melonds",
    )

    @Test fun cadaNucleoEstaEnElBuildbot() {
        assertEquals(enElBuildbot, Nucleos.nucleos.map { it.id }.toSet())
        for (n in Nucleos.nucleos) assertEquals("${Nucleos.BUILDBOT}/${n.id}_libretro_android.so.zip", Nucleos.url(n.id))
        assertEquals("mupen64plus_next_gles3_libretro_android.so", Nucleos.nombreSo("mupen64plus_next_gles3"))
    }

    @Test fun losDeLemuroidParaCadaConsola() {
        val esperado = mapOf(
            "gba" to "mgba", "gb" to "gambatte", "gbc" to "gambatte", "nes" to "fceumm", "snes" to "snes9x", "megadrive" to "genesis_plus_gx",
            "mastersystem" to "genesis_plus_gx", "gamegear" to "genesis_plus_gx", "segacd" to "genesis_plus_gx", "pcengine" to "mednafen_pce_fast",
            "ngp" to "mednafen_ngp", "ngpc" to "mednafen_ngp", "wonderswan" to "mednafen_wswan", "wonderswancolor" to "mednafen_wswan",
            "atarilynx" to "handy", "atari2600" to "stella", "atari7800" to "prosystem", "neogeo" to "fbneo", "arcade" to "fbneo",
            "n64" to "mupen64plus_next_gles3", "psx" to "pcsx_rearmed", "psp" to "ppsspp", "nds" to "melonds",
        )
        for ((s, id) in esperado) assertEquals(s, id, Nucleos.para(s)?.id)
    }

    @Test fun cadaSistemaExisteEnElCatalogoYEscaneaSusExtensiones() {
        for ((s, n) in Nucleos.catalogo) {
            val sis = Catalogo.sistema(s)
            assertNotNull("$s no está en emulators.json", sis)
            // Lo que abre el núcleo lo encuentra el escaneo de carpetas (si no, la ROM no llegaría a la biblioteca)
            for (e in n.extensiones) assertTrue("$s: .$e no está en las extensiones de ES-DE", e in sis!!.extensiones)
        }
    }

    @Test fun losDelicadosEstanEnPruebas() {
        for (s in listOf("n64", "psx", "psp", "nds", "neogeo", "arcade", "segacd")) assertTrue(s, Nucleos.para(s)!!.pruebas)
        for (s in listOf("gba", "gb", "gbc", "nes", "snes", "megadrive", "mastersystem", "gamegear", "pcengine", "ngp", "wonderswan", "atarilynx", "atari2600", "atari7800"))
            assertFalse(s, Nucleos.para(s)!!.pruebas)
        // Solo GBA pasa por delante de un emulador que ya tengas
        assertEquals(setOf("gba"), Nucleos.catalogo.filterValues { it.alFrente }.keys)
    }

    @Test fun lasOpcionesDeLemuroidConLosNombresDelBuildbot() {
        assertEquals("Touch", Nucleos.para("nds")!!.variables["melonds_touch_mode"])
        assertEquals("disabled", Nucleos.para("psx")!!.variables["pcsx_rearmed_drc"])
        assertEquals("320x240", Nucleos.para("n64")!!.variables["mupen64plus-43screensize"])
        assertEquals("enabled", Nucleos.para("psp")!!.variables["ppsspp_frame_duplication"])
        assertEquals("GB - Pocket", Nucleos.para("gb")!!.variables["gambatte_gb_internal_palette"])
        assertEquals("landscape", Nucleos.para("wonderswan")!!.variables["wswan_rotate_display"])
        assertFalse(Nucleos.para("n64")!!.saltarRepetidos); assertFalse(Nucleos.para("psx")!!.saltarRepetidos); assertTrue(Nucleos.para("snes")!!.saltarRepetidos)
        assertTrue(Nucleos.para("psx")!!.directo && Nucleos.para("psp")!!.directo)
        assertTrue(Nucleos.para("neogeo")!!.zipEntero)
        assertTrue(Nucleos.para("nds")!!.tactil)
        // Cada opción que se puede cambiar empieza en un valor que está entre los suyos
        for (n in Nucleos.nucleos) for (o in n.opciones) {
            val ini = n.variables[o.clave]
            if (ini != null) assertTrue("${n.id} ${o.clave}=$ini", o.valores.any { it.first == ini })
        }
    }

    @Test fun variablesConLasTuyasEncima() {
        val gb = Nucleos.para("gb")!!
        val v = Nucleos.variables(gb, mapOf("gambatte_gb_colorization" to "SGB", "inventada" to "x", "gambatte_gb_internal_palette" to "Cualquiera"))
        assertEquals("SGB", v["gambatte_gb_colorization"])
        // Lo que no es una opción que se pueda cambiar, o un valor que no es de los suyos, no se pasa al núcleo
        assertNull(v["inventada"]); assertEquals("GB - Pocket", v["gambatte_gb_internal_palette"])
        assertEquals("internal", Nucleos.variables(gb, mapOf("gambatte_gb_colorization" to "rosa"))["gambatte_gb_colorization"])
    }

    @Test fun biosQueFaltan() {
        // El Mega-CD necesita una de las tres regiones
        val cd = Nucleos.para("segacd")!!
        assertEquals(3, Nucleos.faltan(cd) { false }.size)
        assertEquals(emptyList<Nucleos.Bios>(), Nucleos.faltan(cd) { it == "bios_CD_E.bin" })
        // Neo Geo: neogeo.zip, en la carpeta de FBNeo
        assertEquals(listOf("fbneo/neogeo.zip"), Nucleos.faltan(Nucleos.para("neogeo")!!) { false }.map { it.ruta })
        assertEquals(emptyList<Nucleos.Bios>(), Nucleos.faltan(Nucleos.para("arcade")!!) { false })
        // FDS: la BIOS del disco; la NES, ninguna. PlayStation, DS y Lynx arrancan sin la suya
        assertEquals(listOf("disksys.rom"), Nucleos.faltan(Nucleos.para("fds")!!) { false }.map { it.ruta })
        for (s in listOf("nes", "psx", "nds", "atarilynx", "gba")) assertEquals(s, emptyList<Nucleos.Bios>(), Nucleos.faltan(Nucleos.para(s)!!) { false })
    }

    @Test fun reconocerUnaBios() {
        assertEquals("scph5501.bin", Nucleos.identificarBios("bios-ps1-usa.bin", "490F666E1AFB15B7362B406ED1CEA246")?.ruta)
        assertEquals("bios_CD_J.bin", Nucleos.identificarBios("x", "278a9397d192149e84e820ac621a8edd")?.ruta)
        // Sin MD5 conocido (las de FBNeo): por el nombre
        assertEquals("fbneo/neogeo.zip", Nucleos.identificarBios("NEOGEO.ZIP", "abc")?.ruta)
        assertEquals("lynxboot.img", Nucleos.identificarBios("lynxboot.img", "otro")?.ruta)
        assertNull(Nucleos.identificarBios("foto.jpg", "123"))
    }

    @Test fun nadaSeSaleDeLaCarpeta() {
        val raiz = File(System.getProperty("java.io.tmpdir"), "hiedra-zip").apply { mkdirs() }
        assertNotNull(Nucleos.dentroDe(raiz, "PPSSPP/ppge_atlas.zim"))
        assertNull(Nucleos.dentroDe(raiz, "../../etc/passwd"))
        assertNull(Nucleos.dentroDe(raiz, "PPSSPP/../../fuera"))
    }

    @Test fun cuandoHayUnaVersionNueva() {
        val i = Nucleos.Instalado("snes9x", 1_000_000_000_000L, 1, "v")
        assertFalse(Nucleos.hayNueva(i, 1_000_000_000_000L + 3600_000L))
        assertTrue(Nucleos.hayNueva(i, 1_000_000_000_000L + 3 * 86_400_000L))
        assertFalse(Nucleos.hayNueva(null, 2_000_000_000_000L))
        assertFalse(Nucleos.hayNueva(i.copy(fecha = 0), 2_000_000_000_000L))
    }

    @Test fun pesos() {
        assertEquals("420 KB", Nucleos.megas(420 * 1024L))
        assertEquals("16,3 MB", Nucleos.megas(16_700 * 1024L))
        // PPSSPP dice lo que pesa con sus archivos
        assertEquals("PPSSPP · se descarga al jugar (17,7 MB)", Nucleos.describir(Nucleos.para("psp")!!, null))
    }

    // ------------------------------------------------------------------ los mandos de cada consola

    @Test fun cadaConsolaUsaSuMando() {
        assertEquals(Mandos.Pad.SNES, Nucleos.para("snes")!!.pad)
        assertEquals(Mandos.Pad.PSX, Nucleos.para("psx")!!.pad)
        assertEquals(Mandos.Pad.ARCADE4, Nucleos.para("neogeo")!!.pad)
        assertTrue(Mandos.disposicion("n64").analogico)
        assertFalse(Mandos.disposicion("psx").analogico)
        // Un sistema sin núcleo: todos los botones y los sticks como sticks
        assertTrue(Mandos.disposicion("dreamcast").analogico)
    }

    @Test fun r2AceleraSalvoEnPlayStation() {
        assertEquals(Mandos.Destino.Hacer(Mandos.Accion.AVANCE), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_R2, "snes", false))
        assertEquals(Mandos.Destino.Hacer(Mandos.Accion.AVANCE), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_R2, "nds", false))
        assertEquals(Mandos.Destino.Pad(Mandos.Boton.R2), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_R2, "psx", false))
        // N64: el gatillo izquierdo es Z (L2); el botón de abajo del mando es el A de la N64 (B en el RetroPad)
        assertEquals(Mandos.Destino.Pad(Mandos.Boton.L2), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_L2, "n64", false))
        assertEquals(Mandos.Destino.Pad(Mandos.Boton.B), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_A, "n64", false))
        assertNull(Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_L2, "megadrive", false))
    }

    @Test fun losSticksDeLaN64() {
        val e = Mandos.Estado("n64")
        // El stick izquierdo es el stick; la cruceta, solo la cruceta
        val s = e.ejes("m", Mandos.Ejes(x = 0.8f, y = -0.5f))
        assertTrue(Mandos.Salida.Analogico(true, 0.8f, -0.5f) in s)
        assertFalse(s.any { it is Mandos.Salida.Cruceta && (it.x != 0f || it.y != 0f) })
        // Los botones C del táctil van al stick derecho
        assertEquals(listOf(Mandos.Salida.Analogico(false, 0f, -1f)), e.stickTactil(false, 0f, -1f))
        // El táctil y el mando a la vez se suman (sin pasar de 1)
        assertEquals(listOf(Mandos.Salida.Analogico(true, 1f, -0.5f)), e.stickTactil(true, 0.5f, 0f))
        // Soltar todo deja los sticks en el centro
        val fin = e.soltarTodo()
        assertTrue(Mandos.Salida.Analogico(true, 0f, 0f) in fin && Mandos.Salida.Analogico(false, 0f, 0f) in fin)
    }

    @Test fun enPlayStationElStickEsCruceta() {
        // Mando clásico de PlayStation (el de Lemuroid por defecto): el stick izquierdo mueve la cruceta
        assertEquals(listOf(Mandos.Salida.Cruceta(-1f, 0f)), Mandos.Estado("psx").ejes("m", Mandos.Ejes(x = -0.9f)).filterIsInstance<Mandos.Salida.Cruceta>())
    }

    // ------------------------------------------------------------------ discos de varios archivos

    @Test fun lasPistasDeUnCue() {
        val cue = """
            FILE "Final Fantasy VII (Disc 1) (Track 1).bin" BINARY
              TRACK 01 MODE2/2352
                INDEX 01 00:00:00
            FILE "Final Fantasy VII (Disc 1) (Track 2).bin" BINARY
              TRACK 02 AUDIO
            file Sin_Comillas.bin BINARY
            FILE "C:\Juegos\otra carpeta\Pista.wav" WAVE
        """.trimIndent()
        assertEquals(listOf("Final Fantasy VII (Disc 1) (Track 1).bin", "Final Fantasy VII (Disc 1) (Track 2).bin", "Sin_Comillas.bin", "Pista.wav"), Discos.pistasCue(cue))
    }

    @Test fun unM3uConSusDiscosYSusPistas() {
        val textos = mapOf(
            "FF7.m3u" to "# Final Fantasy VII\nFF7 (Disc 1).cue\n\nFF7 (Disc 2).chd\n",
            "FF7 (Disc 1).cue" to "FILE \"FF7 (Disc 1).bin\" BINARY\n",
        )
        assertEquals(listOf("FF7 (Disc 1).cue", "FF7 (Disc 1).bin", "FF7 (Disc 2).chd"), Discos.necesarios("FF7.m3u", { textos[it] }))
        assertEquals(listOf("Juego.img", "Juego.sub"), Discos.necesarios("Juego.ccd", { null }))
        assertEquals(listOf("Juego.img"), Discos.necesarios("Juego.ccd", { null }, { it.endsWith(".img") }))
        assertEquals(emptyList<String>(), Discos.necesarios("Juego.chd", { null }))
        // Un .m3u que se apunta a sí mismo no da vueltas sin fin
        assertEquals(emptyList<String>(), Discos.necesarios("x.m3u", { "x.m3u" }))
    }

    @Test fun elEscaneoNoDuplicaLosDiscosDeUnM3u() {
        val carpeta = listOf("1" to "FF7.m3u", "2" to "FF7 (Disc 1).cue", "3" to "FF7 (Disc 1).bin", "4" to "FF7 (Disc 2).cue", "5" to "FF7 (Disc 2).bin", "6" to "Crash.chd")
        val l = Escaneo.quitarDiscos(Escaneo.quitarPistas(carpeta), listOf("FF7 (Disc 1).cue", "ff7 (disc 2).CUE"))
        assertEquals(listOf("FF7.m3u", "Crash.chd"), l.map { it.second })
        assertEquals(carpeta, Escaneo.quitarDiscos(carpeta, emptyList()))
    }

    @Test fun elJuegoDeUnZip() {
        val psx = Nucleos.para("psx")!!.extensiones
        assertEquals("Juego.cue", Discos.principal(listOf("Juego (Track 1).bin", "Juego.cue", "Juego (Track 2).bin"), psx))
        assertEquals("Juego.m3u", Discos.principal(listOf("Juego (Disc 1).cue", "Juego.m3u"), psx))
        assertEquals("Zelda.sfc", Discos.principal(listOf("leeme.txt", "Zelda.sfc"), Nucleos.para("snes")!!.extensiones))
        assertNull(Discos.principal(listOf("leeme.txt"), Nucleos.para("snes")!!.extensiones))
    }

    @Test fun laCarpetaDeUnDocumento() {
        assertEquals("primary:Roms/psx", Discos.padre("primary:Roms/psx/Juego.cue"))
        assertEquals("primary:", Discos.padre("primary:Juego.cue"))
        assertEquals("1234-ABCD:ROMs", Discos.padre("1234-ABCD:ROMs/a.cue"))
        assertNull(Discos.padre("msf:1234"))
        assertNull(Discos.padre("1234"))
    }

    // ------------------------------------------------------------------ el emulador automático

    private fun eleccion(etiqueta: String, ok: Boolean, plantilla: String = "%EMULATOR_X% %DATA%=%ROMSAF%") = Eleccion(etiqueta, plantilla,
        if (ok) Resultado.Ok(Especificacion("p", null, null, null, null, emptyList(), emptyList(), false, false, null, etiqueta)) else Resultado.Falta("no"))
    private val hiedra = eleccion(Lanzar.HIEDRA, true, Lanzar.PLANTILLA_HIEDRA)

    @Test fun automaticoNoTeQuitaTuEmulador() {
        val ppsspp = eleccion("PPSSPP (Standalone)", true)
        val retroarch = eleccion("PPSSPP", true, "%EMULATOR_RETROARCH% %EXTRA_ROM%=%ROM%")
        // Con PPSSPP instalado sigue siendo PPSSPP; sin él (o solo con RetroArch), Hiedra
        assertEquals("PPSSPP (Standalone)", Lanzar.elegirDe(listOf(hiedra, retroarch, ppsspp), "", "psp")?.etiqueta)
        assertTrue(Lanzar.esInterno(Lanzar.elegirDe(listOf(hiedra, retroarch, eleccion("PPSSPP (Standalone)", false)), "", "psp")))
        // En GBA, Hiedra aunque tengas otro (como desde la 0.4)
        assertTrue(Lanzar.esInterno(Lanzar.elegirDe(listOf(hiedra, eleccion("My Boy! (Standalone)", true)), "", "gba")))
        // Lo que eliges tú manda siempre
        assertTrue(Lanzar.esInterno(Lanzar.elegirDe(listOf(hiedra, ppsspp), Lanzar.HIEDRA, "psp")))
        assertEquals("PPSSPP (Standalone)", Lanzar.elegirDe(listOf(hiedra, ppsspp), "PPSSPP (Standalone)", "gba")?.etiqueta)
    }
}

/** Los mandos táctiles de cada consola (RadialGamePad necesita las clases de Android: con Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MandoTactilTest {
    @Test fun cadaBotonTactilVaAUnBotonDelNucleo() {
        for (pad in Mandos.Pad.entries) for (lado in MandoTactil.Lado.entries) {
            val c = MandoTactil.disposicion(pad, lado)
            assertEquals(12, c.sockets)
            for (id in MandoTactil.botones(c)) {
                val d = MandoTactil.destinoDe(id)
                assertNotNull("$pad $lado: el botón $id no va a ningún botón del RetroPad", d)
                assertTrue("$pad $lado: ${(d as Mandos.Destino.Pad).boton} no lo usa el núcleo", d.boton in pad.botones)
            }
        }
    }

    @Test fun cadaBotonDelNucleoEstaEnElTactil() {
        // Lo que usa cada consola se puede pulsar en la pantalla (salvo la tapa y el micrófono de la DS, que también están)
        for (pad in Mandos.Pad.entries) {
            val tactil = MandoTactil.Lado.entries.flatMap { MandoTactil.botones(MandoTactil.disposicion(pad, it)) }.mapNotNull { (MandoTactil.destinoDe(it) as? Mandos.Destino.Pad)?.boton }.toSet()
            val sinCruceta = pad.botones - setOf(Mandos.Boton.ARRIBA, Mandos.Boton.ABAJO, Mandos.Boton.IZQUIERDA, Mandos.Boton.DERECHA)
            assertEquals("$pad", sinCruceta, tactil)
        }
    }

    @Test fun losDeLemuroid() {
        // N64: Z, B y A en el dial derecho (L2, Y y B del RetroPad); PlayStation: ○ △ □ ✕ (A, X, Y, B)
        val n64 = MandoTactil.disposicion(Mandos.Pad.N64, MandoTactil.Lado.DERECHO).primaryDial as com.swordfish.radialgamepad.library.config.PrimaryDialConfig.PrimaryButtons
        assertEquals(listOf(KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B), n64.dials.map { it.id })
        assertEquals(listOf("Z", "B", "A"), n64.dials.map { it.label })
        val psx = MandoTactil.disposicion(Mandos.Pad.PSX, MandoTactil.Lado.DERECHO).primaryDial as com.swordfish.radialgamepad.library.config.PrimaryDialConfig.PrimaryButtons
        assertEquals(listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B), psx.dials.map { it.id })
        // Los sticks y los botones C llevan sus ids
        val ids = MandoTactil.disposicion(Mandos.Pad.N64, MandoTactil.Lado.IZQUIERDO).secondaryDials.filterIsInstance<com.swordfish.radialgamepad.library.config.SecondaryDialConfig.Stick>().map { it.id }
        assertEquals(listOf(MandoTactil.ID_STICK_IZQ), ids)
        // La palanca de arcade va a la cruceta y al stick
        assertEquals(MandoTactil.ID_CRUZ_Y_STICK, ((MandoTactil.disposicion(Mandos.Pad.ARCADE6, MandoTactil.Lado.IZQUIERDO).primaryDial as com.swordfish.radialgamepad.library.config.PrimaryDialConfig.Cross).crossConfig.id))
    }

    @Test fun conElTemaYLaVibracion() {
        val c = MandoTactil.configuracion("psx", MandoTactil.Lado.IZQUIERDO, 0.6f, android.graphics.Color.RED, vibracion = false)
        assertEquals(com.swordfish.radialgamepad.library.haptics.HapticConfig.OFF, c.haptic)
        // Android 14 (el de las pruebas): coordenadas de la vista, como Lemuroid desde Android 13
        assertFalse(c.preferScreenTouchCoordinates)
    }
}
