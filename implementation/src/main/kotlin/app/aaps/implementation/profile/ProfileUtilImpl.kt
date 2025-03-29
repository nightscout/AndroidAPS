package app.aaps.implementation.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.Reusable
import java.util.Locale
import javax.inject.Inject

@Reusable
class ProfileUtilImpl @Inject constructor(
    private val preferences: Preferences,
    private val decimalFormatter: DecimalFormatter
) : ProfileUtil {

    override val units: GlucoseUnit
        get() =
            if (preferences.get(StringKey.GeneralUnits) == GlucoseUnit.MGDL.asText) GlucoseUnit.MGDL
            else GlucoseUnit.MMOL

    override fun fromMgdlToUnits(valueInMgdl: Double, targetUnits: GlucoseUnit): Double =
        if (targetUnits == GlucoseUnit.MGDL) valueInMgdl else valueInMgdl * GlucoseUnit.MGDL_TO_MMOLL

    override fun fromMmolToUnits(value: Double, targetUnits: GlucoseUnit): Double =
        if (targetUnits == GlucoseUnit.MMOL) value else value * GlucoseUnit.MMOLL_TO_MGDL

    override fun valueInCurrentUnitsDetect(anyBg: Double): Double =
        if (isMmol(anyBg)) fromMmolToUnits(anyBg, units)
        else fromMgdlToUnits(anyBg, units)

    override fun stringInCurrentUnitsDetect(anyBg: Double): String =
        if (isMmol(anyBg)) toUnitsString(anyBg * GlucoseUnit.MMOLL_TO_MGDL, units)
        else toUnitsString(anyBg, units)

    override fun fromMgdlToStringInUnits(valueInMgdl: Double?, targetUnits: GlucoseUnit): String =
        valueInMgdl?.let { toUnitsString(valueInMgdl, targetUnits) } ?: ""

    override fun fromMgdlToSignedStringInUnits(valueInMgdl: Double, targetUnits: GlucoseUnit): String =
        if (targetUnits == GlucoseUnit.MGDL) (if (valueInMgdl > 0) "+" else "") + decimalFormatter.to0Decimal(valueInMgdl)
        else (if (valueInMgdl > 0) "+" else "") + decimalFormatter.to1Decimal(valueInMgdl * GlucoseUnit.MGDL_TO_MMOLL)

    override fun isMgdl(anyBg: Double) = anyBg >= 36
    override fun isMmol(anyBg: Double) = anyBg < 36
    override fun unitsDetect(anyBg: Double) = if (isMgdl(anyBg)) GlucoseUnit.MGDL else GlucoseUnit.MMOL

    override fun valueInUnitsDetect(anyBg: Double, targetUnits: GlucoseUnit): Double =
        if (isMmol(anyBg)) fromMmolToUnits(anyBg, targetUnits)
        else fromMgdlToUnits(anyBg, targetUnits)

    override fun stringInUnitsDetect(anyBg: Double, targetUnits: GlucoseUnit): String =
        if (isMmol(anyBg)) toUnitsString(anyBg * GlucoseUnit.MMOLL_TO_MGDL, targetUnits)
        else toUnitsString(anyBg, targetUnits)

    override fun convertToMgdlDetect(anyBg: Double): Double =
        if (isMgdl(anyBg)) anyBg else anyBg * GlucoseUnit.MMOLL_TO_MGDL

    override fun convertToMgdl(value: Double, sourceUnits: GlucoseUnit): Double =
        if (sourceUnits == GlucoseUnit.MGDL) value else value * GlucoseUnit.MMOLL_TO_MGDL

    override fun convertToMmol(value: Double, sourceUnits: GlucoseUnit): Double =
        if (sourceUnits == GlucoseUnit.MGDL) value * GlucoseUnit.MGDL_TO_MMOLL else value

    override fun toTargetRangeString(low: Double, high: Double, sourceUnits: GlucoseUnit, targetUnits: GlucoseUnit): String {
        val lowMgdl = convertToMgdl(low, sourceUnits)
        val highMgdl = convertToMgdl(high, sourceUnits)
        return if (low == high) toUnitsString(lowMgdl, targetUnits)
        else toUnitsString(lowMgdl, targetUnits) + " - " + toUnitsString(highMgdl, targetUnits)
    }

    private fun toUnitsString(valueInMgdl: Double, targetUnits: GlucoseUnit): String =
        if (targetUnits == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(valueInMgdl) else decimalFormatter.to1Decimal(valueInMgdl * GlucoseUnit.MGDL_TO_MMOLL)

    override fun getBasalProfilesDisplayable(profiles: Array<Profile.ProfileValue>, pumpType: PumpType): String {
        val stringBuilder = StringBuilder()
        for (basalValue in profiles) {
            val basalValueValue = pumpType.determineCorrectBasalSize(basalValue.value)
            val hour = basalValue.timeAsSeconds / (60 * 60)
            stringBuilder.append((if (hour < 10) "0" else "") + hour + ":00")
            stringBuilder.append(" ")
            stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", basalValueValue))
            stringBuilder.append(",\n")
        }
        return if (stringBuilder.length > 3) stringBuilder.substring(0, stringBuilder.length - 2) else stringBuilder.toString()
    }
}