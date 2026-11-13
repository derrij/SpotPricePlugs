package com.spotpriceplugs.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.spotpriceplugs.R
import com.spotpriceplugs.data.Store
import com.spotpriceplugs.data.Updates
import kotlin.coroutines.cancellation.CancellationException

/**
 * Background update check: the app mostly runs in the background and is rarely
 * opened, so a new version is announced by a notification (once per version).
 */
object UpdateNotifier {
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 2001

    suspend fun checkInBackground(context: Context) {
        if (!Updates.isDue(context)) return
        val release = try {
            Updates.check(context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return
        val store = Store(context)
        if (store.notifiedVersion == release.version) return
        if (notify(context, release.version, store.updateUrl)) {
            store.notifiedVersion = release.version
        }
    }

    private fun notify(context: Context, version: String, url: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_plug)
            .setContentTitle("Spot Price Plugs $version")
            .setContentText(context.getString(R.string.update_notification))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
        return true
    }
}
