package app.aaps.core.ui.compose.dialogs

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.UiStrings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.R

/**
 * Dialog for querying a free-form password with optional explanation and warning messages.
 * Does NOT validate the password - just returns whatever was entered.
 *
 * @param title Dialog title
 * @param passwordExplanation Optional explanation text shown above the input
 * @param passwordWarning Optional warning text shown in error color
 * @param onConfirm Called with entered password when OK is clicked
 * @param onCancel Called when Cancel is clicked or dialog is dismissed
 *
 * @see QueryAnyPasswordDialogPreview
 */
@Composable
fun QueryAnyPasswordDialog(
    title: String,
    passwordExplanation: String? = null,
    passwordWarning: String? = null,
    errorMessage: String? = null,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var passwordText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null
            )
        },
        title = {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                passwordExplanation?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                passwordWarning?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (passwordExplanation != null || passwordWarning != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = passwordText,
                    onValueChange = { passwordText = it },
                    label = { Text(stringResource(UiStrings.protection_password_hint)) },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg -> { Text(msg, color = MaterialTheme.colorScheme.error) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (passwordText.isNotBlank()) {
                                keyboardController?.hide()
                                onConfirm(passwordText)
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = passwordText.isNotBlank(),
                onClick = {
                    keyboardController?.hide()
                    onConfirm(passwordText)
                }
            ) {
                Text(stringResource(UiStrings.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(UiStrings.cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}
