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
    when (this) {
        NSOfflineEvent.Reason.DISCONNECT_PUMP -> OfflineEvent.Reason.DISCONNECT_PUMP
        NSOfflineEvent.Reason.SUSPEND         -> OfflineEvent.Reason.SUSPEND
        NSOfflineEvent.Reason.DISABLE_LOOP    -> OfflineEvent.Reason.DISABLE_LOOP
        NSOfflineEvent.Reason.SUPER_BOLUS     -> OfflineEvent.Reason.SUPER_BOLUS
        NSOfflineEvent.Reason.OTHER           -> OfflineEvent.Reason.OTHER
        null                                  -> OfflineEvent.Reason.OTHER
    }