package app.aaps.core.interfaces.alerts

import app.aaps.core.interfaces.notifications.NotificationId

/**
 * Alarms that stand in for one another. While one is going off, the ones after it in the same
 * group stay quiet: an urgent low needs no low alarm on top, and an ended sensor needs no "ending
 * soon" on top. [INFO] alarms never quiet each other.
 */
enum class CgmAlarmGroup { LOW, HIGH, SENSOR, INFO }

/**
 * The CGM alarms, most important first. That order decides which alarm of a [group] goes off when
 * several conditions hold at the same time.
 *
 * One alarm layer for every BG source: the glucose alarms read the stored glucose, the sensor
 * alarms read the sensor state of a source that reports it
 * ([app.aaps.core.interfaces.source.CgmSensorAlertProvider]). The alarms only notify. They never
 * change the glucose values the loop receives, and never change dosing.
 *
 * @param id stable name, used in the stored alarm state and in the messages to the watch. It must
 *   not change once released.
 * @param notificationId the notification this alarm posts.
 * @param reminds true for an alarm that goes off again every few minutes until it is acknowledged.
 *   False for an information that is shown once per episode.
 */
enum class CgmAlarmType(
    val id: String,
    val group: CgmAlarmGroup,
    val notificationId: NotificationId,
    val reminds: Boolean = true,
) {

    URGENT_LOW("urgent_low", CgmAlarmGroup.LOW, NotificationId.CGM_URGENT_LOW),
    SENSOR_FAILED("sensor_failed", CgmAlarmGroup.SENSOR, NotificationId.CGM_SENSOR_FAILED),
    URGENT_LOW_SOON("urgent_low_soon", CgmAlarmGroup.LOW, NotificationId.CGM_URGENT_LOW_SOON),
    LOW("low", CgmAlarmGroup.LOW, NotificationId.BG_HYPO),
    FALL_RATE("fall_rate", CgmAlarmGroup.LOW, NotificationId.BG_RAPID_FALL),
    SENSOR_ENDED("sensor_ended", CgmAlarmGroup.SENSOR, NotificationId.CGM_SENSOR_EXPIRED),
    HIGH("high", CgmAlarmGroup.HIGH, NotificationId.BG_HYPER),
    RISE_RATE("rise_rate", CgmAlarmGroup.HIGH, NotificationId.CGM_RAPID_RISE),
    SIGNAL_LOSS("signal_loss", CgmAlarmGroup.SENSOR, NotificationId.CGM_SIGNAL_LOST),
    SENSOR_ISSUE("sensor_issue", CgmAlarmGroup.SENSOR, NotificationId.CGM_SENSOR_ISSUE),
    SENSOR_ENDING_SOON("sensor_ending_soon", CgmAlarmGroup.SENSOR, NotificationId.CGM_SENSOR_EXPIRING, reminds = false),
    SECOND_SENSOR_READY("second_sensor_ready", CgmAlarmGroup.INFO, NotificationId.CGM_SECOND_SENSOR_READY, reminds = false),
    WARMUP_DONE("warmup_done", CgmAlarmGroup.INFO, NotificationId.CGM_WARMUP_DONE, reminds = false);

    /** True for the alarms that read glucose. The others read the sensor state. */
    val isGlucose: Boolean get() = group == CgmAlarmGroup.LOW || group == CgmAlarmGroup.HIGH

    /**
     * The mute timer can never silence this alarm. Only urgent low: a person who muted the alarms for
     * a meeting must still be woken by a glucose of 55 mg/dL or less.
     */
    val neverMuted: Boolean get() = this == URGENT_LOW

    companion object {

        fun byId(id: String): CgmAlarmType? = entries.firstOrNull { it.id == id }

        fun byNotificationId(id: NotificationId): CgmAlarmType? = entries.firstOrNull { it.notificationId == id }
    }
}
