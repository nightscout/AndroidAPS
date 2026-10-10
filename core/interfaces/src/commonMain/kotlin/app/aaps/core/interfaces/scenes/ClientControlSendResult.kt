package app.aaps.core.interfaces.scenes

/**
 * Outcome of a low-level client→master publish (`ClientControlPublisher.publish` / the senders /
 * the round-trip dispatcher). Three variants distinguish the cases the call site treats differently:
 *
 * - [Success]: the envelope reached NS with HTTP 2xx. Master will dispatch (or has already);
 *   no user action needed.
 * - [NotPaired]: this device has no master pairing. Recoverable by the user — they need to
 *   complete the pairing flow before remote control can work.
 * - [PublishFailed]: NS upload failed (no client, HTTP error, exception). Transient — retry
 *   when connectivity recovers. [reason] is for logs / dialog detail.
 *
 * Boolean was the original return type; collapsing the three cases into a single `false` hid
 * the not-paired case from the user, who would otherwise tap the action forever wondering
 * why nothing happened on the master.
 */
sealed class ClientControlSendResult {

    data object Success : ClientControlSendResult()

    data object NotPaired : ClientControlSendResult()

    data class PublishFailed(val reason: String? = null) : ClientControlSendResult()
}
