package com.expensetracker.util

import java.util.Calendar

object PeriodCalculator {
    fun rangeFor(period: String): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val end = cal.timeInMillis
        when(period) {
            "Day" -> cal.add(Calendar.DAY_OF_YEAR, -1)
            "Week" -> cal.add(Calendar.DAY_OF_YEAR, -7)
            "Month" -> cal.add(Calendar.MONTH, -1)
            "Year" -> cal.add(Calendar.YEAR, -1)
            else -> cal.add(Calendar.MONTH, -1)
        }
        val start = cal.timeInMillis
        return start to end
    }

    data class Comparison(val current: Long, val previous: Long, val deltaAbs: Long, val deltaPct: Double?)
    fun compare(currentRange: LongRange, previousSum: Long, currentSum: Long): Comparison {
        val deltaAbs = currentSum - previousSum
        val deltaPct = if (previousSum != 0L) deltaAbs.toDouble()/previousSum*100 else null
        return Comparison(currentSum, previousSum, deltaAbs, deltaPct)
    }
}
