package app.aaps.core.interfaces.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import org.json.JSONObject

interface Profile {

    /**
     *
     */
    //val hasDynamicIsf: Boolean

    class ValidityCheck(var isValid: Boolean = true, val reasons: ArrayList<String> = arrayListOf())

    /**
     * Check validity of profile
     */
    fun isValid(from: String, pump: Pump, config: Config, rh: ResourceHelper, rxBus: RxBus, hardLimits: HardLimits, sendNotifications: Boolean): ValidityCheck

    /**
     * Units used for ISF & target
     */
    val units: GlucoseUnit

    //@Deprecated("Replace in favor of accessing InsulinProfile")
    val dia: Double

    val percentage: Int

    /**
     * Timeshift modifier of base profile in hours
     */
    val timeshift: Int

    /**
     * is equal to another profile?
     */
    fun isEqual(profile: Profile): Boolean

    /**
     * Basal value according to "now"
     */
    fun getBasal(): Double

    /**
     * Basal value according to timestamp
     */
    fun getBasal(timestamp: Long): Double

    /**
     * I:C value according to "now"
     */
    fun getIc(): Double

    /**
     * I:C value according to timestamp
     */
    fun getIc(timestamp: Long): Double

    /**
     * ISF value according to "now"" in MGDL
     */
    fun getIsfMgdl(caller: String): Double

    /**
     * ISF profile value according to "now"" in MGDL
     */
    fun getProfileIsfMgdl(): Double

    /**
     * ISF value according to timestamp in MGDL for use in Wizard and COB calculations
     */
    fun getIsfMgdlForCarbs(timestamp: Long, caller: String, config: Config, processedDeviceStatusData: ProcessedDeviceStatusData): Double

    /**
     * Average target value according to "now" in MGDL
     */
    fun getTargetMgdl(): Double
    fun getTargetLowMgdl(): Double
    fun getTargetLowMgdl(timestamp: Long): Double
    fun getTargetHighMgdl(): Double
    fun getTargetHighMgdl(timestamp: Long): Double

    /**
     * Basal value according to elapsed seconds from midnight
     */
    fun getBasalTimeFromMidnight(timeAsSeconds: Int): Double

    /**
     * I:C value according to elapsed seconds from midnight
     */
    fun getIcTimeFromMidnight(timeAsSeconds: Int): Double

    /**
     * ISF value according to elapsed seconds from midnight
     */
    fun getIsfMgdlTimeFromMidnight(timeAsSeconds: Int): Double

    /**
     * Low target value according to elapsed seconds from midnight
     */
    fun getTargetLowMgdlTimeFromMidnight(timeAsSeconds: Int): Double

    /**
     * High target value according to elapsed seconds from midnight in MGDL
     */
    fun getTargetHighMgdlTimeFromMidnight(timeAsSeconds: Int): Double

    fun getIcList(rh: ResourceHelper, dateUtil: DateUtil): String
    fun getIsfList(rh: ResourceHelper, dateUtil: DateUtil): String
    fun getBasalList(rh: ResourceHelper, dateUtil: DateUtil): String
    fun getTargetList(rh: ResourceHelper, dateUtil: DateUtil): String

    fun convertToNonCustomizedProfile(dateUtil: DateUtil): PureProfile
    fun toPureNsJson(dateUtil: DateUtil): JSONObject
    fun getMaxDailyBasal(): Double
    fun baseBasalSum(): Double
    fun percentageBasalSum(): Double

    fun getBasalValues(): Array<ProfileValue>
    fun getIcsValues(): Array<ProfileValue>
    fun getIsfsMgdlValues(): Array<ProfileValue>
    fun getSingleTargetsMgdl(): Array<ProfileValue>

    open class ProfileValue(var timeAsSeconds: Int, var value: Double) {

        override fun equals(other: Any?): Boolean {
            if (other !is ProfileValue) {
                return false
            }
            return timeAsSeconds == other.timeAsSeconds && Round.isSame(value, other.value)
        }

        override fun hashCode(): Int {
            var result = timeAsSeconds
            result = 31 * result + value.hashCode()
            return result
        }
    }
}