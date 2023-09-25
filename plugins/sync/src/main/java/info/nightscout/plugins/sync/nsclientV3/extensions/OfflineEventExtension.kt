package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.interfaces.utils.T
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSOfflineEvent
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.embedments.InterfaceIDs
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
