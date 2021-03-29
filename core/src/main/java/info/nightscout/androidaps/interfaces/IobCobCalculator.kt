package info.nightscout.androidaps.interfaces

import androidx.collection.LongSparseArray
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.MealData
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.BasalData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData

interface IobCobCalculator {

    val dataLock: Any
    var bgReadings: List<GlucoseValue>

    val mealData: MealData
    fun getAutosensDataTable(): LongSparseArray<AutosensData>
    fun calculateIobArrayInDia(profile: Profile): Array<IobTotal>
    fun lastDataTime(): String
    fun getAutosensData(fromTime: Long): AutosensData?
    fun getLastAutosensData(reason: String): AutosensData?
    fun getLastAutosensDataSynchronized(reason: String): AutosensData?
    fun calculateAbsInsulinFromTreatmentsAndTempsSynchronized(fromTime: Long): IobTotal
    fun calculateFromTreatmentsAndTempsSynchronized(time: Long, profile: Profile?): IobTotal
    fun getBasalData(profile: Profile, fromTime: Long): BasalData
    fun calculateIobArrayForSMB(lastAutosensResult: AutosensResult, exercise_mode: Boolean, half_basal_exercise_target: Int, isTempTarget: Boolean): Array<IobTotal>
    fun iobArrayToString(array: Array<IobTotal>): String
    fun slowAbsorptionPercentage(timeInMinutes: Int): Double

    /**
     * Return last valid (>39) GlucoseValue from database or null if db is empty
     *
     * @return GlucoseValue or null
     */
    fun lastBg(): GlucoseValue?

    /**
     *  Calculate CobInfo to now()
     *
     *  @param _synchronized access autosens data synchronized (wait for result if calculation is running)
     *  @param reason caller identification
     *  @return CobInfo
     */
    fun getCobInfo(_synchronized: Boolean, reason: String): CobInfo

    /**
     * Provide last GlucoseValue or null if none exists withing last 9 minutes
     *
     * @return GlucoseValue or null
     */
    fun actualBg(): GlucoseValue?

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
     * Calculate IobTotal from boluses and extended to provided timestamp.
     * NOTE: Only isValid == true boluses are included
     * NOTE: if faking by TBR by extended boluses is enabled, extended boluses are not included
     *  and are calculated towards temporary basals
     *
     * @param timestamp timestamp in milliseconds
     * @return calculated iob
     */
    fun calculateIobFromBolusToTime(timestamp: Long): IobTotal
}