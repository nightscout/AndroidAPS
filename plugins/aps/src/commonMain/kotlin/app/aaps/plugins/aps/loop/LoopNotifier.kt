package app.aaps.plugins.aps.loop

/**
 * The loop's own system notification: the open loop suggestion, which opens the app when tapped.
 *
 * Separate from [app.aaps.core.interfaces.notifications.NotificationManager] on purpose: it carries a tap action that
 * reaches back into the app, which that interface has no way to express. Carbs required used to be here too, with
 * three "ignore" buttons; it is an alert of the core now (`_docs/ALARMS.md`, step 6), and those buttons became the
 * silence buttons of every warning.
 *
 * Only the **decision** lives in `LoopPlugin`; everything about how the notification looks belongs to the platform.
 * The title and icon are resolved by the implementation, so the plugin passes nothing but the text it computed.
 *
 * **A silent implementation would be a safety problem here.** It tells the user open loop has a suggestion waiting
 * that it will not apply on its own. A target that cannot post it should say so rather than accept the call and do
 * nothing.
 */
interface LoopNotifier {

    /**
     * Open loop has a suggestion the user has to accept. Tapping it opens the app.
     *
     * @param localOnly keep it on the phone, because the watch is showing it too
     */
    fun openLoopSuggestion(text: String, localOnly: Boolean)

    /** Take the suggestion down. */
    fun dismiss()
}
