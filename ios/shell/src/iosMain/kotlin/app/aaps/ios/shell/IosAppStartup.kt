package app.aaps.ios.shell

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase

/**
 * What has to happen before the first screen is composed - the iOS counterpart of `MainApp`.
 *
 * Only one step so far, and it is not optional. `PluginStore.plugins` is a `lateinit`, and the very
 * first thing that asks for the active pump reads it: `ProfileEditorViewModel` does so in its own
 * constructor, from a coroutine, and an exception thrown there is unhandled and **aborts the
 * process**. That was the crash on the first run of this - the app started, drew nothing and died.
 *
 * Run before the composition rather than beside it. Registering plugins is a list assignment, so
 * there is nothing to gain from doing it in the background, and everything to lose: a view model
 * built while the list was still empty would fail exactly as before.
 *
 * ## What Android also does here, and this does not yet
 *
 * `MainApp` migrates preferences, re-reads the profile repository, vacuums the database, starts the
 * running-mode reconciler and expiry schedulers, starts the automation runtime and runs data
 * migrations - all in the background, behind the splash screen that `AapsAppRoot` shows until
 * `config.initCompleted()`. None of that is wired up here yet. `IosClientConfig` reports
 * initialization done from the start, so the splash does not appear at all.
 */
internal class IosAppStartup(
    private val aapsLogger: AAPSLogger,
    private val registry: PluginRegistry,
    private val contributedPlugins: Map<Int, PluginBase>
) {

    fun run() {
        // Sorted by the order key each plugin registers itself with, which is the order the plugin
        // list is shown in and the order defaults are picked in.
        val plugins = contributedPlugins.entries.sortedBy { it.key }.map { it.value }
        registry.register(plugins)
        aapsLogger.debug(LTag.CORE, "Registered ${plugins.size} plugins on iOS")

        // Picks the active plugin in each category - the pump, the sensitivity, the smoothing and
        // so on - from what is enabled, falling back to each category's default. Registering the
        // list is not enough on its own: until this runs, `activePumpInternal` has nothing to
        // return and throws "No pump selected", which was the second crash on the way here.
        //
        // On Android `configBuilder.initialize()` does this. The iOS ConfigBuilder is a placeholder,
        // so the call is made directly. When a real one exists, this line moves into it.
        registry.verifySelections()
        aapsLogger.debug(LTag.CORE, "Plugin selection verified, starting the UI")
    }
}
