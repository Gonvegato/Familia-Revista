package com.gonzalo.hiedra.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import com.gonzalo.familia.pc.TextoAireado
import com.gonzalo.familia.pc.revista.Paper
import com.gonzalo.familia.pc.revista.TemaFamilia
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume
import org.junit.Test
import java.io.File

/** Capturas de Hiedra del ordenador. Solo con `-Dcapturas=<carpeta>`. */
class CapturasTest {
    /**
     * La sala, a 1920×1080: un Steam de mentira con juegos de verdad (las portadas y los fondos llegan de Steam), ROMs en una carpeta (las
     * portadas de libretro) y juegos de la cuenta (uno con su ROM aquí, otro de Switch que no se juega en este PC).
     */
    private fun capturasSala(dir: File, tmp: File, otra: com.gonzalo.familia.nucleo.Familia) {
        val steam = File(tmp, "Steam/steamapps").also { it.mkdirs() }
        listOf(1145360 to "Hades", 367520 to "Hollow Knight", 504230 to "Celeste", 753640 to "Outer Wilds", 1086940 to "Baldur's Gate 3", 1245620 to "ELDEN RING",
            413150 to "Stardew Valley", 1091500 to "Cyberpunk 2077", 620 to "Portal 2", 632470 to "Disco Elysium", 2379780 to "Balatro", 1794680 to "Vampire Survivors")
            .forEach { (id, n) -> File(steam, "appmanifest_$id.acf").writeText("\"AppState\" { \"appid\" \"$id\" \"name\" \"$n\" \"installdir\" \"$n\" \"StateFlags\" \"4\" }") }
        val roms = File(tmp, "Roms").also { it.mkdirs() }
        listOf("gba/Legend of Zelda, The - The Minish Cap (USA).gba", "gba/Pokemon - Emerald Version (USA, Europe).gba", "gba/Advance Wars (USA).gba",
            "snes/Super Mario World (USA).sfc", "snes/Chrono Trigger (USA).sfc", "psx/Crash Bandicoot (USA).cue")
            .forEach { File(roms, it).also { f -> f.parentFile.mkdirs(); f.writeText("x") } }
        Pc.paraPruebas(tmp, listOf(File(tmp, "Steam")))
        Emulacion.anadeCarpeta(roms)
        Emulacion.instaladoPruebas = { e -> if (e.id == "retroarch" || e.id == "duckstation") Instalado.Binario("/usr/bin/${e.binarios.first()}") else null }
        Emulacion.escaneaTodo()
        // (los de las capturas de la ventana, fuera: salvo el de Switch, que no se juega en este PC)
        listOf("j_000000000001", "j_000000000003", "j_000000000004", "j_000000000005").forEach { otra.coleccion("juegos").borra(it) }
        val an = System.currentTimeMillis() - 200 * 86_400_000L
        // En la cuenta: Hades jugándose (abierto antes con Atrio en el PC), la ROM del Zelda del móvil (la misma que hay aquí), Celeste pasado
        val hoy = java.time.LocalDate.now()
        otra.coleccion("juegos").pon("j_00000000000a", mapOf("t" to "Hades", "pl" to "PC", "estado" to "en_curso", "fav" to true, "an" to an, "ul" to System.currentTimeMillis() - 3_600_000L, "menu" to "pc",
            "ln" to listOf(mapOf("tp" to "PC", "pck" to "steam:1145360", "pcd" to "pc-atrio"))))
        otra.coleccion("tiempo").pon("j_00000000000a~steam", mapOf("ref" to "j_00000000000a", "fuente" to "steam", "total" to 41 * 3600_000L, "d$hoy" to 2 * 3600_000L))
        otra.coleccion("juegos").pon("j_00000000000b", mapOf("t" to "The Legend of Zelda: The Minish Cap", "estado" to "en_curso", "an" to an, "ul" to System.currentTimeMillis() - 86_400_000L, "menu" to "emulacion",
            "ln" to listOf(mapOf("tp" to "ROM", "s" to "gba", "a" to "Legend of Zelda, The - The Minish Cap (USA)"))))
        otra.coleccion("tiempo").pon("j_00000000000b~hiedra-movil", mapOf("ref" to "j_00000000000b", "fuente" to "hiedra-movil", "total" to 7 * 3600_000L))
        otra.coleccion("juegos").pon("j_00000000000c", mapOf("t" to "Celeste", "pl" to "PC", "estado" to "terminado", "fav" to true, "an" to an, "fin" to System.currentTimeMillis() - 30 * 86_400_000L, "menu" to "pc",
            "ln" to listOf(mapOf("tp" to "PC", "pck" to "steam:504230", "pcd" to "pc-atrio"))))
        check(otra.sincroniza().ok); check(LaFamilia.sincronizaYa().ok)
        Pc.lista()
        Entrada.con = ConQue.MANDO
        fun sala(nombre: String, prepara: (EstadoSala, FocoSala) -> Unit = { _, _ -> }, mueve: (androidx.compose.ui.focus.FocusManager) -> Unit = { }, oscura: Boolean = true) {
            AjustesSala.ponOscura(oscura)
            val est = EstadoSala(); val foco = FocoSala()
            prepara(est, foco)
            var fm: androidx.compose.ui.focus.FocusManager? = null
            val escena = ImageComposeScene(1920, 1080, Density(1f)) {
                TemaFamilia { TextoAireado { fm = androidx.compose.ui.platform.LocalFocusManager.current; PantallaSala(est, foco) {} } }
            }
            var t = 0L
            fun frames(n: Int) { repeat(n) { Thread.sleep(90); t += 100_000_000L; escena.render(t) } }
            frames(12)
            fm?.let { mueve(it) }
            frames(60)   // (las imágenes llegan de internet)
            File(dir, "$nombre.png").writeBytes(escena.render(t + 1).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        val abajo = androidx.compose.ui.focus.FocusDirection.Down
        val derecha = androidx.compose.ui.focus.FocusDirection.Right
        sala("sala-inicio")
        sala("sala-inicio-fila", mueve = { fm -> fm.moveFocus(abajo); fm.moveFocus(derecha); fm.moveFocus(derecha) })
        sala("sala-biblioteca", { e, _ -> e.seccion = Seccion.BIBLIOTECA })
        sala("sala-pc", { e, _ -> e.seccion = Seccion.PC })
        sala("sala-juego", { e, _ -> e.pila += "j_00000000000a" })
        sala("sala-juego-rom", { e, _ -> e.pila += "j_00000000000b" }, oscura = false)
        sala("sala-buscar", { e, f -> e.buscando = true; e.consulta = "ce"; f.abre("buscar"); f.pedido = "k:E" })
        sala("sala-opciones", { e, f -> e.opciones = "j_00000000000a"; f.abre("opciones"); f.pedido = "op:0" })
        sala("sala-menu", { e, f -> e.menu = true; f.abre("menu"); f.pedido = "op:1" })
        AjustesSala.ponOscura(true)
        Entrada.con = ConQue.RATON
    }

    @Test fun capturas() {
        val dir = System.getProperty("capturas")?.let { File(it) }
        Assume.assumeTrue("Solo con -Dcapturas=<carpeta>", dir != null)
        dir!!.mkdirs()
        Emulador.limpia(); Emulador.cuenta("gonzalo")
        val otra = Emulador.dispositivo("gonzalo")
        val hoy = java.time.LocalDate.now()
        fun juego(id: String, t: String, pl: String, estado: String, ms: Long = 0) {
            otra.coleccion("juegos").pon(id, mapOf("t" to t, "pl" to pl, "estado" to estado, "an" to System.currentTimeMillis(), "ul" to System.currentTimeMillis(), "menu" to if (pl == "PC") "pc" else "emulacion"))
            if (ms > 0) otra.coleccion("tiempo").pon("$id~hiedra-movil", mapOf("ref" to id, "fuente" to "hiedra-movil", "total" to ms, "veces" to 3L, "d$hoy" to ms / 3))
        }
        juego("j_000000000001", "Hades", "PC", "en_curso", 31 * 3600_000L)
        juego("j_000000000002", "Hollow Knight", "Switch", "en_curso", 12 * 3600_000L)
        juego("j_000000000003", "The Legend of Zelda: The Minish Cap", "GBA", "en_curso", 5 * 3600_000L)
        juego("j_000000000004", "Celeste", "PC", "pendiente")
        juego("j_000000000005", "Outer Wilds", "PC", "terminado", 22 * 3600_000L)
        check(otra.sincroniza().ok)
        val tmp = File(System.getProperty("java.io.tmpdir"), "hiedra-capturas-" + System.nanoTime()).also { it.mkdirs() }
        LaFamilia.reiniciaParaPruebas(); LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg; LaFamilia.dirPruebas = tmp; LaFamilia.llaveroPruebas = LlaveroMemoria()
        val aqui = LaFamilia.inicia("hiedra").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Juegos.dispositivo = { "hiedra-linux-pc1" }; Pc.paraPruebas(tmp, emptyList())
        listOf("juegos", "tiempo", "ajustes").forEach { LaFamilia.coleccion(it) }
        check(aqui.sincroniza().ok)
        fun captura(nombre: String, p: Pantalla, oscuro: Boolean = false) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            val escena = ImageComposeScene(1440, 900, Density(1f)) { TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { AppHiedra(p) } } } }
            var img = escena.render(0)
            repeat(5) { i -> Thread.sleep(120); img = escena.render((i + 1) * 500_000_000L) }
            File(dir, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        captura("hiedra-portada", Pantalla.Portada)
        captura("hiedra-ficha", Pantalla.Ficha("j_000000000001", Pantalla.Portada), oscuro = true)
        capturasSala(dir, tmp, otra)
        captura("hiedra-ordenador", Pantalla.EsteOrdenador)
        LaFamilia.reiniciaParaPruebas()
        Pc.paraPruebas(null, null); Emulacion.instaladoPruebas = null
    }
}
