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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Preference(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    widgetContainer: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    BasicPreference(
        textContainer = {
            val theme = LocalPreferenceTheme.current
            Column(
                modifier =
                    Modifier.padding(
                        theme.padding.copy(
                            start = if (icon != null) 0.dp else Dp.Unspecified,
                            end = if (widgetContainer != null) 0.dp else Dp.Unspecified,
                        )
                    )
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides
                        theme.titleColor.let {
                            if (enabled) it else it.copy(alpha = theme.disabledOpacity)
                        }
                ) {
                    ProvideTextStyle(value = theme.titleTextStyle, content = title)
                }
                if (summary != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides
                            theme.summaryColor.let {
                                if (enabled) it else it.copy(alpha = theme.disabledOpacity)
                            }
                    ) {
                        ProvideTextStyle(value = theme.summaryTextStyle, content = summary)
                    }
                }
            }
        },
        modifier = modifier,
        enabled = enabled,
        iconContainer = {
            if (icon != null) {
                val theme = LocalPreferenceTheme.current
                Box(
                    modifier =
                        Modifier
                            .widthIn(min = theme.iconContainerMinWidth)
                            .padding(theme.padding.copy(end = 0.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides
                            theme.iconColor.let {
                                if (enabled) it else it.copy(alpha = theme.disabledOpacity)
                            },
                        content = icon,
                    )
                }
            }
        },
        widgetContainer = { widgetContainer?.invoke() },
        onClick = onClick,
    )
}

@Preview(showBackground = true)
@Composable
private fun PreferencePreview() {
    PreviewTheme {
        Preference(
            title = { Text("Preference title") },
            summary = { Text("Summary text") },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceDisabledPreview() {
    PreviewTheme {
        Preference(
            title = { Text("Disabled preference") },
            summary = { Text("This preference is disabled") },
            enabled = false
        )
    }
}
