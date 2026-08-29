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
        autoForced = autoForced,
        reasons = reasons,
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

        // DISABLED_LOOP can be open-ended either as "user permanent" (duration == 0) or as
        // "auto-forced by constraint" (duration == Long.MAX_VALUE — see LoopPlugin.runningModePreCheck).
        // NS only renders an offline window when treatment.duration > 0, so substitute a long-but-finite
        // wire duration. originalDuration is normalized to 0 for both open-ended cases — Long.MAX_VALUE
        // does not round-trip through JSON cleanly (exceeds JS Number precision at 2^53), so giving the
        // AAPSCLIENT a clean 0 is both safer and sufficient (autoForced is carried separately).
        RM.Mode.DISABLED_LOOP     -> {
            val isOpenEnded = duration == 0L || duration == Long.MAX_VALUE
            NSOfflineEvent(
                eventType = EventType.APS_OFFLINE,
                isValid = isValid,
                date = timestamp,
                utcOffset = T.msecs(utcOffset).mins(),
                duration = if (isOpenEnded) T.days(365L * 10).msecs() else duration,
                originalDuration = if (isOpenEnded) 0L else duration,
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
        }

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
