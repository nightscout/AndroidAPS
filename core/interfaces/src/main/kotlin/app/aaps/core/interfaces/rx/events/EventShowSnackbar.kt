package app.aaps.core.interfaces.rx.events

/**
 * Request a snackbar to be displayed on the currently-visible screen.
 *
 * Consumed by the root snackbar host in [ComposeMainActivity] (and sibling
 * activities via their own [GlobalSnackbarHost]). If no activity is visible
 * when the event fires, an application-scoped collector falls back to a
 * system Notification so the message is not silently lost.
 *
 * @param message User-facing message text (already localized).
 * @param type    Styling bucket (error/warning/info/success).
 * @param key     Optional dedup key. If set, rapid duplicates with the same
 *                key collapse so retry loops don't flood the host.
 */
class EventShowSnackbar(
    val message: String,
    val type: Type = Type.Info,
    val key: String? = null
) : Event() {

    enum class Type { Error, Warning, Info, Success }
}
