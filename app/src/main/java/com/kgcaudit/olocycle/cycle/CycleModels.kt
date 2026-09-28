package com.kgcaudit.olocycle.cycle

import java.time.LocalDate

/** Which part of the cycle a given day falls in. */
enum class Phase { PERIOD, PREDICTED_PERIOD, FOLLICULAR, FERTILE, OVULATION, LUTEAL, PMS, UNKNOWN }

/**
 * Tunable parameters for one person's cycle. Defaults are the textbook calendar-method values,
 * used until enough history exists to average from.
 */
data class CycleParams(
    val cycleLength: Int = DEFAULT_CYCLE_LENGTH,
    val periodLength: Int = DEFAULT_PERIOD_LENGTH,
    val lutealLength: Int = DEFAULT_LUTEAL_LENGTH,
) {
    init {
        require(cycleLength in MIN_CYCLE..MAX_CYCLE) { "cycleLength out of range: $cycleLength" }
        require(periodLength in 1..cycleLength) { "periodLength out of range: $periodLength" }
        require(lutealLength in 1 until cycleLength) { "lutealLength out of range: $lutealLength" }
    }

    companion object {
        const val DEFAULT_CYCLE_LENGTH = 28
        const val DEFAULT_PERIOD_LENGTH = 5
        const val DEFAULT_LUTEAL_LENGTH = 14
        const val MIN_CYCLE = 15
        const val MAX_CYCLE = 60
    }
}

/**
 * Prediction for the cycle that starts on [periodStart].
 *
 * @property basedOnActualStart true when [periodStart] is a recorded start, false when it is itself
 *   a prediction rolled forward from the last recorded start.
 */
data class CyclePrediction(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val fertileStart: LocalDate,
    val fertileEnd: LocalDate,
    val ovulation: LocalDate,
    val nextPeriodStart: LocalDate,
    val basedOnActualStart: Boolean,
)
