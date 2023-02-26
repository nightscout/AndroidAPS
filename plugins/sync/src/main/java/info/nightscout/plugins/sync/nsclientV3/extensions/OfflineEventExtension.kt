package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSOfflineEvent
import info.nightscout.shared.utils.T
import java.security.InvalidParameterException

fun NSOfflineEvent.toOfflineEvent(): OfflineEvent =
    OfflineEvent(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        duration = duration,
        reason = reason.toReason(),
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSOfflineEvent.Reason?.toReason(): OfflineEvent.Reason =
    OfflineEvent.Reason.fromString(this?.name)

fun OfflineEvent.toNSOfflineEvent(): NSOfflineEvent =
    NSOfflineEvent(
        eventType = EventType.APS_OFFLINE,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        duration = duration,
        reason = reason.toReason(),
        identifier = interfaceIDs.nightscoutId,
        pumpId = interfaceIDs.pumpId,
        pumpType = interfaceIDs.pumpType?.name,
        pumpSerial = interfaceIDs.pumpSerial,
        endId = interfaceIDs.endId
    )

fun OfflineEvent.Reason?.toReason(): NSOfflineEvent.Reason =
    NSOfflineEvent.Reason.fromString(this?.name)
