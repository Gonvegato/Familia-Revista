package com.gonzalo.shai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    /** Lo que pide un aviso (abrir una carrera). */
    private var pedido by androidx.compose.runtime.mutableStateOf<Pedido?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        iniciar(this)
        Actualizador.init(this)
        if (savedInstanceState == null) atiende(intent)
        setContent { ShaiTheme { BarrasDelSistema(this); ShaiApp(pedido = pedido) } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        atiende(intent)
    }

    private fun atiende(i: android.content.Intent?) { i?.getStringExtra(EXTRA_CARRERA)?.let { pedido = Pedido(it) } }

    companion object { const val EXTRA_CARRERA = "carrera" }

    override fun onStart() {
        super.onStart()
        val c = applicationContext
        // Los colores de Jonsu pueden haber cambiado; y lo de entre partidos que haya llegado o le toque salir
        kotlin.concurrent.thread { TemaAtrio.refrescar(c); Eventos.vuelta(c); Eventos.programa(c) }
        SincroP.iniciar(c)
    }
}

/** Las barras del sistema (arriba y abajo), claras u oscuras según el papel. */
@androidx.compose.runtime.Composable
fun BarrasDelSistema(a: ComponentActivity) {
    SideEffect {
        val t = android.graphics.Color.TRANSPARENT
        val oscuro = Skin.tones.darkBg
        a.enableEdgeToEdge(
            statusBarStyle = if (oscuro) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
            navigationBarStyle = if (oscuro) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        )
    }
}
