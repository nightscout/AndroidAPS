package info.nightscout.implementation.profile

import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.interfaces.ProfileUtil
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileUtilImpl @Inject constructor(
    private val sp: SP,
    private val decimalFormatter: DecimalFormatter
) : ProfileUtil {

    override val units: GlucoseUnit
        get() =
            if (sp.getString(info.nightscout.core.utils.R.string.key_units, GlucoseUnit.MGDL.asText) == GlucoseUnit.MGDL.asText) GlucoseUnit.MGDL
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

    override fun isMgdl(anyBg: Double) = anyBg >= 39
    override fun isMmol(anyBg: Double) = anyBg < 39
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
}