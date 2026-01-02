package app.aaps.database.persistence.converters

import app.aaps.core.data.model.TE
import app.aaps.database.entities.TherapyEvent

fun TherapyEvent.Type.fromDb(): TE.Type = when (this) {
    TherapyEvent.Type.CANNULA_CHANGE          -> TE.Type.CANNULA_CHANGE
    TherapyEvent.Type.INSULIN_CHANGE          -> TE.Type.INSULIN_CHANGE
    TherapyEvent.Type.SETTINGS_EXPORT         -> TE.Type.SETTINGS_EXPORT
    TherapyEvent.Type.PUMP_BATTERY_CHANGE     -> TE.Type.PUMP_BATTERY_CHANGE
    TherapyEvent.Type.SENSOR_CHANGE           -> TE.Type.SENSOR_CHANGE
    TherapyEvent.Type.SENSOR_STARTED          -> TE.Type.SENSOR_STARTED
    TherapyEvent.Type.SENSOR_STOPPED          -> TE.Type.SENSOR_STOPPED
    TherapyEvent.Type.FINGER_STICK_BG_VALUE   -> TE.Type.FINGER_STICK_BG_VALUE
    TherapyEvent.Type.EXERCISE                -> TE.Type.EXERCISE
    TherapyEvent.Type.ANNOUNCEMENT            -> TE.Type.ANNOUNCEMENT
    TherapyEvent.Type.QUESTION                -> TE.Type.QUESTION
    TherapyEvent.Type.NOTE                    -> TE.Type.NOTE
    TherapyEvent.Type.APS_OFFLINE             -> TE.Type.APS_OFFLINE
    TherapyEvent.Type.DAD_ALERT               -> TE.Type.DAD_ALERT
    TherapyEvent.Type.NS_MBG                  -> TE.Type.NS_MBG
    TherapyEvent.Type.CARBS_CORRECTION        -> TE.Type.CARBS_CORRECTION
    TherapyEvent.Type.BOLUS_WIZARD            -> TE.Type.BOLUS_WIZARD
    TherapyEvent.Type.CORRECTION_BOLUS        -> TE.Type.CORRECTION_BOLUS
    TherapyEvent.Type.MEAL_BOLUS              -> TE.Type.MEAL_BOLUS
    TherapyEvent.Type.COMBO_BOLUS             -> TE.Type.COMBO_BOLUS
    TherapyEvent.Type.TEMPORARY_TARGET        -> TE.Type.TEMPORARY_TARGET
    TherapyEvent.Type.TEMPORARY_TARGET_CANCEL -> TE.Type.TEMPORARY_TARGET_CANCEL
    TherapyEvent.Type.PROFILE_SWITCH          -> TE.Type.PROFILE_SWITCH
    TherapyEvent.Type.SNACK_BOLUS             -> TE.Type.SNACK_BOLUS
    TherapyEvent.Type.TEMPORARY_BASAL         -> TE.Type.TEMPORARY_BASAL
    TherapyEvent.Type.TEMPORARY_BASAL_START   -> TE.Type.TEMPORARY_BASAL_START
    TherapyEvent.Type.TEMPORARY_BASAL_END     -> TE.Type.TEMPORARY_BASAL_END
    TherapyEvent.Type.TUBE_CHANGE             -> TE.Type.TUBE_CHANGE
    TherapyEvent.Type.FALLING_ASLEEP          -> TE.Type.FALLING_ASLEEP
    TherapyEvent.Type.BATTERY_EMPTY           -> TE.Type.BATTERY_EMPTY
    TherapyEvent.Type.RESERVOIR_EMPTY         -> TE.Type.RESERVOIR_EMPTY
    TherapyEvent.Type.OCCLUSION               -> TE.Type.OCCLUSION
    TherapyEvent.Type.PUMP_STOPPED            -> TE.Type.PUMP_STOPPED
    TherapyEvent.Type.PUMP_STARTED            -> TE.Type.PUMP_STARTED
    TherapyEvent.Type.PUMP_PAUSED             -> TE.Type.PUMP_PAUSED
    TherapyEvent.Type.WAKING_UP               -> TE.Type.WAKING_UP
    TherapyEvent.Type.SICKNESS                -> TE.Type.SICKNESS
    TherapyEvent.Type.STRESS                  -> TE.Type.STRESS
    TherapyEvent.Type.PRE_PERIOD              -> TE.Type.PRE_PERIOD
    TherapyEvent.Type.ALCOHOL                 -> TE.Type.ALCOHOL
    TherapyEvent.Type.CORTISONE               -> TE.Type.CORTISONE
    TherapyEvent.Type.FEELING_LOW             -> TE.Type.FEELING_LOW
    TherapyEvent.Type.FEELING_HIGH            -> TE.Type.FEELING_HIGH
    TherapyEvent.Type.LEAKING_INFUSION_SET    -> TE.Type.LEAKING_INFUSION_SET
    TherapyEvent.Type.NONE                    -> TE.Type.NONE
}

fun TE.Type.toDb(): TherapyEvent.Type = when (this) {
    TE.Type.CANNULA_CHANGE          -> TherapyEvent.Type.CANNULA_CHANGE
    TE.Type.INSULIN_CHANGE          -> TherapyEvent.Type.INSULIN_CHANGE
    TE.Type.SETTINGS_EXPORT         -> TherapyEvent.Type.SETTINGS_EXPORT
    TE.Type.PUMP_BATTERY_CHANGE     -> TherapyEvent.Type.PUMP_BATTERY_CHANGE
    TE.Type.SENSOR_CHANGE           -> TherapyEvent.Type.SENSOR_CHANGE
    TE.Type.SENSOR_STARTED          -> TherapyEvent.Type.SENSOR_STARTED
    TE.Type.SENSOR_STOPPED          -> TherapyEvent.Type.SENSOR_STOPPED
    TE.Type.FINGER_STICK_BG_VALUE   -> TherapyEvent.Type.FINGER_STICK_BG_VALUE
    TE.Type.EXERCISE                -> TherapyEvent.Type.EXERCISE
    TE.Type.ANNOUNCEMENT            -> TherapyEvent.Type.ANNOUNCEMENT
    TE.Type.QUESTION                -> TherapyEvent.Type.QUESTION
    TE.Type.NOTE                    -> TherapyEvent.Type.NOTE
    TE.Type.APS_OFFLINE             -> TherapyEvent.Type.APS_OFFLINE
    TE.Type.DAD_ALERT               -> TherapyEvent.Type.DAD_ALERT
    TE.Type.NS_MBG                  -> TherapyEvent.Type.NS_MBG
    TE.Type.CARBS_CORRECTION        -> TherapyEvent.Type.CARBS_CORRECTION
    TE.Type.BOLUS_WIZARD            -> TherapyEvent.Type.BOLUS_WIZARD
    TE.Type.CORRECTION_BOLUS        -> TherapyEvent.Type.CORRECTION_BOLUS
    TE.Type.MEAL_BOLUS              -> TherapyEvent.Type.MEAL_BOLUS
    TE.Type.COMBO_BOLUS             -> TherapyEvent.Type.COMBO_BOLUS
    TE.Type.TEMPORARY_TARGET        -> TherapyEvent.Type.TEMPORARY_TARGET
    TE.Type.TEMPORARY_TARGET_CANCEL -> TherapyEvent.Type.TEMPORARY_TARGET_CANCEL
    TE.Type.PROFILE_SWITCH          -> TherapyEvent.Type.PROFILE_SWITCH
    TE.Type.SNACK_BOLUS             -> TherapyEvent.Type.SNACK_BOLUS
    TE.Type.TEMPORARY_BASAL         -> TherapyEvent.Type.TEMPORARY_BASAL
    TE.Type.TEMPORARY_BASAL_START   -> TherapyEvent.Type.TEMPORARY_BASAL_START
    TE.Type.TEMPORARY_BASAL_END     -> TherapyEvent.Type.TEMPORARY_BASAL_END
    TE.Type.TUBE_CHANGE             -> TherapyEvent.Type.TUBE_CHANGE
    TE.Type.FALLING_ASLEEP          -> TherapyEvent.Type.FALLING_ASLEEP
    TE.Type.BATTERY_EMPTY           -> TherapyEvent.Type.BATTERY_EMPTY
    TE.Type.RESERVOIR_EMPTY         -> TherapyEvent.Type.RESERVOIR_EMPTY
    TE.Type.OCCLUSION               -> TherapyEvent.Type.OCCLUSION
    TE.Type.PUMP_STOPPED            -> TherapyEvent.Type.PUMP_STOPPED
    TE.Type.PUMP_STARTED            -> TherapyEvent.Type.PUMP_STARTED
    TE.Type.PUMP_PAUSED             -> TherapyEvent.Type.PUMP_PAUSED
    TE.Type.WAKING_UP               -> TherapyEvent.Type.WAKING_UP
    TE.Type.SICKNESS                -> TherapyEvent.Type.SICKNESS
    TE.Type.STRESS                  -> TherapyEvent.Type.STRESS
    TE.Type.PRE_PERIOD              -> TherapyEvent.Type.PRE_PERIOD
    TE.Type.ALCOHOL                 -> TherapyEvent.Type.ALCOHOL
    TE.Type.CORTISONE               -> TherapyEvent.Type.CORTISONE
    TE.Type.FEELING_LOW             -> TherapyEvent.Type.FEELING_LOW
    TE.Type.FEELING_HIGH            -> TherapyEvent.Type.FEELING_HIGH
    TE.Type.LEAKING_INFUSION_SET    -> TherapyEvent.Type.LEAKING_INFUSION_SET
    TE.Type.NONE                    -> TherapyEvent.Type.NONE
}

fun TherapyEvent.MeterType.fromDb(): TE.MeterType = when (this) {
    TherapyEvent.MeterType.FINGER -> TE.MeterType.FINGER
    TherapyEvent.MeterType.SENSOR -> TE.MeterType.SENSOR
    TherapyEvent.MeterType.MANUAL -> TE.MeterType.MANUAL
}

fun TE.MeterType.toDb(): TherapyEvent.MeterType = when (this) {
    TE.MeterType.FINGER -> TherapyEvent.MeterType.FINGER
    TE.MeterType.SENSOR -> TherapyEvent.MeterType.SENSOR
    TE.MeterType.MANUAL -> TherapyEvent.MeterType.MANUAL
}

fun TherapyEvent.Location.fromDb(): TE.Location = when (this) {
    TherapyEvent.Location.FRONT_RIGHT_UPPER_CHEST   -> TE.Location.FRONT_RIGHT_UPPER_CHEST
    TherapyEvent.Location.FRONT_LEFT_UPPER_CHEST    -> TE.Location.FRONT_LEFT_UPPER_CHEST
    TherapyEvent.Location.SIDE_RIGHT_UPPER_ARM      -> TE.Location.SIDE_RIGHT_UPPER_ARM
    TherapyEvent.Location.SIDE_LEFT_UPPER_ARM       -> TE.Location.SIDE_LEFT_UPPER_ARM
    TherapyEvent.Location.BACK_RIGHT_UPPER_ARM      -> TE.Location.BACK_RIGHT_UPPER_ARM
    TherapyEvent.Location.BACK_LEFT_UPPER_ARM       -> TE.Location.BACK_LEFT_UPPER_ARM
    TherapyEvent.Location.SIDE_RIGHT_UPPER_ABDOMEN  -> TE.Location.SIDE_RIGHT_UPPER_ABDOMEN
    TherapyEvent.Location.SIDE_LEFT_UPPER_ABDOMEN   -> TE.Location.SIDE_LEFT_UPPER_ABDOMEN
    TherapyEvent.Location.SIDE_RIGHT_LOWER_ABDOMEN  -> TE.Location.SIDE_RIGHT_LOWER_ABDOMEN
    TherapyEvent.Location.SIDE_LEFT_LOWER_ABDOMEN   -> TE.Location.SIDE_LEFT_LOWER_ABDOMEN
    TherapyEvent.Location.FRONT_RIGHT_UPPER_ABDOMEN -> TE.Location.FRONT_RIGHT_UPPER_ABDOMEN
    TherapyEvent.Location.FRONT_LEFT_UPPER_ABDOMEN  -> TE.Location.FRONT_LEFT_UPPER_ABDOMEN
    TherapyEvent.Location.FRONT_RIGHT_LOWER_ABDOMEN -> TE.Location.FRONT_RIGHT_LOWER_ABDOMEN
    TherapyEvent.Location.FRONT_LEFT_LOWER_ABDOMEN  -> TE.Location.FRONT_LEFT_LOWER_ABDOMEN
    TherapyEvent.Location.BACK_RIGHT_BUTTOCK        -> TE.Location.BACK_RIGHT_BUTTOCK
    TherapyEvent.Location.BACK_LEFT_BUTTOCK         -> TE.Location.BACK_LEFT_BUTTOCK
    TherapyEvent.Location.FRONT_RIGHT_UPPER_THIGH   -> TE.Location.FRONT_RIGHT_UPPER_THIGH
    TherapyEvent.Location.FRONT_LEFT_UPPER_THIGH    -> TE.Location.FRONT_LEFT_UPPER_THIGH
    TherapyEvent.Location.FRONT_RIGHT_LOWER_THIGH   -> TE.Location.FRONT_RIGHT_LOWER_THIGH
    TherapyEvent.Location.FRONT_LEFT_LOWER_THIGH    -> TE.Location.FRONT_LEFT_LOWER_THIGH
    TherapyEvent.Location.SIDE_RIGHT_UPPER_THIGH    -> TE.Location.SIDE_RIGHT_UPPER_THIGH
    TherapyEvent.Location.SIDE_LEFT_UPPER_THIGH     -> TE.Location.SIDE_LEFT_UPPER_THIGH
    TherapyEvent.Location.SIDE_RIGHT_LOWER_THIGH    -> TE.Location.SIDE_RIGHT_LOWER_THIGH
    TherapyEvent.Location.SIDE_LEFT_LOWER_THIGH     -> TE.Location.SIDE_LEFT_LOWER_THIGH
    TherapyEvent.Location.NONE                      -> TE.Location.NONE
}

fun TE.Location.toDb(): TherapyEvent.Location = when (this) {
    TE.Location.FRONT_RIGHT_UPPER_CHEST   -> TherapyEvent.Location.FRONT_RIGHT_UPPER_CHEST
    TE.Location.FRONT_LEFT_UPPER_CHEST    -> TherapyEvent.Location.FRONT_LEFT_UPPER_CHEST
    TE.Location.SIDE_RIGHT_UPPER_ARM      -> TherapyEvent.Location.SIDE_RIGHT_UPPER_ARM
    TE.Location.SIDE_LEFT_UPPER_ARM       -> TherapyEvent.Location.SIDE_LEFT_UPPER_ARM
    TE.Location.BACK_RIGHT_UPPER_ARM      -> TherapyEvent.Location.BACK_RIGHT_UPPER_ARM
    TE.Location.BACK_LEFT_UPPER_ARM       -> TherapyEvent.Location.BACK_LEFT_UPPER_ARM
    TE.Location.SIDE_RIGHT_UPPER_ABDOMEN  -> TherapyEvent.Location.SIDE_RIGHT_UPPER_ABDOMEN
    TE.Location.SIDE_LEFT_UPPER_ABDOMEN   -> TherapyEvent.Location.SIDE_LEFT_UPPER_ABDOMEN
    TE.Location.SIDE_RIGHT_LOWER_ABDOMEN  -> TherapyEvent.Location.SIDE_RIGHT_LOWER_ABDOMEN
    TE.Location.SIDE_LEFT_LOWER_ABDOMEN   -> TherapyEvent.Location.SIDE_LEFT_LOWER_ABDOMEN
    TE.Location.FRONT_RIGHT_UPPER_ABDOMEN -> TherapyEvent.Location.FRONT_RIGHT_UPPER_ABDOMEN
    TE.Location.FRONT_LEFT_UPPER_ABDOMEN  -> TherapyEvent.Location.FRONT_LEFT_UPPER_ABDOMEN
    TE.Location.FRONT_RIGHT_LOWER_ABDOMEN -> TherapyEvent.Location.FRONT_RIGHT_LOWER_ABDOMEN
    TE.Location.FRONT_LEFT_LOWER_ABDOMEN  -> TherapyEvent.Location.FRONT_LEFT_LOWER_ABDOMEN
    TE.Location.BACK_RIGHT_BUTTOCK        -> TherapyEvent.Location.BACK_RIGHT_BUTTOCK
    TE.Location.BACK_LEFT_BUTTOCK         -> TherapyEvent.Location.BACK_LEFT_BUTTOCK
    TE.Location.FRONT_RIGHT_UPPER_THIGH   -> TherapyEvent.Location.FRONT_RIGHT_UPPER_THIGH
    TE.Location.FRONT_LEFT_UPPER_THIGH    -> TherapyEvent.Location.FRONT_LEFT_UPPER_THIGH
    TE.Location.FRONT_RIGHT_LOWER_THIGH   -> TherapyEvent.Location.FRONT_RIGHT_LOWER_THIGH
    TE.Location.FRONT_LEFT_LOWER_THIGH    -> TherapyEvent.Location.FRONT_LEFT_LOWER_THIGH
    TE.Location.SIDE_RIGHT_UPPER_THIGH    -> TherapyEvent.Location.SIDE_RIGHT_UPPER_THIGH
    TE.Location.SIDE_LEFT_UPPER_THIGH     -> TherapyEvent.Location.SIDE_LEFT_UPPER_THIGH
    TE.Location.SIDE_RIGHT_LOWER_THIGH    -> TherapyEvent.Location.SIDE_RIGHT_LOWER_THIGH
    TE.Location.SIDE_LEFT_LOWER_THIGH     -> TherapyEvent.Location.SIDE_LEFT_LOWER_THIGH
    TE.Location.NONE                      -> TherapyEvent.Location.NONE
}

fun TherapyEvent.Arrow.fromDb(): TE.Arrow = when (this) {
    TherapyEvent.Arrow.UP         -> TE.Arrow.UP
    TherapyEvent.Arrow.UP_RIGHT   -> TE.Arrow.UP_RIGHT
    TherapyEvent.Arrow.RIGHT      -> TE.Arrow.RIGHT
    TherapyEvent.Arrow.DOWN_RIGHT -> TE.Arrow.DOWN_RIGHT
    TherapyEvent.Arrow.DOWN       -> TE.Arrow.DOWN
    TherapyEvent.Arrow.DOWN_LEFT  -> TE.Arrow.DOWN_LEFT
    TherapyEvent.Arrow.LEFT       -> TE.Arrow.LEFT
    TherapyEvent.Arrow.UP_LEFT    -> TE.Arrow.UP_LEFT
    TherapyEvent.Arrow.CENTER     -> TE.Arrow.CENTER
    TherapyEvent.Arrow.NONE       -> TE.Arrow.NONE
}

fun TE.Arrow.toDb(): TherapyEvent.Arrow = when (this) {
    TE.Arrow.UP         -> TherapyEvent.Arrow.UP
    TE.Arrow.UP_RIGHT   -> TherapyEvent.Arrow.UP_RIGHT
    TE.Arrow.RIGHT      -> TherapyEvent.Arrow.RIGHT
    TE.Arrow.DOWN_RIGHT -> TherapyEvent.Arrow.DOWN_RIGHT
    TE.Arrow.DOWN       -> TherapyEvent.Arrow.DOWN
    TE.Arrow.DOWN_LEFT  -> TherapyEvent.Arrow.DOWN_LEFT
    TE.Arrow.LEFT       -> TherapyEvent.Arrow.LEFT
    TE.Arrow.UP_LEFT    -> TherapyEvent.Arrow.UP_LEFT
    TE.Arrow.CENTER     -> TherapyEvent.Arrow.CENTER
    TE.Arrow.NONE       -> TherapyEvent.Arrow.NONE
}

fun TherapyEvent.fromDb(): TE = TE(
    id = this.id,
    version = this.version,
    dateCreated = this.dateCreated,
    isValid = this.isValid,
    referenceId = this.referenceId,
    ids = this.interfaceIDs.fromDb(),
    timestamp = this.timestamp,
    utcOffset = this.utcOffset,
    duration = this.duration,
    type = this.type.fromDb(),
    note = this.note,
    enteredBy = this.enteredBy,
    glucose = this.glucose,
    glucoseType = this.glucoseType?.fromDb(),
    glucoseUnit = this.glucoseUnit.fromDb(),
    location = this.location?.fromDb(),
    arrow = this.arrow?.fromDb()
)

fun TE.toDb(): TherapyEvent = TherapyEvent(
    id = this.id,
    version = this.version,
    dateCreated = this.dateCreated,
    isValid = this.isValid,
    referenceId = this.referenceId,
    interfaceIDs_backing = this.ids.toDb(),
    timestamp = this.timestamp,
    utcOffset = this.utcOffset,
    duration = this.duration,
    type = this.type.toDb(),
    note = this.note,
    enteredBy = this.enteredBy,
    glucose = this.glucose,
    glucoseType = this.glucoseType?.toDb(),
    glucoseUnit = this.glucoseUnit.toDb(),
    location = this.location?.toDb(),
    arrow = this.arrow?.toDb()
)