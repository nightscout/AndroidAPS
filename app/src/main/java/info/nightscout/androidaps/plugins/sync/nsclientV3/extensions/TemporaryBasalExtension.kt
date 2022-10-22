package info.nightscout.androidaps.plugins.sync.nsclientV3.extensions

import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.TemporaryBasal

fun info.nightscout.sdk.localmodel.treatment.TemporaryBasal.toTemporaryBasal(): TemporaryBasal =
    TemporaryBasal(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        type = type.toType(),
        rate = rate,
        isAbsolute = isAbsolute,
        duration = duration,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial)
    )

fun info.nightscout.sdk.localmodel.treatment.TemporaryBasal.Type?.toType(): TemporaryBasal.Type =
    when (this) {
        info.nightscout.sdk.localmodel.treatment.TemporaryBasal.Type.NORMAL                -> TemporaryBasal.Type.NORMAL
        info.nightscout.sdk.localmodel.treatment.TemporaryBasal.Type.EMULATED_PUMP_SUSPEND -> TemporaryBasal.Type.EMULATED_PUMP_SUSPEND
        info.nightscout.sdk.localmodel.treatment.TemporaryBasal.Type.PUMP_SUSPEND          -> TemporaryBasal.Type.PUMP_SUSPEND
        info.nightscout.sdk.localmodel.treatment.TemporaryBasal.Type.SUPERBOLUS            -> TemporaryBasal.Type.SUPERBOLUS
        info.nightscout.sdk.localmodel.treatment.TemporaryBasal.Type.FAKE_EXTENDED         -> TemporaryBasal.Type.FAKE_EXTENDED
        null                                                                               -> TemporaryBasal.Type.NORMAL
    }