package app.aaps.core.interfaces.notifications

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.StateFlow

interface NotificationManager {

    val notifications: StateFlow<List<AapsNotification>>

    /** Remove expired and validity-failed notifications on demand. */
    fun cleanUp()

    fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel = id.defaultLevel,
        validMinutes: Int = 0,
        @RawRes soundRes: Int? = null,
        actions: List<NotificationAction> = emptyList(),
        validityCheck: (() -> Boolean)? = null
    ): NotificationHandle

    fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel = id.defaultLevel,
        date: Long = System.currentTimeMillis(),
        validTo: Long = 0L,
        @RawRes soundRes: Int? = null,
        actions: List<NotificationAction> = emptyList(),
        validityCheck: (() -> Boolean)? = null
    ): NotificationHandle

    fun post(
        id: NotificationId,
        @StringRes textRes: Int,
        vararg formatArgs: Any?,
        level: NotificationLevel = id.defaultLevel,
        validMinutes: Int = 0,
        date: Long = System.currentTimeMillis(),
        validTo: Long = 0L,
        @RawRes soundRes: Int? = null,
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

    companion object {

        const val CHANNEL_ID = "AndroidAPS-Overview"
        const val DISMISS_ACTION = "app.aaps.plugins.main.general.overview.notifications.receivers.DismissNotificationReceiver"
    }
}
