package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.entry.NsUnits
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSTemporaryTarget
import info.nightscout.shared.utils.T
import java.security.InvalidParameterException

fun NSTemporaryTarget.toTemporaryTarget(): TemporaryTarget =
    TemporaryTarget(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        reason = reason.toReason(),
        highTarget = targetTop.asMgdl(),
        lowTarget = targetBottom.asMgdl(),
        duration = duration,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSTemporaryTarget.Reason?.toReason(): TemporaryTarget.Reason =
    TemporaryTarget.Reason.fromString(this?.text)

fun TemporaryTarget.toNSTemporaryTarget(): NSTemporaryTarget =
    NSTemporaryTarget(
        eventType = EventType.TEMPORARY_TARGET,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        reason = reason.toReason(),
        targetTop = highTarget,
        targetBottom = lowTarget,
        units = NsUnits.MG_DL,
        duration = duration,
        identifier = interfaceIDs.nightscoutId,
        pumpId = interfaceIDs.pumpId,
        pumpType = interfaceIDs.pumpType?.name,
        pumpSerial = interfaceIDs.pumpSerial,
        endId = interfaceIDs.endId
    )

fun TemporaryTarget.Reason?.toReason(): NSTemporaryTarget.Reason =
    NSTemporaryTarget.Reason.fromString(this?.text)
