package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.interfaces.Constants
import java.util.concurrent.TimeUnit

fun TemporaryTarget.isInProgress(dateUtil: DateUtil): Boolean =
    dateUtil.now() in timestamp..timestamp + duration

fun TemporaryTarget.lowValueToUnitsToString(units: GlucoseUnit): String =
    if (units == GlucoseUnit.MGDL) DecimalFormatter.to0Decimal(this.lowTarget)
    else DecimalFormatter.to1Decimal(this.lowTarget * Constants.MGDL_TO_MMOLL)

fun TemporaryTarget.highValueToUnitsToString(units: GlucoseUnit): String =
    if (units == GlucoseUnit.MGDL) DecimalFormatter.to0Decimal(this.highTarget)
    else DecimalFormatter.to1Decimal(this.highTarget * Constants.MGDL_TO_MMOLL)

fun TemporaryTarget.target(): Double =
    (this.lowTarget + this.highTarget) / 2

fun TemporaryTarget.friendlyDescription(units: GlucoseUnit, rh: ResourceHelper): String =
    Profile.toTargetRangeString(lowTarget, highTarget, GlucoseUnit.MGDL, units) +
        units.asText +
        "@" + rh.gs(R.string.format_mins, TimeUnit.MILLISECONDS.toMinutes(duration)) + "(" + reason.text + ")"
