package ir.tenderwatch.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.tenderwatch.data.Prefs
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val r = SyncRunner.run(applicationContext, forceContracts = false)
        Notifier.notifyNew(applicationContext, r)
        // Both sources failing usually means no usable connection; let WorkManager back off and retry.
        return if (r.errors.size >= 2) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "periodic-sync"

        fun schedule(context: Context) {
            val prefs = Prefs(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<SyncWorker>(prefs.intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
