package app.aaps.cgm.dexcomg7.alarm

import androidx.annotation.StringRes
import app.aaps.cgm.dexcomg7.R

/**
 * Alarms that stand in for one another. While one is active, the ones after it in the same group stay
 * quiet: an urgent low needs no low alert on top, and a failed sensor needs no signal loss alert.
 */
enum class G7AlarmGroup { LOW, HIGH, SENSOR }

/** How long a condition must last before the alarm goes off, when the user can choose it. */
data class G7AlarmDelay(
    @StringRes val title: Int,
    val defaultMinutes: Int,
    /** Choices in minutes. 0 means "right away". */
    val choices: List<Int>
)

/**
 * The alarms of the Dexcom G7 app, most important first. That order decides which sound plays when
 * two go off together, and which alarm quiets another in its [group].
 *
 * Defaults follow the Dexcom G7 app: urgent low (55 mg/dL) and sensor failed cannot be turned off,
 * low 70 mg/dL, high 250 mg/dL, urgent low soon on, rise and fall rate off, signal loss after 20 min.
 *
 * [id] is part of the setting keys, so it must not change once released.
 */
enum class G7AlarmType(
    val id: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val group: G7AlarmGroup,
    val family: G7SoundFamily,
    val canTurnOff: Boolean,
    val defaultEnabled: Boolean,
    /** Minutes after an acknowledge before the alarm may go off again while the condition lasts. 0 = never. */
    val defaultRepeatMinutes: Int,
    val defaultSound: G7AlarmSound,
    /** Stronger vibration. */
    val urgent: Boolean,
    val delay: G7AlarmDelay? = null
) {

    URGENT_LOW(
        id = "urgent_low",
        title = R.string.dexcom_g7_alarm_urgent_low,
        summary = R.string.dexcom_g7_alarm_urgent_low_summary,
        group = G7AlarmGroup.LOW,
        family = G7SoundFamily.URGENT_LOW,
        canTurnOff = false,
        defaultEnabled = true,
        defaultRepeatMinutes = 30,
        defaultSound = G7AlarmSound.URGENT_LOW_INTENSE,
        urgent = true
    ),
    SENSOR_FAILED(
        id = "sensor_failed",
        title = R.string.dexcom_g7_alarm_sensor_failed,
        summary = R.string.dexcom_g7_alarm_sensor_failed_summary,
        group = G7AlarmGroup.SENSOR,
        family = G7SoundFamily.TECHNICAL,
        canTurnOff = false,
        defaultEnabled = true,
        defaultRepeatMinutes = 0,
        defaultSound = G7AlarmSound.TECHNICAL_MEDIUM,
        urgent = true
    ),
    URGENT_LOW_SOON(
        id = "urgent_low_soon",
        title = R.string.dexcom_g7_alarm_urgent_low_soon,
        summary = R.string.dexcom_g7_alarm_urgent_low_soon_summary,
        group = G7AlarmGroup.LOW,
        family = G7SoundFamily.URGENT_LOW_SOON,
        canTurnOff = true,
        defaultEnabled = true,
        defaultRepeatMinutes = 30,
        defaultSound = G7AlarmSound.URGENT_LOW_SOON_MEDIUM,
        urgent = false
    ),
    LOW(
        id = "low",
        title = R.string.dexcom_g7_alarm_low,
        summary = R.string.dexcom_g7_alarm_low_summary,
        group = G7AlarmGroup.LOW,
        family = G7SoundFamily.LOW,
        canTurnOff = true,
        defaultEnabled = true,
        defaultRepeatMinutes = 15,
        defaultSound = G7AlarmSound.LOW_MEDIUM,
        urgent = false
    ),
    FALL_RATE(
        id = "fall_rate",
        title = R.string.dexcom_g7_alarm_fall_rate,
        summary = R.string.dexcom_g7_alarm_fall_rate_summary,
        group = G7AlarmGroup.LOW,
        family = G7SoundFamily.FALL_RATE,
        canTurnOff = true,
        defaultEnabled = false,
        defaultRepeatMinutes = 0,
        defaultSound = G7AlarmSound.FALL_RATE_MEDIUM,
        urgent = false
    ),
    HIGH(
        id = "high",
        title = R.string.dexcom_g7_alarm_high,
        summary = R.string.dexcom_g7_alarm_high_summary,
        group = G7AlarmGroup.HIGH,
        family = G7SoundFamily.HIGH,
        canTurnOff = true,
        defaultEnabled = true,
        defaultRepeatMinutes = 120,
        defaultSound = G7AlarmSound.HIGH_MEDIUM,
        urgent = false,
        delay = G7AlarmDelay(R.string.dexcom_g7_alarm_delay_first, 0, listOf(0, 15, 30, 45, 60, 90, 120, 180, 240))
    ),
    RISE_RATE(
        id = "rise_rate",
        title = R.string.dexcom_g7_alarm_rise_rate,
        summary = R.string.dexcom_g7_alarm_rise_rate_summary,
        group = G7AlarmGroup.HIGH,
        family = G7SoundFamily.RISE_RATE,
        canTurnOff = true,
        defaultEnabled = false,
        defaultRepeatMinutes = 0,
        defaultSound = G7AlarmSound.RISE_RATE_MEDIUM,
        urgent = false
    ),
    /**
     * Goes off at each [app.aaps.cgm.dexcomg7.protocol.G7SessionMilestone]: 24, 6 and 2 hours before the
     * session ends, when the grace period starts, 6 and 2 hours before it ends, and when readings stop.
     * Each moment is a new alert, so acknowledging one does not quiet the next. The id keeps its old
     * name so the stored settings stay.
     */
    SENSOR_END(
        id = "sensor_ended",
        title = R.string.dexcom_g7_alarm_sensor_end,
        summary = R.string.dexcom_g7_alarm_sensor_end_summary,
        group = G7AlarmGroup.SENSOR,
        family = G7SoundFamily.SYSTEM,
        canTurnOff = true,
        defaultEnabled = true,
        defaultRepeatMinutes = 0,
        defaultSound = G7AlarmSound.SYSTEM_MEDIUM,
        urgent = false
    ),
    SIGNAL_LOSS(
        id = "signal_loss",
        title = R.string.dexcom_g7_alarm_signal_loss,
        summary = R.string.dexcom_g7_alarm_signal_loss_summary,
        group = G7AlarmGroup.SENSOR,
        family = G7SoundFamily.SIGNAL_LOSS,
        canTurnOff = true,
        defaultEnabled = true,
        defaultRepeatMinutes = 30,
        defaultSound = G7AlarmSound.SIGNAL_LOSS_MEDIUM,
        urgent = false,
        delay = G7AlarmDelay(R.string.dexcom_g7_alarm_delay_for_more_than, 20, listOf(10, 15, 20, 30, 45, 60, 90, 120))
    ),
    SENSOR_ISSUE(
        id = "sensor_issue",
        title = R.string.dexcom_g7_alarm_sensor_issue,
        summary = R.string.dexcom_g7_alarm_sensor_issue_summary,
        group = G7AlarmGroup.SENSOR,
        family = G7SoundFamily.SENSOR_ISSUE,
        canTurnOff = true,
        defaultEnabled = true,
        defaultRepeatMinutes = 30,
        defaultSound = G7AlarmSound.SENSOR_ISSUE_MEDIUM,
        urgent = false,
        delay = G7AlarmDelay(R.string.dexcom_g7_alarm_delay_for_more_than, 20, listOf(0, 10, 15, 20, 30, 45, 60))
    );

    companion object {

        /** Fixed by Dexcom, not a setting. */
        const val URGENT_LOW_MGDL = 55.0

        /** Urgent low soon looks this far ahead. */
        const val URGENT_LOW_SOON_LOOKAHEAD_MINUTES = 20

        val REPEAT_CHOICES = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)

        /** The order of the alarms in the settings. Not the order of importance, which is the enum order. */
        val SETTINGS_ORDER = listOf(
            LOW, URGENT_LOW, URGENT_LOW_SOON, HIGH, RISE_RATE, FALL_RATE, SIGNAL_LOSS, SENSOR_ISSUE, SENSOR_FAILED, SENSOR_END
        )
    }
}
