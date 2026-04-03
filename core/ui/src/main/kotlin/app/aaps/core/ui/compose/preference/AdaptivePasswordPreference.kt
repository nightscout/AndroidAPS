/*
 * Adaptive Password Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.SetPasswordDialog
import kotlinx.coroutines.launch

/**
 * Composable password/PIN preference that opens a dialog to set the password.
 * Automatically detects if it's a password or PIN from the StringPreferenceKey flags.
 *
 * @param preferences The Preferences instance
 * @param stringKey The StringPreferenceKey (should have isPassword=true or isPin=true)
 * @param hashPassword Function to hash the password before storing
 * @param titleResId Optional title resource ID. If 0, uses stringKey.titleResId
 * @param visibilityKey Optional IntPreferenceKey that controls visibility
 * @param visibilityValue The value that visibilityKey must equal for this preference to be visible
 * @param visibilityContext Optional context for evaluating runtime visibility conditions
 */
@Composable
fun AdaptivePasswordPreferenceItem(
    stringKey: StringPreferenceKey,
    hashPassword: (String) -> String,
    titleResId: Int = 0,
    visibilityKey: IntPreferenceKey? = null,
    visibilityValue: Int? = null,
    visibilityContext: PreferenceVisibilityContext? = null
) {
    val preferences = LocalPreferences.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val effectiveTitleResId = if (titleResId != 0) titleResId else stringKey.titleResId

    // Skip if no title resource is available
    if (effectiveTitleResId == 0) return

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
        title = { Text(stringResource(effectiveTitleResId)) },
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
            title = stringResource(effectiveTitleResId),
            pinInput = isPin,
            onConfirm = { password1, password2 ->
                when {
                    password1 != password2 -> {
                        scope.launch { snackbarHostState.showSnackbar(dontMatchMsg) }
                    }

                    password1.isNotEmpty() -> {
                        preferences.put(stringKey, if (stringKey.isHashed) hashPassword(password1) else password1)
                        scope.launch { snackbarHostState.showSnackbar(setMsg) }
                        hasValue = true
                        showDialog = false
                    }

                    preferences.getIfExists(stringKey) != null -> {
                        preferences.remove(stringKey)
                        scope.launch { snackbarHostState.showSnackbar(clearedMsg) }
                        hasValue = false
                        showDialog = false
                    }

                    else -> {
                        scope.launch { snackbarHostState.showSnackbar(notChangedMsg) }
                        showDialog = false
                    }
                }
            },
            onCancel = {
                scope.launch { snackbarHostState.showSnackbar(notChangedMsg) }
                showDialog = false
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AdaptivePasswordPreferencePreview() {
    PreviewTheme {
        AdaptivePasswordPreferenceItem(
            stringKey = StringKey.ProtectionSettingsPassword,
            hashPassword = { it }
        )
    }
}
