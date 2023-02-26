package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSBolus
import info.nightscout.shared.utils.T
import java.security.InvalidParameterException

fun NSBolus.toBolus(): Bolus =
    Bolus(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        amount = insulin,
        type = type.toBolusType(),
        notes = notes,
        isBasalInsulin = isBasalInsulin,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSBolus.BolusType?.toBolusType(): Bolus.Type =
    Bolus.Type.fromString(this?.name)

fun Bolus.toNSBolus(): NSBolus =
    NSBolus(
        eventType = if (type == Bolus.Type.SMB) EventType.CORRECTION_BOLUS else EventType.MEAL_BOLUS,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        insulin = amount,
        type = type.toBolusType(),
        notes = notes,
        isBasalInsulin = isBasalInsulin,
        identifier = interfaceIDs.nightscoutId,
        pumpId = interfaceIDs.pumpId,
        pumpType = interfaceIDs.pumpType?.name,
        pumpSerial = interfaceIDs.pumpSerial,
        endId = interfaceIDs.endId
    )

fun Bolus.Type?.toBolusType(): NSBolus.BolusType =
    NSBolus.BolusType.fromString(this?.name)
