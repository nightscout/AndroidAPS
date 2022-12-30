package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.NSOfflineEvent

fun NSOfflineEvent.toOfflineEvent(): OfflineEvent =
    OfflineEvent(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        duration = duration,
        reason = reason.toReason(),
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSOfflineEvent.Reason?.toReason(): OfflineEvent.Reason =
    OfflineEvent.Reason.fromString(this?.name)
