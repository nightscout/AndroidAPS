package app.aaps.core.interfaces.rx.events

/**
 * Request a snackbar to be displayed on the currently-visible screen.
 *
 * Consumed by `GlobalSnackbarHost`, which every shell places at the root of
 * its UI, and which a few standalone activities host themselves. A host
 * collects only while it is on screen. When none is,
 * `SnackbarNotificationFallback` turns the message into a system notification
 * instead, so it is not silently lost - on Android, iOS and desktop alike.
 *
 * @param message User-facing message text (already localized).
 * @param type    Styling bucket (error/warning/info/success).
 * @param key     Optional dedup key. If set, rapid duplicates with the same
 *                key collapse so retry loops don't flood the host.
 */
data class EventShowSnackbar(
    val message: String,
    val type: Type = Type.Info,
    val key: String? = null
) : Event() {

    enum class Type { Error, Warning, Info, Success }
}
