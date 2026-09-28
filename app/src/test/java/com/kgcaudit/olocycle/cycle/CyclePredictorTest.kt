package com.kgcaudit.olocycle.cycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CyclePredictorTest {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun usesDefaultsWithTooFewStarts() {
        val params = CyclePredictor.deriveParams(listOf(d("2026-09-01"), d("2026-09-29")))
        assertEquals(CycleParams.DEFAULT_CYCLE_LENGTH, params.cycleLength)
    }

    @Test
    fun averagesRecentCyclesOnceEnoughHistory() {
        // Gaps: 27, 29, 28 -> average 28.
        val starts = listOf("2026-06-01", "2026-06-28", "2026-07-27", "2026-08-24").map(::d)
        val params = CyclePredictor.deriveParams(starts)
        assertEquals(28, params.cycleLength)
    }

    @Test
    fun ignoresOutlierGaps() {
        // A 120-day gap (missed logging) must not skew the average of ~28.
        val starts = listOf("2026-01-01", "2026-05-01", "2026-05-29", "2026-06-26", "2026-07-24").map(::d)
        val params = CyclePredictor.deriveParams(starts)
        assertTrue("cycle length should stay near 28, was ${params.cycleLength}", params.cycleLength in 27..29)
    }

    @Test
    fun predictsPeriodOvulationAndFertileWindow() {
        val p = CyclePredictor.predictFrom(d("2026-09-01"), CycleParams(28, 5, 14), basedOnActualStart = true)
        assertEquals(d("2026-09-05"), p.periodEnd)          // 5-day period, inclusive
        assertEquals(d("2026-09-29"), p.nextPeriodStart)    // +28
        assertEquals(d("2026-09-15"), p.ovulation)          // next - 14
        assertEquals(d("2026-09-10"), p.fertileStart)       // ovulation - 5
        assertEquals(d("2026-09-16"), p.fertileEnd)         // ovulation + 1
    }

    @Test
    fun currentCycleRollsForwardPastToday() {
        val params = CycleParams(28, 5, 14)
        val cycle = CyclePredictor.currentCycle(listOf(d("2026-01-01")), params, today = d("2026-09-29"))!!
        assertTrue("today must be before the next start", d("2026-09-29").isBefore(cycle.nextPeriodStart))
        assertFalse("today must be within the cycle", d("2026-09-29").isBefore(cycle.periodStart))
        assertFalse("rolled-forward cycle is a prediction", cycle.basedOnActualStart)
    }

    @Test
    fun currentCycleKeepsActualFlagWhileWithinFirstCycle() {
        val cycle = CyclePredictor.currentCycle(listOf(d("2026-09-01")), CycleParams(28, 5, 14), today = d("2026-09-10"))!!
        assertTrue(cycle.basedOnActualStart)
    }

    @Test
    fun nullWhenNoHistory() {
        assertNull(CyclePredictor.currentCycle(emptyList(), CycleParams(), d("2026-09-29")))
    }

    @Test
    fun daysUntilNextPeriodCounts() {
        val p = CyclePredictor.predictFrom(d("2026-09-01"), CycleParams(28, 5, 14), true)
        assertEquals(2, CyclePredictor.daysUntilNextPeriod(p, d("2026-09-27")))
        assertEquals(-1, CyclePredictor.daysUntilNextPeriod(p, d("2026-09-30")))
    }

    @Test
    fun phaseClassification() {
        val p = CyclePredictor.predictFrom(d("2026-09-01"), CycleParams(28, 5, 14), true)
        assertEquals(Phase.PERIOD, CyclePredictor.phaseOf(d("2026-09-02"), p))
        assertEquals(Phase.FOLLICULAR, CyclePredictor.phaseOf(d("2026-09-08"), p))
        assertEquals(Phase.FERTILE, CyclePredictor.phaseOf(d("2026-09-11"), p))
        assertEquals(Phase.OVULATION, CyclePredictor.phaseOf(d("2026-09-15"), p))
        assertEquals(Phase.LUTEAL, CyclePredictor.phaseOf(d("2026-09-20"), p))
        assertEquals(Phase.PMS, CyclePredictor.phaseOf(d("2026-09-27"), p))
    }

    @Test
    fun predictedPeriodPhaseForProjectedCycle() {
        val p = CyclePredictor.predictFrom(d("2026-09-29"), CycleParams(28, 5, 14), basedOnActualStart = false)
        assertEquals(Phase.PREDICTED_PERIOD, CyclePredictor.phaseOf(d("2026-09-30"), p))
    }

    @Test
    fun phaseForDayColorsEveryRecordedPeriod() {
        val params = CycleParams(28, 5, 14)
        val starts = listOf(d("2026-09-02"), d("2026-09-28"))
        // Both recorded periods are PERIOD, in their own 5-day windows.
        assertEquals(Phase.PERIOD, CyclePredictor.phaseForDay(d("2026-09-02"), starts, params))
        assertEquals(Phase.PERIOD, CyclePredictor.phaseForDay(d("2026-09-06"), starts, params))
        assertEquals(Phase.PERIOD, CyclePredictor.phaseForDay(d("2026-09-28"), starts, params))
        assertEquals(Phase.PERIOD, CyclePredictor.phaseForDay(d("2026-10-02"), starts, params))
        // A gap day between them is not period.
        assertEquals(Phase.UNKNOWN, CyclePredictor.phaseForDay(d("2026-08-31"), starts, params)) // before first
    }

    @Test
    fun phaseForDayPredictsFutureCyclePeriod() {
        val params = CycleParams(28, 5, 14)
        val starts = listOf(d("2026-09-02"))
        // Next projected start = 9/30; its bleeding window is a PREDICTED period.
        assertEquals(Phase.PREDICTED_PERIOD, CyclePredictor.phaseForDay(d("2026-09-30"), starts, params))
        assertEquals(Phase.OVULATION, CyclePredictor.phaseForDay(d("2026-09-16"), starts, params)) // 9/30 - 14
        assertEquals(Phase.PMS, CyclePredictor.phaseForDay(d("2026-09-27"), starts, params))
    }

    @Test
    fun phaseForDayEmptyIsUnknown() {
        assertEquals(Phase.UNKNOWN, CyclePredictor.phaseForDay(d("2026-09-02"), emptyList(), CycleParams()))
    }
}
