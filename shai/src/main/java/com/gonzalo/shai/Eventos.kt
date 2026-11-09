package com.gonzalo.shai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Lo de entre partidos, de fondo: cada cuarto de hora (lo mínimo que deja Android), con red, se mira si han vuelto los lotes de los
 * eventos y si a alguno le ha llegado su hora; los que salen, con un aviso. Cuando no queda nada por llegar ni por salir, se para solo.
 */
object Eventos {
    private const val TRABAJO = "shai_eventos"
    private const val CANAL = "carrera"

    /** ¿Queda algo por llegar o por salir? */
    fun hayPendientes(): Boolean = Carreras.todas().any { u -> u.lotes.isNotEmpty() || Carreras.entradasDe(u.id, todas = true).any { !it.aplicada } }

    fun programa(ctx: Context) {
        runCatching {
            val wm = WorkManager.getInstance(ctx)
            if (hayPendientes()) wm.enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<EventosWorker>(15, TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
            else wm.cancelUniqueWork(TRABAJO)
        }
    }

    /** Una vuelta: lo que ha llegado y lo que sale (y su aviso). */
    fun vuelta(ctx: Context): List<Entrada> {
        runCatching { Motor.miraLotes() }
        val salen = runCatching { Motor.revela() }.getOrDefault(emptyList())
        salen.forEach { avisa(ctx, it) }
        return salen
    }

    private fun avisa(ctx: Context, e: Entrada) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CANAL, "Tu carrera", NotificationManager.IMPORTANCE_DEFAULT))
        val u = Carreras.de(e.universo)
        val abre = Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_CARRERA, e.universo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, e.id.hashCode(), abre, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, CANAL).setSmallIcon(R.drawable.ic_launcher_fcfeed_mono)
            .setContentTitle(e.titulo.ifBlank { "Ha pasado algo" })
            .setContentText(listOfNotNull(u?.nombre, e.decision?.let { "tienes que decidir" }).joinToString(" · ").ifBlank { e.texto.take(80) })
            .setStyle(Notification.BigTextStyle().bigText(e.texto + (e.decision?.let { "\n\n${it.texto}" } ?: "")))
            .setContentIntent(pi).setAutoCancel(true).build()
        runCatching { nm.notify(e.id.hashCode(), n) }
    }
}

class EventosWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        iniciar(applicationContext)
        Eventos.vuelta(applicationContext)
        Eventos.programa(applicationContext)
        return Result.success()
    }
}
