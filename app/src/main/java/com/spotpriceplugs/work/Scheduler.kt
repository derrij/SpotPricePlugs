package com.spotpriceplugs.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.spotpriceplugs.data.NordPool
import com.spotpriceplugs.data.Store
import com.spotpriceplugs.domain.Refresher
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Decides when to wake the app, taking the earliest of:
 *   1. the daily run at the configured time (14:05 local by default);
 *   2. the end of any plug's plan coverage;
 *   3. a delayed retry when tomorrow's prices were not out yet;
 *   4. "plan overdue": catch up within a couple of minutes.
 *
 * Two mechanisms are armed: an exact alarm (fires in Doze) and WorkManager
 * as a fallback in case alarms are denied or the job gets lost.
 */
object Scheduler {
    private const val REQUEST_CODE = 1001
    private const val REQUEST_CODE_WINDOW = 1002
    private const val WORK_NOW = "plug-refresh"
    private const val WORK_FALLBACK = "plug-refresh-fallback"
    private const val WORK_HOME = "plug-refresh-home"

    /**
     * [away]: the last run found no Wi-Fi/Ethernet/VPN, so the plugs cannot be
     * reached. The next look is then at least an hour off (instead of the
     * "overdue, catch up in two minutes" rule), and a job waiting for an
     * unmetered network runs the refresh as soon as the phone is back on Wi-Fi.
     */
    fun schedule(context: Context, away: Boolean = false) {
        val store = Store(context)
        val now = ZonedDateTime.now(NordPool.zone)
        val next = nextRun(store, now).let { if (away) maxOf(it, now.plusHours(1)) else it }
        val delayMs = Duration.between(now, next).toMillis().coerceAtLeast(60_000L)

        store.nextRun = next.toInstant().toEpochMilli()

        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (am != null) {
            val triggerAt = System.currentTimeMillis() + delayMs
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                am.canScheduleExactAlarms()
            val pending = alarmIntent(context, REQUEST_CODE)
            val windowed = alarmIntent(context, REQUEST_CODE_WINDOW)
            try {
                if (canExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                    am.cancel(windowed)
                } else {
                    setInexact(am, triggerAt, pending, windowed)
                }
            } catch (e: SecurityException) {
                setInexact(am, triggerAt, pending, windowed)
            }
        }

        // Fallback: 25 minutes after the alarm, only with a network connection.
        val fallback = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(delayMs + 25 * 60_000L, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(WORK_FALLBACK)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_FALLBACK, ExistingWorkPolicy.REPLACE, fallback)

        if (away) {
            val home = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .addTag(WORK_HOME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_HOME, ExistingWorkPolicy.KEEP, home)
        }
    }

    /**
     * Without the exact-alarm permission an allow-while-idle alarm may legally
     * arrive up to ~75% of the delay late (hours for the daily 14:05 run). A
     * second, windowed alarm keeps the run within 10 minutes while the phone is
     * awake; the allow-while-idle one still covers Doze. Whichever fires first
     * wins: the refresh reschedules both.
     */
    private fun setInexact(am: AlarmManager, triggerAt: Long, idle: PendingIntent, windowed: PendingIntent) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, idle)
        am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60_000L, windowed)
    }

    /** Immediate run (the alarm fired or a button was pressed). */
    fun refreshNow(context: Context) {
        val work = OneTimeWorkRequestBuilder<RefreshWorker>()
            // Expedited: started from an alarm, it should not wait for the next
            // Doze maintenance window. Without quota it runs as normal work.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(WORK_NOW)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.KEEP, work)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.cancel(alarmIntent(context, REQUEST_CODE))
        am?.cancel(alarmIntent(context, REQUEST_CODE_WINDOW))
        WorkManager.getInstance(context).cancelUniqueWork(WORK_FALLBACK)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_HOME)
    }

    private fun nextRun(store: Store, now: ZonedDateTime): ZonedDateTime {
        val candidates = ArrayList<ZonedDateTime>()
        candidates.add(Refresher.nextDaily(now, store.refreshHour, store.refreshMinute))

        val nowMillis = now.toInstant().toEpochMilli()
        // Devices the app does not control never wake it.
        val plugs = store.plugs.filter { it.managed }
        val plugTimes = plugs.mapNotNull { plug ->
            if (plug.nextAt <= 0L) null
            else Instant.ofEpochMilli(plug.nextAt).atZone(NordPool.zone)
        }
        // A time already due or only moments away still counts: run it in a
        // minute rather than dropping it.
        plugTimes.minOrNull()?.let { candidates.add(maxOf(it, now.plusMinutes(1))) }

        val overdue = plugs.any { it.nextAt > 0L && it.nextAt <= nowMillis }
        if (overdue) {
            // The phone was off or the job did not run: catch up.
            candidates.add(now.plusMinutes(2))
        } else if (plugs.isNotEmpty() && plugTimes.isEmpty() && store.lastRun > 0L) {
            // No plan at all (the last run failed): try again in an hour.
            candidates.add(now.plusHours(1))
        }

        val retryAt = store.retryAt
        if (retryAt > 0L) {
            val retry = Instant.ofEpochMilli(retryAt).atZone(NordPool.zone)
            if (retry.isAfter(now.plusMinutes(1)) && retry.isBefore(now.plusHours(6))) {
                candidates.add(retry)
            }
        }

        return candidates.min()
    }

    private fun alarmIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, RefreshReceiver::class.java)
            .setAction(RefreshReceiver.ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
