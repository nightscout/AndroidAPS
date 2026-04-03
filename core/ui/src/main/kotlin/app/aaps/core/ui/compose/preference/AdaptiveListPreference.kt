/*
 * Adaptive List Preferences for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.keys.interfaces.StringPreferenceKey

/**
 * Composable list int preference for use inside card sections.
 *
 * @param titleResId Optional title resource ID. If 0 or not provided, uses intKey.titleResId
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 */
@Composable
fun AdaptiveListIntPreferenceItem(
    intKey: IntPreferenceKey,
    titleResId: Int = 0,
    entries: List<String>,
    entryValues: List<Int>,
    visibilityContext: PreferenceVisibilityContext? = null
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
    val currentValue = state.value
    val currentIndex = entryValues.indexOf(currentValue).coerceAtLeast(0)
    val currentEntry = entries.getOrElse(currentIndex) { currentValue.toString() }

    // Get dialog summary from key
    val summaryResId = intKey.summaryResId
    val dialogSummary = if (summaryResId != null && summaryResId != 0) stringResource(summaryResId) else null

    ListPreference(
        state = state,
        values = entryValues,
        title = { Text(stringResource(effectiveTitleResId)) },
        enabled = visibility.enabled,
        summary = { Text(currentEntry) },
        dialogSummary = dialogSummary,
        valueToText = { value ->
            val index = entryValues.indexOf(value)
            AnnotatedString(entries.getOrElse(index) { value.toString() })
        }
    )
}

/**
 * Composable string list preference for use inside card sections.
 *
 * @param titleResId Optional title resource ID. If 0 or not provided, uses stringKey.titleResId
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 */
@Composable
fun AdaptiveStringListPreferenceItem(
    stringKey: StringPreferenceKey,
    titleResId: Int = 0,
    entries: Map<String, String>,
    visibilityContext: PreferenceVisibilityContext? = null
) {
    val effectiveTitleResId = if (titleResId != 0) titleResId else stringKey.titleResId

    // Skip if no title resource is available
    if (effectiveTitleResId == 0) return

    val visibility = calculatePreferenceVisibility(
        preferenceKey = stringKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    val state = rememberPreferenceStringState(stringKey)
    val currentValue = state.value
    val currentEntry = entries[currentValue] ?: currentValue
    val values = entries.keys.toList()

    // Get dialog summary from key
    val summaryResId = stringKey.summaryResId
    val dialogSummary = if (summaryResId != null && summaryResId != 0) stringResource(summaryResId) else null

    ListPreference(
        state = state,
        values = values,
        title = { Text(stringResource(effectiveTitleResId)) },
        enabled = visibility.enabled,
        summary = { Text(currentEntry) },
        dialogSummary = dialogSummary,
        valueToText = { value ->
            AnnotatedString(entries[value] ?: value)
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun AdaptiveListIntPreferencePreview() {
    PreviewTheme {
        AdaptiveListIntPreferenceItem(
            intKey = IntKey.OverviewCarbsButtonIncrement1,
            entries = listOf("5g", "10g", "15g", "20g"),
            entryValues = listOf(5, 10, 15, 20)
        )
    }
}
