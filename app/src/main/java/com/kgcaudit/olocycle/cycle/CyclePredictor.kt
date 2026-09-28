package com.kgcaudit.olocycle.cycle

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Calendar-method cycle math, kept free of Android so it can be unit-tested on the JVM.
 *
 * Given a person's recorded period-start dates it estimates their average cycle and, from the most
 * recent start, projects upcoming cycles: predicted period, ovulation (next start − luteal length),
 * and the fertile window (ovulation − 5 to + 1). These are estimates, not medical or contraceptive
 * advice.
 */
object CyclePredictor {

    /** Fertile window spans this many days before ovulation through this many after. */
    private const val FERTILE_DAYS_BEFORE = 5
    private const val FERTILE_DAYS_AFTER = 1

    /** How many recent cycles feed the average. */
    const val AVERAGE_WINDOW = 6

    /** Minimum recorded starts before switching from defaults to the personal average. */
    const val MIN_STARTS_FOR_AVERAGE = 3

    /**
     * Derives cycle parameters from recorded [periodStarts] (any order), falling back to [defaults]
     * for anything history cannot supply. Needs [MIN_STARTS_FOR_AVERAGE] starts before averaging
     * the cycle length.
     */
    fun deriveParams(
        periodStarts: List<LocalDate>,
        recordedPeriodLength: Int? = null,
        defaults: CycleParams = CycleParams(),
    ): CycleParams {
        val sorted = periodStarts.distinct().sorted()
        val gaps = sorted.zipWithNext { a, b -> a.until(b).days }
            .filter { it in CycleParams.MIN_CYCLE..CycleParams.MAX_CYCLE }
            .takeLast(AVERAGE_WINDOW)

        val cycleLength =
            if (sorted.size >= MIN_STARTS_FOR_AVERAGE && gaps.isNotEmpty()) {
                gaps.average().roundToInt().coerceIn(CycleParams.MIN_CYCLE, CycleParams.MAX_CYCLE)
            } else {
                defaults.cycleLength
            }
        val periodLength = (recordedPeriodLength ?: defaults.periodLength).coerceIn(1, cycleLength)
        val luteal = defaults.lutealLength.coerceIn(1, cycleLength - 1)
        return CycleParams(cycleLength, periodLength, luteal)
    }

    /** Builds the prediction for a single cycle beginning on [start]. */
    fun predictFrom(start: LocalDate, params: CycleParams, basedOnActualStart: Boolean): CyclePrediction {
        val nextStart = start.plusDays(params.cycleLength.toLong())
        val ovulation = nextStart.minusDays(params.lutealLength.toLong())
        return CyclePrediction(
            periodStart = start,
            periodEnd = start.plusDays((params.periodLength - 1).toLong()),
            fertileStart = ovulation.minusDays(FERTILE_DAYS_BEFORE.toLong()),
            fertileEnd = ovulation.plusDays(FERTILE_DAYS_AFTER.toLong()),
            ovulation = ovulation,
            nextPeriodStart = nextStart,
            basedOnActualStart = basedOnActualStart,
        )
    }

    /**
     * Rolls forward from the last recorded start so that [today] falls within the returned cycle
     * (i.e. `today < nextPeriodStart`). Returns null when there is no history to project from.
     */
    fun currentCycle(periodStarts: List<LocalDate>, params: CycleParams, today: LocalDate): CyclePrediction? {
        val lastStart = periodStarts.maxOrNull() ?: return null
        var prediction = predictFrom(lastStart, params, basedOnActualStart = true)
        while (!today.isBefore(prediction.nextPeriodStart)) {
            prediction = predictFrom(prediction.nextPeriodStart, params, basedOnActualStart = false)
        }
        return prediction
    }

    /** Whole days from [today] until the next period start; negative if it is overdue. */
    fun daysUntilNextPeriod(prediction: CyclePrediction, today: LocalDate): Int =
        today.until(prediction.nextPeriodStart).days

    /**
     * Classifies [day] within [prediction]. Recorded period days are [Phase.PERIOD]; a projected
     * cycle's bleeding days are [Phase.PREDICTED_PERIOD]. PMS is the [pmsDays] before the next start.
     */
    fun phaseOf(day: LocalDate, prediction: CyclePrediction, pmsDays: Int = 5): Phase = when {
        day in prediction.periodStart..prediction.periodEnd ->
            if (prediction.basedOnActualStart) Phase.PERIOD else Phase.PREDICTED_PERIOD
        day == prediction.ovulation -> Phase.OVULATION
        day in prediction.fertileStart..prediction.fertileEnd -> Phase.FERTILE
        day >= prediction.nextPeriodStart.minusDays(pmsDays.toLong()) && day < prediction.nextPeriodStart -> Phase.PMS
        day > prediction.ovulation -> Phase.LUTEAL
        day > prediction.periodEnd -> Phase.FOLLICULAR
        else -> Phase.UNKNOWN
    }

    private operator fun ClosedRange<LocalDate>.contains(d: LocalDate) =
        !d.isBefore(start) && !d.isAfter(endInclusive)
}
