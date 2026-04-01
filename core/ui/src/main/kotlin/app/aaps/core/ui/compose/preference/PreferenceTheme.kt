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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class PreferenceTheme(
    val categoryPadding: PaddingValues,
    val categoryColor: Color,
    val categoryTextStyle: TextStyle,
    val summaryCategoryColor: Color,
    val summaryCategoryTextStyle: TextStyle,
    val padding: PaddingValues,
    val horizontalSpacing: Dp,
    val verticalSpacing: Dp,
    val disabledOpacity: Float,
    val iconContainerMinWidth: Dp,
    val iconColor: Color,
    val titleColor: Color,
    val titleTextStyle: TextStyle,
    val summaryColor: Color,
    val summaryTextStyle: TextStyle,
    val dividerHeight: Dp,
    // Card styling
    val cardPadding: PaddingValues,
    val cardElevation: Dp,
    val cardContentBottomPadding: Dp,
    // Nested content
    val nestedContentIndent: Dp,
    // Header
    val headerPaddingInsideCard: PaddingValues,
    val expandIconSize: Dp,
    // List items
    val listItemMinHeight: Dp,
    val listItemPadding: PaddingValues,
    val listItemSpacing: Dp,
    // Dialog
    val dialogTitlePadding: PaddingValues,
    val dialogButtonsPadding: PaddingValues,
    val dialogButtonSpacing: Dp,
    val dialogButtonCrossSpacing: Dp,
    // Text fields
    val textFieldHorizontalPadding: Dp,
)

@Composable
fun preferenceTheme(
    categoryPadding: PaddingValues =
        PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
    categoryColor: Color = MaterialTheme.colorScheme.secondary,
    categoryTextStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
    summaryCategoryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    summaryCategoryTextStyle: TextStyle = MaterialTheme.typography.bodySmall,
    padding: PaddingValues = PaddingValues(16.dp),
    horizontalSpacing: Dp = 16.dp,
    verticalSpacing: Dp = 16.dp,
    disabledOpacity: Float = 0.38f,
    iconContainerMinWidth: Dp = 56.dp,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    titleTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    summaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    summaryTextStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    dividerHeight: Dp = 32.dp,
    // Card styling
    cardPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    cardElevation: Dp = 4.dp,
    cardContentBottomPadding: Dp = 8.dp,
    // Nested content
    nestedContentIndent: Dp = 16.dp,
    // Header
    headerPaddingInsideCard: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    expandIconSize: Dp = 24.dp,
    // List items
    listItemMinHeight: Dp = 48.dp,
    listItemPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
    listItemSpacing: Dp = 24.dp,
    // Dialog
    dialogTitlePadding: PaddingValues = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
    dialogButtonsPadding: PaddingValues = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 24.dp),
    dialogButtonSpacing: Dp = 8.dp,
    dialogButtonCrossSpacing: Dp = 12.dp,
    // Text fields
    textFieldHorizontalPadding: Dp = 24.dp,
): PreferenceTheme =
    PreferenceTheme(
        categoryPadding = categoryPadding,
        categoryColor = categoryColor,
        categoryTextStyle = categoryTextStyle,
        summaryCategoryColor = summaryCategoryColor,
        summaryCategoryTextStyle = summaryCategoryTextStyle,
        padding = padding,
        horizontalSpacing = horizontalSpacing,
        verticalSpacing = verticalSpacing,
        disabledOpacity = disabledOpacity,
        iconContainerMinWidth = iconContainerMinWidth,
        iconColor = iconColor,
        titleColor = titleColor,
        titleTextStyle = titleTextStyle,
        summaryColor = summaryColor,
        summaryTextStyle = summaryTextStyle,
        dividerHeight = dividerHeight,
        cardPadding = cardPadding,
        cardElevation = cardElevation,
        cardContentBottomPadding = cardContentBottomPadding,
        nestedContentIndent = nestedContentIndent,
        headerPaddingInsideCard = headerPaddingInsideCard,
        expandIconSize = expandIconSize,
        listItemMinHeight = listItemMinHeight,
        listItemPadding = listItemPadding,
        listItemSpacing = listItemSpacing,
        dialogTitlePadding = dialogTitlePadding,
        dialogButtonsPadding = dialogButtonsPadding,
        dialogButtonSpacing = dialogButtonSpacing,
        dialogButtonCrossSpacing = dialogButtonCrossSpacing,
        textFieldHorizontalPadding = textFieldHorizontalPadding,
    )

val LocalPreferenceTheme: ProvidableCompositionLocal<PreferenceTheme> = compositionLocalOf {
    noLocalProvidedFor("LocalPreferenceTheme")
}

@Composable
fun ProvidePreferenceTheme(
    theme: PreferenceTheme = preferenceTheme(),
    content: @Composable () -> Unit,
) {
    val sharedPreferenceStates = remember { mutableStateMapOf<String, Any?>() }
    CompositionLocalProvider(
        LocalPreferenceTheme provides theme,
        LocalSharedPreferenceStates provides sharedPreferenceStates,
        content = content
    )
}

internal fun noLocalProvidedFor(name: String): Nothing {
    error("CompositionLocal $name not present")
}
