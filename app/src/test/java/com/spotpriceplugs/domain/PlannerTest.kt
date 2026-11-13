package com.spotpriceplugs.domain

import com.spotpriceplugs.data.NordPool.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PlannerTest {
    private val zone = ZoneId.of("Europe/Riga")
    private val today = LocalDate.of(2026, 11, 5) // Thursday
    private val tomorrow = today.plusDays(1) // Friday

    /** 96 slots of [day]; [cheap] maps "HH:mm" slot starts to a price, others cost 0.30. */
    private fun day(day: LocalDate, cheap: Map<String, Double>): Pair<LocalDate, List<Slot>> {
        val start = day.atStartOfDay(zone)
        val slots = (0 until 96).map { i ->
            val t = start.plusMinutes(15L * i)
            val key = "%02d:%02d".format(t.hour, t.minute)
            Slot(t, t.plusMinutes(15), cheap[key] ?: 0.30)
        }
        return day to slots
    }

    private fun range(from: String, to: String, price: Double): Map<String, Double> {
        val (h1, m1) = from.split(":").map { it.toInt() }
        val (h2, m2) = to.split(":").map { it.toInt() }
        val out = LinkedHashMap<String, Double>()
        var m = h1 * 60 + m1
        while (m < h2 * 60 + m2) {
            out["%02d:%02d".format(m / 60, m % 60)] = price
            m += 15
        }
        return out
    }

    private fun at(d: LocalDate, h: Int, m: Int): ZonedDateTime = d.atTime(h, m).atZone(zone)

    /** Slot starts of [d] in [from, to): a previous plan's choice. */
    private fun done(d: LocalDate, from: String, to: String): Set<Instant> =
        range(from, to, 0.0).keys.map { k ->
            val (h, m) = k.split(":").map { it.toInt() }
            at(d, h, m).toInstant()
        }.toSet()

    private val todayCheap = range("02:00", "04:00", 0.05) + range("15:00", "16:00", 0.06)

    @Test fun replanSameDayAddsNothingWhenQuotaAlreadyRan() {
        // (a) 4 h ran 02:00–06:00 from the previous plan; replanned at 14:05.
        val cheap = range("02:00", "06:00", 0.05) + range("15:00", "17:00", 0.06)
        val plans = Planner.plan(
            listOf(day(today, cheap)), 4.0, 1.0, at(today, 14, 5),
            doneToday = done(today, "02:00", "06:00")
        )
        val p = plans.single()
        assertEquals(96, p.slots.size) // whole day
        assertEquals(16, p.chosen.size) // the 4 h already done, nothing new
        assertTrue(p.chosen.all { it.start.isBefore(at(today, 6, 0)) })
        assertTrue(p.blocks.isEmpty())
        assertEquals(listOf(at(today, 2, 0) to at(today, 6, 0)), p.shown)
    }

    @Test fun firstRunLateInTheDayUsesWhatIsLeft() {
        // (b) No previous plan: the full 4 h come from 20:00–24:00.
        val plans = Planner.plan(listOf(day(today, todayCheap)), 4.0, 1.0, at(today, 20, 0))
        val p = plans.single()
        assertEquals(16, p.slots.size)
        assertEquals(16, p.chosen.size)
        assertEquals(
            listOf(at(today, 20, 0) to at(today.plusDays(1), 0, 0)),
            p.blocks.map { it.start to it.end }
        )
    }

    @Test fun firstRunRespectsLimit() {
        // Without a previous plan, cheap morning hours that already passed do not count.
        val plans = Planner.plan(listOf(day(today, todayCheap)), 4.0, 0.10, at(today, 14, 5))
        val p = plans.single()
        assertEquals(4, p.chosen.size) // only 15:00–16:00 is left under the limit
        assertEquals(listOf(at(today, 15, 0) to at(today, 16, 0)), p.blocks.map { it.start to it.end })
    }

    @Test fun hoursRaisedAddsOnlyTheDifference() {
        // (c) 2 h ran 02:00–04:00, hours raised to 4: 2 h more from the future.
        val cheap = range("02:00", "04:00", 0.05) + range("15:00", "17:00", 0.06)
        val plans = Planner.plan(
            listOf(day(today, cheap)), 4.0, 1.0, at(today, 14, 5),
            doneToday = done(today, "02:00", "04:00")
        )
        val p = plans.single()
        assertEquals(16, p.chosen.size)
        assertEquals(listOf(at(today, 15, 0) to at(today, 17, 0)), p.blocks.map { it.start to it.end })
    }

    @Test fun pastSlotsNotInPreviousPlanDoNotCount() {
        // The previous plan chose 15:00–16:00 only; cheap 02:00–04:00 was not run.
        val plans = Planner.plan(
            listOf(day(today, todayCheap)), 1.0, 1.0, at(today, 14, 5),
            doneToday = done(today, "15:00", "16:00")
        )
        val p = plans.single()
        assertEquals(4, p.chosen.size)
        assertEquals(listOf(at(today, 15, 0) to at(today, 16, 0)), p.blocks.map { it.start to it.end })
    }

    @Test fun nothingLeftTodayWhenCheapHoursAreOver() {
        val plans = Planner.plan(
            listOf(day(today, todayCheap)), 3.0, 1.0, at(today, 18, 0),
            doneToday = done(today, "02:00", "04:00") + done(today, "15:00", "16:00")
        )
        assertEquals(12, plans.single().chosen.size)
        assertTrue(plans.single().blocks.isEmpty())
    }

    @Test fun blockInProgressKeepsItsRealStart() {
        // 02:00–03:00 ran; the 03:00 slot is the current one and is picked again.
        val plans = Planner.plan(
            listOf(day(today, todayCheap)), 3.0, 1.0, at(today, 3, 10),
            doneToday = done(today, "02:00", "04:00") + done(today, "15:00", "16:00")
        )
        val blocks = plans.single().blocks.map { it.start to it.end }
        assertEquals(
            listOf(at(today, 2, 0) to at(today, 4, 0), at(today, 15, 0) to at(today, 16, 0)),
            blocks
        )
        assertEquals(12, plans.single().chosen.size)
    }

    @Test fun savingsDoNotShrinkLaterInTheDay() {
        val morning = Planner.plan(listOf(day(today, todayCheap)), 3.0, 1.0, at(today, 0, 0))
        val ran = morning.single().chosen.map { it.start.toInstant() }.toSet()
        val evening = Planner.plan(
            listOf(day(today, todayCheap)), 3.0, 1.0, at(today, 20, 0), doneToday = ran
        )
        assertEquals(morning.single().chosen, evening.single().chosen)
        assertEquals(
            Planner.dailyEuros(morning, 2000.0),
            Planner.dailyEuros(evening, 2000.0),
            1e-9
        )
    }

    @Test fun twoDayMasksAndMidnightMerge() {
        val t = day(today, range("10:00", "11:00", 0.05) + range("23:00", "24:00", 0.04))
        val n = day(tomorrow, range("00:00", "01:00", 0.04) + range("12:00", "13:00", 0.05))
        val plans = Planner.plan(
            listOf(t, n), 2.0, 1.0, at(today, 14, 5), doneToday = done(today, "10:00", "11:00")
        )
        assertEquals(2, plans.size)
        assertEquals(8, plans[0].chosen.size)
        assertEquals(8, plans[1].chosen.size)

        val blocks = plans.flatMap { it.blocks }
        // 10:00–11:00 today is over and not written.
        assertEquals(2, blocks.size)
        val night = blocks[0]
        assertEquals(at(today, 23, 0), night.start)
        assertEquals(at(tomorrow, 1, 0), night.end)
        assertEquals(Planner.dayMask(today), night.onDays) // Thursday
        assertEquals(Planner.dayMask(tomorrow), night.offDays) // Friday
        assertEquals("0000100", night.onDays)
        assertEquals("0000010", night.offDays)

        val noon = blocks[1]
        assertEquals(at(tomorrow, 12, 0), noon.start)
        assertEquals("0000010", noon.onDays)
    }

    @Test fun todaysBlocksSkipTomorrowsWeekday() {
        val t = day(today, range("18:00", "19:00", 0.05))
        val n = day(tomorrow, range("12:00", "13:00", 0.05))
        val plans = Planner.plan(listOf(t, n), 1.0, 1.0, at(today, 14, 5))
        val block = plans[0].blocks.single()
        assertEquals(Planner.maskExcept(tomorrow), block.onDays)
        assertEquals("1111101", block.onDays)
    }

    @Test fun limitIsRespected() {
        val plans = Planner.plan(listOf(day(today, todayCheap)), 6.0, 0.10, at(today, 0, 0))
        assertEquals(12, plans.single().chosen.size) // only 12 slots are under 0.10
    }

    /** Ten single cheap quarter hours, two hours apart: 01:00, 03:00 … 19:00. */
    private val scattered = (0 until 10).associate { "%02d:00".format(1 + 2 * it) to 0.05 }

    @Test fun fragmentedChoiceFitsTheTimerBudget() {
        // The ten cheapest slots would need ten blocks; a plug has room for eight.
        val plans = Planner.plan(listOf(day(today, scattered)), 2.5, 1.0, at(today, 0, 0))
        val p = plans.single()
        assertEquals(10, p.chosen.size) // exactly the requested time, no more
        assertTrue(p.blocks.size <= Planner.MAX_BLOCKS)
        // Cheapest way to do it: eight cheap slots, each of two with a 0.30 neighbour.
        assertEquals(8 * 0.05 + 2 * 0.30, p.chosen.sumOf { it.price }, 1e-9)
        // Every chosen slot is inside a block and nothing else is.
        val inBlocks = p.slots.filter { s -> p.shown.any { !s.start.isBefore(it.first) && s.start.isBefore(it.second) } }
        assertEquals(p.chosen, inBlocks)
    }

    @Test fun fragmentedChoiceNeverGoesAboveTheLimit() {
        // With the cap below 0.30 no gap can be bridged: eight blocks, eight slots.
        val plans = Planner.plan(listOf(day(today, scattered)), 2.5, 0.10, at(today, 0, 0))
        val p = plans.single()
        assertEquals(8, p.chosen.size)
        assertEquals(8, p.blocks.size)
        assertTrue(p.chosen.all { it.price <= 0.10 })
    }

    @Test fun secondDayGetsTheBlocksTheFirstLeftUnused() {
        // Today needs one block, so tomorrow may use seven of the eight.
        val t = day(today, range("18:00", "19:00", 0.05))
        val n = day(tomorrow, scattered)
        val plans = Planner.plan(listOf(t, n), 1.0, 0.10, at(today, 14, 5))
        assertEquals(1, plans[0].blocks.size)
        assertEquals(4, plans[1].chosen.size) // 1 h asked: four single slots fit
        val more = Planner.plan(listOf(t, n), 2.5, 0.10, at(today, 14, 5))
        assertEquals(7, more[1].chosen.size)
        assertEquals(8, more.sumOf { it.blocks.size })
    }

    @Test fun cheapestInRunsPrefersTheCheaperBridge() {
        val slots = day(today, mapOf("01:00" to 0.05, "01:15" to 0.10, "01:30" to 0.05, "05:00" to 0.06)).second
        // Three slots in one run: 01:00–01:45 (0.20) beats anything using 0.30 slots.
        val one = Planner.cheapestInRuns(slots, 3, 1)
        assertEquals(listOf(at(today, 1, 0), at(today, 1, 15), at(today, 1, 30)), one.map { it.start })
        // Two runs allowed: still the bridge — the three cheapest slots are three separate runs.
        val two = Planner.cheapestInRuns(slots, 3, 2)
        assertEquals(0.05 + 0.10 + 0.05, two.sumOf { it.price }, 1e-9)
        assertTrue(Planner.cheapestInRuns(slots, 3, 0).isEmpty())
    }

    @Test fun appliedBlocksAreComparedWithoutTheElapsedOnes() {
        val plans = Planner.plan(listOf(day(today, todayCheap)), 3.0, 1.0, at(today, 0, 0))
        val morning = Planner.encodeBlocks(plans.single().blocks)
        assertEquals(2, morning.split(';').size)
        // At 14:05 the 02:00–04:00 block is over: what is left equals a fresh plan's blocks.
        val now = at(today, 14, 5)
        val later = Planner.plan(
            listOf(day(today, todayCheap)), 3.0, 1.0, now,
            doneToday = plans.single().chosen.map { it.start.toInstant() }.toSet()
        )
        assertEquals(
            Planner.encodeBlocks(later.single().blocks),
            Planner.stillAhead(morning, now.toInstant().toEpochMilli())
        )
        assertEquals("", Planner.stillAhead(morning, at(today, 23, 0).toInstant().toEpochMilli()))
        assertEquals("", Planner.stillAhead("", 0L))
    }
}
