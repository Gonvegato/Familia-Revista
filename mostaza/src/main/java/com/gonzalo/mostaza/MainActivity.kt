package com.gonzalo.mostaza

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    /** La ficha que pide abrir otra app o un widget («ficha» = la ref del título: Azabache la manda con «Ver en Mostaza»). */
    private var fichaPedida by mutableStateOf<String?>(null)
    /** La sección que pide abrir un widget («seccion» = el nombre del [Destino]: la cabecera de «Pendientes»). */
    private var seccionPedida by mutableStateOf<String?>(null)
    /** Un addon que instalar (el botón «Install» de su página: stremio://…/manifest.json). */
    private var addonPedido by mutableStateOf<String?>(null)

    private fun lee(i: Intent?) {
        fichaPedida = i?.getStringExtra("ficha")
        seccionPedida = i?.getStringExtra("seccion")
        addonPedido = i?.data?.takeIf { it.scheme.equals("stremio", true) }?.toString()
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); lee(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        iniciar(this)
        Actualizador.init(this)
        lee(intent)
        setContent {
            MostazaTheme {
                SideEffect {
                    val oscuro = Skin.tones.darkBg
                    enableEdgeToEdge(
                        statusBarStyle = if (oscuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = if (oscuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    )
                }
                // En la tele (o en una tablet con un mando, si lo pides en Ajustes), la sala; si no, la Mostaza de siempre
                Sala.enTablet
                val sala = Sala.activa(this)
                LaunchedEffect(sala) { pantallaCompleta(sala) }
                if (sala) SalaApp(fichaPedida, addonPedido, seccionPedida, alAtender = { fichaPedida = null; addonPedido = null; seccionPedida = null })
                else MostazaApp(fichaPedida, addonPedido, seccionPedida) { fichaPedida = null; addonPedido = null; seccionPedida = null }
            }
        }
    }

    /** La sala va a pantalla completa (en una tablet, sin las barras del sistema; en la tele no las hay); fuera de ella, como siempre. */
    private fun pantallaCompleta(si: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (si) { c.hide(WindowInsetsCompat.Type.systemBars()); c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        else c.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onStart() {
        super.onStart()
        val c = applicationContext
        // Los colores de Azabache pueden haber cambiado mientras estabas en ella
        kotlin.concurrent.thread { TemaAtrio.refrescar(c) }
        SincroP.iniciar(c)
        // Lo que entró sin código de IMDb (de Azabache): se busca, sin molestar
        kotlin.concurrent.thread { runCatching { MigraAzabache.completaImdb(c) } }
        // Los próximos capítulos de tus series (el widget), si toca
        Proximos.alDia(c)
    }
}
