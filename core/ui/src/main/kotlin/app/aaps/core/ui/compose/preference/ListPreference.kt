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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

enum class ListPreferenceType {
    ALERT_DIALOG,
    DROPDOWN_MENU,
}

@Composable
fun <T> ListPreference(
    state: MutableState<T>,
    values: List<T>,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    dialogSummary: String? = null,
    type: ListPreferenceType = ListPreferenceType.ALERT_DIALOG,
    valueToText: (T) -> AnnotatedString = { AnnotatedString(it.toString()) },
    item: @Composable (value: T, currentValue: T, onClick: () -> Unit) -> Unit =
        ListPreferenceDefaults.item(type, valueToText),
) {
    var value by state
    ListPreference(
        value = value,
        onValueChange = { value = it },
        values = values,
        title = title,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
        dialogSummary = dialogSummary,
        type = type,
        valueToText = valueToText,
        item = item,
    )
}

@Composable
fun <T> ListPreference(
    value: T,
    onValueChange: (T) -> Unit,
    values: List<T>,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    dialogSummary: String? = null,
    type: ListPreferenceType = ListPreferenceType.ALERT_DIALOG,
    valueToText: (T) -> AnnotatedString = { AnnotatedString(it.toString()) },
    item: @Composable (value: T, currentValue: T, onClick: () -> Unit) -> Unit =
        ListPreferenceDefaults.item(type, valueToText),
) {
    var openSelector by rememberSaveable { mutableStateOf(false) }
    if (openSelector) {
        when (type) {
            ListPreferenceType.ALERT_DIALOG  -> {
                PreferenceAlertDialog(
                    onDismissRequest = { openSelector = false },
                    title = title,
                    buttons = {
                        TextButton(onClick = { openSelector = false }) {
                            Text(text = stringResource(android.R.string.cancel))
                        }
                    },
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (dialogSummary != null) {
                            val theme = LocalPreferenceTheme.current
                            Text(
                                text = dialogSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(theme.listItemPadding)
                                    .padding(bottom = 8.dp)
                            )
                        }
                        val lazyListState = rememberLazyListState()
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScrollIndicators(lazyListState),
                            state = lazyListState,
                        ) {
                            items(values) { itemValue ->
                                item(itemValue, value) {
                                    onValueChange(itemValue)
                                    openSelector = false
                                }
                            }
                        }
                    }
                }
            }

            ListPreferenceType.DROPDOWN_MENU -> {
                val theme = LocalPreferenceTheme.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(theme.padding.copy(vertical = 0.dp))
                ) {
                    DropdownMenu(
                        expanded = openSelector,
                        onDismissRequest = { openSelector = false },
                    ) {
                        for (itemValue in values) {
                            item(itemValue, value) {
                                onValueChange(itemValue)
                                openSelector = false
                            }
                        }
                    }
                }
            }
        }
    }
    Preference(
        title = title,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
    ) {
        openSelector = true
    }
}

@Preview(showBackground = true)
@Composable
private fun ListPreferencePreview() {
    PreviewTheme {
        ListPreference(
            value = "Option B",
            onValueChange = {},
            values = listOf("Option A", "Option B", "Option C"),
            title = { Text("Choose option") },
            summary = { Text("Option B") }
        )
    }
}

object ListPreferenceDefaults {

    fun <T> item(
        type: ListPreferenceType,
        valueToText: (T) -> AnnotatedString,
    ): @Composable (value: T, currentValue: T, onClick: () -> Unit) -> Unit =
        when (type) {
            ListPreferenceType.ALERT_DIALOG  -> {
                { value, currentValue, onClick ->
                    DialogItem(value, currentValue, valueToText, onClick)
                }
            }

            ListPreferenceType.DROPDOWN_MENU -> {
                { value, currentValue, onClick ->
                    DropdownMenuItemContent(value, currentValue, valueToText, onClick)
                }
            }
        }

    @Composable
    private fun <T> DialogItem(
        value: T,
        currentValue: T,
        valueToText: (T) -> AnnotatedString,
        onClick: () -> Unit,
    ) {
        val theme = LocalPreferenceTheme.current
        val selected = value == currentValue
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = theme.listItemMinHeight)
                    .selectable(selected, true, Role.RadioButton, onClick = onClick)
                    .padding(theme.listItemPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(theme.listItemSpacing))
            Text(
                text = valueToText(value),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    @Composable
    private fun <T> DropdownMenuItemContent(
        value: T,
        currentValue: T,
        valueToText: (T) -> AnnotatedString,
        onClick: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(text = valueToText(value)) },
            onClick = onClick,
            modifier =
                if (value == currentValue) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                } else {
                    Modifier
                },
            colors = MenuDefaults.itemColors(),
        )
    }
}
