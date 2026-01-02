package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.RM
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSOfflineEvent
import java.security.InvalidParameterException

fun NSOfflineEvent.toRunningMode(): RM =
    RM(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        duration = originalDuration ?: duration,
        mode = mode.toMode(),
        ids = IDs(nightscoutId = identifier, pumpId = pumpId, pumpType = PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSOfflineEvent.Mode?.toMode(): RM.Mode =
    RM.Mode.fromString(this?.name)

fun RM.toNSOfflineEvent(): NSOfflineEvent =
    when (mode) {
        RM.Mode.OPEN_LOOP,
        RM.Mode.CLOSED_LOOP,
        RM.Mode.CLOSED_LOOP_LGS   ->
            NSOfflineEvent(
                eventType = EventType.APS_OFFLINE,
                isValid = isValid,
                date = timestamp,
                utcOffset = T.msecs(utcOffset).mins(),
                duration = 0,
                originalDuration = duration,
                reason = NSOfflineEvent.Reason.OTHER, // Unused
                mode = mode.toMode(),
                autoForced = autoForced,
                reasons = reasons,
                identifier = ids.nightscoutId,
                pumpId = ids.pumpId,
                pumpType = ids.pumpType?.name,
                pumpSerial = ids.pumpSerial,
                endId = ids.endId
            )

        RM.Mode.DISABLED_LOOP,
        RM.Mode.SUPER_BOLUS,
        RM.Mode.DISCONNECTED_PUMP,
        RM.Mode.SUSPENDED_BY_PUMP,
        RM.Mode.SUSPENDED_BY_DST,
        RM.Mode.SUSPENDED_BY_USER ->
            NSOfflineEvent(
                eventType = EventType.APS_OFFLINE,
                isValid = isValid,
                date = timestamp,
                utcOffset = T.msecs(utcOffset).mins(),
                duration = duration,
                originalDuration = duration,
                reason = NSOfflineEvent.Reason.OTHER, // Unused
                mode = mode.toMode(),
                autoForced = autoForced,
                reasons = reasons,
                identifier = ids.nightscoutId,
                pumpId = ids.pumpId,
                pumpType = ids.pumpType?.name,
                pumpSerial = ids.pumpSerial,
                endId = ids.endId
            )

        RM.Mode.RESUME            -> error("Invalid mode")
    }

fun RM.Mode?.toMode(): NSOfflineEvent.Mode =
    NSOfflineEvent.Mode.fromString(this?.name)
