package com.spotpriceplugs.ui

import android.Manifest
import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.spotpriceplugs.domain.Vat
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal fun parseNum(text: String, fallback: Double): Double {
    val value = text.replace(',', '.').trim().toDoubleOrNull() ?: return fallback
    return if (value.isFinite() && value > 0.0) value else fallback
}

/** "" = automatic VAT by region; otherwise a number in 0..100 (0 is valid). */
internal fun parseVat(text: String): Double {
    val value = text.replace(',', '.').trim().toDoubleOrNull() ?: return Vat.AUTO
    return if (value.isFinite() && value in 0.0..100.0) value else Vat.AUTO
}

internal fun trimVat(value: Double): String =
    if (value < 0.0) "" else trimNum(value)

/**
 * Up to 4 decimals without trailing zeros: 4 → "4", 0.2 → "0.2", 0.125 → "0.125".
 * Precise enough that a value shown in an edit field is saved back unchanged.
 */
internal fun trimNum(value: Double): String =
    DecimalFormat("0.####", DecimalFormatSymbols.getInstance(Locale.getDefault()))
        .apply { roundingMode = RoundingMode.HALF_UP }
        .format(value)

/** Empty = power not set (0), so no savings in euros are computed. */
internal fun trimWatts(value: Double): String =
    if (value > 0.0) value.toInt().toString() else ""

/** "" or 0 means unknown power; otherwise 0 to 50 000 W. */
internal fun parseWatts(text: String, fallback: Double): Double {
    val clean = text.trim()
    if (clean.isEmpty()) return 0.0
    val value = clean.toDoubleOrNull() ?: return fallback
    return if (value.isFinite() && value >= 0.0 && value <= 50_000.0) value else fallback
}

/** "14:05" → (14, 5); null for a malformed value. */
internal fun parseClock(text: String): Pair<Int, Int>? {
    val parts = text.trim().split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour to minute else null
}

internal fun ipOrder(ip: String): Long =
    ip.split(".").mapNotNull { it.toIntOrNull() }.fold(0L) { acc, part -> acc * 256 + part }

internal fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Device name", text))
}

internal fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun canExact(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return am.canScheduleExactAlarms()
}

internal fun batteryIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

internal fun notificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

internal fun openExactSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** System per-app language picker (Android 13+). */
internal fun openLanguageSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

internal fun openBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
