package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSCarbs
import info.nightscout.shared.utils.T
import java.security.InvalidParameterException

fun NSCarbs.toCarbs(): Carbs =
    Carbs(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        amount = carbs,
        notes = notes,
        duration = duration ?: 0L,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun Carbs.toNSCarbs(): NSCarbs =
    NSCarbs(
        eventType = if (amount < 12) EventType.CARBS_CORRECTION else EventType.MEAL_BOLUS,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        carbs = amount,
        notes = notes,
        duration = if (duration != 0L) duration else null,
        identifier = interfaceIDs.nightscoutId,
        pumpId = interfaceIDs.pumpId,
        pumpType = interfaceIDs.pumpType?.name,
        pumpSerial = interfaceIDs.pumpSerial,
        endId = interfaceIDs.endId
    )
