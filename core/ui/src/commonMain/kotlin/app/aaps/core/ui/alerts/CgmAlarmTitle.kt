package app.aaps.core.ui.alerts

import app.aaps.core.interfaces.alerts.CgmAlarmType
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

/** The short name of a CGM alarm, for its notification, the watch and the settings. */
val CgmAlarmType.title: TextRef
    get() = when (this) {
        CgmAlarmType.URGENT_LOW          -> CoreUiStrings.cgm_alarm_urgent_low
        CgmAlarmType.SENSOR_FAILED       -> CoreUiStrings.cgm_alarm_sensor_failed
        CgmAlarmType.URGENT_LOW_SOON     -> CoreUiStrings.cgm_alarm_urgent_low_soon
        CgmAlarmType.LOW                 -> CoreUiStrings.cgm_alarm_low
        CgmAlarmType.FALL_RATE           -> CoreUiStrings.cgm_alarm_fall_rate
        CgmAlarmType.SENSOR_ENDED        -> CoreUiStrings.cgm_alarm_sensor_ended
        CgmAlarmType.HIGH                -> CoreUiStrings.cgm_alarm_high
        CgmAlarmType.RISE_RATE           -> CoreUiStrings.cgm_alarm_rise_rate
        CgmAlarmType.SIGNAL_LOSS         -> CoreUiStrings.cgm_alarm_signal_loss
        CgmAlarmType.SENSOR_ISSUE        -> CoreUiStrings.cgm_alarm_sensor_issue
        CgmAlarmType.SENSOR_ENDING_SOON  -> CoreUiStrings.cgm_alarm_sensor_ending_soon
        CgmAlarmType.SECOND_SENSOR_READY -> CoreUiStrings.cgm_alarm_second_sensor_ready
        CgmAlarmType.WARMUP_DONE         -> CoreUiStrings.cgm_alarm_warmup_done
    }
