package com.gonzalo.hiedra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.gonzalo.familia.MauMovil
import com.gonzalo.mau.Frases
import com.gonzalo.mau.Lugar
import com.gonzalo.mau.ui.Mau

class MainActivity : ComponentActivity() {
    /** La ficha que pide abrir un widget (se consume al abrirla). */
    private var fichaPedida by androidx.compose.runtime.mutableStateOf<String?>(null)
    /** Con la ficha: jugar directamente (un juego del PC desde un widget o un acceso directo: se elige dónde). */
    private var jugarPedido by androidx.compose.runtime.mutableStateOf(false)
    /** La sección que pide un aviso u otra app (`go`: «jugando», «pendientes», «sistemas»…). */
    private var irPedido by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent); fichaPedida = intent.getStringExtra("ficha"); jugarPedido = intent.getBooleanExtra("jugar", false); irPedido = intent.getStringExtra("go")
        MauMovil.miraSiLlega(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        iniciar(this)
        Actualizador.init(this)
        fichaPedida = intent?.getStringExtra("ficha"); jugarPedido = intent?.getBooleanExtra("jugar", false) == true; irPedido = intent?.getStringExtra("go")
        setContent {
            HiedraTheme {
                // Iconos de la barra de estado claros u oscuros según el fondo
                SideEffect {
                    val oscuro = Skin.tones.darkBg
                    enableEdgeToEdge(
                        statusBarStyle = if (oscuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = if (oscuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    )
                }
                HiedraApp(fichaPedida, jugarPedido, { fichaPedida = null; jugarPedido = false }, irPedido) { irPedido = null }
            }
        }
        // Mau, con su ovillo (no en el jugador ni en los juegos web: ahí estorbaría)
        MauMovil.pon(this, "hiedra", Lugar.JUGANDO)
    }

    /**
     * Ya delante (después de [onStart], que cierra la sesión): si vienes de jugar un rato, Mau lo comenta. Aquí y no antes: su bocadillo
     * dura unos segundos contados desde que lo dice, y con otra app delante no se vería.
     */
    override fun onResume() {
        super.onResume()
        Sesion.paraMau(this)?.let { j ->
            Mau.toque()   // (volver de jugar es hacer algo: si se había dormido, se despierta)
            Mau.di(Frases.hasJugado(j.minutos, j.titulo), importante = true)
        }
    }

    /** Volver a Hiedra después de jugar: se apunta el tiempo que has estado fuera. */
    override fun onStart() {
        super.onStart(); Sesion.cerrar(this)
        // Los colores de Atrio pueden haber cambiado mientras estabas en ella: se vuelven a leer
        val c = applicationContext
        kotlin.concurrent.thread { TemaAtrio.refrescar(c) }
        // Tu cuenta de Atrio, en directo mientras Hiedra se ve
        SincroP.escuchar(c)
        // Tu PC en directo (Atrio del PC)
        Suite.start(c)
        SubirRomsWorker.programar(c)
    }

    override fun onStop() {
        super.onStop(); SincroP.parar(); Suite.stop()
        // Android congela la app al poco de dejar de verse: si quedaba algo por subir, lo sube un trabajo de fondo (que corre igual)
        if (Cuenta.vinculada && SincroP.hayPendiente()) SubidaWorker.encolar(applicationContext)
    }
}
