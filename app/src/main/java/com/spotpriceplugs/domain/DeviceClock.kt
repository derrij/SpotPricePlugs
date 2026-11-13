package com.spotpriceplugs.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Converts plan times (phone time zone) into the plug's own wall-clock time.
 *
 * Plugs run their timers in their local time, which is often not the phone's:
 * Tasmota defaults to `Timezone 1` (UTC+1, no DST), a Shelly may have been set
 * up abroad. The plug's current UTC offset is measured and every timer is
 * written in that offset, with the weekday mask moved along when the shift
 * crosses midnight.
 */
object DeviceClock {

    /** A timer time on the plug: hour, minute and the Tasmota-style mask (0 = Sunday). */
    data class At(val hour: Int, val minute: Int, val mask: String) {
        val hhmm: String get() = String.format(Locale.US, "%02d:%02d", hour, minute)
        val hhmmCompact: String get() = String.format(Locale.US, "%02d%02d", hour, minute)
    }

    /**
     * The plug's clock as measured before a schedule is written.
     *
     * [followsPhone]: the plug is taken to be in the phone's time zone,
     * daylight-saving rules included, so plan times are written as the phone's
     * wall time. That keeps blocks after a clock change (the night the clocks
     * move) at the right moment; a fixed offset would put them an hour off.
     */
    data class Zone(val offsetSec: Int?, val followsPhone: Boolean = false)

    /**
     * Whether a plug that is [offsetSec] from UTC right now shares the phone's
     * zone. [observesDst] = false for a plug known to run on a fixed offset
     * (Tasmota with a numeric `Timezone`): it only looks the same until the
     * phone's zone next changes its offset.
     */
    fun zoneOf(offsetSec: Int?, phone: ZoneId, now: Instant, observesDst: Boolean = true): Zone =
        Zone(
            offsetSec,
            offsetSec != null && observesDst && phone.rules.getOffset(now).totalSeconds == offsetSec
        )

    fun at(t: ZonedDateTime, mask: String, zone: Zone): At =
        if (zone.followsPhone) At(t.hour, t.minute, mask) else at(t, mask, zone.offsetSec)

    /**
     * Wall time on the plug for [t]. With [deviceOffsetSec] == null (offset
     * unknown) the phone's wall time is used unchanged.
     */
    fun at(t: ZonedDateTime, mask: String, deviceOffsetSec: Int?): At {
        if (deviceOffsetSec == null) return At(t.hour, t.minute, mask)
        val local = t.toInstant().atOffset(ZoneOffset.ofTotalSeconds(deviceOffsetSec))
        val shift = ChronoUnit.DAYS.between(t.toLocalDate(), local.toLocalDate()).toInt()
        return At(local.hour, local.minute, rotate(mask, shift))
    }

    /** Moves every set day of a 7-character mask by [days] (positive = later). */
    fun rotate(mask: String, days: Int): String {
        if (days % 7 == 0 || mask.length < 7) return mask
        val out = CharArray(7) { '0' }
        for (i in 0 until 7) {
            if (mask[i] == '1') out[((i + days) % 7 + 7) % 7] = '1'
        }
        return String(out)
    }

    /**
     * The plug's UTC offset from its local wall time and the true UTC time,
     * rounded to 15 minutes. null when the plug clock is clearly not set
     * (more than 15 minutes off after rounding is impossible to tell apart
     * from a wrong zone, so only an unset clock — year before 2020 — is rejected).
     */
    fun offsetSeconds(deviceLocal: LocalDateTime, utc: Instant): Int? {
        if (deviceLocal.year < 2020) return null
        val utcLocal = LocalDateTime.ofInstant(utc, ZoneOffset.UTC)
        val minutes = ChronoUnit.SECONDS.between(utcLocal, deviceLocal) / 60.0
        val rounded = (minutes / 15.0).roundToInt() * 15
        if (rounded < -14 * 60 || rounded > 14 * 60) return null
        return rounded * 60
    }
}
