package app.aaps.core.ui.search

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.navigation.description
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.label
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
     * The item's display title.
     */
    abstract val title: TextRef

    /**
     * Compose ImageVector icon.
     */
    open val icon: ImageVector? = null

    /**
     * Optional summary/description.
     */
    open val summary: TextRef? = null

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
     * @param ownerPlugin Optional reference to the plugin that owns this preference
     */
    data class Preference(
        val preferenceKey: PreferenceKey,
        val parentScreenKey: String? = null,
        val ownerPlugin: PluginBase? = null
    ) : SearchableItem() {

        override val key: String = preferenceKey.key
        override val title: TextRef = preferenceKey.title
        override val summary: TextRef? = preferenceKey.summary
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
        override val title: TextRef = screenDef.title
        override val summary: TextRef? = screenDef.summary
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
        override val title: TextRef = elementType.label() ?: TextRef.Literal("")

        @Deprecated("use icon")
        override val icon: ImageVector = elementType.icon()
        override val summary: TextRef? = elementType.description()
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

        // pluginId is the documented stable identity for a plugin and defaults to the class simple name,
        // so this is the same string javaClass.simpleName gave - without tying the file to the JVM.
        override val key: String = pluginRef.pluginId
        override val title: TextRef = pluginRef.pluginDescription.pluginName ?: TextRef.Literal(pluginRef.pluginId)
        override val summary: TextRef? = pluginRef.pluginDescription.description
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

        /**
         * The title comes from the ReadTheDocs API, so it is plain text, not a resource. It used to
         * be stored as resource id 0, which the index then turned into an empty string - wiki hits
         * could not be found by their own title.
         */
        override val title: TextRef = TextRef.Literal(wikiTitle)
        override val summary: TextRef? = snippet?.let { TextRef.Literal(it) }
    }
}
