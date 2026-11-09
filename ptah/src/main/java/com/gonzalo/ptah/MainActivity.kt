package com.gonzalo.ptah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    /** Lo que pide un widget (editar su diseño). */
    private var pedido by androidx.compose.runtime.mutableStateOf<Pedido?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        iniciar(this)
        Actualizador.init(this)
        if (savedInstanceState == null) atiende(intent)
        setContent {
            WidgetsTheme {
                BarrasDelSistema(this)
                WidgetsApp(pedido = pedido)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        atiende(intent)
    }

    private fun atiende(i: android.content.Intent?) {
        i?.getStringExtra(Widgets.EXTRA_EDITAR)?.let { pedido = Pedido(it) }
    }

    override fun onStart() {
        super.onStart()
        val c = applicationContext
        // La batería, el sitio y el tiempo, al día
        kotlin.concurrent.thread {
            Ubicacion.actualiza(c)
            Refresco.tiempo(c)
            Widgets.pedir(c)
            Refresco.programa(c)
        }
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
