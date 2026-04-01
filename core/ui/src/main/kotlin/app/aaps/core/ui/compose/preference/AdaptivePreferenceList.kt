/*
 * Adaptive Preference List for Jetpack Compose
 * Provides generic rendering for lists of PreferenceItems
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable

import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext

/**
 * Renders a list of preference items (keys, subscreens, custom items).
 * This is the enhanced version that handles all PreferenceItem types.
 *
 * Supported types:
 * - PreferenceKey: Rendered with AdaptivePreferenceItem
 * - PreferenceSubScreenDef: Rendered as navigation item
 *
 * @param items List of PreferenceItems to render
 * @param visibilityContext Optional context for evaluating runtime visibility conditions
 * @param onNavigateToSubScreen Callback for navigating to subscreens
 */
@Composable
fun AdaptivePreferenceList(
    items: List<PreferenceItem>,
    visibilityContext: PreferenceVisibilityContext? = null,
    onNavigateToSubScreen: ((PreferenceSubScreenDef) -> Unit)? = null
) {
    items.forEach { item ->
        when (item) {
            is PreferenceKey          -> {
                // AdaptivePreferenceItem handles all visibility checks
                // (simpleMode, mode flags, dependencies, engineeringMode, runtime visibility)
                AdaptivePreferenceItem(
                    key = item,
                    visibilityContext = visibilityContext
                )
            }

            is PreferenceSubScreenDef -> {
                // Subscreens are handled by PreferenceContentExtensions as nested collapsible sections
                // Not rendered here in the flat list
            }
        }
    }
}
