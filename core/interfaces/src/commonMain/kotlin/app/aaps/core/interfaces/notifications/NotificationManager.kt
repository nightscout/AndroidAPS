package app.aaps.core.interfaces.notifications

import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock

interface NotificationManager {

    val notifications: StateFlow<List<AapsNotification>>

    /** Remove expired and validity-failed notifications on demand. */
    fun cleanUp()

    fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel = id.defaultLevel,
        validMinutes: Int = 0,
        sound: AlarmSound? = null,
        actions: List<NotificationAction> = emptyList(),
        validityCheck: (() -> Boolean)? = null
    ): NotificationHandle

    /**
     * The overload the alarm path uses. [alarmTitle], [canMute], [systemText] and [vibrateOnly] are what an alarm tells
     * the system notification and the watch beyond its text; see [AapsNotification]. Nothing but an alarm needs to
     * pass them.
     */
    fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel = id.defaultLevel,
        date: Long = Clock.System.now().toEpochMilliseconds(),
        validTo: Long = 0L,
        sound: AlarmSound? = null,
        actions: List<NotificationAction> = emptyList(),
        validityCheck: (() -> Boolean)? = null,
        alarmTitle: String? = null,
        canMute: Boolean = false,
        tellWatch: Boolean = true,
        breakThroughDnd: Boolean = false,
        ringtoneUri: String? = null,
        systemText: String? = null,
        vibrateOnly: Boolean = false
    ): NotificationHandle

    /**
     * Posts a notification whose text is resolved when it is shown.
     *
     * Android callers keep writing `post(id, R.string.x, arg)`: that goes through the extension in
     * `NotificationManagerAndroid`, which wraps the id in a [TextRef.AndroidRes]. Keeping the id out of
     * this declaration is what lets the interface be platform neutral.
     */
    fun post(
        id: NotificationId,
        textRef: TextRef,
        level: NotificationLevel = id.defaultLevel,
        validMinutes: Int = 0,
        date: Long = Clock.System.now().toEpochMilliseconds(),
        validTo: Long = 0L,
        sound: AlarmSound? = null,
        actions: List<NotificationAction> = emptyList(),
        validityCheck: (() -> Boolean)? = null
    ): NotificationHandle

    /** Dismiss all instances of this notification type. */
    fun dismiss(id: NotificationId)

    /** Dismiss a specific instance by handle. */
    fun dismiss(handle: NotificationHandle)

    /**
     * Silence and dismiss every currently audible alarm. Used by the global "mute all" entry
     * points (Wear snooze/mute gesture, full-screen acknowledge, app onTerminate). Stops both the
     * internal-notification (`AlarmSoundPlayer.OWNER_INTERNAL`) and full-screen
     * audio, cancels their system notifications, and removes
     * the audible alarms from the registry so the in-app cards clear. Non-audible notifications are
     * left untouched.
     */
    fun muteAllAlarms()

    /**
     * Takes out of the system tray the cards a previous run of the app left there. Called once, at the start of the
     * app's start. The cards live in memory, so after a restart (a crash, an update, iOS ending the app) nothing
     * removes the old ones, and an alert raised again puts a second card next to its old one. What is still active
     * is raised again and gets a new card.
     */
    fun clearLeftovers()

    companion object {

        /**
         * Do not change: Android keeps the user's settings of a channel (sound, importance, Do Not Disturb)
         * by its ID. A new ID is a new channel with default settings, and the old one stays in the system.
         */
        const val CHANNEL_ID = "AndroidAPS-Overview"
        const val DISMISS_ACTION = "app.aaps.plugins.main.general.overview.notifications.receivers.DismissNotificationReceiver"
    }
}
