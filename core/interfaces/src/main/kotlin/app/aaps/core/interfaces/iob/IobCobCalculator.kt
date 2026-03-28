package app.aaps.core.interfaces.iob

import app.aaps.core.data.aps.BasalData
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.profile.Profile

/**
 * Calculator for Insulin on Board (IOB) and Carbs on Board (COB).
 *
 * This is one of the most critical components in the closed-loop system. It computes:
 * - **IOB**: How much active insulin remains from previous boluses and temp basals.
 * - **COB**: How many carbs are still being absorbed from previous meals.
 * - **Autosensitivity**: How the patient's insulin sensitivity differs from the profile.
 * - **Meal data**: Aggregated carb and bolus data for the APS algorithm.
 *
 * ## Data Sources
 * - Boluses and extended boluses from the database
 * - Temporary basal rates from the database
 * - Carbohydrate entries from the database
 * - Glucose values for autosensitivity detection
 *
 * ## Thread Safety
 * Calculations may be long-running. Methods with "WaitingForCalculationFinish" in their name
 * block until the latest background calculation cycle completes.
 *
 * @see IobTotal
 * @see CobInfo
 * @see AutosensData
 * @see MealData
 */
interface IobCobCalculator {

    /** Autosensitivity data store containing bucketed BG data and autosens results. */
    var ads: AutosensDataStore

    /**
     * Get meal data (carbs, boluses, etc.) for the APS algorithm.
     * Blocks until the current calculation cycle finishes.
     *
     * @return aggregated [MealData] for the current time.
     */
    fun getMealDataWithWaitingForCalculationFinish(): MealData

    /**
     * Get the most recent autosensitivity data point.
     * Blocks until the current calculation cycle finishes.
     *
     * @param reason Caller identification for logging.
     * @return the latest [AutosensData] or null if unavailable.
     */
    fun getLastAutosensDataWithWaitForCalculationFinish(reason: String): AutosensData?

    /**
     * Calculate total IOB from all treatments (boluses + temp basals) up to [toTime].
     *
     * @param toTime Timestamp in milliseconds to calculate IOB to.
     * @param profile Current profile for insulin curve parameters.
     * @return [IobTotal] containing iob, basaliob, activity, and other fields.
     */
    fun calculateFromTreatmentsAndTemps(toTime: Long, profile: Profile): IobTotal

    /**
     * Calculate the start timestamp for autosensitivity detection.
     *
     * @param from Base timestamp.
     * @param limitDataToOldestAvailable If true, limit to oldest available BG data.
     * @return timestamp in milliseconds for detection window start.
     */
    fun calculateDetectionStart(from: Long, limitDataToOldestAvailable: Boolean): Long

    /**
     * Get basal data (running rate, temp basal info) at a specific time.
     *
     * @param profile Current profile.
     * @param fromTime Timestamp to evaluate.
     * @return [BasalData] with the effective basal rate at that time.
     */
    fun getBasalData(profile: Profile, fromTime: Long): BasalData

    /**
     * Calculate an array of IOB values across the Duration of Insulin Action (DIA).
     * Used for IOB prediction curves in the UI.
     *
     * @param profile Current profile (provides DIA and insulin curve).
     * @return array of [IobTotal] at regular intervals across the DIA.
     */
    fun calculateIobArrayInDia(profile: Profile): Array<IobTotal>

    /**
     * Calculate an IOB array specifically for the SMB algorithm.
     * Takes into account autosens adjustments and exercise mode.
     *
     * @param lastAutosensResult Latest autosensitivity result for ISF adjustments.
     * @param exerciseMode Whether exercise mode is active (reduces insulin).
     * @param halfBasalExerciseTarget BG target at which basal is halved during exercise.
     * @param isTempTarget Whether a temporary target is currently active.
     * @return array of [IobTotal] for SMB calculations.
     */
    fun calculateIobArrayForSMB(lastAutosensResult: AutosensResult, exerciseMode: Boolean, halfBasalExerciseTarget: Int, isTempTarget: Boolean): Array<IobTotal>

    /**
     * Serialize an IOB array to a JSON-compatible string for logging/NS upload.
     *
     * @param array The IOB array to serialize.
     * @return JSON string representation.
     */
    fun iobArrayToString(array: Array<IobTotal>): String

    /** Clear all cached calculation results. Called when underlying data changes significantly. */
    fun clearCache()

    /**
     *  Calculate CobInfo to now()
     *
     *  @param reason caller identification
     *  @return CobInfo
     */
    fun getCobInfo(reason: String): CobInfo

    /**
     * Calculate IobTotal from boluses and extended boluses to now().
     * NOTE: Only isValid == true boluses are included
     * NOTE: if faking by TBR by extended boluses is enabled, extended boluses are not included
     *  and are calculated towards temporary basals
     *
     * @return calculated iob
     */
    fun calculateIobFromBolus(): IobTotal

    /**
     *  Calculate IOB of base basal insulin (usually not accounted towards IOB)
     *
     *  @param  toTime
     *  @return IobTotal
     */
    fun calculateAbsoluteIobFromBaseBasals(toTime: Long): IobTotal

    /**
     *  Calculate IOB from Temporary basals and Extended boluses (if emulation is enabled) to the the time specified
     *
     *  @param  toTime    time to calculate to
     *  @return IobTotal
     */
    fun calculateIobToTimeFromTempBasalsIncludingConvertedExtended(toTime: Long): IobTotal

    /**
     *  Calculate IOB from Temporary basals and Extended boluses (if emulation is enabled) to now
     *
     *  @return IobTotal
     */
    fun calculateIobFromTempBasalsIncludingConvertedExtended(): IobTotal
}