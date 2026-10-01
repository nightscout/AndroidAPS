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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.stringResource

/**
 * Internal composable for clickable category header with expand/collapse icon.
 *
 * @param insideCard If true, uses symmetric padding suitable for card headers
 * @param icon Optional Compose ImageVector shown next to the title
 */
@Composable
internal fun ClickablePreferenceCategoryHeader(
    title: TextRef,
    summaryItems: List<TextRef> = emptyList(),
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    insideCard: Boolean = false,
    icon: ImageVector? = null,
    collapsible: Boolean = true
) {
    val theme = LocalPreferenceTheme.current
    val expandedState = stringResource(CoreUiStrings.state_expanded)
    val collapsedState = stringResource(CoreUiStrings.state_collapsed)
    val rotationAngle = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "expandIconRotation"
    )

    // Build summary text from the child titles — resolve each one in composable context
    val resolvedSummaries = summaryItems.map { stringResource(it) }
    val summaryText = if (resolvedSummaries.isNotEmpty()) {
        resolvedSummaries.joinToString(", ")
    } else null

    // Use symmetric padding for card headers
    val headerPadding = if (insideCard) {
        theme.headerPaddingInsideCard
    } else {
        theme.categoryPadding
    }

    // Use subtle background color when expanded (Material 3 pattern)
    val backgroundColor = if (expanded && insideCard) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .then(if (collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
            // Marked as a heading so a screen reader can jump between categories. Without this the
            // only way through a settings screen is to swipe past every single row, because
            // heading navigation - the usual way of skimming - has nothing to land on. This one
            // component backs every preference screen and settings sheet in the app.
            //
            // The open/closed state rides here too, because it belongs to the header rather than to
            // the little arrow: a screen reader announces a stateDescription as part of the item, so
            // this reads "Pump settings, collapsed" wherever the user lands on the row.
            .semantics {
                heading()
                if (collapsible) {
                    stateDescription = if (expanded) expandedState else collapsedState
                }
            }
            .padding(headerPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides theme.categoryColor) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                ProvideTextStyle(value = theme.categoryTextStyle) {
                    Text(text = stringResource(title))
                }
                // Show summary when collapsed
                if (!expanded && summaryText != null) {
                    ProvideTextStyle(value = theme.summaryCategoryTextStyle) {
                        Text(
                            text = summaryText,
                            color = theme.summaryCategoryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (collapsible) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    // Decorative now: the row carries the state, and tapping anywhere on the row
                    // toggles it, so naming the arrow as well only repeated the same fact.
                    contentDescription = null,
                    modifier = Modifier
                        .size(theme.expandIconSize)
                        .graphicsLayer { rotationZ = rotationAngle.value }
                )
            }
        }
    }
}
