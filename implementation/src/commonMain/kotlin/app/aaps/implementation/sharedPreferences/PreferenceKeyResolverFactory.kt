package app.aaps.implementation.sharedPreferences

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.keys.PreferenceKeyResolver
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.data.plugin.PluginType
import dev.zacsweers.metro.Inject

/**
 * Builds a [PreferenceKeyResolver] that knows which plugin owns which key.
 *
 * ## Why this is a separate thing and not a method on `Preferences`
 *
 * The resolver needs two facts that live in different places and cannot meet in either of them:
 *
 * - the registered keys, which only `PreferencesImpl` has (`prefsList`), and
 * - who owns them, which only the plugin list has.
 *
 * `registerPreferences(keys)` is `prefsList.addAll(keys)` - it throws the caller away, so ownership
 * is not recorded anywhere and has to be re-derived by walking the plugins. And `core:keys`, where
 * the resolver lives, cannot see `PluginBase` at all: `core:interfaces` depends on `core:keys`, so
 * the arrow cannot point back.
 *
 * ## What counts as a pump
 *
 * `pluginDescription.mainType == PluginType.PUMP`, which is the same property `ConfigBuilderImpl`
 * uses to elect the active pump. Deliberately not the `@PumpDriver` annotation: that is a DI bucket
 * used to decide what gets built per flavour, and a key's category should follow what the plugin IS
 * at runtime rather than how the graph assembled it.
 *
 * ## The flavour hole this does NOT close
 *
 * `MetroGraphs.allPlugins` never constructs the `@APS`, `@PumpDriver` and `@NotNSClient` buckets on
 * an AAPSCLIENT or pumpcontrol build, so on those flavours there are no pump plugins to walk and no
 * pump keys registered. An import there cannot classify a pump key at all - it will not resolve in
 * the first place. That is a real limit of owner-based classification, not something a better walk
 * would fix, and it is why an unresolved key must not simply be dropped (plan 4.1.6).
 *
 * ## Call it late
 *
 * The plugin list is `lateinit` and assigned in `MainApp.onCreate` AFTER `doMigrations()`. Building
 * a resolver before that point throws, and building one during start-up would capture a half-built
 * registry, so this makes a fresh one per call rather than caching. The cost is one pass over the
 * plugins and one map build; an import does it once.
 */
@Inject
class PreferenceKeyResolverFactory(
    private val preferences: Preferences,
    private val activePlugin: ActivePlugin
) {

    fun create(): PreferenceKeyResolver {
        val owners = activePlugin.getPluginsList().filterIsInstance<PluginBaseWithPreferences>()
        val (pumps, others) = owners.partition { it.pluginDescription.mainType == PluginType.PUMP }

        return PreferenceKeyResolver(
            keys = preferences.getAllKeys(),
            // A key claimed by a pump and by something else lands in both lists. `categoryOf` checks
            // pump first, so it is treated as a pump key - the direction that PRESERVES it when the
            // user asked to keep the pump settings, rather than overwriting it.
            pumpOwned = pumps.flatMap { it.ownPreferences },
            otherPluginOwned = others.flatMap { it.ownPreferences }
        )
    }

}
