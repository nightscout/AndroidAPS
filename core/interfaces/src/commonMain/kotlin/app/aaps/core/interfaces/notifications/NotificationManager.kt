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

    fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel = id.defaultLevel,
        date: Long = Clock.System.now().toEpochMilliseconds(),
        validTo: Long = 0L,
        sound: AlarmSound? = null,
        actions: List<NotificationAction> = emptyList(),
        validityCheck: (() -> Boolean)? = null
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
     * (`AlarmSoundPlayer.OWNER_FULLSCREEN`) audio, cancels their system notifications, and removes
     * the audible alarms from the registry so the in-app cards clear. Non-audible notifications are
     * left untouched.
     */
    fun muteAllAlarms()

    /**
     * Runs button [index] of the notification [instanceKey] and then dismisses it, as a tap on that button in the
     * notification list does. Used by the watch, which shows the buttons of the alarm that rings. Returns false and
     * does nothing when that notification or button is gone, so a late tap on the watch runs nothing.
     *
     * The button runs outside any lock of the manager: a button may post or dismiss notifications itself. It runs on
     * the caller's thread, from the watch that is the message collector and not the main thread: a button that must
     * touch the UI has to switch to it by itself. A notification posted later under the same id, as the next
     * Nightscout alarm, has the same [instanceKey]: a late press then runs the button of that newer one.
     */
    fun runAction(instanceKey: Int, index: Int): Boolean {
        val notification = notifications.value.firstOrNull { it.instanceKey == instanceKey } ?: return false
        val action = notification.actions.getOrNull(index) ?: return false
        action.action()
        dismiss(NotificationHandle(instanceKey))
        return true
    }

    companion object {

        /**
         * Do not change: Android keeps the user's settings of a channel (sound, importance, Do Not Disturb)
         * by its ID. A new ID is a new channel with default settings, and the old one stays in the system.
         */
        const val CHANNEL_ID = "AndroidAPS-Overview"
        const val DISMISS_ACTION = "app.aaps.plugins.main.general.overview.notifications.receivers.DismissNotificationReceiver"
    }
}
