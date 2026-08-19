package app.aaps.core.interfaces.protection

import app.aaps.core.keys.interfaces.TextRef

/**
 * A password prompt waiting to be shown.
 *
 * [PasswordCheck] does not present anything itself. It publishes one of these and the UI - which
 * hosts `PasswordCheckHost` once, near its root - renders the matching dialog. That indirection is
 * what removes the Android `Context` from the contract: the old implementation built an
 * `android.app.Dialog` around a `ComposeView`, which needed an Activity to hang a window on and a
 * hand written lifecycle owner to satisfy the view tree. Rendering inside the composition that is
 * already running needs neither, and works the same on every platform.
 *
 * The callbacks carry the whole outcome, so the host stays dumb: it collects passwords and reports
 * them back, and every decision about whether a password is CORRECT stays in the implementation.
 */
sealed interface PasswordRequest {

    /** Title of the prompt. */
    val label: TextRef

    /** Invoked when the user backs out. The implementation turns this into the caller's `cancel`. */
    val onCancel: () -> Unit

    /**
     * Ask for an existing password and check it.
     *
     * [onConfirm] receives what the user typed; the implementation compares it and decides whether
     * that means `ok` or `fail`, including counting attempts.
     */
    data class Query(
        override val label: TextRef,
        val pinInput: Boolean,
        val onConfirm: (String) -> Unit,
        override val onCancel: () -> Unit
    ) : PasswordRequest

    /**
     * Set or clear a password.
     *
     * [onConfirm] receives both entries so the implementation can reject a mismatch, and an empty
     * password means "clear it".
     */
    data class Set(
        override val label: TextRef,
        val pinInput: Boolean,
        val onConfirm: (String, String) -> Unit,
        override val onCancel: () -> Unit
    ) : PasswordRequest

    /**
     * Ask for a free-form password that is NOT checked against anything - used when the password is
     * about to encrypt something rather than unlock it.
     */
    data class QueryAny(
        override val label: TextRef,
        val explanation: TextRef?,
        val warning: TextRef?,
        val onConfirm: (String) -> Unit,
        override val onCancel: () -> Unit
    ) : PasswordRequest
}
