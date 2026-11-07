package io.github.raafiabir.acc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts a long accessibility clean so Android keeps ACC alive while Settings is on screen,
 * and gives you a Stop button in the notification.
 */
class CleaningService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val running = job
            if (running == null) stopSelf() else running.cancel()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY

        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build("Getting ready"), type)

        job = scope.launch {
            val progress = launch {
                AccessibilityCleaner.progress.collect { text -> if (text != null) update(text) }
            }
            try {
                val ctx = applicationContext
                Cleaner.runRecorded(ctx)
                    .onSuccess { Notifier.post(ctx, "Cache cleaned", Notifier.summary(ctx, it)) }
                    .onFailure { Notifier.post(ctx, "Cleaning stopped", it.message ?: "Unknown error") }
            } finally {
                progress.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Cleaning in progress", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(text))
    }

    private fun build(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 0, Intent(this, CleaningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Cleaning cache")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        private const val CHANNEL = "acc_progress"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_STOP = "io.github.raafiabir.acc.STOP"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, CleaningService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, CleaningService::class.java).setAction(ACTION_STOP))
        }
    }
}
