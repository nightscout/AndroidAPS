package app.aaps.core.ui.compose.navigation

sealed class NavigationRequest {
    data class Element(val type: ElementType) : NavigationRequest()
    data class QuickWizard(val guid: String) : NavigationRequest()
    data class Plugin(val className: String) : NavigationRequest()
    data class PluginPreferences(val pluginKey: String) : NavigationRequest()
}
