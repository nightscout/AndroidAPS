package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.NSTemporaryTarget

fun NSTemporaryTarget.toTemporaryTarget(): TemporaryTarget =
    TemporaryTarget(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        reason = reason.toReason(),
        highTarget = targetTop.asMgdl(),
        lowTarget = targetBottom.asMgdl(),
        duration = duration,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSTemporaryTarget.Reason?.toReason(): TemporaryTarget.Reason =
    when (this) {
        NSTemporaryTarget.Reason.CUSTOM       -> TemporaryTarget.Reason.CUSTOM
        NSTemporaryTarget.Reason.HYPOGLYCEMIA -> TemporaryTarget.Reason.HYPOGLYCEMIA
        NSTemporaryTarget.Reason.ACTIVITY     -> TemporaryTarget.Reason.ACTIVITY
        NSTemporaryTarget.Reason.EATING_SOON  -> TemporaryTarget.Reason.EATING_SOON
        NSTemporaryTarget.Reason.AUTOMATION   -> TemporaryTarget.Reason.AUTOMATION
        NSTemporaryTarget.Reason.WEAR         -> TemporaryTarget.Reason.WEAR
        null                                  -> TemporaryTarget.Reason.CUSTOM
    }