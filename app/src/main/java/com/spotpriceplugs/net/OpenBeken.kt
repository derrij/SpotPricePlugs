package com.spotpriceplugs.net

import com.spotpriceplugs.R
import com.spotpriceplugs.msg
import com.spotpriceplugs.domain.DeviceClock
import com.spotpriceplugs.domain.Planner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * Schedules for OpenBeken (OpenBK7231T_App: BK7231, BL602, W800 … plugs).
 *
 * The firmware answers the Tasmota `cm?cmnd=` endpoint, so detection, the
 * clock (`Status 7`) and the relay (`Power`) go through [Tasmota]. It has no
 * `Timer`/`Timers` commands, though, and answers `{}` to anything it does not
 * know, so the schedule is written as clock events instead:
 *
 *   addClockEvent HH:mm <weekday flags> <id> POWER ON|OFF
 *   removeClockEvent <id>
 *
 * Weekday flags are a bit mask with bit 0 = Sunday. Only the ids
 * [FIRST_ID] … are used, so the user's own events are never touched.
 *
 * The plug keeps a fixed UTC offset (`ntp_timeZoneOfs`) and knows nothing of
 * daylight saving, so before a schedule is written the offset is set to the
 * phone's current one.
 *
 * Clock events live in the plug's RAM and a reboot drops them. The same
 * commands are therefore also kept in `autoexec.bat` on the plug's LittleFS,
 * which the firmware runs at boot (after its startup command), between two
 * marker lines; anything else in that file is left as it is. On a plug
 * without a file system the app falls back to noticing the reboot by the
 * plug's uptime ([needsRewrite]) and writing the events again.
 */
object OpenBeken {
    private const val FIRST_ID = 7301
    private const val AUTOEXEC = "autoexec.bat"
    internal const val BEGIN = "// spotpriceplugs begin"
    internal const val END = "// spotpriceplugs end"

    /**
     * Replaces this app's clock events with [blocks] and sets the relay. With
     * [timersOn] == false (the user paused the plug) the events are only
     * removed and the relay is not touched.
     *
     * Returns whether the schedule is also in the plug's boot script, i.e.
     * survives a reboot.
     */
    suspend fun apply(
        ip: String,
        blocks: List<Planner.Block>,
        powerOn: Boolean,
        timersOn: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        if (blocks.size * 2 > Planner.MAX_TIMERS) {
            throw IllegalStateException(msg(R.string.e_too_many_blocks, Planner.MAX_TIMERS / 2))
        }
        // Also the check that the clock is set: events never fire without it.
        var zone = Tasmota.deviceZone(ip)
        val phone = phoneOffset()
        if (zone.offsetSec != null && zone.offsetSec != phone) {
            Tasmota.commandText(ip, "ntp_timeZoneOfs ${offsetText(phone)}")
            zone = Tasmota.deviceZone(ip)
        }

        val events = ArrayList<String>()
        if (timersOn) {
            var id = FIRST_ID
            for (block in blocks) {
                event(id++, DeviceClock.at(block.start, block.onDays, zone), true)?.let(events::add)
                event(id++, DeviceClock.at(block.end, block.offDays, zone), false)?.let(events::add)
            }
        }

        removeEvents(ip)
        for (command in events) Tasmota.commandText(ip, command)
        // The boot script carries the offset the times were written in.
        val offset = zone.offsetSec
        val persisted = persist(
            ip,
            if (events.isEmpty()) emptyList()
            else listOfNotNull(offset?.let { "ntp_timeZoneOfs ${offsetText(it)}" }) + events
        )
        if (timersOn) Tasmota.commandText(ip, "Power ${if (powerOn) "ON" else "OFF"}")
        persisted
    }

    /** Pause: removes this app's clock events (there is no "timers off" switch). */
    suspend fun clear(ip: String) = withContext(Dispatchers.IO) {
        removeEvents(ip)
        persist(ip, emptyList())
        Unit
    }

    /**
     * Whether the schedule written at [appliedAt] (phone clock, ms) has to be
     * written again although the plan is the same: the plug has rebooted
     * since, or its UTC offset is no longer the phone's (a clock change).
     * false when the plug does not answer: nothing can be written then anyway.
     */
    suspend fun needsRewrite(ip: String, appliedAt: Long): Boolean = withContext(Dispatchers.IO) {
        val uptime = try {
            JSONObject(Tasmota.commandText(ip, "Status 11"))
                .optJSONObject("StatusSTS")?.takeIf { it.has("UptimeSec") }?.optLong("UptimeSec")
        } catch (e: Exception) {
            null
        } ?: return@withContext false
        if (bootedAfter(uptime, appliedAt, System.currentTimeMillis())) return@withContext true
        val offset = try {
            Tasmota.deviceZone(ip).offsetSec
        } catch (e: Exception) {
            null
        }
        offset != null && offset != phoneOffset()
    }

    /** A few seconds of slack towards "rebooted": writing the events once more is harmless. */
    internal fun bootedAfter(uptimeSec: Long, appliedAt: Long, nowMillis: Long): Boolean =
        nowMillis - uptimeSec * 1000L > appliedAt - 5_000L

    /** Weekday flags (bit 0 = Sunday) from a Tasmota mask (index 0 = Sunday). */
    internal fun dayFlags(tasmotaMask: String): Int =
        tasmotaMask.take(7).foldIndexed(0) { i, acc, c -> if (c == '1') acc or (1 shl i) else acc }

    /** `ntp_timeZoneOfs` argument: "2:00", "-3:30". */
    internal fun offsetText(offsetSec: Int): String {
        val minutes = abs(offsetSec) / 60
        return String.format(
            Locale.US, "%s%d:%02d", if (offsetSec < 0) "-" else "", minutes / 60, minutes % 60
        )
    }

    /**
     * [old] (the plug's autoexec.bat) with this app's block replaced by
     * [lines]; no block at all when [lines] is empty. "" = the file is not
     * needed any more.
     */
    internal fun mergeAutoexec(old: String, lines: List<String>): String {
        val kept = ArrayList<String>()
        var inside = false
        for (line in old.lines()) {
            when (line.trim()) {
                BEGIN -> inside = true
                END -> inside = false
                else -> if (!inside) kept.add(line)
            }
        }
        while (kept.isNotEmpty() && kept[kept.lastIndex].isBlank()) kept.removeAt(kept.lastIndex)
        val out = if (lines.isEmpty()) kept else kept + BEGIN + lines + END
        return if (out.isEmpty()) "" else out.joinToString("\r\n", postfix = "\r\n")
    }

    private fun phoneOffset(): Int = ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds

    private fun event(id: Int, at: DeviceClock.At, on: Boolean): String? {
        val flags = dayFlags(at.mask)
        if (flags == 0) return null
        val days = String.format(Locale.US, "0x%02x", flags)
        return "addClockEvent ${at.hhmm} $days $id POWER ${if (on) "ON" else "OFF"}"
    }

    private fun removeEvents(ip: String) {
        for (id in FIRST_ID until FIRST_ID + Planner.MAX_TIMERS) {
            Tasmota.commandText(ip, "removeClockEvent $id")
        }
    }

    /**
     * Puts [lines] into the plug's autoexec.bat (see [mergeAutoexec]) and
     * reads the file back. Nothing is written when the file already says so,
     * to spare the flash. A plug without a file system is left alone; the
     * result is false then.
     */
    private fun persist(ip: String, lines: List<String>): Boolean {
        val url = "http://$ip/api/lfs/$AUTOEXEC"
        val (code, body) = request("GET", url, null) ?: return false
        val old = when (code) {
            200 -> body
            404 -> ""
            else -> return false
        }
        val wanted = mergeAutoexec(old, lines)
        if (wanted == old) return true
        if (wanted.isEmpty()) {
            Tasmota.commandText(ip, "lfs_remove $AUTOEXEC")
            return true
        }
        val posted = request("POST", url, wanted)
        if (posted == null || posted.first !in 200..299) return false
        val stored = request("GET", url, null)
        if (stored == null || stored.first != 200 || stored.second != wanted) {
            throw IllegalStateException(msg(R.string.e_schedule_rejected, ip) + " ($AUTOEXEC)")
        }
        return true
    }

    /** Raw HTTP exchange: (status code, body), or null when the plug is unreachable. */
    private fun request(verb: String, url: String, body: String?): Pair<Int, String>? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = verb
                connectTimeout = 3000
                readTimeout = 7000
                setRequestProperty("User-Agent", "spotpriceplugs-android")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "text/plain")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
