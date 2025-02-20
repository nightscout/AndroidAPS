package app.aaps.core.interfaces.protection

import android.content.Context
import androidx.annotation.StringRes
import app.aaps.core.keys.interfaces.StringPreferenceKey

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

}