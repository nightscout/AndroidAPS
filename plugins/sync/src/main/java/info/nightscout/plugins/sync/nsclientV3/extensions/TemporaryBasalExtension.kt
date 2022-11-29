package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.embedments.InterfaceIDs
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