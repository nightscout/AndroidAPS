package app.aaps.core.interfaces.protection

import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.flow.StateFlow

/**
 * Asks the user for a password, and decides whether the answer was right.
 *
 * No `Context` and no resource ids: the prompt is published as a [PasswordRequest] on [request] and
 * drawn by `PasswordCheckHost`, which the UI places once near its root. See [PasswordRequest] for
 * why that indirection exists.
 */
interface PasswordCheck {

    /** The prompt to show, or null when nothing is being asked. */
    val request: StateFlow<PasswordRequest?>

    /**
     *  Asks for "managed" kind of password, checking if it is valid.
     */
    fun queryPassword(
        label: TextRef,
        preference: StringPreferenceKey,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)? = null,
        fail: (() -> Unit)? = null,
        pinInput: Boolean = false
    )

    fun setPassword(
        label: TextRef,
        preference: StringPreferenceKey,
        ok: ((String) -> Unit)? = null,
        cancel: (() -> Unit)? = null,
        clear: (() -> Unit)? = null,
        pinInput: Boolean = false
    )

    /**
     * Prompt free-form password, with additional help and warning messages.
     * Preference ID (preference) is used only to generate ID for password managers,
     * since this query does NOT check validity of password.
     */
    fun queryAnyPassword(
        label: TextRef,
        preference: StringPreferenceKey,
        passwordExplanation: TextRef?,
        passwordWarning: TextRef?,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)? = null
    )
}
