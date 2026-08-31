package app.aaps.implementation.notifications

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.IosNotificationDelegate
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionCustomDismissAction
import platform.UserNotifications.UNNotificationDismissActionIdentifier
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelActive
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The system tray half of notifications on iOS.
 *
 * iOS keeps far more of this than Android does. There is no channel to create, no `PendingIntent`
 * to build, and no volume ramp to drive: a delivered notification carries its own sound, and the
 * system decides how to present it. So the interesting part here is what is *absent* compared with
 * the Android side.
 *
 * Permission is requested once, lazily, on the first notification. Asking in the constructor would
 * put the system prompt in front of the user during start up, before anything has explained why the
 * app wants it.
 */
class IosSystemNotificationPlatform(
    private val aapsLogger: AAPSLogger,
    private val alarmSoundPlayer: AlarmSoundPlayer
) : SystemNotificationPlatform {

    /** The alarm currently owning the audio, so an unchanged owner does not restart the sound. */
    private var soundingKey: Int? = null

    /**
     * Resolved on first use, not in the constructor.
     *
     * `currentNotificationCenter()` needs an app bundle and throws
     * `bundleProxyForCurrentProcess is nil` without one, so constructing this class eagerly made it
     * impossible to build outside a running app - including in a test. Nothing here needs the centre
     * until something is actually posted.
     */
    private val center by lazy { UNUserNotificationCenter.currentNotificationCenter() }
    private var authorizationAsked = false

    /**
     * iOS shows every notification, unlike Android.
     *
     * The two fields Android needs to decide with - `sound` and `actions` - are ignored here on
     * purpose. There is no "post the alarm silently" split because there is no separate ramping
     * player to hand the audio to (see [setAudibleAlarm]), and no preference gating whether a system
     * notification appears, because on iOS that choice belongs to the user in Settings.
     */
    override fun show(notification: AapsNotification, title: String) {
        ensureAuthorization()
        val urgent = notification.level == NotificationLevel.URGENT
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(notification.text)
            // Time sensitive breaks through Focus modes, which is what an urgent AAPS alarm is for.
            // The sound stays off here on purpose: audible alarms are driven by setAudibleAlarm, the
            // same split the Android side uses so a replaced alarm does not restart the sound.
            // Without this the dismiss callback never fires - see onDismissed.
            setCategoryIdentifier(CATEGORY)
            setInterruptionLevel(
                if (urgent) UNNotificationInterruptionLevelTimeSensitive
                else UNNotificationInterruptionLevelActive
            )
        }
        // A repeated identifier replaces the delivered notification rather than adding another,
        // which is the behaviour the registry expects when it reposts the same id.
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier(notification.instanceKey),
            content = content,
            trigger = null
        )
        center.addNotificationRequest(request) { error ->
            if (error != null) aapsLogger.error(LTag.NOTIFICATION, "Cannot post notification: $error")
        }
    }

    override fun cancel(instanceKey: Int) {
        val ids = listOf(identifier(instanceKey))
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    override fun cancelAll() {
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }

    /**
     * Hands the ramping alarm to [AlarmSoundPlayer], or silences it.
     *
     * The registry calls this after every change with whichever alarm owns the sound, so calling it
     * again with the same key must not restart the audio - that is what stops a second alarm
     * cutting the first one off, and why the owner key is compared here rather than in the player.
     */
    override fun setAudibleAlarm(instanceKey: Int?, sound: AlarmSound?) {
        if (instanceKey == soundingKey) return
        soundingKey = instanceKey
        if (instanceKey == null || sound == null) {
            alarmSoundPlayer.stop(AlarmSoundPlayer.OWNER_INTERNAL)
            aapsLogger.debug(LTag.NOTIFICATION, "Alarm audio stopped")
        } else {
            alarmSoundPlayer.play(sound, AlarmSoundPlayer.OWNER_INTERNAL)
            aapsLogger.debug(LTag.NOTIFICATION, "Alarm audio owner is now $instanceKey ($sound)")
        }
    }

    /**
     * Learn about notifications the user swiped away outside the app.
     *
     * Two things are needed, and missing either one makes this silently never fire: a delegate on
     * the shared centre, and a category carrying `customDismissAction` - without that iOS reports
     * taps but not dismissals, which is the trap, because the code looks right and nothing arrives.
     *
     * The delegate is not set here. There is one slot for the whole app and `setDelegate` replaces
     * whatever was in it, so [IosNotificationDelegate] owns it and routes; a second owner would
     * silently stop the first one's callbacks.
     */
    override fun onDismissed(callback: (instanceKey: Int) -> Unit) {
        IosNotificationDelegate.register(setOf(dismissibleCategory())) { actionId, notificationId ->
            if (actionId != UNNotificationDismissActionIdentifier) return@register false
            // A tap is deliberately not a dismissal: the notification goes away, but the user asked
            // to *see* the thing, so it stays in the in-app list.
            instanceKeyOf(notificationId)?.let(callback)
            true
        }
    }

    private fun dismissibleCategory(): UNNotificationCategory =
        UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY,
            actions = emptyList<Any>(),
            intentIdentifiers = emptyList<Any>(),
            options = UNNotificationCategoryOptionCustomDismissAction
        )

    internal fun identifier(instanceKey: Int) = "$IDENTIFIER_PREFIX$instanceKey"

    /** The reverse of [identifier]. Null for anything this class did not post. */
    internal fun instanceKeyOf(identifier: String): Int? =
        if (identifier.startsWith(IDENTIFIER_PREFIX)) identifier.removePrefix(IDENTIFIER_PREFIX).toIntOrNull()
        else null

    private fun ensureAuthorization() {
        if (authorizationAsked) return
        authorizationAsked = true
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, error ->
            if (error != null) aapsLogger.error(LTag.NOTIFICATION, "Notification permission failed: $error")
            else aapsLogger.debug(LTag.NOTIFICATION, "Notification permission granted=$granted")
        }
    }

    companion object {

        private const val IDENTIFIER_PREFIX = "aaps-"

        /** Named once: it has to match between the posted content and the registered category. */
        private const val CATEGORY = "aaps-notification"
    }
}
