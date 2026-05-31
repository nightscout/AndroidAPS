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
     * To dispatch a navigation request from within this content (e.g. open a careportal
     * dialog, jump to another plugin), read `LocalPluginNavigationRequest.current` and
     * invoke it with a [app.aaps.core.ui.compose.navigation.NavigationRequest]. The
     * compose-time default is a no-op so previews don't crash.
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
