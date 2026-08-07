/*
 * Adaptive Password Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.dialogs.SetPasswordDialog
import app.aaps.core.ui.compose.stringResource

/**
 * Composable password/PIN preference that opens a dialog to set the password.
 * Automatically detects if it's a password or PIN from the StringPreferenceKey flags.
 *
 * @param preferences The Preferences instance
 * @param stringKey The StringPreferenceKey (should have isPassword=true or isPin=true)
 * @param hashPassword Function to hash the password before storing
 * @param titleResId Optional title resource ID. If 0, uses stringKey.title
 * @param visibilityKey Optional IntPreferenceKey that controls visibility
 * @param visibilityValue The value that visibilityKey must equal for this preference to be visible
 * @param visibilityContext Optional context for evaluating runtime visibility conditions
 *
 * @see AdaptivePasswordPreferencePreview
 */
@Composable
fun AdaptivePasswordPreferenceItem(
    stringKey: StringPreferenceKey,
    hashPassword: (String) -> String,
    onShowMessage: (String) -> Unit,
    title: TextRef? = null,
    visibilityKey: IntPreferenceKey? = null,
    visibilityValue: Int? = null,
    visibilityContext: VisibilityContext? = null
) {
    val preferences = LocalPreferences.current
    val effectiveTitle = title ?: stringKey.title

    // Check conditional visibility based on visibilityKey
    if (visibilityKey != null && visibilityValue != null) {
        val currentValue by rememberPreferenceIntState(visibilityKey)
        if (currentValue != visibilityValue) return
    }

    // Check standard visibility
    val visibility = calculatePreferenceVisibility(
        preferenceKey = stringKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    // State for tracking if password is set
    var hasValue by remember { mutableStateOf(preferences.get(stringKey).isNotEmpty()) }
    val isPin = stringKey.isPin

    // State for showing the dialog
    var showDialog by remember { mutableStateOf(false) }

    val summary = when {
        hasValue -> "••••••••"
        isPin    -> stringResource(R.string.pin_not_set)
        else     -> stringResource(R.string.password_not_set)
    }

    Preference(
        title = { Text(stringResource(effectiveTitle)) },
        summary = { Text(summary) },
        enabled = visibility.enabled,
        onClick = if (visibility.enabled) {
            { showDialog = true }
        } else null
    )

    if (showDialog) {
        val dontMatchMsg = stringResource(if (isPin) R.string.pin_dont_match else R.string.passwords_dont_match)
        val setMsg = stringResource(if (isPin) R.string.pin_set else R.string.password_set)
        val clearedMsg = stringResource(if (isPin) R.string.pin_cleared else R.string.password_cleared)
        val notChangedMsg = stringResource(if (isPin) R.string.pin_not_changed else R.string.password_not_changed)

        SetPasswordDialog(
            title = stringResource(effectiveTitle),
            pinInput = isPin,
            onConfirm = { password1, password2 ->
                when {
                    password1 != password2 -> {
                        onShowMessage(dontMatchMsg)
                    }

                    password1.isNotEmpty() -> {
                        preferences.put(stringKey, if (stringKey.isHashed) hashPassword(password1) else password1)
                        onShowMessage(setMsg)
                        hasValue = true
                        showDialog = false
                    }

                    preferences.getIfExists(stringKey) != null -> {
                        preferences.remove(stringKey)
                        onShowMessage(clearedMsg)
                        hasValue = false
                        showDialog = false
                    }

                    else -> {
                        onShowMessage(notChangedMsg)
                        showDialog = false
                    }
                }
            },
            onCancel = {
                onShowMessage(notChangedMsg)
                showDialog = false
            }
        )
    }
}
