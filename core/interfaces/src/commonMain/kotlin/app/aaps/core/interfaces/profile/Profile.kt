package app.aaps.core.interfaces.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import kotlinx.serialization.json.JsonObject

interface Profile {

    /**
     *
     */
    //val hasDynamicIsf: Boolean

    class ValidityCheck(var isValid: Boolean = true, val reasons: ArrayList<String> = arrayListOf()) {

        /** Why the basal rate does not fit the pump. Only the pump check sets it, so it is null for the other checks. */
        var basalProblem: BasalProblem? = null
    }

    /** The ways a basal rate can fail the pump check. */
    enum class BasalProblem {

        /** The pump takes only full hours, and a basal block does not end on one. */
        NOT_ALIGNED_TO_HOURS,
        BELOW_PUMP_MINIMUM,
        ABOVE_PUMP_MAXIMUM
    }

    /**
     * Check validity of profile
     */
    fun isValid(from: String, pump: Pump, config: Config, rh: TextResolver, notificationManager: NotificationManager, hardLimits: HardLimits, sendNotifications: Boolean): ValidityCheck

    /**
     * Units used for ISF & target
     */
    val units: GlucoseUnit

    //@Deprecated("Replace in favor of accessing InsulinProfile")
    val iCfg: ICfg?

    val percentage: Int

    /**
     * Timeshift modifier of base profile in hours
     */
    val timeshift: Int

    /**
     * is equal to another profile?
     */
    fun isEqual(profile: Profile, ignoreName: Boolean = false): Boolean

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
     * Average target value according to "now" in MGDL
     */
    fun getTargetMgdl(): Double
    fun getTargetLowMgdl(): Double
    fun getTargetLowMgdl(timestamp: Long): Double
    fun getTargetHighMgdl(): Double
    fun getTargetHighMgdl(timestamp: Long): Double

    /**
     * Average target in MGDL with the low/high targets each rounded to 0.1 mg/dL first — exactly how
     * the APS builds its target_bg (min_bg/max_bg = Round.roundTo(getTargetLow/HighMgdl(), 0.1), then
     * target_bg = (min_bg + max_bg) / 2).
     *
     * Use this — not [getTargetMgdl] — when comparing the profile target against the APS result target
     * to decide whether the loop has adjusted it. [getTargetMgdl] is unrounded, so with a non-integer
     * mmol→mg/dL factor (e.g. 5.0 mmol = 90.078 mg/dL) it never equals the APS value (90.1) and would
     * falsely flag every loop run as "adjusted".
     */
    fun getRoundedTargetMgdl(): Double = (Round.roundTo(getTargetLowMgdl(), 0.1) + Round.roundTo(getTargetHighMgdl(), 0.1)) / 2.0

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

    fun getIcList(rh: TextResolver, dateUtil: DateUtil): String
    fun getIsfList(rh: TextResolver, dateUtil: DateUtil): String
    fun getBasalList(rh: TextResolver, dateUtil: DateUtil): String
    fun getTargetList(rh: TextResolver, dateUtil: DateUtil): String

    fun convertToNonCustomizedProfile(dateUtil: DateUtil): PureProfile
    fun toPureNsJson(dateUtil: DateUtil): JsonObject
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