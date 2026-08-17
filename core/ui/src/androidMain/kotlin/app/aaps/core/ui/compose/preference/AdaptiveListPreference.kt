/*
 * Adaptive List Preferences for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull

/**
 * Composable list int preference for use inside card sections.
 *
 * @param title Optional title override. If null, uses intKey.title
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 *
 * @see AdaptiveListIntPreferencePreview
 */
@Composable
fun AdaptiveListIntPreferenceItem(
    intKey: IntPreferenceKey,
    title: TextRef? = null,
    entries: List<String>,
    entryValues: List<Int>,
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
    val currentValue = state.value
    val currentIndex = entryValues.indexOf(currentValue).coerceAtLeast(0)
    val currentEntry = entries.getOrElse(currentIndex) { currentValue.toString() }

    // Get dialog summary from key
    val dialogSummary = stringResourceOrNull(intKey.summary)

    ListPreference(
        state = state,
        values = entryValues,
        title = { Text(stringResource(effectiveTitle)) },
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
 * @param title Optional title override. If null, uses stringKey.title
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 */
@Composable
fun AdaptiveStringListPreferenceItem(
    stringKey: StringPreferenceKey,
    title: TextRef? = null,
    entries: Map<String, String>,
    visibilityContext: VisibilityContext? = null
) {
    val effectiveTitle = title ?: stringKey.title

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
    val dialogSummary = stringResourceOrNull(stringKey.summary)

    ListPreference(
        state = state,
        values = values,
        title = { PreferenceTitleWithSyncBadge(effectiveTitle, stringKey) },
        enabled = visibility.enabled,
        summary = { Text(currentEntry) },
        dialogSummary = dialogSummary,
        valueToText = { value ->
            AnnotatedString(entries[value] ?: value)
        }
    )
}
