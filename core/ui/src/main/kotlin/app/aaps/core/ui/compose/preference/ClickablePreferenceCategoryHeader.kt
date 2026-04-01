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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Internal composable for clickable category header with expand/collapse icon
 *
 * @param insideCard If true, uses symmetric padding suitable for card headers
 * @param iconResId Optional drawable resource ID for the icon shown next to the title
 */
@Composable
internal fun ClickablePreferenceCategoryHeader(
    titleResId: Int,
    summaryItems: List<Int> = emptyList(),
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    insideCard: Boolean = false,
    iconResId: Int? = null,
    icon: ImageVector? = null
) {
    val theme = LocalPreferenceTheme.current
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "expandIconRotation"
    )

    // Build summary text from list of resource IDs — resolve each string in composable context
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
            .clickable(onClick = onToggle)
            .padding(headerPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides theme.categoryColor) {
            // Icon (compose icon preferred, resource fallback)
            val iconPainter = when {
                icon != null                         -> rememberVectorPainter(icon)
                iconResId != null && iconResId != -1 -> painterResource(id = iconResId)
                else                                 -> null
            }
            if (iconPainter != null) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                ProvideTextStyle(value = theme.categoryTextStyle) {
                    Text(text = stringResource(titleResId))
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
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = stringResource(if (expanded) app.aaps.core.ui.R.string.collapse else app.aaps.core.ui.R.string.expand),
                modifier = Modifier
                    .size(theme.expandIconSize)
                    .rotate(rotationAngle)
            )
        }
    }
}
