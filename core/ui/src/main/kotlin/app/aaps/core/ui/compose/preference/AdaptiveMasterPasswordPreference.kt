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
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.QueryPasswordDialog
import app.aaps.core.ui.compose.dialogs.SetPasswordDialog
import kotlinx.coroutines.launch

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
 */
@Composable
fun AdaptiveMasterPasswordPreferenceItem(
    checkPassword: (password: String, hash: String) -> Boolean,
    hashPassword: (String) -> String
) {
    val preferences = LocalPreferences.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val stringKey = StringKey.ProtectionMasterPassword

    val visibility = calculatePreferenceVisibility(
        preferenceKey = stringKey
    )

    if (!visibility.visible) return

    var passwordState by rememberPreferenceStringState(stringKey)
    val hasPassword = passwordState.isNotEmpty()

    val summary = if (hasPassword) {
        stringResource(R.string.password_set)
    } else {
        stringResource(R.string.password_not_set)
    }

    // Dialog states
    var showQueryDialog by remember { mutableStateOf(false) }
    var showSetDialog by remember { mutableStateOf(false) }

    Preference(
        title = { Text(stringResource(app.aaps.core.keys.R.string.master_password)) },
        summary = { Text(summary) },
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
    val wrongPasswordMsg = stringResource(R.string.wrongpassword)
    val dontMatchMsg = stringResource(R.string.passwords_dont_match)
    val passwordSetMsg = stringResource(R.string.password_set)
    val passwordClearedMsg = stringResource(R.string.password_cleared)
    val notChangedMsg = stringResource(R.string.password_not_changed)

    // Query current password dialog
    if (showQueryDialog) {
        QueryPasswordDialog(
            title = stringResource(R.string.current_master_password),
            pinInput = false,
            onConfirm = { enteredPassword ->
                if (checkPassword(enteredPassword, passwordState)) {
                    showQueryDialog = false
                    showSetDialog = true
                } else {
                    scope.launch { snackbarHostState.showSnackbar(wrongPasswordMsg) }
                }
            },
            onCancel = { showQueryDialog = false }
        )
    }

    // Set new password dialog
    if (showSetDialog) {
        SetPasswordDialog(
            title = stringResource(app.aaps.core.keys.R.string.master_password),
            pinInput = false,
            onConfirm = { password1, password2 ->
                when {
                    password1 != password2 -> {
                        scope.launch { snackbarHostState.showSnackbar(dontMatchMsg) }
                    }

                    password1.isNotEmpty() -> {
                        preferences.put(stringKey, hashPassword(password1))
                        passwordState = preferences.get(stringKey)
                        scope.launch { snackbarHostState.showSnackbar(passwordSetMsg) }
                        showSetDialog = false
                    }

                    preferences.getIfExists(stringKey) != null -> {
                        preferences.remove(stringKey)
                        passwordState = ""
                        scope.launch { snackbarHostState.showSnackbar(passwordClearedMsg) }
                        showSetDialog = false
                    }

                    else -> {
                        scope.launch { snackbarHostState.showSnackbar(notChangedMsg) }
                        showSetDialog = false
                    }
                }
            },
            onCancel = {
                scope.launch { snackbarHostState.showSnackbar(notChangedMsg) }
                showSetDialog = false
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AdaptiveMasterPasswordPreferencePreview() {
    PreviewTheme {
        AdaptiveMasterPasswordPreferenceItem(
            checkPassword = { _, _ -> false },
            hashPassword = { it }
        )
    }
}
