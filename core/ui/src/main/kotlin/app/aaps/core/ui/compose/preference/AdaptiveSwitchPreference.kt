/*
 * Adaptive Switch Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext

/**
 * Composable switch preference for use inside card sections.
 *
 * @param titleResId Optional title resource ID. If 0 or not provided, uses booleanKey.titleResId
 * @param summaryResId Optional summary resource ID. If null, uses booleanKey.summaryResId
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 */
@Composable
fun AdaptiveSwitchPreferenceItem(
    booleanKey: BooleanPreferenceKey,
    titleResId: Int = 0,
    summaryResId: Int? = null,
    summaryOnResId: Int? = null,
    summaryOffResId: Int? = null,
    visibilityContext: PreferenceVisibilityContext? = null
) {
    val effectiveTitleResId = if (titleResId != 0) titleResId else booleanKey.titleResId
    val effectiveSummaryResId = summaryResId ?: booleanKey.summaryResId

    // Skip if no title resource is available
    if (effectiveTitleResId == 0) return

    val visibility = calculatePreferenceVisibility(
        preferenceKey = booleanKey,
        engineeringModeOnly = booleanKey.engineeringModeOnly,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    val state = rememberPreferenceBooleanState(booleanKey)
    SwitchPreference(
        state = state,
        title = { Text(stringResource(effectiveTitleResId)) },
        summary = when {
            summaryOnResId != null && summaryOffResId != null -> {
                { Text(stringResource(if (state.value) summaryOnResId else summaryOffResId)) }
            }

            effectiveSummaryResId != null                     -> {
                { Text(stringResource(effectiveSummaryResId)) }
            }

            else                                              -> null
        },
        enabled = visibility.enabled
    )
}
