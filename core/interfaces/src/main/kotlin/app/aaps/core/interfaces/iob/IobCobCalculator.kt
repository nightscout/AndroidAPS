package app.aaps.core.interfaces.iob

import app.aaps.core.data.aps.BasalData
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.Profile

interface IobCobCalculator {

    var ads: AutosensDataStore

    suspend fun getMealDataWithWaitingForCalculationFinish(): MealData
    fun getLastAutosensDataWithWaitForCalculationFinish(reason: String): AutosensData?

    suspend fun calculateFromTreatmentsAndTemps(toTime: Long, profile: EffectiveProfile): IobTotal

    suspend fun calculateDetectionStart(from: Long, limitDataToOldestAvailable: Boolean): Long

    fun getBasalData(profile: Profile, fromTime: Long): BasalData

    suspend fun calculateIobArrayInDia(profile: EffectiveProfile): Array<IobTotal>
    suspend fun calculateIobArrayForSMB(lastAutosensResult: AutosensResult, exerciseMode: Boolean, halfBasalExerciseTarget: Int, isTempTarget: Boolean): Array<IobTotal>
    fun iobArrayToString(array: Array<IobTotal>): String

    fun clearCache()

    /**
     *  Calculate CobInfo to now()
     *
     *  @param reason caller identification
     *  @return CobInfo
     */
    suspend fun getCobInfo(reason: String): CobInfo

    /**
     * Calculate IobTotal from boluses and extended boluses to now().
     * NOTE: Only isValid == true boluses are included
     * NOTE: if faking by TBR by extended boluses is enabled, extended boluses are not included
     *  and are calculated towards temporary basals
     *
     * @return calculated iob
     */
    suspend fun calculateIobFromBolus(): IobTotal

    /**
     *  Calculate IOB of base basal insulin (usually not accounted towards IOB)
     *
     *  @param  toTime
     *  @return IobTotal
     */
    suspend fun calculateAbsoluteIobFromBaseBasals(toTime: Long): IobTotal

    /**
     *  Calculate IOB from Temporary basals and Extended boluses (if emulation is enabled) to the time specified
     *
     *  @param  toTime    time to calculate to
     *  @return IobTotal
     */
    suspend fun calculateIobToTimeFromTempBasalsIncludingConvertedExtended(toTime: Long): IobTotal

    /**
     *  Calculate IOB from Temporary basals and Extended boluses (if emulation is enabled) to now
     *
     *  @return IobTotal
     */
    suspend fun calculateIobFromTempBasalsIncludingConvertedExtended(): IobTotal
}