package com.gonzalo.cobre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    /** Lo que pide un widget (una página de la app). */
    private var pedido by androidx.compose.runtime.mutableStateOf<Pedido?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        iniciar(this)
        Actualizador.init(this)
        if (savedInstanceState == null) atiende(intent)
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
                CobreApp(pedido = pedido)
            }
        }
        // Como una tele: si lo eliges, al abrir Cobre se pone el último canal que viste
        if (savedInstanceState == null && AjustesDirecto.alAbrir(this)) kotlin.concurrent.thread { runCatching { ponUltimo(this) } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        atiende(intent)
    }

    /** Lo de los widgets: poner un canal de un grupo del fútbol ([Widgets.ACCION_CANAL]) o abrir una página ([Widgets.EXTRA_DESTINO]). */
    private fun atiende(i: android.content.Intent?) {
        i ?: return
        if (i.action == Widgets.ACCION_CANAL) {
            val g = i.getStringExtra("grupo").orEmpty(); val n = i.getIntExtra("n", 0)
            val c = applicationContext
            kotlin.concurrent.thread {
                val aviso = runCatching { Futbol.ponGrupo(c, g, n) }.getOrElse { "No se ha podido poner el canal." }
                if (aviso != null) runOnUiThread { android.widget.Toast.makeText(c, aviso, android.widget.Toast.LENGTH_LONG).show(); pedido = Pedido(Destino.AjustesFutbol) }
            }
        }
        i.getStringExtra(Widgets.EXTRA_DESTINO)?.let { d -> Destino.entries.firstOrNull { it.name == d }?.let { pedido = Pedido(it) } }
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
