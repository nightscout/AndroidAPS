package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.interfaces.Profile.ProfileValue
import info.nightscout.androidaps.utils.DecimalFormatter.to0Decimal
import info.nightscout.androidaps.utils.DecimalFormatter.to1Decimal
import info.nightscout.androidaps.utils.Round
import org.joda.time.DateTime
import org.json.JSONObject

interface Profile {

    fun isValid(from: String): Boolean
    fun isValid(from: String, notify: Boolean): Boolean

    /**
     * Units used for ISF & target
     */
    val units: GlucoseUnit

    //@Deprecated("Replace in favor of accessing InsulinProfile")
    val dia: Double

    @Deprecated("????why here")
    val percentage: Int
    @Deprecated("????why here")
    val timeshift: Int

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
     * High target value according to elapsed seconds from midnight
     */
    fun getTargetHighTimeFromMidnight(timeAsSeconds: Int): Double

    /**
     * High target value according to elapsed seconds from midnight in MGDL
     */
    fun getTargetHighMgdlTimeFromMidnight(timeAsSeconds: Int): Double

    val icList: String
    val isfList: String
    val basalList: String
    val targetList: String

    fun convertToNonCustomizedProfile(): Profile
    fun toNsJson(): JSONObject
    fun getMaxDailyBasal(): Double
    fun baseBasalSum(): Double
    fun percentageBasalSum(): Double

    fun getBasalValues(): Array<ProfileValue>
    fun getIcs(): Array<ProfileValue>
    fun getIsfsMgdl(): Array<ProfileValue>
    fun getSingleTargetsMgdl(): Array<ProfileValue>

    class ProfileValue(var timeAsSeconds: Int, var value: Double) {

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
         * Midnight time conversion
         */
        fun secondsFromMidnight(): Int {
            val passed = DateTime().millisOfDay.toLong()
            return (passed / 1000).toInt()
        }

        fun secondsFromMidnight(date: Long): Int {
            val passed = DateTime(date).millisOfDay.toLong()
            return (passed / 1000).toInt()
        }

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
            if (units == GlucoseUnit.MGDL) to0Decimal(valueInMgdl) else to1Decimal(valueInMmol)

        fun toSignedUnitsString(valueInMgdl: Double, valueInMmol: Double, units: GlucoseUnit): String =
            if (units == GlucoseUnit.MGDL) (if (valueInMgdl > 0) "+" else "") + to0Decimal(valueInMgdl)
            else (if (valueInMmol > 0) "+" else "") + to1Decimal(valueInMmol)

        fun toCurrentUnits(profileFunction: ProfileFunction, anyBg: Double): Double =
            if (anyBg < 32) fromMmolToUnits(anyBg, profileFunction.getUnits())
            else fromMgdlToUnits(anyBg, profileFunction.getUnits())

        fun toCurrentUnits(units: GlucoseUnit, anyBg: Double): Double =
            if (anyBg < 32) fromMmolToUnits(anyBg, units)
            else fromMgdlToUnits(anyBg, units)

        fun toCurrentUnitsString(profileFunction: ProfileFunction, anyBg: Double): String =
            if (anyBg < 32) toUnitsString(anyBg * Constants.MMOLL_TO_MGDL, anyBg, profileFunction.getUnits())
            else toUnitsString(anyBg, anyBg * Constants.MGDL_TO_MMOLL, profileFunction.getUnits())

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
            return if (low == high) toUnitsString(lowMgdl, lowMmol, units) else toUnitsString(lowMgdl, lowMmol, units) + " - " + toUnitsString(highMgdl, highMmol, units)
        }

    }
}