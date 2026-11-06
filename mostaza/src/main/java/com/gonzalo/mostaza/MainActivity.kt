package com.gonzalo.mostaza

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    /** La ficha que pide abrir otra app («ficha» = la ref del título: Azabache la manda con «Ver en Mostaza»). */
    private var fichaPedida by mutableStateOf<String?>(null)
    /** Un addon que instalar (el botón «Install» de su página: stremio://…/manifest.json). */
    private var addonPedido by mutableStateOf<String?>(null)

    private fun lee(i: Intent?) {
        fichaPedida = i?.getStringExtra("ficha")
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
                MostazaApp(fichaPedida, addonPedido) { fichaPedida = null; addonPedido = null }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val c = applicationContext
        // Los colores de Azabache pueden haber cambiado mientras estabas en ella
        kotlin.concurrent.thread { TemaAtrio.refrescar(c) }
        SincroP.iniciar(c)
        // Lo que entró sin código de IMDb (de Azabache): se busca, sin molestar
        kotlin.concurrent.thread { runCatching { MigraAzabache.completaImdb(c) } }
    }
}
