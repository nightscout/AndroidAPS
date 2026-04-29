package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import app.aaps.core.keys.interfaces.PreferenceItem

/**
 * Extension point for plugin-defined preference rows that need behavior beyond what
 * [PreferenceKey][app.aaps.core.keys.interfaces.PreferenceKey] can express — e.g., live
 * data alongside an editable value. Subclass and override [Content] to render a row
 * that the framework will place inline among regular preferences.
 */
abstract class CustomPreferenceItem : PreferenceItem {

    @Composable
    abstract fun Content()
}
