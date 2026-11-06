package com.gonzalo.hiedra.pc

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import com.gonzalo.familia.pc.appDeLaFamilia
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.io.File
import javax.imageio.ImageIO

/**
 * Probar la sala de verdad: Hiedra abierta en una ventana (la de la familia, a pantalla completa con `--sala`), contra el emulador de
 * Firebase y con un Steam de mentira; un «mando» (el teclado, con java.awt.Robot) la recorre y se guardan capturas de la pantalla.
 * `./gradlew -PsoloEscritorio :escritorio:hiedra:probarSala -Psalida=<carpeta>` (con una pantalla: en un servidor, Xvfb).
 */
fun main(args: Array<String>) {
    val salida = File(args.firstOrNull() ?: "probar-sala").also { it.mkdirs() }
    val tmp = File(salida, "datos").also { it.deleteRecursively(); it.mkdirs() }
    System.setProperty("familia.dir", File(tmp, "familia").path)
    Emulador.limpia(); Emulador.cuenta("gonzalo")
    val otra = Emulador.dispositivo("gonzalo")
    otra.coleccion("juegos").pon("j_00000000000a", mapOf("t" to "Hades", "pl" to "PC", "estado" to "en_curso", "fav" to true, "an" to System.currentTimeMillis() - 90 * 86_400_000L,
        "ul" to System.currentTimeMillis() - 3_600_000L, "menu" to "pc", "ln" to listOf(mapOf("tp" to "PC", "pck" to "steam:1145360", "pcd" to "pc-atrio"))))
    otra.coleccion("tiempo").pon("j_00000000000a~steam", mapOf("ref" to "j_00000000000a", "fuente" to "steam", "total" to 41 * 3600_000L))
    check(otra.sincroniza().ok)
    val steam = File(tmp, "Steam/steamapps").also { it.mkdirs() }
    listOf(1145360 to "Hades", 367520 to "Hollow Knight", 504230 to "Celeste", 753640 to "Outer Wilds", 1086940 to "Baldur's Gate 3", 2379780 to "Balatro")
        .forEach { (id, n) -> File(steam, "appmanifest_$id.acf").writeText("\"AppState\" { \"appid\" \"$id\" \"name\" \"$n\" \"StateFlags\" \"4\" }") }
    Steam.raicesPruebas = listOf(File(tmp, "Steam")); Heroic.raicesPruebas = emptyList(); Lutris.salidaPruebas = ""
    LaFamilia.configPruebas = Emulador.cfg; LaFamilia.llaveroPruebas = LlaveroMemoria()

    val log = File(salida, "probar.txt").printWriter()
    fun apunta(s: String) { println(s); log.println(s); log.flush() }
    Thread({
        val r = Robot()
        val pantalla = Rectangle(Toolkit.getDefaultToolkit().screenSize)
        fun foto(n: String) { Thread.sleep(1500); ImageIO.write(r.createScreenCapture(pantalla), "png", File(salida, "$n.png")); apunta("foto $n · foco=${focoDePrueba?.actual} · completa=${com.gonzalo.familia.pc.VentanaFamilia.pantallaCompleta}") }
        fun tecla(k: Int, veces: Int = 1) = repeat(veces) { r.keyPress(k); Thread.sleep(40); r.keyRelease(k); Thread.sleep(260) }
        Thread.sleep(14_000)
        apunta("mando: ${Mando.estado} · mandos: ${Mando.mandos.size} · ventana: ${VentanaHiedra.ventana?.bounds}")
        foto("1-inicio")
        tecla(KeyEvent.VK_RIGHT, 2); foto("2-derecha")
        tecla(KeyEvent.VK_DOWN); foto("3-abajo")
        tecla(KeyEvent.VK_UP); tecla(KeyEvent.VK_LEFT, 2); tecla(KeyEvent.VK_ENTER); foto("4-pagina")
        tecla(KeyEvent.VK_SPACE); foto("5-opciones")
        tecla(KeyEvent.VK_ESCAPE); tecla(KeyEvent.VK_ESCAPE); tecla(KeyEvent.VK_E); foto("6-biblioteca")
        tecla(KeyEvent.VK_END); foto("7-letra")
        tecla(KeyEvent.VK_F); tecla(KeyEvent.VK_C); tecla(KeyEvent.VK_E); foto("8-buscar")
        tecla(KeyEvent.VK_ESCAPE); tecla(KeyEvent.VK_S); foto("9-menu")
        tecla(KeyEvent.VK_ESCAPE); tecla(KeyEvent.VK_F11); foto("10-ventana")
        log.close()
        VentanaHiedra.cerrar()
        Thread.sleep(4000); System.exit(0)
    }, "probar").apply { isDaemon = true; start() }
    appDeLaFamilia("hiedra", "Hiedra", icono(), alIniciar = { LaFamilia.entrar("gonzalo", "secreto1"); alIniciarHiedra() }, alCerrar = { alCerrarHiedra() }) { AppHiedra(sala = true) }
    System.exit(0)
}
