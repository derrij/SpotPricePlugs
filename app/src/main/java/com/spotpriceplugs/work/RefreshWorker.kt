package com.spotpriceplugs.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.spotpriceplugs.R
import com.spotpriceplugs.msg
import com.spotpriceplugs.data.Store
import com.spotpriceplugs.domain.Refresher
import com.spotpriceplugs.net.Scanner
import kotlin.coroutines.cancellation.CancellationException

class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        if (!Scanner.lanAvailable(applicationContext)) {
            // Mobile data only: the plugs are out of reach. Leave them as they
            // are (no errors on the cards) and come back when Wi-Fi does.
            Scheduler.schedule(applicationContext, away = true)
            return Result.success()
        }
        try {
            // Only the plugs that are due: a plug that keeps failing must not
            // make every other plug's timers be rewritten each time it is retried.
            Refresher.run(applicationContext, background = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.lastRun = System.currentTimeMillis()
            store.lastRunInfo = msg(R.string.msg_error, e.message ?: e.javaClass.simpleName)
        }
        Scheduler.schedule(applicationContext)
        // The network is up anyway: ask GitHub for a new version (at most every 12 h).
        try {
            UpdateNotifier.checkInBackground(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        // No WorkManager retry: the next run is already scheduled.
        return Result.success()
    }

    /**
     * Expedited work runs as a foreground service on Android 8–11 and needs a
     * notification there; on Android 12+ it runs as a job and this is not shown.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.refresh_channel),
                NotificationManager.IMPORTANCE_MIN
            )
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_plug)
            .setContentTitle(context.getString(R.string.refresh_running))
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL = "refresh"
        const val NOTIFICATION_ID = 2002
    }
}
