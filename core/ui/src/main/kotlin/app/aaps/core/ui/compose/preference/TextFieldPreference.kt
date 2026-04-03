/*
 * Copyright 2023 Google LLC
 * Adapted for AndroidAPS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun <T> TextFieldPreference(
    state: MutableState<T>,
    title: @Composable () -> Unit,
    textToValue: (String) -> T?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    dialogSummary: String? = null,
    valueToText: (T) -> String = { it.toString() },
    textField:
    @Composable
        (value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, onOk: () -> Unit) -> Unit =
        TextFieldPreferenceDefaults.TextField,
) {
    var value by state
    TextFieldPreference(
        value = value,
        onValueChange = { value = it },
        title = title,
        textToValue = textToValue,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
        dialogSummary = dialogSummary,
        valueToText = valueToText,
        textField = textField,
    )
}

@Composable
fun <T> TextFieldPreference(
    value: T,
    onValueChange: (T) -> Unit,
    title: @Composable () -> Unit,
    textToValue: (String) -> T?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    dialogSummary: String? = null,
    valueToText: (T) -> String = { it.toString() },
    textField:
    @Composable
        (value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, onOk: () -> Unit) -> Unit =
        TextFieldPreferenceDefaults.TextField,
) {
    var openDialog by rememberSaveable { mutableStateOf(false) }
    Preference(
        title = title,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
    ) {
        openDialog = true
    }
    if (openDialog) {
        var dialogText by
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            val text = valueToText(value)
            mutableStateOf(TextFieldValue(text, TextRange(text.length)))
        }
        val onOk = {
            val dialogValue = textToValue(dialogText.text)
            if (dialogValue != null) {
                onValueChange(dialogValue)
                openDialog = false
            }
        }
        PreferenceAlertDialog(
            onDismissRequest = { openDialog = false },
            title = title,
            buttons = {
                TextButton(onClick = { openDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(onClick = onOk) { Text(text = stringResource(android.R.string.ok)) }
            },
        ) {
            val focusRequester = remember { FocusRequester() }
            val theme = LocalPreferenceTheme.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.textFieldHorizontalPadding)
            ) {
                if (dialogSummary != null) {
                    Text(
                        text = dialogSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                Box(modifier = Modifier.focusRequester(focusRequester)) {
                    textField(dialogText, { dialogText = it }, onOk)
                }
            }
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TextFieldPreferencePreview() {
    PreviewTheme {
        TextFieldPreference(
            value = "Hello",
            onValueChange = {},
            title = { Text("Username") },
            textToValue = { it },
            summary = { Text("Hello") }
        )
    }
}

object TextFieldPreferenceDefaults {

    val TextField:
        @Composable
            (value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, onOk: () -> Unit) -> Unit =
        { value, onValueChange, onOk ->
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = KeyboardActions { onOk() },
                singleLine = true,
            )
        }
}
