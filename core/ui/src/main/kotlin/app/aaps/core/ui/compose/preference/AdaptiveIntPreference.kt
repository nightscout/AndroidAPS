/*
 * Adaptive Int Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.keys.rangeResId
import app.aaps.core.keys.unitLabelResId
import app.aaps.core.keys.valueResId
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.ExcludeFromJacocoGeneratedReport
import java.text.DecimalFormat

/**
 * Composable int preference for use inside card sections.
 *
 * @param titleResId Optional title resource ID. If 0 or not provided, uses intKey.titleResId
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 */
@Composable
fun AdaptiveIntPreferenceItem(
    intKey: IntPreferenceKey,
    titleResId: Int = 0,
    unit: String = "",
    visibilityContext: VisibilityContext? = null
) {
    val effectiveTitleResId = if (titleResId != 0) titleResId else intKey.titleResId

    // Skip if no title resource is available
    if (effectiveTitleResId == 0) return

    val visibility = calculatePreferenceVisibility(
        preferenceKey = intKey,
        engineeringModeOnly = intKey.engineeringModeOnly,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    val state = rememberPreferenceIntState(intKey)
    val value = state.value
    val theme = LocalPreferenceTheme.current

    // Get formatting info from UnitType
    val unitType = intKey.unitType
    val valueFormatResId = unitType.valueResId()

    // Get unit label from UnitType (for dialog input suffix)
    val unitLabelResId = unitType.unitLabelResId()
    val unitLabel = unitLabelResId?.let { stringResource(it) } ?: unit

    // Get summary if available
    val summaryResId = intKey.summaryResId
    val summary = if (summaryResId != null && summaryResId != 0) stringResource(summaryResId) else null

    // Use slider if min/max range is specified (not default extreme values)
    val hasValidRange = intKey.min > Int.MIN_VALUE && intKey.max < Int.MAX_VALUE

    if (hasValidRange) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(theme.padding)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(effectiveTitleResId),
                    style = theme.titleTextStyle,
                    // Mirror Preference's disabled styling (the switch row greys the same way) since this
                    // slider branch builds its own row instead of going through Preference.
                    color = theme.titleColor.let { if (visibility.enabled) it else it.copy(alpha = theme.disabledOpacity) }
                )
                SyncBadge(intKey, Modifier.padding(start = 6.dp))
            }
            if (summary != null) {
                Text(
                    text = summary,
                    style = theme.summaryTextStyle,
                    color = theme.summaryColor.let { if (visibility.enabled) it else it.copy(alpha = theme.disabledOpacity) }
                )
            }
            PreferenceSliderWithButtons(
                value = value.toDouble(),
                onValueChange = { newValue ->
                    if (visibility.enabled) {
                        state.value = newValue.toInt()
                    }
                },
                valueRange = intKey.min.toDouble()..intKey.max.toDouble(),
                step = 1.0,
                showValue = true,
                valueFormatResId = valueFormatResId,
                formatAsInt = true,
                valueFormat = DecimalFormat("0"),
                unitLabel = unitLabel,
                dialogLabel = stringResource(effectiveTitleResId),
                dialogSummary = summary,
                enabled = visibility.enabled
            )
        }
    } else {
        // For unspecified ranges, use text field with range summary
        val rangeFormatResId = unitType.rangeResId()
        val summaryText = if (rangeFormatResId != null) {
            stringResource(rangeFormatResId, value, intKey.min, intKey.max)
        } else {
            stringResource(R.string.preference_range_summary, value.toString(), unitLabel, intKey.min.toString(), intKey.max.toString())
        }
        TextFieldPreference(
            state = state,
            title = { PreferenceTitleWithSyncBadge(effectiveTitleResId, intKey) },
            textToValue = { text ->
                text.toIntOrNull()?.coerceIn(intKey.min, intKey.max)
            },
            enabled = visibility.enabled,
            summary = { Text(summaryText) }
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun AdaptiveIntPreferencePreview() {
    PreviewTheme {
        AdaptiveIntPreferenceItem(
            intKey = IntKey.OverviewCarbsButtonIncrement1
        )
    }
}
