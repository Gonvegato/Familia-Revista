package com.gonzalo.cobalto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    /** Lo que pide abrir un widget (se consume al abrirlo). */
    private var pedida by mutableStateOf<Peticion?>(null)

    private fun peticionDe(i: Intent?): Peticion? {
        val obra = i?.getStringExtra("obra") ?: return null
        return Peticion(obra, i.getStringExtra("escena"), i.getBooleanExtra("sprint", false))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent); pedida = peticionDe(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        iniciar(this)
        Actualizador.init(this)
        pedida = peticionDe(intent)
        setContent {
            CobaltoTheme {
                // Iconos de la barra de estado claros u oscuros según el fondo
                SideEffect {
                    val oscuro = Skin.tones.darkBg
                    enableEdgeToEdge(
                        statusBarStyle = if (oscuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = if (oscuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    )
                }
                CobaltoApp(pedida) { pedida = null }
            }
        }
    }

    /** Al volver a la app: colores de Atrio al día y la cuenta en directo. */
    override fun onStart() {
        super.onStart()
        val c = applicationContext
        kotlin.concurrent.thread { TemaAtrio.refrescar(c) }
        SincroP.escuchar(c)
    }

    override fun onStop() {
        super.onStop()
        SincroP.parar()
        // Lo escrito, a disco (y los widgets, al día)
        runCatching { Escritos.get(applicationContext).vacia() }
        Widgets.pedir(applicationContext)
        // Android congela la app al poco de dejar de verse: lo que quede por subir (y el capítulo diferido por los 20 s de calma) lo sube
        // un trabajo de fondo, que corre aunque la app esté congelada. Si la app ya lo subió antes, el trabajo no encuentra nada que hacer.
        if (Cuenta.vinculada) NubeTarea.encolar(applicationContext, Publicar.QUIETO + 5_000L)
    }
}
