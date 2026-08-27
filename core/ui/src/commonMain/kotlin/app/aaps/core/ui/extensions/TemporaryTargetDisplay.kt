package app.aaps.core.ui.extensions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.CoreUiStrings
import kotlin.time.Duration.Companion.milliseconds

/**
 * Text for a [TT] on screen.
 *
 * The maths that belongs to the model - `TT.target()` - stays in `:core:objects`. Only the part that
 * formats for a reader lives here, next to the strings it uses.
 */
fun TT.lowValueToUnitsToString(units: GlucoseUnit, decimalFormatter: DecimalFormatter): String =
    if (units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(this.lowTarget)
    else decimalFormatter.to1Decimal(this.lowTarget * Constants.MGDL_TO_MMOLL)

fun TT.highValueToUnitsToString(units: GlucoseUnit, decimalFormatter: DecimalFormatter): String =
    if (units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(this.highTarget)
    else decimalFormatter.to1Decimal(this.highTarget * Constants.MGDL_TO_MMOLL)

fun TT.friendlyDescription(units: GlucoseUnit, rh: TextResolver, profileUtil: ProfileUtil): String =
    profileUtil.toTargetRangeString(lowTarget, highTarget, GlucoseUnit.MGDL, units) +
        profileUtil.unitLabel +
        "@" + rh.gs(CoreUiStrings.format_mins, duration.milliseconds.inWholeMinutes) + "(" + reason.text + ")"
