package io.github.raafiabir.acc

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val WORK_NAME = "acc_daily"

    /** Call after any change to the schedule settings. */
    fun apply(ctx: Context) {
        val prefs = Prefs(ctx)
        val wm = WorkManager.getInstance(ctx)
        if (!prefs.scheduleOn) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = ZonedDateTime.now()
        var next = now.withHour(prefs.hour).withMinute(prefs.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val request = PeriodicWorkRequestBuilder<CleanWorker>(prefs.intervalDays.toLong(), TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class CleanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Prefs(ctx).backend != Backend.Root && ShizukuBridge.state(ctx) != ShizukuState.NotInstalled) {
            ShizukuBridge.awaitBinder(4_000) // after a cold start the binder arrives late
        }
        if (Executor.resolve(ctx) == null) {
            val msg = "Skipped, no Shizuku, root or accessibility access"
            Prefs(ctx).saveLast(System.currentTimeMillis(), -1L, msg)
            Notifier.post(ctx, "Scheduled clean skipped", "Start Shizuku, allow root or turn on accessibility, then clean from the app.")
            return Result.success()
        }
        Cleaner.runRecorded(ctx, scheduled = true)
            .onSuccess { Notifier.post(ctx, "Cache cleaned", Notifier.summary(ctx, it)) }
            .onFailure { Notifier.post(ctx, "Scheduled clean failed", it.message ?: "Unknown error") }
        // Always success: a failed run must not stop tomorrow's run.
        return Result.success()
    }
}

object Notifier {
    private const val CHANNEL = "acc_runs"

    fun summary(ctx: Context, r: CleanResult): String =
        when {
            r.freedBytes >= 0 ->
                "Freed " + android.text.format.Formatter.formatFileSize(ctx, r.freedBytes) +
                    (if (r.appsTried > r.appsCleaned) " (${r.appsCleaned} of ${r.appsTried} apps)" else "")
            r.appsCleaned > 0 -> "Cleaned ${r.appsCleaned} apps"
            else -> "Done"
        }

    fun postUpdate(ctx: Context, info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val page = info.pageUrl ?: return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("acc_updates", "App updates", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx, 3, Intent(Intent.ACTION_VIEW, Uri.parse(page)), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, "acc_updates")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("ACC ${info.version} is available")
            .setContentText("Tap to see what's new and download it.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(3, n)
    }

    fun post(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Cleaning results", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }
}
