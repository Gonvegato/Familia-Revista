package com.gonzalo.cobre

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * «Seguir sonando al salir»: el servicio en primer plano (Media3 [MediaSessionService]) que tiene la [MediaSession] del reproductor
 * mientras sales de él. El reproductor (el ExoPlayer) sigue siendo el de [ReproductorActivity] —no hay otro—: aquí solo se le pone la
 * sesión, con la que Android enseña la notificación (el nombre del canal o del partido, pausa y «Parar») y los controles de la pantalla de
 * bloqueo. Al volver a Nut, el vídeo vuelve a pintarse en el mismo reproductor, sin cortar. Lo arranca y lo para el reproductor ([pon], [quita]).
 */
@OptIn(UnstableApi::class)
class ReproduccionService : MediaSessionService() {
    private var sesion: MediaSession? = null

    companion object {
        const val PARAR = "cobre.PARAR"
        @Volatile private var jugador: Player? = null
        @Volatile private var activo: ReproduccionService? = null
        /** Lo que hace «Parar» (lo pone el reproductor: cerrarse). */
        @Volatile var alParar: (() -> Unit)? = null

        /** Arranca el servicio con [p] (o, si ya está, le cambia el reproductor: cada canal es un ExoPlayer nuevo). En el hilo principal. */
        fun pon(c: Context, p: Player) {
            jugador = p
            val s = activo
            if (s != null) s.sesion?.player = p
            else runCatching { c.startService(Intent(c, ReproduccionService::class.java)) }
        }

        /** Lo para (el reproductor no se suelta: es de la actividad). */
        fun quita(c: Context) {
            jugador = null
            activo?.let { s -> s.sesion?.run { release() }; s.sesion = null; s.stopSelf() }
            runCatching { c.stopService(Intent(c, ReproduccionService::class.java)) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val p = jugador ?: run { stopSelf(); return }
        val abrir = PendingIntent.getActivity(this, 7, Intent(this, ReproductorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val parar = CommandButton.Builder(CommandButton.ICON_STOP).setDisplayName("Parar").setSessionCommand(SessionCommand(PARAR, Bundle.EMPTY)).build()
        sesion = MediaSession.Builder(this, p).setId("nut").setSessionActivity(abrir).setMediaButtonPreferences(listOf(parar))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SessionCommand(PARAR, Bundle.EMPTY)).build())
                        .setMediaButtonPreferences(listOf(parar)).build()

                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == PARAR) { session.player.stop(); alParar?.invoke() ?: quita(this@ReproduccionService) }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }).build()
        activo = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = sesion

    /** Si quitas Nut de las recientes, se para todo. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        sesion?.player?.stop()
        alParar?.invoke()
        stopSelf()
    }

    override fun onDestroy() {
        sesion?.release(); sesion = null
        if (activo === this) activo = null
        super.onDestroy()
    }
}
