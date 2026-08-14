package app.aaps.core.interfaces.protection

import android.content.Context
import androidx.annotation.StringRes
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

interface PasswordCheck {

    /**
     *  Asks for "managed" kind of password, checking if it is valid.
     */
    fun queryPassword(
        context: Context,
        @StringRes labelId: Int,
        preference: StringPreferenceKey,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)? = null,
        fail: (() -> Unit)? = null,
        pinInput: Boolean = false
    )

    fun setPassword(
        context: Context,
        @StringRes labelId: Int,
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
        context: Context, @StringRes labelId: Int, preference: StringPreferenceKey, @StringRes passwordExplanation: Int?,
        @StringRes passwordWarning: Int?, ok: ((String) -> Unit)?, cancel: (() -> Unit)? = null
    )

    /**
     * [TextRef] variants of the three calls above.
     *
     * Needed because the label often comes from a preference key, and keys in a multiplatform module
     * have no Android resource id to pass. The `Int` versions stay for callers that name their own
     * module's `R.string.x`.
     */
    fun queryPassword(
        context: Context,
        label: TextRef,
        preference: StringPreferenceKey,
        ok: ((String) -> Unit)?,
        cancel: (() -> Unit)? = null,
        fail: (() -> Unit)? = null,
        pinInput: Boolean = false
    )

    fun setPassword(
        context: Context,
        label: TextRef,
        preference: StringPreferenceKey,
        ok: ((String) -> Unit)? = null,
        cancel: (() -> Unit)? = null,
        clear: (() -> Unit)? = null,
        pinInput: Boolean = false
    )
}