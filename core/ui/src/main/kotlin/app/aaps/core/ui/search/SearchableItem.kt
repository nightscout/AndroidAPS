package app.aaps.core.ui.search

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.descriptionResId
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef

/**
 * Represents an item that can be found via the global search feature.
 * Each subtype represents a different category of searchable content.
 */
sealed class SearchableItem {

    /**
     * Unique key identifying this searchable item.
     */
    abstract val key: String

    /**
     * Resource ID for the item's display title.
     */
    abstract val titleResId: Int

    /**
     * Optional resource ID for an icon to display with this item.
     * @deprecated Use [icon] instead for Compose icons
     */
    abstract val iconResId: Int?

    /**
     * Optional Compose ImageVector icon.
     * Preferred over iconResId when available.
     */
    open val icon: ImageVector? = null

    /**
     * Optional resource ID for a summary/description.
     */
    open val summaryResId: Int? = null

    /**
     * Optional reference to the plugin that owns this item.
     * Null for built-in preferences (general, protection, etc.).
     * Used to check if plugin is enabled at runtime.
     */
    open val plugin: PluginBase? = null

    /**
     * A searchable preference key.
     * Navigates to the preference screen and highlights this specific preference.
     *
     * @param preferenceKey The PreferenceKey enum value
     * @param parentScreenKey Optional key of the parent screen containing this preference
     * @param parentIconResId Optional icon from parent screen (used if preference has no icon)
     * @param ownerPlugin Optional reference to the plugin that owns this preference
     */
    data class Preference(
        val preferenceKey: PreferenceKey,
        val parentScreenKey: String? = null,
        val parentIconResId: Int? = null,
        val ownerPlugin: PluginBase? = null
    ) : SearchableItem() {

        override val key: String = preferenceKey.key
        override val titleResId: Int = preferenceKey.titleResId
        override val iconResId: Int? = parentIconResId
        override val summaryResId: Int? = preferenceKey.summaryResId
        override val plugin: PluginBase? = ownerPlugin
    }

    /**
     * A searchable preference category/section.
     * Navigates directly to this category screen.
     *
     * @param screenDef The PreferenceSubScreenDef defining the category
     * @param ownerPlugin Optional reference to the plugin that owns this category
     */
    data class Category(
        val screenDef: PreferenceSubScreenDef,
        val ownerPlugin: PluginBase? = null
    ) : SearchableItem() {

        override val key: String = screenDef.key
        override val titleResId: Int = screenDef.titleResId
        override val iconResId: Int? = screenDef.iconResId
        override val summaryResId: Int? = screenDef.summaryResId
        override val plugin: PluginBase? = ownerPlugin
        override val icon: ImageVector? = screenDef.icon
    }

    /**
     * A searchable dialog or navigation screen.
     * [ElementType] is the type-safe key — also provides icon, label, and description.
     *
     * @param elementType The element type (serves as key AND visual identity)
     */
    data class Dialog(
        val elementType: ElementType
    ) : SearchableItem() {

        override val key: String = elementType.name
        override val titleResId: Int = elementType.labelResId()

        @Deprecated("use icon")
        override val iconResId: Int? = null
        override val icon: ImageVector = elementType.icon()
        override val summaryResId: Int? = elementType.descriptionResId().takeIf { it != 0 }
    }

    /**
     * A searchable plugin.
     * Clicking navigates to the plugin (same as drawer click).
     *
     * @param pluginRef The plugin reference
     */
    data class Plugin(
        val pluginRef: PluginBase
    ) : SearchableItem() {

        override val key: String = pluginRef.javaClass.simpleName
        override val titleResId: Int = pluginRef.pluginDescription.pluginName
        override val iconResId: Int? = pluginRef.pluginDescription.pluginIcon.takeIf { it != -1 }
        override val summaryResId: Int? = pluginRef.pluginDescription.description.takeIf { it != -1 }
        override val plugin: PluginBase = pluginRef
    }

    /**
     * A wiki/documentation search result from ReadTheDocs.
     * Clicking opens the URL in the default browser.
     *
     * @param url Full URL to the documentation page (with anchor)
     * @param wikiTitle Page/section title from the API
     * @param snippet Content snippet with search term context
     */
    data class Wiki(
        val url: String,
        val wikiTitle: String,
        val snippet: String?
    ) : SearchableItem() {

        override val key: String = url
        override val titleResId: Int = 0 // not used — title is dynamic
        override val iconResId: Int? = null
    }
}
