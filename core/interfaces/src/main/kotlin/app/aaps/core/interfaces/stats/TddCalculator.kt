package app.aaps.core.interfaces.stats

import androidx.collection.LongSparseArray
import app.aaps.core.data.aps.AverageTDD
import app.aaps.core.data.model.TDD

/**
 * Total Daily Dose calculations
 */
interface TddCalculator {

    /**
     * Calculate past whole 'days' before timestamp
     * @param timestamp date
     * @param days how many days
     * @param allowMissingDays if true intervals without data are allowed (no profile, bolus, TBR)
     * @return list of TDDs or null if data is not available
     */
    fun calculate(timestamp: Long, days: Long, allowMissingDays: Boolean): LongSparseArray<TDD>?

    /**
     * Calculate past whole 'days' before now
     * @param days how many days
     * @param allowMissingDays if true intervals without data are allowed (no profile, bolus, TBR)
     * @return list of TDDs or null if data is not available
     */
    fun calculate(days: Long, allowMissingDays: Boolean): LongSparseArray<TDD>?

    /**
     * Calculate today up to now
     * @return TDD or null if data is not available
     */
    fun calculateToday(): TDD?

    /**
     * Calculate interval in the past from now
     * @param startHours hours back. It must be < 0 because we want data in the past
     * @param endHours hours back. It must be <= 0 because we want data in the past
     * @return TDD or null if data is not available
     */
    fun calculateDaily(startHours: Long, endHours: Long): TDD?

    /**
     * Calculate interval in the past from timestamp
     * @param timestamp date
     * @param startHours hours back. It must be < 0 because we want data in the past
     * @param endHours hours back. It must be <= 0 because we want data in the past
     * @return TDD or null if data is not available
     */
    fun calculateDaily(timestamp: Long, startHours: Long, endHours: Long): TDD?

    /**
     * Calculate interval in the past
     * @param startTime start
     * @param endTime end
     * @param allowMissingData if true intervals without data are allowed (no profile, bolus, TBR)
     * @return TDD or null if data is not available
     */
    fun calculateInterval(startTime: Long, endTime: Long, allowMissingData: Boolean): TDD?

    /**
     * Calculate average TDD from list of daily TDDs
     * @param tdds list of precalculated data for days
     * @return [AverageTDD] or null if data is not available
     */
    fun averageTDD(tdds: LongSparseArray<TDD>?): AverageTDD?
}
