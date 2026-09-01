package app.aaps.ios.shell

import app.aaps.core.interfaces.configuration.ConfigBuilder
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
     * Loads each plugin's stored enabled state and picks the active one in each category. Must run
     * after [register] - it reads the list that call provides.
     *
     * This used to only verify the categories, which is a strictly smaller job and left iOS with a
     * plugin list where **nothing was started**. Starting is what `setPluginEnabled` does, and only
     * `ConfigBuilder.initialize` walks the whole list calling it, so an enabled NSClientV3 sat there
     * never having run `onStart` - no ticks, no sync, no websocket - until the user happened to open
     * the Configuration screen, which ran the same load as a side effect and made it work.
     */
    fun initializeConfig()
}

/** The real one. */
internal class PluginStoreRegistry(
    private val pluginStore: PluginStore,
    private val configBuilder: ConfigBuilder
) : PluginRegistry {

    override fun register(plugins: List<PluginBase>) {
        pluginStore.plugins = plugins
    }

    // The same call Android's `MainApp` and the desktop `Main` both make. `ConfigBuilderImpl` is
    // shared, so this is the real one, not a copy of what it does - and it calls
    // `verifySelectionInCategories` itself at the end of loading, so nothing is lost by not calling
    // that directly any more.
    override fun initializeConfig() = configBuilder.initialize()
}
