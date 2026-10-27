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

    /**
     * AAPSCLIENT only. True when the calculation had to use the profile ISF for carbs, because the client
     * has not stored any of the master's ISF values for the calculated time yet - a new install, or after a
     * database reset. On a master that uses dynamic ISF the profile ISF is much higher, so carbs decay too
     * slowly and COB stays too high.
     *
     * Only in this state does a device status that brings the master's ISF change the result, so only then
     * is a full reset worth its cost. Set by the calculation, stays set until the calculation is reset,
     * because the buckets calculated that way stay in the cache.
     */
    var usedProfileIsfForCarbs: Boolean

    suspend fun getMealDataWithWaitingForCalculationFinish(): MealData
    suspend fun getLastAutosensDataWithWaitForCalculationFinish(reason: String): AutosensData?

    suspend fun calculateFromTreatmentsAndTemps(toTime: Long, profile: EffectiveProfile): IobTotal

    suspend fun calculateDetectionStart(from: Long, limitDataToOldestAvailable: Boolean): Long

    suspend fun getBasalData(profile: Profile, fromTime: Long): BasalData

    suspend fun calculateIobArrayInDia(profile: EffectiveProfile): Array<IobTotal>
    suspend fun calculateIobArrayForSMB(lastAutosensResult: AutosensResult, exerciseMode: Boolean, halfBasalExerciseTarget: Int, isTempTarget: Boolean): Array<IobTotal>
    fun iobArrayToString(array: Array<IobTotal>): String

    fun clearCache()

    /**
     * The BG data was loaded again, normally for a new BG.
     *
     * The cached IOB of the past stays: it depends only on boluses, temporary basals, extended boluses,
     * the profile switches in force at their times and the time zone. A change of one of those clears the
     * cache from its time on (history change) or completely (time zone, configuration, import). Only
     * entries older than anything the calculation asks for are dropped.
     */
    fun bgDataReloaded()

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