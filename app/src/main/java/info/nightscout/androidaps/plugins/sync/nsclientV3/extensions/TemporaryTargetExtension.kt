package info.nightscout.androidaps.plugins.sync.nsclientV3.extensions

import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.TemporaryTarget

fun info.nightscout.sdk.localmodel.treatment.TemporaryTarget.toTemporaryTarget(): TemporaryTarget =
    TemporaryTarget(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        reason = reason.toReason(),
        highTarget = targetTop.asMgdl(),
        lowTarget = targetBottom.asMgdl(),
        duration = duration,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier)
    )

fun info.nightscout.sdk.localmodel.treatment.TemporaryTarget.Reason?.toReason(): TemporaryTarget.Reason =
    when (this) {
        info.nightscout.sdk.localmodel.treatment.TemporaryTarget.Reason.CUSTOM       -> TemporaryTarget.Reason.CUSTOM
        info.nightscout.sdk.localmodel.treatment.TemporaryTarget.Reason.HYPOGLYCEMIA -> TemporaryTarget.Reason.HYPOGLYCEMIA
        info.nightscout.sdk.localmodel.treatment.TemporaryTarget.Reason.ACTIVITY     -> TemporaryTarget.Reason.ACTIVITY
        info.nightscout.sdk.localmodel.treatment.TemporaryTarget.Reason.EATING_SOON  -> TemporaryTarget.Reason.EATING_SOON
        info.nightscout.sdk.localmodel.treatment.TemporaryTarget.Reason.AUTOMATION   -> TemporaryTarget.Reason.AUTOMATION
        info.nightscout.sdk.localmodel.treatment.TemporaryTarget.Reason.WEAR         -> TemporaryTarget.Reason.WEAR
        null                                                                         -> TemporaryTarget.Reason.CUSTOM
    }