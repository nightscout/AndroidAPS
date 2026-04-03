/*
 * Adaptive Intent Preferences for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.ui.compose.ComposeScreenContent
import app.aaps.core.ui.compose.dialogs.OkCancelDialog

/**
 * Composable intent preference for use inside card sections.
 *
 * @param titleResId Optional title resource ID. If 0 or not provided, uses intentKey.titleResId
 * @param summaryResId Optional summary resource ID. If null, uses intentKey.summaryResId
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 */
@Composable
fun AdaptiveIntentPreferenceItem(
    intentKey: IntentPreferenceKey,
    titleResId: Int = 0,
    summaryResId: Int? = null,
    onClick: () -> Unit,
    visibilityContext: PreferenceVisibilityContext? = null
) {
    val effectiveTitleResId = if (titleResId != 0) titleResId else intentKey.titleResId
    val effectiveSummaryResId = summaryResId ?: intentKey.summaryResId

    // Skip if no title resource is available
    if (effectiveTitleResId == 0) return

    val visibility = calculateIntentPreferenceVisibility(
        intentKey = intentKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    // Show confirmation dialog when confirmationMessageResId is set on the key
    val confirmationResId = intentKey.confirmationMessageResId
    var showConfirmation by remember { mutableStateOf(false) }

    if (showConfirmation && confirmationResId != null) {
        OkCancelDialog(
            title = stringResource(effectiveTitleResId),
            message = stringResource(confirmationResId),
            onConfirm = {
                onClick()
                showConfirmation = false
            },
            onDismiss = { showConfirmation = false }
        )
    }

    val effectiveOnClick = if (confirmationResId != null) {
        { showConfirmation = true }
    } else {
        onClick
    }

    Preference(
        title = { Text(stringResource(effectiveTitleResId)) },
        summary = effectiveSummaryResId?.let { { Text(stringResource(it)) } },
        enabled = visibility.enabled,
        onClick = if (visibility.enabled) effectiveOnClick else null
    )
}

/**
 * Composable URL preference for use inside card sections.
 *
 * @param titleResId Optional title resource ID. If 0 or not provided, uses intentKey.titleResId
 */
@Composable
fun AdaptiveUrlPreferenceItem(
    intentKey: IntentPreferenceKey,
    titleResId: Int = 0,
    url: String,
    visibilityContext: PreferenceVisibilityContext? = null
) {
    val effectiveTitleResId = if (titleResId != 0) titleResId else intentKey.titleResId

    // Skip if no title resource is available
    if (effectiveTitleResId == 0) return

    val visibility = calculateIntentPreferenceVisibility(
        intentKey = intentKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    val uriHandler = LocalUriHandler.current
    Preference(
        title = { Text(stringResource(effectiveTitleResId)) },
        summary = { Text(url) },
        enabled = visibility.enabled,
        onClick = if (visibility.enabled) {
            { uriHandler.openUri(url) }
        } else null
    )
}

/**
 * Composable dynamic activity preference for use inside card sections.
 *
 * @param titleResId Optional title resource ID. If 0 or not provided, uses intentKey.titleResId
 * @param summaryResId Optional summary resource ID. If null, uses intentKey.summaryResId
 */
@Composable
fun AdaptiveDynamicActivityPreferenceItem(
    intentKey: IntentPreferenceKey,
    titleResId: Int = 0,
    activityClass: Class<*>,
    summaryResId: Int? = null,
    visibilityContext: PreferenceVisibilityContext? = null
) {
    val effectiveTitleResId = if (titleResId != 0) titleResId else intentKey.titleResId
    val effectiveSummaryResId = summaryResId ?: intentKey.summaryResId

    // Skip if no title resource is available
    if (effectiveTitleResId == 0) return

    val visibility = calculateIntentPreferenceVisibility(
        intentKey = intentKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    val context = LocalContext.current
    Preference(
        title = { Text(stringResource(effectiveTitleResId)) },
        summary = effectiveSummaryResId?.let { { Text(stringResource(it)) } },
        enabled = visibility.enabled,
        onClick = if (visibility.enabled) {
            { context.startActivity(Intent(context, activityClass)) }
        } else null
    )
}

/**
 * Composable preference that navigates to an inline Compose screen.
 * Used for IntentPreferenceKey with composeScreen attached via withCompose().
 */
@Composable
fun AdaptiveComposeScreenPreferenceItem(
    intentKey: IntentPreferenceKey,
    composeScreen: ComposeScreenContent,
    onNavigate: (ComposeScreenContent) -> Unit,
    titleResId: Int = 0,
    summaryResId: Int? = null,
    visibilityContext: PreferenceVisibilityContext? = null
) {
    val effectiveTitleResId = if (titleResId != 0) titleResId else intentKey.titleResId
    val effectiveSummaryResId = summaryResId ?: intentKey.summaryResId

    if (effectiveTitleResId == 0) return

    val visibility = calculateIntentPreferenceVisibility(
        intentKey = intentKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    Preference(
        title = { Text(stringResource(effectiveTitleResId)) },
        summary = effectiveSummaryResId?.let { { Text(stringResource(it)) } },
        enabled = visibility.enabled,
        onClick = if (visibility.enabled) {
            { onNavigate(composeScreen) }
        } else null
    )
}
