package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.OE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSOfflineEvent
import java.security.InvalidParameterException

fun NSOfflineEvent.toOfflineEvent(): OE =
    OE(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        duration = duration,
        reason = reason.toReason(),
        ids = IDs(nightscoutId = identifier, pumpId = pumpId, pumpType = PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSOfflineEvent.Reason?.toReason(): OE.Reason =
    OE.Reason.fromString(this?.name)

fun OE.toNSOfflineEvent(): NSOfflineEvent =
    NSOfflineEvent(
        eventType = EventType.APS_OFFLINE,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        duration = duration,
        reason = reason.toReason(),
        identifier = ids.nightscoutId,
        pumpId = ids.pumpId,
        pumpType = ids.pumpType?.name,
        pumpSerial = ids.pumpSerial,
        endId = ids.endId
    )

fun OE.Reason?.toReason(): NSOfflineEvent.Reason =
    NSOfflineEvent.Reason.fromString(this?.name)
