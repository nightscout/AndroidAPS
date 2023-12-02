package app.aaps.core.objects.extensions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import java.util.concurrent.TimeUnit

fun TT.lowValueToUnitsToString(units: GlucoseUnit, decimalFormatter: DecimalFormatter): String =
    if (units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(this.lowTarget)
    else decimalFormatter.to1Decimal(this.lowTarget * Constants.MGDL_TO_MMOLL)

fun TT.highValueToUnitsToString(units: GlucoseUnit, decimalFormatter: DecimalFormatter): String =
    if (units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(this.highTarget)
    else decimalFormatter.to1Decimal(this.highTarget * Constants.MGDL_TO_MMOLL)

fun TT.target(): Double =
    (this.lowTarget + this.highTarget) / 2

fun TT.friendlyDescription(units: GlucoseUnit, rh: ResourceHelper, profileUtil: ProfileUtil): String =
    profileUtil.toTargetRangeString(lowTarget, highTarget, GlucoseUnit.MGDL, units) +
        units.asText +
        "@" + rh.gs(app.aaps.core.ui.R.string.format_mins, TimeUnit.MILLISECONDS.toMinutes(duration)) + "(" + reason.text + ")"
