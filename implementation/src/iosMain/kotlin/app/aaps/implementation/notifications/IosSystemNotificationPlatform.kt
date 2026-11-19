package app.aaps.implementation.notifications

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.IosNotificationDelegate
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.implementation.lifecycle.observeAppForeground
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionCustomDismissAction
import platform.UserNotifications.UNNotificationDismissActionIdentifier
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
    private val alarmSoundPlayer: AlarmSoundPlayer,
    /**
     * `AlertOverrideDoNotDisturb`, read fresh each time so a change takes effect without a restart.
     *
     * A supplier rather than `Preferences` itself: this needs one boolean, while `Preferences` has 76
     * members and no test binary here can fake it - `iosTest` has no Mockito. Narrowing it is what
     * lets [breaksThroughFocus] be checked at all.
     */
    private val overrideDoNotDisturb: () -> Boolean
) : SystemNotificationPlatform {

    /** The alarm currently owning the audio, so an unchanged owner does not restart the sound. */
    private var soundingKey: Int? = null

    /** The sound [soundingKey] should be playing, kept so [onAppForeground] can start it after a failed background start. */
    private var audibleSound: AlarmSound? = null

    /** Whether an AAPS screen is in front. See [onAppForeground] for the two things that depend on it. */
    private var inForeground = false

    init {
        observeAppForeground { onAppForeground(it) }
    }

    /**
     * Two things change with the app leaving or entering the screen.
     *
     * **A notification posted in the background carries a sound** ([show]), because [AlarmSoundPlayer] cannot start
     * there. `AVAudioSession` needs the process to be running and the app has no `audio` background mode, so an alarm
     * that arrives while the app is suspended - which is most of them, on a phone in a pocket - plays nothing at all.
     * A notification sound is one shot rather than a ramping loop, but it is heard, and that is the whole point of an
     * alarm.
     *
     * **The ramping alarm is started again on the way in.** The registry calls [setAudibleAlarm] once, when the top
     * alarm changes, and never again for the same alarm (`CommonNotificationManager.refreshAlarmSound`). So a CRITICAL
     * alarm whose audio was refused in the background stayed silent after the user opened the app, with the alarm
     * still on screen. Starting it here is what closes that gap. `play` stops any previous playback first, so an
     * alarm that *is* already sounding is not cut in two.
     *
     * `internal` so a test can drive it: the UIKit read behind [observeAppForeground] does not run in a test binary.
     */
    internal fun onAppForeground(inForeground: Boolean) {
        val came = inForeground && !this.inForeground
        this.inForeground = inForeground
        if (!came) return
        val sound = audibleSound ?: return
        aapsLogger.debug(LTag.NOTIFICATION, "App in front with alarm $soundingKey still ringing, starting its audio")
        alarmSoundPlayer.play(sound, AlarmSoundPlayer.OWNER_INTERNAL)
    }

    /**
     * Instance keys whose notification carries actions and has not been answered yet.
     *
     * These are the ones a swipe must not clear. See [onDismissed] for what went wrong without it.
     * Entries leave only through [cancel] or [cancelAll] - that is, when the notification is
     * genuinely finished with, rather than when the user brushed it off the screen.
     */
    private val unanswered = mutableSetOf<Int>()

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
     * `AlertUrgentAsAndroidNotification` is not consulted, and that is deliberate: it decides whether
     * AAPS raises an OS notification at all, and on iOS that choice belongs to the user in Settings.
     * The key says so itself now - it is marked Android only.
     *
     * `sound` **is** read, but only to decide whether this notification has to be audible by itself. While an AAPS
     * screen is in front the notification stays silent and the audio is handed to [AlarmSoundPlayer] through
     * [setAudibleAlarm], which is the same split Android uses, so a replaced alarm does not restart the ramp. In the
     * background the player cannot start at all, so the notification carries the system sound instead - see
     * [onAppForeground]. (An earlier version of this comment said `sound` was not read and that every notification
     * here was silent. That was true, and it meant a CRITICAL pump alert on a phone in a pocket made no noise.)
     *
     * `actions` **is** still ignored, and that one is a real gap rather than a decision - a
     * notification carrying actions reaches the tray here with none of them attached, where Android
     * suppresses it so it can be answered in the app.
     */
    override fun show(notification: AapsNotification, title: String) {
        ensureAuthorization()
        rememberIfUnanswered(notification)
        val breakThroughFocus = breaksThroughFocus(notification.level)
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(notification.text)
            // Silent while the app is in front, where setAudibleAlarm drives the ramping player instead; audible when
            // it is not, where that player cannot start. See onAppForeground.
            if (audibleInBackground(notification)) setSound(UNNotificationSound.defaultSound())
            // Without the category the dismiss callback never fires - see onDismissed.
            setCategoryIdentifier(CATEGORY)
            setInterruptionLevel(
                if (breakThroughFocus) UNNotificationInterruptionLevelTimeSensitive
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

    /**
     * Whether this notification is allowed past a Focus mode.
     *
     * The Focus half of `AlertOverrideDoNotDisturb`. On iOS that one setting splits across two
     * mechanisms, because Focus suppresses **notifications** while the Ring/Silent switch silences
     * **audio**, and neither lever reaches the other:
     *
     * - the silent switch is answered by the audio session category, in `IosAlarmSoundPlayer`;
     * - Focus is answered here, and only `timeSensitive` gets through it.
     *
     * This used to read the urgency alone, so half of what the switch promised - "when disabled,
     * alarms respect silent/DND" - was not true whatever the user set.
     *
     * Non-urgent notifications never break through, exactly as before: the override widens what an
     * alarm may do, it does not promote ordinary notifications into alarms.
     */
    internal fun breaksThroughFocus(level: NotificationLevel): Boolean =
        level == NotificationLevel.URGENT && overrideDoNotDisturb()

    /**
     * Whether this notification has to make the noise itself.
     *
     * Only an alarm does - one the registry gave a [AlarmSound] - and only while no AAPS screen is in front, because
     * that is the case [AlarmSoundPlayer] cannot cover. The Focus decision is left to [breaksThroughFocus] above: the
     * sound is what the user hears, the interruption level is whether they are allowed to hear it, and the setting
     * that governs the second one must not be quietly widened by the first.
     *
     * `internal` so it can be checked without a notification centre, which a test binary has no bundle for.
     */
    internal fun audibleInBackground(notification: AapsNotification): Boolean =
        notification.sound != null && !inForeground

    /**
     * Notes, before posting, that this notification must survive a swipe.
     *
     * Split from [show] only so it can be exercised: [show] reaches `UNUserNotificationCenter`,
     * which needs an app bundle a test binary does not have.
     */
    internal fun rememberIfUnanswered(notification: AapsNotification) {
        if (notification.actions.isNotEmpty()) unanswered += notification.instanceKey
    }

    /** The counterpart: this notification is finished with, however that came about. */
    internal fun forget(instanceKey: Int) {
        unanswered -= instanceKey
    }

    /** Same, for the "mute all alarms" path. */
    internal fun forgetAll() {
        unanswered.clear()
    }

    override fun cancel(instanceKey: Int) {
        forget(instanceKey)
        val ids = listOf(identifier(instanceKey))
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    /**
     * Only the alarms this class posted, and only the delivered ones.
     *
     * This was `removeAllPendingNotificationRequests()` plus `removeAllDeliveredNotifications()`,
     * and both halves were wrong. This class never creates a pending request at all - every [show]
     * posts with `trigger = null`, so it is delivered straight away - but
     * `IosReminderScheduler` does, and its scheduled reminders are named `aaps-reminder-N`, which
     * sits inside this class's own `aaps-` prefix. Muting alarms therefore deleted every automation
     * reminder the user was still waiting on, and none of them ever rang. The delivered half swept
     * up the loop's `aaps-loop` notification for the same reason.
     *
     * Android draws exactly this line and says why in `AndroidSystemNotificationPlatform.cancelAll`:
     * "mute all alarms" means take *my* alarms out of the tray, not empty the tray. [instanceKeyOf]
     * is what decides ownership here, so a `aaps-reminder-3` or an `aaps-loop` is left alone because
     * neither tail parses to a key.
     */
    override fun cancelAll() {
        // "Mute all alarms" is an answer, given deliberately, so these are finished with.
        forgetAll()
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val posted = delivered.orEmpty().mapNotNull { (it as? UNNotification)?.request?.identifier }
            val ids = ownIdentifiers(posted)
            if (ids.isNotEmpty()) center.removeDeliveredNotificationsWithIdentifiers(ids)
        }
    }

    /**
     * The identifiers [cancelAll] is allowed to remove.
     *
     * Split out so it can be checked without a notification centre, which a test binary has no
     * bundle for. The whole safety of [cancelAll] is in this one filter.
     */
    internal fun ownIdentifiers(identifiers: List<String>): List<String> =
        identifiers.filter { instanceKeyOf(it) != null }

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
            audibleSound = null
            alarmSoundPlayer.stop(AlarmSoundPlayer.OWNER_INTERNAL)
            aapsLogger.debug(LTag.NOTIFICATION, "Alarm audio stopped")
        } else {
            // Remembered, not only played: in the background the start below does nothing, and onAppForeground is
            // then the only thing that gets the ramp going when the user opens the app.
            audibleSound = sound
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
            val instanceKey = instanceKeyOf(notificationId) ?: return@register true
            if (clearedByDismissal(instanceKey)) callback(instanceKey)
            else aapsLogger.debug(LTag.NOTIFICATION, "Swipe ignored for $instanceKey: it still carries an unanswered action")
            true
        }
    }

    /**
     * Whether swiping this notification away is allowed to count as answering it.
     *
     * No, when it carries actions nobody has used yet - and that case is why this exists.
     *
     * Every notification posted here is swipeable, because the category has to carry
     * `customDismissAction` for dismissals to be reported at all. The registry turns a reported
     * dismissal into `dismiss(handle)`, which drops the notification, and `refreshAlarmSound` then
     * finds no audible alarm and **stops the sound**. So on an urgent Nightscout alarm the swipe -
     * the first gesture anyone reaches for - silenced it, threw away the card holding the three
     * snooze buttons, never acknowledged Nightscout and never recorded a snooze, so the same alarm
     * returned on the next push.
     *
     * Android forbids exactly this rather than handling it: `AlarmNotificationManager` posts with
     * `setOngoing(true)`, and says why - "so the user can't swipe to dismiss". iOS has no ongoing
     * flag, so the guard has to be here instead. The banner does go from the tray, which iOS will
     * not undo; what does not happen is the alarm being treated as answered.
     */
    internal fun clearedByDismissal(instanceKey: Int): Boolean = instanceKey !in unanswered

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
