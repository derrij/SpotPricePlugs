package com.spotpriceplugs.domain

import com.spotpriceplugs.R
import com.spotpriceplugs.str
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

private const val SLOT_MS = 15 * 60_000L

/** Parsed plan from Plug.planJson, for the chart, summary and "now" line. */
data class PlanView(
    /** On-blocks as epoch milliseconds [start, end). */
    val blocks: List<Pair<Long, Long>>,
    /** All plan slots: start millisecond → price, EUR/kWh incl. VAT. */
    val slots: List<Pair<Long, Double>>
) {
    val minPrice: Double get() = slots.minOfOrNull { it.second } ?: 0.0
    val maxPrice: Double get() = slots.maxOfOrNull { it.second } ?: 1.0

    /** Slots that fall inside on-blocks. */
    val chosen: List<Pair<Long, Double>> by lazy { slots.filter { isOnAt(it.first) } }

    /** Minutes the plug is on according to the plan. */
    val onMinutes: Int get() = chosen.size * 15

    val avgChosen: Double?
        get() = if (chosen.isEmpty()) null else chosen.sumOf { it.second } / chosen.size

    /** How much cheaper the chosen slots are than the plan average, %. */
    val cheaperPercent: Double?
        get() {
            val avgAll = slots.sumOf { it.second } / slots.size.coerceAtLeast(1)
            val avg = avgChosen ?: return null
            return if (avgAll > 0.0) (avgAll - avg) / avgAll * 100.0 else null
        }

    /**
     * Savings in euros: for each chosen slot, (its day's average price − slot
     * price) × 0.25 h × kW. Same formula as Planner.dailyEuros.
     */
    fun euros(watts: Double, zone: ZoneId = ZoneId.systemDefault()): Double {
        if (watts <= 0.0) return 0.0
        val kw = watts / 1000.0
        return slots.groupBy { Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate() }
            .values.sumOf { day ->
                val avgDay = day.sumOf { it.second } / day.size
                day.filter { isOnAt(it.first) }
                    .sumOf { (avgDay - it.second) * kw * 0.25 }
                    .coerceAtLeast(0.0)
            }
    }

    /**
     * On-time split by day: "on: today 1 h 30 min / tomorrow 4 h".
     * Days are named relative to [nowMillis], so the label stays right after midnight.
     */
    fun onTimeText(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val perDay = slots
            .groupBy { Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate() }
            .toSortedMap()
            .filterKeys { !it.isBefore(today) }
        val parts = perDay.map { (day, daySlots) ->
            val time = minutesText(daySlots.count { isOnAt(it.first) } * 15)
            when (day) {
                today -> str(R.string.day_today, time)
                today.plusDays(1) -> str(R.string.day_tomorrow, time)
                else -> str(R.string.day_date, "%02d.%02d".format(day.dayOfMonth, day.monthValue), time)
            }
        }
        return str(R.string.sum_on, parts.joinToString(" / ").ifEmpty { minutesText(0) })
    }

    /** "avg 0.263 €/kWh · 26% cheaper · saves ≈ 0.53 €"; null when nothing is switched on. */
    fun priceSummary(watts: Double): String? {
        if (onMinutes == 0) return null
        return buildList {
            avgChosen?.let { add(str(R.string.sum_avg, "%.3f".format(it))) }
            cheaperPercent?.takeIf { it >= 1.0 }?.let {
                add(str(R.string.sum_cheaper, "%.0f".format(it)))
            }
            val eur = euros(watts)
            if (eur >= 0.005) add(str(R.string.sum_saves, eurosText(eur)))
        }.joinToString(" · ").ifEmpty { null }
    }
}

fun parsePlan(json: String): PlanView? {
    if (json.isBlank()) return null
    return try {
        val root = JSONObject(json)
        val blocks = ArrayList<Pair<Long, Long>>()
        val bArr = root.optJSONArray("blocks") ?: JSONArray()
        for (i in 0 until bArr.length()) {
            val b = bArr.getJSONObject(i)
            blocks.add(b.getLong("s") to b.getLong("e"))
        }
        val slots = ArrayList<Pair<Long, Double>>()
        val sArr = root.optJSONArray("slots") ?: JSONArray()
        for (i in 0 until sArr.length()) {
            val s = sArr.getJSONObject(i)
            slots.add(s.getLong("t") to s.getDouble("p"))
        }
        if (blocks.isEmpty() || slots.isEmpty()) null else PlanView(blocks, slots)
    } catch (e: Exception) {
        null
    }
}

/** Whether the moment lies inside any on-block. */
fun PlanView.isOnAt(millis: Long): Boolean =
    blocks.any { millis >= it.first && millis < it.second }

/**
 * Label for the next switch:
 *   "ON for 1 h 15 min more", "ON in 20 min", "ON tomorrow at 10:00".
 * null when the plan does not cover the moment.
 */
fun PlanView.switchText(nowMillis: Long): String? {
    val current = blocks.firstOrNull { nowMillis >= it.first && nowMillis < it.second }
    if (current != null) {
        val minutes = ((current.second - nowMillis) / 60_000L).toInt().coerceAtLeast(1)
        return str(R.string.sw_on_left, minutesText(minutes))
    }
    val next = blocks.filter { it.first > nowMillis }.minByOrNull { it.first }
        ?: return null
    val minutes = ((next.first - nowMillis) / 60_000L).toInt().coerceAtLeast(1)
    if (minutes < 12 * 60) return str(R.string.sw_on_in, minutesText(minutes))
    val zone = ZoneId.systemDefault()
    val nowDay = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val nextDay = Instant.ofEpochMilli(next.first).atZone(zone).toLocalDate()
    val at = "%02d:%02d".format(
        Instant.ofEpochMilli(next.first).atZone(zone).hour,
        Instant.ofEpochMilli(next.first).atZone(zone).minute
    )
    return str(if (nextDay != nowDay) R.string.sw_on_tomorrow else R.string.sw_on_today, at)
}

/** Price of the current slot, if the plan covers it. */
fun PlanView.priceAt(millis: Long): Double? =
    slots.lastOrNull { it.first <= millis && millis < it.first + SLOT_MS }?.second
