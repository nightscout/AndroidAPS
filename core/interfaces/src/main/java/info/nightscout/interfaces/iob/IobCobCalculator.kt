package info.nightscout.interfaces.iob

import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.interfaces.aps.AutosensData
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.aps.AutosensResult
import info.nightscout.interfaces.aps.BasalData
import info.nightscout.interfaces.profile.Profile
import org.json.JSONArray

interface IobCobCalculator {

    var ads: AutosensDataStore

    fun getMealDataWithWaitingForCalculationFinish(): MealData
    fun getLastAutosensDataWithWaitForCalculationFinish(reason: String): AutosensData?

    fun calculateFromTreatmentsAndTemps(toTime: Long, profile: Profile): IobTotal

    fun calculateDetectionStart(from: Long, limitDataToOldestAvailable: Boolean): Long

    fun getBasalData(profile: Profile, fromTime: Long): BasalData

    fun calculateIobArrayInDia(profile: Profile): Array<IobTotal>
    fun calculateIobArrayForSMB(lastAutosensResult: AutosensResult, exercise_mode: Boolean, half_basal_exercise_target: Int, isTempTarget: Boolean): Array<IobTotal>
    fun iobArrayToString(array: Array<IobTotal>): String
    fun convertToJSONArray(iobArray: Array<IobTotal>): JSONArray

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
     * Get running temporary basal at time
     *
     *  @return     running temporary basal or null if no tbr is running
     */
    fun getTempBasal(timestamp: Long): TemporaryBasal?

    /**
     * Get running temporary basal at time
     *
     *  @return     running temporary basal or null if no tbr is running
     *              If pump is faking extended boluses as temporary basals
     *              return extended converted to temporary basal with type == FAKE_EXTENDED
     */
    fun getTempBasalIncludingConvertedExtended(timestamp: Long): TemporaryBasal?

    /**
     * Get running temporary basals for given time range, sliced by calculationStep.
     * For each step between given range it calculates equivalent of getTempBasalIncludingConvertedExtended
     *
     *  @param startTime start of calculated period, timestamp
     *  @param endTime end of calculated period, timestamp
     *  @param calculationStep calculation step, in millisecond
     *  @return map where for each step, its timestamp is a key and calculated optional temporary basal is a value
     */
    fun getTempBasalIncludingConvertedExtendedForRange(startTime: Long, endTime: Long, calculationStep: Long): Map<Long, TemporaryBasal?>

    /**
     * Get running extended bolus at time
     *
     *  @return     running extended bolus or null if no eb is running
     */
    fun getExtendedBolus(timestamp: Long): ExtendedBolus?

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