package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.entry.NsUnits
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSTherapyEvent
import info.nightscout.shared.utils.T
import java.security.InvalidParameterException

fun NSTherapyEvent.toTherapyEvent(): TherapyEvent =
    TherapyEvent(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        glucoseUnit = units.toUnits(),
        type = eventType.toType(),
        note = notes,
        enteredBy = enteredBy,
        glucose = glucose,
        glucoseType = glucoseType.toMeterType(),
        duration = duration,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun EventType.toType(): TherapyEvent.Type =
    TherapyEvent.Type.fromString(this.text)

fun NSTherapyEvent.MeterType?.toMeterType(): TherapyEvent.MeterType? =
    TherapyEvent.MeterType.fromString(this?.text)

fun NsUnits?.toUnits(): TherapyEvent.GlucoseUnit =
    when (this) {
        NsUnits.MG_DL  -> TherapyEvent.GlucoseUnit.MGDL
        NsUnits.MMOL_L -> TherapyEvent.GlucoseUnit.MMOL
        null           -> TherapyEvent.GlucoseUnit.MGDL
    }

fun TherapyEvent.toNSTherapyEvent(): NSTherapyEvent =
    NSTherapyEvent(
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        units = glucoseUnit.toUnits(),
        eventType = type.toType(),
        notes = note,
        enteredBy = enteredBy,
        glucose = glucose,
        glucoseType = glucoseType.toNSMeterType(),
        duration = duration,
        identifier = interfaceIDs.nightscoutId,
        pumpId = interfaceIDs.pumpId,
        pumpType = interfaceIDs.pumpType?.name,
        pumpSerial = interfaceIDs.pumpSerial,
        endId = interfaceIDs.endId
    )

fun TherapyEvent.Type.toType(): EventType =
    EventType.fromString(this.text)

fun TherapyEvent.MeterType?.toNSMeterType(): NSTherapyEvent.MeterType? =
    NSTherapyEvent.MeterType.fromString(this?.text)

fun TherapyEvent.GlucoseUnit?.toUnits(): NsUnits =
    when (this) {
        TherapyEvent.GlucoseUnit.MGDL -> NsUnits.MG_DL
        TherapyEvent.GlucoseUnit.MMOL -> NsUnits.MMOL_L
        null                          -> NsUnits.MG_DL
    }