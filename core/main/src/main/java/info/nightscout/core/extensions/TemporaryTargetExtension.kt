package info.nightscout.core.extensions

import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.interfaces.ProfileUtil
import info.nightscout.shared.interfaces.ResourceHelper
import java.util.concurrent.TimeUnit

fun TemporaryTarget.lowValueToUnitsToString(units: GlucoseUnit, decimalFormatter: DecimalFormatter): String =
    if (units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(this.lowTarget)
    else decimalFormatter.to1Decimal(this.lowTarget * Constants.MGDL_TO_MMOLL)

fun TemporaryTarget.highValueToUnitsToString(units: GlucoseUnit, decimalFormatter: DecimalFormatter): String =
    if (units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(this.highTarget)
    else decimalFormatter.to1Decimal(this.highTarget * Constants.MGDL_TO_MMOLL)

fun TemporaryTarget.target(): Double =
    (this.lowTarget + this.highTarget) / 2

fun TemporaryTarget.friendlyDescription(units: GlucoseUnit, rh: ResourceHelper, profileUtil: ProfileUtil): String =
    profileUtil.toTargetRangeString(lowTarget, highTarget, GlucoseUnit.MGDL, units) +
        units.asText +
        "@" + rh.gs(info.nightscout.core.ui.R.string.format_mins, TimeUnit.MILLISECONDS.toMinutes(duration)) + "(" + reason.text + ")"
