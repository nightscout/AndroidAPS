package info.nightscout.interfaces.profile

import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.interfaces.utils.Round
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject

interface Profile {

    class ValidityCheck(var isValid: Boolean = true, val reasons: ArrayList<String> = arrayListOf())

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
    fun getIsfMgdl(): Double

    /**
     * ISF value according to timestamp in MGDL
     */
    fun getIsfMgdl(timestamp: Long): Double

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

    companion object {
        /*
         * Units conversion
         */

        fun fromMgdlToUnits(value: Double, units: GlucoseUnit): Double =
            if (units == GlucoseUnit.MGDL) value else value * Constants.MGDL_TO_MMOLL

        fun fromMmolToUnits(value: Double, units: GlucoseUnit): Double =
            if (units == GlucoseUnit.MMOL) value else value * Constants.MMOLL_TO_MGDL

        fun toUnits(valueInMgdl: Double, valueInMmol: Double, units: GlucoseUnit): Double =
            if (units == GlucoseUnit.MGDL) valueInMgdl else valueInMmol

        fun toUnitsString(valueInMgdl: Double, valueInMmol: Double, units: GlucoseUnit): String =
            if (units == GlucoseUnit.MGDL) DecimalFormatter.to0Decimal(valueInMgdl) else DecimalFormatter.to1Decimal(valueInMmol)

        fun toSignedUnitsString(valueInMgdl: Double, valueInMmol: Double, units: GlucoseUnit): String =
            if (units == GlucoseUnit.MGDL) (if (valueInMgdl > 0) "+" else "") + DecimalFormatter.to0Decimal(valueInMgdl)
            else (if (valueInMmol > 0) "+" else "") + DecimalFormatter.to1Decimal(valueInMmol)

        fun isMgdl(anyBg: Double) = anyBg >= 39
        fun isMmol(anyBg: Double) = anyBg < 39
        fun unit(anyBg: Double) = if (isMgdl(anyBg)) GlucoseUnit.MGDL else GlucoseUnit.MMOL

        fun toCurrentUnits(profileFunction: ProfileFunction, anyBg: Double): Double =
            if (isMmol(anyBg)) fromMmolToUnits(anyBg, profileFunction.getUnits())
            else fromMgdlToUnits(anyBg, profileFunction.getUnits())

        fun toCurrentUnits(units: GlucoseUnit, anyBg: Double): Double =
            if (isMmol(anyBg)) fromMmolToUnits(anyBg, units)
            else fromMgdlToUnits(anyBg, units)

        fun toCurrentUnitsString(profileFunction: ProfileFunction, anyBg: Double): String =
            if (isMmol(anyBg)) toUnitsString(anyBg * Constants.MMOLL_TO_MGDL, anyBg, profileFunction.getUnits())
            else toUnitsString(anyBg, anyBg * Constants.MGDL_TO_MMOLL, profileFunction.getUnits())

        fun toMgdl(value: Double): Double =
            if (isMgdl(value)) value else value * Constants.MMOLL_TO_MGDL

        fun toMgdl(value: Double, units: GlucoseUnit): Double =
            if (units == GlucoseUnit.MGDL) value else value * Constants.MMOLL_TO_MGDL

        fun toMmol(value: Double, units: GlucoseUnit): Double =
            if (units == GlucoseUnit.MGDL) value * Constants.MGDL_TO_MMOLL else value

        // targets are stored in mg/dl but profile vary
        fun toTargetRangeString(low: Double, high: Double, sourceUnits: GlucoseUnit, units: GlucoseUnit): String {
            val lowMgdl = toMgdl(low, sourceUnits)
            val highMgdl = toMgdl(high, sourceUnits)
            val lowMmol = toMmol(low, sourceUnits)
            val highMmol = toMmol(high, sourceUnits)
            return if (low == high) toUnitsString(lowMgdl, lowMmol, units)
            else toUnitsString(lowMgdl, lowMmol, units) + " - " + toUnitsString(highMgdl, highMmol, units)
        }

    }
}