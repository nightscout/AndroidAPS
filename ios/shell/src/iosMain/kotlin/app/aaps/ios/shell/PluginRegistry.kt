package app.aaps.ios.shell

import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.implementation.plugin.PluginStore

/**
 * The two things start up has to do to the plugin list, named so they can be tested.
 *
 * [PluginStore] needs a `Preferences` to build, which makes it awkward to stand up in a test for
 * what is really a question about ordering. This seam keeps [IosAppStartup] testable and states the
 * contract that the two calls are a pair and happen in this order.
 */
internal interface PluginRegistry {

    /** Fills the registry. Until this runs, `PluginStore.plugins` is an unset `lateinit`. */
    fun register(plugins: List<PluginBase>)

    /**
     * Picks the active plugin in each category from what is enabled, falling back to the category
     * default. Must run after [register] - it reads the list that call provides.
     */
    fun verifySelections()
}

/** The real one. */
internal class PluginStoreRegistry(private val pluginStore: PluginStore) : PluginRegistry {

    override fun register(plugins: List<PluginBase>) {
        pluginStore.plugins = plugins
    }

    override fun verifySelections() = pluginStore.verifySelectionInCategories()
}
