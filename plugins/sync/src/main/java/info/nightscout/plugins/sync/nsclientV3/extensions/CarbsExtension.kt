package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.NSCarbs

fun NSCarbs.toCarbs(): Carbs =
    Carbs(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        amount = carbs,
        notes = notes,
        duration = duration,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )
