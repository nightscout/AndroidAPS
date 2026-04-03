package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable

/**
 * Interface for providing Compose screen content to the preference system.
 * Used with [IntentPreferenceKey.withCompose] to embed Compose screens
 * in preferences without needing a separate Activity.
 *
 * Defined in core:ui (which has Compose dependencies) so the @Composable
 * annotation works correctly. Stored as Any? in core:keys which has no
 * Compose dependency.
 */
fun interface ComposeScreenContent {

    @Composable
    fun Content(onBack: () -> Unit)
}
