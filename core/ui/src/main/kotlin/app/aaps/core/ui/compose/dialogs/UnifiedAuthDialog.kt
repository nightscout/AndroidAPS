package app.aaps.core.ui.compose.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.interfaces.protection.AuthMethod
import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.protection.ProtectionType
import app.aaps.core.ui.R

/**
 * Unified authentication dialog that accepts a single credential input and tries it
 * against all configured authentication methods (from highest level down).
 *
 * Returns the highest matching [ProtectionCheck.Protection] level.
 * Shows numeric keyboard only if all methods use PIN input.
 *
 * @param methods Available authentication methods (excluding biometric, which is handled separately)
 * @param checkPassword Function to verify an entered password against a stored hash
 * @param onResult Callback with the authorization result
 */
@Composable
fun UnifiedAuthDialog(
    methods: List<AuthMethod>,
    checkPassword: (password: String, hash: String) -> Boolean,
    onResult: (AuthorizationResult) -> Unit
) {
    // Use numeric keyboard only if ALL methods are PIN-based
    val pinInput = methods.isNotEmpty() && methods.all { it.isPinInput }
    val hasPin = methods.any { it.isPinInput }
    val hasCustomPassword = methods.any { it.type == ProtectionType.CUSTOM_PASSWORD }

    // Pick hint: master password is always accepted, plus any custom credentials
    val hintRes = when {
        hasPin -> R.string.auth_hint_master_or_pin
        hasCustomPassword -> R.string.auth_hint_master_or_password
        else -> R.string.auth_hint_master_password
    }

    var passwordText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun tryAuthenticate(entered: String) {
        // Try from highest level down to find the best match
        val sorted = methods
            .filter { it.type != ProtectionType.BIOMETRIC && it.credentialHash.isNotEmpty() }
            .sortedByDescending { it.level.level }

        for (method in sorted) {
            if (checkPassword(entered, method.credentialHash)) {
                onResult(AuthorizationResult(method.level, ProtectionResult.GRANTED))
                return
            }
        }
        onResult(AuthorizationResult(null, ProtectionResult.DENIED))
    }

    AlertDialog(
        onDismissRequest = { onResult(AuthorizationResult(null, ProtectionResult.CANCELLED)) },
        icon = {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null
            )
        },
        title = {
            Text(
                text = stringResource(R.string.biometric_title),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            OutlinedTextField(
                value = passwordText,
                onValueChange = { passwordText = it },
                label = {
                    Text(stringResource(hintRes))
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (pinInput) KeyboardType.NumberPassword else KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        tryAuthenticate(passwordText)
                    }
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    keyboardController?.hide()
                    tryAuthenticate(passwordText)
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { onResult(AuthorizationResult(null, ProtectionResult.CANCELLED)) }) {
                Text(stringResource(R.string.cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}
