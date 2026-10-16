package app.aaps.plugins.aps.loop

/**
 * The loop's own system notifications - the ones with action buttons, not AAPS in-app notifications.
 *
 * Separate from [app.aaps.core.interfaces.notifications.NotificationManager] on purpose: these carry
 * tap actions that reach back into the app ("ignore for 15 minutes", "open the app here"), which that
 * interface has no way to express.
 *
 * Only the **decision** lives in `LoopPlugin`; everything about how a notification looks and what its
 * buttons do belongs to the platform. Titles, icons and the ignore-button labels are resolved by the
 * implementation, so the plugin passes nothing but the text it computed.
 *
 * **A silent implementation would be a safety problem here.** These tell the user the loop wants
 * carbs, or has a suggestion waiting that open loop will not apply on its own. A target that cannot
 * post them should say so rather than accept the call and do nothing.
 */
interface LoopNotifier {

    /**
     * The loop needs carbs. Carries three "ignore for N minutes" actions.
     *
     * @param text what to show - already formatted, including the amount and the window
     */
    fun carbsRequired(text: String)

    /**
     * Open loop has a suggestion the user has to accept. Tapping it opens the app.
     *
     * @param localOnly keep it on the phone, because the watch is showing it too
     */
    fun openLoopSuggestion(text: String, localOnly: Boolean)

    /** Take down whichever of the two is showing. */
    fun dismiss()
}
