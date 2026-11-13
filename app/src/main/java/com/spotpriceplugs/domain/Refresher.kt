package com.spotpriceplugs.domain

import android.content.Context
import com.spotpriceplugs.R
import com.spotpriceplugs.msg
import com.spotpriceplugs.data.NordPool
import com.spotpriceplugs.data.Store
import com.spotpriceplugs.model.DeviceName
import com.spotpriceplugs.model.Plug
import com.spotpriceplugs.model.PlugKind
import com.spotpriceplugs.net.OpenBeken
import com.spotpriceplugs.net.Shelly
import com.spotpriceplugs.net.Tasmota
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException

/**
 * One refresh pass: builds a plan for every saved plug and writes it to the
 * timers. Used both by the background job and by the UI button.
 */
object Refresher {

    data class Outcome(
        val message: String,
        val okCount: Int,
        val failCount: Int,
        val nextAt: Long
    )

    /**
     * Process-wide: the UI button, the expedited work and the fallback work may
     * all start a run; they are executed one after another.
     */
    private val lock = Mutex()

    /**
     * [only]: just these plugs (by IP); null = all controlled plugs.
     *
     * [background]: a scheduled run rather than a button. It only handles the
     * plugs whose time has come ([Plug.nextAt]) and leaves a plug alone when
     * the timers it would write are the ones already there, so neither the
     * plug's flash nor a relay the user switched by hand is touched without
     * need.
     */
    suspend fun run(
        context: Context,
        only: Set<String>? = null,
        background: Boolean = false,
        progress: (String) -> Unit = {}
    ): Outcome = lock.withLock { runLocked(context, only, background, progress) }

    private suspend fun runLocked(
        context: Context,
        only: Set<String>?,
        background: Boolean,
        progress: (String) -> Unit
    ): Outcome {
        val store = Store(context)
        val now = ZonedDateTime.now(NordPool.zone)
        val nowMillis = now.toInstant().toEpochMilli()
        val daily = nextDaily(now, store.refreshHour, store.refreshMinute)
        val dailyMillis = daily.toInstant().toEpochMilli()
        val targets = store.plugs.filter { plug ->
            plug.managed &&
                (only == null || plug.ip in only) &&
                (!background || plug.nextAt <= nowMillis + DUE_SLACK_MS)
        }

        if (targets.isEmpty()) {
            // A background run with nothing due is not worth a line in the header.
            if (background) return Outcome("", 0, 0, 0)
            store.lastRun = System.currentTimeMillis()
            store.lastRunInfo = msg(R.string.r_no_plugs)
            return Outcome(store.lastRunInfo, 0, 0, 0)
        }

        var ok = 0
        var fail = 0
        var plansBuilt = 0
        var daysCovered = 0
        // Per plug: the refresh-owned fields to put into the stored plug.
        val updates = LinkedHashMap<String, Update>()

        targets.forEachIndexed { index, plug ->
            val label = plug.displayName
            progress("${index + 1}/${targets.size}: $label")

            // Area: from the device name first, then from settings.
            val region = plug.region.ifBlank { store.defaultRegion.uppercase() }

            if (region.isBlank()) {
                fail++
                // Only the user can fix this: no point in waking up hourly for it.
                updates[plug.ip] = Update(lastError = msg(R.string.r_no_region), nextAt = dailyMillis)
                return@forEachIndexed
            }

            var mac = ""
            var kind = plug.kind
            try {
                // The address may have been handed to another device since the
                // scan: never write timers to a plug that is not this one.
                val isShelly = PlugKind.isShelly(plug.kind)
                val info = if (isShelly) null else Tasmota.status(plug.ip)
                mac = DeviceName.normalizeMac(
                    if (isShelly) Shelly.mac(plug.ip, plug.kind) else info?.mac
                )
                // OpenBeken answers like a Tasmota and may have been saved as
                // one, but its schedule is written differently.
                if (info != null) {
                    kind = if (info.openBeken) PlugKind.OPENBEKEN else PlugKind.TASMOTA
                }
                val isOpenBeken = kind == PlugKind.OPENBEKEN
                if (plug.mac.isNotBlank() && mac.isNotBlank() && mac != plug.mac) {
                    throw IllegalStateException(msg(R.string.e_wrong_device, plug.ip))
                }

                val plans = Planner.build(
                    context, region, Vat.effective(region, store.vat), plug.hours, plug.limit, now,
                    doneToday = doneToday(plug.planJson, now)
                )
                if (plans.isEmpty()) {
                    throw IllegalStateException(msg(R.string.r_no_prices))
                }
                plansBuilt++
                daysCovered = maxOf(daysCovered, plans.size)

                val blocks = plans.flatMap { it.blocks }
                val planned = blocks.any { !now.isBefore(it.start) && now.isBefore(it.end) }
                // A plug the user paused stays paused: the new schedule is
                // written (so resuming uses fresh timers), but the timers stay
                // off and the relay is left as it is.
                val paused = !plug.timersOn

                // Power: the user's value from the card wins. Without it, the
                // highest reading of a Shelly's own meter is used: one reading
                // may catch the load idling (a boiler whose thermostat is off).
                val state = if (isShelly) Shelly.readState(plug.ip, plug.kind) else null
                val autoWatts = maxOf(plug.autoWatts, state?.second ?: 0.0)
                val effectiveWatts = if (plug.watts > 0.0) plug.watts else autoWatts

                val schedule = Planner.encodeBlocks(blocks)
                // An OpenBeken may have rebooted, or the clocks have changed.
                val unchanged = background && plug.applied.isNotBlank() &&
                    schedule == Planner.stillAhead(plug.applied, nowMillis) &&
                    !(isOpenBeken && OpenBeken.needsRewrite(plug.ip, plug.appliedAt))

                val power: Boolean
                var persisted = plug.persisted
                if (unchanged) {
                    // The timers on the plug already say this: write nothing.
                    power = state?.first ?: plug.power
                } else if (isShelly) {
                    Shelly.apply(
                        plug.ip, plug.kind, blocks,
                        powerOn = if (paused) null else planned,
                        enabled = !paused
                    )
                    power = if (paused) state?.first ?: plug.power else planned
                } else {
                    if (isOpenBeken) {
                        persisted = OpenBeken.apply(plug.ip, blocks, planned, timersOn = !paused)
                    } else {
                        Tasmota.apply(plug.ip, blocks, planned, timersOn = !paused)
                    }
                    power = if (paused) Tasmota.readPower(plug.ip) ?: plug.power else planned
                }

                // The card builds the summary ("on 8 h · avg …") from planJson itself,
                // so it is always in the current UI language.

                // The plan covers whole days even when no cheap slots are left.
                // Recalculate at the end of coverage or at the daily price
                // publication time, whichever comes first.
                val coverage = plans.last().day.plusDays(1)
                    .atStartOfDay(NordPool.zone)
                    .toInstant().toEpochMilli()
                // An OpenBeken whose schedule could not be put into its boot
                // script is looked at more often: a reboot wipes the
                // schedule, and only the next run can put it back.
                val hourly = isOpenBeken && !persisted && !paused
                val nextAt = minOf(
                    coverage, dailyMillis,
                    if (hourly) nowMillis + OPENBEKEN_CHECK_MS else Long.MAX_VALUE
                )

                ok++
                updates[plug.ip] = Update(
                    lastError = "",
                    nextAt = nextAt,
                    mac = mac,
                    kind = kind,
                    success = Success(
                        written = !unchanged,
                        power = power,
                        timersOn = !paused,
                        planJson = planJson(plans),
                        autoWatts = autoWatts,
                        persisted = persisted,
                        applied = schedule
                    )
                )
                if (effectiveWatts > 0.0) {
                    // Plans cover whole days, so this is the full-day figure
                    // however late in the day the plan is rebuilt.
                    plans.forEach { plan ->
                        val euros = Planner.dailyEuros(listOf(plan), effectiveWatts)
                        if (euros > 0.0) store.recordSavings(plug.ip, plan.day, euros)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail++
                updates[plug.ip] = Update(
                    lastError = e.message ?: e.javaClass.simpleName,
                    nextAt = now.plusHours(1).toInstant().toEpochMilli(),
                    mac = mac,
                    kind = kind
                )
            }
        }

        // Tomorrow's prices appear around 13:00 CET. If the plan only covers
        // today, it is worth checking again in 15 minutes (at most 5 times a day).
        // Only a run that did get prices says anything about tomorrow.
        val today = now.toLocalDate().toString()
        if (store.retryDay != today) {
            store.retryDay = today
            store.retryCount = 0
        }
        val waitingForTomorrow = plansBuilt > 0 && daysCovered < 2 && now.hour in 12..19
        if (waitingForTomorrow && store.retryCount < 5) {
            store.retryAt = System.currentTimeMillis() + 15 * 60_000L
            store.retryCount += 1
        } else if (plansBuilt > 0) {
            store.retryAt = 0L
            if (daysCovered >= 2) store.retryCount = 0
        }

        val retryAt = store.retryAt
        if (retryAt > System.currentTimeMillis()) {
            for ((ip, u) in updates.entries.toList()) {
                if (u.success != null && u.nextAt > retryAt) updates[ip] = u.copy(nextAt = retryAt)
            }
        }

        // Merge into the plugs as they are now: the user may have renamed,
        // edited or removed a plug while the run was talking to the network.
        val applied = System.currentTimeMillis()
        store.mutatePlugs { saved ->
            saved.map { plug -> updates[plug.ip]?.applyTo(plug, applied) ?: plug }
        }

        store.lastRun = System.currentTimeMillis()
        val message = when {
            fail == 0 -> msg(R.string.r_ok, ok)
            ok == 0 -> msg(R.string.r_fail, fail)
            else -> msg(R.string.r_mixed, ok, fail)
        }
        store.lastRunInfo = message
        progress("")
        return Outcome(
            message, ok, fail,
            updates.values.minOfOrNull { it.nextAt } ?: 0L
        )
    }

    /** A plug is due when its time is at most this far ahead (alarms are not to the second). */
    private const val DUE_SLACK_MS = 2 * 60_000L

    /** How often a background run checks an OpenBeken that cannot keep its schedule over a reboot. */
    private const val OPENBEKEN_CHECK_MS = 60 * 60_000L

    /** Fields a refresh owns; name, hours, limit, region etc. belong to the user. */
    private data class Update(
        val lastError: String,
        val nextAt: Long,
        /** MAC read from the device during this run ("" = not read). */
        val mac: String = "",
        /** Device type as detected during this run (null = not detected). */
        val kind: Int? = null,
        val success: Success? = null
    ) {
        fun applyTo(plug: Plug, applied: Long): Plug {
            val base = plug.copy(
                lastApplied = applied,
                lastError = lastError,
                nextAt = nextAt,
                // Learn the MAC of a plug saved before MACs were kept.
                mac = plug.mac.ifBlank { mac },
                kind = kind ?: plug.kind
            )
            val s = success ?: return base
            return base.copy(
                lastSummary = "",
                power = s.power,
                timersOn = s.timersOn,
                planJson = s.planJson,
                autoWatts = s.autoWatts,
                persisted = s.persisted,
                applied = s.applied,
                appliedAt = if (s.written) applied else plug.appliedAt
            )
        }
    }

    private data class Success(
        /** false = the timers were already there and nothing was written. */
        val written: Boolean,
        val power: Boolean,
        val timersOn: Boolean,
        val planJson: String,
        /** Highest meter reading so far, W (0 = none). */
        val autoWatts: Double,
        /** See [Plug.persisted]. */
        val persisted: Boolean,
        /** The timer blocks now on the plug, see [Planner.encodeBlocks]. */
        val applied: String
    )

    /**
     * Slot starts of today that the plug's previous plan switched on, or null
     * when that plan does not cover today (first run, or a plan from another day).
     */
    internal fun doneToday(planJson: String, now: ZonedDateTime): Set<Instant>? {
        val plan = parsePlan(planJson) ?: return null
        val today = now.toLocalDate()
        val todays = plan.slots.filter {
            Instant.ofEpochMilli(it.first).atZone(now.zone).toLocalDate() == today
        }
        if (todays.isEmpty()) return null
        return todays.filter { plan.isOnAt(it.first) }
            .map { Instant.ofEpochMilli(it.first) }
            .toSet()
    }

    /** Next daily run at the given local time. */
    fun nextDaily(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val today = now.toLocalDate().atTime(h, m).atZone(NordPool.zone)
        return if (today.isAfter(now.plusMinutes(1))) {
            today
        } else {
            now.toLocalDate().plusDays(1).atTime(h, m).atZone(NordPool.zone)
        }
    }

    /** Blocks and prices of the latest plan, for the card chart. */
    private fun planJson(plans: List<Planner.DayPlan>): String {
        val blocks = JSONArray()
        // Whole days, elapsed blocks included, so the chart and the savings
        // shown on the card match the full-day plan.
        plans.flatMap { it.shown }.forEach { (start, end) ->
            blocks.put(
                JSONObject()
                    .put("s", start.toInstant().toEpochMilli())
                    .put("e", end.toInstant().toEpochMilli())
            )
        }
        val slots = JSONArray()
        plans.forEach { plan ->
            plan.slots.forEach { slot ->
                slots.put(
                    JSONObject()
                        .put("t", slot.start.toInstant().toEpochMilli())
                        .put("p", slot.price)
                )
            }
        }
        return JSONObject().put("blocks", blocks).put("slots", slots).toString()
    }
}
