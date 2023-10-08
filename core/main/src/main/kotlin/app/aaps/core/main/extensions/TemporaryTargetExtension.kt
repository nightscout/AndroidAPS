package app.aaps.core.main.extensions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.db.GlucoseUnit
import app.aaps.core.data.db.TT
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.database.entities.TemporaryTarget
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

fun TT.end() = timestamp + duration

fun TemporaryTarget.Reason.fromDb(): TT.Reason =
    when (this) {
        TemporaryTarget.Reason.CUSTOM       -> TT.Reason.CUSTOM
        TemporaryTarget.Reason.HYPOGLYCEMIA -> TT.Reason.HYPOGLYCEMIA
        TemporaryTarget.Reason.ACTIVITY     -> TT.Reason.ACTIVITY
        TemporaryTarget.Reason.EATING_SOON  -> TT.Reason.EATING_SOON
        TemporaryTarget.Reason.AUTOMATION   -> TT.Reason.AUTOMATION
        TemporaryTarget.Reason.WEAR         -> TT.Reason.WEAR
    }

fun TT.Reason.toDb(): TemporaryTarget.Reason =
    when (this) {
        TT.Reason.CUSTOM       -> TemporaryTarget.Reason.CUSTOM
        TT.Reason.HYPOGLYCEMIA -> TemporaryTarget.Reason.HYPOGLYCEMIA
        TT.Reason.ACTIVITY     -> TemporaryTarget.Reason.ACTIVITY
        TT.Reason.EATING_SOON  -> TemporaryTarget.Reason.EATING_SOON
        TT.Reason.AUTOMATION   -> TemporaryTarget.Reason.AUTOMATION
        TT.Reason.WEAR         -> TemporaryTarget.Reason.WEAR
    }

fun TemporaryTarget.fromDb(): TT =
    TT(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        ids = this.interfaceIDs.fromDb(),
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        reason = this.reason.fromDb(),
        highTarget = this.highTarget,
        lowTarget = this.lowTarget,
        duration = this.duration
    )

fun TT.toDb(): TemporaryTarget =
    TemporaryTarget(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        interfaceIDs_backing = this.ids.toDb(),
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        reason = this.reason.toDb(),
        highTarget = this.highTarget,
        lowTarget = this.lowTarget,
        duration = this.duration
    )
