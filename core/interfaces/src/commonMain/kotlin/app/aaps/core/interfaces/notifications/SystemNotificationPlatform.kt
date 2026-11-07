package app.aaps.core.interfaces.notifications

/**
 * The part of notification handling that only the operating system can do.
 *
 * Everything about *which* notifications exist - posting, replacing, expiry, dismissal, and which
 * alarm currently owns the sound - is plain logic and lives in the shared notification manager.
 * What is left here is the platform half: putting a notification in the system tray, taking it away
 * again, and making noise.
 *
 * The two platforms differ more than they look:
 *
 * - Android builds a `Notification` on a channel, and drives a volume ramp itself through
 *   `AlarmSoundPlayer`, because a channel sound cannot ramp.
 * - iOS hands a `UNNotificationRequest` to the system, which owns presentation and sound.
 *
 * Neither of those belongs in shared code, and neither needs to know about the registry.
 */
interface SystemNotificationPlatform {

    /**
     * Show the system notification for [notification], replacing any earlier one with its
     * `instanceKey`.
     *
     * The whole record is passed rather than a chosen few fields, because what to show is a
     * platform decision and the shared registry should not decide it in advance. Android needs
     * `level` **and** `sound` **and** `actions` together: an urgent notification carrying a sound is
     * posted silently, because the ramping audio is driven by [setAudibleAlarm] instead, and the
     * plain visual notification is gated behind a user preference and only used when there are no
     * actions. A signature naming a subset of the fields could not express that, and an
     * implementation forced to guess would either double alert an alarm meant to be silent or
     * ignore the preference.
     *
     * [title] is resolved by the registry because it needs a `TextResolver`, which is not something
     * a platform implementation should have to carry.
     *
     * An implementation may legitimately decide to show nothing at all.
     */
    fun show(notification: AapsNotification, title: String)

    /** Take one notification out of the system tray. Does nothing when it is not there. */
    fun cancel(instanceKey: Int)

    /** Take every notification this app posted out of the system tray. */
    fun cancelAll()

    /**
     * Say which alarm owns the audio now, or pass null for silence.
     *
     * Called after every change to the registry, so the implementation must treat being called
     * again with the same key as "keep playing" rather than restarting the sound. That is what lets
     * a second urgent alarm arrive without interrupting the first, and what lets dismissing the
     * audible one promote the next instead of going quiet.
     */
    fun setAudibleAlarm(instanceKey: Int?, sound: AlarmSound?)

    /**
     * Register interest in dismissals the user made outside the app, by swiping the system
     * notification away. Called once during start up.
     */
    fun onDismissed(callback: (instanceKey: Int) -> Unit)
}
