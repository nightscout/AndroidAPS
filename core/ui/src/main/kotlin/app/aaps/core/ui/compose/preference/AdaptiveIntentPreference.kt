/*
 * Adaptive Intent Preferences for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.UiStrings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.compose.ComposeScreenContent
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.stringResource

/**
 * Composable intent preference for use inside card sections.
 *
 * @param title Optional title override. If null, uses intentKey.title
 * @param summary Optional summary override. If null, uses intentKey.summary
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 */
@Composable
fun AdaptiveIntentPreferenceItem(
    intentKey: IntentPreferenceKey,
    title: TextRef? = null,
    summary: TextRef? = null,
    onClick: () -> Unit,
    visibilityContext: VisibilityContext? = null
) {
    val effectiveTitle = title ?: intentKey.title
    val effectiveSummary = summary ?: intentKey.summary

    val visibility = calculateIntentPreferenceVisibility(
        intentKey = intentKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    // Show confirmation dialog when confirmationMessage is set on the key
    val confirmation = intentKey.confirmationMessage
    var showConfirmation by remember { mutableStateOf(false) }

    if (showConfirmation && confirmation != null) {
        OkCancelDialog(
            title = stringResource(effectiveTitle),
            message = stringResource(confirmation),
            onConfirm = {
                onClick()
                showConfirmation = false
            },
            onDismiss = { showConfirmation = false }
        )
    }

    val effectiveOnClick = if (confirmation != null) {
        { showConfirmation = true }
    } else {
        onClick
    }

    Preference(
        title = { Text(stringResource(effectiveTitle)) },
        summary = effectiveSummary?.let { { Text(stringResource(it)) } },
        enabled = visibility.enabled,
        onClick = if (visibility.enabled) effectiveOnClick else null
    )
}

/**
 * Composable URL preference for use inside card sections.
 *
 * @param title Optional title override. If null, uses intentKey.title
 */
@Composable
fun AdaptiveUrlPreferenceItem(
    intentKey: IntentPreferenceKey,
    title: TextRef? = null,
    url: String,
    visibilityContext: VisibilityContext? = null
) {
    val effectiveTitle = title ?: intentKey.title

    val visibility = calculateIntentPreferenceVisibility(
        intentKey = intentKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    val uriHandler = LocalUriHandler.current
    Preference(
        title = { Text(stringResource(effectiveTitle)) },
        summary = { Text(url) },
        enabled = visibility.enabled,
        onClick = if (visibility.enabled) {
            { uriHandler.openUri(url) }
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
    title: TextRef? = null,
    summary: TextRef? = null,
    visibilityContext: VisibilityContext? = null
) {
    val effectiveTitle = title ?: intentKey.title
    val effectiveSummary = summary ?: intentKey.summary

    val visibility = calculateIntentPreferenceVisibility(
        intentKey = intentKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible) return

    Preference(
        title = { Text(stringResource(effectiveTitle)) },
        summary = effectiveSummary?.let { { Text(stringResource(it)) } },
        enabled = visibility.enabled,
        onClick = if (visibility.enabled) {
            { onNavigate(composeScreen) }
        } else null
    )
}
