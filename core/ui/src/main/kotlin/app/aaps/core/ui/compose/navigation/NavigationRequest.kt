package app.aaps.core.ui.compose.navigation

import androidx.compose.runtime.compositionLocalOf

sealed class NavigationRequest {
    data class Element(val type: ElementType) : NavigationRequest()
    data class QuickWizard(val guid: String) : NavigationRequest()
    data class Plugin(val className: String) : NavigationRequest()
    data class PluginPreferences(val pluginKey: String) : NavigationRequest()
}

/**
 * CompositionLocal for dispatching a [NavigationRequest] from within a plugin's compose content
 * (or any nested screen) up to whichever host owns the NavController. Provided by
 * `PluginContentRoute` in the app module — defaults to a no-op so screens used outside that
 * route (previews, tests) don't crash.
 */
val LocalPluginNavigationRequest = compositionLocalOf<(NavigationRequest) -> Unit> { { } }
