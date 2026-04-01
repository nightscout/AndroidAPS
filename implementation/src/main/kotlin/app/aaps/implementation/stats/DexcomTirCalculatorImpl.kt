package app.aaps.implementation.stats

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.stats.DexcomTIR
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import dagger.Reusable
import javax.inject.Inject

/**
 * Implementation of Dexcom Time In Range (TIR) statistics calculator.
 *
 * This class calculates comprehensive glucose statistics following Dexcom's methodology
 * over a fixed 14-day period. It retrieves blood glucose readings from the persistence layer
 * and processes them into a [DexcomTIR] object containing all statistical measures.
 *
 * The calculation process:
 * 1. Determines the time range (midnight 14 days ago to current midnight)
 * 2. Retrieves all BG readings from the database within this range
 * 3. Processes each reading into appropriate glucose ranges
 * 4. Returns a DexcomTirImpl with all calculated statistics
 *
 * This class is marked as @Reusable for efficient dependency injection, as it has no
 * mutable state and can be shared across multiple injection points.
 *
 * @property dateUtil Utility for date/time calculations
 * @property persistenceLayer Database layer for retrieving BG readings
 *
 * @see DexcomTirCalculator
 * @see DexcomTirImpl
 */
@Reusable
class DexcomTirCalculatorImpl @Inject constructor(
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer
) : DexcomTirCalculator {

    /**
     * Number of days to include in Dexcom TIR calculations.
     *
     * Fixed at 14 days per Dexcom's standard reporting period.
     */
    val days = 14L

    /**
     * Calculates Dexcom TIR statistics for the last 14 days.
     *
     * Retrieves all blood glucose readings from midnight 14 days ago until current midnight,
     * iterates through them adding each to a DexcomTirImpl accumulator, and returns the
     * final result containing all calculated statistics.
     *
     * @return DexcomTIR object with calculated statistics including percentages in each range,
     *         mean glucose, standard deviation, and estimated HbA1c
     */
    override suspend fun calculate(): DexcomTIR {
        val startTime = MidnightTime.calcDaysBack(days)
        val endTime = MidnightTime.calc(dateUtil.now())

        val bgReadings = persistenceLayer.getBgReadingsDataFromTimeToTime(startTime, endTime, true)
        val result = DexcomTirImpl()
        for (bg in bgReadings) result.add(bg.timestamp, bg.value)
        return result
    }
}