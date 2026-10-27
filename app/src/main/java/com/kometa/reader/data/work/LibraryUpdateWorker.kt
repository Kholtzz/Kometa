package com.kometa.reader.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kometa.reader.data.repository.CatalogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

object UpdateNotifier {
    private const val CHANNEL = "updates"

    fun notify(context: Context, totalNew: Int) {
        if (totalNew <= 0) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Новые главы", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.notify(
            1,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Обновление библиотеки")
                .setContentText("Новых глав: $totalNew")
                .setAutoCancel(true)
                .build()
        )
    }
}

@HiltWorker
class LibraryUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: CatalogRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val total = repo.checkNewChapters()
        UpdateNotifier.notify(applicationContext, total)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "library_update",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LibraryUpdateWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints(NetworkType.CONNECTED))
                    .build(),
            )
        }
    }
}
