package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.core.extensions.convertedToAbsolute
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.interfaces.profile.Profile
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal

fun NSTemporaryBasal.toTemporaryBasal(): TemporaryBasal =
    TemporaryBasal(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        type = type.toType(),
        rate = rate,
        isAbsolute = isAbsolute,
        duration = duration,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSTemporaryBasal.Type?.toType(): TemporaryBasal.Type =
    when (this) {
        NSTemporaryBasal.Type.NORMAL                -> TemporaryBasal.Type.NORMAL
        NSTemporaryBasal.Type.EMULATED_PUMP_SUSPEND -> TemporaryBasal.Type.EMULATED_PUMP_SUSPEND
        NSTemporaryBasal.Type.PUMP_SUSPEND          -> TemporaryBasal.Type.PUMP_SUSPEND
        NSTemporaryBasal.Type.SUPERBOLUS            -> TemporaryBasal.Type.SUPERBOLUS
        NSTemporaryBasal.Type.FAKE_EXTENDED         -> TemporaryBasal.Type.FAKE_EXTENDED
        null                                        -> TemporaryBasal.Type.NORMAL
    }

fun TemporaryBasal.toNSTemporaryBasal(profile: Profile): NSTemporaryBasal =
    NSTemporaryBasal(
        eventType = EventType.TEMPORARY_BASAL,
        isValid = isValid,
        date = timestamp,
        utcOffset = utcOffset,
        type = type.toType(),
        rate = convertedToAbsolute(timestamp, profile),
        isAbsolute = isAbsolute,
        absolute = if (isAbsolute) rate else null,
        percent = if (!isAbsolute) rate - 100 else null,
        duration = duration,
        identifier = interfaceIDs.nightscoutId,
        pumpId = interfaceIDs.pumpId,
        pumpType = interfaceIDs.pumpType?.name,
        pumpSerial = interfaceIDs.pumpSerial,
        endId = interfaceIDs.endId
    )

fun TemporaryBasal.Type?.toType(): NSTemporaryBasal.Type =
    when (this) {
        TemporaryBasal.Type.NORMAL                -> NSTemporaryBasal.Type.NORMAL
        TemporaryBasal.Type.EMULATED_PUMP_SUSPEND -> NSTemporaryBasal.Type.EMULATED_PUMP_SUSPEND
        TemporaryBasal.Type.PUMP_SUSPEND          -> NSTemporaryBasal.Type.PUMP_SUSPEND
        TemporaryBasal.Type.SUPERBOLUS            -> NSTemporaryBasal.Type.SUPERBOLUS
        TemporaryBasal.Type.FAKE_EXTENDED         -> NSTemporaryBasal.Type.FAKE_EXTENDED
        null                                      -> NSTemporaryBasal.Type.NORMAL
    }