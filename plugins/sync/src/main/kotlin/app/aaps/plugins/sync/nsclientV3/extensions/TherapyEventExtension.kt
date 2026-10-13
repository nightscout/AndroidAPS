package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSTherapyEvent
import java.security.InvalidParameterException

fun NSTherapyEvent.toTherapyEvent(): TE =
    TE(
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
        location = TE.Location.fromString(location),
        arrow = TE.Arrow.fromString(arrow),
        ids = IDs(nightscoutId = identifier, pumpId = pumpId, pumpType = PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun EventType.toType(): TE.Type =
    TE.Type.fromString(this.text)

fun NSTherapyEvent.MeterType?.toMeterType(): TE.MeterType? =
    TE.MeterType.fromString(this?.text)

fun NsUnits?.toUnits(): GlucoseUnit =
    when (this) {
        NsUnits.MG_DL  -> GlucoseUnit.MGDL
        NsUnits.MMOL_L -> GlucoseUnit.MMOL
        null           -> GlucoseUnit.MGDL
    }

fun TE.toNSTherapyEvent(): NSTherapyEvent =
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
        location = location?.text,
        arrow = arrow?.text,
        identifier = ids.nightscoutId,
        pumpId = ids.pumpId,
        pumpType = ids.pumpType?.name,
        pumpSerial = ids.pumpSerial,
        endId = ids.endId
    )

fun TE.Type.toType(): EventType =
    EventType.fromString(this.text)

fun TE.MeterType?.toNSMeterType(): NSTherapyEvent.MeterType? =
    NSTherapyEvent.MeterType.fromString(this?.text)

fun GlucoseUnit?.toUnits(): NsUnits =
    when (this) {
        GlucoseUnit.MGDL -> NsUnits.MG_DL
        GlucoseUnit.MMOL -> NsUnits.MMOL_L
        null             -> NsUnits.MG_DL
    }