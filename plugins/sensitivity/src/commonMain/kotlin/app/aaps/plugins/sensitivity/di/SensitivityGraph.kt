package app.aaps.plugins.sensitivity.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sensitivity.SensitivityAAPSPlugin
import app.aaps.plugins.sensitivity.SensitivityOref1Plugin
import app.aaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The Metro counterpart of `SensitivityPluginsModule` (Dagger, in :app). Same three plugins, same
 * 100-120 block, wiring in commonMain so it compiles for iOS.
 *
 * Two notes carried over from `SmoothingGraph`, both of which matter for the spike comparison:
 *
 *  - Metro is a Kotlin **compiler plugin**, not KSP. There is no annotation-processing round and no
 *    generated source file - the graph is lowered straight into IR.
 *  - The map key is a real `@IntKey(100)`, the same annotation shape the Dagger module used.
 *
 * Two things the Dagger module said cannot be repeated here, and both are source-set facts rather
 * than Metro limits:
 *
 *  - The Dagger providers asked for `ResourceHelper`. That interface lives in androidMain, so it
 *    cannot be named in common code. The real plugin constructors take [TextResolver] (which
 *    `ResourceHelper` extends), so the parameter type here is [TextResolver] and nothing is lost.
 *  - The Dagger bindings carried the `@AllConfigs` qualifier. That annotation is androidMain too, so
 *    the map exposed here is a plain `Map<Int, PluginBase>`; whoever merges it into the global
 *    @AllConfigs map does so on the :app side.
 *
 * [SingleIn] is Metro's scope marker. Stating it is still required - Metro is unscoped by default,
 * so without it every read would build a new plugin and the sensitivity state would be duplicated.
 * All three providers were `@Singleton` in the Dagger module, so all three are scoped.
 */
@DependencyGraph(AppScope::class)
interface SensitivityGraph {

    val plugins: Map<Int, PluginBase>

    @DependencyGraph.Factory
    fun interface Factory {

        /**
         * The bridge. Every dependency the module does not own is a checked parameter here, so a
         * missing one is a compile error rather than a crash on first use.
         */
        fun create(
            @Provides aapsLogger: AAPSLogger,
            @Provides rh: TextResolver,
            @Provides preferences: Preferences,
            @Provides dateUtil: DateUtil,
            @Provides activePlugin: ActivePlugin
        ): SensitivityGraph
    }

    @SingleIn(AppScope::class)
    @Provides
    fun provideSensitivityAAPSPlugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver,
        preferences: Preferences,
        dateUtil: DateUtil,
        activePlugin: ActivePlugin
    ): SensitivityAAPSPlugin = SensitivityAAPSPlugin(
        aapsLogger = aapsLogger,
        rh = rh,
        preferences = preferences,
        dateUtil = dateUtil,
        activePlugin = activePlugin
    )

    @SingleIn(AppScope::class)
    @Provides
    fun provideSensitivityWeightedAveragePlugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver,
        preferences: Preferences,
        dateUtil: DateUtil,
        activePlugin: ActivePlugin
    ): SensitivityWeightedAveragePlugin = SensitivityWeightedAveragePlugin(
        aapsLogger = aapsLogger,
        rh = rh,
        preferences = preferences,
        dateUtil = dateUtil,
        activePlugin = activePlugin
    )

    // No activePlugin here. The real constructor takes four parameters, not five.
    @SingleIn(AppScope::class)
    @Provides
    fun provideSensitivityOref1Plugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver,
        preferences: Preferences,
        dateUtil: DateUtil
    ): SensitivityOref1Plugin = SensitivityOref1Plugin(
        aapsLogger = aapsLogger,
        rh = rh,
        preferences = preferences,
        dateUtil = dateUtil
    )

    // The 100-120 block, step 10. @IntKey is the same shape the Dagger module used.
    @Provides @IntoMap @IntKey(100) fun aaps(plugin: SensitivityAAPSPlugin): PluginBase = plugin

    @Provides @IntoMap @IntKey(110) fun weightedAverage(plugin: SensitivityWeightedAveragePlugin): PluginBase = plugin

    @Provides @IntoMap @IntKey(120) fun oref1(plugin: SensitivityOref1Plugin): PluginBase = plugin
}
