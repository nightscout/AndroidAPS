/*
 * Adaptive Switch Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.BooleanKeyWithChangeGuard
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
    val changeGuard = (booleanKey as? BooleanKeyWithChangeGuard)?.guard

    var guardMessage by remember { mutableStateOf<String?>(null) }

    val summary: @Composable (() -> Unit)? = when {
        summaryOnResId != null && summaryOffResId != null -> {
            { Text(stringResource(if (state.value) summaryOnResId else summaryOffResId)) }
        }

        effectiveSummaryResId != null                     -> {
            { Text(stringResource(effectiveSummaryResId)) }
        }

        else                                              -> null
    }

    if (changeGuard != null) {
        SwitchPreference(
            value = state.value,
            onValueChange = { newValue ->
                val message = changeGuard(newValue)
                if (message == null) {
                    state.value = newValue
                } else {
                    guardMessage = message
                }
            },
            title = { Text(stringResource(effectiveTitleResId)) },
            summary = summary,
            enabled = visibility.enabled
        )
    } else {
        SwitchPreference(
            state = state,
            title = { Text(stringResource(effectiveTitleResId)) },
            summary = summary,
            enabled = visibility.enabled
        )
    }

    // Show guard rejection dialog
    guardMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { guardMessage = null },
            confirmButton = {
                TextButton(onClick = { guardMessage = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            text = { Text(message) }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AdaptiveSwitchPreferencePreview() {
    PreviewTheme {
        AdaptiveSwitchPreferenceItem(
            booleanKey = BooleanKey.OverviewKeepScreenOn
        )
    }
}
