package com.spotpriceplugs.domain

import android.content.Context
import com.spotpriceplugs.data.NordPool
import com.spotpriceplugs.data.NordPool.Slot
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * Planner: today + tomorrow once prices exist, the cheapest `hours` hours of
 * each whole day, never above `limit`. Only the part of today that is still
 * ahead is written into the timers.
 *
 * Tasmota weekday masks: 7 characters starting with Sunday.
 */
object Planner {
    const val MAX_TIMERS = 16
    const val MAX_BLOCKS = 8

    data class Block(
        val start: ZonedDateTime,
        val end: ZonedDateTime,
        val onDays: String,
        val offDays: String
    )

    data class DayPlan(
        val day: LocalDate,
        val slots: List<Slot>,
        val chosen: List<Slot>,
        /** Timer blocks still to come (see [plan]). */
        val blocks: MutableList<Block>,
        val limit: Double,
        /** On-blocks of the whole day, elapsed ones included: for the chart and savings. */
        val shown: List<Pair<ZonedDateTime, ZonedDateTime>> = emptyList()
    ) {
        val minutes: Int get() = chosen.size * 15
        val avg: Double get() = if (chosen.isEmpty()) 0.0 else chosen.sumOf { it.price } / chosen.size
        val maxPrice: Double get() = chosen.maxOfOrNull { it.price } ?: 0.0
        val eligible: Int get() = slots.count { it.price <= limit + 1e-9 }
    }

    fun floorSlot(t: ZonedDateTime): ZonedDateTime =
        t.withMinute((t.minute / 15) * 15).withSecond(0).withNano(0)

    /** Position 0 is Sunday, as in Tasmota firmware (SMTWTFS). */
    fun dayMask(day: LocalDate): String {
        val index = day.dayOfWeek.value % 7
        return (0..6).joinToString("") { if (it == index) "1" else "0" }
    }

    fun maskExcept(day: LocalDate): String =
        dayMask(day).map { if (it == '1') '0' else '1' }.joinToString("")


    /**
     * How much lower the average price of the chosen slots is than the average
     * over the whole plan, in %. null when there is nothing to compare.
     */
    fun savingsPercent(plans: List<DayPlan>): Double? {
        var all = 0.0
        var chosen = 0.0
        var nAll = 0
        var nChosen = 0
        plans.forEach { p ->
            p.slots.forEach { all += it.price; nAll++ }
            p.chosen.forEach { chosen += it.price; nChosen++ }
        }
        if (nAll == 0 || nChosen == 0) return null
        val avgAll = all / nAll
        val avgChosen = chosen / nChosen
        if (avgAll <= 0.0) return null
        return (avgAll - avgChosen) / avgAll * 100.0
    }

    /**
     * Expected savings in euros per day: the gap between the day's average price
     * and each chosen slot's price, multiplied by the load power.
     */
    fun dailyEuros(plans: List<DayPlan>, watts: Double): Double {
        if (watts <= 0.0) return 0.0
        val kw = watts / 1000.0
        return plans.sumOf { p ->
            val avgAll = if (p.slots.isEmpty()) 0.0 else p.slots.sumOf { it.price } / p.slots.size
            p.chosen.sumOf { (avgAll - it.price) * kw * 0.25 }.coerceAtLeast(0.0)
        }
    }

    suspend fun build(
        context: Context,
        area: String,
        vat: Double,
        hours: Double,
        limit: Double,
        now: ZonedDateTime,
        horizonDays: Int = 2,
        doneToday: Set<Instant>? = null
    ): List<DayPlan> {
        val days = ArrayList<Pair<LocalDate, List<Slot>>>()

        for (i in 0 until horizonDays.coerceAtLeast(1)) {
            val day = now.toLocalDate().plusDays(i.toLong())
            val slots = try {
                NordPool.slotsForDay(context, area, day, vat)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (i == 0) throw e
                break
            }
            if (slots.isNotEmpty()) days.add(day to slots)
        }
        return plan(days, hours, limit, now, doneToday)
    }

    /**
     * Pure planning step.
     *
     * Today: [doneToday] holds the slot starts the previous plan chose for
     * today (null = no previous plan covering today). Past slots in it count as
     * already done and only the rest of the quota is picked from the slots still
     * ahead; [DayPlan.slots]/[DayPlan.chosen] then describe the whole day, so
     * savings stay stable when the same day is replanned. Without a previous
     * plan the full quota is picked from the slots ahead (first install, hours
     * changed) and the day starts at the current slot.
     *
     * [DayPlan.blocks] (what is written into the timers) only holds blocks that
     * have not ended by [now]; a block in progress keeps its real start (that
     * time has passed today, so the timer cannot fire twice; the relay state is
     * set separately). [DayPlan.shown] holds all blocks, for the chart.
     */
    internal fun plan(
        days: List<Pair<LocalDate, List<Slot>>>,
        hours: Double,
        limit: Double,
        now: ZonedDateTime,
        doneToday: Set<Instant>? = null
    ): List<DayPlan> {
        val cut = floorSlot(now)
        val today = now.toLocalDate()
        val input = days.mapNotNull { (day, slots) ->
            if (day != today || doneToday != null) day to slots
            else slots.filter { !it.start.isBefore(cut) }.takeIf { it.isNotEmpty() }?.let { day to it }
        }
        return planDays(input, hours, limit, now, if (doneToday == null) emptySet() else doneToday)
    }

    private fun planDays(
        days: List<Pair<LocalDate, List<Slot>>>,
        hours: Double,
        limit: Double,
        now: ZonedDateTime,
        doneToday: Set<Instant>
    ): List<DayPlan> {
        if (days.isEmpty()) return emptyList()
        val cut = floorSlot(now)
        val today = now.toLocalDate()

        // Timer budget: every day may use an equal share, and the last day
        // also gets whatever the earlier ones left unused.
        val perDay = (MAX_BLOCKS / days.size).coerceAtLeast(1)
        var budget = MAX_BLOCKS

        val spare = HashMap<LocalDate, String>()
        days.forEachIndexed { i, (day, _) ->
            spare[day] = when {
                days.size == 1 -> "1111111"
                i < days.size - 1 -> maskExcept(days[i + 1].first)
                else -> dayMask(day)
            }
        }

        val plans = ArrayList<DayPlan>()
        val rawByDay = HashMap<LocalDate, List<Pair<ZonedDateTime, ZonedDateTime>>>()
        for ((day, slots) in days) {
            val done = if (day == today) {
                slots.filter { it.start.isBefore(cut) && it.start.toInstant() in doneToday }
            } else {
                emptyList()
            }
            val isLast = day == days.last().first
            val allowance = (if (isLast) budget else perDay).coerceAtLeast(1)
            val (chosen, raw) = buildPlan(slots, done, cut, hours, limit, allowance)
            budget -= raw.count { it.second.isAfter(cut) }
            plans.add(DayPlan(day, slots, chosen, mutableListOf(), limit, raw))
            rawByDay[day] = raw
        }

        // Merge blocks that cross midnight.
        val merged = ArrayList<Pair<ZonedDateTime, ZonedDateTime>>()
        for ((day, _) in days) {
            for (block in rawByDay[day] ?: emptyList()) {
                if (merged.isNotEmpty() && merged.last().second == block.first) {
                    merged[merged.size - 1] = merged.last().first to block.second
                } else {
                    merged.add(block)
                }
            }
        }

        for ((start, end) in merged) {
            // Already over: it ran (or was due) earlier today, nothing to write.
            if (!end.isAfter(now)) continue
            val block = when {
                days.size == 1 -> Block(start, end, "1111111", "1111111")
                start.toLocalDate() == end.toLocalDate() -> {
                    val mask = spare[start.toLocalDate()] ?: "1111111"
                    Block(start, end, mask, mask)
                }
                else -> Block(start, end, dayMask(start.toLocalDate()), dayMask(end.toLocalDate()))
            }
            val owner = plans.firstOrNull { it.day == start.toLocalDate() } ?: plans.last()
            owner.blocks.add(block)
        }

        return plans
    }

    /**
     * Chooses the cheapest eligible slots from those not before [cut], minus
     * the [done] ones already behind. The slots still ahead form at most
     * [maxBlocks] on-blocks (each block costs two timers on the plug); when
     * the plain cheapest choice is more fragmented than that, the cheapest
     * choice that does fit is taken instead. Nothing above [limit] is ever
     * switched on, and never more than the requested time.
     */
    private fun buildPlan(
        slots: List<Slot>,
        done: List<Slot>,
        cut: ZonedDateTime,
        hours: Double,
        limit: Double,
        maxBlocks: Int
    ): Pair<List<Slot>, List<Pair<ZonedDateTime, ZonedDateTime>>> {
        val need = (hours * 4).roundToInt().coerceAtLeast(1)
        val remaining = (need - done.size).coerceAtLeast(0)
        val eligible = slots
            .filter { !it.start.isBefore(cut) && it.price <= limit + 1e-9 }
            .sortedBy { it.start }
        var picked = eligible
            .sortedWith(compareBy({ it.price }, { it.start }))
            .take(remaining)
        if (runs(picked) > maxBlocks) picked = cheapestInRuns(eligible, remaining, maxBlocks)
        val chosen = (done + picked).sortedBy { it.start }

        val blocks = ArrayList<Pair<ZonedDateTime, ZonedDateTime>>()
        for (slot in chosen) {
            if (blocks.isNotEmpty() && blocks.last().second == slot.start) {
                blocks[blocks.size - 1] = blocks.last().first to slot.end
            } else {
                blocks.add(slot.start to slot.end)
            }
        }
        return chosen to blocks
    }

    /** Number of uninterrupted runs the slots form. */
    private fun runs(slots: List<Slot>): Int {
        var count = 0
        var end: ZonedDateTime? = null
        for (slot in slots.sortedBy { it.start }) {
            if (slot.start != end) count++
            end = slot.end
        }
        return count
    }

    /**
     * The cheapest [count] slots of [eligible] (in time order) that form at
     * most [maxRuns] uninterrupted runs. When that many cannot be fitted, as
     * many as possible are taken, still at the lowest total price.
     *
     * Dynamic programme over (slots seen, slots taken, runs used, last taken):
     * about 100 × 100 × 9 × 2 states for a day of quarter hours.
     */
    internal fun cheapestInRuns(eligible: List<Slot>, count: Int, maxRuns: Int): List<Slot> {
        val m = eligible.size
        val n = minOf(count, m)
        if (n <= 0 || maxRuns <= 0) return emptyList()

        val runsDim = maxRuns + 1
        val perSlot = (n + 1) * runsDim * 2
        fun at(i: Int, taken: Int, used: Int, last: Int) =
            i * perSlot + (taken * runsDim + used) * 2 + last

        val cost = DoubleArray((m + 1) * perSlot) { Double.MAX_VALUE }
        // For each state, the "last taken" flag of the state it came from.
        val from = ByteArray((m + 1) * perSlot)
        cost[at(0, 0, 0, 0)] = 0.0

        for (i in 0 until m) {
            val joins = i > 0 && eligible[i].start == eligible[i - 1].end
            for (taken in 0..minOf(i, n)) {
                for (used in 0..maxRuns) {
                    for (last in 0..1) {
                        val c = cost[at(i, taken, used, last)]
                        if (c == Double.MAX_VALUE) continue
                        val skip = at(i + 1, taken, used, 0)
                        if (c < cost[skip]) {
                            cost[skip] = c
                            from[skip] = last.toByte()
                        }
                        if (taken == n) continue
                        val nextUsed = if (last == 1 && joins) used else used + 1
                        if (nextUsed > maxRuns) continue
                        val take = at(i + 1, taken + 1, nextUsed, 1)
                        val price = c + eligible[i].price
                        if (price < cost[take]) {
                            cost[take] = price
                            from[take] = last.toByte()
                        }
                    }
                }
            }
        }

        // The largest number of slots that fits, at its lowest price.
        var taken = n
        var used = -1
        var last = 0
        while (taken > 0 && used < 0) {
            var best = Double.MAX_VALUE
            for (u in 0..maxRuns) for (l in 0..1) {
                val c = cost[at(m, taken, u, l)]
                if (c < best) {
                    best = c
                    used = u
                    last = l
                }
            }
            if (used < 0) taken--
        }
        if (used < 0) return emptyList()

        val out = ArrayList<Slot>(taken)
        for (i in m downTo 1) {
            val previous = from[at(i, taken, used, last)].toInt()
            if (last == 1) {
                out.add(eligible[i - 1])
                val joins = i > 1 && eligible[i - 1].start == eligible[i - 2].end
                if (!(previous == 1 && joins)) used--
                taken--
            }
            last = previous
        }
        return out.asReversed()
    }

    /**
     * The blocks as one line of text, to remember what was written to a plug:
     * "startMillis,endMillis,onDays,offDays" joined with ';'.
     */
    fun encodeBlocks(blocks: List<Block>): String = blocks.joinToString(";") {
        "${it.start.toInstant().toEpochMilli()},${it.end.toInstant().toEpochMilli()},${it.onDays},${it.offDays}"
    }

    /** [applied] (see [encodeBlocks]) without the blocks that ended by [nowMillis]. */
    fun stillAhead(applied: String, nowMillis: Long): String =
        applied.split(';')
            .filter { entry -> (entry.split(',').getOrNull(1)?.toLongOrNull() ?: 0L) > nowMillis }
            .joinToString(";")
}
