package app.aaps.ui.search

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.search.SearchableItem
import app.aaps.core.ui.search.SearchableProvider
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and maintains the search index for the global search feature.
 * Collects searchable items from:
 * - Registered SearchableProviders (built-in screens, dialogs, hints)
 * - Plugin preference screens via ActivePlugin
 * - All registered preference keys via Preferences
 *
 * Index is built lazily on first search to avoid startup delay.
 */
@Singleton
class SearchIndexBuilder @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val providers: Set<@JvmSuppressWildcards SearchableProvider>,
    private val preferences: Preferences,
    private val rh: ResourceHelper
) {

    private var cachedIndex: List<SearchIndexEntry>? = null

    /**
     * Builds or returns the cached search index.
     */
    fun getIndex(): List<SearchIndexEntry> {
        return cachedIndex ?: buildIndex().also { cachedIndex = it }
    }

    /**
     * Clears the cached index, forcing rebuild on next search.
     * Call this when plugins are enabled/disabled or language changes.
     */
    fun invalidateIndex() {
        cachedIndex = null
    }

    /**
     * Searches the index for items matching the query.
     * Matches against localized title, English title, and summaries.
     *
     * Special queries:
     * - %PLUGINS% - lists all plugins
     * - %DIALOGS% - lists all dialogs
     *
     * @param query Search query string (case-insensitive)
     * @return List of matching entries, sorted by relevance (title matches first)
     */
    fun search(query: String): List<SearchIndexEntry> {
        if (query.isBlank()) return emptyList()

        val index = getIndex()
        val normalizedQuery = query.trim().lowercase()

        // Special category filters
        when (normalizedQuery) {
            "%plugins%" -> return index.filter { it.category == SearchCategory.PLUGIN }
            "%dialogs%" -> return index.filter { it.category == SearchCategory.DIALOG }
        }

        return index
            .map { entry -> entry to calculateRelevance(entry, normalizedQuery) }
            .filter { (_, relevance) -> relevance > 0 }
            .sortedByDescending { (_, relevance) -> relevance }
            .map { (entry, _) -> entry }
    }

    private fun calculateRelevance(entry: SearchIndexEntry, query: String): Int {
        var relevance = 0
        val normalizedQuery = query.removeDiacritics()

        // Title matches are most relevant
        val normalizedLocalizedTitle = entry.localizedTitle.lowercase().removeDiacritics()
        if (normalizedLocalizedTitle.contains(normalizedQuery)) {
            relevance += 100
            // Exact start match is even better
            if (normalizedLocalizedTitle.startsWith(normalizedQuery)) {
                relevance += 50
            }
        }

        // English title match (useful if user searches in English but UI is localized)
        val normalizedEnglishTitle = entry.englishTitle.lowercase().removeDiacritics()
        if (normalizedEnglishTitle.contains(normalizedQuery)) {
            relevance += 80
        }

        // Summary matches are less relevant but still useful
        entry.localizedSummary?.let {
            if (it.lowercase().removeDiacritics().contains(normalizedQuery)) {
                relevance += 30
            }
        }

        entry.englishSummary?.let {
            if (it.lowercase().removeDiacritics().contains(normalizedQuery)) {
                relevance += 20
            }
        }

        return relevance
    }

    /**
     * Removes diacritics from string (ů→u, é→e, etc.)
     */
    private fun String.removeDiacritics(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{M}"), "")
    }

    private fun buildIndex(): List<SearchIndexEntry> {
        val entries = mutableListOf<SearchIndexEntry>()
        val seenKeys = mutableSetOf<String>()

        // Helper to add entry only if not already seen
        fun addIfNew(entry: SearchIndexEntry) {
            val uniqueKey = "${entry.category.name}_${entry.item.key}"
            if (seenKeys.add(uniqueKey)) {
                entries.add(entry)
            }
        }

        // 1. Collect plugins (visible ones only)
        collectPlugins(entries, seenKeys)

        // 2. Collect from SearchableProviders (built-in screens, dialogs)
        providers.forEach { provider ->
            provider.getSearchableItems().forEach { item ->
                addIfNew(createIndexEntry(item))
            }
        }

        // 3. Collect from plugin preference screens
        collectPluginScreens(entries, seenKeys)

        // 4. Collect individual preference keys
        collectPreferenceKeys(entries, seenKeys)

        return entries
    }

    private fun collectPlugins(entries: MutableList<SearchIndexEntry>, seenKeys: MutableSet<String>) {
        activePlugin.getPluginsList()
            .filter { plugin ->
                // Only include plugins that are visible in at least one category
                plugin.showInList(plugin.pluginDescription.mainType) &&
                    plugin.pluginDescription.pluginName != -1
            }
            .forEach { plugin ->
                val item = SearchableItem.Plugin(plugin)
                val entry = createIndexEntry(item)
                val uniqueKey = "${entry.category.name}_${entry.item.key}"
                if (seenKeys.add(uniqueKey)) {
                    entries.add(entry)
                }
            }
    }

    private fun collectPluginScreens(entries: MutableList<SearchIndexEntry>, seenKeys: MutableSet<String>) {
        activePlugin.getPluginsList().forEach { plugin ->
            val content = plugin.getPreferenceScreenContent()
            if (content is PreferenceSubScreenDef) {
                // Add the screen itself
                val screenItem = SearchableItem.Category(content, ownerPlugin = plugin)
                val entry = createIndexEntry(screenItem)
                val uniqueKey = "${entry.category.name}_${entry.item.key}"
                if (seenKeys.add(uniqueKey)) {
                    entries.add(entry)
                }

                // Recursively collect nested screens
                collectNestedScreens(content, plugin, entries, seenKeys)
            }
        }
    }

    private fun collectNestedScreens(screen: PreferenceSubScreenDef, plugin: PluginBase, entries: MutableList<SearchIndexEntry>, seenKeys: MutableSet<String>) {
        screen.items.forEach { item ->
            if (item is PreferenceSubScreenDef) {
                val screenItem = SearchableItem.Category(item, ownerPlugin = plugin)
                val entry = createIndexEntry(screenItem)
                val uniqueKey = "${entry.category.name}_${entry.item.key}"
                if (seenKeys.add(uniqueKey)) {
                    entries.add(entry)
                }
                collectNestedScreens(item, plugin, entries, seenKeys)
            }
        }
    }

    private data class ParentScreenInfo(val key: String, val iconResId: Int?, val plugin: PluginBase?)

    private fun collectPreferenceKeys(entries: MutableList<SearchIndexEntry>, seenKeys: MutableSet<String>) {
        // Get all preference keys from registered enums
        val allKeys = preferences.getAllPreferenceKeys()

        // Build a map of preference key to parent screen info (key + icon + plugin)
        val parentScreenMap = buildParentScreenMap()

        allKeys.forEach { prefKey ->
            // Skip keys with invalid title resource
            if (prefKey.titleResId == 0) return@forEach

            val parentInfo = parentScreenMap[prefKey.key]
            val item = SearchableItem.Preference(prefKey, parentInfo?.key, parentInfo?.iconResId, parentInfo?.plugin)
            val entry = createIndexEntry(item)
            val uniqueKey = "${entry.category.name}_${entry.item.key}"
            if (seenKeys.add(uniqueKey)) {
                entries.add(entry)
            }
        }
    }

    private fun buildParentScreenMap(): Map<String, ParentScreenInfo> {
        val map = mutableMapOf<String, ParentScreenInfo>()

        // From providers (built-in, no plugin)
        providers.forEach { provider ->
            provider.getSearchableItems().forEach { item ->
                if (item is SearchableItem.Category) {
                    collectPreferenceKeysFromScreen(item.screenDef, item.screenDef.key, item.screenDef.iconResId, null, map)
                }
            }
        }

        // From plugins
        activePlugin.getPluginsList().forEach { plugin ->
            val content = plugin.getPreferenceScreenContent()
            if (content is PreferenceSubScreenDef) {
                collectPreferenceKeysFromScreen(content, content.key, content.iconResId, plugin, map)
            }
        }

        return map
    }

    private fun collectPreferenceKeysFromScreen(
        screen: PreferenceSubScreenDef,
        screenKey: String,
        screenIconResId: Int?,
        plugin: PluginBase?,
        map: MutableMap<String, ParentScreenInfo>
    ) {
        screen.items.forEach { item ->
            when (item) {
                is PreferenceKey          -> map[item.key] = ParentScreenInfo(screenKey, screenIconResId, plugin)
                is PreferenceSubScreenDef -> {
                    // Use subscreen's icon if available, otherwise inherit from parent
                    val iconToUse = item.iconResId ?: screenIconResId
                    collectPreferenceKeysFromScreen(item, item.key, iconToUse, plugin, map)
                }

                else                      -> { /* ignore other types */
                }
            }
        }
    }

    private fun createIndexEntry(item: SearchableItem): SearchIndexEntry {
        val category = when (item) {
            is SearchableItem.Plugin     -> SearchCategory.PLUGIN
            is SearchableItem.Category   -> SearchCategory.CATEGORY
            is SearchableItem.Preference -> SearchCategory.PREFERENCE
            is SearchableItem.Dialog     -> SearchCategory.DIALOG
            is SearchableItem.Wiki       -> SearchCategory.WIKI
        }

        val localizedTitle = safeGetString(item.titleResId)
        val englishTitle = safeGetStringNotLocalised(item.titleResId)
        val localizedSummary = item.summaryResId?.let { safeGetString(it) }
        val englishSummary = item.summaryResId?.let { safeGetStringNotLocalised(it) }

        return SearchIndexEntry(
            item = item,
            localizedTitle = localizedTitle,
            englishTitle = englishTitle,
            localizedSummary = localizedSummary,
            englishSummary = englishSummary,
            category = category
        )
    }

    private fun safeGetString(resId: Int): String {
        return try {
            if (resId != 0) rh.gs(resId) else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun safeGetStringNotLocalised(resId: Int): String {
        return try {
            if (resId != 0) rh.gsNotLocalised(resId) else ""
        } catch (e: Exception) {
            ""
        }
    }
}
