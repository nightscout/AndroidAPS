package info.nightscout.core.profile

import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import org.joda.time.DateTime

/*
 * Midnight time conversion
 */
fun Profile.Companion.secondsFromMidnight(): Int {
    val passed = DateTime().millisOfDay.toLong()
    return (passed / 1000).toInt()
}

fun Profile.Companion.secondsFromMidnight(date: Long): Int {
    val passed = DateTime(date).millisOfDay.toLong()
    return (passed / 1000).toInt()
}

fun Profile.Companion.milliSecFromMidnight(date: Long): Long {
    return DateTime(date).millisOfDay.toLong()
}
/*
 * Units conversion
 */

fun Profile.Companion.fromMgdlToUnits(value: Double, units: GlucoseUnit): Double =
    if (units == GlucoseUnit.MGDL) value else value * Constants.MGDL_TO_MMOLL

fun Profile.Companion.fromMmolToUnits(value: Double, units: GlucoseUnit): Double =
    if (units == GlucoseUnit.MMOL) value else value * Constants.MMOLL_TO_MGDL

fun Profile.Companion.toUnits(valueInMgdl: Double, valueInMmol: Double, units: GlucoseUnit): Double =
    if (units == GlucoseUnit.MGDL) valueInMgdl else valueInMmol

fun Profile.Companion.toUnitsString(valueInMgdl: Double, valueInMmol: Double, units: GlucoseUnit): String =
    if (units == GlucoseUnit.MGDL) DecimalFormatter.to0Decimal(valueInMgdl) else DecimalFormatter.to1Decimal(valueInMmol)

fun Profile.Companion.toSignedUnitsString(valueInMgdl: Double, valueInMmol: Double, units: GlucoseUnit): String =
    if (units == GlucoseUnit.MGDL) (if (valueInMgdl > 0) "+" else "") + DecimalFormatter.to0Decimal(valueInMgdl)
    else (if (valueInMmol > 0) "+" else "") + DecimalFormatter.to1Decimal(valueInMmol)

fun Profile.Companion.isMgdl(anyBg: Double) = anyBg >= 39
fun Profile.Companion.isMmol(anyBg: Double) = anyBg < 39
fun Profile.Companion.unit(anyBg: Double) = if (isMgdl(anyBg)) GlucoseUnit.MGDL else GlucoseUnit.MMOL

fun Profile.Companion.toCurrentUnits(profileFunction: ProfileFunction, anyBg: Double): Double =
    if (isMmol(anyBg)) fromMmolToUnits(anyBg, profileFunction.getUnits())
    else fromMgdlToUnits(anyBg, profileFunction.getUnits())

fun Profile.Companion.toCurrentUnits(units: GlucoseUnit, anyBg: Double): Double =
    if (isMmol(anyBg)) fromMmolToUnits(anyBg, units)
    else fromMgdlToUnits(anyBg, units)

fun Profile.Companion.toCurrentUnitsString(profileFunction: ProfileFunction, anyBg: Double): String =
    if (isMmol(anyBg)) toUnitsString(anyBg * Constants.MMOLL_TO_MGDL, anyBg, profileFunction.getUnits())
    else toUnitsString(anyBg, anyBg * Constants.MGDL_TO_MMOLL, profileFunction.getUnits())

fun Profile.Companion.toMgdl(value: Double): Double =
    if (isMgdl(value)) value else value * Constants.MMOLL_TO_MGDL

fun Profile.Companion.toMgdl(value: Double, units: GlucoseUnit): Double =
    if (units == GlucoseUnit.MGDL) value else value * Constants.MMOLL_TO_MGDL

fun Profile.Companion.toMmol(value: Double, units: GlucoseUnit): Double =
    if (units == GlucoseUnit.MGDL) value * Constants.MGDL_TO_MMOLL else value

// targets are stored in mg/dl but profile vary
fun Profile.Companion.toTargetRangeString(low: Double, high: Double, sourceUnits: GlucoseUnit, units: GlucoseUnit): String {
    val lowMgdl = toMgdl(low, sourceUnits)
    val highMgdl = toMgdl(high, sourceUnits)
    val lowMmol = toMmol(low, sourceUnits)
    val highMmol = toMmol(high, sourceUnits)
    return if (low == high) toUnitsString(lowMgdl, lowMmol, units)
    else toUnitsString(lowMgdl, lowMmol, units) + " - " + toUnitsString(highMgdl, highMmol, units)
}

