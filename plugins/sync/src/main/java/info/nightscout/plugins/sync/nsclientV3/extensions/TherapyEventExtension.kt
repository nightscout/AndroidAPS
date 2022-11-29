package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.entry.NsUnits
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSTherapyEvent

fun NSTherapyEvent.toTherapyEvent(): TherapyEvent =
    TherapyEvent(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
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
    when (this) {
        EventType.CANNULA_CHANGE          -> TherapyEvent.Type.CANNULA_CHANGE
        EventType.INSULIN_CHANGE          -> TherapyEvent.Type.INSULIN_CHANGE
        EventType.PUMP_BATTERY_CHANGE     -> TherapyEvent.Type.PUMP_BATTERY_CHANGE
        EventType.SENSOR_CHANGE           -> TherapyEvent.Type.SENSOR_CHANGE
        EventType.SENSOR_STARTED          -> TherapyEvent.Type.SENSOR_STARTED
        EventType.SENSOR_STOPPED          -> TherapyEvent.Type.SENSOR_STOPPED
        EventType.FINGER_STICK_BG_VALUE   -> TherapyEvent.Type.FINGER_STICK_BG_VALUE
        EventType.EXERCISE                -> TherapyEvent.Type.EXERCISE
        EventType.ANNOUNCEMENT            -> TherapyEvent.Type.ANNOUNCEMENT
        EventType.QUESTION                -> TherapyEvent.Type.QUESTION
        EventType.NOTE                    -> TherapyEvent.Type.NOTE
        EventType.APS_OFFLINE             -> TherapyEvent.Type.APS_OFFLINE
        EventType.DAD_ALERT               -> TherapyEvent.Type.DAD_ALERT
        EventType.NS_MBG                  -> TherapyEvent.Type.NS_MBG
        EventType.CARBS_CORRECTION        -> TherapyEvent.Type.CARBS_CORRECTION
        EventType.BOLUS_WIZARD            -> TherapyEvent.Type.BOLUS_WIZARD
        EventType.CORRECTION_BOLUS        -> TherapyEvent.Type.CORRECTION_BOLUS
        EventType.MEAL_BOLUS              -> TherapyEvent.Type.MEAL_BOLUS
        EventType.COMBO_BOLUS             -> TherapyEvent.Type.COMBO_BOLUS
        EventType.TEMPORARY_TARGET        -> TherapyEvent.Type.TEMPORARY_TARGET
        EventType.TEMPORARY_TARGET_CANCEL -> TherapyEvent.Type.TEMPORARY_TARGET_CANCEL
        EventType.PROFILE_SWITCH          -> TherapyEvent.Type.PROFILE_SWITCH
        EventType.SNACK_BOLUS             -> TherapyEvent.Type.SNACK_BOLUS
        EventType.TEMPORARY_BASAL         -> TherapyEvent.Type.TEMPORARY_BASAL
        EventType.TEMPORARY_BASAL_START   -> TherapyEvent.Type.TEMPORARY_BASAL_START
        EventType.TEMPORARY_BASAL_END     -> TherapyEvent.Type.TEMPORARY_BASAL_END
        EventType.NONE                    -> TherapyEvent.Type.NONE
    }

fun NSTherapyEvent.MeterType?.toMeterType(): TherapyEvent.MeterType =
    when (this) {
        NSTherapyEvent.MeterType.FINGER -> TherapyEvent.MeterType.FINGER
        NSTherapyEvent.MeterType.SENSOR -> TherapyEvent.MeterType.SENSOR
        NSTherapyEvent.MeterType.MANUAL -> TherapyEvent.MeterType.MANUAL
        null                            -> TherapyEvent.MeterType.MANUAL
    }

fun NsUnits?.toUnits(): TherapyEvent.GlucoseUnit =
    when (this) {
        NsUnits.MG_DL  -> TherapyEvent.GlucoseUnit.MGDL
        NsUnits.MMOL_L -> TherapyEvent.GlucoseUnit.MMOL
        null           -> TherapyEvent.GlucoseUnit.MGDL
    }