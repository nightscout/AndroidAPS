package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSBolus

fun NSBolus.toBolus(): Bolus =
    Bolus(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        amount = insulin,
        type = type.toBolusType(),
        notes = notes,
        isBasalInsulin = isBasalInsulin,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSBolus.BolusType?.toBolusType(): Bolus.Type =
    when (this) {
        NSBolus.BolusType.NORMAL  -> Bolus.Type.NORMAL
        NSBolus.BolusType.SMB     -> Bolus.Type.SMB
        NSBolus.BolusType.PRIMING -> Bolus.Type.PRIMING
        null                      -> Bolus.Type.NORMAL
    }

fun Bolus.toNSBolus(): NSBolus =
    NSBolus(
        eventType = if (type == Bolus.Type.SMB) EventType.CORRECTION_BOLUS else EventType.MEAL_BOLUS,
        isValid = isValid,
        date = timestamp,
        utcOffset = utcOffset,
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
    when (this) {
        Bolus.Type.NORMAL  -> NSBolus.BolusType.NORMAL
        Bolus.Type.SMB     -> NSBolus.BolusType.SMB
        Bolus.Type.PRIMING -> NSBolus.BolusType.PRIMING
        null               -> NSBolus.BolusType.NORMAL
    }