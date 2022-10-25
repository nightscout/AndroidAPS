package info.nightscout.androidaps.plugins.sync.nsclientV3.extensions

import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.sdk.localmodel.treatment.NSExtendedBolus

fun NSExtendedBolus.toExtendedBolus(): ExtendedBolus =
    ExtendedBolus(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        amount = enteredinsulin,
        duration = duration,
        isEmulatingTempBasal = isEmulatingTempbasal,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )
