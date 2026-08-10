package app.aaps.core.ui.compose.preference

import app.aaps.core.ui.UiStrings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.dialogs.QueryPasswordDialog
import app.aaps.core.ui.compose.dialogs.SetPasswordDialog

/**
 * Master password preference that requires current password verification before allowing change.
 *
 * Logic:
 * - If master password is set: user must enter current password before setting new one
 * - If master password is not set: user can set it directly
 *
 * @param preferences The Preferences instance
 * @param checkPassword Function to verify password: (enteredPassword, storedHash) -> Boolean
 * @param hashPassword Function to hash password before storing: (password) -> String
 *
 * @see AdaptiveMasterPasswordPreferencePreview
 */
@Composable
fun AdaptiveMasterPasswordPreferenceItem(
    checkPassword: (password: String, hash: String) -> Boolean,
    hashPassword: (String) -> String,
    onShowMessage: (String) -> Unit,
    showTitle: Boolean = true
) {
    val preferences = LocalPreferences.current
    val clearExportPasswordStore = LocalClearExportPasswordStore.current
    val stringKey = StringKey.ProtectionMasterPassword

    val visibility = calculatePreferenceVisibility(
        preferenceKey = stringKey
    )

    if (!visibility.visible) return

    var passwordState by rememberPreferenceStringState(stringKey)
    val hasPassword = passwordState.isNotEmpty()

    val summary = if (hasPassword) {
        stringResource(UiStrings.password_set)
    } else {
        stringResource(UiStrings.password_not_set)
    }

    // Dialog states
    var showQueryDialog by remember { mutableStateOf(false) }
    var showSetDialog by remember { mutableStateOf(false) }

    Preference(
        title = if (showTitle) {
            { Text(stringResource(StringKey.ProtectionMasterPassword.title)) }
        } else {
            { Text(summary) }
        },
        summary = if (showTitle) {
            { Text(summary) }
        } else null,
        enabled = visibility.enabled,
        onClick = if (visibility.enabled) {
            {
                if (hasPassword) {
                    // Password exists - query current password first
                    showQueryDialog = true
                } else {
                    // No password - set directly
                    showSetDialog = true
                }
            }
        } else null
    )

    // Message strings (resolved here for use in callbacks)
    val wrongPasswordMsg = stringResource(UiStrings.wrongpassword)
    val dontMatchMsg = stringResource(UiStrings.passwords_dont_match)
    val passwordSetMsg = stringResource(UiStrings.password_set)
    val passwordClearedMsg = stringResource(UiStrings.password_cleared)
    val notChangedMsg = stringResource(UiStrings.password_not_changed)

    // Query current password dialog
    if (showQueryDialog) {
        QueryPasswordDialog(
            title = stringResource(UiStrings.current_master_password),
            pinInput = false,
            onConfirm = { enteredPassword ->
                if (checkPassword(enteredPassword, passwordState)) {
                    showQueryDialog = false
                    showSetDialog = true
                } else {
                    onShowMessage(wrongPasswordMsg)
                }
            },
            onCancel = { showQueryDialog = false }
        )
    }

    // Set new password dialog
    if (showSetDialog) {
        SetPasswordDialog(
            title = stringResource(StringKey.ProtectionMasterPassword.title),
            pinInput = false,
            onConfirm = { password1, password2 ->
                when {
                    password1 != password2 -> {
                        onShowMessage(dontMatchMsg)
                    }

                    password1.isNotEmpty() -> {
                        preferences.put(stringKey, hashPassword(password1))
                        // Master password changed: drop the stored unattended-export password so exports
                        // can't keep using the old secret until it expires.
                        clearExportPasswordStore?.invoke()
                        passwordState = preferences.get(stringKey)
                        onShowMessage(passwordSetMsg)
                        showSetDialog = false
                    }

                    preferences.getIfExists(stringKey) != null -> {
                        preferences.remove(stringKey)
                        clearExportPasswordStore?.invoke()
                        passwordState = ""
                        onShowMessage(passwordClearedMsg)
                        showSetDialog = false
                    }

                    else -> {
                        onShowMessage(notChangedMsg)
                        showSetDialog = false
                    }
                }
            },
            onCancel = {
                onShowMessage(notChangedMsg)
                showSetDialog = false
            }
        )
    }
}
