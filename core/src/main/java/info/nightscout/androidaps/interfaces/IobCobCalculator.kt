package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.MealData
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensDataStore
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.BasalData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData
import org.json.JSONArray

interface IobCobCalculator {

    var ads: AutosensDataStore

    fun getMealDataWithWaitingForCalculationFinish(): MealData
    fun getLastAutosensDataWithWaitForCalculationFinish(reason: String): AutosensData?

    fun calculateFromTreatmentsAndTemps(toTime: Long, profile: Profile): IobTotal

    fun getBasalData(profile: Profile, fromTime: Long): BasalData

    fun calculateIobArrayInDia(profile: Profile): Array<IobTotal>
    fun calculateIobArrayForSMB(lastAutosensResult: AutosensResult, exercise_mode: Boolean, half_basal_exercise_target: Int, isTempTarget: Boolean): Array<IobTotal>
    fun iobArrayToString(array: Array<IobTotal>): String
    fun convertToJSONArray(iobArray: Array<IobTotal>): JSONArray

    fun clearCache()

    /**
     *  Calculate CobInfo to now()
     *
     *  @param waitForCalculationFinish access autosens data synchronized (wait for result if calculation is running)
     *  @param reason caller identification
     *  @return CobInfo
     */
    fun getCobInfo(waitForCalculationFinish: Boolean, reason: String): CobInfo

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
     * Get running extended bolus at time
     *
     *  @return     running extended bolus or null if no eb is running
     */
    fun getExtendedBolus(timestamp: Long): ExtendedBolus?

    /**
     *  Calculate IOB of base basal insulin (usualy not accounted towards IOB)
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