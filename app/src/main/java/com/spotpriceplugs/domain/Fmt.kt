package com.spotpriceplugs.domain

import com.spotpriceplugs.App
import com.spotpriceplugs.R
import com.spotpriceplugs.data.NordPool
import com.spotpriceplugs.str
import java.text.NumberFormat
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Currency

/** "4 h 15 min", localised. */
fun minutesText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> str(R.string.dur_hm, h, m)
        h > 0 -> str(R.string.dur_h, h)
        else -> str(R.string.dur_m, m)
    }
}

/** "14:05" for today, "31.10 14:05" for another day. */
fun clockText(millis: Long): String {
    if (millis <= 0L) return "—"
    val t = Instant.ofEpochMilli(millis).atZone(NordPool.zone)
    val now = ZonedDateTime.now(NordPool.zone)
    return if (t.toLocalDate() == now.toLocalDate()) {
        "%02d:%02d".format(t.hour, t.minute)
    } else {
        "%02d.%02d %02d:%02d".format(t.dayOfMonth, t.monthValue, t.hour, t.minute)
    }
}

fun tapText(millis: Long): String {
    if (millis <= 0L) return str(R.string.never_run)
    val t = Instant.ofEpochMilli(millis).atZone(NordPool.zone)
    return "%02d.%02d %02d:%02d".format(t.dayOfMonth, t.monthValue, t.hour, t.minute)
}

/** Euros in the app language's currency format: "0,53 €", "€0.53", "0,53 €" … */
fun eurosText(euros: Double): String {
    val locale = App.context.resources.configuration.locales[0]
    return NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("EUR")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(euros)
}
