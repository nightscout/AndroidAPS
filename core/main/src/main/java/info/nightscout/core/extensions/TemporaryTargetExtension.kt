package info.nightscout.core.extensions

import app.aaps.interfaces.configuration.Constants
import app.aaps.interfaces.db.GlucoseUnit
import app.aaps.interfaces.profile.ProfileUtil
import app.aaps.interfaces.resources.ResourceHelper
import app.aaps.interfaces.utils.DecimalFormatter
import info.nightscout.database.entities.TemporaryTarget
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
