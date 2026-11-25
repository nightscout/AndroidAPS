package app.aaps.core.interfaces.notifications

import kotlin.time.Clock

data class AapsNotification(
    val id: NotificationId,
    val instanceKey: Int,
    val text: String,
    val level: NotificationLevel,
    val date: Long = Clock.System.now().toEpochMilliseconds(),
    val validTo: Long = 0L,
    val sound: AlarmSound? = null,
    val actions: List<NotificationAction> = emptyList(),
    val validityCheck: (() -> Boolean)? = null,
    /**
     * The title this alarm is announced with, on the system notification and on the watch.
     *
     * Null means "whatever the level is called", which is what every notification used to get: the watch was always
     * told "Urgent alarm" whatever had rung. `UiInteraction.runAlarm` has always carried a title from its caller and
     * sent it to the watch itself; now that its alarms are cards like any other, the card has to carry it or the
     * title is lost. Only the alarm path sets it.
     */
    val alarmTitle: String? = null,
    /**
     * Whether the watch may offer Mute for this alarm.
     *
     * The watch has Silence and Mute 5 min. Mute means "ring again in five minutes", which only makes sense for an
     * alarm something will ring again - the app's own alarms. A pump alert comes back by itself while its cause
     * lasts, so the watch is told it cannot be muted, which is what every card did before this field existed.
     */
    val canMute: Boolean = false,
    /**
     * Whether the watch is told this alarm is ringing.
     *
     * False only for an alarm the watch rings by itself - the "time to eat" timer. Everything else tells it, which
     * is what every card did before this field existed.
     */
    val tellWatch: Boolean = true,
    /** Its [sound] rings in Do Not Disturb and silent mode whatever the user's override says: urgent low only. */
    val breakThroughDnd: Boolean = false,
    /**
     * Android: a ringtone of the phone the user chose for this alarm (`_docs/ALARMS.md`, step 2e), played instead of
     * [sound]. [sound] stays the fallback: a ringtone that was deleted or cannot be read must not leave the alarm silent.
     */
    val ringtoneUri: String? = null,
    /**
     * The text of the system notification and the watch, when it is not [text]. An alert's card starts its text with
     * the alert's title, because the card in the app has no title line; the system notification and the watch show
     * that title above the text already ([alarmTitle]), so they get the alert's text alone. Only the alarm path sets it.
     */
    val systemText: String? = null
) {

    /** What the system notification and the watch show under the title. */
    val systemBody: String get() = systemText ?: text
}
