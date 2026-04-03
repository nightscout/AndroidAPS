package app.aaps.core.ui.compose.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.R

/**
 * Dialog for querying an existing password or PIN.
 *
 * @param title Dialog title
 * @param pinInput If true, shows numeric keyboard for PIN input
 * @param onConfirm Called with entered password when OK is clicked or Done is pressed
 * @param onCancel Called when Cancel is clicked or dialog is dismissed
 */
@Composable
fun QueryPasswordDialog(
    title: String,
    pinInput: Boolean,
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
            OutlinedTextField(
                value = passwordText,
                onValueChange = { passwordText = it },
                label = {
                    Text(stringResource(if (pinInput) R.string.protection_pin_hint else R.string.protection_password_hint))
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (pinInput) KeyboardType.NumberPassword else KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onConfirm(passwordText)
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
                    onConfirm(passwordText)
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}

@Preview(showBackground = true)
@Composable
private fun QueryPasswordDialogPreview() {
    MaterialTheme {
        QueryPasswordDialog(
            title = "Enter Password",
            pinInput = false,
            onConfirm = {},
            onCancel = {}
        )
    }
}
