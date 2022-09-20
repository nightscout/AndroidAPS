package info.nightscout.sdk.mapper

import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.GlucoseType

@JvmSynthetic
internal fun EventType.toRemoteString(): String = when (this) {
    EventType.FINGER_STICK_BG_VALUE     -> "BG Check"
    EventType.SNACK_BOLUS               -> "Snack Bolus"
    EventType.MEAL_BOLUS                -> "Meal Bolus"
    EventType.CORRECTION_BOLUS          -> "Correction Bolus"
    EventType.CARBS_CORRECTION          -> "Carb Correction"
    EventType.COMBO_BOLUS               -> "Combo Bolus"
    EventType.ANNOUNCEMENT              -> "Announcement"
    EventType.NOTE                      -> "Note"
    EventType.QUESTION                  -> "Question"
    EventType.EXERCISE                  -> "Exercise"
    EventType.CANNULA_CHANGE            -> "Site Change"
    EventType.SENSOR_STARTED            -> "Sensor Start"
    EventType.SENSOR_CHANGE             -> "Sensor Change"
    EventType.PUMP_BATTERY_CHANGE       -> "Pump Battery Change"
    EventType.INSULIN_CHANGE            -> "Insulin Change"
    EventType.TEMPORARY_BASAL           -> "Temp Basal"
    EventType.PROFILE_SWITCH            -> "Profile Switch"
    EventType.DAD_ALERT                 -> "D.A.D. Alert"
    EventType.TEMPORARY_TARGET          -> "Temporary Target"
    EventType.APS_OFFLINE               -> "OpenAPS Offline"
    EventType.BOLUS_WIZARD              -> "Bolus Wizard"
    EventType.SENSOR_STOPPED            -> "Sensor Stop"
    EventType.NS_MBG                    -> "Mbg"
    EventType.TEMPORARY_TARGET_CANCEL   -> "Temporary Target Cancel"
    EventType.TEMPORARY_BASAL_START     -> "Temp Basal Start"
    EventType.TEMPORARY_BASAL_END       -> "Temp Basal End"
    EventType.NONE                      -> "<none>"
}

@JvmSynthetic
internal fun String?.toEventType(): EventType = when (this) {
    "BG Check"                  -> EventType.FINGER_STICK_BG_VALUE
    "Snack Bolus"               -> EventType.SNACK_BOLUS
    "Meal Bolus"                -> EventType.MEAL_BOLUS
    "Correction Bolus"          -> EventType.CORRECTION_BOLUS
    "Carb Correction"           -> EventType.CARBS_CORRECTION
    "Combo Bolus"               -> EventType.COMBO_BOLUS
    "Announcement"              -> EventType.ANNOUNCEMENT
    "Note"                      -> EventType.NOTE
    "Question"                  -> EventType.QUESTION
    "Exercise"                  -> EventType.EXERCISE
    "Site Change"               -> EventType.CANNULA_CHANGE
    "Sensor Start"              -> EventType.SENSOR_STARTED
    "Sensor Change"             -> EventType.SENSOR_CHANGE
    "Pump Battery Change"       -> EventType.PUMP_BATTERY_CHANGE
    "Insulin Change"            -> EventType.INSULIN_CHANGE
    "Temp Basal"                -> EventType.TEMPORARY_BASAL
    "Profile Switch"            -> EventType.PROFILE_SWITCH
    "D.A.D. Alert"              -> EventType.DAD_ALERT
    "Temporary Target"          -> EventType.TEMPORARY_TARGET
    "OpenAPS Offline"           -> EventType.APS_OFFLINE
    "Bolus Wizard"              -> EventType.BOLUS_WIZARD
    "Sensor Stop"               -> EventType.SENSOR_STOPPED
    "Mbg"                       -> EventType.NS_MBG
    "Temporary Target Cancel"   -> EventType.TEMPORARY_TARGET_CANCEL
    "Temp Basal Start"          -> EventType.TEMPORARY_BASAL_START
    "Temp Basal End"            -> EventType.TEMPORARY_BASAL_END
    else                        -> EventType.NONE
}

@JvmSynthetic
internal fun GlucoseType.toRemoteString(): String = when (this) {
    GlucoseType.Sensor  -> "Sensor"
    GlucoseType.Finger  -> "Finger"
    GlucoseType.Manual  -> "Manual"
}

@JvmSynthetic
internal fun String?.toGlucoseType(): GlucoseType? = when (this) {
    "Sensor"   -> GlucoseType.Sensor
    "Finger"   -> GlucoseType.Finger
    "Manual"   -> GlucoseType.Manual
    else       -> null
}

/*
@JvmSynthetic
internal fun RemoteEntry.toSgv(): Sgv? {

    this.sgv ?: return null
    if (this.type != "sgv") return null

    return Sgv(
        date = this.date,
        device = this.device,
        identifier = this.identifier,
        srvModified = this.srvModified,
        srvCreated = this.srvCreated,
        utcOffset = this.utcOffset ?: 0,
        subject = this.subject,
        direction = this.direction.toDirection(),
        sgv = this.sgv,
        isReadOnly = this.isReadOnly ?: false,
        isValid = this.isValid ?: true,
        noise = this.noise, // TODO: to Enum?
        filtered = this.filtered,
        unfiltered = this.unfiltered,
        units = this.units.toSvgUnits()
    )
}
*/