package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable

/**
 * Interface for plugins that provide Jetpack Compose content.
 *
 * This interface is defined in core:ui (which has Compose dependencies) and can be
 * properly invoked as a Composable.
 */
interface ComposablePluginContent {

    /**
     * Renders the plugin's Compose content.
     *
     * @param setToolbarConfig Callback to configure the toolbar from within the content
     * @param onNavigateBack Callback to navigate back (typically finish the activity)
     * @param onSettings Callback to open plugin settings, or null if settings are not available
     */
    @Composable
    fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)? = null
    )
}
