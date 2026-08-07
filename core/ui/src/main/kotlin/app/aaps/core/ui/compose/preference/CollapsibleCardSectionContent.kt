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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.keys.interfaces.TextRef

/**
 * Composable for a collapsible card section.
 * This is separated from the LazyListScope extension to avoid cross-module compilation issues
 * with @Composable lambda parameters.
 *
 * @see CollapsibleCardSectionContentPreview
 */
@Composable
fun CollapsibleCardSectionContent(
    title: TextRef,
    summaryItems: List<TextRef> = emptyList(),
    expanded: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector? = null,
    collapsible: Boolean = true,
    content: @Composable () -> Unit
) {
    val theme = LocalPreferenceTheme.current
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(theme.cardPadding),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = theme.cardElevation),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            ClickablePreferenceCategoryHeader(
                title = title,
                summaryItems = summaryItems,
                expanded = expanded,
                onToggle = onToggle,
                insideCard = true,
                icon = icon,
                collapsible = collapsible
            )

            AnimatedVisibility(
                visible = expanded || !collapsible,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(bottom = theme.cardContentBottomPadding)) {
                    content()
                }
            }
        }
    }
}
