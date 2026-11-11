package com.gonzalo.ammit

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * El botón de la barra de arriba: un toque enciende Ammit (si está todo listo: pantalla de inicio y vigilante) y te lleva a su pantalla
 * de inicio; otro toque la apaga y vuelves a la de siempre. Si falta algo, abre los pasos ([AjustesActivity]).
 */
class BotonAmmit : TileService() {
    override fun onStartListening() { Ammit.cargar(this); pinta() }

    override fun onClick() {
        Ammit.cargar(this)
        val encender = !Ammit.encendida
        if (encender && !(Ammit.esInicio(this) && Ammit.vigiaActivo(this))) {
            abrir(Intent(this, AjustesActivity::class.java).putExtra(AjustesActivity.EXTRA_FALTA, true))
            return
        }
        Ammit.encender(this, encender)
        pinta()
        // A la pantalla de inicio: encendida, la de Ammit; apagada, Ammit pasa sola a la de siempre
        abrir(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    private fun abrir(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(i)
        }
    }

    private fun pinta() {
        val t = qsTile ?: return
        t.state = if (Ammit.encendida) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Ammit"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = if (Ammit.encendida) "Solo la familia" else "Apagado"
        t.contentDescription = if (Ammit.encendida) "Ammit: solo la familia" else "Ammit: apagado"
        t.updateTile()
    }

    companion object {
        /** Que el botón se repinte (al encender o apagar desde la app). */
        fun avisar(c: Context) = runCatching { requestListeningState(c, android.content.ComponentName(c, BotonAmmit::class.java)) }
    }
}
