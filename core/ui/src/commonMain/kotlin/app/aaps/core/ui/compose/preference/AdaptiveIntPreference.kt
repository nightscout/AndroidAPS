/*
 * Adaptive Int Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import app.aaps.core.ui.UiStrings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.compose.isDuration
import app.aaps.core.ui.compose.rangeText
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull
import app.aaps.core.ui.compose.unitLabel
import app.aaps.core.ui.compose.valueFormat

/**
 * Composable int preference for use inside card sections.
 *
 * @param title Optional title override. If null, uses intKey.title
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 *
 * @see AdaptiveIntPreferencePreview
 */
@Composable
fun AdaptiveIntPreferenceItem(
    intKey: IntPreferenceKey,
    title: TextRef? = null,
    unit: String = "",
    visibilityContext: VisibilityContext? = null
) {
    val effectiveTitle = title ?: intKey.title

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
    val valueFormatRef = unitType.valueFormat()

    // Get unit label from UnitType (for dialog input suffix)
    val unitLabelRef = unitType.unitLabel() ?: unit.takeIf { it.isNotEmpty() }?.let { TextRef.Literal(it) }
    val unitLabelText = unitLabelRef?.let { stringResource(it) } ?: ""

    // Get summary if available
    val summary = stringResourceOrNull(intKey.summary)

    // Use slider if min/max range is specified (not default extreme values)
    val hasValidRange = intKey.min > Int.MIN_VALUE && intKey.max < Int.MAX_VALUE

    if (hasValidRange) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(theme.padding)
        ) {
            TextWithSyncBadge(
                text = stringResource(effectiveTitle),
                key = intKey,
                style = theme.titleTextStyle,
                // Mirror Preference's disabled styling (the switch row greys the same way) since this
                // slider branch builds its own row instead of going through Preference.
                color = theme.titleColor.let { if (visibility.enabled) it else it.copy(alpha = theme.disabledOpacity) }
            )
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
                valueFormatRef = valueFormatRef,
                formatAsInt = true,
                valueFormat = NumberFormat.INTEGER,
                unitLabel = unitLabelRef,
                asDuration = unitType.isDuration(),
                dialogLabel = stringResource(effectiveTitle),
                dialogSummary = summary,
                enabled = visibility.enabled
            )
        }
    } else {
        // For unspecified ranges, use text field with range summary
        val rangeRef = unitType.rangeText(value, intKey.min, intKey.max)
        val summaryText = if (rangeRef != null) {
            stringResource(rangeRef)
        } else {
            stringResource(UiStrings.preference_range_summary, value.toString(), unitLabelText, intKey.min.toString(), intKey.max.toString())
        }
        TextFieldPreference(
            state = state,
            title = { PreferenceTitleWithSyncBadge(effectiveTitle, intKey) },
            textToValue = { text ->
                text.toIntOrNull()?.coerceIn(intKey.min, intKey.max)
            },
            enabled = visibility.enabled,
            summary = { Text(summaryText) }
        )
    }
}
