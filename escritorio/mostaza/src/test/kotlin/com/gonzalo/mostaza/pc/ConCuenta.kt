package com.gonzalo.mostaza.pc

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Lo común de las pruebas con la cuenta de verdad (el emulador oficial de Firebase con las reglas de `nube/reglas.json`), como en el móvil:
 * Mostaza del ordenador dentro de la cuenta «gonzalo» y [otro], otro dispositivo de la misma cuenta (el móvil, escribiendo con su formato).
 */
abstract class ConCuenta {
    protected lateinit var otro: Familia
    protected var uid = ""
    protected lateinit var dir: File

    protected fun espera(que: String, segundos: Int = 10, como: () -> String = { "" }, cond: () -> Boolean) {
        val hasta = System.currentTimeMillis() + segundos * 1000L
        while (System.currentTimeMillis() < hasta) { if (cond()) return; Thread.sleep(50) }
        assertTrue(que + " " + como(), cond())
    }

    protected fun entra(usuario: String = "gonzalo") {
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg
        dir = kotlin.io.path.createTempDirectory("mostaza-pruebas").toFile()
        LaFamilia.dirPruebas = dir
        // (todo lo de este «ordenador» en la carpeta de la prueba: la caché de imágenes, el número de dispositivo…)
        System.setProperty("familia.dir", File(dir, "familia").absolutePath)
        LaFamilia.llaveroPruebas = LlaveroMemoria()
        Local.dirPruebas = File(dir, "local"); Local.olvidaParaPruebas()
        MemoriaPistas.archivo = File(dir, "pistas.txt"); MemoriaPistas.olvidaParaPruebas()
        Biblioteca.olvidaParaPruebas(); Biblioteca.dispositivoPruebas = "mostaza-linux-pruebas"; Biblioteca.sinPortadas = true
        Addons.olvidaParaPruebas(); Addons.http = SoloLocal
        Tmdb.http = SoloLocal
        Torrents.apagado = true
        LaFamilia.inicia("mostaza").also { it.automatico = false }
        LaFamilia.entrar(usuario, "secreto1")
        listOf("titulos", "progreso", "tiempo", "ajustes").forEach { LaFamilia.coleccion(it) }
    }

    /** En las pruebas no se sale a internet: solo los servidores de mentira de este ordenador. */
    object SoloLocal : Http {
        override fun get(url: String, cabeceras: Map<String, String>): String =
            if (url.startsWith("http://127.0.0.1")) HttpJava(3_000).get(url, cabeceras) else throw java.io.IOException("Sin internet en las pruebas")
    }

    protected fun preparaCuenta(colecciones: List<String> = listOf("titulos", "progreso", "tiempo", "ajustes", "azabache")) {
        Emulador.limpia()
        uid = Emulador.cuenta("gonzalo")
        entra()
        otro = Emulador.dispositivo("gonzalo")
        colecciones.forEach { otro.coleccion(it) }
    }

    protected fun sincroniza() {
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok)
    }

    protected fun suelta() {
        LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null; LaFamilia.dirPruebas = null
        System.clearProperty("familia.dir")
        Biblioteca.olvidaParaPruebas(); Addons.olvidaParaPruebas(); Local.dirPruebas = null; Local.olvidaParaPruebas()
        runCatching { dir.deleteRecursively() }
    }
}
