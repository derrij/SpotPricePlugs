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
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object Tasmota {
    /**
     * [openBeken]: the device is OpenBeken, which answers the same endpoint
     * but keeps its schedule differently (see [OpenBeken]). [version] is the
     * firmware version it reports.
     */
    data class Info(
        val name: String,
        val topic: String,
        val mac: String,
        val version: String = "",
        val openBeken: Boolean = false
    )

    suspend fun status(ip: String, connectMs: Int = 900, readMs: Int = 1800): Info? =
        withContext(Dispatchers.IO) {
            val text = httpGet("http://$ip/cm?cmnd=Status0", connectMs, readMs)
                ?: return@withContext null
            try {
                val root = JSONObject(text)
                val status = root.optJSONObject("Status") ?: return@withContext null
                var name = status.optString("DeviceName", "")
                if (name.isBlank()) {
                    val friendly = status.optJSONArray("FriendlyName")
                    if (friendly != null && friendly.length() > 0) name = friendly.optString(0, "")
                }
                if (name.isBlank()) name = status.optString("Topic", "")
                if (name.isBlank()) return@withContext null
                // The MAC is in StatusNET; very old builds had it in Status.
                val mac = root.optJSONObject("StatusNET")?.optString("Mac", "").orEmpty()
                    .ifBlank { status.optString("Mac", "") }
                val firmware = root.optJSONObject("StatusFWR")
                val version = firmware?.optString("Version", "").orEmpty()
                Info(
                    name, status.optString("Topic", ""), mac, version,
                    isOpenBeken(version, firmware?.optString("SDK", "").orEmpty())
                )
            } catch (e: Exception) {
                null
            }
        }

    /**
     * OpenBeken reports `"Version":"OpenBL602_1.18.247"` (OpenBK7231N_…,
     * OpenW800_…) and `"SDK":"obk"`; Tasmota's version starts with a digit.
     */
    internal fun isOpenBeken(version: String, sdk: String): Boolean =
        sdk.equals("obk", ignoreCase = true) || version.startsWith("Open", ignoreCase = true)

    /**
     * Writes the schedule into the timers and sets the current relay state.
     * Times are converted into the plug's own clock (Tasmota defaults to
     * `Timezone 1`, a fixed UTC+1, which is not the phone's zone half the year).
     * With [timersOn] == false (the user paused the plug) the timers are written
     * but stay disabled and the relay is not touched.
     */
    suspend fun apply(
        ip: String,
        blocks: List<Planner.Block>,
        powerOn: Boolean,
        timersOn: Boolean = true
    ) = withContext(Dispatchers.IO) {
        if (blocks.size * 2 > Planner.MAX_TIMERS) {
            throw IllegalStateException(msg(R.string.e_too_many_blocks, Planner.MAX_TIMERS / 2))
        }
        val zone = deviceZone(ip)
        // Paused: disable first, so no timer fires while they are rewritten.
        if (!timersOn) command(ip, "Timers 0")
        var number = 1
        for (block in blocks) {
            val on = DeviceClock.at(block.start, block.onDays, zone)
            command(ip, "Timer$number ${timerJson(on, true)}")
            number++
            val off = DeviceClock.at(block.end, block.offDays, zone)
            command(ip, "Timer$number ${timerJson(off, false)}")
            number++
        }
        while (number <= Planner.MAX_TIMERS) {
            command(ip, "Timer$number 0")
            number++
        }
        if (timersOn) {
            command(ip, "Timers 1")
            command(ip, "Power ${if (powerOn) "ON" else "OFF"}")
        }
    }

    /**
     * The plug's clock from `Status 7`: its UTC offset, and whether it follows
     * daylight-saving rules (`Timezone 99`) rather than a fixed offset. The
     * offset is null when the reply has a format we do not know (the phone's
     * wall time is then used as is). Throws when the clock is not set at all.
     */
    internal fun deviceZone(ip: String): DeviceClock.Zone {
        val unknown = DeviceClock.Zone(null)
        val text = commandText(ip, "Status 7")
        val tim = try {
            JSONObject(text).optJSONObject("StatusTIM")
        } catch (e: Exception) {
            null
        } ?: return unknown
        val local = parseDeviceTime(tim.optString("Local")) ?: return unknown
        val utc = parseDeviceTime(tim.optString("UTC")) ?: return unknown
        if (local.year < 2020 || utc.year < 2020) {
            throw IllegalStateException(msg(R.string.e_no_time, ip))
        }
        val instant = utc.toInstant(ZoneOffset.UTC)
        val offset = DeviceClock.offsetSeconds(local, instant)
            ?: throw IllegalStateException(msg(R.string.e_no_time, ip))
        // "Timezone":99 = the TimeStd/TimeDst rules; anything else ("+01:00") is fixed.
        val rules = tim.opt("Timezone")?.toString()?.trim() == "99"
        return DeviceClock.zoneOf(offset, ZoneId.systemDefault(), instant, observesDst = rules)
    }

    private val legacyTime = DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.US)

    /**
     * `Status 7` times: "2026-11-05T09:10:11" (current firmware, sometimes with
     * a fraction, "Z" or an offset) or "Thu Nov 05 09:10:11 2026" (old builds).
     */
    internal fun parseDeviceTime(raw: String): LocalDateTime? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.length >= 19 && text[4] == '-' && text[10] == 'T') {
            return try {
                LocalDateTime.parse(text.substring(0, 19))
            } catch (e: DateTimeParseException) {
                null
            }
        }
        return try {
            LocalDateTime.parse(text.split(Regex("""\s+""")).joinToString(" "), legacyTime)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** Switches the relay and returns its new state. */
    suspend fun setPower(ip: String, on: Boolean): Boolean = withContext(Dispatchers.IO) {
        val root = JSONObject(commandText(ip, "Power ${if (on) "ON" else "OFF"}"))
        // The plug accepted the command, so on an unrecognised reply
        // assume it did what was asked.
        powerStateOf(root) ?: on
    }

    /** Reads the relay state: true/false, or null if the plug did not answer. */
    suspend fun readPower(ip: String): Boolean? = withContext(Dispatchers.IO) {
        try {
            powerStateOf(JSONObject(commandText(ip, "Power")))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * New firmware answers `{"Power":"ON"}`, older builds (an S26 often runs
     * Tasmota 8.x–9.x) `{"POWER":"ON"}`, and very old ones may return `"1"`.
     * All variants are handled.
     */
    private fun powerStateOf(root: JSONObject): Boolean? {
        for (key in listOf("Power", "POWER", "POWER1")) {
            when (root.optString(key).uppercase()) {
                "ON", "1" -> return true
                "OFF", "0" -> return false
            }
        }
        return null
    }

    /** Pause/resume the schedule: `Timers 0` / `Timers 1`. */
    suspend fun setTimersEnabled(ip: String, on: Boolean) = withContext(Dispatchers.IO) {
        commandText(ip, "Timers ${if (on) 1 else 0}")
        Unit
    }

    private fun timerJson(at: DeviceClock.At, on: Boolean): String =
        JSONObject()
            .put("Enable", 1)
            .put("Mode", 0)
            .put("Time", at.hhmm)
            .put("Window", 0)
            .put("Days", at.mask)
            .put("Repeat", 1)
            .put("Output", 1)
            .put("Action", if (on) 1 else 0)
            .toString()

    private fun command(ip: String, command: String) {
        commandText(ip, command)
    }

    internal fun commandText(ip: String, command: String): String {
        val encoded = URLEncoder.encode(command, "UTF-8")
        val url = "http://$ip/cm?cmnd=$encoded"
        // Tasmota's single-threaded web server sometimes drops a connection,
        // and in a background run the phone's Wi-Fi may still be waking up:
        // retry a few times. All commands sent here are idempotent.
        var text: String? = null
        for (attempt in 1..3) {
            text = httpGet(url, 3000, 7000)
            if (text != null) break
            if (attempt < 3) Thread.sleep(400L * attempt)
        }
        if (text == null) throw IllegalStateException(msg(R.string.e_no_reply, ip, command))
        if (text.contains("\"Command\":\"Unknown\"") || text.contains("\"Command\":\"Error\"")) {
            throw IllegalStateException(msg(R.string.e_rejected, "Tasmota", command))
        }
        return text
    }

    private fun httpGet(url: String, connectMs: Int, readMs: Int): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectMs
        conn.readTimeout = readMs
        conn.setRequestProperty("User-Agent", "spotpriceplugs-android")
        return try {
            if (conn.responseCode !in 200..299) {
                null
            } else {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
