package com.gonzalo.ptah

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Al poner un widget de Ptah en la pantalla de inicio (y al tocar uno que no tiene diseño, o al reconfigurarlo desde el lanzador): eliges
 * cuál de tus diseños va en él, o haces uno (de una plantilla o desde cero). Hasta que eliges, el widget no se pone.
 */
class ConfigWidgetActivity : ComponentActivity() {
    companion object { const val EXTRA_CAMBIAR = "cambiar" }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Si se sale sin elegir, el lanzador no pone el widget
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        iniciar(this)
        // Si viene de «Ponerlo en la pantalla de inicio» (y el lanzador abre esto igualmente), ya se sabe qué diseño va
        AlFijar.pendiente(this)?.let { d -> Disenos.de(this, d)?.let { AlFijar.pendiente(this, null); elige(id, it); return } }
        setContent {
            PtahTheme {
                BarrasDelSistema(this)
                PtahApp(eligiendo = true, alElegir = { d -> elige(id, d) })
            }
        }
    }

    private fun elige(id: Int, d: Diseno) {
        Widgets.asigna(this, id, d.id)
        Widgets.pintar(this, intArrayOf(id), null)
        Refresco.programa(this)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
