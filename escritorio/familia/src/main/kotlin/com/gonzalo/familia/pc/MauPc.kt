package com.gonzalo.familia.pc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.gonzalo.mau.Guardado
import com.gonzalo.mau.Lugar
import com.gonzalo.mau.ui.Mau
import com.gonzalo.mau.ui.MauCasa
import java.io.File
import java.util.Properties

/**
 * Mau en el ordenador: lo pone la ventana de cada app de la familia (appDeLaFamilia), encima de todo. Las pantallas dicen dónde está
 * con [MauLugar].
 */
object MauPc {
    /** Lo que se le pasa a una hermana al abrirla desde el menú de Mau: Mau llega con ella. */
    const val ARG_LLEGA = "--mau-llega"
    /** La clave de la ventana (una por app). */
    const val VENTANA = "ventana"

    private class Casa(override val app: String) : MauCasa {
        override val ordenador = true
        override val sinAnimaciones = false
        override val guardado: Guardado = object : Guardado {
            private val archivo = File(Sitio.de(app), "mau.properties")
            private val p = Properties().apply { runCatching { archivo.inputStream().use { load(it) } } }
            @Synchronized override fun lee(clave: String): String? = p.getProperty(clave)
            @Synchronized override fun guarda(clave: String, valor: String?) {
                if (valor == null) p.remove(clave) else p.setProperty(clave, valor)
                runCatching {
                    val tmp = File(archivo.parentFile, "mau.properties.parte")
                    tmp.outputStream().use { p.store(it, "Mau") }
                    if (!tmp.renameTo(archivo)) { archivo.delete(); tmp.renameTo(archivo) }
                }
            }
        }
        override fun instalada(app: String) = app == this.app || HermanasPc.esta(app)
        override fun abre(app: String) = HermanasPc.abre(app, listOf(ARG_LLEGA))
        override fun ronronea() {}
        override fun tic() {}
        override fun enPrincipal(f: () -> Unit) { if (java.awt.EventQueue.isDispatchThread()) f() else java.awt.EventQueue.invokeLater(f) }
    }

    fun inicia(app: String) = Mau.inicia(Casa(app))

    /** Quita de [args] lo de Mau (y si venía, Mau llega). */
    fun miraSiLlega(args: List<String>): List<String> {
        if (ARG_LLEGA in args) Mau.llega = true
        return args.filter { it != ARG_LLEGA }
    }
}

/** Mientras esta pantalla se ve, Mau está en [lugar] (leyendo, escribiendo, viendo…). */
@Composable
fun MauLugar(lugar: Lugar) {
    DisposableEffect(lugar) {
        val f = Mau.empuja(MauPc.VENTANA, lugar)
        onDispose { Mau.quita(MauPc.VENTANA, f) }
    }
}

/** Mientras esta pantalla se ve, Mau no sale (la sala de la tele, un juego). */
@Composable
fun MauFuera() {
    DisposableEffect(Unit) {
        Mau.sal(MauPc.VENTANA)
        onDispose { Mau.vuelveA(MauPc.VENTANA) }
    }
}
