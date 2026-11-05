package com.gonzalo.familia

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Los trabajos de fondo de la cuenta: subir lo pendiente en cuanto haya red (aunque cierres la app o Android la congele) y,
 * cada hora, bajar lo nuevo (para que los widgets enseñen lo último hecho en otra app u otro dispositivo).
 */
class SubidaFamilia(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val fam = LaFamilia.inicia(applicationContext)
        if (!fam.sesion.dentro) runCatching { Acceso.pideAHermanas(applicationContext) }
        if (!fam.sesion.dentro) return@withContext Result.success()
        // Para que los widgets tengan lo de todas las colecciones que usa la app, se abren las que ya se conocen
        val inf = fam.sincroniza()
        if (inf.ok || fam.pendientes == 0) Result.success() else Result.retry()
    }

    companion object {
        private const val SUBIDA = "familia_subida"
        private const val PERIODICA = "familia_periodica"
        private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun encola(ctx: Context) {
            runCatching {
                WorkManager.getInstance(ctx).enqueueUniqueWork(SUBIDA, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<SubidaFamilia>().setConstraints(conRed).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
            }
        }

        fun periodica(ctx: Context) {
            runCatching {
                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODICA, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<SubidaFamilia>(1, TimeUnit.HOURS).setConstraints(conRed).build())
            }
        }
    }
}
