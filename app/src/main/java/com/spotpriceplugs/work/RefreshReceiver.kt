package com.spotpriceplugs.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The exact alarm fired. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH) return
        Scheduler.refreshNow(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.spotpriceplugs.REFRESH"
    }
}
