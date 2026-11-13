package com.spotpriceplugs.data

import com.spotpriceplugs.R
import com.spotpriceplugs.msg
import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PriceError(message: String) : Exception(message)

/**
 * Nord Pool prices with a cache: several plugs in the same area share one
 * response, so the server is not hammered.
 */
object NordPool {
    private const val API = "https://dataportal-api.nordpoolgroup.com/api/DayAheadPrices"
    private const val UA = "spotpriceplugs-android/1.0"
    private const val TTL_PARTIAL = 30 * 60 * 1000L
    private const val TTL_EMPTY = 5 * 60 * 1000L

    /** A CET day has 92, 96 or 100 quarter-hour slots; fewer means not fully published. */
    private const val FULL_DAY = 92
    private const val KEEP_DAYS = 3L

    /**
     * Phone time zone: days, "today" and all labels use it. Read on every use,
     * so a time zone change is picked up without restarting the process.
     */
    val zone: ZoneId get() = ZoneId.systemDefault()

    /** Nord Pool numbers delivery days in CET/CEST. */
    private val CET: ZoneId = ZoneId.of("Europe/Brussels")

    data class Slot(val start: ZonedDateTime, val end: ZonedDateTime, val price: Double)

    private data class Cached(val at: Long, val prices: Map<Instant, Double>)

    /** Shared by parallel IO-thread calls, hence concurrent. */
    private val memory = ConcurrentHashMap<String, Cached>()

    /**
     * Delivery-area code used by the data portal API. Users and device names
     * say "DE"; the API calls Germany "GER" (and answers "DE" with 204).
     */
    internal fun apiArea(area: String): String = when (area.uppercase()) {
        "DE" -> "GER"
        else -> area.uppercase()
    }

    /** All 15-minute slots of the local day `day` (92/96/100 of them). */
    suspend fun slotsForDay(
        context: Context,
        area: String,
        day: LocalDate,
        vat: Double
    ): List<Slot> = withContext(Dispatchers.IO) {
        val start = day.atStartOfDay(zone)
        val end = day.plusDays(1).atStartOfDay(zone)

        // Fetch every CET day the local day overlaps: east of CET that is usually
        // two, and this works in any time zone.
        val raw = HashMap<Instant, Double>()
        val firstCet = start.withZoneSameInstant(CET).toLocalDate()
        val lastCet = end.minusMinutes(1).withZoneSameInstant(CET).toLocalDate()
        var cet = firstCet
        while (!cet.isAfter(lastCet)) {
            raw.putAll(cetDay(context, area, cet))
            cet = cet.plusDays(1)
        }

        val slots = ArrayList<Slot>()
        var t = start
        var missing = 0
        while (t.isBefore(end)) {
            val price = raw[t.toInstant()]
            if (price == null) {
                missing++
            } else {
                val kwh = (price / 1000.0 * (1.0 + vat / 100.0) * 100000.0).roundToLong() / 100000.0
                slots.add(Slot(t, t.plusMinutes(15), kwh))
            }
            t = t.plusMinutes(15)
        }
        if (missing > 0) {
            throw PriceError(msg(R.string.e_incomplete, day.toString(), missing))
        }
        slots
    }

    /**
     * Prices of one CET delivery day. A complete day never changes once
     * published, so it is served from the cache without asking again; an empty
     * or partial answer (not published yet) is retried after a short while.
     * When the server cannot be reached, whatever is cached is still used.
     */
    private fun cetDay(context: Context, area: String, cetDay: LocalDate): Map<Instant, Double> {
        val key = "$area|$cetDay"
        val file = File(context.cacheDir, "np_${area}_$cetDay.json")
        val cached = memory[key] ?: readFile(file)?.also { memory[key] = it }
        if (cached != null && isFresh(cached)) return cached.prices

        val fetched = try {
            fetch(area, cetDay)
        } catch (e: Exception) {
            if (cached != null && cached.prices.isNotEmpty()) return cached.prices
            throw e
        }
        val entry = Cached(System.currentTimeMillis(), fetched)
        memory[key] = entry
        writeFile(file, entry)
        prune(context, cetDay)
        return fetched
    }

    private fun isFresh(cached: Cached): Boolean {
        if (cached.prices.size >= FULL_DAY) return true
        val ttl = if (cached.prices.isEmpty()) TTL_EMPTY else TTL_PARTIAL
        return System.currentTimeMillis() - cached.at < ttl
    }

    /** Drops cached days more than [KEEP_DAYS] older than the day just fetched. */
    private fun prune(context: Context, fetchedDay: LocalDate) {
        val oldest = fetchedDay.minusDays(KEEP_DAYS)
        fun stale(day: String) = try {
            LocalDate.parse(day).isBefore(oldest)
        } catch (e: Exception) {
            true
        }
        memory.keys.removeIf { stale(it.substringAfterLast('|')) }
        context.cacheDir
            .listFiles { f -> f.name.startsWith("np_") && f.name.endsWith(".json") }
            ?.filter { stale(it.name.removeSuffix(".json").substringAfterLast('_')) }
            ?.forEach { it.delete() }
    }

    private fun fetch(area: String, cetDay: LocalDate): Map<Instant, Double> {
        val code = apiArea(area)
        val url = URL("$API?date=$cetDay&market=DayAhead&deliveryArea=$code&currency=EUR")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", UA)
        try {
            if (conn.responseCode == 204) return emptyMap()
            if (conn.responseCode !in 200..299) {
                throw PriceError(msg(R.string.e_http, "Nord Pool", conn.responseCode))
            }
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (text.isBlank()) return emptyMap()
            val root = JSONObject(text)
            val entries = root.optJSONArray("multiAreaEntries") ?: return emptyMap()
            val out = HashMap<Instant, Double>()
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val start = item.optString("deliveryStart")
                val per = item.optJSONObject("entryPerArea") ?: continue
                if (start.isBlank() || !per.has(code)) continue
                out[Instant.parse(start)] = per.getDouble(code)
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    private fun readFile(file: File): Cached? {
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())
            val at = root.getLong("at")
            val obj = root.getJSONObject("entries")
            val map = HashMap<Instant, Double>()
            obj.keys().forEach { k -> map[Instant.ofEpochSecond(k.toLong())] = obj.getDouble(k) }
            Cached(at, map)
        } catch (e: Exception) {
            null
        }
    }

    private fun writeFile(file: File, entry: Cached) {
        try {
            val obj = JSONObject()
            entry.prices.forEach { (instant, price) -> obj.put(instant.epochSecond.toString(), price) }
            val root = JSONObject().put("at", entry.at).put("entries", obj)
            file.writeText(root.toString())
        } catch (_: Exception) {
        }
    }

    fun clearCache(context: Context) {
        memory.clear()
        context.cacheDir.listFiles { f -> f.name.startsWith("np_") }?.forEach { it.delete() }
    }
}
