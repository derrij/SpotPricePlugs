package com.spotpriceplugs.net

import com.spotpriceplugs.R
import com.spotpriceplugs.msg
import com.spotpriceplugs.domain.DeviceClock
import com.spotpriceplugs.domain.Planner.Block
import com.spotpriceplugs.model.PlugKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Transport for Shelly plugs.
 *
 * Gen2/Gen3 (Plus Plug S, Plug S Gen3, Mini…): JSON-RPC over HTTP.
 *   POST http://IP/rpc with the frame {"id":1,"src":…,"method":…,"params":…};
 *   the reply is {"id":1,"src":…,"result":{…}} or {"id":1,"src":…,"error":{"code":…,"message":…}}.
 *   Plain reads also work as GET http://IP/rpc/Method (bare result object).
 *   Schedules are `Schedule.*` jobs (cron-like timespec, max 20 per device);
 *   the ids of the jobs we created are kept on the plug in KVS so that the
 *   user's own jobs are never touched.
 *
 * Gen1 (Plug S, Plug…): the legacy HTTP API — /shelly, /status, /relay/0?turn=on,
 *   /settings/relay/0?schedule=…&schedule_rules=….
 *
 * Plugs run timers in their own local time, so before writing a schedule the
 * plug's UTC offset is measured and every time is converted with [DeviceClock].
 */
object Shelly {

    data class Info(val name: String, val kind: Int, val model: String, val mac: String)

    /** Detects a Shelly: Gen2/Gen3 RPC first, then classic Gen1. */
    suspend fun detect(ip: String): Info? = withContext(Dispatchers.IO) {
        // Gen2/Gen3: GET /rpc/Shelly.GetDeviceInfo → bare object {id, name|null, model, mac, gen…}
        getJson("http://$ip/rpc/Shelly.GetDeviceInfo")?.let { d ->
            val name = text(d, "name") ?: text(d, "id") ?: return@withContext null
            return@withContext Info(
                name, PlugKind.SHELLY_RPC,
                text(d, "model") ?: "", text(d, "mac") ?: ""
            )
        }
        // Gen1: /shelly → type, mac; the user-given name comes from /settings
        val top = getJson("http://$ip/shelly", 900, 1800) ?: return@withContext null
        // Gen1 /shelly always carries "type"; a Gen2 answer would have been caught above.
        if (!top.has("type") && !top.has("mac")) return@withContext null
        var name = text(top, "name")
        if (name == null) {
            val settings = getJson("http://$ip/settings", 900, 1800)
            name = settings?.let { text(it, "name") }
                ?: settings?.optJSONObject("device")?.let { text(it, "hostname") }
        }
        if (name == null) name = text(top, "id") ?: text(top, "mac")
        if (name == null) return@withContext null
        Info(
            name, PlugKind.SHELLY_GEN1,
            text(top, "type") ?: text(top, "model") ?: "", text(top, "mac") ?: ""
        )
    }

    /** The plug's MAC, or null when it did not answer. */
    suspend fun mac(ip: String, kind: Int): String? = withContext(Dispatchers.IO) {
        val url = if (kind == PlugKind.SHELLY_GEN1) "http://$ip/shelly" else "http://$ip/rpc/Shelly.GetDeviceInfo"
        getJson(url)?.let { text(it, "mac") }
    }

    /** Relay on/off; returns the actual state. */
    suspend fun setPower(ip: String, kind: Int, on: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            when (kind) {
                PlugKind.SHELLY_GEN1 -> {
                    val relay = getJson(
                        "http://$ip/relay/0?turn=${if (on) "on" else "off"}", 3000, 7000
                    ) ?: throw IllegalStateException(msg(R.string.e_no_reply, ip, "/relay/0"))
                    relay.optBoolean("ison", on)
                }
                else -> {
                    // Switch.Set answers {"was_on": …} — the previous state. The plug
                    // accepted the command (no error), so it now is what was asked.
                    rpc(ip, "Switch.Set", JSONObject().put("id", 0).put("on", on))
                    on
                }
            }
        }

    /** Relay state and instantaneous meter power (if there is a meter). */
    suspend fun readState(ip: String, kind: Int): Pair<Boolean?, Double?> =
        withContext(Dispatchers.IO) {
            try {
                if (kind == PlugKind.SHELLY_GEN1) {
                    val s = getJson("http://$ip/status", 900, 1800)
                        ?: return@withContext null to null
                    val relay = s.optJSONArray("relays")?.optJSONObject(0)
                    val on = if (relay != null && relay.has("ison")) relay.optBoolean("ison") else null
                    val watts = s.optJSONArray("meters")?.optJSONObject(0)
                        ?.optDouble("power", 0.0)
                        ?.takeIf { it > 0.5 && it <= 50_000.0 }
                    on to watts
                } else {
                    // Result: {"id":0,"output":true,"apower":12.3,…}; apower is absent without a meter.
                    val s = rpc(ip, "Switch.GetStatus", JSONObject().put("id", 0), 900, 1800)
                    val on = if (s.has("output")) s.optBoolean("output") else null
                    val watts = s.optDouble("apower", 0.0)
                        .takeIf { it > 0.5 && it <= 50_000.0 }
                    on to watts
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null to null
            }
        }

    /**
     * Pause/resume the schedule.
     *
     * Gen1: `/settings/relay/0?schedule=false|true` (the rules stay stored).
     * Gen2/Gen3: `Schedule.Update {"id":…,"enable":…}` for our own jobs only.
     */
    suspend fun setTimersEnabled(ip: String, kind: Int, on: Boolean) =
        withContext(Dispatchers.IO) {
            if (kind == PlugKind.SHELLY_GEN1) {
                val path = "/settings/relay/0?schedule=$on"
                val res = getJson("http://$ip$path", 3000, 7000)
                    ?: throw IllegalStateException(msg(R.string.e_no_reply, ip, path))
                if (res.has("schedule") && res.optBoolean("schedule") != on) {
                    throw IllegalStateException(msg(R.string.e_rejected, ip, path))
                }
            } else {
                val jobs = listJobs(ip)
                for (id in ourJobIds(ip, jobs)) {
                    rpc(ip, "Schedule.Update", JSONObject().put("id", id).put("enable", on))
                }
            }
            Unit
        }

    /**
     * Writes the plan: on/off blocks → weekday schedule rules in the plug's
     * local time, then sets the current relay state. [powerOn] == null leaves
     * the relay alone, and [enabled] == false writes the schedule switched off
     * (a plug the user paused).
     *
     * Gen1 has a single rule list per relay (`schedule_rules`), so the app owns
     * it entirely: any rules the user created in the Shelly app are replaced.
     *
     * Gen2/Gen3 keep separate jobs; only the jobs created by this app are
     * deleted and recreated (see [ourJobIds]).
     */
    suspend fun apply(
        ip: String, kind: Int, blocks: List<Block>, powerOn: Boolean?, enabled: Boolean = true
    ) =
        withContext(Dispatchers.IO) {
            if (blocks.size * 2 > MAX_RULES) {
                throw IllegalStateException(msg(R.string.e_too_many_blocks, MAX_RULES / 2))
            }
            if (kind == PlugKind.SHELLY_GEN1) applyGen1(ip, blocks, enabled)
            else applyRpc(ip, blocks, enabled)
            if (powerOn != null) setPower(ip, kind, powerOn)
            Unit
        }

    // --- Gen1 ----------------------------------------------------------------

    /**
     * Gen1 rules are `HHMM-DAYS-on|off`, DAYS being the digits of the
     * weekdays with 0 = Monday … 6 = Sunday, e.g. `0730-01234-on`. They are
     * sent comma-separated in one `schedule_rules` query parameter.
     */
    private fun applyGen1(ip: String, blocks: List<Block>, enabled: Boolean) {
        val offset = gen1Offset(ip)
        val rules = ArrayList<String>()
        for (block in blocks) {
            gen1Rule(DeviceClock.at(block.start, block.onDays, offset), true)?.let(rules::add)
            gen1Rule(DeviceClock.at(block.end, block.offDays, offset), false)?.let(rules::add)
        }
        val value = URLEncoder.encode(rules.joinToString(","), "UTF-8")
        val path = "/settings/relay/0?schedule=${enabled && rules.isNotEmpty()}&schedule_rules=$value"
        val res = getJson("http://$ip$path", 3000, 7000)
            ?: throw IllegalStateException(msg(R.string.e_schedule_rejected, ip))
        // The reply is the relay settings; when it echoes the rules, check them.
        res.optJSONArray("schedule_rules")?.let { echo ->
            if (echo.length() != rules.size) {
                throw IllegalStateException(msg(R.string.e_schedule_rejected, ip) + ": $echo")
            }
        }
    }

    private fun gen1Rule(at: DeviceClock.At, isOn: Boolean): String? {
        val days = gen1Days(at.mask)
        if (days.isEmpty()) return null
        return "${at.hhmmCompact}-$days-${if (isOn) "on" else "off"}"
    }

    /** Gen1 weekday digits (0 = Monday) from a Tasmota mask (index 0 = Sunday). */
    internal fun gen1Days(tasmotaMask: String): String {
        val days = ArrayList<Int>()
        tasmotaMask.take(7).forEachIndexed { i, c ->
            if (c == '1') days.add((i + 6) % 7)
        }
        return days.sorted().joinToString("")
    }

    /** Gen1 `/status`: "unixtime" (UTC seconds, 0 when unsynced) and "time" ("HH:MM" local). */
    private fun gen1Offset(ip: String): DeviceClock.Zone {
        val s = getJson("http://$ip/status", 3000, 7000)
            ?: throw IllegalStateException(msg(R.string.e_no_reply, ip, "/status"))
        return offsetFrom(ip, s)
    }

    // --- Gen2/Gen3 -----------------------------------------------------------

    private fun applyRpc(ip: String, blocks: List<Block>, enabled: Boolean) {
        val offset = rpcOffset(ip)

        val jobs = listJobs(ip)
        val old = ourJobIds(ip, jobs)
        val others = jobs.count { it.id !in old }
        if (others + blocks.size * 2 > MAX_RULES) {
            throw IllegalStateException(
                msg(R.string.e_too_many_blocks, ((MAX_RULES - others) / 2).coerceAtLeast(0))
            )
        }
        for (id in old) rpc(ip, "Schedule.Delete", JSONObject().put("id", id))

        val created = ArrayList<Int>()
        val foreign = jobs.map { it.id }.filter { it !in old }
        try {
            for (block in blocks) {
                for ((time, days, isOn) in listOf(
                    Triple(block.start, block.onDays, true),
                    Triple(block.end, block.offDays, false)
                )) {
                    val at = DeviceClock.at(time, days, offset)
                    createJob(ip, at, isOn, enabled, known = foreign + created)?.let(created::add)
                }
            }
        } finally {
            // Remember even a partial set so the next run can clean it up.
            saveOurIds(ip, created)
        }
    }

    /**
     * One job: `Schedule.Create {"enable":…,"timespec":"0 MM HH * * MON,…","calls":[Switch.Set]}`.
     * Returns its id, or null when the mask has no day.
     *
     * When the reply is lost the job may or may not exist. The plug is asked
     * for its job list: a job of ours that was not there before ([known])
     * is the one just made; otherwise it is created again.
     */
    private fun createJob(
        ip: String, at: DeviceClock.At, isOn: Boolean, enabled: Boolean, known: List<Int>
    ): Int? {
        val days = cronDays(at.mask)
        if (days.isEmpty()) return null
        val timespec = "0 ${at.minute} ${at.hour} * * $days"
        val params = JSONObject()
            .put("enable", enabled)
            .put("timespec", timespec)
            .put(
                "calls", JSONArray().put(
                    JSONObject()
                        .put("method", "Switch.Set")
                        .put("params", JSONObject().put("id", 0).put("on", isOn))
                )
            )
        val res = try {
            rpc(ip, "Schedule.Create", params)
        } catch (e: RpcException) {
            throw e
        } catch (e: IllegalStateException) {
            val made = listJobs(ip).firstOrNull {
                it.id !in known && it.looksOurs && it.on == isOn
            }
            if (made != null) return made.id
            rpc(ip, "Schedule.Create", params)
        }
        if (!res.has("id")) {
            throw IllegalStateException(msg(R.string.e_schedule_rejected, ip) + ": $res")
        }
        return res.getInt("id")
    }

    /** Cron weekday names (SUN…SAT, as the Gen2 timespec accepts) from a Tasmota mask (index 0 = Sunday). */
    internal fun cronDays(tasmotaMask: String): String =
        tasmotaMask.take(7).mapIndexedNotNull { i, c -> if (c == '1') CRON_DAYS[i] else null }
            .joinToString(",")

    private data class Job(val id: Int, val looksOurs: Boolean, val on: Boolean?)

    /** `Schedule.List` → {"jobs":[{"id","enable","timespec","calls":[…]}],"rev"}. */
    private fun listJobs(ip: String): List<Job> {
        val arr = rpc(ip, "Schedule.List", null).optJSONArray("jobs") ?: return emptyList()
        val out = ArrayList<Job>()
        for (i in 0 until arr.length()) {
            val job = arr.optJSONObject(i) ?: continue
            if (!job.has("id")) continue
            val calls = job.optJSONArray("calls")
            val call = calls?.optJSONObject(0)
            val looksOurs = calls != null && calls.length() == 1 && call != null &&
                call.optString("method").equals("Switch.Set", ignoreCase = true) &&
                call.optJSONObject("params")?.let { it.optInt("id", -1) == 0 && it.has("on") } == true
            val on = call?.optJSONObject("params")?.takeIf { it.has("on") }?.optBoolean("on")
            out += Job(job.getInt("id"), looksOurs, on)
        }
        return out
    }

    /**
     * Ids of the jobs this app created that still exist on the plug.
     *
     * The ids are stored on the plug in KVS under [KVS_KEY]. Only jobs that
     * are both listed there and still look like ours (a single
     * `Switch.Set {"id":0,…}` call) are returned, in case ids got reused.
     *
     * Fallback when the plug has no KVS service: jobs are recognised by
     * content alone — exactly one call, `Switch.Set` with params.id == 0.
     * A hand-made on/off job of the same shape would then also be treated as
     * ours; that is accepted because there is no other place to remember ids.
     */
    private fun ourJobIds(ip: String, jobs: List<Job>): Set<Int> {
        val stored = loadOurIds(ip)
            ?: return jobs.filter { it.looksOurs }.map { it.id }.toSet()
        return jobs.filter { it.id in stored && it.looksOurs }.map { it.id }.toSet()
    }

    /**
     * `KVS.Get {"key":…}` → result.value. A missing key is an error reply
     * (treated as "no jobs yet" → empty set). null means KVS is unavailable
     * on this plug (method not found), which switches to the content fallback.
     */
    private fun loadOurIds(ip: String): Set<Int>? {
        val res = try {
            rpc(ip, "KVS.Get", JSONObject().put("key", KVS_KEY))
        } catch (e: RpcException) {
            return if (e.code in KVS_UNAVAILABLE) null else emptySet()
        }
        val raw = res.opt("value")
        val arr = when (raw) {
            is JSONArray -> raw
            is String -> try { JSONArray(raw) } catch (e: Exception) { return emptySet() }
            else -> return emptySet()
        }
        val out = HashSet<Int>()
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)
            if (v is Number) out += v.toInt()
            else (v as? String)?.toIntOrNull()?.let(out::add)
        }
        return out
    }

    /** `KVS.Set {"key":…,"value":"[1,2,3]"}` (value ≤ 253 chars: 20 ids fit easily). */
    private fun saveOurIds(ip: String, ids: List<Int>) {
        try {
            rpc(ip, "KVS.Set", JSONObject().put("key", KVS_KEY).put("value", JSONArray(ids).toString()))
        } catch (e: RpcException) {
            // Without KVS the content fallback finds our jobs next time.
            if (e.code !in KVS_UNAVAILABLE) throw e
        }
    }

    /** Gen2/Gen3 `Sys.GetStatus` → {"time":"HH:MM"|null,"unixtime":…|null,…}. */
    private fun rpcOffset(ip: String): DeviceClock.Zone =
        offsetFrom(ip, rpc(ip, "Sys.GetStatus", null))

    // --- Device clock --------------------------------------------------------

    /**
     * Device UTC offset from a status object holding "unixtime" (UTC) and
     * "time" ("HH:MM" local). The local date is not reported, so of the UTC
     * date −1/0/+1 the candidate closest to the UTC instant is taken.
     * Throws when the plug clock is not set.
     *
     * A Shelly keeps a real time zone with daylight-saving rules, so when its
     * offset equals the phone's it is treated as being in the phone's zone.
     */
    private fun offsetFrom(ip: String, status: JSONObject): DeviceClock.Zone {
        val noTime = IllegalStateException(msg(R.string.e_no_time, ip))
        val unix = if (status.isNull("unixtime")) 0L else status.optLong("unixtime", 0L)
        val hhmm = text(status, "time") ?: throw noTime
        if (unix <= 0L) throw noTime
        val localTime = try {
            LocalTime.parse(hhmm.trim().padStart(5, '0'))
        } catch (e: Exception) {
            throw noTime
        }
        val utc = Instant.ofEpochSecond(unix)
        val utcLocal = LocalDateTime.ofInstant(utc, ZoneOffset.UTC)
        val local = (-1L..1L)
            .map { LocalDateTime.of(utcLocal.toLocalDate().plusDays(it), localTime) }
            .minByOrNull { abs(ChronoUnit.SECONDS.between(utcLocal, it)) }!!
        val offset = DeviceClock.offsetSeconds(local, utc) ?: throw noTime
        return DeviceClock.zoneOf(offset, ZoneId.systemDefault(), utc)
    }

    // --- HTTP helpers --------------------------------------------------------

    private const val MAX_RULES = 20
    private const val KVS_KEY = "spotpriceplugs_jobs"

    /** Error codes meaning "no such method/service": 404 = no handler, -114 = unavailable. */
    private val KVS_UNAVAILABLE = setOf(404, -114)

    /** Gen2 timespec weekday names, index 0 = Sunday (as in Tasmota masks). */
    private val CRON_DAYS = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

    /** An RPC error reply; the message is ready for the user. */
    private class RpcException(val code: Int, message: String) : IllegalStateException(message)

    /** A non-blank string field, or null (org.json returns "null" for JSON null). */
    private fun text(o: JSONObject, key: String): String? {
        if (!o.has(key) || o.isNull(key)) return null
        return o.optString(key).takeIf { it.isNotBlank() }
    }

    private fun getJson(url: String, connectMs: Int = 900, readMs: Int = 1800): JSONObject? {
        val (code, body) = request("GET", url, null, connectMs, readMs) ?: return null
        if (code !in 200..299) return null
        return try { JSONObject(body) } catch (e: Exception) { null }
    }

    /**
     * Gen2/Gen3 JSON-RPC call; returns the unwrapped `result` (an empty
     * object when the method returns null). No reply → IllegalStateException
     * (e_no_reply); an `error` reply → [RpcException] (e_rejected).
     */
    private fun rpc(
        ip: String, method: String, params: JSONObject?,
        connectMs: Int = 3000, readMs: Int = 7000
    ): JSONObject {
        val frame = JSONObject()
            .put("id", 1)
            .put("src", "spotpriceplugs")
            .put("method", method)
        if (params != null) frame.put("params", params)
        // The plug's small web server sometimes drops a connection (or the
        // phone's Wi-Fi is still waking up in a background run): retry a few
        // times. Not for Schedule.Create — a reply lost after the job was made
        // would create a duplicate job the app does not know about.
        val attempts = if (method == "Schedule.Create") 1 else 3
        var exchange: Pair<Int, String>? = null
        for (attempt in 1..attempts) {
            exchange = request("POST", "http://$ip/rpc", frame, connectMs, readMs)
            if (exchange != null) break
            if (attempt < attempts) Thread.sleep(400L * attempt)
        }
        val (code, body) = exchange
            ?: throw IllegalStateException(msg(R.string.e_no_reply, ip, method))
        val reply = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw IllegalStateException(msg(R.string.e_no_reply, ip, "$method (HTTP $code)"))
        }
        // An error comes as a frame {"error":{code,message}} or, on some
        // paths/firmwares, as a bare {"code","message"} with an HTTP error status.
        val error = reply.optJSONObject("error")
            ?: if (code !in 200..299 || (reply.has("code") && reply.has("message") && !reply.has("result"))) reply else null
        if (error != null) {
            val errCode = error.optInt("code", code)
            val reason = text(error, "message") ?: "HTTP $code"
            throw RpcException(errCode, msg(R.string.e_rejected, ip, "$method: $reason"))
        }
        return reply.optJSONObject("result") ?: JSONObject()
    }

    /** Raw HTTP exchange: (status code, body) — the error body too — or null when unreachable. */
    private fun request(
        verb: String, url: String, body: JSONObject?,
        connectMs: Int, readMs: Int
    ): Pair<Int, String>? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = verb
                connectTimeout = connectMs
                readTimeout = readMs
                setRequestProperty("User-Agent", "spotpriceplugs-android")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            code to text
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
