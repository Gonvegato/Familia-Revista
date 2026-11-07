package com.gonzalo.cobre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        iniciar(this)
        Actualizador.init(this)
        setContent {
            CobreTheme {
                SideEffect {
                    val t = android.graphics.Color.TRANSPARENT
                    val oscuro = Skin.tones.darkBg
                    enableEdgeToEdge(
                        statusBarStyle = if (oscuro) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
                        navigationBarStyle = if (oscuro) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                    )
                }
                CobreApp()
            }
        }
        // Como una tele: si lo eliges, al abrir Cobre se pone el último canal que viste
        if (savedInstanceState == null && AjustesDirecto.alAbrir(this)) kotlin.concurrent.thread { runCatching { ponUltimo(this) } }
    }

    override fun onStart() {
        super.onStart()
        val c = applicationContext
        // Los colores de Azabache pueden haber cambiado mientras estabas en ella
        kotlin.concurrent.thread { TemaAtrio.refrescar(c) }
        SincroP.iniciar(c)
        // Cada lista se pone al día si le toca (los canales una vez al día, la guía cada tantas horas)
        Tele.alAbrir()
    }
}

/** Pone el último canal que viste (bloquea: lee la lista del disco). */
fun ponUltimo(ctx: android.content.Context): Boolean {
    val u = MiCuenta.ultimo() ?: return false
    val l = Listas.por(u.lista) ?: return false
    val cg = Tele.cargada(l) ?: return false
    val c = cg.porClave[u.canal] ?: return false
    val grupo = Tele.visibles(cg, ctx, c.grupo).ifEmpty { listOf(c) }
    Reproducir.canal(ctx, l, grupo, c)
    return true
}
